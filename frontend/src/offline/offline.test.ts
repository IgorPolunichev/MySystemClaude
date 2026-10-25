import 'fake-indexeddb/auto';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Engineer, Equipment } from '../api/generated/directory/model';
import { db } from './db';
import { network } from './network';

const auth = vi.hoisted(() => ({ token: 'token' as string | undefined }));
vi.mock('../app/auth/tokenStore', () => ({
  getAccessToken: () => auth.token,
  onAccessToken: () => () => undefined,
}));

const me = { id: 'e1', fullName: 'Петров И. С.', active: true } as Engineer;
const equipment = [
  {
    id: 'q1',
    organizationId: 'o1',
    organizationName: 'АО «Тест»',
    equipmentTypeName: 'Самосвал',
    brand: 'CAT',
    model: '777G',
    meterType: 'HOURS',
  } as Equipment,
];

describe('офлайн: акт без связи → очередь → синхронизация', () => {
  beforeEach(async () => {
    await db.delete();
    await db.open();
    await db.refs.bulkPut([
      { key: 'equipment', data: equipment, savedAt: 0 },
      { key: 'engineers', data: [me], savedAt: 0 },
      { key: 'executors', data: [], savedAt: 0 },
      { key: 'units', data: [], savedAt: 0 },
      { key: 'me', data: me, savedAt: 0 },
    ]);
    await db.meta.put({ key: 'refsAt', value: Date.now() });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('создаёт черновик с временным номером, копит операции и отправляет их по порядку', async () => {
    network.setOnline(false);
    const { apiFetch } = await import('../api/http');
    const created = await apiFetch<{ data: { id: string; number: string; status: string } }>('/api/v1/acts', {
      method: 'POST',
      body: JSON.stringify({ id: '01990000-0000-7000-8000-0000000000a1', equipmentId: 'q1' }),
    });
    expect(created.data.status).toBe('DRAFT');
    expect(created.data.number).toMatch(/^Ч-[0-9A-F]{4}-1$/);

    const timed = await apiFetch<{ data: { act: { totalHours: number } } }>(
      `/api/v1/acts/${created.data.id}/time/e1/2026-10-05`,
      {
        method: 'PUT',
        body: JSON.stringify({
          startTime: '10:00',
          endTime: '13:00',
          breaks: [{ startTime: '11:00', endTime: '12:00' }],
        }),
      },
    );
    expect(timed.data.act.totalHours).toBe(2);

    const list = await apiFetch<{ data: { items: { number: string }[] } }>('/api/v1/acts?mine=true');
    expect(list.data.items[0].number).toBe(created.data.number);
    expect(await db.outbox.count()).toBe(2);
    expect(network.get().pending).toBe(2);

    // Связь вернулась: сервер принимает операции и отвечает своими карточками акта.
    const calls: string[] = [];
    const server = {
      id: created.data.id,
      version: 1,
      status: 'DRAFT',
      number: null,
      permissions: {},
      timeEntries: [],
      engineers: [],
      parts: [],
      photos: [],
    };
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        calls.push(`${init?.method ?? 'GET'} ${url}`);
        if (url.startsWith('/api/v1/acts/device'))
          return new Response(JSON.stringify([server]), { status: 200 });
        return new Response(JSON.stringify(url.includes('/time/') ? { act: server } : server), {
          status: 200,
        });
      }),
    );
    network.setOnline(true);
    const { syncNow } = await import('./sync');
    await syncNow();
    expect(calls.slice(0, 2)).toEqual([
      'POST /api/v1/acts',
      `PUT /api/v1/acts/${created.data.id}/time/e1/2026-10-05`,
    ]);
    expect(await db.outbox.count()).toBe(0);
    expect((await db.acts.get(created.data.id))?.localOnly).toBe(false);
  });

  it('конфликт версии останавливает отправку правок этого акта', async () => {
    network.setOnline(false);
    const { apiFetch } = await import('../api/http');
    const created = await apiFetch<{ data: { id: string } }>('/api/v1/acts', {
      method: 'POST',
      body: JSON.stringify({ id: '01990000-0000-7000-8000-0000000000b2', equipmentId: 'q1' }),
    });
    await db.outbox.clear();
    await db.acts.update(created.data.id, { localOnly: false });
    await apiFetch(`/api/v1/acts/${created.data.id}`, {
      method: 'PUT',
      body: JSON.stringify({ version: 3, equipmentId: 'q1', shortDescription: 'ТО' }),
    });
    await apiFetch(`/api/v1/acts/${created.data.id}/time/e1/2026-10-06`, {
      method: 'PUT',
      body: JSON.stringify({ startTime: '08:00', endTime: '12:00' }),
    });

    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url.startsWith('/api/v1/acts/device')) return new Response('[]', { status: 200 });
        return new Response(
          JSON.stringify({ title: 'Конфликт', detail: 'Акт изменён другим пользователем' }),
          { status: 409 },
        );
      }),
    );
    network.setOnline(true);
    const { syncNow } = await import('./sync');
    await syncNow();
    const ops = await db.outbox.orderBy('seq').toArray();
    expect(ops.map((o) => o.status)).toEqual(['conflict', 'pending']);
    expect(network.get().problems).toBe(1);
  });

  it('«Открыть акт» без связи: номер выдаёт сервер после синхронизации', async () => {
    network.setOnline(false);
    const { apiFetch } = await import('../api/http');
    const id = '01990000-0000-7000-8000-0000000000c3';
    const created = await apiFetch<{ data: { permissions: { canOpen: boolean } } }>('/api/v1/acts', {
      method: 'POST',
      body: JSON.stringify({ id, equipmentId: 'q1' }),
    });
    expect(created.data.permissions.canOpen).toBe(true);
    const opened = await apiFetch<{ data: { status: string } }>(`/api/v1/acts/${id}/open`, {
      method: 'POST',
    });
    expect(opened.data.status).toBe('DRAFT');
    expect((await db.acts.get(id))?.openAfterSync).toBe(true);

    const numbers: string[] = [];
    window.addEventListener('acts:numbered', (e) =>
      numbers.push((e as CustomEvent<{ number: string }>).detail.number),
    );
    const draft = {
      id,
      version: 1,
      status: 'DRAFT',
      number: null,
      permissions: {},
      timeEntries: [],
      engineers: [],
      parts: [],
      photos: [],
    };
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        if (url.startsWith('/api/v1/acts/device')) {
          return new Response(JSON.stringify([{ ...draft, status: 'OPEN', number: 'АВР-2026-000123' }]), {
            status: 200,
          });
        }
        if (url.endsWith('/open') && init?.method === 'POST') {
          return new Response(JSON.stringify({ ...draft, status: 'OPEN', number: 'АВР-2026-000123' }), {
            status: 200,
          });
        }
        return new Response(JSON.stringify(draft), { status: 200 });
      }),
    );
    network.setOnline(true);
    const { syncNow } = await import('./sync');
    await syncNow();
    expect(numbers).toEqual(['АВР-2026-000123']);
    expect((await db.acts.get(id))?.detail.status).toBe('OPEN');
    expect(await db.outbox.count()).toBe(0);
  });

  it('операции другого пользователя ПК ждут его входа', async () => {
    await db.outbox.add({
      opId: 'x1',
      actId: 'a-other',
      method: 'PUT',
      url: '/api/v1/acts/a-other/time/e2/2026-10-05',
      body: { startTime: '08:00', endTime: '12:00' },
      label: 'Время',
      createdAt: Date.now(),
      status: 'pending',
      attempts: 0,
      user: 'colleague',
    });
    const calls: string[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        calls.push(url);
        return new Response('[]', { status: 200 });
      }),
    );
    network.setOnline(true);
    const { syncNow } = await import('./sync');
    await syncNow();
    expect(calls.some((u) => u.includes('a-other'))).toBe(false);
    expect(await db.outbox.count()).toBe(1);
  });

  it('справочники загружаются по отдельности: ошибка одного не мешает остальным', async () => {
    await db.refs.clear();
    await db.meta.delete('refsAt');
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url.startsWith('/api/v1/equipment')) return new Response('{}', { status: 500 });
        if (url.startsWith('/api/v1/organizations?')) {
          return new Response(
            JSON.stringify({ items: [{ id: 'o1', name: 'АО «Тест»' }], totalElements: 1 }),
            { status: 200 },
          );
        }
        if (url.startsWith('/api/v1/acts/device')) return new Response('[]', { status: 200 });
        return new Response(JSON.stringify({ items: [] }), { status: 200 });
      }),
    );
    network.setOnline(true);
    const { syncNow } = await import('./sync');
    await syncNow();
    expect((await db.refs.get('organizations'))?.data).toEqual([{ id: 'o1', name: 'АО «Тест»' }]);
    expect(await db.refs.get('equipment')).toBeUndefined();
    expect(String((await db.meta.get('lastSyncError'))?.value)).toContain('equipment');
  });

  it('«Мои» без связи: если карточки инженера на ПК нет — по отметке «вы участник» из акта', async () => {
    const detail = {
      id: 'a9',
      status: 'OPEN',
      number: 'АВР-1',
      engineers: [{ engineerId: 'eX', isCurrentUser: true }],
      timeEntries: [],
    };
    await db.acts.put({ id: 'a9', detail: detail as never, localOnly: false, savedAt: 0 });
    await db.refs.delete('me:');
    network.setOnline(false);
    const { apiFetch } = await import('../api/http');
    const list = await apiFetch<{ data: { items: { number: string }[] } }>('/api/v1/acts?mine=true');
    expect(list.data.items.map((i) => i.number)).toContain('АВР-1');
  });

  it('синхронизация до входа не «залипает»: после появления пропуска она выполняется', async () => {
    const calls: string[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        calls.push(url);
        return new Response(url.startsWith('/api/v1/acts/device') ? '[]' : JSON.stringify({ items: [] }), {
          status: 200,
        });
      }),
    );
    const { syncNow } = await import('./sync');
    auth.token = undefined;
    await syncNow();
    expect(calls).toHaveLength(0);
    auth.token = 'token';
    await syncNow();
    expect(calls.some((u) => u.startsWith('/api/v1/acts/device'))).toBe(true);
  });

  it('акт без связи + «моргнувшая» связь при отправке: правки шапки уходят без ложного конфликта', async () => {
    auth.token = 'token';
    network.setOnline(false);
    const { apiFetch } = await import('../api/http');
    const id = '01990000-0000-7000-8000-0000000000d4';
    await apiFetch('/api/v1/acts', { method: 'POST', body: JSON.stringify({ id, equipmentId: 'q1' }) });
    await apiFetch(`/api/v1/acts/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ version: 0, equipmentId: 'q1', shortDescription: 'ТО' }),
    });
    await apiFetch(`/api/v1/acts/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ version: 0, equipmentId: 'q1', shortDescription: 'ТО-2000' }),
    });

    // Сервер проверяет версию, как настоящий: при создании — версия 1, каждая правка шапки увеличивает её.
    const server = {
      id,
      version: 0,
      status: 'DRAFT',
      number: null,
      shortDescription: null as string | null,
      permissions: {},
      timeEntries: [],
      engineers: [],
      parts: [],
      photos: [],
    };
    let dropAfterCreate = true;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        const method = init?.method ?? 'GET';
        if (url.startsWith('/api/v1/acts/device'))
          return new Response(JSON.stringify([server]), { status: 200 });
        if (method === 'POST' && url === '/api/v1/acts') {
          server.version = 1;
          return new Response(JSON.stringify(server), { status: 201 });
        }
        if (method === 'PUT') {
          if (dropAfterCreate) {
            dropAfterCreate = false;
            throw new TypeError('Failed to fetch');
          }
          const body = JSON.parse(String(init?.body)) as { version: number; shortDescription: string };
          if (body.version !== server.version) {
            return new Response(
              JSON.stringify({ title: 'Конфликт', detail: 'Акт изменён другим пользователем' }),
              { status: 409 },
            );
          }
          server.version += 1;
          server.shortDescription = body.shortDescription;
          return new Response(JSON.stringify(server), { status: 200 });
        }
        return new Response(JSON.stringify({ items: [] }), { status: 200 });
      }),
    );
    network.setOnline(true);
    const { syncNow } = await import('./sync');
    await syncNow(); // создание ушло, на правке связь оборвалась
    expect((await db.outbox.toArray()).map((o) => o.status)).toEqual(['pending', 'pending']);
    await syncNow(); // связь вернулась
    expect(await db.outbox.count()).toBe(0);
    expect(server.shortDescription).toBe('ТО-2000');
    expect(server.version).toBe(3);
  });

  it('настоящий конфликт по-прежнему распознаётся: акт со связью изменил коллега', async () => {
    auth.token = 'token';
    const id = '01990000-0000-7000-8000-0000000000e5';
    const base = {
      id,
      version: 5,
      status: 'OPEN',
      number: 'АВР-5',
      permissions: {},
      timeEntries: [],
      engineers: [],
      parts: [],
      photos: [],
    };
    await db.acts.put({ id, detail: base as never, localOnly: false, savedAt: 0 });
    network.setOnline(false);
    const { apiFetch } = await import('../api/http');
    await apiFetch(`/api/v1/acts/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ version: 5, shortDescription: 'моё' }),
    });
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        if (url.startsWith('/api/v1/acts/device')) return new Response('[]', { status: 200 });
        if (init?.method === 'PUT') {
          const body = JSON.parse(String(init.body)) as { version: number };
          // коллега уже сохранил версию 6
          return new Response(JSON.stringify({ title: 'Конфликт' }), {
            status: body.version === 6 ? 200 : 409,
          });
        }
        return new Response(JSON.stringify({ items: [] }), { status: 200 });
      }),
    );
    network.setOnline(true);
    const { syncNow } = await import('./sync');
    await syncNow();
    expect((await db.outbox.toArray()).map((o) => o.status)).toEqual(['conflict']);
  });

  it('шлюз отвечает 502 (запускается): операции остаются в очереди и уходят, когда он поднялся', async () => {
    auth.token = 'token';
    network.setOnline(false);
    const { apiFetch } = await import('../api/http');
    const id = '01990000-0000-7000-8000-0000000000f6';
    await apiFetch('/api/v1/acts', { method: 'POST', body: JSON.stringify({ id, equipmentId: 'q1' }) });
    // в прежней версии такая операция могла быть ошибочно отклонена — она должна вернуться в очередь
    await db.outbox.add({
      opId: 'old',
      actId: id,
      method: 'PUT',
      url: `/api/v1/acts/${id}/time/e1/2026-10-05`,
      body: { startTime: '08:00', endTime: '10:00' },
      label: 'Время',
      createdAt: Date.now(),
      status: 'rejected',
      error: 'Ошибка 502',
      attempts: 1,
    });
    let up = false;
    const server = {
      id,
      version: 1,
      status: 'DRAFT',
      number: null,
      permissions: {},
      timeEntries: [],
      engineers: [],
      parts: [],
      photos: [],
    };
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (!up) return new Response('<html>502 Bad Gateway</html>', { status: 502 });
        if (url.startsWith('/api/v1/acts/device'))
          return new Response(JSON.stringify([server]), { status: 200 });
        return new Response(JSON.stringify(url.includes('/time/') ? { act: server } : server), {
          status: 200,
        });
      }),
    );
    const { syncNow } = await import('./sync');
    network.setOnline(true);
    await syncNow();
    expect((await db.outbox.toArray()).map((o) => o.status)).toEqual(['pending', 'pending']);
    expect(network.get().online).toBe(false);
    up = true;
    await syncNow();
    expect(await db.outbox.count()).toBe(0);
    expect(network.get().online).toBe(true);
  });

  it('фото без связи: на ПК → подпись → синхронизация загружает файл и привязывает к акту', async () => {
    auth.token = 'token';
    network.setOnline(false);
    const { apiFetch } = await import('../api/http');
    const id = '01990000-0000-7000-8000-0000000000a7';
    await apiFetch('/api/v1/acts', { method: 'POST', body: JSON.stringify({ id, equipmentId: 'q1' }) });
    const { queueLocalPhoto, isLocalMedia } = await import('./files');
    const photo = await queueLocalPhoto(id, new Blob(['jpeg'], { type: 'image/jpeg' }), 'IMG_1.jpg', null);
    const second = await queueLocalPhoto(id, new Blob(['jpeg2'], { type: 'image/jpeg' }), 'IMG_2.jpg', null);
    expect(isLocalMedia(photo.mediaId)).toBe(true);
    await apiFetch(`/api/v1/acts/${id}/photos/${photo.id}`, {
      method: 'PUT',
      body: JSON.stringify({ caption: 'Течь масла' }),
    });
    await apiFetch(`/api/v1/acts/${id}/photos/${second.id}`, { method: 'DELETE' });
    const local = await apiFetch<{ data: { photos: { caption: string | null }[] } }>(`/api/v1/acts/${id}`);
    expect(local.data.photos.map((p) => p.caption)).toEqual(['Течь масла']);
    expect((await db.outbox.toArray()).map((o) => o.label)).toEqual(['Создание акта', 'Фото']);

    const posted: string[] = [];
    let attachBody: { mediaId: string; caption: string } | undefined;
    const server = {
      id,
      version: 1,
      status: 'DRAFT',
      number: null,
      permissions: {},
      timeEntries: [],
      engineers: [],
      parts: [],
      photos: [] as unknown[],
      attachments: [],
    };
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        posted.push(`${init?.method ?? 'GET'} ${url}`);
        if (url === '/api/v1/media') return new Response(JSON.stringify({ id: 'm-real' }), { status: 201 });
        if (url.endsWith('/photos') && init?.method === 'POST') {
          attachBody = JSON.parse(String(init.body));
          server.photos = [{ id: 'p-real', mediaId: 'm-real', caption: attachBody!.caption, position: 1 }];
          return new Response(JSON.stringify(server), { status: 201 });
        }
        if (url.startsWith('/api/v1/acts/device'))
          return new Response(JSON.stringify([server]), { status: 200 });
        return new Response(JSON.stringify(server), { status: 200 });
      }),
    );
    network.setOnline(true);
    const { syncNow } = await import('./sync');
    await syncNow();
    expect(posted.slice(0, 3)).toEqual([
      'POST /api/v1/acts',
      'POST /api/v1/media',
      `POST /api/v1/acts/${id}/photos`,
    ]);
    expect(attachBody).toEqual({ mediaId: 'm-real', caption: 'Течь масла' });
    expect(await db.outbox.count()).toBe(0);
    expect(await db.files.get('m-real:original')).toBeDefined();
    expect(await db.files.get(`${photo.mediaId}:original`)).toBeUndefined();
    expect(await db.files.get(`${second.mediaId}:original`)).toBeUndefined();
  });
});

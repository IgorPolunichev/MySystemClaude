import 'fake-indexeddb/auto';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Engineer, Equipment } from '../api/generated/directory/model';
import { db } from './db';
import { network } from './network';

vi.mock('../app/auth/tokenStore', () => ({
  getAccessToken: () => 'token',
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
});

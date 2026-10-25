import type { ActDetail } from '../api/generated/acts/model';
import { getAccessToken, onAccessToken } from '../app/auth/tokenStore';
import { db, getMeta, setMeta, type OutboxOp } from './db';
import { network } from './network';
import { getDeviceScope } from './db';
import { currentUsername } from './user';

/**
 * Синхронизация (раздел 17 SPEC.md):
 * 1) отправка очереди по порядку — сервер заново проверяет права и данные каждой операции;
 * 2) загрузка на устройство моих актов (черновики, открытые, изменённые за 90 дней) и справочников.
 * Конфликт версии (409) останавливает отправку изменений этого акта до решения пользователя;
 * отказ сервера (403, 404, 422) помечает операцию «отклонена».
 */
const REFS_TTL = 24 * 60 * 60 * 1000;
let running: Promise<void> | null = null;

async function call(method: string, url: string, body?: unknown): Promise<{ status: number; data: unknown }> {
  const headers = new Headers({ Accept: 'application/json' });
  const token = getAccessToken();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (body !== undefined) headers.set('Content-Type', 'application/json');
  const response = await fetch(url, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let data: unknown;
  try {
    data = text ? JSON.parse(text) : undefined;
  } catch {
    data = text;
  }
  return { status: response.status, data };
}

const isActDetail = (v: unknown): v is ActDetail =>
  !!v && typeof v === 'object' && 'permissions' in (v as object) && 'timeEntries' in (v as object);

export async function refreshQueueState(): Promise<void> {
  const ops = await db.outbox.toArray();
  network.setQueue(
    ops.filter((o) => o.status === 'pending').length,
    ops.filter((o) => o.status !== 'pending').length,
  );
}

/** Отправить очередь; возвращает false, если связь пропала. */
async function push(): Promise<boolean> {
  const blocked = new Set((await db.outbox.where('status').equals('conflict').toArray()).map((o) => o.actId));
  const user = currentUsername();
  // Операции другого пользователя этого ПК ждут его входа: сервер проверяет права автора.
  const ops = (await db.outbox.where('status').equals('pending').sortBy('seq')).filter(
    (o) => !o.user || o.user === user,
  );
  for (const op of ops) {
    if (blocked.has(op.actId)) continue;
    let body = op.body;
    // Правка шапки отправляется от версии из карточки на ПК: до первой удачной операции это версия, которую
    // видел пользователь (так распознаётся чужая правка на сервере), после — версия, которую вернул сервер на
    // нашу предыдущую операцию. Карточка хранится в базе ПК, поэтому версия не теряется, если связь «моргнула»
    // между операциями. У акта, созданного без связи, версии на сервере до создания нет — ждём создания.
    if (op.method === 'PUT' && /^\/api\/v1\/acts\/[^/]+$/.test(op.url)) {
      const stored = await db.acts.get(op.actId);
      if (stored && !stored.localOnly) body = { ...(body as object), version: stored.detail.version };
    }
    let result;
    try {
      result = await call(op.method, op.url, body);
    } catch {
      network.setOnline(false);
      return false;
    }
    if (result.status >= 200 && result.status < 300) {
      const detail = isActDetail(result.data)
        ? result.data
        : isActDetail((result.data as { act?: unknown })?.act)
          ? (result.data as { act: ActDetail }).act
          : undefined;
      if (detail) {
        const stored = await db.acts.get(op.actId);
        await db.acts.put({
          ...(stored ?? { id: op.actId, savedAt: 0 }),
          id: op.actId,
          detail,
          localOnly: false,
          savedAt: Date.now(),
        });
      }
      await db.outbox.delete(op.seq!);
      continue;
    }
    const message =
      (result.data as { detail?: string; title?: string })?.detail ??
      (result.data as { title?: string })?.title ??
      `Ошибка ${result.status}`;
    if (result.status === 401) return false;
    const status: OutboxOp['status'] = result.status === 409 ? 'conflict' : 'rejected';
    await db.outbox.update(op.seq!, { status, error: message, attempts: op.attempts + 1 });
    if (status === 'conflict') blocked.add(op.actId);
  }
  // «Открыть после синхронизации»: черновики, созданные без связи, открываются сервером и получают номер.
  for (const s of await db.acts.filter((a) => !!a.openAfterSync && !a.localOnly).toArray()) {
    if ((await db.outbox.where('actId').equals(s.id).count()) > 0) continue;
    try {
      const r = await call('POST', `/api/v1/acts/${s.id}/open`);
      if (r.status === 200 && isActDetail(r.data)) {
        await db.acts.put({ ...s, detail: r.data, openAfterSync: false, savedAt: Date.now() });
        window.dispatchEvent(new CustomEvent('acts:numbered', { detail: { number: r.data.number } }));
      }
    } catch {
      return false;
    }
  }
  return true;
}

async function pages<T>(url: string): Promise<T[]> {
  const all: T[] = [];
  for (let page = 0; page < 100; page++) {
    const r = await call('GET', `${url}${url.includes('?') ? '&' : '?'}page=${page}&size=200`);
    if (r.status !== 200) throw new Error(`ответ ${r.status}`);
    const items = (r.data as { items?: T[]; totalElements?: number })?.items ?? [];
    all.push(...items);
    if (items.length < 200) break;
  }
  return all;
}

/** Загрузить на устройство мои акты; справочники — раз в сутки или принудительно. */
async function pull(forceRefs: boolean): Promise<void> {
  const scope = await getDeviceScope();
  const query = [
    'days=90',
    ...scope.siteIds.map((id) => `siteId=${id}`),
    ...(scope.siteIds.length ? [] : scope.organizationIds.map((id) => `organizationId=${id}`)),
  ].join('&');
  const r = await call('GET', `/api/v1/acts/device?${query}`);
  if (r.status !== 200) await setMeta('lastSyncError', `акты: ответ ${r.status}`);
  if (r.status === 200 && Array.isArray(r.data)) {
    const server = r.data as ActDetail[];
    const pendingActs = new Set((await db.outbox.toArray()).map((o) => o.actId));
    const keep = new Set(server.map((a) => a.id));
    await db.transaction('rw', db.acts, async () => {
      for (const detail of server) {
        if (pendingActs.has(detail.id)) continue;
        const stored = await db.acts.get(detail.id);
        await db.acts.put({
          ...(stored ?? {}),
          id: detail.id,
          detail,
          localOnly: false,
          savedAt: Date.now(),
        });
      }
      for (const s of await db.acts.toArray()) {
        if (!keep.has(s.id) && !s.localOnly && !pendingActs.has(s.id)) await db.acts.delete(s.id);
      }
    });
  }
  const last = (await getMeta<number>('refsAt')) ?? 0;
  const haveRefs = (await db.refs.get('organizations')) && (await db.refs.get('equipment'));
  if (!forceRefs && haveRefs && Date.now() - last < REFS_TTL) return;
  // Каждый справочник — отдельно: ошибка одного не мешает остальным; текст ошибки — в журнале синхронизации.
  const errors: string[] = [];
  const now = Date.now();
  const put = async (key: string, load: () => Promise<unknown>) => {
    try {
      await db.refs.put({ key, data: await load(), savedAt: now });
    } catch (e) {
      errors.push(`${key}: ${e instanceof Error ? e.message : String(e)}`);
    }
  };
  const one = async (url: string) => {
    const r = await call('GET', url);
    if (r.status !== 200) throw new Error(`ответ ${r.status}`);
    return r.data;
  };
  await put('equipment', () => pages('/api/v1/equipment'));
  await put('engineers', () => pages('/api/v1/engineers?active=true'));
  await put('organizations', () => pages('/api/v1/organizations'));
  await put('parts', () => pages('/api/v1/parts'));
  await put('executors', () => one('/api/v1/executors?includeInactive=false'));
  await put('units', () => one('/api/v1/units'));
  await put(`me:${currentUsername() ?? ''}`, () => one('/api/v1/engineers/me'));
  await put('sites', async () => {
    const equipment = ((await db.refs.get('equipment'))?.data ?? []) as { organizationId: string }[];
    const orgs = ((await db.refs.get('organizations'))?.data ?? []) as { id: string }[];
    const ids = [...new Set([...equipment.map((e) => e.organizationId), ...orgs.map((o) => o.id)])];
    const sites: Record<string, unknown> = {};
    for (const id of ids) {
      const r = await call('GET', `/api/v1/organizations/${id}/sites`);
      if (r.status === 200) sites[id] = r.data;
    }
    return sites;
  });
  await setMeta('refsAt', now);
  await setMeta('lastSyncError', errors.length ? errors.join('; ') : null);
}

/** Синхронизировать сейчас (по кнопке, при появлении сети, при запуске и по таймеру). */
export function syncNow(forceRefs = false): Promise<void> {
  // Без пропуска (до входа или при входе по ПИН) синхронизировать нечего; флаг «идёт» не ставим.
  if (!getAccessToken()) return Promise.resolve();
  if (running) return running;
  running = (async () => {
    network.setSyncing(true);
    try {
      if (await push()) {
        await pull(forceRefs);
        network.setOnline(true);
      }
    } catch (e) {
      network.setOnline(false);
      await setMeta('lastSyncError', e instanceof Error ? e.message : String(e)).catch(() => undefined);
    } finally {
      await setMeta('lastSyncAt', Date.now()).catch(() => undefined);
      await refreshQueueState();
      network.setSyncing(false);
      running = null;
    }
  })();
  return running;
}

/** Предупреждение, когда на ПК заканчивается место для данных приложения (раздел 17.2). */
export async function checkStorage(): Promise<{ usedMb: number; quotaMb: number; low: boolean } | undefined> {
  const estimate = await navigator.storage?.estimate?.();
  if (!estimate?.quota) return undefined;
  const usedMb = Math.round((estimate.usage ?? 0) / 1024 / 1024);
  const quotaMb = Math.round(estimate.quota / 1024 / 1024);
  const low = (estimate.usage ?? 0) / estimate.quota > 0.85;
  if (low) window.dispatchEvent(new CustomEvent('acts:storage-low', { detail: { usedMb, quotaMb } }));
  return { usedMb, quotaMb, low };
}

/** Конфликт: «оставить моё» — отправить снова от текущей серверной версии; «оставить серверное» — отбросить. */
export async function resolveConflict(seq: number, keepMine: boolean): Promise<void> {
  const op = await db.outbox.get(seq);
  if (!op) return;
  if (!keepMine) {
    await db.outbox.where('actId').equals(op.actId).delete();
  } else {
    const r = await call('GET', `/api/v1/acts/${op.actId}`);
    const version = isActDetail(r.data) ? r.data.version : undefined;
    await db.outbox.update(seq, {
      status: 'pending',
      error: undefined,
      body: version === undefined ? op.body : { ...(op.body as object), version },
    });
  }
  await syncNow();
}

export async function dropOperation(seq: number): Promise<void> {
  await db.outbox.delete(seq);
  await refreshQueueState();
}

let started = false;

/** Запуск: синхронизация при старте, при появлении сети и каждые 2 минуты. */
export function startSync(): void {
  if (started || typeof window === 'undefined') return;
  started = true;
  void navigator.storage?.persist?.();
  void checkStorage();
  // Синхронизация сразу после входа: токен появляется чуть позже, чем запускается приложение.
  onAccessToken((token) => {
    if (token) void syncNow();
  });
  window.addEventListener('online', () => void syncNow());
  // Со связью — раз в 2 минуты; без связи — каждые 15 секунд проверяем, не появилась ли она.
  let last = Date.now();
  setInterval(() => {
    const interval = network.get().online ? 120_000 : 15_000;
    if (navigator.onLine && Date.now() - last >= interval) {
      last = Date.now();
      void syncNow();
    }
  }, 5_000);
  void syncNow();
}

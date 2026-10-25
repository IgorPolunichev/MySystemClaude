import type { ActDetail } from '../api/generated/acts/model';
import { getAccessToken } from '../app/auth/tokenStore';
import { db, getMeta, setMeta, type OutboxOp } from './db';
import { network } from './network';

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
  const versions = new Map<string, number>();
  const ops = await db.outbox.where('status').equals('pending').sortBy('seq');
  for (const op of ops) {
    if (blocked.has(op.actId)) continue;
    let body = op.body;
    // Последовательные правки шапки без связи: следующая отправляется от версии, которую вернул сервер.
    if (op.method === 'PUT' && /^\/api\/v1\/acts\/[^/]+$/.test(op.url) && versions.has(op.actId)) {
      body = { ...(body as object), version: versions.get(op.actId) };
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
        versions.set(op.actId, detail.version);
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
    const items = (r.data as { items?: T[]; totalElements?: number })?.items ?? [];
    all.push(...items);
    if (items.length < 200) break;
  }
  return all;
}

/** Загрузить на устройство мои акты; справочники — раз в сутки или принудительно. */
async function pull(forceRefs: boolean): Promise<void> {
  const r = await call('GET', '/api/v1/acts/device?days=90');
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
  if (!forceRefs && Date.now() - last < REFS_TTL) return;
  const [equipment, engineers, organizations, parts, executors, units, me] = await Promise.all([
    pages('/api/v1/equipment'),
    pages('/api/v1/engineers?active=true'),
    pages('/api/v1/organizations'),
    pages('/api/v1/parts'),
    call('GET', '/api/v1/executors?includeInactive=false'),
    call('GET', '/api/v1/units'),
    call('GET', '/api/v1/engineers/me'),
  ]);
  const orgIds = [...new Set((equipment as { organizationId: string }[]).map((e) => e.organizationId))];
  const sites: Record<string, unknown> = {};
  for (const id of orgIds) {
    const s = await call('GET', `/api/v1/organizations/${id}/sites`);
    if (s.status === 200) sites[id] = s.data;
  }
  const now = Date.now();
  await db.refs.bulkPut([
    { key: 'equipment', data: equipment, savedAt: now },
    { key: 'engineers', data: engineers, savedAt: now },
    { key: 'organizations', data: organizations, savedAt: now },
    { key: 'parts', data: parts, savedAt: now },
    { key: 'executors', data: executors.status === 200 ? executors.data : [], savedAt: now },
    { key: 'units', data: units.status === 200 ? units.data : [], savedAt: now },
    { key: 'sites', data: sites, savedAt: now },
    ...(me.status === 200 ? [{ key: 'me', data: me.data, savedAt: now }] : []),
  ]);
  await setMeta('refsAt', now);
}

/** Синхронизировать сейчас (по кнопке, при появлении сети, при запуске и по таймеру). */
export function syncNow(forceRefs = false): Promise<void> {
  if (running) return running;
  running = (async () => {
    if (!getAccessToken()) return;
    network.setSyncing(true);
    try {
      if (await push()) {
        await pull(forceRefs);
        network.setOnline(true);
      }
    } catch {
      network.setOnline(false);
    } finally {
      await refreshQueueState();
      network.setSyncing(false);
      running = null;
    }
  })();
  return running;
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
  window.addEventListener('online', () => void syncNow());
  setInterval(() => {
    if (navigator.onLine) void syncNow();
  }, 120_000);
  void syncNow();
}

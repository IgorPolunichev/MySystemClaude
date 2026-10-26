import { ApiError } from '../api/http';
import type { ActDetail } from '../api/generated/acts/model';
import { db } from './db';
import { actIdOf, isQueueable, LocalNotFound, localGet, localMutate, OfflineUnavailable } from './localApi';
import { network } from './network';
import { getAccessToken } from '../app/auth/tokenStore';
import { refreshQueueState } from './sync';

/** Связка apiFetch с локальным слоем. */
const READABLE =
  /^\/api\/v1\/(acts(\/[^/]+(\/history)?|\/short-descriptions)?|equipment(\/[^/]+)?|engineers(\/me)?|executors|organizations(\/[^/]+\/sites)?|parts|units)$/;

export function canHandleLocally(method: string, path: string): boolean {
  return method === 'GET' ? READABLE.test(path) : isQueueable(method, path);
}

export async function mustHandleLocally(method: string, path: string, body: unknown): Promise<boolean> {
  if (!canHandleLocally(method, path)) return false;
  // Без пропуска (вход по ПИН без связи) — только локально. Иначе сначала пробуем сервер: в очередь — лишь при сбое.
  if (!getAccessToken()) return true;
  if (typeof navigator !== 'undefined' && !navigator.onLine) return true;
  // Связь недавно пропадала — не ждём сервер на каждом действии: работаем на ПК, связь проверяется в фоне
  // каждые 15 секунд (синхронизация), и при её появлении запросы снова идут на сервер.
  if (!network.get().online) return true;
  if (method === 'GET') return false;
  const actId = actIdOf(path, body);
  if (!actId) return false;
  const [pending, stored] = await Promise.all([
    db.outbox.where('actId').equals(actId).count(),
    db.acts.get(actId),
  ]);
  return pending > 0 || !!stored?.localOnly;
}

export async function handleLocally(method: string, target: URL, body: unknown): Promise<unknown> {
  try {
    const result =
      method === 'GET'
        ? await localGet(target.pathname, target.searchParams)
        : await localMutate(method as 'POST' | 'PUT' | 'DELETE', target.pathname, body);
    if (method !== 'GET') await refreshQueueState();
    return { data: result.data, status: result.status, headers: new Headers() };
  } catch (e) {
    if (e instanceof LocalNotFound)
      throw new ApiError(404, { title: 'Нет на устройстве', detail: 'Этот акт не загружен на устройство' });
    if (e instanceof OfflineUnavailable) throw new ApiError(503, { title: 'Нет связи', detail: e.message });
    throw new ApiError(422, { title: 'Ошибка', detail: e instanceof Error ? e.message : String(e) });
  }
}

export function isMarkedOffline(): boolean {
  return !network.get().online;
}

export function markOffline(): void {
  network.setOnline(false);
}

export function markOnline(): void {
  if (!network.get().online) {
    network.setOnline(true);
    void import('./sync').then((m) => m.syncNow());
  }
}

const isActDetail = (v: unknown): v is ActDetail =>
  !!v && typeof v === 'object' && 'permissions' in (v as object) && 'timeEntries' in (v as object);

/** Карточки актов, полученные со связью, сохраняются на устройство (если нет неотправленных правок). */
export async function remember(method: string, path: string, data: unknown): Promise<void> {
  const deleted = method === 'DELETE' ? path.match(/^\/api\/v1\/acts\/([^/]+)$/) : null;
  if (deleted) {
    await db.acts.delete(deleted[1]).catch(() => undefined);
    return;
  }
  const detail = isActDetail(data)
    ? data
    : isActDetail((data as { act?: unknown })?.act)
      ? (data as { act: ActDetail }).act
      : undefined;
  if (!detail || !/^\/api\/v1\/acts/.test(path)) return;
  if (method === 'DELETE' && /^\/api\/v1\/acts\/[^/]+$/.test(path)) return;
  try {
    if ((await db.outbox.where('actId').equals(detail.id).count()) > 0) return;
    const stored = await db.acts.get(detail.id);
    await db.acts.put({ ...(stored ?? {}), id: detail.id, detail, localOnly: false, savedAt: Date.now() });
  } catch {
    // Локальная база недоступна (приватный режим браузера) — работаем только онлайн.
  }
}

/** Файл к акту идёт на ПК (а не сразу на сервер): нет связи, акт создан без связи или у него есть неотправленные изменения. */
export async function filesGoLocal(actId: string): Promise<boolean> {
  if (!getAccessToken() || !network.get().online || (typeof navigator !== 'undefined' && !navigator.onLine))
    return true;
  const [pending, stored] = await Promise.all([
    db.outbox.where('actId').equals(actId).count(),
    db.acts.get(actId),
  ]);
  return pending > 0 || !!stored?.localOnly;
}

import { useSyncExternalStore } from 'react';
import type { Role } from '../app/auth/roles';
import { getMeta, setMeta } from './db';

/**
 * Вход без связи с сервером компании (решение 19.12 SPEC.md): после обычного входа через Keycloak пользователь
 * задаёт ПИН-код; на ПК хранится только его отпечаток (PBKDF2-SHA-256). Без связи пользователь выбирает себя и
 * вводит ПИН. Срок — 7 дней с последнего входа со связью; после 5 неверных ПИН — только вход со связью.
 * Вход по ПИН открывает лишь данные на этом ПК: изменения уйдут на сервер только после входа со связью.
 */
export const OFFLINE_DAYS = 7;
export const MAX_ATTEMPTS = 5;
const ITERATIONS = 210_000;
const DAY = 24 * 60 * 60 * 1000;

export interface OfflineProfile {
  username: string;
  fullName: string;
  roles: Role[];
  lastOnlineAt: number;
  pinHash?: string;
  salt?: string;
  failed: number;
}

type Profiles = Record<string, OfflineProfile>;

async function profiles(): Promise<Profiles> {
  return (await getMeta<Profiles>('offlineProfiles')) ?? {};
}

async function saveProfiles(p: Profiles): Promise<void> {
  await setMeta('offlineProfiles', p);
}

const toHex = (buf: ArrayBuffer) =>
  Array.from(new Uint8Array(buf), (b) => b.toString(16).padStart(2, '0')).join('');
const fromHex = (hex: string) =>
  new Uint8Array(new ArrayBuffer(hex.length / 2)).map((_, i) => parseInt(hex.slice(i * 2, i * 2 + 2), 16));

async function derive(pin: string, salt: Uint8Array<ArrayBuffer>): Promise<string> {
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(pin), 'PBKDF2', false, [
    'deriveBits',
  ]);
  const bits = await crypto.subtle.deriveBits(
    { name: 'PBKDF2', hash: 'SHA-256', salt, iterations: ITERATIONS },
    key,
    256,
  );
  return toHex(bits);
}

/** После входа со связью: запомнить пользователя на ПК и продлить срок работы без связи. */
export async function rememberOnlineLogin(
  username: string,
  fullName: string,
  roles: Role[],
): Promise<OfflineProfile> {
  const all = await profiles();
  const profile: OfflineProfile = {
    ...(all[username] ?? { failed: 0 }),
    username,
    fullName,
    roles,
    lastOnlineAt: Date.now(),
    failed: 0,
  };
  all[username] = profile;
  await saveProfiles(all);
  return profile;
}

export async function hasPin(username: string): Promise<boolean> {
  return !!(await profiles())[username]?.pinHash;
}

export function validatePin(pin: string): string | null {
  if (!/^\d{4,8}$/.test(pin)) return 'ПИН — от 4 до 8 цифр';
  if (/^(\d)\1+$/.test(pin) || '0123456789'.includes(pin) || '9876543210'.includes(pin))
    return 'Слишком простой ПИН';
  return null;
}

export async function setPin(username: string, pin: string): Promise<void> {
  const error = validatePin(pin);
  if (error) throw new Error(error);
  const all = await profiles();
  if (!all[username]) throw new Error('Сначала войдите со связью');
  const salt = crypto.getRandomValues(new Uint8Array(new ArrayBuffer(16)));
  all[username] = { ...all[username], salt: toHex(salt.buffer), pinHash: await derive(pin, salt), failed: 0 };
  await saveProfiles(all);
}

/** Пользователи, которые могут войти без связи на этом ПК. */
export async function offlineCandidates(): Promise<
  (OfflineProfile & { expired: boolean; locked: boolean })[]
> {
  const now = Date.now();
  return Object.values(await profiles())
    .filter((p) => p.pinHash)
    .map((p) => ({
      ...p,
      expired: now - p.lastOnlineAt > OFFLINE_DAYS * DAY,
      locked: p.failed >= MAX_ATTEMPTS,
    }))
    .sort((a, b) => b.lastOnlineAt - a.lastOnlineAt);
}

/** Проверка ПИН; при успехе начинается офлайн-сессия. */
export async function offlineLogin(username: string, pin: string): Promise<OfflineProfile> {
  const all = await profiles();
  const p = all[username];
  if (!p?.pinHash || !p.salt) throw new Error('Для этого пользователя ПИН не задан — войдите со связью');
  if (Date.now() - p.lastOnlineAt > OFFLINE_DAYS * DAY) {
    throw new Error(`Прошло больше ${OFFLINE_DAYS} дней с последнего входа со связью — войдите со связью`);
  }
  if (p.failed >= MAX_ATTEMPTS)
    throw new Error('ПИН заблокирован после неверных попыток — войдите со связью');
  if ((await derive(pin, fromHex(p.salt))) !== p.pinHash) {
    all[username] = { ...p, failed: p.failed + 1 };
    await saveProfiles(all);
    const left = MAX_ATTEMPTS - p.failed - 1;
    throw new Error(
      left > 0 ? `Неверный ПИН. Осталось попыток: ${left}` : 'ПИН заблокирован — войдите со связью',
    );
  }
  all[username] = { ...p, failed: 0 };
  await saveProfiles(all);
  startOfflineSession(p);
  return p;
}

// ---------- Офлайн-сессия (до закрытия окна) ----------

const KEY = 'acts.offlineSession';
let session: OfflineProfile | null = (() => {
  try {
    const raw = sessionStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as OfflineProfile) : null;
  } catch {
    return null;
  }
})();
const listeners = new Set<() => void>();

function startOfflineSession(p: OfflineProfile) {
  session = { ...p, pinHash: undefined, salt: undefined };
  sessionStorage.setItem(KEY, JSON.stringify(session));
  listeners.forEach((l) => l());
}

export function endOfflineSession(): void {
  session = null;
  sessionStorage.removeItem(KEY);
  listeners.forEach((l) => l());
}

export function getOfflineSession(): OfflineProfile | null {
  return session;
}

export function useOfflineSession(): OfflineProfile | null {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l);
      return () => listeners.delete(l);
    },
    () => session,
    () => session,
  );
}

/** Доступен ли сервер входа (Keycloak). Без него переход на страницу входа закончится ошибкой браузера. */
export async function authServerReachable(authority: string, timeoutMs = 4000): Promise<boolean> {
  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    const r = await fetch(`${authority}/.well-known/openid-configuration`, {
      cache: 'no-store',
      signal: controller.signal,
    });
    clearTimeout(timer);
    return r.ok;
  } catch {
    return false;
  }
}

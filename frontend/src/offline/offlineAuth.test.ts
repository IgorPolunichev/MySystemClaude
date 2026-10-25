import 'fake-indexeddb/auto';
import { beforeEach, describe, expect, it } from 'vitest';
import { db } from './db';
import {
  getOfflineSession,
  offlineCandidates,
  offlineLogin,
  rememberOnlineLogin,
  setPin,
  validatePin,
} from './offlineAuth';

describe('вход без связи по ПИН', () => {
  beforeEach(async () => {
    await db.delete();
    await db.open();
  });

  it('ПИН задаётся после входа со связью, хранится отпечаток, ошибки считаются', async () => {
    expect(validatePin('12')).toMatch(/от 4/);
    expect(validatePin('1111')).toMatch(/простой/);
    expect(validatePin('1234')).toMatch(/простой/);
    expect(validatePin('4719')).toBeNull();
    await expect(setPin('engineer', '4719')).rejects.toThrow(/со связью/);

    await rememberOnlineLogin('engineer', 'Петров И. С.', ['ENGINEER']);
    await setPin('engineer', '4719');
    const stored = JSON.stringify(await db.meta.get('offlineProfiles'));
    expect(stored).not.toContain('4719');

    expect((await offlineCandidates()).map((p) => p.username)).toEqual(['engineer']);
    await expect(offlineLogin('engineer', '0000')).rejects.toThrow(/Осталось попыток: 4/);
    const p = await offlineLogin('engineer', '4719');
    expect(p.fullName).toBe('Петров И. С.');
    expect(getOfflineSession()?.username).toBe('engineer');
    expect(getOfflineSession()?.pinHash).toBeUndefined();
  });

  it('срок 7 дней и блокировка после 5 ошибок', async () => {
    await rememberOnlineLogin('head', 'Смирнов А. В.', ['DEPARTMENT_HEAD']);
    await setPin('head', '5823');
    for (let i = 0; i < 5; i++) await offlineLogin('head', '0001').catch(() => undefined);
    await expect(offlineLogin('head', '5823')).rejects.toThrow(/заблокирован/);
    await rememberOnlineLogin('head', 'Смирнов А. В.', ['DEPARTMENT_HEAD']);
    const all = (await db.meta.get('offlineProfiles'))!.value as Record<string, { lastOnlineAt: number }>;
    all.head.lastOnlineAt = Date.now() - 8 * 24 * 60 * 60 * 1000;
    await db.meta.put({ key: 'offlineProfiles', value: all });
    await expect(offlineLogin('head', '5823')).rejects.toThrow(/7 дней/);
  });
});

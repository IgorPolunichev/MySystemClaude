import { describe, expect, it } from 'vitest';
import { modules, visibleModules } from './moduleRegistry';
import { rolesFromAccessToken } from './auth/roles';

function token(payload: object): string {
  const b64 = btoa(JSON.stringify(payload)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `header.${b64}.signature`;
}

describe('moduleRegistry', () => {
  it('скрывает администрирование от инженера', () => {
    const keys = visibleModules(modules, ['ENGINEER']).map((m) => m.key);
    expect(keys).toContain('acts');
    expect(keys).not.toContain('admin');
  });

  it('показывает администрирование администратору', () => {
    expect(visibleModules(modules, ['ADMIN']).map((m) => m.key)).toContain('admin');
  });

  it('у каждого модуля уникальный путь', () => {
    const paths = modules.map((m) => m.path);
    expect(new Set(paths).size).toBe(paths.length);
  });
});

describe('rolesFromAccessToken', () => {
  it('берёт только известные роли из realm_access', () => {
    const t = token({ realm_access: { roles: ['ENGINEER', 'offline_access', 'ADMIN'] } });
    expect(rolesFromAccessToken(t)).toEqual(['ENGINEER', 'ADMIN']);
  });

  it('возвращает пустой список для пустого или испорченного токена', () => {
    expect(rolesFromAccessToken(undefined)).toEqual([]);
    expect(rolesFromAccessToken('not-a-token')).toEqual([]);
  });
});

export const ROLES = ['ENGINEER', 'DEPARTMENT_HEAD', 'ADMIN'] as const;
export type Role = (typeof ROLES)[number];

/**
 * Роли берутся из access-токена Keycloak (realm_access.roles) только для отображения интерфейса.
 * Права всегда проверяет сервер.
 */
export function rolesFromAccessToken(token: string | undefined): Role[] {
  if (!token) return [];
  const payload = decodeJwtPayload(token);
  const realmAccess = payload?.realm_access as { roles?: unknown } | undefined;
  const roles = Array.isArray(realmAccess?.roles) ? realmAccess.roles : [];
  return roles.filter((r): r is Role => (ROLES as readonly string[]).includes(String(r)));
}

function decodeJwtPayload(token: string): Record<string, unknown> | undefined {
  const part = token.split('.')[1];
  if (!part) return undefined;
  try {
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    );
    return JSON.parse(json) as Record<string, unknown>;
  } catch {
    return undefined;
  }
}

import { useMemo } from 'react';
import { useAuth } from 'react-oidc-context';
import { rolesFromAccessToken, type Role } from '../../app/auth/roles';
import { useOfflineSession } from '../../offline/offlineAuth';

/**
 * Роли текущего пользователя для показа кнопок (раздел 11 SPEC.md). Сервер проверяет права сам —
 * скрытая кнопка лишь избавляет пользователя от заведомо запрещённого действия.
 */
export function useRoles() {
  const auth = useAuth();
  const offline = useOfflineSession();
  return useMemo(() => {
    const roles: Role[] = auth.user ? rolesFromAccessToken(auth.user.access_token) : (offline?.roles ?? []);
    const isAdmin = roles.includes('ADMIN');
    return {
      roles,
      isAdmin,
      /** Изменять и удалять записи справочников, импортировать. */
      canEdit: isAdmin || roles.includes('DEPARTMENT_HEAD'),
    };
  }, [auth.user, offline]);
}

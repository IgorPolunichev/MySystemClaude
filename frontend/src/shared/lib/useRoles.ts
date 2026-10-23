import { useMemo } from 'react';
import { useAuth } from 'react-oidc-context';
import { rolesFromAccessToken, type Role } from '../../app/auth/roles';

/**
 * Роли текущего пользователя для показа кнопок (раздел 11 SPEC.md). Сервер проверяет права сам —
 * скрытая кнопка лишь избавляет пользователя от заведомо запрещённого действия.
 */
export function useRoles() {
  const auth = useAuth();
  return useMemo(() => {
    const roles: Role[] = rolesFromAccessToken(auth.user?.access_token);
    const isAdmin = roles.includes('ADMIN');
    return {
      roles,
      isAdmin,
      /** Изменять и удалять записи справочников, импортировать. */
      canEdit: isAdmin || roles.includes('DEPARTMENT_HEAD'),
    };
  }, [auth.user]);
}

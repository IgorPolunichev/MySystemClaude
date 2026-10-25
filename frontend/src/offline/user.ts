import { getAccessToken } from '../app/auth/tokenStore';
import { getOfflineSession } from './offlineAuth';

/** Логин текущего пользователя из токена (без проверки подписи — только для разметки локальных данных). */
export function currentUsername(): string | undefined {
  const token = getAccessToken();
  if (!token) return getOfflineSession()?.username;
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = JSON.parse(decodeURIComponent(escape(atob(payload)))) as {
      preferred_username?: string;
      sub?: string;
    };
    return json.preferred_username ?? json.sub;
  } catch {
    return undefined;
  }
}

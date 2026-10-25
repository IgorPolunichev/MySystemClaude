// Текущий access-токен для HTTP-клиента. Обновляется провайдером авторизации.
let accessToken: string | undefined;
const listeners = new Set<(token: string | undefined) => void>();

export function setAccessToken(token: string | undefined): void {
  const changed = token !== accessToken;
  accessToken = token;
  if (changed) listeners.forEach((l) => l(token));
}

export function getAccessToken(): string | undefined {
  return accessToken;
}

/** Подписка на появление и смену токена (офлайн-синхронизация стартует сразу после входа). */
export function onAccessToken(listener: (token: string | undefined) => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

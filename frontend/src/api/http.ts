import { getAccessToken } from '../app/auth/tokenStore';

/** Ошибка API с телом в формате RFC 7807. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly problem?: { title?: string; detail?: string },
  ) {
    super(problem?.detail ?? problem?.title ?? `Ошибка ${status}`);
  }
}

/**
 * Единственная точка обращения к серверу: добавляет токен и разбирает ошибки.
 * Используется сгенерированным клиентом Orval; напрямую вызывать не нужно.
 */
export async function apiFetch<T>(url: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  const token = getAccessToken();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  headers.set('Accept', 'application/json, application/problem+json');

  const response = await fetch(url, { ...options, headers });
  const body = await response.text();
  const parsed = body ? safeJson(body) : undefined;

  if (!response.ok) {
    throw new ApiError(response.status, parsed as { title?: string; detail?: string } | undefined);
  }
  return { data: parsed, status: response.status, headers: response.headers } as T;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

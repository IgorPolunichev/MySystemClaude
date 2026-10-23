import { getAccessToken } from '../app/auth/tokenStore';

/** Тело ошибки RFC 7807 от сервера. */
export interface ProblemBody {
  title?: string;
  detail?: string;
  /** Ошибки по полям формы: имя поля → сообщение. */
  errors?: Record<string, string>;
}

/** Ошибка API с телом в формате RFC 7807. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly problem?: ProblemBody,
  ) {
    super(problem?.detail ?? problem?.title ?? `Ошибка ${status}`);
  }
}

function authHeaders(init?: HeadersInit): Headers {
  const headers = new Headers(init);
  const token = getAccessToken();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  return headers;
}

/**
 * Единственная точка обращения к серверу: добавляет токен и разбирает ошибки.
 * Используется сгенерированным клиентом Orval; напрямую вызывать не нужно.
 */
export async function apiFetch<T>(url: string, options: RequestInit = {}): Promise<T> {
  const headers = authHeaders(options.headers);
  headers.set('Accept', 'application/json, application/problem+json');

  const response = await fetch(url, { ...options, headers });
  const body = await response.text();
  const parsed = body ? safeJson(body) : undefined;

  if (!response.ok) {
    throw new ApiError(response.status, typeof parsed === 'object' ? (parsed as ProblemBody) : undefined);
  }
  return { data: parsed, status: response.status, headers: response.headers } as T;
}

/** Скачивание файла (шаблон импорта, выгрузка) с токеном: браузер сохраняет его под именем из ответа. */
export async function downloadFile(url: string, fallbackName: string): Promise<void> {
  const response = await fetch(url, { headers: authHeaders() });
  if (!response.ok) {
    throw new ApiError(response.status);
  }
  const blob = await response.blob();
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1];
  const name = encoded ? decodeURIComponent(encoded) : fallbackName;
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = name;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(link.href), 1000);
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

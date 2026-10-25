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
 *
 * Офлайн (раздел 17 SPEC.md): без связи чтение актов и справочников идёт из локальной базы, а изменения актов
 * применяются на устройстве и встают в очередь. Изменения акта с неотправленными операциями тоже идут в очередь,
 * чтобы сохранить порядок. Со связью полученные карточки актов сохраняются на устройство.
 */
export async function apiFetch<T>(url: string, options: RequestInit = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase();
  const target = new URL(url, window.location.origin);
  const body = typeof options.body === 'string' ? safeJson(options.body) : undefined;
  const offline = await import('../offline/gateway');

  if (await offline.mustHandleLocally(method, target.pathname, body)) {
    return (await offline.handleLocally(method, target, body)) as T;
  }

  const headers = authHeaders(options.headers);
  headers.set('Accept', 'application/json, application/problem+json');
  let response: Response;
  try {
    response = await fetch(url, { ...options, headers });
  } catch (e) {
    if (offline.canHandleLocally(method, target.pathname)) {
      offline.markOffline();
      return (await offline.handleLocally(method, target, body)) as T;
    }
    throw new ApiError(0, {
      title: 'Нет связи с сервером',
      detail: e instanceof Error ? e.message : undefined,
    });
  }
  if ([502, 503, 504].includes(response.status) && offline.canHandleLocally(method, target.pathname)) {
    offline.markOffline();
    return (await offline.handleLocally(method, target, body)) as T;
  }
  const text = await response.text();
  const parsed = text ? safeJson(text) : undefined;

  if (!response.ok) {
    throw new ApiError(response.status, typeof parsed === 'object' ? (parsed as ProblemBody) : undefined);
  }
  offline.markOnline();
  void offline.remember(method, target.pathname, parsed);
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

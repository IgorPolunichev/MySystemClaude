/**
 * Данные успешного ответа сгенерированного клиента. Типы ответов Orval — объединение по статусам
 * (200 | 404 …); ошибки apiFetch бросает исключением, поэтому здесь остаётся только успешная ветка.
 */
type Success<R> = Extract<R, { status: 200 | 201 }>;

export function ok<R extends { status: number; data: unknown }>(
  res: R | undefined,
): Success<R>['data'] | undefined {
  if (!res || (res.status !== 200 && res.status !== 201)) return undefined;
  return (res as Success<R>).data;
}

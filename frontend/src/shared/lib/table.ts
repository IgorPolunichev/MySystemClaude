import type { SorterResult } from 'antd/es/table/interface';

/** Параметр сортировки API: «name» по возрастанию, «-name» по убыванию. */
export function sortParam<T>(sorter: SorterResult<T> | SorterResult<T>[]): string | undefined {
  const s = Array.isArray(sorter) ? sorter[0] : sorter;
  if (!s?.order || !s.columnKey) return undefined;
  return (s.order === 'descend' ? '-' : '') + String(s.columnKey);
}

/** Пустую строку формы — в null, чтобы сервер очистил поле. */
export function emptyToNull<T extends object>(values: T): T {
  const result: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(values)) {
    result[key] = typeof value === 'string' && value.trim() === '' ? null : value;
  }
  return result as T;
}

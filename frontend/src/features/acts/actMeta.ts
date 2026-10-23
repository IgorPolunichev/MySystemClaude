import type { ActStatus } from '../../api/generated/acts/model';

/** Подписи и цвета статусов акта. */
export const STATUS: Record<ActStatus, { label: string; color: string }> = {
  DRAFT: { label: 'Черновик', color: 'default' },
  OPEN: { label: 'Открыт', color: 'processing' },
  CLOSED: { label: 'Закрыт', color: 'success' },
  CANCELLED: { label: 'Аннулирован', color: 'error' },
};

export const HISTORY_ACTIONS: Record<string, string> = {
  CREATED: 'Создан черновик',
  UPDATED: 'Изменена шапка',
  UPDATED_AFTER_CLOSE: 'Изменён после закрытия',
  OPENED: 'Открыт, присвоен номер',
  CLOSED: 'Закрыт',
  REOPENED: 'Возвращён в работу',
  CANCELLED: 'Аннулирован',
  COPIED: 'Создан на основе акта',
  ENGINEER_ADDED: 'Добавлен инженер',
  ENGINEER_REMOVED: 'Убран инженер',
  PART_ADDED: 'Добавлена запчасть',
  PART_REMOVED: 'Удалена запчасть',
  PHOTO_ADDED: 'Добавлено фото',
  PHOTO_REMOVED: 'Удалено фото',
};

const dateFmt = new Intl.DateTimeFormat('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric' });
const hoursFmt = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 });

/** «05.10.2026» из ISO-даты. */
export function formatDate(iso?: string | null): string {
  return iso ? dateFmt.format(new Date(iso.length === 10 ? `${iso}T00:00:00` : iso)) : '—';
}

export function formatPeriod(start?: string | null, end?: string | null): string {
  if (!start && !end) return '—';
  if (start === end || !end) return formatDate(start);
  return `${formatDate(start)} — ${formatDate(end)}`;
}

export function formatHours(value?: number | null): string {
  return value == null ? '—' : `${hoursFmt.format(value)} ч`;
}

/** Локальная дата в формате ISO (YYYY-MM-DD) без сдвига часового пояса. */
export function isoDate(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Понедельник недели, в которую входит дата. */
export function weekStart(d: Date): Date {
  const result = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  const shift = (result.getDay() + 6) % 7;
  result.setDate(result.getDate() - shift);
  return result;
}

export function addDays(d: Date, days: number): Date {
  const result = new Date(d);
  result.setDate(result.getDate() + days);
  return result;
}

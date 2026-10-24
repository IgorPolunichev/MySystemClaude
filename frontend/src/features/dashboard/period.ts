import dayjs, { type Dayjs } from 'dayjs';

export type PeriodKind = 'month' | 'quarter' | 'year' | 'range';

/** Границы периода для дашборда (включительно) в формате ГГГГ-ММ-ДД. */
export function periodBounds(
  kind: PeriodKind,
  anchor: Dayjs,
  range?: [Dayjs, Dayjs] | null,
): { from: string; to: string } {
  const fmt = (d: Dayjs) => d.format('YYYY-MM-DD');
  if (kind === 'range' && range) return { from: fmt(range[0]), to: fmt(range[1]) };
  if (kind === 'quarter') {
    const startMonth = Math.floor(anchor.month() / 3) * 3;
    const start = anchor.month(startMonth).startOf('month');
    return { from: fmt(start), to: fmt(start.add(2, 'month').endOf('month')) };
  }
  if (kind === 'year') return { from: fmt(anchor.startOf('year')), to: fmt(anchor.endOf('year')) };
  return { from: fmt(anchor.startOf('month')), to: fmt(anchor.endOf('month')) };
}

/** Цвет индикатора по порогам: до red — красный, до yellow — жёлтый, дальше — зелёный. */
export function levelColor(percent: number, red: number, yellow: number): string {
  if (percent < red) return '#c0392b';
  if (percent < yellow) return '#d99a1e';
  return '#2e8b57';
}

export function defaultAnchor(): Dayjs {
  return dayjs();
}

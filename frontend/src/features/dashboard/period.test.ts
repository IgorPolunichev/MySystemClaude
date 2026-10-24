import { describe, expect, it } from 'vitest';
import dayjs from 'dayjs';
import { levelColor, periodBounds } from './period';

describe('период дашборда', () => {
  const d = dayjs('2026-11-14');
  it('месяц, квартал, год', () => {
    expect(periodBounds('month', d)).toEqual({ from: '2026-11-01', to: '2026-11-30' });
    expect(periodBounds('quarter', d)).toEqual({ from: '2026-10-01', to: '2026-12-31' });
    expect(periodBounds('year', d)).toEqual({ from: '2026-01-01', to: '2026-12-31' });
    expect(periodBounds('range', d, [dayjs('2026-10-15'), dayjs('2026-11-14')])).toEqual({
      from: '2026-10-15',
      to: '2026-11-14',
    });
  });
  it('цвета по порогам', () => {
    expect(levelColor(69, 70, 90)).toBe('#c0392b');
    expect(levelColor(70, 70, 90)).toBe('#d99a1e');
    expect(levelColor(90, 70, 90)).toBe('#2e8b57');
  });
});

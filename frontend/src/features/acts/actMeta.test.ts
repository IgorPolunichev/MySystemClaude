import { describe, expect, it } from 'vitest';
import { addDays, formatHours, formatPeriod, isoDate, weekStart } from './actMeta';

describe('даты табеля', () => {
  it('неделя начинается с понедельника', () => {
    expect(isoDate(weekStart(new Date(2026, 9, 7)))).toBe('2026-10-05'); // среда → понедельник
    expect(isoDate(weekStart(new Date(2026, 9, 11)))).toBe('2026-10-05'); // воскресенье → тот же понедельник
    expect(isoDate(weekStart(new Date(2026, 9, 5)))).toBe('2026-10-05');
  });

  it('isoDate не сдвигает дату часовым поясом', () => {
    expect(isoDate(new Date(2026, 0, 1, 23, 30))).toBe('2026-01-01');
    expect(isoDate(addDays(new Date(2026, 1, 28), 1))).toBe('2026-03-01');
  });

  it('форматирует период и часы по-русски', () => {
    expect(formatPeriod('2026-10-05', '2026-10-07')).toBe('05.10.2026 — 07.10.2026');
    expect(formatPeriod('2026-10-05', '2026-10-05')).toBe('05.10.2026');
    expect(formatPeriod(null, null)).toBe('—');
    expect(formatHours(38.75)).toBe('38,75 ч');
  });
});

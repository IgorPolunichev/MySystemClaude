import { describe, expect, it } from 'vitest';
import { calculateWorkTime, formatDuration } from './workTime';

const calc = (work: string, ...breaks: string[]) => {
  const [start, end] = work.split('-');
  return calculateWorkTime(
    { start, end },
    breaks.map((b) => {
      const [s, e] = b.split('-');
      return { start: s, end: e };
    }),
  );
};

describe('продуктивное время = интервал − перерывы (как на сервере)', () => {
  it('пример из ТЗ: 10:00–13:00, обед 11:00–12:00 → 2 ч', () => {
    expect(calc('10:00-13:00', '11:00-12:00').minutes).toBe(120);
  });
  it('рабочий день с двумя перерывами', () => {
    expect(calc('08:00-17:00', '12:00-13:00', '15:00-15:30').minutes).toBe(450);
  });
  it('ночная смена и перерыв через полночь', () => {
    expect(calc('20:00-08:00', '23:45-00:15').minutes).toBe(690);
  });
  it('окончание = начало — ровно сутки', () => {
    expect(calc('09:00-09:00').minutes).toBe(1440);
  });
  it('ошибки как на сервере', () => {
    expect(calc('10:00-13:00', '13:30-14:00').error).toContain('выходит за интервал');
    expect(calc('08:00-17:00', '12:00-13:00', '12:30-13:30').error).toContain('пересекаться');
    expect(calc('10:00-11:00', '10:00-11:00').error).toContain('всё время');
  });
  it('форматирует длительность', () => {
    expect(formatDuration(140)).toBe('2 ч 20 мин');
    expect(formatDuration(120)).toBe('2 ч');
    expect(formatDuration(45)).toBe('45 мин');
  });
});

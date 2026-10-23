/**
 * Расчёт продуктивного времени — то же правило, что на сервере (TimeEntry в act-service):
 * интервал работы минус перерывы; окончание не позже начала — работа до следующего дня.
 * Клиент считает только для подсказки, источник правды — сервер.
 */
export interface Interval {
  start: string; // ЧЧ:ММ
  end: string;
}

export interface WorkTimeResult {
  minutes: number;
  spanMinutes: number;
  breakMinutes: number;
  error?: string;
}

const DAY = 24 * 60;

export function minuteOfDay(time: string): number {
  const [h, m] = time.split(':').map(Number);
  return h * 60 + m;
}

function offset(start: string, t: string): number {
  return (((minuteOfDay(t) - minuteOfDay(start)) % DAY) + DAY) % DAY;
}

export function spanMinutes(start: string, end: string): number {
  const diff = offset(start, end);
  return diff === 0 ? DAY : diff;
}

export function calculateWorkTime(work: Interval, breaks: Interval[]): WorkTimeResult {
  const span = spanMinutes(work.start, work.end);
  const ranges: [number, number][] = [];
  for (const b of breaks) {
    const from = offset(work.start, b.start);
    let to = offset(work.start, b.end);
    if (to === 0 && from > 0) to = span === DAY ? DAY : 0;
    if (to <= from)
      return {
        minutes: 0,
        spanMinutes: span,
        breakMinutes: 0,
        error: `Перерыв ${b.start}–${b.end} должен заканчиваться позже, чем начинается`,
      };
    if (to > span)
      return {
        minutes: 0,
        spanMinutes: span,
        breakMinutes: 0,
        error: `Перерыв ${b.start}–${b.end} выходит за интервал работы`,
      };
    ranges.push([from, to]);
  }
  ranges.sort((a, b) => a[0] - b[0]);
  let breakMinutes = 0;
  for (let i = 0; i < ranges.length; i++) {
    if (i > 0 && ranges[i][0] < ranges[i - 1][1]) {
      return { minutes: 0, spanMinutes: span, breakMinutes: 0, error: 'Перерывы не должны пересекаться' };
    }
    breakMinutes += ranges[i][1] - ranges[i][0];
  }
  if (breakMinutes >= span) {
    return { minutes: 0, spanMinutes: span, breakMinutes, error: 'Перерывы занимают всё время работы' };
  }
  return { minutes: span - breakMinutes, spanMinutes: span, breakMinutes };
}

/** «2 ч», «2 ч 20 мин», «45 мин». */
export function formatDuration(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h && m) return `${h} ч ${m} мин`;
  if (h) return `${h} ч`;
  return `${m} мин`;
}

export function crossesMidnight(work: Interval): boolean {
  return minuteOfDay(work.end) <= minuteOfDay(work.start);
}

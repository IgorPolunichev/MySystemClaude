import { describe, expect, it } from 'vitest';
import { isDeviation } from './layout';

describe('отклонение от нормы', () => {
  it('ниже жёлтого порога или выше 100 % — отклонение; без нормы — нет', () => {
    expect(isDeviation(60, 168, 90)).toBe(true);
    expect(isDeviation(95, 168, 90)).toBe(false);
    expect(isDeviation(112, 168, 90)).toBe(true);
    expect(isDeviation(0, null, 90)).toBe(false);
  });
});

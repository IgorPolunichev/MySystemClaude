import { describe, expect, it } from 'vitest';
import { highlightParts } from './savedFilters';

describe('highlightParts', () => {
  it('выделяет совпадения из ts_headline', () => {
    expect(highlightParts('Замена [[форсунок]] и [[форсунки]] насоса')).toEqual([
      { text: 'Замена ', hit: false },
      { text: 'форсунок', hit: true },
      { text: ' и ', hit: false },
      { text: 'форсунки', hit: true },
      { text: ' насоса', hit: false },
    ]);
    expect(highlightParts('без совпадений')).toEqual([{ text: 'без совпадений', hit: false }]);
  });
});

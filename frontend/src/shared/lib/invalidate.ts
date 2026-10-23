import type { QueryClient } from '@tanstack/react-query';

/** Сбросить кэш всех запросов, чей адрес начинается с одного из префиксов (после изменения данных). */
export function invalidateByPrefix(client: QueryClient, ...prefixes: string[]): Promise<void> {
  return client.invalidateQueries({
    predicate: (query) => {
      const key = query.queryKey[0];
      return typeof key === 'string' && prefixes.some((p) => key.startsWith(p));
    },
  });
}

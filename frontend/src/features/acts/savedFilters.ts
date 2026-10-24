/** Сохранённые наборы фильтров реестра — в браузере пользователя (раздел 14 SPEC.md). */
export interface RegistryFilters {
  q?: string;
  mine?: boolean;
  statuses?: string[];
  organizationId?: string;
  siteId?: string;
  engineerId?: string;
  partNumber?: string;
  shortDescription?: string;
  signedScan?: boolean;
  from?: string;
  to?: string;
}

export interface SavedFilter {
  name: string;
  filters: RegistryFilters;
}

const KEY = 'acts.registry.savedFilters';

export function loadSavedFilters(): SavedFilter[] {
  try {
    const raw = localStorage.getItem(KEY);
    const parsed: unknown = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? (parsed as SavedFilter[]) : [];
  } catch {
    return [];
  }
}

export function storeSavedFilters(list: SavedFilter[]): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(list));
  } catch {
    // Хранилище браузера недоступно — наборы просто не сохранятся.
  }
}

/** Разбивает фрагмент подсветки сервера («[[совпадение]]») на части для вывода. */
export function highlightParts(text: string): { text: string; hit: boolean }[] {
  const parts: { text: string; hit: boolean }[] = [];
  const re = /\[\[(.*?)\]\]/g;
  let last = 0;
  let m: RegExpExecArray | null;
  while ((m = re.exec(text)) !== null) {
    if (m.index > last) parts.push({ text: text.slice(last, m.index), hit: false });
    parts.push({ text: m[1], hit: true });
    last = m.index + m[0].length;
  }
  if (last < text.length) parts.push({ text: text.slice(last), hit: false });
  return parts;
}

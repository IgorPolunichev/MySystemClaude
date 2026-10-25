/** Какие блоки дашборда показывать — настройка пользователя, хранится в браузере. */
export interface DashboardLayout {
  departments: boolean;
  engineers: boolean;
  compare: boolean;
  daily: boolean;
  table: boolean;
  /** Показывать только инженеров с отклонением: ниже жёлтого порога или выше нормы. */
  onlyDeviations: boolean;
}

export const DEFAULT_LAYOUT: DashboardLayout = {
  departments: true,
  engineers: true,
  compare: true,
  daily: true,
  table: true,
  onlyDeviations: false,
};

const KEY = 'acts.dashboard.layout';

export function loadLayout(): DashboardLayout {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? { ...DEFAULT_LAYOUT, ...(JSON.parse(raw) as Partial<DashboardLayout>) } : DEFAULT_LAYOUT;
  } catch {
    return DEFAULT_LAYOUT;
  }
}

export function saveLayout(layout: DashboardLayout): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(layout));
  } catch {
    // Хранилище браузера недоступно — настройка действует до перезагрузки.
  }
}

/** Отклонение: норма задана и процент ниже жёлтого порога или выше 100 %. */
export function isDeviation(percent: number, normHours: number | null | undefined, yellow: number): boolean {
  return !!normHours && (percent < yellow || percent > 100);
}

/** Сколько индикаторов инженеров показывать сразу; остальные — по кнопке «Показать всех». */
export const GAUGES_COLLAPSED = 12;

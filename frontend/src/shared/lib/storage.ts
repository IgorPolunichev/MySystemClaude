/** Безопасная работа с localStorage: в приватном режиме и при переполнении хранилище может быть недоступно. */
export function readSetting(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function writeSetting(key: string, value: string): void {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    // Настройка не сохранится между сеансами — это не мешает работе.
  }
}

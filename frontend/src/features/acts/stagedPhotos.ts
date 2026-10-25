/** Фото перед добавлением в акт: файл, превью и подпись. */
export interface StagedPhoto {
  key: string;
  file: File;
  url: string;
  caption: string;
}

export function stage(files: File[]): StagedPhoto[] {
  return files.map((file, i) => ({
    key: `${Date.now()}-${i}-${file.name}`,
    file,
    url: URL.createObjectURL(file),
    caption: '',
  }));
}

/** Освободить превью (blob:) — когда фото убрано, окно закрыто или фото добавлены. */
export function release(items: StagedPhoto[]): void {
  items.forEach((i) => URL.revokeObjectURL(i.url));
}

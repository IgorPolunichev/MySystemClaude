import type { ActDetail } from '../../api/generated/acts/model';
import { db } from '../db';
import { buildActWorkbook, type PrintPhoto } from './actWorkbook';

/** Excel акта на ПК без связи: фото — оригиналы или превью, сохранённые на ПК. */
export async function downloadActOffline(act: ActDetail): Promise<{ photos: number; missing: number }> {
  const sorted = [...act.photos].sort((a, b) => a.position - b.position);
  const photos: PrintPhoto[] = [];
  for (let i = 0; i < sorted.length; i++) {
    const p = sorted[i];
    const file =
      (await db.files.get(`${p.mediaId}:original`)) ?? (await db.files.get(`${p.mediaId}:thumbnail`));
    if (!file) continue;
    try {
      const bitmap = await createImageBitmap(file.blob);
      photos.push({
        number: i + 1,
        caption: p.caption ?? null,
        data: await file.blob.arrayBuffer(),
        extension: file.blob.type === 'image/png' ? 'png' : 'jpeg',
        width: bitmap.width,
        height: bitmap.height,
      });
      bitmap.close();
    } catch {
      // Повреждённый файл на ПК — пропускаем фото, остальное печатаем.
    }
  }
  const buffer = await buildActWorkbook(act, photos);
  const blob = new Blob([buffer], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${act.number ?? 'Черновик'}.xlsx`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 30_000);
  return { photos: photos.length, missing: sorted.length - photos.length };
}

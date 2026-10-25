import type { ActAttachment, ActDetail, ActPhoto, AttachmentKind } from '../api/generated/acts/model';
import { db, type OutboxOp } from './db';
import { refreshQueueState } from './sync';
import { currentUsername } from './user';
import { uuidv7 } from './uuid';

/**
 * Фото и документы без связи (часть В, раздел 17 и 19.11 SPEC.md): файл хранится на ПК (`local:{uuid}`), в акте
 * сразу виден с отметкой «на ПК», при синхронизации загружается в хранилище и привязывается к акту.
 */
export const LOCAL_PREFIX = 'local:';
export const isLocalMedia = (mediaId: string | null | undefined): boolean =>
  !!mediaId?.startsWith(LOCAL_PREFIX);

/** Тело операции загрузки файла. */
export interface UploadBody {
  target: 'photo' | 'attachment';
  localMediaId: string;
  localItemId: string;
  fileName: string;
  contentType: string;
  caption?: string | null;
  kind?: AttachmentKind;
  comment?: string | null;
}

/** Превью 400 px для показа и печати; если браузер не может обработать картинку — превью не создаётся. */
async function thumbnail(blob: Blob, maxSide = 400): Promise<Blob | null> {
  try {
    const bitmap = await createImageBitmap(blob);
    const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(bitmap.width * scale));
    canvas.height = Math.max(1, Math.round(bitmap.height * scale));
    canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    bitmap.close();
    return await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.82));
  } catch {
    return null;
  }
}

async function enqueueUpload(actId: string, body: UploadBody, label: string): Promise<void> {
  const op: OutboxOp = {
    opId: uuidv7(),
    actId,
    method: 'UPLOAD',
    url: body.target === 'photo' ? `/api/v1/acts/${actId}/photos` : `/api/v1/acts/${actId}/attachments`,
    body,
    label,
    createdAt: Date.now(),
    status: 'pending',
    attempts: 0,
    user: currentUsername(),
  };
  await db.outbox.add(op);
}

async function updateAct(actId: string, change: (a: ActDetail) => ActDetail): Promise<ActDetail> {
  const stored = await db.acts.get(actId);
  if (!stored) throw new Error('Акт не сохранён на этом ПК');
  const detail = change(stored.detail);
  await db.acts.put({ ...stored, detail, savedAt: Date.now() });
  return detail;
}

/** Фото на ПК: уже подготовленный (сжатый) файл. */
export async function queueLocalPhoto(
  actId: string,
  file: Blob,
  fileName: string,
  caption: string | null,
): Promise<ActPhoto> {
  const localMediaId = `${LOCAL_PREFIX}${uuidv7()}`;
  const photoId = `local-photo:${uuidv7()}`;
  const now = Date.now();
  await db.files.put({ key: `${localMediaId}:original`, blob: file, savedAt: now });
  const thumb = await thumbnail(file);
  await db.files.put({ key: `${localMediaId}:thumbnail`, blob: thumb ?? file, savedAt: now });
  let photo: ActPhoto | undefined;
  await updateAct(actId, (a) => {
    photo = { id: photoId, mediaId: localMediaId, caption, position: a.photos.length + 1 };
    return { ...a, photos: [...a.photos, photo] };
  });
  await enqueueUpload(
    actId,
    {
      target: 'photo',
      localMediaId,
      localItemId: photoId,
      fileName,
      contentType: file.type || 'image/jpeg',
      caption,
    },
    'Фото',
  );
  await refreshQueueState();
  return photo!;
}

/** Документ на ПК (скан подписанного акта и др.). */
export async function queueLocalAttachment(
  actId: string,
  file: File,
  kind: AttachmentKind,
  comment: string | null,
): Promise<ActAttachment> {
  const localMediaId = `${LOCAL_PREFIX}${uuidv7()}`;
  const attachmentId = `local-att:${uuidv7()}`;
  await db.files.put({ key: `${localMediaId}:original`, blob: file, savedAt: Date.now() });
  if (file.type.startsWith('image/')) {
    const thumb = await thumbnail(file);
    if (thumb) await db.files.put({ key: `${localMediaId}:thumbnail`, blob: thumb, savedAt: Date.now() });
  }
  const attachment: ActAttachment = {
    id: attachmentId,
    mediaId: localMediaId,
    kind,
    fileName: file.name,
    contentType: file.type || 'application/octet-stream',
    sizeBytes: file.size,
    comment,
    uploadedBy: currentUsername() ?? null,
    uploadedAt: new Date().toISOString(),
    canDelete: true,
  };
  await updateAct(actId, (a) => ({ ...a, attachments: [...a.attachments, attachment] }));
  await enqueueUpload(
    actId,
    {
      target: 'attachment',
      localMediaId,
      localItemId: attachmentId,
      fileName: file.name,
      contentType: attachment.contentType!,
      kind,
      comment,
    },
    kind === 'SIGNED_ACT' ? 'Скан подписанного акта' : 'Документ',
  );
  await refreshQueueState();
  return attachment;
}

/** Подпись к фото, которое ещё на ПК: меняется прямо в операции загрузки. */
export async function captionLocalPhoto(
  actId: string,
  photoId: string,
  caption: string | null,
): Promise<ActDetail> {
  await db.outbox
    .filter((o) => o.method === 'UPLOAD' && (o.body as UploadBody).localItemId === photoId)
    .modify((o) => {
      o.body = { ...(o.body as UploadBody), caption };
    });
  return updateAct(actId, (a) => ({
    ...a,
    photos: a.photos.map((p) => (p.id === photoId ? { ...p, caption } : p)),
  }));
}

/** Удалить фото или документ, которые ещё не отправлены: убрать из очереди и с ПК. */
export async function removeLocalItem(actId: string, itemId: string): Promise<ActDetail> {
  const ops = await db.outbox
    .filter((o) => o.method === 'UPLOAD' && (o.body as UploadBody).localItemId === itemId)
    .toArray();
  for (const op of ops) {
    const media = (op.body as UploadBody).localMediaId;
    await db.files.bulkDelete([`${media}:original`, `${media}:thumbnail`]);
    await db.outbox.delete(op.seq!);
  }
  await refreshQueueState();
  return updateAct(actId, (a) => ({
    ...a,
    photos: a.photos.filter((p) => p.id !== itemId).map((p, i) => ({ ...p, position: i + 1 })),
    attachments: a.attachments.filter((x) => x.id !== itemId),
  }));
}

/** Открыть или скачать файл, который хранится на ПК (ещё не отправлен или сохранён при просмотре). */
export async function localFileUrl(mediaId: string): Promise<string | null> {
  const file = (await db.files.get(`${mediaId}:original`)) ?? (await db.files.get(`${mediaId}:thumbnail`));
  return file ? URL.createObjectURL(file.blob) : null;
}

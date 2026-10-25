import { useState } from 'react';
import {
  App,
  Button,
  Empty,
  Input,
  Modal,
  Popconfirm,
  Progress,
  Space,
  Spin,
  Tag,
  Typography,
  Upload,
} from 'antd';
import { CameraOutlined, DeleteOutlined, HolderOutlined, InboxOutlined } from '@ant-design/icons';
import imageCompression from 'browser-image-compression';
import {
  DndContext,
  PointerSensor,
  TouchSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core';
import { SortableContext, arrayMove, rectSortingStrategy, useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { useQueryClient } from '@tanstack/react-query';
import { getGetActQueryKey } from '../../api/generated/acts/acts';
import {
  useAddActPhoto,
  useCaptionActPhoto,
  useDeleteActPhoto,
  useReorderActPhotos,
} from '../../api/generated/acts/act-photos';
import type { ActDetail, ActPhoto } from '../../api/generated/acts/model';
import { uploadMedia } from '../../api/generated/media/media';
import { ApiError } from '../../api/http';
import { isLocalMedia, queueLocalPhoto } from '../../offline/files';
import { filesGoLocal } from '../../offline/gateway';
import { ok } from '../../api/unwrap';
import { errorText } from '../../shared/lib/apiErrors';
import { useMediaUrl } from '../../shared/lib/useMediaBlob';
import { PhotoStaging } from './PhotoStaging';
import { release, stage, type StagedPhoto } from './stagedPhotos';

const MAX_CAPTION = 200;

/** Сжатие в браузере: до 2560 px по длинной стороне, JPEG; поворот по EXIF применяется, геоданные удаляются. */
async function prepare(file: File): Promise<File> {
  const compressed = await imageCompression(file, {
    maxWidthOrHeight: 2560,
    maxSizeMB: 3,
    fileType: 'image/jpeg',
    initialQuality: 0.85,
    useWebWorker: true,
    preserveExif: false,
  });
  const name = file.name.replace(/\.[^.]+$/, '') + '.jpg';
  return new File([compressed], name, { type: 'image/jpeg' });
}

function PhotoCard({
  photo,
  number,
  editable,
  onCaption,
  onDelete,
  onOpen,
}: {
  photo: ActPhoto;
  number: number;
  editable: boolean;
  onCaption: (caption: string) => void;
  onDelete: () => void;
  onOpen: () => void;
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: photo.id,
    disabled: !editable,
  });
  const { url, isLoading } = useMediaUrl(photo.mediaId, 'thumbnail');
  const [caption, setCaption] = useState(photo.caption ?? '');
  const missing = !caption.trim();

  return (
    <div
      ref={setNodeRef}
      className={`photo-card${isDragging ? ' dragging' : ''}${missing ? ' missing' : ''}`}
      style={{ transform: CSS.Transform.toString(transform), transition }}
    >
      {isLocalMedia(photo.mediaId) && (
        <Tag
          color="orange"
          className="photo-local"
          title="Фото сохранено на этом ПК и будет отправлено на сервер при синхронизации"
        >
          на ПК
        </Tag>
      )}
      <button type="button" className="photo-thumb" onClick={onOpen} aria-label={`Открыть фото ${number}`}>
        {url ? (
          <img src={url} alt={caption || `Фото ${number}`} />
        ) : isLoading ? (
          <Spin />
        ) : (
          <span>Нет превью</span>
        )}
      </button>
      <div className="photo-meta">
        <Space size={4}>
          {editable && (
            <span
              className="drag-handle"
              {...attributes}
              {...listeners}
              aria-label="Перетащите, чтобы изменить порядок"
            >
              <HolderOutlined />
            </span>
          )}
          <b>Фото {number}</b>
        </Space>
        {editable && (
          <Popconfirm title="Удалить фото?" okText="Удалить" cancelText="Отмена" onConfirm={onDelete}>
            <Button type="text" size="small" danger icon={<DeleteOutlined />} aria-label="Удалить фото" />
          </Popconfirm>
        )}
      </div>
      {editable ? (
        <Input.TextArea
          value={caption}
          onChange={(e) => setCaption(e.target.value)}
          onBlur={() => caption.trim() !== (photo.caption ?? '') && onCaption(caption.trim())}
          placeholder="Что на фото — обязательно"
          maxLength={MAX_CAPTION}
          autoSize={{ minRows: 2, maxRows: 4 }}
          status={missing ? 'warning' : undefined}
        />
      ) : (
        <Typography.Paragraph style={{ margin: 0 }}>{photo.caption ?? '—'}</Typography.Paragraph>
      )}
    </div>
  );
}

function PhotoViewer({ photo, onClose }: { photo: ActPhoto; onClose: () => void }) {
  const { url, isLoading } = useMediaUrl(photo.mediaId, 'original');
  return (
    <Modal
      open
      footer={null}
      onCancel={onClose}
      width="min(1100px, 96vw)"
      title={photo.caption ?? 'Фото'}
      centered
    >
      <div className="photo-viewer">
        {url ? <img src={url} alt={photo.caption ?? ''} /> : isLoading ? <Spin /> : 'Не удалось загрузить'}
      </div>
    </Modal>
  );
}

/** Фото технического отчёта: загрузка с компьютера или камеры телефона, подписи, порядок перетаскиванием. */
export function PhotosSection({ act }: { act: ActDetail }) {
  const { message } = App.useApp();
  const client = useQueryClient();
  const add = useAddActPhoto();
  const caption = useCaptionActPhoto();
  const reorder = useReorderActPhotos();
  const remove = useDeleteActPhoto();
  const [queue, setQueue] = useState<{ done: number; total: number } | null>(null);
  const [viewing, setViewing] = useState<ActPhoto | null>(null);
  const [order, setOrder] = useState<string[] | null>(null);
  const [staged, setStaged] = useState<StagedPhoto[] | null>(null);
  const editable = act.permissions.canEdit;
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 200, tolerance: 6 } }),
  );

  const photos = order
    ? (order.map((id) => act.photos.find((p) => p.id === id)).filter(Boolean) as ActPhoto[])
    : [...act.photos].sort((a, b) => a.position - b.position);

  const refresh = () => client.invalidateQueries({ queryKey: getGetActQueryKey(act.id) });

  const isImage = (f: File) => f.type.startsWith('image/') || /\.(jpe?g|png|heic|heif|webp)$/i.test(f.name);

  /** Несколько фото — сначала окно «Новые фото» для порядка и подписей; одно фото добавляется сразу. */
  const pick = (files: File[]) => {
    const images = files.filter(isImage);
    if (images.length > 1) setStaged(stage(images));
    else if (images.length === 1) void uploadAll([{ file: images[0], caption: null }]);
  };

  const uploadAll = async (items: { file: File; caption: string | null }[]) => {
    const images = items.map((i) => i.file);
    const captions = items.map((i) => i.caption?.trim() || null);
    if (!images.length) return;
    setQueue({ done: 0, total: images.length });
    let failed = 0;
    let onPc = 0;
    for (let i = 0; i < images.length; i++) {
      try {
        const file = await prepare(images[i]);
        // Без связи (или пока у акта есть неотправленные изменения) фото сохраняется на ПК и уйдёт при синхронизации.
        if (await filesGoLocal(act.id)) {
          await queueLocalPhoto(act.id, file, images[i].name, captions[i]);
          onPc++;
        } else {
          try {
            const media = ok(await uploadMedia({ file, ownerType: 'ACT_PHOTO', ownerId: act.id }))!;
            await add.mutateAsync({ id: act.id, data: { mediaId: media.id, caption: captions[i] } });
          } catch (e) {
            const unreachable = !(e instanceof ApiError) || e.status === 0 || e.status >= 502;
            if (!unreachable) throw e;
            await queueLocalPhoto(act.id, file, images[i].name, captions[i]);
            onPc++;
          }
        }
      } catch (e) {
        failed++;
        message.error(`${images[i].name}: ${errorText(e)}`);
      }
      setQueue({ done: i + 1, total: images.length });
    }
    setQueue(null);
    await refresh();
    if (onPc > 0) {
      message.info(
        `Нет связи: фото сохранены на ПК (${onPc}) и будут отправлены при синхронизации. Добавьте подписи.`,
      );
    } else if (images.length - failed > 0) {
      message.success(`Загружено фото: ${images.length - failed}. Добавьте подписи.`);
    }
  };

  const onDragEnd = async (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const ids = photos.map((p) => p.id);
    const next = arrayMove(ids, ids.indexOf(String(active.id)), ids.indexOf(String(over.id)));
    setOrder(next);
    try {
      await reorder.mutateAsync({ id: act.id, data: { photoIds: next } });
      await refresh();
    } catch (e) {
      message.error(errorText(e));
    } finally {
      setOrder(null);
    }
  };

  const withoutCaption = act.photos.filter((p) => !p.caption).length;

  return (
    <div className="act-section">
      <div className="section-head">
        <Typography.Title level={5} style={{ margin: 0 }}>
          Фото для технического отчёта
        </Typography.Title>
        <span className="cell-muted">
          {act.photos.length ? `${act.photos.length} фото` : ''}
          {withoutCaption > 0 && ` · без подписи: ${withoutCaption}`}
        </span>
      </div>

      {editable && (
        <div className="photo-upload">
          <Upload.Dragger
            multiple
            accept="image/*"
            showUploadList={false}
            disabled={!!queue}
            beforeUpload={(_file, list) => {
              if (_file === list[0]) pick(list as unknown as File[]);
              return false;
            }}
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">Перетащите фото сюда или нажмите, чтобы выбрать</p>
            <p className="ant-upload-hint">
              Можно несколько сразу — перед добавлением их можно расставить по порядку и подписать. Фото
              уменьшаются до 2560 px, геоданные удаляются
            </p>
          </Upload.Dragger>
          <Upload
            accept="image/*"
            capture="environment"
            showUploadList={false}
            disabled={!!queue}
            beforeUpload={(file) => {
              pick([file as unknown as File]);
              return false;
            }}
          >
            <Button icon={<CameraOutlined />} size="large" className="camera-button">
              Сделать фото
            </Button>
          </Upload>
          {queue && (
            <Progress
              percent={Math.round((queue.done / queue.total) * 100)}
              format={() => `${queue.done} из ${queue.total}`}
            />
          )}
        </div>
      )}

      {photos.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Фото пока нет" />
      ) : (
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={(e) => void onDragEnd(e)}>
          <SortableContext items={photos.map((p) => p.id)} strategy={rectSortingStrategy}>
            <div className="photo-grid">
              {photos.map((p, i) => (
                <PhotoCard
                  key={p.id}
                  photo={p}
                  number={i + 1}
                  editable={editable}
                  onOpen={() => setViewing(p)}
                  onCaption={(text) =>
                    void caption
                      .mutateAsync({ id: act.id, photoId: p.id, data: { caption: text || null } })
                      .then(refresh)
                      .catch((e) => message.error(errorText(e)))
                  }
                  onDelete={() =>
                    void remove
                      .mutateAsync({ id: act.id, photoId: p.id })
                      .then(refresh)
                      .catch((e) => message.error(errorText(e)))
                  }
                />
              ))}
            </div>
          </SortableContext>
        </DndContext>
      )}
      <Typography.Paragraph type="secondary" className="hint">
        Подпись к каждому фото обязательна — без неё акт нельзя закрыть. В отчёте фото идут в этом порядке по
        6 на лист.
      </Typography.Paragraph>
      {viewing && <PhotoViewer photo={viewing} onClose={() => setViewing(null)} />}
      {staged && (
        <PhotoStaging
          items={staged}
          startNumber={act.photos.length + 1}
          onChange={setStaged}
          onCancel={() => {
            release(staged);
            setStaged(null);
          }}
          onConfirm={(items) => {
            setStaged(null);
            void uploadAll(items.map((x) => ({ file: x.file, caption: x.caption }))).finally(() =>
              release(items),
            );
          }}
        />
      )}
    </div>
  );
}

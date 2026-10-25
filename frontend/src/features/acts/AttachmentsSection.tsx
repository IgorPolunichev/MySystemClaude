import { useState } from 'react';
import {
  App,
  Button,
  Empty,
  Input,
  List,
  Popconfirm,
  Segmented,
  Space,
  Tag,
  Tooltip,
  Typography,
  Upload,
} from 'antd';
import {
  DeleteOutlined,
  DownloadOutlined,
  EyeOutlined,
  FileExcelOutlined,
  FileImageOutlined,
  FilePdfOutlined,
  FileOutlined,
  FileWordOutlined,
  PaperClipOutlined,
} from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import { getGetActQueryKey } from '../../api/generated/acts/acts';
import { useAddActAttachment, useDeleteActAttachment } from '../../api/generated/acts/act-attachments';
import type { ActAttachment, ActDetail, AttachmentKind } from '../../api/generated/acts/model';
import { getGetMediaContentUrl, uploadMedia } from '../../api/generated/media/media';
import { ApiError, downloadFile } from '../../api/http';
import { isLocalMedia, localFileUrl, queueLocalAttachment } from '../../offline/files';
import { filesGoLocal } from '../../offline/gateway';
import { ok } from '../../api/unwrap';
import { getAccessToken } from '../../app/auth/tokenStore';
import { errorText } from '../../shared/lib/apiErrors';
import { invalidateByPrefix } from '../../shared/lib/invalidate';

const MAX_MB = 25;
const dateTime = new Intl.DateTimeFormat('ru-RU', { dateStyle: 'short', timeStyle: 'short' });

function icon(contentType?: string | null) {
  if (!contentType) return <FileOutlined />;
  if (contentType === 'application/pdf') return <FilePdfOutlined style={{ color: '#c0392b' }} />;
  if (contentType.startsWith('image/')) return <FileImageOutlined style={{ color: '#1f5c99' }} />;
  if (contentType.includes('wordprocessing')) return <FileWordOutlined style={{ color: '#2b579a' }} />;
  if (contentType.includes('spreadsheet')) return <FileExcelOutlined style={{ color: '#217346' }} />;
  return <FileOutlined />;
}

function size(bytes: number) {
  return bytes >= 1024 * 1024
    ? `${(bytes / 1024 / 1024).toFixed(1).replace('.', ',')} МБ`
    : `${Math.max(1, Math.round(bytes / 1024))} КБ`;
}

/** Открыть PDF или изображение в новой вкладке (запрос с токеном, затем blob:). */
async function openInTab(mediaId: string) {
  const tab = window.open('', '_blank');
  if (isLocalMedia(mediaId)) {
    const url = await localFileUrl(mediaId);
    if (!url) {
      tab?.close();
      throw new Error('Файл не найден на этом ПК');
    }
    if (tab) tab.location.href = url;
    return;
  }
  const token = getAccessToken();
  const response = await fetch(getGetMediaContentUrl(mediaId), {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });
  if (!response.ok) {
    tab?.close();
    throw new Error(`Ошибка ${response.status}`);
  }
  const url = URL.createObjectURL(await response.blob());
  if (tab) tab.location.href = url;
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/**
 * Документы акта: скан подписанного акта и другие файлы (письма, заявки, протоколы). Прикреплять можно и к
 * закрытому акту — скан подписанного акта появляется как раз после закрытия.
 */
export function AttachmentsSection({ act }: { act: ActDetail }) {
  const { message } = App.useApp();
  const client = useQueryClient();
  const add = useAddActAttachment();
  const remove = useDeleteActAttachment();
  const [kind, setKind] = useState<AttachmentKind>(
    act.attachments.some((a) => a.kind === 'SIGNED_ACT') ? 'OTHER' : 'SIGNED_ACT',
  );
  const [comment, setComment] = useState('');
  const [uploading, setUploading] = useState(false);

  const refresh = async () => {
    await client.invalidateQueries({ queryKey: getGetActQueryKey(act.id) });
    await invalidateByPrefix(client, '/api/v1/acts?');
  };

  const upload = async (file: File) => {
    if (file.size > MAX_MB * 1024 * 1024) {
      message.error(`Файл больше ${MAX_MB} МБ`);
      return;
    }
    setUploading(true);
    const local = async () => {
      await queueLocalAttachment(act.id, file, kind, comment.trim() || null);
      setComment('');
      await refresh();
      message.info('Нет связи: документ сохранён на ПК и будет отправлен при синхронизации');
    };
    try {
      if (await filesGoLocal(act.id)) {
        await local();
        return;
      }
      try {
        const media = ok(await uploadMedia({ file, ownerType: 'ACT_ATTACHMENT', ownerId: act.id }))!;
        await add.mutateAsync({
          id: act.id,
          data: { mediaId: media.id, kind, comment: comment.trim() || null },
        });
      } catch (e) {
        const unreachable = !(e instanceof ApiError) || e.status === 0 || e.status >= 502;
        if (!unreachable) throw e;
        await local();
        return;
      }
      setComment('');
      await refresh();
      message.success(kind === 'SIGNED_ACT' ? 'Скан подписанного акта прикреплён' : 'Документ прикреплён');
    } catch (e) {
      message.error(`${file.name}: ${errorText(e)}`);
    } finally {
      setUploading(false);
    }
  };

  const del = async (a: ActAttachment) => {
    try {
      await remove.mutateAsync({ id: act.id, attachmentId: a.id });
      await refresh();
    } catch (e) {
      message.error(errorText(e));
    }
  };

  const viewable = (a: ActAttachment) =>
    a.contentType === 'application/pdf' || !!a.contentType?.startsWith('image/');

  return (
    <div className="act-section">
      <div className="section-head">
        <Typography.Title level={5} style={{ margin: 0 }}>
          Документы
        </Typography.Title>
        {act.attachments.some((a) => a.kind === 'SIGNED_ACT') ? (
          <Tag color="green">скан подписанного акта приложен</Tag>
        ) : (
          act.status === 'CLOSED' && <Tag color="orange">нет скана подписанного акта</Tag>
        )}
      </div>

      {act.permissions.canAttach && (
        <div className="attach-form">
          <Segmented
            value={kind}
            onChange={(v) => setKind(v as AttachmentKind)}
            options={[
              { value: 'SIGNED_ACT', label: 'Подписанный акт' },
              { value: 'OTHER', label: 'Другой документ' },
            ]}
          />
          <Input
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            maxLength={300}
            placeholder="Комментарий (необязательно)"
            style={{ flex: 1, minWidth: 200 }}
          />
          <Upload
            accept=".pdf,.jpg,.jpeg,.png,.tif,.tiff,.docx,.xlsx,.zip"
            showUploadList={false}
            disabled={uploading}
            beforeUpload={(file) => {
              void upload(file as unknown as File);
              return false;
            }}
          >
            <Button type="primary" icon={<PaperClipOutlined />} loading={uploading}>
              Прикрепить файл
            </Button>
          </Upload>
        </div>
      )}

      {act.attachments.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Документов нет" />
      ) : (
        <List
          dataSource={act.attachments}
          renderItem={(a) => (
            <List.Item
              actions={[
                viewable(a) && (
                  <Tooltip key="v" title="Открыть">
                    <Button
                      type="text"
                      icon={<EyeOutlined />}
                      onClick={() => void openInTab(a.mediaId).catch((e) => message.error(errorText(e)))}
                    />
                  </Tooltip>
                ),
                <Tooltip key="d" title="Скачать">
                  <Button
                    type="text"
                    icon={<DownloadOutlined />}
                    onClick={() =>
                      void (async () => {
                        if (isLocalMedia(a.mediaId)) {
                          const url = await localFileUrl(a.mediaId);
                          if (!url) throw new Error('Файл не найден на этом ПК');
                          const link = document.createElement('a');
                          link.href = url;
                          link.download = a.fileName ?? 'документ';
                          link.click();
                          setTimeout(() => URL.revokeObjectURL(url), 30_000);
                          return;
                        }
                        await downloadFile(
                          `/api/v1/media/${a.mediaId}/content?download=true`,
                          a.fileName ?? 'документ',
                        );
                      })().catch((e) => message.error(errorText(e)))
                    }
                  />
                </Tooltip>,
                a.canDelete && (
                  <Popconfirm
                    key="x"
                    title="Удалить документ?"
                    okText="Удалить"
                    cancelText="Отмена"
                    onConfirm={() => void del(a)}
                  >
                    <Button type="text" danger icon={<DeleteOutlined />} title="Удалить" />
                  </Popconfirm>
                ),
              ].filter(Boolean)}
            >
              <List.Item.Meta
                avatar={<span className="attach-icon">{icon(a.contentType)}</span>}
                title={
                  <Space wrap size={6}>
                    <span>{a.fileName ?? 'Без имени'}</span>
                    {a.kind === 'SIGNED_ACT' ? <Tag color="green">подписанный акт</Tag> : <Tag>документ</Tag>}
                    {isLocalMedia(a.mediaId) && (
                      <Tag color="orange" title="Сохранён на этом ПК, будет отправлен при синхронизации">
                        на ПК
                      </Tag>
                    )}
                  </Space>
                }
                description={
                  <span className="cell-muted">
                    {size(a.sizeBytes)} · {a.uploadedBy} ·{' '}
                    {a.uploadedAt ? dateTime.format(new Date(a.uploadedAt)) : ''}
                    {a.comment && ` · ${a.comment}`}
                  </span>
                }
              />
            </List.Item>
          )}
        />
      )}
      <Typography.Paragraph type="secondary" className="hint">
        PDF, сканы (JPEG, PNG, TIFF), документы Word и Excel, архивы ZIP — до {MAX_MB} МБ. Прикреплять
        документы можно и к закрытому акту.
      </Typography.Paragraph>
    </div>
  );
}

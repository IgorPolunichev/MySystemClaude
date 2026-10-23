import { useState } from 'react';
import { Alert, App, Button, Modal, Space, Statistic, Table, Typography, Upload } from 'antd';
import { DownloadOutlined, InboxOutlined } from '@ant-design/icons';
import { downloadFile } from '../../api/http';
import { errorText } from '../lib/apiErrors';

/** Итог импорта (общая схема ImportResult всех сервисов). */
export interface ImportOutcome {
  created: number;
  updated: number;
  skipped: number;
  errors: { row: number; message: string }[];
}

interface Props {
  open: boolean;
  title: string;
  hint: string;
  templateUrl: string;
  templateName: string;
  onImport: (file: File) => Promise<ImportOutcome>;
  onClose: (changed: boolean) => void;
}

/** Импорт из Excel/CSV: скачать шаблон, выбрать файл, увидеть итог и ошибки по строкам. */
export function ImportDialog({ open, title, hint, templateUrl, templateName, onImport, onClose }: Props) {
  const { message } = App.useApp();
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<ImportOutcome | null>(null);

  const close = () => {
    const changed = !!result && result.created + result.updated > 0;
    setFile(null);
    setResult(null);
    onClose(changed);
  };

  const run = async () => {
    if (!file) return;
    setBusy(true);
    try {
      setResult(await onImport(file));
    } catch (e) {
      message.error(errorText(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open={open}
      title={title}
      onCancel={close}
      width={720}
      destroyOnHidden
      footer={
        result ? (
          <Button type="primary" onClick={close}>
            Готово
          </Button>
        ) : (
          <Space>
            <Button onClick={close}>Отмена</Button>
            <Button type="primary" disabled={!file} loading={busy} onClick={() => void run()}>
              Загрузить
            </Button>
          </Space>
        )
      }
    >
      {!result && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
            {hint}
          </Typography.Paragraph>
          <Button
            icon={<DownloadOutlined />}
            onClick={() => downloadFile(templateUrl, templateName).catch((e) => message.error(errorText(e)))}
          >
            Скачать шаблон Excel
          </Button>
          <Upload.Dragger
            accept=".xlsx,.csv"
            maxCount={1}
            beforeUpload={(f) => {
              setFile(f);
              return false;
            }}
            onRemove={() => setFile(null)}
            fileList={file ? [{ uid: '1', name: file.name, status: 'done' }] : []}
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">Перетащите файл сюда или нажмите, чтобы выбрать</p>
            <p className="ant-upload-hint">Excel (.xlsx) или CSV, до 10 МБ</p>
          </Upload.Dragger>
        </Space>
      )}
      {result && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Space size="large" wrap>
            <Statistic title="Создано" value={result.created} />
            <Statistic title="Обновлено" value={result.updated} />
            <Statistic title="Пропущено с ошибками" value={result.errors.length} />
          </Space>
          {result.errors.length === 0 ? (
            <Alert type="success" showIcon message="Все строки загружены без ошибок." />
          ) : (
            <>
              <Alert
                type="warning"
                showIcon
                message="Строки с ошибками не загружены. Исправьте их в файле и загрузите файл снова — уже загруженные строки обновятся, дубликатов не будет."
              />
              <Table
                size="small"
                rowKey={(r) => `${r.row}-${r.message}`}
                dataSource={result.errors}
                pagination={{ pageSize: 8, hideOnSinglePage: true }}
                columns={[
                  { title: 'Строка', dataIndex: 'row', width: 90 },
                  { title: 'Ошибка', dataIndex: 'message' },
                ]}
              />
            </>
          )}
        </Space>
      )}
    </Modal>
  );
}

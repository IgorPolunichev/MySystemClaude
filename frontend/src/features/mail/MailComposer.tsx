import { useEffect, useRef, useState } from 'react';
import {
  Alert,
  App,
  Button,
  Checkbox,
  Drawer,
  Form,
  Input,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import {
  BoldOutlined,
  DownloadOutlined,
  OrderedListOutlined,
  SendOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import { usePreviewMail, useSendMail } from '../../api/generated/mail/mail';
import { useListEmailTemplates } from '../../api/generated/mail/templates';
import type { MailPreview, SendMode } from '../../api/generated/mail/model';
import { downloadFile } from '../../api/http';
import { ok } from '../../api/unwrap';
import { errorText } from '../../shared/lib/apiErrors';
import { invalidateByPrefix } from '../../shared/lib/invalidate';
import { RecipientSelect } from './RecipientSelect';

/** Простой редактор HTML-тела письма: жирный, списки; таблица актов приходит из шаблона. */
function BodyEditor({ html, onChange }: { html: string; onChange: (v: string) => void }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (ref.current && ref.current.innerHTML !== html) ref.current.innerHTML = html;
  }, [html]);
  const exec = (command: string) => {
    ref.current?.focus();
    // execCommand устарел, но поддерживается всеми браузерами и достаточен для простого редактора
    document.execCommand(command);
    onChange(ref.current?.innerHTML ?? '');
  };
  return (
    <div className="mail-editor">
      <Space size={4} className="mail-editor-toolbar">
        <Button
          size="small"
          icon={<BoldOutlined />}
          onMouseDown={(e) => e.preventDefault()}
          onClick={() => exec('bold')}
          title="Жирный"
        />
        <Button
          size="small"
          icon={<UnorderedListOutlined />}
          onMouseDown={(e) => e.preventDefault()}
          onClick={() => exec('insertUnorderedList')}
          title="Список"
        />
        <Button
          size="small"
          icon={<OrderedListOutlined />}
          onMouseDown={(e) => e.preventDefault()}
          onClick={() => exec('insertOrderedList')}
          title="Нумерованный список"
        />
      </Space>
      <div
        ref={ref}
        className="mail-editor-body"
        contentEditable
        suppressContentEditableWarning
        onInput={() => onChange(ref.current?.innerHTML ?? '')}
      />
    </div>
  );
}

/**
 * Мастер письма по актам (раздел 10 SPEC.md): шаблон, получатели (подставляются по заказчику), тема и текст с
 * правкой, вложения Excel, отправка через SMTP или скачивание .eml для Outlook.
 */
export function MailComposer({
  actIds,
  open,
  onClose,
}: {
  actIds: string[];
  open: boolean;
  onClose: () => void;
}) {
  const { message } = App.useApp();
  const client = useQueryClient();
  const templates = ok(useListEmailTemplates().data) ?? [];
  const preview = usePreviewMail();
  const send = useSendMail();
  const [templateId, setTemplateId] = useState<string>();
  const [data, setData] = useState<MailPreview>();
  const [to, setTo] = useState<string[]>([]);
  const [cc, setCc] = useState<string[]>([]);
  const [bcc, setBcc] = useState<string[]>([]);
  const [showCopies, setShowCopies] = useState(false);
  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [zip, setZip] = useState(false);
  const [busy, setBusy] = useState<SendMode | null>(null);

  useEffect(() => {
    if (!open || actIds.length === 0) return;
    let cancelled = false;
    preview
      .mutateAsync({ data: { actIds, templateId: templateId ?? null } })
      .then((r) => {
        const p = ok(r);
        if (cancelled || !p) return;
        setData(p);
        setSubject(p.subject);
        setBody(p.body);
        setTo((current) => (current.length ? current : p.defaultTo));
        if (!templateId) setTemplateId(p.templateId);
      })
      .catch((e) => message.error(errorText(e)));
    return () => {
      cancelled = true;
    };
    // preview (мутация) меняется при каждом вызове; перезапрос нужен только при смене актов и шаблона
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, actIds.join(','), templateId]);

  const reset = () => {
    setData(undefined);
    setTemplateId(undefined);
    setTo([]);
    setCc([]);
    setBcc([]);
    setZip(false);
    setShowCopies(false);
  };

  const submit = async (mode: SendMode) => {
    setBusy(mode);
    try {
      const result = ok(
        await send.mutateAsync({
          data: { actIds, templateId: templateId ?? null, to, cc, bcc, subject, body, mode, zip },
        }),
      )!;
      await invalidateByPrefix(client, '/api/v1/mail/messages');
      if (mode === 'EML') {
        await downloadFile(`/api/v1/mail/messages/${result.id}/eml`, 'Письмо с актами.eml');
        message.success('Письмо сохранено: откройте файл .eml и отправьте из Outlook');
      } else {
        message.success('Письмо поставлено в очередь и уйдёт в течение минуты');
      }
      reset();
      onClose();
    } catch (e) {
      const text = errorText(e);
      message.error(text);
      if (text.includes('ZIP')) setZip(true);
    } finally {
      setBusy(null);
    }
  };

  return (
    <Drawer
      open={open}
      onClose={() => {
        reset();
        onClose();
      }}
      width="min(820px, 100vw)"
      title={`Письмо: ${actIds.length} ${actIds.length === 1 ? 'акт' : 'акта(ов)'}`}
      destroyOnHidden
      extra={
        <Space>
          <Button
            icon={<DownloadOutlined />}
            loading={busy === 'EML'}
            disabled={!data || !!busy}
            onClick={() => void submit('EML')}
          >
            Скачать .eml
          </Button>
          <Button
            type="primary"
            icon={<SendOutlined />}
            loading={busy === 'SMTP'}
            disabled={!data || !!busy || to.length === 0}
            onClick={() => void submit('SMTP')}
          >
            Отправить
          </Button>
        </Space>
      }
    >
      {!data ? (
        <Spin style={{ display: 'block', margin: '60px auto' }} />
      ) : (
        <Form layout="vertical">
          {data.warnings.length > 0 && (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 12 }}
              message={data.warnings.map((w) => (
                <div key={w}>{w}</div>
              ))}
            />
          )}
          <Form.Item label="Шаблон">
            <Select
              value={templateId}
              onChange={setTemplateId}
              options={templates.map((t) => ({
                value: t.id,
                label: t.isDefault ? `${t.name} (по умолчанию)` : t.name,
              }))}
            />
          </Form.Item>
          <Form.Item
            label="Кому"
            required
            extra={data.defaultTo.length ? 'Подставлены получатели заказчика из адресной книги' : undefined}
          >
            <RecipientSelect
              value={to}
              onChange={setTo}
              placeholder="Начните вводить имя, адрес или группу"
            />
          </Form.Item>
          {showCopies ? (
            <>
              <Form.Item label="Копия">
                <RecipientSelect value={cc} onChange={setCc} />
              </Form.Item>
              <Form.Item label="Скрытая копия">
                <RecipientSelect value={bcc} onChange={setBcc} />
              </Form.Item>
            </>
          ) : (
            <Button type="link" style={{ padding: 0, marginBottom: 12 }} onClick={() => setShowCopies(true)}>
              Копия, скрытая копия
            </Button>
          )}
          <Form.Item label="Тема">
            <Input value={subject} onChange={(e) => setSubject(e.target.value)} maxLength={1000} />
          </Form.Item>
          <Form.Item label="Текст письма">
            <BodyEditor html={body} onChange={setBody} />
          </Form.Item>
          <Form.Item label="Вложения">
            <Space wrap>
              {data.attachmentNames.map((n) => (
                <Tag key={n}>{n}</Tag>
              ))}
            </Space>
            <div style={{ marginTop: 8 }}>
              <Checkbox checked={zip} onChange={(e) => setZip(e.target.checked)}>
                Одним архивом ZIP (обязательно, если вложения больше 20 МБ)
              </Checkbox>
            </div>
          </Form.Item>
          <Typography.Paragraph type="secondary" className="hint">
            «Отправить» — письмо уйдёт от общего ящика компании, ответы придут на ваш адрес. «Скачать .eml» —
            откройте файл в Outlook и отправьте из своего ящика. В обоих случаях акты получат отметку
            «Отправлен».
          </Typography.Paragraph>
        </Form>
      )}
    </Drawer>
  );
}

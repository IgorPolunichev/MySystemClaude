import { useState } from 'react';
import { App, Button, Checkbox, Form, Input, Modal, Popconfirm, Space, Table, Tag, Typography } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import {
  useCreateEmailTemplate,
  useDeleteEmailTemplate,
  useListEmailTemplates,
  useUpdateEmailTemplate,
} from '../../api/generated/mail/templates';
import type { EmailTemplate, EmailTemplateInput } from '../../api/generated/mail/model';
import { ok } from '../../api/unwrap';
import { applyFormError, errorText } from '../../shared/lib/apiErrors';
import { invalidateByPrefix } from '../../shared/lib/invalidate';
import { useRoles } from '../../shared/lib/useRoles';

const HELP = `Переменные: acts — список актов (у каждого: number, organization, period, equipment, shortDescription, totalHours),
sender.fullName, sender.email, today, totalHours. Пример: <#list acts as a>\${a.number}<#sep>, </#list>`;

function TemplateDialog({ template, onClose }: { template?: EmailTemplate | null; onClose: () => void }) {
  const [form] = Form.useForm<EmailTemplateInput>();
  const { message } = App.useApp();
  const client = useQueryClient();
  const create = useCreateEmailTemplate();
  const update = useUpdateEmailTemplate();
  const save = async (v: EmailTemplateInput) => {
    try {
      if (template) await update.mutateAsync({ id: template.id, data: v });
      else await create.mutateAsync({ data: v });
      await invalidateByPrefix(client, '/api/v1/email-templates');
      onClose();
    } catch (e) {
      message.error(applyFormError(form, e));
    }
  };
  return (
    <Modal
      open
      width="min(860px, 96vw)"
      title={template ? 'Шаблон письма' : 'Новый шаблон'}
      okText="Сохранить"
      cancelText="Отмена"
      onOk={() => form.submit()}
      onCancel={onClose}
      confirmLoading={create.isPending || update.isPending}
      destroyOnHidden
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={template ?? { isDefault: false }}
        onFinish={(v) => void save(v)}
      >
        <Form.Item name="name" label="Название" rules={[{ required: true, message: 'Укажите название' }]}>
          <Input maxLength={200} />
        </Form.Item>
        <Form.Item name="subject" label="Тема" rules={[{ required: true, message: 'Укажите тему' }]}>
          <Input maxLength={1000} className="input-code" />
        </Form.Item>
        <Form.Item
          name="body"
          label="Текст (HTML и FreeMarker)"
          rules={[{ required: true, message: 'Укажите текст' }]}
          extra={HELP}
        >
          <Input.TextArea autoSize={{ minRows: 10, maxRows: 24 }} className="input-code" />
        </Form.Item>
        <Form.Item name="isDefault" valuePropName="checked">
          <Checkbox>Шаблон по умолчанию</Checkbox>
        </Form.Item>
      </Form>
      <Typography.Paragraph type="secondary">
        Шаблон проверяется на тестовом акте; с ошибкой он не сохранится.
      </Typography.Paragraph>
    </Modal>
  );
}

/** Шаблоны писем — изменяет администратор. */
export function TemplatesPage() {
  const { message } = App.useApp();
  const { isAdmin } = useRoles();
  const client = useQueryClient();
  const { data, isFetching } = useListEmailTemplates();
  const remove = useDeleteEmailTemplate();
  const [editing, setEditing] = useState<EmailTemplate | null | undefined>(undefined);
  return (
    <>
      <div className="toolbar">
        <Typography.Text type="secondary">Тема и текст письма с подстановкой данных актов.</Typography.Text>
        <div className="toolbar-spacer" />
        {isAdmin && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing(null)}>
            Шаблон
          </Button>
        )}
      </div>
      <div className="page-panel">
        <Table<EmailTemplate>
          rowKey="id"
          loading={isFetching}
          dataSource={ok(data)}
          pagination={false}
          columns={[
            {
              title: 'Название',
              key: 'name',
              render: (_, t) => (
                <Space>
                  {t.name}
                  {t.isDefault && <Tag color="gold">по умолчанию</Tag>}
                </Space>
              ),
            },
            {
              title: 'Тема',
              dataIndex: 'subject',
              render: (v: string) => <span className="cell-codes">{v}</span>,
            },
            {
              title: '',
              key: 'x',
              width: 90,
              render: (_, t) =>
                isAdmin && (
                  <Space size={0}>
                    <Button type="text" size="small" icon={<EditOutlined />} onClick={() => setEditing(t)} />
                    {!t.isDefault && (
                      <Popconfirm
                        title="Удалить шаблон?"
                        okText="Удалить"
                        cancelText="Отмена"
                        onConfirm={() =>
                          void remove
                            .mutateAsync({ id: t.id })
                            .then(() => invalidateByPrefix(client, '/api/v1/email-templates'))
                            .catch((e) => message.error(errorText(e)))
                        }
                      >
                        <Button type="text" size="small" danger icon={<DeleteOutlined />} />
                      </Popconfirm>
                    )}
                  </Space>
                ),
            },
          ]}
        />
      </div>
      {editing !== undefined && <TemplateDialog template={editing} onClose={() => setEditing(undefined)} />}
    </>
  );
}

import { useState } from 'react';
import {
  App,
  Button,
  Checkbox,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  Upload,
} from 'antd';
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  PlusOutlined,
  StarFilled,
  UploadOutlined,
} from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import {
  importContacts,
  useCreateContact,
  useCreateContactGroup,
  useDeleteContactGroup,
  useListContactGroups,
  useListContacts,
  useUpdateContact,
  useUpdateContactGroup,
} from '../../api/generated/mail/contacts';
import type { Contact, ContactGroup, ContactInput } from '../../api/generated/mail/model';
import { downloadFile } from '../../api/http';
import { ok } from '../../api/unwrap';
import { applyFormError, errorText } from '../../shared/lib/apiErrors';
import { invalidateByPrefix } from '../../shared/lib/invalidate';
import { useRoles } from '../../shared/lib/useRoles';
import { OrganizationSelect } from '../directory/lookups';

function ContactDialog({ contact, onClose }: { contact?: Contact | null; onClose: () => void }) {
  const [form] = Form.useForm<ContactInput>();
  const { message } = App.useApp();
  const client = useQueryClient();
  const create = useCreateContact();
  const update = useUpdateContact();
  const organizationId = Form.useWatch('organizationId', form);
  const save = async (v: ContactInput) => {
    try {
      if (contact) await update.mutateAsync({ id: contact.id, data: v });
      else await create.mutateAsync({ data: v });
      await invalidateByPrefix(client, '/api/v1/contacts', '/api/v1/contact-groups');
      onClose();
    } catch (e) {
      message.error(applyFormError(form, e));
    }
  };
  return (
    <Modal
      open
      title={contact ? 'Контакт' : 'Новый контакт'}
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
        initialValues={contact ?? { active: true }}
        onFinish={(v) => void save(v)}
      >
        <Form.Item
          name="email"
          label="Адрес почты"
          rules={[{ required: true, type: 'email', message: 'Укажите верный адрес' }]}
        >
          <Input maxLength={254} />
        </Form.Item>
        <Form.Item name="fullName" label="ФИО">
          <Input maxLength={200} />
        </Form.Item>
        <Form.Item name="position" label="Должность">
          <Input maxLength={200} />
        </Form.Item>
        <Form.Item
          name="organizationId"
          label="Организация-заказчик"
          extra="Чтобы контакт подставлялся в письма по актам этой организации"
        >
          <OrganizationSelect allowClear />
        </Form.Item>
        <Form.Item
          name="organizationName"
          label="Организация (текстом)"
          extra="Если организации нет в справочнике"
        >
          <Input maxLength={300} />
        </Form.Item>
        <Form.Item name="defaultForOrganization" valuePropName="checked">
          <Checkbox disabled={!organizationId}>
            Получатель по умолчанию для писем по актам этой организации
          </Checkbox>
        </Form.Item>
        <Form.Item name="notes" label="Примечание">
          <Input maxLength={500} />
        </Form.Item>
        <Form.Item name="active" label="Активен" valuePropName="checked">
          <Switch />
        </Form.Item>
      </Form>
    </Modal>
  );
}

function GroupDialog({ group, onClose }: { group?: ContactGroup | null; onClose: () => void }) {
  const { message } = App.useApp();
  const client = useQueryClient();
  const [name, setName] = useState(group?.name ?? '');
  const [members, setMembers] = useState<string[]>(group?.memberIds ?? []);
  const [q, setQ] = useState('');
  const all = ok(useListContacts({ q: q || undefined }).data) ?? [];
  const create = useCreateContactGroup();
  const update = useUpdateContactGroup();
  const save = async () => {
    try {
      if (group) await update.mutateAsync({ id: group.id, data: { name, memberIds: members } });
      else await create.mutateAsync({ data: { name, memberIds: members } });
      await invalidateByPrefix(client, '/api/v1/contact-groups');
      onClose();
    } catch (e) {
      message.error(errorText(e));
    }
  };
  return (
    <Modal
      open
      title={group ? 'Группа' : 'Новая группа'}
      okText="Сохранить"
      cancelText="Отмена"
      onOk={() => void save()}
      onCancel={onClose}
    >
      <Form layout="vertical">
        <Form.Item label="Название" required>
          <Input
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={200}
            placeholder="Бухгалтерия АО «Северный ГОК»"
          />
        </Form.Item>
        <Form.Item label="Участники">
          <Select
            mode="multiple"
            value={members}
            onChange={setMembers}
            onSearch={setQ}
            filterOption={false}
            options={all.map((c) => ({
              value: c.id,
              label: c.fullName ? `${c.fullName} <${c.email}>` : c.email,
            }))}
            placeholder="Найдите контакты"
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** Адресная книга: контакты, группы, импорт и экспорт CSV. */
export function ContactsPage() {
  const { message } = App.useApp();
  const { canEdit } = useRoles();
  const client = useQueryClient();
  const [q, setQ] = useState('');
  const [inactive, setInactive] = useState(false);
  const { data, isFetching } = useListContacts({ q: q || undefined, includeInactive: inactive });
  const groups = ok(useListContactGroups().data) ?? [];
  const removeGroup = useDeleteContactGroup();
  const [contact, setContact] = useState<Contact | null | undefined>(undefined);
  const [group, setGroup] = useState<ContactGroup | null | undefined>(undefined);

  const doImport = async (file: File) => {
    try {
      const r = ok(await importContacts({ file }));
      await invalidateByPrefix(client, '/api/v1/contacts');
      message.success(
        `Добавлено: ${r?.created ?? 0}, обновлено: ${r?.updated ?? 0}${r?.errors?.length ? `, ошибок: ${r.errors.length}` : ''}`,
      );
      r?.errors?.slice(0, 5).forEach((e) => message.warning(`Строка ${e.row}: ${e.message}`));
    } catch (e) {
      message.error(errorText(e));
    }
  };

  return (
    <>
      <div className="toolbar">
        <Input.Search
          allowClear
          placeholder="ФИО, адрес, организация"
          onSearch={(v) => setQ(v.trim())}
          style={{ width: 300 }}
        />
        <Space size={4}>
          <Switch size="small" checked={inactive} onChange={setInactive} />
          <span className="cell-muted">неактивные</span>
        </Space>
        <div className="toolbar-spacer" />
        <Upload
          accept=".csv,.xlsx,.xls"
          showUploadList={false}
          beforeUpload={(f) => (void doImport(f as unknown as File), false)}
        >
          <Button icon={<UploadOutlined />}>Импорт</Button>
        </Upload>
        <Button
          icon={<DownloadOutlined />}
          onClick={() =>
            void downloadFile('/api/v1/contacts/export', 'Адресная книга.csv').catch((e) =>
              message.error(errorText(e)),
            )
          }
        >
          Экспорт CSV
        </Button>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setContact(null)}>
          Контакт
        </Button>
      </div>
      <div className="page-panel">
        <Table<Contact>
          rowKey="id"
          loading={isFetching}
          dataSource={ok(data)}
          pagination={{ pageSize: 20, hideOnSinglePage: true }}
          size="middle"
          onRow={(c) => ({ onClick: () => setContact(c), style: { cursor: 'pointer' } })}
          columns={[
            {
              title: 'Контакт',
              key: 'name',
              render: (_, c) => (
                <Space direction="vertical" size={0}>
                  <span className="cell-strong">
                    {c.defaultForOrganization && (
                      <StarFilled
                        style={{ color: '#d99a1e', marginRight: 6 }}
                        title="Получатель по умолчанию"
                      />
                    )}
                    {c.fullName ?? c.email}
                  </span>
                  {c.fullName && <span className="cell-muted">{c.email}</span>}
                </Space>
              ),
            },
            { title: 'Организация', dataIndex: 'organizationName' },
            { title: 'Должность', dataIndex: 'position' },
            { title: '', key: 'state', width: 110, render: (_, c) => !c.active && <Tag>неактивен</Tag> },
          ]}
        />
      </div>

      <div className="page-panel" style={{ marginTop: 16 }}>
        <div className="section-head">
          <Typography.Title level={5} style={{ margin: 0 }}>
            Группы получателей
          </Typography.Title>
          {canEdit && (
            <Button size="small" icon={<PlusOutlined />} onClick={() => setGroup(null)}>
              Группа
            </Button>
          )}
        </div>
        <Table<ContactGroup>
          rowKey="id"
          dataSource={groups}
          pagination={false}
          size="small"
          locale={{ emptyText: 'Групп нет' }}
          columns={[
            { title: 'Группа', dataIndex: 'name' },
            { title: 'Адреса', key: 'emails', render: (_, g) => g.emails.join(', ') || '—' },
            {
              title: '',
              key: 'x',
              width: 90,
              render: (_, g) =>
                canEdit && (
                  <Space size={0}>
                    <Button type="text" size="small" icon={<EditOutlined />} onClick={() => setGroup(g)} />
                    <Popconfirm
                      title="Удалить группу?"
                      okText="Удалить"
                      cancelText="Отмена"
                      onConfirm={() =>
                        void removeGroup
                          .mutateAsync({ id: g.id })
                          .then(() => invalidateByPrefix(client, '/api/v1/contact-groups'))
                          .catch((e) => message.error(errorText(e)))
                      }
                    >
                      <Button type="text" size="small" danger icon={<DeleteOutlined />} />
                    </Popconfirm>
                  </Space>
                ),
            },
          ]}
        />
      </div>
      {contact !== undefined && <ContactDialog contact={contact} onClose={() => setContact(undefined)} />}
      {group !== undefined && <GroupDialog group={group} onClose={() => setGroup(undefined)} />}
    </>
  );
}

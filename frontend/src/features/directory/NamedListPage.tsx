import { useState } from 'react';
import { App, Button, Form, Input, Modal, Popconfirm, Space, Table, Typography } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import { applyFormError, errorText } from '../../shared/lib/apiErrors';
import { invalidateByPrefix } from '../../shared/lib/invalidate';

export interface NamedItem {
  id: string;
  name: string;
  version: number;
}

interface Props {
  url: string;
  items: NamedItem[] | undefined;
  loading: boolean;
  canAdd: boolean;
  canEdit: boolean;
  hint: string;
  newTitle: string;
  editTitle: string;
  create: (name: string) => Promise<unknown>;
  update: (item: NamedItem, name: string) => Promise<unknown>;
  remove: (item: NamedItem) => Promise<unknown>;
}

/** Справочник из одного названия: типы техники, отделы сервисной компании. */
export function NamedListPage(props: Props) {
  const { message } = App.useApp();
  const client = useQueryClient();
  const [form] = Form.useForm<{ name: string }>();
  const [editing, setEditing] = useState<{ item?: NamedItem } | null>(null);
  const [saving, setSaving] = useState(false);

  const open = (item?: NamedItem) => {
    form.resetFields();
    form.setFieldsValue({ name: item?.name ?? '' });
    setEditing({ item });
  };

  const save = async ({ name }: { name: string }) => {
    setSaving(true);
    try {
      if (editing?.item) await props.update(editing.item, name.trim());
      else await props.create(name.trim());
      await invalidateByPrefix(client, props.url);
      setEditing(null);
    } catch (e) {
      message.error(applyFormError(form, e));
    } finally {
      setSaving(false);
    }
  };

  const del = async (item: NamedItem) => {
    try {
      await props.remove(item);
      await invalidateByPrefix(client, props.url);
    } catch (e) {
      message.error(errorText(e));
    }
  };

  return (
    <>
      <div className="toolbar">
        <Typography.Text type="secondary">{props.hint}</Typography.Text>
        <div className="toolbar-spacer" />
        {props.canAdd && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => open()}>
            Добавить
          </Button>
        )}
      </div>
      <div className="page-panel narrow-panel">
        <Table<NamedItem>
          rowKey="id"
          size="middle"
          loading={props.loading}
          dataSource={props.items}
          pagination={false}
          columns={[
            { title: 'Название', dataIndex: 'name' },
            {
              title: '',
              key: 'actions',
              width: 96,
              render: (_, item) =>
                props.canEdit && (
                  <Space size={0}>
                    <Button
                      type="text"
                      icon={<EditOutlined />}
                      title="Переименовать"
                      onClick={() => open(item)}
                    />
                    <Popconfirm
                      title="Удалить?"
                      okText="Удалить"
                      cancelText="Отмена"
                      onConfirm={() => void del(item)}
                    >
                      <Button type="text" danger icon={<DeleteOutlined />} title="Удалить" />
                    </Popconfirm>
                  </Space>
                ),
            },
          ]}
        />
      </div>
      <Modal
        open={!!editing}
        title={editing?.item ? props.editTitle : props.newTitle}
        okText="Сохранить"
        cancelText="Отмена"
        onOk={() => form.submit()}
        onCancel={() => setEditing(null)}
        confirmLoading={saving}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={(v) => void save(v)}>
          <Form.Item
            name="name"
            label="Название"
            rules={[{ required: true, whitespace: true, message: 'Укажите название' }]}
          >
            <Input maxLength={200} autoFocus />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}

import { useEffect, useState } from 'react';
import {
  App,
  Button,
  Col,
  Drawer,
  Form,
  Input,
  Row,
  Segmented,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd';
import { EditOutlined, PlusOutlined, StarFilled } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import {
  useCreateExecutor,
  useListExecutors,
  useUpdateExecutor,
} from '../../api/generated/directory/executors';
import type { Executor, ExecutorInput } from '../../api/generated/directory/model';
import { ok } from '../../api/unwrap';
import { applyFormError } from '../../shared/lib/apiErrors';
import { invalidateByPrefix } from '../../shared/lib/invalidate';
import { emptyToNull } from '../../shared/lib/table';
import { useRoles } from '../../shared/lib/useRoles';

function ExecutorForm({
  open,
  executor,
  onClose,
}: {
  open: boolean;
  executor?: Executor;
  onClose: () => void;
}) {
  const [form] = Form.useForm<ExecutorInput>();
  const { message } = App.useApp();
  const client = useQueryClient();
  const create = useCreateExecutor();
  const update = useUpdateExecutor();

  useEffect(() => {
    if (open) {
      form.resetFields();
      form.setFieldsValue(executor ? { ...executor } : { active: true, isDefault: false });
    }
  }, [open, executor, form]);

  const submit = async (values: ExecutorInput) => {
    const data = emptyToNull({ ...values, version: executor?.version });
    try {
      if (executor) await update.mutateAsync({ id: executor.id, data });
      else await create.mutateAsync({ data });
      await invalidateByPrefix(client, '/api/v1/executors');
      message.success('Исполнитель сохранён');
      onClose();
    } catch (e) {
      message.error(applyFormError(form, e));
    }
  };

  return (
    <Drawer
      open={open}
      onClose={onClose}
      width="min(640px, 100vw)"
      title={executor ? (executor.shortName ?? executor.name) : 'Новый исполнитель'}
      destroyOnHidden
      extra={
        <Space>
          <Button onClick={onClose}>Отмена</Button>
          <Button type="primary" loading={create.isPending || update.isPending} onClick={() => form.submit()}>
            Сохранить
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical" requiredMark="optional" onFinish={(v) => void submit(v)}>
        <Form.Item
          name="name"
          label="Полное наименование"
          rules={[{ required: true, message: 'Укажите наименование' }]}
        >
          <Input maxLength={300} />
        </Form.Item>
        <Form.Item name="shortName" label="Краткое наименование">
          <Input maxLength={150} />
        </Form.Item>
        <Row gutter={16}>
          <Col xs={24} sm={8}>
            <Form.Item name="inn" label="ИНН">
              <Input maxLength={12} inputMode="numeric" />
            </Form.Item>
          </Col>
          <Col xs={24} sm={8}>
            <Form.Item name="kpp" label="КПП">
              <Input maxLength={9} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={8}>
            <Form.Item name="ogrn" label="ОГРН">
              <Input maxLength={15} inputMode="numeric" />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="address" label="Адрес">
          <Input maxLength={500} />
        </Form.Item>
        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item name="phone" label="Телефон">
              <Input maxLength={50} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="email" label="Почта" rules={[{ type: 'email', message: 'Неверный адрес' }]}>
              <Input maxLength={150} />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="bankDetails" label="Банковские реквизиты">
          <Input.TextArea maxLength={1000} autoSize={{ minRows: 2, maxRows: 5 }} />
        </Form.Item>
        <Row gutter={16}>
          <Col xs={12}>
            <Form.Item
              name="isDefault"
              label="Основной исполнитель"
              valuePropName="checked"
              extra="Подставляется в новые акты"
            >
              <Switch />
            </Form.Item>
          </Col>
          <Col xs={12}>
            <Form.Item
              name="active"
              label="Активен"
              valuePropName="checked"
              extra="Заблокированного нельзя выбрать в акте"
            >
              <Switch />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Drawer>
  );
}

/** Исполнители — компании, от имени которых оказываются услуги. Изменяет администратор. */
export function ExecutorsPage() {
  const { isAdmin } = useRoles();
  const [filter, setFilter] = useState<'active' | 'all'>('active');
  const { data, isFetching } = useListExecutors({ includeInactive: filter === 'all' });
  const [form, setForm] = useState<{ open: boolean; executor?: Executor }>({ open: false });

  return (
    <>
      <div className="toolbar">
        <Segmented
          value={filter}
          onChange={(v) => setFilter(v as typeof filter)}
          options={[
            { value: 'active', label: 'Активные' },
            { value: 'all', label: 'Все' },
          ]}
        />
        <Typography.Text type="secondary">Ваши компании, от имени которых составляются акты.</Typography.Text>
        <div className="toolbar-spacer" />
        {isAdmin && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setForm({ open: true })}>
            Добавить
          </Button>
        )}
      </div>
      <div className="page-panel">
        <Table<Executor>
          rowKey="id"
          loading={isFetching}
          dataSource={ok(data)}
          pagination={false}
          scroll={{ x: 720 }}
          columns={[
            {
              title: 'Исполнитель',
              key: 'name',
              render: (_, e) => (
                <Space direction="vertical" size={0}>
                  <span className="cell-strong">
                    {e.isDefault && (
                      <StarFilled style={{ color: '#d99a1e', marginRight: 6 }} title="Основной" />
                    )}
                    {e.shortName ?? e.name}
                  </span>
                  {e.shortName && <span className="cell-muted">{e.name}</span>}
                </Space>
              ),
            },
            { title: 'ИНН', dataIndex: 'inn', width: 130 },
            { title: 'Телефон', dataIndex: 'phone', width: 170 },
            {
              title: '',
              key: 'state',
              width: 150,
              render: (_, e) => (
                <Space>
                  {e.isDefault && <Tag color="gold">основной</Tag>}
                  {!e.active && <Tag>заблокирован</Tag>}
                  {isAdmin && (
                    <Button
                      type="text"
                      icon={<EditOutlined />}
                      title="Изменить"
                      onClick={() => setForm({ open: true, executor: e })}
                    />
                  )}
                </Space>
              ),
            },
          ]}
        />
      </div>
      <ExecutorForm open={form.open} executor={form.executor} onClose={() => setForm({ open: false })} />
    </>
  );
}

import { useEffect, useState } from 'react';
import {
  Alert,
  App,
  Button,
  Col,
  Drawer,
  Form,
  Input,
  Row,
  Segmented,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd';
import { EditOutlined, KeyOutlined, PlusOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import {
  useCreateEngineer,
  useListEngineers,
  useUpdateEngineer,
} from '../../api/generated/directory/engineers';
import type { Engineer, EngineerCreate, EngineerUpdate } from '../../api/generated/directory/model';
import { ok } from '../../api/unwrap';
import { applyFormError } from '../../shared/lib/apiErrors';
import { invalidateByPrefix } from '../../shared/lib/invalidate';
import { emptyToNull, sortParam } from '../../shared/lib/table';
import { useRoles } from '../../shared/lib/useRoles';
import { ServiceDepartmentSelect } from './lookups';
import { generatePassword } from './password';

type FormValues = EngineerCreate & EngineerUpdate;

function EngineerForm({
  open,
  engineer,
  onClose,
}: {
  open: boolean;
  engineer?: Engineer;
  onClose: () => void;
}) {
  const [form] = Form.useForm<FormValues>();
  const { message } = App.useApp();
  const client = useQueryClient();
  const create = useCreateEngineer();
  const update = useUpdateEngineer();
  const [created, setCreated] = useState<{ username: string; password: string } | null>(null);
  const close = () => {
    setCreated(null);
    onClose();
  };

  useEffect(() => {
    if (open) {
      form.resetFields();
      form.setFieldsValue(
        engineer ? { ...engineer } : { role: 'ENGINEER', initialPassword: generatePassword() },
      );
    }
  }, [open, engineer, form]);

  const submit = async (values: FormValues) => {
    try {
      if (engineer) {
        const data: EngineerUpdate = emptyToNull({
          lastName: values.lastName,
          firstName: values.firstName,
          middleName: values.middleName,
          position: values.position,
          serviceDepartmentId: values.serviceDepartmentId ?? null,
          phone: values.phone,
          email: values.email,
          active: values.active,
          version: engineer.version,
        });
        await update.mutateAsync({ id: engineer.id, data });
        message.success('Карточка инженера сохранена');
        close();
      } else {
        await create.mutateAsync({ data: emptyToNull(values) });
        setCreated({ username: values.username, password: values.initialPassword });
      }
      await invalidateByPrefix(client, '/api/v1/engineers');
    } catch (e) {
      message.error(applyFormError(form, e));
    }
  };

  return (
    <Drawer
      open={open}
      onClose={close}
      width="min(600px, 100vw)"
      title={engineer ? engineer.fullName : 'Новый инженер'}
      destroyOnHidden
      extra={
        !created && (
          <Space>
            <Button onClick={close}>Отмена</Button>
            <Button
              type="primary"
              loading={create.isPending || update.isPending}
              onClick={() => form.submit()}
            >
              {engineer ? 'Сохранить' : 'Создать'}
            </Button>
          </Space>
        )
      }
    >
      {created ? (
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <Alert
            type="success"
            showIcon
            message="Учётная запись создана"
            description="Передайте инженеру логин и временный пароль. При первом входе система попросит сменить пароль. Пароль больше нигде не показывается."
          />
          <div className="credentials">
            <div>
              <span className="cell-muted">Логин</span>
              <Typography.Text copyable strong>
                {created.username}
              </Typography.Text>
            </div>
            <div>
              <span className="cell-muted">Временный пароль</span>
              <Typography.Text copyable strong className="input-code">
                {created.password}
              </Typography.Text>
            </div>
          </div>
          <Button type="primary" onClick={close}>
            Готово
          </Button>
        </Space>
      ) : (
        <Form form={form} layout="vertical" requiredMark="optional" onFinish={(v) => void submit(v)}>
          {!engineer && (
            <>
              <Row gutter={16}>
                <Col xs={24} sm={12}>
                  <Form.Item
                    name="username"
                    label="Логин"
                    rules={[
                      { required: true, message: 'Укажите логин' },
                      {
                        pattern: /^[a-zA-Z0-9._-]{3,50}$/,
                        message: 'Латинские буквы, цифры, точка, дефис; от 3 символов',
                      },
                    ]}
                  >
                    <Input maxLength={50} autoComplete="off" placeholder="i.petrov" />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item name="role" label="Роль" rules={[{ required: true }]}>
                    <Select
                      options={[
                        { value: 'ENGINEER', label: 'Инженер' },
                        { value: 'DEPARTMENT_HEAD', label: 'Руководитель отдела' },
                        { value: 'ADMIN', label: 'Администратор' },
                      ]}
                    />
                  </Form.Item>
                </Col>
              </Row>
              <Form.Item
                name="initialPassword"
                label="Временный пароль"
                rules={[{ required: true, min: 8, message: 'Не короче 8 символов' }]}
                extra="Сгенерирован автоматически; при первом входе инженер задаст свой"
              >
                <Input
                  className="input-code"
                  autoComplete="off"
                  addonAfter={
                    <KeyOutlined
                      title="Сгенерировать новый"
                      onClick={() => form.setFieldValue('initialPassword', generatePassword())}
                    />
                  }
                />
              </Form.Item>
            </>
          )}
          <Row gutter={16}>
            <Col xs={24} sm={8}>
              <Form.Item
                name="lastName"
                label="Фамилия"
                rules={[{ required: true, message: 'Укажите фамилию' }]}
              >
                <Input maxLength={100} />
              </Form.Item>
            </Col>
            <Col xs={24} sm={8}>
              <Form.Item name="firstName" label="Имя" rules={[{ required: true, message: 'Укажите имя' }]}>
                <Input maxLength={100} />
              </Form.Item>
            </Col>
            <Col xs={24} sm={8}>
              <Form.Item name="middleName" label="Отчество">
                <Input maxLength={100} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="position" label="Должность">
            <Input maxLength={150} placeholder="Сервисный инженер" />
          </Form.Item>
          <Form.Item name="serviceDepartmentId" label="Отдел">
            <ServiceDepartmentSelect />
          </Form.Item>
          <Row gutter={16}>
            <Col xs={24} sm={12}>
              <Form.Item name="phone" label="Телефон">
                <Input maxLength={50} inputMode="tel" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="email" label="Почта" rules={[{ type: 'email', message: 'Неверный адрес' }]}>
                <Input maxLength={150} inputMode="email" />
              </Form.Item>
            </Col>
          </Row>
          {engineer && (
            <Form.Item
              name="active"
              label="Учётная запись активна"
              valuePropName="checked"
              extra="Если выключить, инженер не сможет войти в систему; его акты и часы сохранятся"
            >
              <Switch />
            </Form.Item>
          )}
        </Form>
      )}
    </Drawer>
  );
}

/** Инженеры сервисной компании. Создавать и изменять может только администратор. */
export function EngineersPage() {
  const { isAdmin } = useRoles();
  const [q, setQ] = useState('');
  const [departmentId, setDepartmentId] = useState<string>();
  const [active, setActive] = useState<'active' | 'blocked' | 'all'>('active');
  const [page, setPage] = useState({ page: 0, size: 20, sort: undefined as string | undefined });
  const { data, isFetching } = useListEngineers({
    q: q || undefined,
    serviceDepartmentId: departmentId,
    active: active === 'all' ? undefined : active === 'active',
    page: page.page,
    size: page.size,
    sort: page.sort,
  });
  const [form, setForm] = useState<{ open: boolean; engineer?: Engineer }>({ open: false });
  const result = ok(data);

  return (
    <>
      <div className="toolbar">
        <Input.Search
          allowClear
          placeholder="ФИО или логин"
          onSearch={(v) => {
            setQ(v.trim());
            setPage((p) => ({ ...p, page: 0 }));
          }}
          style={{ maxWidth: 280 }}
        />
        <ServiceDepartmentSelect value={departmentId} onChange={setDepartmentId} style={{ width: 260 }} />
        <Segmented
          value={active}
          onChange={(v) => setActive(v as typeof active)}
          options={[
            { value: 'active', label: 'Работают' },
            { value: 'blocked', label: 'Заблокированы' },
            { value: 'all', label: 'Все' },
          ]}
        />
        <div className="toolbar-spacer" />
        {isAdmin && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setForm({ open: true })}>
            Добавить инженера
          </Button>
        )}
      </div>
      <div className="page-panel">
        <Table<Engineer>
          rowKey="id"
          loading={isFetching}
          dataSource={result?.items}
          scroll={{ x: 820 }}
          onChange={(p, _f, sorter) =>
            setPage({ page: (p.current ?? 1) - 1, size: p.pageSize ?? 20, sort: sortParam(sorter) })
          }
          pagination={{
            current: page.page + 1,
            pageSize: page.size,
            total: result?.totalElements,
            showTotal: (t) => `Всего: ${t}`,
          }}
          columns={[
            {
              title: 'Инженер',
              key: 'lastName',
              sorter: true,
              render: (_, e) => (
                <Space direction="vertical" size={0}>
                  <span className="cell-strong">
                    {[e.lastName, e.firstName, e.middleName].filter(Boolean).join(' ')}
                  </span>
                  <span className="cell-muted">{e.position ?? '—'}</span>
                </Space>
              ),
            },
            { title: 'Логин', key: 'username', dataIndex: 'username', sorter: true, width: 150 },
            {
              title: 'Отдел',
              key: 'serviceDepartmentName',
              dataIndex: 'serviceDepartmentName',
              sorter: true,
            },
            { title: 'Телефон', dataIndex: 'phone', width: 170 },
            {
              title: '',
              key: 'actions',
              width: 150,
              render: (_, e) => (
                <Space>
                  {!e.active && <Tag color="default">Заблокирован</Tag>}
                  {isAdmin && (
                    <Button
                      type="text"
                      icon={<EditOutlined />}
                      title="Изменить"
                      onClick={() => setForm({ open: true, engineer: e })}
                    />
                  )}
                </Space>
              ),
            },
          ]}
        />
      </div>
      <EngineerForm open={form.open} engineer={form.engineer} onClose={() => setForm({ open: false })} />
    </>
  );
}

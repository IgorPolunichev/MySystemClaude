import { useState } from 'react';
import {
  App,
  Button,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Popconfirm,
  Space,
  Switch,
  Tag,
  Typography,
} from 'antd';
import { EditOutlined, PlusOutlined, StopOutlined, UndoOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import { useCreateSite, useListOrganizationSites, useUpdateSite } from '../../api/generated/directory/sites';
import type { Site, SiteInput } from '../../api/generated/directory/model';
import { ok } from '../../api/unwrap';
import { applyFormError, errorText } from '../../shared/lib/apiErrors';
import { invalidateByPrefix } from '../../shared/lib/invalidate';
import { useRoles } from '../../shared/lib/useRoles';

/**
 * Производственные площадки организации. Площадку не удаляют, а блокируют: её больше нельзя выбрать для
 * техники и новых актов, но в выданных актах она остаётся.
 */
export function SitesPanel({ organizationId }: { organizationId: string }) {
  const { message } = App.useApp();
  const { canEdit } = useRoles();
  const client = useQueryClient();
  const [showBlocked, setShowBlocked] = useState(false);
  const { data, isLoading } = useListOrganizationSites(organizationId, { includeInactive: true });
  const all = ok(data) ?? [];
  const sites = showBlocked ? all : all.filter((s) => s.active);
  const create = useCreateSite();
  const update = useUpdateSite();
  const [form] = Form.useForm<SiteInput>();
  const [editing, setEditing] = useState<{ site?: Site } | null>(null);

  const refresh = () =>
    invalidateByPrefix(client, `/api/v1/organizations/${organizationId}/sites`, '/api/v1/equipment');

  const open = (site?: Site) => {
    form.resetFields();
    form.setFieldsValue(site ? { ...site } : {});
    setEditing({ site });
  };

  const save = async (values: SiteInput) => {
    const site = editing?.site;
    const body: SiteInput = {
      ...values,
      organizationId,
      version: site?.version,
      active: site?.active ?? true,
    };
    try {
      if (site) await update.mutateAsync({ id: site.id, data: body });
      else await create.mutateAsync({ data: body });
      await refresh();
      setEditing(null);
    } catch (e) {
      message.error(applyFormError(form, e));
    }
  };

  const toggle = async (site: Site) => {
    try {
      await update.mutateAsync({ id: site.id, data: { ...site, organizationId, active: !site.active } });
      await refresh();
      message.success(site.active ? 'Площадка заблокирована' : 'Площадка снова доступна');
    } catch (e) {
      message.error(errorText(e));
    }
  };

  const blocked = all.filter((s) => !s.active).length;

  return (
    <div>
      <div className="section-head">
        <Typography.Title level={5} style={{ margin: 0 }}>
          Производственные площадки
        </Typography.Title>
        <Space>
          {blocked > 0 && (
            <Space size={4}>
              <Switch size="small" checked={showBlocked} onChange={setShowBlocked} />
              <span className="cell-muted">заблокированные ({blocked})</span>
            </Space>
          )}
          <Button size="small" icon={<PlusOutlined />} onClick={() => open()}>
            Добавить
          </Button>
        </Space>
      </div>
      {!isLoading && sites.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Площадок нет" />
      ) : (
        <List
          size="small"
          loading={isLoading}
          dataSource={sites}
          renderItem={(s) => (
            <List.Item
              actions={
                canEdit
                  ? [
                      <Button
                        key="e"
                        type="text"
                        size="small"
                        icon={<EditOutlined />}
                        title="Изменить"
                        onClick={() => open(s)}
                      />,
                      <Popconfirm
                        key="b"
                        title={s.active ? 'Заблокировать площадку?' : 'Разблокировать площадку?'}
                        description={
                          s.active ? 'Её нельзя будет выбрать для техники и новых актов.' : undefined
                        }
                        okText={s.active ? 'Заблокировать' : 'Разблокировать'}
                        cancelText="Отмена"
                        onConfirm={() => void toggle(s)}
                      >
                        <Button
                          type="text"
                          size="small"
                          danger={s.active}
                          icon={s.active ? <StopOutlined /> : <UndoOutlined />}
                          title={s.active ? 'Заблокировать' : 'Разблокировать'}
                        />
                      </Popconfirm>,
                    ]
                  : []
              }
            >
              <List.Item.Meta
                title={
                  <Space>
                    <span className={s.active ? undefined : 'cell-muted'}>{s.name}</span>
                    {s.code && <Tag className="cell-codes">{s.code}</Tag>}
                    {!s.active && <Tag>заблокирована</Tag>}
                  </Space>
                }
                description={s.address}
              />
            </List.Item>
          )}
        />
      )}
      <Modal
        open={!!editing}
        title={editing?.site ? 'Площадка' : 'Новая площадка'}
        okText="Сохранить"
        cancelText="Отмена"
        onOk={() => form.submit()}
        onCancel={() => setEditing(null)}
        confirmLoading={create.isPending || update.isPending}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={(v) => void save(v)}>
          <Form.Item name="name" label="Название" rules={[{ required: true, message: 'Укажите название' }]}>
            <Input maxLength={200} autoFocus placeholder="Площадка А" />
          </Form.Item>
          <Form.Item
            name="code"
            label="Код площадки"
            extra="Если указан, входит в номер акта: АВР-А-2026-000001. 1–6 букв или цифр"
            rules={[{ pattern: /^[\p{L}\p{Nd}]{1,6}$/u, message: 'От 1 до 6 букв или цифр' }]}
          >
            <Input maxLength={6} style={{ width: 120 }} placeholder="А" />
          </Form.Item>
          <Form.Item name="address" label="Адрес">
            <Input maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

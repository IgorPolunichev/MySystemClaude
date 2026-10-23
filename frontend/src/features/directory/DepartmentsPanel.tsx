import { useMemo, useState } from 'react';
import { App, Button, Empty, Form, Input, Modal, Popconfirm, Space, Spin, Tree, Typography } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import {
  useCreateDepartment,
  useDeleteDepartment,
  useListOrganizationDepartments,
  useUpdateDepartment,
} from '../../api/generated/directory/departments';
import type { Department, DepartmentInput } from '../../api/generated/directory/model';
import { ok } from '../../api/unwrap';
import { applyFormError, errorText } from '../../shared/lib/apiErrors';
import { invalidateByPrefix } from '../../shared/lib/invalidate';
import { useRoles } from '../../shared/lib/useRoles';
import { departmentTree } from './departmentTree';
import { DepartmentSelect } from './lookups';

/** Дерево подразделений организации: добавить, переименовать, перенести, удалить. */
export function DepartmentsPanel({ organizationId }: { organizationId: string }) {
  const { message } = App.useApp();
  const { canEdit } = useRoles();
  const client = useQueryClient();
  const { data, isLoading } = useListOrganizationDepartments(organizationId, { query: {} });
  const departments = useMemo(() => ok(data) ?? [], [data]);
  const tree = useMemo(() => departmentTree(departments), [departments]);
  const create = useCreateDepartment();
  const update = useUpdateDepartment();
  const remove = useDeleteDepartment();
  const [form] = Form.useForm<DepartmentInput>();
  const [editing, setEditing] = useState<{ department?: Department; parentId?: string } | null>(null);

  const refresh = () =>
    invalidateByPrefix(client, `/api/v1/organizations/${organizationId}/departments`, '/api/v1/equipment');

  const openEditor = (department?: Department, parentId?: string) => {
    form.resetFields();
    form.setFieldsValue(department ? { ...department } : { parentId: parentId ?? null });
    setEditing({ department, parentId });
  };

  const save = async (values: DepartmentInput) => {
    const dep = editing?.department;
    const body = { ...values, organizationId, version: dep?.version, parentId: values.parentId ?? null };
    try {
      if (dep) await update.mutateAsync({ id: dep.id, data: body });
      else await create.mutateAsync({ data: body });
      await refresh();
      setEditing(null);
    } catch (e) {
      message.error(applyFormError(form, e));
    }
  };

  const del = async (id: string) => {
    try {
      await remove.mutateAsync({ id });
      await refresh();
      message.success('Подразделение удалено');
    } catch (e) {
      message.error(errorText(e));
    }
  };

  const byId = new Map(departments.map((d) => [d.id, d]));

  return (
    <div>
      <div className="section-head">
        <Typography.Title level={5} style={{ margin: 0 }}>
          Подразделения
        </Typography.Title>
        <Button size="small" icon={<PlusOutlined />} onClick={() => openEditor()}>
          Добавить
        </Button>
      </div>
      {isLoading ? (
        <Spin />
      ) : tree.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Подразделений пока нет" />
      ) : (
        <Tree
          treeData={tree}
          defaultExpandAll
          selectable={false}
          blockNode
          titleRender={(node) => {
            const d = byId.get(String(node.key));
            return (
              <span className="tree-row">
                <span>
                  {d?.name}
                  {d?.code && <Typography.Text type="secondary"> · {d.code}</Typography.Text>}
                </span>
                <Space size={0} className="tree-actions">
                  <Button
                    type="text"
                    size="small"
                    icon={<PlusOutlined />}
                    title="Добавить вложенное"
                    onClick={() => openEditor(undefined, d?.id)}
                  />
                  {canEdit && d && (
                    <>
                      <Button
                        type="text"
                        size="small"
                        icon={<EditOutlined />}
                        title="Изменить"
                        onClick={() => openEditor(d)}
                      />
                      <Popconfirm
                        title="Удалить подразделение?"
                        okText="Удалить"
                        cancelText="Отмена"
                        onConfirm={() => void del(d.id)}
                      >
                        <Button type="text" size="small" danger icon={<DeleteOutlined />} title="Удалить" />
                      </Popconfirm>
                    </>
                  )}
                </Space>
              </span>
            );
          }}
        />
      )}
      <Modal
        open={!!editing}
        title={editing?.department ? 'Подразделение' : 'Новое подразделение'}
        okText="Сохранить"
        cancelText="Отмена"
        onCancel={() => setEditing(null)}
        onOk={() => form.submit()}
        confirmLoading={create.isPending || update.isPending}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={(v) => void save(v)}>
          <Form.Item name="name" label="Название" rules={[{ required: true, message: 'Укажите название' }]}>
            <Input maxLength={200} autoFocus />
          </Form.Item>
          <Form.Item name="code" label="Код или сокращение">
            <Input maxLength={50} />
          </Form.Item>
          <Form.Item
            name="parentId"
            label="Входит в"
            extra="Оставьте пустым для подразделения верхнего уровня"
          >
            <DepartmentSelect
              organizationId={organizationId}
              excludeId={editing?.department?.id}
              placeholder="Верхний уровень"
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

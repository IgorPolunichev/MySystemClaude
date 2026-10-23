import { useEffect } from 'react';
import { App, Button, Col, Drawer, Form, Input, Row, Space, Switch } from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import { useCreateOrganization, useUpdateOrganization } from '../../api/generated/directory/organizations';
import type { Organization, OrganizationInput } from '../../api/generated/directory/model';
import { applyFormError } from '../../shared/lib/apiErrors';
import { invalidateByPrefix } from '../../shared/lib/invalidate';
import { emptyToNull } from '../../shared/lib/table';

interface Props {
  open: boolean;
  organization?: Organization;
  onClose: () => void;
}

const digits = (len: number[]) => ({
  validator: (_: unknown, v?: string) =>
    !v || (/^\d+$/.test(v) && len.includes(v.length))
      ? Promise.resolve()
      : Promise.reject(new Error(`Нужно ${len.join(' или ')} цифр`)),
});

/** Создание и изменение организации-заказчика. */
export function OrganizationForm({ open, organization, onClose }: Props) {
  const [form] = Form.useForm<OrganizationInput>();
  const { message } = App.useApp();
  const client = useQueryClient();
  const create = useCreateOrganization();
  const update = useUpdateOrganization();
  const editing = !!organization;

  useEffect(() => {
    if (open) {
      form.resetFields();
      form.setFieldsValue(organization ? { ...organization } : { active: true });
    }
  }, [open, organization, form]);

  const submit = async (values: OrganizationInput) => {
    const data = emptyToNull({ ...values, version: organization?.version });
    try {
      if (organization) await update.mutateAsync({ id: organization.id, data });
      else await create.mutateAsync({ data });
      await invalidateByPrefix(client, '/api/v1/organizations');
      message.success(editing ? 'Организация сохранена' : 'Организация добавлена');
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
      title={editing ? 'Организация' : 'Новая организация'}
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
      <Form form={form} layout="vertical" onFinish={(v) => void submit(v)} requiredMark="optional">
        <Form.Item
          name="name"
          label="Полное наименование"
          rules={[{ required: true, message: 'Укажите наименование' }]}
        >
          <Input maxLength={300} placeholder="Акционерное общество «Пример»" />
        </Form.Item>
        <Form.Item
          name="shortName"
          label="Краткое наименование"
          extra="Так организация будет показана в списках и актах"
        >
          <Input maxLength={150} placeholder="АО «Пример»" />
        </Form.Item>
        <Row gutter={16}>
          <Col xs={24} sm={8}>
            <Form.Item name="inn" label="ИНН" rules={[digits([10, 12])]}>
              <Input maxLength={12} inputMode="numeric" />
            </Form.Item>
          </Col>
          <Col xs={24} sm={8}>
            <Form.Item name="kpp" label="КПП">
              <Input maxLength={9} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={8}>
            <Form.Item name="ogrn" label="ОГРН" rules={[digits([13, 15])]}>
              <Input maxLength={15} inputMode="numeric" />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="legalAddress" label="Юридический адрес">
          <Input maxLength={500} />
        </Form.Item>
        <Form.Item name="actualAddress" label="Фактический адрес">
          <Input maxLength={500} />
        </Form.Item>
        <Form.Item name="bankDetails" label="Банковские реквизиты">
          <Input.TextArea maxLength={1000} autoSize={{ minRows: 2, maxRows: 5 }} />
        </Form.Item>
        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item name="phone" label="Телефон">
              <Input maxLength={50} inputMode="tel" />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item
              name="email"
              label="Электронная почта"
              rules={[{ type: 'email', message: 'Неверный адрес' }]}
            >
              <Input maxLength={150} inputMode="email" />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="notes" label="Примечание">
          <Input.TextArea maxLength={2000} autoSize={{ minRows: 2, maxRows: 6 }} />
        </Form.Item>
        <Form.Item
          name="active"
          label="Активна"
          valuePropName="checked"
          extra="Неактивные организации не предлагаются при выборе в актах"
        >
          <Switch />
        </Form.Item>
      </Form>
    </Drawer>
  );
}

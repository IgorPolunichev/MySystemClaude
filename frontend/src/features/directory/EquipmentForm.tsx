import { useEffect } from 'react';
import { App, Button, Col, Drawer, Form, Input, InputNumber, Radio, Row, Space } from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import { useCreateEquipment, useUpdateEquipment } from '../../api/generated/directory/equipment';
import type { Equipment, EquipmentInput } from '../../api/generated/directory/model';
import { applyFormError } from '../../shared/lib/apiErrors';
import { invalidateByPrefix } from '../../shared/lib/invalidate';
import { emptyToNull } from '../../shared/lib/table';
import { DepartmentSelect, EquipmentTypeSelect, OrganizationSelect } from './lookups';

interface Props {
  open: boolean;
  equipment?: Equipment;
  /** Предзаполнение при добавлении из отфильтрованного списка. */
  defaults?: Partial<EquipmentInput>;
  onClose: () => void;
}

/** Создание и изменение единицы техники. */
export function EquipmentForm({ open, equipment, defaults, onClose }: Props) {
  const [form] = Form.useForm<EquipmentInput>();
  const { message } = App.useApp();
  const client = useQueryClient();
  const create = useCreateEquipment();
  const update = useUpdateEquipment();
  const organizationId = Form.useWatch('organizationId', form);
  const meterType = Form.useWatch('meterType', form) ?? 'HOURS';

  useEffect(() => {
    if (open) {
      form.resetFields();
      form.setFieldsValue(equipment ? { ...equipment } : { meterType: 'HOURS', ...defaults });
    }
  }, [open, equipment, defaults, form]);

  const submit = async (values: EquipmentInput) => {
    const data = emptyToNull({ ...values, version: equipment?.version });
    if (data.meterType === 'KM') data.currentHours = null;
    if (data.meterType === 'HOURS') data.currentKm = null;
    try {
      if (equipment) await update.mutateAsync({ id: equipment.id, data });
      else await create.mutateAsync({ data });
      await invalidateByPrefix(client, '/api/v1/equipment');
      message.success(equipment ? 'Техника сохранена' : 'Техника добавлена');
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
      title={equipment ? `${equipment.brand} ${equipment.model}` : 'Новая техника'}
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
      <Form
        form={form}
        layout="vertical"
        requiredMark="optional"
        onFinish={(v) => void submit(v)}
        onValuesChange={(changed) => {
          if ('organizationId' in changed) form.setFieldValue('departmentId', null);
        }}
      >
        <Form.Item
          name="organizationId"
          label="Организация"
          rules={[{ required: true, message: 'Выберите организацию' }]}
        >
          <OrganizationSelect />
        </Form.Item>
        <Form.Item name="departmentId" label="Подразделение">
          <DepartmentSelect organizationId={organizationId} />
        </Form.Item>
        <Form.Item
          name="equipmentTypeId"
          label="Тип техники"
          rules={[{ required: true, message: 'Выберите тип' }]}
        >
          <EquipmentTypeSelect />
        </Form.Item>
        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item name="brand" label="Марка" rules={[{ required: true, message: 'Укажите марку' }]}>
              <Input maxLength={100} placeholder="CAT" />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="model" label="Модель" rules={[{ required: true, message: 'Укажите модель' }]}>
              <Input maxLength={100} placeholder="777G" />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item
          name="serialNumber"
          label="Серийный номер / VIN / PIN"
          extra="Уникален в пределах организации"
        >
          <Input maxLength={100} className="input-code" />
        </Form.Item>
        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item name="garageNumber" label="Гаражный номер">
              <Input maxLength={50} className="input-code" />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="plateNumber" label="Госномер">
              <Input maxLength={20} className="input-code" placeholder="А123ВС77" />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="meterType" label="Учёт наработки">
          <Radio.Group
            optionType="button"
            options={[
              { value: 'HOURS', label: 'Моточасы' },
              { value: 'KM', label: 'Пробег' },
              { value: 'BOTH', label: 'Оба' },
            ]}
          />
        </Form.Item>
        <Row gutter={16}>
          {meterType !== 'KM' && (
            <Col xs={24} sm={12}>
              <Form.Item name="currentHours" label="Текущие моточасы">
                <InputNumber min={0} step={0.1} style={{ width: '100%' }} addonAfter="м/ч" />
              </Form.Item>
            </Col>
          )}
          {meterType !== 'HOURS' && (
            <Col xs={24} sm={12}>
              <Form.Item name="currentKm" label="Текущий пробег">
                <InputNumber min={0} step={1} style={{ width: '100%' }} addonAfter="км" />
              </Form.Item>
            </Col>
          )}
        </Row>
        <Form.Item name="year" label="Год выпуска">
          <InputNumber min={1950} max={2100} style={{ width: 160 }} />
        </Form.Item>
        <Form.Item name="notes" label="Примечание">
          <Input.TextArea maxLength={2000} autoSize={{ minRows: 2, maxRows: 6 }} />
        </Form.Item>
      </Form>
    </Drawer>
  );
}

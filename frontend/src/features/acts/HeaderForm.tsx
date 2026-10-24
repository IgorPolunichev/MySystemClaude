import { useState } from 'react';
import {
  App,
  AutoComplete,
  Button,
  Col,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Switch,
  Typography,
} from 'antd';
import { SaveOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import { useQueryClient } from '@tanstack/react-query';
import { getGetActQueryKey, useListShortDescriptions, useUpdateAct } from '../../api/generated/acts/acts';
import type { ActDetail, ActHeaderInput } from '../../api/generated/acts/model';
import { useGetEquipment } from '../../api/generated/directory/equipment';
import { ok } from '../../api/unwrap';
import { applyFormError } from '../../shared/lib/apiErrors';
import { invalidateByPrefix } from '../../shared/lib/invalidate';
import { emptyToNull } from '../../shared/lib/table';
import { isoDate } from './actMeta';
import { EquipmentSelect } from './lookups';
import { ExecutorSelect, SiteSelect } from '../directory/lookups';

type FormValues = Omit<ActHeaderInput, 'workStartDate' | 'workEndDate' | 'version'> & {
  workStartDate?: Dayjs | null;
  workEndDate?: Dayjs | null;
};

function toForm(act: ActDetail): FormValues {
  return {
    equipmentId: act.equipment?.id ?? null,
    executorId: act.executor?.id ?? null,
    siteId: act.site?.id ?? null,
    meterHours: act.meterHours,
    meterKm: act.meterKm,
    location: act.location,
    workStartDate: act.periodOverridden && act.workStartDate ? dayjs(act.workStartDate) : null,
    workEndDate: act.periodOverridden && act.workEndDate ? dayjs(act.workEndDate) : null,
    shortDescription: act.shortDescription,
    detailedDescription: act.detailedDescription,
    recommendations: act.recommendations,
    customerRepName: act.customerRepName,
    customerRepPosition: act.customerRepPosition,
    hideRequisites: act.hideRequisites,
    hideTravelTime: act.hideTravelTime,
  };
}

/**
 * Шапка и описания акта. Форма пересоздаётся при смене версии акта (key в родителе), поэтому после
 * сохранения всегда показывает данные сервера.
 */
export function HeaderForm({ act }: { act: ActDetail }) {
  const [form] = Form.useForm<FormValues>();
  const { message } = App.useApp();
  const client = useQueryClient();
  const update = useUpdateAct();
  const [dirty, setDirty] = useState(false);
  const [commentFor, setCommentFor] = useState<FormValues | null>(null);
  const [adminComment, setAdminComment] = useState('');
  const editable = act.permissions.canEdit;

  const equipmentId = Form.useWatch('equipmentId', form);
  const selected = ok(
    useGetEquipment(equipmentId ?? '', {
      query: { enabled: !!equipmentId && equipmentId !== act.equipment?.id },
    }).data,
  );
  const meterType = equipmentId === act.equipment?.id ? act.equipment?.meterType : selected?.meterType;
  const [shortQuery, setShortQuery] = useState('');
  const shortOptions =
    ok(useListShortDescriptions({ q: shortQuery || undefined }, { query: { enabled: editable } }).data) ?? [];

  const save = async (values: FormValues, comment?: string) => {
    const data: ActHeaderInput = emptyToNull({
      ...values,
      version: act.version,
      workStartDate: values.workStartDate ? isoDate(values.workStartDate.toDate()) : null,
      workEndDate: values.workEndDate ? isoDate(values.workEndDate.toDate()) : null,
      adminComment: comment ?? null,
    });
    try {
      await update.mutateAsync({ id: act.id, data });
      await client.invalidateQueries({ queryKey: getGetActQueryKey(act.id) });
      await invalidateByPrefix(client, '/api/v1/acts?', '/api/v1/acts/short-descriptions');
      message.success('Акт сохранён');
      setCommentFor(null);
    } catch (e) {
      message.error(applyFormError(form, e));
    }
  };

  const submit = (values: FormValues) => {
    if (act.permissions.requiresAdminComment) {
      setAdminComment('');
      setCommentFor(values);
    } else {
      void save(values);
    }
  };

  return (
    <>
      <Form
        form={form}
        layout="vertical"
        initialValues={toForm(act)}
        disabled={!editable}
        onFinish={submit}
        onValuesChange={(changed) => {
          setDirty(true);
          // Новая техника — площадку подставит сервер по технике.
          if ('equipmentId' in changed) form.setFieldValue('siteId', null);
        }}
      >
        <div className="act-section">
          <Typography.Title level={5}>Стороны и техника</Typography.Title>
          <Form.Item
            name="executorId"
            label="Исполнитель"
            extra="Компания, от имени которой составляется акт"
          >
            <ExecutorSelect />
          </Form.Item>
          <Form.Item
            name="equipmentId"
            label="Техника"
            extra={
              act.organization
                ? `Заказчик: ${act.organization.shortName ?? act.organization.name}${act.organization.departmentName ? ' · ' + act.organization.departmentName : ''}`
                : undefined
            }
          >
            <EquipmentSelect disabled={!editable} />
          </Form.Item>
          {act.organization && equipmentId === act.equipment?.id && (
            <Form.Item
              name="siteId"
              label="Производственная площадка"
              extra="По умолчанию — площадка техники"
            >
              <SiteSelect organizationId={act.organization.id} />
            </Form.Item>
          )}
          <Row gutter={16}>
            {meterType !== 'KM' && (
              <Col xs={24} sm={8}>
                <Form.Item name="meterHours" label="Моточасы">
                  <InputNumber min={0} step={0.1} style={{ width: '100%' }} addonAfter="м/ч" />
                </Form.Item>
              </Col>
            )}
            {meterType && meterType !== 'HOURS' && (
              <Col xs={24} sm={8}>
                <Form.Item name="meterKm" label="Пробег">
                  <InputNumber min={0} step={1} style={{ width: '100%' }} addonAfter="км" />
                </Form.Item>
              </Col>
            )}
            <Col xs={24} sm={8}>
              <Form.Item name="location" label="Место проведения работ">
                <Input maxLength={300} placeholder="Карьер, бокс № 2" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col xs={24} sm={12}>
              <Form.Item name="customerRepName" label="Представитель заказчика (ФИО)">
                <Input maxLength={200} placeholder="Сидоров А. В." />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="customerRepPosition" label="Должность представителя">
                <Input maxLength={200} placeholder="Механик" />
              </Form.Item>
            </Col>
          </Row>
        </div>

        <div className="act-section">
          <Typography.Title level={5}>Выполненные работы</Typography.Title>
          <Form.Item
            name="shortDescription"
            label="Краткое описание"
            extra="Например: ТО-2000, замена ГМП. Подсказки — из прошлых актов"
          >
            <AutoComplete
              maxLength={100}
              options={shortOptions.map((s) => ({ value: s }))}
              onSearch={setShortQuery}
              placeholder="ТО-2000"
            />
          </Form.Item>
          <Form.Item name="detailedDescription" label="Подробное описание выполненных работ">
            <Input.TextArea maxLength={10000} autoSize={{ minRows: 4, maxRows: 16 }} showCount />
          </Form.Item>
          <Form.Item name="recommendations" label="Рекомендации" extra="Попадут в технический отчёт">
            <Input.TextArea maxLength={10000} autoSize={{ minRows: 2, maxRows: 10 }} />
          </Form.Item>
        </div>

        <div className="act-section">
          <Typography.Title level={5}>Период и печатная форма</Typography.Title>
          <Row gutter={16}>
            <Col xs={24} sm={12}>
              <Form.Item name="workStartDate" label="Начало работ" extra="Пусто — по первой дате в табеле">
                <DatePicker format="DD.MM.YYYY" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                name="workEndDate"
                label="Окончание работ"
                extra="Пусто — по последней дате в табеле"
              >
                <DatePicker format="DD.MM.YYYY" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col xs={24} sm={12}>
              <Form.Item
                name="hideRequisites"
                label="Скрыть реквизиты компаний"
                valuePropName="checked"
                extra="В акте останутся только наименования"
              >
                <Switch />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                name="hideTravelTime"
                label="Скрыть время в пути"
                valuePropName="checked"
                extra="Время в пути учитывается, но не печатается"
              >
                <Switch />
              </Form.Item>
            </Col>
          </Row>
        </div>

        {editable && (
          <div className="sticky-save">
            <span className="cell-muted">
              {dirty ? 'Есть несохранённые изменения' : 'Все изменения сохранены'}
            </span>
            <Button
              type="primary"
              htmlType="submit"
              icon={<SaveOutlined />}
              loading={update.isPending}
              disabled={!dirty}
            >
              Сохранить
            </Button>
          </div>
        )}
      </Form>
      <Modal
        open={!!commentFor}
        title="Изменение закрытого акта"
        okText="Сохранить"
        cancelText="Отмена"
        okButtonProps={{ disabled: adminComment.trim() === '' }}
        confirmLoading={update.isPending}
        onCancel={() => setCommentFor(null)}
        onOk={() => commentFor && void save(commentFor, adminComment.trim())}
      >
        <Typography.Paragraph>
          Акт уже закрыт. Укажите, что и почему исправляется — это попадёт в журнал.
        </Typography.Paragraph>
        <Input.TextArea
          value={adminComment}
          onChange={(e) => setAdminComment(e.target.value)}
          maxLength={1000}
          autoSize={{ minRows: 3 }}
          autoFocus
        />
      </Modal>
    </>
  );
}

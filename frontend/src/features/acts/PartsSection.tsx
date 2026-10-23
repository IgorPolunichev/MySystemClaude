import { useState } from 'react';
import {
  App,
  AutoComplete,
  Button,
  Checkbox,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import { getGetActQueryKey } from '../../api/generated/acts/acts';
import { useAddActPart, useDeleteActPart, useUpdateActPart } from '../../api/generated/acts/act-content';
import type { ActDetail, ActPartLine, PartLineInput } from '../../api/generated/acts/model';
import { useListParts } from '../../api/generated/parts/parts';
import { useListUnits } from '../../api/generated/parts/units';
import { ok } from '../../api/unwrap';
import { applyFormError, errorText } from '../../shared/lib/apiErrors';

const qty = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 3 });

type FormValues = PartLineInput;

function PartLineDialog({
  act,
  line,
  open,
  onClose,
}: {
  act: ActDetail;
  line?: ActPartLine;
  open: boolean;
  onClose: () => void;
}) {
  const [form] = Form.useForm<FormValues>();
  const { message } = App.useApp();
  const client = useQueryClient();
  const add = useAddActPart();
  const update = useUpdateActPart();
  const units = ok(useListUnits().data) ?? [];
  const [q, setQ] = useState('');
  const catalog =
    ok(useListParts({ q: q || undefined, size: 15 }, { query: { enabled: open && q.length >= 2 } }).data)
      ?.items ?? [];
  const source = Form.useWatch('source', form) ?? 'MANUAL';
  const unitCode = Form.useWatch('unitCode', form);
  const fractional = units.find((u) => u.code === unitCode)?.allowsFraction ?? false;

  const initial: FormValues = line
    ? {
        source: line.source,
        catalogItemId: line.catalogItemId,
        name: line.name,
        catalogNumber: line.catalogNumber,
        quantity: line.quantity,
        unitCode: line.unitCode,
        notes: line.notes,
      }
    : { source: 'MANUAL', name: '', quantity: 1, unitCode: 'PCS' };

  const submit = async (values: FormValues) => {
    const data: PartLineInput = { ...initial, ...values, source: values.source ?? 'MANUAL' };
    try {
      if (line) await update.mutateAsync({ id: act.id, lineId: line.id, data });
      else await add.mutateAsync({ id: act.id, data });
      await client.invalidateQueries({ queryKey: getGetActQueryKey(act.id) });
      onClose();
    } catch (e) {
      message.error(applyFormError(form, e));
    }
  };

  return (
    <Modal
      open={open}
      title={line ? 'Позиция' : 'Добавить запчасть или жидкость'}
      okText="Сохранить"
      cancelText="Отмена"
      onOk={() => form.submit()}
      onCancel={onClose}
      confirmLoading={add.isPending || update.isPending}
      width={620}
      destroyOnHidden
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={initial}
        onFinish={(v) => void submit(v)}
        preserve={false}
      >
        <Form.Item name="source" hidden>
          <Input />
        </Form.Item>
        <Form.Item name="catalogItemId" hidden>
          <Input />
        </Form.Item>
        <Form.Item
          name="name"
          label="Наименование"
          rules={[{ required: true, message: 'Укажите наименование' }]}
          extra={
            source === 'CATALOG'
              ? 'Из каталога'
              : 'Начните вводить — найдутся позиции каталога; можно ввести новую'
          }
        >
          <AutoComplete
            onSearch={(v) => {
              setQ(v);
              if (form.getFieldValue('source') === 'CATALOG') {
                form.setFieldsValue({ source: 'MANUAL', catalogItemId: null });
              }
            }}
            onSelect={(_value: string, option: { partId?: string }) => {
              const p = catalog.find((c) => c.id === option.partId);
              if (p) {
                form.setFieldsValue({
                  source: 'CATALOG',
                  catalogItemId: p.id,
                  name: p.name,
                  catalogNumber: p.catalogNumber,
                  unitCode: p.defaultUnit,
                });
              }
            }}
            options={catalog.map((p) => ({
              key: p.id,
              partId: p.id,
              value: p.name,
              label: (
                <Space direction="vertical" size={0}>
                  <span>{p.name}</span>
                  <span className="cell-codes cell-muted">
                    {[p.catalogNumber, p.manufacturer].filter(Boolean).join(' · ')}
                  </span>
                </Space>
              ),
            }))}
          >
            <Input maxLength={300} placeholder="Фильтр масляный" />
          </AutoComplete>
        </Form.Item>
        <Row gutter={16}>
          <Col xs={24} sm={10}>
            <Form.Item name="catalogNumber" label="Номер по каталогу">
              <Input maxLength={100} className="input-code" disabled={source === 'CATALOG'} />
            </Form.Item>
          </Col>
          <Col xs={12} sm={7}>
            <Form.Item
              name="quantity"
              label="Количество"
              rules={[{ required: true, message: 'Укажите количество' }]}
            >
              <InputNumber
                min={fractional ? 0.001 : 1}
                step={fractional ? 0.5 : 1}
                precision={fractional ? 3 : 0}
                style={{ width: '100%' }}
              />
            </Form.Item>
          </Col>
          <Col xs={12} sm={7}>
            <Form.Item name="unitCode" label="Ед. изм.">
              <Select options={units.map((u) => ({ value: u.code, label: u.name }))} />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="notes" label="Примечание">
          <Input maxLength={500} />
        </Form.Item>
        {source === 'MANUAL' && !line && (
          <Form.Item name="addToCatalog" valuePropName="checked">
            <Checkbox>Добавить в каталог, чтобы в следующий раз выбрать из списка</Checkbox>
          </Form.Item>
        )}
      </Form>
    </Modal>
  );
}

/** Запчасти и жидкости, использованные в работах. */
export function PartsSection({ act }: { act: ActDetail }) {
  const { message } = App.useApp();
  const client = useQueryClient();
  const remove = useDeleteActPart();
  const [dialog, setDialog] = useState<{ open: boolean; line?: ActPartLine }>({ open: false });
  const editable = act.permissions.canEdit;

  const del = async (line: ActPartLine) => {
    try {
      await remove.mutateAsync({ id: act.id, lineId: line.id });
      await client.invalidateQueries({ queryKey: getGetActQueryKey(act.id) });
    } catch (e) {
      message.error(errorText(e));
    }
  };

  return (
    <div className="act-section">
      <div className="section-head">
        <Typography.Title level={5} style={{ margin: 0 }}>
          Запчасти и материалы
        </Typography.Title>
        {editable && (
          <Button size="small" icon={<PlusOutlined />} onClick={() => setDialog({ open: true })}>
            Добавить
          </Button>
        )}
      </div>
      <Table<ActPartLine>
        rowKey="id"
        size="small"
        pagination={false}
        dataSource={act.parts}
        scroll={{ x: 640 }}
        locale={{ emptyText: 'Запчасти не добавлены' }}
        columns={[
          { title: '№', dataIndex: 'position', width: 48 },
          {
            title: 'Наименование',
            key: 'name',
            render: (_, l) => (
              <Space size={6} wrap>
                <span>{l.name}</span>
                {l.source === 'MANUAL' && <Tag>вручную</Tag>}
              </Space>
            ),
          },
          {
            title: 'Номер по каталогу',
            key: 'number',
            width: 170,
            render: (_, l) => <span className="cell-codes">{l.catalogNumber ?? '—'}</span>,
          },
          {
            title: 'Кол-во',
            key: 'qty',
            width: 110,
            align: 'right',
            render: (_, l) => `${qty.format(l.quantity)} ${l.unitName}`,
          },
          { title: 'Примечание', dataIndex: 'notes', width: 160 },
          {
            title: '',
            key: 'actions',
            width: 84,
            render: (_, l) =>
              editable && (
                <Space size={0}>
                  <Button
                    type="text"
                    size="small"
                    icon={<EditOutlined />}
                    title="Изменить"
                    onClick={() => setDialog({ open: true, line: l })}
                  />
                  <Popconfirm
                    title="Удалить позицию?"
                    okText="Удалить"
                    cancelText="Отмена"
                    onConfirm={() => void del(l)}
                  >
                    <Button type="text" size="small" danger icon={<DeleteOutlined />} title="Удалить" />
                  </Popconfirm>
                </Space>
              ),
          },
        ]}
      />
      {dialog.open && (
        <PartLineDialog act={act} line={dialog.line} open onClose={() => setDialog({ open: false })} />
      )}
    </div>
  );
}

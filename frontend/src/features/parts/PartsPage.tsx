import { useEffect, useState } from 'react';
import {
  App,
  Button,
  Col,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import {
  getGetPartsImportTemplateUrl,
  importParts,
  useCreatePart,
  useDeletePart,
  useListParts,
  useUpdatePart,
} from '../../api/generated/parts/parts';
import { useListUnits } from '../../api/generated/parts/units';
import type { Part, PartInput } from '../../api/generated/parts/model';
import { ok } from '../../api/unwrap';
import { applyFormError, errorText } from '../../shared/lib/apiErrors';
import { invalidateByPrefix } from '../../shared/lib/invalidate';
import { emptyToNull, sortParam } from '../../shared/lib/table';
import { useRoles } from '../../shared/lib/useRoles';
import { ImportDialog } from '../../shared/ui/ImportDialog';

function PartForm({ open, part, onClose }: { open: boolean; part?: Part; onClose: () => void }) {
  const [form] = Form.useForm<PartInput>();
  const { message } = App.useApp();
  const client = useQueryClient();
  const units = ok(useListUnits().data) ?? [];
  const create = useCreatePart();
  const update = useUpdatePart();

  useEffect(() => {
    if (open) {
      form.resetFields();
      form.setFieldsValue(part ? { ...part } : { defaultUnit: 'PCS', fluid: false });
    }
  }, [open, part, form]);

  const submit = async (values: PartInput) => {
    const data = emptyToNull({ ...values, version: part?.version });
    try {
      if (part) await update.mutateAsync({ id: part.id, data });
      else await create.mutateAsync({ data });
      await invalidateByPrefix(client, '/api/v1/parts');
      message.success(part ? 'Позиция сохранена' : 'Позиция добавлена в каталог');
      onClose();
    } catch (e) {
      message.error(applyFormError(form, e));
    }
  };

  return (
    <Modal
      open={open}
      title={part ? 'Позиция каталога' : 'Новая позиция каталога'}
      okText="Сохранить"
      cancelText="Отмена"
      onOk={() => form.submit()}
      onCancel={onClose}
      confirmLoading={create.isPending || update.isPending}
      width={600}
      destroyOnHidden
    >
      <Form
        form={form}
        layout="vertical"
        requiredMark="optional"
        onFinish={(v) => void submit(v)}
        onValuesChange={(changed) => {
          if (changed.defaultUnit === 'L') form.setFieldValue('fluid', true);
        }}
      >
        <Form.Item
          name="name"
          label="Наименование"
          rules={[{ required: true, message: 'Укажите наименование' }]}
        >
          <Input maxLength={300} placeholder="Фильтр масляный двигателя" autoFocus />
        </Form.Item>
        <Row gutter={16}>
          <Col xs={24} sm={14}>
            <Form.Item name="catalogNumber" label="Номер по каталогу">
              <Input maxLength={100} className="input-code" placeholder="1R-0750" />
            </Form.Item>
          </Col>
          <Col xs={24} sm={10}>
            <Form.Item name="manufacturer" label="Производитель">
              <Input maxLength={100} placeholder="CAT" />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={16}>
          <Col xs={24} sm={14}>
            <Form.Item name="defaultUnit" label="Единица измерения" rules={[{ required: true }]}>
              <Select options={units.map((u) => ({ value: u.code, label: u.name }))} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={10}>
            <Form.Item name="fluid" label="Жидкость на розлив" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="notes" label="Примечание">
          <Input.TextArea maxLength={2000} autoSize={{ minRows: 2, maxRows: 5 }} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** Каталог запчастей и жидкостей. Добавлять может любой, изменять и удалять — руководитель и администратор. */
export function PartsPage() {
  const { message } = App.useApp();
  const { canEdit } = useRoles();
  const client = useQueryClient();
  const [q, setQ] = useState('');
  const [page, setPage] = useState({ page: 0, size: 20, sort: undefined as string | undefined });
  const { data, isFetching } = useListParts({
    q: q || undefined,
    page: page.page,
    size: page.size,
    sort: page.sort,
  });
  const units = new Map((ok(useListUnits().data) ?? []).map((u) => [u.code, u.name]));
  const remove = useDeletePart();
  const [form, setForm] = useState<{ open: boolean; part?: Part }>({ open: false });
  const [importOpen, setImportOpen] = useState(false);
  const result = ok(data);

  const del = async (p: Part) => {
    try {
      await remove.mutateAsync({ id: p.id });
      await invalidateByPrefix(client, '/api/v1/parts');
    } catch (e) {
      message.error(errorText(e));
    }
  };

  return (
    <section className="page">
      <Typography.Title level={2} className="page-title">
        Каталог запчастей
      </Typography.Title>
      <div className="toolbar">
        <Input.Search
          allowClear
          placeholder="Наименование, номер, производитель"
          onSearch={(v) => {
            setQ(v.trim());
            setPage((p) => ({ ...p, page: 0 }));
          }}
          style={{ maxWidth: 360 }}
        />
        <div className="toolbar-spacer" />
        {canEdit && (
          <Button icon={<UploadOutlined />} onClick={() => setImportOpen(true)}>
            Импорт
          </Button>
        )}
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setForm({ open: true })}>
          Добавить
        </Button>
      </div>
      <div className="page-panel">
        <Table<Part>
          rowKey="id"
          loading={isFetching}
          dataSource={result?.items}
          scroll={{ x: 760 }}
          onChange={(p, _f, sorter) =>
            setPage({ page: (p.current ?? 1) - 1, size: p.pageSize ?? 20, sort: sortParam(sorter) })
          }
          pagination={{
            current: page.page + 1,
            pageSize: page.size,
            total: result?.totalElements,
            showSizeChanger: true,
            showTotal: (t) => `Всего: ${t}`,
          }}
          columns={[
            {
              title: 'Наименование',
              key: 'name',
              sorter: true,
              render: (_, p) => (
                <Space size={8}>
                  <span className="cell-strong">{p.name}</span>
                  {p.fluid && <Tag color="blue">на розлив</Tag>}
                </Space>
              ),
            },
            {
              title: 'Номер по каталогу',
              key: 'catalogNumber',
              sorter: true,
              width: 200,
              render: (_, p) => <span className="cell-codes">{p.catalogNumber ?? '—'}</span>,
            },
            {
              title: 'Производитель',
              key: 'manufacturer',
              dataIndex: 'manufacturer',
              sorter: true,
              width: 160,
            },
            {
              title: 'Ед. изм.',
              key: 'unit',
              width: 100,
              render: (_, p) => units.get(p.defaultUnit) ?? p.defaultUnit,
            },
            {
              title: '',
              key: 'actions',
              width: 96,
              render: (_, p) =>
                canEdit && (
                  <Space size={0}>
                    <Button
                      type="text"
                      icon={<EditOutlined />}
                      title="Изменить"
                      onClick={() => setForm({ open: true, part: p })}
                    />
                    <Popconfirm
                      title="Удалить позицию из каталога?"
                      okText="Удалить"
                      cancelText="Отмена"
                      onConfirm={() => void del(p)}
                    >
                      <Button type="text" danger icon={<DeleteOutlined />} title="Удалить" />
                    </Popconfirm>
                  </Space>
                ),
            },
          ]}
        />
      </div>
      <PartForm open={form.open} part={form.part} onClose={() => setForm({ open: false })} />
      <ImportDialog
        open={importOpen}
        title="Импорт каталога запчастей"
        hint="Позиция с тем же номером по каталогу и производителем (или, если номера нет, с тем же наименованием) обновится. Единица измерения — шт, л, кг, м или компл."
        templateUrl={getGetPartsImportTemplateUrl()}
        templateName="Шаблон импорта запчастей.xlsx"
        onImport={async (file) => ok(await importParts({ file }))!}
        onClose={(changed) => {
          setImportOpen(false);
          if (changed) void invalidateByPrefix(client, '/api/v1/parts');
        }}
      />
    </section>
  );
}

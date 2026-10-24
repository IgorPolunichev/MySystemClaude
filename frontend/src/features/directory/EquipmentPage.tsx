import { useMemo, useState } from 'react';
import { App, Button, Input, Popconfirm, Space, Table, Typography } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import {
  getGetEquipmentImportTemplateUrl,
  importEquipment,
  useDeleteEquipment,
  useListEquipment,
} from '../../api/generated/directory/equipment';
import type { Equipment } from '../../api/generated/directory/model';
import { ok } from '../../api/unwrap';
import { errorText } from '../../shared/lib/apiErrors';
import { invalidateByPrefix } from '../../shared/lib/invalidate';
import { sortParam } from '../../shared/lib/table';
import { useRoles } from '../../shared/lib/useRoles';
import { ImportDialog } from '../../shared/ui/ImportDialog';
import { EquipmentForm } from './EquipmentForm';
import { DepartmentSelect, EquipmentTypeSelect, OrganizationSelect } from './lookups';

const number = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1 });

function meter(e: Equipment): string {
  const parts: string[] = [];
  if (e.meterType !== 'KM' && e.currentHours != null) parts.push(`${number.format(e.currentHours)} м/ч`);
  if (e.meterType !== 'HOURS' && e.currentKm != null) parts.push(`${number.format(e.currentKm)} км`);
  return parts.join(' · ') || '—';
}

/** Реестр техники: поиск по любому номеру, фильтры по организации, подразделению и типу. */
export function EquipmentPage() {
  const { message } = App.useApp();
  const { canEdit } = useRoles();
  const client = useQueryClient();
  const [q, setQ] = useState('');
  const [organizationId, setOrganizationId] = useState<string>();
  const [departmentId, setDepartmentId] = useState<string>();
  const [typeId, setTypeId] = useState<string>();
  const [page, setPage] = useState({ page: 0, size: 20, sort: undefined as string | undefined });
  const { data, isFetching } = useListEquipment({
    q: q || undefined,
    organizationId,
    departmentId,
    equipmentTypeId: typeId,
    page: page.page,
    size: page.size,
    sort: page.sort,
  });
  const remove = useDeleteEquipment();
  const [form, setForm] = useState<{ open: boolean; equipment?: Equipment }>({ open: false });
  const [importOpen, setImportOpen] = useState(false);
  const result = ok(data);
  const defaults = useMemo(
    () => ({ organizationId, departmentId, equipmentTypeId: typeId }),
    [organizationId, departmentId, typeId],
  );
  const resetPage = () => setPage((p) => ({ ...p, page: 0 }));

  const del = async (e: Equipment) => {
    try {
      await remove.mutateAsync({ id: e.id });
      await invalidateByPrefix(client, '/api/v1/equipment');
      message.success('Техника удалена');
    } catch (err) {
      message.error(errorText(err));
    }
  };

  return (
    <>
      <div className="toolbar">
        <Input.Search
          allowClear
          placeholder="Серийный, гаражный, госномер, марка"
          onSearch={(v) => {
            setQ(v.trim());
            resetPage();
          }}
          style={{ width: 260 }}
        />
        <OrganizationSelect
          allowClear
          value={organizationId}
          onChange={(v) => {
            setOrganizationId(v);
            setDepartmentId(undefined);
            resetPage();
          }}
          style={{ width: 210 }}
        />
        <DepartmentSelect
          organizationId={organizationId}
          value={departmentId}
          onChange={(v) => {
            setDepartmentId(v);
            resetPage();
          }}
          style={{ width: 200 }}
        />
        <EquipmentTypeSelect
          allowClear
          value={typeId}
          onChange={(v) => {
            setTypeId(v);
            resetPage();
          }}
          style={{ width: 180 }}
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
        <Table<Equipment>
          rowKey="id"
          loading={isFetching}
          dataSource={result?.items}
          scroll={{ x: 1000 }}
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
              title: 'Техника',
              key: 'brand',
              sorter: true,
              render: (_, e) => (
                <Space direction="vertical" size={0}>
                  <span className="cell-strong">
                    {e.brand} {e.model}
                  </span>
                  <span className="cell-muted">{e.equipmentTypeName}</span>
                </Space>
              ),
            },
            {
              title: 'Номера',
              key: 'serialNumber',
              sorter: true,
              render: (_, e) => (
                <Space direction="vertical" size={0} className="cell-codes">
                  {e.serialNumber && <span>S/N {e.serialNumber}</span>}
                  {e.garageNumber && <span>Гар. № {e.garageNumber}</span>}
                  {e.plateNumber && <span>{e.plateNumber}</span>}
                </Space>
              ),
            },
            {
              title: 'Организация',
              key: 'organizationName',
              sorter: true,
              render: (_, e) => (
                <Space direction="vertical" size={0}>
                  <span>{e.organizationName}</span>
                  {e.siteName && <span className="cell-muted">{e.siteName}</span>}
                  {e.departmentName && <span className="cell-muted">{e.departmentName}</span>}
                </Space>
              ),
            },
            { title: 'Наработка', key: 'meter', width: 170, render: (_, e) => meter(e) },
            {
              title: '',
              key: 'actions',
              width: 96,
              render: (_, e) =>
                canEdit && (
                  <Space size={0}>
                    <Button
                      type="text"
                      icon={<EditOutlined />}
                      title="Изменить"
                      onClick={() => setForm({ open: true, equipment: e })}
                    />
                    <Popconfirm
                      title="Удалить технику?"
                      okText="Удалить"
                      cancelText="Отмена"
                      onConfirm={() => void del(e)}
                    >
                      <Button type="text" danger icon={<DeleteOutlined />} title="Удалить" />
                    </Popconfirm>
                  </Space>
                ),
            },
          ]}
          locale={{
            emptyText: (
              <Typography.Text type="secondary">
                {q || organizationId || typeId
                  ? 'Ничего не найдено — измените условия поиска'
                  : 'Техники пока нет'}
              </Typography.Text>
            ),
          }}
        />
      </div>
      <EquipmentForm
        open={form.open}
        equipment={form.equipment}
        defaults={defaults}
        onClose={() => setForm({ open: false })}
      />
      <ImportDialog
        open={importOpen}
        title="Импорт техники"
        hint="Организация указывается ИНН или наименованием и должна уже быть в справочнике. Новые подразделения и типы техники создаются автоматически. Техника с тем же серийным номером (или гаражным, или госномером) обновится."
        templateUrl={getGetEquipmentImportTemplateUrl()}
        templateName="Шаблон импорта техники.xlsx"
        onImport={async (file) => ok(await importEquipment({ file }))!}
        onClose={(changed) => {
          setImportOpen(false);
          if (changed)
            void invalidateByPrefix(
              client,
              '/api/v1/equipment',
              '/api/v1/equipment-types',
              '/api/v1/organizations',
            );
        }}
      />
    </>
  );
}

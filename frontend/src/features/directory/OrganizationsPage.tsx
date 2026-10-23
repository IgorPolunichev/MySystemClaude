import { useState } from 'react';
import { App, Button, Descriptions, Drawer, Input, Popconfirm, Segmented, Space, Table, Tag } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import {
  getGetOrganizationsImportTemplateUrl,
  importOrganizations,
  useDeleteOrganization,
  useListOrganizations,
} from '../../api/generated/directory/organizations';
import type { Organization } from '../../api/generated/directory/model';
import { ok } from '../../api/unwrap';
import { errorText } from '../../shared/lib/apiErrors';
import { invalidateByPrefix } from '../../shared/lib/invalidate';
import { sortParam } from '../../shared/lib/table';
import { useRoles } from '../../shared/lib/useRoles';
import { ImportDialog } from '../../shared/ui/ImportDialog';
import { DepartmentsPanel } from './DepartmentsPanel';
import { OrganizationForm } from './OrganizationForm';

/** Реестр организаций-заказчиков с карточкой и деревом подразделений. */
export function OrganizationsPage() {
  const { message } = App.useApp();
  const { canEdit } = useRoles();
  const client = useQueryClient();
  const [q, setQ] = useState('');
  const [active, setActive] = useState<'all' | 'active' | 'inactive'>('active');
  const [page, setPage] = useState({ page: 0, size: 20, sort: undefined as string | undefined });
  const { data, isFetching } = useListOrganizations({
    q: q || undefined,
    active: active === 'all' ? undefined : active === 'active',
    page: page.page,
    size: page.size,
    sort: page.sort,
  });
  const remove = useDeleteOrganization();
  const [form, setForm] = useState<{ open: boolean; organization?: Organization }>({ open: false });
  const [card, setCard] = useState<Organization | null>(null);
  const [importOpen, setImportOpen] = useState(false);
  const result = ok(data);

  const del = async (o: Organization) => {
    try {
      await remove.mutateAsync({ id: o.id });
      await invalidateByPrefix(client, '/api/v1/organizations');
      setCard(null);
      message.success('Организация удалена');
    } catch (e) {
      message.error(errorText(e));
    }
  };

  return (
    <>
      <div className="toolbar">
        <Input.Search
          allowClear
          placeholder="Название или ИНН"
          onSearch={(v) => {
            setQ(v.trim());
            setPage((p) => ({ ...p, page: 0 }));
          }}
          style={{ maxWidth: 320 }}
        />
        <Segmented
          value={active}
          onChange={(v) => {
            setActive(v as typeof active);
            setPage((p) => ({ ...p, page: 0 }));
          }}
          options={[
            { value: 'active', label: 'Активные' },
            { value: 'inactive', label: 'Неактивные' },
            { value: 'all', label: 'Все' },
          ]}
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
        <Table<Organization>
          rowKey="id"
          loading={isFetching}
          dataSource={result?.items}
          scroll={{ x: 760 }}
          onRow={(o) => ({ onClick: () => setCard(o), style: { cursor: 'pointer' } })}
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
              title: 'Организация',
              key: 'name',
              sorter: true,
              render: (_, o) => (
                <Space direction="vertical" size={0}>
                  <span className="cell-strong">{o.shortName ?? o.name}</span>
                  {o.shortName && <span className="cell-muted">{o.name}</span>}
                </Space>
              ),
            },
            { title: 'ИНН', key: 'inn', dataIndex: 'inn', sorter: true, width: 130 },
            { title: 'КПП', dataIndex: 'kpp', width: 110 },
            { title: 'Телефон', dataIndex: 'phone', width: 170 },
            {
              title: '',
              key: 'state',
              width: 110,
              render: (_, o) => (o.active ? null : <Tag>Неактивна</Tag>),
            },
          ]}
        />
      </div>

      <Drawer
        open={!!card}
        onClose={() => setCard(null)}
        width="min(620px, 100vw)"
        title={card?.shortName ?? card?.name}
        extra={
          card &&
          canEdit && (
            <Space>
              <Button icon={<EditOutlined />} onClick={() => setForm({ open: true, organization: card })}>
                Изменить
              </Button>
              <Popconfirm
                title="Удалить организацию?"
                description="Удалить можно только организацию без техники и подразделений."
                okText="Удалить"
                cancelText="Отмена"
                onConfirm={() => void del(card)}
              >
                <Button danger icon={<DeleteOutlined />} />
              </Popconfirm>
            </Space>
          )
        }
      >
        {card && (
          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="Полное наименование">{card.name}</Descriptions.Item>
              <Descriptions.Item label="ИНН / КПП">
                {[card.inn, card.kpp].filter(Boolean).join(' / ') || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="ОГРН">{card.ogrn ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="Юридический адрес">{card.legalAddress ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="Фактический адрес">{card.actualAddress ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="Телефон">{card.phone ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="Почта">{card.email ?? '—'}</Descriptions.Item>
              {card.notes && <Descriptions.Item label="Примечание">{card.notes}</Descriptions.Item>}
            </Descriptions>
            <DepartmentsPanel organizationId={card.id} />
          </Space>
        )}
      </Drawer>

      <OrganizationForm
        open={form.open}
        organization={form.organization}
        onClose={() => {
          setForm({ open: false });
          setCard(null);
        }}
      />
      <ImportDialog
        open={importOpen}
        title="Импорт организаций"
        hint="Заполните шаблон или загрузите свою таблицу с такими же заголовками колонок. Организация с тем же ИНН (или, если ИНН нет, с тем же наименованием) обновится."
        templateUrl={getGetOrganizationsImportTemplateUrl()}
        templateName="Шаблон импорта организаций.xlsx"
        onImport={async (file) => ok(await importOrganizations({ file }))!}
        onClose={(changed) => {
          setImportOpen(false);
          if (changed) void invalidateByPrefix(client, '/api/v1/organizations');
        }}
      />
    </>
  );
}

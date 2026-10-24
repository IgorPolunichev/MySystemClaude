import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  App,
  Button,
  DatePicker,
  Input,
  Modal,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { Dayjs } from 'dayjs';
import { useCreateAct, useListActs } from '../../api/generated/acts/acts';
import type { ActStatus, ActSummary } from '../../api/generated/acts/model';
import { ok } from '../../api/unwrap';
import { errorText } from '../../shared/lib/apiErrors';
import { sortParam } from '../../shared/lib/table';
import { OrganizationSelect } from '../directory/lookups';
import { STATUS, formatHours, formatPeriod, isoDate } from './actMeta';
import { EngineerSelect, EquipmentSelect } from './lookups';

/** Создание акта: сразу выбрать технику (можно пропустить и выбрать позже). */
function NewActDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const navigate = useNavigate();
  const { message } = App.useApp();
  const create = useCreateAct();
  const [equipmentId, setEquipmentId] = useState<string>();
  const submit = async () => {
    try {
      const act = ok(await create.mutateAsync({ data: { equipmentId: equipmentId ?? null } }))!;
      onClose();
      navigate(`/acts/${act.id}`);
    } catch (e) {
      message.error(errorText(e));
    }
  };
  return (
    <Modal
      open={open}
      title="Новый акт"
      okText="Создать черновик"
      cancelText="Отмена"
      onOk={() => void submit()}
      onCancel={onClose}
      confirmLoading={create.isPending}
      destroyOnHidden
    >
      <Typography.Paragraph type="secondary">
        Выберите технику — данные машины и заказчика подставятся в акт. Номер акт получит, когда вы его
        откроете.
      </Typography.Paragraph>
      <EquipmentSelect value={equipmentId} onChange={setEquipmentId} />
    </Modal>
  );
}

/** Реестр актов: поиск, фильтры по статусу, периоду, организации и инженеру, «мои акты». */
export function ActsRegistry() {
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const [mine, setMine] = useState<'mine' | 'all'>('mine');
  const [statuses, setStatuses] = useState<ActStatus[]>([]);
  const [organizationId, setOrganizationId] = useState<string>();
  const [engineerId, setEngineerId] = useState<string>();
  const [period, setPeriod] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [page, setPage] = useState({ page: 0, size: 20, sort: undefined as string | undefined });
  const [newOpen, setNewOpen] = useState(false);
  const { data, isFetching } = useListActs({
    q: q || undefined,
    mine: mine === 'mine' || undefined,
    status: statuses.length ? statuses : undefined,
    organizationId,
    engineerId,
    from: period?.[0] ? isoDate(period[0].toDate()) : undefined,
    to: period?.[1] ? isoDate(period[1].toDate()) : undefined,
    page: page.page,
    size: page.size,
    sort: page.sort,
  });
  const result = ok(data);
  const reset = () => setPage((p) => ({ ...p, page: 0 }));

  return (
    <section className="page">
      <div className="page-head">
        <Typography.Title level={2} className="page-title">
          Акты
        </Typography.Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setNewOpen(true)}>
          Новый акт
        </Button>
      </div>
      <div className="toolbar" style={{ marginTop: 16 }}>
        <Segmented
          value={mine}
          onChange={(v) => {
            setMine(v as typeof mine);
            reset();
          }}
          options={[
            { value: 'mine', label: 'Мои' },
            { value: 'all', label: 'Все' },
          ]}
        />
        <Input.Search
          allowClear
          placeholder="Номер, описание, техника, заказчик"
          onSearch={(v) => {
            setQ(v.trim());
            reset();
          }}
          style={{ width: 280 }}
        />
        <Select
          mode="multiple"
          allowClear
          placeholder="Статус"
          value={statuses}
          onChange={(v) => {
            setStatuses(v);
            reset();
          }}
          options={(Object.keys(STATUS) as ActStatus[]).map((s) => ({ value: s, label: STATUS[s].label }))}
          style={{ minWidth: 180 }}
          maxTagCount="responsive"
        />
        <DatePicker.RangePicker
          value={period}
          onChange={(v) => {
            setPeriod(v);
            reset();
          }}
          format="DD.MM.YYYY"
          placeholder={['Период с', 'по']}
        />
        <OrganizationSelect
          allowClear
          value={organizationId}
          onChange={(v) => {
            setOrganizationId(v);
            reset();
          }}
          style={{ width: 200 }}
        />
        {mine === 'all' && (
          <EngineerSelect
            allowClear
            value={engineerId}
            onChange={(v) => {
              setEngineerId(v);
              reset();
            }}
            style={{ width: 200 }}
          />
        )}
      </div>
      <div className="page-panel">
        <Table<ActSummary>
          rowKey="id"
          loading={isFetching}
          dataSource={result?.items}
          scroll={{ x: 1000 }}
          onRow={(a) => ({ onClick: () => navigate(`/acts/${a.id}`), style: { cursor: 'pointer' } })}
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
              title: 'Акт',
              key: 'number',
              sorter: true,
              width: 190,
              render: (_, a) => (
                <Space direction="vertical" size={2}>
                  <span className="cell-codes cell-strong">{a.number ?? 'без номера'}</span>
                  <Space size={4} wrap>
                    <Tag color={STATUS[a.status].color}>{STATUS[a.status].label}</Tag>
                    {a.hasSignedScan && (
                      <Tag color="green" title="Скан подписанного акта приложен">
                        скан
                      </Tag>
                    )}
                  </Space>
                </Space>
              ),
            },
            {
              title: 'Период работ',
              key: 'periodStart',
              sorter: true,
              width: 200,
              render: (_, a) => formatPeriod(a.periodStart, a.periodEnd),
            },
            {
              title: 'Техника и заказчик',
              key: 'organizationName',
              sorter: true,
              render: (_, a) => (
                <Space direction="vertical" size={0}>
                  <span className="cell-strong">{a.equipmentTitle ?? 'техника не выбрана'}</span>
                  {a.equipmentNumbers && <span className="cell-codes cell-muted">{a.equipmentNumbers}</span>}
                  <span className="cell-muted">{a.organizationName}</span>
                </Space>
              ),
            },
            { title: 'Работы', key: 'shortDescription', dataIndex: 'shortDescription', sorter: true },
            { title: 'Инженеры', dataIndex: 'engineerNames', width: 220 },
            {
              title: 'Часы',
              key: 'totalHours',
              sorter: true,
              width: 90,
              align: 'right',
              render: (_, a) => formatHours(a.totalHours),
            },
          ]}
        />
      </div>
      <NewActDialog open={newOpen} onClose={() => setNewOpen(false)} />
    </section>
  );
}

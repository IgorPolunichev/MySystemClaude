import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  App,
  AutoComplete,
  Badge,
  Button,
  DatePicker,
  Dropdown,
  Input,
  Modal,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  DeleteOutlined,
  FileExcelOutlined,
  FilterOutlined,
  PlusOutlined,
  SaveOutlined,
  StarOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  getExportActsUrl,
  useCreateAct,
  useListActs,
  useListShortDescriptions,
} from '../../api/generated/acts/acts';
import type { ActStatus, ActSummary, ListActsParams } from '../../api/generated/acts/model';
import { downloadFile } from '../../api/http';
import { ok } from '../../api/unwrap';
import { errorText } from '../../shared/lib/apiErrors';
import { sortParam } from '../../shared/lib/table';
import { OrganizationSelect, SiteSelect } from '../directory/lookups';
import { STATUS, formatHours, formatPeriod } from './actMeta';
import { EngineerSelect, EquipmentSelect } from './lookups';
import {
  highlightParts,
  loadSavedFilters,
  storeSavedFilters,
  type RegistryFilters,
  type SavedFilter,
} from './savedFilters';

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
        Выберите технику — данные машины, заказчика и площадки подставятся в акт. Номер акт получит, когда вы
        его откроете.
      </Typography.Paragraph>
      <EquipmentSelect value={equipmentId} onChange={setEquipmentId} />
    </Modal>
  );
}

function Highlight({ text }: { text: string }) {
  return (
    <span className="search-snippet">
      {highlightParts(text).map((p, i) =>
        p.hit ? <mark key={i}>{p.text}</mark> : <span key={i}>{p.text}</span>,
      )}
    </span>
  );
}

/**
 * Реестр актов: полнотекстовый поиск по всему акту с подсветкой, фильтры (статус, период, заказчик, площадка,
 * инженер, запчасть, краткое описание, скан), сохранённые наборы фильтров, выгрузка в Excel.
 */
export function ActsRegistry() {
  const navigate = useNavigate();
  const { message, modal } = App.useApp();
  const [filters, setFilters] = useState<RegistryFilters>({ mine: true });
  const [searchText, setSearchText] = useState('');
  const [more, setMore] = useState(false);
  const [page, setPage] = useState({ page: 0, size: 20, sort: undefined as string | undefined });
  const [newOpen, setNewOpen] = useState(false);
  const [saved, setSaved] = useState<SavedFilter[]>(loadSavedFilters);
  const [exporting, setExporting] = useState(false);
  const [shortQuery, setShortQuery] = useState('');
  const shortOptions =
    ok(useListShortDescriptions({ q: shortQuery || undefined }, { query: { enabled: more } }).data) ?? [];

  const params: ListActsParams = useMemo(
    () => ({
      q: filters.q || undefined,
      mine: filters.mine || undefined,
      status: filters.statuses?.length ? (filters.statuses as ActStatus[]) : undefined,
      organizationId: filters.organizationId,
      siteId: filters.siteId,
      engineerId: filters.engineerId,
      partNumber: filters.partNumber || undefined,
      shortDescription: filters.shortDescription || undefined,
      signedScan: filters.signedScan,
      from: filters.from,
      to: filters.to,
    }),
    [filters],
  );
  const { data, isFetching } = useListActs({ ...params, page: page.page, size: page.size, sort: page.sort });
  const result = ok(data);

  const update = (patch: Partial<RegistryFilters>) => {
    setFilters((f) => ({ ...f, ...patch }));
    setPage((p) => ({ ...p, page: 0 }));
  };

  const extraCount = [
    filters.siteId,
    filters.partNumber,
    filters.shortDescription,
    filters.signedScan,
    filters.engineerId,
  ].filter((v) => v !== undefined && v !== '').length;

  const exportXlsx = async () => {
    setExporting(true);
    try {
      await downloadFile(getExportActsUrl({ ...params, sort: page.sort }), 'Реестр актов.xlsx');
    } catch (e) {
      message.error(errorText(e));
    } finally {
      setExporting(false);
    }
  };

  const saveCurrent = () => {
    let name = '';
    modal.confirm({
      title: 'Сохранить набор фильтров',
      content: (
        <Input
          autoFocus
          maxLength={60}
          placeholder="Например: «Северный ГОК, закрытые без скана»"
          onChange={(e) => (name = e.target.value)}
        />
      ),
      okText: 'Сохранить',
      cancelText: 'Отмена',
      onOk: () => {
        const title = name.trim() || `Набор ${saved.length + 1}`;
        const next = [...saved.filter((s) => s.name !== title), { name: title, filters }];
        setSaved(next);
        storeSavedFilters(next);
      },
    });
  };

  const removeSaved = (name: string) => {
    const next = saved.filter((s) => s.name !== name);
    setSaved(next);
    storeSavedFilters(next);
  };

  return (
    <section className="page">
      <div className="page-head">
        <Typography.Title level={2} className="page-title">
          Акты
        </Typography.Title>
        <Space wrap>
          <Button icon={<FileExcelOutlined />} loading={exporting} onClick={() => void exportXlsx()}>
            Выгрузить в Excel
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setNewOpen(true)}>
            Новый акт
          </Button>
        </Space>
      </div>

      <div className="toolbar" style={{ marginTop: 16 }}>
        <Segmented
          value={filters.mine ? 'mine' : 'all'}
          onChange={(v) => update({ mine: v === 'mine' })}
          options={[
            { value: 'mine', label: 'Мои' },
            { value: 'all', label: 'Все' },
          ]}
        />
        <Input.Search
          allowClear
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
          placeholder="Поиск по всему акту: «форсунки», номер, гаражный, запчасть…"
          onSearch={(v) => update({ q: v.trim() || undefined })}
          style={{ width: 340 }}
        />
        <Select
          mode="multiple"
          allowClear
          placeholder="Статус"
          value={filters.statuses ?? []}
          onChange={(v) => update({ statuses: v })}
          options={(Object.keys(STATUS) as ActStatus[]).map((s) => ({ value: s, label: STATUS[s].label }))}
          style={{ minWidth: 170 }}
          maxTagCount="responsive"
        />
        <DatePicker.RangePicker
          value={[filters.from ? dayjs(filters.from) : null, filters.to ? dayjs(filters.to) : null]}
          onChange={(v) => update({ from: v?.[0]?.format('YYYY-MM-DD'), to: v?.[1]?.format('YYYY-MM-DD') })}
          format="DD.MM.YYYY"
          placeholder={['Период с', 'по']}
        />
        <OrganizationSelect
          allowClear
          value={filters.organizationId}
          onChange={(v) => update({ organizationId: v, siteId: undefined })}
          style={{ width: 200 }}
        />
        <Badge count={extraCount} size="small">
          <Button
            icon={<FilterOutlined />}
            onClick={() => setMore((m) => !m)}
            type={more ? 'primary' : 'default'}
            ghost={more}
          >
            Ещё фильтры
          </Button>
        </Badge>
        <Dropdown
          trigger={['click']}
          menu={{
            items: [
              { key: '__save', icon: <SaveOutlined />, label: 'Сохранить текущие фильтры…' },
              ...(saved.length ? [{ type: 'divider' as const }] : []),
              ...saved.map((s) => ({
                key: s.name,
                label: (
                  <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                    <span>{s.name}</span>
                    <DeleteOutlined
                      onClick={(e) => {
                        e.stopPropagation();
                        removeSaved(s.name);
                      }}
                    />
                  </Space>
                ),
              })),
            ],
            onClick: ({ key }) => {
              if (key === '__save') {
                saveCurrent();
                return;
              }
              const preset = saved.find((s) => s.name === key);
              if (preset) {
                setFilters(preset.filters);
                setSearchText(preset.filters.q ?? '');
                setMore(true);
                setPage((p) => ({ ...p, page: 0 }));
              }
            },
          }}
        >
          <Button icon={<StarOutlined />}>Мои фильтры</Button>
        </Dropdown>
        {(filters.q ||
          filters.statuses?.length ||
          filters.organizationId ||
          extraCount > 0 ||
          filters.from) && (
          <Button
            type="link"
            onClick={() => {
              setFilters({ mine: filters.mine });
              setSearchText('');
              setPage((p) => ({ ...p, page: 0 }));
            }}
          >
            Сбросить
          </Button>
        )}
      </div>

      {more && (
        <div className="toolbar more-filters">
          <SiteSelect
            organizationId={filters.organizationId}
            value={filters.siteId}
            onChange={(v) => update({ siteId: v })}
            style={{ width: 220 }}
          />
          {!filters.mine && (
            <EngineerSelect
              allowClear
              value={filters.engineerId}
              onChange={(v) => update({ engineerId: v })}
              style={{ width: 220 }}
            />
          )}
          <Input
            allowClear
            className="input-code"
            placeholder="Номер запчасти, напр. 1R-0750"
            value={filters.partNumber}
            onChange={(e) => update({ partNumber: e.target.value || undefined })}
            style={{ width: 230 }}
          />
          <AutoComplete
            allowClear
            value={filters.shortDescription}
            options={shortOptions.map((s) => ({ value: s }))}
            onSearch={setShortQuery}
            onChange={(v: string) => update({ shortDescription: v || undefined })}
            placeholder="Краткое описание (точно)"
            style={{ width: 220 }}
          />
          <Select
            allowClear
            placeholder="Скан подписанного акта"
            value={filters.signedScan === undefined ? undefined : filters.signedScan ? 'yes' : 'no'}
            onChange={(v) => update({ signedScan: v === undefined ? undefined : v === 'yes' })}
            options={[
              { value: 'yes', label: 'Скан приложен' },
              { value: 'no', label: 'Скана нет' },
            ]}
            style={{ width: 200 }}
          />
        </div>
      )}

      <div className="page-panel">
        <Table<ActSummary>
          rowKey="id"
          loading={isFetching}
          dataSource={result?.items}
          scroll={{ x: 1100 }}
          onRow={(a) => ({ onClick: () => navigate(`/acts/${a.id}`), style: { cursor: 'pointer' } })}
          onChange={(p, _f, sorter) =>
            setPage({ page: (p.current ?? 1) - 1, size: p.pageSize ?? 20, sort: sortParam(sorter) })
          }
          pagination={{
            current: page.page + 1,
            pageSize: page.size,
            total: result?.totalElements,
            showSizeChanger: true,
            showTotal: (t) => `Найдено: ${t}`,
          }}
          locale={{
            emptyText: filters.q
              ? 'Ничего не найдено — попробуйте другие слова или уберите фильтры'
              : 'Актов пока нет',
          }}
          columns={[
            {
              title: 'Акт',
              key: 'number',
              sorter: true,
              width: 200,
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
              width: 190,
              render: (_, a) => formatPeriod(a.periodStart, a.periodEnd),
            },
            {
              title: 'Техника и заказчик',
              key: 'equipmentTitle',
              sorter: true,
              render: (_, a) => (
                <Space direction="vertical" size={0}>
                  <span className="cell-strong">{a.equipmentTitle ?? 'техника не выбрана'}</span>
                  {a.equipmentNumbers && <span className="cell-codes cell-muted">{a.equipmentNumbers}</span>}
                  <span className="cell-muted">
                    {a.organizationName}
                    {a.siteName && ` · ${a.siteName}`}
                  </span>
                </Space>
              ),
            },
            {
              title: 'Работы',
              key: 'shortDescription',
              sorter: true,
              render: (_, a) => (
                <Space direction="vertical" size={2}>
                  <span>{a.shortDescription}</span>
                  {a.highlight && <Highlight text={a.highlight} />}
                </Space>
              ),
            },
            { title: 'Инженеры', key: 'engineerNames', dataIndex: 'engineerNames', sorter: true, width: 210 },
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

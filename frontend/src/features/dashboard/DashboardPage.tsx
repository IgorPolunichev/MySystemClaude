import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  App,
  Button,
  Card,
  Checkbox,
  Popover,
  DatePicker,
  Empty,
  Progress,
  Segmented,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  theme,
  Typography,
} from 'antd';
import { CalendarOutlined, EyeOutlined, FileExcelOutlined, SyncOutlined } from '@ant-design/icons';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import dayjs, { type Dayjs } from 'dayjs';
import {
  getExportDashboardUrl,
  useGetDashboard,
  useGetSeries,
  useRebuildAnalytics,
} from '../../api/generated/analytics/dashboard';
import type { DepartmentRow, EngineerRow, GetDashboardParams } from '../../api/generated/analytics/model';
import { downloadFile } from '../../api/http';
import { ok } from '../../api/unwrap';
import { errorText } from '../../shared/lib/apiErrors';
import { useRoles } from '../../shared/lib/useRoles';
import { OrganizationSelect, ServiceDepartmentSelect } from '../directory/lookups';
import { levelColor, periodBounds, type PeriodKind } from './period';
import { GAUGES_COLLAPSED, isDeviation, loadLayout, saveLayout, type DashboardLayout } from './layout';

const num = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1 });
const hours = (v?: number | null) => (v == null ? '—' : `${num.format(v)} ч`);

function Gauge({
  title,
  subtitle,
  fact,
  norm,
  percent,
  computed,
  red,
  yellow,
  onClick,
  active,
}: {
  title: string;
  subtitle?: string | null;
  fact: number;
  norm?: number | null;
  percent: number;
  computed?: boolean;
  red: number;
  yellow: number;
  onClick?: () => void;
  active?: boolean;
}) {
  const hasNorm = norm != null && norm > 0;
  return (
    <Card
      size="small"
      className={`gauge-card${active ? ' active' : ''}`}
      hoverable={!!onClick}
      onClick={onClick}
    >
      <div className="gauge-title" title={title}>
        {title}
      </div>
      {subtitle && <div className="cell-muted gauge-sub">{subtitle}</div>}
      <Progress
        type="dashboard"
        size={120}
        percent={hasNorm ? Math.min(percent, 100) : 0}
        strokeColor={hasNorm ? levelColor(percent, red, yellow) : '#bbb'}
        format={() => (hasNorm ? `${percent}%` : 'нет нормы')}
      />
      <div className="gauge-numbers">
        <b>{hours(fact)}</b> из {hours(norm)}
        {computed && (
          <Tag className="gauge-tag" title="Норма отдела не задана — показана сумма норм инженеров">
            сумма
          </Tag>
        )}
      </div>
    </Card>
  );
}

/** Дашборд продуктивных часов (раздел 9 SPEC.md). */
/**
 * Цвета графиков из текущей темы: в тёмной — приглушённые, без белого фона всплывающей подсказки
 * и ярких синих и белых столбцов.
 */
function useChartColors() {
  const { token } = theme.useToken();
  const dark = document.querySelector('.theme-root')?.getAttribute('data-theme') === 'dark';
  return {
    grid: token.colorBorderSecondary,
    axis: { fill: token.colorTextSecondary, fontSize: 12 },
    norm: dark ? '#3c4a57' : '#c3cfda',
    fact: dark ? '#6f8fae' : '#1f5c99',
    line: dark ? '#c9a24a' : '#d99a1e',
    tooltip: {
      contentStyle: {
        background: token.colorBgElevated,
        border: `1px solid ${token.colorBorder}`,
        borderRadius: 6,
        color: token.colorText,
        boxShadow: token.boxShadowSecondary,
      },
      labelStyle: { color: token.colorText, fontWeight: 600 },
      itemStyle: { color: token.colorTextSecondary },
      cursor: { fill: dark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)' },
    },
    legend: { color: token.colorTextSecondary },
  };
}

export function DashboardPage() {
  const navigate = useNavigate();
  const colors = useChartColors();
  const [layout, setLayoutState] = useState<DashboardLayout>(loadLayout);
  const [showAllGauges, setShowAllGauges] = useState(false);
  const setLayout = (patch: Partial<DashboardLayout>) =>
    setLayoutState((l) => {
      const next = { ...l, ...patch };
      saveLayout(next);
      return next;
    });
  const { message } = App.useApp();
  const { isAdmin } = useRoles();
  const [kind, setKind] = useState<PeriodKind>('month');
  const [anchor, setAnchor] = useState<Dayjs>(dayjs());
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(null);
  const [departmentId, setDepartmentId] = useState<string>();
  const [engineerIds, setEngineerIds] = useState<string[]>([]);
  const [organizationId, setOrganizationId] = useState<string>();
  const [selected, setSelected] = useState<string>();
  const [granularity, setGranularity] = useState<'day' | 'week'>('day');
  const [exporting, setExporting] = useState(false);
  const rebuild = useRebuildAnalytics();

  const { from, to } = periodBounds(kind, anchor, range);
  const params: GetDashboardParams = {
    from,
    to,
    departmentId,
    engineerId: engineerIds.length ? engineerIds : undefined,
    organizationId,
  };
  const { data, isFetching, error } = useGetDashboard(params);
  const view = ok(data);
  // Список инженеров для фильтра — без фильтра по инженерам, чтобы не терять варианты.
  const all = ok(useGetDashboard({ from, to }).data);
  const scope = view?.scope ?? 'SELF';
  const allEngineers = useMemo(() => view?.engineers ?? [], [view]);
  const engineers = useMemo(
    () =>
      layout.onlyDeviations
        ? allEngineers.filter((e) => isDeviation(e.percent, e.normHours, view?.thresholds.yellow ?? 90))
        : allEngineers,
    [allEngineers, layout.onlyDeviations, view],
  );
  const gaugeEngineers = useMemo(() => {
    if (showAllGauges || engineers.length <= GAUGES_COLLAPSED) return engineers;
    // Сначала самые отстающие и превысившие норму — их важнее увидеть при большом числе инженеров.
    return [...engineers]
      .sort(
        (a, b) =>
          Math.abs(100 - (a.normHours ? a.percent : 100)) - Math.abs(100 - (b.normHours ? b.percent : 100)),
      )
      .reverse()
      .slice(0, GAUGES_COLLAPSED);
  }, [engineers, showAllGauges]);
  const current = selected ?? (engineers.length === 1 ? engineers[0].engineerId : undefined);
  const series = ok(
    useGetSeries({ engineerId: current ?? '', from, to, granularity }, { query: { enabled: !!current } })
      .data,
  );
  const red = view?.thresholds.red ?? 70;
  const yellow = view?.thresholds.yellow ?? 90;

  const exportXlsx = async () => {
    setExporting(true);
    try {
      await downloadFile(getExportDashboardUrl(params), `Продуктивные часы ${from} — ${to}.xlsx`);
    } catch (e) {
      message.error(errorText(e));
    } finally {
      setExporting(false);
    }
  };

  const doRebuild = async () => {
    try {
      const r = ok(await rebuild.mutateAsync())!;
      message.success(`Данные пересобраны: актов ${r.acts}, записей времени ${r.timeEntries}`);
    } catch (e) {
      message.error(errorText(e));
    }
  };

  const openActs = (e: EngineerRow) => navigate(`/acts?engineerId=${e.engineerId}&from=${from}&to=${to}`);
  const chartData = engineers.map((e) => ({ name: e.fullName, Факт: e.factHours, Норма: e.normHours ?? 0 }));
  const currentName = engineers.find((e) => e.engineerId === current)?.fullName;

  return (
    <section className="page">
      <div className="page-head">
        <Typography.Title level={2} className="page-title">
          Продуктивные часы
        </Typography.Title>
        <Space wrap>
          <Popover
            trigger="click"
            placement="bottomRight"
            title="Что показывать"
            content={
              <Space direction="vertical">
                <Checkbox
                  checked={layout.departments}
                  onChange={(e) => setLayout({ departments: e.target.checked })}
                >
                  Индикаторы отделов
                </Checkbox>
                <Checkbox
                  checked={layout.engineers}
                  onChange={(e) => setLayout({ engineers: e.target.checked })}
                >
                  Индикаторы инженеров
                </Checkbox>
                <Checkbox checked={layout.compare} onChange={(e) => setLayout({ compare: e.target.checked })}>
                  График «Факт против нормы»
                </Checkbox>
                <Checkbox checked={layout.daily} onChange={(e) => setLayout({ daily: e.target.checked })}>
                  Часы по дням
                </Checkbox>
                <Checkbox checked={layout.table} onChange={(e) => setLayout({ table: e.target.checked })}>
                  Таблица
                </Checkbox>
                <Checkbox
                  checked={layout.onlyDeviations}
                  onChange={(e) => setLayout({ onlyDeviations: e.target.checked })}
                >
                  Только инженеры с отклонением от нормы
                </Checkbox>
              </Space>
            }
          >
            <Button icon={<EyeOutlined />}>Вид</Button>
          </Popover>
          <Button icon={<CalendarOutlined />} onClick={() => navigate('/dashboard/norms')}>
            Нормы и календарь
          </Button>
          <Button icon={<FileExcelOutlined />} loading={exporting} onClick={() => void exportXlsx()}>
            Выгрузить в Excel
          </Button>
          {isAdmin && (
            <Button
              icon={<SyncOutlined />}
              loading={rebuild.isPending}
              onClick={() => void doRebuild()}
              title="Пересчитать все данные из актов"
            >
              Пересобрать
            </Button>
          )}
        </Space>
      </div>

      <div className="toolbar" style={{ marginTop: 16 }}>
        <Segmented
          value={kind}
          onChange={(v) => setKind(v as PeriodKind)}
          options={[
            { value: 'month', label: 'Месяц' },
            { value: 'quarter', label: 'Квартал' },
            { value: 'year', label: 'Год' },
            { value: 'range', label: 'Период' },
          ]}
        />
        {kind === 'range' ? (
          <DatePicker.RangePicker
            value={range}
            onChange={(v) => setRange(v && v[0] && v[1] ? [v[0], v[1]] : null)}
            format="DD.MM.YYYY"
          />
        ) : (
          <DatePicker
            picker={kind}
            value={anchor}
            allowClear={false}
            onChange={(v) => v && setAnchor(v)}
            format={kind === 'month' ? 'MMMM YYYY' : kind === 'quarter' ? 'YYYY, [Q]Q' : 'YYYY'}
          />
        )}
        {scope === 'ALL' && (
          <ServiceDepartmentSelect value={departmentId} onChange={setDepartmentId} style={{ width: 200 }} />
        )}
        {scope !== 'SELF' && (
          <Select
            mode="multiple"
            allowClear
            placeholder="Инженеры"
            value={engineerIds}
            onChange={setEngineerIds}
            optionFilterProp="label"
            maxTagCount="responsive"
            style={{ minWidth: 220 }}
            options={(all?.engineers ?? []).map((e) => ({ value: e.engineerId, label: e.fullName }))}
          />
        )}
        <OrganizationSelect
          allowClear
          value={organizationId}
          onChange={setOrganizationId}
          placeholder="Заказчик"
          style={{ width: 200 }}
        />
      </div>

      {error ? (
        <Empty description={errorText(error)} />
      ) : !view ? (
        <Spin size="large" style={{ display: 'block', margin: '60px auto' }} />
      ) : (
        <Spin spinning={isFetching}>
          {layout.departments && view.departments.length > 0 && scope !== 'SELF' && (
            <>
              <Typography.Title level={5} className="section-title">
                Отделы
              </Typography.Title>
              <div className="gauge-grid">
                {view.departments.map((d: DepartmentRow) => (
                  <Gauge
                    key={d.departmentId}
                    title={d.name}
                    fact={d.factHours}
                    norm={d.normHours}
                    percent={d.percent}
                    computed={d.normComputed}
                    red={red}
                    yellow={yellow}
                    onClick={() => setDepartmentId(d.departmentId)}
                  />
                ))}
              </div>
            </>
          )}

          {(layout.engineers || scope === 'SELF') && (
            <>
              <Typography.Title level={5} className="section-title">
                {scope === 'SELF' ? 'Мои показатели' : 'Инженеры'}
                {layout.onlyDeviations && scope !== 'SELF' && (
                  <Tag style={{ marginLeft: 8 }}>
                    только с отклонением: {engineers.length} из {allEngineers.length}
                  </Tag>
                )}
              </Typography.Title>
              {engineers.length === 0 ? (
                <Empty
                  description={layout.onlyDeviations ? 'Отклонений от нормы нет' : 'Нет данных за период'}
                />
              ) : (
                <>
                  <div className="gauge-grid">
                    {gaugeEngineers.map((e) => (
                      <Gauge
                        key={e.engineerId}
                        title={e.fullName}
                        subtitle={scope !== 'SELF' ? e.departmentName : undefined}
                        fact={e.factHours}
                        norm={e.normHours}
                        percent={e.percent}
                        red={red}
                        yellow={yellow}
                        active={e.engineerId === current}
                        onClick={() => setSelected(e.engineerId)}
                      />
                    ))}
                  </div>
                  {engineers.length > GAUGES_COLLAPSED && (
                    <Button type="link" onClick={() => setShowAllGauges((v) => !v)}>
                      {showAllGauges
                        ? 'Показать только наиболее отклонившихся'
                        : `Показать всех (${engineers.length}); сейчас — ${GAUGES_COLLAPSED} с наибольшим отклонением от нормы`}
                    </Button>
                  )}
                </>
              )}
            </>
          )}

          <div className="chart-row">
            {layout.compare && engineers.length > 1 && (
              <Card size="small" title="Факт против нормы" className="chart-card">
                <ResponsiveContainer width="100%" height={Math.max(220, engineers.length * 36)}>
                  <BarChart data={chartData} layout="vertical" margin={{ left: 20, right: 20 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke={colors.grid} />
                    <XAxis type="number" tick={colors.axis} stroke={colors.grid} />
                    <YAxis
                      type="category"
                      dataKey="name"
                      width={150}
                      tick={colors.axis}
                      stroke={colors.grid}
                    />
                    <Tooltip formatter={(v: number) => hours(v)} {...colors.tooltip} />
                    <Legend wrapperStyle={colors.legend} />
                    <Bar dataKey="Норма" fill={colors.norm} radius={[0, 3, 3, 0]} />
                    <Bar dataKey="Факт" fill={colors.fact} radius={[0, 3, 3, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </Card>
            )}
            {layout.daily && (
              <Card
                size="small"
                className="chart-card"
                title={currentName ? `Часы: ${currentName}` : 'Часы по дням'}
                extra={
                  <Segmented
                    size="small"
                    value={granularity}
                    onChange={(v) => setGranularity(v as 'day' | 'week')}
                    options={[
                      { value: 'day', label: 'По дням' },
                      { value: 'week', label: 'По неделям' },
                    ]}
                  />
                }
              >
                {!current ? (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="Выберите инженера на индикаторе выше"
                  />
                ) : (
                  <ResponsiveContainer width="100%" height={260}>
                    <LineChart
                      data={(series ?? []).map((p) => ({
                        date: dayjs(p.start).format(granularity === 'week' ? '[с] DD.MM' : 'DD.MM'),
                        Часы: p.hours,
                      }))}
                      margin={{ left: 0, right: 20 }}
                    >
                      <CartesianGrid strokeDasharray="3 3" stroke={colors.grid} />
                      <XAxis dataKey="date" tick={{ ...colors.axis, fontSize: 11 }} stroke={colors.grid} />
                      <YAxis tick={colors.axis} stroke={colors.grid} />
                      <Tooltip
                        formatter={(v: number) => hours(v)}
                        {...colors.tooltip}
                        cursor={{ stroke: colors.grid }}
                      />
                      <Line
                        type="linear"
                        dataKey="Часы"
                        stroke={colors.line}
                        strokeWidth={2}
                        dot={{ r: 3, fill: colors.line }}
                      />
                    </LineChart>
                  </ResponsiveContainer>
                )}
              </Card>
            )}
          </div>

          {layout.table && (
            <div className="page-panel" style={{ marginTop: 16 }}>
              <Table<EngineerRow>
                rowKey="engineerId"
                dataSource={engineers}
                pagination={false}
                size="middle"
                scroll={{ x: 760 }}
                onRow={(e) => ({ onClick: () => openActs(e), style: { cursor: 'pointer' } })}
                columns={[
                  {
                    title: 'Инженер',
                    dataIndex: 'fullName',
                    sorter: (a, b) => a.fullName.localeCompare(b.fullName),
                  },
                  { title: 'Отдел', dataIndex: 'departmentName', responsive: ['md'] },
                  { title: 'Норма', key: 'norm', align: 'right', render: (_, e) => hours(e.normHours) },
                  {
                    title: 'Факт',
                    key: 'fact',
                    align: 'right',
                    sorter: (a, b) => a.factHours - b.factHours,
                    render: (_, e) => <b>{hours(e.factHours)}</b>,
                  },
                  {
                    title: '%',
                    key: 'percent',
                    align: 'right',
                    sorter: (a, b) => a.percent - b.percent,
                    render: (_, e) =>
                      e.normHours ? (
                        <span style={{ color: levelColor(e.percent, red, yellow), fontWeight: 600 }}>
                          {e.percent}%
                        </span>
                      ) : (
                        '—'
                      ),
                  },
                  { title: 'Актов', dataIndex: 'acts', align: 'right' },
                  { title: 'Машин', dataIndex: 'machines', align: 'right' },
                ]}
              />
              <Typography.Paragraph type="secondary" className="hint">
                Нажмите на строку, чтобы открыть акты инженера за период. Учитываются открытые и закрытые
                акты; время в пути не входит в продуктивные часы.
              </Typography.Paragraph>
            </div>
          )}
        </Spin>
      )}
    </section>
  );
}

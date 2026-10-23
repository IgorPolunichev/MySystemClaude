import { useMemo, useState } from 'react';
import {
  App,
  Button,
  InputNumber,
  Popconfirm,
  Segmented,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { DeleteOutlined, LeftOutlined, RightOutlined, UserAddOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import { getGetActQueryKey } from '../../api/generated/acts/acts';
import {
  useAddActEngineer,
  useDeleteActTime,
  usePutActTime,
  useRemoveActEngineer,
} from '../../api/generated/acts/act-content';
import type { ActDetail, ActParticipant, ActTimeEntry } from '../../api/generated/acts/model';
import { ok } from '../../api/unwrap';
import { errorText } from '../../shared/lib/apiErrors';
import { invalidateByPrefix } from '../../shared/lib/invalidate';
import { addDays, formatHours, isoDate, weekStart } from './actMeta';
import { EngineerSelect } from './lookups';

const DAY_NAMES = ['пн', 'вт', 'ср', 'чт', 'пт', 'сб', 'вс'];
const dm = new Intl.DateTimeFormat('ru-RU', { day: '2-digit', month: '2-digit' });
const num = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 });

type Mode = 'hours' | 'travelHours';

/**
 * Табель «инженер × день» по неделям. Каждая ячейка сохраняется отдельным запросом при выходе из поля,
 * поэтому инженеры, вносящие время в один акт одновременно, не мешают друг другу.
 */
export function Timesheet({ act }: { act: ActDetail }) {
  const { message } = App.useApp();
  const client = useQueryClient();
  const putTime = usePutActTime();
  const deleteTime = useDeleteActTime();
  const addEngineer = useAddActEngineer();
  const removeEngineer = useRemoveActEngineer();
  const [mode, setMode] = useState<Mode>('hours');
  const [newEngineer, setNewEngineer] = useState<string>();
  const [week, setWeek] = useState(() =>
    weekStart(act.workStartDate ? new Date(`${act.workStartDate}T00:00:00`) : new Date()),
  );

  const days = useMemo(() => Array.from({ length: 7 }, (_, i) => addDays(week, i)), [week]);
  const entries = useMemo(() => {
    const map = new Map<string, ActTimeEntry>();
    act.timeEntries.forEach((e) => map.set(`${e.engineerId}|${e.workDate}`, e));
    return map;
  }, [act.timeEntries]);

  const refresh = async () => {
    await client.invalidateQueries({ queryKey: getGetActQueryKey(act.id) });
    await invalidateByPrefix(client, '/api/v1/acts?');
  };

  const canEditRow = (p: ActParticipant) =>
    act.permissions.canEditAnyTime || (act.permissions.canEdit && p.isCurrentUser);

  const commit = async (p: ActParticipant, date: string, value: number | null) => {
    const existing = entries.get(`${p.engineerId}|${date}`);
    const current = existing ? (mode === 'hours' ? existing.hours : (existing.travelHours ?? null)) : null;
    if ((value ?? 0) === (current ?? 0)) return;
    const hours = mode === 'hours' ? (value ?? 0) : (existing?.hours ?? 0);
    const travel = mode === 'travelHours' ? value : (existing?.travelHours ?? null);
    try {
      if (!hours && !travel) {
        if (existing) await deleteTime.mutateAsync({ id: act.id, engineerId: p.engineerId, date });
      } else {
        const res = ok(
          await putTime.mutateAsync({
            id: act.id,
            engineerId: p.engineerId,
            date,
            data: { hours, travelHours: travel, comment: existing?.comment ?? null },
          }),
        );
        if (res?.warning) message.warning(res.warning);
      }
      await refresh();
    } catch (e) {
      message.error(errorText(e));
      await refresh();
    }
  };

  const add = async () => {
    if (!newEngineer) return;
    try {
      await addEngineer.mutateAsync({ id: act.id, data: { engineerId: newEngineer } });
      setNewEngineer(undefined);
      await refresh();
    } catch (e) {
      message.error(errorText(e));
    }
  };

  const remove = async (p: ActParticipant) => {
    try {
      await removeEngineer.mutateAsync({ id: act.id, engineerId: p.engineerId });
      await refresh();
    } catch (e) {
      message.error(errorText(e));
    }
  };

  const weekTotal = (p: ActParticipant) =>
    days.reduce((sum, d) => {
      const e = entries.get(`${p.engineerId}|${isoDate(d)}`);
      return sum + (e ? (mode === 'hours' ? e.hours : (e.travelHours ?? 0)) : 0);
    }, 0);

  const dayTotal = (d: Date) =>
    act.engineers.reduce((sum, p) => {
      const e = entries.get(`${p.engineerId}|${isoDate(d)}`);
      return sum + (e ? (mode === 'hours' ? e.hours : (e.travelHours ?? 0)) : 0);
    }, 0);

  const today = isoDate(new Date());

  return (
    <div className="act-section">
      <div className="section-head">
        <Typography.Title level={5} style={{ margin: 0 }}>
          Инженеры и время
        </Typography.Title>
        <Space wrap>
          <Segmented
            size="small"
            value={mode}
            onChange={(v) => setMode(v as Mode)}
            options={[
              { value: 'hours', label: 'Часы работы' },
              { value: 'travelHours', label: 'Время в пути' },
            ]}
          />
        </Space>
      </div>

      <div className="week-nav">
        <Button
          size="small"
          icon={<LeftOutlined />}
          onClick={() => setWeek(addDays(week, -7))}
          aria-label="Предыдущая неделя"
        />
        <span className="week-title">
          {dm.format(days[0])} — {dm.format(days[6])}.{days[6].getFullYear()}
        </span>
        <Button
          size="small"
          icon={<RightOutlined />}
          onClick={() => setWeek(addDays(week, 7))}
          aria-label="Следующая неделя"
        />
        <Button size="small" type="link" onClick={() => setWeek(weekStart(new Date()))}>
          Текущая неделя
        </Button>
        {act.workStartDate && (
          <Button
            size="small"
            type="link"
            onClick={() => setWeek(weekStart(new Date(`${act.workStartDate}T00:00:00`)))}
          >
            К началу работ
          </Button>
        )}
      </div>

      <Table<ActParticipant>
        rowKey="engineerId"
        size="small"
        pagination={false}
        dataSource={act.engineers}
        scroll={{ x: 860 }}
        className="timesheet"
        summary={() => (
          <Table.Summary.Row>
            <Table.Summary.Cell index={0}>
              <span className="cell-muted">Итого за день</span>
            </Table.Summary.Cell>
            {days.map((d, i) => (
              <Table.Summary.Cell index={i + 1} key={i} align="center">
                <span className="cell-muted">{dayTotal(d) ? num.format(dayTotal(d)) : ''}</span>
              </Table.Summary.Cell>
            ))}
            <Table.Summary.Cell index={8} align="right">
              <b>{formatHours(act.engineers.reduce((s, p) => s + weekTotal(p), 0))}</b>
            </Table.Summary.Cell>
            <Table.Summary.Cell index={9} align="right">
              <b>{formatHours(mode === 'hours' ? act.totalHours : act.totalTravelHours)}</b>
            </Table.Summary.Cell>
            <Table.Summary.Cell index={10} />
          </Table.Summary.Row>
        )}
        columns={[
          {
            title: 'Инженер',
            key: 'name',
            fixed: 'left',
            width: 200,
            render: (_, p) => (
              <Space direction="vertical" size={0}>
                <span className="cell-strong">{p.fullName}</span>
                <span>
                  {p.role === 'LEAD' && <Tag color="gold">ответственный</Tag>}
                  {p.isCurrentUser && <Tag>это вы</Tag>}
                </span>
              </Space>
            ),
          },
          ...days.map((d, i) => {
            const iso = isoDate(d);
            const weekend = i >= 5;
            return {
              title: (
                <span className={`day-head${weekend ? ' weekend' : ''}${iso === today ? ' today' : ''}`}>
                  {DAY_NAMES[i]}
                  <br />
                  {dm.format(d)}
                </span>
              ),
              key: iso,
              width: 76,
              align: 'center' as const,
              render: (_: unknown, p: ActParticipant) => {
                const e = entries.get(`${p.engineerId}|${iso}`);
                const value = e ? (mode === 'hours' ? e.hours : (e.travelHours ?? null)) : null;
                return (
                  <InputNumber
                    key={`${p.engineerId}|${iso}|${mode}|${value ?? ''}`}
                    size="small"
                    min={0}
                    max={24}
                    step={0.25}
                    controls={false}
                    formatter={(v) =>
                      v === undefined || v === null || `${v}` === ''
                        ? ''
                        : String(Number(v)).replace('.', ',')
                    }
                    parser={(v) => (v ? Number(v.replace(',', '.')) : (null as unknown as number))}
                    defaultValue={value ?? undefined}
                    disabled={!canEditRow(p)}
                    className={`hours-cell${weekend ? ' weekend' : ''}`}
                    aria-label={`${p.fullName}, ${dm.format(d)}`}
                    onBlur={(ev) => {
                      const raw = ev.target.value.replace(',', '.').trim();
                      void commit(p, iso, raw === '' ? null : Number(raw));
                    }}
                    onPressEnter={(ev) => (ev.target as HTMLInputElement).blur()}
                  />
                );
              },
            };
          }),
          {
            title: 'Неделя',
            key: 'week',
            width: 80,
            align: 'right',
            render: (_, p) => formatHours(weekTotal(p)),
          },
          {
            title: 'Всего',
            key: 'total',
            width: 80,
            align: 'right',
            render: (_, p) => <b>{formatHours(mode === 'hours' ? p.totalHours : null)}</b>,
          },
          {
            title: '',
            key: 'actions',
            width: 44,
            render: (_, p) =>
              act.permissions.canEdit &&
              p.role !== 'LEAD' && (
                <Popconfirm
                  title="Убрать инженера из акта?"
                  okText="Убрать"
                  cancelText="Отмена"
                  onConfirm={() => void remove(p)}
                >
                  <Tooltip title="Убрать из акта">
                    <Button type="text" size="small" danger icon={<DeleteOutlined />} />
                  </Tooltip>
                </Popconfirm>
              ),
          },
        ]}
      />

      {act.permissions.canEdit && (
        <Space.Compact style={{ marginTop: 12, width: '100%', maxWidth: 480 }}>
          <EngineerSelect
            value={newEngineer}
            onChange={setNewEngineer}
            exclude={act.engineers.map((e) => e.engineerId)}
            placeholder="Добавить инженера в акт"
            style={{ width: '100%' }}
          />
          <Button
            icon={<UserAddOutlined />}
            disabled={!newEngineer}
            loading={addEngineer.isPending}
            onClick={() => void add()}
          >
            Добавить
          </Button>
        </Space.Compact>
      )}
      <Typography.Paragraph type="secondary" className="hint">
        Шаг — 0,25 ч (15 минут). Значение сохраняется при выходе из ячейки; пустая ячейка удаляет запись.
        {!act.permissions.canEditAnyTime &&
          act.permissions.canEdit &&
          ' Вы можете вносить время только за себя.'}
      </Typography.Paragraph>
    </div>
  );
}

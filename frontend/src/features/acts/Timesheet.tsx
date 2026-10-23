import { useMemo, useState } from 'react';
import { App, Button, Popconfirm, Space, Table, Tag, Tooltip, Typography } from 'antd';
import { CarOutlined, DeleteOutlined, LeftOutlined, RightOutlined, UserAddOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import { getGetActQueryKey } from '../../api/generated/acts/acts';
import {
  useAddActEngineer,
  useDeleteActTime,
  usePutActTime,
  useRemoveActEngineer,
} from '../../api/generated/acts/act-content';
import type { ActDetail, ActParticipant, ActTimeEntry, TimeEntryInput } from '../../api/generated/acts/model';
import { ok } from '../../api/unwrap';
import { errorText } from '../../shared/lib/apiErrors';
import { invalidateByPrefix } from '../../shared/lib/invalidate';
import { addDays, formatHours, isoDate, weekStart } from './actMeta';
import { EngineerSelect } from './lookups';
import { TimeEntryDialog } from './TimeEntryDialog';
import { formatDuration } from './workTime';

const DAY_NAMES = ['пн', 'вт', 'ср', 'чт', 'пт', 'сб', 'вс'];
const dm = new Intl.DateTimeFormat('ru-RU', { day: '2-digit', month: '2-digit' });
const dmy = new Intl.DateTimeFormat('ru-RU', { weekday: 'long', day: 'numeric', month: 'long' });
const num = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 });

/**
 * Табель «инженер × день» по неделям. В ячейке — интервал работы и рассчитанное продуктивное время;
 * по клику открывается ввод интервала и перерывов. Каждая ячейка сохраняется отдельным запросом.
 */
export function Timesheet({ act }: { act: ActDetail }) {
  const { message } = App.useApp();
  const client = useQueryClient();
  const putTime = usePutActTime();
  const deleteTime = useDeleteActTime();
  const addEngineer = useAddActEngineer();
  const removeEngineer = useRemoveActEngineer();
  const [newEngineer, setNewEngineer] = useState<string>();
  const [editing, setEditing] = useState<{ participant: ActParticipant; date: Date } | null>(null);
  const [week, setWeek] = useState(() =>
    weekStart(act.workStartDate ? new Date(`${act.workStartDate}T00:00:00`) : new Date()),
  );

  const days = useMemo(() => Array.from({ length: 7 }, (_, i) => addDays(week, i)), [week]);
  const entries = useMemo(() => {
    const map = new Map<string, ActTimeEntry>();
    act.timeEntries.forEach((e) => map.set(`${e.engineerId}|${e.workDate}`, e));
    return map;
  }, [act.timeEntries]);
  const entryOf = (engineerId: string, d: Date) => entries.get(`${engineerId}|${isoDate(d)}`);

  const refresh = async () => {
    await client.invalidateQueries({ queryKey: getGetActQueryKey(act.id) });
    await invalidateByPrefix(client, '/api/v1/acts?');
  };

  const canEditRow = (p: ActParticipant) =>
    act.permissions.canEditAnyTime || (act.permissions.canEdit && p.isCurrentUser);

  const save = async (p: ActParticipant, date: Date, data: TimeEntryInput) => {
    try {
      const res = ok(
        await putTime.mutateAsync({ id: act.id, engineerId: p.engineerId, date: isoDate(date), data }),
      );
      if (res?.warning) message.warning(res.warning);
      await refresh();
      return true;
    } catch (e) {
      message.error(errorText(e));
      return false;
    }
  };

  const remove = async (p: ActParticipant, date: Date) => {
    try {
      await deleteTime.mutateAsync({ id: act.id, engineerId: p.engineerId, date: isoDate(date) });
      await refresh();
      return true;
    } catch (e) {
      message.error(errorText(e));
      return false;
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

  const removeParticipant = async (p: ActParticipant) => {
    try {
      await removeEngineer.mutateAsync({ id: act.id, engineerId: p.engineerId });
      await refresh();
    } catch (e) {
      message.error(errorText(e));
    }
  };

  const minutesOf = (p: ActParticipant, list: Date[]) =>
    list.reduce((sum, d) => sum + (entryOf(p.engineerId, d)?.workMinutes ?? 0), 0);
  const dayMinutes = (d: Date) =>
    act.engineers.reduce((sum, p) => sum + (entryOf(p.engineerId, d)?.workMinutes ?? 0), 0);
  const today = isoDate(new Date());

  return (
    <div className="act-section">
      <div className="section-head">
        <Typography.Title level={5} style={{ margin: 0 }}>
          Инженеры и время
        </Typography.Title>
        <span className="cell-muted">
          Всего: <b>{formatHours(act.totalHours)}</b>
          {act.totalTravelHours > 0 && ` · в пути ${formatHours(act.totalTravelHours)}`}
        </span>
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
        scroll={{ x: 980 }}
        className="timesheet"
        summary={() => (
          <Table.Summary.Row>
            <Table.Summary.Cell index={0}>
              <span className="cell-muted">Итого за день</span>
            </Table.Summary.Cell>
            {days.map((d, i) => (
              <Table.Summary.Cell index={i + 1} key={i} align="center">
                <span className="cell-muted">
                  {dayMinutes(d) ? `${num.format(dayMinutes(d) / 60)} ч` : ''}
                </span>
              </Table.Summary.Cell>
            ))}
            <Table.Summary.Cell index={8} align="right">
              <b>{formatHours(act.engineers.reduce((s, p) => s + minutesOf(p, days), 0) / 60)}</b>
            </Table.Summary.Cell>
            <Table.Summary.Cell index={9} align="right">
              <b>{formatHours(act.totalHours)}</b>
            </Table.Summary.Cell>
            <Table.Summary.Cell index={10} />
          </Table.Summary.Row>
        )}
        columns={[
          {
            title: 'Инженер',
            key: 'name',
            fixed: 'left',
            width: 190,
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
              width: 96,
              align: 'center' as const,
              render: (_: unknown, p: ActParticipant) => {
                const e = entryOf(p.engineerId, d);
                const editable = canEditRow(p);
                const label = e ? (
                  <>
                    {e.startTime && (
                      <span className="time-range">
                        {e.startTime}–{e.endTime}
                      </span>
                    )}
                    <b>{e.workMinutes ? formatDuration(e.workMinutes) : '—'}</b>
                    {!!e.travelHours && (
                      <span className="time-travel">
                        <CarOutlined /> {num.format(e.travelHours)} ч
                      </span>
                    )}
                  </>
                ) : (
                  <span className="time-empty">{editable ? '+' : ''}</span>
                );
                const tip = e
                  ? [
                      e.startTime && `${e.startTime}–${e.endTime}`,
                      e.breaks.length
                        ? `перерывы: ${e.breaks.map((b) => `${b.startTime}–${b.endTime}`).join(', ')}`
                        : null,
                      e.comment,
                    ]
                      .filter(Boolean)
                      .join(' · ')
                  : undefined;
                return (
                  <Tooltip title={tip}>
                    <button
                      type="button"
                      className={`time-cell${weekend ? ' weekend' : ''}${e ? ' filled' : ''}`}
                      disabled={!editable}
                      aria-label={`${p.fullName}, ${dm.format(d)}${e ? `: ${formatDuration(e.workMinutes)}` : ''}`}
                      onClick={() => setEditing({ participant: p, date: d })}
                    >
                      {label}
                    </button>
                  </Tooltip>
                );
              },
            };
          }),
          {
            title: 'Неделя',
            key: 'week',
            width: 80,
            align: 'right',
            render: (_, p) => formatHours(minutesOf(p, days) / 60),
          },
          {
            title: 'Всего',
            key: 'total',
            width: 80,
            align: 'right',
            render: (_, p) => <b>{formatHours(p.totalHours)}</b>,
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
                  onConfirm={() => void removeParticipant(p)}
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
        Нажмите на ячейку, чтобы указать время начала и окончания работы и перерывы — продуктивные часы
        посчитаются сами.
        {!act.permissions.canEditAnyTime &&
          act.permissions.canEdit &&
          ' Вы можете вносить время только за себя.'}
      </Typography.Paragraph>

      {editing && (
        <TimeEntryDialog
          title={`${editing.participant.fullName} · ${dmy.format(editing.date)}`}
          entry={entryOf(editing.participant.engineerId, editing.date)}
          previous={entryOf(editing.participant.engineerId, addDays(editing.date, -1))}
          saving={putTime.isPending}
          onSave={(data) => save(editing.participant, editing.date, data)}
          onDelete={
            entryOf(editing.participant.engineerId, editing.date)
              ? () => remove(editing.participant, editing.date)
              : undefined
          }
          onClose={() => setEditing(null)}
        />
      )}
    </div>
  );
}

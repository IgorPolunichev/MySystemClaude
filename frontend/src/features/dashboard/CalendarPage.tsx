import { useState } from 'react';
import { App, Button, DatePicker, Input, Popconfirm, Segmented, Space, Table, Tag, Typography } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import { useQueryClient } from '@tanstack/react-query';
import {
  getGetCalendarQueryKey,
  useGetCalendar,
  useSetCalendarDay,
} from '../../api/generated/analytics/calendar';
import type { CalendarDay } from '../../api/generated/analytics/model';
import { ok } from '../../api/unwrap';
import { errorText } from '../../shared/lib/apiErrors';
import { useRoles } from '../../shared/lib/useRoles';

/** Производственный календарь: пн–пт рабочие, исключения задаёт администратор. */
export function CalendarPage() {
  const { message } = App.useApp();
  const { isAdmin } = useRoles();
  const client = useQueryClient();
  const [year, setYear] = useState<Dayjs>(dayjs());
  const y = year.year();
  const { data, isFetching } = useGetCalendar(y);
  const view = ok(data);
  const set = useSetCalendarDay();
  const [day, setDay] = useState<Dayjs | null>(null);
  const [working, setWorking] = useState<'off' | 'work'>('off');
  const [comment, setComment] = useState('');

  const save = async (date: string, value: boolean | null, text?: string) => {
    try {
      await set.mutateAsync({ date, data: { working: value, comment: text ?? null } });
      await client.invalidateQueries({ queryKey: getGetCalendarQueryKey(y) });
    } catch (e) {
      message.error(errorText(e));
    }
  };

  return (
    <>
      <div className="toolbar">
        <DatePicker picker="year" value={year} allowClear={false} onChange={(v) => v && setYear(v)} />
        <Typography.Text type="secondary">
          Рабочие дни — понедельник–пятница; ниже — праздники, переносы и рабочие выходные.
        </Typography.Text>
      </div>
      {isAdmin && (
        <div className="toolbar more-filters">
          <DatePicker value={day} onChange={setDay} format="DD.MM.YYYY" placeholder="Дата" />
          <Segmented
            value={working}
            onChange={(v) => setWorking(v as 'off' | 'work')}
            options={[
              { value: 'off', label: 'Выходной / праздник' },
              { value: 'work', label: 'Рабочий день' },
            ]}
          />
          <Input
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            placeholder="Комментарий"
            maxLength={200}
            style={{ width: 240 }}
          />
          <Button
            type="primary"
            icon={<PlusOutlined />}
            disabled={!day}
            loading={set.isPending}
            onClick={() => {
              if (!day) return;
              void save(day.format('YYYY-MM-DD'), working === 'work', comment.trim() || undefined).then(
                () => {
                  setDay(null);
                  setComment('');
                },
              );
            }}
          >
            Добавить
          </Button>
        </div>
      )}
      <div className="calendar-layout">
        <div className="page-panel">
          <Typography.Title level={5} style={{ marginTop: 0 }}>
            Исключения {y} года
          </Typography.Title>
          <Table<CalendarDay>
            rowKey="date"
            loading={isFetching}
            dataSource={view?.exceptions}
            pagination={false}
            size="small"
            locale={{ emptyText: 'Исключений нет' }}
            columns={[
              { title: 'Дата', key: 'date', render: (_, d) => dayjs(d.date).format('DD.MM.YYYY, dd') },
              {
                title: 'День',
                key: 'working',
                render: (_, d) =>
                  d.working ? <Tag color="blue">рабочий</Tag> : <Tag color="red">выходной</Tag>,
              },
              {
                title: '',
                key: 'x',
                width: 50,
                render: (_, d) =>
                  isAdmin && (
                    <Popconfirm
                      title="Убрать исключение?"
                      okText="Убрать"
                      cancelText="Отмена"
                      onConfirm={() => void save(d.date, null)}
                    >
                      <Button type="text" danger size="small" icon={<DeleteOutlined />} />
                    </Popconfirm>
                  ),
              },
            ]}
          />
        </div>
        <div className="page-panel">
          <Typography.Title level={5} style={{ marginTop: 0 }}>
            Рабочих дней по месяцам
          </Typography.Title>
          <Space direction="vertical" style={{ width: '100%' }} size={2}>
            {(view?.months ?? []).map((m) => (
              <div key={m.month} className="month-line">
                <span>{dayjs(`${m.month}-01`).format('MMMM')}</span>
                <b>{m.workingDays}</b>
              </div>
            ))}
          </Space>
        </div>
      </div>
    </>
  );
}

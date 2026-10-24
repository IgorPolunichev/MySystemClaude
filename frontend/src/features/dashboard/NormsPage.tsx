import { useState } from 'react';
import { App, Button, DatePicker, InputNumber, Space, Table, Tag, Typography } from 'antd';
import { CopyOutlined, TeamOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import { useQueryClient } from '@tanstack/react-query';
import {
  getGetNormsQueryKey,
  useCopyPreviousNorms,
  useGetNorms,
  useSetDepartmentNorm,
  useSetEngineerNorm,
  useSetNormForAllEngineers,
} from '../../api/generated/analytics/norms';
import type { DepartmentNorm, EngineerNorm } from '../../api/generated/analytics/model';
import { ok } from '../../api/unwrap';
import { errorText } from '../../shared/lib/apiErrors';
import { useRoles } from '../../shared/lib/useRoles';

const num = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 });

/** Поле нормы: сохраняется при выходе из поля; пустое — норма удаляется. */
function HoursInput({
  value,
  disabled,
  onSave,
}: {
  value?: number | null;
  disabled: boolean;
  onSave: (v: number | null) => void;
}) {
  return (
    <InputNumber
      key={String(value ?? '')}
      defaultValue={value ?? undefined}
      min={0}
      max={744}
      step={8}
      disabled={disabled}
      decimalSeparator=","
      addonAfter="ч"
      style={{ width: 130 }}
      onBlur={(e) => {
        const raw = e.target.value.replace(',', '.').trim();
        const next = raw === '' ? null : Number(raw);
        if (next !== (value ?? null) && (next === null || !Number.isNaN(next))) onSave(next);
      }}
      onPressEnter={(e) => (e.target as HTMLInputElement).blur()}
    />
  );
}

/** Нормы часов на месяц: инженеры и отделы, массовые операции (раздел 9 SPEC.md). */
export function NormsPage() {
  const { message, modal } = App.useApp();
  const { isAdmin } = useRoles();
  const client = useQueryClient();
  const [month, setMonth] = useState<Dayjs>(dayjs());
  const key = month.format('YYYY-MM');
  const { data, isFetching } = useGetNorms({ month: key });
  const view = ok(data);
  const setEngineer = useSetEngineerNorm();
  const setDepartment = useSetDepartmentNorm();
  const setAll = useSetNormForAllEngineers();
  const copy = useCopyPreviousNorms();
  const editable = view?.canEdit ?? false;

  const refresh = () => client.invalidateQueries({ queryKey: getGetNormsQueryKey({ month: key }) });
  const run = async (action: () => Promise<unknown>, done?: string) => {
    try {
      await action();
      await refresh();
      if (done) message.success(done);
    } catch (e) {
      message.error(errorText(e));
    }
  };

  const bulk = (d: DepartmentNorm) => {
    let hours: number | null = null;
    modal.confirm({
      title: `Норма всем инженерам: ${d.name}`,
      content: (
        <Space direction="vertical">
          <span>Норма на {month.format('MMMM YYYY')} для каждого активного инженера отдела:</span>
          <InputNumber min={0} max={744} step={8} addonAfter="ч" autoFocus onChange={(v) => (hours = v)} />
        </Space>
      ),
      okText: 'Установить',
      cancelText: 'Отмена',
      onOk: () =>
        run(async () => {
          if (hours == null) throw new Error('Укажите норму');
          const r = ok(
            await setAll.mutateAsync({ departmentId: d.departmentId, month: key, data: { hours } }),
          );
          message.success(`Норма установлена инженерам: ${r?.count ?? 0}`);
        }),
    });
  };

  return (
    <>
      <div className="toolbar">
        <DatePicker
          picker="month"
          value={month}
          allowClear={false}
          onChange={(v) => v && setMonth(v)}
          format="MMMM YYYY"
        />
        {view && <Typography.Text type="secondary">Рабочих дней: {view.workingDays}</Typography.Text>}
        <div className="toolbar-spacer" />
        {isAdmin && (
          <Button
            icon={<CopyOutlined />}
            loading={copy.isPending}
            onClick={() =>
              void run(async () => {
                const r = ok(await copy.mutateAsync({ month: key }));
                message.success(`Скопировано норм: ${r?.count ?? 0} (уже заданные не изменялись)`);
              })
            }
          >
            Скопировать нормы прошлого месяца
          </Button>
        )}
      </div>

      <div className="page-panel">
        <Typography.Title level={5} style={{ marginTop: 0 }}>
          Отделы
        </Typography.Title>
        <Table<DepartmentNorm>
          rowKey="departmentId"
          loading={isFetching}
          dataSource={view?.departments}
          pagination={false}
          size="small"
          columns={[
            { title: 'Отдел', dataIndex: 'name' },
            {
              title: 'Норма отдела',
              key: 'hours',
              render: (_, d) => (
                <HoursInput
                  value={d.hours}
                  disabled={!editable}
                  onSave={(v) =>
                    void run(() =>
                      setDepartment.mutateAsync({
                        departmentId: d.departmentId,
                        month: key,
                        data: { hours: v },
                      }),
                    )
                  }
                />
              ),
            },
            {
              title: 'Сумма норм инженеров',
              key: 'computed',
              render: (_, d) => (
                <Space>
                  {d.computedHours != null ? `${num.format(d.computedHours)} ч` : '—'}
                  {d.hours == null && d.computedHours != null && <Tag>используется</Tag>}
                </Space>
              ),
            },
            {
              title: '',
              key: 'bulk',
              width: 220,
              render: (_, d) =>
                editable && (
                  <Button size="small" icon={<TeamOutlined />} onClick={() => bulk(d)}>
                    Норма всем инженерам
                  </Button>
                ),
            },
          ]}
        />
      </div>

      <div className="page-panel" style={{ marginTop: 16 }}>
        <Typography.Title level={5} style={{ marginTop: 0 }}>
          Инженеры
        </Typography.Title>
        <Table<EngineerNorm>
          rowKey="engineerId"
          loading={isFetching}
          dataSource={view?.engineers}
          pagination={false}
          size="small"
          columns={[
            { title: 'Инженер', dataIndex: 'fullName' },
            { title: 'Отдел', dataIndex: 'departmentName' },
            {
              title: 'Норма на месяц',
              key: 'hours',
              render: (_, e) => (
                <HoursInput
                  value={e.hours}
                  disabled={!editable}
                  onSave={(v) =>
                    void run(() =>
                      setEngineer.mutateAsync({ engineerId: e.engineerId, month: key, data: { hours: v } }),
                    )
                  }
                />
              ),
            },
          ]}
        />
        <Typography.Paragraph type="secondary" className="hint">
          Норма сохраняется при выходе из поля; пустое поле удаляет норму. Если норма отдела не задана,
          используется сумма норм его инженеров. Норма за квартал или произвольный период складывается из
          месячных, неполный месяц — пропорционально рабочим дням.
        </Typography.Paragraph>
      </div>
    </>
  );
}

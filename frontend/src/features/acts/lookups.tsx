import { useState } from 'react';
import { Select, Space, Typography } from 'antd';
import { useGetEquipment, useListEquipment } from '../../api/generated/directory/equipment';
import { useListEngineers } from '../../api/generated/directory/engineers';
import type { Equipment } from '../../api/generated/directory/model';
import { ok } from '../../api/unwrap';

function equipmentLabel(e: Equipment) {
  const numbers = [
    e.garageNumber && `гар. № ${e.garageNumber}`,
    e.plateNumber,
    e.serialNumber && `S/N ${e.serialNumber}`,
  ]
    .filter(Boolean)
    .join(' · ');
  return (
    <Space direction="vertical" size={0} style={{ lineHeight: 1.3 }}>
      <span>
        <b>
          {e.brand} {e.model}
        </b>{' '}
        <Typography.Text type="secondary">{e.organizationName}</Typography.Text>
      </span>
      <span className="cell-codes cell-muted">{numbers}</span>
    </Space>
  );
}

/** Выбор техники: поиск на сервере по любому номеру, марке, модели. */
export function EquipmentSelect({
  value,
  onChange,
  disabled,
}: {
  value?: string | null;
  onChange?: (v: string | undefined) => void;
  disabled?: boolean;
}) {
  const [q, setQ] = useState('');
  const { data, isFetching } = useListEquipment({ q: q || undefined, size: 20 });
  const selected = useGetEquipment(value ?? '', { query: { enabled: !!value } });
  const list = ok(data)?.items ?? [];
  const current = ok(selected.data);
  const items = current && !list.some((e) => e.id === current.id) ? [current, ...list] : list;
  return (
    <Select
      showSearch
      allowClear
      disabled={disabled}
      value={value ?? undefined}
      onChange={(v) => onChange?.(v)}
      onSearch={setQ}
      filterOption={false}
      loading={isFetching}
      placeholder="Найдите по гаражному, госномеру, серийному номеру или модели"
      notFoundContent={isFetching ? 'Поиск…' : 'Ничего не найдено'}
      options={items.map((e) => ({ value: e.id, label: equipmentLabel(e), title: `${e.brand} ${e.model}` }))}
      optionLabelProp="title"
      style={{ width: '100%' }}
    />
  );
}

/** Выбор активного инженера (для добавления в акт и фильтра реестра). */
export function EngineerSelect({
  value,
  onChange,
  exclude = [],
  placeholder = 'Инженер',
  style,
  allowClear,
}: {
  value?: string | null;
  onChange?: (v: string | undefined) => void;
  exclude?: string[];
  placeholder?: string;
  style?: React.CSSProperties;
  allowClear?: boolean;
}) {
  const [q, setQ] = useState('');
  const { data, isFetching } = useListEngineers({ q: q || undefined, active: true, size: 30 });
  const items = (ok(data)?.items ?? []).filter((e) => !exclude.includes(e.id));
  return (
    <Select
      showSearch
      allowClear={allowClear}
      value={value ?? undefined}
      onChange={(v) => onChange?.(v)}
      onSearch={setQ}
      filterOption={false}
      loading={isFetching}
      placeholder={placeholder}
      style={style}
      options={items.map((e) => ({
        value: e.id,
        label: e.serviceDepartmentName ? `${e.fullName} · ${e.serviceDepartmentName}` : e.fullName,
      }))}
    />
  );
}

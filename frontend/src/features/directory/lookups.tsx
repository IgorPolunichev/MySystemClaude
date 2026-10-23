import { useMemo, useState } from 'react';
import { Select, TreeSelect } from 'antd';
import { useGetOrganization, useListOrganizations } from '../../api/generated/directory/organizations';
import { useListOrganizationDepartments } from '../../api/generated/directory/departments';
import { useListEquipmentTypes } from '../../api/generated/directory/equipment-types';
import { useListServiceDepartments } from '../../api/generated/directory/service-departments';
import { ok } from '../../api/unwrap';
import { departmentTree } from './departmentTree';

interface ValueProps {
  value?: string | null;
  onChange?: (value: string | undefined) => void;
  placeholder?: string;
  allowClear?: boolean;
  style?: React.CSSProperties;
}

/** Выбор организации с поиском по названию и ИНН на сервере. */
export function OrganizationSelect({
  value,
  onChange,
  placeholder = 'Организация',
  allowClear,
  style,
}: ValueProps) {
  const [q, setQ] = useState('');
  const { data, isFetching } = useListOrganizations({ q: q || undefined, active: true, size: 20 });
  // Выбранная организация может не попасть в первые 20 результатов поиска — подгружаем её отдельно.
  const selected = useGetOrganization(value ?? '', { query: { enabled: !!value } });
  const list = ok(data)?.items ?? [];
  const selectedOrg = ok(selected.data);
  const extra = selectedOrg && !list.some((o) => o.id === value) ? [selectedOrg] : [];
  const options = [...extra, ...list].map((o) => ({ value: o.id, label: o.shortName ?? o.name }));
  return (
    <Select
      showSearch
      value={value ?? undefined}
      onChange={(v) => onChange?.(v)}
      onSearch={setQ}
      filterOption={false}
      loading={isFetching}
      options={options}
      placeholder={placeholder}
      allowClear={allowClear}
      style={style}
      notFoundContent={isFetching ? 'Поиск…' : 'Ничего не найдено'}
    />
  );
}

/** Выбор подразделения выбранной организации (деревом). */
export function DepartmentSelect({
  organizationId,
  value,
  onChange,
  placeholder = 'Подразделение',
  allowClear = true,
  style,
  excludeId,
}: ValueProps & { organizationId?: string | null; excludeId?: string }) {
  const { data, isFetching } = useListOrganizationDepartments(organizationId ?? '', {
    query: { enabled: !!organizationId },
  });
  const tree = useMemo(
    () => departmentTree((ok(data) ?? []).filter((d) => d.id !== excludeId)),
    [data, excludeId],
  );
  return (
    <TreeSelect
      value={value ?? undefined}
      onChange={(v) => onChange?.(v)}
      treeData={tree}
      treeDefaultExpandAll
      loading={isFetching}
      disabled={!organizationId}
      placeholder={organizationId ? placeholder : 'Сначала выберите организацию'}
      allowClear={allowClear}
      style={style}
      showSearch
      treeNodeFilterProp="title"
    />
  );
}

export function EquipmentTypeSelect({
  value,
  onChange,
  placeholder = 'Тип техники',
  allowClear,
  style,
}: ValueProps) {
  const { data, isFetching } = useListEquipmentTypes();
  return (
    <Select
      showSearch
      optionFilterProp="label"
      value={value ?? undefined}
      onChange={(v) => onChange?.(v)}
      loading={isFetching}
      options={(ok(data) ?? []).map((t) => ({ value: t.id, label: t.name }))}
      placeholder={placeholder}
      allowClear={allowClear}
      style={style}
    />
  );
}

export function ServiceDepartmentSelect({
  value,
  onChange,
  placeholder = 'Отдел',
  allowClear = true,
  style,
}: ValueProps) {
  const { data, isFetching } = useListServiceDepartments();
  return (
    <Select
      value={value ?? undefined}
      onChange={(v) => onChange?.(v)}
      loading={isFetching}
      options={(ok(data) ?? []).map((d) => ({ value: d.id, label: d.name }))}
      placeholder={placeholder}
      allowClear={allowClear}
      style={style}
    />
  );
}

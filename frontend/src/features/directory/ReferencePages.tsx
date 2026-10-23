import { ok } from '../../api/unwrap';
import {
  useCreateEquipmentType,
  useDeleteEquipmentType,
  useListEquipmentTypes,
  useUpdateEquipmentType,
} from '../../api/generated/directory/equipment-types';
import {
  useCreateServiceDepartment,
  useDeleteServiceDepartment,
  useListServiceDepartments,
  useUpdateServiceDepartment,
} from '../../api/generated/directory/service-departments';
import { useRoles } from '../../shared/lib/useRoles';
import { NamedListPage } from './NamedListPage';

export function EquipmentTypesPage() {
  const { canEdit } = useRoles();
  const { data, isFetching } = useListEquipmentTypes();
  const create = useCreateEquipmentType();
  const update = useUpdateEquipmentType();
  const remove = useDeleteEquipmentType();
  return (
    <NamedListPage
      url="/api/v1/equipment-types"
      items={ok(data)}
      loading={isFetching}
      canAdd
      canEdit={canEdit}
      hint="Типы используются в карточках техники и для выбора формы акта."
      newTitle="Новый тип техники"
      editTitle="Тип техники"
      create={(name) => create.mutateAsync({ data: { name } })}
      update={(item, name) => update.mutateAsync({ id: item.id, data: { name, version: item.version } })}
      remove={(item) => remove.mutateAsync({ id: item.id })}
    />
  );
}

export function ServiceDepartmentsPage() {
  const { isAdmin } = useRoles();
  const { data, isFetching } = useListServiceDepartments();
  const create = useCreateServiceDepartment();
  const update = useUpdateServiceDepartment();
  const remove = useDeleteServiceDepartment();
  return (
    <NamedListPage
      url="/api/v1/service-departments"
      items={ok(data)}
      loading={isFetching}
      canAdd={isAdmin}
      canEdit={isAdmin}
      hint="Отделы сервисной компании: в них числятся инженеры, на них задаются нормы часов."
      newTitle="Новый отдел"
      editTitle="Отдел"
      create={(name) => create.mutateAsync({ data: { name } })}
      update={(item, name) => update.mutateAsync({ id: item.id, data: { name, version: item.version } })}
      remove={(item) => remove.mutateAsync({ id: item.id })}
    />
  );
}

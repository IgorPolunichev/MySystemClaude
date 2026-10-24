import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { Segmented, Typography } from 'antd';
import { EquipmentPage } from './EquipmentPage';
import { EngineersPage } from './EngineersPage';
import { OrganizationsPage } from './OrganizationsPage';
import { EquipmentTypesPage, ServiceDepartmentsPage } from './ReferencePages';
import { ExecutorsPage } from './ExecutorsPage';

const SECTIONS = [
  { value: 'equipment', label: 'Техника', element: <EquipmentPage /> },
  { value: 'organizations', label: 'Организации', element: <OrganizationsPage /> },
  { value: 'engineers', label: 'Инженеры', element: <EngineersPage /> },
  { value: 'executors', label: 'Исполнители', element: <ExecutorsPage /> },
  { value: 'equipment-types', label: 'Типы техники', element: <EquipmentTypesPage /> },
  { value: 'service-departments', label: 'Отделы сервиса', element: <ServiceDepartmentsPage /> },
];

/** Раздел «Справочники»: вкладки с отдельным адресом у каждой, чтобы ссылку можно было отправить. */
export function DirectoryLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const current = location.pathname.split('/')[2] ?? SECTIONS[0].value;

  return (
    <section className="page">
      <Typography.Title level={2} className="page-title">
        Справочники
      </Typography.Title>
      <div className="section-tabs">
        <Segmented
          value={current}
          onChange={(v) => navigate(`/directory/${v}`)}
          options={SECTIONS.map(({ value, label }) => ({ value, label }))}
        />
      </div>
      <Routes>
        <Route index element={<Navigate to={SECTIONS[0].value} replace />} />
        {SECTIONS.map((s) => (
          <Route key={s.value} path={s.value} element={s.element} />
        ))}
        <Route path="*" element={<Navigate to={SECTIONS[0].value} replace />} />
      </Routes>
    </section>
  );
}

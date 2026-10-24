import { Segmented, Typography } from 'antd';
import { useNavigate, useParams } from 'react-router-dom';
import { CalendarPage } from './CalendarPage';
import { DashboardPage } from './DashboardPage';
import { NormsPage } from './NormsPage';

const TABS = [
  { value: 'norms', label: 'Нормы' },
  { value: 'calendar', label: 'Производственный календарь' },
];

/** Раздел «Часы и нормы»: дашборд — основная страница, нормы и календарь — вкладки. */
export function HoursLayout() {
  const { tab } = useParams();
  const navigate = useNavigate();
  if (!tab) return <DashboardPage />;
  return (
    <section className="page">
      <div className="page-head">
        <Typography.Title level={2} className="page-title">
          Часы и нормы
        </Typography.Title>
      </div>
      <Segmented
        style={{ margin: '16px 0' }}
        value={tab}
        onChange={(v) => navigate(`/dashboard/${String(v)}`)}
        options={[{ value: '', label: 'Дашборд' }, ...TABS]}
      />
      {tab === 'norms' ? <NormsPage /> : <CalendarPage />}
    </section>
  );
}

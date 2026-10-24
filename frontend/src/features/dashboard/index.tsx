import { DashboardOutlined } from '@ant-design/icons';
import type { AppModule } from '../../app/moduleTypes';
import { HoursRoutes } from './HoursRoutes';

export const dashboardModule: AppModule = {
  key: 'dashboard',
  path: 'dashboard',
  titleKey: 'modules.dashboard',
  icon: <DashboardOutlined />,
  element: <HoursRoutes />,
};

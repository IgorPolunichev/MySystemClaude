import { DashboardOutlined } from '@ant-design/icons';
import type { AppModule } from '../../app/moduleTypes';
import { PlaceholderPage } from '../../shared/ui/PlaceholderPage';

export const dashboardModule: AppModule = {
  key: 'dashboard',
  path: 'dashboard',
  titleKey: 'modules.dashboard',
  icon: <DashboardOutlined />,
  element: <PlaceholderPage titleKey="modules.dashboard" textKey="placeholder.dashboard" stage={7} />,
};

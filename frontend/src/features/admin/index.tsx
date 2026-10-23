import { SettingOutlined } from '@ant-design/icons';
import type { AppModule } from '../../app/moduleTypes';
import { PlaceholderPage } from '../../shared/ui/PlaceholderPage';

export const adminModule: AppModule = {
  key: 'admin',
  path: 'admin',
  titleKey: 'modules.admin',
  icon: <SettingOutlined />,
  roles: ['ADMIN'],
  element: <PlaceholderPage titleKey="modules.admin" textKey="placeholder.admin" stage={9} />,
};

import { ApartmentOutlined } from '@ant-design/icons';
import type { AppModule } from '../../app/moduleTypes';
import { PlaceholderPage } from '../../shared/ui/PlaceholderPage';

export const directoryModule: AppModule = {
  key: 'directory',
  path: 'directory',
  titleKey: 'modules.directory',
  icon: <ApartmentOutlined />,
  element: <PlaceholderPage titleKey="modules.directory" textKey="placeholder.directory" stage={2} />,
};

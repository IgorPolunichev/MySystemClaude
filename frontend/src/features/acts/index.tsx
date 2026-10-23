import { FileTextOutlined } from '@ant-design/icons';
import type { AppModule } from '../../app/moduleTypes';
import { PlaceholderPage } from '../../shared/ui/PlaceholderPage';

export const actsModule: AppModule = {
  key: 'acts',
  path: 'acts',
  titleKey: 'modules.acts',
  icon: <FileTextOutlined />,
  element: <PlaceholderPage titleKey="modules.acts" textKey="placeholder.acts" stage={3} />,
};

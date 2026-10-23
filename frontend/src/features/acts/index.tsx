import { FileTextOutlined } from '@ant-design/icons';
import type { AppModule } from '../../app/moduleTypes';
import { ActsRoutes } from './ActsRoutes';

export const actsModule: AppModule = {
  key: 'acts',
  path: 'acts',
  titleKey: 'modules.acts',
  icon: <FileTextOutlined />,
  element: <ActsRoutes />,
};

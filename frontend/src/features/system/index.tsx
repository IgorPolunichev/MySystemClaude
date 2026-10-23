import { HeartOutlined } from '@ant-design/icons';
import type { AppModule } from '../../app/moduleTypes';
import { SystemStatusPage } from './SystemStatusPage';

export const systemModule: AppModule = {
  key: 'system',
  path: 'system',
  titleKey: 'modules.system',
  icon: <HeartOutlined />,
  element: <SystemStatusPage />,
};

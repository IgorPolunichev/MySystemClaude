import { ToolOutlined } from '@ant-design/icons';
import type { AppModule } from '../../app/moduleTypes';
import { PartsPage } from './PartsPage';

export const partsModule: AppModule = {
  key: 'parts',
  path: 'parts',
  titleKey: 'modules.parts',
  icon: <ToolOutlined />,
  element: <PartsPage />,
};

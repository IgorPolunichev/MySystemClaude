import { ToolOutlined } from '@ant-design/icons';
import type { AppModule } from '../../app/moduleTypes';
import { PlaceholderPage } from '../../shared/ui/PlaceholderPage';

export const partsModule: AppModule = {
  key: 'parts',
  path: 'parts',
  titleKey: 'modules.parts',
  icon: <ToolOutlined />,
  element: <PlaceholderPage titleKey="modules.parts" textKey="placeholder.parts" stage={2} />,
};

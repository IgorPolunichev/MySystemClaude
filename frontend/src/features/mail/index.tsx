import { MailOutlined } from '@ant-design/icons';
import type { AppModule } from '../../app/moduleTypes';
import { PlaceholderPage } from '../../shared/ui/PlaceholderPage';

export const mailModule: AppModule = {
  key: 'mail',
  path: 'mail',
  titleKey: 'modules.mail',
  icon: <MailOutlined />,
  element: <PlaceholderPage titleKey="modules.mail" textKey="placeholder.mail" stage={8} />,
};

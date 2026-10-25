import { MailOutlined } from '@ant-design/icons';
import type { AppModule } from '../../app/moduleTypes';
import { MailRoutes } from './MailLayout';

export const mailModule: AppModule = {
  key: 'mail',
  path: 'mail',
  titleKey: 'modules.mail',
  icon: <MailOutlined />,
  element: <MailRoutes />,
};

import { ApartmentOutlined } from '@ant-design/icons';
import type { AppModule } from '../../app/moduleTypes';
import { DirectoryLayout } from './DirectoryLayout';

export const directoryModule: AppModule = {
  key: 'directory',
  path: 'directory',
  titleKey: 'modules.directory',
  icon: <ApartmentOutlined />,
  element: <DirectoryLayout />,
};

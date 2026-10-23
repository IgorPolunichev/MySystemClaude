import type { Role } from './auth/roles';
import type { AppModule } from './moduleTypes';
import { actsModule } from '../features/acts';
import { directoryModule } from '../features/directory';
import { partsModule } from '../features/parts';
import { dashboardModule } from '../features/dashboard';
import { mailModule } from '../features/mail';
import { adminModule } from '../features/admin';
import { systemModule } from '../features/system';

/** Порядок здесь — порядок пунктов меню. Новый модуль добавляется одной строкой. */
export const modules: AppModule[] = [
  actsModule,
  directoryModule,
  partsModule,
  dashboardModule,
  mailModule,
  adminModule,
  systemModule,
];

export function visibleModules(all: AppModule[], userRoles: Role[]): AppModule[] {
  return all.filter((m) => !m.roles || m.roles.some((r) => userRoles.includes(r)));
}

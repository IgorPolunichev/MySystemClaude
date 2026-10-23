import type { ReactNode } from 'react';
import type { Role } from './auth/roles';

/**
 * Описание модуля интерфейса. Каждый раздел (features/*) экспортирует один AppModule,
 * а moduleRegistry собирает их в меню и маршруты. Новый модуль подключается одной строкой.
 */
export interface AppModule {
  key: string;
  /** Путь раздела без ведущего слэша, например «acts». */
  path: string;
  /** Ключ названия в файле локализации. */
  titleKey: string;
  icon: ReactNode;
  /** Если задано — раздел виден только этим ролям (сервер всё равно проверяет права). */
  roles?: Role[];
  element: ReactNode;
}

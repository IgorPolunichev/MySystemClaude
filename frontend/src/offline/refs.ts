import type { Engineer, Equipment, Executor, Organization, Site } from '../api/generated/directory/model';
import type { Part, Unit } from '../api/generated/parts/model';
import type { LocalRefs } from './apply';
import { getRef } from './db';
import { currentUsername } from './user';

let cache: { user: string | undefined; refs: Promise<LocalRefs> } | null = null;

/** Сбросить кэш справочников в памяти — после загрузки новых справочников при синхронизации. */
export function invalidateRefs(): void {
  cache = null;
}

/**
 * Справочники с ПК — для применения изменений и ответов экранам без связи. Читаются с диска один раз и держатся в
 * памяти (раньше каждое изменение акта перечитывало всю технику и каталог — это давало задержки).
 */
export function loadRefs(): Promise<LocalRefs> {
  const user = currentUsername();
  if (!cache || cache.user !== user) {
    const refs = readRefs();
    cache = { user, refs };
    refs.catch(() => {
      cache = null;
    });
  }
  return cache.refs;
}

async function readRefs(): Promise<LocalRefs> {
  const [equipment, engineers, executors, units, sites, me] = await Promise.all([
    getRef<Equipment[]>('equipment'),
    getRef<Engineer[]>('engineers'),
    getRef<Executor[]>('executors'),
    getRef<Unit[]>('units'),
    getRef<Record<string, Site[]>>('sites'),
    getRef<Engineer>(`me:${currentUsername() ?? ''}`),
  ]);
  return {
    equipment: equipment ?? [],
    engineers: engineers ?? [],
    executors: executors ?? [],
    units: units ?? [],
    sites: sites ?? {},
    me,
  };
}

let organizationsCache: Promise<Organization[]> | null = null;
let partsCache: Promise<Part[]> | null = null;

export function organizations(): Promise<Organization[]> {
  organizationsCache ??= getRef<Organization[]>('organizations').then((v) => v ?? []);
  return organizationsCache;
}

export function parts(): Promise<Part[]> {
  partsCache ??= getRef<Part[]>('parts').then((v) => v ?? []);
  return partsCache;
}

/** Сбросить и остальные кэши — после синхронизации справочников. */
export function invalidateAllRefs(): void {
  invalidateRefs();
  organizationsCache = null;
  partsCache = null;
}

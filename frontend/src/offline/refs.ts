import type { Engineer, Equipment, Executor, Organization, Site } from '../api/generated/directory/model';
import type { Part, Unit } from '../api/generated/parts/model';
import type { LocalRefs } from './apply';
import { getRef } from './db';

/** Справочники с устройства — для применения изменений и ответов экранам без связи. */
export async function loadRefs(): Promise<LocalRefs> {
  const [equipment, engineers, executors, units, sites, me] = await Promise.all([
    getRef<Equipment[]>('equipment'),
    getRef<Engineer[]>('engineers'),
    getRef<Executor[]>('executors'),
    getRef<Unit[]>('units'),
    getRef<Record<string, Site[]>>('sites'),
    getRef<Engineer>('me'),
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

export async function organizations(): Promise<Organization[]> {
  return (await getRef<Organization[]>('organizations')) ?? [];
}

export async function parts(): Promise<Part[]> {
  return (await getRef<Part[]>('parts')) ?? [];
}

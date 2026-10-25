import type {
  ActDetail,
  ActHeaderInput,
  ActSummary,
  PartLineInput,
  TimeEntryInput,
} from '../api/generated/acts/model';
import {
  applyCaption,
  applyEngineer,
  applyHeader,
  applyPart,
  applyTime,
  createLocalAct,
  removePhoto,
} from './apply';
import { db, getMeta, setMeta, type OutboxOp, type StoredAct } from './db';
import { loadRefs, organizations, parts } from './refs';
import { uuidv7 } from './uuid';
import { currentUsername } from './user';

/** Ответ «локального сервера» в той же форме, что у сгенерированного клиента. */
export interface LocalResponse {
  data: unknown;
  status: number;
}

/** Действие недоступно без связи (раздел 17.1 SPEC.md). */
export class OfflineUnavailable extends Error {
  constructor() {
    super('Это действие доступно только при подключении к сети');
  }
}

export class LocalNotFound extends Error {}

const page = <T>(items: T[], params: URLSearchParams) => {
  const p = Number(params.get('page') ?? 0);
  const size = Number(params.get('size') ?? 20);
  return { items: items.slice(p * size, p * size + size), page: p, size, totalElements: items.length };
};

const contains = (q: string | null, ...values: (string | null | undefined)[]) =>
  !q || values.some((v) => v?.toLowerCase().includes(q.toLowerCase()));

/** Временный номер черновика без связи: Ч-{код устройства}-{n} (раздел 17.4). */
async function tempNumber(): Promise<string> {
  let device = await getMeta<string>('deviceCode');
  if (!device) {
    device = uuidv7().slice(-4).toUpperCase();
    await setMeta('deviceCode', device);
  }
  const n = ((await getMeta<number>('tempCounter')) ?? 0) + 1;
  await setMeta('tempCounter', n);
  return `Ч-${device}-${n}`;
}

function summary(s: StoredAct): ActSummary {
  const a = s.detail;
  const e = a.equipment;
  return {
    id: a.id,
    number: a.number ?? s.tempNumber ?? null,
    status: a.status,
    organizationName: a.organization?.shortName ?? a.organization?.name ?? null,
    departmentName: a.organization?.departmentName ?? null,
    siteName: a.site?.name ?? null,
    equipmentTitle: e ? `${e.brand} ${e.model}` : null,
    equipmentNumbers: e
      ? [e.garageNumber && `гар. № ${e.garageNumber}`, e.plateNumber].filter(Boolean).join(' · ')
      : null,
    shortDescription: a.shortDescription ?? null,
    periodStart: a.workStartDate ?? null,
    periodEnd: a.workEndDate ?? null,
    totalHours: a.totalHours,
    engineerNames: a.engineers.map((p) => p.fullName).join(', '),
    createdAt: a.createdAt ?? null,
    updatedAt: a.updatedAt ?? null,
    hasSignedScan: false,
    lastSentAt: a.lastSentAt ?? null,
    highlight: null,
  } as ActSummary;
}

/** Ответ на чтение из локальной базы. */
export async function localGet(path: string, params: URLSearchParams): Promise<LocalResponse> {
  let m: RegExpMatchArray | null;
  if (path === '/api/v1/acts') {
    const q = params.get('q');
    const statuses = params.getAll('status');
    const meId = (await loadRefs()).me?.id;
    const mine = params.get('mine') === 'true';
    const list = (await db.acts.toArray())
      .filter(
        (s) =>
          !mine ||
          s.localOnly ||
          s.detail.engineers.some((e) => (meId ? e.engineerId === meId : e.isCurrentUser)),
      )
      .filter((s) => !statuses.length || statuses.includes(s.detail.status))
      .filter((s) =>
        contains(
          q,
          s.detail.number,
          s.tempNumber,
          s.detail.shortDescription,
          s.detail.detailedDescription,
          s.detail.organization?.name,
          s.detail.equipment?.model,
          s.detail.equipment?.garageNumber,
        ),
      )
      .sort((a, b) => (b.detail.createdAt ?? '').localeCompare(a.detail.createdAt ?? ''))
      .map(summary);
    return { data: page(list, params), status: 200 };
  }
  if (path === '/api/v1/acts/short-descriptions') {
    const q = params.get('q');
    const values = [
      ...new Set(
        (await db.acts.toArray()).map((s) => s.detail.shortDescription).filter((v): v is string => !!v),
      ),
    ];
    return { data: values.filter((v) => contains(q, v)).slice(0, 20), status: 200 };
  }
  if ((m = path.match(/^\/api\/v1\/acts\/([^/]+)$/))) {
    const s = await db.acts.get(m[1]);
    if (!s) throw new LocalNotFound();
    return { data: { ...s.detail, number: s.detail.number ?? s.tempNumber ?? null }, status: 200 };
  }
  if ((m = path.match(/^\/api\/v1\/acts\/[^/]+\/history$/))) return { data: [], status: 200 };
  const refs = await loadRefs();
  if (path === '/api/v1/equipment') {
    const q = params.get('q');
    const list = refs.equipment.filter((e) =>
      contains(q, e.brand, e.model, e.garageNumber, e.plateNumber, e.serialNumber, e.organizationName),
    );
    return { data: page(list, params), status: 200 };
  }
  if ((m = path.match(/^\/api\/v1\/equipment\/([^/]+)$/))) {
    const e = refs.equipment.find((x) => x.id === m![1]);
    if (!e) throw new LocalNotFound();
    return { data: e, status: 200 };
  }
  if (path === '/api/v1/engineers/me') {
    if (!refs.me) throw new LocalNotFound();
    return { data: refs.me, status: 200 };
  }
  if (path === '/api/v1/engineers') {
    const q = params.get('q');
    return {
      data: page(
        refs.engineers.filter((e) => e.active && contains(q, e.fullName)),
        params,
      ),
      status: 200,
    };
  }
  if (path === '/api/v1/executors') return { data: refs.executors, status: 200 };
  if ((m = path.match(/^\/api\/v1\/organizations\/([^/]+)\/sites$/)))
    return { data: refs.sites[m[1]] ?? [], status: 200 };
  if (path === '/api/v1/organizations') {
    const q = params.get('q');
    return {
      data: page(
        (await organizations()).filter((o) => contains(q, o.name, o.shortName, o.inn)),
        params,
      ),
      status: 200,
    };
  }
  if (path === '/api/v1/parts') {
    const q = params.get('q');
    return {
      data: page(
        (await parts()).filter((p) => contains(q, p.name, p.catalogNumber, p.manufacturer)),
        params,
      ),
      status: 200,
    };
  }
  if (path === '/api/v1/units') return { data: refs.units, status: 200 };
  throw new OfflineUnavailable();
}

/** Пути изменений акта, которые можно выполнить без связи. */
export function isQueueable(method: string, path: string): boolean {
  if (method === 'GET') return false;
  if (method === 'POST' && path === '/api/v1/acts') return true;
  return (
    /^\/api\/v1\/acts\/[^/]+(\/(engineers(\/[^/]+)?|time\/[^/]+\/[^/]+|parts(\/[^/]+)?|photos\/[^/]+|open))?$/.test(
      path,
    ) && !(method === 'DELETE' && /^\/api\/v1\/acts\/[^/]+$/.test(path))
  );
}

export function actIdOf(path: string, body: unknown): string | undefined {
  if (path === '/api/v1/acts') return (body as { id?: string } | undefined)?.id ?? undefined;
  return path.match(/^\/api\/v1\/acts\/([^/]+)/)?.[1];
}

const LABELS: [RegExp, string][] = [
  [/^\/api\/v1\/acts$/, 'Создание акта'],
  [/\/open$/, 'Открыть после синхронизации'],
  [/\/time\//, 'Время'],
  [/\/engineers/, 'Инженеры'],
  [/\/parts/, 'Запчасти'],
  [/\/photos\//, 'Фото'],
  [/^\/api\/v1\/acts\/[^/]+$/, 'Шапка и описания'],
];

/** Изменение без связи: применить к акту на устройстве и поставить операцию в очередь. */
export async function localMutate(
  method: 'POST' | 'PUT' | 'DELETE',
  path: string,
  body: unknown,
): Promise<LocalResponse> {
  const refs = await loadRefs();
  let m: RegExpMatchArray | null;
  if (method === 'POST' && path === '/api/v1/acts') {
    const input = (body ?? {}) as { id?: string | null; equipmentId?: string | null };
    const id = input.id ?? uuidv7();
    const detail = createLocalAct(id, input.equipmentId, refs);
    await db.acts.put({ id, detail, localOnly: true, tempNumber: await tempNumber(), savedAt: Date.now() });
    await enqueue(id, 'POST', path, { ...input, id });
    return { data: { ...detail, number: (await db.acts.get(id))?.tempNumber ?? null }, status: 201 };
  }
  const actId = actIdOf(path, body);
  const stored = actId ? await db.acts.get(actId) : undefined;
  if (!actId || !stored) throw new OfflineUnavailable();
  if (stored.detail.status === 'CLOSED' || stored.detail.status === 'CANCELLED') {
    throw new Error('Акт закрыт или аннулирован — изменить его без связи нельзя');
  }
  let detail: ActDetail = stored.detail;
  let openAfterSync = stored.openAfterSync;
  if (/^\/api\/v1\/acts\/[^/]+$/.test(path) && method === 'PUT') {
    detail = applyHeader(detail, body as ActHeaderInput, refs);
  } else if ((m = path.match(/\/time\/([^/]+)\/([^/]+)$/))) {
    detail = applyTime(detail, m[1], m[2], method === 'DELETE' ? null : (body as TimeEntryInput));
  } else if (method === 'POST' && /\/engineers$/.test(path)) {
    detail = applyEngineer(detail, (body as { engineerId: string }).engineerId, true, refs);
  } else if (method === 'DELETE' && (m = path.match(/\/engineers\/([^/]+)$/))) {
    detail = applyEngineer(detail, m[1], false, refs);
  } else if (method === 'POST' && /\/parts$/.test(path)) {
    const input = body as PartLineInput & { id?: string | null };
    const id = input.id ?? uuidv7();
    body = { ...input, id };
    detail = applyPart(detail, id, input, refs);
  } else if ((m = path.match(/\/parts\/([^/]+)$/))) {
    detail = applyPart(detail, m[1], method === 'DELETE' ? null : (body as PartLineInput), refs);
  } else if ((m = path.match(/\/photos\/([^/]+)$/)) && m[1] !== 'order') {
    detail =
      method === 'DELETE'
        ? removePhoto(detail, m[1])
        : applyCaption(detail, m[1], (body as { caption?: string | null }).caption ?? null);
  } else if (method === 'POST' && /\/open$/.test(path)) {
    if (detail.status !== 'DRAFT') throw new OfflineUnavailable();
    openAfterSync = true;
  } else {
    throw new OfflineUnavailable();
  }
  await db.acts.put({ ...stored, detail, openAfterSync, savedAt: Date.now() });
  await enqueue(actId, method, path, body);
  const data = { ...detail, number: detail.number ?? stored.tempNumber ?? null };
  return { data: /\/time\//.test(path) ? { act: data, warning: null } : data, status: 200 };
}

async function enqueue(actId: string, method: OutboxOp['method'], url: string, body: unknown) {
  await db.outbox.add({
    opId: uuidv7(),
    actId,
    method,
    url,
    body,
    label: LABELS.find(([re]) => re.test(url))?.[1] ?? 'Изменение',
    createdAt: Date.now(),
    status: 'pending',
    attempts: 0,
    user: currentUsername(),
  });
}

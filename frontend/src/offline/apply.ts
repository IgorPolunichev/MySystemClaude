import type {
  ActDetail,
  ActHeaderInput,
  ActPartLine,
  ActTimeEntry,
  PartLineInput,
  TimeEntryInput,
} from '../api/generated/acts/model';
import type { Engineer, Equipment, Executor, Site } from '../api/generated/directory/model';
import type { Unit } from '../api/generated/parts/model';
import { calculateWorkTime } from '../features/acts/workTime';
import { uuidv7 } from './uuid';

/** Справочники на устройстве, нужные для применения изменений. */
export interface LocalRefs {
  equipment: Equipment[];
  engineers: Engineer[];
  executors: Executor[];
  units: Unit[];
  sites: Record<string, Site[]>;
  me?: Engineer;
}

/**
 * Изменения акта без связи (раздел 17.1 SPEC.md) — те же правила, что на сервере, в упрощённом виде: сервер при
 * синхронизации всё равно проверит каждую операцию и вернёт свою, точную карточку акта.
 */
const round2 = (v: number) => Math.round(v * 100) / 100;

function equipmentSnapshot(e: Equipment) {
  return {
    id: e.id,
    typeName: e.equipmentTypeName,
    brand: e.brand,
    model: e.model,
    serialNumber: e.serialNumber ?? null,
    garageNumber: e.garageNumber ?? null,
    plateNumber: e.plateNumber ?? null,
    meterType: e.meterType,
  } as ActDetail['equipment'];
}

function organizationSnapshot(e: Equipment) {
  return {
    id: e.organizationId,
    name: e.organizationName,
    shortName: e.organizationName,
    departmentId: e.departmentId ?? null,
    departmentName: e.departmentName ?? null,
  } as ActDetail['organization'];
}

/** Черновик, созданный без связи. */
export function createLocalAct(
  id: string,
  equipmentId: string | null | undefined,
  refs: LocalRefs,
): ActDetail {
  const now = new Date().toISOString();
  const eq = equipmentId ? refs.equipment.find((e) => e.id === equipmentId) : undefined;
  const executor = refs.executors.find((x) => x.isDefault);
  const me = refs.me;
  const act = {
    id,
    version: 0,
    number: null,
    status: 'DRAFT',
    organization: eq ? organizationSnapshot(eq) : null,
    equipment: eq ? equipmentSnapshot(eq) : null,
    site: eq?.siteId ? { id: eq.siteId, name: eq.siteName ?? 'Площадка', code: null, address: null } : null,
    executor: executor
      ? {
          id: executor.id,
          name: executor.name,
          shortName: executor.shortName ?? null,
          inn: null,
          kpp: null,
          address: null,
          phone: null,
        }
      : null,
    meterHours: null,
    meterKm: null,
    location: null,
    workStartDate: null,
    workEndDate: null,
    periodOverridden: false,
    calendarDays: 0,
    workedDays: 0,
    shortDescription: null,
    detailedDescription: null,
    recommendations: null,
    customerRepName: null,
    customerRepPosition: null,
    hideRequisites: true,
    hideTravelTime: true,
    authorEngineerId: me?.id ?? null,
    createdAt: now,
    updatedAt: now,
    lastSentAt: null,
    closedAt: null,
    closedBy: null,
    engineers: me
      ? [
          {
            engineerId: me.id,
            fullName: me.fullName,
            position: me.position ?? null,
            role: 'LEAD',
            totalHours: 0,
            isCurrentUser: true,
          },
        ]
      : [],
    timeEntries: [],
    parts: [],
    photos: [],
    attachments: [],
    totalHours: 0,
    totalTravelHours: 0,
    missingForClose: [],
    permissions: {
      canEdit: true,
      // Автор черновика вносит время за всех инженеров акта — как на сервере.
      canEditAnyTime: true,
      canOpen: !!eq,
      canClose: false,
      canReopen: false,
      canCancel: false,
      canDelete: true,
      requiresAdminComment: false,
      canAttach: true,
    },
  };
  return act as unknown as ActDetail;
}

export function applyHeader(act: ActDetail, h: ActHeaderInput, refs: LocalRefs): ActDetail {
  const next: ActDetail = { ...act };
  if ((h.equipmentId ?? null) !== (act.equipment?.id ?? null)) {
    const eq = h.equipmentId ? refs.equipment.find((e) => e.id === h.equipmentId) : undefined;
    next.equipment = eq ? equipmentSnapshot(eq) : null;
    next.organization = eq ? organizationSnapshot(eq) : null;
    next.site = eq?.siteId
      ? { id: eq.siteId, name: eq.siteName ?? 'Площадка', code: null, address: null }
      : null;
    if (act.status === 'DRAFT') next.permissions = { ...act.permissions, canOpen: !!eq };
  }
  if (act.status !== 'DRAFT') {
    next.site = act.site;
  } else if (h.siteId && h.siteId !== next.site?.id && next.organization) {
    const site = (refs.sites[next.organization.id] ?? []).find((s) => s.id === h.siteId);
    next.site = {
      id: h.siteId,
      name: site?.name ?? 'Площадка',
      code: site?.code ?? null,
      address: site?.address ?? null,
    };
  } else if (h.siteId === null && (h.equipmentId ?? null) === (act.equipment?.id ?? null)) {
    next.site = null;
  }
  if (h.executorId && h.executorId !== act.executor?.id) {
    const x = refs.executors.find((e) => e.id === h.executorId);
    if (x)
      next.executor = {
        id: x.id,
        name: x.name,
        shortName: x.shortName ?? null,
        inn: x.inn ?? null,
        kpp: x.kpp ?? null,
        address: x.address ?? null,
        phone: x.phone ?? null,
      };
  }
  next.meterHours = h.meterHours ?? null;
  next.meterKm = h.meterKm ?? null;
  next.location = h.location ?? null;
  next.shortDescription = h.shortDescription ?? null;
  next.detailedDescription = h.detailedDescription ?? null;
  next.recommendations = h.recommendations ?? null;
  next.customerRepName = h.customerRepName ?? null;
  next.customerRepPosition = h.customerRepPosition ?? null;
  next.hideRequisites = h.hideRequisites ?? true;
  next.hideTravelTime = h.hideTravelTime ?? true;
  next.periodOverridden = !!(h.workStartDate || h.workEndDate);
  next.workStartDate = h.workStartDate ?? null;
  next.workEndDate = h.workEndDate ?? null;
  next.updatedAt = new Date().toISOString();
  return recompute(next);
}

/** Запись времени: null — удалить. Минуты считаются тем же правилом, что на сервере. */
export function applyTime(
  act: ActDetail,
  engineerId: string,
  date: string,
  input: TimeEntryInput | null,
): ActDetail {
  const others = act.timeEntries.filter((e) => !(e.engineerId === engineerId && e.workDate === date));
  if (!input) return recompute({ ...act, timeEntries: others });
  const existing = act.timeEntries.find((e) => e.engineerId === engineerId && e.workDate === date);
  let minutes = 0;
  if (input.startTime && input.endTime) {
    const result = calculateWorkTime(
      { start: input.startTime, end: input.endTime },
      (input.breaks ?? []).map((b) => ({ start: b.startTime, end: b.endTime })),
    );
    if (result.error) throw new Error(result.error);
    minutes = result.minutes;
  }
  const entry: ActTimeEntry = {
    id: existing?.id ?? uuidv7(),
    engineerId,
    workDate: date,
    startTime: input.startTime ?? null,
    endTime: input.endTime ?? null,
    breaks: input.breaks ?? [],
    workMinutes: minutes,
    hours: round2(minutes / 60),
    travelHours: input.travelHours ?? null,
    comment: input.comment ?? null,
  };
  return recompute({
    ...act,
    timeEntries: [...others, entry].sort((a, b) => a.workDate.localeCompare(b.workDate)),
  });
}

export function applyEngineer(act: ActDetail, engineerId: string, add: boolean, refs: LocalRefs): ActDetail {
  if (!add) {
    return recompute({
      ...act,
      engineers: act.engineers.filter((e) => e.engineerId !== engineerId),
      timeEntries: act.timeEntries.filter((e) => e.engineerId !== engineerId),
    });
  }
  if (act.engineers.some((e) => e.engineerId === engineerId)) return act;
  const e = refs.engineers.find((x) => x.id === engineerId);
  return recompute({
    ...act,
    engineers: [
      ...act.engineers,
      {
        engineerId,
        fullName: e?.fullName ?? 'Инженер',
        position: e?.position ?? null,
        role: 'MEMBER',
        totalHours: 0,
        isCurrentUser: !!refs.me && refs.me.id === engineerId,
      },
    ],
  });
}

export function applyPart(
  act: ActDetail,
  lineId: string,
  input: PartLineInput | null,
  refs: LocalRefs,
): ActDetail {
  if (!input) {
    const parts = act.parts.filter((p) => p.id !== lineId).map((p, i) => ({ ...p, position: i + 1 }));
    return { ...act, parts };
  }
  const unit = refs.units.find((u) => u.code === input.unitCode);
  const existing = act.parts.find((p) => p.id === lineId);
  const line: ActPartLine = {
    id: lineId,
    position: existing?.position ?? act.parts.length + 1,
    source: input.source,
    catalogItemId: input.catalogItemId ?? null,
    name: input.name,
    catalogNumber: input.catalogNumber ?? null,
    quantity: input.quantity,
    unitCode: input.unitCode,
    unitName: unit?.name ?? input.unitCode,
    notes: input.notes ?? null,
  };
  const parts = existing ? act.parts.map((p) => (p.id === lineId ? line : p)) : [...act.parts, line];
  return { ...act, parts };
}

export function applyCaption(act: ActDetail, photoId: string, caption: string | null): ActDetail {
  return { ...act, photos: act.photos.map((p) => (p.id === photoId ? { ...p, caption } : p)) };
}

export function removePhoto(act: ActDetail, photoId: string): ActDetail {
  return {
    ...act,
    photos: act.photos.filter((p) => p.id !== photoId).map((p, i) => ({ ...p, position: i + 1 })),
  };
}

/** Итоги акта: часы, время в пути, период работ и дни — как считает сервер. */
export function recompute(act: ActDetail): ActDetail {
  const totalMinutes = act.timeEntries.reduce((s, e) => s + e.workMinutes, 0);
  const travel = act.timeEntries.reduce((s, e) => s + (e.travelHours ?? 0), 0);
  const engineers = act.engineers.map((p) => ({
    ...p,
    totalHours: round2(
      act.timeEntries.filter((e) => e.engineerId === p.engineerId).reduce((s, e) => s + e.workMinutes, 0) /
        60,
    ),
  }));
  const dates = [...new Set(act.timeEntries.map((e) => e.workDate))].sort();
  let start = act.periodOverridden ? act.workStartDate : (dates[0] ?? null);
  let end = act.periodOverridden ? act.workEndDate : (dates[dates.length - 1] ?? null);
  if (act.periodOverridden) {
    start = act.workStartDate ?? dates[0] ?? null;
    end = act.workEndDate ?? dates[dates.length - 1] ?? null;
  }
  const calendarDays =
    start && end
      ? Math.round((Date.parse(`${end}T00:00:00Z`) - Date.parse(`${start}T00:00:00Z`)) / 86_400_000) + 1
      : 0;
  return {
    ...act,
    engineers,
    totalHours: round2(totalMinutes / 60),
    totalTravelHours: round2(travel),
    workStartDate: start ?? null,
    workEndDate: end ?? null,
    calendarDays,
    workedDays: dates.length,
  };
}

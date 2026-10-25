import { describe, expect, it } from 'vitest';
import type { Engineer, Equipment, Executor } from '../api/generated/directory/model';
import { applyEngineer, applyHeader, applyPart, applyTime, createLocalAct, type LocalRefs } from './apply';
import { uuidv7 } from './uuid';

const me = { id: 'e1', fullName: 'Петров И. С.', position: 'Инженер', active: true } as Engineer;
const refs: LocalRefs = {
  equipment: [
    {
      id: 'q1',
      organizationId: 'o1',
      organizationName: 'АО «Тест»',
      equipmentTypeName: 'Самосвал',
      brand: 'CAT',
      model: '777G',
      garageNumber: '45',
      meterType: 'HOURS',
      siteId: 's1',
      siteName: 'Площадка А',
    } as Equipment,
    {
      id: 'q2',
      organizationId: 'o2',
      organizationName: 'ООО «Другой»',
      equipmentTypeName: 'Погрузчик',
      brand: 'Volvo',
      model: 'L350',
      meterType: 'HOURS',
    } as Equipment,
  ],
  engineers: [me, { id: 'e2', fullName: 'Кузнецов С. А.', active: true } as Engineer],
  executors: [{ id: 'x1', name: 'ООО «Сервис»', isDefault: true, active: true } as Executor],
  units: [{ code: 'L', name: 'л', allowsFraction: true }],
  sites: {},
  me,
};

describe('изменения акта без связи', () => {
  it('черновик: техника, заказчик, площадка, исполнитель, автор-ответственный', () => {
    const act = createLocalAct('a1', 'q1', refs);
    expect(act.status).toBe('DRAFT');
    expect(act.number).toBeNull();
    expect(act.organization?.name).toBe('АО «Тест»');
    expect(act.site?.name).toBe('Площадка А');
    expect(act.executor?.name).toBe('ООО «Сервис»');
    expect(act.engineers).toEqual([
      expect.objectContaining({ engineerId: 'e1', role: 'LEAD', isCurrentUser: true }),
    ]);
  });

  it('время: интервал минус перерывы (пример из ТЗ — 2 ч), итоги и период', () => {
    let act = createLocalAct('a1', 'q1', refs);
    act = applyTime(act, 'e1', '2026-10-05', {
      startTime: '10:00',
      endTime: '13:00',
      breaks: [{ startTime: '11:00', endTime: '12:00' }],
      travelHours: 1.5,
    });
    act = applyEngineer(act, 'e2', true, refs);
    act = applyTime(act, 'e2', '2026-10-07', {
      startTime: '08:00',
      endTime: '17:00',
      breaks: [{ startTime: '12:00', endTime: '13:00' }],
    });
    expect(act.timeEntries[0].workMinutes).toBe(120);
    expect(act.totalHours).toBe(10);
    expect(act.totalTravelHours).toBe(1.5);
    expect(act.workStartDate).toBe('2026-10-05');
    expect(act.workEndDate).toBe('2026-10-07');
    expect(act.calendarDays).toBe(3);
    expect(act.workedDays).toBe(2);
    act = applyTime(act, 'e2', '2026-10-07', null);
    expect(act.totalHours).toBe(2);
    expect(() =>
      applyTime(act, 'e1', '2026-10-06', {
        startTime: '10:00',
        endTime: '13:00',
        breaks: [{ startTime: '13:30', endTime: '14:00' }],
      }),
    ).toThrow(/выходит/);
  });

  it('шапка: смена техники меняет заказчика и площадку; запчасти', () => {
    let act = createLocalAct('a1', 'q1', refs);
    act = applyHeader(
      act,
      {
        version: 0,
        equipmentId: 'q2',
        shortDescription: 'ТО-500',
        hideRequisites: true,
        hideTravelTime: true,
      },
      refs,
    );
    expect(act.organization?.name).toBe('ООО «Другой»');
    expect(act.site).toBeNull();
    expect(act.shortDescription).toBe('ТО-500');
    const line = uuidv7();
    act = applyPart(act, line, { source: 'MANUAL', name: 'Масло', quantity: 45.5, unitCode: 'L' }, refs);
    expect(act.parts[0]).toEqual(expect.objectContaining({ id: line, unitName: 'л', position: 1 }));
    act = applyPart(act, line, null, refs);
    expect(act.parts).toHaveLength(0);
  });

  it('uuid v7 — версия 7 и рост по времени', () => {
    const a = uuidv7();
    expect(a).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  });
});

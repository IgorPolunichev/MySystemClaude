import ExcelJS from 'exceljs';
import { describe, expect, it } from 'vitest';
import type { ActDetail } from '../../api/generated/acts/model';
import { buildActWorkbook, type PrintPhoto } from './actWorkbook';

// PNG 1×1 — содержимое картинки для теста не важно, важны размеры, которые передаются отдельно.
const PNG = Uint8Array.from(
  atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='),
  (c) => c.charCodeAt(0),
).buffer;

function act(hideRequisites: boolean, hideTravelTime: boolean, photos: number): ActDetail {
  return {
    id: 'a1',
    version: 3,
    number: 'АВР-СК-2026-000047',
    status: 'OPEN',
    executor: { id: 'x', name: 'ООО «Сервис»', inn: '7707083893', kpp: '773601001', address: 'г. Москва' },
    organization: {
      id: 'o',
      name: 'Акционерное общество «Северный горно-обогатительный комбинат имени Героев Труда»',
      inn: '7707083893',
      departmentName: 'Участок 1',
    },
    site: { id: 's', name: 'Площадка «Северный карьер»', address: 'Воркута' },
    equipment: {
      id: 'q',
      typeName: 'Самосвал',
      brand: 'CAT',
      model: '777G',
      garageNumber: '45',
      meterType: 'HOURS',
    },
    meterHours: 18310.5,
    location: 'Бокс № 2',
    workStartDate: '2026-10-05',
    workEndDate: '2026-10-07',
    calendarDays: 3,
    shortDescription: 'ТО-2000',
    detailedDescription: 'Выполнено ТО-2000. '.repeat(30),
    recommendations: 'Заменить ремни',
    customerRepName: 'Сидоров А. В.',
    customerRepPosition: 'Механик',
    hideRequisites,
    hideTravelTime,
    engineers: [
      {
        engineerId: 'e1',
        fullName: 'Петров И. С.',
        position: 'Сервисный инженер',
        role: 'LEAD',
        totalHours: 6,
        isCurrentUser: true,
      },
    ],
    timeEntries: [
      {
        id: 't1',
        engineerId: 'e1',
        workDate: '2026-10-05',
        startTime: '10:00',
        endTime: '13:00',
        breaks: [{ startTime: '11:00', endTime: '12:00' }],
        workMinutes: 120,
        hours: 2,
        travelHours: 1.5,
      },
      {
        id: 't2',
        engineerId: 'e1',
        workDate: '2026-10-07',
        startTime: '08:00',
        endTime: '12:00',
        breaks: [],
        workMinutes: 240,
        hours: 4,
      },
    ],
    parts: [
      {
        id: 'p',
        position: 1,
        source: 'CATALOG',
        name: 'Фильтр масляный',
        catalogNumber: '1R-0750',
        quantity: 2,
        unitCode: 'PCS',
        unitName: 'шт',
      },
    ],
    photos: Array.from({ length: photos }, (_, i) => ({
      id: `f${i}`,
      mediaId: `m${i}`,
      caption: `Узел ${i + 1}`,
      position: i + 1,
    })),
    attachments: [],
    totalHours: 6,
    totalTravelHours: 1.5,
    missingForClose: [],
    workedDays: 2,
    periodOverridden: false,
    hideTravel: undefined,
    permissions: {} as never,
  } as unknown as ActDetail;
}

const printPhotos = (n: number): PrintPhoto[] =>
  Array.from({ length: n }, (_, i) => ({
    number: i + 1,
    caption: `Узел ${i + 1}`,
    data: PNG,
    extension: 'png',
    width: 400,
    height: 300,
  }));

async function read(buffer: ArrayBuffer) {
  const wb = new ExcelJS.Workbook();
  await wb.xlsx.load(buffer);
  return wb;
}

function text(ws: ExcelJS.Worksheet): string {
  const out: string[] = [];
  ws.eachRow((row) => row.eachCell((c) => out.push(String(c.value ?? ''))));
  return out.join('\n');
}

describe('Excel акта на ПК без связи', () => {
  it('листы и фото по 6 на лист', async () => {
    for (const n of [0, 1, 7]) {
      const wb = await read(await buildActWorkbook(act(true, true, n), printPhotos(n)));
      const sheets = Math.max(1, Math.ceil(n / 6));
      expect(wb.worksheets.map((s) => s.name)).toEqual([
        'Акт',
        'Табель',
        ...Array.from({ length: sheets }, (_, i) => `Техотчёт ${i + 1}`),
      ]);
      const pictures = wb.worksheets.slice(2).map((s) => s.getImages().length);
      expect(pictures.reduce((a, b) => a + b, 0)).toBe(n);
      expect(text(wb.worksheets[wb.worksheets.length - 1])).toContain('Представитель исполнителя');
    }
  });

  it('реквизиты и время в пути — по галочкам; часы числами; длинное название в несколько строк', async () => {
    const hidden = await read(await buildActWorkbook(act(true, true, 0), []));
    const a = text(hidden.getWorksheet('Акт')!);
    expect(a).toContain('АКТ ВЫПОЛНЕННЫХ РАБОТ № АВР-СК-2026-000047');
    expect(a).toContain('Производственная площадка');
    expect(a).not.toContain('ИНН');
    expect(a).not.toContain('В пути');
    const shown = await read(await buildActWorkbook(act(false, false, 0), []));
    expect(text(shown.getWorksheet('Акт')!)).toContain('ИНН 7707083893');
    const t = shown.getWorksheet('Табель')!;
    expect(text(t)).toContain('11:00–12:00');
    expect(t.getRow(5).getCell(6).value).toBe(2);
    const customerRow = [...Array(40).keys()]
      .map((i) => hidden.getWorksheet('Акт')!.getRow(i + 1))
      .find((r) => r.getCell(1).value === 'Заказчик');
    expect(customerRow?.height ?? 0).toBeGreaterThan(16);
  });
});

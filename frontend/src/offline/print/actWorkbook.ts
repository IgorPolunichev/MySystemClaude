import type { Borders, Cell, Fill, Font, Worksheet } from 'exceljs';
import type { ActDetail } from '../../api/generated/acts/model';

/**
 * Excel акта на ПК без связи (решение 19.13 SPEC.md) — тот же макет, что формирует document-service:
 * листы «Акт», «Табель», «Техотчёт 1…N» (6 фото на лист), палитра, рамки, высота строк по самому длинному тексту,
 * подписи «Представитель заказчика» и «Представитель исполнителя». Фото — превью, сохранённые на ПК.
 */
export interface PrintPhoto {
  number: number;
  caption: string | null;
  data: ArrayBuffer;
  extension: 'jpeg' | 'png';
  width: number;
  height: number;
}

const ARIAL = 'Arial';
const C = {
  section: 'FF1F4E79',
  label: 'FFEAEFF5',
  head: 'FFD6E1EC',
  total: 'FFFDF2DC',
  border: 'FF8C9BAA',
  white: 'FFFFFFFF',
};
const thin = { style: 'thin' as const, color: { argb: C.border } };
const BORDER: Partial<Borders> = { top: thin, bottom: thin, left: thin, right: thin };
const fill = (argb: string): Fill => ({ type: 'pattern', pattern: 'solid', fgColor: { argb } });

interface Style {
  font?: Partial<Font>;
  fill?: Fill;
  border?: boolean;
  align?: 'left' | 'center' | 'right';
  valign?: 'top' | 'middle';
  wrap?: boolean;
  bottomLine?: boolean;
}

const S: Record<string, Style> = {
  title: { font: { name: ARIAL, size: 14, bold: true, color: { argb: C.section } }, align: 'center' },
  subtitle: { font: { name: ARIAL, size: 10 }, align: 'center' },
  section: {
    font: { name: ARIAL, size: 10, bold: true, color: { argb: C.white } },
    fill: fill(C.section),
    border: true,
  },
  label: { font: { name: ARIAL, size: 10 }, fill: fill(C.label), border: true, wrap: true },
  value: { font: { name: ARIAL, size: 10 }, border: true, wrap: true },
  valueBold: { font: { name: ARIAL, size: 10, bold: true }, border: true, wrap: true },
  text: { font: { name: ARIAL, size: 10 }, border: true, wrap: true, valign: 'top' },
  head: {
    font: { name: ARIAL, size: 10, bold: true },
    fill: fill(C.head),
    border: true,
    align: 'center',
    wrap: true,
  },
  cell: { font: { name: ARIAL, size: 10 }, border: true, wrap: true },
  center: { font: { name: ARIAL, size: 10 }, border: true, align: 'center' },
  number: { font: { name: ARIAL, size: 10 }, border: true, align: 'right' },
  total: { font: { name: ARIAL, size: 10, bold: true }, fill: fill(C.total), border: true, align: 'right' },
  signHead: { font: { name: ARIAL, size: 10, bold: true }, fill: fill(C.head), border: true },
  sign: { font: { name: ARIAL, size: 10 }, wrap: true },
  signLine: { font: { name: ARIAL, size: 10 }, bottomLine: true },
  small: { font: { name: ARIAL, size: 8, color: { argb: 'FF5A646E' } }, align: 'center' },
  note: { font: { name: ARIAL, size: 8, color: { argb: 'FF5A646E' } }, wrap: true },
  caption: {
    font: { name: ARIAL, size: 10 },
    fill: fill(C.label),
    border: true,
    align: 'center',
    wrap: true,
    valign: 'top',
  },
  photo: { font: { name: ARIAL, size: 10 }, border: true },
};

class SheetWriter {
  row = 0;
  constructor(
    readonly ws: Worksheet,
    readonly widths: number[],
  ) {
    ws.columns = widths.map((width) => ({ width }));
  }

  next(height: number): number {
    this.row += 1;
    this.ws.getRow(this.row).height = height;
    return this.row;
  }

  put(row: number, from: number, to: number, value: string | number | null | undefined, style: Style): Cell {
    for (let c = from; c <= to; c++) apply(this.ws.getCell(row, c + 1), style);
    if (to > from) this.ws.mergeCells(row, from + 1, row, to + 1);
    const cell = this.ws.getCell(row, from + 1);
    if (value !== null && value !== undefined && value !== '') cell.value = value;
    return cell;
  }

  /** Высота строки с запасом (Excel не подбирает высоту объединённых ячеек): 0,85 символа на единицу ширины. */
  heightFor(min: number, ...cells: [string | null | undefined, number, number][]): number {
    let height = min;
    for (const [text, from, to] of cells) {
      if (!text) continue;
      const chars = this.widths.slice(from, to + 1).reduce((a, b) => a + b, 0);
      const perLine = Math.max(6, Math.floor(chars * 0.85));
      const lines = text.split('\n').reduce((n, p) => n + Math.max(1, Math.ceil(p.length / perLine)), 0);
      height = Math.max(height, lines * 13.5 + 5);
    }
    return height;
  }

  setup(header: string) {
    this.ws.pageSetup = {
      paperSize: 9,
      orientation: 'portrait',
      fitToPage: true,
      fitToWidth: 1,
      fitToHeight: 0,
      horizontalCentered: true,
      margins: { left: 0.59, right: 0.59, top: 0.59, bottom: 0.59, header: 0.3, footer: 0.3 },
    };
    this.ws.headerFooter = { oddHeader: `&R${header}`, oddFooter: '&CСтраница &P из &N' };
  }
}

function apply(cell: Cell, s: Style) {
  if (s.font) cell.font = s.font;
  if (s.fill) cell.fill = s.fill;
  if (s.border) cell.border = BORDER;
  if (s.bottomLine) cell.border = { bottom: thin };
  cell.alignment = { horizontal: s.align ?? 'left', vertical: s.valign ?? 'middle', wrapText: !!s.wrap };
}

// ---------- Тексты ----------

const date = (iso?: string | null) =>
  iso ? `${iso.slice(8, 10)}.${iso.slice(5, 7)}.${iso.slice(0, 4)}` : '';
const num = (v: number) => new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 }).format(v);

function meter(a: ActDetail): string | null {
  const parts: string[] = [];
  const type = a.equipment?.meterType ?? 'HOURS';
  if (a.meterHours != null && type !== 'KM') parts.push(`${num(a.meterHours)} м/ч`);
  if (a.meterKm != null && type !== 'HOURS') parts.push(`${num(a.meterKm)} км`);
  return parts.length ? parts.join(', ') : null;
}

function period(a: ActDetail): string | null {
  if (!a.workStartDate && !a.workEndDate) return null;
  const range =
    a.workStartDate && a.workEndDate && a.workStartDate !== a.workEndDate
      ? `${date(a.workStartDate)} — ${date(a.workEndDate)}`
      : date(a.workStartDate ?? a.workEndDate);
  return a.calendarDays > 0 ? `${range} (${a.calendarDays} дн.)` : range;
}

function requisites(
  hidden: boolean,
  inn?: string | null,
  kpp?: string | null,
  address?: string | null,
  phone?: string | null,
) {
  if (hidden) return null;
  const parts = [inn && `ИНН ${inn}`, kpp && `КПП ${kpp}`, address, phone && `тел. ${phone}`].filter(Boolean);
  return parts.length ? parts.join(', ') : null;
}

const documentDate = (a: ActDetail) => (a.closedAt ?? new Date().toISOString()).slice(0, 10);
const actTitle = (a: ActDetail) =>
  a.number ? `АКТ ВЫПОЛНЕННЫХ РАБОТ № ${a.number}` : 'ЧЕРНОВИК АКТА ВЫПОЛНЕННЫХ РАБОТ';

function engineers(a: ActDetail) {
  return a.engineers.map((p) => {
    const own = a.timeEntries.filter((t) => t.engineerId === p.engineerId);
    return {
      name: p.fullName,
      position: p.position ?? null,
      lead: p.role === 'LEAD',
      days: new Set(own.map((t) => t.workDate)).size,
      hours: Math.round((own.reduce((s, t) => s + t.workMinutes, 0) / 60) * 100) / 100,
      travel: own.reduce((s, t) => s + (t.travelHours ?? 0), 0),
    };
  });
}

function signatures(w: SheetWriter, a: ActDetail, left: [number, number], right: [number, number]) {
  const people = engineers(a);
  const lead = people.find((p) => p.lead) ?? people[0];
  const customer = [a.customerRepPosition, a.customerRepName].filter(Boolean).join(', ') || null;
  w.next(10);
  let r = w.next(18);
  w.put(r, ...left, 'Представитель заказчика', S.signHead);
  w.put(r, ...right, 'Представитель исполнителя', S.signHead);
  r = w.next(w.heightFor(30, [customer, ...left], [lead?.name, ...right]));
  w.put(r, ...left, customer, S.sign);
  w.put(r, ...right, lead?.name ?? null, S.sign);
  r = w.next(26);
  w.put(r, ...left, null, S.signLine);
  w.put(r, ...right, null, S.signLine);
  r = w.next(12);
  w.put(r, ...left, 'подпись / расшифровка', S.small);
  w.put(r, ...right, 'подпись / расшифровка', S.small);
  r = w.next(20);
  w.put(r, ...left, 'Дата: «___» ____________ 20___ г.', S.sign);
  w.put(r, ...right, 'Дата: «___» ____________ 20___ г.', S.sign);
}

// ---------- Лист «Акт» ----------

function actSheet(ws: Worksheet, a: ActDetail) {
  const w = new SheetWriter(ws, [5, 34, 18, 12, 12, 13]);
  const L = 5;
  w.setup(a.number ? `Акт № ${a.number}` : 'Черновик');
  w.put(w.next(24), 0, L, actTitle(a), S.title);
  w.put(w.next(16), 0, L, `от ${date(documentDate(a))}`, S.subtitle);
  w.next(8);
  const section = (t: string) => {
    w.next(6);
    w.put(w.next(18), 0, L, t, S.section);
  };
  const kv = (label: string, value: string | null | undefined, bold = false) => {
    if (!value) return;
    const r = w.next(w.heightFor(16, [value, 2, L], [label, 0, 1]));
    w.put(r, 0, 1, label, S.label);
    w.put(r, 2, L, value, bold ? S.valueBold : S.value);
  };
  section('Стороны');
  const x = a.executor;
  kv('Исполнитель', x?.name ?? null, true);
  kv('Реквизиты исполнителя', x ? requisites(a.hideRequisites, x.inn, x.kpp, x.address, x.phone) : null);
  const o = a.organization;
  if (o) {
    kv('Заказчик', o.name, true);
    kv('Реквизиты заказчика', requisites(a.hideRequisites, o.inn, o.kpp, o.legalAddress, null));
    kv('Производственная площадка', a.site?.name);
    kv('Адрес площадки', a.site?.address);
    kv('Подразделение', o.departmentName);
  }
  section('Техника');
  const e = a.equipment;
  if (e) {
    kv('Тип', e.typeName);
    kv('Марка, модель', `${e.brand} ${e.model}`, true);
    kv('Серийный номер / VIN', e.serialNumber);
    kv('Гаражный номер', e.garageNumber);
    kv('Государственный номер', e.plateNumber);
    kv('Наработка', meter(a));
  } else {
    kv('Техника', 'не указана');
  }
  section('Период и место работ');
  kv('Период работ', period(a));
  kv('Место проведения работ', a.location);
  section('Выполненные работы');
  kv('Краткое описание', a.shortDescription, true);
  if (a.detailedDescription) {
    w.put(w.next(16), 0, L, 'Подробное описание выполненных работ', S.label);
    w.put(w.next(w.heightFor(30, [a.detailedDescription, 0, L])), 0, L, a.detailedDescription, S.text);
  }

  section('Исполнители и трудозатраты');
  const travel = !a.hideTravelTime;
  let r = w.next(28);
  ['№', 'ФИО', 'Должность', 'Дней', 'Часы', travel ? 'В пути, ч' : null].forEach((h, i) =>
    w.put(r, i, i, h, S.head),
  );
  let hours = 0;
  let travelHours = 0;
  engineers(a).forEach((p, i) => {
    r = w.next(w.heightFor(16, [p.name, 1, 1], [p.position, 2, 2]));
    w.put(r, 0, 0, String(i + 1), S.center);
    w.put(r, 1, 1, p.name, S.cell);
    w.put(r, 2, 2, p.position, S.cell);
    w.put(r, 3, 3, p.days, S.number);
    w.put(r, 4, 4, p.hours, S.number);
    w.put(r, 5, 5, travel ? p.travel : null, S.number);
    hours += p.hours;
    travelHours += p.travel;
  });
  r = w.next(16);
  w.put(r, 0, 3, 'Итого', S.total);
  w.put(r, 4, 4, Math.round(hours * 100) / 100, S.total);
  w.put(r, 5, 5, travel ? travelHours : null, S.total);

  section('Использованные запчасти и материалы');
  if (!a.parts.length) {
    w.put(w.next(16), 0, L, 'Не использовались', S.cell);
  } else {
    r = w.next(28);
    w.put(r, 0, 0, '№', S.head);
    w.put(r, 1, 1, 'Наименование', S.head);
    w.put(r, 2, 3, 'Номер по каталогу', S.head);
    w.put(r, 4, 4, 'Кол-во', S.head);
    w.put(r, 5, 5, 'Ед. изм.', S.head);
    for (const p of a.parts) {
      r = w.next(w.heightFor(16, [p.name, 1, 1], [p.catalogNumber, 2, 3]));
      w.put(r, 0, 0, String(p.position), S.center);
      w.put(r, 1, 1, p.name, S.cell);
      w.put(r, 2, 3, p.catalogNumber, S.cell);
      w.put(r, 4, 4, p.quantity, S.number);
      w.put(r, 5, 5, p.unitName, S.center);
    }
  }
  signatures(w, a, [0, 1], [3, L]);
}

// ---------- Лист «Табель» ----------

function timesheetSheet(ws: Worksheet, a: ActDetail) {
  const travel = !a.hideTravelTime;
  const widths = travel ? [11, 24, 8, 10, 22, 9, 9] : [11, 26, 9, 11, 24, 10];
  const L = widths.length - 1;
  const w = new SheetWriter(ws, widths);
  w.setup(a.number ? `Табель к акту № ${a.number}` : 'Табель');
  w.put(w.next(22), 0, L, 'ТАБЕЛЬ РАБОЧЕГО ВРЕМЕНИ', S.title);
  w.put(
    w.next(16),
    0,
    L,
    `${a.number ? `к акту № ${a.number}` : 'к черновику акта'} от ${date(documentDate(a))}`,
    S.subtitle,
  );
  w.next(8);
  const head = w.next(30);
  ['Дата', 'Инженер', 'Начало', 'Окончание', 'Перерывы', 'Часы', ...(travel ? ['В пути, ч'] : [])].forEach(
    (h, i) => w.put(head, i, i, h, S.head),
  );
  ws.pageSetup.printTitlesRow = `${head}:${head}`;
  const names = new Map(a.engineers.map((p) => [p.engineerId, p.fullName]));
  const rows = [...a.timeEntries].sort(
    (x, y) =>
      x.workDate.localeCompare(y.workDate) ||
      (names.get(x.engineerId) ?? '').localeCompare(names.get(y.engineerId) ?? ''),
  );
  let hours = 0;
  let travelHours = 0;
  for (const t of rows) {
    const breaks = t.breaks.length ? t.breaks.map((b) => `${b.startTime}–${b.endTime}`).join(', ') : null;
    const name = names.get(t.engineerId) ?? '—';
    const r = w.next(w.heightFor(16, [breaks, 4, 4], [name, 1, 1]));
    const h = Math.round((t.workMinutes / 60) * 100) / 100;
    w.put(r, 0, 0, date(t.workDate), S.center);
    w.put(r, 1, 1, name, S.cell);
    w.put(r, 2, 2, t.startTime ?? null, S.center);
    w.put(r, 3, 3, t.endTime ?? null, S.center);
    w.put(r, 4, 4, breaks, S.cell);
    w.put(r, 5, 5, h, S.number);
    if (travel) w.put(r, 6, 6, t.travelHours ?? null, S.number);
    hours += h;
    travelHours += t.travelHours ?? 0;
  }
  const r = w.next(16);
  w.put(r, 0, 4, 'Итого продуктивных часов', S.total);
  w.put(r, 5, 5, Math.round(hours * 100) / 100, S.total);
  if (travel) w.put(r, 6, 6, travelHours, S.total);
  w.next(8);
  w.put(
    w.next(28),
    0,
    L,
    'Продуктивные часы рассчитаны как продолжительность работы за вычетом перерывов. Окончание раньше начала означает окончание работы на следующие сутки.',
    S.note,
  );
}

// ---------- Листы «Техотчёт» ----------

const PER_SHEET = 6;
const PHOTO_ROW = 215;
const CAPTION_ROW = 30;
const colPx = (chars: number) => Math.floor(chars * 7 + 5);

function techReportSheets(wb: import('exceljs').Workbook, a: ActDetail, photos: PrintPhoto[]) {
  const widths = [16, 31, 16, 31];
  const L = 3;
  const sheets = Math.max(1, Math.ceil(photos.length / PER_SHEET));
  for (let s = 0; s < sheets; s++) {
    const ws = wb.addWorksheet(`Техотчёт ${s + 1}`);
    const w = new SheetWriter(ws, widths);
    w.setup(a.number ? `Техотчёт к акту № ${a.number}` : 'Техотчёт');
    w.put(
      w.next(22),
      0,
      L,
      `ТЕХНИЧЕСКИЙ ОТЧЁТ${a.number ? ` к акту № ${a.number}` : ''} от ${date(documentDate(a))}`,
      S.title,
    );
    w.put(w.next(14), 0, L, sheets > 1 ? `Лист ${s + 1} из ${sheets}` : null, S.subtitle);
    w.next(4);
    w.put(w.next(18), 0, L, 'Сведения о работах', S.section);
    const pair = (
      l1: string,
      v1: string | null | undefined,
      l2: string | null,
      v2: string | null | undefined,
    ) => {
      if (!v1 && !v2) return;
      if (v1 && v2) {
        const r = w.next(w.heightFor(16, [v1, 1, 1], [v2, 3, 3], [l1, 0, 0], [l2, 2, 2]));
        w.put(r, 0, 0, l1, S.label);
        w.put(r, 1, 1, v1, S.value);
        w.put(r, 2, 2, l2, S.label);
        w.put(r, 3, 3, v2, S.value);
      } else {
        const label = v1 ? l1 : l2!;
        const value = (v1 ?? v2)!;
        const r = w.next(w.heightFor(16, [value, 1, L], [label, 0, 0]));
        w.put(r, 0, 0, label, S.label);
        w.put(r, 1, L, value, S.value);
      }
    };
    if (a.organization) {
      pair('Заказчик', a.organization.name, 'Площадка', a.site?.name);
      pair('Подразделение', a.organization.departmentName, 'Адрес площадки', a.site?.address);
    }
    const e = a.equipment;
    if (e) {
      pair('Техника', `${e.typeName ? `${e.typeName} ` : ''}${e.brand} ${e.model}`, 'Наработка', meter(a));
      pair('Серийный номер', e.serialNumber, 'Гаражный номер', e.garageNumber);
      pair('Госномер', e.plateNumber, null, null);
    }
    pair('Период работ', period(a), 'Место работ', a.location);
    if (a.shortDescription) {
      const r = w.next(w.heightFor(16, [a.shortDescription, 1, L]));
      w.put(r, 0, 0, 'Работы', S.label);
      w.put(r, 1, L, a.shortDescription, S.valueBold);
    }
    const block = (title: string, text?: string | null) => {
      if (!text) return;
      w.next(6);
      w.put(w.next(18), 0, L, title, S.section);
      w.put(w.next(w.heightFor(30, [text, 0, L])), 0, L, text, S.text);
    };
    if (s === 0) {
      block('Подробное описание выполненных работ', a.detailedDescription);
      block('Рекомендации', a.recommendations);
    }
    w.next(6);
    w.put(w.next(18), 0, L, 'Фотографии', S.section);
    const page = photos.slice(s * PER_SHEET, (s + 1) * PER_SHEET);
    if (!page.length) {
      w.put(w.next(18), 0, L, a.photos.length ? 'Фото не сохранены на этом ПК' : 'Фото не приложены', S.cell);
    }
    for (let i = 0; i < page.length; i += 2) {
      const pr = w.next(PHOTO_ROW);
      const cr = w.next(CAPTION_ROW);
      for (let slot = 0; slot < 2; slot++) {
        const from = slot * 2;
        const photo = page[i + slot];
        w.put(pr, from, from + 1, null, S.photo);
        if (!photo) {
          w.put(cr, from, from + 1, null, S.caption);
          continue;
        }
        const leftPx = colPx(widths[from]);
        const areaW = leftPx + colPx(widths[from + 1]);
        const areaH = (PHOTO_ROW * 96) / 72;
        const scale = Math.min((areaW - 12) / photo.width, (areaH - 12) / photo.height);
        const width = photo.width * scale;
        const height = photo.height * scale;
        const x = (areaW - width) / 2;
        const col = x < leftPx ? from + x / leftPx : from + 1 + (x - leftPx) / colPx(widths[from + 1]);
        const id = wb.addImage({ buffer: photo.data, extension: photo.extension });
        ws.addImage(id, { tl: { col, row: pr - 1 + (areaH - height) / 2 / areaH }, ext: { width, height } });
        const caption = `Фото ${photo.number}${photo.caption ? `. ${photo.caption}` : ''}`;
        w.put(cr, from, from + 1, caption, S.caption);
        const needed = w.heightFor(CAPTION_ROW, [caption, from, from + 1]);
        if (needed > (ws.getRow(cr).height ?? 0)) ws.getRow(cr).height = needed;
      }
    }
    if (s === sheets - 1) signatures(w, a, [0, 1], [2, 3]);
  }
}

/** Файл .xlsx акта; ExcelJS загружается только при печати. */
export async function buildActWorkbook(act: ActDetail, photos: PrintPhoto[]): Promise<ArrayBuffer> {
  const ExcelJS = (await import('exceljs')).default;
  const wb = new ExcelJS.Workbook();
  wb.creator = 'Сервисные акты (ПК без связи)';
  actSheet(wb.addWorksheet('Акт'), act);
  if (act.timeEntries.length) timesheetSheet(wb.addWorksheet('Табель'), act);
  techReportSheets(wb, act, photos);
  return (await wb.xlsx.writeBuffer()) as ArrayBuffer;
}

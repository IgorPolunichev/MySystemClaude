import Dexie, { type Table } from 'dexie';
import type { ActDetail } from '../api/generated/acts/model';

/** Акт на устройстве: карточка как с сервера; localOnly — создан офлайн и ещё не отправлен. */
export interface StoredAct {
  id: string;
  detail: ActDetail;
  localOnly: boolean;
  /** Временный номер черновика, созданного без связи: Ч-{код устройства}-{n}. */
  tempNumber?: string;
  /** Пользователь отметил «Открыть после синхронизации». */
  openAfterSync?: boolean;
  savedAt: number;
}

/** Операция в очереди отправки (клиентский outbox, раздел 17.2). */
export interface OutboxOp {
  seq?: number;
  opId: string;
  actId: string;
  method: 'POST' | 'PUT' | 'DELETE';
  url: string;
  body?: unknown;
  /** Что сделано — для журнала синхронизации. */
  label: string;
  createdAt: number;
  status: 'pending' | 'conflict' | 'rejected';
  error?: string;
  attempts: number;
}

export interface RefRow {
  key: string;
  data: unknown;
  savedAt: number;
}

export interface MetaRow {
  key: string;
  value: unknown;
}

class OfflineDb extends Dexie {
  acts!: Table<StoredAct, string>;
  outbox!: Table<OutboxOp, number>;
  refs!: Table<RefRow, string>;
  meta!: Table<MetaRow, string>;

  constructor() {
    super('acts-offline');
    this.version(1).stores({ acts: 'id, savedAt', outbox: '++seq, actId, status', refs: 'key', meta: 'key' });
  }
}

export const db = new OfflineDb();

export async function getMeta<T>(key: string): Promise<T | undefined> {
  return (await db.meta.get(key))?.value as T | undefined;
}

export async function setMeta(key: string, value: unknown): Promise<void> {
  await db.meta.put({ key, value });
}

export async function getRef<T>(key: string): Promise<T | undefined> {
  return (await db.refs.get(key))?.data as T | undefined;
}

/** Выход из учётной записи очищает локальную базу (раздел 17.8). */
export async function clearLocalData(): Promise<void> {
  await Promise.all([db.acts.clear(), db.outbox.clear(), db.refs.clear(), db.meta.clear()]);
}

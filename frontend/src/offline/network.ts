import { useSyncExternalStore } from 'react';

/**
 * Состояние связи и очереди для индикатора в шапке. «Офлайн» — браузер сообщил об отсутствии сети или запрос к
 * серверу не дошёл; «Онлайн» — снова после события online или удачного запроса.
 */
interface State {
  online: boolean;
  pending: number;
  problems: number;
  syncing: boolean;
  lastSyncAt?: number;
}

let state: State = {
  online: typeof navigator === 'undefined' ? true : navigator.onLine,
  pending: 0,
  problems: 0,
  syncing: false,
};
const listeners = new Set<() => void>();

function emit(patch: Partial<State>) {
  const next = { ...state, ...patch };
  if (Object.keys(patch).every((k) => next[k as keyof State] === state[k as keyof State])) return;
  state = next;
  listeners.forEach((l) => l());
}

export const network = {
  get: () => state,
  setOnline: (online: boolean) => emit({ online }),
  setQueue: (pending: number, problems: number) => emit({ pending, problems }),
  setSyncing: (syncing: boolean) => emit(syncing ? { syncing } : { syncing, lastSyncAt: Date.now() }),
  subscribe: (l: () => void) => {
    listeners.add(l);
    return () => listeners.delete(l);
  },
};

if (typeof window !== 'undefined') {
  window.addEventListener('online', () => network.setOnline(true));
  window.addEventListener('offline', () => network.setOnline(false));
}

export function useNetwork(): State {
  return useSyncExternalStore(network.subscribe, network.get, network.get);
}

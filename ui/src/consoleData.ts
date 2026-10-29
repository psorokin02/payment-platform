import type { Page, Payment } from './types';

export function paymentPage(
  rows: Payment[],
  {
    search,
    status,
    page,
    pageSize = 20,
  }: { search: string; status: string; page: number; pageSize?: number },
) {
  const query = search.trim().toLocaleLowerCase('ru-RU');
  const filtered = rows.filter(
    (row) =>
      (status === 'all' || row.status === status) &&
      `${row.id} ${row.reference} ${row.customer} ${row.email} ${row.description} ${row.paymentType}`
        .toLocaleLowerCase('ru-RU')
        .includes(query),
  );
  const pages = Math.max(1, Math.ceil(filtered.length / pageSize));
  const currentPage = Math.min(Math.max(1, page), pages);
  return {
    rows: filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize),
    total: filtered.length,
    pages,
    page: currentPage,
  };
}
export function readsForPage(page: Page) {
  return {
    payments: page === 'overview' || page === 'payments',
    customers: page === 'overview',
    health: page === 'services' || page === 'api',
    audit: page === 'audit',
    logs: page === 'services',
  };
}

export type RefreshResult<T = unknown> =
  | { state: 'ready'; data: T }
  | { state: 'error'; error: unknown }
  | { state: 'cancelled' }
  | { state: 'skipped' };
export type RefreshState =
  | { state: 'idle' | 'refreshing' | 'success' }
  | { state: 'error'; message: string };

/** Wait for every active source, even when one of them fails first. */
export async function refreshTogether(
  reads: Promise<RefreshResult>[],
): Promise<RefreshResult<void>> {
  const results = await Promise.all(
    reads.map((read) => read.catch((error): RefreshResult => ({ state: 'error', error }))),
  );
  const failure = results.find((result) => result.state === 'error');
  if (failure?.state === 'error') return failure;
  if (results.some((result) => result.state === 'cancelled')) return { state: 'cancelled' };
  return results.some((result) => result.state === 'ready')
    ? { state: 'ready', data: undefined }
    : { state: 'skipped' };
}

/** Only explicit actions publish feedback; background polling stays quiet. */
export function createRefreshAction(
  read: () => Promise<RefreshResult>,
  onState: (state: RefreshState) => void,
) {
  let stopped = false;
  let latest = 0;
  return {
    async refresh(): Promise<RefreshResult> {
      if (stopped) return { state: 'cancelled' };
      const request = ++latest;
      onState({ state: 'refreshing' });
      let result: RefreshResult;
      try {
        result = await read();
      } catch (error) {
        result = { state: 'error', error };
      }
      if (!stopped && request === latest) {
        onState(
          result.state === 'ready'
            ? { state: 'success' }
            : result.state === 'error'
              ? {
                  state: 'error',
                  message:
                    result.error instanceof Error
                      ? result.error.message
                      : 'Не удалось получить данные',
                }
              : { state: 'idle' },
        );
      }
      return result;
    },
    stop() {
      stopped = true;
      latest++;
    },
  };
}

/** A refresh during a request waits for the next read; stop settles all waiting callers. */
export function startPolling<T>(
  load: (signal: AbortSignal) => Promise<T>,
  onValue: (value: T) => void,
  {
    interval = 3000,
    visible = () => true,
    onError = () => {},
  }: { interval?: number; visible?: () => boolean; onError?: (error: unknown) => void } = {},
) {
  let stopped = false;
  let running = false;
  type Waiter = (result: RefreshResult<T>) => void;
  let pending: Waiter[] = [];
  let active: Waiter[] = [];
  let timer: ReturnType<typeof setTimeout> | undefined;
  let controller: AbortController | undefined;
  async function read() {
    running = true;
    active = pending;
    pending = [];
    controller = new AbortController();
    let result: RefreshResult<T>;
    try {
      const value = await load(controller.signal);
      if (!stopped) onValue(value);
      result = stopped ? { state: 'cancelled' } : { state: 'ready', data: value };
    } catch (error) {
      if (!stopped) onError(error);
      result = stopped ? { state: 'cancelled' } : { state: 'error', error };
    } finally {
      running = false;
    }
    active.forEach((resolve) => resolve(result));
    active = [];
    if (stopped) return;
    if (pending.length) void read();
    else timer = setTimeout(() => void refresh(), interval);
  }
  function refresh(): Promise<RefreshResult<T>> {
    if (stopped) return Promise.resolve({ state: 'cancelled' });
    clearTimeout(timer);
    if (!visible()) return Promise.resolve({ state: 'skipped' });
    const result = new Promise<RefreshResult<T>>((resolve) => pending.push(resolve));
    if (!running) void read();
    return result;
  }
  void refresh();
  return {
    refresh,
    stop: () => {
      stopped = true;
      clearTimeout(timer);
      controller?.abort();
      [...active, ...pending].forEach((resolve) => resolve({ state: 'cancelled' }));
      active = [];
      pending = [];
    },
  };
}

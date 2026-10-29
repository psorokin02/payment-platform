import { useCallback, useEffect, useRef, useState } from 'react';
import { api, errorMessage, fetchServiceLogs } from './api';
import {
  createRefreshAction,
  readsForPage,
  refreshTogether,
  startPolling,
  type RefreshResult,
  type RefreshState,
} from './consoleData';
import type {
  AntifraudStatus,
  AuditEntry,
  AuditStatus,
  Customer,
  ListResult,
  Page,
  PaymentStatus,
  Source,
  ServiceLogs,
  ServiceId,
} from './types';

export function useRemote<T>(key: string | null, load: (signal: AbortSignal) => Promise<T>) {
  const [value, setValue] = useState<{ key: string | null; source: Source<T> }>({
    key,
    source: { state: 'loading' },
  });
  const loader = useRef(load);
  loader.current = load;
  const watcher = useRef<ReturnType<typeof startPolling<T>> | null>(null);
  useEffect(() => {
    if (!key) return;
    setValue({ key, source: { state: 'loading' } });
    const poll = startPolling(
      (signal) => loader.current(signal),
      (data) => setValue({ key, source: { state: 'ready', data } }),
      {
        visible: () => document.visibilityState !== 'hidden',
        onError: (error) =>
          setValue({ key, source: { state: 'error', error: errorMessage(error) } }),
      },
    );
    watcher.current = poll;
    const onVisible = () => {
      if (document.visibilityState !== 'hidden') poll.refresh();
    };
    document.addEventListener('visibilitychange', onVisible);
    window.addEventListener('focus', onVisible);
    return () => {
      poll.stop();
      watcher.current = null;
      document.removeEventListener('visibilitychange', onVisible);
      window.removeEventListener('focus', onVisible);
    };
  }, [key]);
  const refresh = useCallback(
    () => watcher.current?.refresh() ?? Promise.resolve<RefreshResult<T>>({ state: 'skipped' }),
    [],
  );
  return {
    source: value.key === key && key ? value.source : ({ state: 'loading' } as Source<T>),
    refresh,
  };
}

export function useManualRefresh(key: string | null, read: () => Promise<RefreshResult>) {
  const [value, setValue] = useState<{ key: string | null; state: RefreshState }>({
    key,
    state: { state: 'idle' },
  });
  const loader = useRef(read);
  loader.current = read;
  const action = useRef<ReturnType<typeof createRefreshAction> | null>(null);
  useEffect(() => {
    setValue({ key, state: { state: 'idle' } });
    if (!key) return;
    const current = createRefreshAction(
      () => loader.current(),
      (state) => setValue({ key, state }),
    );
    action.current = current;
    return () => {
      current.stop();
      action.current = null;
    };
  }, [key]);
  const refresh = useCallback(
    () => action.current?.refresh() ?? Promise.resolve<RefreshResult>({ state: 'cancelled' }),
    [],
  );
  return {
    refresh,
    refreshState: value.key === key ? value.state : ({ state: 'idle' } as RefreshState),
  };
}

export function usePlatform(page: Page) {
  const reads = readsForPage(page);
  const listRead = useRemote<ListResult>(reads.payments ? `${page}:payments` : null, (signal) =>
    api('/api/payments', { signal }),
  );
  const customersRead = useRemote<Customer[]>(reads.customers ? 'customers' : null, (signal) =>
    api('/api/customers', { signal }),
  );
  const paymentRead = useRemote<PaymentStatus>(reads.health ? 'payment-health' : null, (signal) =>
    api('/api/admin/status', { signal }),
  );
  const antifraudRead = useRemote<AntifraudStatus>(
    reads.health ? 'antifraud-health' : null,
    (signal) => api('/api/antifraud/admin/status', { signal }),
  );
  const auditRead = useRemote<AuditStatus>(reads.health ? 'audit-health' : null, (signal) =>
    api('/api/audit/admin/status', { signal }),
  );
  const entriesRead = useRemote<AuditEntry[]>(reads.audit ? 'audit-entries' : null, (signal) =>
    api('/api/audit/entries', { signal }),
  );
  const paymentLogs = useRemote<ServiceLogs>(reads.logs ? 'payment-logs' : null, (signal) =>
    fetchServiceLogs('payment', signal),
  );
  const antifraudLogs = useRemote<ServiceLogs>(reads.logs ? 'antifraud-logs' : null, (signal) =>
    fetchServiceLogs('antifraud', signal),
  );
  const auditLogs = useRemote<ServiceLogs>(reads.logs ? 'audit-logs' : null, (signal) =>
    fetchServiceLogs('audit', signal),
  );
  const [busy, setBusy] = useState('');
  const working = useRef(false);
  const [toast, setToast] = useState<{ message: string; error: boolean } | null>(null);
  const run = useCallback(async (label: string, action: () => Promise<void>) => {
    if (working.current) return;
    working.current = true;
    setBusy(label);
    try {
      await action();
    } catch (error) {
      setToast({ message: errorMessage(error), error: true });
    } finally {
      working.current = false;
      setBusy('');
    }
  }, []);
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), 6000);
    return () => clearTimeout(timer);
  }, [toast]);
  const manualRefresh = useManualRefresh(page, () =>
    refreshTogether([
      listRead.refresh(),
      customersRead.refresh(),
      paymentRead.refresh(),
      antifraudRead.refresh(),
      auditRead.refresh(),
      entriesRead.refresh(),
      paymentLogs.refresh(),
      antifraudLogs.refresh(),
      auditLogs.refresh(),
    ]),
  );
  return {
    list: listRead.source.state === 'ready' ? listRead.source.data : null,
    listSource: listRead.source,
    customers: customersRead.source.state === 'ready' ? customersRead.source.data : [],
    customersSource: customersRead.source,
    paymentSource: paymentRead.source,
    antifraudSource: antifraudRead.source,
    auditSource: auditRead.source,
    entriesSource: entriesRead.source,
    logSources: {
      payment: paymentLogs.source,
      antifraud: antifraudLogs.source,
      audit: auditLogs.source,
    },
    busy,
    toast,
    setToast,
    run,
    ...manualRefresh,
    reload: listRead.refresh,
    reloadService: (service: ServiceId) =>
      service === 'payment'
        ? paymentRead.refresh()
        : service === 'antifraud'
          ? antifraudRead.refresh()
          : auditRead.refresh(),
  };
}
export type Platform = ReturnType<typeof usePlatform>;

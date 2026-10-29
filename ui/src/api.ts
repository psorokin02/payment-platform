import type {
  AntifraudMode,
  AntifraudStatus,
  AuditEntry,
  CreatePaymentDraft,
  FraudCheck,
  Payment,
  PaymentSources,
  ReadResult,
  Source,
  PaymentStatus,
  AuditStatus,
  AuditSettings,
  Settings,
  ServiceId,
  ServiceLogs,
} from './types';

class HttpError extends Error {
  readonly status: number;
  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const timeout = AbortSignal.timeout(12000);
  const response = await fetch(path, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options.headers },
    signal: options.signal ? AbortSignal.any([options.signal, timeout]) : timeout,
  });
  if (!response.ok) {
    const text = await response.text();
    let message = `HTTP ${response.status}`;
    try {
      const body = JSON.parse(text);
      message = body.message || body.detail || message;
    } catch {
      /* A proxy can return non-JSON. */
    }
    throw new HttpError(message, response.status);
  }
  return response.json() as Promise<T>;
}
export const post = <T>(path: string, body?: unknown) =>
  api<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) });
export const createPayment = (draft: CreatePaymentDraft) => post<Payment>('/api/payments', draft);

const serviceAdmin: Record<ServiceId, string> = {
  payment: '/api/admin',
  antifraud: '/api/antifraud/admin',
  audit: '/api/audit/admin',
};

export const fetchServiceLogs = (service: ServiceId, signal?: AbortSignal) =>
  api<ServiceLogs>(`${serviceAdmin[service]}/logs`, { signal });

export async function savePaymentSettings(settings: Settings, signal?: AbortSignal) {
  await api<PaymentStatus>('/api/admin/settings', {
    method: 'PUT',
    body: JSON.stringify(settings),
    signal,
  });
  const saved = await api<PaymentStatus>('/api/admin/status', { signal });
  if (
    Object.entries(settings).some(([key, value]) => saved.settings[key as keyof Settings] !== value)
  )
    throw new Error('Настройки Payment не подтверждены при повторном чтении.');
  return saved;
}

export async function saveAntifraudMode(mode: AntifraudMode, signal?: AbortSignal) {
  await api<AntifraudStatus>('/api/antifraud/admin/mode', {
    method: 'PUT',
    body: JSON.stringify({ mode }),
    signal,
  });
  const saved = await api<AntifraudStatus>('/api/antifraud/admin/status', { signal });
  if (saved.mode !== mode) throw new Error('Режим Antifraud не подтверждён при повторном чтении.');
  return saved;
}

export async function saveAuditSettings(settings: AuditSettings, signal?: AbortSignal) {
  // Пауза запрашивается до остальных изменений; возобновление — после сохранения защиты от дублей.
  if (settings.paused) await api<AuditStatus>('/api/audit/admin/pause', { method: 'POST', signal });
  await api<AuditStatus>('/api/audit/admin/settings', {
    method: 'PUT',
    body: JSON.stringify({ idempotencyEnabled: settings.idempotencyEnabled }),
    signal,
  });
  if (!settings.paused)
    await api<AuditStatus>('/api/audit/admin/resume', { method: 'POST', signal });
  const saved = await api<AuditStatus>('/api/audit/admin/status', { signal });
  if (saved.idempotencyEnabled !== settings.idempotencyEnabled || saved.paused !== settings.paused)
    throw new Error('Настройки Audit не подтверждены при повторном чтении.');
  return saved;
}

export const errorMessage = (error: unknown) =>
  error instanceof Error ? error.message : 'Не удалось получить данные';

async function readSource<T>(
  path: string,
  signal?: AbortSignal,
  allowMissing = false,
): Promise<Source<T>> {
  try {
    return { state: 'ready', data: await api<T>(path, { signal }) };
  } catch (error) {
    if (allowMissing && error instanceof HttpError && error.status === 404) {
      return { state: 'missing', reason: 'not-found' };
    }
    return { state: 'error', error: errorMessage(error) };
  }
}

/** Read the saved facts only: opening a drawer never performs another fraud check. */
export async function fetchPaymentSources(
  id: number,
  signal?: AbortSignal,
): Promise<PaymentSources> {
  const auditRead = readSource<AuditEntry[]>(`/api/audit/entries?paymentId=${id}`, signal);
  const payment = await readSource<ReadResult>(`/api/payments/${id}`, signal);
  let fraud: Source<FraudCheck>;
  if (payment.state !== 'ready') {
    fraud = { state: 'error', error: 'Ссылка на проверку недоступна: Payment не ответил.' };
  } else if (!payment.data.payment.antifraudRequestId) {
    fraud = { state: 'missing' };
  } else {
    fraud = await readSource<FraudCheck>(
      `/api/antifraud/checks/${encodeURIComponent(payment.data.payment.antifraudRequestId)}`,
      signal,
      true,
    );
  }
  return { payment, fraud, audit: await auditRead };
}

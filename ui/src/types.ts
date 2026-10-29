export type PaymentType = 'CARD' | 'QR' | 'INSTALLMENT' | 'RECURRENT';
export type Payment = {
  id: number;
  reference: string;
  customer: string;
  email: string;
  customerId: number;
  amount: number;
  currency: string;
  status: 'NEW' | 'CONFIRMED' | 'REJECTED';
  description: string;
  createdAt: string;
  paymentType: PaymentType;
  rejectionReason: string | null;
  antifraudRequestId: string | null;
};
export type Customer = { id: number; name: string; email: string };
export type CreatePaymentDraft = {
  customerId: number;
  amount: number;
  description: string;
  paymentType: PaymentType;
};
export type ReadResult = {
  payment: Payment;
  source: string;
};
export type ListResult = {
  payments: Payment[];
  source: string;
};
export type Settings = {
  fetchMode: 'nplus1' | 'join';
  cacheEnabled: boolean;
  cacheInvalidationEnabled: boolean;
  httpMode: 'timeout' | 'retry' | 'breaker';
  eventCopies: number;
};
export type PaymentStatus = {
  settings: Settings;
  circuitState: string;
  antifraudTimeoutMs?: number;
};
export type AntifraudMode = 'normal' | 'slow' | 'error' | 'reject';
export type AntifraudStatus = {
  mode: AntifraudMode;
  slowDelayMs?: number;
};
export type AuditStatus = {
  idempotencyEnabled: boolean;
  paused: boolean;
  pauseApplied: boolean;
  assigned: number;
};
export type AuditSettings = {
  idempotencyEnabled: boolean;
  paused: boolean;
};
export type AuditEntry = {
  id: number;
  eventId: string;
  paymentId: number;
  amount: number;
  customer: string;
  type: string;
  occurredAt: string;
  receivedAt: string;
  paymentType: PaymentType | null;
  status: Payment['status'] | null;
  reason: string | null;
};
export type FraudCheck = {
  requestId: string;
  amount: number;
  paymentType: PaymentType;
  approved: boolean;
  reason: string | null;
  checkedAt: string;
};
export type Page = 'overview' | 'payments' | 'services' | 'api' | 'audit';
export type ServiceId = 'payment' | 'antifraud' | 'audit';
export type ServiceLog = {
  sequence: number;
  timestamp: string;
  service: string;
  level: string;
  logger: string;
  message: string;
};
export type ServiceLogs = { service: string; entries: ServiceLog[] };
export type LogSources = Record<ServiceId, Source<ServiceLogs>>;
export type Source<T> =
  | { state: 'loading'; data?: never; error?: never }
  | { state: 'ready'; data: T; error?: never }
  | { state: 'missing'; reason?: 'not-found'; data?: never; error?: never }
  | { state: 'error'; data?: never; error: string };
export type PaymentSources = {
  payment: Source<ReadResult>;
  fraud: Source<FraudCheck>;
  audit: Source<AuditEntry[]>;
};

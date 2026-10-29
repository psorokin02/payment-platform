import { ArrowUpRight, ChevronRight, Radio, RefreshCw, X } from 'lucide-react';
import type { Payment, Source } from './types';
import type { RefreshState } from './consoleData';

export const money = (value: number) =>
  new Intl.NumberFormat('ru-RU', {
    style: 'currency',
    currency: 'RUB',
    maximumFractionDigits: 2,
  }).format(value);
export const dateTime = (value: string) =>
  new Date(value).toLocaleString('ru-RU', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
export const statusLabel = { NEW: 'Новый', CONFIRMED: 'Подтверждён', REJECTED: 'Отклонён' };
export const methodLabel = {
  CARD: 'Банковская карта',
  QR: 'QR-код',
  INSTALLMENT: 'Рассрочка',
  RECURRENT: 'Рекуррентный',
};
export function Badge({ status }: { status: Payment['status'] }) {
  return (
    <span className={`badge ${status.toLowerCase()}`}>
      <i />
      {statusLabel[status]}
    </span>
  );
}
export function Empty({ text }: { text: string }) {
  return (
    <div className="empty">
      <Radio size={22} />
      <span>{text}</span>
    </div>
  );
}
export function SourceMessage({
  source,
  onRetry,
  emptyText,
}: {
  source: Source<unknown>;
  onRetry: () => void;
  emptyText?: string;
}) {
  if (source.state === 'error')
    return (
      <div className="source-error" role="status">
        <span>
          Источник недоступен<small>{source.error}</small>
        </span>
        <button className="text-button" onClick={onRetry}>
          <RefreshCw size={13} />
          Повторить
        </button>
      </div>
    );
  if (source.state === 'loading')
    return (
      <div className="loading-state" role="status">
        <RefreshCw className="spin" size={15} />
        Загружаем данные…
      </div>
    );
  if (source.state === 'missing') return <Empty text={emptyText ?? 'Данных пока нет'} />;
  return null;
}
export function RefreshControl({
  state,
  onRefresh,
  compact = false,
  disabled = false,
}: {
  state: RefreshState;
  onRefresh: () => void;
  compact?: boolean;
  disabled?: boolean;
}) {
  const refreshing = state.state === 'refreshing';
  return (
    <div className={`refresh-control ${compact ? 'compact' : ''}`}>
      <button
        className={compact ? 'text-button' : 'icon-button page-refresh'}
        aria-label={refreshing ? 'Обновляем данные' : 'Обновить данные'}
        disabled={disabled || refreshing}
        onClick={onRefresh}
      >
        <RefreshCw className={refreshing ? 'spin' : ''} size={compact ? 14 : 18} />
        {compact && (refreshing ? 'Обновляем…' : 'Обновить')}
      </button>
      <span role="status" className={`refresh-status ${state.state}`}>
        {refreshing && 'Обновляем данные…'}
        {state.state === 'success' && 'Данные обновлены'}
        {state.state === 'error' && `Не удалось обновить: ${state.message}`}
      </span>
    </div>
  );
}
export function Json({ data, label = 'JSON источника' }: { data: unknown; label?: string }) {
  return (
    <details className="json-details">
      <summary>{label}</summary>
      <pre>{JSON.stringify(data, null, 2)}</pre>
    </details>
  );
}
export function Modal({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: React.ReactNode;
}) {
  return (
    <div
      className="overlay"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section role="dialog" aria-modal="true" aria-label={title} className="modal">
        <div className="modal-heading">
          <h2>{title}</h2>
          <button autoFocus className="icon-button" aria-label="Закрыть" onClick={onClose}>
            <X size={20} />
          </button>
        </div>
        {children}
      </section>
    </div>
  );
}
export function PaymentTable({
  payments,
  onSelect,
  compact = false,
  emptyText = 'Платежей пока нет. Создайте первый.',
}: {
  payments: Payment[];
  onSelect: (id: number) => void;
  compact?: boolean;
  emptyText?: string;
}) {
  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            <th>Платёж</th>
            <th className="customer-name">Владелец</th>
            <th>Сумма</th>
            <th>Статус</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {(compact ? payments.slice(0, 5) : payments).map((payment) => (
            <tr
              key={payment.id}
              tabIndex={0}
              aria-label={`Открыть ${payment.reference}`}
              onClick={() => onSelect(payment.id)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  onSelect(payment.id);
                }
              }}
            >
              <td>
                <div className="payment-reference">
                  <span className="row-icon">
                    <ArrowUpRight size={17} />
                  </span>
                  <div>
                    <strong>{payment.reference}</strong>
                    <small>
                      {payment.description || methodLabel[payment.paymentType] || 'Платёж'}
                    </small>
                  </div>
                </div>
              </td>
              <td className="customer-name">{payment.customer}</td>
              <td className="amount">{money(payment.amount)}</td>
              <td>
                <Badge status={payment.status} />
              </td>
              <td>
                <ChevronRight size={16} className="secondary" />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {!payments.length && <Empty text={emptyText} />}
    </div>
  );
}

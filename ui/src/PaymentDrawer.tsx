import { Check, X } from 'lucide-react';
import {
  Badge,
  dateTime,
  Empty,
  Json,
  methodLabel,
  money,
  RefreshControl,
  SourceMessage,
} from './components';
import type { RefreshState } from './consoleData';
import type { PaymentSources } from './types';
export default function PaymentDrawer({
  paymentId,
  sources,
  busy,
  refreshState = { state: 'idle' },
  onClose,
  onRead,
  onConfirm,
}: {
  paymentId: number;
  sources: PaymentSources;
  busy: boolean;
  refreshState?: RefreshState;
  onClose: () => void;
  onRead: () => void;
  onConfirm: () => void;
}) {
  const payment = sources.payment.state === 'ready' ? sources.payment.data.payment : null;
  const fraud = sources.fraud.state === 'ready' ? sources.fraud.data : null;
  const audit = sources.audit.state === 'ready' ? sources.audit.data : null;
  return (
    <div
      className="drawer-overlay"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <aside
        className="payment-drawer"
        role="dialog"
        aria-modal="true"
        aria-label={`Карточка ${payment?.reference ?? `платежа ${paymentId}`}`}
      >
        <div className="drawer-top">
          <span className="eyebrow">КАРТОЧКА ПЛАТЕЖА</span>
          <button autoFocus className="icon-button" aria-label="Закрыть карточку" onClick={onClose}>
            <X size={20} />
          </button>
        </div>
        <div className="drawer-title">
          <h2>{payment?.reference ?? `Платёж #${paymentId}`}</h2>
          {payment && <p>{payment.description}</p>}
        </div>
        {payment && (
          <>
            <div className="payment-total">{money(payment.amount)}</div>
            <Badge status={payment.status} />
            <span className="drawer-method">
              {methodLabel[payment.paymentType] ?? 'Способ оплаты не сохранён'}
            </span>
          </>
        )}
        <div className="drawer-toolbar">
          <span>Данные трёх сервисов</span>
          <RefreshControl compact state={refreshState} disabled={busy} onRefresh={onRead} />
        </div>
        <section className="source-section">
          <div className="source-heading">
            <span className="source-number">01</span>
            <h3>Payment</h3>
            <span>Состояние платежа</span>
          </div>
          <SourceMessage source={sources.payment} onRetry={onRead} />
          {payment && (
            <>
              <dl className="payment-fields">
                <div>
                  <dt>Владелец</dt>
                  <dd>
                    {payment.customer}
                    <small>{payment.email}</small>
                  </dd>
                </div>
                <div>
                  <dt>Создан</dt>
                  <dd>{dateTime(payment.createdAt)}</dd>
                </div>
                {payment.rejectionReason && (
                  <div>
                    <dt>Причина отказа</dt>
                    <dd className="error-text">{payment.rejectionReason}</dd>
                  </div>
                )}
              </dl>
              {payment.status === 'NEW' && (
                <button className="button primary wide" disabled={busy} onClick={onConfirm}>
                  <Check size={16} />
                  Подтвердить платёж
                </button>
              )}
              <Json data={payment} label="JSON Payment" />
            </>
          )}
        </section>
        <section className="source-section">
          <div className="source-heading">
            <span className="source-number">02</span>
            <h3>Antifraud</h3>
            <span>Сохранённое решение</span>
          </div>
          {sources.fraud.state === 'missing' ? (
            <p className="missing-note">
              {sources.fraud.reason === 'not-found'
                ? 'Сохранённая проверка не найдена по ссылке из Payment.'
                : 'Сохранённой проверки нет. У исторических платежей ссылка на проверку может отсутствовать.'}
            </p>
          ) : (
            <SourceMessage source={sources.fraud} onRetry={onRead} />
          )}
          {fraud && (
            <>
              <div className={`fraud-result ${fraud.approved ? 'approved' : 'rejected'}`}>
                <span>{fraud.approved ? 'Операция одобрена' : 'Операция отклонена'}</span>
                <small>{dateTime(fraud.checkedAt)}</small>
              </div>
              {fraud.reason && <p className="fraud-reason">{fraud.reason}</p>}
              <Json data={fraud} label="JSON Antifraud" />
            </>
          )}
        </section>
        <section className="source-section">
          <div className="source-heading">
            <span className="source-number">03</span>
            <h3>Audit</h3>
            <span>Сохранённые события</span>
          </div>
          <SourceMessage source={sources.audit} onRetry={onRead} />
          {audit && (
            <>
              {audit.length ? (
                <div className="audit-facts">
                  {audit.map((entry) => (
                    <div className="audit-fact" key={entry.id}>
                      <i className="live-dot" />
                      <div>
                        <strong>{entry.type}</strong>
                        <small>{dateTime(entry.receivedAt)}</small>
                        {entry.reason && <p>{entry.reason}</p>}
                      </div>
                      {entry.status && <Badge status={entry.status} />}
                    </div>
                  ))}
                </div>
              ) : (
                <Empty text="Сохранённых событий пока нет" />
              )}
              <Json data={audit} label="JSON Audit" />
            </>
          )}
        </section>
      </aside>
    </div>
  );
}

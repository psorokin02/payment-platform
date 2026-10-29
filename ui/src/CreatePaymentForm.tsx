import { useEffect, useState } from 'react';
import { ArrowUpRight, CreditCard, QrCode, CalendarDays, Repeat2 } from 'lucide-react';
import { methodLabel } from './components';
import type { CreatePaymentDraft, Customer, PaymentType } from './types';
const methods = [
  { id: 'CARD', icon: CreditCard },
  { id: 'QR', icon: QrCode },
  { id: 'INSTALLMENT', icon: CalendarDays },
  { id: 'RECURRENT', icon: Repeat2 },
] as const;
export default function CreatePaymentForm({
  customers,
  busy,
  onCreate,
}: {
  customers: Customer[];
  busy: boolean;
  onCreate: (draft: CreatePaymentDraft) => void;
}) {
  const [customerId, setCustomerId] = useState(String(customers[0]?.id ?? ''));
  const [amount, setAmount] = useState('4990');
  const [description, setDescription] = useState('Java Backend · заказ');
  const [paymentType, setPaymentType] = useState<PaymentType>('CARD');
  useEffect(() => {
    if (!customerId && customers.length) setCustomerId(String(customers[0].id));
  }, [customerId, customers]);
  return (
    <section className="panel create-panel">
      <div className="create-intro">
        <span className="eyebrow">НОВАЯ ОПЕРАЦИЯ</span>
        <h2>
          Создать платёж<span className="accent">_</span>
        </h2>
        <p>
          Выберите способ оплаты.
          <br />
          Решение Antifraud сохранится в карточке.
        </p>
      </div>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          onCreate({
            customerId: Number(customerId),
            amount: Number(amount),
            description,
            paymentType,
          });
        }}
      >
        <fieldset className="payment-methods" disabled={busy}>
          <legend>Способ оплаты</legend>
          {methods.map((method) => (
            <label
              className={`method-option ${paymentType === method.id ? 'selected' : ''}`}
              key={method.id}
            >
              <input
                type="radio"
                name="paymentType"
                value={method.id}
                checked={paymentType === method.id}
                onChange={() => setPaymentType(method.id)}
              />
              <method.icon size={18} />
              <span>{methodLabel[method.id]}</span>
            </label>
          ))}
        </fieldset>
        <div className="form-fields">
          <label>
            Владелец
            <select
              required
              value={customerId}
              disabled={busy || !customers.length}
              onChange={(event) => setCustomerId(event.target.value)}
            >
              {!customers.length && <option value="">Нет данных о клиентах</option>}
              {customers.map((customer) => (
                <option key={customer.id} value={customer.id}>
                  {customer.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Сумма, ₽
            <input
              required
              type="number"
              min="0.01"
              max="10000000"
              step="0.01"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
              disabled={busy}
            />
          </label>
          <label className="description-field">
            Описание
            <input
              maxLength={160}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              disabled={busy}
            />
          </label>
        </div>
        <div className="form-bottom">
          <p>
            {paymentType === 'CARD' || paymentType === 'QR'
              ? 'Карта и QR: свыше 100 000 ₽ Antifraud отклонит платёж.'
              : 'Лимит 100 000 ₽ не применяется к этому способу оплаты.'}
          </p>
          <button type="submit" className="button primary" disabled={busy || !customerId}>
            {busy ? 'Создаём…' : 'Создать платёж'}
            <ArrowUpRight size={17} />
          </button>
        </div>
      </form>
    </section>
  );
}

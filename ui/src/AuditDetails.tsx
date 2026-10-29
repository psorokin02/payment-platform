import { ArrowRight } from 'lucide-react';
import { Modal, money } from './components';
import type { AuditEntry } from './types';

export default function AuditDetails({
  entry,
  onClose,
  onPayment,
}: {
  entry: AuditEntry;
  onClose: () => void;
  onPayment: (id: number) => void;
}) {
  return (
    <Modal title="Сохранённая запись Audit" onClose={onClose}>
      <div className="detail-stats event-detail-stats">
        <div>
          <span>Платёж</span>
          <strong>PAY-{String(entry.paymentId).padStart(4, '0')}</strong>
        </div>
        <div>
          <span>Сумма</span>
          <strong>{money(entry.amount)}</strong>
        </div>
        <div>
          <span>Событие произошло</span>
          <strong>{new Date(entry.occurredAt).toLocaleString('ru-RU')}</strong>
        </div>
        <div>
          <span>Audit сохранил</span>
          <strong>{new Date(entry.receivedAt).toLocaleString('ru-RU')}</strong>
        </div>
      </div>
      <pre className="event-payload">{JSON.stringify(entry, null, 2)}</pre>
      <button className="button primary wide" onClick={() => onPayment(entry.paymentId)}>
        Открыть платёж <ArrowRight size={15} />
      </button>
    </Modal>
  );
}

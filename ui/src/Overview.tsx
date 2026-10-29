import { ArrowRight } from 'lucide-react';
import { PaymentTable, SourceMessage } from './components';
import CreatePaymentForm from './CreatePaymentForm';
import type { CreatePaymentDraft } from './types';
import type { Platform } from './usePlatform';
export default function Overview({
  platform,
  onCreate,
  onPayment,
  onPage,
}: {
  platform: Platform;
  onCreate: (draft: CreatePaymentDraft) => void;
  onPayment: (id: number) => void;
  onPage: (page: 'payments') => void;
}) {
  return (
    <div className="overview-stack">
      <CreatePaymentForm
        customers={platform.customers}
        busy={!!platform.busy}
        onCreate={onCreate}
      />
      {platform.customersSource && platform.customersSource.state === 'error' && (
        <SourceMessage source={platform.customersSource} onRetry={platform.refresh} />
      )}
      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2>Последние платежи</h2>
            <p className="panel-caption">Откройте карточку, чтобы увидеть все три источника.</p>
          </div>
          <button className="text-button" onClick={() => onPage('payments')}>
            Все платежи
            <ArrowRight size={14} />
          </button>
        </div>
        {platform.listSource ? (
          <SourceMessage source={platform.listSource} onRetry={platform.reload} />
        ) : null}
        {(!platform.listSource || platform.listSource.state === 'ready') && (
          <PaymentTable payments={platform.list?.payments ?? []} compact onSelect={onPayment} />
        )}
      </section>
    </div>
  );
}

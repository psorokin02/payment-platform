import { useEffect, useRef, useState } from 'react';
import {
  ArrowLeft,
  ArrowRight,
  Code2,
  CreditCard,
  LayoutDashboard,
  ListChecks,
  RefreshCw,
  Search,
  Server,
  X,
} from 'lucide-react';
import { createPayment, fetchPaymentSources, post } from './api';
import ApiPage from './ApiPage';
import AuditDetails from './AuditDetails';
import AuditPage from './AuditPage';
import { PaymentTable, RefreshControl, SourceMessage } from './components';
import { paymentPage } from './consoleData';
import Overview from './Overview';
import PaymentDrawer from './PaymentDrawer';
import Services, { type ServiceId } from './Services';
import { useManualRefresh, usePlatform, useRemote } from './usePlatform';
import type { AuditEntry, CreatePaymentDraft, Page, Payment, PaymentSources } from './types';
const navigation = [
  { id: 'overview', label: 'Обзор', icon: LayoutDashboard },
  { id: 'payments', label: 'Платежи', icon: CreditCard },
  { id: 'services', label: 'Сервисы', icon: Server },
  { id: 'api', label: 'API', icon: Code2 },
  { id: 'audit', label: 'Аудит', icon: ListChecks },
] as const;
const headings = {
  overview: {
    title: 'Каждый платёж — на виду.',
    description: 'Создавайте операции и следите за сохранённым результатом.',
  },
  payments: { title: 'Платежи', description: 'Все операции. От создания до подтверждения.' },
  services: { title: 'Сервисы', description: 'Три сервиса. Текущее состояние каждого.' },
  api: { title: 'API', description: 'Настоящий Swagger. Прямой доступ к каждому сервису.' },
  audit: { title: 'Аудит', description: 'Факты, которые уже обработаны и сохранены.' },
};
const loadingSources: PaymentSources = {
  payment: { state: 'loading' },
  fraud: { state: 'loading' },
  audit: { state: 'loading' },
};
export default function App() {
  const [page, setPage] = useState<Page>('overview');
  const platform = usePlatform(page);
  const [paymentId, setPaymentId] = useState<number | null>(null);
  const [selectedAudit, setSelectedAudit] = useState<AuditEntry | null>(null);
  const [openService, setOpenService] = useState<ServiceId | null>('payment');
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('all');
  const [pageNumber, setPageNumber] = useState(1);
  const searchRef = useRef<HTMLInputElement>(null);
  const drawer = useRemote<PaymentSources>(
    paymentId === null ? null : `payment:${paymentId}`,
    (signal) => fetchPaymentSources(paymentId!, signal),
  );
  const drawerRefresh = useManualRefresh(
    paymentId === null ? null : `payment:${paymentId}`,
    async () => {
      const result = await drawer.refresh();
      if (result.state === 'ready') {
        const failure = Object.values(result.data).find((source) => source.state === 'error');
        if (failure?.state === 'error') return { state: 'error', error: new Error(failure.error) };
      }
      return result;
    },
  );
  const sources: PaymentSources =
    drawer.source.state === 'ready'
      ? drawer.source.data
      : drawer.source.state === 'error'
        ? { payment: drawer.source, fraud: drawer.source, audit: drawer.source }
        : loadingSources;
  const dialogOpen = paymentId !== null || selectedAudit !== null;
  useEffect(() => {
    const handleKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setPaymentId(null);
        setSelectedAudit(null);
      }
      if ((event.metaKey || event.ctrlKey) && event.key === 'k' && !dialogOpen) {
        event.preventDefault();
        setPage('payments');
        requestAnimationFrame(() => searchRef.current?.focus());
      }
      if (event.key === 'Tab' && dialogOpen) {
        const dialog = document.querySelector('[role="dialog"]');
        const focusable = dialog?.querySelectorAll<HTMLElement>(
          'button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), summary, [tabindex="0"]',
        );
        if (!focusable?.length) return;
        const first = focusable[0],
          last = focusable[focusable.length - 1];
        if (
          event.shiftKey &&
          (document.activeElement === first || !dialog?.contains(document.activeElement))
        ) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first.focus();
        }
      }
    };
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, [dialogOpen]);
  useEffect(() => {
    if (!dialogOpen) return;
    const previousOverflow = document.body.style.overflow;
    const previousFocus = document.activeElement as HTMLElement | null;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previousOverflow;
      previousFocus?.focus();
    };
  }, [dialogOpen]);
  function go(next: Page) {
    setPage(next);
    setSearch('');
    setPageNumber(1);
  }
  function onCreate(draft: CreatePaymentDraft) {
    void platform.run('Создаём платёж', async () => {
      const created = await createPayment(draft);
      platform.reload();
      setPaymentId(created.id);
      platform.setToast({
        message: `${created.reference} · ${created.status === 'REJECTED' ? 'платёж отклонён' : 'платёж создан'}`,
        error: created.status === 'REJECTED',
      });
    });
  }
  const filtered = paymentPage(platform.list?.payments ?? [], { search, status, page: pageNumber });
  return (
    <div className="app-shell">
      <header className="app-header" inert={dialogOpen}>
        <button
          className="brand"
          onClick={() => go('overview')}
          aria-label="Payment Platform · главная"
        >
          <span className="brand-symbol">p_</span>
          <span>
            Payment Platform<small>SOROKIN SCHOOL</small>
          </span>
        </button>
        <nav aria-label="Основная навигация">
          {navigation.map((item) => (
            <button
              key={item.id}
              className={page === item.id ? 'active' : ''}
              aria-current={page === item.id ? 'page' : undefined}
              onClick={() => go(item.id)}
            >
              <item.icon size={16} />
              {item.label}
            </button>
          ))}
        </nav>
      </header>
      <main className="page-content" inert={dialogOpen}>
        <div className="page-heading">
          <div>
            <span className="eyebrow">
              PAYMENT PLATFORM<span className="heading-slash">/</span>
              {navigation.find((item) => item.id === page)?.label}
            </span>
            <h1>{headings[page].title}</h1>
            <p>{headings[page].description}</p>
          </div>
          {page !== 'overview' && (
            <RefreshControl state={platform.refreshState} onRefresh={platform.refresh} />
          )}
        </div>
        {page === 'overview' && (
          <Overview platform={platform} onCreate={onCreate} onPayment={setPaymentId} onPage={go} />
        )}
        {page === 'payments' && (
          <section className="panel full-table">
            <div className="panel-heading">
              <div className="search-field">
                <Search size={16} />
                <input
                  ref={searchRef}
                  aria-label="Поиск платежей"
                  placeholder="Платёж, владелец, описание…"
                  value={search}
                  onChange={(event) => {
                    setSearch(event.target.value);
                    setPageNumber(1);
                  }}
                />
                <kbd>⌘K</kbd>
              </div>
              <div className="table-controls">
                <select
                  aria-label="Фильтр статуса"
                  value={status}
                  onChange={(event) => {
                    setStatus(event.target.value);
                    setPageNumber(1);
                  }}
                >
                  <option value="all">Все статусы</option>
                  <option value="NEW">Новый</option>
                  <option value="CONFIRMED">Подтверждён</option>
                  <option value="REJECTED">Отклонён</option>
                </select>
              </div>
            </div>
            <SourceMessage source={platform.listSource} onRetry={platform.reload} />
            {platform.listSource.state === 'ready' && (
              <>
                <PaymentTable
                  payments={filtered.rows}
                  onSelect={setPaymentId}
                  emptyText={
                    search || status !== 'all' ? 'По этому запросу ничего не найдено' : undefined
                  }
                />
                <div className="panel-footer pagination">
                  <span>
                    {filtered.total
                      ? `${(filtered.page - 1) * 20 + 1}–${Math.min(filtered.page * 20, filtered.total)} из ${filtered.total}`
                      : '0 платежей'}
                  </span>
                  <div>
                    <button
                      className="icon-button"
                      aria-label="Предыдущая страница"
                      disabled={filtered.page === 1}
                      onClick={() => setPageNumber(filtered.page - 1)}
                    >
                      <ArrowLeft size={15} />
                    </button>
                    <span className="mono">
                      {filtered.page} / {filtered.pages}
                    </span>
                    <button
                      className="icon-button"
                      aria-label="Следующая страница"
                      disabled={filtered.page === filtered.pages}
                      onClick={() => setPageNumber(filtered.page + 1)}
                    >
                      <ArrowRight size={15} />
                    </button>
                  </div>
                </div>
              </>
            )}
          </section>
        )}
        {page === 'services' && <Services platform={platform} />}
        {page === 'api' && (
          <ApiPage
            platform={platform}
            openService={openService}
            onToggle={(service) => setOpenService(openService === service ? null : service)}
          />
        )}
        {page === 'audit' && (
          <AuditPage
            source={platform.entriesSource}
            onRetry={platform.refresh}
            onSelect={setSelectedAudit}
          />
        )}
      </main>
      {platform.busy && (
        <div className="busy-bar" role="status">
          <RefreshCw className="spin" size={15} />
          {platform.busy}
        </div>
      )}
      {platform.toast && (
        <div role="alert" className={`toast ${platform.toast.error ? 'error' : ''}`}>
          <span>{platform.toast.message}</span>
          <button
            className="icon-button"
            aria-label="Закрыть уведомление"
            onClick={() => platform.setToast(null)}
          >
            <X size={16} />
          </button>
        </div>
      )}
      {paymentId !== null && (
        <PaymentDrawer
          key={paymentId}
          paymentId={paymentId}
          sources={sources}
          busy={!!platform.busy}
          refreshState={drawerRefresh.refreshState}
          onClose={() => setPaymentId(null)}
          onRead={drawerRefresh.refresh}
          onConfirm={() => {
            void platform.run('Подтверждаем платёж', async () => {
              await post<Payment>(`/api/payments/${paymentId}/confirm`);
              drawer.refresh();
              platform.reload();
            });
          }}
        />
      )}
      {selectedAudit && (
        <AuditDetails
          entry={selectedAudit}
          onClose={() => setSelectedAudit(null)}
          onPayment={(id) => {
            setSelectedAudit(null);
            setPaymentId(id);
          }}
        />
      )}
    </div>
  );
}

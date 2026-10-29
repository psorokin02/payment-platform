import { useState } from 'react';
import { dateTime, Empty, SourceMessage } from './components';
import type { LogSources, ServiceId } from './types';

const names: Record<ServiceId, string> = {
  payment: 'Payment',
  antifraud: 'Anti-Fraud',
  audit: 'Audit',
};
const loading: LogSources = {
  payment: { state: 'loading' },
  antifraud: { state: 'loading' },
  audit: { state: 'loading' },
};
type LogFilter = ServiceId | 'all';
const filters: { id: LogFilter; label: string }[] = [
  { id: 'all', label: 'Все сервисы' },
  { id: 'payment', label: names.payment },
  { id: 'antifraud', label: names.antifraud },
  { id: 'audit', label: names.audit },
];

export default function ServiceLogs({
  sources = loading,
  onRetry,
}: {
  sources?: LogSources;
  onRetry: () => void;
}) {
  const [filter, setFilter] = useState<LogFilter>('all');
  return (
    <ServiceLogView sources={sources} filter={filter} onFilter={setFilter} onRetry={onRetry} />
  );
}

export function ServiceLogView({
  sources,
  filter,
  onFilter,
  onRetry,
}: {
  sources: LogSources;
  filter: LogFilter;
  onFilter: (filter: LogFilter) => void;
  onRetry: () => void;
}) {
  const selected = (Object.keys(names) as ServiceId[]).filter(
    (service) => filter === 'all' || service === filter,
  );
  const entries = selected
    .flatMap((service) => {
      const source = sources[service];
      return source.state === 'ready'
        ? source.data.entries.map((entry) => ({ ...entry, service }))
        : [];
    })
    .sort((a, b) => Date.parse(b.timestamp) - Date.parse(a.timestamp) || b.sequence - a.sequence);
  return (
    <section className="panel service-logs">
      <div className="panel-heading">
        <div>
          <h2>Логи сервисов</h2>
          <p className="panel-caption">Последние 200 сообщений каждого сервиса. Новые — сверху.</p>
        </div>
        <div className="log-filters" role="group" aria-label="Фильтр логов по сервису">
          {filters.map(({ id, label }) => (
            <button
              type="button"
              key={id}
              aria-pressed={filter === id}
              className={filter === id ? 'active' : ''}
              onClick={() => onFilter(id)}
            >
              {label}
            </button>
          ))}
        </div>
      </div>
      {selected.map(
        (service) =>
          sources[service].state !== 'ready' && (
            <div className="log-source-state" key={service}>
              <strong>{names[service]}</strong>
              <SourceMessage source={sources[service]} onRetry={onRetry} />
            </div>
          ),
      )}
      {!!entries.length && (
        <div className="log-output" tabIndex={0} aria-label="Сообщения логов сервисов">
          {entries.map((entry) => (
            <article
              className={`log-entry log-${entry.level.toLowerCase()}`}
              key={`${entry.service}:${entry.sequence}`}
            >
              <div className="log-metadata mono">
                <time dateTime={entry.timestamp}>{dateTime(entry.timestamp)}</time>
                <strong>{names[entry.service]}</strong>
                <span>{entry.level}</span>
                <small>{entry.logger}</small>
              </div>
              <pre>{entry.message}</pre>
            </article>
          ))}
        </div>
      )}
      {!entries.length && selected.every((service) => sources[service].state === 'ready') && (
        <Empty text="Сообщений в логах пока нет" />
      )}
    </section>
  );
}

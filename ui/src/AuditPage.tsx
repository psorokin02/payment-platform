import { Badge, dateTime, Empty, money, SourceMessage } from './components';
import type { AuditEntry, Source } from './types';
export default function AuditPage({
  source,
  onRetry,
  onSelect,
}: {
  source: Source<AuditEntry[]>;
  onRetry: () => void;
  onSelect: (entry: AuditEntry) => void;
}) {
  return (
    <section className="panel audit-panel">
      <div className="panel-heading">
        <div>
          <h2>Обработанные события</h2>
          <p className="panel-caption">Последние 100 записей, сохранённых сервисом Audit.</p>
        </div>
        <span className="source-tag">
          <i className={source.state === 'ready' ? 'live-dot' : 'warn-dot'} />
          Audit DB
        </span>
      </div>
      <SourceMessage source={source} onRetry={onRetry} />
      {source.state === 'ready' &&
        (source.data.length ? (
          <div
            className="table-scroll audit-table-scroll"
            tabIndex={0}
            aria-label="Таблица событий, прокручивается по горизонтали"
          >
            <table className="audit-table" aria-label="Обработанные события Audit">
              <thead>
                <tr>
                  <th scope="col">Событие</th>
                  <th scope="col">Платёж и владелец</th>
                  <th scope="col">Сохранено</th>
                  <th scope="col" className="audit-amount">
                    Сумма
                  </th>
                  <th scope="col">Статус</th>
                </tr>
              </thead>
              <tbody>
                {source.data.map((entry) => (
                  <tr
                    key={entry.id}
                    tabIndex={0}
                    aria-label={`Открыть событие ${entry.type} платежа ${entry.paymentId}`}
                    onClick={() => onSelect(entry)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault();
                        onSelect(entry);
                      }
                    }}
                  >
                    <td className="audit-event-name">{entry.type}</td>
                    <td className="audit-payment">
                      <strong>PAY-{String(entry.paymentId).padStart(4, '0')}</strong>
                      <small>{entry.customer}</small>
                    </td>
                    <td className="audit-time">
                      <time dateTime={entry.receivedAt}>{dateTime(entry.receivedAt)}</time>
                    </td>
                    <td className="audit-amount mono">{money(entry.amount)}</td>
                    <td>
                      {entry.status ? (
                        <Badge status={entry.status} />
                      ) : (
                        <span className="secondary" aria-label="Статус не сохранён">
                          —
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty text="Сохранённых событий пока нет" />
        ))}
    </section>
  );
}

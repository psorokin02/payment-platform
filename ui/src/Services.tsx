import { Radio, ShieldCheck, Workflow } from 'lucide-react';
import { SourceMessage } from './components';
import { AntifraudControls, AuditControls, PaymentControls } from './ServiceControls';
import ServiceLogs from './ServiceLogs';
import type { Platform } from './usePlatform';
import { antifraudAvailability } from './antifraudAvailability';
export const serviceDefinitions = [
  {
    id: 'payment',
    name: 'Payment',
    port: 8081,
    description: 'Платежи и их состояние',
    icon: Workflow,
  },
  {
    id: 'antifraud',
    name: 'Antifraud',
    port: 8082,
    description: 'Проверки и сохранённые решения',
    icon: ShieldCheck,
  },
  {
    id: 'audit',
    name: 'Audit',
    port: 8083,
    description: 'Обработанные события платежей',
    icon: Radio,
  },
] as const;
export type ServiceId = (typeof serviceDefinitions)[number]['id'];
export default function Services({ platform }: { platform: Platform }) {
  return (
    <div className="services-stack">
      <div className="services-grid">
        {serviceDefinitions.map((service) => {
          const source =
            service.id === 'payment'
              ? platform.paymentSource
              : service.id === 'antifraud'
                ? platform.antifraudSource
                : platform.auditSource;
          const defaultHealth = {
            state:
              source.state === 'ready'
                ? 'available'
                : source.state === 'loading'
                  ? 'loading'
                  : 'unavailable',
            text:
              source.state === 'ready'
                ? 'HTTP доступен'
                : source.state === 'loading'
                  ? 'Проверяем HTTP…'
                  : 'HTTP недоступен',
          };
          const httpHealth =
            service.id === 'antifraud' && platform.antifraudSource.state === 'ready'
              ? antifraudAvailability(
                  platform.antifraudSource.data,
                  platform.paymentSource.state === 'ready'
                    ? platform.paymentSource.data.antifraudTimeoutMs
                    : undefined,
                )
              : defaultHealth;
          return (
            <section className="panel service-card" key={service.id}>
              <div className="service-card-top">
                <span className={`service-icon service-${service.id}`}>
                  <service.icon size={21} />
                </span>
                <span className="port mono">:{service.port}</span>
              </div>
              <h2>{service.name}</h2>
              <p>{service.description}</p>
              <div className={`http-health ${httpHealth.state}`}>
                <i
                  className={
                    httpHealth.state === 'available'
                      ? 'live-dot'
                      : httpHealth.state === 'loading' || httpHealth.state === 'warning'
                        ? 'warn-dot'
                        : 'offline-dot'
                  }
                />
                {httpHealth.text}
              </div>
              <SourceMessage source={source} onRetry={platform.refresh} />
              {service.id === 'payment' && platform.paymentSource.state === 'ready' && (
                <PaymentControls
                  settings={platform.paymentSource.data.settings}
                  circuitState={platform.paymentSource.data.circuitState}
                  onSaved={() => platform.reloadService('payment')}
                />
              )}
              {service.id === 'antifraud' && platform.antifraudSource.state === 'ready' && (
                <AntifraudControls
                  mode={platform.antifraudSource.data.mode}
                  onSaved={() => void platform.reloadService('antifraud')}
                />
              )}
              {service.id === 'audit' && platform.auditSource.state === 'ready' && (
                <>
                  <div className="consumer-state">
                    <span className="eyebrow">CONSUMER</span>
                    <strong
                      className={
                        platform.auditSource.data.paused || !platform.auditSource.data.assigned
                          ? 'warning-text'
                          : 'accent'
                      }
                    >
                      {platform.auditSource.data.assigned === 0
                        ? 'Kafka: нет подключения'
                        : platform.auditSource.data.paused
                          ? platform.auditSource.data.pauseApplied
                            ? 'Kafka: пауза'
                            : 'Kafka: переходит на паузу'
                          : platform.auditSource.data.pauseApplied
                            ? 'Kafka: возобновляется'
                            : 'Kafka: подключён'}
                    </strong>
                  </div>
                  <AuditControls
                    status={platform.auditSource.data}
                    onSaved={() => platform.reloadService('audit')}
                  />
                </>
              )}
            </section>
          );
        })}
      </div>
      <ServiceLogs sources={platform.logSources} onRetry={platform.refresh} />
    </div>
  );
}

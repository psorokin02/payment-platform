import { ChevronDown, ExternalLink, RefreshCw } from 'lucide-react';
import { SourceMessage } from './components';
import { serviceDefinitions, type ServiceId } from './Services';
import type { Platform } from './usePlatform';
export default function ApiPage({
  platform,
  openService,
  onToggle,
}: {
  platform: Platform;
  openService: ServiceId | null;
  onToggle: (service: ServiceId) => void;
}) {
  return (
    <div className="api-stack">
      {serviceDefinitions.map((service) => {
        const opened = openService === service.id;
        const source =
          service.id === 'payment'
            ? platform.paymentSource
            : service.id === 'antifraud'
              ? platform.antifraudSource
              : platform.auditSource;
        const url = `http://127.0.0.1:${service.port}/swagger-dark/index.html`;
        return (
          <section className={`panel api-service ${opened ? 'expanded' : ''}`} key={service.id}>
            <button
              className="api-service-toggle"
              onClick={() => onToggle(service.id)}
              aria-expanded={opened}
              aria-controls={`swagger-${service.id}`}
            >
              <span className={`service-icon service-${service.id}`}>
                <service.icon size={19} />
              </span>
              <span>
                <strong>{service.name}</strong>
                <small>{service.description}</small>
              </span>
              <span className="api-port mono">:{service.port}</span>
              <ChevronDown size={18} />
            </button>
            {opened && (
              <div id={`swagger-${service.id}`} className="swagger-content">
                <div className="swagger-toolbar">
                  <span>Swagger UI · {service.name}</span>
                  <div>
                    <button
                      className="text-button"
                      onClick={platform.refresh}
                      disabled={platform.refreshState?.state === 'refreshing'}
                    >
                      <RefreshCw
                        size={13}
                        className={platform.refreshState?.state === 'refreshing' ? 'spin' : ''}
                      />
                      {platform.refreshState?.state === 'refreshing'
                        ? 'Проверяем…'
                        : 'Проверить сервис'}
                    </button>
                    <a className="text-button" href={url} target="_blank" rel="noreferrer">
                      В новом окне
                      <ExternalLink size={13} />
                    </a>
                  </div>
                </div>
                <SourceMessage source={source} onRetry={platform.refresh} />
                <iframe title={`Swagger ${service.name}`} src={url} className="swagger-frame" />
              </div>
            )}
          </section>
        );
      })}
      <p className="api-note">
        Запросы выполняются в сервисе через Swagger. После создания платежа откройте «Обзор» или
        «Платежи» — список обновится автоматически.
      </p>
    </div>
  );
}

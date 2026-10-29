import type { AntifraudStatus } from './types';

type Availability = { state: 'available' | 'warning' | 'unavailable'; text: string };
const seconds = (milliseconds: number) =>
  `${new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 }).format(milliseconds / 1000)} с`;

/** GET статуса может отвечать 200, когда бизнес-проверки намеренно возвращают 503/timeout. */
export function antifraudAvailability(status: AntifraudStatus, timeoutMs?: number): Availability {
  switch (status.mode) {
    case 'error':
      return { state: 'unavailable', text: 'HTTP недоступен (всегда возвращает 503)' };
    case 'reject':
      return { state: 'available', text: 'HTTP доступен (отклоняет все платежи)' };
    case 'slow': {
      const delay = status.slowDelayMs;
      const response =
        delay === undefined ? 'после задержки вернёт 503' : `через ${seconds(delay)} вернёт 503`;
      const timeout =
        timeoutMs !== undefined && timeoutMs > 0 ? `, таймаут: ${seconds(timeoutMs)}` : '';
      return { state: 'unavailable', text: `HTTP недоступен (${response}${timeout})` };
    }
    default:
      return { state: 'available', text: 'HTTP доступен (обычный ответ)' };
  }
}

import { useEffect, useReducer, useRef, useState } from 'react';
import { errorMessage, saveAntifraudMode, savePaymentSettings, saveAuditSettings } from './api';
import type { AntifraudMode, AuditStatus, AuditSettings, Settings } from './types';
import type { RefreshResult, RefreshState } from './consoleData';

type PaymentDraft = { settings: Settings; protected: boolean };
type PaymentDraftAction =
  | { type: 'remote' | 'saved'; settings: Settings }
  | { type: 'saving' }
  | { type: 'edit'; key: keyof Settings; value: Settings[keyof Settings] };

export function paymentDraftReducer(state: PaymentDraft, action: PaymentDraftAction): PaymentDraft {
  if (action.type === 'saving') return { ...state, protected: true };
  if (action.type === 'edit')
    return { settings: { ...state.settings, [action.key]: action.value }, protected: true };
  if (action.type === 'remote' && state.protected) return state;
  return {
    settings: { ...action.settings, eventCopies: action.settings.eventCopies ?? 1 },
    protected: false,
  };
}

export async function savePaymentDraft(
  settings: Settings,
  onSaved: () => Promise<RefreshResult>,
  dispatch: (action: PaymentDraftAction) => void,
  signal?: AbortSignal,
) {
  dispatch({ type: 'saving' });
  const saved = await savePaymentSettings(settings, signal);
  const reloaded = await onSaved();
  signal?.throwIfAborted();
  if (reloaded.state === 'error') throw reloaded.error;
  if (reloaded.state !== 'ready') throw new Error('Обновление настроек Payment не завершено.');
  dispatch({ type: 'saved', settings: saved.settings });
  return saved;
}

function useServiceMutation() {
  const [state, setState] = useState<RefreshState>({ state: 'idle' });
  const active = useRef<AbortController | null>(null);
  const mounted = useRef(false);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      active.current?.abort();
    };
  }, []);
  async function run<T>(save: (signal: AbortSignal) => Promise<T>): Promise<T | undefined> {
    if (active.current) return;
    const controller = new AbortController();
    active.current = controller;
    setState({ state: 'refreshing' });
    try {
      const saved = await save(controller.signal);
      if (mounted.current && !controller.signal.aborted) {
        setState({ state: 'success' });
        return saved;
      }
    } catch (error) {
      if (mounted.current && !controller.signal.aborted)
        setState({ state: 'error', message: errorMessage(error) });
    } finally {
      active.current = null;
    }
  }
  function clearFeedback() {
    if (!active.current) setState({ state: 'idle' });
  }
  return { state, run, clearFeedback, busy: state.state === 'refreshing' };
}

function SaveFeedback({ state }: { state: RefreshState }) {
  return (
    <p role="status" className={`save-feedback ${state.state}`}>
      {state.state === 'refreshing' && 'Сохраняем и проверяем…'}
      {state.state === 'success' && 'Изменения подтверждены сервисом.'}
      {state.state === 'error' && `Не удалось подтвердить изменения: ${state.message}`}
    </p>
  );
}

export function PaymentControls({
  settings,
  circuitState,
  onSaved,
}: {
  settings: Settings;
  circuitState: string;
  onSaved: () => Promise<RefreshResult>;
}) {
  const [state, dispatch] = useReducer(paymentDraftReducer, {
    settings: { ...settings, eventCopies: settings.eventCopies ?? 1 },
    protected: false,
  });
  const draft = state.settings;
  const mutation = useServiceMutation();
  useEffect(() => {
    dispatch({ type: 'remote', settings });
  }, [settings]);
  function update<K extends keyof Settings>(key: K, value: Settings[K]) {
    dispatch({ type: 'edit', key, value });
  }
  return (
    <details className="service-controls">
      <summary>Настройки Payment</summary>
      <form
        onChange={mutation.clearFeedback}
        onSubmit={(event) => {
          event.preventDefault();
          void mutation.run((signal) => savePaymentDraft(draft, onSaved, dispatch, signal));
        }}
      >
        <fieldset disabled={mutation.busy}>
          <label>
            Чтение платежей
            <select
              aria-label="Чтение платежей"
              value={draft.fetchMode}
              onChange={(event) => update('fetchMode', event.target.value as Settings['fetchMode'])}
            >
              <option value="nplus1">N+1 · отдельные запросы</option>
              <option value="join">JOIN · один запрос</option>
            </select>
          </label>
          <label>
            Кэш Redis
            <select
              aria-label="Кэш Redis"
              value={draft.cacheEnabled ? 'on' : 'off'}
              onChange={(event) => update('cacheEnabled', event.target.value === 'on')}
            >
              <option value="on">Включён</option>
              <option value="off">Выключен</option>
            </select>
          </label>
          <label>
            Инвалидация кэша
            <select
              aria-label="Инвалидация кэша"
              value={draft.cacheInvalidationEnabled ? 'on' : 'off'}
              onChange={(event) => update('cacheInvalidationEnabled', event.target.value === 'on')}
            >
              <option value="on">Включена</option>
              <option value="off">Выключена</option>
            </select>
          </label>
          <label>
            HTTP client
            <select
              aria-label="HTTP client"
              value={draft.httpMode}
              onChange={(event) => update('httpMode', event.target.value as Settings['httpMode'])}
            >
              <option value="timeout">Timeout · таймаут</option>
              <option value="retry">Retry · повторные запросы</option>
              <option value="breaker">Circuit breaker · защита от сбоев</option>
            </select>
          </label>
          <label>
            Публикаций одного события
            <select
              aria-label="Публикаций одного события"
              value={draft.eventCopies}
              onChange={(event) => update('eventCopies', Number(event.target.value))}
            >
              <option value={1}>1 · обычная публикация</option>
              <option value={2}>2</option>
              <option value={3}>3</option>
              <option value={4}>4</option>
              <option value={5}>5 · проверка идемпотентности</option>
            </select>
          </label>
          <p className="control-note">Circuit breaker: {circuitState}</p>
          <button className="button wide" type="submit">
            Сохранить настройки
          </button>
        </fieldset>
        <SaveFeedback state={mutation.state} />
      </form>
    </details>
  );
}

export function AntifraudControls({ mode, onSaved }: { mode: AntifraudMode; onSaved: () => void }) {
  const [draft, setDraft] = useState(mode);
  const dirty = useRef(false);
  const mutation = useServiceMutation();
  useEffect(() => {
    if (!dirty.current) setDraft(mode);
  }, [mode]);
  return (
    <details className="service-controls">
      <summary>Настройки Antifraud</summary>
      <form
        onChange={mutation.clearFeedback}
        onSubmit={(event) => {
          event.preventDefault();
          void mutation
            .run((signal) => saveAntifraudMode(draft, signal))
            .then((saved) => {
              if (saved) {
                dirty.current = false;
                setDraft(saved.mode);
                onSaved();
              }
            });
        }}
      >
        <fieldset disabled={mutation.busy}>
          <label>
            Режим ответа
            <select
              aria-label="Режим ответа Antifraud"
              value={draft}
              onChange={(event) => {
                dirty.current = true;
                setDraft(event.target.value as AntifraudMode);
              }}
            >
              <option value="normal">Обычный ответ</option>
              <option value="slow">60 секунд, затем HTTP 503</option>
              <option value="error">Ошибка HTTP 503</option>
              <option value="reject">Отклонять платежи</option>
            </select>
          </label>
          <button className="button wide" type="submit">
            Сохранить настройки
          </button>
        </fieldset>
        <SaveFeedback state={mutation.state} />
      </form>
    </details>
  );
}

type AuditDraft = { settings: AuditSettings; protected: boolean };
type AuditDraftAction =
  | { type: 'remote'; status: AuditStatus }
  | { type: 'saved'; settings: AuditSettings }
  | { type: 'saving' }
  | { type: 'edit'; key: keyof AuditSettings; value: AuditSettings[keyof AuditSettings] };

export function auditDraftReducer(state: AuditDraft, action: AuditDraftAction): AuditDraft {
  if (action.type === 'saving') return { ...state, protected: true };
  if (action.type === 'edit')
    return { settings: { ...state.settings, [action.key]: action.value }, protected: true };
  if (action.type === 'remote') {
    if (state.protected) return state;
    return {
      settings: {
        ...state.settings,
        idempotencyEnabled: action.status.idempotencyEnabled ?? true,
        paused: action.status.paused,
      },
      protected: false,
    };
  }
  return { settings: action.settings, protected: false };
}

export function AuditControls({
  status,
  onSaved,
}: {
  status: AuditStatus;
  onSaved: () => Promise<RefreshResult>;
}) {
  const [state, dispatch] = useReducer(auditDraftReducer, {
    settings: {
      idempotencyEnabled: status.idempotencyEnabled ?? true,
      paused: status.paused,
    },
    protected: false,
  });
  useEffect(() => {
    dispatch({ type: 'remote', status });
  }, [status]);
  const mutation = useServiceMutation();
  const draft = state.settings;
  function update<K extends keyof AuditSettings>(key: K, value: AuditSettings[K]) {
    dispatch({ type: 'edit', key, value });
  }
  async function save() {
    dispatch({ type: 'saving' });
    await mutation.run(async (signal) => {
      const saved = await saveAuditSettings(draft, signal);
      const refreshed = await onSaved();
      signal.throwIfAborted();
      if (refreshed.state === 'error') throw refreshed.error;
      if (refreshed.state !== 'ready') throw new Error('Обновление настроек Audit не завершено.');
      dispatch({
        type: 'saved',
        settings: { ...draft, idempotencyEnabled: saved.idempotencyEnabled, paused: saved.paused },
      });
      return saved;
    });
  }
  return (
    <details className="service-controls">
      <summary>Настройки Audit</summary>
      <form
        onChange={mutation.clearFeedback}
        onSubmit={(event) => {
          event.preventDefault();
          void save();
        }}
      >
        <fieldset disabled={mutation.busy}>
          <label>
            Защита от дублей
            <select
              aria-label="Защита от дублей"
              value={draft.idempotencyEnabled ? 'on' : 'off'}
              onChange={(event) => update('idempotencyEnabled', event.target.value === 'on')}
            >
              <option value="on">Включена</option>
              <option value="off">Выключена</option>
            </select>
          </label>
          <label>
            Обработка Kafka
            <select
              aria-label="Обработка Kafka"
              value={draft.paused ? 'paused' : 'running'}
              onChange={(event) => update('paused', event.target.value === 'paused')}
            >
              <option value="running">Включена</option>
              <option value="paused">Остановлена</option>
            </select>
          </label>
          <button className="button wide" type="submit">
            Сохранить настройки
          </button>
          <p className="control-note">Изменения применяются по кнопке.</p>
        </fieldset>
        <SaveFeedback state={mutation.state} />
      </form>
    </details>
  );
}

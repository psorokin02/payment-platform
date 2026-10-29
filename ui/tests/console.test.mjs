import assert from 'node:assert/strict';
import { afterEach, mock, test } from 'node:test';
import { build } from 'esbuild';
import { renderToStaticMarkup } from 'react-dom/server';
import React from 'react';

// Bundle real TSX components for server rendering, without replacement UI.
async function sourceModule(path) {
  const result = await build({
    entryPoints: [path],
    bundle: true,
    write: false,
    platform: 'node',
    format: 'esm',
    packages: 'external',
    jsx: 'automatic',
  });
  // File URLs keep external React imports resolvable without writing build output.
  const { pathToFileURL } = await import('node:url');
  const { writeFile, unlink } = await import('node:fs/promises');
  const file = new URL(
    `../.console-test-${process.pid}-${Math.random().toString(36).slice(2)}.mjs`,
    import.meta.url,
  );
  await writeFile(file, result.outputFiles[0].text);
  try {
    return await import(pathToFileURL(file.pathname).href);
  } finally {
    await unlink(file);
  }
}
const components = await sourceModule('src/components.tsx');
const network = await sourceModule('src/api.ts');
const overview = await sourceModule('src/Overview.tsx');
const payment = (id, extra = {}) => ({
  id,
  reference: `PAY-${id}`,
  customer: 'Анна',
  email: 'anna@example.com',
  customerId: 1,
  amount: 4990,
  currency: 'RUB',
  status: 'NEW',
  description: 'Курс',
  createdAt: '2026-10-26T10:00:00Z',
  paymentType: 'CARD',
  rejectionReason: null,
  antifraudRequestId: null,
  ...extra,
});
const readResult = (value) => ({
  payment: value,
  source: 'DB',
});

afterEach(() => mock.restoreAll());

test('full payment table renders records after the twentieth row', () => {
  const html = renderToStaticMarkup(
    React.createElement(components.PaymentTable, {
      payments: Array.from({ length: 25 }, (_, i) => payment(i + 1)),
      onSelect() {},
    }),
  );
  assert.match(html, /PAY-25/);
});

test('overview renders an inline form with four real payment method values', () => {
  const html = renderToStaticMarkup(
    React.createElement(overview.default, {
      platform: {
        list: null,
        customers: [{ id: 1, name: 'Анна', email: 'anna@example.com' }],
        busy: '',
        events: [],
        online: [true, true, true],
      },
      onCreate() {},
      onPayment() {},
      onPage() {},
    }),
  );
  assert.match(html, /<form/);
  for (const type of ['CARD', 'QR', 'INSTALLMENT', 'RECURRENT'])
    assert.match(html, new RegExp(`value="${type}"`));
});

test('saved payment sources read per-payment audit and skip fraud for legacy rows', async () => {
  assert.equal(typeof network.fetchPaymentSources, 'function');
  const paths = [];
  mock.method(globalThis, 'fetch', async (path) => {
    paths.push(path);
    if (path === '/api/payments/42') return Response.json(readResult(payment(42)));
    if (path === '/api/audit/entries?paymentId=42') return Response.json([]);
    throw new Error(`Unexpected request ${path}`);
  });
  const result = await network.fetchPaymentSources(42);
  assert.equal(result.payment.state, 'ready');
  assert.equal(result.fraud.state, 'missing');
  assert.equal(result.audit.state, 'ready');
  assert.deepEqual(result.audit.data, []);
  assert.deepEqual(paths.sort(), ['/api/audit/entries?paymentId=42', '/api/payments/42']);
});

test('saved fraud is read by linked requestId and failure remains distinct from absent data', async () => {
  assert.equal(typeof network.fetchPaymentSources, 'function');
  mock.method(globalThis, 'fetch', async (path) => {
    if (path === '/api/payments/7')
      return Response.json(readResult(payment(7, { antifraudRequestId: 'check-7' })));
    if (path === '/api/audit/entries?paymentId=7') return Response.json([]);
    if (path === '/api/antifraud/checks/check-7')
      return Response.json({ message: 'Antifraud offline' }, { status: 503 });
    throw new Error(`Unexpected request ${path}`);
  });
  const result = await network.fetchPaymentSources(7);
  assert.equal(result.payment.state, 'ready');
  assert.equal(result.fraud.state, 'error');
  assert.match(result.fraud.error, /Antifraud offline/);
  assert.equal(result.audit.state, 'ready');
});

test('creation sends the selected payment type to the backend', async () => {
  assert.equal(typeof network.createPayment, 'function');
  let sent;
  mock.method(globalThis, 'fetch', async (path, options) => {
    assert.equal(path, '/api/payments');
    assert.equal(options.method, 'POST');
    sent = JSON.parse(options.body);
    return Response.json(payment(9, { paymentType: 'QR' }));
  });
  const created = await network.createPayment({
    customerId: 1,
    amount: 100001,
    description: 'Курс',
    paymentType: 'QR',
  });
  assert.deepEqual(sent, { customerId: 1, amount: 100001, description: 'Курс', paymentType: 'QR' });
  assert.equal(created.paymentType, 'QR');
});

const data = await sourceModule('src/consoleData.ts');

test('search and status apply before pagination, including rows beyond twenty', () => {
  assert.equal(typeof data.paymentPage, 'function');
  const rows = Array.from({ length: 45 }, (_, i) =>
    payment(i + 1, { status: i >= 20 ? 'REJECTED' : 'NEW' }),
  );
  const result = data.paymentPage(rows, { search: '', status: 'REJECTED', page: 2, pageSize: 20 });
  assert.equal(result.total, 25);
  assert.equal(result.pages, 2);
  assert.deepEqual(
    result.rows.map((row) => row.id),
    [41, 42, 43, 44, 45],
  );
  assert.deepEqual(
    data
      .paymentPage(rows, { search: 'PAY-43', status: 'REJECTED', page: 1, pageSize: 20 })
      .rows.map((row) => row.id),
    [43],
  );
});

test('API screen never schedules list reads, overview and payments do', () => {
  assert.equal(typeof data.readsForPage, 'function');
  assert.equal(data.readsForPage('api').payments, false);
  assert.equal(data.readsForPage('api').health, true);
  assert.equal(data.readsForPage('overview').payments, true);
  assert.equal(data.readsForPage('payments').payments, true);
  assert.equal(data.readsForPage('audit').audit, true);
});

test('polling serializes refreshes and ignores responses after the drawer is closed', async () => {
  assert.equal(typeof data.startPolling, 'function');
  let resolveRead;
  let active = 0;
  let maxActive = 0;
  let calls = 0;
  const seen = [];
  const poll = data.startPolling(
    async () => {
      calls++;
      active++;
      maxActive = Math.max(active, maxActive);
      await new Promise((resolve) => {
        resolveRead = resolve;
      });
      active--;
      return calls;
    },
    (value) => seen.push(value),
    { interval: 10000, visible: () => true },
  );
  poll.refresh();
  poll.refresh();
  assert.equal(calls, 1);
  resolveRead();
  await new Promise((resolve) => setTimeout(resolve, 0));
  assert.equal(calls, 2);
  assert.equal(maxActive, 1);
  poll.stop();
  resolveRead();
  await new Promise((resolve) => setTimeout(resolve, 0));
  assert.deepEqual(seen, [1]);
});

const services = await sourceModule('src/Services.tsx');
const apiPage = await sourceModule('src/ApiPage.tsx');
const drawer = await sourceModule('src/PaymentDrawer.tsx');
const ready = (data) => ({ state: 'ready', data });
const health = {
  paymentSource: ready({
    settings: {
      fetchMode: 'join',
      cacheEnabled: false,
      cacheInvalidationEnabled: true,
      httpMode: 'timeout',
      eventCopies: 1,
    },
    circuitState: 'CLOSED',
  }),
  antifraudSource: ready({ mode: 'normal' }),
  auditSource: ready({ paused: true, pauseApplied: true, assigned: 1 }),
  refresh() {},
};

test('service page distinguishes live HTTP from paused and disconnected Kafka', () => {
  const paused = renderToStaticMarkup(React.createElement(services.default, { platform: health }));
  assert.match(paused, /HTTP доступен/);
  assert.match(paused, /Kafka: пауза/);
  for (const port of ['8081', '8082', '8083']) assert.match(paused, new RegExp(port));
  const disconnected = renderToStaticMarkup(
    React.createElement(services.default, {
      platform: {
        ...health,
        auditSource: ready({
          paused: false,
          pauseApplied: false,
          assigned: 0,
        }),
      },
    }),
  );
  assert.match(disconnected, /Kafka: нет подключения/);
});

test('API renders one real service Swagger frame and an unavailable health retry', () => {
  const html = renderToStaticMarkup(
    React.createElement(apiPage.default, {
      platform: { ...health, antifraudSource: { state: 'error', error: 'HTTP 503' } },
      openService: 'antifraud',
      onToggle() {},
    }),
  );
  assert.equal((html.match(/<iframe/g) ?? []).length, 1);
  assert.match(html, /src="http:\/\/127\.0\.0\.1:8082\/swagger-dark\/index.html"/);
  assert.match(html, /Повторить/);
  assert.match(html, /HTTP 503/);
});

for (const scenario of [
  { mode: 'error', text: 'HTTP недоступен (всегда возвращает 503)', state: 'unavailable' },
  { mode: 'reject', text: 'HTTP доступен (отклоняет все платежи)', state: 'available' },
  { mode: 'normal', text: 'HTTP доступен (обычный ответ)', state: 'available' },
  {
    mode: 'slow',
    text: 'HTTP недоступен (через 60 с вернёт 503, таймаут: 0,9 с)',
    state: 'unavailable',
  },
]) {
  test(`Antifraud ${scenario.mode} describes the real check mode`, () => {
    const html = renderToStaticMarkup(
      React.createElement(services.default, {
        platform: {
          ...health,
          paymentSource: ready({ ...health.paymentSource.data, antifraudTimeoutMs: 900 }),
          antifraudSource: ready({
            mode: scenario.mode,
            slowDelayMs: 60000,
          }),
        },
      }),
    );
    assert.ok(html.includes(scenario.text), scenario.text);
    assert.ok(html.includes(`http-health ${scenario.state}`));
  });
}

test('slow remains unavailable even if Payment allows the full delay because it ends in 503', () => {
  const html = renderToStaticMarkup(
    React.createElement(services.default, {
      platform: {
        ...health,
        paymentSource: ready({ ...health.paymentSource.data, antifraudTimeoutMs: 90000 }),
        antifraudSource: ready({ mode: 'slow', slowDelayMs: 60000 }),
      },
    }),
  );
  assert.ok(html.includes('HTTP недоступен (через 60 с вернёт 503, таймаут: 90 с)'));
});

for (const httpMode of ['retry', 'breaker']) {
  test(`slow with ${httpMode} stays unavailable because retries also end in 503`, () => {
    const html = renderToStaticMarkup(
      React.createElement(services.default, {
        platform: {
          ...health,
          paymentSource: ready({
            ...health.paymentSource.data,
            settings: { ...health.paymentSource.data.settings, httpMode },
            antifraudTimeoutMs: 900,
          }),
          antifraudSource: ready({ mode: 'slow', slowDelayMs: 60000 }),
        },
      }),
    );
    assert.ok(html.includes('HTTP недоступен (через 60 с вернёт 503, таймаут: 0,9 с)'));
    assert.ok(html.includes('http-health unavailable'));
  });
}

test('payment card keeps confirm but no longer exposes manual Kafka replay', () => {
  const value = payment(8);
  const html = renderToStaticMarkup(
    React.createElement(drawer.default, {
      paymentId: 8,
      busy: false,
      sources: { payment: ready(readResult(value)), fraud: { state: 'missing' }, audit: ready([]) },
      onClose() {},
      onRead() {},
      onConfirm() {},
    }),
  );
  assert.match(html, /Подтвердить платёж/);
  assert.doesNotMatch(html, /Повторить последнее событие/);
  assert.doesNotMatch(html, /Отправится тот же eventId/);
});

test('drawer exposes persisted fraud reason and all scoped audit facts', () => {
  const value = payment(8, {
    status: 'REJECTED',
    rejectionReason: 'AMOUNT_LIMIT',
    antifraudRequestId: 'fraud-8',
  });
  const entries = [
    {
      id: 1,
      eventId: 'event-8',
      paymentId: 8,
      amount: 4990,
      customer: 'Анна',
      type: 'PaymentRejected',
      occurredAt: '2026-10-26T10:00:00Z',
      receivedAt: '2026-10-26T10:00:01Z',
      paymentType: 'CARD',
      status: 'REJECTED',
      reason: 'AMOUNT_LIMIT',
    },
  ];
  const html = renderToStaticMarkup(
    React.createElement(drawer.default, {
      paymentId: 8,
      result: readResult(value),
      events: [],
      sources: {
        payment: ready(readResult(value)),
        fraud: ready({
          requestId: 'fraud-8',
          amount: 4990,
          paymentType: 'CARD',
          approved: false,
          reason: 'AMOUNT_LIMIT',
          checkedAt: '2026-10-26T10:00:00Z',
        }),
        audit: ready(entries),
      },
      onClose() {},
      onRead() {},
      onConfirm() {},
    }),
  );
  assert.match(html, /Antifraud/);
  assert.match(html, /AMOUNT_LIMIT/);
  assert.match(html, /PaymentRejected/);
});

test('drawer does not describe missing legacy fraud as an unavailable service', () => {
  const value = payment(1);
  const html = renderToStaticMarkup(
    React.createElement(drawer.default, {
      paymentId: 1,
      result: readResult(value),
      events: [],
      sources: { payment: ready(readResult(value)), fraud: { state: 'missing' }, audit: ready([]) },
      onClose() {},
      onRead() {},
      onConfirm() {},
    }),
  );
  assert.match(html, /Сохранённой проверки нет/);
  assert.match(html, /историческ/);
  assert.match(html, /Сохранённых событий пока нет/);
});

const app = await sourceModule('src/App.tsx');
const auditPage = await sourceModule('src/AuditPage.tsx');

test('initial console provides the five approved destinations and inline creation', () => {
  const html = renderToStaticMarkup(React.createElement(app.default));
  assert.match(html, /<nav[^>]*aria-label="Основная навигация"/);
  for (const label of ['Обзор', 'Платежи', 'Сервисы', 'API', 'Аудит'])
    assert.match(html, new RegExp(`>${label}</button>`));
  assert.match(html, /<form/);
  assert.doesNotMatch(html, /PAYMENT CONSOLE/);
});

test('audit screen renders processed database facts including event type and payment', () => {
  const entry = {
    id: 17,
    eventId: 'stable-event',
    paymentId: 42,
    amount: 300,
    customer: 'Анна',
    type: 'PaymentConfirmed',
    occurredAt: '2026-10-26T10:00:00Z',
    receivedAt: '2026-10-26T10:00:01Z',
    paymentType: 'QR',
    status: 'CONFIRMED',
    reason: null,
  };
  const html = renderToStaticMarkup(
    React.createElement(auditPage.default, { source: ready([entry]), onRetry() {}, onSelect() {} }),
  );
  assert.match(html, /PaymentConfirmed/);
  assert.match(html, /PAY-0042/);
  assert.match(html, /Анна/);
  const unavailable = renderToStaticMarkup(
    React.createElement(auditPage.default, {
      source: { state: 'error', error: 'Connection failed' },
      onRetry() {},
      onSelect() {},
    }),
  );
  assert.match(unavailable, /Источник недоступен/);
  assert.doesNotMatch(unavailable, /Сохранённых событий пока нет/);
});

test('a missing linked saved check is absent data rather than an offline Antifraud', async () => {
  mock.method(globalThis, 'fetch', async (path) => {
    if (path === '/api/payments/10')
      return Response.json(readResult(payment(10, { antifraudRequestId: 'missing-check' })));
    if (path === '/api/audit/entries?paymentId=10') return Response.json([]);
    if (path === '/api/antifraud/checks/missing-check')
      return Response.json({ message: 'Check not found' }, { status: 404 });
    throw new Error(`Unexpected request ${path}`);
  });
  const sources = await network.fetchPaymentSources(10);
  assert.equal(sources.fraud.state, 'missing');
  assert.equal(sources.fraud.reason, 'not-found');
  const html = renderToStaticMarkup(
    React.createElement(drawer.default, {
      paymentId: 10,
      sources,
      onClose() {},
      onRead() {},
      onConfirm() {},
    }),
  );
  assert.match(html, /Сохранённая проверка не найдена/);
  assert.doesNotMatch(html, /историческ/);
});

test('hidden screens do not make requests and refresh resumes with current data', async () => {
  let visible = false;
  let calls = 0;
  const values = [];
  const poll = data.startPolling(
    async () => ++calls,
    (value) => values.push(value),
    { interval: 10000, visible: () => visible },
  );
  poll.refresh();
  await new Promise((resolve) => setTimeout(resolve, 0));
  assert.equal(calls, 0);
  visible = true;
  poll.refresh();
  await new Promise((resolve) => setTimeout(resolve, 0));
  poll.stop();
  assert.deepEqual(values, [1]);
});

const nextTurn = () => new Promise((resolve) => setTimeout(resolve, 0));

test('manual refresh waits for the queued fresh read instead of an older in-flight response', async () => {
  const reads = [];
  const values = [];
  const poll = data.startPolling(
    () => new Promise((resolve) => reads.push(resolve)),
    (value) => values.push(value),
    { interval: 10000 },
  );
  try {
    let completed = false;
    const refresh = poll.refresh();
    assert.equal(typeof refresh?.then, 'function');
    refresh.then(() => {
      completed = true;
    });
    reads[0]('old response');
    await nextTurn();
    assert.equal(completed, false);
    assert.equal(reads.length, 2);
    reads[1]('fresh response');
    assert.deepEqual(await refresh, { state: 'ready', data: 'fresh response' });
    assert.deepEqual(values, ['old response', 'fresh response']);
  } finally {
    poll.stop();
  }
});

test('failed polling and manual reads settle without an unhandled rejection or false success', async () => {
  const failures = [];
  const error = new Error('Audit database unavailable');
  const poll = data.startPolling(
    async () => {
      throw error;
    },
    () => assert.fail('a failed read must not publish a value'),
    { interval: 10000, onError: (failure) => failures.push(failure) },
  );
  try {
    const result = await poll.refresh();
    assert.deepEqual(result, { state: 'error', error });
    assert.ok(failures.length >= 1);
  } finally {
    poll.stop();
  }
});

test('stopping aborts the active read and settles both active and queued refreshes', async () => {
  const reads = [];
  let signal;
  const values = [];
  const failures = [];
  const poll = data.startPolling(
    (readSignal) => {
      signal = readSignal;
      return new Promise((resolve) => reads.push(resolve));
    },
    (value) => values.push(value),
    { interval: 10000, onError: (error) => failures.push(error) },
  );
  reads[0]('initial');
  await nextTurn();
  const active = poll.refresh();
  const queued = poll.refresh();
  poll.stop();
  assert.equal(signal.aborted, true);
  assert.deepEqual(await active, { state: 'cancelled' });
  assert.deepEqual(await queued, { state: 'cancelled' });
  reads[1]('late result');
  await nextTurn();
  assert.deepEqual(values, ['initial']);
  assert.deepEqual(failures, []);
  assert.equal(reads.length, 2);
  assert.deepEqual(await poll.refresh(), { state: 'cancelled' });
});

test('manual feedback stays in progress until fresh reads finish and reports backend failure', async () => {
  assert.equal(typeof data.createRefreshAction, 'function');
  const states = [];
  let finish;
  const action = data.createRefreshAction(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
    (state) => states.push(state),
  );
  const success = action.refresh();
  assert.deepEqual(states, [{ state: 'refreshing' }]);
  finish({ state: 'ready', data: 42 });
  await success;
  assert.deepEqual(states, [{ state: 'refreshing' }, { state: 'success' }]);
  const failure = action.refresh();
  finish({ state: 'error', error: new Error('HTTP 503') });
  await failure;
  assert.deepEqual(states.at(-1), { state: 'error', message: 'HTTP 503' });
  action.stop();
});

test('rapid manual clicks keep progress for the latest request and ignore results after stop', async () => {
  assert.equal(typeof data.createRefreshAction, 'function');
  const states = [];
  const reads = [];
  const action = data.createRefreshAction(
    () => new Promise((resolve) => reads.push(resolve)),
    (state) => states.push(state),
  );
  const first = action.refresh();
  const second = action.refresh();
  reads[0]({ state: 'ready', data: 1 });
  await first;
  assert.deepEqual(states.at(-1), { state: 'refreshing' });
  action.stop();
  reads[1]({ state: 'ready', data: 2 });
  await second;
  assert.deepEqual(states.at(-1), { state: 'refreshing' });
});

test('refreshing several sources awaits every response and preserves any failure', async () => {
  assert.equal(typeof data.refreshTogether, 'function');
  let finish;
  let complete = false;
  const error = new Error('Payment offline');
  const refresh = data.refreshTogether([
    Promise.resolve({ state: 'error', error }),
    new Promise((resolve) => {
      finish = resolve;
    }),
  ]);
  refresh.then(() => {
    complete = true;
  });
  await nextTurn();
  assert.equal(complete, false);
  finish({ state: 'ready', data: [] });
  assert.deepEqual(await refresh, { state: 'error', error });
});

test('refresh controls expose visible progress, success and failure without relying on icon changes', () => {
  assert.equal(typeof components.RefreshControl, 'function');
  const render = (state) =>
    renderToStaticMarkup(React.createElement(components.RefreshControl, { state, onRefresh() {} }));
  const pending = render({ state: 'refreshing' });
  assert.match(pending, /disabled=""/);
  assert.match(pending, /role="status"/);
  assert.match(pending, /Обновляем/);
  assert.match(render({ state: 'success' }), /Данные обновлены/);
  const failure = render({ state: 'error', message: 'Audit offline' });
  assert.match(failure, /Не удалось обновить/);
  assert.match(failure, /Audit offline/);
  assert.doesNotMatch(render({ state: 'idle' }), /Данные обновлены/);
});

test('audit keeps stable columns for current and historical rows and retains event selection', () => {
  const entries = [
    {
      id: 1,
      paymentId: 42,
      customer: 'Анна',
      amount: 1000,
      type: 'PaymentConfirmed',
      receivedAt: '2026-10-26T10:00:01Z',
      status: 'CONFIRMED',
    },
    {
      id: 2,
      paymentId: 43,
      customer: 'Длинное имя владельца',
      amount: 4990.5,
      type: 'HistoricalPaymentEventWithLongName',
      receivedAt: '2026-10-26T10:05:01Z',
      status: null,
    },
  ];
  let selected;
  const element = auditPage.default({
    source: ready(entries),
    onRetry() {},
    onSelect: (entry) => {
      selected = entry;
    },
  });
  const html = renderToStaticMarkup(element);
  assert.match(html, /<table[^>]*aria-label="Обработанные события Audit"/);
  for (const label of ['Событие', 'Платёж и владелец', 'Сохранено', 'Сумма', 'Статус'])
    assert.match(html, new RegExp(`<th[^>]*>${label}</th>`));
  const rows = [...html.matchAll(/<tr[^>]*>(.*?)<\/tr>/gs)].slice(1);
  assert.deepEqual(
    rows.map((row) => (row[1].match(/<td[ >]/g) ?? []).length),
    [5, 5],
  );
  assert.match(rows[1][1], /aria-label="Статус не сохранён"/);
  assert.match(html, /<time dateTime="2026-10-26T10:00:01Z">/);
  assert.doesNotMatch(html, />2026-10-26T10:00:01Z</);
  const find = (node, predicate) => {
    if (!node || typeof node !== 'object') return null;
    if (predicate(node)) return node;
    for (const child of React.Children.toArray(node.props?.children)) {
      const found = find(child, predicate);
      if (found) return found;
    }
    return null;
  };
  const row = find(element, (node) => node.type === 'tr' && node.props.tabIndex === 0);
  row.props.onClick();
  assert.equal(selected, entries[0]);
  selected = null;
  row.props.onKeyDown({ key: 'Enter', preventDefault() {} });
  assert.equal(selected, entries[0]);
});

test('service controls remain collapsed and expose the exact editable backend values', () => {
  const html = renderToStaticMarkup(React.createElement(services.default, { platform: health }));
  assert.equal((html.match(/<details class="service-controls"/g) ?? []).length, 3);
  assert.doesNotMatch(html, /<details[^>]* open/);
  for (const value of [
    'nplus1',
    'join',
    'off',
    'on',
    'timeout',
    'retry',
    'breaker',
    'normal',
    'slow',
    'error',
    'reject',
  ])
    assert.match(html, new RegExp(`value="${value}"`));
  assert.doesNotMatch(html, /value="transient"/);
  for (const label of [
    'Кэш Redis',
    'Инвалидация кэша',
    'Публикаций одного события',
    'Логи сервисов',
  ])
    assert.match(html, new RegExp(label, 'i'));
  assert.match(html, /aria-label="Публикаций одного события"/);
  assert.match(html, /value="5"/);
  assert.doesNotMatch(html, /Блокировка платежа|value="optimistic"|value="pessimistic"/);
});

test('saving Payment settings sends the selected wire values and awaits verified readback', async () => {
  assert.equal(typeof network.savePaymentSettings, 'function');
  const settings = {
    fetchMode: 'nplus1',
    cacheEnabled: true,
    cacheInvalidationEnabled: false,
    httpMode: 'retry',
    eventCopies: 5,
  };
  const paths = [];
  let finish;
  mock.method(globalThis, 'fetch', async (path, options) => {
    paths.push(path);
    if (options.method === 'PUT') {
      assert.equal(path, '/api/admin/settings');
      assert.deepEqual(JSON.parse(options.body), settings);
      return Response.json({ settings, circuitState: 'CLOSED' });
    }
    assert.equal(path, '/api/admin/status');
    return new Promise((resolve) => {
      finish = resolve;
    });
  });
  let complete = false;
  const saved = network.savePaymentSettings(settings);
  saved.then(() => {
    complete = true;
  });
  await nextTurn();
  assert.equal(complete, false);
  finish(Response.json({ settings, circuitState: 'CLOSED' }));
  assert.deepEqual((await saved).settings, settings);
  assert.deepEqual(paths, ['/api/admin/settings', '/api/admin/status']);
});

test('failed save or mismatching readback never reports settings as saved', async () => {
  assert.equal(typeof network.savePaymentSettings, 'function');
  const settings = {
    fetchMode: 'join',
    cacheEnabled: true,
    cacheInvalidationEnabled: true,
    httpMode: 'timeout',
    eventCopies: 1,
  };
  mock.method(globalThis, 'fetch', async () =>
    Response.json({ message: 'Settings rejected' }, { status: 400 }),
  );
  await assert.rejects(() => network.savePaymentSettings(settings), /Settings rejected/);
  mock.restoreAll();
  mock.method(globalThis, 'fetch', async () =>
    Response.json({ settings: { ...settings, eventCopies: 5 }, circuitState: 'CLOSED' }),
  );
  await assert.rejects(() => network.savePaymentSettings(settings), /не подтверждены/);
});

test('Antifraud mode uses its actual endpoint and fresh status', async () => {
  assert.equal(typeof network.saveAntifraudMode, 'function');
  const requests = [];
  mock.method(globalThis, 'fetch', async (path, options) => {
    requests.push({
      path,
      method: options.method ?? 'GET',
      body: options.body && JSON.parse(options.body),
    });
    return Response.json(
      path.includes('antifraud')
        ? { mode: 'error' }
        : { paused: true, pauseApplied: false, assigned: 1 },
    );
  });
  assert.equal((await network.saveAntifraudMode('error')).mode, 'error');
  assert.deepEqual(requests, [
    { path: '/api/antifraud/admin/mode', method: 'PUT', body: { mode: 'error' } },
    { path: '/api/antifraud/admin/status', method: 'GET', body: undefined },
  ]);
});

test('all services use one Save settings button and Audit has two draft selects', () => {
  for (const enabled of [true, false]) {
    const html = renderToStaticMarkup(
      React.createElement(services.default, {
        platform: {
          ...health,
          auditSource: ready({ ...health.auditSource.data, idempotencyEnabled: enabled }),
        },
      }),
    );
    assert.equal((html.match(/>Сохранить настройки<\/button>/g) ?? []).length, 3);
    const audit = [...html.matchAll(/<details class="service-controls">(.*?)<\/details>/gs)].find(
      (match) => match[1].includes('Настройки Audit'),
    )?.[1];
    assert.ok(audit.includes('<form'));
    assert.equal((audit.match(/<select/g) ?? []).length, 2);
    assert.match(audit, /aria-label="Защита от дублей"/);
    assert.match(audit, /aria-label="Обработка Kafka"/);
    assert.doesNotMatch(audit, /Сценарий ошибок|DLT|retry/);
    assert.match(audit, new RegExp(`value="${enabled ? 'on' : 'off'}" selected=""`));
    assert.doesNotMatch(audit, /role="switch"|Приостановить обработку Kafka|Применить к следующим/);
  }
});

test('Audit Save applies both draft values and waits for independent fresh readback', async () => {
  assert.equal(typeof network.saveAuditSettings, 'function');
  const paths = [];
  let finish;
  mock.method(globalThis, 'fetch', async (path, options) => {
    paths.push(path);
    if (options.method === 'PUT') {
      assert.equal(path, '/api/audit/admin/settings');
      assert.deepEqual(JSON.parse(options.body), { idempotencyEnabled: false });
      return Response.json({ ...health.auditSource.data, idempotencyEnabled: false });
    }
    if (path.endsWith('/resume')) {
      assert.equal(options.method, 'POST');
      return Response.json({ ...health.auditSource.data, paused: false });
    }
    assert.equal(path, '/api/audit/admin/status');
    return new Promise((resolve) => {
      finish = resolve;
    });
  });
  let done = false;
  const result = network
    .saveAuditSettings({ idempotencyEnabled: false, paused: false })
    .then((value) => {
      done = true;
      return value;
    });
  await nextTurn();
  assert.equal(done, false);
  finish(
    Response.json({
      ...health.auditSource.data,
      idempotencyEnabled: false,
      paused: false,
    }),
  );
  assert.equal((await result).idempotencyEnabled, false);
  assert.deepEqual(paths, [
    '/api/audit/admin/settings',
    '/api/audit/admin/resume',
    '/api/audit/admin/status',
  ]);
});

test('Audit setting never reports success after rejected save or mismatching readback', async () => {
  assert.equal(typeof network.saveAuditSettings, 'function');
  mock.method(globalThis, 'fetch', async () =>
    Response.json({ message: 'Rejected' }, { status: 400 }),
  );
  const settings = { idempotencyEnabled: false, paused: true };
  await assert.rejects(() => network.saveAuditSettings(settings), /Rejected/);
  mock.restoreAll();
  mock.method(globalThis, 'fetch', async () =>
    Response.json({ ...health.auditSource.data, idempotencyEnabled: true }),
  );
  await assert.rejects(() => network.saveAuditSettings(settings), /не подтвержден/);
});

test('Audit Save pauses first when requested and does not claim success after a later failure', async () => {
  const paths = [];
  mock.method(globalThis, 'fetch', async (path) => {
    paths.push(path);
    return path.endsWith('/settings')
      ? Response.json({ message: 'Cannot save settings' }, { status: 503 })
      : Response.json(health.auditSource.data);
  });
  await assert.rejects(
    () => network.saveAuditSettings({ idempotencyEnabled: true, paused: true }),
    /Cannot save settings/,
  );
  assert.deepEqual(paths, ['/api/audit/admin/pause', '/api/audit/admin/settings']);
});

test('logs are scheduled only for Services and fetched from each real service', async () => {
  assert.equal(typeof network.fetchServiceLogs, 'function');
  for (const page of ['overview', 'payments', 'api', 'audit'])
    assert.equal(data.readsForPage(page).logs, false);
  assert.equal(data.readsForPage('services').logs, true);
  const paths = [];
  const controller = new AbortController();
  mock.method(globalThis, 'fetch', async (path, options) => {
    paths.push(path);
    assert.equal(options.signal.aborted, false);
    return Response.json({ service: 'payment', entries: [] });
  });
  await Promise.all(
    ['payment', 'antifraud', 'audit'].map((service) =>
      network.fetchServiceLogs(service, controller.signal),
    ),
  );
  assert.deepEqual(paths, [
    '/api/admin/logs',
    '/api/antifraud/admin/logs',
    '/api/audit/admin/logs',
  ]);
});

test('one failed log source remains visible while actual logs from other services stay readable', () => {
  const html = renderToStaticMarkup(
    React.createElement(services.default, {
      platform: {
        ...health,
        logSources: {
          payment: ready({
            service: 'payment',
            entries: [
              {
                sequence: 1,
                timestamp: '2026-10-26T10:00:01Z',
                service: 'payment',
                level: 'DEBUG',
                logger: 'org.hibernate.SQL',
                message: 'select payment where id=42',
              },
            ],
          }),
          antifraud: { state: 'error', error: 'Antifraud logs HTTP 503' },
          audit: ready({ service: 'audit', entries: [] }),
        },
      },
    }),
  );
  assert.match(html, /select payment where id=42/);
  assert.match(html, /org.hibernate.SQL/);
  assert.match(html, /Antifraud logs HTTP 503/);
  assert.match(html, /aria-label="Фильтр логов по сервису"/);
  for (const name of ['Все сервисы', 'Payment', 'Anti-Fraud', 'Audit'])
    assert.ok(html.includes(`>${name}</button>`));
});

test('leaving Services aborts a log fetch and prevents further polling or late error publication', async () => {
  let calls = 0;
  let signal;
  const published = [];
  mock.method(globalThis, 'fetch', async (path, options) => {
    assert.equal(path, '/api/admin/logs');
    calls++;
    signal = options.signal;
    return new Promise((resolve, reject) => {
      options.signal.addEventListener(
        'abort',
        () => reject(new DOMException('Aborted', 'AbortError')),
        { once: true },
      );
    });
  });
  const poll = data.startPolling(
    (readSignal) => network.fetchServiceLogs('payment', readSignal),
    (value) => published.push(value),
    { interval: 5, onError: (error) => published.push(error) },
  );
  const pending = poll.refresh();
  poll.stop();
  assert.deepEqual(await pending, { state: 'cancelled' });
  assert.equal(signal.aborted, true);
  await new Promise((resolve) => setTimeout(resolve, 20));
  assert.equal(calls, 1);
  assert.deepEqual(published, []);
});

const controls = await sourceModule('src/ServiceControls.tsx');
const logView = await sourceModule('src/ServiceLogs.tsx');

test('log filter buttons switch actual entries and selected state for every service', () => {
  const sources = Object.fromEntries(
    ['payment', 'antifraud', 'audit'].map((service) => [
      service,
      ready({
        service,
        entries: [
          {
            sequence: 1,
            timestamp: '2026-10-27T10:00:00Z',
            level: 'INFO',
            logger: 'test',
            message: `${service}-message`,
          },
        ],
      }),
    ]),
  );
  let filter = 'all';
  const findButtons = (node) => {
    if (!node || typeof node !== 'object') return [];
    return [
      ...(node.type === 'button' && node.props['aria-pressed'] !== undefined ? [node] : []),
      ...React.Children.toArray(node.props?.children).flatMap(findButtons),
    ];
  };
  for (const [name, expected] of [
    ['Payment', 'payment'],
    ['Anti-Fraud', 'antifraud'],
    ['Audit', 'audit'],
    ['Все сервисы', 'all'],
  ]) {
    const element = logView.ServiceLogView({
      sources,
      filter,
      onFilter: (value) => {
        filter = value;
      },
      onRetry() {},
    });
    const buttons = findButtons(element);
    assert.equal(buttons.length, 4);
    buttons.find((button) => button.props.children === name).props.onClick();
    assert.equal(filter, expected);
    const html = renderToStaticMarkup(
      React.createElement(logView.ServiceLogView, { sources, filter, onFilter() {}, onRetry() {} }),
    );
    for (const service of ['payment', 'antifraud', 'audit'])
      assert.equal(html.includes(`${service}-message`), filter === 'all' || filter === service);
    assert.equal((html.match(/aria-pressed="true"/g) ?? []).length, 1);
    assert.doesNotMatch(html, /<select/);
  }
});

test('Audit draft edits survive polling and only successful save releases the draft', () => {
  let state = {
    settings: { idempotencyEnabled: true, paused: false },
    protected: false,
  };
  state = controls.auditDraftReducer(state, { type: 'edit', key: 'paused', value: true });
  state = controls.auditDraftReducer(state, {
    type: 'edit',
    key: 'idempotencyEnabled',
    value: false,
  });
  state = controls.auditDraftReducer(state, {
    type: 'remote',
    status: { ...health.auditSource.data, paused: false, idempotencyEnabled: true },
  });
  assert.equal(state.settings.paused, true);
  assert.equal(state.settings.idempotencyEnabled, false);
  state = controls.auditDraftReducer(state, { type: 'saved', settings: state.settings });
  assert.equal(state.protected, false);
  state = controls.auditDraftReducer(state, {
    type: 'remote',
    status: { ...health.auditSource.data, paused: false, idempotencyEnabled: true },
  });
  assert.equal(state.settings.paused, false);
  assert.equal(state.settings.idempotencyEnabled, true);
});

test('an old status poll cannot overwrite a saved Payment draft while its fresh reload is pending', async () => {
  assert.equal(typeof controls.paymentDraftReducer, 'function');
  assert.equal(typeof controls.savePaymentDraft, 'function');
  const oldSettings = {
    fetchMode: 'join',
    cacheEnabled: false,
    cacheInvalidationEnabled: true,
    httpMode: 'timeout',
    eventCopies: 1,
  };
  const newSettings = {
    ...oldSettings,
    fetchMode: 'nplus1',
    cacheEnabled: true,
    cacheInvalidationEnabled: false,
    eventCopies: 5,
  };
  let draft = { settings: newSettings, protected: true };
  const dispatch = (action) => {
    draft = controls.paymentDraftReducer(draft, action);
  };
  let resolveOld;
  let resolveFresh;
  let statusReads = 0;
  const writes = [];
  mock.method(globalThis, 'fetch', async (path, options) => {
    if (path === '/api/admin/settings') {
      writes.push(JSON.parse(options.body));
      return Response.json({ settings: newSettings, circuitState: 'CLOSED' });
    }
    assert.equal(path, '/api/admin/status');
    statusReads++;
    if (statusReads === 1)
      return new Promise((resolve) => {
        resolveOld = resolve;
      });
    if (statusReads === 2) return Response.json({ settings: newSettings, circuitState: 'CLOSED' });
    return new Promise((resolve) => {
      resolveFresh = resolve;
    });
  });
  const poll = data.startPolling(
    (signal) => network.api('/api/admin/status', { signal }),
    (status) => dispatch({ type: 'remote', settings: status.settings }),
    { interval: 10000 },
  );
  try {
    let completed = false;
    const save = controls.savePaymentDraft(newSettings, poll.refresh, dispatch);
    save.then(() => {
      completed = true;
    });
    await nextTurn();
    resolveOld(Response.json({ settings: oldSettings, circuitState: 'CLOSED' }));
    await nextTurn();
    assert.deepEqual(draft.settings, newSettings);
    assert.equal(completed, false);
    assert.equal(draft.protected, true);
    resolveFresh(Response.json({ settings: newSettings, circuitState: 'CLOSED' }));
    await save;
    assert.equal(draft.protected, false);
    assert.deepEqual(draft.settings, newSettings);
    assert.deepEqual(writes, [newSettings]);
    // The next submit reads this draft, so it must still carry the saved fields.
    assert.equal(draft.settings.cacheInvalidationEnabled, false);
    assert.equal(draft.settings.eventCopies, 5);
  } finally {
    poll.stop();
  }
});

test('logs sort mixed ISO precision by instant and use sequence for equal timestamps', () => {
  const entry = (sequence, timestamp, message) => ({
    sequence,
    timestamp,
    service: 'payment',
    level: 'INFO',
    logger: 'test',
    message,
  });
  const html = renderToStaticMarkup(
    React.createElement(services.default, {
      platform: {
        ...health,
        logSources: {
          payment: ready({
            service: 'payment',
            entries: [
              entry(9, '2026-10-26T10:00:01Z', 'older-whole-second'),
              entry(10, '2026-10-26T10:00:01.123Z', 'newer-fractional-second'),
              entry(12, '2026-10-26T10:00:01.123Z', 'same-instant-higher-sequence'),
            ],
          }),
          antifraud: ready({ service: 'antifraud', entries: [] }),
          audit: ready({ service: 'audit', entries: [] }),
        },
      },
    }),
  );
  const messages = [...html.matchAll(/<article[^>]*>.*?<pre>(.*?)<\/pre>/gs)].map(
    (match) => match[1],
  );
  assert.deepEqual(messages, [
    'same-instant-higher-sequence',
    'newer-fractional-second',
    'older-whole-second',
  ]);
});

test('Payment eventCopies represents every valid backend value and preserves externally saved three', () => {
  const html = renderToStaticMarkup(
    React.createElement(services.default, {
      platform: {
        ...health,
        paymentSource: ready({
          ...health.paymentSource.data,
          settings: { ...health.paymentSource.data.settings, eventCopies: 3 },
        }),
      },
    }),
  );
  const select = html.match(
    /<select aria-label="Публикаций одного события"[^>]*>(.*?)<\/select>/s,
  )?.[1];
  assert.ok(select);
  assert.match(select, /<option value="3" selected=""/);
  assert.deepEqual(
    [...select.matchAll(/<option value="(\d)"/g)].map((match) => Number(match[1])),
    [1, 2, 3, 4, 5],
  );
});

import assert from 'node:assert/strict';
import { afterEach, mock, test } from 'node:test';
import { createPayment } from '../src/api.ts';

afterEach(() => mock.restoreAll());

const draft = { customerId: 1, amount: 1500, description: 'Проверка ошибки', paymentType: 'CARD' };

test('ошибка 503 создания передаёт пояснение сервера, а не успешный платёж', async () => {
  const message =
    'Невозможно создать платёж: система недоступна (Antifraud возвращает ошибку HTTP 503).';
  mock.method(globalThis, 'fetch', async (path, options) => {
    assert.equal(path, '/api/payments');
    assert.equal(options.method, 'POST');
    assert.deepEqual(JSON.parse(options.body), draft);
    return Response.json({ message }, { status: 503 });
  });

  await assert.rejects(() => createPayment(draft), { message, status: 503 });
});

test('ошибка в сумме не выдаётся за недоступность Antifraud', async () => {
  mock.method(globalThis, 'fetch', async () =>
    Response.json({ message: 'Некорректная сумма' }, { status: 400 }),
  );

  await assert.rejects(() => createPayment(draft), { message: 'Некорректная сумма', status: 400 });
});

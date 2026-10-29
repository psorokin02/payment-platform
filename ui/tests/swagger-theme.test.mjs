import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { unlink } from 'node:fs/promises';
import { build } from 'esbuild';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

const bundle = new URL(`../.swagger-test-${process.pid}.mjs`, import.meta.url);
await build({
  entryPoints: ['src/ApiPage.tsx'],
  outfile: bundle.pathname,
  bundle: true,
  platform: 'node',
  format: 'esm',
  packages: 'external',
  jsx: 'automatic',
});
after(() => unlink(bundle));
const { default: ApiPage } = await import(bundle.href);
const ready = (data) => ({ state: 'ready', data });
const platform = {
  paymentSource: ready({}),
  antifraudSource: ready({}),
  auditSource: ready({}),
  refresh: async () => {},
};

for (const [service, port] of [
  ['payment', 8081],
  ['antifraud', 8082],
  ['audit', 8083],
]) {
  test(`${service} opens its own themed Swagger and keeps real service URLs`, () => {
    const html = renderToStaticMarkup(
      React.createElement(ApiPage, { platform, openService: service, onToggle() {} }),
    );
    assert.match(
      html,
      new RegExp(`src="http://127\\.0\\.0\\.1:${port}/swagger-dark/index\\.html"`),
    );
    assert.match(
      html,
      new RegExp(`href="http://127\\.0\\.0\\.1:${port}/swagger-dark/index\\.html"`),
    );
    assert.equal((html.match(/<iframe /g) ?? []).length, 1);
  });
}

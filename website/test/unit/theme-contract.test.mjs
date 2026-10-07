import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const css = readFileSync(fileURLToPath(new URL('../../src/css/custom.css', import.meta.url)), 'utf8');

// The body of the `html[data-theme='dark'] { ... }` token block. It must be anchored on `html`: Infima declares its dark
// variables on `html[data-theme='dark']`, so a bare `[data-theme='dark']` rule would lose on specificity.
const darkMatch = /^html\[data-theme='dark'\]\s*\{([^}]*)\}/m.exec(css);
assert.ok(darkMatch, "custom.css must declare the dark tokens in a rule whose selector is exactly `html[data-theme='dark']`");
const dark = darkMatch[1];

function variable(block, name) {
  const m = new RegExp(`${name}\\s*:\\s*([^;]+);`).exec(block);
  return m && m[1].trim();
}

test('dark mode page background and base text use the brand ink and paper', () => {
  assert.equal(variable(dark, '--ifm-background-color'), 'var(--ink)');
  assert.equal(variable(dark, '--ifm-font-color-base'), 'var(--paper-ink)');
});

test('dark mode text variables never use the lifted blue', () => {
  for (const name of [
    '--ifm-breadcrumb-color-active',
    '--ifm-tabs-color-active',
    '--ifm-pagination-color-active',
    '--ifm-pagination-nav-color-hover',
    '--ifm-pills-color-active',
  ]) {
    assert.equal(variable(dark, name), 'var(--paper-ink)', name);
  }
});

test('dark mode overrides the table of contents and .text--primary colours', () => {
  const rule = /(\[data-theme='dark'\][^{]*)\{([^}]*)\}/g;
  const overrides = [...css.matchAll(rule)]
    .filter((m) => /table-of-contents|text--primary/.test(m[1]))
    .map((m) => [m[1].replace(/\s+/g, ' ').trim(), m[2].trim()]);
  assert.deepEqual(overrides, [
    [
      "[data-theme='dark'] .table-of-contents__link:hover, [data-theme='dark'] .table-of-contents__link--active, [data-theme='dark'] .text--primary",
      'color: var(--paper-ink);',
    ],
  ]);
});

test('the navbar active link is white', () => {
  const root = /:root\s*\{([^}]*)\}/.exec(css)[1];
  assert.equal(variable(root, '--ifm-navbar-link-active-color'), '#ffffff');
});

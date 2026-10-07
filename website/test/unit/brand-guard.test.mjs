import { test } from 'node:test';
import assert from 'node:assert/strict';
import { findViolations } from '../../scripts/lib/brand-guard.mjs';

test('flat CSS and Infima switch-offs are allowed', () => {
  const css = `
    :root { --ifm-global-radius: 0; --ifm-global-shadow-lw: none; --ifm-navbar-shadow: none; }
    .navbar { box-shadow: none !important; border-radius: 0; }
    .x { background: var(--ink); border: 1px solid var(--rule); }
  `;
  assert.deepEqual(findViolations(css), []);
});

test('radius, shadow, gradient, rotation and skew are violations', () => {
  assert.deepEqual(findViolations('.a { border-radius: 4px; }'), ['border-radius: 4px']);
  assert.deepEqual(findViolations('.a { box-shadow: 0 1px 2px #000; }'), ['box-shadow: 0 1px 2px #000']);
  assert.deepEqual(findViolations('.a { background: linear-gradient(red, blue); }'), ['gradient']);
  assert.deepEqual(findViolations('.a { transform: rotate(3deg); }'), ['rotation or skew']);
  assert.deepEqual(findViolations('.a { transform: skewX(3deg); }'), ['rotation or skew']);
  assert.deepEqual(findViolations('.a { filter: drop-shadow(0 0 2px #000); }'), ['drop-shadow']);
});

test('inline style objects in JavaScript are checked too', () => {
  assert.deepEqual(findViolations("const s = { boxShadow: '0 1px 2px #000', borderRadius: 8 };"), [
    "boxShadow: 0 1px 2px #000",
    'borderRadius: 8',
  ]);
  assert.deepEqual(findViolations("const s = { boxShadow: 'none', borderRadius: 0 };"), []);
});

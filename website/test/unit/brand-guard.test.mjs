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

test('comments and strings are not scanned', () => {
  assert.deepEqual(findViolations('/* shadow: none */ .a{color:red}'), []);
  assert.deepEqual(findViolations('/* no shadow: here */\n.a { color: red; }'), []);
  assert.deepEqual(findViolations('/*\n multi-line\n box-shadow: 0 1px #000;\n*/ .a { color: red; }'), []);
  assert.deepEqual(findViolations('// shadow: none\nconst a = 1;'), []);
  assert.deepEqual(findViolations('const a = 1; // border-radius: 8px'), []);
  assert.deepEqual(findViolations('.a { font-family: "Rotated"; }'), []);
  assert.deepEqual(findViolations('.a { background: url(https://x.dev/a.png); }'), []);
});

test('zero with a unit is flat; inherit and var() stay flagged', () => {
  assert.deepEqual(findViolations('.a { border-radius: 0px; }'), []);
  assert.deepEqual(findViolations('.a { border-radius: inherit; }'), ['border-radius: inherit']);
});

test('every real rotation or skew form is flagged', () => {
  for (const css of [
    '.a { transform: rotateX(3deg); }',
    '.a { transform: rotate3d(1,1,1,3deg); }',
    '.a { transform: skewY(2deg); }',
    '.a { rotate: 3deg; }',
  ]) {
    assert.deepEqual(findViolations(css), ['rotation or skew'], css);
  }
});

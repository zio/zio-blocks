import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const css = readFileSync(fileURLToPath(new URL('../../src/components/landing/landing.css', import.meta.url)), 'utf8');

/** The text between the braces of the first rule or at-rule whose head is `head` (braces balanced). */
function block(source, head) {
  const start = source.indexOf(head);
  assert.notEqual(start, -1, `landing.css must contain: ${head}`);
  const open = source.indexOf('{', start);
  let depth = 0;
  for (let i = open; i < source.length; i++) {
    if (source[i] === '{') depth++;
    if (source[i] === '}' && --depth === 0) return source.slice(open + 1, i);
  }
  return assert.fail(`unbalanced braces after: ${head}`);
}

test('CSS no longer snaps: JavaScript owns section-to-section moves, so snap would fight it', () => {
  assert.doesNotMatch(css, /scroll-snap/);
});

test('hash targets clear the sticky navbar', () => {
  assert.match(block(css, 'html:has(.lp-page)'), /scroll-padding-top:\s*var\(--ifm-navbar-height\);/);
});

test('hero, principles and deep dives fill the screen with min-height, never a fixed height', () => {
  const rule = block(css, '.lp-hero, .lp-screen');
  assert.match(rule, /min-height:\s*calc\(100svh - var\(--ifm-navbar-height\)\);/);
  assert.doesNotMatch(rule, /(^|[;\s])height:/);
});

const MOTION = '@media (prefers-reduced-motion: no-preference)';
const SUPPORTS = '@supports (animation-timeline: view())';
/** The no-preference media query that encloses the @supports guard (the nearest one before it). */
const motionBlock = () => block(css.slice(css.lastIndexOf(MOTION, css.indexOf(SUPPORTS))), MOTION);

test('every scroll-driven declaration lives inside the @supports block, so unsupported browsers see static content', () => {
  const inside = block(css, SUPPORTS);
  const outside = css.replace(css.slice(css.indexOf(SUPPORTS), css.indexOf('{', css.indexOf(SUPPORTS)) + 1 + inside.length + 1), '');
  for (const token of ['animation-timeline', 'view-timeline', 'lp-handoff', 'lp-settle']) {
    assert.equal(outside.includes(token), false, `${token} must only appear inside ${SUPPORTS}`);
    assert.ok(inside.includes(token), `${SUPPORTS} must use ${token}`);
  }
});

test('motion only runs when the visitor has not asked for reduced motion, and the @supports guard sits inside that media query', () => {
  const noPref = motionBlock();
  const inside = block(noPref, SUPPORTS);
  assert.ok(inside.includes('animation-timeline'));
  // The media query holds nothing but the guard, and the guard is not found anywhere outside it.
  assert.equal(noPref.replace(`${SUPPORTS} {${inside}}`, '').trim(), '');
  assert.equal(css.split(SUPPORTS).length, 2, 'exactly one @supports guard');
});

test('the hand-off moves the squares wrapper and the settle-in covers principles rows and catalog headings', () => {
  const guarded = block(motionBlock(), SUPPORTS);
  assert.match(guarded, /#hero\s*\{\s*view-timeline:\s*--hero-out block;\s*\}/);
  assert.match(block(guarded, '#hero .lp-field'), /animation-timeline:\s*--hero-out;/);
  assert.match(guarded, /\.lp-principles li, \.lp-cat h3\s*\{/);
});

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { currentIndex, sectionStep } from '../../src/lib/sectionStep.mjs';

const NAV = 60;
const VH = 800;
// Three sections; `y` is the page scroll offset. Heights: one screen, one screen, then a tall 2000px one.
const HEIGHTS = [VH - NAV, VH - NAV, 2000];
function rectsAt(y) {
  let top = NAV - y;
  return HEIGHTS.map((h) => {
    const r = { top, bottom: top + h };
    top += h;
    return r;
  });
}

test('currentIndex is the last section whose top has reached the navbar', () => {
  assert.equal(currentIndex(rectsAt(0), NAV), 0);
  assert.equal(currentIndex(rectsAt(740), NAV), 1);
  assert.equal(currentIndex(rectsAt(500), NAV), 0);
  assert.equal(currentIndex(rectsAt(1600), NAV), 2);
});

test('scrolling down from a screen-sized section advances to the next one', () => {
  assert.equal(sectionStep({ dir: 1, rects: rectsAt(0), navH: NAV, vh: VH }), 1);
  assert.equal(sectionStep({ dir: 1, rects: rectsAt(740), navH: NAV, vh: VH }), 2);
});

test('scrolling up from a section at its top goes back one', () => {
  assert.equal(sectionStep({ dir: -1, rects: rectsAt(740), navH: NAV, vh: VH }), 0);
  assert.equal(sectionStep({ dir: -1, rects: rectsAt(1480), navH: NAV, vh: VH }), 1);
});

test('a tall section scrolls natively until its edge, in both directions', () => {
  // Inside the tall section (starts at page y 1480): neither edge is on screen.
  assert.equal(sectionStep({ dir: 1, rects: rectsAt(1900), navH: NAV, vh: VH }), null);
  assert.equal(sectionStep({ dir: -1, rects: rectsAt(1900), navH: NAV, vh: VH }), null);
});

test('no neighbour at either end of the page', () => {
  assert.equal(sectionStep({ dir: -1, rects: rectsAt(0), navH: NAV, vh: VH }), null);
  assert.equal(sectionStep({ dir: 1, rects: rectsAt(3480 - VH + NAV), navH: NAV, vh: VH }), null);
});

test('no direction or a single section never moves', () => {
  assert.equal(sectionStep({ dir: 0, rects: rectsAt(0), navH: NAV, vh: VH }), null);
  assert.equal(sectionStep({ dir: 1, rects: rectsAt(0).slice(0, 1), navH: NAV, vh: VH }), null);
});

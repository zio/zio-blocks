// Decides where one scroll gesture should go. Pure, so the rules are unit-tested without a browser.
//
// `rects` are the sections' bounding boxes in page order (`top`/`bottom` relative to the viewport), `navH` is the
// sticky navbar height and `vh` the viewport height. Returns the index of the section to move to, or `null` to let the
// browser scroll natively (the current section is taller than the screen and has more to show, or there is no
// neighbour in that direction).
export const EPS = 4;

/** The section whose top has reached the navbar: the last one at or above it. */
export function currentIndex(rects, navH) {
  let idx = 0;
  for (let i = 0; i < rects.length; i++) {
    if (rects[i].top <= navH + EPS * 2) idx = i;
    else break;
  }
  return idx;
}

export function sectionStep({ dir, rects, navH, vh }) {
  if (rects.length < 2 || !dir) return null;
  const idx = currentIndex(rects, navH);
  const rect = rects[idx];
  if (dir > 0) {
    if (rect.bottom > vh + EPS) return null; // more of this section below the fold
    return idx < rects.length - 1 ? idx + 1 : null;
  }
  if (rect.top < navH - EPS) return null; // more of this section above
  return idx > 0 ? idx - 1 : null;
}

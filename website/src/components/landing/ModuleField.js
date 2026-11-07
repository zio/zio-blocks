import React from 'react';

// Decorative field of equal squares, in the spirit of the social card: a 3-wide grid of modules bleeding off the
// right edge. `1` = a module, `0` = a gap.
const PATTERN = [
  [1, 1, 1],
  [0, 0, 1],
  [0, 0, 0],
  [0, 1, 0],
  [1, 1, 0],
  [1, 1, 1],
];
const squares = PATTERN.flatMap((row, r) => row.map((on, c) => (on ? { r, c } : null)).filter(Boolean));

export default function ModuleField() {
  return (
    <div className="lp-field" aria-hidden="true">
      {squares.map((s, i) => (
        <span key={i} className="lp-sq" style={{'--c': s.c, '--r': s.r, '--i': i}} />
      ))}
    </div>
  );
}

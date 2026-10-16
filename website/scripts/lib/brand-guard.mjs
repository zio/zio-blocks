/**
 * Brand rules (assets/logo/README.md): flat and square. Returns the violations found in `source` (CSS or JS):
 * gradients, rotation or skew, drop shadows, and any shadow or radius property whose value is not `none` or `0`
 * (so Infima's `--ifm-global-radius: 0` and `box-shadow: none` are fine). `0px` counts as `0`; `inherit` and `var(...)`
 * values are still flagged because they cannot be proven flat: write `0` or `none`.
 */
export function findViolations(input) {
  const out = [];
  // Comments are not code: strip block comments and `//` line comments (only at a line start or after whitespace,
  // so the `//` in `https://` survives).
  const source = input.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/(^|\s)\/\/[^\n]*/g, '$1');
  if (/gradient\s*\(/i.test(source)) out.push('gradient');
  // Real CSS forms only: the transform functions and the standalone property, never the bare word.
  if (/\b(?:rotate(?:[XYZ]|3d)?|skew[XY]?)\s*\(/i.test(source) || /(?:^|[\s;{])rotate\s*:/i.test(source)) {
    out.push('rotation or skew');
  }
  if (/drop-shadow\s*\(/i.test(source)) out.push('drop-shadow');
  // A value runs to `;`, `}` or the end of the line, or stops before `, nextKey:` (a JavaScript object property);
  // commas inside a CSS value list (`0 1px #000, 0 2px #000`) stay part of the value.
  const property = /([\w-]*(?:shadow|radius)[\w-]*)\s*:\s*([^;}\n]*?)(?=\s*,\s*[A-Za-z_$][\w$-]*\s*:|\s*[;}\n]|\s*$)/gi;
  for (const m of source.matchAll(property)) {
    const value = m[2].replace(/!important/i, '').replace(/['"]/g, '').trim();
    if (value.toLowerCase() === 'none' || value === '0' || value === '0px') continue;
    out.push(`${m[1]}: ${value}`);
  }
  return out;
}

/**
 * Brand rules (assets/logo/README.md): flat and square. Returns the violations found in `source` (CSS or JS):
 * gradients, rotation or skew, drop shadows, and any shadow or radius property whose value is not `none` or `0`
 * (so Infima's `--ifm-global-radius: 0` and `box-shadow: none` are fine).
 */
export function findViolations(source) {
  const out = [];
  if (/gradient\s*\(/i.test(source)) out.push('gradient');
  if (/rotate|skew/i.test(source)) out.push('rotation or skew');
  if (/drop-shadow\s*\(/i.test(source)) out.push('drop-shadow');
  // A value runs to `;`, `}` or the end of the line, or stops before `, nextKey:` (a JavaScript object property);
  // commas inside a CSS value list (`0 1px #000, 0 2px #000`) stay part of the value.
  const property = /([\w-]*(?:shadow|radius)[\w-]*)\s*:\s*([^;}\n]*?)(?=\s*,\s*[A-Za-z_$][\w$-]*\s*:|\s*[;}\n]|\s*$)/gi;
  for (const m of source.matchAll(property)) {
    const value = m[2].replace(/!important/i, '').replace(/['"]/g, '').trim();
    if (value.toLowerCase() === 'none' || value === '0') continue;
    out.push(`${m[1]}: ${value}`);
  }
  return out;
}

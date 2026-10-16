/**
 * Shows a principle with different text than docs/index.md, so the landing page can word it more tightly without
 * editing the docs. An entry that matches no principle fails the build (the docs principle was probably renamed). The
 * input is not mutated.
 */
export function overridePrincipleText(principles, overrides) {
  const names = new Set(principles.map((p) => p.name));
  for (const name of Object.keys(overrides)) {
    if (!names.has(name)) {
      throw new Error(`text override for unknown principle "${name}"; check PRINCIPLE_TEXT in scripts/build-catalog.mjs`);
    }
  }
  return principles.map((p) => (p.name in overrides ? { ...p, text: overrides[p.name] } : p));
}

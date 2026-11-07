/**
 * Shows categories under a different name than the `###` heading in docs/index.md, so the landing page can word a
 * category differently without editing the docs. An entry that matches no category fails the build (the docs heading
 * was probably renamed). The input is not mutated.
 */
export function renameCategories(categories, renames) {
  const names = new Set(categories.map((c) => c.name));
  for (const from of Object.keys(renames)) {
    if (!names.has(from)) {
      throw new Error(`rename for unknown category "${from}"; check CATEGORY_RENAMES in scripts/build-catalog.mjs`);
    }
  }
  return categories.map((c) => (c.name in renames ? { ...c, name: renames[c.name] } : c));
}

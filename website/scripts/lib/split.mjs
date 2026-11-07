/**
 * Splits a category in two for the landing page without editing docs/index.md. For each `{ from, into, where }`, the
 * blocks of the category `from` that satisfy `where` move to a new category `into` placed right after it, and the
 * docs note goes with them (it described those rows); the original keeps the rest. Fails the build if the category is
 * missing or either side would be empty. The input is not mutated.
 */
export function splitCategories(categories, splits) {
  return splits.reduce((cats, { from, into, where }) => {
    const index = cats.findIndex((c) => c.name === from);
    if (index === -1) {
      throw new Error(`split of unknown category "${from}"; check CATEGORY_SPLITS in scripts/build-catalog.mjs`);
    }
    const original = cats[index];
    const moved = original.blocks.filter(where);
    const kept = original.blocks.filter((b) => !where(b));
    if (moved.length === 0) throw new Error(`split of "${from}" into "${into}" matches no blocks`);
    if (kept.length === 0) throw new Error(`split of "${from}" into "${into}" leaves "${from}" with no blocks`);
    return [
      ...cats.slice(0, index),
      { ...original, note: null, blocks: kept },
      { name: into, note: original.note, blocks: moved },
      ...cats.slice(index + 1),
    ];
  }, categories);
}

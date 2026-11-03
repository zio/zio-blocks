/**
 * The block directory on /reference/ (docs/ReferenceDirectory.jsx): one entry per docs/index.md category, in docs order,
 * with one row per block. Rows that belong to another row do not get a row of their own, they become that row's
 * `related` links:
 *
 * - a row that shares its artifact with an earlier row of the same category (Resource, Unscoped, ... ship in
 *   `zio-blocks-scope`, so they fold into Scope; Wire sits in another category and keeps its row), and
 * - the rows named by a fold (`{ into, where }`): the blocks of the category holding the block `into` that satisfy
 *   `where` (the codec rows into Schema, the config adapters into Configuration), which have their own artifacts.
 *
 * Unlike the landing catalog this keeps the docs headings (no renames or splits) because the groups must match the
 * reference sidebar. A fold naming a block that is not in the docs, or matching nothing, fails the build. The input is
 * not mutated.
 */
export function referenceGroups(categories, folds = []) {
  const foldedInto = new Map(); // block name -> parent name, per fold
  for (const { into, where } of folds) {
    const category = categories.find((c) => c.blocks.some((b) => b.name === into));
    if (!category) {
      throw new Error(`reference fold into unknown block "${into}"; check REFERENCE_FOLDS in scripts/build-catalog.mjs`);
    }
    const matched = category.blocks.filter((b) => b.name !== into && where(b));
    if (matched.length === 0) throw new Error(`reference fold into "${into}" matches no blocks`);
    for (const block of matched) foldedInto.set(block.name, into);
  }

  const related = (block) => ({ name: block.name, url: block.docsUrl, artifact: block.artifact, description: block.description });

  return categories.map((category) => {
    const rows = [];
    for (const block of category.blocks) {
      const parentName = foldedInto.get(block.name);
      const parent = parentName
        ? rows.find((r) => r.name === parentName)
        : rows.find((r) => r.artifact === block.artifact);
      if (parent) parent.related.push(related(block));
      else rows.push({ name: block.name, url: block.docsUrl, artifact: block.artifact, platforms: block.platforms, scala: block.scala, description: block.description, related: [] });
    }
    return { name: category.name, blocks: rows };
  });
}

/**
 * Within each category, keeps only the first block of every artifact. docs/index.md lists the types that ship in one
 * artifact (for example Scope, Resource, Unscoped, ... in `zio-blocks-scope`) as separate rows; on the landing page
 * one artifact is one tile. The same artifact in different categories is kept in each. A category that lost blocks
 * also loses its note, because that note described the collapsed rows ("All of these ship in ..."). The input is not
 * mutated.
 */
export function collapseSameArtifact(categories) {
  return categories.map((category) => {
    const seen = new Set();
    const blocks = category.blocks.filter((block) => {
      if (seen.has(block.artifact)) return false;
      seen.add(block.artifact);
      return true;
    });
    return blocks.length === category.blocks.length ? category : { ...category, blocks, note: null };
  });
}

/**
 * One tile per artifact across the whole page: the first block of every artifact wins, later rows that share it are
 * dropped. docs/index.md lists the types that ship in one artifact (Scope, Resource, Unscoped, ... and Wire all live
 * in `zio-blocks-scope`) as separate rows, and Wire sits in a different category; on the landing page one artifact is
 * one block. A category that lost blocks also loses its note, because that note described the collapsed rows ("All of
 * these ship in ..."), and a category left with no blocks is dropped. The input is not mutated.
 */
export function collapseSameArtifact(categories) {
  const seen = new Set();
  return categories
    .map((category) => {
      const blocks = category.blocks.filter((block) => {
        if (seen.has(block.artifact)) return false;
        seen.add(block.artifact);
        return true;
      });
      return blocks.length === category.blocks.length ? category : { ...category, blocks, note: null };
    })
    .filter((category) => category.blocks.length > 0);
}

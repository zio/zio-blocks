/** Pure helpers of docs/ReferenceDirectory.jsx (the block directory on /reference/); unit-tested. */

/** `Web & HTTP` -> `web-http`: the anchor of a group, used by the jump links. */
export function groupId(name) {
  return 'ref-' + name.toLowerCase().replace(/&/g, ' ').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

export const platformLabel = (block) => block.platforms.join(' · ');

/** `Scala 2.13 · 3.x`, or `Scala 3 only` for a block that has no Scala 2.13 build. */
export function scalaLabel(block) {
  return block.scala.includes('2.13') ? 'Scala ' + block.scala.join(' · ') : 'Scala 3 only';
}

export const isScala3Only = (block) => !block.scala.includes('2.13');

const text = (parts) => parts.join(' ').toLowerCase();
const relatedText = (r) => text([r.name, r.artifact, r.description]);

/**
 * The groups that have a block matching `query` (case-insensitive substring over the group name, a block's name,
 * artifact and description, and the same three fields of its related links), each reduced to its matching blocks.
 * A group whose own name matches keeps all its blocks. A related link of a matching block is marked `hit` when it
 * is the one that matches, so the page can point at it. An empty query returns every group unchanged.
 */
export function filterGroups(groups, query) {
  const q = query.trim().toLowerCase();
  if (!q) return groups;
  return groups
    .map((group) => {
      if (group.name.toLowerCase().includes(q)) {
        return { ...group, blocks: group.blocks.map((b) => ({ ...b, related: b.related.map((r) => ({ ...r, hit: false })) })) };
      }
      const blocks = group.blocks
        .map((b) => ({
          block: b,
          own: text([b.name, b.artifact, b.description]).includes(q),
          related: b.related.map((r) => ({ ...r, hit: relatedText(r).includes(q) })),
        }))
        .filter((m) => m.own || m.related.some((r) => r.hit))
        .map((m) => ({ ...m.block, related: m.related }));
      return { ...group, blocks };
    })
    .filter((group) => group.blocks.length > 0);
}

export const countBlocks = (groups) => groups.reduce((n, g) => n + g.blocks.length, 0);

const weight = (group) => 2 + group.blocks.reduce((n, b) => n + 3 + (b.related.length > 0 ? 1 : 0), 0);

/**
 * Two columns that keep the groups in order (the first run goes left, the rest right), cut where the heights are
 * closest (a tie goes to the later cut, so a single group is the left column), so reading down the left column and then the right one follows the order of the jump links. One group is a
 * single left column; the right one is empty then.
 */
export function splitColumns(groups) {
  let best = groups.length;
  let bestGap = Infinity;
  const total = groups.reduce((n, g) => n + weight(g), 0);
  let left = 0;
  for (let i = 0; i <= groups.length; i += 1) {
    const gap = Math.abs(total - 2 * left);
    if (gap <= bestGap) { bestGap = gap; best = i; }
    if (i < groups.length) left += weight(groups[i]);
  }
  return [groups.slice(0, best), groups.slice(best)];
}

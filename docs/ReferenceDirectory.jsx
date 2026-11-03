import React, { useEffect, useState } from 'react';
import Link from '@docusaurus/Link';
import site from '@site/src/data/site.json';
import {
  filterGroups, countBlocks, splitColumns, groupId, platformLabel, scalaLabel, isScala3Only,
} from '@site/src/lib/reference-directory.mjs';

// The block directory on /reference/: every block of docs/index.md, grouped as in the reference sidebar, with a filter
// box and a jump link per group. The data is `reference` in src/data/site.json, which scripts/build-catalog.mjs
// generates from the "All Blocks" tables of docs/index.md (rows that ship inside another row are that row's related
// links: Resource under Scope, the codecs under Schema). Styles: `.zb-ref*` in src/css/custom.css.

// Artifacts of the related links that are not the block's own (the codecs of Schema), shown in text because the chip's
// title is invisible on touch and keyboard.
const alsoShips = (block) => [...new Set(block.related.map((r) => r.artifact).filter((a) => a !== block.artifact))];

function Group({ group }) {
  const id = groupId(group.name);
  return (
    <section className="zb-ref-group" aria-labelledby={id + '-h'} id={id}>
      <div className="zb-ref-group-head">
        <h2 id={id + '-h'}>{group.name}</h2>
        <span>{group.blocks.length === 1 ? '1 block' : group.blocks.length + ' blocks'}</span>
      </div>
      {group.blocks.map((block) => (
        <div className="zb-ref-block" key={block.name}>
          <div className="zb-ref-block-head">
            <Link className="zb-ref-name" to={block.url}>{block.name}</Link>
            <div className="zb-ref-badges">
              <span className="zb-ref-badge">{platformLabel(block)}</span>
              <span className={isScala3Only(block) ? 'zb-ref-badge zb-ref-badge-accent' : 'zb-ref-badge'}>{scalaLabel(block)}</span>
            </div>
          </div>
          <p className="zb-ref-desc">{block.description}</p>
          <code className="zb-ref-artifact">{block.artifact}</code>
          {alsoShips(block).length > 0 && (
            <p className="zb-ref-also">Also ships as {alsoShips(block).join(', ')}</p>
          )}
          {block.related.length > 0 && (
            <div className="zb-ref-related">
              {block.related.map((r) => (
                <Link
                  className={r.hit ? 'zb-ref-chip zb-ref-chip-hit' : 'zb-ref-chip'}
                  key={r.name}
                  to={r.url}
                  title={r.artifact + ': ' + r.description}
                >
                  {r.name}
                </Link>
              ))}
            </div>
          )}
        </div>
      ))}
    </section>
  );
}

export default function ReferenceDirectory() {
  const [query, setQuery] = useState('');
  const all = site.reference;
  const groups = filterGroups(all, query);
  const shown = countBlocks(groups);
  const total = countBlocks(all);
  const summary = query.trim() ? shown + ' of ' + total + ' blocks match' : total + ' blocks in ' + all.length + ' areas';
  const columns = splitColumns(groups);
  // The live region announces the count once typing pauses, not on every keystroke.
  const [announced, setAnnounced] = useState(summary);
  useEffect(() => {
    const t = setTimeout(() => setAnnounced(summary), 500);
    return () => clearTimeout(t);
  }, [summary]);

  return (
    <div className="zb-ref">
      <div className="zb-ref-filter" role="search">
        <label htmlFor="zb-ref-q">Filter blocks</label>
        <input
          id="zb-ref-q"
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Try jwt, chunk, or avro"
          autoComplete="off"
        />
      </div>
      {groups.length > 0 && (
        <nav className="zb-ref-jump" aria-label="Areas">
          {groups.map((g) => (
            <a key={g.name} href={'#' + groupId(g.name)}>
              {g.name} <span>{g.blocks.length}</span>
            </a>
          ))}
        </nav>
      )}
      <p className="zb-ref-summary" role="status" aria-atomic="true">{announced}</p>
      {shown === 0 ? (
        <p className="zb-ref-empty">No block matches that. Try a shorter name, such as <code>stream</code> or <code>http</code>.</p>
      ) : (
        <div className="zb-ref-columns">
          {columns.map((column, i) => (
            <div className="zb-ref-column" key={i}>
              {column.map((group) => <Group group={group} key={group.name} />)}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

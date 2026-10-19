import React from 'react';
import Link from '@docusaurus/Link';
import site from '../../data/site.json';
import {titleCase} from '../../lib/title-case.mjs';

// The hero's block wall: every block in the catalog as one equal square module, in catalog order, dealt into columns.
// Each column starts half a module lower than the one to its right, so the columns fall like the logo's stair (top
// right to bottom left). The wall bleeds off the right edge and is decorative: hidden from assistive technology and
// out of the tab order, since the catalog lists the same blocks with their docs links.
const COLUMNS = 4;
const blocks = site.categories.flatMap((c) => c.blocks);
const columns = Array.from({length: COLUMNS}, (_, c) => blocks.filter((_, i) => i % COLUMNS === c));

export default function ModuleField() {
  return (
    <div className="lp-field" aria-hidden="true">
      {columns.map((column, c) => (
        <div key={c} className="lp-field-col" style={{'--step': COLUMNS - 1 - c}}>
          {column.map((b, r) => (
            <Link key={b.artifact} className="lp-sq" to={b.docsUrl} tabIndex={-1} style={{'--i': r + c}}>
              {titleCase(b.name)}
            </Link>
          ))}
        </div>
      ))}
    </div>
  );
}

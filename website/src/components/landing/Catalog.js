import React from 'react';
import Link from '@docusaurus/Link';
import site from '../../data/site.json';
import {renderInline} from '../../lib/inline.mjs';
import {packRows} from '../../lib/rows.mjs';
import {titleCase} from '../../lib/title-case.mjs';

const COLUMNS = 3;
const html = (markup) => ({__html: markup});

export default function Catalog() {
  const rows = packRows(site.categories, COLUMNS);
  return (
    <section id="catalog" className="lp-section" aria-labelledby="catalog-title">
      <div className="lp-wrap">
        <p className="lp-label">{'03   Block Catalog'}</p>
        <h2 id="catalog-title">Take Only What You Need</h2>
        <p className="lp-sub">
          Each block is a separate artifact under <code>dev.zio</code>. Copy the artifact name, add it to your build, and use it.
        </p>

        {rows.map((row, r) => (
          <div key={r} className="lp-row" {...(row.length > 1 ? {'data-shared': ''} : {})}>
            {row.map((c) => (
              <section key={c.name} className="lp-cat" data-span={Math.min(c.blocks.length, COLUMNS)}>
                <h3>{titleCase(c.name)}</h3>
                {c.note && <p className="lp-note" dangerouslySetInnerHTML={html(renderInline(c.note))} />}
                <ul className="lp-grid">
                  {c.blocks.map((b) => (
                    <li key={b.artifact} className="lp-tile">
                      <h4><Link to={b.docsUrl}>{titleCase(b.name)}</Link></h4>
                      <p className="lp-desc" dangerouslySetInnerHTML={html(renderInline(b.description))} />
                      <code className="lp-artifact">{b.artifact}</code>
                      <div className="lp-foot">
                        <p className="lp-badges">{`${b.platforms.join(' · ')} / Scala ${b.scala.join(' · ')}`}</p>
                        <Link className="lp-learn" to={b.docsUrl} aria-label={`Learn More about ${titleCase(b.name)}`}>Learn More</Link>
                      </div>
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
        ))}
      </div>
    </section>
  );
}

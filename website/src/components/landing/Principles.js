import React from 'react';
import site from '../../data/site.json';
import {titleCase} from '../../lib/title-case.mjs';

export default function Principles() {
  return (
    <section id="principles" className="lp-section" aria-labelledby="principles-title">
      <div className="lp-wrap">
        <p className="lp-label">01 &nbsp; Principles</p>
        <h2 id="principles-title">Use What You Need, Nothing More</h2>
        <ol className="lp-principles">
          {site.principles.map((p, i) => (
            <li key={p.name}>
              <span className="lp-n">{`0${i + 1}`}</span>{' '}
              <strong>{titleCase(p.name)}</strong>{' '}
              <span className="lp-t">{p.text}</span>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}

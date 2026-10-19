import React from 'react';
import site from '../../data/site.json';
import {titleCase} from '../../lib/title-case.mjs';

// On wide screens the five principles are the five blocks of the logo's Z on its ground (landing.css, `.lp-z`): the
// first and last are the bars, the middle three the stair. The heading sits in the ground the stair leaves empty.
export default function Principles() {
  return (
    <section id="principles" className="lp-section lp-screen" aria-labelledby="principles-title">
      <div className="lp-wrap">
        <p className="lp-label"><span className="lp-num">01</span> Principles</p>
        <div className="lp-z">
          <h2 id="principles-title">Use What You Need, Nothing More</h2>
          <ol className="lp-principles" role="list">
            {site.principles.map((p, i) => (
              <li key={p.name}>
                <span className="lp-n">{`0${i + 1}`}</span>{' '}
                <strong>{titleCase(p.name)}</strong>{' '}
                <span className="lp-t">{p.text}</span>
              </li>
            ))}
          </ol>
        </div>
      </div>
    </section>
  );
}

import React from 'react';
import Link from '@docusaurus/Link';
import site from '../../data/site.json';
import {renderInline} from '../../lib/inline.mjs';
import {titleCase} from '../../lib/title-case.mjs';
import OnboardAgentButton from '../OnboardAgentButton';
import ModuleField from './ModuleField';

// The docs tagline is "<headline>—no effect system required."; the headline is the part before the first em dash.
const dash = site.tagline.indexOf('—');
const base = (dash === -1 ? site.tagline : site.tagline.slice(0, dash)).trim();
// The headline is title-cased. A non-breaking space (U+00A0) keeps "Building Blocks" on one line.
const headline = titleCase(base).replace('Building Blocks', 'Building\u00a0Blocks');

export default function Hero() {
  return (
    <section id="hero" className="lp-hero" aria-labelledby="hero-title">
      <ModuleField />
      <div className="lp-wrap lp-inner">
        <div className="lp-copy">
          <h1 id="hero-title">{headline}</h1>
          <p className="lp-lead" dangerouslySetInnerHTML={{__html: renderInline(site.lead)}} />

          <div className="lp-onboard">
            <OnboardAgentButton tone="onDark" />
          </div>

          <p className="lp-cta">
            <Link className="lp-btn lp-btn-primary" to="/overview">Get started</Link>
            <a className="lp-btn lp-btn-ghost" href="https://github.com/zio/zio-blocks">GitHub</a>
          </p>

          <p className="lp-stack">Works with {site.compatibility.join(' · ')}</p>
        </div>
      </div>
    </section>
  );
}

import React, {useEffect, useRef, useState} from 'react';
import {Highlight} from 'prism-react-renderer';
import site from '../../data/site.json';
import {renderCode, renderInline} from '../../lib/inline.mjs';
import {titleCase} from '../../lib/title-case.mjs';
import prismThemes from '../../prism-themes';

// JSON is built into the Schema block; every block in the "Codecs" category adds one more format.
const codecs = site.categories.find((c) => c.name === 'Codecs')?.blocks.map((b) => b.name.replace(/ Codec$/, '')) ?? [];
const formats = ['JSON', ...codecs];

function Code({source, lang}) {
  return (
    <div className="lp-codecard">
      <Highlight theme={prismThemes.dark} code={source} language={lang}>
        {({className, style, tokens, getLineProps, getTokenProps}) => (
          <pre className={className} style={style}>
            {tokens.map((line, i) => (
              <div key={i} {...getLineProps({line})}>
                {line.map((token, key) => (
                  <span key={key} {...getTokenProps({token})} />
                ))}
              </div>
            ))}
          </pre>
        )}
      </Highlight>
    </div>
  );
}

const html = (markup) => ({__html: markup});

export default function DeepDives() {
  const [active, setActive] = useState(0);
  // Server-rendered HTML shows every panel and no tablist; after hydration the tablist appears and only the
  // selected panel stays visible, so the content is readable without JavaScript.
  const [hydrated, setHydrated] = useState(false);
  const tabs = useRef([]);
  useEffect(() => setHydrated(true), []);

  const select = (index, focus = false) => {
    setActive(index);
    if (focus) tabs.current[index]?.focus();
  };
  const onKeyDown = (event, i) => {
    const last = site.deepDives.length - 1;
    const target = {ArrowRight: i === last ? 0 : i + 1, ArrowLeft: i === 0 ? last : i - 1, Home: 0, End: last}[event.key];
    if (target === undefined) return;
    event.preventDefault();
    select(target, true);
  };

  return (
    <section id="deep-dives" className="lp-section" aria-labelledby="deep-dives-title">
      <div className="lp-wrap">
        <p className="lp-label">02 &nbsp; Deep Dives</p>
        <h2 id="deep-dives-title">Four Blocks, in Code</h2>

        <div className="lp-tablist" role="tablist" aria-label="Deep dives" hidden={!hydrated}>
          {site.deepDives.map((d, i) => (
            <button
              key={d.id}
              ref={(el) => { tabs.current[i] = el; }}
              role="tab"
              type="button"
              id={`tab-${d.id}`}
              aria-controls={`panel-${d.id}`}
              aria-selected={i === active}
              tabIndex={i === active ? 0 : -1}
              onClick={() => select(i)}
              onKeyDown={(e) => onKeyDown(e, i)}>
              {titleCase(d.title)}
            </button>
          ))}
        </div>

        {site.deepDives.map((d, i) => (
          <div
            key={d.id}
            className="lp-panel"
            role="tabpanel"
            id={`panel-${d.id}`}
            aria-labelledby={`tab-${d.id}`}
            hidden={hydrated && i !== active}>
            <h3 className={hydrated ? 'lp-vh' : undefined}>{titleCase(d.title)}</h3>
            <p className="lp-intro" dangerouslySetInnerHTML={html(renderInline(d.intro))} />
            <div className="lp-cols">
              <div className="lp-col">
                <p className="lp-label">The Problem</p>
                {d.problem.paragraphs.map((p, k) => <p key={k} dangerouslySetInnerHTML={html(renderInline(p))} />)}
                {d.problem.code && <Code source={d.problem.code.source} lang={d.problem.code.lang} />}
              </div>
              <div className="lp-col">
                <p className="lp-label">The Solution</p>
                {d.solution.paragraphs.map((p, k) => <p key={k} dangerouslySetInnerHTML={html(renderInline(p))} />)}
                <Code source={d.solution.code.source} lang={d.solution.code.lang} />
              </div>
            </div>
            {d.id === 'schema' && formats.length > 1 && (
              <div className="lp-formats">
                <p className="lp-label">One Schema, Many Formats</p>
                <ul>{formats.map((f) => <li key={f} className="lp-chip">{f}</li>)}</ul>
              </div>
            )}
            <ul className="lp-more">
              {d.learnMore.map((l) => (
                <li key={l.url}>
                  <a href={l.url} dangerouslySetInnerHTML={html(renderCode(l.title))} /> — <span dangerouslySetInnerHTML={html(renderInline(l.description))} />
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </section>
  );
}

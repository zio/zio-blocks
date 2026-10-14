import React, {useEffect, useState} from 'react';
import {RAIL} from '../../lib/rail.mjs';
import {scrollToSection} from './scrollToSection';

// A section is "current" while it crosses a thin band just above the middle of the viewport; that works for
// sections shorter and taller than the screen alike. Without JavaScript the links still jump to their sections
// and the first one stays marked.
const BAND = '-45% 0px -50% 0px';

// Rail links animate like a wheel gesture; without JavaScript they remain plain anchor jumps.
function go(e, id) {
  const el = document.getElementById(id);
  if (!el) return;
  e.preventDefault();
  scrollToSection(el);
  window.history.replaceState(null, '', `#${id}`);
}

export default function SectionRail() {
  const [active, setActive] = useState(RAIL[0].id);

  useEffect(() => {
    if (typeof IntersectionObserver === 'undefined') return undefined;
    const observer = new IntersectionObserver(
      (entries) => entries.forEach((e) => { if (e.isIntersecting) setActive(e.target.id); }),
      {rootMargin: BAND},
    );
    RAIL.forEach(({id}) => {
      const el = document.getElementById(id);
      if (el) observer.observe(el);
    });
    return () => observer.disconnect();
  }, []);

  return (
    <nav className="lp-rail" aria-label="Sections">
      {RAIL.map(({id, label}) => (
        <a
          key={id}
          className="lp-rail-link"
          href={`#${id}`}
          onClick={(e) => go(e, id)}
          aria-label={label}
          data-label={label}
          {...(id === active ? {'aria-current': 'true'} : {})}
        />
      ))}
    </nav>
  );
}

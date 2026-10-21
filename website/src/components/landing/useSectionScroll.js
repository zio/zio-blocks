import {useEffect} from 'react';
import {sectionStep} from '../../lib/sectionStep.mjs';
import {isLocked, navHeight, reducedMotion, scrollToSection} from './scrollToSection';

// One scroll gesture moves to the next landing section (fullPage-style). A section taller than the screen scrolls
// natively until its edge, then the next gesture advances. Touch is left to the browser, and nothing is intercepted
// when the visitor prefers reduced motion.
const EPS = 4;
const KEY_DIR = {ArrowDown: 1, PageDown: 1, ArrowUp: -1, PageUp: -1};
// Elements that own the arrow keys and Space themselves.
const OWNS_KEYS = 'input, textarea, select, button, a, summary, [contenteditable], [role="tab"], [role="tablist"]';

/** True when `node` sits inside a nested scroller (a code block, say) that can still move in `dir`. */
function nestedScrollerCanMove(node, root, dir) {
  for (let el = node; el && el !== root; el = el.parentElement) {
    const {overflowY} = getComputedStyle(el);
    if ((overflowY === 'auto' || overflowY === 'scroll') && el.scrollHeight > el.clientHeight) {
      const atEdge = dir > 0 ? el.scrollTop + el.clientHeight >= el.scrollHeight - EPS : el.scrollTop <= EPS;
      if (!atEdge) return true;
    }
  }
  return false;
}

export default function useSectionScroll(ids) {
  useEffect(() => {
    const sections = () => ids.map((id) => document.getElementById(id)).filter(Boolean);

    const step = (dir) => {
      const list = sections();
      const target = sectionStep({
        dir,
        rects: list.map((el) => el.getBoundingClientRect()),
        navH: navHeight(),
        vh: window.innerHeight,
      });
      if (target === null) return false;
      scrollToSection(list[target]);
      return true;
    };

    const onWheel = (e) => {
      if (e.ctrlKey || reducedMotion()) return; // pinch-zoom, or the visitor opted out of motion
      if (isLocked()) {
        e.preventDefault(); // swallow trackpad inertia from the previous gesture
        return;
      }
      if (Math.abs(e.deltaX) > Math.abs(e.deltaY)) return;
      const dir = e.deltaY > EPS ? 1 : e.deltaY < -EPS ? -1 : 0;
      if (!dir || nestedScrollerCanMove(e.target, document.body, dir)) return;
      if (step(dir)) e.preventDefault();
    };

    const onKeyDown = (e) => {
      if (e.defaultPrevented || e.ctrlKey || e.metaKey || e.altKey || reducedMotion() || isLocked()) return;
      if (e.target instanceof Element && e.target.closest(OWNS_KEYS)) return;
      const dir = e.key === ' ' ? (e.shiftKey ? -1 : 1) : KEY_DIR[e.key];
      if (dir && step(dir)) e.preventDefault();
    };

    window.addEventListener('wheel', onWheel, {passive: false});
    window.addEventListener('keydown', onKeyDown);
    return () => {
      window.removeEventListener('wheel', onWheel);
      window.removeEventListener('keydown', onKeyDown);
    };
  }, [ids]);
}

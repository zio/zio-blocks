// One way to move between landing sections, shared by the wheel/keyboard hook and the rail links, so both animate
// the same way and a gesture fired mid-flight cannot stack a second move.
export const LOCK_MS = 800;

let lockUntil = 0;

export const reducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/** The sticky navbar's real height, so a section lands under it, not behind it. */
export const navHeight = () => document.querySelector('.navbar')?.offsetHeight ?? 60;

export const isLocked = () => Date.now() < lockUntil;

export function scrollToSection(el) {
  lockUntil = Date.now() + LOCK_MS;
  const top = el.getBoundingClientRect().top + window.pageYOffset - navHeight();
  window.scrollTo({ top, behavior: reducedMotion() ? 'auto' : 'smooth' });
}

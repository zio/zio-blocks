import { matches } from '../lib/filter.mjs';
import './copy.js';

const root = document.querySelector('[data-catalog]');
if (root) {
  const tiles = [...root.querySelectorAll('[data-tile]')].map((el) => ({
    el,
    category: el.dataset.category,
    platforms: el.dataset.platforms.split(' '),
    scala: el.dataset.scala.split(' '),
  }));
  const sections = [...root.querySelectorAll('[data-category-section]')];
  const status = root.querySelector('[data-status]');
  const empty = root.querySelector('[data-empty]');
  const state = { category: null, platform: null, scala: null };

  function apply() {
    let shown = 0;
    for (const tile of tiles) {
      const ok = matches(tile, state);
      tile.el.hidden = !ok;
      if (ok) shown += 1;
    }
    for (const section of sections) {
      section.hidden = [...section.querySelectorAll('[data-tile]')].every((el) => el.hidden);
    }
    status.textContent = `${shown} of ${tiles.length} blocks`;
    empty.hidden = shown !== 0;
  }

  for (const group of root.querySelectorAll('[data-filter]')) {
    group.addEventListener('click', (e) => {
      const button = e.target.closest('button');
      if (!button) return;
      state[group.dataset.filter] = button.dataset.value === '' ? null : button.dataset.value;
      for (const b of group.querySelectorAll('button')) b.setAttribute('aria-pressed', String(b === button));
      apply();
    });
  }
  apply();
}

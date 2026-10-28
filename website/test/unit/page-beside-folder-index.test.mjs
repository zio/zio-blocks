import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readdirSync } from 'node:fs';
import { basename, dirname, extname, join, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

// Docusaurus gives `docs/X.md` and `docs/X/index.md` the same URL, `/X`.
// The build keeps one of them and drops the other without an error, so a
// stale copy can silently replace the page that the sidebar points at.
const docs = fileURLToPath(new URL('../../../docs', import.meta.url));

const pages = readdirSync(docs, { recursive: true })
  .filter((rel) => ['.md', '.mdx'].includes(extname(rel)))
  .filter((rel) => !rel.startsWith(`dev${sep}`));

test('the docs tree has pages to scan', () => {
  assert.ok(pages.includes(join('reference', 'datastar', 'index.md')));
});

test('no docs page sits next to a folder index with the same name', () => {
  const clashes = [];
  for (const rel of pages) {
    const name = basename(rel, extname(rel));
    if (name === 'index') continue;
    const folder = join(docs, dirname(rel), name);
    for (const index of ['index.md', 'index.mdx'])
      if (existsSync(join(folder, index)))
        clashes.push(`docs/${rel} sits next to docs/${join(dirname(rel), name, index)}`);
  }
  assert.deepEqual(clashes, []);
});

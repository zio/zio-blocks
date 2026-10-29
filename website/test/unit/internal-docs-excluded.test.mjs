import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

// These folders and files hold internal notes, plans and reports. They stay in
// the repo, but they must never be published on the docs site, so each one has
// to stay in the Docusaurus `exclude` list. The llms plugin keeps its own
// `ignoreFiles` list, which controls llms.txt, llms-full.txt and the per-page
// markdown copies, so each one has to stay in that list too.
const config = readFileSync(fileURLToPath(new URL('../../docusaurus.config.js', import.meta.url)), 'utf8');

// Read each array from its own line, so a pattern in one list cannot satisfy the other.
const listEntries = (key) => {
  const match = config.match(new RegExp(`^\\s*${key}: \\[(.*)\\],$`, 'm'));
  assert.ok(match, `docusaurus.config.js must have a single-line \`${key}: [...]\` array`);
  return [...match[1].matchAll(/'([^']+)'/g)].map((m) => m[1]);
};

const excludeEntries = listEntries('exclude');
const ignoreFilesEntries = listEntries('ignoreFiles');

for (const entry of ['plans/**', 'dev/**', 'superpowers/**', 'undocumented-report.md']) {
  test(`the docs site excludes ${entry}`, () => {
    assert.ok(excludeEntries.includes(entry), `docusaurus.config.js must list '${entry}' in the docs exclude array`);
  });

  test(`the llms plugin ignores ${entry}`, () => {
    assert.ok(
      ignoreFilesEntries.includes(entry),
      `docusaurus.config.js must list '${entry}' in the docusaurus-plugin-llms ignoreFiles array`,
    );
  });
}

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

// These folders and files hold internal notes, plans and reports. They stay in
// the repo, but they must never be published on the docs site, so each one has
// to stay in the Docusaurus `exclude` list.
const config = readFileSync(fileURLToPath(new URL('../../docusaurus.config.js', import.meta.url)), 'utf8');

for (const entry of ['plans/**', 'dev/**', 'superpowers/**', 'undocumented-report.md'])
  test(`the docs site excludes ${entry}`, () => {
    assert.ok(config.includes(`'${entry}'`), `docusaurus.config.js must list '${entry}' in the docs exclude array`);
  });

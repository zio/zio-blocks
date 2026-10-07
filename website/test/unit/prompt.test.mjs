import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { promptFor } from '../../src/lib/prompt.mjs';

const expected = (origin) =>
  `Fetch ${origin}/start.md and follow the instructions to set up my environment for ZIO Blocks development.`;

test('promptFor uses the configured site origin', () => {
  assert.equal(promptFor('https://example.dev'), expected('https://example.dev'));
});

test('promptFor drops trailing slashes', () => {
  assert.equal(promptFor('https://example.dev/'), expected('https://example.dev'));
  assert.equal(promptFor('http://localhost:3000//'), expected('http://localhost:3000'));
});

test('static/start.md names only real skills and has the agent sections', () => {
  const md = readFileSync(new URL('../../static/start.md', import.meta.url), 'utf8');
  assert.match(md, /^# Get your agent ready for ZIO Blocks/);
  assert.ok(md.includes('claude plugin install zio-skills@ziogenetics'));
  assert.ok(md.includes('npx skills add zio/zio-skills --skill zio-knowledge'));
  assert.equal(/zio-blocks-knowledge|mcp\.zio/.test(md), false);
});

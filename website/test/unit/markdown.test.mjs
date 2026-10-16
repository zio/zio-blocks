import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  CatalogError, splitSections, fences, paragraphs, proseBeforeFence, parseTable, bullets,
} from '../../scripts/lib/markdown.mjs';

const DOC = [
  '---', 'id: x', '---', '', '**Tagline**', '',
  '## One', 'intro', '', '### Sub A', 'a body', '```scala', '## not a heading', '```',
  '### Sub B', 'b body', '',
  '## Two', 'two body',
].join('\n');

test('splitSections splits on exact level and ignores headings inside fences', () => {
  const top = splitSections(DOC, 2);
  assert.equal(top.preamble, '---\nid: x\n---\n\n**Tagline**\n');
  assert.deepEqual(top.sections.map((s) => s.title), ['One', 'Two']);
  const sub = splitSections(top.sections[0].body, 3);
  assert.equal(sub.preamble, 'intro\n');
  assert.deepEqual(sub.sections, [
    { title: 'Sub A', body: 'a body\n```scala\n## not a heading\n```' },
    { title: 'Sub B', body: 'b body\n' },
  ]);
});

test('splitSections ends a section at a higher-level heading', () => {
  const doc = '### Orphan\nx\n## Top\ny\n### Child\nz';
  const sub = splitSections(doc, 3);
  assert.deepEqual(sub.sections.map((s) => s.title), ['Orphan', 'Child']);
  assert.equal(sub.sections[0].body, 'x');
});

test('fences returns lang without mdoc modifiers, source verbatim', () => {
  const body = 'text\n```scala mdoc:compile-only\nval a = 1\n  val b = 2\n```\n````\nnested ``` ok\n````\n';
  assert.deepEqual(fences(body), [
    { info: 'scala mdoc:compile-only', lang: 'scala', source: 'val a = 1\n  val b = 2' },
    { info: '', lang: 'text', source: 'nested ``` ok' },
  ]);
});

test('paragraphs joins wrapped lines and drops empties', () => {
  assert.deepEqual(paragraphs('one\ntwo\n\n\nthree\n---\n'), ['one two', 'three']);
});

test('proseBeforeFence stops at the first fence', () => {
  assert.equal(proseBeforeFence('before\n```x\ncode\n```\nafter'), 'before');
  assert.equal(proseBeforeFence('only prose'), 'only prose');
});

test('parseTable returns header and rows, null when there is no table', () => {
  const body = 'note\n\n| A | B |\n|---|:-:|\n| 1 | two words |\n| 3 | 4 |\n\n---\n';
  assert.deepEqual(parseTable(body), { header: ['A', 'B'], rows: [['1', 'two words'], ['3', '4']] });
  assert.equal(parseTable('no table here'), null);
});

test('parseTable rejects a row with the wrong number of cells', () => {
  assert.throws(
    () => parseTable('| A | B |\n|---|---|\n| only one |'),
    (e) => e instanceof CatalogError && e.message === 'table row "| only one |" has 1 cells, expected 2',
  );
});

test('bullets returns only top-level dash items', () => {
  assert.deepEqual(bullets('intro\n- one\n- two\n  - nested\nend'), ['one', 'two']);
});

test('bullets treats * like - and skips fenced lines', () => {
  assert.deepEqual(bullets('* a\n- b\n```\n- fenced\n* fenced\n```\n- c'), ['a', 'b', 'c']);
});

test('parseTable ignores pipe lines inside fences', () => {
  const body = '```\n| X | Y |\n|---|---|\n| 1 | 2 |\n```\n\n| A | B |\n|---|---|\n| 3 | 4 |';
  assert.deepEqual(parseTable(body), { header: ['A', 'B'], rows: [['3', '4']] });
  assert.equal(parseTable('```\n| X | Y |\n|---|---|\n```'), null);
});

test('parseTable stops at the first non-pipe line and rejects a second table', () => {
  assert.deepEqual(parseTable('| A |\n|---|\n| 1 |\ntext\n'), { header: ['A'], rows: [['1']] });
  assert.throws(
    () => parseTable('| A |\n|---|\n| 1 |\n\n| B |\n|---|\n| 2 |'),
    (e) => e instanceof CatalogError && e.message === 'body has more than one table',
  );
});

test('parseTable treats \\| in a cell as a literal pipe', () => {
  assert.deepEqual(parseTable('| A | B |\n|---|---|\n| a \\| b | `x\\|y` |'), { header: ['A', 'B'], rows: [['a | b', '`x|y`']] });
});

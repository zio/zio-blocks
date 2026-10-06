import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { CatalogError } from '../../scripts/lib/markdown.mjs';
import { docsUrl, firstSentence, parseIndex } from '../../scripts/lib/parse-index.mjs';

const dive = (title, problemCode, extra = '') => `## ${title}

${title} intro sentence. More words.

### The Problem

${title} problem prose:

${problemCode}

### The Solution

${title} solution prose.

\`\`\`scala mdoc
val ${title.toLowerCase()} = 1  // comment
\`\`\`

### Learn More

- [${title} reference](./reference/${title.toLowerCase()}.md) — the full API
- [\`${title.toLowerCase()}-examples\`](https://github.com/zio/zio-blocks/blob/main/x.scala) — a demo ${extra}

---
`;

const GOOD = `---
id: index
title: "ZIO Blocks"
---

**Modular blocks—no effect system required.**

@PROJECT_BADGES@

## What Is ZIO Blocks?

First paragraph.

The philosophy is simple. Use what you need.

## Core Principles

- **Zero Lock-In**: No dependency on any effect system. Use a block with your stack.
- **Modular**: Each block is a separate artifact.

## Getting Started

\`\`\`scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"
\`\`\`

\`\`\`scala mdoc:compile-only
val alice = Person("Alice", 30)
val jsonStr = alice.toJsonString                      // {"name":"Alice","age":30}
\`\`\`

## All Blocks

### Meta Programming

JSON is built in.

| Block | Artifact | Platform | Scala | Description |
|-------|----------|----------|-------|-------------|
| [Schema](./reference/schema/index.md) | \`zio-blocks-schema\` | JVM · JS | 2.13 · 3.x | Type-safe schemas with \`derive\` & <codecs> |
| [Avro Codec](./reference/schema/built-in-codecs/avro.md) | \`zio-blocks-schema-avro\` | JVM | 2.13 · 3.x | Avro binary |

### Web & HTTP

| Block | Artifact | Platform | Scala | Description |
|-------|----------|----------|-------|-------------|
| [Mux](./reference/mux.mdx) | \`zio-blocks-mux\` | JVM · JS | 3.x | Multiplexer |

---

${dive('Schema', '```javascript\nconst data = await res.json();\n```')}
${dive('Scope', '```scala\nval db = open()\n```')}
${dive('Async', '')}
${dive('SQL', '')}
## Compatibility

| Stack | Compatible |
|-------|------------|
| ZIO 2.x | ✅ |
| Kyo | ✅ |
| Nope | ❌ |

## Guides

- [Getting Started with Async](./guides/async-getting-started.md) - Create and compose
- [Migrating from ZIO Schema](./guides/zio-schema-migration.md) - Step-by-step port
`;

test('docsUrl maps repo-relative links to zio.dev URLs', () => {
  assert.equal(docsUrl('./reference/schema/index.md'), 'https://zio.dev/zio-blocks/reference/schema/');
  assert.equal(docsUrl('./reference/mux.mdx'), 'https://zio.dev/zio-blocks/reference/mux');
  assert.equal(docsUrl('./reference/ringbuffer/index.mdx'), 'https://zio.dev/zio-blocks/reference/ringbuffer/');
  assert.equal(docsUrl('./guides/zio-schema-migration.md'), 'https://zio.dev/zio-blocks/guides/zio-schema-migration');
  assert.equal(docsUrl('./reference/schema/built-in-codecs/avro.md'), 'https://zio.dev/zio-blocks/reference/schema/built-in-codecs/avro');
});

test('docsUrl rejects anything that is not a ./ markdown path', () => {
  for (const bad of ['https://example.com/a.md', '../a.md', './a.txt', 'reference/a.md']) {
    assert.throws(() => docsUrl(bad), (e) => e instanceof CatalogError && e.message === `unmapped docs link "${bad}"`);
  }
});

test('firstSentence keeps version numbers and file names intact', () => {
  assert.equal(
    firstSentence('Most blocks build for Scala.js and Scala 2.13 and 3.x. The catalog records exceptions.'),
    'Most blocks build for Scala.js and Scala 2.13 and 3.x.',
  );
  assert.equal(firstSentence('No terminator'), 'No terminator');
});

test('parseIndex extracts the header content', () => {
  const p = parseIndex(GOOD);
  assert.equal(p.tagline, 'Modular blocks—no effect system required.');
  assert.equal(p.lead, 'The philosophy is simple. Use what you need.');
  assert.deepEqual(p.principles, [
    { name: 'Zero Lock-In', text: 'No dependency on any effect system.' },
    { name: 'Modular', text: 'Each block is a separate artifact.' },
  ]);
  assert.deepEqual(p.hero, {
    install: 'libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"',
    jsonCode: 'val jsonStr = alice.toJsonString',
    jsonResult: '{"name":"Alice","age":30}',
  });
  assert.deepEqual(p.compatibility, ['ZIO 2.x', 'Kyo']);
});

test('parseIndex extracts the catalog with exact platform and Scala arrays', () => {
  assert.deepEqual(parseIndex(GOOD).categories, [
    {
      name: 'Meta Programming',
      note: 'JSON is built in.',
      blocks: [
        {
          name: 'Schema', docsUrl: 'https://zio.dev/zio-blocks/reference/schema/', artifact: 'zio-blocks-schema',
          platforms: ['JVM', 'JS'], scala: ['2.13', '3.x'], description: 'Type-safe schemas with `derive` & <codecs>',
        },
        {
          name: 'Avro Codec', docsUrl: 'https://zio.dev/zio-blocks/reference/schema/built-in-codecs/avro',
          artifact: 'zio-blocks-schema-avro', platforms: ['JVM'], scala: ['2.13', '3.x'], description: 'Avro binary',
        },
      ],
    },
    {
      name: 'Web & HTTP',
      note: null,
      blocks: [
        {
          name: 'Mux', docsUrl: 'https://zio.dev/zio-blocks/reference/mux', artifact: 'zio-blocks-mux',
          platforms: ['JVM', 'JS'], scala: ['3.x'], description: 'Multiplexer',
        },
      ],
    },
  ]);
});

test('parseIndex extracts the four deep dives', () => {
  const dives = parseIndex(GOOD).deepDives;
  assert.deepEqual(dives.map((d) => d.id), ['schema', 'scope', 'async', 'sql']);
  assert.deepEqual(dives[0], {
    id: 'schema', title: 'Schema', intro: 'Schema intro sentence. More words.',
    problem: { paragraphs: ['Schema problem prose:'], code: { info: 'javascript', lang: 'javascript', source: 'const data = await res.json();' } },
    solution: { paragraphs: ['Schema solution prose.'], code: { info: 'scala mdoc', lang: 'scala', source: 'val schema = 1  // comment' } },
    learnMore: [
      { title: 'Schema reference', url: 'https://zio.dev/zio-blocks/reference/schema', description: 'the full API' },
      { title: '`schema-examples`', url: 'https://github.com/zio/zio-blocks/blob/main/x.scala', description: 'a demo' },
    ],
  });
  assert.equal(dives[2].problem.code, null);
});

test('parseIndex extracts guides', () => {
  assert.deepEqual(parseIndex(GOOD).guides, [
    { title: 'Getting Started with Async', url: 'https://zio.dev/zio-blocks/guides/async-getting-started', description: 'Create and compose' },
    { title: 'Migrating from ZIO Schema', url: 'https://zio.dev/zio-blocks/guides/zio-schema-migration', description: 'Step-by-step port' },
  ]);
});

const failsWith = (md, message) =>
  assert.throws(() => parseIndex(md), (e) => e instanceof CatalogError && e.message === message);

test('parseIndex fails loudly, naming the offender', () => {
  failsWith(GOOD.replace('| JVM · JS | 2.13 · 3.x | Type-safe', '| JVM · Native | 2.13 · 3.x | Type-safe'),
    'Schema: unknown platform "Native"');
  failsWith(GOOD.replace('| JVM | 2.13 · 3.x | Avro binary |', '| JVM | 2.13 · 2.12 | Avro binary |'),
    'Avro Codec: unknown Scala version "2.12"');
  failsWith(GOOD.replace('| `zio-blocks-schema-avro` |', '| zio-blocks-schema-avro |'),
    'Avro Codec: artifact cell "zio-blocks-schema-avro" is not a `code` span');
  failsWith(GOOD.replace('[Avro Codec]', '[Schema]'), 'block "Schema" is listed twice');
  failsWith(GOOD.replace('(./reference/mux.mdx)', '(https://example.com/mux)'), 'unmapped docs link "https://example.com/mux"');
  failsWith(GOOD.replace('| Block | Artifact | Platform | Scala | Description |\n|-------|----------|----------|-------|-------------|\n| [Mux]',
    '| Block | Artifact | Platform | Scala |\n|-------|----------|----------|-------|\n| [Mux]').replace('| JVM · JS | 3.x | Multiplexer |', '| JVM · JS | 3.x |'),
    'category "Web & HTTP": expected columns Block | Artifact | Platform | Scala | Description');
  failsWith(GOOD.replace('## Core Principles', '## Core Principle'), 'docs/index.md has no "## Core Principles" section');
  failsWith(GOOD.replace('toJsonString', 'toJson'), 'Getting Started: no code line containing toJsonString with a // result comment');
  failsWith(GOOD.replace('### The Solution\n\nSchema solution prose.\n\n```scala mdoc\nval schema = 1  // comment\n```', '### The Solution\n\nSchema solution prose.'),
    'Schema: "The Solution" has no code block');
});

test('parseIndex fails when a category has no table or no rows', () => {
  const head = '| Block | Artifact | Platform | Scala | Description |\n|-------|----------|----------|-------|-------------|\n';
  const row = '| [Mux](./reference/mux.mdx) | `zio-blocks-mux` | JVM · JS | 3.x | Multiplexer |\n';
  assert.ok(GOOD.includes(head + row));
  failsWith(GOOD.replace(head + row, ''), 'category "Web & HTTP" has no block table');
  failsWith(GOOD.replace(head + row, head), 'category "Web & HTTP" has no blocks');
});

test('parseIndex accepts the real docs/index.md', () => {
  const real = parseIndex(readFileSync(new URL('../../../docs/index.md', import.meta.url), 'utf8'));
  assert.deepEqual(real.deepDives.map((d) => d.id), ['schema', 'scope', 'async', 'sql']);
  assert.ok(real.categories.length > 0);
  for (const b of real.categories.flatMap((c) => c.blocks)) {
    assert.match(b.artifact, /^zio-blocks-[a-z0-9-]+$/);
    assert.match(b.docsUrl, /^https:\/\/zio\.dev\/zio-blocks\//);
    assert.ok(b.platforms.length > 0 && b.scala.length > 0 && b.description !== '');
  }
  assert.ok(real.hero.install.includes('@VERSION@'));
  assert.equal(real.hero.jsonResult, '{"name":"Alice","age":30}');
});

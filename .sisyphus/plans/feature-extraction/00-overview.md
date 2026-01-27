# Feature Extraction from ZIO Ecosystem to zio-blocks

## Overview

This plan covers extracting effect-agnostic utilities from ZIO and zio-http into zio-blocks, a collection of cloud app utilities that do not depend on ZIO the effect system.

## Motivation

Several utilities in zio-http and zio-core are already effect-agnostic but are trapped in libraries that have heavy dependencies. Extracting them to zio-blocks:

1. Makes them usable without pulling in ZIO/zio-http
2. Reduces duplication across the ecosystem
3. Provides foundational building blocks for schema, codecs, and other zio-blocks modules

## Candidates

| PR | Module | Source | Status |
|----|--------|--------|--------|
| 1 | [Combiner](./01-combiner.md) | zio-http codec | Ready |
| 2 | [MediaType](./02-mediatype.md) | zio-http | Ready |
| 3 | [Doc](./03-doc.md) | zio-http codec | Under consideration |
| 4 | [Template](./04-template.md) | zio-http template2 | Ready (depends on PR 2) |

## Dependency Graph

```
PR 1: Combiner     (no deps)
PR 2: MediaType    (no deps)
PR 3: Doc          (uses Chunk, Schema - already in zio-blocks)
PR 4: Template     (depends on MediaType from PR 2)
```

## Execution Order

PRs 1, 2, 3 can be done in parallel.
PR 4 should wait for PR 2 (MediaType).

## Source Repositories

- zio-http: `/Users/nabil_abdel-hafeez/zio-repos/zio-http`
- zio-core: `/Users/nabil_abdel-hafeez/zio-repos/zio`
- zio-blocks: `/Users/nabil_abdel-hafeez/zio-repos/zio-blocks`

## Draft Issues

See `issues/` folder for GitHub issue drafts ready to be opened.

## Notes

- HttpContentCodec is explicitly OUT OF SCOPE - it stays in zio-http
- Doc module is under consideration - may be dropped
- All extractions should maintain cross-platform support (JVM/JS/Native)

# PR 3: Doc Module

> **STATUS: Under Consideration** - May be dropped from the plan.

## Summary

Extract `Doc` documentation DSL from zio-http for composable documentation generation.

## What Doc Is

A composable documentation DSL that can render to multiple formats.

```scala
sealed trait Doc {
  def +(that: Doc): Doc
  def isEmpty: Boolean
  def tag(tag: String): Doc
  def toCommonMark: String
  def toHtml: String
  def toPlaintext(width: Int): String
}
```

### Doc Types
- `Doc.Empty` - No documentation
- `Doc.Header(value, level)` - Headers (h1-h6)
- `Doc.Paragraph(value)` - Text paragraphs
- `Doc.Span(...)` - Inline content (text, code, links, bold, italic)
- `Doc.Listing(items, listingType)` - Ordered/unordered lists
- `Doc.DescriptionList(items)` - Definition lists
- `Doc.Raw(value, format)` - Pre-formatted content
- `Doc.Sequence(left, right)` - Composition
- `Doc.Tagged(doc, tags)` - Tagged documentation

## Why It Might Fit zio-blocks

- Schema documentation generation
- API documentation for codecs
- Error message formatting
- General-purpose doc composition

## Why It Might Not

- Fairly specialized use case
- Could live in a separate micro-library
- Not strictly necessary for core zio-blocks functionality

## Source Files

| File | Source Location |
|------|-----------------|
| Doc.scala | `zio-http/zio-http/shared/src/main/scala/zio/http/codec/Doc.scala` |

## Target Location (if included)

```
zio-blocks/
├── doc/
│   └── shared/
│       └── src/
│           └── main/
│               └── scala/
│                   └── zio/
│                       └── blocks/
│                           └── doc/
│                               ├── Doc.scala
│                               └── Span.scala
```

## Dependencies

- `zio.Chunk` → Use `zio.blocks.chunk.Chunk`
- `zio.schema.Schema` → Use `zio.blocks.schema.Schema`
- `zio.http.template` → Minimal extraction or remove HTML rendering

## Required Changes

### Doc.scala
1. Change package: `zio.http.codec` → `zio.blocks.doc`
2. Replace `zio.Chunk` with `zio.blocks.chunk.Chunk`
3. Remove or adapt template dependency for HTML rendering
4. Keep CommonMark and plaintext rendering

## Build Configuration

```scala
lazy val doc = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .in(file("doc"))
  .settings(stdSettings("zio-blocks-doc"))
  .dependsOn(chunk, schema)
```

## Estimated Effort

**Low** - Straightforward extraction with dependency updates.

~4-8 hours

## Acceptance Criteria

- [ ] Doc DSL works standalone
- [ ] CommonMark rendering works
- [ ] Plaintext rendering works
- [ ] HTML rendering works (or is cleanly removed)
- [ ] Cross-platform build works
- [ ] No ZIO effect dependencies

## Decision Needed

**Should this be included in zio-blocks?**

Pros:
- Useful for schema/codec documentation
- Already effect-agnostic
- Small footprint

Cons:
- Specialized use case
- Could be a separate library
- Adds maintenance burden

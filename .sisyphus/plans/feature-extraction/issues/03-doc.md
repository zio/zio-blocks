# GitHub Issue Draft: Doc Module

## Title

[Discussion] Add Doc documentation DSL

## Labels

- `enhancement`
- `discussion`
- `new module`

## Body

### Summary

Consider extracting the `Doc` documentation DSL from zio-http for composable documentation generation.

### ⚠️ Status: Under Discussion

This module is under consideration. We should decide if it belongs in zio-blocks or should remain in zio-http / become a separate library.

### What is Doc?

A composable documentation DSL that renders to multiple formats:

```scala
val doc = Doc.h1("API Reference") +
  Doc.p("This endpoint handles user authentication.") +
  Doc.codeBlock("POST /api/login") +
  Doc.descriptionList(
    "username" -> Doc.p("The user's email"),
    "password" -> Doc.p("The user's password")
  )

doc.toCommonMark  // → Markdown string
doc.toHtml        // → HTML string  
doc.toPlaintext   // → Plain text
```

### Potential Use Cases in zio-blocks

- Schema documentation generation
- Codec format documentation
- Error message formatting
- API documentation

### Arguments For Inclusion

- Already effect-agnostic
- Uses Chunk and Schema (already in zio-blocks)
- Small footprint
- Useful for schema/codec docs

### Arguments Against Inclusion

- Specialized use case
- Could be a separate micro-library
- Adds maintenance burden
- Not strictly necessary for core functionality

### Questions to Decide

1. Is documentation generation a core concern for zio-blocks?
2. Would schema/codec modules benefit from this?
3. Should this be a separate library instead?

### If Included

Tasks would be:
- [ ] Create `doc` module
- [ ] Extract `Doc.scala`
- [ ] Update Chunk/Schema imports to zio-blocks versions
- [ ] Handle HTML rendering (keep or remove template dependency)
- [ ] Add tests

### Feedback Requested

Please comment with your thoughts on whether this should be included in zio-blocks.

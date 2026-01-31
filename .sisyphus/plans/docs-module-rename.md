# Rename Markdown Module to Docs + Add Documentation

## TL;DR

> **Quick Summary**: Rename the markdown module's package and artifact from `zio.blocks.markdown`/`zio-blocks-markdown` to `zio.blocks.docs`/`zio-blocks-docs`, and add documentation to the main docs site.
> 
> **Deliverables**:
> - Package renamed from `zio.blocks.markdown` to `zio.blocks.docs`
> - Artifact renamed from `zio-blocks-markdown` to `zio-blocks-docs`
> - "Docs" section added to `docs/index.md` (like Schema, Chunk sections)
> - Detailed reference at `docs/reference/docs.md`
> - The Blocks table updated to include Docs module
> 
> **Estimated Effort**: Medium (2-3 hours)
> **Parallel Execution**: YES - 2 waves
> **Critical Path**: Task 1 → Task 2 → Task 3 → Task 4 → Task 5

---

## Context

### Original Request
Rename the markdown module to "docs" (zio-blocks-docs) and add documentation under `/docs` directory (the main documentation site).

### Decisions Made
- **Directory**: Keep as `markdown/` to avoid conflict with `docs/` website directory
- **Package**: Rename from `zio.blocks.markdown` to `zio.blocks.docs`
- **Artifact**: Rename from `zio-blocks-markdown` to `zio-blocks-docs`
- **Documentation**: Add to `/docs/index.md` (main overview) and `/docs/reference/docs.md` (detailed reference)

---

## Work Objectives

### Core Objective
Rename the module's package/artifact and add comprehensive documentation to the main docs site.

### Concrete Deliverables
- All source/test files moved to `zio/blocks/docs/` package path
- Package declarations updated to `package zio.blocks.docs`
- build.sbt updated with new artifact name
- `docs/index.md` updated with "Docs" section (like Schema, Chunk)
- `docs/reference/docs.md` created with detailed reference documentation
- The Blocks table in index.md updated to include Docs

### Definition of Done
- [ ] All tests pass: `sbt markdownJVM/test`
- [ ] Cross-Scala tests pass: `sbt '++2.13.18; markdownJVM/test'`
- [ ] Code compiles on all platforms
- [ ] `docs/index.md` has Docs section
- [ ] `docs/reference/docs.md` exists with detailed docs
- [ ] CI passes

### Must NOT Have (Guardrails)
- NO directory rename from `markdown/` (conflicts with `docs/` website)
- NO changes to core logic (only package/naming changes)
- NO breaking API changes beyond the package rename

---

## TODOs

- [ ] 1. Move source files to new package directory structure

  **What to do**:
  Move all source and test files from `zio/blocks/markdown/` to `zio/blocks/docs/`:
  
  ```bash
  # Main sources
  mkdir -p markdown/shared/src/main/scala/zio/blocks/docs
  git mv markdown/shared/src/main/scala/zio/blocks/markdown/*.scala markdown/shared/src/main/scala/zio/blocks/docs/
  rmdir markdown/shared/src/main/scala/zio/blocks/markdown
  
  # Scala 2 macros
  mkdir -p markdown/shared/src/main/scala-2/zio/blocks/docs
  git mv markdown/shared/src/main/scala-2/zio/blocks/markdown/*.scala markdown/shared/src/main/scala-2/zio/blocks/docs/
  rmdir markdown/shared/src/main/scala-2/zio/blocks/markdown
  
  # Scala 3 macros
  mkdir -p markdown/shared/src/main/scala-3/zio/blocks/docs
  git mv markdown/shared/src/main/scala-3/zio/blocks/markdown/*.scala markdown/shared/src/main/scala-3/zio/blocks/docs/
  rmdir markdown/shared/src/main/scala-3/zio/blocks/markdown
  
  # Test sources
  mkdir -p markdown/shared/src/test/scala/zio/blocks/docs
  git mv markdown/shared/src/test/scala/zio/blocks/markdown/*.scala markdown/shared/src/test/scala/zio/blocks/docs/
  rmdir markdown/shared/src/test/scala/zio/blocks/markdown
  ```

  **Must NOT do**:
  - Do not rename the `markdown/` top-level directory

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `["git-master"]`

  **Acceptance Criteria**:
  - [ ] All .scala files moved to `zio/blocks/docs/` directories
  - [ ] Old `zio/blocks/markdown/` directories removed
  - [ ] `git status` shows renames

  **Commit**: NO (group with Task 3)

---

- [ ] 2. Update build.sbt

  **What to do**:
  Update build.sbt to use new artifact name and package:
  
  ```scala
  // Change from:
  .settings(stdSettings("zio-blocks-markdown"))
  .settings(buildInfoSettings("zio.blocks.markdown"))
  
  // To:
  .settings(stdSettings("zio-blocks-docs"))
  .settings(buildInfoSettings("zio.blocks.docs"))
  ```

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`

  **References**:
  - `build.sbt` - Find `lazy val markdown` definition

  **Acceptance Criteria**:
  - [ ] `stdSettings("zio-blocks-docs")` in build.sbt
  - [ ] `buildInfoSettings("zio.blocks.docs")` in build.sbt

  **Commit**: NO (group with Task 3)

---

- [ ] 3. Update all package declarations and imports

  **What to do**:
  Update all package declarations from `zio.blocks.markdown` to `zio.blocks.docs`:
  
  - `package zio.blocks.markdown` → `package zio.blocks.docs`
  - `import zio.blocks.markdown._` → `import zio.blocks.docs._`
  - `import zio.blocks.markdown.` → `import zio.blocks.docs.`
  - `zio.blocks.markdown.` references in code

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`

  **Acceptance Criteria**:
  - [ ] No occurrences of `zio.blocks.markdown` in any .scala file under markdown/
  - [ ] All files have `package zio.blocks.docs`
  - [ ] `sbt markdownJVM/compile` succeeds

  **Commit**: YES
  - Message: `refactor(docs): rename package from zio.blocks.markdown to zio.blocks.docs`

---

- [ ] 4. Add Docs section to docs/index.md

  **What to do**:
  1. Update "The Blocks" table to include Docs:
     ```markdown
     | **Docs** | GitHub Flavored Markdown parsing and rendering | ✅ Available |
     ```
  
  2. Add a "Docs" section after the Chunk section (before "Streams"), following the same pattern:
     - Brief description
     - Why Docs?
     - Key Features
     - Installation
     - Example (parsing, rendering, interpolator)

  **Content for Docs section**:
  ```markdown
  ## Docs

  A zero-dependency GitHub Flavored Markdown library for parsing, rendering, and programmatic construction of Markdown documents.

  ### Why Docs?

  Generating documentation, README files, or any Markdown content programmatically is common but error-prone with string concatenation. Docs provides:

  - **Type-safe AST**: Build Markdown documents with compile-time guarantees
  - **Compile-time validation**: The `md"..."` interpolator validates syntax at compile time
  - **Multiple renderers**: Output to Markdown, HTML, or ANSI terminal
  - **Round-trip parsing**: Parse Markdown to AST and render back to Markdown

  ### Key Features

  - **GFM Compliant**: Tables, strikethrough, autolinks, task lists, fenced code blocks
  - **Zero Dependencies**: Only depends on zio-blocks-chunk
  - **Cross-Platform**: Full support for JVM, Scala.js, and Scala Native
  - **Type-Safe Interpolator**: `md"# Hello $name"` with compile-time validation
  - **Multiple Renderers**: Markdown, HTML (full document or fragment), ANSI terminal

  ### Installation

  ```scala
  libraryDependencies += "dev.zio" %% "zio-blocks-docs" % "@VERSION@"
  ```

  ### Example

  ```scala
  import zio.blocks.docs._

  // Parse Markdown
  val doc = Parser.parse("# Hello\n\nThis is **bold** text.")
  // Right(Doc(Chunk(Heading(H1, "Hello"), Paragraph(Text("This is "), Strong("bold"), Text(" text.")))))

  // Render to HTML
  val html = doc.map(_.toHtml)
  // "<html>...<h1>Hello</h1><p>This is <strong>bold</strong> text.</p>...</html>"

  // Render to terminal with ANSI colors
  val terminal = doc.map(_.toTerminal)

  // Use the type-safe interpolator
  val name = "World"
  val greeting = md"# Hello $name"
  // Doc containing: Heading(H1, "Hello World")

  // Build documents programmatically
  val manual = Doc(Chunk(
    Heading(HeadingLevel.H1, Chunk(Text("API Reference"))),
    Paragraph(Chunk(Text("See "), Link(Chunk(Text("docs")), "/docs", None), Text(" for details.")))
  ))
  ```

  ### Supported GFM Features

  | Feature | Supported |
  |---------|-----------|
  | Headings (ATX) | ✅ |
  | Paragraphs | ✅ |
  | Emphasis/Strong | ✅ |
  | Code (inline & fenced) | ✅ |
  | Links & Images | ✅ |
  | Lists (bullet, ordered, task) | ✅ |
  | Blockquotes | ✅ |
  | Tables | ✅ |
  | Strikethrough | ✅ |
  | Autolinks | ✅ |
  | Hard/Soft breaks | ✅ |
  | HTML (passthrough) | ✅ |

  ### Limitations

  - **No frontmatter**: YAML/TOML headers are not parsed
  - **No HTML entity decoding**: `&amp;` stays as-is
  - **No footnotes**: GFM footnote extension not supported
  - **No emoji shortcodes**: `:smile:` not converted to emoji
  ```

  **Recommended Agent Profile**:
  - **Category**: `writing`
  - **Skills**: `[]`

  **References**:
  - `docs/index.md:16-24` - The Blocks table
  - `docs/index.md:36-106` - Schema section pattern
  - `docs/index.md:109-165` - Chunk section pattern
  - `markdown/shared/src/main/scala/zio/blocks/docs/package.scala` - Existing Scaladoc

  **Acceptance Criteria**:
  - [ ] "Docs" row added to The Blocks table
  - [ ] "## Docs" section added after Chunk, before Streams
  - [ ] Section includes: description, features, installation, example, GFM support, limitations

  **Commit**: YES
  - Message: `docs: add Docs module section to main documentation`

---

- [ ] 5. Create docs/reference/docs.md with detailed reference

  **What to do**:
  Create detailed reference documentation at `docs/reference/docs.md`:

  ```markdown
  ---
  id: docs
  title: "Docs Reference"
  ---

  # Docs Module Reference

  Complete API reference for the zio-blocks-docs module.

  ## Core Types

  ### Doc
  The top-level document type...

  ### Block
  Block-level elements...

  ### Inline
  Inline elements...

  ## Parsing

  ### Parser.parse
  ...

  ## Rendering

  ### Markdown Rendering
  ### HTML Rendering  
  ### Terminal Rendering

  ## String Interpolator

  ### The md"..." Interpolator
  ### ToMarkdown Typeclass

  ## Working with the AST

  ### Traversing Documents
  ### Transforming Documents
  ### Normalization
  ```

  **Recommended Agent Profile**:
  - **Category**: `writing`
  - **Skills**: `[]`

  **References**:
  - `docs/reference/schema.md` - Pattern for reference docs
  - `markdown/shared/src/main/scala/zio/blocks/docs/*.scala` - All API types

  **Acceptance Criteria**:
  - [ ] `docs/reference/docs.md` exists
  - [ ] Contains frontmatter with id and title
  - [ ] Documents all core types (Doc, Block, Inline)
  - [ ] Documents parsing, rendering, interpolator

  **Commit**: YES
  - Message: `docs: add detailed reference documentation for Docs module`

---

- [ ] 6. Verify, format, and push

  **What to do**:
  1. Run full test suite across Scala versions
  2. Format all code
  3. Create new branch and push
  4. Create PR

  ```bash
  # Test Scala 3
  sbt markdownJVM/test
  
  # Test Scala 2
  sbt '++2.13.18; markdownJVM/test'
  
  # Format
  sbt markdownJVM/scalafmt
  sbt 'markdownJVM/Test/scalafmt'
  
  # Create branch and push
  git checkout -b docs-module-rename
  git push origin docs-module-rename
  ```

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `["git-master"]`

  **Acceptance Criteria**:
  - [ ] `sbt markdownJVM/test` passes
  - [ ] `sbt '++2.13.18; markdownJVM/test'` passes
  - [ ] All code formatted
  - [ ] Changes pushed to origin
  - [ ] CI passes

  **Commit**: NO (just verify and push existing commits)

---

## Success Criteria

### Verification Commands
```bash
# Verify package rename
grep -r "package zio.blocks.docs" markdown/shared/src/main/scala/

# Verify no old references
grep -r "zio.blocks.markdown" markdown/ --include="*.scala" | wc -l
# Expected: 0

# Run tests
sbt markdownJVM/test
sbt '++2.13.18; markdownJVM/test'

# Verify docs exist
cat docs/index.md | grep "## Docs"
ls docs/reference/docs.md
```

### Final Checklist
- [ ] Package renamed to `zio.blocks.docs`
- [ ] Artifact renamed to `zio-blocks-docs`
- [ ] All tests pass (Scala 2 + 3)
- [ ] `docs/index.md` has Docs section
- [ ] `docs/reference/docs.md` created
- [ ] CI passes

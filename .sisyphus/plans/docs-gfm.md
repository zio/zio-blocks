# GitHub Flavored Markdown Micro-Library for zio-blocks

## TL;DR

> **Quick Summary**: Create a `docs` module providing a pure Scala ADT for GitHub Flavored Markdown, with strict parsing, rendering, and a compile-time validated `md"..."` interpolator.
> 
> **Deliverables**:
> - Three-tier ADT (Document > Block > Inline) with type-safe heading levels
> - `ToMarkdown[A]` typeclass for interpolator type safety
> - Strict GFM parser returning `Either[ParseError, Document]`
> - GFM renderer (Document → String)
> - `md"..."` string interpolator with Scala 2/3 macro implementations
> - Comprehensive test suite with TDD approach
> 
> **Estimated Effort**: Large
> **Parallel Execution**: YES - 3 waves
> **Critical Path**: Task 1 (ADT) → Task 5 (Parser) → Task 6 (Renderer) → Task 7 (Interpolator)

---

## Context

### Original Request
Create a docs micro-library for zio-blocks based on GitHub Flavored Markdown with:
- ADT representing GFM
- Rendering to GFM
- Parsing GFM
- `md"..."` string interpolator with compile-time validation

### Interview Summary
**Key Discussions**:
- **Use case**: General-purpose GFM library (not schema-specific)
- **ADT structure**: Three-tier (Document > Block > Inline) preferred for clarity
- **Heading levels**: Type-safe enforcement (1-6 as enum/sealed trait)
- **Interpolator design**: `ToMarkdown[A]` typeclass with built-in instances
- **Parser mode**: Strict - fail on malformed input with error details
- **Platforms**: Full cross-platform (JVM, JS, Native)

**Research Findings**:
- GFM spec 0.29 has ~29 element types (14 blocks, 15 inlines)
- Existing interpolator patterns in schema module (`json"..."`, `p"..."`)
- Macro organization: `scala-2/` and `scala-3/` directories with mirrored implementations
- Test pattern: BaseSpec extending ZIOSpecDefault

### Metis Review
**Identified Gaps** (addressed):
- **Error granularity**: ParseError should include line/column and offending input
- **Edge cases**: Malformed tables, nested formatting, unclosed blocks need handling
- **Acceptance criteria**: Made explicit and agent-executable
- **Guardrails**: Explicit enumeration of in-scope vs out-of-scope features

---

## Work Objectives

### Core Objective
Build a zero-dependency (except chunk) GFM micro-library with a type-safe ADT, strict parser, renderer, and compile-time validated string interpolator supporting Scala 2.13 and 3.3+ across JVM, JS, and Native.

### Concrete Deliverables
- `docs/` directory with CrossType.Full layout
- `zio.blocks.docs.Document` - root ADT type
- `zio.blocks.docs.Block` - sealed trait for block elements
- `zio.blocks.docs.Inline` - sealed trait for inline elements
- `zio.blocks.docs.HeadingLevel` - type-safe 1-6 enum
- `zio.blocks.docs.ToMarkdown[A]` - typeclass for interpolator
- `zio.blocks.docs.Parser.parse(String): Either[ParseError, Document]`
- `zio.blocks.docs.Renderer.render(Document): String`
- `zio.blocks.docs.md` - string interpolator extension
- `DocsBaseSpec` - test base trait

### Definition of Done
- [x] `sbt markdownJVM/test` passes with 100% new code covered
- [x] `sbt markdownJS/test` passes
- [x] `sbt markdownNative/test` passes
- [x] `sbt "++2.13.18; markdownJVM/test"` passes (Scala 2 cross-test)
- [x] `md"# Hello"` compiles and produces correct Document
- [x] `md"unclosed \`code"` fails compilation with clear error
- [x] Parser round-trips: `render(parse(input).toOption.get) == input` for valid GFM

### Must Have
- Document ADT with all in-scope GFM elements
- Strict parser with position-aware errors
- Renderer producing valid GFM
- `md"..."` interpolator with compile-time validation
- `ToMarkdown[A]` typeclass with instances for String, primitives, ADT nodes
- Full test coverage

### Must NOT Have (Guardrails)
- **NO frontmatter parsing** - YAML/TOML headers cause parse error
- **NO entity references** - `&amp;`, `&#123;` not processed, treated as text
- **NO multi-line table cells** - Only single-line cells supported
- **NO HTML sanitization** - HTML blocks are raw passthrough
- **NO GFM extensions beyond spec** - No footnotes, emoji shortcodes, math blocks
- **NO third-party dependencies** - Only chunk module
- **NO streaming/async parsing** - Strict in-memory only
- **NO HTML output rendering** - Only Markdown-to-Markdown
- **NO runtime interpolator fallback** - Compile-time errors only
- **NO AI slop**: Excessive comments, over-abstraction, premature optimization

---

## Verification Strategy (MANDATORY)

### Test Decision
- **Infrastructure exists**: YES (ZIO Test, scoverage)
- **User wants tests**: TDD
- **Framework**: ZIO Test (zio-test, zio-test-sbt)

### TDD Workflow
Each TODO follows RED-GREEN-REFACTOR:

**Task Structure:**
1. **RED**: Write failing test first
   - Test file: `docs/shared/src/test/scala/zio/blocks/docs/*.scala`
   - Test command: `sbt docsJVM/test`
   - Expected: FAIL (test exists, implementation doesn't)
2. **GREEN**: Implement minimum code to pass
   - Command: `sbt docsJVM/test`
   - Expected: PASS
3. **REFACTOR**: Clean up while keeping green
   - Command: `sbt docsJVM/test`
   - Expected: PASS (still)

### Coverage Requirements
- JVM: 95% statement, 90% branch (repo standard)
- JS/Native: Coverage disabled per repo config

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Start Immediately):
├── Task 1: Project scaffold + sbt configuration
└── Task 2: ADT definitions (Block, Inline, Document)

Wave 2 (After Wave 1):
├── Task 3: ToMarkdown typeclass + instances
├── Task 4: HeadingLevel type-safe enum
└── Task 5: Parser implementation

Wave 3 (After Wave 2):
├── Task 6: Renderer implementation
└── Task 7: md"..." interpolator (Scala 2 + 3 macros)

Sequential (After Wave 3):
└── Task 8: Cross-platform + cross-Scala verification

Critical Path: Task 1 → Task 2 → Task 5 → Task 6 → Task 7
Parallel Speedup: ~35% faster than sequential
```

### Dependency Matrix

| Task | Depends On | Blocks | Can Parallelize With |
|------|------------|--------|---------------------|
| 1 | None | 2, 3, 4, 5, 6, 7 | None |
| 2 | 1 | 3, 5, 6, 7 | None (but is quick) |
| 3 | 2 | 7 | 4, 5 |
| 4 | 2 | 5, 6, 7 | 3, 5 |
| 5 | 2, 4 | 6, 7 | 3 |
| 6 | 5 | 7 | None |
| 7 | 3, 6 | 8 | None |
| 8 | 7 | None | None (final) |

### Agent Dispatch Summary

| Wave | Tasks | Recommended Agents |
|------|-------|-------------------|
| 1 | 1, 2 | Sequential: scaffold first, then ADT |
| 2 | 3, 4, 5 | Parallel: typeclass, enum, parser |
| 3 | 6, 7 | Sequential: renderer before interpolator |
| Final | 8 | Cross-platform verification |

---

## GFM Elements Scope (Explicit Enumeration)

### In-Scope Block Elements (10 types)
1. `Document` - root container with `Chunk[Block]`
2. `Paragraph` - contains `Chunk[Inline]`
3. `Heading` - level 1-6 (type-safe), contains `Chunk[Inline]`
4. `CodeBlock` - info string optional, code content
5. `ThematicBreak` - horizontal rule (`---`, `***`, `___`)
6. `BlockQuote` - contains `Chunk[Block]`
7. `BulletList` - items with tight/loose distinction
8. `OrderedList` - items with start number, tight/loose
9. `ListItem` - contains `Chunk[Block]`, optional checkbox (task list)
10. `HtmlBlock` - raw HTML passthrough (no parsing)
11. `Table` - header row, alignments, data rows (GFM extension)

### In-Scope Inline Elements (11 types)
1. `Text` - plain text content
2. `Code` - inline code span
3. `Emphasis` - `*em*` or `_em_`
4. `Strong` - `**strong**` or `__strong__`
5. `Strikethrough` - `~~del~~` (GFM extension)
6. `Link` - inline or reference style
7. `Image` - `![alt](url)`
8. `HtmlInline` - raw inline HTML
9. `SoftBreak` - newline within paragraph
10. `HardBreak` - two spaces + newline or `\`
11. `Autolink` - `<url>` and extended bare URLs (GFM)

### Out-of-Scope (Cause Parse Error)
- Frontmatter (`---\nyaml\n---`)
- Entity references (`&amp;`, `&#123;`)
- Setext headings (use ATX only: `#`)
- Indented code blocks (use fenced only)
- Link reference definitions (inline links only for v1)

---

## TODOs

- [x] 1. Project Scaffold and SBT Configuration

  **What to do**:
  - Create `docs/` directory with CrossType.Full layout:
    - `docs/shared/src/main/scala/zio/blocks/docs/`
    - `docs/shared/src/main/scala-2/zio/blocks/docs/`
    - `docs/shared/src/main/scala-3/zio/blocks/docs/`
    - `docs/shared/src/test/scala/zio/blocks/docs/`
    - `docs/jvm/`, `docs/js/`, `docs/native/` (empty initially)
  - Add `docs` project to `build.sbt` as `crossProject(JSPlatform, JVMPlatform, NativePlatform)`
  - Configure: depends on `chunk`, add zio-test dependencies
  - Create `DocsBaseSpec` trait extending `ZIOSpecDefault`
  - Add scala-reflect dependency for Scala 2 macros

  **Must NOT do**:
  - Do not add any third-party markdown libraries
  - Do not create platform-specific source files yet
  - Do not add docs to `testJVM` alias (it's new, keep isolated)

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Scaffolding task with known patterns from existing modules
  - **Skills**: [`git-master`]
    - `git-master`: Atomic commits for scaffold setup

  **Parallelization**:
  - **Can Run In Parallel**: NO (foundational)
  - **Parallel Group**: Wave 1 - Sequential start
  - **Blocks**: All other tasks
  - **Blocked By**: None

  **References**:

  **Pattern References**:
  - `schema/shared/src/main/scala/zio/blocks/schema/` - Directory structure pattern for CrossType.Full
  - `schema/shared/src/test/scala/zio/blocks/schema/SchemaBaseSpec.scala` - BaseSpec pattern to replicate

  **Build Configuration References**:
  - `build.sbt:schema` definition block - Pattern for crossProject settings
  - `build.sbt:chunk` definition block - Simpler module to reference
  - `project/BuildHelper.scala:stdSettings` - Standard settings to apply

  **Acceptance Criteria**:

  ```bash
  # Agent runs: Verify directory structure exists
  ls -la docs/shared/src/main/scala/zio/blocks/docs/
  # Assert: Directory exists (exit code 0)
  
  ls -la docs/shared/src/main/scala-2/zio/blocks/docs/
  # Assert: Directory exists (exit code 0)
  
  ls -la docs/shared/src/main/scala-3/zio/blocks/docs/
  # Assert: Directory exists (exit code 0)
  
  # Agent runs: Verify sbt recognizes project
  sbt "project docsJVM; compile"
  # Assert: Compiles successfully (exit code 0)
  
  # Agent runs: Verify test framework works
  sbt docsJVM/test
  # Assert: "No tests were executed" or passes (exit code 0)
  ```

  **Commit**: YES
  - Message: `feat(docs): scaffold docs module with CrossType.Full layout`
  - Files: `docs/**`, `build.sbt`
  - Pre-commit: `sbt docsJVM/compile`

---

- [x] 2. Core ADT Definitions (Document, Block, Inline)

  **What to do**:
  - Create `Inline.scala` with sealed trait and all 11 inline case classes
  - Create `Block.scala` with sealed trait and all 11 block case classes
  - Create `Document.scala` with Document case class
  - Create `TableRow.scala` and `Alignment.scala` supporting types
  - Use `Chunk[_]` from chunk module for all collections
  - Write tests for ADT construction and pattern matching

  **Must NOT do**:
  - Do not implement parsing or rendering yet
  - Do not add methods beyond basic construction
  - Do not use `List`, `Vector`, or `Array` - only `Chunk`

  **Recommended Agent Profile**:
  - **Category**: `unspecified-low`
    - Reason: Straightforward ADT definitions following established patterns
  - **Skills**: []
    - No special skills needed - pure Scala ADT work

  **Parallelization**:
  - **Can Run In Parallel**: NO (quick, run right after Task 1)
  - **Parallel Group**: Wave 1 - After scaffold
  - **Blocks**: Tasks 3, 5, 6, 7
  - **Blocked By**: Task 1

  **References**:

  **Pattern References**:
  - `schema/shared/src/main/scala/zio/blocks/schema/Schema.scala` - Sealed trait ADT pattern
  - `chunk/shared/src/main/scala/zio/blocks/chunk/Chunk.scala` - Chunk usage patterns

  **Test References**:
  - `schema/shared/src/test/scala/zio/blocks/schema/SchemaSpec.scala` - Test structure pattern

  **ADT Structure** (from interview):
  ```scala
  // Document.scala
  final case class Document(blocks: Chunk[Block], metadata: Map[String, String] = Map.empty)
  
  // Block.scala - sealed trait Block with 11 implementations
  // Inline.scala - sealed trait Inline with 11 implementations
  // Alignment.scala - sealed trait with Left, Right, Center, None
  // TableRow.scala - case class TableRow(cells: Chunk[Chunk[Inline]])
  ```

  **Acceptance Criteria**:

  ```bash
  # Agent runs: Compile ADT
  sbt docsJVM/compile
  # Assert: Compiles successfully
  
  # Agent runs: Test ADT construction
  sbt 'docsJVM/testOnly *AdtSpec'
  # Assert: Tests pass verifying:
  #   - Document can hold Chunk[Block]
  #   - Paragraph can hold Chunk[Inline]
  #   - Pattern matching exhaustive on Block and Inline
  #   - All 11 Block types constructible
  #   - All 11 Inline types constructible
  ```

  **Commit**: YES
  - Message: `feat(docs): add GFM ADT (Document, Block, Inline)`
  - Files: `docs/shared/src/main/scala/zio/blocks/docs/*.scala`
  - Pre-commit: `sbt docsJVM/test`

---

- [x] 3. ToMarkdown Typeclass and Instances

  **What to do**:
  - Create `ToMarkdown.scala` with `trait ToMarkdown[A] { def toMarkdown(a: A): Inline }`
  - Add companion object with:
    - `apply[A](implicit ev: ToMarkdown[A]): ToMarkdown[A]`
    - Instance for `String` → `Text`
    - Instance for `Int`, `Long`, `Double`, `Boolean` → `Text(_.toString)`
    - Instance for `Inline` → identity (pass-through)
    - Instance for `Block` → ??? (consider if needed or error)
    - Instance for `Chunk[Inline]` → helper for sequences
  - Write tests for all instances

  **Must NOT do**:
  - Do not create instances for complex types (user-defined)
  - Do not add rendering logic to typeclass
  - Do not make instances implicit in package object yet

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Simple typeclass with straightforward instances
  - **Skills**: []
    - No special skills needed

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with Tasks 4, 5)
  - **Blocks**: Task 7
  - **Blocked By**: Task 2

  **References**:

  **Pattern References**:
  - `schema/shared/src/main/scala/zio/blocks/schema/json/JsonEncoder.scala` - Typeclass pattern
  - `schema/shared/src/main/scala/zio/blocks/schema/Keyable.scala` - Simple typeclass example

  **Acceptance Criteria**:

  ```bash
  # Agent runs via bun/node REPL:
  sbt 'docsJVM/console' <<'EOF'
  import zio.blocks.docs._
  val s: Inline = ToMarkdown[String].toMarkdown("hello")
  val i: Inline = ToMarkdown[Int].toMarkdown(42)
  val pass: Inline = ToMarkdown[Inline].toMarkdown(Text("test"))
  println(s"String: $s, Int: $i, Passthrough: $pass")
  EOF
  # Assert: Output shows correct Text nodes
  
  sbt 'docsJVM/testOnly *ToMarkdownSpec'
  # Assert: All instance tests pass
  ```

  **Commit**: YES
  - Message: `feat(docs): add ToMarkdown typeclass with primitive instances`
  - Files: `docs/shared/src/main/scala/zio/blocks/docs/ToMarkdown.scala`, tests
  - Pre-commit: `sbt docsJVM/test`

---

- [x] 4. HeadingLevel Type-Safe Enum

  **What to do**:
  - Create `HeadingLevel.scala` with type-safe representation of levels 1-6
  - For Scala 2/3 compatibility, use sealed trait + case objects pattern:
    ```scala
    sealed abstract class HeadingLevel(val value: Int)
    object HeadingLevel {
      case object H1 extends HeadingLevel(1)
      case object H2 extends HeadingLevel(2)
      // ... H3-H6
      def fromInt(n: Int): Option[HeadingLevel]
      def unsafeFromInt(n: Int): HeadingLevel // throws on invalid
    }
    ```
  - Update `Heading` case class to use `HeadingLevel` instead of `Int`
  - Write tests for all levels and edge cases

  **Must NOT do**:
  - Do not use Scala 3 enum (breaks Scala 2 compatibility)
  - Do not allow arbitrary integers
  - Do not add level 0 or level 7+

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Small, focused type definition
  - **Skills**: []
    - No special skills needed

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with Tasks 3, 5)
  - **Blocks**: Tasks 5, 6, 7
  - **Blocked By**: Task 2

  **References**:

  **Pattern References**:
  - `Alignment.scala` (from Task 2) - Same sealed trait pattern

  **Acceptance Criteria**:

  ```bash
  # Agent runs: Compile check
  sbt docsJVM/compile
  # Assert: Heading uses HeadingLevel, not Int
  
  sbt 'docsJVM/testOnly *HeadingLevelSpec'
  # Assert: Tests pass:
  #   - HeadingLevel.H1.value == 1
  #   - HeadingLevel.fromInt(3) == Some(H3)
  #   - HeadingLevel.fromInt(0) == None
  #   - HeadingLevel.fromInt(7) == None
  #   - HeadingLevel.unsafeFromInt(7) throws
  ```

  **Commit**: YES
  - Message: `feat(docs): add type-safe HeadingLevel (1-6)`
  - Files: `docs/shared/src/main/scala/zio/blocks/docs/HeadingLevel.scala`, tests
  - Pre-commit: `sbt docsJVM/test`

---

- [x] 5. GFM Parser Implementation

  **What to do**:
  - Create `ParseError.scala` with position-aware error type:
    ```scala
    final case class ParseError(
      message: String,
      line: Int,
      column: Int,
      input: String // offending line or snippet
    )
    ```
  - Create `Parser.scala` with `def parse(input: String): Either[ParseError, Document]`
  - Implement block-level parsing:
    - Heading detection (`#` prefix, 1-6 levels)
    - Code block detection (fenced with ``` or ~~~)
    - Thematic break detection (`---`, `***`, `___`)
    - Block quote detection (`>` prefix)
    - List detection (bullet `-`, `*`, `+` and ordered `1.`)
    - Task list detection (`- [ ]`, `- [x]`)
    - Table detection (pipe-delimited with alignment row)
    - HTML block detection (raw passthrough)
    - Paragraph as fallback
  - Implement inline parsing:
    - Emphasis (`*`, `_`)
    - Strong (`**`, `__`)
    - Strikethrough (`~~`)
    - Code spans (`` ` ``)
    - Links `[text](url)` and `[text](url "title")`
    - Images `![alt](url)`
    - Autolinks `<url>` and bare URLs
    - Hard breaks (two spaces + newline, or `\`)
  - Fail with ParseError on:
    - Frontmatter detected
    - Multi-line table cells
    - Invalid heading level
    - Unclosed code fences
  - Write comprehensive tests for each element type

  **Must NOT do**:
  - Do not support indented code blocks (fenced only)
  - Do not support setext headings (ATX only)
  - Do not support link reference definitions
  - Do not parse HTML content (passthrough only)
  - Do not use regex for main parsing (parser combinators or recursive descent)

  **Recommended Agent Profile**:
  - **Category**: `ultrabrain`
    - Reason: Complex parsing logic requiring careful state management
  - **Skills**: []
    - No special skills needed - pure algorithmic work

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Task 3)
  - **Parallel Group**: Wave 2
  - **Blocks**: Task 6, 7
  - **Blocked By**: Tasks 2, 4

  **References**:

  **Pattern References**:
  - No direct pattern in codebase - this is new parsing code

  **External References**:
  - GFM Spec: https://github.github.com/gfm/ - Authoritative parsing rules
  - CommonMark Spec: https://spec.commonmark.org/ - Base parsing rules

  **Test References**:
  - `schema/shared/src/test/scala/zio/blocks/schema/json/JsonParserSpec.scala` - Parser test patterns (if exists)

  **Acceptance Criteria**:

  ```bash
  # Agent runs: Test basic parsing
  sbt 'docsJVM/testOnly *ParserSpec'
  # Assert: Tests pass for:
  #   - "# Heading" → Document(Chunk(Heading(H1, Chunk(Text("Heading")))))
  #   - "para1\n\npara2" → Document with 2 Paragraphs
  #   - "```scala\ncode\n```" → CodeBlock with info="scala"
  #   - "| a | b |\n|---|---|\n| 1 | 2 |" → Table with correct structure
  #   - "- [ ] task" → ListItem with checked=Some(false)
  
  # Agent runs: Test error cases
  sbt 'docsJVM/testOnly *ParserErrorSpec'
  # Assert: Tests pass for:
  #   - "---\nfrontmatter\n---\n# h" → Left(ParseError(...frontmatter...))
  #   - "####### h" → Left(ParseError(...level...))
  #   - "```\nunclosed" → Left(ParseError(...fence...))
  ```

  **Commit**: YES
  - Message: `feat(docs): implement strict GFM parser`
  - Files: `docs/shared/src/main/scala/zio/blocks/docs/Parser.scala`, `ParseError.scala`, tests
  - Pre-commit: `sbt docsJVM/test`

---

- [x] 6. GFM Renderer Implementation

  **What to do**:
  - Create `Renderer.scala` with `def render(doc: Document): String`
  - Implement block rendering:
    - Heading → `# ` prefix based on level
    - Paragraph → inline content + blank line
    - CodeBlock → fenced with info string
    - ThematicBreak → `---`
    - BlockQuote → `> ` prefix per line
    - Lists → proper indentation and markers
    - Tables → pipe-delimited with alignment row
    - HtmlBlock → raw passthrough
  - Implement inline rendering:
    - Text → raw content
    - Emphasis → `*content*`
    - Strong → `**content**`
    - Strikethrough → `~~content~~`
    - Code → `` `content` ``
    - Link → `[text](url "title")`
    - Image → `![alt](url "title")`
    - Autolink → `<url>` or bare
    - SoftBreak → `\n`
    - HardBreak → `  \n` or `\\\n`
  - Ensure idempotency: `render(parse(input).toOption.get) == input` for canonical GFM
  - Write property-based tests for round-tripping

  **Must NOT do**:
  - Do not produce HTML output
  - Do not add pretty-printing options (simple render only)
  - Do not escape content that doesn't need escaping

  **Recommended Agent Profile**:
  - **Category**: `unspecified-low`
    - Reason: Straightforward traversal and string building
  - **Skills**: []
    - No special skills needed

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 3 - Sequential after parser
  - **Blocks**: Task 7
  - **Blocked By**: Task 5

  **References**:

  **Pattern References**:
  - `schema/shared/src/main/scala/zio/blocks/schema/json/JsonEncoder.scala` - Encoder/rendering pattern

  **Acceptance Criteria**:

  ```bash
  sbt 'docsJVM/testOnly *RendererSpec'
  # Assert: Tests pass for:
  #   - Heading(H1, Chunk(Text("Hello"))) → "# Hello\n"
  #   - Paragraph(Chunk(Strong(Chunk(Text("bold"))))) → "**bold**\n\n"
  #   - Table rendering matches GFM format
  
  sbt 'docsJVM/testOnly *RoundTripSpec'
  # Assert: For valid GFM inputs:
  #   - parse(input) succeeds
  #   - render(parse(input).toOption.get) produces valid GFM
  #   - parse(render(doc)) == Right(doc) (structural equality)
  ```

  **Commit**: YES
  - Message: `feat(docs): implement GFM renderer`
  - Files: `docs/shared/src/main/scala/zio/blocks/docs/Renderer.scala`, tests
  - Pre-commit: `sbt docsJVM/test`

---

- [x] 7. md"..." String Interpolator (Scala 2 + 3 Macros)

  **What to do**:
  - Create shared runtime in `docs/shared/src/main/scala/zio/blocks/docs/MdInterpolatorRuntime.scala`:
    - Helper methods for parsing literal parts at compile time
    - Interpolation context tracking
  - Create Scala 2 macro in `docs/shared/src/main/scala-2/zio/blocks/docs/MdInterpolator.scala`:
    - `implicit class MdStringContext(sc: StringContext)` with `def md(args: Any*): Document`
    - Macro implementation using blackbox macros
    - Compile-time parsing of StringContext parts
    - Type-check interpolated values have `ToMarkdown[A]` instances
    - Abort with clear error on invalid markdown
  - Create Scala 3 macro in `docs/shared/src/main/scala-3/zio/blocks/docs/MdInterpolator.scala`:
    - `extension (inline sc: StringContext) inline def md(inline args: Any*): Document`
    - Macro implementation using `scala.quoted`
    - Same validation logic as Scala 2
  - Create `package.scala` to export the interpolator extension
  - Write tests for:
    - Simple markdown: `md"# Hello"` → Document
    - Interpolation: `md"Hello $name"` with ToMarkdown[String]
    - Invalid markdown: `md"####### Bad"` → compile error
    - Missing instance: `md"$customType"` without ToMarkdown → compile error

  **Must NOT do**:
  - Do not fall back to runtime parsing on errors
  - Do not allow arbitrary types without ToMarkdown instance
  - Do not support multi-line string literals differently than single-line

  **Recommended Agent Profile**:
  - **Category**: `ultrabrain`
    - Reason: Macro implementation requires careful Scala 2/3 handling
  - **Skills**: []
    - No special skills needed - advanced Scala macros

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 3 - After renderer
  - **Blocks**: Task 8
  - **Blocked By**: Tasks 3, 6

  **References**:

  **Pattern References**:
  - `schema/shared/src/main/scala-2/zio/blocks/schema/PathInterpolator.scala` - Scala 2 interpolator pattern
  - `schema/shared/src/main/scala-3/zio/blocks/schema/PathInterpolator.scala` - Scala 3 interpolator pattern
  - `schema/js-jvm/src/main/scala-2/zio/blocks/schema/json/package.scala` - json"..." Scala 2 macro with typeclass checks
  - `schema/js-jvm/src/main/scala-3/zio/blocks/schema/json/package.scala` - json"..." Scala 3 macro
  - `schema/shared/src/main/scala/zio/blocks/schema/json/JsonInterpolatorRuntime.scala` - Shared runtime pattern

  **Acceptance Criteria**:

  ```bash
  # Agent runs: Test valid interpolation
  sbt 'docsJVM/testOnly *MdInterpolatorSpec'
  # Assert: Tests pass for:
  #   - md"# Hello" compiles and produces Document
  #   - md"Hello $name" with name: String compiles
  #   - md"Value: $n" with n: Int compiles
  
  # Agent runs: Test compile-time errors (negative tests)
  # These tests should verify that invalid code fails to compile
  sbt 'docsJVM/testOnly *MdInterpolatorCompileErrorSpec'
  # Assert: Tests verify compile failures for:
  #   - md"####### Bad" (invalid level)
  #   - md"```\nunclosed" (unclosed fence)
  #   - Given case class Foo(), md"$foo" without ToMarkdown[Foo] fails
  ```

  **Commit**: YES
  - Message: `feat(docs): add md"..." string interpolator with Scala 2/3 macros`
  - Files: `docs/shared/src/main/scala*/zio/blocks/docs/MdInterpolator.scala`, runtime, package, tests
  - Pre-commit: `sbt docsJVM/test`

---

- [x] 8. Cross-Platform and Cross-Scala Verification

  **What to do**:
  - Run full test suite on all platforms:
    - `sbt docsJVM/test` (Scala 3)
    - `sbt docsJS/test` (Scala 3)
    - `sbt docsNative/test` (Scala 3)
  - Run cross-Scala tests:
    - `sbt "++2.13.18; docsJVM/test"` (Scala 2)
    - `sbt "++2.13.18; docsJS/test"` (Scala 2)
    - `sbt "++2.13.18; docsNative/test"` (Scala 2)
  - Run coverage on JVM:
    - `sbt "project docsJVM; coverage; test; coverageReport"`
  - Fix any platform-specific issues
  - Format all code:
    - `sbt docsJVM/scalafmt; sbt docsJVM/Test/scalafmt`

  **Must NOT do**:
  - Do not add platform-specific implementations unless absolutely necessary
  - Do not skip any platform or Scala version
  - Do not reduce coverage thresholds

  **Recommended Agent Profile**:
  - **Category**: `unspecified-low`
    - Reason: Verification and fixes only
  - **Skills**: []
    - No special skills needed

  **Parallelization**:
  - **Can Run In Parallel**: NO (final verification)
  - **Parallel Group**: Final - Sequential
  - **Blocks**: None (final task)
  - **Blocked By**: Task 7

  **References**:

  **Pattern References**:
  - `AGENTS.md` - Staged verification workflow documentation

  **Acceptance Criteria**:

  ```bash
  # Agent runs: All platform tests
  sbt docsJVM/test && sbt docsJS/test && sbt docsNative/test
  # Assert: All pass
  
  # Agent runs: Scala 2 cross-test
  sbt "++2.13.18; docsJVM/test"
  # Assert: Pass
  
  # Agent runs: Coverage
  sbt "project docsJVM; coverage; test; coverageReport"
  # Assert: Statement coverage ≥95%, Branch coverage ≥90%
  
  # Agent runs: Format check
  sbt docsJVM/scalafmtCheck && sbt docsJVM/Test/scalafmtCheck
  # Assert: No formatting issues
  ```

  **Commit**: YES (if fixes needed)
  - Message: `fix(docs): cross-platform/cross-Scala compatibility`
  - Files: Any files needing fixes
  - Pre-commit: `sbt docsJVM/test`

---

## Commit Strategy

| After Task | Message | Files | Verification |
|------------|---------|-------|--------------|
| 1 | `feat(docs): scaffold docs module with CrossType.Full layout` | `docs/**`, `build.sbt` | `sbt docsJVM/compile` |
| 2 | `feat(docs): add GFM ADT (Document, Block, Inline)` | ADT files + tests | `sbt docsJVM/test` |
| 3 | `feat(docs): add ToMarkdown typeclass with primitive instances` | typeclass + tests | `sbt docsJVM/test` |
| 4 | `feat(docs): add type-safe HeadingLevel (1-6)` | HeadingLevel + tests | `sbt docsJVM/test` |
| 5 | `feat(docs): implement strict GFM parser` | Parser + tests | `sbt docsJVM/test` |
| 6 | `feat(docs): implement GFM renderer` | Renderer + tests | `sbt docsJVM/test` |
| 7 | `feat(docs): add md"..." string interpolator with Scala 2/3 macros` | macros + tests | `sbt docsJVM/test` |
| 8 | `fix(docs): cross-platform/cross-Scala compatibility` (if needed) | fixes | all platform tests |

---

## Success Criteria

### Verification Commands
```bash
# Full test suite (JVM, Scala 3)
sbt docsJVM/test
# Expected: All tests pass

# Cross-Scala (Scala 2.13)
sbt "++2.13.18; docsJVM/test"
# Expected: All tests pass

# Cross-platform
sbt docsJS/test && sbt docsNative/test
# Expected: All tests pass

# Coverage
sbt "project docsJVM; coverage; test; coverageReport"
# Expected: ≥95% statement, ≥90% branch

# Interpolator works
# In REPL or test:
# md"# Hello *world*" produces Document with Heading and Emphasis
```

### Final Checklist
- [x] All "Must Have" features implemented
- [x] All "Must NOT Have" items absent
- [x] All tests pass on JVM, JS, Native
- [x] All tests pass on Scala 2.13 and 3.3+
- [x] Coverage meets thresholds (100% statement, 100% branch)
- [x] Code formatted with scalafmt
- [x] No new warnings

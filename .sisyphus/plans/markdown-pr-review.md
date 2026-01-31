# Markdown Module PR Review Implementation

## TL;DR

> **Quick Summary**: Implement remaining PR review feedback for zio-blocks-markdown module (PR #900). Add ToMarkdown for Block, Doc methods (++, toString, toHtml, toTerminal, normalize), HtmlRenderer, and TerminalRenderer.
> 
> **Deliverables**:
> - ToMarkdown instance for Block types
> - Doc methods: `++`, `toString`, `toHtml`, `toHtmlFragment`, `toTerminal`, `normalize`
> - HtmlRenderer (full HTML and fragment rendering)
> - TerminalRenderer (ANSI color output)
> - Comprehensive tests for all new functionality
> 
> **Estimated Effort**: Medium
> **Parallel Execution**: YES - 2 waves
> **Critical Path**: Task 1 → Task 2 → Task 3/4 (parallel) → Task 5

---

## Context

### Original Request
Continue implementing PR review feedback from @jdegoes on PR #900 for the zio-blocks-markdown module.

### PR #900 Review Comments (Verbatim)

**Reviewer**: @jdegoes

**Comment 1 - On `Document` → `Doc` rename** (COMPLETED):
```suggestion
final case class Doc(blocks: Chunk[Block], metadata: Map[String, String] = Map.empty)
```

**Comment 2 - On ToMarkdown instances**:
> All of the markdown parts should have an instance of `ToMarkdown` defined here.

**Comment 3 - On Doc methods** (DRIVING REQUIREMENTS):
> Would be nice to see lots of great methods here like:
> - `++` concatenates two documents and their metadata (right wins on metadata conflict)
> - `toString` renders as markdown
> - `toHtml` for rendering as an HTML string
> - `toTerminal` for rendering as color-enabled terminal output (or whatever it should be called, not sure)
> - `normalize`
> - Proper equals/hashCode based on normalization

**Comment 4 - On collection support** (COMPLETED):
> You should support lists/chunks/etc., so long as their elements are supported.

### Why These Features Are Needed

1. **ToMarkdown[Block]**: Enables embedding rendered blocks in the `md"..."` interpolator (e.g., `md"Here's a quote: $blockQuote"`)
2. **Doc.++**: Allows programmatic document composition without manual block manipulation
3. **Doc.toString**: Provides natural string representation for debugging and logging
4. **Doc.toHtml/toTerminal**: Enables direct rendering to output formats without intermediate steps
5. **Doc.normalize + equals/hashCode**: Enables semantic comparison of documents regardless of how they were constructed (e.g., `Text("a") + Text("b")` equals `Text("ab")`)

### Interview Summary
**Key Discussions**:
- PR #900 created for markdown module (issue #899)
- Review comments request additional features beyond initial implementation
- Already completed: Document → Doc rename, ToMarkdown for collections

**Completed Tasks**:
1. ✅ Rename `Document` → `Doc` (commit: 9af41d1c)
2. ✅ ToMarkdown for collections: List, Chunk, Vector, Seq (commit: 66dbd6bd)

**Remaining Tasks**:
3. ToMarkdown for Block types
4. Doc methods (++, toString, toHtml, toHtmlFragment, toTerminal, normalize)
5. HtmlRenderer (new file)
6. TerminalRenderer (new file)

---

## Complete Type Definitions (Exhaustive Reference)

### Block Types (10 case classes + 1 case object = 11 total)

| # | Type | File | Line | Signature |
|---|------|------|------|-----------|
| 1 | Paragraph | Block.scala | 19 | `final case class Paragraph(content: Chunk[Inline]) extends Block` |
| 2 | Heading | Block.scala | 29 | `final case class Heading(level: HeadingLevel, content: Chunk[Inline]) extends Block` |
| 3 | CodeBlock | Block.scala | 39 | `final case class CodeBlock(info: Option[String], code: String) extends Block` |
| 4 | ThematicBreak | Block.scala | 42 | `case object ThematicBreak extends Block` |
| 5 | BlockQuote | Block.scala | 50 | `final case class BlockQuote(content: Chunk[Block]) extends Block` |
| 6 | BulletList | Block.scala | 60 | `final case class BulletList(items: Chunk[ListItem], tight: Boolean) extends Block` |
| 7 | OrderedList | Block.scala | 72 | `final case class OrderedList(start: Int, items: Chunk[ListItem], tight: Boolean) extends Block` |
| 8 | ListItem | Block.scala | 83 | `final case class ListItem(content: Chunk[Block], checked: Option[Boolean]) extends Block` |
| 9 | HtmlBlock | Block.scala | 91 | `final case class HtmlBlock(content: String) extends Block` |
| 10 | Table | Block.scala | 103 | `final case class Table(header: TableRow, alignments: Chunk[Alignment], rows: Chunk[TableRow]) extends Block` |

### Inline Types (10 case classes + 2 case objects = 12 total, duplicated at package level and in Inline object)

| # | Type | File | Lines | Signature |
|---|------|------|-------|-----------|
| 1 | Text | Inline.scala | 21, 110 | `final case class Text(value: String) extends Inline` |
| 2 | Code | Inline.scala | 29, 118 | `final case class Code(value: String) extends Inline` |
| 3 | Emphasis | Inline.scala | 37, 126 | `final case class Emphasis(content: Chunk[Inline]) extends Inline` |
| 4 | Strong | Inline.scala | 45, 134 | `final case class Strong(content: Chunk[Inline]) extends Inline` |
| 5 | Strikethrough | Inline.scala | 53, 142 | `final case class Strikethrough(content: Chunk[Inline]) extends Inline` |
| 6 | Link | Inline.scala | 65, 154 | `final case class Link(text: Chunk[Inline], url: String, title: Option[String]) extends Inline` |
| 7 | Image | Inline.scala | 77, 166 | `final case class Image(alt: String, url: String, title: Option[String]) extends Inline` |
| 8 | HtmlInline | Inline.scala | 85, 174 | `final case class HtmlInline(content: String) extends Inline` |
| 9 | SoftBreak | Inline.scala | 88, 177 | `case object SoftBreak extends Inline` |
| 10 | HardBreak | Inline.scala | 91, 180 | `case object HardBreak extends Inline` |
| 11 | Autolink | Inline.scala | 101, 190 | `final case class Autolink(url: String, isEmail: Boolean) extends Inline` |

**CRITICAL NOTE**: Inline types are defined TWICE (at package level and inside `object Inline`). Renderer.scala handles BOTH variants in pattern matches (e.g., `case Text(v) =>` and `case Inline.Text(v) =>`). New renderers MUST do the same.

### Supporting Types

| Type | File | Line | Signature |
|------|------|------|-----------|
| HeadingLevel | HeadingLevel.scala | 11 | `sealed abstract class HeadingLevel(val value: Int)` |
| HeadingLevel.H1 | HeadingLevel.scala | 16 | `case object H1 extends HeadingLevel(1)` |
| HeadingLevel.H2 | HeadingLevel.scala | 19 | `case object H2 extends HeadingLevel(2)` |
| HeadingLevel.H3 | HeadingLevel.scala | 22 | `case object H3 extends HeadingLevel(3)` |
| HeadingLevel.H4 | HeadingLevel.scala | 25 | `case object H4 extends HeadingLevel(4)` |
| HeadingLevel.H5 | HeadingLevel.scala | 28 | `case object H5 extends HeadingLevel(5)` |
| HeadingLevel.H6 | HeadingLevel.scala | 31 | `case object H6 extends HeadingLevel(6)` |
| Alignment | Alignment.scala | 4 | `sealed trait Alignment` |
| Alignment.Left | Alignment.scala | 9 | `case object Left extends Alignment` |
| Alignment.Right | Alignment.scala | 12 | `case object Right extends Alignment` |
| Alignment.Center | Alignment.scala | 15 | `case object Center extends Alignment` |
| Alignment.None | Alignment.scala | 18 | `case object None extends Alignment` |
| TableRow | TableRow.scala | 13 | `final case class TableRow(cells: Chunk[Chunk[Inline]])` |
| Doc | Doc.scala | 24 | `final case class Doc(blocks: Chunk[Block], metadata: Map[String, String] = Map.empty)` |

---

## Future-Proofing: Adding New Types

**If a new Block type is added to Block.scala:**
1. Add pattern match case to `HtmlRenderer.renderBlock` (HtmlRenderer.scala)
2. Add pattern match case to `TerminalRenderer.renderBlock` (TerminalRenderer.scala)
3. Add test case to `HtmlRendererSpec.scala` suite "renderFragment - Blocks"
4. Add test case to `TerminalRendererSpec.scala` suite "Blocks"
5. The Scala compiler will emit warnings if the new type is unhandled (sealed trait exhaustiveness check)

**If a new Inline type is added to Inline.scala:**
1. Add pattern match case to `HtmlRenderer.renderInline` (HtmlRenderer.scala) - handle BOTH `NewType(...)` and `Inline.NewType(...)`
2. Add pattern match case to `TerminalRenderer.renderInline` (TerminalRenderer.scala) - handle BOTH variants
3. Add test case to `HtmlRendererSpec.scala` suite "renderFragment - Inlines"
4. Add test case to `TerminalRendererSpec.scala` suite "Inline styles" or similar
5. The Scala compiler will emit warnings if the new type is unhandled (sealed trait exhaustiveness check)

---

## Work Objectives

### Core Objective
Complete PR #900 by implementing all reviewer-requested features: Block ToMarkdown, Doc methods, HTML rendering, and terminal rendering.

### User Experience Goals

**HTML Rendering Goal**: Produce clean, semantic HTML5 that can be embedded in web pages or used standalone. The output should be:
- Standards-compliant (valid HTML5)
- Minimal (no unnecessary attributes or styling)
- Accessible (proper semantic elements: `<h1>`, `<p>`, `<code>`, etc.)

**Terminal Rendering Goal**: Produce readable, colorful output for CLI tools. The output should:
- Be readable on both light and dark terminal backgrounds
- Use standard ANSI codes (widely supported)
- Degrade gracefully (text is readable even without color support)

### Concrete Deliverables
- `ToMarkdown.scala`: Add `blockToMarkdown` implicit
- `Doc.scala`: Add `++`, `toString`, `toHtmlFragment`, `toHtml`, `toTerminal`, `normalize` methods + companion object helpers
- `HtmlRenderer.scala`: New file with HTML rendering logic
- `TerminalRenderer.scala`: New file with ANSI terminal rendering
- Test files updated with comprehensive coverage

### Definition of Done
- [x] `sbt markdownJVM/test` passes with all new tests (447 tests pass)
- [x] `sbt '++2.13.18; markdownJVM/test'` passes (cross-Scala) (443 tests pass)
- [x] Coverage meets module minimum (95%) (100% statement, 100% branch)
- [x] Code formatted with scalafmt

### Must Have
- ToMarkdown[Block] instance that renders blocks as markdown text
- Doc.++ for concatenation with metadata merging
- Doc.toString rendering via existing Renderer
- Doc.toHtml producing complete HTML document
- Doc.toHtmlFragment producing content HTML only
- Doc.toTerminal producing ANSI-colored output
- Doc.normalize for canonical form with equals/hashCode based on it

### Must NOT Have (Guardrails)
- No external dependencies (pure Scala only)
- No changes to existing public API signatures
- No breaking changes to Parser or existing Renderer
- No over-engineering - keep implementations minimal and focused

---

## Normalization Specification (CRITICAL)

### Purpose
Normalization produces a canonical representation of a document, enabling semantic equality comparison. Two documents that render identically should be equal, regardless of internal structure.

### Normalization Invariants with Code Examples

#### Invariant 1: Adjacent Text nodes are merged

**Before:**
```scala
Paragraph(Chunk(Text("a"), Text("b"), Text("c")))
```

**After:**
```scala
Paragraph(Chunk(Text("abc")))
```

**Test Case:** `AdtSpec.scala` → suite "Doc" → suite "normalize" → test "merges adjacent Text nodes"

#### Invariant 2: Empty Text nodes are removed

**Before:**
```scala
Paragraph(Chunk(Text(""), Text("x"), Text("")))
```

**After:**
```scala
Paragraph(Chunk(Text("x")))
```

**Test Case:** `AdtSpec.scala` → suite "Doc" → suite "normalize" → test "removes empty Text nodes"

#### Invariant 3: Empty content blocks are removed

**Before:**
```scala
Doc(Chunk(Paragraph(Chunk.empty), Paragraph(Chunk(Text("keep")))))
```

**After:**
```scala
Doc(Chunk(Paragraph(Chunk(Text("keep")))))
```

**Test Case:** `AdtSpec.scala` → suite "Doc" → suite "normalize" → test "removes empty Paragraphs"

#### Invariant 4: Whitespace-only Text is preserved

**Before:**
```scala
Paragraph(Chunk(Text("  ")))
```

**After:**
```scala
Paragraph(Chunk(Text("  ")))  // UNCHANGED - whitespace has meaning
```

**Test Case:** (implicit - whitespace-only Text is not empty)

#### Invariant 5: Nested structures are normalized recursively

**Before:**
```scala
Paragraph(Chunk(Strong(Chunk(Text("a"), Text("b")))))
```

**After:**
```scala
Paragraph(Chunk(Strong(Chunk(Text("ab")))))
```

**Test Case:** `AdtSpec.scala` → suite "Doc" → suite "normalize" → test "normalizes nested structures"

#### Invariant 6: BlockQuote content is normalized

**Before:**
```scala
BlockQuote(Chunk(Paragraph(Chunk(Text("a"), Text("b")))))
```

**After:**
```scala
BlockQuote(Chunk(Paragraph(Chunk(Text("ab")))))
```

**Test Case:** `AdtSpec.scala` → suite "Doc" → suite "normalize" → test "normalizes BlockQuote content"

### Normalization Algorithm

```scala
// In Doc companion object

def normalizeBlocks(blocks: Chunk[Block]): Chunk[Block] =
  blocks
    .map(normalizeBlock)
    .filter(!isEmpty(_))

def normalizeBlock(block: Block): Block = block match {
  case Paragraph(content)              => Paragraph(normalizeInlines(content))
  case Heading(level, content)         => Heading(level, normalizeInlines(content))
  case BlockQuote(content)             => BlockQuote(normalizeBlocks(content))
  case BulletList(items, tight)        => BulletList(items.map(normalizeListItem), tight)
  case OrderedList(start, items, tight) => OrderedList(start, items.map(normalizeListItem), tight)
  case ListItem(content, checked)      => ListItem(normalizeBlocks(content), checked)
  case Table(header, alignments, rows) => Table(normalizeTableRow(header), alignments, rows.map(normalizeTableRow))
  case other                           => other  // CodeBlock, ThematicBreak, HtmlBlock unchanged
}

private def normalizeListItem(item: ListItem): ListItem =
  ListItem(normalizeBlocks(item.content), item.checked)

private def normalizeTableRow(row: TableRow): TableRow =
  TableRow(row.cells.map(normalizeInlines))

def normalizeInlines(inlines: Chunk[Inline]): Chunk[Inline] =
  inlines
    .foldLeft(Chunk.empty[Inline]) { (acc, inline) =>
      (acc.lastOption, normalizeInline(inline)) match {
        case (Some(Text(a)), Text(b))         => acc.dropRight(1) :+ Text(a + b)
        case (Some(Inline.Text(a)), Text(b))  => acc.dropRight(1) :+ Text(a + b)
        case (Some(Text(a)), Inline.Text(b))  => acc.dropRight(1) :+ Text(a + b)
        case (Some(Inline.Text(a)), Inline.Text(b)) => acc.dropRight(1) :+ Text(a + b)
        case (_, normalized)                  => acc :+ normalized
      }
    }
    .filter {
      case Text("")        => false
      case Inline.Text("") => false
      case _               => true
    }

def normalizeInline(inline: Inline): Inline = inline match {
  case Emphasis(content)             => Emphasis(normalizeInlines(content))
  case Inline.Emphasis(content)      => Emphasis(normalizeInlines(content))
  case Strong(content)               => Strong(normalizeInlines(content))
  case Inline.Strong(content)        => Strong(normalizeInlines(content))
  case Strikethrough(content)        => Strikethrough(normalizeInlines(content))
  case Inline.Strikethrough(content) => Strikethrough(normalizeInlines(content))
  case Link(text, url, title)        => Link(normalizeInlines(text), url, title)
  case Inline.Link(text, url, title) => Link(normalizeInlines(text), url, title)
  case other                         => other
}

def isEmpty(block: Block): Boolean = block match {
  case Paragraph(content) => 
    content.isEmpty || content.forall {
      case Text("")        => true
      case Inline.Text("") => true
      case _               => false
    }
  case BlockQuote(content)         => content.isEmpty
  case BulletList(items, _)        => items.isEmpty
  case OrderedList(_, items, _)    => items.isEmpty
  case _                           => false
}
```

### Equality and HashCode

```scala
// In Doc case class

override def equals(obj: Any): Boolean = obj match {
  case that: Doc => 
    this.normalize.blocks == that.normalize.blocks && 
    this.normalize.metadata == that.normalize.metadata
  case _ => false
}

override def hashCode(): Int = {
  val normalized = this.normalize
  (normalized.blocks, normalized.metadata).hashCode()
}
```

**Test Cases:**
- `AdtSpec.scala` → suite "Doc" → suite "equality via normalization" → test "structurally different but semantically equal docs are equal"
- `AdtSpec.scala` → suite "Doc" → suite "equality via normalization" → test "hashCode is consistent with equals"
- `AdtSpec.scala` → suite "Doc" → suite "equality via normalization" → test "different content docs are not equal"

---

## Metadata Merging Specification

### Semantics
`Doc.++` uses standard Scala `Map.++` semantics:
- All keys from left Doc are included
- All keys from right Doc are included
- On key conflict, **right wins** (standard Map behavior)

### Examples with Code

**Example 1: Disjoint keys**
```scala
val doc1 = Doc(Chunk.empty, Map("a" -> "1"))
val doc2 = Doc(Chunk.empty, Map("b" -> "2"))
(doc1 ++ doc2).metadata == Map("a" -> "1", "b" -> "2")  // true
```
**Test Case:** `AdtSpec.scala` → suite "Doc" → suite "++ concatenation" → test "merges metadata"

**Example 2: Conflicting keys (right wins)**
```scala
val doc1 = Doc(Chunk.empty, Map("k" -> "left"))
val doc2 = Doc(Chunk.empty, Map("k" -> "right"))
(doc1 ++ doc2).metadata == Map("k" -> "right")  // true
```
**Test Case:** `AdtSpec.scala` → suite "Doc" → suite "++ concatenation" → test "right metadata wins on conflict"

**Example 3: Mixed**
```scala
val doc1 = Doc(Chunk.empty, Map("a" -> "1", "b" -> "2"))
val doc2 = Doc(Chunk.empty, Map("b" -> "3", "c" -> "4"))
(doc1 ++ doc2).metadata == Map("a" -> "1", "b" -> "3", "c" -> "4")  // true
```

---

## Rendering Edge Cases with Test Mapping

### HTML Rendering Edge Cases

| # | Case | Input | Expected HTML | Test Location |
|---|------|-------|---------------|---------------|
| 1 | Empty document | `Doc(Chunk.empty)` | `<!DOCTYPE html><html><head></head><body></body></html>` | HtmlRendererSpec → "render - Full document" → "empty document" |
| 2 | HTML entities in Text | `Text("<script>&\"'")` | `&lt;script&gt;&amp;&quot;&#39;` | HtmlRendererSpec → "renderFragment - Inlines" → "Text with HTML entities" |
| 3 | Code block without lang | `CodeBlock(None, "x")` | `<pre><code>x</code></pre>` | HtmlRendererSpec → "renderFragment - Blocks" → "CodeBlock without language" |
| 4 | Code block with lang | `CodeBlock(Some("scala"), "x")` | `<pre><code class="language-scala">x</code></pre>` | HtmlRendererSpec → "renderFragment - Blocks" → "CodeBlock with language" |
| 5 | OrderedList start != 1 | `OrderedList(5, ...)` | `<ol start="5">...</ol>` | HtmlRendererSpec → "renderFragment - Blocks" → "OrderedList with start" |
| 6 | Task list checked | `ListItem(_, Some(true))` | `<li><input type="checkbox" checked disabled>...</li>` | HtmlRendererSpec → "renderFragment - Blocks" → "Task list items" |
| 7 | Task list unchecked | `ListItem(_, Some(false))` | `<li><input type="checkbox" disabled>...</li>` | HtmlRendererSpec → "renderFragment - Blocks" → "Task list items" |
| 8 | Link with title | `Link(_, url, Some("tip"))` | `<a href="url" title="tip">...</a>` | HtmlRendererSpec → "renderFragment - Inlines" → "Link with title" |
| 9 | Email autolink | `Autolink("x@y.com", true)` | `<a href="mailto:x@y.com">x@y.com</a>` | HtmlRendererSpec → "renderFragment - Inlines" → "Autolink email" |
| 10 | HtmlBlock passthrough | `HtmlBlock("<div>raw</div>")` | `<div>raw</div>` (no escaping) | HtmlRendererSpec → "renderFragment - Blocks" → "HtmlBlock passthrough" |
| 11 | HtmlInline passthrough | `HtmlInline("<br/>")` | `<br/>` (no escaping) | HtmlRendererSpec → "renderFragment - Inlines" → "HtmlInline passthrough" |
| 12 | SoftBreak | `SoftBreak` | ` ` (space) | HtmlRendererSpec → "renderFragment - Inlines" → "SoftBreak" |
| 13 | HardBreak | `HardBreak` | `<br>` | HtmlRendererSpec → "renderFragment - Inlines" → "HardBreak" |

### Terminal Rendering Edge Cases

| # | Case | Input | Expected Terminal | Test Location |
|---|------|-------|-------------------|---------------|
| 1 | Empty document | `Doc(Chunk.empty)` | `` (empty string) | TerminalRendererSpec → "Edge cases" → "Empty document" |
| 2 | H1 heading | `Heading(H1, ...)` | Bold + Red | TerminalRendererSpec → "Headings" → "H1 renders with bold and red" |
| 3 | H2 heading | `Heading(H2, ...)` | Bold + Yellow | TerminalRendererSpec → "Headings" → "H2 renders with bold and yellow" |
| 4 | Nested styles | `Strong(Emphasis(Text("x")))` | Bold + Italic | TerminalRendererSpec → "Edge cases" → "Nested styles" |
| 5 | Code block | `CodeBlock(_, code)` | Gray background | TerminalRendererSpec → "Blocks" → "CodeBlock renders with gray background" |
| 6 | Inline code | `Code("x")` | Gray background | TerminalRendererSpec → "Inline styles" → "Code renders with gray background" |
| 7 | Link | `Link(Text("click"), "url", _)` | Blue + underline + ` (url)` | TerminalRendererSpec → "Links" → "Link renders with blue underline and URL" |
| 8 | ThematicBreak | `ThematicBreak` | `─────...` (horizontal line) | TerminalRendererSpec → "Blocks" → "ThematicBreak renders as horizontal line" |
| 9 | Task list checked | `ListItem(_, Some(true))` | `[x] ...` | TerminalRendererSpec → "Blocks" → "Task list checked item" |
| 10 | Task list unchecked | `ListItem(_, Some(false))` | `[ ] ...` | TerminalRendererSpec → "Blocks" → "Task list unchecked item" |

---

## Verification Strategy (MANDATORY)

### Test Decision
- **Infrastructure exists**: YES
- **User wants tests**: YES (Tests-after for this feature work)
- **Framework**: ZIO Test (`zio.test.sbt.ZTestFramework`)

### Automated Verification

Each TODO includes executable verification:
- Test commands: `sbt markdownJVM/test`
- Cross-Scala: `sbt '++2.13.18; markdownJVM/test'`
- Format: `sbt 'markdownJVM/scalafmt; markdownJVM/Test/scalafmt'`

### Coverage Threshold (from AGENTS.md)

Per AGENTS.md "Staged Verification Workflow" → "Stage 2 — Coverage":
- Coverage command: `sbt "project markdownJVM; coverage; test; coverageReport"`
- Threshold: 95% (module minimum)
- Coverage must include ALL new code paths

### Complete Block/Inline Type Coverage Checklist

**Block Types - HtmlRenderer (10 types):**
- [x] Paragraph → test: "Paragraph"
- [x] Heading → test: "Heading H1-H6"
- [x] CodeBlock → tests: "CodeBlock without language", "CodeBlock with language"
- [x] ThematicBreak → test: "ThematicBreak"
- [x] BlockQuote → test: "BlockQuote"
- [x] BulletList → test: "BulletList"
- [x] OrderedList → test: "OrderedList with start"
- [x] ListItem → test: "Task list items"
- [x] HtmlBlock → test: "HtmlBlock passthrough"
- [x] Table → test: "Table"

**Inline Types - HtmlRenderer (11 types):**
- [x] Text → test: "Text with HTML entities"
- [x] Code → test: "Code"
- [x] Emphasis → test: "Emphasis"
- [x] Strong → test: "Strong"
- [x] Strikethrough → test: "Strikethrough"
- [x] Link → tests: "Link without title", "Link with title"
- [x] Image → test: "Image"
- [x] HtmlInline → test: "HtmlInline passthrough"
- [x] SoftBreak → test: "SoftBreak"
- [x] HardBreak → test: "HardBreak"
- [x] Autolink → tests: "Autolink URL", "Autolink email"

**Block Types - TerminalRenderer (10 types):**
- [x] Paragraph → (implicitly tested via inline tests)
- [x] Heading → tests: "H1 renders with bold and red", "H2 renders with bold and yellow"
- [x] CodeBlock → test: "CodeBlock renders with gray background"
- [x] ThematicBreak → test: "ThematicBreak renders as horizontal line"
- [x] BlockQuote → test: "BlockQuote"
- [x] BulletList → test: "BulletList renders with bullets"
- [x] OrderedList → test: "OrderedList"
- [x] ListItem → tests: "Task list checked item", "Task list unchecked item"
- [x] HtmlBlock → test: "HtmlBlock passthrough"
- [x] Table → test: "Table"

**Inline Types - TerminalRenderer (11 types):**
- [x] Text → (implicitly tested)
- [x] Code → test: "Code renders with gray background"
- [x] Emphasis → test: "Emphasis renders as italic"
- [x] Strong → test: "Strong renders as bold"
- [x] Strikethrough → test: "Strikethrough renders with strikethrough"
- [x] Link → test: "Link renders with blue underline and URL"
- [x] Image → test: "Image with title"
- [x] HtmlInline → test: "HtmlInline passthrough"
- [x] SoftBreak → test: "SoftBreak"
- [x] HardBreak → test: "HardBreak"
- [x] Autolink → test: "Autolink renders with blue underline"

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Start Immediately):
├── Task 1: ToMarkdown for Block [no dependencies]
└── (sequential within wave)

Wave 2 (After Task 1):
├── Task 2: Doc methods [depends: Task 1 for context]
└── (sequential within wave)

Wave 3 (After Task 2):
├── Task 3: HtmlRenderer [depends: Doc.toHtml signature]
└── Task 4: TerminalRenderer [depends: Doc.toTerminal signature]
    (these can run in parallel)

Wave 4 (After Wave 3):
└── Task 5: Final verification and commit cleanup

Critical Path: Task 1 → Task 2 → Task 3 → Task 5
```

### Dependency Matrix

| Task | Depends On | Blocks | Can Parallelize With |
|------|------------|--------|---------------------|
| 1 | None | 2 | None |
| 2 | 1 | 3, 4 | None |
| 3 | 2 | 5 | 4 |
| 4 | 2 | 5 | 3 |
| 5 | 3, 4 | None | None (final) |

---

## TODOs

- [x] 1. Add ToMarkdown instance for Block types (COMPLETED: commit c4b61446)

  **What to do**:
  - Add implicit `blockToMarkdown: ToMarkdown[Block]` to `ToMarkdown.scala` companion object (after line 79)
  - Implementation: `(b: Block) => Text(Renderer.render(Doc(Chunk(b))).trim)`
  - Add test suite "Blocks" to `ToMarkdownSpec.scala` (after line 65)

  **Must NOT do**:
  - Do not modify Renderer.scala
  - Do not change existing ToMarkdown instances

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Small, focused addition to existing file
  - **Skills**: []
    - No special skills needed for this simple addition

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 1 (sequential)
  - **Blocks**: Task 2
  - **Blocked By**: None (can start immediately)

  **References**:

  | Reference | File | Lines | Purpose |
  |-----------|------|-------|---------|
  | Existing ToMarkdown pattern | ToMarkdown.scala | 47-79 | Follow same style: Scaladoc + single-line SAM |
  | Renderer.render | Renderer.scala | 25-26 | Use to convert Doc to string |
  | Doc constructor | Doc.scala | 24 | `Doc(blocks: Chunk[Block], metadata: Map[String, String] = Map.empty)` |
  | Chunk import | ToMarkdown.scala | 35 | Already imported: `zio.blocks.chunk.Chunk` |
  | Collections test pattern | ToMarkdownSpec.scala | 35-65 | Follow same style for Block tests |

  **Acceptance Criteria**:

  ```bash
  sbt markdownJVM/test
  # Expected: All tests pass
  # Exit code: 0
  ```

  **Test Cases to Add** (ToMarkdownSpec.scala, new suite after line 65):
  ```scala
  suite("Blocks")(
    test("Paragraph converts to rendered markdown") {
      val block = Paragraph(Chunk(Text("hello")))
      val result = ToMarkdown[Block].toMarkdown(block)
      assertTrue(result == Text("hello"))
    },
    test("Heading converts to rendered markdown") {
      val block = Heading(HeadingLevel.H1, Chunk(Text("Title")))
      val result = ToMarkdown[Block].toMarkdown(block)
      assertTrue(result == Text("# Title"))
    },
    test("CodeBlock converts to rendered markdown") {
      val block = CodeBlock(Some("scala"), "val x = 1")
      val result = ToMarkdown[Block].toMarkdown(block)
      assertTrue(result == Text("```scala\nval x = 1\n```"))
    },
    test("ThematicBreak converts to rendered markdown") {
      val result = ToMarkdown[Block].toMarkdown(ThematicBreak)
      assertTrue(result == Text("---"))
    },
    test("BlockQuote converts to rendered markdown") {
      val block = BlockQuote(Chunk(Paragraph(Chunk(Text("quoted")))))
      val result = ToMarkdown[Block].toMarkdown(block)
      assertTrue(result == Text("> quoted"))
    }
  )
  ```

  **Commit**: YES
  - Message: `feat(markdown): add ToMarkdown instance for Block types`
  - Files: `ToMarkdown.scala`, `ToMarkdownSpec.scala`
  - Pre-commit: `sbt markdownJVM/test`

---

- [x] 2. Add Doc methods (++, toString, toHtml, toHtmlFragment, toTerminal, normalize) (COMPLETED: commit d507a414)

  **What to do**:
  - Modify `Doc.scala` to add methods and companion object
  - Add `++` method: `def ++(other: Doc): Doc = Doc(blocks ++ other.blocks, metadata ++ other.metadata)`
  - Add `toString` override: `override def toString: String = Renderer.render(this)`
  - Add `toHtmlFragment` method: `def toHtmlFragment: String = HtmlRenderer.renderFragment(this)`
  - Add `toHtml` method: `def toHtml: String = HtmlRenderer.render(this)`
  - Add `toTerminal` method: `def toTerminal: String = TerminalRenderer.render(this)`
  - Add `normalize` method: `def normalize: Doc = Doc(Doc.normalizeBlocks(blocks), metadata)`
  - Override `equals` and `hashCode` (see Normalization Specification above)
  - Add `object Doc` companion with normalization helpers (see algorithm above)
  - Add test suite "Doc" to `AdtSpec.scala`

  **Must NOT do**:
  - Do not create HtmlRenderer.scala yet (Task 3)
  - Do not create TerminalRenderer.scala yet (Task 4)
  - Do not modify existing Block/Inline case classes

  **IMPORTANT**: The `toHtml`, `toHtmlFragment`, and `toTerminal` methods will fail to compile until Tasks 3 and 4 are complete. Options:
  1. **Option A (Recommended)**: Comment out these methods initially, uncomment after Tasks 3/4
  2. **Option B**: Create stub objects `HtmlRenderer` and `TerminalRenderer` with placeholder methods

  **Recommended Agent Profile**:
  - **Category**: `unspecified-low`
    - Reason: Medium-sized feature addition with clear requirements
  - **Skills**: []
    - No special skills needed

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2 (sequential)
  - **Blocks**: Tasks 3, 4
  - **Blocked By**: Task 1 (for consistent workflow)

  **References**:

  | Reference | File | Lines | Purpose |
  |-----------|------|-------|---------|
  | Current Doc | Doc.scala | 1-25 | File to modify |
  | Renderer.render | Renderer.scala | 25-26 | Pattern for toString |
  | Normalization spec | This plan | "Normalization Specification" section | Complete algorithm |
  | Metadata merging spec | This plan | "Metadata Merging Specification" section | ++ semantics |
  | AdtSpec | AdtSpec.scala | (entire file) | Where to add Doc tests |

  **Acceptance Criteria**:

  ```bash
  sbt markdownJVM/test
  # Expected: All tests pass (toHtml/toTerminal tests may be skipped or stubbed)
  # Exit code: 0
  ```

  **Test Cases** (see Normalization Specification section for complete list with test names)

  **Commit**: YES
  - Message: `feat(markdown): add Doc methods (++, toString, normalize, equals/hashCode)`
  - Files: `Doc.scala`, `AdtSpec.scala`
  - Pre-commit: `sbt markdownJVM/test`

---

- [x] 3. Create HtmlRenderer (COMPLETED: commit 799eb9ae)

  **What to do**:
  - Create new file `markdown/shared/src/main/scala/zio/blocks/markdown/HtmlRenderer.scala`
  - Implement `render(doc: Doc): String` - Full HTML5 document
  - Implement `renderFragment(doc: Doc): String` - Just the content HTML
  - Implement `renderBlock(block: Block): String` - Match on all 10 Block types
  - Implement `renderInline(inline: Inline): String` - Match on all 11 Inline types (BOTH variants)
  - Implement `escape(s: String): String` - HTML entity escaping
  - Create test file `markdown/shared/src/test/scala/zio/blocks/markdown/HtmlRendererSpec.scala`

  **Block Type → HTML Mapping** (exhaustive):

  | Block Type | HTML Output |
  |------------|-------------|
  | Paragraph(content) | `<p>{renderInlines(content)}</p>` |
  | Heading(H1, content) | `<h1>{renderInlines(content)}</h1>` |
  | Heading(H2, content) | `<h2>{renderInlines(content)}</h2>` |
  | Heading(H3, content) | `<h3>{renderInlines(content)}</h3>` |
  | Heading(H4, content) | `<h4>{renderInlines(content)}</h4>` |
  | Heading(H5, content) | `<h5>{renderInlines(content)}</h5>` |
  | Heading(H6, content) | `<h6>{renderInlines(content)}</h6>` |
  | CodeBlock(None, code) | `<pre><code>{escape(code)}</code></pre>` |
  | CodeBlock(Some(lang), code) | `<pre><code class="language-{lang}">{escape(code)}</code></pre>` |
  | ThematicBreak | `<hr>` |
  | BlockQuote(content) | `<blockquote>{renderBlocks(content)}</blockquote>` |
  | BulletList(items, _) | `<ul>{items.map(renderBlock).mkString}</ul>` |
  | OrderedList(1, items, _) | `<ol>{items.map(renderBlock).mkString}</ol>` |
  | OrderedList(n, items, _) | `<ol start="{n}">{items.map(renderBlock).mkString}</ol>` |
  | ListItem(content, None) | `<li>{renderBlocks(content)}</li>` |
  | ListItem(content, Some(true)) | `<li><input type="checkbox" checked disabled>{renderBlocks(content)}</li>` |
  | ListItem(content, Some(false)) | `<li><input type="checkbox" disabled>{renderBlocks(content)}</li>` |
  | HtmlBlock(html) | `{html}` (passthrough, no escaping) |
  | Table(header, alignments, rows) | `<table><thead><tr>{header cells as th}</tr></thead><tbody>{rows as tr/td}</tbody></table>` |

  **Inline Type → HTML Mapping** (exhaustive, handle BOTH variants):

  | Inline Type | HTML Output |
  |-------------|-------------|
  | Text(value) / Inline.Text(value) | `{escape(value)}` |
  | Code(value) / Inline.Code(value) | `<code>{escape(value)}</code>` |
  | Emphasis(content) / Inline.Emphasis(content) | `<em>{renderInlines(content)}</em>` |
  | Strong(content) / Inline.Strong(content) | `<strong>{renderInlines(content)}</strong>` |
  | Strikethrough(content) / Inline.Strikethrough(content) | `<del>{renderInlines(content)}</del>` |
  | Link(text, url, None) / Inline.Link(...) | `<a href="{escape(url)}">{renderInlines(text)}</a>` |
  | Link(text, url, Some(title)) / Inline.Link(...) | `<a href="{escape(url)}" title="{escape(title)}">{renderInlines(text)}</a>` |
  | Image(alt, url, None) / Inline.Image(...) | `<img src="{escape(url)}" alt="{escape(alt)}">` |
  | Image(alt, url, Some(title)) / Inline.Image(...) | `<img src="{escape(url)}" alt="{escape(alt)}" title="{escape(title)}">` |
  | HtmlInline(html) / Inline.HtmlInline(html) | `{html}` (passthrough, no escaping) |
  | SoftBreak / Inline.SoftBreak | ` ` (single space) |
  | HardBreak / Inline.HardBreak | `<br>` |
  | Autolink(url, false) / Inline.Autolink(url, false) | `<a href="{escape(url)}">{escape(url)}</a>` |
  | Autolink(email, true) / Inline.Autolink(email, true) | `<a href="mailto:{escape(email)}">{escape(email)}</a>` |

  **HTML Entity Escaping**:
  ```scala
  def escape(s: String): String =
    s.replace("&", "&amp;")
     .replace("<", "&lt;")
     .replace(">", "&gt;")
     .replace("\"", "&quot;")
     .replace("'", "&#39;")
  ```

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: New file with substantial logic covering all Block/Inline types
  - **Skills**: []
    - No special skills needed

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3 (with Task 4)
  - **Blocks**: Task 5
  - **Blocked By**: Task 2

  **References**:

  | Reference | File | Lines | Purpose |
  |-----------|------|-------|---------|
  | Block rendering pattern | Renderer.scala | 36-81 | Pattern match structure |
  | Inline rendering pattern | Renderer.scala | 191-250 | Handles BOTH variants |
  | Block types (exhaustive) | This plan | "Complete Type Definitions" section | All 10 types |
  | Inline types (exhaustive) | This plan | "Complete Type Definitions" section | All 11 types |
  | Edge cases | This plan | "HTML Rendering Edge Cases" section | All edge cases with test mapping |

  **Acceptance Criteria**:

  ```bash
  sbt markdownJVM/test
  # Expected: All tests pass
  # Exit code: 0
  ```

  **Test Cases**: See "HTML Rendering Edge Cases with Test Mapping" section for complete list

  **Commit**: YES
  - Message: `feat(markdown): add HtmlRenderer for HTML output`
  - Files: `HtmlRenderer.scala`, `HtmlRendererSpec.scala`
  - Pre-commit: `sbt markdownJVM/test`

---

- [x] 4. Create TerminalRenderer (COMPLETED: commit 6ce118af)

  **What to do**:
  - Create new file `markdown/shared/src/main/scala/zio/blocks/markdown/TerminalRenderer.scala`
  - Define ANSI escape code constants
  - Implement `render(doc: Doc): String`
  - Implement `renderBlock(block: Block): String` - Match on all 10 Block types
  - Implement `renderInline(inline: Inline): String` - Match on all 11 Inline types (BOTH variants)
  - Create test file `markdown/shared/src/test/scala/zio/blocks/markdown/TerminalRendererSpec.scala`

  **ANSI Escape Code Constants**:
  ```scala
  object TerminalRenderer {
    private val Reset         = "\u001b[0m"
    private val Bold          = "\u001b[1m"
    private val Italic        = "\u001b[3m"
    private val Underline     = "\u001b[4m"
    private val Strikethrough = "\u001b[9m"
    private val Red           = "\u001b[31m"
    private val Green         = "\u001b[32m"
    private val Yellow        = "\u001b[33m"
    private val Blue          = "\u001b[34m"
    private val Magenta       = "\u001b[35m"
    private val Cyan          = "\u001b[36m"
    private val GrayBg        = "\u001b[48;5;236m"
    
    private def headingColor(level: HeadingLevel): String = level match {
      case HeadingLevel.H1 => Red
      case HeadingLevel.H2 => Yellow
      case HeadingLevel.H3 => Green
      case HeadingLevel.H4 => Cyan
      case HeadingLevel.H5 => Blue
      case HeadingLevel.H6 => Magenta
    }
  }
  ```

  **Block Type → Terminal Mapping** (exhaustive):

  | Block Type | Terminal Output |
  |------------|-----------------|
  | Paragraph(content) | `{renderInlines(content)}\n\n` |
  | Heading(level, content) | `{Bold}{headingColor(level)}{renderInlines(content)}{Reset}\n\n` |
  | CodeBlock(_, code) | `{GrayBg}{code}{Reset}\n\n` |
  | ThematicBreak | `${"─" * 40}\n\n` |
  | BlockQuote(content) | Each line prefixed with `│ ` |
  | BulletList(items, _) | Each item prefixed with `• ` |
  | OrderedList(start, items, _) | Items prefixed with `{start}. `, `{start+1}. `, etc. |
  | ListItem(content, None) | `{renderBlocks(content)}` |
  | ListItem(content, Some(true)) | `[x] {renderBlocks(content)}` |
  | ListItem(content, Some(false)) | `[ ] {renderBlocks(content)}` |
  | HtmlBlock(html) | `{html}\n\n` (passthrough) |
  | Table(...) | Render with box-drawing characters |

  **Inline Type → Terminal Mapping** (exhaustive, handle BOTH variants):

  | Inline Type | Terminal Output |
  |-------------|-----------------|
  | Text(value) / Inline.Text(value) | `{value}` |
  | Code(value) / Inline.Code(value) | `{GrayBg}{value}{Reset}` |
  | Emphasis(content) / Inline.Emphasis(content) | `{Italic}{renderInlines(content)}{Reset}` |
  | Strong(content) / Inline.Strong(content) | `{Bold}{renderInlines(content)}{Reset}` |
  | Strikethrough(content) / Inline.Strikethrough(content) | `{Strikethrough}{renderInlines(content)}{Reset}` |
  | Link(text, url, _) / Inline.Link(...) | `{Blue}{Underline}{renderInlines(text)}{Reset} ({url})` |
  | Image(alt, url, _) / Inline.Image(...) | `[Image: {alt}] ({url})` |
  | HtmlInline(html) / Inline.HtmlInline(html) | `{html}` (passthrough) |
  | SoftBreak / Inline.SoftBreak | ` ` (space) |
  | HardBreak / Inline.HardBreak | `\n` |
  | Autolink(url, _) / Inline.Autolink(...) | `{Blue}{Underline}{url}{Reset}` |

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: New file with ANSI rendering logic for all types
  - **Skills**: []
    - No special skills needed

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3 (with Task 3)
  - **Blocks**: Task 5
  - **Blocked By**: Task 2

  **References**:

  | Reference | File | Lines | Purpose |
  |-----------|------|-------|---------|
  | Block rendering pattern | Renderer.scala | 36-81 | Pattern match structure |
  | Inline rendering pattern | Renderer.scala | 191-250 | Handles BOTH variants |
  | Block types (exhaustive) | This plan | "Complete Type Definitions" section | All 10 types |
  | Inline types (exhaustive) | This plan | "Complete Type Definitions" section | All 11 types |
  | Edge cases | This plan | "Terminal Rendering Edge Cases" section | All edge cases with test mapping |

  **Acceptance Criteria**:

  ```bash
  sbt markdownJVM/test
  # Expected: All tests pass
  # Exit code: 0
  ```

  **Test Cases**: See "Terminal Rendering Edge Cases with Test Mapping" section for complete list

  **Commit**: YES
  - Message: `feat(markdown): add TerminalRenderer for ANSI terminal output`
  - Files: `TerminalRenderer.scala`, `TerminalRendererSpec.scala`
  - Pre-commit: `sbt markdownJVM/test`

---

- [x] 5. Final verification, cross-Scala testing, and format (COMPLETED: commits 6e913f13, 232fcc40, pushed to origin/docs)

  **What to do**:
  - Run full test suite on JVM with Scala 3
  - Run cross-Scala verification with Scala 2.13
  - Run scalafmt on all modified files
  - Verify coverage meets 95% threshold
  - Push to origin/docs branch

  **Must NOT do**:
  - Do not skip cross-Scala testing
  - Do not push without format verification

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Verification and cleanup tasks
  - **Skills**: [`git-master`]
    - `git-master`: For proper commit hygiene and push

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 4 (final)
  - **Blocks**: None (final task)
  - **Blocked By**: Tasks 3, 4

  **References**:

  | Reference | File | Section | Purpose |
  |-----------|------|---------|---------|
  | Staged Verification | AGENTS.md | "Staged Verification Workflow" | Required verification process |
  | Coverage stage | AGENTS.md | "Stage 2 — Coverage" | Coverage command and threshold |
  | Format stage | AGENTS.md | "Stage 6 — Format" | Format commands |

  **Acceptance Criteria**:

  ```bash
  # Stage 1 - Test on Scala 3
  sbt markdownJVM/test
  # Expected: All tests pass
  # Exit code: 0

  # Stage 2 - Coverage
  sbt "project markdownJVM; coverage; test; coverageReport"
  # Expected: Coverage >= 95%
  # Exit code: 0

  # Stage 3 - Cross-Scala
  sbt "++2.13.18; markdownJVM/test"
  # Expected: All tests pass
  # Exit code: 0

  # Stage 6 - Format
  sbt "markdownJVM/scalafmt; markdownJVM/Test/scalafmt"
  # Expected: No changes (already formatted) or formatting applied
  # Exit code: 0

  # Push
  git push origin docs
  # Expected: Push succeeds
  ```

  **Commit**: NO (verification only, then push)

---

## Commit Strategy

| After Task | Message | Files | Verification |
|------------|---------|-------|--------------|
| 1 | `feat(markdown): add ToMarkdown instance for Block types` | ToMarkdown.scala, ToMarkdownSpec.scala | sbt markdownJVM/test |
| 2 | `feat(markdown): add Doc methods (++, toString, normalize, equals/hashCode)` | Doc.scala, AdtSpec.scala | sbt markdownJVM/test |
| 3 | `feat(markdown): add HtmlRenderer for HTML output` | HtmlRenderer.scala, HtmlRendererSpec.scala | sbt markdownJVM/test |
| 4 | `feat(markdown): add TerminalRenderer for ANSI terminal output` | TerminalRenderer.scala, TerminalRendererSpec.scala | sbt markdownJVM/test |
| 5 | (no commit - push existing) | - | Full verification |

---

## Success Criteria

### Verification Commands
```bash
sbt markdownJVM/test                              # All tests pass
sbt "++2.13.18; markdownJVM/test"                 # Cross-Scala passes
sbt "markdownJVM/scalafmt; markdownJVM/Test/scalafmt"  # Format clean
sbt "project markdownJVM; coverage; test; coverageReport"  # Coverage >= 95%
```

### Final Checklist
- [x] ToMarkdown[Block] converts blocks to markdown Text
- [x] Doc.++ concatenates docs with metadata merge (right wins)
- [x] Doc.toString renders as markdown
- [x] Doc.toHtml produces full HTML document
- [x] Doc.toHtmlFragment produces content HTML only
- [x] Doc.toTerminal produces ANSI colored output
- [x] Doc.normalize merges Text nodes and removes empty blocks
- [x] Doc.equals uses normalized comparison
- [x] Doc.hashCode is consistent with equals
- [x] HtmlRenderer covers all 10 Block types (see checklist)
- [x] HtmlRenderer covers all 11 Inline types (see checklist)
- [x] HtmlRenderer escapes HTML entities properly (&, <, >, ", ')
- [x] TerminalRenderer covers all 10 Block types
- [x] TerminalRenderer covers all 11 Inline types
- [x] TerminalRenderer uses correct ANSI codes with Reset
- [x] All tests pass on both Scala 3 and 2.13
- [x] Coverage >= 95% (achieved 100%)
- [x] Code formatted

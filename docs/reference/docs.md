---
id: docs
title: "Docs (Markdown)"
---

ZIO Blocks Markdown is a **pure, zero-dependency GitHub Flavored Markdown library** providing an immutable ADT for markdown documents, a strict parser with error handling, multiple renderers (GFM markdown, HTML, terminal), and a compile-time validated string interpolator. Core types: `Doc`, `Block`, `Inline`, `Parser`, `Renderer`, `ToMarkdown`.

Here are the core type definitions:

```scala
final case class Doc(blocks: Chunk[Block], metadata: Map[String, String] = Map.empty)

sealed trait Block
final case class Paragraph(content: Chunk[Inline]) extends Block
final case class Heading(level: HeadingLevel, content: Chunk[Inline]) extends Block
final case class CodeBlock(info: Option[String], code: String) extends Block

sealed trait Inline
final case class Text(value: String) extends Inline
final case class Code(value: String) extends Inline
final case class Link(text: Chunk[Inline], url: String, title: Option[String]) extends Inline
```

## Motivation

Markdown is the de facto standard for documentation, READMEs, and formatted text in software development. However, parsing and generating markdown at runtime often requires hand-crafted parsers or fragile string concatenation. ZIO Blocks Markdown provides:

- **Type-safe ADT**: Every markdown element is a Scala case class—no stringly-typed HTML generation
- **Strict parsing**: Rejects invalid markdown with precise error locations (line, column)
- **Round-trip semantics**: Parse-render-parse cycles preserve document structure
- **Normalized equality**: Two documents are equal if their normalized forms are equal (adjacent text nodes merged, empty blocks removed)
- **Multiple output formats**: Render to GFM markdown, HTML (full document or fragment), or colorized terminal
- **Compile-time validation**: The `md"..."` string interpolator validates markdown syntax at compile time, catching errors before runtime
- **Type class system**: `ToMarkdown` lets you interpolate custom types into markdown documents

## Installation

Add the dependency to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-docs" % "@VERSION@"
```

For Scala.js, use the cross-build syntax:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-docs" % "@VERSION@"
```

Supported Scala versions: 2.13.x and 3.x

## How They Work Together

Markdown documents flow through a **parsing → normalization → rendering pipeline**:

```
Markdown String ──> Parser ──> Doc (blocks) ──> Renderer ──> Markdown/HTML/Terminal
                        │           │
                    ParseError   ToMarkdown
                                (type class)

Doc = Chunk[Block]
Block = Paragraph | Heading | CodeBlock | List | Table | ... (contains Inlines)
Inline = Text | Code | Emphasis | Strong | Link | Image | ... (leaf nodes)
```

**Typical workflow:**

1. **Parse markdown** — `Parser.parse("# Hello")` returns `Either[ParseError, Doc]`
2. **Build programmatically** — construct `Doc` with `Heading`, `Paragraph`, `BulletList`, etc.
3. **Normalize** — `doc.normalize` merges adjacent text, removes empty blocks
4. **Render to desired format** — GFM (`Renderer.render`), HTML (`HtmlRenderer.render`), terminal (`TerminalRenderer.render`)
5. **Use interpolator** — `md"# Title with $interpolation"` validates markdown at compile time

Here's a complete example of composing a document:

```scala mdoc:silent
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val doc = Doc(Chunk(
  Heading(HeadingLevel.H1, Chunk(Text("My Document"))),
  Paragraph(Chunk(Text("This is a paragraph with "), Strong(Chunk(Text("bold"))), Text(" text."))),
  CodeBlock(Some("scala"), "val x = 42"),
  BulletList(Chunk(
    ListItem(Chunk(Paragraph(Chunk(Text("Item 1")))), None),
    ListItem(Chunk(Paragraph(Chunk(Text("Item 2")))), None)
  ), tight = true)
))
```

Rendering to GFM markdown:

```scala mdoc
Renderer.render(doc)
```

Rendering to HTML:

```scala mdoc
HtmlRenderer.render(doc)
```

Rendering to terminal:

```scala mdoc
TerminalRenderer.render(doc)
```

Parsing markdown strings to create documents:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val markdown = """# My Document

This is a paragraph with **bold** text.

- Item 1
- Item 2
"""

Parser.parse(markdown) match {
  case Right(parsedDoc) => println(s"Successfully parsed ${parsedDoc.blocks.size} blocks")
  case Left(err) => println(s"Parse error: ${err.message}")
}
```

Round-trip verification (parse → render → parse preserves structure):

```scala mdoc
val input = "# Hello\n\nWorld"
val parsed1 = Parser.parse(input).toOption.get
val rendered = Renderer.render(parsed1)
val parsed2 = Parser.parse(rendered).toOption.get
parsed1 == parsed2  // Equal after normalization
```

## Common Patterns

The markdown module provides several patterns for working with documents and types. Here are the most common usage scenarios:

### String Interpolation with Compile-Time Validation

The `md"..."` interpolator validates markdown at compile time and supports interpolation of any type with a `ToMarkdown` instance:

```scala mdoc:silent
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val name = "Alice"
val count = 42
```

Now interpolate these values into markdown:

```scala mdoc
md"# Welcome $name\nYou have $count items."
```

### Task Lists (GFM Feature)

Task list items use `ListItem` with `checked: Option[Boolean]`:

```scala mdoc:silent
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val tasks = BulletList(Chunk(
  ListItem(Chunk(Paragraph(Chunk(Text("Buy groceries")))), Some(true)),
  ListItem(Chunk(Paragraph(Chunk(Text("Write docs")))), Some(false)),
  ListItem(Chunk(Paragraph(Chunk(Text("Call Alice")))), None)  // No checkbox
), tight = true)
```

Rendering the task list:

```scala mdoc
Renderer.render(Doc(Chunk(tasks)))
```

### Tables with Alignment

Tables require a header row, alignment specification, and data rows:

```scala mdoc:silent
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val table = Table(
  header = TableRow(Chunk(
    Chunk(Text("Name")),
    Chunk(Text("Age")),
    Chunk(Text("City"))
  )),
  alignments = Chunk(Alignment.Left, Alignment.Right, Alignment.Center),
  rows = Chunk(
    TableRow(Chunk(
      Chunk(Text("Alice")),
      Chunk(Text("30")),
      Chunk(Text("NYC"))
    )),
    TableRow(Chunk(
      Chunk(Text("Bob")),
      Chunk(Text("25")),
      Chunk(Text("LA"))
    ))
  )
)
```

Rendering the table to markdown:

```scala mdoc
Renderer.render(Doc(Chunk(table)))
```

### Parsing with Error Handling

Parser returns `Either[ParseError, Doc]` with precise location information:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

Parser.parse("# Hello\n[invalid link](") match {
  case Right(doc) => println("Parsed successfully")
  case Left(err) => println(s"Error at line ${err.line}, column ${err.column}: ${err.message}")
}
```

### Round-Trip Semantics

Parse-render-parse cycles preserve document meaning (normalized forms are equal):

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val input = "# Hello\n\nWorld"
val parsed1 = Parser.parse(input).toOption.get
val rendered = Renderer.render(parsed1)
val parsed2 = Parser.parse(rendered).toOption.get
assert(parsed1 == parsed2)  // Equal after normalization
```

### Custom Type Interpolation

Implement `ToMarkdown` for your types to enable interpolation:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

case class User(name: String, role: String)

implicit val userToMarkdown: ToMarkdown[User] = { user =>
  Strong(Chunk(Text(user.name), Text(s" – ${user.role}")))
}

val user = User("Alice", "Engineer")
val doc = md"# Team\n$user"
```

Additional use cases for custom type interpolation include building documentation programmatically with rich data types, generating reports with structured business objects, and creating templates that mix markdown formatting with domain-specific data. For example, you could create instances for your API models to automatically format them as markdown tables or code examples, making it easy to generate consistent documentation from live data structures.

## Integration Points

The markdown module integrates seamlessly with other ZIO Blocks components to provide a cohesive ecosystem for working with structured data and documentation.

**Schema Integration**: The `schema` module can derive codecs for markdown documents, enabling serialization and deserialization of `Doc` and related types. This allows you to persist markdown structures to various formats (JSON, MessagePack, BSON, etc.) while maintaining type safety and schema validation.

**Chunk Integration**: Core data structures like `Doc`, `Block`, and `Inline` use `Chunk[T]` throughout for efficient immutable sequences. This provides O(1) concatenation, memory efficiency, and seamless interoperability with other ZIO libraries that also rely on chunks for data streaming and collection manipulation.

**HTTP Integration**: Markdown documents can be served directly as documentation endpoints in HTTP servers. Combined with the multiple renderer options (GFM, HTML, terminal), you can build documentation APIs that serve content in multiple formats based on client preferences, making it trivial to expose living documentation alongside your application.

---

## Doc

A complete GitHub Flavored Markdown document.

### Definition

`Doc(blocks: Chunk[Block], metadata: Map[String, String] = Map.empty)` represents a parsed or constructed markdown document. The `metadata` field is reserved for future use and defaults to an empty map.

### Construction

Create an empty document:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val empty = Doc.empty
```

Construct a document from blocks:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val doc = Doc(Chunk(
  Heading(HeadingLevel.H1, Chunk(Text("Title"))),
  Paragraph(Chunk(Text("Content")))
))
```

Parse a markdown string to create a document:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

Parser.parse("# Title\n\nContent") match {
  case Right(doc) => // use doc
  case Left(err) => // handle error
}
```

### Core Operations

Merge documents using concatenation with `Doc#++`:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val doc1 = Doc(Chunk(Heading(HeadingLevel.H1, Chunk(Text("Part 1")))))
val doc2 = Doc(Chunk(Paragraph(Chunk(Text("Part 2")))))
val combined = doc1 ++ doc2
```

Render a document to GFM markdown:

```scala mdoc:reset
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val doc = Doc(Chunk(Heading(HeadingLevel.H1, Chunk(Text("Hello")))))
val markdown: String = doc.toString  // Calls Renderer.render internally
```

Render to HTML with full document structure including DOCTYPE (returns complete HTML5 document with `<!DOCTYPE html>` wrapper):

```scala mdoc:reset
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val doc = Doc(Chunk(Heading(HeadingLevel.H1, Chunk(Text("Hello")))))
val html = doc.toHtml
```

Render to HTML fragment containing only the content (returns just the rendered HTML blocks without wrapper tags):

```scala mdoc:reset
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val doc = Doc(Chunk(Heading(HeadingLevel.H1, Chunk(Text("Hello")))))
val fragment = doc.toHtmlFragment
```

Render to colorized terminal output (returns ANSI-colored string suitable for terminal display):

```scala mdoc:reset
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val doc = Doc(Chunk(Heading(HeadingLevel.H1, Chunk(Text("Hello")))))
val terminal = doc.toTerminal
```

Canonicalize document structure with `Doc#normalize` to merge adjacent text nodes and remove empty blocks:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val doc = Doc(Chunk(
  Paragraph(Chunk(Text("A"), Text("B"))),  // Adjacent text nodes
  Paragraph(Chunk()),                      // Empty paragraph
  Paragraph(Chunk(Text("C")))
))
```

Call `Doc#normalize` to see the result:

```scala mdoc
val normalized = doc.normalize
```

### Equality and Hashing

Two documents are equal if their **normalized forms** are equal. This means:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val doc1 = Doc(Chunk(Paragraph(Chunk(Text("Hello"), Text(" "), Text("World")))))
val doc2 = Doc(Chunk(Paragraph(Chunk(Text("Hello World")))))
assert(doc1 == doc2)  // Equal after normalization
```

Hash code is computed from the normalized form for consistency with `equals`.

---

## Block (Sealed Trait)

A block-level markdown element. Block is a sealed trait with the following concrete subtypes.

### Paragraph

A paragraph containing inline content.

Construct a paragraph with inline content:

```
Paragraph(content: Chunk[Inline])
```

Here's an example of creating a paragraph with mixed content:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val para = Paragraph(Chunk(
  Text("Hello "),
  Strong(Chunk(Text("world")))
))
```

### Heading

An ATX-style heading (# to ######) with a level and inline content.

Construct a heading with a level and inline content:

```
Heading(level: HeadingLevel, content: Chunk[Inline])
```

Here's an example of creating headings with different levels:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val h1 = Heading(HeadingLevel.H1, Chunk(Text("Title")))
val h3 = Heading(HeadingLevel.H3, Chunk(Text("Subsection")))
```

### CodeBlock

A fenced code block with optional language/info string.

Construct a code block with optional language specification:

```scala
CodeBlock(info: Option[String], code: String)
```

Here are examples of creating code blocks with and without language specification:

```scala mdoc:compile-only
// Scala code block
val scalaBlock = CodeBlock(Some("scala"), "val x = 42\nprintln(x)")

// No language specified
val plainBlock = CodeBlock(None, "some code")
```

### ThematicBreak

A thematic break (horizontal rule) represented as `case object ThematicBreak`.

Create a thematic break:

```scala mdoc:compile-only
val break = ThematicBreak
```

**Renders as:** `---\n` (or `***` or `___`)

### BlockQuote

A block quote containing nested blocks.

Construct a block quote with nested blocks:

```scala
BlockQuote(content: Chunk[Block])
```

Here's an example of creating a block quote:
```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val quote = BlockQuote(Chunk(
  Paragraph(Chunk(Text("This is a famous quote.")))
))
```

### BulletList

An unordered list with bullet markers (-, *, +).

Construct a bullet list with items and a tightness parameter:

```scala
BulletList(items: Chunk[ListItem], tight: Boolean)
```

The `tight` parameter controls spacing: `true` removes blank lines between items for compact rendering. Here's an example:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val list = BulletList(Chunk(
  ListItem(Chunk(Paragraph(Chunk(Text("Item 1")))), None),
  ListItem(Chunk(Paragraph(Chunk(Text("Item 2")))), None)
), tight = true)
```

### OrderedList

An ordered list with numeric markers (1., 2., etc.).

Construct an ordered list with a starting number, items, and a tightness parameter:

```scala
OrderedList(start: Int, items: Chunk[ListItem], tight: Boolean)
```

The `start` parameter specifies the starting number (typically 1). Here's an example:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val list = OrderedList(
  start = 1,
  items = Chunk(
    ListItem(Chunk(Paragraph(Chunk(Text("First")))), None),
    ListItem(Chunk(Paragraph(Chunk(Text("Second")))), None)
  ),
  tight = true
)
```

### ListItem

A list item, optionally a task list item.

Construct a list item with content and optional checkbox status:

```scala
ListItem(content: Chunk[Block], checked: Option[Boolean])
```

The `checked` parameter: `Some(true)` renders as `[x]`, `Some(false)` renders as `[ ]`, `None` for regular list items. Here are examples of each type:

```scala mdoc:compile-only
// Regular list item
val item = ListItem(Chunk(Paragraph(Chunk(Text("Task")))), None)

// Completed task
val completed = ListItem(Chunk(Paragraph(Chunk(Text("Done")))), Some(true))

// Incomplete task
val incomplete = ListItem(Chunk(Paragraph(Chunk(Text("TODO")))), Some(false))
```

### HtmlBlock

Raw HTML block content.

Construct raw HTML block content:

```scala
HtmlBlock(content: String)
```

Here's an example of creating an HTML block:

```scala mdoc:compile-only
val html = HtmlBlock("<div class='alert'>Custom HTML</div>")
```

### Table

A GitHub Flavored Markdown table with aligned columns.

Construct a table with header, column alignments, and data rows:

```scala
Table(header: TableRow, alignments: Chunk[Alignment], rows: Chunk[TableRow])
```

Here's an example of creating a table:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val table = Table(
  header = TableRow(Chunk(Chunk(Text("Name")), Chunk(Text("Age")))),
  alignments = Chunk(Alignment.Left, Alignment.Right),
  rows = Chunk(
    TableRow(Chunk(Chunk(Text("Alice")), Chunk(Text("30")))),
    TableRow(Chunk(Chunk(Text("Bob")), Chunk(Text("25"))))
  )
)
```

---

## Inline (Sealed Trait)

An inline-level markdown element. Inline is a sealed trait with concrete subtypes (defined both in object and at top level for API compatibility).

### Text

Plain text content.

Create plain text content:

```scala
Text(value: String)
// or
Inline.Text(value: String)
```

Here's an example of creating text:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val text = Text("Hello world")
```

### Code

Inline code span (backtick-delimited).

Create inline code:

```scala
Code(value: String)
// or
Inline.Code(value: String)
```

Here's an example of creating inline code:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val code = Code("val x = 42")
```

### Emphasis

Emphasized (italic) text.

Create emphasized text:

```scala
Emphasis(content: Chunk[Inline])
// or
Inline.Emphasis(content: Chunk[Inline])
```

Here's an example of creating emphasized text:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val emphasis = Emphasis(Chunk(Text("italic")))
// Renders as: *italic*
```

### Strong

Strong (bold) text.

Create strong text:

```scala
Strong(content: Chunk[Inline])
// or
Inline.Strong(content: Chunk[Inline])
```

Here's an example of creating strong text:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val strong = Strong(Chunk(Text("bold")))
// Renders as: **bold**
```

### Strikethrough

Strikethrough text (GFM feature).

Create strikethrough text:

```scala
Strikethrough(content: Chunk[Inline])
// or
Inline.Strikethrough(content: Chunk[Inline])
```

Here's an example of creating strikethrough text:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val struck = Strikethrough(Chunk(Text("deprecated")))
// Renders as: ~~deprecated~~
```

### Link

A hyperlink.

Construct a link with text, URL, and optional title:

```scala
Link(text: Chunk[Inline], url: String, title: Option[String])
// or
Inline.Link(text: Chunk[Inline], url: String, title: Option[String])
```

The `title` parameter is optional link title text. Here are examples of creating links:

```scala mdoc:compile-only
// Simple link
val link = Link(Chunk(Text("Click here")), "https://example.com", None)

// Link with title
val titled = Link(Chunk(Text("Docs")), "/docs", Some("Documentation"))
```

### Image

An image reference.

Construct an image with alt text, URL, and optional title:

```scala
Image(alt: String, url: String, title: Option[String])
// or
Inline.Image(alt: String, url: String, title: Option[String])
```

Here are examples of creating images:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val img = Image(alt = "Logo", url = "/logo.png", None)
val imgWithTitle = Image(alt = "Icon", url = "/icon.svg", Some("App Icon"))
```

### HtmlInline

Raw HTML inline content.

Create raw HTML inline content:

```
HtmlInline(content: String)
// or
Inline.HtmlInline(content: String)
```

Here's an example of creating HTML inline content:

```scala mdoc:compile-only
val html = HtmlInline("<span class='highlight'>custom</span>")
```

### SoftBreak

A soft line break (single newline, rendered as space or newline depending on context).

Create a soft line break:

```scala mdoc:compile-only
SoftBreak
// or
Inline.SoftBreak
```

**Renders as:** `\n` (a single newline in output)

### HardBreak

A hard line break (two spaces or backslash before newline).

Create a hard line break:

```scala mdoc:compile-only
HardBreak
// or
Inline.HardBreak
```

**Renders as:** `  \n` (two spaces followed by newline)

### Autolink

An autolink (URL or email in angle brackets).

Construct an autolink with a URL or email:

```
Autolink(url: String, isEmail: Boolean)
// or
Inline.Autolink(url: String, isEmail: Boolean)
```

Here are examples of creating autolinks:

```scala mdoc:compile-only
val urlLink = Autolink("https://example.com", isEmail = false)
val emailLink = Autolink("user@example.com", isEmail = true)
// Renders as: <https://example.com> and <user@example.com>
```

---

## HeadingLevel

Heading levels from H1 to H6, with convenience constructors.

### Predefined Levels

Access predefined heading levels from H1 to H6:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

HeadingLevel.H1  // value = 1
HeadingLevel.H2  // value = 2
HeadingLevel.H3  // value = 3
HeadingLevel.H4  // value = 4
HeadingLevel.H5  // value = 5
HeadingLevel.H6  // value = 6
```

### Safe Construction

Construct a heading level from an integer, returning an `Option`:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

HeadingLevel.fromInt(2) == Some(HeadingLevel.H2)
HeadingLevel.fromInt(7) == None  // Out of range
```

Construct a heading level unsafely, throwing if input is invalid:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

HeadingLevel.unsafeFromInt(3)  // HeadingLevel.H3
HeadingLevel.unsafeFromInt(7)  // Throws IllegalArgumentException
```

### Accessing Value

Access the numeric value of a heading level:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

HeadingLevel.H1.value == 1
```

---

## Alignment

Table column alignment specification.

### Predefined Alignments

Use predefined alignment values for table columns:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

Alignment.Left    // Renders as :---
Alignment.Right   // Renders as ---:
Alignment.Center  // Renders as :---:
Alignment.None    // Renders as ---
```

### Use in Tables

Create a table with specific column alignments:

```
val alignments = Chunk(Alignment.Left, Alignment.Center, Alignment.Right)
val table = Table(header, alignments, rows)
```

---

## TableRow

A single row in a table, containing cells as inline content.

Construct a table row with cells:

```
TableRow(cells: Chunk[Chunk[Inline]])
```

Each cell is a `Chunk[Inline]`, allowing formatted content (text, links, emphasis, etc.). Here's an example:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val row = TableRow(Chunk(
  Chunk(Text("Alice")),
  Chunk(Strong(Chunk(Text("30")))),
  Chunk(Link(Chunk(Text("NYC")), "/cities/nyc", None))
))
```

---

## Parser

Strict GitHub Flavored Markdown parser with position-aware error reporting.

### Parsing

Use the main entry point to parse markdown:

```
Parser.parse(input: String): Either[ParseError, Doc]
```

Returns `Right(doc)` on success or `Left(error)` on parse failure. Here's an example:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val result = Parser.parse("# Hello\n\nWorld")
result match {
  case Right(doc) => println(s"Parsed ${doc.blocks.size} blocks")
  case Left(err) => println(s"Parse error at line ${err.line}: ${err.message}")
}
```

### Supported Features

- ATX headings (# to ######)
- Fenced code blocks (``` or ~~~)
- Thematic breaks (---, ***, ___)
- Block quotes (> prefix)
- Bullet lists (-, *, +)
- Ordered lists (1., 2., etc.)
- Task lists (- [ ] and - [x])
- Tables with alignment (GFM)
- Inline formatting (emphasis, strong, strikethrough)
- Links and images with optional titles
- Autolinks (<url> or <email>)
- HTML blocks and inline HTML
- Soft and hard line breaks

### Unsupported Features

- YAML frontmatter (causes parse error)
- Setext headings (use ATX style with # instead)
- Indented code blocks (use fenced with ``` instead)
- Link reference definitions

---

## ParseError

Error information from parsing, with precise location data.

Construct a `ParseError` with message and location information:

```
ParseError(
  message: String,    // Human-readable error description
  line: Int,          // 1-based line number
  column: Int,        // 1-based column number
  input: String       // The line that caused the error
)
```

Here's an example of creating a `ParseError`:

```scala mdoc:silent
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val err = ParseError("Unexpected token", line = 5, column = 12, input = "[invalid markdown]")
```

Get the string representation of the error:

```scala mdoc
err.toString
```

---

## Renderer

Renders markdown documents back to GitHub Flavored Markdown string format.

### Rendering

Render an entire document to GFM markdown:

```
Renderer.render(doc: Doc): String
```

Here's an example of rendering a document:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val doc = Doc(Chunk(
  Heading(HeadingLevel.H1, Chunk(Text("Title"))),
  Paragraph(Chunk(Text("Content")))
))
val rendered = Renderer.render(doc)
```

The result:

```scala mdoc
rendered
```

Render individual blocks:

```
Renderer.renderBlock(block: Block): String
```

Render inline content:

```
Renderer.renderInlines(inlines: Chunk[Inline]): String
Renderer.renderInline(inline: Inline): String
```

### Normalization During Rendering

The renderer does not normalize; use `doc.normalize` before rendering if normalization is needed.

### Round-Trip Semantics

Re-parse rendered output to verify round-trip semantics:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val original = Doc(Chunk(Heading(HeadingLevel.H1, Chunk(Text("Title")))))
val markdown = Renderer.render(original)
val reparsed = Parser.parse(markdown).toOption.get
assert(original == reparsed)  // Equal after normalization
```

---

## HtmlRenderer

Renders markdown documents to HTML5.

### Full Document Rendering

Render a document as complete HTML with DOCTYPE and html wrapper tags:

```
HtmlRenderer.render(doc: Doc): String
```

Here's an example of rendering a complete HTML document:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val doc = Doc(Chunk(Heading(HeadingLevel.H1, Chunk(Text("Title")))))
val html = HtmlRenderer.render(doc)
```

The result:

```scala mdoc
html
```

### Fragment Rendering

Render content-only HTML without wrapper tags:

```
HtmlRenderer.renderFragment(doc: Doc): String
```

Here's an example of rendering an HTML fragment:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val fragment = HtmlRenderer.renderFragment(doc)
// <h1>Title</h1>
```

Use fragments to embed markdown content into existing HTML templates.

### HTML Feature Mapping

- Headings → `<h1>` through `<h6>`
- Paragraphs → `<p>`
- Code blocks → `<pre><code class="language-...">` (language from info string)
- Thematic breaks → `<hr>`
- Block quotes → `<blockquote>`
- Lists → `<ul>` and `<ol>` with `<li>`
- Task lists → `<li>` with checkbox-like rendering
- Tables → `<table>`, `<tr>`, `<td>` with alignment classes
- Inline code → `<code>`
- Emphasis → `<em>`
- Strong → `<strong>`
- Strikethrough → `<del>` or `<s>`
- Links → `<a href="...">`
- Images → `<img alt="..." src="...">`
- HTML blocks and inlines → passed through as-is

---

## TerminalRenderer

Renders markdown to ANSI-colored terminal output optimized for console display.

### Rendering

Render a document to ANSI-colored terminal output:
```
TerminalRenderer.render(doc: Doc): String
```

Here's an example of rendering to terminal:
```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val doc = Doc(Chunk(Heading(HeadingLevel.H1, Chunk(Text("Title")))))
val terminal = TerminalRenderer.render(doc)
// Returns ANSI-colored string: [31m[1mTitle[0m\n\n
```

### Color Scheme

Headings use different colors by level:
- H1 → Red
- H2 → Yellow
- H3 → Green
- H4 → Cyan
- H5 → Blue
- H6 → Magenta

Inline elements use ANSI styles:
- Strong/Bold → ANSI bold
- Emphasis/Italic → ANSI italic
- Code → Gray background
- Links → Underlined
- Strikethrough → ANSI strikethrough style

The output is designed to be readable on both light and dark terminal backgrounds.

---

## ToMarkdown

Type class for converting Scala values to markdown inline elements, enabling interpolation in the `md"..."` string interpolator.

### Definition

Define the `ToMarkdown` type class:

```scala
trait ToMarkdown[-A] {
  def toMarkdown(a: A): Inline
}
```

The type parameter is contravariant (`-A`), allowing supertype instances to be used for subtypes.

### Summon an Instance

Summon a ToMarkdown instance:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

ToMarkdown[String]  // Implicitly summons the ToMarkdown[String] instance
```

### Built-In Instances

Convert primitive types to plain text:
```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

ToMarkdown[String]    // a: String => Text(a)
ToMarkdown[Int]       // a: Int => Text(a.toString)
ToMarkdown[Long]      // a: Long => Text(a.toString)
ToMarkdown[Double]    // a: Double => Text(a.toString)
ToMarkdown[Boolean]   // a: Boolean => Text(a.toString)
```

Pass inline elements through unchanged:
```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

ToMarkdown[Inline]    // a: Inline => a (identity)
```

Convert collections to comma-separated text:
```
ToMarkdown[List[A]]    // as: List[A] => Text(as.map(...).mkString(", "))
ToMarkdown[Chunk[A]]   // as: Chunk[A] => Text(as.map(...).mkString(", "))
ToMarkdown[Vector[A]]  // as: Vector[A] => Text(as.map(...).mkString(", "))
ToMarkdown[Seq[A]]     // as: Seq[A] => Text(as.map(...).mkString(", "))
```

For collection instances, each element is converted using its `ToMarkdown[A]` instance, then joined with ", ".

Render blocks to markdown text:
```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

ToMarkdown[Block]     // b: Block => Text(Renderer.render(Doc(Chunk(b))).trim)
```

### Custom Implementations

Define custom `ToMarkdown` instances for your types:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

case class Person(name: String, age: Int)

implicit val personToMarkdown: ToMarkdown[Person] = { person =>
  Strong(Chunk(Text(person.name)))
}

val p = Person("Alice", 30)
val doc = md"## User: $p"  // Interpolates as strong text "Alice"
```

---

## String Interpolator (`md"..."`)

The `md` string interpolator validates markdown at compile time and supports runtime interpolation of values.

### Compile-Time Validation

Markdown syntax inside `md"..."` is validated at compile time:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val doc = md"# Valid heading"  // Compiles

val invalid = md"# [unclosed link]("  // Compile error: Invalid markdown
```

### Runtime Interpolation

Values are interpolated and converted to inline markdown using `ToMarkdown`:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

val name = "Alice"
val count = 42
val doc = md"""
# Welcome $name

You have $count items.
"""
// Equivalent to parsing: "# Welcome Alice\n\nYou have 42 items."
```

### Custom Type Interpolation

Any type with a `ToMarkdown` instance can be interpolated:

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.docs._

case class Tag(label: String)
implicit val tagToMarkdown: ToMarkdown[Tag] = tag =>
  Code(tag.label)

val tag = Tag("important")
val doc = md"Please review: $tag"  // Renders tag as inline code
```

### Interpolation Mechanics

The interpolator:
1. Collects string parts and interpolated values (as `Inline` elements via `ToMarkdown`)
2. Combines parts and rendered inlines into a markdown string
3. Parses the combined string at runtime
4. Returns a `Doc` or throws `IllegalArgumentException` if parsing fails

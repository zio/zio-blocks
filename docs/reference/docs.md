---
id: docs
title: "Docs (Markdown)"
---

ZIO Blocks Markdown is a **pure, zero-dependency GitHub Flavored Markdown library** providing an immutable ADT for markdown documents, a strict parser with error handling, multiple renderers (GFM markdown, HTML, terminal), and a compile-time validated string interpolator. Core types: `Doc`, `Block`, `Inline`, `Parser`, `Renderer`, `ToMarkdown`.

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

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-docs" % "@VERSION@"
```

For Scala.js:

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

**Example composition:**

```scala
val doc = Doc(Chunk(
  Heading(HeadingLevel.H1, Chunk(Text("My Document"))),
  Paragraph(Chunk(Text("This is a paragraph with "), Strong(Chunk(Text("bold"))), Text(" text."))),
  CodeBlock(Some("scala"), "val x = 42"),
  BulletList(Chunk(
    ListItem(Chunk(Paragraph(Chunk(Text("Item 1")))), None),
    ListItem(Chunk(Paragraph(Chunk(Text("Item 2")))), None)
  ), tight = true)
))

val markdown = Renderer.render(doc)  // String in GFM format
val html = HtmlRenderer.render(doc)  // Complete HTML5 document
val terminal = TerminalRenderer.render(doc)  // ANSI-colored terminal output
```

## Common Patterns

The markdown module provides several patterns for working with documents and types. Here are the most common usage scenarios:

### String Interpolation with Compile-Time Validation

The `md"..."` interpolator validates markdown at compile time and supports interpolation of any type with a `ToMarkdown` instance:

```scala
val name = "Alice"
val count = 42
val doc = md"# Welcome $name\nYou have $count items."
```

### Task Lists (GFM Feature)

Task list items use `ListItem` with `checked: Option[Boolean]`:

```scala
val tasks = BulletList(Chunk(
  ListItem(Chunk(Paragraph(Chunk(Text("Buy groceries")))), Some(true)),
  ListItem(Chunk(Paragraph(Chunk(Text("Write docs")))), Some(false))
), tight = true)

Renderer.render(Doc(Chunk(tasks)))
// Renders as:
// - [x] Buy groceries
// - [ ] Write docs
```

### Tables with Alignment

Tables require a header row, alignment specification, and data rows:

```scala
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

### Parsing with Error Handling

Parser returns `Either[ParseError, Doc]` with precise location information:

```scala
Parser.parse("# Hello\n[invalid link](") match {
  case Right(doc) => println("Parsed successfully")
  case Left(err) => println(s"Error at line ${err.line}, column ${err.column}: ${err.message}")
}
```

### Round-Trip Semantics

Parse-render-parse cycles preserve document meaning (normalized forms are equal):

```scala
val input = "# Hello\n\nWorld"
val parsed1 = Parser.parse(input).toOption.get
val rendered = Renderer.render(parsed1)
val parsed2 = Parser.parse(rendered).toOption.get
assert(parsed1 == parsed2)  // Equal after normalization
```

### Custom Type Interpolation

Implement `ToMarkdown` for your types to enable interpolation:

```scala
case class User(name: String, role: String)

implicit val userToMarkdown: ToMarkdown[User] = { user =>
  Paragraph(Chunk(
    Strong(Chunk(Text(user.name))),
    Text(s" – ${user.role}")
  ))
}

val user = User("Alice", "Engineer")
val doc = md"# Team\n$user"
```

## Integration Points

- **With Schema**: `schema` module can derive codecs for markdown documents
- **With Chunk**: `Doc` and `Block`/`Inline` use `Chunk[T]` for efficient immutable sequences
- **With HTTP**: Markdown can be served as documentation endpoints in HTTP servers

---

## Doc

A complete GitHub Flavored Markdown document.

### Definition

`Doc(blocks: Chunk[Block], metadata: Map[String, String] = Map.empty)` represents a parsed or constructed markdown document. The `metadata` field is reserved for future use and defaults to an empty map.

### Construction

**Empty document:**
```scala
val empty = Doc.empty
```

**From blocks:**
```scala
val doc = Doc(Chunk(
  Heading(HeadingLevel.H1, Chunk(Text("Title"))),
  Paragraph(Chunk(Text("Content")))
))
```

**By parsing:**
```scala
Parser.parse("# Title\n\nContent") match {
  case Right(doc) => // use doc
  case Left(err) => // handle error
}
```

### Core Operations

**Concatenation** (`++`) merges blocks and metadata:

```scala
val doc1 = Doc(Chunk(Heading(HeadingLevel.H1, Chunk(Text("Part 1")))))
val doc2 = Doc(Chunk(Paragraph(Chunk(Text("Part 2")))))
val combined = doc1 ++ doc2
```

**Rendering to GFM markdown:**

```scala
val doc = Doc(Chunk(Heading(HeadingLevel.H1, Chunk(Text("Hello")))))
val markdown: String = doc.toString  // Calls Renderer.render internally
```

**Rendering to HTML (full document with DOCTYPE):**

```scala
val html = doc.toHtml
// Returns: <!DOCTYPE html><html><head></head><body>...</body></html>
```

**Rendering to HTML fragment (content only):**

```scala
val fragment = doc.toHtmlFragment
// Returns: <h1>Hello</h1><p>...</p> (no DOCTYPE or wrapper tags)
```

**Rendering to colorized terminal output:**

```scala
val terminal = doc.toTerminal
// Returns ANSI-colored string suitable for terminal display
```

**Normalization** (`normalize`) canonicalizes structure by merging adjacent `Text` nodes and removing empty blocks:

```scala
val doc = Doc(Chunk(
  Paragraph(Chunk(Text("A"), Text("B"))),  // Adjacent text nodes
  Paragraph(Chunk()),                      // Empty paragraph
  Paragraph(Chunk(Text("C")))
))
val normalized = doc.normalize
// Result: Paragraph with Chunk(Text("AB")), and empty paragraph removed
```

### Equality and Hashing

Two documents are equal if their **normalized forms** are equal. This means:

```scala
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

**Construction:**
```scala
Paragraph(content: Chunk[Inline])
```

**Example:**
```scala
val para = Paragraph(Chunk(
  Text("Hello "),
  Strong(Chunk(Text("world")))
))
```

### Heading

An ATX-style heading (# to ######) with a level and inline content.

**Construction:**
```scala
Heading(level: HeadingLevel, content: Chunk[Inline])
```

**Example:**
```scala
val h1 = Heading(HeadingLevel.H1, Chunk(Text("Title")))
val h3 = Heading(HeadingLevel.H3, Chunk(Text("Subsection")))
```

### CodeBlock

A fenced code block with optional language/info string.

**Construction:**
```scala
CodeBlock(info: Option[String], code: String)
```

**Examples:**
```scala
// Scala code block
val scalaBlock = CodeBlock(Some("scala"), "val x = 42\nprintln(x)")

// No language specified
val plainBlock = CodeBlock(None, "some code")
```

### ThematicBreak

A thematic break (horizontal rule) represented as `case object ThematicBreak`.

**Construction:**
```scala
val break = ThematicBreak
```

**Renders as:** `---\n` (or `***` or `___`)

### BlockQuote

A block quote containing nested blocks.

**Construction:**
```scala
BlockQuote(content: Chunk[Block])
```

**Example:**
```scala
val quote = BlockQuote(Chunk(
  Paragraph(Chunk(Text("This is a famous quote.")))
))
```

### BulletList

An unordered list with bullet markers (-, *, +).

**Construction:**
```scala
BulletList(items: Chunk[ListItem], tight: Boolean)
```

The `tight` parameter controls spacing: `true` removes blank lines between items for compact rendering.

**Example:**
```scala
val list = BulletList(Chunk(
  ListItem(Chunk(Paragraph(Chunk(Text("Item 1")))), None),
  ListItem(Chunk(Paragraph(Chunk(Text("Item 2")))), None)
), tight = true)
```

### OrderedList

An ordered list with numeric markers (1., 2., etc.).

**Construction:**
```scala
OrderedList(start: Int, items: Chunk[ListItem], tight: Boolean)
```

The `start` parameter specifies the starting number (typically 1).

**Example:**
```scala
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

**Construction:**
```scala
ListItem(content: Chunk[Block], checked: Option[Boolean])
```

The `checked` parameter: `Some(true)` renders as `[x]`, `Some(false)` renders as `[ ]`, `None` for regular list items.

**Examples:**
```scala
// Regular list item
val item = ListItem(Chunk(Paragraph(Chunk(Text("Task")))), None)

// Completed task
val completed = ListItem(Chunk(Paragraph(Chunk(Text("Done")))), Some(true))

// Incomplete task
val incomplete = ListItem(Chunk(Paragraph(Chunk(Text("TODO")))), Some(false))
```

### HtmlBlock

Raw HTML block content.

**Construction:**
```scala
HtmlBlock(content: String)
```

**Example:**
```scala
val html = HtmlBlock("<div class='alert'>Custom HTML</div>")
```

### Table

A GitHub Flavored Markdown table with aligned columns.

**Construction:**
```scala
Table(header: TableRow, alignments: Chunk[Alignment], rows: Chunk[TableRow])
```

**Example:**
```scala
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

**Construction:**
```scala
Text(value: String)
// or
Inline.Text(value: String)
```

**Example:**
```scala
val text = Text("Hello world")
```

### Code

Inline code span (backtick-delimited).

**Construction:**
```scala
Code(value: String)
// or
Inline.Code(value: String)
```

**Example:**
```scala
val code = Code("val x = 42")
```

### Emphasis

Emphasized (italic) text.

**Construction:**
```scala
Emphasis(content: Chunk[Inline])
// or
Inline.Emphasis(content: Chunk[Inline])
```

**Example:**
```scala
val emphasis = Emphasis(Chunk(Text("italic")))
// Renders as: *italic*
```

### Strong

Strong (bold) text.

**Construction:**
```scala
Strong(content: Chunk[Inline])
// or
Inline.Strong(content: Chunk[Inline])
```

**Example:**
```scala
val strong = Strong(Chunk(Text("bold")))
// Renders as: **bold**
```

### Strikethrough

Strikethrough text (GFM feature).

**Construction:**
```scala
Strikethrough(content: Chunk[Inline])
// or
Inline.Strikethrough(content: Chunk[Inline])
```

**Example:**
```scala
val struck = Strikethrough(Chunk(Text("deprecated")))
// Renders as: ~~deprecated~~
```

### Link

A hyperlink.

**Construction:**
```scala
Link(text: Chunk[Inline], url: String, title: Option[String])
// or
Inline.Link(text: Chunk[Inline], url: String, title: Option[String])
```

The `title` parameter is optional link title text.

**Examples:**
```scala
// Simple link
val link = Link(Chunk(Text("Click here")), "https://example.com", None)

// Link with title
val titled = Link(Chunk(Text("Docs")), "/docs", Some("Documentation"))
```

### Image

An image reference.

**Construction:**
```scala
Image(alt: String, url: String, title: Option[String])
// or
Inline.Image(alt: String, url: String, title: Option[String])
```

**Examples:**
```scala
val img = Image(alt = "Logo", url = "/logo.png", None)
val imgWithTitle = Image(alt = "Icon", url = "/icon.svg", Some("App Icon"))
```

### HtmlInline

Raw HTML inline content.

**Construction:**
```scala
HtmlInline(content: String)
// or
Inline.HtmlInline(content: String)
```

**Example:**
```scala
val html = HtmlInline("<span class='highlight'>custom</span>")
```

### SoftBreak

A soft line break (single newline, rendered as space or newline depending on context).

**Construction:**
```scala
SoftBreak
// or
Inline.SoftBreak
```

**Renders as:** `\n` (a single newline in output)

### HardBreak

A hard line break (two spaces or backslash before newline).

**Construction:**
```scala
HardBreak
// or
Inline.HardBreak
```

**Renders as:** `  \n` (two spaces followed by newline)

### Autolink

An autolink (URL or email in angle brackets).

**Construction:**
```scala
Autolink(url: String, isEmail: Boolean)
// or
Inline.Autolink(url: String, isEmail: Boolean)
```

**Examples:**
```scala
val urlLink = Autolink("https://example.com", isEmail = false)
val emailLink = Autolink("user@example.com", isEmail = true)
// Renders as: <https://example.com> and <user@example.com>
```

---

## HeadingLevel

Heading levels from H1 to H6, with convenience constructors.

### Predefined Levels

```scala
HeadingLevel.H1  // value = 1
HeadingLevel.H2  // value = 2
HeadingLevel.H3  // value = 3
HeadingLevel.H4  // value = 4
HeadingLevel.H5  // value = 5
HeadingLevel.H6  // value = 6
```

### Safe Construction

**From integer (returns `Option`):**
```scala
HeadingLevel.fromInt(2) == Some(HeadingLevel.H2)
HeadingLevel.fromInt(7) == None  // Out of range
```

**Unsafe construction (throws on invalid input):**
```scala
HeadingLevel.unsafeFromInt(3)  // HeadingLevel.H3
HeadingLevel.unsafeFromInt(7)  // Throws IllegalArgumentException
```

### Accessing Value

```scala
HeadingLevel.H1.value == 1
```

---

## Alignment

Table column alignment specification.

### Predefined Alignments

```scala
Alignment.Left    // Renders as :---
Alignment.Right   // Renders as ---:
Alignment.Center  // Renders as :---:
Alignment.None    // Renders as ---
```

### Use in Tables

```scala
val alignments = Chunk(Alignment.Left, Alignment.Center, Alignment.Right)
val table = Table(header, alignments, rows)
```

---

## TableRow

A single row in a table, containing cells as inline content.

**Construction:**
```scala
TableRow(cells: Chunk[Chunk[Inline]])
```

Each cell is a `Chunk[Inline]`, allowing formatted content (text, links, emphasis, etc.).

**Example:**
```scala
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

**Main entry point:**
```scala
Parser.parse(input: String): Either[ParseError, Doc]
```

Returns `Right(doc)` on success or `Left(error)` on parse failure.

**Example:**
```scala
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

**Construction:**
```scala
ParseError(
  message: String,    // Human-readable error description
  line: Int,          // 1-based line number
  column: Int,        // 1-based column number
  input: String       // The line that caused the error
)
```

**Example:**
```scala
ParseError("Unexpected token", line = 5, column = 12, input = "[invalid markdown]")
```

**String representation:**
```scala
err.toString
// ParseError at line 5, column 12: Unexpected token
//   [invalid markdown]
```

---

## Renderer

Renders markdown documents back to GitHub Flavored Markdown string format.

### Rendering

**Render entire document:**
```scala
Renderer.render(doc: Doc): String
```

**Example:**
```scala
val doc = Doc(Chunk(
  Heading(HeadingLevel.H1, Chunk(Text("Title"))),
  Paragraph(Chunk(Text("Content")))
))
val markdown = Renderer.render(doc)
// "# Title\n\nContent\n\n"
```

**Render blocks:**
```scala
Renderer.renderBlock(block: Block): String
```

**Render inlines:**
```scala
Renderer.renderInlines(inlines: Chunk[Inline]): String
Renderer.renderInline(inline: Inline): String
```

### Normalization During Rendering

The renderer does not normalize; use `doc.normalize` before rendering if normalization is needed.

### Round-Trip Semantics

Rendered output can be re-parsed:
```scala
val original = Doc(Chunk(Heading(HeadingLevel.H1, Chunk(Text("Title")))))
val markdown = Renderer.render(original)
val reparsed = Parser.parse(markdown).toOption.get
assert(original == reparsed)  // Equal after normalization
```

---

## HtmlRenderer

Renders markdown documents to HTML5.

### Full Document Rendering

**Render with DOCTYPE, html, head, body tags:**
```scala
HtmlRenderer.render(doc: Doc): String
```

**Example:**
```scala
val doc = Doc(Chunk(Heading(HeadingLevel.H1, Chunk(Text("Title")))))
val html = HtmlRenderer.render(doc)
// <!DOCTYPE html><html><head></head><body><h1>Title</h1></body></html>
```

### Fragment Rendering

**Render content only (no wrapper tags):**
```scala
HtmlRenderer.renderFragment(doc: Doc): String
```

**Example:**
```scala
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

**Render to terminal string:**
```scala
TerminalRenderer.render(doc: Doc): String
```

**Example:**
```scala
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

```scala
trait ToMarkdown[-A] {
  def toMarkdown(a: A): Inline
}
```

The type parameter is contravariant (`-A`), allowing supertype instances to be used for subtypes.

### Summon an Instance

```scala
ToMarkdown[String]  // Implicitly summons the ToMarkdown[String] instance
```

### Built-In Instances

**Primitive types → plain text:**
```scala
ToMarkdown[String]    // a: String => Text(a)
ToMarkdown[Int]       // a: Int => Text(a.toString)
ToMarkdown[Long]      // a: Long => Text(a.toString)
ToMarkdown[Double]    // a: Double => Text(a.toString)
ToMarkdown[Boolean]   // a: Boolean => Text(a.toString)
```

**Inline pass-through:**
```scala
ToMarkdown[Inline]    // a: Inline => a (identity)
```

**Collections → comma-separated text:**
```scala
ToMarkdown[List[A]]    // as: List[A] => Text(as.map(...).mkString(", "))
ToMarkdown[Chunk[A]]   // as: Chunk[A] => Text(as.map(...).mkString(", "))
ToMarkdown[Vector[A]]  // as: Vector[A] => Text(as.map(...).mkString(", "))
ToMarkdown[Seq[A]]     // as: Seq[A] => Text(as.map(...).mkString(", "))
```

For collection instances, each element is converted using its `ToMarkdown[A]` instance, then joined with ", ".

**Block → rendered markdown text:**
```scala
ToMarkdown[Block]     // b: Block => Text(Renderer.render(Doc(Chunk(b))).trim)
```

### Custom Implementations

Define implicit instances for your types:

```scala
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

```scala
val doc = md"# Valid heading"  // Compiles

val invalid = md"# [unclosed link]("  // Compile error: Invalid markdown
```

### Runtime Interpolation

Values are interpolated and converted to inline markdown using `ToMarkdown`:

```scala
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

```scala
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

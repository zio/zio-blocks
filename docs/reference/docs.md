---
id: docs
title: "Docs (Markdown)"
---

ZIO Blocks Markdown is a **pure, zero-dependency GitHub Flavored Markdown library** providing an immutable ADT for markdown documents, a strict parser with error handling, multiple renderers (GFM markdown, HTML, terminal), and a compile-time validated string interpolator. Core types: `Doc`, `Block`, `Inline`, `Parser`, `Renderer`, `ToMarkdown`.

Here is the core definition of the `Doc` type:

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

Here are the main block and inline types you'll work with:

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

Add the dependency to your `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-docs" % "@VERSION@"
```

For Scala.js, use the cross-platform variant:

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

Here is an example of composing a complete document:

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

Create an empty document:

```scala
val empty = Doc.empty
```

Construct a document from blocks:

```scala
val doc = Doc(Chunk(
  Heading(HeadingLevel.H1, Chunk(Text("Title"))),
  Paragraph(Chunk(Text("Content")))
))
```

Parse markdown to construct a Doc:

```scala
Parser.parse("# Title\n\nContent") match {
  case Right(doc) => // use doc
  case Left(err) => // handle error
}
```

### Core Operations

Merge two documents together using concatenation:

```scala
val doc1 = Doc(Chunk(Heading(HeadingLevel.H1, Chunk(Text("Part 1")))))
val doc2 = Doc(Chunk(Paragraph(Chunk(Text("Part 2")))))
val combined = doc1 ++ doc2
```

Render a document back to GitHub Flavored Markdown:

```scala
val doc = Doc(Chunk(Heading(HeadingLevel.H1, Chunk(Text("Hello")))))
val markdown: String = doc.toString  // Calls Renderer.render internally
```

Render a document as a complete HTML5 document with DOCTYPE:

```scala
val html = doc.toHtml
// Returns: <!DOCTYPE html><html><head></head><body>...</body></html>
```

Render a document as an HTML fragment without wrapper tags:

```scala
val fragment = doc.toHtmlFragment
// Returns: <h1>Hello</h1><p>...</p> (no DOCTYPE or wrapper tags)
```

Render a document to ANSI-colored terminal output:

```scala
val terminal = doc.toTerminal
// Returns ANSI-colored string suitable for terminal display
```

Normalize a document by merging adjacent text nodes and removing empty blocks:

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

Two documents are equal if their **normalized forms** are equal, as shown here:

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

A paragraph containing inline content. Construct a paragraph with inline content:

```scala
Paragraph(content: Chunk[Inline])
```

Here is an example paragraph containing mixed inline elements:

```scala
val para = Paragraph(Chunk(
  Text("Hello "),
  Strong(Chunk(Text("world")))
))
```

### Heading

An ATX-style heading (# to ######) with a level and inline content. Construct a heading with a level and inline content:

```scala
Heading(level: HeadingLevel, content: Chunk[Inline])
```

Create headings at different levels:

```scala
val h1 = Heading(HeadingLevel.H1, Chunk(Text("Title")))
val h3 = Heading(HeadingLevel.H3, Chunk(Text("Subsection")))
```

### CodeBlock

A fenced code block with optional language/info string. Construct a code block with optional language specification:

```scala
CodeBlock(info: Option[String], code: String)
```

Create code blocks with and without language specification:

```scala
// Scala code block
val scalaBlock = CodeBlock(Some("scala"), "val x = 42\nprintln(x)")

// No language specified
val plainBlock = CodeBlock(None, "some code")
```

### ThematicBreak

A thematic break (horizontal rule) represented as `case object ThematicBreak`. Create a thematic break:

```scala
val break = ThematicBreak
```

This renders as `---\n` (or `***` or `___`).

### BlockQuote

A block quote containing nested blocks. Construct a block quote:

```scala
BlockQuote(content: Chunk[Block])
```

Here is an example block quote:

```scala
val quote = BlockQuote(Chunk(
  Paragraph(Chunk(Text("This is a famous quote.")))
))
```

### BulletList

An unordered list with bullet markers (-, *, +). Construct an unordered list:

```scala
BulletList(items: Chunk[ListItem], tight: Boolean)
```

The `tight` parameter controls spacing: `true` removes blank lines between items for compact rendering. Create a bullet list:

```scala
val list = BulletList(Chunk(
  ListItem(Chunk(Paragraph(Chunk(Text("Item 1")))), None),
  ListItem(Chunk(Paragraph(Chunk(Text("Item 2")))), None)
), tight = true)
```

### OrderedList

An ordered list with numeric markers (1., 2., etc.). Construct an ordered list:

```scala
OrderedList(start: Int, items: Chunk[ListItem], tight: Boolean)
```

The `start` parameter specifies the starting number (typically 1). Create an ordered list:

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

A list item, optionally a task list item. Construct a list item:

```scala
ListItem(content: Chunk[Block], checked: Option[Boolean])
```

The `checked` parameter: `Some(true)` renders as `[x]`, `Some(false)` renders as `[ ]`, `None` for regular list items. Create different types of list items:

```scala
// Regular list item
val item = ListItem(Chunk(Paragraph(Chunk(Text("Task")))), None)

// Completed task
val completed = ListItem(Chunk(Paragraph(Chunk(Text("Done")))), Some(true))

// Incomplete task
val incomplete = ListItem(Chunk(Paragraph(Chunk(Text("TODO")))), Some(false))
```

### HtmlBlock

Raw HTML block content. Construct an HTML block:

```scala
HtmlBlock(content: String)
```

Here is an example:

```scala
val html = HtmlBlock("<div class='alert'>Custom HTML</div>")
```

### Table

A GitHub Flavored Markdown table with aligned columns. Construct a table:

```scala
Table(header: TableRow, alignments: Chunk[Alignment], rows: Chunk[TableRow])
```

Here is an example table:

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

Plain text content. Construct plain text:

```scala
Text(value: String)
// or
Inline.Text(value: String)
```

Here is an example:

```scala
val text = Text("Hello world")
```

### Code

Inline code span (backtick-delimited). Construct inline code:

```scala
Code(value: String)
// or
Inline.Code(value: String)
```

Here is an example:

```scala
val code = Code("val x = 42")
```

### Emphasis

Emphasized (italic) text. Construct emphasized text:

```scala
Emphasis(content: Chunk[Inline])
// or
Inline.Emphasis(content: Chunk[Inline])
```

Here is an example:

```scala
val emphasis = Emphasis(Chunk(Text("italic")))
// Renders as: *italic*
```

### Strong

Strong (bold) text. Construct strong text:

```scala
Strong(content: Chunk[Inline])
// or
Inline.Strong(content: Chunk[Inline])
```

Here is an example:

```scala
val strong = Strong(Chunk(Text("bold")))
// Renders as: **bold**
```

### Strikethrough

Strikethrough text (GFM feature). Construct strikethrough text:

```scala
Strikethrough(content: Chunk[Inline])
// or
Inline.Strikethrough(content: Chunk[Inline])
```

Here is an example:

```scala
val struck = Strikethrough(Chunk(Text("deprecated")))
// Renders as: ~~deprecated~~
```

### Link

A hyperlink. Construct a link:

```scala
Link(text: Chunk[Inline], url: String, title: Option[String])
// or
Inline.Link(text: Chunk[Inline], url: String, title: Option[String])
```

The `title` parameter is optional link title text. Create links with and without titles:

```scala
// Simple link
val link = Link(Chunk(Text("Click here")), "https://example.com", None)

// Link with title
val titled = Link(Chunk(Text("Docs")), "/docs", Some("Documentation"))
```

### Image

An image reference. Construct an image:

```scala
Image(alt: String, url: String, title: Option[String])
// or
Inline.Image(alt: String, url: String, title: Option[String])
```

Here are examples:

```scala
val img = Image(alt = "Logo", url = "/logo.png", None)
val imgWithTitle = Image(alt = "Icon", url = "/icon.svg", Some("App Icon"))
```

### HtmlInline

Raw HTML inline content. Construct HTML inline content:

```scala
HtmlInline(content: String)
// or
Inline.HtmlInline(content: String)
```

Here is an example:

```scala
val html = HtmlInline("<span class='highlight'>custom</span>")
```

### SoftBreak

A soft line break (single newline, rendered as space or newline depending on context). Create a soft break:

```scala
SoftBreak
// or
Inline.SoftBreak
```

This renders as `\n` (a single newline in output).

### HardBreak

A hard line break (two spaces or backslash before newline). Create a hard break:

```scala
HardBreak
// or
Inline.HardBreak
```

This renders as `  \n` (two spaces followed by newline).

### Autolink

An autolink (URL or email in angle brackets). Construct an autolink:

```scala
Autolink(url: String, isEmail: Boolean)
// or
Inline.Autolink(url: String, isEmail: Boolean)
```

Here are examples:

```scala
val urlLink = Autolink("https://example.com", isEmail = false)
val emailLink = Autolink("user@example.com", isEmail = true)
// Renders as: <https://example.com> and <user@example.com>
```

---

## HeadingLevel

Heading levels from H1 to H6, with convenience constructors.

### Predefined Levels

Use the predefined heading level constants:

```scala
HeadingLevel.H1  // value = 1
HeadingLevel.H2  // value = 2
HeadingLevel.H3  // value = 3
HeadingLevel.H4  // value = 4
HeadingLevel.H5  // value = 5
HeadingLevel.H6  // value = 6
```

### Safe Construction

Construct a heading level from an integer, which returns `Option`:

```scala
HeadingLevel.fromInt(2) == Some(HeadingLevel.H2)
HeadingLevel.fromInt(7) == None  // Out of range
```

Construct a heading level unsafely (throws on invalid input):

```scala
HeadingLevel.unsafeFromInt(3)  // HeadingLevel.H3
HeadingLevel.unsafeFromInt(7)  // Throws IllegalArgumentException
```

### Accessing Value

Access the numeric value of a heading level:

```scala
HeadingLevel.H1.value == 1
```

---

## Alignment

Table column alignment specification.

### Predefined Alignments

Use the predefined alignment constants:

```scala
Alignment.Left    // Renders as :---
Alignment.Right   // Renders as ---:
Alignment.Center  // Renders as :---:
Alignment.None    // Renders as ---
```

### Use in Tables

Specify alignments when creating a table:

```scala
val alignments = Chunk(Alignment.Left, Alignment.Center, Alignment.Right)
val table = Table(header, alignments, rows)
```

---

## TableRow

A single row in a table, containing cells as inline content. Construct a table row:

```scala
TableRow(cells: Chunk[Chunk[Inline]])
```

Each cell is a `Chunk[Inline]`, allowing formatted content (text, links, emphasis, etc.). Here is an example table row:

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

The main entry point for parsing markdown is:

```scala
Parser.parse(input: String): Either[ParseError, Doc]
```

Returns `Right(doc)` on success or `Left(error)` on parse failure. Parse markdown and handle the result:

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

Error information from parsing, with precise location data. Construct a parse error:

```scala
ParseError(
  message: String,    // Human-readable error description
  line: Int,          // 1-based line number
  column: Int,        // 1-based column number
  input: String       // The line that caused the error
)
```

Here is an example:

```scala
ParseError("Unexpected token", line = 5, column = 12, input = "[invalid markdown]")
```

Get the string representation of a parse error:

```scala
err.toString
// ParseError at line 5, column 12: Unexpected token
//   [invalid markdown]
```

---

## Renderer

Renders markdown documents back to GitHub Flavored Markdown string format.

### Rendering

Render an entire document to GFM markdown:

```scala
Renderer.render(doc: Doc): String
```

Here is an example:

```scala
val doc = Doc(Chunk(
  Heading(HeadingLevel.H1, Chunk(Text("Title"))),
  Paragraph(Chunk(Text("Content")))
))
val markdown = Renderer.render(doc)
```

Render individual blocks and inlines:

```scala
Renderer.renderBlock(block: Block): String
Renderer.renderInlines(inlines: Chunk[Inline]): String
Renderer.renderInline(inline: Inline): String
```

### Normalization During Rendering

The renderer does not normalize; use `doc.normalize` before rendering if normalization is needed.

### Round-Trip Semantics

Verify that rendered output can be re-parsed:

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

Render a complete HTML5 document with DOCTYPE, html, head, body tags:

```scala
HtmlRenderer.render(doc: Doc): String
```

Here is an example:

```scala
val doc = Doc(Chunk(Heading(HeadingLevel.H1, Chunk(Text("Title")))))
val html = HtmlRenderer.render(doc)
// <!DOCTYPE html><html><head></head><body><h1>Title</h1></body></html>
```

### Fragment Rendering

Render only the content without wrapper tags:

```scala
HtmlRenderer.renderFragment(doc: Doc): String
```

Here is an example:

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

Render a document as ANSI-colored terminal output:
```scala
TerminalRenderer.render(doc: Doc): String
```

Here is an example:
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

The `ToMarkdown` type class is defined as:

```scala
trait ToMarkdown[-A] {
  def toMarkdown(a: A): Inline
}
```

The type parameter is contravariant (`-A`), allowing supertype instances to be used for subtypes.

### Summon an Instance
Summon a ToMarkdown instance using implicit resolution:


```scala
ToMarkdown[String]  // Implicitly summons the ToMarkdown[String] instance
```

### Built-In Instances

See the built-in instances for primitive types:
```scala
ToMarkdown[String]    // a: String => Text(a)
ToMarkdown[Int]       // a: Int => Text(a.toString)
ToMarkdown[Long]      // a: Long => Text(a.toString)
ToMarkdown[Double]    // a: Double => Text(a.toString)
ToMarkdown[Boolean]   // a: Boolean => Text(a.toString)
```

Inline values pass through unchanged:
```scala
ToMarkdown[Inline]    // a: Inline => a (identity)
```

Collections render as comma-separated text:
```scala
ToMarkdown[List[A]]    // as: List[A] => Text(as.map(...).mkString(", "))
ToMarkdown[Chunk[A]]   // as: Chunk[A] => Text(as.map(...).mkString(", "))
ToMarkdown[Vector[A]]  // as: Vector[A] => Text(as.map(...).mkString(", "))
ToMarkdown[Seq[A]]     // as: Seq[A] => Text(as.map(...).mkString(", "))
```

For collection instances, each element is converted using its `ToMarkdown[A]` instance, then joined with ", ".

Blocks render as their markdown representation:
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

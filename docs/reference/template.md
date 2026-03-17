---
id: template
title: "Template"
---

`zio-blocks-template` is a type-safe HTML templating library with compile-time optimizations. Build HTML, CSS, and JavaScript using a fluent Scala DSL with zero runtime overhead on Scala 3.

## Why Template?

String concatenation for HTML is error-prone and lacks type safety:

```scala
// Unsafe: XSS vulnerability if userInput contains "<script>"
val html = "<div>" + userInput + "</div>"

// Manual escaping required everywhere
val escaped = userInput.replace("<", "&lt;").replace(">", "&gt;")
```

Template libraries with runtime overhead compromise performance:

```scala
// Runtime parsing and validation at every render
val html = Template.parse("<div>{{content}}</div>")
```

Template provides type-safe HTML construction with automatic XSS protection and Scala 3 compile-time optimizations that eliminate runtime overhead.

## Installation

Add the following to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-template" % "<version>"
```

For cross-platform projects (Scala.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-template" % "<version>"
```

Supported Scala versions: 2.13.x and 3.x

## Overview

Template provides four core features:

1. **HTML DSL** for type-safe element construction: `div(id := "main", p("Hello"))`
2. **String interpolators** with position-aware escaping: `html"<p>$name</p>"`, `css"color: $color"`, `js"console.log($msg)"`
3. **CSS Selector DSL** with combinator methods: `div.hover`, `div > span`, `(div | p).firstChild`
4. **CSS ADT** for structured stylesheets: `Css.Rule(selector, declarations)`

All features are cross-platform (JVM and Scala.js) and work identically on Scala 2.13 and Scala 3, with Scala 3 receiving additional compile-time optimizations.

## The HTML DSL

The HTML DSL provides functions for every HTML5 element. Elements are constructed by calling the element name with modifiers (attributes, children, or other elements).

### Basic Element Construction

```scala
import zio.blocks.template._

// Simple elements
val paragraph = p("Hello, world!")
val heading = h1("Welcome")

// Nested elements
val page = div(
  h1("My Page"),
  p("This is a paragraph."),
  p("Another paragraph.")
)

// Render to string
println(page.render)
// <div><h1>My Page</h1><p>This is a paragraph.</p><p>Another paragraph.</p></div>
```

### Attributes

Attributes are set using the `:=` operator:

```scala
import zio.blocks.template._

val link = a(
  href := "https://example.com",
  target := "_blank",
  "Visit Example"
)

val styledDiv = div(
  id := "container",
  className := "main-content",
  p("Content here")
)

println(link.render)
// <a href="https://example.com" target="_blank">Visit Example</a>
```

### Children

Children can be strings, elements, or collections:

```scala
import zio.blocks.template._

// String children
val simple = p("Plain text")

// Element children
val nested = div(p("First"), p("Second"))

// Lists of children
val items = List("Apple", "Banana", "Cherry")
val listEl = ul(items.map(item => li(item)))

// Option children (None renders nothing)
val maybeHeader: Option[String] = Some("Welcome")
val conditional = div(maybeHeader.map(text => h1(text)))

println(listEl.render)
// <ul><li>Apple</li><li>Banana</li><li>Cherry</li></ul>
```

### Void Elements

Void elements (self-closing tags) automatically render with the correct syntax:

```scala
import zio.blocks.template._

val voidElements = div(
  br(),
  hr(),
  img(src := "photo.jpg", alt := "A photo"),
  input(`type` := "text", name := "username")
)

println(voidElements.render)
// <div><br/><hr/><img src="photo.jpg" alt="A photo"/><input type="text" name="username"/></div>
```

### Conditional Rendering with `when` and `whenSome`

Use `when` to apply modifiers conditionally:

```scala
import zio.blocks.template._

val isHighlighted = true
val box = div(
  className := "box"
).when(isHighlighted)(
  className := "highlighted",
  title := "This is highlighted"
)

println(box.render)
// <div class="highlighted" title="This is highlighted"></div>
```

Use `whenSome` to apply modifiers based on an `Option`:

```scala
import zio.blocks.template._

val maybeTitle: Option[String] = Some("Important")
val card = div(
  className := "card",
  p("Content")
).whenSome(maybeTitle)(title => Seq(
  title := title,
  className := "has-title"
))

println(card.render)
// <div class="has-title" title="Important"><p>Content</p></div>
```

### Special Elements: `script` and `style`

The `script` and `style` elements have specialized APIs:

```scala
import zio.blocks.template._

// Inline JavaScript
val inlineScript = script().inlineJs("console.log('Hello');")

// External JavaScript
val externalScript = script().externalJs("/app.js")

// Inline CSS
val inlineStyle = style().inlineCss("body { margin: 0; }")

// With Css ADT
val styleWithAdt = style().inlineCss(
  Css.Rule(CssSelector.element("body"), Chunk(
    Css.Declaration("margin", "0"),
    Css.Declaration("padding", "0")
  ))
)

println(inlineScript.render)
// <script>console.log('Hello');</script>

println(externalScript.render)
// <script src="/app.js"></script>
```

:::caution
`script` and `style` elements render their text children **without HTML escaping** (by design—JavaScript and CSS contain `<`, `>` naturally). Never interpolate untrusted user input into script or style content. Use the `js""` interpolator (which applies JS-specific escaping) or the `css""` interpolator instead.
:::

## String Interpolators

Template provides four interpolators: `html""`, `css""`, `js""`, and `selector""`. All interpolators are type-safe and leverage typeclasses to convert interpolated values.

### `html""` Interpolator

The `html""` interpolator is position-aware: it summons `ToAttrValue[A]` for attribute values and `ToElements[A]` for element content.

```scala
import zio.blocks.template._

val name = "Alice"
val age = 30

// Attribute value position -> ToAttrValue
val withAttrs = html"""<div id="user-$age" class="profile">User: $name</div>"""

// Element content position -> ToElements
val content = html"""<p>Hello, $name!</p>"""

println(withAttrs.render)
// <div id="user-30" class="profile">User: Alice</div>
```

The interpolator automatically escapes values to prevent XSS:

```scala
import zio.blocks.template._

val userInput = "<script>alert('XSS')</script>"
val safe = html"""<p>$userInput</p>"""

println(safe.render)
// <p>&lt;script&gt;alert(&#x27;XSS&#x27;)&lt;/script&gt;</p>
```

### `css""` Interpolator

The `css""` interpolator returns a `Css` value. Variables are converted via the `ToCss[A]` typeclass:

```scala
import zio.blocks.template._

val color = "blue"
val size = 16

val styles = css"color: $color; font-size: ${size}px;"

println(styles.render)
// color: blue; font-size: 16px;
```

On Scala 3, CSS strings with no variables are folded to compile-time constants (zero runtime cost).

### `js""` Interpolator

The `js""` interpolator returns a `Js` value. Variables are converted via the `ToJs[A]` typeclass and automatically escaped. The `ToJs[String]` instance automatically adds quotes around strings, so you don't need to manually quote them:

```scala
import zio.blocks.template._

val message = "Hello, world!"
val count = 42

val code = js"console.log($message); alert($count);"

println(code.value)
// console.log('Hello, world!'); alert(42);
```

The interpolator protects against `</script>` injection:

```scala
import zio.blocks.template._

val dangerous = "</script><script>alert('XSS')</script>"
val safe = js"console.log($dangerous);"

// The </script> is escaped as \u003c/script\u003e
// Output: console.log("\u003c/script\u003e<script>alert('XSS')</script>")
```

### `selector""` Interpolator

The `selector""` interpolator returns a `CssSelector`:

```scala
import zio.blocks.template._

val className = "active"
val selector = selector".$className"

println(selector.render)
// .active
```

## CSS Selectors

The `CssSelector` DSL provides a fluent API for building CSS selectors with combinator methods.

### Basic Selectors

```scala
import zio.blocks.template._

// Element selector
val divSel = CssSelector.element("div")

// Class selector
val classSel = CssSelector.`class`("container")

// ID selector
val idSel = CssSelector.id("header")

// Universal selector
val universal = CssSelector.universal

println(divSel.render)  // div
println(classSel.render)  // .container
println(idSel.render)  // #header
```

### Combinators

Selectors support fluent combinator methods via the `CssSelectable` trait:

```scala
import zio.blocks.template._

val div = CssSelector.element("div")
val span = CssSelector.element("span")
val button = CssSelector.element("button")

// Descendant: div span
val descendant = div descendant span

// Child: div > span
val child = div > span

// Adjacent sibling: div + span
val adjacent = div + span

// General sibling: div ~ span
val sibling = div ~ span

// And (chaining): div.active
val and = div & CssSelector.Class("active")

// Or (grouping): div, span
val or = div | span

println(descendant.render)  // div span
println(child.render)  // div > span
println(and.render)  // div.active
println(or.render)  // div, span
```

### Pseudo-Classes and Pseudo-Elements

```scala
import zio.blocks.template._

val link = CssSelector.element("a")

// Pseudo-class
val hover = link.hover
val firstChild = link.firstChild
val nthChild = link.nthChild(2)

// Pseudo-element
val before = link.before
val after = link.after

println(hover.render)  // a:hover
println(before.render)  // a::before
```

### Attribute Selectors

```scala
import zio.blocks.template._

val input = CssSelector.element("input")

// Has attribute
val hasType = input.withAttribute("type")

// Exact match
val exactType = input.withAttribute("type", "text")

// Contains
val containsClass = input.withAttributeContaining("class", "btn")

// Starts with
val startsWithHref = input.withAttributeStarting("href", "https")

// Ends with
val endsWithPng = input.withAttributeEnding("src", ".png")

println(hasType.render)  // input[type]
println(exactType.render)  // input[type="text"]
println(containsClass.render)  // input[class*="btn"]
```

### Using Selectors with HTML Elements

All HTML elements created with the DSL implement `CssSelectable`, so you can use them directly in selector expressions:

```scala
import zio.blocks.template._

// Element reference
val myDiv = div(className := "container")

// Use in selector expressions
val hoverSel = myDiv.hover
val childSel = myDiv > span

println(hoverSel.render)  // div:hover
println(childSel.render)  // div > span
```

## CSS ADT

The `Css` ADT represents structured CSS with three main types: `Rule`, `Declaration`, and `Sheet`.

### Declarations and Rules

```scala
import zio.blocks.template._
import zio.blocks.chunk.Chunk

// A CSS declaration (property-value pair)
val marginDecl = Css.Declaration("margin", "10px")
val colorDecl = Css.Declaration("color", "blue")

// A CSS rule (selector + declarations)
val rule = Css.Rule(
  CssSelector.element("p"),
  Chunk(marginDecl, colorDecl)
)

println(rule.render)
// p{margin:10px;color:blue;}

println(rule.render(indent = 2))
// p {
//   margin: 10px;
//   color: blue;
// }
```

### Stylesheets

```scala
import zio.blocks.template._
import zio.blocks.chunk.Chunk

val bodyRule = Css.Rule(
  CssSelector.element("body"),
  Chunk(
    Css.Declaration("margin", "0"),
    Css.Declaration("font-family", "sans-serif")
  )
)

val headerRule = Css.Rule(
  CssSelector.element("h1"),
  Chunk(
    Css.Declaration("font-size", "2em"),
    Css.Declaration("color", "#333")
  )
)

val stylesheet = Css.Sheet(Chunk(bodyRule, headerRule))

println(stylesheet.render(indent = 2))
// body {
//   margin: 0;
//   font-family: sans-serif;
// }
// h1 {
//   font-size: 2em;
//   color: #333;
// }
```

### Raw CSS

For cases where you need raw CSS strings:

```scala
import zio.blocks.template._

val raw = Css.Raw("""
  |body {
  |  margin: 0;
  |}
""".stripMargin)

println(raw.render)
// (prints the raw string unchanged)
```

## Rendering

All `Dom` and `Css` values have three rendering methods:

### `render`

Returns a compact string with no extra whitespace:

```scala
import zio.blocks.template._

val page = div(h1("Title"), p("Content"))
println(page.render)
// <div><h1>Title</h1><p>Content</p></div>
```

### `render(indent: Int)`

Returns a pretty-printed string with the specified indentation:

```scala
import zio.blocks.template._

val page = div(h1("Title"), p("Content"))
println(page.render(indent = 2))
// <div>
//   <h1>Title</h1>
//   <p>Content</p>
// </div>
```

### `renderMinified`

Same as `render` (exists for API consistency):

```scala
import zio.blocks.template._

val page = div(h1("Title"), p("Content"))
println(page.renderMinified)
// <div><h1>Title</h1><p>Content</p></div>
```

## Performance and Optimizations

Template is designed for zero-overhead HTML generation with Scala 3 compile-time optimizations.

### Compile-Time Constant Folding (Scala 3)

On Scala 3, interpolators with no variables are folded to compile-time constants:

```scala
// What you write:
val styles = css"margin: 10px; padding: 5px"
val code = js"console.log('hello')"
val sel = selector".container"

// What Scala 3 generates (via macro):
val styles = Css.Raw("margin: 10px; padding: 5px")
val code = Js("console.log('hello')")
val sel = CssSelector.Raw(".container")
```

No `StringBuilder`, no string concatenation, no runtime processing at all. The values are literal constants embedded in the bytecode.

### Macro-Powered Element Construction (Scala 3)

On Scala 3, the HTML DSL uses compile-time modifier analysis to eliminate intermediate allocations:

```scala
// What you write:
val page = div(id := "main", className := "container", p("Hello"))

// What Scala 3 generates (via macro):
Dom.Element.Generic(
  "div",
  Chunk(
    Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("main")),
    Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue("container"))
  ),
  Chunk(
    Dom.Element.Generic("p", Chunk.empty, Chunk(Dom.Text("Hello")))
  )
)
```

Instead of creating N intermediate element copies (the Scala 2 runtime approach), the macro classifies modifiers at compile time and generates a single direct construction call.

On Scala 2, element construction uses runtime iteration over modifiers. This is still fast, but not zero-allocation like Scala 3.

### Position-Aware HTML Interpolation (Scala 3)

The `html""` interpolator analyzes the template structure at compile time to summon the correct typeclass for each interpolation position:

```scala
// Attribute value position -> ToAttrValue (HTML-escaped)
html"""<div id="$userId">"""

// Element content position -> ToElements (XSS-safe)
html"""<p>$content</p>"""
```

The macro inspects the preceding text (`id="`) to determine whether the interpolation is an attribute value or element content, then summons `ToAttrValue` or `ToElements` accordingly. This ensures correct escaping with zero runtime checks.

### Runtime Characteristics

Even on Scala 2, Template is highly optimized:

- **Zero intermediate allocations** for direct element construction paths
- **StringBuilder pre-allocation** with estimated sizes to minimize resizing
- **While-loop rendering** to avoid iterator overhead
- **Chunk-based collections** for cache-friendly, zero-boxing storage

## Security: XSS Protection

Template provides automatic XSS protection through multiple layers:

### Automatic HTML Escaping in Text Nodes

All `Dom.Text` nodes are HTML-escaped when rendered:

```scala
import zio.blocks.template._

val userInput = "<script>alert('XSS')</script>"
val safe = div(p(userInput))

println(safe.render)
// <div><p>&lt;script&gt;alert(&#x27;XSS&#x27;)&lt;/script&gt;</p></div>
```

The five HTML-sensitive characters are escaped:
- `&` becomes `&amp;`
- `<` becomes `&lt;`
- `>` becomes `&gt;`
- `"` becomes `&quot;`
- `'` becomes `&#x27;`

### JS String Escaping with `</script>` Protection

The `ToJs` typeclass escapes strings to prevent breaking out of script contexts:

```scala
import zio.blocks.template._

val dangerous = "</script><script>alert('XSS')</script>"
val safe = js"console.log($dangerous);"

// The </script> is escaped as \u003c/script\u003e to prevent closing the script tag
```

Escaped characters:
- `"`, `'`, `\` are backslash-escaped
- `<`, `>`, `&` are unicode-escaped (`\u003c`, `\u003e`, `\u0026`)
- Line terminators (`\n`, `\r`, `\u2028`, `\u2029`) are escaped
- Control characters (< 32) are unicode-escaped

### Position-Aware Escaping in Interpolators

The `html""` interpolator summons the correct typeclass based on position:

```scala
import zio.blocks.template._

val attrValue = "user@example.com"
val content = "<b>Bold</b>"

// attrValue is in attribute position -> ToAttrValue (HTML-escaped)
// content is in element position -> ToElements (HTML-escaped)
val el = html"""<div data-email="$attrValue">$content</div>"""

println(el.render)
// <div data-email="user@example.com">&lt;b&gt;Bold&lt;/b&gt;</div>
```

Both positions are escaped, but the typeclass dispatch ensures the correct escaping strategy for each context.

### Safety by Design: No Raw HTML Escape Hatch



Template intentionally provides no public `Dom.Raw` API for embedding arbitrary HTML. This is by design: there is no safe way to interpolate untrusted HTML, and HTML templating libraries that provide raw escape hatches are a known source of XSS vulnerabilities.



If you need dynamic HTML content:

- Use the DSL to construct it type-safely (`div(content)`)

- Use `html"..."` interpolators with proper context-aware escaping

- Pre-render trusted content at build time, not at runtime

## Cross-Version Behavior: Scala 2 vs. Scala 3

Template provides the same API surface on both Scala 2.13 and Scala 3. The differences are purely in optimization:

| Feature | Scala 2 | Scala 3 |
|---------|---------|---------|
| HTML DSL | Runtime modifier application | Compile-time modifier classification |
| `html""` interpolator | Runtime position detection | Compile-time position analysis |
| `css""` interpolator | Runtime string building | Constant folding for no-variable cases |
| `js""` interpolator | Runtime string building | Constant folding for no-variable cases |
| `selector""` interpolator | Runtime string building | Constant folding for no-variable cases |
| API surface | Identical | Identical |
| Type safety | Full | Full |
| XSS protection | Full | Full |

Scala 2 code is still fast and allocation-efficient. Scala 3 adds additional compile-time optimizations for zero-allocation hot paths.

## Example: A Complete HTML Page

```scala
import zio.blocks.template._
import zio.blocks.chunk.Chunk

val userName = "Alice"
val items = List("Dashboard", "Settings", "Logout")

val page = html(
  head(
    meta(charset := "utf-8"),
    title("My App"),
    link(rel := "stylesheet", href := "/style.css"),
    style().inlineCss(
      Css.Sheet(Chunk(
        Css.Rule(CssSelector.element("body"), Chunk(
          Css.Declaration("margin", "0"),
          Css.Declaration("font-family", "sans-serif")
        )),
        Css.Rule(CssSelector.element("nav") > CssSelector.element("a"), Chunk(
          Css.Declaration("padding", "10px"),
          Css.Declaration("text-decoration", "none")
        ))
      ))
    )
  ),
  body(
    header(
      nav(items.map(item => a(href := "#", item)))
    ),
    main(
      h1(s"Welcome, $userName!"),
      p("This is your dashboard.")
    ),
    footer(
      p("© 2026 My App")
    ),
    script().inlineJs(js"console.log($userName); console.log('Page loaded');")
  )
)

println(page.render(indent = 2))
```

This produces a complete, well-formed HTML5 document with inline styles and scripts, all type-checked at compile time and XSS-safe.

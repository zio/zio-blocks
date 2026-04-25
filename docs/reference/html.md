---
id: html
title: "HTML"
---

`zio-blocks-html` is a type-safe HTML templating library with compile-time optimizations. We can build HTML, CSS, and JavaScript using a fluent Scala DSL that ensures correctness and prevents common vulnerabilities.

## Why `zio-blocks-html`?

String concatenation for HTML is error-prone and lacks type safety. Untrusted user input can lead to XSS vulnerabilities if not manually escaped everywhere:

```scala
import zio.blocks.html._

// Unsafe: XSS vulnerability if userInput contains "<script>"
val html = "<div>" + userInput + "</div>"

// Manual escaping is tedious and easy to forget
val escaped = userInput.replace("<", "&lt;").replace(">", "&gt;")
```

Template libraries with runtime overhead compromise performance by parsing and validating at every render:

```scala
// Runtime parsing and validation at every render
val html = RuntimeTemplate.parse("<div>{{content}}</div>")
```

`zio-blocks-html` provides type-safe HTML construction with automatic XSS protection and Scala 3 compile-time optimizations for string interpolators.

## Installation

Add the following to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-html" % "@VERSION@"
```

For cross-platform projects (Scala.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-html" % "@VERSION@"
```

Supported Scala versions: 2.13.x and 3.x.

:::note
The `html` module depends on `zio-blocks-schema`. This dependency is pulled in transitively. The `ToJs` typeclass auto-derives from `Schema`, so any type with a `Schema` instance can be interpolated into `js"..."` expressions.
:::

## Overview

`zio-blocks-html` provides five core features:

1. **HTML DSL** for type-safe element construction: `div(id := "main", p("Hello"))`
2. **String interpolators** with position-aware escaping: `html"<p>$name</p>"`, `css"color: $color"`, `js"console.log($msg)"`
3. **CSS Selector DSL** with combinator methods: `div.hover`, `div > span`, `(div | p).firstChild`
4. **CSS ADT** for structured stylesheets: `Css.Rule(selector, declarations)`
5. **DOM Querying** for manipulating DOM trees: `page.select(div.hover).texts`

All features are cross-platform (JVM and Scala.js) and work identically on Scala 2.13 and Scala 3, with Scala 3 receiving additional compile-time optimizations.

## The HTML DSL

The HTML DSL provides functions for every HTML5 element. We construct elements by calling the element name with modifiers such as attributes, children, or other elements.

### Basic Element Construction

We can create simple or nested elements using the DSL:

```scala
import zio.blocks.html._

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

:::note
The HTML DSL supports all HTML Living Standard elements and attributes, including newer ones such as `hgroup`, `menu`, `popover`, `inert`, `fetchpriority`, and many others.
:::

### Attributes

We set attributes using the `:=` operator:

```scala
import zio.blocks.html._

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

### Attribute Override vs Accumulation

For multi-value attributes like `class` and `rel`, we can choose between overriding or accumulating values:

- `:=` **overrides** (last wins):
  ```scala
  import zio.blocks.html._
  div(className := "a", className := "b")
  // renders: <div class="b"></div>
  ```

- `+=` **accumulates**:
  ```scala
  import zio.blocks.html._
  div(className += "a", className += "b")
  // renders: <div class="a b"></div>
  ```

- Mixed — override then append:
  ```scala
  import zio.blocks.html._
  div(className := "base", className += "extra")
  // renders: <div class="base extra"></div>
  ```

Accumulation also works with conditional rendering:

```scala
import zio.blocks.html._
div(className += "card").when(isActive)(className += "active")
// renders: <div class="card active"></div>  (when true)
```

### Boolean Attributes

Use `BooleanAttribute.:=` to conditionally include boolean attributes like `disabled`, `required`, or `checked`:

```scala
import zio.blocks.html._

val isDisabled = true
val submitButton = button(disabled := isDisabled, "Submit")
// renders: <button disabled>Submit</button>

val isNotDisabled = false
val activeButton = button(disabled := isNotDisabled, "Submit")
// renders: <button>Submit</button>
```

### Data and ARIA Attributes

Use `dataAttr()` and `aria()` for HTML5 data attributes and ARIA accessibility attributes:

```scala
import zio.blocks.html._

// Data attributes
val userDiv = div(dataAttr("user-id") := "42", dataAttr("action") := "edit")
// <div data-user-id="42" data-action="edit"></div>

// ARIA attributes  
val closeButton = button(aria("label") := "Close", aria("expanded") := "false")
// <button aria-label="Close" aria-expanded="false"></button>

// Generic custom attributes
val alpineDiv = div(attr("x-data") := "{open: false}")  // Alpine.js
val htmxDiv = div(attr("hx-get") := "/api/data")      // HTMX
```

### Children

Children can be strings, elements, or collections. The DSL uses the `ToDom` typeclass to convert values into DOM nodes:

```scala
import zio.blocks.html._

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

### ToDom Typeclass

The `ToDom` typeclass provides a uniform way to convert Scala values into `Dom` nodes. We can provide custom instances to support rendering domain-specific types:

```scala
import zio.blocks.html._

case class User(name: String)
implicit val userToDom: ToDom[User] = new ToDom[User] {
  def toDom(user: User): Dom = p(className := "user", user.name)
}

val userList = div(User("Alice"), User("Bob"))
// <div><p class="user">Alice</p><p class="user">Bob</p></div>
```

### Void Elements

Void elements (self-closing tags) automatically render with the correct syntax:

```scala
import zio.blocks.html._

val voidElements = div(
  br,
  hr,
  img(src := "photo.jpg", alt := "A photo"),
  input(`type` := "text", name := "username")
)

println(voidElements.render)
// <div><br/><hr/><img src="photo.jpg" alt="A photo"/><input type="text" name="username"/></div>
```

### DOCTYPE

Use `doctype` to add an HTML5 doctype declaration to your document:

```scala
import zio.blocks.html._

val page = div(doctype, html(head(title("My App")), body(p("Hello"))))
println(doctype.render)
// <!DOCTYPE html>
```

### Custom Elements

We can create custom HTML elements (web components, etc.) or dynamic tags with `element()`:

```scala
import zio.blocks.html._

val myComponent = element("my-component")(id := "app", "Hello")
println(myComponent.render)
// <my-component id="app">Hello</my-component>

// Dynamic tag names
val level = 2
val heading = element(s"h$level")("Title")
// <h2>Title</h2>
```

### Conditional Rendering with `when` and `whenSome`

Use `Dom.Element#when` to apply modifiers conditionally:

```scala
import zio.blocks.html._

val isHighlighted = true
val box = div(
  className := "box"
).when(isHighlighted)(
  className := "highlighted",
  titleAttr := "This is highlighted"
)

println(box.render)
// <div class="box highlighted" title="This is highlighted"></div>
```

Use `Dom.Element#whenSome` to apply modifiers based on an `Option`. We use a unique variable name to avoid shadowing attributes:

```scala
import zio.blocks.html._

val maybeTitle: Option[String] = Some("Important")
val card = div(
  className := "card",
  p("Content")
).whenSome(maybeTitle)(t => Seq(
  attr("title") := t,
  className += "has-title"
))

println(card.render)
// <div class="card has-title" title="Important"><p>Content</p></div>
```

### Special Elements: `script` and `style`

The `script` and `style` elements have specialized APIs for handling inline code:

```scala
import zio.blocks.html._
import zio.blocks.chunk.Chunk

// Inline JavaScript
val inlineScript = script().inlineJs("console.log('Hello');")

// External JavaScript
val externalScript = script().externalJs("/app.js")

// Inline CSS
val inlineStyle = style().inlineCss("body { margin: 0; }")

// With Css ADT
val styleWithAdt = style().inlineCss(
  Css.Rule(CssSelector.Element("body"), Chunk(
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
`script` and `style` elements render their text children **without HTML escaping**. Never interpolate untrusted user input into script or style content. Use the `js""` interpolator or the `css""` interpolator instead.
:::

## String Interpolators

`zio-blocks-html` provides four interpolators: `html""`, `css""`, `js""`, and `selector""`. All interpolators are type-safe and leverage typeclasses to convert interpolated values.

### `html""` Interpolator

The `html""` interpolator is position-aware: it summons `ToAttrValue[A]` for attribute values and `ToElements[A]` for element content:

```scala
import zio.blocks.html._

val name = "Alice"
val age = 30

// Attribute value position -> ToAttrValue
val withAttrs = html"""<div id="user-$age" class="profile">User: $name</div>"""

// Element content position -> ToElements
val content = html"""<p>Hello, $name!</p>"""

println(withAttrs.render)
// <div id="user-30" class="profile">User: Alice</div>
```

:::caution
The `html""` interpolator requires a **single root element**. Templates with multiple top-level nodes produce an error at runtime.
:::

The interpolator automatically escapes values to prevent XSS:

```scala
import zio.blocks.html._

val userInput = "<script>alert('XSS')</script>"
val safe = html"""<p>$userInput</p>"""

println(safe.render)
// <p>&lt;script&gt;alert(&#x27;XSS&#x27;)&lt;/script&gt;</p>
```

### `css""` Interpolator

The `css""` interpolator returns a `Css` value. Variables are converted via the `ToCss[A]` typeclass:

```scala
import zio.blocks.html._

val color = "blue"
val size = 16

val styles = css"color: $color; font-size: ${size}px;"

println(styles.render)
// color: blue; font-size: 16px;
```

### `js""` Interpolator

The `js""` interpolator returns a `Js` value. Variables are converted via the `ToJs[A]` typeclass and automatically escaped. The `ToJs[String]` instance automatically adds quotes around strings:

```scala
import zio.blocks.html._

val message = "Hello, world!"
val count = 42

val code = js"console.log($message); alert($count);"

println(code.value)
// console.log("Hello, world!"); alert(42);
```

The interpolator protects against `</script>` injection by escaping it as `\u003c/script\u003e`.

### `selector""` Interpolator

The `selector""` interpolator returns a `CssSelector`:

```scala
import zio.blocks.html._

val className = "active"
val selector = selector".$className"

println(selector.render)
// .active
```

## CSS Selectors

The `CssSelector` DSL provides a fluent API for building CSS selectors with combinator methods.

### Basic Selectors

We can create selectors for elements, classes, IDs, or universal matches:

```scala
import zio.blocks.html._

// Element selector
val divSel = CssSelector.Element("div")

// Class selector
val classSel = CssSelector.Class("container")

// ID selector
val idSel = CssSelector.Id("header")

// Universal selector
val universal = CssSelector.Universal

println(divSel.render)  // div
println(classSel.render)  // .container
println(idSel.render)  // #header
```

### Combinators

Selectors support fluent combinator methods via the `CssSelectable` trait:

```scala
import zio.blocks.html._

val divSel = CssSelector.Element("div")
val spanSel = CssSelector.Element("span")

// Descendant: div span
val descendant = divSel descendant spanSel

// Child: div > span
val child = divSel > spanSel

// Adjacent sibling: div + span
val adjacent = divSel + spanSel

// General sibling: div ~ span
val sibling = divSel ~ spanSel

// And (chaining): div.active
val and = divSel & CssSelector.Class("active")

// Or (grouping): div, span
val or = divSel | spanSel

println(descendant.render)  // div span
println(child.render)  // div > span
```

### Element Selector Shortcuts

All HTML elements created with the DSL implement `CssSelectable`, allowing us to use them directly in selector expressions:

```scala
import zio.blocks.html._

// Elements ARE selectors
val hoverSel = div.hover    // div:hover
val childSel = div > span   // div > span

println(hoverSel.render)  // div:hover
println(childSel.render)  // div > span
```

### Pseudo-Classes and Pseudo-Elements

```scala
import zio.blocks.html._

val linkSel = CssSelector.Element("a")

// Pseudo-class
val hover = linkSel.hover
val firstChild = linkSel.firstChild
val nthChild = linkSel.nthChild(2)

// Pseudo-element
val before = linkSel.before
val after = linkSel.after

println(hover.render)  // a:hover
println(before.render)  // a::before
```

### Attribute Selectors

```scala
import zio.blocks.html._

val inputSel = CssSelector.Element("input")

// Has attribute
val hasType = inputSel.withAttribute("type")

// Exact match
val exactType = inputSel.withAttribute("type", "text")

// Contains
val containsClass = inputSel.withAttributeContaining("class", "btn")

// Starts with
val startsWithHref = inputSel.withAttributeStarting("href", "https")

// Ends with
val endsWithPng = inputSel.withAttributeEnding("src", ".png")

println(hasType.render)  // input[type]
println(exactType.render)  // input[type="text"]
```

## DOM Querying with DomSelection

Use `Dom#select(CssSelector)` to query and manipulate DOM trees using CSS selectors. The `DomSelection` wrapper provides fluent chaining for navigation:

```scala
import zio.blocks.html._
import zio.blocks.chunk.Chunk

val page = div(
  header(nav(a(href := "/", "Home"), a(href := "/about", "About"))),
  main(p(className += "intro", "Hello"), p("World")),
  footer(p("© 2026"))
)

// Query by tag
val paragraphs = page.select(CssSelector.Element("p"))
println(paragraphs.length)  // 3

// Query by class
val intros = page.select(CssSelector.Class("intro"))
println(intros.texts)  // Chunk("Hello")

// Chain queries
val navLinks = page.select(CssSelector.Element("nav")).children
println(navLinks.length)  // 2

// Extract attribute values
val hrefs = page.select(CssSelector.Element("a")).attrs("href")
// Chunk("/", "/about")
```

## CSS ADT

The `Css` ADT represents structured CSS with three main types: `Rule`, `Declaration`, and `Sheet`.

### Declarations and Rules

```scala
import zio.blocks.html._
import zio.blocks.chunk.Chunk

// A CSS declaration (property-value pair)
val marginDecl = Css.Declaration("margin", "10px")
val colorDecl = Css.Declaration("color", "blue")

// A CSS rule (selector + declarations)
val rule = Css.Rule(
  CssSelector.Element("p"),
  Chunk(marginDecl, colorDecl)
)

println(rule.render)
// p{margin:10px;color:blue;}
```

### Stylesheets

```scala
import zio.blocks.html._
import zio.blocks.chunk.Chunk

val bodyRule = Css.Rule(
  CssSelector.Element("body"),
  Chunk(
    Css.Declaration("margin", "0"),
    Css.Declaration("font-family", "sans-serif")
  )
)

val stylesheet = Css.Sheet(Chunk(bodyRule))

println(stylesheet.render(indent = 2))
// body {
//   margin: 0;
//   font-family: sans-serif;
// }
```

## Rendering

All `Dom` and `Css` values support multiple rendering modes:

### `render`

Returns a compact string with no extra whitespace:

```scala
import zio.blocks.html._

val page = div(h1("Title"), p("Content"))
println(page.render)
// <div><h1>Title</h1><p>Content</p></div>
```

### `render(indent: Int)`

Returns a pretty-printed string with the specified indentation:

```scala
import zio.blocks.html._

val page = div(h1("Title"), p("Content"))
println(page.render(indent = 2))
// <div>
//   <h1>Title</h1>
//   <p>Content</p>
// </div>
```

## Performance and Optimizations

`zio-blocks-html` is designed for efficient HTML generation with Scala 3 compile-time optimizations for string interpolators.

### Compile-Time Constant Folding (Scala 3)

On Scala 3, interpolators with no variables are folded to compile-time constants, eliminating runtime overhead:

```scala
// What you write:
val styles = css"margin: 10px; padding: 5px"

// What Scala 3 generates (via macro):
val styles = Css.Raw("margin: 10px; padding: 5px")
```

### Position-Aware HTML Interpolation (Scala 3)

The `html""` interpolator analyzes the template structure at compile time to summon the correct typeclass for each interpolation position. This ensures correct escaping with zero runtime checks.

### Runtime Characteristics

Even on Scala 2, `zio-blocks-html` is highly optimized:

- **Zero intermediate allocations** for direct element construction paths.
- **StringBuilder pre-allocation** with estimated sizes to minimize resizing.
- **While-loop rendering** to avoid iterator overhead.
- **Chunk-based collections** for cache-friendly, zero-boxing storage.

## Security: XSS Protection

`zio-blocks-html` provides automatic XSS protection through multiple layers:

### Automatic HTML Escaping in Text Nodes

All `Dom.Text` nodes are HTML-escaped when rendered:

```scala
import zio.blocks.html._

val userInput = "<script>alert('XSS')</script>"
val safe = div(p(userInput))

println(safe.render)
// <div><p>&lt;script&gt;alert(&#x27;XSS&#x27;)&lt;/script&gt;</p></div>
```

### JS String Escaping with `</script>` Protection

The `ToJs` typeclass escapes strings to prevent breaking out of script contexts. Characters like `<`, `>`, and `&` are unicode-escaped, and `</script>` is transformed to prevent closing the script tag prematurely.

### Safety by Design: No Raw HTML Escape Hatch

`zio-blocks-html` intentionally provides no public `Dom.Raw` API for embedding arbitrary HTML. This prevents XSS vulnerabilities by ensuring all dynamic content is either constructed via the DSL or interpolated through context-aware interpolators.

## Example: A Complete HTML Page

```scala
import zio.blocks.html._
import zio.blocks.chunk.Chunk

val userName = "Alice"
val items = List("Dashboard", "Settings", "Logout")

val page = div(
  doctype,
  html(
    head(
      meta(charset := "utf-8"),
      title("My App"),
      link(rel := "stylesheet", href := "/style.css"),
      style().inlineCss(
        Css.Sheet(Chunk(
          Css.Rule(CssSelector.Element("body"), Chunk(
            Css.Declaration("margin", "0"),
            Css.Declaration("font-family", "sans-serif")
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
      script().inlineJs(js"console.log($userName);")
    )
  )
)

println(page.render(indent = 2))
```

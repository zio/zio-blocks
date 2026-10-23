---
id: your-first-html-page
title: "Render Your First Server-Side HTML Page"
description: "Build a book detail page with the ZIO Blocks HTML DSL, escape user input, add CSS and JSON state, and save it as an HTML file."
keywords:
  - "Getting Started"
  - "Server-Side Rendering"
  - "HTML Escaping"
  - "Dom"
  - "Css"
  - "ToJs"
---

Welcome! This tutorial is for Scala developers who want to generate HTML on the server with ZIO Blocks. If you can write a case class, a function and a `List#map`, and you know what an HTML tag looks like, you have everything you need. You don't need any prior experience with templating libraries, web frameworks or ZIO.

## 1. Introduction

By the end of this tutorial, you will understand:

- How a page is built as a tree of `Dom` values and turned into a string with `Dom#render`
- How elements, attributes and Scala collections compose into a full page, and how the compiler checks which children an element accepts
- Why user input is safe to put in a page: text, URLs and embedded JSON are each escaped for the place they land
- How to add a typed style sheet with the `css"..."` interpolator and an embedded JSON state with `ToJs`
- How to query a page with `Dom#select` to check what you built

We learn these concepts by building a detail page for the book *Zionomicon*, one step at a time:

1. Render your first element
2. Compose a page from smaller pieces
3. Show user input safely
4. Style the page with CSS
5. Embed the book as JSON
6. Check the page with selectors

At the end, we save the finished page as `book.html` and open it in a browser. We recommend reading from top to bottom — each section builds on the previous one.

To follow along, add the HTML module to your `build.sbt`:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-html" % "@VERSION@"

// Scala 3.9 and newer (also needs scalacOptions += "-experimental")
libraryDependencies += "dev.zio" %% "zio-blocks-next-html" % "@VERSION@"
```

Scala 3.9 and newer use the `zio-blocks-next-html` artifact name. The HTML module depends on the schema module, so `Schema` is available without another dependency. The code in this tutorial is the same for every Scala version.

Section 7 uses a `Schema`, so you should finish the [Your First Schema](./your-first-schema.md) tutorial first.

## 2. Background: A Page Is a Tree of Values

A web page is a tree. The `<html>` element contains a `<head>` and a `<body>`, the body contains headings and lists, and a list contains items. Many server-side tools build that tree by gluing strings together in a template file. That works until a piece of user input contains a `<` and breaks the page. It also breaks when a typo leaves a tag unclosed, or when a `<div>` ends up inside a `<ul>`.

The ZIO Blocks HTML module takes a different approach. Every tag is a Scala function, and calling it returns an immutable `Dom` value. You build the page by calling those functions. Ordinary Scala code (`List#map`, `if`, helper functions) decides what goes in, and the compiler checks what you build. Only at the very end does `Dom#render` turn the tree into a string, and that is where every piece of text is escaped. No HTML is written by hand, so no tag can be left unclosed and no user input can be mistaken for markup.

## 3. Render Your First Element

A `Dom` is one node of an HTML tree: an element such as `<h1>`, a piece of text, or a doctype. The single import `zio.blocks.html._` brings in a function for every HTML tag. Calling a tag function with a `String` gives an element with that text inside.

Let's create a heading for our book:

```scala mdoc:silent
import zio.blocks.html._

val heading = h1("Zionomicon")
```

The code above has two parts:

- `import zio.blocks.html._` brings in the whole HTML DSL: tag functions such as `h1`, `div` and `ul`, attribute keys such as `id` and `href`, and the `css"..."` interpolator we use later.
- `h1("Zionomicon")` returns a `Dom.Element` for an `<h1>` tag with the text `Zionomicon` as its only child. Nothing is rendered yet; `heading` is a plain value.

To see the HTML, we call `Dom#render` and print the string it returns:

```scala mdoc
println(heading.render)
```

That's our first piece of HTML. Elements nest by passing one element to another. Let's put the heading and a paragraph inside a `<div>`:

```scala mdoc:silent
val summary = div(
  h1("Zionomicon"),
  p("by John A. De Goes, Adam Fraser and Milad Khajavi")
)
```

The code above passes two elements to `div`, which makes them its children in the order we wrote them. Now let's print it twice — once with `Dom#render` and once with `render(indent)`, which puts each child on its own line:

```scala mdoc
println(summary.render)

println(summary.render(indent = 2))
```

The code above prints the same tree in two layouts:

- `summary.render` gives compact HTML with no extra whitespace, which is what we send to a browser.
- `summary.render(indent = 2)` indents each nesting level by two spaces, which is easier to read while we learn. We use it for the rest of this tutorial.

## 4. Compose a Page From Smaller Pieces

A real page needs a `<head>` with metadata, a `<body>` with content, and attributes such as `lang` and `charset`. An attribute is written as `key := value` and passed to the tag function along with its children. Because a page is just a value, we can build it with ordinary Scala functions that take our data and return a `Dom.Element`.

Let's start with the data. We describe a book as a case class and derive a `Schema` for it, exactly as in [Your First Schema](./your-first-schema.md). We don't need the schema until section 7, but deriving it now lets us keep one `Book` type for the whole tutorial:

```scala mdoc:silent
import zio.blocks.schema.Schema

final case class Book(title: String, authors: List[String], year: Int)

object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

val zionomicon = Book("Zionomicon", List("John A. De Goes", "Adam Fraser", "Milad Khajavi"), 2024)

val related = List(
  Book("Programming in Scala", List("Martin Odersky", "Lex Spoon", "Bill Venners"), 2008),
  Book("Functional Programming in Scala", List("Paul Chiusano", "Rúnar Bjarnason"), 2014)
)
```

The code above defines our sample data:

- `Book` holds a title, the list of authors and the year of publication.
- `zionomicon` is the book the page is about.
- `related` is a list of two other Scala books that we show as recommendations.

Now let's write two functions. `bookLine` turns a book into one line of text, and `bookPage` builds the whole page:

```scala mdoc:silent
def bookLine(book: Book): String =
  s"${book.title} by ${book.authors.mkString(", ")} (${book.year})"

def bookPage(book: Book, related: List[Book]): Dom.Element =
  html(lang := "en")(
    head(
      meta(charset := "utf-8"),
      title(book.title)
    ),
    body(
      h1(book.title),
      p(bookLine(book)),
      h2("Related books"),
      ul(related.map(relatedBook => li(bookLine(relatedBook))))
    )
  )
```

The code above builds the page shell:

- `html(lang := "en")(...)` creates the `<html>` element with a `lang` attribute, then adds the head and the body as its children.
- `meta(charset := "utf-8")` is a void element — an element that has attributes but never children, such as `<meta>`, `<img>` and `<br>`. Its tag function accepts attributes only.
- `title(book.title)` sets the text shown on the browser tab.
- `related.map(relatedBook => li(bookLine(relatedBook)))` turns the list of books into a list of `<li>` items with ordinary `List#map`.
- `ul(...)` wraps the items in an unordered list.

Let's render the page for Zionomicon:

```scala mdoc
println(bookPage(zionomicon, related).render(indent = 2))
```

Notice that `<meta charset="utf-8"/>` has no closing tag, and that each related book became one `<li>`. We never wrote a closing tag ourselves, so none can be missing.

The compiler also knows which children each element may have. In HTML, a `<ul>` may only contain `<li>` items, so the `ul` function accepts `li` elements and attributes but nothing else. Let's try to put a paragraph directly inside a list:

```scala mdoc:fail
ul(p("Zionomicon"))
```

The code above does not compile: `p(...)` is not a list item, so the mistake is caught before the page is ever rendered. The error lists what `ul` accepts instead: attributes and `Dom.Element.Li` values, which is the type `li(...)` returns. The same rule applies to tables (`table` takes `tr` rows, `tr` takes `th` and `td` cells) and to drop-down lists (a `<select>` takes `<option>` elements). Void elements are checked the same way, so `meta(p("x"))` does not compile either.

## 5. Show User Input Safely

Pages often show text that users wrote, such as reviews and comments. That text can contain anything, including HTML. If it were pasted into the page as-is, a review containing `<script>` would run its code in every reader's browser. This attack is called cross-site scripting (XSS).

With the HTML module, we don't need to remember to escape anything. A `String` passed as a child is always text, never markup, and `Dom#render` escapes it. Attributes that hold a URL, such as `href` and `src`, get an extra check: a `javascript:`, `vbscript:` or `data:text/html` link is prefixed with `unsafe:`, so the browser no longer treats it as code.

Let's model a review that a user submitted, with a hostile message and a hostile website link:

```scala mdoc:silent
final case class Review(reviewer: String, text: String, website: String)

object Review {
  implicit val schema: Schema[Review] = Schema.derived
}

val review = Review(
  reviewer = "mallory",
  text = "Loved it! <script>alert('stolen cookies')</script>",
  website = "javascript:alert('stolen cookies')"
)

def reviewBlock(review: Review): Dom.Element =
  div(
    className := "review",
    p(review.text),
    a(href := review.website, s"Visit ${review.reviewer}'s website")
  )
```

The code above:

- `Review` holds the reviewer's name, the review text and a link to the reviewer's website, all supplied by a user. Like `Book`, it gets a `Schema` that we use in section 7.
- `review.text` contains a `<script>` tag, and `review.website` is a `javascript:` URL — two common XSS attempts.
- `reviewBlock` puts the text in a paragraph and the link in an `<a>` element, exactly as we would for a friendly review. Attributes and children can be mixed in one call; `className := "review"` sets the `class` attribute, which we style in the next section.

Let's render the block:

```scala mdoc
println(reviewBlock(review).render(indent = 2))
```

Notice what happened to each piece of user input:

- In the paragraph, `<` and `>` became `&lt;` and `&gt;`, and the quotes became `&#x27;`. The browser shows the review text literally instead of running it.
- In `href`, the link now starts with `unsafe:`, so clicking it does nothing harmful.
- The apostrophe in `mallory's` was escaped too — every piece of text goes through the same rule, so there is no special case to forget.

:::note
Escaping happens in `Dom#render`, not when the value is created. The tree keeps the original text, which is why the same `Dom` value can be rendered many times without ever being escaped twice.
:::

## 6. Style the Page With CSS

A page usually comes with a style sheet. The `css"..."` interpolator builds a `Css` value from CSS text. Values interpolated into it are converted with a `ToCss` instance for their type. That way a length or a color is written by a typed value instead of a hand-written string.

Let's write the style sheet for our book page:

```scala mdoc:silent
val pageStyle: Css =
  css"""body { font-family: sans-serif; max-width: ${CssLength(40, "em")}; margin: 0 auto; }
.review { border-left: ${CssLength(4, "px")} solid ${CssColor.Rgb(200, 60, 40)}; padding-left: ${CssLength(1, "em")}; }"""
```

The code above:

- `css"""..."""` turns the text into a `Css` value. The interpolator works like the `s"..."` string interpolator, but each `${...}` must have a `ToCss` instance.
- `CssLength(40, "em")` is a length with a unit. The unit is checked when the value is created, so a typo such as `"emm"` throws an exception instead of producing CSS the browser silently ignores.
- `CssColor.Rgb(200, 60, 40)` is a color written as red, green and blue components.

To put the style sheet into the page, we pass it to the `style` tag function, which creates a `<style>` element:

```scala mdoc
println(style(pageStyle).render)
```

The code above prints the `<style>` element. Notice that each typed value was rendered in CSS syntax — `40em`, `4px` and `rgb(200,60,40)` — and that the second rule targets the `review` class we gave the review block in the previous section.

## 7. Embed the Book as JSON

Pages often carry data for JavaScript running in the browser, written as JSON inside a `<script>` element. The type class `ToJs` turns a Scala value into JavaScript source text. For any type that has a `Schema`, the instance `ToJs.fromSchema` is found automatically and encodes the value as JSON, using the same JSON codec as `toJsonString` in [Your First Schema](./your-first-schema.md).

Let's encode the book and wrap the JSON in a `<script>` element:

```scala mdoc:silent
val bookData = script(`type` := "application/json", id := "book-data", Js(ToJs[Book].toJs(zionomicon)))
```

The code above:

- `ToJs[Book]` asks the compiler for a `ToJs` instance for `Book`. Because `Book.schema` is in scope, it finds `ToJs.fromSchema`.
- `ToJs#toJs` encodes `zionomicon` as a JSON string.
- `Js(...)` marks that string as JavaScript source for the `script` element. The `script` function only accepts attributes and `Js` values, so plain text can't end up inside a `<script>` by accident.
- `` `type` := "application/json" `` tells the browser the script holds data, not code. `type` is a Scala keyword, so it is written in backticks.
- `id := "book-data"` gives the browser's JavaScript a way to find the element.

Let's render it:

```scala mdoc
println(bookData.render)
```

Text inside `<script>` is not HTML, so it can't be escaped with `&lt;`. JSON that contains `</script>` would end the element early and let the rest run as code. That is why `ToJs.fromSchema` writes every `<` and `>` as a JSON unicode escape. Let's encode our hostile review from section 5 the same way:

```scala mdoc
println(ToJs[Review].toJs(review))
```

Notice that `<script>` became `\u003cscript\u003e`. JavaScript reads it back as the original text, but the HTML parser never sees a tag. The same review is now safe in three places: as text, as a link and as JSON, each escaped in the way that place needs.

## 8. Check the Page With Selectors

Because a page is a tree of values, we can query it before we render it. `Dom#select` takes a `CssSelector` and returns a `DomSelection` with every element that matches, much like `document.querySelectorAll` in a browser. This is useful in tests: we can check the structure of a page without comparing long strings.

Let's find all list items on the book page:

```scala mdoc:silent
val page = bookPage(zionomicon, related)

val items = page.select(CssSelector.element("li"))
```

The code above:

- `bookPage(zionomicon, related)` builds the page from section 4 again.
- `CssSelector.element("li")` is a selector that matches every `<li>` element, like the CSS selector `li`.
- `page.select(...)` searches the whole tree below `page` and collects the matches.

Now let's count the matches and print their text:

```scala mdoc
println(items.length)

items.texts.foreach(println)
```

The code above confirms what we expected: `DomSelection#length` says there are two items, one per related book, and `DomSelection#texts` returns the text inside each match. If someone later breaks `bookPage`, a test with these two lines catches it.

## 9. Putting It Together

This example combines all the concepts we've learned. It builds the book page with its style sheet, the hostile review and the embedded JSON. Then it checks the related-book items with a selector and writes the page to `book.html` with a `<!DOCTYPE html>` line in front:

```scala mdoc:embed:html-examples/src/main/scala/yourfirsthtmlpage/CompleteExample.scala
```

A few details are new here:

- `doctype.render` gives `<!DOCTYPE html>`. The doctype is not part of the tree, so we put its string and a line break in front of the rendered page.
- `java.nio.file.Files.write` saves the document. The HTML module only builds strings; saving or sending them is up to you.

Run the example, then open the `book.html` file it creates in your browser. You should see the book title, the authors, the related books, and the review shown as plain text with a harmless link.

## 10. Running the Examples

All examples in this tutorial have corresponding runnable Scala files in the `html-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/html-examples
```

Run them in order to follow the tutorial step by step.

### Render Your First Element

This example renders a heading and a small `<div>`, compact and indented.

<details>
  <summary>html-examples/src/main/scala/yourfirsthtmlpage/Concept1FirstElementExample.scala</summary>

```scala mdoc:embed:html-examples/src/main/scala/yourfirsthtmlpage/Concept1FirstElementExample.scala:show-line-numbers
```

</details>

Run it from the `html-examples` directory:

```bash
sbt "runMain yourfirsthtmlpage.Concept1FirstElementExample"
```

### Compose a Page From Smaller Pieces

This example builds the page shell with a head, a body and a list of related books generated from a `List[Book]`.

<details>
  <summary>html-examples/src/main/scala/yourfirsthtmlpage/Concept2ComposePageExample.scala</summary>

```scala mdoc:embed:html-examples/src/main/scala/yourfirsthtmlpage/Concept2ComposePageExample.scala:show-line-numbers
```

</details>

Run it from the `html-examples` directory:

```bash
sbt "runMain yourfirsthtmlpage.Concept2ComposePageExample"
```

### Show User Input Safely

This example renders a hostile review and shows the escaped text and the `unsafe:` link.

<details>
  <summary>html-examples/src/main/scala/yourfirsthtmlpage/Concept3EscapingExample.scala</summary>

```scala mdoc:embed:html-examples/src/main/scala/yourfirsthtmlpage/Concept3EscapingExample.scala:show-line-numbers
```

</details>

Run it from the `html-examples` directory:

```bash
sbt "runMain yourfirsthtmlpage.Concept3EscapingExample"
```

### Style the Page With CSS

This example builds a style sheet with typed lengths and a color and renders it in a `<style>` element.

<details>
  <summary>html-examples/src/main/scala/yourfirsthtmlpage/Concept4CssExample.scala</summary>

```scala mdoc:embed:html-examples/src/main/scala/yourfirsthtmlpage/Concept4CssExample.scala:show-line-numbers
```

</details>

Run it from the `html-examples` directory:

```bash
sbt "runMain yourfirsthtmlpage.Concept4CssExample"
```

### Embed the Book as JSON

This example embeds the book as JSON in a `<script>` element and shows how `<` and `>` in a review are escaped.

<details>
  <summary>html-examples/src/main/scala/yourfirsthtmlpage/Concept5JsonStateExample.scala</summary>

```scala mdoc:embed:html-examples/src/main/scala/yourfirsthtmlpage/Concept5JsonStateExample.scala:show-line-numbers
```

</details>

Run it from the `html-examples` directory:

```bash
sbt "runMain yourfirsthtmlpage.Concept5JsonStateExample"
```

### Check the Page With Selectors

This example selects the `<li>` items of the book page, counts them and prints their text.

<details>
  <summary>html-examples/src/main/scala/yourfirsthtmlpage/Concept6SelectExample.scala</summary>

```scala mdoc:embed:html-examples/src/main/scala/yourfirsthtmlpage/Concept6SelectExample.scala:show-line-numbers
```

</details>

Run it from the `html-examples` directory:

```bash
sbt "runMain yourfirsthtmlpage.Concept6SelectExample"
```

### The Complete Example

The complete example from "Putting It Together" builds the full page and writes it to `book.html` in the `html-examples` directory:

```bash
sbt "runMain yourfirsthtmlpage.CompleteExample"
```

To compile every example at once without running them:

```bash
sbt compile
```

## 11. What You've Learned

In this tutorial, you learned:

- That a page is a tree of immutable `Dom` values built by calling tag functions, and that `Dom#render` and `render(indent)` turn it into HTML
- How attributes (`key := value`), void elements and Scala collections compose into a full page, and how the compiler rejects children an element can't hold
- That user input is escaped for where it lands: text becomes `&lt;...&gt;`, dangerous URLs get an `unsafe:` prefix, and JSON in a `<script>` gets unicode escapes
- How to write a style sheet with `css"..."` and typed values such as `CssLength` and `CssColor`
- How `ToJs` derives a JSON encoder from a `Schema`, and how `Dom#select` with a `CssSelector` checks a page's structure

You now have a solid foundation in server-side HTML with ZIO Blocks. The next step is to make the page interactive and serve it over HTTP.

## 12. Where to Go Next

- **Want the full HTML API?** Read the [HTML](../reference/html.md) reference page, which covers the `html"..."` template interpolator, the `Css` data type, `Dom#transform`, and every `DomSelection` operation.
- **Ready to make the page interactive?** [Add Interactivity to a Page with HTMX](./add-interactivity-with-htmx.md) builds a live search on top of the page you just made. The [HTMX](../reference/htmx/index.md) reference covers the typed `hx-*` attributes behind it.
- **Want to serve the page over HTTP?** [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md) shows how to hand an http-model `Response` to the server built into the JDK.
- **Prefer reactive signals?** [Datastar](../reference/datastar/index.md) builds on the HTML module with typed `data-*` attributes and server-sent events.
- **Want more from your schemas?** Return to [Your First Schema](./your-first-schema.md) and follow its links to the JSON codec and the other formats one `Schema` can produce.

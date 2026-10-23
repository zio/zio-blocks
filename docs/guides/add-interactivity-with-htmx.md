---
id: add-interactivity-with-htmx
title: "Add Interactivity to a Page with HTMX"
description: "Turn a server-rendered book page into a live search with HTMX: fragments, a debounced search box, category filters and a full-page fallback."
keywords:
  - "HTMX"
  - "Server-Side Rendering"
  - "Live Search"
  - "HTML Fragments"
  - "HxTrigger"
  - "HxRequest"
---

Welcome! This tutorial is for Scala developers who can already render an HTML page on the server and now want parts of it to update without a page reload, and without writing a JavaScript framework. If you have finished [Render Your First Server-Side HTML Page](./your-first-html-page.md) and know what a `Request => Response` function is, you have everything you need. You don't need any prior experience with HTMX.

## 1. Introduction

By the end of this tutorial, you will understand:

- What HTMX is, and why the server answers an HTMX request with a piece of a page instead of a whole page
- How `hxGet`, `hxTarget` and `hxSwap` make an element ask the server for a fragment and put it into the page
- How `hxTrigger` with `delay` and `changed` turns a search box into a debounced live search
- How `hxInclude` sends the value of one control along with another, so several filters work together
- How the server tells an HTMX request from a normal browser request with the `HX-Request` header, and why a history restore needs the full page

We learn these concepts by building a search page for a small shelf of Scala books, one step at a time:

1. Load a fragment with a button
2. Search as you type
3. Filter by category
4. Serve a full page or a fragment
5. Put it all together and run it in a browser

We recommend reading from top to bottom — each section builds on the previous one.

The HTMX module is Scala 3 only. Add it to your `build.sbt`, together with the HTML module:

```scala
// Scala 3.3 to 3.8
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-http-htmx" % "@VERSION@",
  "dev.zio" %% "zio-blocks-html"      % "@VERSION@"
)

// Scala 3.9 and newer
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-next-http-htmx" % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-html"      % "@VERSION@"
)
scalacOptions += "-experimental"
```

The HTMX module builds on the HTTP model, which is compiled for Java 21, so run the programs in this tutorial on **JDK 21 or newer**. The code is the same for every Scala 3 version.

The module writes the attributes for **HTMX 2.x**, and the page in this tutorial loads HTMX 2.0.4 from a CDN. The attribute names and the `HX-*` headers the module supports are the ones HTMX 2 defines.

## 2. Background: HTML Over the Wire

A page that updates itself usually does this: JavaScript asks the server for JSON, then more JavaScript turns the JSON into HTML and puts it into the page. That means two copies of your page logic, one on the server and one in the browser.

[HTMX](https://htmx.org) is a small JavaScript library that skips the middle step. You write attributes such as `hx-get="/search"` on an ordinary element. When the element's event fires, HTMX sends a request, takes the HTML the server sends back, and swaps it into the page. The server keeps doing what it already does well: it renders HTML. It just renders a smaller piece of it, called a **fragment**.

The ZIO Blocks HTMX module does not replace that JavaScript library. It gives the HTML DSL you already know typed versions of the `hx-*` attributes, so that `delay:300ms` is a method call, not a string you can mistype. The page still loads `htmx.min.js` in a `<script>` tag, and HTMX does the work in the browser.

## 3. Load a Fragment With a Button

Let's start with the data and the fragment. A fragment is only the part of the page that changes: here, the list of books. We describe a book with a title, an author and a category, and we use real Scala books:

```scala mdoc:silent
import scala.concurrent.duration._
import zio.blocks.html._
import zio.http._
import zio.http.htmx._
import zio.http.htmx.headers.{HxHistoryRestoreRequest, HxRequest}

final case class Book(title: String, author: String, category: String)

val books = List(
  Book("Zionomicon", "John A. De Goes and Adam Fraser", "Concurrency"),
  Book("Learning Concurrent Programming in Scala", "Aleksandar Prokopec", "Concurrency"),
  Book("Akka in Action", "Raymond Roestenburg, Rob Bakker and Rob Williams", "Concurrency"),
  Book("Functional Programming in Scala", "Paul Chiusano and Rúnar Bjarnason", "Functional"),
  Book("Scala with Cats", "Noel Welsh and Dave Gurnell", "Functional"),
  Book("Programming in Scala", "Martin Odersky, Lex Spoon and Bill Venners", "Language"),
  Book("Scala Cookbook", "Alvin Alexander", "Language")
)

val categories = List("Concurrency", "Functional", "Language")

def bookList(found: List[Book]): Dom =
  if (found.isEmpty) p("No books found.")
  else ul(found.map(book => li(strong(book.title), s" by ${book.author} (${book.category})")))
```

The code above defines:

- `Book` and `books`, the seven books on our shelf, in three categories.
- `bookList`, which turns a list of books into a fragment: a `<ul>` with one `<li>` per book, or a short message when the list is empty. There is no `<html>` or `<body>` in it.

Let's render the fragment for one book:

```scala mdoc
println(bookList(books.take(1)).render)
```

The `<ul>` is all the server will send when HTMX asks for the list.

Now the page needs an element that asks for it. A button with `hxGet` does that:

```scala mdoc:silent
val loadButton = button(
  hxGet    := "/books",
  hxTarget := HxTarget.css("#results"),
  hxSwap   := HxSwap.InnerHTML
)("Show all books")
```

```scala mdoc
println(loadButton.render)
```

The code above gives the button three attributes:

- `hxGet := "/books"` renders `hx-get="/books"`: when the button is clicked, HTMX sends `GET /books`. A button's default event is `click`, so we don't need to name it.
- `hxTarget := HxTarget.css("#results")` renders `hx-target="#results"`: the response goes into the element with the id `results`. Without a target, HTMX puts the response into the button itself.
- `hxSwap := HxSwap.InnerHTML` renders `hx-swap="innerHTML"`: the response replaces what is inside the target. This is HTMX's default, and we write it out once so you can see where the swap strategy lives.

The page around the button holds an empty `results` element and loads HTMX. We put the shell in a function, because every later step reuses it:

```scala mdoc:silent
val htmxScript = "https://cdn.jsdelivr.net/npm/htmx.org@2.0.4/dist/htmx.min.js"

def shell(content: Dom*): Dom.Element =
  html(lang := "en")(
    head(
      meta(charset := "utf-8"),
      title("Scala Bookshelf"),
      script(src := htmxScript)
    ),
    body(h1("Scala Bookshelf"), content)
  )

val page = shell(loadButton, div(id := "results"))
```

`shell` writes the head, with the `<script>` tag that loads HTMX, and the body with a heading and whatever content we pass in. `page` is the shell around our button and the empty `results` div.

Next, the server. We write it as a plain `Request => Response` function, the same shape that [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md) turns into a running server. Two routes are enough: `GET /` returns the whole page, and `GET /books` returns the fragment:

```scala mdoc:silent
def htmlResponse(dom: Dom): Response =
  Response.ok(Body.fromArray(dom.render.getBytes("UTF-8"), ContentType.`text/html`))

def notFound(request: Request): Response =
  Response.text(Status.NotFound, s"No route for ${request.method.name} ${request.path.encode}")

def app1(request: Request): Response =
  (request.method, request.path.segments.toList) match {
    case (Method.GET, Nil)           => htmlResponse(page)
    case (Method.GET, List("books")) => htmlResponse(bookList(books))
    case _                           => notFound(request)
  }
```

Because `app1` is an ordinary function, we can call it without a socket. Let's write a small helper that sends a `GET` and returns the body, and ask for the fragment:

```scala mdoc:silent
def get(app: Request => Response, target: String, headers: Headers = Headers.empty): String =
  app(Request.get(URL.parse(target).toOption.get).addHeaders(headers)).body.asString()
```

```scala mdoc
println(get(app1, "/books"))
```

That is exactly the response HTMX receives when the button is clicked: a `<ul>` and nothing else. HTMX puts it inside `<div id="results">`, and the page now shows the list without reloading.

## 4. Search as You Type

A button is a fine start, but a search page should update while the reader types. A search box needs three things: a name for the value it sends, an event to listen to, and a pause so we don't send a request for every key press.

```scala mdoc:silent
val searchBox = input(
  `type`      := "search",
  name        := "q",
  placeholder := "Search by title or author",
  hxGet       := "/search",
  hxTrigger   := HxTrigger("keyup").delay(300.millis).changed,
  hxTarget    := HxTarget.css("#results")
)
```

```scala mdoc
println(searchBox.render)
```

The code above is new in three places:

- `name := "q"` names the value. HTMX sends the input's own value with its request, so the request is `GET /search?q=...`.
- `hxTrigger := HxTrigger("keyup").delay(300.millis).changed` renders `hx-trigger="keyup delay:300ms changed"`. `HxTrigger("keyup")` fires on every key release. `delay(300.millis)` waits 300 milliseconds and restarts the wait whenever another key arrives, so a reader who types `zio` quickly causes one request, not three. This is called a **debounce**. `changed` skips the request when the value is the same as the last one sent, so pressing an arrow key does nothing.
- `HxTrigger("keyup")` takes the event name as a string. The module has factories for common events such as `HxTrigger.click`, `HxTrigger.change` and `HxTrigger.input`, but not for every browser event.

:::note
`delay` takes a `FiniteDuration` and writes it the way HTMX expects it: whole seconds as `1s`, anything else as milliseconds, such as `300ms`. A negative duration throws `IllegalArgumentException`.
:::

The server needs a filter and a `/search` route. The filter keeps the books whose title or author contains the text:

```scala mdoc:silent
def matching(query: String, category: String): List[Book] = {
  val needle = query.trim.toLowerCase
  books.filter { book =>
    (needle.isEmpty || book.title.toLowerCase.contains(needle) || book.author.toLowerCase.contains(needle)) &&
    (category.isEmpty || book.category == category)
  }
}
```

The `category` parameter is for the next section; for now we always pass an empty string, which keeps every category. The route reads `q` from the query string:

```scala mdoc:silent
def queryValue(request: Request, name: String): String =
  request.queryParams.getFirst(name).fold("")(_.replace('+', ' '))

def app2(request: Request): Response =
  (request.method, request.path.segments.toList) match {
    case (Method.GET, Nil)            => htmlResponse(shell(searchBox, div(id := "results")(bookList(books))))
    case (Method.GET, List("search")) => htmlResponse(bookList(matching(queryValue(request, "q"), "")))
    case _                            => notFound(request)
  }
```

`queryValue` returns the first value of a query parameter, or an empty string when it is missing. It also turns `+` back into a space, and that detail matters:

:::caution
A browser, and HTMX with it, writes a space in a query value as `+`. `QueryParams` in 0.0.56 keeps `+` as a plus sign, so a search for `functional programming` arrives as `functional+programming` and finds nothing. This is a known bug in 0.0.56. Until it is fixed, replace `+` with a space yourself, as `queryValue` does. The cost is that a real plus sign, which arrives as `%2B`, also becomes a space.
:::

Let's search the way HTMX would:

```scala mdoc
println(get(app2, "/search?q=zio"))
println(get(app2, "/search?q=functional+programming"))
println(get(app2, "/search?q=quantum"))
```

The first search finds *Zionomicon*, the second finds the book with both words, and the third shows the message for an empty result.

## 5. Filter by Category

Readers also want to narrow the shelf to one category. We add a menu next to the search box. There is one catch. An element sends only its own value, and on a `GET` request HTMX does not add the other fields of the surrounding form. If the menu sent only `category`, picking a category would forget the search text.

The attribute `hxInclude` fixes that: it names other elements whose values travel with the request. We give each control an `id` and ask it to include the other one:

```scala mdoc:silent
val searchBox3 = input(
  id        := "q",
  `type`    := "search",
  name      := "q",
  hxGet     := "/search",
  hxTrigger := HxTrigger("keyup").delay(300.millis).changed,
  hxTarget  := HxTarget.css("#results"),
  hxInclude := HxTarget.css("#category")
)

val categoryMenu = select(
  id        := "category",
  name      := "category",
  hxGet     := "/search",
  hxTrigger := HxTrigger.change,
  hxTarget  := HxTarget.css("#results"),
  hxInclude := HxTarget.css("#q")
)(
  option(value := "")("All categories"),
  categories.map(c => option(value := c)(c))
)
```

```scala mdoc
println(categoryMenu.render)
```

The code above works like this:

- `hxInclude := HxTarget.css("#category")` renders `hx-include="#category"`. Typing in the search box now sends `q` and `category` together.
- The menu does the reverse with `hx-include="#q"`, so choosing a category also sends the current search text.
- `HxTrigger.change` fires when the menu value changes. A `<select>` already defaults to `change`, and we write it out for clarity.
- The first option has an empty `value`, so choosing "All categories" sends `category=` and the filter keeps every category.

The route now reads both values:

```scala mdoc:silent
def app3(request: Request): Response =
  (request.method, request.path.segments.toList) match {
    case (Method.GET, Nil)            =>
      htmlResponse(shell(searchBox3, categoryMenu, div(id := "results")(bookList(books))))
    case (Method.GET, List("search")) =>
      htmlResponse(bookList(matching(queryValue(request, "q"), queryValue(request, "category"))))
    case _                            => notFound(request)
  }
```

```scala mdoc
println(get(app3, "/search?q=&category=Functional"))
println(get(app3, "/search?q=scala&category=Language"))
println(get(app3, "/search?q=zio&category=Language"))
```

The category alone gives the two functional books, "scala" in the language category gives the two language books, and "zio" in that category gives none.

:::tip
`hxInclude` takes an `HxTarget`, the same type `hxTarget` takes, so `HxTarget.css("#q")` is the selector for one element and `HxTarget.closest("form")` would pick the enclosing form. The [HxTarget](../reference/htmx/hx-target.md) reference lists every form.
:::

## 6. Serve a Full Page or a Fragment

So far `/search` returns a fragment, and that creates a problem. If a reader pastes `/search?q=cats` into the address bar, the browser asks for that URL directly and gets a bare `<ul>` with no `<html>` around it. The same happens when a search engine or a reader without JavaScript follows a link.

The fix is to let the server decide. Every request HTMX sends carries the header `HX-Request: true`. A browser navigating normally does not. The module reads it as a typed header, `HxRequest`:

```scala mdoc:silent
val fromHtmx = Request.get(URL.root).addHeaders(Headers(HxRequest.name -> "true"))
```

```scala mdoc
println(fromHtmx.header(HxRequest))
println(Request.get(URL.root).header(HxRequest))
```

`Request#header(HxRequest)` returns `Some(HxRequest(true))` when HTMX sent the header and `None` when it is absent, so `request.header(HxRequest).exists(_.enabled)` is the check for "this came from HTMX".

There is one more case. HTMX can keep the address bar in step with the page: `hxPushUrl := HxUrlUpdate.Enabled` adds the request URL to the browser history after each swap, so the search is bookmarkable and the back button works. When the reader goes back and HTMX no longer has that page in its cache, it asks the server again. That request carries `HX-Request: true` too, but also `HX-History-Restore-Request: true`, and HTMX expects the **full page**, because it puts the response into the whole `<body>`. A server that answered with a fragment would show a bare list. So a fragment is right only for a request that has `HX-Request` and does not have `HX-History-Restore-Request`:

```scala mdoc:silent
def wantsFragment(request: Request): Boolean =
  request.header(HxRequest).exists(_.enabled) && !request.header(HxHistoryRestoreRequest).exists(_.enabled)
```

Now we can write the search page once, for the full-page case, and let the filter fill in the controls from the query string. The controls get `hxPushUrl`, and the search box shows the current text:

```scala mdoc:silent
def searchBox4(query: String): Dom =
  input(
    id        := "q",
    `type`    := "search",
    name      := "q",
    value     := query,
    hxGet     := "/search",
    hxTrigger := HxTrigger("keyup").delay(300.millis).changed,
    hxTarget  := HxTarget.css("#results"),
    hxInclude := HxTarget.css("#category"),
    hxPushUrl := HxUrlUpdate.Enabled
  )

def categoryMenu4(selectedCategory: String): Dom =
  select(
    id        := "category",
    name      := "category",
    hxGet     := "/search",
    hxTrigger := HxTrigger.change,
    hxTarget  := HxTarget.css("#results"),
    hxInclude := HxTarget.css("#q"),
    hxPushUrl := HxUrlUpdate.Enabled
  )(
    option(value := "")("All categories"),
    categories.map(c => option(value := c)(c).when(c == selectedCategory)(selected))
  )

def searchPage(query: String, category: String, found: List[Book]): Dom =
  shell(searchBox4(query), categoryMenu4(category), div(id := "results")(bookList(found)))
```

The code above changes the controls in two ways:

- `value := query` and `.when(c == selectedCategory)(selected)` fill the controls from the query string. A reader who opens a pushed URL sees the search text and the category they had chosen.
- `hxPushUrl := HxUrlUpdate.Enabled` renders `hx-push-url="true"`: after each swap HTMX pushes the request URL, query string included, onto the browser history.

The route chooses between the two answers, and adds a `Vary` header so that a cache doesn't hand the fragment to a browser that asked for the page:

```scala mdoc:silent
def search(request: Request): Response = {
  val query    = queryValue(request, "q")
  val category = queryValue(request, "category")
  val found    = matching(query, category)
  val body     = if (wantsFragment(request)) bookList(found) else searchPage(query, category, found)
  htmlResponse(body).addHeaders(Headers("vary" -> "HX-Request"))
}

def app4(request: Request): Response =
  (request.method, request.path.segments.toList) match {
    case (Method.GET, Nil)            => htmlResponse(searchPage("", "", books))
    case (Method.GET, List("search")) => search(request)
    case _                            => notFound(request)
  }
```

Let's ask for the same URL three ways. First, a browser that opens the address directly:

```scala mdoc
println(get(app4, "/search?q=cats&category=Functional"))
```

That is a complete page, with `value="cats"` in the search box and `selected` on the Functional option. Next, HTMX asking for the same URL:

```scala mdoc
println(get(app4, "/search?q=cats&category=Functional", Headers(HxRequest.name -> "true")))
```

Only the fragment. Last, HTMX restoring the page after the back button:

```scala mdoc
println(
  get(
    app4,
    "/search?q=cats&category=Functional",
    Headers(HxRequest.name -> "true", HxHistoryRestoreRequest.name -> "true")
  )
)
```

The full page again. One URL, two answers, chosen by the headers.

:::note
Page responses and fragment responses share a URL, so they also share a cache entry unless you say otherwise. `Vary: HX-Request` tells browsers and proxies to store the answers for requests with and without that header separately.
:::

## 7. Putting It Together

This example combines everything we learned: the fragment, the debounced search, the category filter with `hxInclude`, and the full-page fallback with `HxRequest`. It adds one attribute, `hxSync := HxSync(HxTarget.This, HxSyncStrategy.Replace)`, which renders `hx-sync="this:replace"`: when a new search request starts while the previous one is still in flight, HTMX cancels the old one, so a slow answer for `zi` can never overwrite the newer answer for `zio`.

The example serves the page with the JDK server adapter from [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md), so you can open it in a browser:

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/CompleteExample.scala:show-line-numbers
```

## 8. Running the Examples

All examples in this tutorial have corresponding runnable Scala files in the `zio-blocks-htmx-examples` project. It is a standalone sbt build that uses the published ZIO Blocks artifacts. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/zio-blocks-htmx-examples
```

The examples share three files. `Library.scala` holds the books, the fragment and the shell from section 3 and the helpers we wrote along the way. `JdkHttp.scala` and `Client.scala` are the adapter and test client from the HTTP server guide, copied unchanged except for the package name.

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Library.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Library.scala:show-line-numbers
```

</details>

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/JdkHttp.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/JdkHttp.scala:show-line-numbers
```

</details>

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Client.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Client.scala:show-line-numbers
```

</details>

### Load a Fragment With a Button

This example renders the button, the page and the `/books` fragment.

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step1LoadFragmentExample.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step1LoadFragmentExample.scala:show-line-numbers
```

</details>

Run it from the `zio-blocks-htmx-examples` directory:

```bash
sbt "runMain addinteractivitywithhtmx.Step1LoadFragmentExample"
```

### Search as You Type

This example renders the debounced search box and calls `/search` three times.

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step2LiveSearchExample.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step2LiveSearchExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain addinteractivitywithhtmx.Step2LiveSearchExample"
```

### Filter by Category

This example adds the category menu and the `hxInclude` attributes.

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step3CategoryFilterExample.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step3CategoryFilterExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain addinteractivitywithhtmx.Step3CategoryFilterExample"
```

### Serve a Full Page or a Fragment

This example asks for one URL as a browser, as HTMX and as an HTMX history restore.

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step4FullPageOrFragmentExample.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step4FullPageOrFragmentExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain addinteractivitywithhtmx.Step4FullPageOrFragmentExample"
```

### The Complete Example

The complete example starts the server and waits. Run it, then open the printed address in a browser, type in the search box and watch the address bar:

```bash
sbt "runMain addinteractivitywithhtmx.CompleteExample"
```

It listens on port 8080. Pass another port after the class name to use it, as in `sbt "runMain addinteractivitywithhtmx.CompleteExample 9000"`. Press Ctrl-C to stop it. The page loads `htmx.min.js` from a CDN, so the browser needs internet access.

To compile all the examples without running them:

```bash
sbt compile
```

## 9. What You've Learned

In this tutorial, you learned:

- That HTMX turns attributes on ordinary elements into requests, and that the server answers with an HTML fragment that HTMX swaps into the page
- How `hxGet`, `hxTarget` and `hxSwap` describe where to ask, where the answer goes and how it replaces what is there
- How `HxTrigger("keyup").delay(300.millis).changed` makes a debounced live search that skips repeated values
- How `hxInclude` sends the value of one control with another, so a search box and a category menu filter together
- How the server reads `HxRequest` to tell HTMX from a browser, why `HX-History-Restore-Request` needs the full page, and why a `Vary: HX-Request` header belongs on a URL with two answers

You now have a solid foundation in building interactive server-rendered pages with the HTMX module. The next step is to see how the module's other attributes and headers shape more complex interactions.

## 10. Where to Go Next

- **Want every attribute type?** Read the [HTMX reference](../reference/htmx/index.md), then [HxTrigger](../reference/htmx/hx-trigger.md), [HxSwap](../reference/htmx/hx-swap.md), [HxTarget](../reference/htmx/hx-target.md), [HxParams](../reference/htmx/hx-params.md) and [HxSync](../reference/htmx/hx-sync.md) for the full list of modifiers.
- **Want the response headers?** [Request and Response Headers](../reference/htmx/response-headers.md) covers `HxRetarget`, `HxReswap`, `HxTriggerHeader` and the other headers a handler can set to steer the page after a response.
- **Want to serve the page?** [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md) explains the adapter that `CompleteExample` uses.
- **Want to check input?** [How to Validate Query Parameters and Headers and Answer 400](./validate-query-parameters-and-headers.md) shows how to read `q` and `category` as typed values and answer bad input with a clear error.
- **Need the HTML basics again?** Return to [Render Your First Server-Side HTML Page](./your-first-html-page.md).

A guide on writing HTMX fragments and response headers in more depth is planned.

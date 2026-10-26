---
id: add-interactivity-with-htmx
title: "Add Interactivity to a Page with HTMX"
description: "Turn a server-rendered book page into a live search with typed hx-* attributes: debounced input, a category filter and HTML fragments."
keywords:
  - "Server-Side Rendering"
  - "Live Search"
  - "HTML Fragments"
  - "HxTrigger"
  - "HxTarget"
  - "HxRequest"
---

Welcome! This tutorial is for Scala developers who can already build an HTML page with the ZIO Blocks HTML module and now want parts of that page to update without a reload. You don't need any JavaScript, and you don't need prior experience with htmx. If you haven't built a page with the HTML module yet, start with [Render Your First Server-Side HTML Page](./your-first-html-page.md); this tutorial picks up where it ends.

## 1. Introduction

By the end of this tutorial, you will understand:

- How htmx turns an ordinary HTML element into one that asks the server for an HTML fragment and swaps it into the page
- How the typed `hx-*` attributes (`hxGet`, `hxTarget`, `hxTrigger`, `hxInclude`, `hxPushUrl`) attach to elements built with the HTML DSL
- How to debounce a search box with `HxTrigger` so the server is asked only when the reader pauses and the text has changed
- How one URL answers with a full page or a fragment, depending on the typed `HxRequest` header

We learn these concepts by turning a static list of Scala books into a live search page, one step at a time:

1. Serve a page that loads htmx
2. Make your first htmx request
3. Search as the reader types
4. Filter by category
5. Answer one URL with a full page or a fragment

At the end, we run the finished page and try it in a browser. We recommend reading from top to bottom — each section builds on the previous one.

The HTMX module is for Scala 3 only. To follow along, add it to your `build.sbt`:

```scala
// Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-http-htmx" % "@VERSION@"

// Scala 3.9 and newer (also needs scalacOptions += "-experimental")
libraryDependencies += "dev.zio" %% "zio-blocks-next-http-htmx" % "@VERSION@"
```

The HTMX module depends on the HTML module and the http-model module, so `Dom`, `Request` and `Response` come with it. The http-model module is compiled for Java 21, so the programs in this tutorial need JDK 21 or newer to run.

## 2. Background: The Server Sends HTML, htmx Swaps It

In a classic server-rendered site, every click loads a whole new page. A JavaScript single-page app avoids that, but then the browser has to fetch JSON, turn it into HTML itself and keep a second copy of the page's logic in JavaScript.

htmx takes a third path. It is a small JavaScript library that reads extra attributes on ordinary HTML elements. An attribute such as `hx-get="/search"` tells htmx to send a `GET /search` request when something happens to that element, and `hx-target="#results"` tells it where to put the answer. The server answers with a piece of HTML (a fragment), and htmx swaps it into the page. All of the HTML is still built on the server, by the same Scala code that builds the full page:

```text
  Browser                                       Server
  ───────                                       ──────
  <input hx-get="/search" hx-target="#results">
        │  the reader types "zio"
        │
        ├──── GET /search?q=zio ───────────────►  findBooks("zio")
        │     HX-Request: true                     │
        │                                          ▼
        ◄──── <ul><li>Zionomicon …</li></ul> ────  resultList(...).render
        │
        ▼
  htmx replaces the content of <div id="results">
```

The ZIO Blocks HTMX module gives each `hx-*` attribute a typed key, such as `hxGet` and `hxTarget`, for the HTML DSL. Each key accepts only values that make sense for it, so a typo in a trigger or a target is a compile error instead of a page that silently does nothing. The module writes the attributes; the htmx library in the browser reads them. The attributes follow htmx 2, so this tutorial loads htmx 2.0.11. htmx 4 doesn't read several attributes the module offers, such as `hx-params` and `hx-disinherit`.

## 3. Serve a Page That Loads htmx

Let's start with the data: a `Book` with a title, an author and a category, and eight real Scala books. `findBooks` keeps the books whose title or author contains the search text and whose category matches, and `resultList` turns the matches into a `<ul>`, or into a short message when nothing matches:

```scala mdoc:silent
import zio.blocks.html._
import zio.http._

final case class Book(title: String, author: String, category: String)

val books: List[Book] = List(
  Book("Zionomicon", "John A. De Goes, Adam Fraser and Milad Khajavi", "Effects"),
  Book("Essential Effects", "Adam Rosien", "Effects"),
  Book("Practical FP in Scala", "Gabriel Volpe", "Effects"),
  Book("Functional Programming in Scala", "Paul Chiusano and Rúnar Bjarnason", "Functional Programming"),
  Book("Scala with Cats", "Noel Welsh and Dave Gurnell", "Functional Programming"),
  Book("Programming in Scala", "Martin Odersky, Lex Spoon and Bill Venners", "Language"),
  Book("Scala for the Impatient", "Cay S. Horstmann", "Language"),
  Book("Hands-on Scala Programming", "Li Haoyi", "Language")
)

val categories: List[String] = List("Effects", "Functional Programming", "Language")

def findBooks(query: String, category: String): List[Book] = {
  val needle = query.trim.toLowerCase
  books.filter { book =>
    val matchesText =
      needle.isEmpty || book.title.toLowerCase.contains(needle) || book.author.toLowerCase.contains(needle)
    val matchesCategory = category.isEmpty || book.category == category
    matchesText && matchesCategory
  }
}

def resultList(found: List[Book]): Dom.Element =
  if (found.isEmpty) p("No books match your search.")
  else ul(found.map(book => li(strong(book.title), s" by ${book.author}")))
```

The code above sets up everything the later sections reuse:

- `import zio.blocks.html._` brings in the HTML DSL, and `import zio.http._` brings in the http-model types `Request`, `Response`, `Body` and friends.
- `findBooks` ignores case and treats an empty query or an empty category as "match everything". Its name isn't `search` because the HTML DSL already has a `search` value for the `<search>` tag.
- `resultList` is the piece of HTML that changes as the reader searches. We render it inside the full page now, and later we send it on its own as a fragment.

Now let's build the page. It looks like the page from the HTML tutorial, with one addition: a `<script>` element in the head that loads htmx from a CDN:

```scala mdoc:silent
val htmxScript: Dom.Element =
  script(src := "https://cdn.jsdelivr.net/npm/htmx.org@2.0.11/dist/htmx.min.js")

def staticPage: Dom.Element =
  html(lang := "en")(
    head(
      meta(charset := "utf-8"),
      title("Scala Bookshelf"),
      htmxScript
    ),
    body(
      h1("Scala Bookshelf"),
      div(id := "results", resultList(books))
    )
  )
```

The code above builds a normal page whose head loads htmx. `htmxScript` loads htmx 2.0.11 from the jsDelivr CDN, and pinning an exact version means the page keeps working the same way when a new htmx release comes out. `div(id := "results", ...)` wraps the result list in an element with an id. Nothing uses the id yet, but it is the spot htmx updates from the next section on.

Let's render the page to check it:

```scala mdoc
println(staticPage.render(indent = 2))
```

The page is still static: loading htmx changes nothing until an element carries an `hx-*` attribute.

To serve the page, we write the application as a plain function from `Request` to `Response`. The guide [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md) explains this shape and provides a small adapter, `JdkHttp`, that runs such a function on the HTTP server built into the JDK. The companion examples use that adapter unchanged:

```scala mdoc:silent
def htmlResponse(html: String): Response =
  Response.ok(Body.fromArray(html.getBytes("UTF-8"), ContentType.`text/html`))

def notFound(request: Request): Response =
  Response.text(Status.NotFound, s"No route for ${request.method.name} ${request.path.encode}")

def staticApp(request: Request): Response =
  (request.method, request.path.segments.toList) match {
    case (Method.GET, Nil) => htmlResponse(doctype.render + staticPage.render)
    case _                 => notFound(request)
  }
```

The code above:

- `htmlResponse` wraps rendered HTML in a `200 OK` response with `Content-Type: text/html`. `Body.fromString` would label the body `text/plain`, so we build it from bytes with an explicit content type.
- `notFound` answers any other request with a `404` and a short message naming the method and path.
- `staticApp` matches on the method and the path segments. `GET /` returns the page with a `<!DOCTYPE html>` line in front; every other request goes to `notFound`.

Because the application is just a function, we can call it without starting a server. This small helper builds a `GET` request for a target, passes it to an application and prints the status and the body. We use it in every section to see exactly what htmx would receive:

```scala mdoc:silent
def call(app: Request => Response, target: String, headers: Header*): Unit = {
  val url      = URL.parse(target).fold(error => throw new IllegalArgumentException(error), identity)
  val request  = headers.foldLeft(Request.get(url))((request, header) => request.addHeader(header))
  val response = app(request)
  println(s"GET $target -> ${response.status.code}")
  println(response.body.asString())
}
```

The code above parses the target into a `URL`, builds the request with `Request.get` and adds any typed headers with `Request#addHeader`. The body is printed with `Body#asString`.

## 4. Make Your First htmx Request

Let's make the page fetch something. A button gets two attributes: `hxGet` names the URL htmx requests when the button is clicked, and `hxTarget` names the element that receives the answer. The server needs a new route, `GET /books`, that answers with the result list only, not a whole page:

```scala mdoc:silent
import zio.http.htmx._

val showAllButton: Dom.Element =
  button(hxGet := "/books", hxTarget := HxTarget.css("#results"))("Show all books")

def buttonPage: Dom.Element =
  html(lang := "en")(
    head(meta(charset := "utf-8"), title("Scala Bookshelf"), htmxScript),
    body(
      h1("Scala Bookshelf"),
      showAllButton,
      div(id := "results", p("Click the button to load the books."))
    )
  )

def buttonApp(request: Request): Response =
  (request.method, request.path.segments.toList) match {
    case (Method.GET, Nil)           => htmlResponse(doctype.render + buttonPage.render)
    case (Method.GET, List("books")) => htmlResponse(resultList(books).render)
    case _                           => notFound(request)
  }
```

The code above:

- `import zio.http.htmx._` brings in every typed attribute key, such as `hxGet` and `hxTarget`, and the value types they accept, such as `HxTarget`.
- `hxGet := "/books"` becomes `hx-get="/books"`. A button triggers its request on a click, which is htmx's default for buttons.
- `hxTarget := HxTarget.css("#results")` becomes `hx-target="#results"`. `hxTarget` only accepts an `HxTarget`, so a plain string such as `"#results"` doesn't compile. `HxTarget` also has `closest`, `find`, `next` and `previous` for targets relative to the element.
- The `/books` route renders `resultList(books)` without `<html>`, `<head>` or `<body>`. That is the fragment.

Let's look at the button the page now contains:

```scala mdoc
println(showAllButton.render)
```

When the reader clicks it, htmx sends `GET /books` and puts the answer inside `<div id="results">`, replacing the "Click the button" paragraph. Let's send that same request ourselves:

```scala mdoc
call(buttonApp, "/books")
```

This is the whole idea of htmx: the server answers with plain HTML, and the attributes decide when to ask and where the answer goes. By default htmx replaces the target's content (`innerHTML`); the `hxSwap` attribute takes an `HxSwap` value to choose another strategy, such as replacing the target itself with `HxSwap.OuterHTML`.

## 5. Search as the Reader Types

A button is a good start, but readers expect results to update as they type. A text input can carry the same `hxGet` and `hxTarget` attributes, and htmx sends the input's value along with the request, using the input's `name` as the query parameter. What we still need to choose is when the request fires. That is the job of `hxTrigger`.

If htmx sent a request on every key press, typing "scala" would send five requests, one per letter. A common fix is debouncing: wait until the reader pauses, then send one request. `HxTrigger` builds the trigger from the event name plus modifiers:

```scala mdoc:silent
import scala.concurrent.duration._

val debounced: HxTrigger = HxTrigger("keyup").changed.delay(300.millis)
```

The code above builds the trigger value in three parts:

- `HxTrigger("keyup")` fires on the `keyup` event, after each key press. `HxTrigger` has ready-made values for common events, such as `HxTrigger.click`, `HxTrigger.change` and `HxTrigger.input`, and takes any other event name as a string.
- `HxTrigger#changed` skips the request when the value hasn't changed since the last one, so arrow keys and Shift don't trigger a search.
- `HxTrigger#delay` with `300.millis` waits 300 milliseconds after the last `keyup` before sending. Each new key press restarts the wait, so a reader who types "scala" without pausing causes one request, not five.

To see the `hx-trigger` value this produces, we print `HxTrigger#render`:

```scala mdoc
println(debounced.render)
```

Notice that the modifiers render in the order we call them, and that the duration is written as `300ms`. Let's put the trigger on a search box and add a `/search` route that reads the `q` parameter:

```scala mdoc:silent
val liveSearchBox: Dom.Element.Void =
  input(
    `type`      := "search",
    name        := "q",
    placeholder := "Search by title or author",
    hxGet       := "/search",
    hxTrigger   := debounced,
    hxTarget    := HxTarget.css("#results")
  )

def liveSearchApp(request: Request): Response =
  (request.method, request.path.segments.toList) match {
    case (Method.GET, List("search")) =>
      val query = request.queryParams.getFirst("q").getOrElse("")
      htmlResponse(resultList(findBooks(query, "")).render)
    case _ => notFound(request)
  }
```

The code above:

- `name := "q"` is what htmx uses as the parameter name, so typing "zio" sends `GET /search?q=zio`.
- `input(...)` returns a `Dom.Element.Void`: `<input>` is a void element, which takes attributes but never children.
- `request.queryParams.getFirst("q")` reads the first value of the `q` parameter, or `None` when the request has none. An empty search shows every book.

Let's check the rendered search box and send two searches:

```scala mdoc
println(liveSearchBox.render)

call(liveSearchApp, "/search?q=zio")
call(liveSearchApp, "/search?q=haskell")
```

The search for "zio" finds Zionomicon, and the search for "haskell" returns the "no match" paragraph. That paragraph is a fragment too, so the reader sees it right where the list was.

:::tip
htmx encodes parameter values with JavaScript's `encodeURIComponent`, so a space arrives as `%20` and `QueryParams` decodes it correctly. A plain HTML form submitted with `GET` encodes a space as `+` instead, which `QueryParams` keeps as a plus sign. If your page also has such a form, read [Values with Spaces and Plus Signs](./validate-query-parameters-and-headers.md#values-with-spaces-and-plus-signs).
:::

## 6. Filter by Category

Let's add a drop-down list of categories next to the search box. A `<select>` can carry `hxGet` and `hxTarget` too, and htmx sends its request on the browser's change event by default, so it needs no `hxTrigger`. There is one catch: each element sends only its own value. A search from the text box would forget the chosen category, and a change of category would forget the search text.

The `hxInclude` attribute fixes that. It names an element whose form fields htmx adds to the request. We put both controls in a `<div id="filters">` and point `hxInclude` at it from both, so every request carries `q` and `category`:

```scala mdoc:silent
val filterSearchBox: Dom.Element.Void =
  input(
    `type`      := "search",
    name        := "q",
    placeholder := "Search by title or author",
    hxGet       := "/search",
    hxTrigger   := debounced,
    hxTarget    := HxTarget.css("#results"),
    hxInclude   := HxTarget.css("#filters")
  )

val categoryFilter: Dom.Element =
  select(
    name      := "category",
    hxGet     := "/search",
    hxTarget  := HxTarget.css("#results"),
    hxInclude := HxTarget.css("#filters")
  )(
    option(value := "")("All categories") :: categories.map(category => option(value := category)(category))
  )

val filters: Dom.Element = div(id := "filters", filterSearchBox, categoryFilter)

def filterApp(request: Request): Response =
  (request.method, request.path.segments.toList) match {
    case (Method.GET, List("search")) =>
      val query    = request.queryParams.getFirst("q").getOrElse("")
      val category = request.queryParams.getFirst("category").getOrElse("")
      htmlResponse(resultList(findBooks(query, category)).render)
    case _ => notFound(request)
  }
```

The code above:

- `hxInclude := HxTarget.css("#filters")` becomes `hx-include="#filters"` on both controls. htmx collects every named field inside that `<div>`, so the search box sends the category and the drop-down sends the search text.
- `option(value := "")("All categories")` is the first choice. Its empty value means "every category", which is exactly what `findBooks` does with an empty category.
- `categories.map(...)` adds one `<option>` per category, and the `::` puts "All categories" in front.
- `filterApp` reads both parameters and passes them to `findBooks`.

Let's check the rendered controls:

```scala mdoc
println(filters.render(indent = 2))
```

Notice that the controls sit in a `<div>`, not a `<form>`. Pressing Enter inside a form submits it the old-fashioned way and reloads the page, which we don't want here. Now let's send the two requests the browser would send, first after choosing "Effects" with an empty search box, then after typing "scala" with "Language" selected:

```scala mdoc
call(filterApp, "/search?q=&category=Effects")
call(filterApp, "/search?q=scala&category=Language")
```

The first request lists the three books about effects. The second finds three of the six books with "scala" in their title, the ones in the "Language" category.

## 7. One URL, a Full Page or a Fragment

The page works, but the address bar never changes. A reader can't bookmark a search or share it, and the browser's Back button leaves the page instead of going back to the previous search. The `hxPushUrl` attribute fixes that: after each request, htmx pushes the request's URL, such as `/search?q=zio&category=Effects`, into the address bar and the browser history.

That raises a new question. When the reader reloads the page or opens a bookmark, the browser itself requests `/search?q=zio&category=Effects`, and the `/search` route answers with a bare `<ul>`. The browser would show an unstyled list with no heading and no search box. The route must answer with a full page when the browser asks and with a fragment when htmx asks.

The server can tell them apart: htmx adds the header `HX-Request: true` to every request it sends. The HTMX module reads it as a typed header, `HxRequest`, from the `zio.http.htmx.headers` package:

```scala mdoc:silent
import zio.http.htmx.headers._

def wantsFragment(request: Request): Boolean =
  request.header(HxRequest).exists(_.enabled) &&
    !request.header(HxHistoryRestoreRequest).exists(_.enabled)
```

The code above reads the header in two steps. `request.header(HxRequest)` parses the `HX-Request` header into an `HxRequest` value, or returns `None` when the header is missing, and `exists(_.enabled)` is `true` only for `HX-Request: true`. `HxHistoryRestoreRequest` handles one special case: when the reader goes back to a search that htmx no longer has in its history cache, htmx 2 requests that URL with `HX-Request: true` and `HX-History-Restore-Request: true`, and it puts the answer in place of the whole `<body>`. Answering with a fragment there would replace the heading and the search box with a bare list, so we answer with the full page.

Now the page itself takes the current search, so a full page loaded from `/search?q=zio&category=Effects` shows "zio" in the box, "Effects" selected and the matching books:

```scala mdoc:silent
def searchBox(query: String): Dom.Element.Void =
  input(
    `type`      := "search",
    name        := "q",
    value       := query,
    placeholder := "Search by title or author",
    hxGet       := "/search",
    hxTrigger   := debounced,
    hxTarget    := HxTarget.css("#results"),
    hxInclude   := HxTarget.css("#filters"),
    hxPushUrl   := HxUrlUpdate(true)
  )

def categorySelect(current: String): Dom.Element =
  select(
    name      := "category",
    hxGet     := "/search",
    hxTarget  := HxTarget.css("#results"),
    hxInclude := HxTarget.css("#filters"),
    hxPushUrl := HxUrlUpdate(true)
  )(
    option(value := "")("All categories") ::
      categories.map(category => option(value := category)(category).when(category == current)(selected))
  )

def searchPage(query: String, category: String): Dom.Element =
  html(lang := "en")(
    head(meta(charset := "utf-8"), title("Scala Bookshelf"), htmxScript),
    body(
      h1("Scala Bookshelf"),
      div(id := "filters", searchBox(query), categorySelect(category)),
      div(id := "results", resultList(findBooks(query, category)))
    )
  )

def searchApp(request: Request): Response =
  (request.method, request.path.segments.toList) match {
    case (Method.GET, Nil) | (Method.GET, List("search")) =>
      val query    = request.queryParams.getFirst("q").getOrElse("")
      val category = request.queryParams.getFirst("category").getOrElse("")
      val html =
        if (wantsFragment(request)) resultList(findBooks(query, category)).render
        else doctype.render + searchPage(query, category).render
      htmlResponse(html).addHeader("vary", "hx-request")
    case _ => notFound(request)
  }
```

The code above:

- `hxPushUrl := HxUrlUpdate(true)` becomes `hx-push-url="true"` on both controls, so every search lands in the address bar.
- `value := query` fills the search box. The HTML module escapes attribute values, so a search for `"><script>` stays text inside `value="..."`, just like the user input in the HTML tutorial.
- `option(...).when(category == current)(selected)` adds the `selected` attribute only to the current category. `Dom.Element#when` adds modifiers to an element when a condition holds.
- `searchApp` answers `/` and `/search` with the same code. The only difference between the two answers is what `wantsFragment` says, so the full page and the fragment always show the same results.
- `addHeader("vary", "hx-request")` tells browsers and caches that the answer depends on the `HX-Request` header. Without it, a browser could show a cached fragment when the reader reloads the page.

Let's build the three kinds of requests the route can receive for the same URL: a reload by the browser, a search sent by htmx and a history restore sent by htmx:

```scala mdoc:silent
val target = URL.parse("/search?q=zio&category=Effects").toOption.get

val reload         = Request.get(target)
val htmxSearch     = Request.get(target).addHeader(HxRequest(true))
val historyRestore = Request.get(target).addHeader(HxRequest(true)).addHeader(HxHistoryRestoreRequest(true))
```

Now let's ask `wantsFragment` about each of them:

```scala mdoc
println(wantsFragment(reload))
println(wantsFragment(htmxSearch))
println(wantsFragment(historyRestore))
```

Only the htmx search gets the fragment. Let's send it, and then the same URL as a reload:

```scala mdoc
call(searchApp, "/search?q=zio&category=Effects", HxRequest(true))
call(searchApp, "/search?q=zio&category=Effects")
```

The same URL gave two answers. The htmx request got the one-item list, and the reload got the whole page with `value="zio"` in the search box and `selected` on the "Effects" option. That is the pattern for every htmx page: build the page from the request's parameters, and let one header decide how much of it to send.

## 8. Putting It Together

This example combines all the concepts we've learned into one file. It serves the search page with the JDK HTTP adapter, and it keeps running until we stop it, so we can try it in a browser:

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/CompleteExample.scala
```

The pieces are the ones from the previous sections: `findBooks` and `resultList` from section 3, the debounced search box from section 5, the category filter with `hxInclude` from section 6, and `hxPushUrl` with the `wantsFragment` check from section 7. The `main` method starts the server on port 8080, or on the port given as the first argument, and stops it on Ctrl+C.

Run it and open `http://localhost:8080/` in a browser. Type "scala" in the search box: the list updates once you pause, and the address bar changes to `/search?q=scala&category=`. Pick "Language": the list narrows to three books. Reload the page: the search box still says "scala" and "Language" is still selected. Press the Back button: the previous search comes back. Open the browser's developer tools on the Network tab to watch htmx send one request per pause, each with the `HX-Request: true` header.

## 9. Running the Examples

All examples in this tutorial have corresponding runnable Scala files in the `zio-blocks-htmx-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/zio-blocks-htmx-examples
```

Each step starts the server on port 8080, prints what the server answers to the requests from that section, and keeps running so you can open `http://localhost:8080/` in a browser. Press Ctrl+C to stop it before running the next step. To use another port, pass it as an argument, for example `sbt "runMain addinteractivitywithhtmx.Step1StaticPageExample 9090"`. The steps share the book list and helpers in `Bookshelf.scala`, and the server adapter in `JdkHttp.scala` and the client in `Client.scala` are copied from the guide [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md).

### Share the Book List and Helpers

Every step below imports the book model, the search function and the response helpers from this file. It has no `main`, so there is nothing to run; read it first.

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Bookshelf.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Bookshelf.scala:show-line-numbers
```

</details>

### Serve a Page That Loads htmx

This example serves the static book list with the htmx script in its head.

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step1StaticPageExample.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step1StaticPageExample.scala:show-line-numbers
```

</details>

Run it from the `zio-blocks-htmx-examples` directory:

```bash
sbt "runMain addinteractivitywithhtmx.Step1StaticPageExample"
```

### Make Your First htmx Request

This example adds a button that loads the book list from `GET /books` into `#results`.

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step2FirstHtmxRequestExample.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step2FirstHtmxRequestExample.scala:show-line-numbers
```

</details>

Run it from the `zio-blocks-htmx-examples` directory:

```bash
sbt "runMain addinteractivitywithhtmx.Step2FirstHtmxRequestExample"
```

### Search as the Reader Types

This example replaces the button with a debounced search box backed by `GET /search?q=...`.

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step3LiveSearchExample.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step3LiveSearchExample.scala:show-line-numbers
```

</details>

Run it from the `zio-blocks-htmx-examples` directory:

```bash
sbt "runMain addinteractivitywithhtmx.Step3LiveSearchExample"
```

### Filter by Category

This example adds the category drop-down and uses `hxInclude` so both controls send both values.

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step4CategoryFilterExample.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step4CategoryFilterExample.scala:show-line-numbers
```

</details>

Run it from the `zio-blocks-htmx-examples` directory:

```bash
sbt "runMain addinteractivitywithhtmx.Step4CategoryFilterExample"
```

### One URL, a Full Page or a Fragment

This example pushes each search into the address bar and answers `/search` with a full page or a fragment, depending on the `HX-Request` header.

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step5FullPageOrFragmentExample.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step5FullPageOrFragmentExample.scala:show-line-numbers
```

</details>

Run it from the `zio-blocks-htmx-examples` directory:

```bash
sbt "runMain addinteractivitywithhtmx.Step5FullPageOrFragmentExample"
```

### The Complete Example

The complete example from "Putting It Together" is a single file with everything in it:

```bash
sbt "runMain addinteractivitywithhtmx.CompleteExample"
```

To compile every example at once without running them:

```bash
sbt compile
```

## 10. What You've Learned

In this tutorial, you learned:

- That htmx reads `hx-*` attributes on ordinary HTML elements, sends a request when something happens to the element, and swaps the server's HTML fragment into the page
- How the typed keys `hxGet`, `hxTarget`, `hxTrigger`, `hxInclude` and `hxPushUrl` attach to elements built with the HTML DSL, and how values such as `HxTarget` and `HxUrlUpdate` keep them correct at compile time
- How `HxTrigger("keyup").changed.delay(300.millis)` debounces a search box so the server is asked only when the reader pauses and the text has changed
- How `hxInclude` makes several controls send their values together
- How one route answers with a full page or a fragment by reading the typed `HxRequest` header, and why a history restore needs the full page

You now have a solid foundation in building interactive pages with htmx and ZIO Blocks. The next step is to explore the other `hx-*` attributes and the response headers that let the server steer the browser.

## 11. Where to Go Next

- **Want every attribute and value type?** Read the [HTMX](../reference/htmx/index.md) reference page, then the pages for [`HxTrigger`](../reference/htmx/hx-trigger.md), [`HxTarget`](../reference/htmx/hx-target.md) and [`HxSwap`](../reference/htmx/hx-swap.md).
- **Want the server to steer the browser?** [Request and Response Headers](../reference/htmx/response-headers.md) covers the typed request headers and the response headers that redirect, retarget or refresh the page.
- **Need stricter query parameters?** [How to Validate Query Parameters and Headers and Answer 400](./validate-query-parameters-and-headers.md) decodes typed parameters and reports every mistake at once.
- **Want more on serving?** [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md) explains the adapter the examples use and its limits.
- **Prefer reactive signals?** [Datastar](../reference/datastar/index.md) builds on the HTML module with typed `data-*` attributes and server-sent events.

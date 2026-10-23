---
id: add-interactivity-with-htmx
title: "Add Interactivity to a Page with HTMX"
description: "Turn a server-rendered book search page into a live search with a debounced request, a category filter and fragment responses, using the typed HTMX DSL."
keywords:
  - "HTMX"
  - "Server-Side Rendering"
  - "Hypermedia"
  - "HxTrigger"
  - "HxTarget"
---

Welcome! This tutorial is for Scala developers who have built a server-rendered page with ZIO Blocks, for example by following [Render Your First Server-Side HTML Page](./your-first-html-page.md). You know what a function from a request to a response looks like, and you know what an HTML tag is. You don't need any prior experience with HTMX, JavaScript or ZIO.

## 1. Introduction

By the end of this tutorial, you will understand:

- How HTMX lets any element ask the server for a piece of HTML and put it into the page, without a JavaScript framework
- How `hx-get`, `hx-target` and `hx-trigger` decide which URL is requested, where the answer goes and when the request fires
- How the `delay` and `changed` trigger modifiers turn a search box into a live search that doesn't flood the server
- How `hx-include` sends the value of a second control along with a request
- How a server answers an HTMX request with a fragment and a browser with a full page

We learn these concepts by building a search page for a small catalog of Scala books, one step at a time:

1. The static page
2. Fetch a fragment with `hx-get`
3. Debounce a live search
4. Filter by category
5. Full page or fragment
6. Putting it together, and running the examples

We recommend reading from top to bottom. Each section builds on the previous one.

:::note
This tutorial uses the attribute syntax of HTMX 2.x and loads `htmx.org` version 2.0.11, the latest release on npm at the time of writing. HTMX 4.0 is published under npm's `next` tag and parses attributes differently, so the examples here don't apply to it as written.
:::

## 2. Background: HTML That Asks the Server

A normal link or form makes the browser load an entirely new page. HTMX changes one part of that. Any element on the page can make a request in the background, and the answer replaces one part of the current page. The server doesn't send JSON for the browser to turn into HTML. It sends HTML, and the browser places it where it belongs.

Each HTMX attribute answers one question about that exchange:

- **When** does the request fire? This is the trigger, for example a click or a key press.
- **What** is requested? This is the URL, given by an attribute such as `hx-get`.
- **Where** does the answer go? This is the target, a part of the page chosen with a CSS selector.
- **How** is the answer put in? This is the swap, for example replacing the contents of the target.

On the server, nothing new is needed. A handler is still a function from a request to a response. The only difference is that for some requests it returns a small fragment of HTML instead of a whole document.

## 3. Set Up the Project

HTMX support is published for Scala 3 only. Scala 3.9 and newer use the `zio-blocks-next-http-htmx` artifact and also need `-experimental`:

```scala
// Scala 3.9 and newer (also needs scalacOptions += "-experimental")
libraryDependencies += "dev.zio" %% "zio-blocks-next-http-htmx" % "@VERSION@"
```

The HTMX module depends on the HTML module and the HTTP model, so their types come with it.

The page itself loads the htmx JavaScript library. We pin an exact version, so the page behaves the same tomorrow as it does today:

```scala mdoc:silent
val htmxScript: String = "https://unpkg.com/htmx.org@2.0.11/dist/htmx.min.js"
```

## 4. The Static Page

We start with a page that looks right but does nothing. It has a search box and a results area, and nothing tells the browser to talk to the server. Here is the markup, rendered with the HTML DSL from [the previous tutorial](./your-first-html-page.md):

```scala mdoc:silent
import zio.blocks.html._

val staticSearch = div(
  input(`type` := "search", name := "q", placeholder := "Search books..."),
  div(id := "results", p("Results appear here."))
)
```

```scala mdoc
println(staticSearch.render(indent = 2))
```

The code above builds the page in three steps:

- The `input` call creates a search box with a `type` of `search`. The `name` attribute matters later, because it is the key under which the text is sent to the server.
- `div(id := "results", ...)` creates the area where the results will go. The `id` gives it a name that HTMX can point at.
- `println(staticSearch.render(indent = 2))` prints the markup. No HTMX attribute appears yet, so the browser does nothing when the reader types.

## 5. Fetch a Fragment with hx-get

The first interactive element is a button. When the reader clicks it, the browser asks the server for `/books`, and the HTML that comes back replaces the contents of `#results`. The attributes are typed values from the `zio.http.htmx` package:

```scala mdoc:silent
import zio.blocks.html._
import zio.http.htmx._

val showAll = button(
  hxGet    := "/books",
  hxTarget := HxTarget.css("#results"),
  "Show all books"
)
```

```scala mdoc
println(showAll.render)
```

The code above adds two HTMX attributes to a plain `button`:

- `hxGet := "/books"` tells HTMX to send a `GET` request to `/books` when the button is clicked. A `String` is accepted here, and so are typed `Path` and `URL` values.
- `hxTarget := HxTarget.css("#results")` names the element that receives the answer. `HxTarget.css` takes a CSS selector, and the rendered attribute is `hx-target="#results"`.

The server's answer is a fragment, a piece of HTML with no `<html>` or `<body>` around it. Here is what the `/books` handler returns:

```scala mdoc:silent
import zio.blocks.html._

val fragment = ul(
  li("Zionomicon by John A. De Goes, Adam Fraser, Milad Khajavi"),
  li("Essential Scala by Noel Welsh, Dave Gurnell")
)
```

```scala mdoc
println(fragment.render)
```

:::tip
The default swap, `innerHTML`, replaces what is inside the target and leaves the target element itself in place. That is why the button keeps working after each click.
:::

To serve that fragment, the handler returns a `text/html` response. The helper below turns any `Dom` value into one:

```scala mdoc:compile-only
import zio.blocks.html._
import zio.http._

def htmlResponse(dom: Dom): Response =
  Response.ok(Body.fromArray(dom.render.getBytes("UTF-8"), ContentType.`text/html`))
```

The code above converts markup into an HTTP response:

- `dom.render` turns the tree into a string, escaping any text along the way.
- `Body.fromArray` wraps the bytes in a body, and the `text/html` content type declares that the body is HTML, so the browser reads the answer as a page.
- `Response.ok(...)` sets the status to `200 OK`.

## 6. Debounce a Live Search

Now the search box. We want the results to update as the reader types, but a request for every key press would send a dozen requests for one word. We want to wait until the typing pauses, and skip the request when nothing has changed. Two trigger modifiers do that:

```scala mdoc
import zio.http.htmx._
import scala.concurrent.duration._

println(HxTrigger("keyup").changed.delay(300.millis).render)
```

The code above builds the trigger string that the search box uses:

- `HxTrigger("keyup")` fires on every key release.
- `.changed` skips the request when the value of the box is the same as before, for example after an arrow key.
- `.delay(300.millis)` waits 300 milliseconds after the last key release. If another key is pressed in that time, the timer starts again, so only the final pause sends a request. The module renders the duration as `delay:300ms`.

The modifiers appear in the output in the order we added them, so the trigger reads `keyup changed delay:300ms`. Now we put the trigger on the search box:

```scala mdoc:silent
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

val search = input(
  `type`      := "search",
  id          := "q",
  name        := "q",
  placeholder := "Search books...",
  hxGet       := "/books",
  hxTrigger   := HxTrigger("keyup").changed.delay(300.millis),
  hxTarget    := HxTarget.css("#results")
)
```

```scala mdoc
println(search.render)
```

The code above makes the search box a live search:

- `hxGet := "/books"` sends the request to the same fragment endpoint as the button.
- `hxTrigger := HxTrigger("keyup").changed.delay(300.millis)` sets the timing we just built. Because the value is a typed `HxTrigger`, a misspelled modifier can't reach the page.
- `name := "q"` matters here. A `GET` request from an input sends the input's own `name=value` pair, so the server receives `/books?q=zion` when the reader has typed `zion`.

:::caution
A space in a `GET` query arrives as a plus sign: htmx sends `functional+programming`. The `QueryParams` type keeps a plus sign as it is, so the handler must turn `+` back into a space. The examples do this in a helper called `queryParam`. This is a known bug in 0.0.56. Until it is fixed, a plus sign that the reader types also becomes a space.
:::

## 7. Filter by Category

A category filter is a `select` element. When its value changes, it sends a request, just like the search box. The filter also needs the text in the search box, or choosing a category would throw the search away. The `hx-include` attribute solves that. It names one more element whose value is sent along with the request:

```scala mdoc:silent
import zio.blocks.html._
import zio.http.htmx._

val category = select(
  id        := "category",
  name      := "category",
  hxGet     := "/books",
  hxTrigger := HxTrigger.change,
  hxTarget  := HxTarget.css("#results"),
  hxInclude := HxTarget.css("#q"),
  option(value := "", "All"),
  option(value := "Language", "Language"),
  option(value := "Functional programming", "Functional programming"),
  option(value := "ZIO", "ZIO")
)
```

```scala mdoc
println(category.render)
```

The code above is the filter, annotated attribute by attribute:

- `hxTrigger := HxTrigger.change` fires the request when the reader picks a different option. No delay is needed, because a choice is made once.
- `hxInclude := HxTarget.css("#q")` adds the value of the search box, so the request carries both `q` and `category`.
- The option with value `""` means "All". The server treats an empty value as "no filter".

The search box needs the same courtesy in return. Its `hxInclude := HxTarget.css("#category")` sends the chosen category with every search. Both controls therefore always describe the whole filter.

## 8. Full Page or Fragment

When a reader opens `/books` in the address bar, the browser needs the whole page. When the search box asks for `/books`, it needs only the results. The request tells the server which one it wants: htmx adds the header `HX-Request: true` to every request it makes.

The module reads that header as a typed value, `HxRequest`:

```scala mdoc:silent
import zio.http._
import zio.http.htmx.headers.HxRequest

val fromHtmx = Request
  .get(URL.parse("/books?q=zio").toOption.get)
  .addHeaders(Headers(HxRequest.name -> "true"))

val fromBrowser = Request.get(URL.parse("/books").toOption.get)
```

```scala mdoc
println(fromHtmx.header(HxRequest))
println(fromBrowser.header(HxRequest))
```

The code above checks the header on two requests:

- `Headers(HxRequest.name -> "true")` adds the header that htmx sends. `HxRequest.name` is the string `hx-request`.
- `request.header(HxRequest)` returns `Some(HxRequest(true))` when the header is present and `None` when it is not.

The handler branches on that value. Here is the rule, written as a helper:

```scala mdoc:compile-only
import zio.http._
import zio.http.htmx.headers.HxRequest

def isHtmxRequest(request: Request): Boolean =
  request.header(HxRequest).exists(_.enabled)
```

The code above returns `true` only when the header is present and its value is `true`. A handler then chooses its answer:

- If the request came from htmx, return the results fragment.
- Otherwise, return the whole page, with the fragment placed inside it.

This way, one route serves both the first visit and every later update, and the page never needs two copies of the markup.

## 9. Putting It Together

The complete example combines the pieces we built: a search box with a debounced `hx-get`, a category filter that includes the search text, and a server that returns the whole page for a browser and only the results for an htmx request. The `main` method starts a server on a free port and sends a few requests to it. It then prints the answers.

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/CompleteExample.scala
```

This example combines all the concepts we've learned: the trigger modifiers, the target and include attributes, and the `HX-Request` check that splits the page from the fragment.

## Running the Examples

All code from this guide is available as runnable examples in the `zio-blocks-htmx-examples` directory. It is a standalone sbt build that uses the published ZIO Blocks artifacts. To run them, clone the repository and move into that directory:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/zio-blocks-htmx-examples
```

Each step has its own `main` method. Run one with `sbt`, passing the fully qualified name of the object. Steps 2 to 5 and the complete example start a server on a free port, send a few requests to it and exit, so no browser is needed to see the output. Step 1 only prints the static page.

The examples share four support files. `JdkHttp.scala` and `Client.scala` are the adapter and test client from [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md), copied unchanged except for the package name. `HxClient.scala` sends the `HX-Request` header, which `Client` doesn't. `Books.scala` holds the sample catalog.

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Books.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Books.scala:show-line-numbers
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

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/HxClient.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/HxClient.scala:show-line-numbers
```

</details>

Now the steps, in order. Step 1 renders the static page and needs no server:

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step1StaticPageExample.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step1StaticPageExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain addinteractivitywithhtmx.Step1StaticPageExample"
```

Step 2 adds the button and the `/books` fragment endpoint:

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step2FragmentEndpointExample.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step2FragmentEndpointExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain addinteractivitywithhtmx.Step2FragmentEndpointExample"
```

Step 3 makes the search box live and filters the fragment by the query:

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step3DebouncedSearchExample.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step3DebouncedSearchExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain addinteractivitywithhtmx.Step3DebouncedSearchExample"
```

Step 4 adds the category filter and the `hx-include` links between the two controls:

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step4CategoryFilterExample.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step4CategoryFilterExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain addinteractivitywithhtmx.Step4CategoryFilterExample"
```

Step 5 answers a browser with the full page and an htmx request with the fragment:

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step5FullPageOrFragmentExample.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/addinteractivitywithhtmx/Step5FullPageOrFragmentExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain addinteractivitywithhtmx.Step5FullPageOrFragmentExample"
```

The complete example is the last step. It is embedded in full in the section above, so you can run it with:

```bash
sbt "runMain addinteractivitywithhtmx.CompleteExample"
```

To check that every example compiles without running any of them, run this from inside `zio-blocks-htmx-examples`:

```bash
sbt compile
```

## What You've Learned

In this tutorial, you learned:

- What HTMX is, and why the server can answer with an HTML fragment instead of JSON
- How `hx-get`, `hx-target` and `hx-trigger` choose the request, the destination and the moment it fires
- How the `delay` and `changed` modifiers debounce a live search, and how the typed `HxTrigger` keeps a misspelled modifier out of the page
- How `hx-include` sends the value of a second control along with a request
- How a handler reads `HX-Request` with `HxRequest` and returns a fragment or a full page

You now have a working pattern for a page that reacts to its reader without a JavaScript framework. The next step is to build the server side on a richer foundation.

## Where to Go Next

- **Need the server side in more depth?** Read [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md) for the adapter this tutorial uses, and [How to Validate Query Parameters and Headers and Answer 400](./validate-query-parameters-and-headers.md) for turning query strings and headers into typed values.
- **Want every HTMX attribute?** The [HTMX reference](../reference/htmx/index.md) lists the typed values for each attribute, and the [`hx-trigger` reference](../reference/htmx/hx-trigger.md) covers every trigger modifier.
- **Planned next:** a guide to fragments and response headers, such as `HX-Trigger` and `HX-Push-Url`, is planned but not yet written.

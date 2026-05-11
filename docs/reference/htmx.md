---
id: htmx
title: "HTMX"
---

`zio-blocks-http-htmx` adds a typed HTMX DSL on top of `zio-blocks-html`, plus typed HTMX request and response headers backed by `zio-blocks-http-model`.

## Installation

```scala
// JVM
libraryDependencies += "dev.zio" %% "zio-blocks-http-htmx" % "@VERSION@"

// Scala.js
libraryDependencies += "dev.zio" %%% "zio-blocks-http-htmx" % "@VERSION@"
```

## Attribute DSL

Import both the HTML and HTMX packages:

```scala
import zio.blocks.html._
import zio.http.htmx._
```

### Requests, targets, swaps, and triggers

```scala
import scala.concurrent.duration.DurationInt

val searchForm = form(
  hxPost := "/search",
  hxTarget := HxTarget.find("#results"),
  hxSwap := HxSwap.InnerHTML.settle(200.millis),
  hxTrigger := HxTrigger.submit
)(
  input(
    nameAttr := "q",
    hxGet := "/suggest",
    hxTrigger := HxTrigger.input.changed.delay(300.millis),
    hxTarget := HxTarget.next(".suggestions")
  )
)
```

The typed attribute keys intentionally reject many plain strings at compile
time. For example, `hxSwap` expects `HxSwap`, `hxTarget` expects `HxTarget`,
selector-only attributes like `hxSelect` expect `CssSelector`, and JSON-bearing
attributes like `hxVals` / `hxHeaders` expect their dedicated wrapper types.
This keeps accidental stringly HTMX usage from slipping through while leaving
request URL attributes such as `hxGet` and `hxPost` flexible enough to accept
`String`, `Path`, or `URL`.

### Schema-backed JSON attributes

```scala
import zio.blocks.schema.Schema

case class Search(query: String, page: Int)
object Search {
  implicit val schema: Schema[Search] = Schema.derived
}

val widget = div(
  hxVals := HxVals.from(Search("zio", 1)),
  hxHeaders := HxHeadersValue.from(Search("zio", 1))
)()
```

### Event handlers

```scala
button(hxOn.click := js"console.log('clicked')")("Click")
```

`hxOn(...)` and `HxTrigger.filter(...)` both accept `Js`, which is an
intentional raw-JavaScript escape hatch. Treat those values the same way you
would treat any other inline script surface: do not build them from unsanitized
user input.

### Parsing and strictness notes

- `HxSwap`, `HxTarget`, `HxSync`, `HxParams`, and HTMX header payload types all
  support parsing from rendered strings.
- HTMX durations accept `ms`, `s`, and bare integers, with bare integers treated
  as milliseconds.
- Blank event names, blank selectors, and blank URL-update values are rejected.
- `HxEncoding.Multipart` models HTMX's explicit multipart override; the default
  URL-encoded form behavior is represented by omitting `hx-encoding`.

## Typed HTMX Headers

The module also provides typed headers in `zio.http.htmx.headers` that work directly with `Request.header` and `Response.header`:

```scala
import zio.http.htmx.headers._
import zio.http._

val request = Request.get(URL.root).addHeader(HxRequest.name, "true")
val isHtmx = request.header(HxRequest).contains(HxRequest(true))

val response = Response.ok
  .addHeader(HxRefresh.name, "true")
  .addHeader(HxTriggerHeader.name, "refresh-list")
```

Available header types include:

- request headers like `HxRequest`, `HxBoosted`, `HxCurrentUrl`, `HxTargetId`, `HxTriggerId`, `HxTriggerName`
- response headers like `HxLocation`, `HxPushUrl`, `HxReplaceUrl`, `HxRedirect`, `HxRefresh`, `HxReswap`, `HxRetarget`, `HxReselect`, `HxTriggerHeader`, `HxTriggerAfterSettle`, and `HxTriggerAfterSwap`

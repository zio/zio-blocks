---
id: return-htmx-fragments-and-response-headers
title: "How to Return HTMX Fragments, Full Pages and Response Headers"
description: "Answer one URL with a full page or an htmx fragment, update a second spot out of band, and steer the browser with typed HX-* response headers."
keywords:
  - "Server-Side Rendering"
  - "HTML Fragments"
  - "Out-of-Band Swap"
  - "HxRequest"
  - "HxTriggerHeader"
  - "HxLocation"
---

In this guide, we write the server side of a small tea shop built with htmx: a catalog, an "Add to cart" button, a cart badge, a cart page with a promo code and a checkout. Every handler answers with HTML built by the ZIO Blocks [HTML module](../reference/html.md) and with typed `HX-*` headers from the [HTMX module](../reference/htmx/index.md). By the end, you will have one handler per URL that sends a full page or a fragment depending on who asks, updates the cart badge in the same response, tells other parts of the page that the cart changed, redirects after checkout, puts validation errors next to the form and keeps the address bar in sync.

We assume you have finished the tutorial [Add Interactivity to a Page with HTMX](./add-interactivity-with-htmx.md), so `hxGet`, `hxTarget` and a swap are familiar. This guide covers:

- Answering one URL with a full page or a fragment, including boosted links and the Back button
- Updating a second spot with an out-of-band swap
- Announcing an event to the page with `HX-Trigger`
- Choosing between `HX-Redirect`, `HX-Location` and `HX-Refresh` after a form post
- Sending validation errors to another element with `HX-Retarget` and `HX-Reswap`
- Updating the address bar from the server with `HX-Push-Url` and `HX-Replace-Url`

## The Problem

An htmx page asks the server for many different things, and they all arrive as ordinary HTTP requests. A click on a category link wants the product list only, a reload of the same URL wants the whole page, and the Back button may want either. After a form post, the server often has to do more than return HTML: update the cart badge in the header, refresh a summary panel, move the browser to a receipt or show an error next to the form.

htmx reads all of this from request and response headers whose names and values are plain strings. Without typed headers, a handler for the "Add to cart" button looks like this:

```scala mdoc:compile-only
import zio.http._

def addToCart(request: Request, name: String, count: Int): Response = {
  val fromHtmx = request.headers.rawGet("hx-request").contains("true")
  val button   = s"""<button hx-post="/cart/items/sencha" hx-target="this" hx-swap="outerHTML">In cart</button>"""
  val badge    = s"""<span id="cart-count" hx-swap-oob="true">$count</span>"""
  val body     = if (fromHtmx) button + badge else s"<!DOCTYPE html><html><body>$button</body></html>"
  Response
    .ok(Body.fromArray(body.getBytes("UTF-8"), ContentType.`text/html`))
    .addHeader("HX-Trigger", s"""{"show-message":{"text":"Added $name"}}""")
    .addHeader("HX-Retarget", "#promo-eror")
}
```

Every string in it can be wrong without any error. A misspelled header name or selector, such as `#promo-eror`, is silently ignored by htmx. The JSON in `HX-Trigger` breaks as soon as `name` contains a quote. And the `fromHtmx` check is wrong in two cases we look at below: a boosted link and a Back button whose page htmx no longer has in its cache both send `HX-Request: true` and still need the full page.

In this guide, we replace each string with a typed value: the request headers `HxRequest`, `HxBoosted` and `HxHistoryRestoreRequest`, the `hxSwapOob` attribute, and the response headers `HxTriggerHeader`, `HxLocation`, `HxRedirect`, `HxRefresh`, `HxRetarget`, `HxReswap` and `HxReplaceUrl`.

## Prerequisites

The HTMX module is for Scala 3 only. Add it to your `build.sbt`:

```scala
// Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-http-htmx" % "@VERSION@"

// Scala 3.9 and newer (also needs scalacOptions += "-experimental")
libraryDependencies += "dev.zio" %% "zio-blocks-next-http-htmx" % "@VERSION@"
```

The HTMX module brings the HTML module and the [http-model](../reference/http-model/index.md) module with it, so `Dom`, `Request` and `Response` are already on the classpath. The http-model module is compiled for Java 21, so the programs need JDK 21 or newer. The handlers are plain `Request => Response` functions; the companion examples serve them with the small JDK adapter from [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md). The guide loads htmx 2.0.11 in the browser, the version the HTMX module's attributes follow, and every browser behavior described below was observed with it.

We use these imports throughout the guide:

```scala mdoc:silent
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import zio.blocks.html._
import zio.blocks.schema.json.{Json, WriterConfig}
import zio.http._
import zio.http.htmx._
import zio.http.htmx.headers._
```

`zio.http.htmx._` brings the `hx*` attribute keys and their value types, such as `HxTarget` and `HxSwap`, and `zio.http.htmx.headers._` brings the typed headers.

## The Tea Shop

The data is five teas in three categories, prices in cents, and one in-memory cart that every handler shares:

```scala mdoc:silent
final case class Product(id: String, name: String, category: String, priceCents: Int)

val products: List[Product] = List(
  Product("sencha", "Sencha", "Green", 1200),
  Product("matcha", "Matcha", "Green", 2400),
  Product("assam", "Assam", "Black", 900),
  Product("earl-grey", "Earl Grey", "Black", 1000),
  Product("rooibos", "Rooibos", "Herbal", 800)
)

val categories: List[String] = List("Green", "Black", "Herbal")

def price(cents: Int): String = "$" + (cents / 100) + "." + f"${cents % 100}%02d"

final class Cart {
  private val lines = new AtomicReference(Map.empty[String, Int])
  private val promo = new AtomicReference(Option.empty[String])

  def add(productId: String): Int =
    lines.updateAndGet(current => current.updated(productId, current.getOrElse(productId, 0) + 1))(productId)
  def quantity(productId: String): Int = lines.get.getOrElse(productId, 0)
  def count: Int                       = lines.get.values.sum
  def applyPromo(code: String): Unit   = promo.set(Some(code))
  def totalCents: Int = {
    val gross = lines.get.map { case (id, n) => products.find(_.id == id).fold(0)(_.priceCents) * n }.sum
    if (promo.get.contains("TEA10")) gross * 9 / 10 else gross
  }
  def clear(): Unit = { lines.set(Map.empty); promo.set(None) }
}

val cart = new Cart
```

The handlers run on the server's threads, so the cart keeps its state in `AtomicReference`s and changes it atomically. A real shop keeps one cart per session; one shared cart keeps the guide short.

Now the page itself. The catalog has category links and an `#catalog` element with sort links and the product list. The layout around it has the cart badge in the header, a `#message` line, the main area and a cart summary on the side:

```scala mdoc:silent
val htmxScript: Dom.Element = script(src := "https://cdn.jsdelivr.net/npm/htmx.org@2.0.11/dist/htmx.min.js")

def catalogUrl(category: String, sort: String): String = {
  val params = List("category" -> category, "sort" -> sort).filter { case (k, v) => v.nonEmpty && !(k == "sort" && v == "name") }
  if (params.isEmpty) "/" else params.map { case (k, v) => s"$k=$v" }.mkString("/?", "&", "")
}

def addButton(product: Product): Dom.Element = {
  val quantity = cart.quantity(product.id)
  button(
    id       := s"add-${product.id}",
    hxPost   := s"/cart/items/${product.id}",
    hxTarget := HxTarget.this_,
    hxSwap   := HxSwap.OuterHTML
  )(if (quantity == 0) "Add to cart" else s"In cart ($quantity)")
}

def catalog(category: String, sort: String): Dom.Element = {
  val shown  = products.filter(product => category.isEmpty || product.category == category)
  val sorted = if (sort == "price") shown.sortBy(_.priceCents) else shown.sortBy(_.name)
  div(id := "catalog")(
    p(
      "Sort by ",
      a(hxGet := catalogUrl(category, "name"), hxTarget := HxTarget.css("#catalog"), hxSwap := HxSwap.OuterHTML)("name"),
      " | ",
      a(hxGet := catalogUrl(category, "price"), hxTarget := HxTarget.css("#catalog"), hxSwap := HxSwap.OuterHTML)("price")
    ),
    ul(sorted.map(product => li(strong(product.name), s" ${price(product.priceCents)} ", addButton(product))))
  )
}

def categoryLinks: Dom.Element =
  p(
    ("" :: categories).map { category =>
      a(
        href      := catalogUrl(category, ""),
        hxGet     := catalogUrl(category, ""),
        hxTarget  := HxTarget.css("#catalog"),
        hxSwap    := HxSwap.OuterHTML,
        hxPushUrl := HxUrlUpdate(true)
      )(if (category.isEmpty) "All teas " else s"$category ")
    }
  )

def catalogContent(category: String, sort: String): Dom.Element =
  div(h1("Teas"), categoryLinks, catalog(category, sort))

def cartBadge: Dom.Element = span(id := "cart-count")(cart.count.toString)

def cartSummary: Dom.Element =
  aside(
    id        := "cart-summary",
    hxGet     := "/cart/summary",
    hxTrigger := HxTrigger("cart-updated").from("body"),
    hxSwap    := HxSwap.OuterHTML
  )(s"${cart.count} items, ${price(cart.totalCents)}")

def layout(heading: String, content: Dom.Element): Dom.Element =
  html(lang := "en")(
    head(meta(charset := "utf-8"), title(s"$heading - Tea Shop"), htmxScript),
    body(hxOn("show-message") := Js("document.getElementById('message').textContent = event.detail.text"))(
      header(nav(hxBoost := true)(a(href := "/")("Teas"), " ", a(href := "/cart")("Cart (", cartBadge, ")"))),
      p(id := "message")(""),
      main(id := "main")(content),
      cartSummary
    )
  )

def htmlResponse(html: String): Response =
  Response.ok(Body.fromArray(html.getBytes("UTF-8"), ContentType.`text/html`))
```

The code above builds every element the later sections talk to:

- `catalogUrl` builds the address of a catalog view and leaves out empty and default values, so "all teas sorted by name" is just `/`.
- `addButton` posts to `/cart/items/<id>` and replaces itself (`hxTarget := HxTarget.this_` with `HxSwap.OuterHTML`) with whatever the server sends back.
- `catalog` is the `#catalog` element. The category links and the sort links ask for a new `#catalog` and swap it in with `outerHTML`, so the fragment the server sends is exactly the element the page already contains.
- `categoryLinks` carries both `href` and `hxGet`: htmx takes over the click, and the `href` still works without JavaScript. `hxPushUrl` puts each category into the address bar.
- `cartBadge` is the `#cart-count` element in the header, `cartSummary` refreshes itself when a `cart-updated` event reaches the page, and the `hxOn("show-message")` handler on `<body>` writes a message into `#message`. Sections below explain each one when the server starts using it.
- `nav(hxBoost := true)` turns the two header links into boosted links: htmx fetches the next page with an AJAX request and swaps its `<body>` in, instead of a full browser navigation.

Render the layout around an empty main area to check the hooks:

```scala mdoc
println(layout("Teas", div).render(indent = 2))
```

Finally, two helpers let us send requests to a handler without a server and print what htmx would receive: the status, every `hx-*` and `vary` header, and the body:

```scala mdoc:silent
def get(target: String, headers: Header*): Request =
  headers.foldLeft(Request.get(URL.parse(target).toOption.get))((request, header) => request.addHeader(header))

def post(target: String, form: Form, headers: Header*): Request =
  headers.foldLeft(Request.post(URL.parse(target).toOption.get, Body.fromString(form.encode)))((request, header) =>
    request.addHeader(header)
  )

def show(response: Response): Unit = {
  println(s"status: ${response.status.code}")
  response.headers.toList.foreach { case (name, value) =>
    if (name.startsWith("hx-") || name == "vary") println(s"$name: $value")
  }
  println(response.body.asString())
}
```

## Answer One URL with a Full Page or a Fragment

The category links push URLs such as `/?category=Green` into the address bar, so `/` must answer three kinds of visitors. A browser that loads, reloads or bookmarks the URL needs the full page. A category link needs only the new `#catalog`. And htmx itself sometimes asks for the URL on the reader's behalf and needs the whole page back:

| Request                                         | `HX-Request` | `HX-Boosted` | `HX-History-Restore-Request` | Needs     |
| ----------------------------------------------- | ------------ | ------------ | ---------------------------- | --------- |
| Address bar, reload, bookmark                   | absent       | absent       | absent                       | full page |
| A click on an `hxGet` link                      | `true`       | absent       | absent                       | fragment  |
| A click on a boosted link (`hxBoost`)           | `true`       | `true`       | absent                       | full page |
| Back button, page not in htmx's history cache   | `true`       | absent       | `true`                       | full page |

A boosted link swaps the response into `<body>` and takes the page title from it, and a history restore puts the response in place of the whole `<body>` as well. Only the second row wants a fragment, so the check reads all three headers:

```scala mdoc:silent
def wantsFragment(request: Request): Boolean =
  request.header(HxRequest).exists(_.enabled) &&
    !request.header(HxBoosted).exists(_.enabled) &&
    !request.header(HxHistoryRestoreRequest).exists(_.enabled)

def pageOrFragment(request: Request, heading: String)(fragment: => Dom.Element, content: => Dom.Element): Response = {
  val html =
    if (wantsFragment(request)) fragment.render
    else doctype.render + layout(heading, content).render
  htmlResponse(html).addHeader("vary", "hx-request, hx-boosted, hx-history-restore-request")
}

def catalogPage(request: Request): Response = {
  val category = request.queryParams.getFirst("category").getOrElse("")
  val sort     = request.queryParams.getFirst("sort").getOrElse("name")
  pageOrFragment(request, "Teas")(catalog(category, sort), catalogContent(category, sort))
}
```

The code above:

- `request.header(HxRequest)` parses the `HX-Request` header into an `HxRequest`, and `exists(_.enabled)` is `true` only for the value `true`. A missing header and a malformed one both give `None`, so both count as "not htmx".
- `pageOrFragment` takes the fragment and the main content as by-name parameters, so only the one it needs is built.
- `addHeader("vary", ...)` tells browsers and caches that the answer depends on these three headers, so a cached fragment is never shown as a page.

Ask `wantsFragment` about each row of the table, plus a request whose `HX-Request` value is not a boolean:

```scala mdoc
println(wantsFragment(get("/?category=Green")))
println(wantsFragment(get("/?category=Green", HxRequest(true))))
println(wantsFragment(get("/cart", HxRequest(true), HxBoosted(true))))
println(wantsFragment(get("/?category=Green", HxRequest(true), HxHistoryRestoreRequest(true))))
println(get("/", Header.Custom("hx-request", "yes")).header(HxRequest))
```

Only the category link gets a fragment. The last line shows that `HX-Request: yes` does not parse, so `Request#header` returns `None` and that request gets the full page. Send the category link's request to the handler:

```scala mdoc
show(catalogPage(get("/?category=Green", HxRequest(true))))
```

The response is the `#catalog` element alone, with the two green teas. The same URL without `HX-Request` returns `<!DOCTYPE html>` and the whole layout around the same element.

In a browser, clicking "Green" sends `HX-Request: true` and swaps the new `#catalog` in, and the address bar shows `/?category=Green`. A reload of that address gets the full page. Clicking the boosted "Cart" link sends `HX-Request: true` and `HX-Boosted: true`, gets the full cart page and keeps the header, and the tab title changes to "Cart - Tea Shop". With htmx's history cache turned off (`htmx.config.historyCacheSize = 0`), pressing Back sends `HX-History-Restore-Request: true` and gets the full page back.

:::warning
A check that reads only `HX-Request` answers the last two rows with a fragment. We tried that version in the browser: after a click on the boosted "Cart" link, the header and its navigation were gone and the tab title still said "Teas", and after Back with a history cache miss, the `<body>` held only the product list. The tutorial's check, which reads `HX-Request` and `HX-History-Restore-Request`, is enough for a page without boosted links; once the page uses `hxBoost`, read `HX-Boosted` too.
:::

## Update a Second Spot with an Out-of-Band Swap

When the reader clicks "Add to cart", the button replaces itself with "In cart (1)". The cart badge in the header must change too, but it is outside the button's target. An out-of-band swap updates it in the same response: htmx looks for elements with an `hx-swap-oob` attribute in the response, takes them out, and swaps each one into the page element with the same `id`. The HTMX module types this attribute as `hxSwapOob`:

```scala mdoc:silent
def addToCart(productId: String): Response =
  products.find(_.id == productId) match {
    case None => Response.text(Status.NotFound, s"No product $productId")
    case Some(product) =>
      cart.add(product.id)
      val badge = cartBadge(hxSwapOob := HxSwapOob.True)
      htmlResponse(addButton(product).render + badge.render)
  }
```

The response is the new button, which htmx swaps into the button's own place, followed by the badge. `cartBadge(hxSwapOob := HxSwapOob.True)` adds the attribute to the same element the layout renders, so the badge's markup lives in one place. The two elements are rendered separately and concatenated: a fragment can contain any number of top-level elements.

Add a Sencha to the cart:

```scala mdoc
show(addToCart("sencha"))
```

In a browser, the button changes to "In cart (1)" and the header's badge changes from 0 to 1 after one request. htmx removes `hx-swap-oob` from the element before it inserts it, so the page keeps a plain `<span id="cart-count">`.

`HxSwapOob.True` replaces the whole element with the same `id`. `HxSwapOob.using(HxSwap.InnerHTML)` swaps only its content, and `HxSwapOob.using(HxSwap.InnerHTML, HxTarget.css("#cart-count"))` names the target explicitly, so the element in the response can have another `id` or none.

:::warning
Keep the `HxSwap` you pass to `HxSwapOob.using` bare, without modifiers such as `swap(...)` or `settle(...)`. In 0.0.56, `HxSwapOob.using(HxSwap.InnerHTML.swap(10.millis))` renders `hx-swap-oob="innerHTML swap:10ms"`, which htmx 2 reads as the strategy `innerHTML swap` and the selector `10ms`. In the browser this raised `htmx:swapError`, and htmx swapped nothing from that response, not even the main fragment.
:::

## Tell the Page Something Happened

An out-of-band swap works when the server knows each spot to update. When several parts of the page care about the cart, it is simpler to announce what happened and let each part decide. The `HX-Trigger` response header names client-side events that htmx fires as soon as the response arrives. Its typed form is `HxTriggerHeader`, and its value is an `HxEventPayload`:

```scala mdoc:silent
def addToCartAndAnnounce(productId: String): Response = {
  val response = addToCart(productId)
  products.find(_.id == productId).fold(response) { product =>
    val message = Json.Object("show-message" -> Json.Object("text" -> Json.String(s"Added ${product.name}")))
    response
      .addHeader(HxTriggerHeader(HxEventPayload.Event("cart-updated")))
      .addHeader(HxTriggerAfterSettle(HxEventPayload.JsonValue(message)))
  }
}

def cartSummaryFragment: Response = htmlResponse(cartSummary.render)
```

The code above sends two events with two kinds of payload:

- `HxEventPayload.Event("cart-updated")` is a plain event name. The `cartSummary` element from the layout listens for it with `hxTrigger := HxTrigger("cart-updated").from("body")`, which renders as `hx-trigger="cart-updated from:body"`: htmx fires the event on the button that sent the request, the event bubbles up to `<body>`, and the summary sends `GET /cart/summary`. `cartSummaryFragment` answers with a new `<aside>`, and the summary's `hxSwap := HxSwap.OuterHTML` replaces the old one with it. With the default `innerHTML` swap, the new `<aside>` would land inside the old one, and in the browser each nested copy then sent its own request on the next `cart-updated`.
- `HxEventPayload.JsonValue(message)` sends a JSON object whose keys are event names and whose values become the event's `detail`. The `hxOn("show-message")` handler on `<body>` reads `event.detail.text` and writes it into `#message`.
- `HxTriggerAfterSettle` fires its events after htmx has swapped and settled the new content, later than `HxTriggerHeader`. `HxTriggerAfterSwap` sits between the two. Use a later header when the listener needs the new HTML to be in the page already.

Both event names are lowercase with dashes. HTML lowercases attribute names, so an `hx-on:` attribute can't name an event with capital letters; `hx-trigger` values keep their case, but one style for every event is easier to follow.

Add an Assam and read the headers:

```scala mdoc
show(addToCartAndAnnounce("assam"))
show(cartSummaryFragment)
```

In a browser, one click on "Add to cart" sends the `POST`, the button and the badge update, the summary sends `GET /cart/summary` with `HX-Trigger: cart-summary` (the requesting element's id) and swaps in the new totals, and `#message` shows "Added Assam".

The JSON payload must be an object. `HxEventPayload.JsonValue` also accepts other JSON values, such as an array, but htmx then treats the whole header as a comma-separated list of event names: in the browser, `HX-Trigger: ["cart-updated"]` fired no `cart-updated` event. To send several plain events, write them as one name list, `HxEventPayload.Event("cart-updated, show-banner")`, or use several keys in one JSON object.

:::warning
`HxEventPayload.JsonValue` and `HxLocation` print their JSON with `Json#print`, which writes characters outside ASCII as they are. HTTP header values are meant to be ASCII. With the JDK HTTP server from the companion examples, a value with a character above `U+00FF`, such as `✓`, makes the server drop the connection without a response, and characters such as `é` are sent as single ISO-8859-1 bytes. This is a known bug in 0.0.56.
:::

Until the module escapes them, write such a payload with the raw header name and escaped JSON. `Json#print` escapes non-ASCII characters when the `WriterConfig` has `withEscapeUnicode(true)`, and `Response#addHeader` accepts the header name and the escaped text:

```scala mdoc:silent
val greeting = Json.Object("show-message" -> Json.Object("text" -> Json.String("Added Sencha ✓")))

val escaped = htmlResponse("").addHeader(HxTriggerHeader.name, greeting.print(WriterConfig.withEscapeUnicode(true)))
```

Print the header value on the wire, then read it back as a typed `HxTriggerHeader`:

```scala mdoc
println(escaped.headers.rawGet(HxTriggerHeader.name))
println(escaped.header(HxTriggerHeader).map(_.renderedValue))
```

The value on the wire is pure ASCII. htmx parses the escaped JSON back into the same text, and the typed `HxTriggerHeader` read gives back the original characters.

## Redirect, Navigate or Refresh After a Form Post

After "Place order" the reader should see a receipt, and after "Empty cart" the cart page should start over. The response to an htmx request can't use an HTTP redirect for this: the browser's `XMLHttpRequest` follows a `303` by itself, and htmx only sees the final response. In the browser, a `303 See Other` to `/orders/1001` was followed with every `HX-*` request header sent again, so our handler answered with a fragment, htmx swapped the receipt into the button's target inside the cart page, and the address bar still said `/cart`.

htmx has three response headers for this instead. Each one makes htmx skip the swap of the response body:

| Header        | Typed value              | What the browser does                                                       |
| ------------- | ------------------------ | --------------------------------------------------------------------------- |
| `HX-Redirect` | `HxRedirect("/")`        | A full page load of the new URL, without `HX-*` request headers             |
| `HX-Location` | `HxLocation("/orders/1001", Some(...))` | An htmx `GET` of the new URL into a target, then pushes the URL |
| `HX-Refresh`  | `HxRefresh(true)`        | A full reload of the current page                                           |

`HX-Location` keeps the page and its scripts and is the smooth choice when the next view fits into the current layout. `HX-Redirect` is the right choice when the next page has another layout, or when the reader leaves the htmx part of the site. The cart page and the three handlers look like this:

```scala mdoc:silent
val orderNumbers = new AtomicInteger(1000)

def cartContent: Dom.Element =
  div(
    h1("Your Cart"),
    ul(products.filter(product => cart.quantity(product.id) > 0).map { product =>
      li(s"${cart.quantity(product.id)} x ${product.name}")
    }),
    p(id := "cart-total")(s"Total: ${price(cart.totalCents)}"),
    form(hxPost := "/cart/promo", hxTarget := HxTarget.css("#cart-total"), hxSwap := HxSwap.OuterHTML)(
      input(name := "code", placeholder := "Promo code"),
      button(`type` := "submit")("Apply"),
      span(id := "promo-error")("")
    ),
    button(hxPost := "/checkout")("Place order"),
    button(hxPost := "/cart/clear")("Empty cart")
  )

def orderContent(number: Int): Dom.Element =
  div(h1(s"Order $number Placed"), p("Thank you! Your tea is on its way."), a(href := "/")("Keep shopping"))

def checkout(): Response =
  if (cart.count == 0) htmlResponse("").addHeader(HxRedirect("/"))
  else {
    val number = orderNumbers.incrementAndGet()
    cart.clear()
    htmlResponse("")
      .addHeader(HxLocation(s"/orders/$number", target = Some(HxTarget.css("#main"))))
      .addHeader(HxTriggerHeader(HxEventPayload.Event("cart-updated")))
  }

def clearCart(): Response = {
  cart.clear()
  htmlResponse("").addHeader(HxRefresh(true))
}

def orderPage(request: Request, number: Int): Response =
  pageOrFragment(request, s"Order $number")(
    div(orderContent(number), cartBadge(hxSwapOob := HxSwapOob.True)),
    orderContent(number)
  )
```

The code above:

- `checkout` with an empty cart sends `HxRedirect("/")`, so the browser loads the catalog as a fresh page.
- `checkout` with items places the order and sends `HxLocation` with the receipt's path and `#main` as the target. htmx then requests `/orders/1001` with `HX-Request: true` and `HX-Target: main`, and `orderPage` answers with the fragment for `#main`. A reload of `/orders/1001` gets the full page from the same handler.
- The checkout empties the cart, but htmx never swaps the body of a response with `HX-Location`, so an out-of-band badge there would be ignored. `checkout` announces `cart-updated` instead, which refreshes the summary, and the receipt fragment carries the out-of-band badge. htmx also finds `hx-swap-oob` elements nested inside the main fragment.
- `clearCart` sends `HxRefresh(true)`, and the browser reloads `/cart` with the badge back at 0.

Place an order with the two teas in the cart, and check the header text:

```scala mdoc
show(checkout())
show(orderPage(get("/orders/1001", HxRequest(true), HxTargetId("main")), 1001))
show(checkout())
```

The first checkout sends `hx-location` with the JSON object htmx expects, and the second one, with the cart now empty, sends `hx-redirect`. In a browser, "Place order" swaps the receipt into `#main`, the header and the summary stay and both drop to 0 items, the address bar shows `/orders/1001`, and Back returns to the cart. The tab title still says "Cart - Tea Shop", because the fragment has no `<title>`. Back shows the cart as htmx saved it in its history cache, with both teas and a badge of 2: a history cache hit restores the saved snapshot without asking the server.

:::warning
Always give `HxLocation` a target that is a plain CSS selector, such as `HxTarget.css("#main")`. Without a target, htmx swaps the answer into `<body>`; our handler sees `HX-Request: true` without `HX-Boosted` and sends the `#main` fragment, so the header and the summary disappeared in the browser. `HxLocation` also accepts relative targets, such as `HxTarget.closest("main")`, but htmx 2 resolves the `HX-Location` target with `document.querySelector`, so in the browser it raised `htmx:targetError` and nothing happened. This is a known bug in 0.0.56.
:::

`HX-Redirect`, `HX-Location` and `HX-Refresh` end htmx's handling of the response before the swap. An `HxTriggerHeader` sent with them still fires, but `HxTriggerAfterSwap` and `HxTriggerAfterSettle` don't: in the browser, a response with `HX-Redirect` and `HX-Trigger-After-Settle` fired only the `HX-Trigger` event before the new page loaded.

## Send Validation Errors Somewhere Else

The promo-code form targets `#cart-total`: a valid code sends the new total, and the form's `hxSwap := HxSwap.OuterHTML` puts it in place. A wrong code should not replace the total. It should put an error message next to the form. `HxRetarget` changes the target for this one response, and `HxReswap` changes how the content is swapped:

```scala mdoc:silent
def applyPromo(request: Request): Response = {
  val code = Form.fromString(request.body.asString()).get("code").getOrElse("").trim.toUpperCase
  if (code == "TEA10") {
    cart.applyPromo(code)
    val total      = p(id := "cart-total")(s"Total: ${price(cart.totalCents)} (TEA10 applied)")
    val clearError = span(id := "promo-error", hxSwapOob := HxSwapOob.True)("")
    htmlResponse(total.render + clearError.render).addHeader(HxTriggerHeader(HxEventPayload.Event("cart-updated")))
  } else
    htmlResponse(span(s"$code is not a valid promo code").render)
      .addHeader(HxRetarget(CssSelector.id("promo-error")))
      .addHeader(HxReswap(HxSwap.InnerHTML))
}
```

The code above handles the two outcomes:

- A valid code applies the discount and sends the new `#cart-total`. An out-of-band `#promo-error` clears a message left by an earlier attempt, and `cart-updated` refreshes the summary on the side.
- An invalid code sends the message only. `HxRetarget(CssSelector.id("promo-error"))` sends it to `#promo-error` instead of `#cart-total`, and `HxReswap(HxSwap.InnerHTML)` puts it inside that element instead of replacing the element. The code the reader typed is rendered as text by the HTML module, so a code such as `<b>` can't add markup to the page.
- `Form.fromString` decodes the `application/x-www-form-urlencoded` body that htmx sends for a form.

The checkout above emptied the cart, so put a Matcha in it first:

```scala mdoc:silent
cart.add("matcha")
```

Try a wrong code, then the right one:

```scala mdoc
show(applyPromo(post("/cart/promo", Form("code" -> "TEA5"), HxRequest(true))))
show(applyPromo(post("/cart/promo", Form("code" -> "tea10"), HxRequest(true))))
```

In a browser, the wrong code leaves the total alone and shows "TEA5 is not a valid promo code" under the form, and the right code updates the total, clears the message and refreshes the summary.

:::warning
Send the error with status `200`. htmx 2 swaps `2xx` and `3xx` responses by default, except `204`: in the browser, the same response with status `422 Unprocessable Entity` swapped nothing, ignored `HX-Retarget` and `HX-Reswap`, and fired `htmx:responseError`. To keep a `4xx` status, change `htmx.config.responseHandling` on the page so that htmx swaps that status.
:::

## Update the Address Bar from the Server

The category links push their URL with the `hxPushUrl` attribute, so each category is a new entry in the browser history. A sort change is different: the reader is looking at the same list in another order, and Back should not step through every sort. The sort links carry no `hx-push-url`, and the server decides instead: `HxReplaceUrl` replaces the current address without a new history entry, and `HxPushUrl` adds one:

```scala mdoc:silent
def catalogRoute(request: Request): Response = {
  val response = catalogPage(request)
  request.queryParams.getFirst("sort") match {
    case Some(sort) if wantsFragment(request) =>
      val category = request.queryParams.getFirst("category").getOrElse("")
      response.addHeader(HxReplaceUrl(HxUrlUpdate(catalogUrl(category, sort))))
    case _ => response
  }
}
```

`HxUrlUpdate` is the same type the `hxPushUrl` attribute takes, so `HxUrlUpdate(true)`, `HxUrlUpdate(false)` and a URL work in both places. The handler adds the header only to an htmx fragment, because a full page load already shows its own URL. `catalogUrl` leaves out the default sort, so a sort by name replaces the address with `/?category=Green` and a sort by price with `/?category=Green&sort=price`:

```scala mdoc
show(catalogRoute(get("/?category=Green&sort=price", HxRequest(true))))
```

In a browser, a click on "Green" adds a history entry, and a click on "price" changes the address to `/?category=Green&sort=price` without one, so a single Back goes from the sorted green teas straight to the page before "Green". A `HX-Push-Url` header in the same place adds an entry instead, and its URL wins over the request's own URL.

Every URL the server pushes or replaces must work as a full page, because htmx asks for it again on a history cache miss and the reader can reload or bookmark it. Here, `catalogRoute` answers `/?category=Green&sort=price` from the same handler and the same `wantsFragment` check as before, so a reload, a bookmark and a Back with a history restore all get the full page.

## Putting It Together

The complete example serves the whole shop with the JDK HTTP server from [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md). It routes each URL to the handlers from the sections above. It differs from them in four ways: the shop lives in its own `CompleteShop` object, because an object that defines `main` would make the `main(...)` element in the layout call that method; it declares the model and helpers itself instead of importing them from `TeaShop.scala`, so the file stands alone; it adds a `notFound` handler; and a single `app` function routes every URL. `main` starts the server on port 8080 or on the port given as the first argument:

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/returnhtmxfragments/CompleteExample.scala
```

Run it and open `http://localhost:8080/`. Pick a category, sort by price, add two teas and watch the badge, the summary and the message line change, then open the cart through the header, try the promo codes "TEA5" and "TEA10", and place the order. Open the browser's developer tools on the Network tab to see the `HX-*` headers of each request and response.

## Running the Examples

All examples in this guide have corresponding runnable Scala files in the `zio-blocks-htmx-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/zio-blocks-htmx-examples
```

Each example prints what its handlers answer to the requests from its section, then starts the server on port 8080 and keeps running so you can open `http://localhost:8080/` in a browser. Press Ctrl+C to stop it before running the next one. To use another port, pass it as an argument, for example `sbt "runMain returnhtmxfragments.Step1FullPageOrFragmentExample 9090"`. The examples share the tea shop in `TeaShop.scala`, and `JdkHttp.scala` is the server adapter from [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md), unchanged.

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/returnhtmxfragments/TeaShop.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/returnhtmxfragments/TeaShop.scala:show-line-numbers
```

</details>

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/returnhtmxfragments/Step1FullPageOrFragmentExample.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/returnhtmxfragments/Step1FullPageOrFragmentExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain returnhtmxfragments.Step1FullPageOrFragmentExample"
```

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/returnhtmxfragments/Step2OutOfBandSwapExample.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/returnhtmxfragments/Step2OutOfBandSwapExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain returnhtmxfragments.Step2OutOfBandSwapExample"
```

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/returnhtmxfragments/Step3TriggerEventsExample.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/returnhtmxfragments/Step3TriggerEventsExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain returnhtmxfragments.Step3TriggerEventsExample"
```

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/returnhtmxfragments/Step4RedirectAfterPostExample.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/returnhtmxfragments/Step4RedirectAfterPostExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain returnhtmxfragments.Step4RedirectAfterPostExample"
```

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/returnhtmxfragments/Step5ValidationErrorsExample.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/returnhtmxfragments/Step5ValidationErrorsExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain returnhtmxfragments.Step5ValidationErrorsExample"
```

<details>
  <summary>zio-blocks-htmx-examples/src/main/scala/returnhtmxfragments/Step6UpdateUrlExample.scala</summary>

```scala mdoc:embed:zio-blocks-htmx-examples/src/main/scala/returnhtmxfragments/Step6UpdateUrlExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain returnhtmxfragments.Step6UpdateUrlExample"
```

The complete example from "Putting It Together" runs the same way:

```bash
sbt "runMain returnhtmxfragments.CompleteExample"
```

## Going Further

These pages cover the pieces this guide uses in more depth:

- [Request and Response Headers](../reference/htmx/response-headers.md) lists every typed `HX-*` header, including `HxReselect`, `HxTriggerAfterSwap` and the request headers `HxCurrentUrl`, `HxTargetId` and `HxTriggerName`.
- [`HxSwap`](../reference/htmx/hx-swap.md), [`HxTarget`](../reference/htmx/hx-target.md) and [`HxUrlUpdate`](../reference/htmx/hx-url-update.md) describe the values `HxReswap`, `HxLocation`, `HxPushUrl` and `HxReplaceUrl` reuse from the attribute DSL.
- [How to Validate Query Parameters and Headers and Answer 400](./validate-query-parameters-and-headers.md) decodes typed query parameters, such as the category and the sort, and reports every mistake at once.
- [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md) explains the adapter the examples use and its limits.

---
id: build-a-live-dashboard-with-datastar
title: "Build a Live Counter and Dashboard with Datastar"
description: "Build a Datastar weather station: a button that posts its signals, a server that patches the page, and a dashboard streamed over one open connection."
keywords:
  - "Datastar"
  - "Signals"
  - "Server-Sent Events"
  - "Live Dashboard"
  - "patchSignals"
  - "patchElements"
---

Welcome! This tutorial is for Scala developers who can already build an HTML page with the ZIO Blocks HTML module and now want that page to react to clicks and to server updates without writing any JavaScript. You don't need prior experience with Datastar. If you haven't built a page with the HTML module yet, start with [Render Your First Server-Side HTML Page](./your-first-html-page.md); this tutorial picks up where it ends.

## 1. Introduction

By the end of this tutorial, you will understand:

- What a Datastar signal is, and how `dataSignals` and `dataText` put one on a page
- How a `dataOn.click` attribute makes the browser send the signals to the server, and how the server answers with a `DatastarEvent.patchSignals` event
- How to read the signals the browser sends into a case class with the schema JSON codec
- How a `DatastarEvent.patchElements` event swaps a piece of HTML into the page
- How one long-lived event stream turns a table into a live dashboard, and what the server sees when the browser goes away

We learn these concepts by building a small weather station, one step at a time:

1. A page with a signal
2. A button that posts
3. Reading the signals
4. Swapping a fragment
5. A live dashboard

At the end, we run the finished page and try it in a browser. We recommend reading from top to bottom — each section builds on the previous one.

The datastar module is for Scala 3 only: there is no Scala 2.13 artifact. Add it to your `build.sbt`:

```scala
// Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-datastar" % "@VERSION@"

// Scala 3.9 and newer (also needs scalacOptions += "-experimental")
libraryDependencies += "dev.zio" %% "zio-blocks-next-datastar" % "@VERSION@"
```

The datastar module depends on the HTML module and the http-model module. The http-model module is compiled for Java 21, so the programs in this tutorial need JDK 21 or newer to run. The module's package is `zio.http.datastar`, even though the artifact is called `zio-blocks-datastar`.

The module gives you the `data-*` attributes and the events. It does not ship a server and it does not ship the Datastar JavaScript, so this tutorial serves its pages with the streaming adapter from [How to Send Server-Sent Events with http-model](./send-server-sent-events.md), a small piece of code that connects the JDK's built-in HTTP server to a `Request => Response` function. We reuse it and don't explain it again.

## 2. Background: Signals in the Browser, Patches from the Server

Datastar is a small JavaScript library that reads `data-*` attributes on ordinary HTML elements. The page keeps named values called *signals*. An attribute such as `data-text="$readings"` shows a signal, and an attribute such as `data-on:click="@post('/reading')"` says what to do on a click: send a `POST` request to the server.

Every request carries the signals. A `POST` sends them as a JSON body, and a `GET` sends the same JSON in a query parameter called `datastar`. The server answers with a `text/event-stream` response, a stream of Server-Sent Events (SSE), where each event is a *patch*: a `datastar-patch-signals` event sets new signal values, and a `datastar-patch-elements` event swaps new HTML into the page. The exchange for one click looks like this:

```text
  Browser                                          Server
  ───────                                          ──────
  <button data-on:click="@post('/reading')">
        │  the reader clicks
        │
        ├──── POST /reading  {"readings":0} ─────►  decode the signals
        │                                            │
        │◄──── text/event-stream ───────────────────┤
        │      event: datastar-patch-signals         │
        │      data: signals {"readings":1}          │
        │      event: datastar-patch-elements        │
        │      data: elements <p id="latest">…</p>   │
        ▼
  signals update, the page changes
```

All of the HTML and all of the logic stay on the server, in Scala. The `datastar` module is the typed Scala side of this protocol: it renders the attributes and builds the events. This tutorial targets Datastar 1.0.4, the version the module's event names and attribute syntax match.

## 3. A Page with a Signal

Let's start with the page itself. A signal is a named, typed handle: `Signal[Int]("readings")` names a signal `readings` that holds an `Int`. `dataSignals` declares it with its first value, and `dataText` shows it. Here is the weather station's first page, which counts the readings taken:

```scala mdoc:silent
import zio.blocks.html._
import zio.http.datastar._

val readings = Signal[Int]("readings")

val counter = div(
  dataSignals(readings := 0),
  p("Readings taken: ", span(dataText := readings))
)
```

The code above:

- `Signal[Int]("readings")` creates the typed handle; the compiler checks the name, which must be a dot-separated path of JavaScript identifiers and must not contain `__`
- `readings := 0` pairs the signal with its first value, encoded as JSON with the type's schema
- `dataSignals(...)` renders the `data-signals` attribute, which declares the signal when the page loads
- `dataText := readings` renders `data-text="$readings"`, which keeps the span's text equal to the signal

Let's look at the HTML this renders:

```scala mdoc
println(counter.renderMinified)
```

Notice that the attribute value for `data-signals` is a JSON object, with its quotes escaped as `&quot;` because it sits inside an HTML attribute, and that `dataText := readings` became `$readings`: the `$` prefix is how a Datastar expression refers to a signal.

Datastar has to be loaded by the page, and here the library stops: it has no helper for the script tag. We write the `<script type="module">` tag by hand. This is glue that the module doesn't provide yet. We pin an exact release from the jsDelivr CDN, so that the page keeps working the way this tutorial describes:

```scala mdoc:silent
val datastarVersion = "1.0.4"

val datastarScript: Dom.Element =
  script(
    `type` := "module",
    src    := s"https://cdn.jsdelivr.net/gh/starfederation/datastar@v$datastarVersion/bundles/datastar.js"
  )

def page(heading: String)(content: Dom.Element*): Dom.Element =
  html(lang := "en")(
    head(meta(charset := "utf-8"), title(heading), datastarScript),
    body(h1(heading), content)
  )
```

The code above:

- `datastarScript` is a `script` element with `type="module"` that loads the pinned Datastar bundle from the CDN
- `page` wraps any content in an `html` document whose `head` holds a `meta` charset, the `title` and the script
- `body(h1(heading), content)` puts the heading above the content we pass in

Serve `page("Weather Station")(counter)` as `text/html` and the browser shows `Readings taken: 0`. Nothing can change the signal yet; that is the next step.

## 4. A Button That Posts

An event attribute turns a click into a request. `dataOn.click := js"@post('/reading')"` renders `data-on:click="@post('/reading')"`, where `@post` is a Datastar action that sends the signals to the server. The `js"..."` interpolator marks the text as a Datastar expression; the module rejects a plain `String` in that position, so a typo cannot turn into a literal. To add the button to the counter:

```scala mdoc:silent
val withButton = div(
  dataSignals(readings := 0),
  p("Readings taken: ", span(dataText := readings)),
  button(dataOn.click := js"@post('/reading')", "Take a reading")
)
```

The code above:

- `dataSignals(readings := 0)` declares the signal with its first value
- `dataText := readings` shows the signal in the `span`
- `dataOn.click := js"@post('/reading')"` posts the signals to `/reading` on each click

To see the attribute the button renders:

```scala mdoc
println(withButton.renderMinified)
```

When the reader clicks, Datastar sends `POST /reading` with the JSON body `{"readings":0}`, the `Content-Type: application/json` header and a `Datastar-Request: true` header. The server answers with a patch. `DatastarEvent.patchSignals` takes the same `signal := value` pairs as `dataSignals`, and `DatastarEvent#renderSSE` renders the event to its wire text:

```scala mdoc
println(DatastarEvent.patchSignals(readings := 1).renderSSE)
```

The event has a name, `datastar-patch-signals`, and one `data:` line with the key `signals` followed by the JSON. The blank line after it ends the event. The browser merges the JSON into its signals, and `data-text` updates the span.

An event is only text; the response that carries it must have the content type `text/event-stream`. The media type registry has no constant for it, so we build the `ContentType` ourselves, and a small helper turns a stream of rendered events into a `Response`. This is the second piece of glue:

```scala mdoc:silent
import java.nio.charset.StandardCharsets
import zio.blocks.chunk.Chunk
import zio.blocks.mediatype.MediaType
import zio.blocks.streams.Stream
import zio.http._

val eventStream: ContentType = ContentType(MediaType("text", "event-stream"))

def eventResponse(events: Stream[Nothing, String]): Response = {
  val bytes = events.flatMap(event => Stream.fromChunk(Chunk.fromArray(event.getBytes(StandardCharsets.UTF_8))))
  Response.ok(Body.fromStream(bytes, eventStream)).addHeader(Header.CacheControl.NoCache)
}

def eventResponse(first: String, rest: String*): Response =
  eventResponse(Stream.fromIterable(first +: rest))
```

The code above:

- `eventResponse(events)` renders nothing itself: each string is the `DatastarEvent#renderSSE` text of one event, and the helper encodes it as UTF-8, the only encoding SSE allows
- `Body.fromStream` makes a streaming body, and `Cache-Control: no-cache` keeps proxies from caching it
- The second overload sends a few events and ends, which is all a button click needs

The first server counts the clicks in memory, which is enough to see the loop work. `Step2CounterExample` serves the page and answers `POST /reading` with `eventResponse(DatastarEvent.patchSignals(readings := taken.incrementAndGet()).renderSSE)`. Its test client sends the click twice, and the output is:

```text
POST /reading -> 200 [text/event-stream]
event: datastar-patch-signals
data: signals {"readings":1}


POST /reading -> 200 [text/event-stream]
event: datastar-patch-signals
data: signals {"readings":2}
```

The server's counter works, but it is a second copy of a value the browser already has. In the next step the server reads the signals instead.

## 5. Reading the Signals

The browser sends all of its signals with every request, so the server can be stateless: it reads the current value, computes the next one and sends it back. This is where the library has a gap: it has no `readSignals`. We decode the JSON ourselves with the schema JSON codec into a case class whose fields are the signal names:

```scala mdoc:silent
import zio.blocks.schema.Schema

final case class Signals(readings: Int, city: String)

object Signals {
  implicit val schema: Schema[Signals] = Schema.derived
}
```

The code above gives `Signals` one field per signal the server reads, with the same names as the page's signals, and `Schema.derived` builds the schema from the case class. `schema.jsonCodec` is the JSON codec for it. A `POST` carries the JSON in the body, so decoding is one call:

```scala mdoc
println(Signals.schema.jsonCodec.decode("""{"readings":4,"city":"Oslo"}"""))
```

The browser sends every signal on the page, not only the ones the server cares about, and the codec ignores fields the case class doesn't have:

```scala mdoc
println(Signals.schema.jsonCodec.decode("""{"readings":4,"city":"Oslo","extra":true}"""))
```

A wrong type is an error, and the codec reports where:

```scala mdoc
println(Signals.schema.jsonCodec.decode("""{"readings":"four"}"""))
```

A `GET` request carries the same JSON in the `datastar` query parameter, so one helper reads both. This is the third piece of glue:

```scala mdoc:silent
def readSignals[A](request: Request)(implicit schema: Schema[A]): Either[String, A] = {
  val json =
    if (request.method == Method.GET) request.url.queryParams.getFirst("datastar").getOrElse("")
    else request.body.asString()
  schema.jsonCodec.decode(json).left.map(_.toString)
}
```

The code above:

- A `GET` takes the JSON from the `datastar` query parameter and every other method reads the request body
- `schema.jsonCodec.decode` returns the decoded value or an error
- `.left.map(_.toString)` turns the error into text, which the route turns into a `400 Bad Request`

Here is the helper reading a `GET` request whose query parameter holds the URL-encoded JSON:

```scala mdoc:silent
val getRequest =
  Request.get(URL.parse("/dashboard?datastar=%7B%22readings%22%3A7%2C%22city%22%3A%22Lisbon%22%7D").toOption.get)
```

The code above builds a `GET` request for `/dashboard` whose `datastar` parameter holds the URL-encoded JSON `{"readings":7,"city":"Lisbon"}`. To call the helper on that request:

```scala mdoc
println(readSignals[Signals](getRequest))
```

The route for the button is now stateless. It decodes `Signals`, adds one to `readings` and sends the patch back, and it answers `400` with the error text when the JSON doesn't decode. `Step3DecodeSignalsExample` serves it and adds a city picker: `dataBind(city)` on a `select` keeps the `city` signal equal to the chosen option. Its test client sends a good click, a click with an extra field, a click with a wrong type and a `GET`, and the output is:

```text
POST /reading -> 200 [text/event-stream]
event: datastar-patch-signals
data: signals {"readings":5}


POST /reading -> 200 [text/event-stream]
event: datastar-patch-signals
data: signals {"readings":5}


POST /reading -> 400 [text/plain; charset=UTF-8]
zio.blocks.schema.SchemaError: illegal number at: .readings
Right(Signals(7,Lisbon))
```

## 6. Swapping a Fragment

A signal patch changes values, and `data-text` turns them into text. To change the structure of the page, the server sends HTML. `DatastarEvent.patchElements` takes an element and, by default, replaces the element on the page that has the same `id`:

```scala mdoc:silent
final case class Reading(city: String, tenthsCelsius: Int, windKmh: Int, humidity: Int) {
  def celsius: String = String.format(java.util.Locale.ROOT, "%.1f", Double.box(tenthsCelsius / 10.0))
}

def latest(reading: Reading): Dom.Element =
  p(
    id := "latest",
    s"${reading.city}: ${reading.celsius} °C, wind ${reading.windKmh} km/h, humidity ${reading.humidity}%"
  )
```

The code above:

- `Reading` holds one city's numbers, with the temperature in tenths of a degree so that it stays an `Int`
- `Reading#celsius` formats the tenths as text such as `3.6`, always with a dot
- `latest` builds a paragraph with `id := "latest"`, the id that the next patch replaces

The paragraph keeps its `id`, so the next patch finds it again. Here is the event for a reading in Oslo:

```scala mdoc
println(DatastarEvent.patchElements(latest(Reading("Oslo", 36, 12, 73))).renderSSE)
```

The data line has the key `elements` followed by the minified HTML. When the target isn't the element's own id, `PatchElementsBuilder#selector` names it, and `PatchElementsBuilder#mode` says where the content lands, for example `ElementPatchMode.Append` to add to a list. Each of them adds its own `data:` line before the `elements` line, and the default mode, `ElementPatchMode.Outer`, adds none. Here the paragraph has no id, so we name the target with `PatchElementsBuilder#selector` and ask for `ElementPatchMode.Inner`, which replaces the target's content:

```scala mdoc
println(
  DatastarEvent
    .patchElements(p("Oslo: 3.6 °C"))
    .selector(CssSelector.id("latest"))
    .mode(ElementPatchMode.Inner)
    .renderSSE
)
```

One response can carry several events. The click now answers with a counter patch and a fragment patch, one after the other, and `eventResponse(first, rest*)` sends them both:

```scala mdoc:silent
val answer = eventResponse(
  DatastarEvent.patchSignals(readings := 5).renderSSE,
  DatastarEvent.patchElements(latest(Reading("Oslo", 36, 12, 73))).renderSSE
)
```

The code above:

- Each argument is the `DatastarEvent#renderSSE` text of one event
- `eventResponse(first, rest*)` sends the two events in order and ends the response

To read the response body of the two events:

```scala mdoc
print(answer.body.asString())
```

`Step4PatchElementsExample` serves this route, and the weather model behind it gives every city a reading for each tick (see `Weather.scala` in the examples). Its client posts `{"readings":4,"city":"Oslo"}` and prints the same two events.

:::caution
`DatastarEvent.patchElements` renders the HTML on one line, but a line break inside the HTML, such as the text of a `<pre>` element, is split across several `data:` lines, and only the first of them carries the `elements` key. In a real browser the rest of the text is dropped. This is a known bug in 0.0.56, so keep line breaks out of the elements you patch.
:::

## 7. A Live Dashboard

The click-driven patches come from requests the reader makes. A dashboard needs the opposite: the server pushes new numbers without being asked. We build it with one open stream: the page sends one request when it loads, and the server keeps that response open, writing a new event whenever it has one. A stream is one request for the whole session, and the server decides when to send, so the page never repeats a request and never resends its signals just to ask for news. The price of the stream is that the server holds a connection, and in our JDK adapter a thread, for as long as the page is open.

The page starts the stream with `dataInit`, which runs its expression once when Datastar first sees the element, and it contains the first version of the dashboard:

```scala mdoc:silent
def dashboard(rows: Seq[Reading]): Dom.Element = {
  val header = tr(th("City"), th("Temperature"), th("Wind"), th("Humidity"))
  val body   = rows.map(r => tr(td(r.city), td(s"${r.celsius} °C"), td(s"${r.windKmh} km/h"), td(s"${r.humidity}%")))
  table(id := "dashboard", (header +: body)*)
}

val live = div(dataInit := js"@get('/dashboard/stream')", dashboard(Seq(Reading("Oslo", 40, 14, 71))))
```

The code above:

- `dashboard` builds a `table` with `id := "dashboard"`, one header row and one row per reading
- `dataInit := js"@get('/dashboard/stream')"` runs the request once when Datastar first sees the `div`
- `live` wraps the first version of the table in that `div`

To render the element and see the attribute and the first table:

```scala mdoc
println(live.renderMinified)
```

The table has an `id`, so each `DatastarEvent.patchElements` event replaces it whole. The `dashboard` function takes the rows to show; `Weather.readings(tick)` from the examples supplies a row per city for every tick.

The stream itself is a `Stream` of rendered events, one per second. The approach is the one from [How to Send Server-Sent Events with http-model](./send-server-sent-events.md): `Stream#tapEach` runs a side effect for each element before the element moves on, so a sleep inside it makes the server write an event only when the pause is over. Here it is with no pause, cut to two events so that we can read it:

```scala mdoc:silent
def ticks(intervalMillis: Long): Stream[Nothing, String] =
  Stream
    .fromIterable(LazyList.from(1))
    .tapEach { _ =>
      Thread.sleep(intervalMillis)
      ()
    }
    .map(tick => DatastarEvent.patchElements(dashboard(Seq(Reading("Oslo", 40 + tick, 14, 71)))).renderSSE)

val firstTwoTicks = eventResponse(ticks(0).take(2))
```

The code above:

- `Stream#tapEach` runs its function for each element, and the `Thread.sleep(intervalMillis)` inside that function makes each element wait
- `Stream#map` turns each tick into a `DatastarEvent.patchElements` event for the table
- `Stream#take` with `2` ends the otherwise endless stream so that we can print it

To read the first two events from the response body:

```scala mdoc
print(firstTwoTicks.body.asString())
```

The stream has no end: `LazyList.from(1)` counts up for as long as somebody reads it, and the response stays open until the client leaves.

When the browser goes away, a closed tab doesn't tell the server anything. The server finds out the next time it writes: the write fails with an `IOException`, `Stream#run` stops pulling the stream and closes it, and the streaming adapter closes the exchange. A finalizer registered with `Stream#ensuring` runs at that moment, which is the place to release anything the dashboard holds. `Step5LiveDashboardExample` registers one that prints how many events were sent, and its client reads two events and then disconnects:

```text
event: datastar-patch-elements
data: elements <table id="dashboard"><tr><th>City</th><th>Temperature</th><th>Wind</th><th>Humidity</th></tr><tr><td>Oslo</td><td>4.6 °C</td><td>17 km/h</td><td>68%</td></tr><tr><td>Lisbon</td><td>19.1 °C</td><td>16 km/h</td><td>66%</td></tr><tr><td>Nairobi</td><td>23.2 °C</td><td>12 km/h</td><td>54%</td></tr></table>

event: datastar-patch-elements
data: elements <table id="dashboard"><tr><th>City</th><th>Temperature</th><th>Wind</th><th>Humidity</th></tr><tr><td>Oslo</td><td>3.8 °C</td><td>13 km/h</td><td>72%</td></tr><tr><td>Lisbon</td><td>20.5 °C</td><td>23 km/h</td><td>59%</td></tr><tr><td>Nairobi</td><td>22.4 °C</td><td>8 km/h</td><td>58%</td></tr></table>

client: disconnected
server: stream closed after 4 events
```

The server writes two more events than the client reads before it notices the disconnect: those are the events in flight when the client closes the connection.

## 8. Putting It Together

This example combines everything we've learned into one file. It serves one page with the city picker, the counter button, the "latest reading" paragraph and the live dashboard, and it keeps running until we stop it:

```scala mdoc:embed:datastar-examples/src/main/scala/buildalivedashboardwithdatastar/CompleteExample.scala
```

The pieces are the ones from the previous sections: the signals and the button from sections 3 and 4, the `Signals` decoding from section 5, the `latest` fragment from section 6, and the `dashboard` table and the `ticks` stream from section 7. It differs from the sections in two ways: the page holds the counter and the dashboard together, and `main` always starts the server, on port 8080 or the port given as the first argument.

Run it and open `http://localhost:8080/` in a browser. The dashboard fills in right away and its numbers change every second. Pick "Lisbon" and press "Take a reading": the counter goes up and the paragraph shows Lisbon's reading, while the dashboard keeps updating and the picker still says "Lisbon". Open the browser's developer tools on the Network tab: you see one `POST /reading` per click, with the body `{"readings":0,"city":"Oslo"}` and the header `Datastar-Request: true`, and one `GET /dashboard/stream?datastar=...` that never finishes.

## 9. Running the Examples

All examples in this tutorial have corresponding runnable Scala files in the `datastar-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/datastar-examples
```

Every step serves its page on the port you pass as the first argument, for example `sbt "runMain buildalivedashboardwithdatastar.Step1StaticPageExample 8080"`, and keeps running until you press Ctrl+C, so you can open it in a browser. Without an argument a step starts a server on a free port, sends the requests from its section with a small test client and prints what the server answers. The complete example always serves, on port 8080 unless you pass another. The steps share the weather model in `Weather.scala`, the glue in `Glue.scala` and the test client in `Client.scala`. The server adapters in `JdkHttp.scala` and `StreamingJdkHttp.scala` are copied from [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md) and [How to Send Server-Sent Events with http-model](./send-server-sent-events.md).

### Share the Weather Model and the Glue

Every step imports these files. They have no `main`, so there is nothing to run; read them first.

<details>
  <summary>datastar-examples/src/main/scala/buildalivedashboardwithdatastar/Weather.scala</summary>

```scala mdoc:embed:datastar-examples/src/main/scala/buildalivedashboardwithdatastar/Weather.scala:show-line-numbers
```

</details>

<details>
  <summary>datastar-examples/src/main/scala/buildalivedashboardwithdatastar/Glue.scala</summary>

```scala mdoc:embed:datastar-examples/src/main/scala/buildalivedashboardwithdatastar/Glue.scala:show-line-numbers
```

</details>

### A Page with a Signal

This example serves the page from section 3 and prints the page the server returns.

<details>
  <summary>datastar-examples/src/main/scala/buildalivedashboardwithdatastar/Step1StaticPageExample.scala</summary>

```scala mdoc:embed:datastar-examples/src/main/scala/buildalivedashboardwithdatastar/Step1StaticPageExample.scala:show-line-numbers
```

</details>

Run it from the `datastar-examples` directory:

```bash
sbt "runMain buildalivedashboardwithdatastar.Step1StaticPageExample"
```

### A Button That Posts

This example adds the button and a server that counts the clicks.

<details>
  <summary>datastar-examples/src/main/scala/buildalivedashboardwithdatastar/Step2CounterExample.scala</summary>

```scala mdoc:embed:datastar-examples/src/main/scala/buildalivedashboardwithdatastar/Step2CounterExample.scala:show-line-numbers
```

</details>

Run it from the `datastar-examples` directory:

```bash
sbt "runMain buildalivedashboardwithdatastar.Step2CounterExample"
```

### Reading the Signals

This example decodes the signals into `Signals` and answers with the next value.

<details>
  <summary>datastar-examples/src/main/scala/buildalivedashboardwithdatastar/Step3DecodeSignalsExample.scala</summary>

```scala mdoc:embed:datastar-examples/src/main/scala/buildalivedashboardwithdatastar/Step3DecodeSignalsExample.scala:show-line-numbers
```

</details>

Run it from the `datastar-examples` directory:

```bash
sbt "runMain buildalivedashboardwithdatastar.Step3DecodeSignalsExample"
```

### Swapping a Fragment

This example answers a click with a `DatastarEvent.patchSignals` event and a `DatastarEvent.patchElements` event.

<details>
  <summary>datastar-examples/src/main/scala/buildalivedashboardwithdatastar/Step4PatchElementsExample.scala</summary>

```scala mdoc:embed:datastar-examples/src/main/scala/buildalivedashboardwithdatastar/Step4PatchElementsExample.scala:show-line-numbers
```

</details>

Run it from the `datastar-examples` directory:

```bash
sbt "runMain buildalivedashboardwithdatastar.Step4PatchElementsExample"
```

### A Live Dashboard

This example streams the dashboard and shows the server noticing a disconnect.

<details>
  <summary>datastar-examples/src/main/scala/buildalivedashboardwithdatastar/Step5LiveDashboardExample.scala</summary>

```scala mdoc:embed:datastar-examples/src/main/scala/buildalivedashboardwithdatastar/Step5LiveDashboardExample.scala:show-line-numbers
```

</details>

Run it from the `datastar-examples` directory:

```bash
sbt "runMain buildalivedashboardwithdatastar.Step5LiveDashboardExample"
```

### The Complete Example

The complete example from "Putting It Together" is a single file with everything in it:

```bash
sbt "runMain buildalivedashboardwithdatastar.CompleteExample"
```

To compile every example at once without running them:

```bash
sbt compile
```

## 10. What You've Learned

In this tutorial, you learned:

- That Datastar keeps named signals in the browser, and that `dataSignals` and `dataText` declare and show a signal on a page built with the HTML DSL
- How `dataOn.click := js"@post('/reading')"` sends the signals to the server, and how `DatastarEvent.patchSignals` answers with new values over a `text/event-stream` response
- How to decode the signals in a `POST` body, or in the `datastar` query parameter of a `GET`, into a case class with the schema JSON codec
- How `DatastarEvent.patchElements` replaces the element with the same `id`, and how `PatchElementsBuilder#selector` and `PatchElementsBuilder#mode` change where the content lands
- How one open `GET` stream pushes a new table every second, and how `Stream#ensuring` tells you when the browser has gone away

You also saw the three pieces of glue the library doesn't provide yet: the `<script type="module">` tag, a `readSignals` helper, and a `Response` that streams Datastar events. You now have a solid foundation in building reactive pages with Datastar and ZIO Blocks.

## 11. Where to Go Next

- **Want every attribute and event?** Read the [Datastar](../reference/datastar/index.md) reference, then [Signals](../reference/datastar/signals.md), [Attributes](../reference/datastar/attributes.md), [Event Handlers](../reference/datastar/events.md) and [Server-Sent Events](../reference/datastar/sse.md).
- **Want to see the other approach to server-driven pages?** [Add Interactivity to a Page with HTMX](./add-interactivity-with-htmx.md) builds a live search with htmx, where the server sends HTML fragments only.
- **Want more on streaming events?** [How to Send Server-Sent Events with http-model](./send-server-sent-events.md) covers event ids, resuming with `Last-Event-ID` and disconnects.
- **Want to learn streams first?** [Your First Streaming Pipeline](./your-first-streaming-pipeline.md) covers creating and running a `Stream`.
- **Want more on serving?** [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md) explains the adapter the examples use and its limits.

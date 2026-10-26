---
id: send-server-sent-events
title: "How to Send Server-Sent Events with http-model"
description: "Stream Server-Sent Events from a ZIO Blocks http-model Response through the JDK HTTP server to curl and the browser's EventSource."
keywords:
  - "Server-Sent Events"
  - "Event Streaming"
  - "EventSource"
  - "ServerSentEvent"
  - "Body"
  - "Last-Event-ID"
---

In this guide, we build a "new arrivals" feed for a Scala bookshop that pushes one book at a time to every open browser tab, using `ServerSentEvent` and `Body.fromStream` from the ZIO Blocks [HTTP model](../reference/http-model/index.md). By the end, you will have a `text/event-stream` endpoint that streams each event the moment it is produced, resumes where a reconnecting client left off, and notices when a client goes away, plus a small HTML page that shows the books as they arrive.

We start from one event on the wire, turn a stream of events into a `Response`, serve it through a streaming version of the adapter from [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md), and then add event ids, resuming, a browser client, and disconnect handling.

## The Problem

With Server-Sent Events (SSE) the server keeps one connection open and writes small text messages made of `event:`, `id:`, `retry:` and `data:` lines, which the browser's built-in `EventSource` reads and reconnects on by itself. The wire format looks simple enough to write by hand, but it is line-based, and every line break in a value changes its meaning. A server also has to send each event as soon as it exists; a body that is collected in full before it is sent never reaches the client while the feed is still running.

For example, a first attempt at formatting an arrival might look like this:

```scala mdoc:compile-only
def formatArrival(id: String, title: String, authors: String): String =
  s"event: arrival\nid: $id\ndata: $title\n$authors\n\n"
```

For the book `Zionomicon`, the authors line has no `data:` prefix, so `EventSource` reads `John A. De Goes, Adam Fraser and Milad Khajavi` as the name of an unknown field and drops it: the browser receives only the title. An id with a line break in it would inject a field of its own. Even with the text right, the [JDK server adapter](./serve-http-model-requests.md) collects the whole body with `Body#toArray` before it sends anything, so a client of a feed that never ends receives nothing at all, not even the status line.

In this guide, we let `ServerSentEvent` produce the wire text, stream it with `Body.fromStream`, and change the adapter so it writes each event as soon as it is rendered.

## Prerequisites

Add the HTTP model to your build. It brings the streams module with it:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-http-model" % "@VERSION@"

// Scala 3.9 and newer
libraryDependencies += "dev.zio" %% "zio-blocks-next-http-model" % "@VERSION@"
scalacOptions += "-experimental"
```

The code in this guide is the same on Scala 2.13 and Scala 3. The HTTP model depends on the ZIO Blocks streams module, which is compiled for Java 21, so run the program on **JDK 21 or newer**. The server parts use `com.sun.net.httpserver`, so they are JVM only.

You should have read [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md), because we reuse its adapter. For streams, [Your First Streaming Pipeline](./your-first-streaming-pipeline.md) covers creating and running a `Stream`.

We use these imports throughout the guide:

```scala mdoc:silent
import java.nio.charset.StandardCharsets
import zio.blocks.chunk.Chunk
import zio.blocks.mediatype.MediaType
import zio.blocks.streams.Stream
import zio.http._
```

## The New-Arrivals Feed

The bookshop announces six Scala books. Each book becomes one event named `arrival`, whose id is the book's position in the list and whose data has two lines, the title and the authors:

```scala mdoc:silent
final case class Book(title: String, authors: String)

val books: Vector[Book] = Vector(
  Book("Zionomicon", "John A. De Goes, Adam Fraser and Milad Khajavi"),
  Book("Programming in Scala", "Martin Odersky, Lex Spoon, Bill Venners and Frank Sommers"),
  Book("Functional Programming in Scala", "Michael Pilquist, Rúnar Bjarnason and Paul Chiusano"),
  Book("Scala with Cats", "Noel Welsh and Dave Gurnell"),
  Book("Hands-on Scala Programming", "Li Haoyi"),
  Book("Essential Effects", "Adam Rosien")
)

def arrival(position: Int, book: Book): ServerSentEvent[String] =
  ServerSentEvent(s"${book.title}\n${book.authors}", "arrival").id(position.toString)

def arrivalsAfter(lastSeen: Int): Vector[ServerSentEvent[String]] =
  books.zipWithIndex.drop(lastSeen).map { case (book, index) => arrival(index + 1, book) }
```

`arrivalsAfter(n)` returns the events for every book after the first `n`. We use it later to resume a feed.

## Rendering One Event

`ServerSentEvent[A]` is an immutable value: the data plus the optional `event`, `id` and `retry` fields. `ServerSentEvent#render` turns it into the exact text that goes on the wire, ending with the blank line that marks the end of an event. The simplest event has only data:

```scala mdoc
ServerSentEvent("Zionomicon").render == "data: Zionomicon\n\n"
```

`ServerSentEvent.apply` takes an optional event name, and `ServerSentEvent#id` and `ServerSentEvent#retry` add the other fields. They are always rendered in the order `event`, `id`, `retry`, `data`:

```scala mdoc
ServerSentEvent("Zionomicon", "arrival").id("1").retry(2000).render ==
  "event: arrival\nid: 1\nretry: 2000\ndata: Zionomicon\n\n"
```

Data is rendered through an implicit `SseDataEncoder[A]`. The encoder for `String` splits the text at every `\n`, `\r` or `\r\n` and writes each piece on its own `data:` line, which is exactly what our two-line arrival needs:

```scala mdoc
println(arrival(1, books.head).render)
```

`EventSource` joins the `data:` lines back together with `\n`, so the browser sees the title and the authors separated by a line break. The other fields must fit on one line, and `ServerSentEvent#event` and `ServerSentEvent#id` throw an `IllegalArgumentException` for a value that contains a line break:

```scala mdoc
val rejected = scala.util.Try(ServerSentEvent("Zionomicon").id("1\n2"))
```

`ServerSentEvent#retry` likewise rejects a negative number. To send a payload of your own type, such as JSON, write an `SseDataEncoder` instance that returns its lines; the [Server-Sent Events reference](../reference/http-model/server-sent-event.md) shows how.

## Turning a Stream of Events into a Response

An SSE response is an ordinary `Response`: status `200 OK`, the content type `text/event-stream`, and a body that is a stream of rendered events. The media type registry has no constant for `text/event-stream`, so we build the `ContentType` from a `MediaType`:

```scala mdoc:silent
val eventStream: ContentType = ContentType(MediaType("text", "event-stream"))
```

`Body.fromStream` takes a `Stream[Nothing, Byte]`, a byte stream that cannot fail, and a content type. We render each event and encode its text as UTF-8, which is the only encoding SSE allows. `Cache-Control: no-cache` keeps proxies and the browser from caching the feed:

```scala mdoc:silent
def encode(events: Stream[Nothing, ServerSentEvent[String]]): Stream[Nothing, Byte] =
  events.flatMap(event => Stream.fromChunk(Chunk.fromArray(event.render.getBytes(StandardCharsets.UTF_8))))

def eventResponse(events: Stream[Nothing, ServerSentEvent[String]]): Response =
  Response.ok(Body.fromStream(encode(events), eventStream)).addHeader(Header.CacheControl.NoCache)
```

`Response.ok` sets the `content-type` header from the body. Nothing runs yet: a `Stream` is a description, and the events are produced only when the server pulls the body. To see the result without a server, we build a response for the first two books and read its body back in the same program:

```scala mdoc:silent
val firstTwo = eventResponse(Stream.fromIterable(arrivalsAfter(0).take(2)))
```

Reading the body runs the stream to the end, which is fine for a finite stream:

```scala mdoc
println(s"status: ${firstTwo.status.code}")
firstTwo.headers.toList.foreach { case (name, value) => println(s"$name: $value") }
println(s"known body length: ${firstTwo.body.length}")
print(firstTwo.body.asString())
```

The body length is `None` because the body is built with `Stream#flatMap`, so its length is not known until the stream has been read. That is why an SSE response is sent with chunked transfer encoding instead of a `Content-Length` header.

## Sending One Book at a Time

A real feed produces events over time. The streams module has no built-in timer, so the simplest honest way to pace a feed is to block before each element with `Stream#tapEach`:

```scala mdoc:silent
def paced(events: Vector[ServerSentEvent[String]], intervalMillis: Long): Stream[Nothing, ServerSentEvent[String]] =
  Stream.fromIterable(events).tapEach(_ => Thread.sleep(intervalMillis))
```

`Thread.sleep` runs on the thread that pulls the stream, which is the server thread writing the response. Every open feed therefore holds one thread for as long as it stays open. A feed that never ends is the same shape with an endless source, such as `Stream.fromIterator(Iterator.continually(...))`. In a real application, the source would be a queue that new arrivals are put into, and pulling the next element would block on that queue instead of sleeping.

## Streaming the Response Through the JDK Server

The `JdkHttp` adapter cannot serve this response. `JdkHttp.writeResponse` calls `Body#toArray`, which pulls the whole stream before it writes a single byte, and the JDK server without an executor runs every exchange on one dispatcher thread, so an open feed blocks every other request. Served through that adapter, an endless feed sends its client no bytes at all, not even the status line; a request to another path waits until the feed ends; and the server keeps producing events after the client has left, because it never writes and so never learns that the connection is gone.

The streaming adapter, `StreamingJdkHttp`, reuses `JdkHttp.toRequest` and `JdkHttp.writeResponse` from the original adapter and changes three things for `text/event-stream` responses. The first change writes the body as the stream produces it. A length of `0` passed to `HttpExchange#sendResponseHeaders` tells the JDK server that the length is unknown, so it sends the headers at once and uses chunked transfer encoding. `Stream#run` with `Sink.fromOutputStream` then pulls the stream and writes each byte as it arrives:

```scala mdoc:silent
import com.sun.net.httpserver.HttpExchange
import java.io.{FilterOutputStream, OutputStream}
import zio.blocks.streams.Sink

final class FlushPerEvent(out: OutputStream) extends FilterOutputStream(out) {
  private var previous = -1

  override def write(b: Int): Unit = {
    out.write(b)
    if (b == '\n' && previous == '\n') out.flush()
    previous = b
  }
}

def writeEventStream(exchange: HttpExchange, response: Response): Unit = {
  val out = exchange.getResponseHeaders
  response.headers.toList.foreach { case (name, value) =>
    if (name != "content-length" && name != "transfer-encoding") out.add(name, value)
  }
  if (!response.headers.has("content-type")) out.set("content-type", response.body.contentType.render)
  exchange.sendResponseHeaders(response.status.code, 0L)
  response.body.stream.run(Sink.fromOutputStream(new FlushPerEvent(exchange.getResponseBody)))
  ()
}
```

The second change is `FlushPerEvent`. The JDK's response stream buffers what we write, so without a flush the first events would sit in the buffer. `FlushPerEvent` flushes at every blank line, the end of an event as `ServerSentEvent#render` writes it, so each event leaves in its own chunk. The third change gives the server an executor, so each open feed runs on its own thread and other requests are not blocked. The threads are daemon threads, so an open feed never keeps the JVM alive after `main` returns:

```scala mdoc:compile-only
import com.sun.net.httpserver.HttpServer
import java.util.concurrent.{Executors, ThreadFactory}

val daemonThreads: ThreadFactory = runnable => {
  val thread = new Thread(runnable, "sse-exchange")
  thread.setDaemon(true)
  thread
}

def useThreadPerExchange(server: HttpServer): Unit =
  server.setExecutor(Executors.newCachedThreadPool(daemonThreads))
```

The handler sends `text/event-stream` responses through `writeEventStream` and every other response through `JdkHttp.writeResponse`, and treats an `IOException` while writing as the client having gone away. The whole adapter is about a hundred lines; it is embedded under [Running the Examples](#running-the-examples).

The live-feed example serves three books, one per second, first through `JdkHttp` and then through the streaming adapter, and its client prints when each event arrives. Its output, from one run:

```text
== JdkHttp (collects the body first)
GET /arrivals -> 200 [content-type: text/event-stream, cache-control: no-cache, transfer-encoding: none]
event: arrival
id: 1
data: Zionomicon
data: John A. De Goes, Adam Fraser and Milad Khajavi
-- event 1 received after 4.0 s
event: arrival
id: 2
data: Programming in Scala
data: Martin Odersky, Lex Spoon, Bill Venners and Frank Sommers
-- event 2 received after 4.0 s
event: arrival
id: 3
data: Functional Programming in Scala
data: Michael Pilquist, Rúnar Bjarnason and Paul Chiusano
-- event 3 received after 4.0 s
== Streaming adapter (StreamingJdkHttp)
GET /arrivals -> 200 [content-type: text/event-stream, cache-control: no-cache, transfer-encoding: chunked]
event: arrival
id: 1
data: Zionomicon
data: John A. De Goes, Adam Fraser and Milad Khajavi
-- event 1 received after 1.0 s
event: arrival
id: 2
data: Programming in Scala
data: Martin Odersky, Lex Spoon, Bill Venners and Frank Sommers
-- event 2 received after 2.0 s
event: arrival
id: 3
data: Functional Programming in Scala
data: Michael Pilquist, Rúnar Bjarnason and Paul Chiusano
-- event 3 received after 3.0 s
```

Through `JdkHttp` all three events arrive together, after the stream has ended. Through the streaming adapter they arrive one second apart.

To watch the feed from a terminal, start the complete example (see [Putting It Together](#putting-it-together)) and open the feed with `curl -N`, which turns off curl's output buffering. The events appear one every two seconds:

```bash
curl -N -i http://localhost:8080/arrivals
```

The first lines it prints:

```text
HTTP/1.1 200 OK
Date: Fri, 23 Oct 2026 19:28:05 GMT
Transfer-encoding: chunked
Content-type: text/event-stream
Cache-control: no-cache

event: arrival
id: 1
retry: 2000
data: Zionomicon
data: John A. De Goes, Adam Fraser and Milad Khajavi

event: arrival
id: 2
data: Programming in Scala
data: Martin Odersky, Lex Spoon, Bill Venners and Frank Sommers
```

The JDK server capitalizes only the first letter of each header name; HTTP header names are case-insensitive. The `retry:` line comes from the next section.

## Naming, Numbering and Resuming the Feed

The three metadata fields are what make a feed survive a dropped connection. The event name selects the listener on the client: an event with an `event:` field is delivered only to listeners for that name, not to `EventSource#onmessage`. The id is remembered by the client, and when `EventSource` reconnects it sends the last id it saw in a `Last-Event-ID` request header. The `retry:` field tells the client how many milliseconds to wait before it reconnects.

http-model has no typed codec for `Last-Event-ID`, so we read it with `Headers#rawGet`, which matches header names case-insensitively. The feed then streams only the books after that id, asks the client to wait two seconds before reconnecting, and answers `204 No Content` when the client already has every book, which tells `EventSource` to stop reconnecting:

```scala mdoc:silent
def lastSeen(request: Request): Int =
  request.headers.rawGet("last-event-id").flatMap(_.toIntOption).getOrElse(0)

def arrivals(request: Request, intervalMillis: Long): Response = {
  val remaining = arrivalsAfter(lastSeen(request))
  if (remaining.isEmpty) Response(Status.NoContent)
  else eventResponse(paced(remaining.updated(0, remaining.head.retry(2000)), intervalMillis))
}
```

The `204` matters for a finite feed. When the stream ends, the server closes the connection, and `EventSource` treats that like any other dropped connection: it reconnects. Without the `204`, a feed that replays from the start would send the same books again on every reconnect.

Because `arrivals` is a plain function, we can call it with a request that carries the header, without a server:

```scala mdoc
def call(lastEventId: String): Unit = {
  val request  = Request.get(URL.fromPath(Path("/arrivals"))).addHeader("last-event-id", lastEventId)
  val response = arrivals(request, intervalMillis = 0)
  println(s"status: ${response.status.code}")
  print(response.body.asString())
}

call("4")
call("6")
```

With `curl`, pass the header yourself to see the same resume against the running server:

```bash
curl -N -H 'Last-Event-ID: 4' http://localhost:8080/arrivals
```

It prints the last two books and exits when the server closes the stream:

```text
event: arrival
id: 5
retry: 2000
data: Hands-on Scala Programming
data: Li Haoyi

event: arrival
id: 6
data: Essential Effects
data: Adam Rosien
```

## Watching the Feed in a Browser

`EventSource` is built into every current browser, so the client needs no JavaScript library. The page opens the feed, adds a list item for each `arrival` event, and shows the connection state. We keep it as a Scala string so the server can send it:

```scala mdoc:silent
val page: String =
  """<!DOCTYPE html>
    |<html>
    |<head><meta charset="utf-8"><title>New arrivals</title></head>
    |<body>
    |<h1>New arrivals</h1>
    |<p id="status">Connecting...</p>
    |<ul id="books"></ul>
    |<script>
    |  const status = document.getElementById("status");
    |  const source = new EventSource("/arrivals");
    |  source.addEventListener("arrival", (event) => {
    |    const [title, authors] = event.data.split("\n");
    |    const item = document.createElement("li");
    |    item.textContent = title + " by " + authors;
    |    document.getElementById("books").append(item);
    |    status.textContent = "Last event id: " + event.lastEventId;
    |  });
    |  source.onerror = () => {
    |    status.textContent = source.readyState === EventSource.CLOSED ? "All books have arrived." : "Reconnecting...";
    |  };
    |</script>
    |</body>
    |</html>
    |""".stripMargin
```

`event.data` is the two `data:` lines joined with `\n`, and `event.lastEventId` is the `id:` field. `EventSource#onerror` fires both when the connection drops and when the browser gives up; `EventSource#readyState` tells the two apart. The application serves the page at `/` and the feed at `/arrivals`. `Body.fromString` would label the page `text/plain`, so we build the body from bytes with ``ContentType.`text/html` ``:

```scala mdoc:silent
def app(intervalMillis: Long): Request => Response = request =>
  (request.method, request.path.encode) match {
    case (Method.GET, "/") =>
      Response.ok(Body.fromArray(page.getBytes(StandardCharsets.UTF_8), ContentType.`text/html`))
    case (Method.GET, "/arrivals") => arrivals(request, intervalMillis)
    case _                         => Response.notFound
  }
```

Serving the page from the same server as the feed keeps the request same-origin; a feed on another origin needs CORS headers. Opened in Chromium against the complete example, the page lists the six books two seconds apart. When the stream ends, the browser reconnects once with `Last-Event-ID: 6`, receives the `204`, and the status line changes to "All books have arrived.". The server log of that visit:

```text
stream 5: opened after event 0
stream 5: closed
stream 6: the client has every book, 204
```

## Noticing When a Client Leaves

A client closes an event stream by closing the connection: the user closes the tab, or the page calls `EventSource#close`. The server finds out only when it next writes, and the write fails with an `IOException`. `Stream#run` then stops pulling the stream and closes it, so a finalizer registered with `Stream#ensuring` runs, and the streaming adapter's handler catches the `IOException` and closes the exchange. To see when that happens, the disconnect example counts the events it produces and logs when the stream closes:

```scala mdoc:silent
import java.util.concurrent.atomic.AtomicInteger

def countedFeed(request: Request): Response = {
  val produced = new AtomicInteger(0)
  val events   = paced(arrivalsAfter(0), intervalMillis = 1000)
    .tapEach(_ => produced.incrementAndGet())
    .ensuring(println(s"server: stream closed after producing ${produced.get} events"))
  eventResponse(events)
}
```

Its client reads two events and disconnects. The output:

```text
GET /arrivals -> 200 [content-type: text/event-stream, cache-control: no-cache, transfer-encoding: chunked]
event: arrival
id: 1
data: Zionomicon
data: John A. De Goes, Adam Fraser and Milad Khajavi
-- event 1 received after 2.2 s
event: arrival
id: 2
data: Programming in Scala
data: Martin Odersky, Lex Spoon, Bill Venners and Frank Sommers
-- event 2 received after 3.2 s
client: disconnected
server: stream closed after producing 4 events
```

The server produces four events, not three. The first write after the client leaves still succeeds, because the operating system accepts the bytes before the client's reset arrives; the second write fails. So the server notices a departed client within about two intervals, and until then the feed keeps its thread. A feed that can stay quiet for minutes should send a small event, such as one named `ping`, at a fixed interval, so a departed client is noticed in bounded time.

:::tip
Each open feed holds one thread. The cached thread pool in the streaming adapter creates a platform thread per open feed; on JDK 21 or newer, a virtual thread per exchange makes a blocked feed cheap:

```scala
server.setExecutor(Executors.newVirtualThreadPerTaskExecutor())
```
:::

## Putting It Together

The complete example serves the page at `/` and the resumable feed at `/arrivals` on port 8080, sends one book every two seconds, and logs when each stream opens and closes. It builds on the shared files embedded under Running the Examples below: the feed model `Bookshop.scala`, the streaming adapter `StreamingJdkHttp.scala`, and the page from `Step5BrowserPageExample.scala`. The complete example:

```scala mdoc:embed:http-model-examples/src/main/scala/sendserversentevents/CompleteExample.scala
```

While it runs, open `http://localhost:8080/` in a browser or watch the feed with `curl -N http://localhost:8080/arrivals`. A full `curl` run, a resumed one with `Last-Event-ID: 4`, a finished one with `Last-Event-ID: 6`, and a `curl` stopped after three seconds produce this log:

```text
Serving http://localhost:8080/ and http://localhost:8080/arrivals (Ctrl+C to stop)
stream 1: opened after event 0
stream 1: closed
stream 2: opened after event 4
stream 2: closed
stream 3: the client has every book, 204
stream 4: opened after event 0
stream 4: closed
```

## Running the Examples

All code from this guide is available as runnable examples in the `http-model-examples` directory. It is a standalone sbt build that uses the published ZIO Blocks artifacts. To run them, clone the repository and move into that directory:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/http-model-examples
```

The examples share three files of their own, the feed model, the streaming adapter and a test client, and use the adapter `JdkHttp.scala` from the serving guide, which lives in the same build:

<details>
  <summary>http-model-examples/src/main/scala/sendserversentevents/Bookshop.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/sendserversentevents/Bookshop.scala:show-line-numbers
```

</details>

<details>
  <summary>http-model-examples/src/main/scala/sendserversentevents/StreamingJdkHttp.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/sendserversentevents/StreamingJdkHttp.scala:show-line-numbers
```

</details>

<details>
  <summary>http-model-examples/src/main/scala/sendserversentevents/FeedClient.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/sendserversentevents/FeedClient.scala:show-line-numbers
```

</details>

The first example prints the wire text of a few events, with every line break shown as `\n`, and the message for a rejected id:

<details>
  <summary>http-model-examples/src/main/scala/sendserversentevents/Step1RenderEventExample.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/sendserversentevents/Step1RenderEventExample.scala:show-line-numbers
```

</details>

To run it:

```bash
sbt "runMain sendserversentevents.Step1RenderEventExample"
```

The second example builds the response for two books and reads it back without a server:

<details>
  <summary>http-model-examples/src/main/scala/sendserversentevents/Step2EventResponseExample.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/sendserversentevents/Step2EventResponseExample.scala:show-line-numbers
```

</details>

To run it:

```bash
sbt "runMain sendserversentevents.Step2EventResponseExample"
```

The third example serves the paced feed through both adapters and prints when each event arrives:

<details>
  <summary>http-model-examples/src/main/scala/sendserversentevents/Step3LiveFeedExample.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/sendserversentevents/Step3LiveFeedExample.scala:show-line-numbers
```

</details>

To run it:

```bash
sbt "runMain sendserversentevents.Step3LiveFeedExample"
```

The fourth example resumes the feed from `Last-Event-ID: 4`, then asks again with `Last-Event-ID: 6` and gets `204`:

<details>
  <summary>http-model-examples/src/main/scala/sendserversentevents/Step4ResumeExample.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/sendserversentevents/Step4ResumeExample.scala:show-line-numbers
```

</details>

To run it:

```bash
sbt "runMain sendserversentevents.Step4ResumeExample"
```

The fifth example serves the browser page and the feed on port 8080 until you stop it with Ctrl+C; pass another port as the first argument if 8080 is taken:

<details>
  <summary>http-model-examples/src/main/scala/sendserversentevents/Step5BrowserPageExample.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/sendserversentevents/Step5BrowserPageExample.scala:show-line-numbers
```

</details>

To run it, then open `http://localhost:8080/`:

```bash
sbt "runMain sendserversentevents.Step5BrowserPageExample"
```

The sixth example disconnects after two events and shows when the server notices:

<details>
  <summary>http-model-examples/src/main/scala/sendserversentevents/Step6DisconnectExample.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/sendserversentevents/Step6DisconnectExample.scala:show-line-numbers
```

</details>

To run it:

```bash
sbt "runMain sendserversentevents.Step6DisconnectExample"
```

To run the complete example, which also serves on port 8080 until you stop it:

```bash
sbt "runMain sendserversentevents.CompleteExample"
```

To check that all of the examples compile without running any of them:

```bash
sbt compile
```

## Going Further

The feed in this guide is deliberately small. These are the next things you are likely to need:

- **Typed payloads**: An `SseDataEncoder` for your own type can render JSON into the `data:` lines; see the [Server-Sent Events reference](../reference/http-model/server-sent-event.md).
- **A real event source**: Replace the paced `Vector` with a stream that blocks on a queue, so events are sent when they happen rather than on a timer.
- **Reading streams**: [How to Stream Files and Sockets Without Leaking Resources](./stream-files-and-sockets.md) covers `Stream#ensuring` and resource safety in more depth.
- **Production traffic**: The JDK server is fine for tools and examples. For production, hand the same `Response` values to a full HTTP server library that streams bodies natively.

Datastar, the hypermedia framework, also uses `text/event-stream` responses on the server side, so the techniques in this guide carry over to it. For the types used here, see the [Server-Sent Events reference](../reference/http-model/server-sent-event.md), the [HTTP model reference](../reference/http-model/model.md) and the [headers reference](../reference/http-model/headers.md).

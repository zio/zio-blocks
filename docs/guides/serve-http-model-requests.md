---
id: serve-http-model-requests
title: "How to Serve http-model Requests with the JDK HTTP Server"
description: "Serve a Request => Response function built on ZIO Blocks http-model with the HTTP server that ships with the JDK."
keywords:
  - "HTTP Server"
  - "JDK HttpServer"
  - "Request Handling"
  - "Request"
  - "Response"
  - "Body"
---

In this guide, we serve a small bookstore over HTTP using the ZIO Blocks [HTTP model](../reference/http-model/index.md) and the HTTP server that ships with the JDK (`com.sun.net.httpserver.HttpServer`). By the end, you will have an adapter of about sixty lines that turns any `Request => Response` function into a running server, and a bookstore that answers with an HTML catalog, JSON lists, single books, validated JSON input and clean 404s.

We take an incremental approach: first we write the adapter and serve a one-line handler, then we add routing, query parameters, a JSON request body and an HTML page. Every section starts a real server on a free port, calls it with the JDK's `java.net.http.HttpClient` and prints what came back.

:::warning
This adapter is glue for examples, tools, tests and small internal services. It is not a production server: ZIO Blocks ships no HTTP server, and the JDK server offers no HTTP/2, no request-body size limit and little tuning. For production traffic, hand your `Request` and `Response` values to a full HTTP server library.
:::

## The Problem

ZIO Blocks has an HTTP *data model* (`Request`, `Response`, `Body`, `Headers`, `URL`, `Method`, `Status`, `ContentType`), but no server that reads requests from a socket. If you build pages or APIs on that model, nothing serves them yet.

The JDK has had a small HTTP server since Java 6, but its `HttpExchange` is a low-level API: the raw request URI, a mutable header map, an `InputStream` and an `OutputStream`. Writing handlers directly against it means every handler repeats the same fragile plumbing.

For example, consider a handler for `GET /books?limit=2` written directly against the JDK API:

```scala mdoc:compile-only
import com.sun.net.httpserver.{HttpExchange, HttpHandler}
import java.nio.charset.StandardCharsets

val booksHandler: HttpHandler = (exchange: HttpExchange) => {
  val query = Option(exchange.getRequestURI.getRawQuery).getOrElse("")
  // Hand-rolled query parsing: no percent-decoding, no repeated keys
  val limit = query.split('&').collectFirst { case s"limit=$value" => value.toInt }.getOrElse(3)
  val json  = List("Zionomicon", "Programming in Scala", "Functional Programming in Scala")
    .take(limit)
    .map(title => "\"" + title + "\"") // Hand-written JSON: breaks on a quote in a title
    .mkString("[", ",", "]")
  val bytes = json.getBytes(StandardCharsets.UTF_8)
  exchange.getResponseHeaders.set("Content-Type", "application/json")
  exchange.sendResponseHeaders(200, bytes.length.toLong) // Wrong for HEAD and for empty bodies
  exchange.getResponseBody.write(bytes)
  exchange.close() // Never reached if anything above throws, e.g. `?limit=abc`
}
```

This approach mixes protocol details with application logic. A bad `limit` throws before `HttpExchange#close`, so the client hangs instead of getting a 400. The handler can't be tested without a socket, and the next handler copies the same lines with new bugs.

In this guide, we solve this by writing the plumbing once: one adapter converts an `HttpExchange` to a `Request` and a `Response` back to the exchange. Everything else is an ordinary, socket-free `Request => Response` function.

## Prerequisites

Add the HTTP model to your `build.sbt`, plus the HTML and schema modules that the later sections use for the catalog page and the JSON codecs:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-http-model" % "@VERSION@",
  "dev.zio" %% "zio-blocks-html"       % "@VERSION@",
  "dev.zio" %% "zio-blocks-schema"     % "@VERSION@"
)

// Scala 3.9 and newer
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-next-http-model" % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-html"       % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-schema"     % "@VERSION@"
)
scalacOptions += "-experimental"
```

The code in this guide is the same for every Scala version. It needs only the JDK for the server and the client, with no extra dependency. The HTTP model depends on the ZIO Blocks streams module, which is compiled for Java 21, so run the program on **JDK 21 or newer**. This guide is JVM only: the HTTP model also builds for Scala.js, but `com.sun.net.httpserver` does not exist there.

You should know the HTTP model types from the [HTTP model reference](../reference/http-model/model.md). The JSON section uses a derived `Schema`, introduced in [Your First Schema](./your-first-schema.md), and the HTML section uses the DSL from [Render Your First Server-Side HTML Page](./your-first-html-page.md).

We use these imports throughout the guide:

```scala mdoc:silent
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import java.net.{InetAddress, InetSocketAddress, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal
import zio.http._
```

## Turning an HttpExchange into a Request

The JDK hands each incoming request to an `HttpHandler` as an `HttpExchange`. Our first job is to read the four parts of a `Request` out of it: the method, the URL, the headers and the body. Two of them can be invalid, so the function returns `Either[Response, Request]`: a `Left` holds the error response to send instead of calling the application:

```scala mdoc:silent
def toRequest(exchange: HttpExchange): Either[Response, Request] = {
  val uri    = exchange.getRequestURI
  val target = uri.getRawPath + Option(uri.getRawQuery).fold("")("?" + _)
  for {
    method <- Method.fromString(exchange.getRequestMethod).toRight(Response(Status.NotImplemented))
    url    <- URL.parse(target).left.map(error => Response.text(Status.BadRequest, error))
  } yield {
    val headers = exchange.getRequestHeaders.asScala.foldLeft(Headers.empty) { case (acc, (name, values)) =>
      values.asScala.foldLeft(acc)((withValue, value) => withValue.add(name, value))
    }
    val contentType =
      headers.get(Header.ContentType).map(_.value).getOrElse(ContentType.`application/octet-stream`)
    val body    = Body.fromArray(exchange.getRequestBody.readAllBytes(), contentType)
    val version = Version.fromString(exchange.getProtocol).getOrElse(Version.`HTTP/1.1`)
    Request(method, url, headers, body, version)
  }
}
```

Each part maps onto an http-model constructor:

- **Method**: `Method.fromString` returns `None` for a method the model doesn't know, such as WebDAV's `PROPFIND`. `Method` is a closed set, so we answer 501 Not Implemented.
- **URL**: We pass the *raw* path and query (`URI#getRawPath`, `URI#getRawQuery`) to `URL.parse`, which percent-decodes every path segment and query value itself. The decoded `URI#getPath` would decode `%2F` into a `/` before the path is split into segments. `URL.parse` returns `Left` with a message instead of throwing, which becomes a 400.
- **Headers**: `Headers#add` takes each name and value. Header names in `Headers` are case-insensitive and stored in lower case.
- **Body**: `InputStream#readAllBytes` reads the whole body into memory, and `Body.fromArray` wraps it with the request's `Content-Type`. A body built from an array has a known length, so reading it later with `Body#toArray` or `Body#asString` never blocks.
- **Version**: `Version.fromString` parses `HTTP/1.1` and similar protocol strings.

:::note
`InputStream#readAllBytes` puts no limit on the size of the body. That is fine for a local tool, but it is one of the reasons this adapter is not meant for untrusted traffic.
:::

## Writing a Response to the Exchange

Going the other way, we copy the status, the headers and the body of a `Response` onto the exchange. The JDK server writes `Content-Length` itself from the length we pass to `HttpExchange#sendResponseHeaders`, so we skip any `content-length` and `transfer-encoding` header the response carries:

```scala mdoc:silent
def writeResponse(exchange: HttpExchange, response: Response): Unit = {
  val out = exchange.getResponseHeaders
  response.headers.toList.foreach { case (name, value) =>
    if (name != "content-length" && name != "transfer-encoding") out.add(name, value)
  }
  val bytes = response.body.toArray
  if (bytes.nonEmpty && !response.headers.has("content-type"))
    out.set("content-type", response.body.contentType.render)
  val code   = response.status.code
  val isHead = exchange.getRequestMethod == "HEAD"
  if (isHead && bytes.nonEmpty) out.set("content-length", bytes.length.toString)
  val noBody = isHead || code == 204 || code == 304 || bytes.isEmpty
  exchange.sendResponseHeaders(code, if (noBody) -1L else bytes.length.toLong)
  if (!noBody) exchange.getResponseBody.write(bytes)
}
```

The length argument of `HttpExchange#sendResponseHeaders` has three meanings, and getting it wrong is the most common bug with this server:

| Length given  | What the JDK does                                    | When we use it                                  |
| ------------- | ---------------------------------------------------- | ----------------------------------------------- |
| `n > 0`       | Sends `Content-Length: n`; we must write `n` bytes   | A response with a body                          |
| `0`           | Uses chunked transfer encoding of unknown length     | Never: we always know the length                |
| `-1`          | Sends no body                                        | Empty bodies, `HEAD`, 204 No Content, 304       |

A few more details matter here:

- **Content type**: `Response.text`, `Response.json` and `Response.ok(body)` set the `content-type` header for us. A `Response` built with its case-class constructor has none, so we fall back to `Body#contentType`.
- **HEAD**: A `HEAD` response has no body but should report the length the `GET` body would have, so we set `content-length` ourselves and pass `-1`.
- **Streaming bodies**: `Body#toArray` collects the whole body before the status line is sent. For a body built with `Body.fromStream` from a stream of unknown length, this call blocks the server thread until the stream ends. A body that never ends never reaches the client, not even its headers, and its producer never stops: the adapter writes nothing, so it never learns that the client has left. [How to Send Server-Sent Events with http-model](./send-server-sent-events.md) shows a write path that streams.
- **Server-Sent Events**: A response whose body never ends needs a different write path. [How to Send Server-Sent Events with http-model](./send-server-sent-events.md) streams `ServerSentEvent` values through a copy of this adapter that writes each event as it is produced and runs each exchange on its own thread.

## Wrapping a Handler Function

Now we can turn any `Request => Response` function into a JDK `HttpHandler`. The handler must close the exchange on every path: if it doesn't, the client waits forever. It also turns an exception thrown by the application into a 500 response, so one bad request can't leave a connection hanging. Writing the response can fail too, and that failure gets the same treatment, as the paragraph after the code explains:

```scala mdoc:silent
def handler(app: Request => Response): HttpHandler = exchange =>
  try {
    val response =
      try
        toRequest(exchange) match {
          case Left(error)                                     => error
          case Right(request) if request.method == Method.HEAD => app(request.copy(method = Method.GET))
          case Right(request)                                  => app(request)
        }
      catch {
        case NonFatal(error) =>
          System.err.println(s"Handler failed: $error")
          Response.text(Status.InternalServerError, "Internal Server Error")
      }
    try writeResponse(exchange, response)
    catch {
      case NonFatal(error) =>
        System.err.println(s"Writing the response failed: $error")
        if (exchange.getResponseCode == -1) {
          exchange.getResponseHeaders.clear()
          exchange.sendResponseHeaders(Status.InternalServerError.code, -1L)
        }
    }
  } finally exchange.close()
```

The handler runs a `HEAD` request as a `GET`, and `writeResponse` drops the body afterwards. That way every `GET` route also answers `HEAD` without writing it twice. The endpoint module's [`RouteTree`](../reference/endpoint/route-tree.md) uses the same fallback. The error message goes to standard error and the client only sees `Internal Server Error`, so exception details never leak to callers.

The second `try` covers `writeResponse`. Writing can fail after the handler has returned, for example when a header value contains a character the JDK refuses, such as a `✓` in `HX-Trigger`. Without that `try`, the exception escaped to the JDK's dispatcher: the client got an empty reply, and nothing was logged. Now the failure is logged, and when no header has gone out yet the client gets a 500. If the response is already partly written, there is no status to change, so the adapter only logs it and the `finally` still closes the exchange.

## Starting and Stopping the Server

`HttpServer.create` binds a socket address, `HttpServer#createContext` attaches our handler to every path under `/`, and `HttpServer#start` starts accepting connections on a background thread. We bind to the loopback interface so that the server is reachable only from this machine. Port 0 asks the operating system for a free port, which we read back from `HttpServer#getAddress`:

```scala mdoc:silent
def start(app: Request => Response, port: Int = 0): HttpServer = {
  val server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress, port), 0)
  server.createContext("/", handler(app))
  server.start()
  server
}
```

A server holds a socket and a thread, so it must be stopped when we are done with it. `HttpServer#stop` takes the number of seconds to wait for exchanges still in progress; 0 stops at once. The `withServer` helper starts a server, hands its port to a block of code, and stops it when the block returns or throws:

```scala mdoc:silent
def withServer[A](app: Request => Response)(use: Int => A): A = {
  val server = start(app)
  try use(server.getAddress.getPort)
  finally server.stop(0)
}
```

`withServer` uses `try`/`finally` to stay free of dependencies. If your program already manages resources with [Scope](./compile-time-resource-safety-with-scope.md), acquire the server as a resource whose release calls `server.stop(0)` instead.

:::tip
Use `start(app, 8080)` to run on a fixed port and open `http://localhost:8080/` in a browser. Call `server.stop(0)` from a JVM shutdown hook to stop it on Ctrl+C.
:::

## Calling the Server from the Same Program

To see real responses, we call the server with `java.net.http.HttpClient`, which is also part of the JDK. The `call` helper sends one request, then prints the status code, the `Content-Type` and `Content-Length` headers, and the body. We pin the client to HTTP/1.1 because the JDK server does not speak HTTP/2:

```scala mdoc:silent
val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

def call(port: Int, method: String, target: String, body: String = ""): Unit = {
  val builder = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port$target"))
  val request =
    if (body.isEmpty) builder.method(method, HttpRequest.BodyPublishers.noBody()).build()
    else
      builder
        .method(method, HttpRequest.BodyPublishers.ofString(body))
        .header("content-type", "application/json")
        .build()
  val response = client.send(request, HttpResponse.BodyHandlers.ofString())
  def header(name: String): String = response.headers.firstValue(name).orElse("none")
  println(s"$method $target -> ${response.statusCode} [${header("content-type")}, ${header("content-length")} bytes]")
  if (response.body.nonEmpty) println(response.body)
}
```

## Serving a Hello World Handler

With the adapter in place, an application is a plain function. Our first one greets the caller with the method and path it received:

```scala mdoc:silent
val hello: Request => Response =
  request => Response.text(s"Hello from ${request.method.name} ${request.path.encode}")
```

To call the handler once with `GET` and once with `HEAD`:

```scala mdoc
withServer(hello) { port =>
  call(port, "GET", "/books")
  call(port, "HEAD", "/books")
}
```

The `HEAD` request reports the same type and length as the `GET`, without a body, and the server is stopped by the time `withServer` returns.

To check the failure path, define an application that always throws:

```scala mdoc:silent
val broken: Request => Response =
  _ => throw new IllegalStateException("the catalog is not loaded")
```

Serving it shows what the client receives when the application fails:

```scala mdoc
withServer(broken) { port =>
  call(port, "GET", "/books")
}
```

The client gets a complete 500 response instead of a hanging connection.

## The Bookstore Model

The rest of the guide serves a tiny bookstore. A `Book` has an id, a title, its authors and the year of publication, and its derived `Schema` gives us JSON codecs for one book and for a list of books:

```scala mdoc:silent
import zio.blocks.schema.Schema
import zio.blocks.schema.json.JsonCodec

final case class Book(id: Int, title: String, authors: List[String], year: Int)

object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

val books: List[Book] = List(
  Book(1, "Zionomicon", List("John A. De Goes", "Adam Fraser", "Milad Khajavi"), 2024),
  Book(2, "Programming in Scala", List("Martin Odersky", "Lex Spoon", "Bill Venners"), 2008),
  Book(3, "Functional Programming in Scala", List("Paul Chiusano", "Rúnar Bjarnason"), 2014)
)

val bookJson: JsonCodec[Book]           = Schema[Book].jsonCodec
val bookListJson: JsonCodec[List[Book]] = Schema[List[Book]].jsonCodec
```

We import `Schema` by name and call `JsonCodec#encodeToString` and `JsonCodec#decode` on the codecs directly, rather than importing `zio.blocks.schema._` for the `toJsonString` and `fromJson` extension methods. On Scala 3, the wildcard import together with `zio.blocks.html._` makes `p(...)` ambiguous, because the schema package also defines a `p"..."` interpolator.

## Routing a Few Paths

A router is a function from a request to a response, so the simplest one is a pattern match. `Request#path` returns a `Path` whose `segments` are already percent-decoded, so `/books/2` becomes `List("books", "2")`. We match on the method and the segments together, and a final case answers 404 for everything else:

```scala mdoc:silent
def notFound(request: Request): Response =
  Response.text(Status.NotFound, s"No route for ${request.method.name} ${request.path.encode}")

def getBook(id: String): Response =
  books.find(_.id.toString == id) match {
    case Some(book) => Response.json(bookJson.encodeToString(book))
    case None       => Response.text(Status.NotFound, s"No book with id $id")
  }

def routes(request: Request): Response =
  (request.method, request.path.segments.toList) match {
    case (Method.GET, List("books"))     => Response.json(bookListJson.encodeToString(books))
    case (Method.GET, List("books", id)) => getBook(id)
    case _                               => notFound(request)
  }
```

`Response.json` sets the status to 200 and the content type to `application/json`. To call a known book, an unknown book and a method the router doesn't handle:

```scala mdoc
withServer(routes) { port =>
  call(port, "GET", "/books/1")
  call(port, "GET", "/books/42")
  call(port, "DELETE", "/books/1")
}
```

Pattern matching keeps every route visible in one place and needs no extra module. When the number of routes grows, the endpoint module's [`RouteTree`](../reference/endpoint/route-tree.md) looks up a handler by method and path in a prefix tree and also falls back from `HEAD` to `GET`.

## Reading Query Parameters

`Request#queryParams` returns the decoded query string. `QueryParams#getFirst` returns the first value of a key as an `Option[String]`, so a missing `limit` and a bad `limit` are two different cases. A bad value gets a 400 with a message that says what was wrong:

```scala mdoc:silent
def listBooks(request: Request): Response =
  request.queryParams.getFirst("limit") match {
    case None      => Response.json(bookListJson.encodeToString(books))
    case Some(raw) =>
      raw.toIntOption.filter(_ >= 0) match {
        case Some(limit) => Response.json(bookListJson.encodeToString(books.take(limit)))
        case None        => Response.text(Status.BadRequest, s"limit must be a non-negative integer, got '$raw'")
      }
  }
```

To serve `listBooks` on its own and call it with a valid and an invalid limit:

```scala mdoc
withServer(listBooks) { port =>
  call(port, "GET", "/books?limit=1")
  call(port, "GET", "/books?limit=all")
}
```

:::note
`QueryParams` decodes `%20` to a space but keeps `+` as a plus sign. Browsers encode a space as `+` when they submit an HTML form with `method="get"`, so a search for `zio blocks` arrives as `zio+blocks`. If your page has such a form, decode the raw query string (`URI#getRawQuery` in `toRequest`) with `Form.fromString`, which treats `+` as a space.
:::

## Decoding a JSON Body

`Request#body` holds the bytes that `toRequest` read. `JsonCodec#decode` turns them into a `Book` and returns `Either[SchemaError, Book]`, so malformed or incomplete JSON never throws. `SchemaError#message` describes what was wrong and where, which makes a clear 400 response:

```scala mdoc:silent
def addBook(request: Request): Response =
  bookJson.decode(request.body.toArray) match {
    case Right(book) => Response.json(Status.Created, bookJson.encodeToString(book))
    case Left(error) => Response.text(Status.BadRequest, s"Invalid book: ${error.message}")
  }
```

This handler only echoes the book back with 201 Created; a real service would store it first. To post a complete book, one with a missing field and one with a field of the wrong type:

```scala mdoc
withServer(addBook) { port =>
  call(port, "POST", "/books", """{"id":4,"title":"Scala with Cats","authors":["Noel Welsh","Dave Gurnell"],"year":2017}""")
  call(port, "POST", "/books", """{"id":4,"title":"Scala with Cats"}""")
  call(port, "POST", "/books", """{"id":"four"}""")
}
```

The error message names the missing field and the path of the bad value (`.id`).

## Serving an HTML Page

The last route serves an HTML catalog at `/`. We build the page with the [HTML DSL](./your-first-html-page.md), render it to a string, and wrap the UTF-8 bytes in a `Body` with the `text/html` content type. We need `Body.fromArray` here because `Body.fromString` always uses `text/plain`:

```scala mdoc:silent
import zio.blocks.html._

def catalogPage(books: List[Book]): Dom.Element =
  html(lang := "en")(
    head(
      meta(charset := "utf-8"),
      title("Scala Bookshelf")
    ),
    body(
      h1("Scala Bookshelf"),
      ul(books.map(book => li(a(href := s"/books/${book.id}")(s"${book.title} (${book.year})"))))
    )
  )

def htmlResponse(page: String): Response =
  Response.ok(Body.fromArray(page.getBytes("UTF-8"), ContentType.`text/html`))
```

`Response.ok(body)` copies the body's content type into the `content-type` header. The `text/html` constant on `ContentType` carries no charset, so the page declares UTF-8 itself with `<meta charset="utf-8">`. To serve the page:

```scala mdoc
withServer(_ => htmlResponse(catalogPage(books).render)) { port =>
  call(port, "GET", "/")
}
```

Each link is built with `href := ...` and points at the JSON route for that book.

## Putting It Together

The complete example combines every route into one bookstore application and calls each route once. It builds on three shared files embedded under Running the Examples below: the adapter `JdkHttp.scala`, which you can copy into your own project unchanged, the test client `Client.scala` and the model `Bookstore.scala`. The complete example:

```scala mdoc:embed:http-model-examples/src/main/scala/servehttpmodelrequests/CompleteExample.scala
```

## Running the Examples

All code from this guide is available as runnable examples in the `http-model-examples` directory. It is a standalone sbt build that uses the published ZIO Blocks artifacts. To run them, clone the repository and move into that directory:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/http-model-examples
```

The examples share four files: the adapter, the test client, and the bookstore model with its catalog page.

<details>
  <summary>http-model-examples/src/main/scala/servehttpmodelrequests/JdkHttp.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/servehttpmodelrequests/JdkHttp.scala:show-line-numbers
```

</details>

<details>
  <summary>http-model-examples/src/main/scala/servehttpmodelrequests/Client.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/servehttpmodelrequests/Client.scala:show-line-numbers
```

</details>

<details>
  <summary>http-model-examples/src/main/scala/servehttpmodelrequests/Bookstore.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/servehttpmodelrequests/Bookstore.scala:show-line-numbers
```

</details>

The first example serves the hello world handler and the failing handler:

<details>
  <summary>http-model-examples/src/main/scala/servehttpmodelrequests/Step1HelloWorldExample.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/servehttpmodelrequests/Step1HelloWorldExample.scala:show-line-numbers
```

</details>

To run it:

```bash
sbt "runMain servehttpmodelrequests.Step1HelloWorldExample"
```

The second example routes `GET /books` and `GET /books/{id}` with a 404 fallback:

<details>
  <summary>http-model-examples/src/main/scala/servehttpmodelrequests/Step2RoutingExample.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/servehttpmodelrequests/Step2RoutingExample.scala:show-line-numbers
```

</details>

To run it:

```bash
sbt "runMain servehttpmodelrequests.Step2RoutingExample"
```

The third example reads `?limit=` and decodes a JSON request body:

<details>
  <summary>http-model-examples/src/main/scala/servehttpmodelrequests/Step3QueryAndJsonExample.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/servehttpmodelrequests/Step3QueryAndJsonExample.scala:show-line-numbers
```

</details>

To run it:

```bash
sbt "runMain servehttpmodelrequests.Step3QueryAndJsonExample"
```

The fourth example serves the HTML catalog page:

<details>
  <summary>http-model-examples/src/main/scala/servehttpmodelrequests/Step4HtmlPageExample.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/servehttpmodelrequests/Step4HtmlPageExample.scala:show-line-numbers
```

</details>

To run it:

```bash
sbt "runMain servehttpmodelrequests.Step4HtmlPageExample"
```

To run the complete example:

```bash
sbt "runMain servehttpmodelrequests.CompleteExample"
```

To compile all the examples without running them:

```bash
sbt compile
```

## Going Further

The adapter is deliberately small. These are the knobs you are most likely to reach for next:

- **Concurrency**: With no executor set, the JDK server runs every handler on its single dispatcher thread, one request at a time. Call `server.setExecutor(...)` before `server.start()` to handle requests in parallel. On JDK 21 or newer, a virtual thread per request is a good fit for blocking handlers: `server.setExecutor(Executors.newVirtualThreadPerTaskExecutor())`.
- **TLS**: `com.sun.net.httpserver.HttpsServer` accepts the same handler; configure it with an `HttpsConfigurator` that wraps your `SSLContext`.
- **Shutdown**: Pass a positive delay to `HttpServer#stop` to let in-flight requests finish.
- **Routing**: The [`RoutePattern`](../reference/endpoint/route-pattern.md) and [`RouteTree`](../reference/endpoint/route-tree.md) pages show typed path parameters and prefix-tree lookup.
- **Typed headers and query values**: The [http-model schema integration](../reference/http-model/schema.md) decodes query parameters and headers into typed values with a `Schema`.

For the types this guide relies on, see the [HTTP model overview](../reference/http-model/index.md), the [HTTP model reference](../reference/http-model/model.md) and the [headers reference](../reference/http-model/headers.md).

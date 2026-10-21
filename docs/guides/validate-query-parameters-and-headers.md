---
id: validate-query-parameters-and-headers
title: "How to Validate Query Parameters and Headers and Answer 400"
description: "Read query parameters and headers as typed values with http-model-schema and answer bad input with a JSON 400 that names every problem."
keywords:
  - "Query Parameters"
  - "Request Validation"
  - "400 Bad Request"
  - "QueryCodec"
  - "QueryParamError"
  - "HeaderError"
---

In this guide, we read the query parameters and headers of a bookstore API as typed values: `limit` and `offset` as `Int`s, an author and year filter as a case class, and an `X-Request-Id` header as a `UUID`. By the end, `GET /books` answers every missing, malformed or out-of-range input with a 400 response whose JSON body names each bad parameter and says what is wrong with it. It never crashes, and it never quietly falls back to a default.

We use the typed accessors of the `http-model-schema` module (`query[T]`, `header[T]` and a derived `QueryCodec`) on top of the small JDK server from [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md). Every section starts that server on a free port, calls it, and prints what came back. The guide covers:

- reading one typed query parameter, required or optional;
- turning every `Missing` and `Malformed` error into one JSON 400 response;
- range checks after decoding;
- decoding a whole filter object with a derived `QueryCodec`;
- typed headers;
- values with spaces and plus signs.

## The Problem

A query parameter arrives as a `String`, or not at all. Each handler has to tell three cases apart: the parameter is missing, it is present but is not a number, or it is a number but out of range. Most handlers get one of these wrong. They crash on `"abc".toInt`, silently use a default when the client sent garbage, or answer a bare 400 that doesn't say which parameter was wrong.

For example, consider `GET /books?limit=20&offset=40&year=2014` handled with nothing but `QueryParams#getFirst`:

```scala mdoc:compile-only
import zio.http._

def listBooksByHand(request: Request): Response = {
  val limit: Either[String, Int] = request.queryParams.getFirst("limit") match {
    case None      => Right(20)
    case Some(raw) => raw.toIntOption.toRight(s"limit must be an integer, got '$raw'")
  }
  val offset: Either[String, Int] = request.queryParams.getFirst("offset") match {
    case None      => Right(0)
    case Some(raw) => raw.toIntOption.toRight(s"offset must be an integer, got '$raw'")
  }
  val year: Either[String, Option[Int]] = request.queryParams.getFirst("year") match {
    case None      => Right(None)
    case Some(raw) => raw.toIntOption.map(Some(_)).toRight(s"year must be an integer, got '$raw'")
  }
  (limit, offset, year) match {
    case (Right(l), Right(o), Right(y)) => Response.text(s"limit=$l offset=$o year=$y")
    case _                              =>
      val errors = List(limit, offset, year).collect { case Left(error) => error }
      Response.text(Status.BadRequest, errors.mkString("\n"))
  }
}
```

Every parameter repeats the same lookup, parse and error message, and the final match grows with each new parameter. Range checks, headers and a machine-readable error body would double the code again. A typo in one of the copied error strings, or a forgotten `toIntOption`, is not caught by anything.

In this guide, we let a `Schema` do the parsing, keep the "missing" and "malformed" cases as data, and write the 400 response once.

## Prerequisites

Add the HTTP model, its schema integration and the schema module (for the JSON error body) to your `build.sbt`:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-http-model"        % "@VERSION@",
  "dev.zio" %% "zio-blocks-http-model-schema" % "@VERSION@",
  "dev.zio" %% "zio-blocks-schema"            % "@VERSION@"
)

// Scala 3.9 and newer
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-next-http-model"        % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-http-model-schema" % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-schema"            % "@VERSION@"
)
scalacOptions += "-experimental"
```

The code is the same for every Scala version. The HTTP model is built on the ZIO Blocks streams module, which is compiled for Java 21, so run the program on **JDK 21 or newer**.

You should know how to derive a `Schema` for a case class, as shown in [Your First Schema](./your-first-schema.md), and how the JDK adapter turns a `Request => Response` function into a server, from [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md). We reuse that guide's `JdkHttp` adapter and its `Client` unchanged:

- `JdkHttp.withServer(app) { port => ... }` starts a server for `app` on a free loopback port and stops it when the block returns.
- `Client.call(port, method, target)` sends one request and prints `METHOD target -> status [content type, length]` followed by the body.

Both files are listed in [Running the Examples](#running-the-examples).

```scala mdoc:invisible
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import java.net.{InetAddress, InetSocketAddress, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal
import zio.http._

object JdkHttp {
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
      writeResponse(exchange, response)
    } finally exchange.close()

  def start(app: Request => Response, port: Int = 0): HttpServer = {
    val server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress, port), 0)
    server.createContext("/", handler(app))
    server.start()
    server
  }

  def withServer[A](app: Request => Response)(use: Int => A): A = {
    val server = start(app)
    try use(server.getAddress.getPort)
    finally server.stop(0)
  }
}

object Client {
  private val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

  def call(port: Int, method: String, target: String, body: String = ""): Unit = {
    val builder = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port$target"))
    val request =
      if (body.isEmpty) builder.method(method, HttpRequest.BodyPublishers.noBody()).build()
      else
        builder
          .method(method, HttpRequest.BodyPublishers.ofString(body))
          .header("content-type", "application/json")
          .build()
    val response                     = client.send(request, HttpResponse.BodyHandlers.ofString())
    def header(name: String): String = response.headers.firstValue(name).orElse("none")
    println(s"$method $target -> ${response.statusCode} [${header("content-type")}, ${header("content-length")} bytes]")
    if (response.body.nonEmpty) println(response.body)
  }
}
```

We use these imports throughout the guide. The `zio.http.schema._` import adds the typed accessors to `Request`, `QueryParams` and `Headers`:

```scala mdoc:silent
import java.util.UUID
import zio.blocks.schema.{Schema, SchemaError}
import zio.blocks.schema.json.JsonCodec
import zio.http._
import zio.http.schema._
```

## The Bookstore Model

The API lists three books. A derived `Schema` gives us the JSON codec for the list:

```scala mdoc:silent
final case class Book(id: Int, title: String, authors: List[String], year: Int)

object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

val books: List[Book] = List(
  Book(1, "Zionomicon", List("John A. De Goes", "Adam Fraser"), 2024),
  Book(2, "Programming in Scala", List("Martin Odersky", "Lex Spoon", "Bill Venners"), 2008),
  Book(3, "Functional Programming in Scala", List("Paul Chiusano", "Rúnar Bjarnason"), 2014)
)

val bookListJson: JsonCodec[List[Book]] = Schema[List[Book]].jsonCodec
```

## Reading One Typed Query Parameter

`Request#query[T](key)` looks up the first value of `key` and decodes it with the `Schema[T]` in scope. It returns `Either[QueryParamError, T]`: `QueryParamError.Missing(key)` when the parameter is absent, and `QueryParamError.Malformed(key, value, cause)` when the value doesn't parse as a `T`. Both errors have a `message`, so a first version of `GET /books?limit=N` answers 400 with it:

```scala mdoc:silent
def listBooks(request: Request): Response =
  request.query[Int]("limit") match {
    case Right(limit) => Response.json(bookListJson.encodeToString(books.take(limit)))
    case Left(error)  => Response.text(Status.BadRequest, error.message)
  }
```

To call it with a good, a missing and a malformed `limit`:

```scala mdoc
JdkHttp.withServer(listBooks) { port =>
  Client.call(port, "GET", "/books?limit=1")
  Client.call(port, "GET", "/books")
  Client.call(port, "GET", "/books?limit=abc")
}
```

`query[T]` decodes `String`, `Int`, `Long`, `Short`, `Byte`, `Boolean`, `Double`, `Float`, `Char`, `BigInt`, `BigDecimal` and `UUID`. `queryAll[T]` decodes every value of a repeated key, such as `?year=2008&year=2014`, into a `Chunk[T]`. The same three methods exist on `QueryParams`, so `request.queryParams.query[Int]("limit")` works too.

## Making a Parameter Optional Without Hiding Mistakes

Most paging parameters are optional: no `limit` means "use 20". The module has `queryOrElse[T](key, default)` for this, but it returns the default whenever decoding fails, not only when the parameter is missing. A client that sends `?limit=abc` never learns about its mistake:

```scala mdoc:silent
val typo = Request.get(URL.parse("/books?limit=abc").toOption.get)
```

```scala mdoc
println(typo.queryOrElse[Int]("limit", 20))
```

To fall back only when the parameter is missing, match on `QueryParamError.Missing` and keep `Malformed` as an error:

```scala mdoc:silent
def optionalQuery[T: Schema](request: Request, key: String): Either[QueryParamError, Option[T]] =
  request.query[T](key) match {
    case Left(QueryParamError.Missing(_)) => Right(None)
    case other                            => other.map(Some(_))
  }

def listBooksWithDefault(request: Request): Response =
  optionalQuery[Int](request, "limit") match {
    case Right(limit) => Response.json(bookListJson.encodeToString(books.take(limit.getOrElse(20))))
    case Left(error)  => Response.text(Status.BadRequest, error.message)
  }
```

Now a missing `limit` lists all three books, and `limit=abc` is still a 400:

```scala mdoc
JdkHttp.withServer(listBooksWithDefault) { port =>
  Client.call(port, "GET", "/books")
  Client.call(port, "GET", "/books?limit=abc")
}
```

:::warning
`request.query[Option[Int]]("limit")` compiles but does not work: it returns `Malformed(limit, 5, Unsupported schema type for string decoding)` even for `?limit=5`. `query[T]` and `header[T]` decode primitive schemas only, so `Option`, collections and wrapper types fail at runtime. Ask for the primitive type and handle `Missing` as above.
:::

## Answering 400 with a JSON Body That Names Every Problem

A plain-text message is fine for a person, but an API client wants to know which parameter failed so it can highlight the right form field. We describe each failure as a `Problem` (where it was, its name and the reason) and send all of them in one JSON body. The [Your First Schema](./your-first-schema.md) tutorial shows the derived JSON codec we use, and [How to Customize JSON Encoding](./customize-json-encoding.md) shows how to rename the fields if your API style needs it:

```scala mdoc:silent
final case class Problem(in: String, name: String, reason: String)

object Problem {
  implicit val schema: Schema[Problem] = Schema.derived

  def query(error: QueryParamError): Problem = error match {
    case QueryParamError.Missing(key)             => Problem("query", key, "is required")
    case QueryParamError.Malformed(key, _, cause) => Problem("query", key, cause)
  }

  def header(error: HeaderError): Problem = error match {
    case HeaderError.Missing(name)             => Problem("header", name, "is required")
    case HeaderError.Malformed(name, _, cause) => Problem("header", name, cause)
  }
}

final case class ErrorBody(problems: List[Problem])

object ErrorBody {
  implicit val schema: Schema[ErrorBody] = Schema.derived

  val json: JsonCodec[ErrorBody] = Schema[ErrorBody].jsonCodec
}

def badRequest(problems: List[Problem]): Response =
  Response.json(Status.BadRequest, ErrorBody.json.encodeToString(ErrorBody(problems)))
```

A for-comprehension over `Either` stops at the first `Left`, so a request with two bad parameters would report only one. To report all of them, we decode each parameter into a `Checked[A]`, an `Either` that carries a list of problems, and combine two results with `both`, which keeps the problems of both sides:

```scala mdoc:silent
type Checked[A] = Either[List[Problem], A]

def checkedQuery[T: Schema](request: Request, key: String): Checked[Option[T]] =
  optionalQuery[T](request, key).left.map(error => List(Problem.query(error)))

def both[A, B](a: Checked[A], b: Checked[B]): Checked[(A, B)] =
  (a, b) match {
    case (Right(x), Right(y)) => Right((x, y))
    case _                    => Left(a.swap.getOrElse(Nil) ++ b.swap.getOrElse(Nil))
  }
```

With these, paging with `limit` (default 20) and `offset` (default 0) is two lookups and one `both`:

```scala mdoc:silent
final case class Paging(limit: Int, offset: Int)

def paging(request: Request): Checked[Paging] = {
  val limit  = checkedQuery[Int](request, "limit").map(_.getOrElse(20))
  val offset = checkedQuery[Int](request, "offset").map(_.getOrElse(0))
  both(limit, offset).map { case (limit, offset) => Paging(limit, offset) }
}

def listPage(request: Request): Response =
  paging(request) match {
    case Right(page)    => Response.json(bookListJson.encodeToString(books.slice(page.offset, page.offset + page.limit)))
    case Left(problems) => badRequest(problems)
  }
```

A request with two malformed parameters now gets both of them back:

```scala mdoc
JdkHttp.withServer(listPage) { port =>
  Client.call(port, "GET", "/books?limit=1&offset=2")
  Client.call(port, "GET", "/books?limit=abc&offset=xyz")
}
```

## Checking Ranges After Decoding

`?limit=500` and `?offset=-1` decode fine as `Int`s, but neither makes sense. The decoders in this module only parse: they don't check the `Validation` constraints a `Schema` can carry, such as `Validation.Numeric.Range` from the [validation reference](../reference/schema/validation.md). A schema for `Int` with a range of 1 to 100 still decodes `?limit=500` to `Right(500)`. Range checks are therefore your own code, run after decoding. `check` turns a predicate into a `Checked` value with a reason the client can read:

```scala mdoc:silent
def check[A](name: String, reason: String)(ok: A => Boolean)(value: A): Checked[A] =
  if (ok(value)) Right(value) else Left(List(Problem("query", name, reason)))

def checkedPaging(request: Request): Checked[Paging] = {
  val limit = checkedQuery[Int](request, "limit")
    .map(_.getOrElse(20))
    .flatMap(check[Int]("limit", "must be between 1 and 100")(n => n >= 1 && n <= 100))
  val offset = checkedQuery[Int](request, "offset")
    .map(_.getOrElse(0))
    .flatMap(check[Int]("offset", "must not be negative")(_ >= 0))
  both(limit, offset).map { case (limit, offset) => Paging(limit, offset) }
}

def listCheckedPage(request: Request): Response =
  checkedPaging(request) match {
    case Right(page)    => Response.json(bookListJson.encodeToString(books.slice(page.offset, page.offset + page.limit)))
    case Left(problems) => badRequest(problems)
  }
```

Because `check` runs inside `flatMap`, a malformed value is reported as malformed and only a well-formed value is range-checked:

```scala mdoc
JdkHttp.withServer(listCheckedPage) { port =>
  Client.call(port, "GET", "/books?limit=500&offset=-1")
  Client.call(port, "GET", "/books?limit=abc&offset=-1")
}
```

## Decoding a Whole Filter with a Derived QueryCodec

When a set of parameters belongs together, such as an optional author and an optional year, a case class describes them better than separate lookups. `Schema#derive(DefaultQueryFormat)` derives a `QueryCodec` for it, and `QueryCodec#decode` reads the whole case class from `QueryParams` in one call. Each field name is a parameter name, and an `Option` field is `None` when its parameter is missing:

```scala mdoc:silent
final case class BookFilter(author: Option[String], year: Option[Int])

object BookFilter {
  implicit val schema: Schema[BookFilter] = Schema.derived

  val codec: QueryCodec[BookFilter] = Schema[BookFilter].derive(DefaultQueryFormat)
}

def matches(filter: BookFilter)(book: Book): Boolean =
  filter.author.forall(book.authors.contains) && filter.year.forall(_ == book.year)
```

`decode` returns `Either[SchemaError, BookFilter]`, not a `QueryParamError`. A missing required field is a `SchemaError.MissingField` that carries the field name. A malformed value is a single `SchemaError` whose message names the parameter but has no separate field for it, so we report it under the name of the whole filter:

```scala mdoc:silent
def decodedProblems(name: String)(error: SchemaError): List[Problem] =
  error.errors.map {
    case SchemaError.MissingField(_, field) => Problem("query", field, "is required")
    case other                              => Problem("query", name, other.message)
  }

def listFiltered(request: Request): Response =
  BookFilter.codec.decode(request.queryParams) match {
    case Right(filter) => Response.json(bookListJson.encodeToString(books.filter(matches(filter))))
    case Left(error)   => badRequest(decodedProblems("filter")(error))
  }
```

To filter by year, by author and year, and with a malformed year:

```scala mdoc
JdkHttp.withServer(listFiltered) { port =>
  Client.call(port, "GET", "/books?year=2014")
  Client.call(port, "GET", "/books?author=Martin%20Odersky&year=2008")
  Client.call(port, "GET", "/books?year=abc")
}
```

The codec stops at the first bad field, so `?year=abc` with another bad field still reports one problem.

:::warning
A derived `QueryCodec` ignores default values. `Paging(limit: Int = 20, offset: Int = 0)` does not fall back to `offset = 0` when `offset` is missing; decoding fails instead:

```scala mdoc:silent
final case class DefaultedPaging(limit: Int = 20, offset: Int = 0)

object DefaultedPaging {
  implicit val schema: Schema[DefaultedPaging] = Schema.derived

  val codec: QueryCodec[DefaultedPaging] = Schema[DefaultedPaging].derive(DefaultQueryFormat)
}
```

```scala mdoc
println(DefaultedPaging.codec.decode(QueryParams.fromEncoded("limit=5&offset=10")))
println(DefaultedPaging.codec.decode(QueryParams.fromEncoded("limit=5")))
```

Use `Option` fields and apply the defaults yourself, or decode the parameters one by one as in the previous sections.
:::

Use a derived codec when a group of optional parameters maps onto a case class you already have, and one error at a time is enough. Use `query[T]` per parameter when you need defaults, every error at once, or the parameter name as structured data.

## Reading Typed Headers

Headers work the same way, with `HeaderError.Missing(name)` and `HeaderError.Malformed(name, value, cause)`. Header names are case-insensitive, so `X-Request-Id` and `x-request-id` find the same header. The `Client` from the adapter guide sends no custom headers, so we add a small client that sends a `GET` with the headers we pass and prints them:

```scala mdoc:silent
object HeaderClient {
  private val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

  def get(port: Int, target: String, headers: (String, String)*): Unit = {
    val builder = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port$target")).GET()
    headers.foreach { case (name, value) => builder.header(name, value) }
    val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    val sent     = headers.map { case (name, value) => s"$name: $value" }.mkString(" [", ", ", "]")
    println(s"GET $target${if (headers.isEmpty) "" else sent} -> ${response.statusCode}")
    if (response.body.nonEmpty) println(response.body)
  }
}
```

An optional `X-Request-Id` must be a `UUID` when it is present. We read it with `Headers#header[T]` and treat `Missing` as `None`, as we did for `limit`:

```scala mdoc:silent
def requestId(request: Request): Checked[Option[UUID]] =
  request.headers.header[UUID]("X-Request-Id") match {
    case Right(id)                    => Right(Some(id))
    case Left(HeaderError.Missing(_)) => Right(None)
    case Left(error)                  => Left(List(Problem.header(error)))
  }
```

:::warning
Call `header[T]` on `request.headers`, not on the request. `Request` has its own `header(Header.Codec[A]): Option[A]` method, and a class's own method always wins over an extension method with the same name, so `request.header[UUID]("X-Request-Id")` does not compile. On Scala 3.9 the compiler reports `Found: ("X-Request-Id" : String)` and `Required: zio.http.Header.Codec[java.util.UUID]`. The same applies to `Response`. `request.headerAll[T]` and `request.headerOrElse[T]` have no such clash and work on the request directly.
:::

`headerOrElse[T](name, default)` is safe for a `String`, because a string never fails to decode. It returns the raw value, though, and `Accept-Language` is a list of language tags with weights. For a header the HTTP model knows, pass its typed `Header.Codec` to `header` instead. `request.headers.header(Header.AcceptLanguage)` returns `Either[HeaderError, Header.AcceptLanguage]` with the parsed ranges:

```scala mdoc:silent
val german = Request.get(URL.parse("/books").toOption.get).addHeader("Accept-Language", "de-CH, en;q=0.8")
```

```scala mdoc
println(german.headerOrElse[String]("Accept-Language", "en"))
println(german.headers.header(Header.AcceptLanguage))
```

The catalog handler uses the first preferred language, or English when the header is missing, and rejects a malformed request id:

```scala mdoc:silent
def language(request: Request): String =
  request.headers.header(Header.AcceptLanguage) match {
    case Right(accept) => accept.languages.headOption.fold("en")(_.tag)
    case Left(_)       => "en"
  }

def catalog(request: Request): Response =
  requestId(request) match {
    case Right(id)      => Response.text(s"Catalog in ${language(request)} for request ${id.getOrElse("(none)")}")
    case Left(problems) => badRequest(problems)
  }
```

```scala mdoc
JdkHttp.withServer(catalog) { port =>
  HeaderClient.get(port, "/books")
  HeaderClient.get(
    port,
    "/books",
    "X-Request-Id"    -> "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
    "Accept-Language" -> "de-CH, en;q=0.8"
  )
  HeaderClient.get(port, "/books", "X-Request-Id" -> "42")
}
```

Header problems use the same `Problem` type with `in = "header"`, so they can share one 400 response with the query problems, as the complete example below does.

## Values with Spaces and Plus Signs

A value with a space must be encoded in the URL. `QueryParams` decodes `%20` to a space, but it keeps `+` as a plus sign. Browsers encode a space as `+` when they submit an HTML form with `method="get"`, so an author search from such a form arrives as `Martin+Odersky` and matches nothing:

```scala mdoc:silent
def byAuthor(request: Request): Response =
  request.query[String]("author") match {
    case Right(author) => Response.json(bookListJson.encodeToString(books.filter(_.authors.contains(author))))
    case Left(error)   => badRequest(List(Problem.query(error)))
  }
```

```scala mdoc
JdkHttp.withServer(byAuthor) { port =>
  Client.call(port, "GET", "/books?author=Martin%20Odersky")
  Client.call(port, "GET", "/books?author=Martin+Odersky")
}
```

Once the query is a `QueryParams`, `+` and `%2B` both look like `+`, so the handler can't repair the value. The fix has to see the raw query string. `Form.fromString` decodes form data and treats `+` as a space, so this wrapper decodes `URI#getRawQuery` with it and hands the result to the unchanged `JdkHttp.handler`:

```scala mdoc:silent
def formQueryHandler(app: Request => Response): HttpHandler = exchange => {
  val rawQuery = Option(exchange.getRequestURI.getRawQuery).getOrElse("")
  val params   = Form.fromString(rawQuery).entries.foldLeft(QueryParams.empty) { case (acc, (key, value)) =>
    acc.add(key, value)
  }
  JdkHttp.handler(request => app(request.updateUrl(_.copy(queryParams = params)))).handle(exchange)
}
```

`JdkHttp.withServer` always installs `JdkHttp.handler`, so we write a variant of it that installs the wrapper:

```scala mdoc:silent
def withFormServer[A](app: Request => Response)(use: Int => A): A = {
  val server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress, 0), 0)
  server.createContext("/", formQueryHandler(app))
  server.start()
  try use(server.getAddress.getPort)
  finally server.stop(0)
}
```

The same `+` request now finds the book:

```scala mdoc
withFormServer(byAuthor) { port =>
  Client.call(port, "GET", "/books?author=Martin+Odersky")
}
```

Use the wrapper only for endpoints that HTML forms submit to. API clients that encode a space as `%20` work with the plain handler.

## Putting It Together

The complete example serves `GET /books` with paging, the derived filter and the optional request id. Every bad parameter and header ends up in one 400 response:

```scala mdoc:embed:http-model-examples/src/main/scala/validatequeryparametersandheaders/CompleteExample.scala
```

## Running the Examples

All code from this guide is available as runnable examples in the `http-model-examples` directory. It is a standalone sbt build that uses the published ZIO Blocks artifacts. To run them, clone the repository and move into that directory:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/http-model-examples
```

The examples share five files: the adapter and the client from the adapter guide (copied unchanged), the header client, the bookstore model, and the helpers this guide builds (`optionalQuery`, `Problem`, `badRequest`, `checkedQuery`, `check` and `both`).

<details>
  <summary>http-model-examples/src/main/scala/validatequeryparametersandheaders/JdkHttp.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/validatequeryparametersandheaders/JdkHttp.scala:show-line-numbers
```

</details>

<details>
  <summary>http-model-examples/src/main/scala/validatequeryparametersandheaders/Client.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/validatequeryparametersandheaders/Client.scala:show-line-numbers
```

</details>

<details>
  <summary>http-model-examples/src/main/scala/validatequeryparametersandheaders/HeaderClient.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/validatequeryparametersandheaders/HeaderClient.scala:show-line-numbers
```

</details>

<details>
  <summary>http-model-examples/src/main/scala/validatequeryparametersandheaders/Bookstore.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/validatequeryparametersandheaders/Bookstore.scala:show-line-numbers
```

</details>

<details>
  <summary>http-model-examples/src/main/scala/validatequeryparametersandheaders/Params.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/validatequeryparametersandheaders/Params.scala:show-line-numbers
```

</details>

The first example reads `?limit=` as a required parameter, then as an optional one:

<details>
  <summary>http-model-examples/src/main/scala/validatequeryparametersandheaders/Step1TypedQueryParameterExample.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/validatequeryparametersandheaders/Step1TypedQueryParameterExample.scala:show-line-numbers
```

</details>

To run it:

```bash
sbt "runMain validatequeryparametersandheaders.Step1TypedQueryParameterExample"
```

The second example reads `limit` and `offset`, checks their ranges and reports every problem in a JSON 400:

<details>
  <summary>http-model-examples/src/main/scala/validatequeryparametersandheaders/Step2BadRequestExample.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/validatequeryparametersandheaders/Step2BadRequestExample.scala:show-line-numbers
```

</details>

To run it:

```bash
sbt "runMain validatequeryparametersandheaders.Step2BadRequestExample"
```

The third example decodes the author and year filter with a derived `QueryCodec`:

<details>
  <summary>http-model-examples/src/main/scala/validatequeryparametersandheaders/Step3QueryCodecExample.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/validatequeryparametersandheaders/Step3QueryCodecExample.scala:show-line-numbers
```

</details>

To run it:

```bash
sbt "runMain validatequeryparametersandheaders.Step3QueryCodecExample"
```

The fourth example reads the `X-Request-Id` and `Accept-Language` headers:

<details>
  <summary>http-model-examples/src/main/scala/validatequeryparametersandheaders/Step4HeadersExample.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/validatequeryparametersandheaders/Step4HeadersExample.scala:show-line-numbers
```

</details>

To run it:

```bash
sbt "runMain validatequeryparametersandheaders.Step4HeadersExample"
```

The fifth example shows `%20` and `+` in a query value, and the form-data wrapper:

<details>
  <summary>http-model-examples/src/main/scala/validatequeryparametersandheaders/Step5SpacesExample.scala</summary>

```scala mdoc:embed:http-model-examples/src/main/scala/validatequeryparametersandheaders/Step5SpacesExample.scala:show-line-numbers
```

</details>

To run it:

```bash
sbt "runMain validatequeryparametersandheaders.Step5SpacesExample"
```

To run the complete example:

```bash
sbt "runMain validatequeryparametersandheaders.CompleteExample"
```

## Going Further

- **One value at a time**: The [schema integration reference](../reference/http-model/schema.md) lists every accessor on `QueryParams`, `Headers`, `Request` and `Response`, the supported types and the exact error messages.
- **Whole values**: The [codecs reference](../reference/http-model/schema-codecs.md) covers `QueryCodec` and `HeaderCodec` in depth, including repeated parameters as `List` fields, wrapper types and how header names are derived from field names.
- **Custom headers**: Implement `Header.Codec[A]` for a header of your own and pass it to `Headers#header`; the [headers reference](../reference/http-model/headers.md) lists the typed headers the HTTP model already has.
- **Error details**: The [`SchemaError` reference](../reference/schema/schema-error.md) describes the error a derived codec returns.
- **Serving**: [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md) explains the adapter, routing and JSON request bodies.

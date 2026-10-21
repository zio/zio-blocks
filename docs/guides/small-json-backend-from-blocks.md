---
id: small-json-backend-from-blocks
title: "Build a Small JSON Backend from Blocks"
description: "Combine schema, config, scope, sql, http-model and telemetry into one small bookstore JSON backend, from settings to shutdown."
keywords:
  - "Getting Started"
  - "JSON Backend"
  - "Building Blocks"
  - "Resource.from"
  - "Repo"
  - "Request"
---

```scala mdoc:invisible
// Page plumbing, not part of the lesson. The telemetry module and the JDK server adapter
// write to System.out and System.err, which mdoc does not capture, so this routes both
// into the page. The JDK server's threads don't inherit mdoc's console, so the page's
// console is captured once, here, and every thread writes to it. Each log line's
// timestamp becomes a stable placeholder, and the SQLite driver's SLF4J notice is dropped.
import java.io.{ByteArrayOutputStream, OutputStream, PrintStream}
import java.nio.charset.StandardCharsets.UTF_8

final class PageOutput(page: PrintStream) extends OutputStream {
  private val line = new ByteArrayOutputStream

  private def mask(text: String): String =
    text.replaceAll("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z", "<timestamp>")

  override def write(b: Int): Unit = synchronized {
    if (b == '\n') {
      val text = new String(line.toByteArray, UTF_8)
      if (!text.startsWith("SLF4J(")) page.println(mask(text))
      line.reset()
    } else line.write(b)
  }
}

System.getProperties.put("docs.capstone.realOut", System.out)
System.getProperties.put("docs.capstone.realErr", System.err)
val pageStream = new PrintStream(new PageOutput(Console.out), true, "UTF-8")
System.setOut(pageStream)
System.setErr(pageStream)
```

Welcome! This tutorial is for Scala 3 developers who want to see the ZIO Blocks modules work together in one small, real program. Every other guide teaches one block; here we build a bookstore backend that answers JSON over HTTP, and each section adds one block to it. You don't need to have used any of the blocks before: each section explains just enough to move on, and links to the guide that teaches that block in depth.

## 1. Introduction

By the end of this tutorial, you will understand:

- How one derived `Schema` gives a case class its JSON codec, its SQL table mapping and its typed settings
- How layered settings (environment over a YAML file over defaults) reach each service as its own typed value
- How `Resource.from` builds a graph of services from their constructors and closes them in reverse order
- How a plain `Request => Response` function becomes an HTTP backend with checked input, logs, spans and a request counter
- How to test that backend without a server, and once through the real one

We build the backend in nine steps:

1. Model the books
2. Load the settings
3. Store books in SQLite
4. Check the query parameters
5. Route requests
6. Log, trace and count requests
7. Serve the routes over HTTP
8. Wire everything together and shut down
9. Test the backend

We recommend reading from top to bottom — each section builds on the previous one.

To follow along, add the modules the backend uses, plus the SQLite JDBC driver, to your `build.sbt`. Scala 3.3 to 3.8 use the `zio-blocks-*` artifacts:

```scala
libraryDependencies ++= Seq(
  "dev.zio"   %% "zio-blocks-schema"            % "@VERSION@",
  "dev.zio"   %% "zio-blocks-config"            % "@VERSION@",
  "dev.zio"   %% "zio-blocks-config-yaml"       % "@VERSION@",
  "dev.zio"   %% "zio-blocks-scope"             % "@VERSION@",
  "dev.zio"   %% "zio-blocks-sql"               % "@VERSION@",
  "dev.zio"   %% "zio-blocks-http-model"        % "@VERSION@",
  "dev.zio"   %% "zio-blocks-http-model-schema" % "@VERSION@",
  "dev.zio"   %% "zio-blocks-telemetry"         % "@VERSION@",
  "org.xerial" % "sqlite-jdbc"                  % "3.53.4.0"
)
```

Scala 3.9 and newer use the `zio-blocks-next-*` artifacts instead, and need the `-experimental` compiler flag:

```scala
libraryDependencies ++= Seq(
  "dev.zio"   %% "zio-blocks-next-schema"            % "@VERSION@",
  "dev.zio"   %% "zio-blocks-next-config"            % "@VERSION@",
  "dev.zio"   %% "zio-blocks-next-config-yaml"       % "@VERSION@",
  "dev.zio"   %% "zio-blocks-next-scope"             % "@VERSION@",
  "dev.zio"   %% "zio-blocks-next-sql"               % "@VERSION@",
  "dev.zio"   %% "zio-blocks-next-http-model"        % "@VERSION@",
  "dev.zio"   %% "zio-blocks-next-http-model-schema" % "@VERSION@",
  "dev.zio"   %% "zio-blocks-next-telemetry"         % "@VERSION@",
  "org.xerial" % "sqlite-jdbc"                       % "3.53.4.0"
)
scalacOptions += "-experimental"
```

The tutorial is Scala 3 only, because the `sql` module is published for Scala 3 only. Use **JDK 21 or newer**, to compile as well as to run. The `telemetry` module is compiled for Java 21, and its `log.*` macros run inside the compiler, so on JDK 17 code that calls `log.info` doesn't even compile. The `http-model` and `sql` modules depend on the `streams` module, which is compiled for Java 21 too. [How to Add ZIO Blocks to Your Build](./add-zio-blocks-to-your-build.md) explains the two artifact names, cross-building and the JDK requirements in full.

## 2. Background: One Backend, Seven Blocks

A backend is mostly glue: read settings, open a database, turn HTTP requests into database calls and database rows into JSON, and write down what happened. ZIO Blocks gives you one small library for each of those jobs, with no dependencies between them that you didn't ask for. This tutorial is about the glue: how the blocks meet.

Here is the bookstore we build. Settings flow down from one layered source; each service receives only its own section; requests flow in from the JDK HTTP server through the telemetry wrapper to the routes and the repository:

```
       BOOKSTORE_* env  >  bookstore.yaml  >  defaults      (config)
                          │ Config.wire
        ┌─────────────────┼──────────────────┐
  ServerConfig      CatalogConfig         DbConfig
        │                 │                  │
   HttpService ──▶ Routes ──────▶ BookRepo ──▶ Db ──▶ bookstore.db
   (JdkHttp +   (http-model,     (sql: Repo,      (SQLite)
    telemetry)   JSON from        Table)
        ▲        schema)
        │
   HTTP client
```

| Block               | Its job in the bookstore                          | Learn it in depth                                                                       |
| ------------------- | ------------------------------------------------- | --------------------------------------------------------------------------------------- |
| `schema`            | JSON codecs for books and errors                  | [Your First Schema](./your-first-schema.md)                                             |
| `config`            | Typed, layered settings with a clear error report | [Build a Typed Configuration Loader](./typed-config-loader.md)                          |
| `scope`             | Builds the services and closes them in order      | [Compile-Time Resource Safety with Scope](./compile-time-resource-safety-with-scope.md) |
| `sql`               | The SQLite repository                             | [Build Your First Database Repository on SQLite](./your-first-sqlite-repository.md)     |
| `http-model`        | `Request`, `Response` and routing                 | [HTTP Model reference](../reference/http-model/index.md)                                |
| `http-model-schema` | Typed query parameters                            | [Schema-Based Typed Access](../reference/http-model/schema.md)                          |
| `telemetry`         | Request logs, spans and a counter                 | [Telemetry Guide](./telemetry-guide.md)                                                 |

:::note
ZIO Blocks ships no HTTP server. The `http-model` module describes requests and responses; it doesn't open sockets. In section 9 we connect it to the HTTP server built into the JDK through a small adapter. That adapter is glue for examples, tools and small internal services, not a production server: it has no TLS, no HTTP/2 and no limit on request body size.
:::

## 3. Model the Books

Everything starts with the data. A `Book` is what the catalog stores and what `GET` returns; a `NewBook` is what a client sends to add one, without an id, because the server picks the id; an `ApiError` is the body of every error response. Each one derives a `Schema`, and the JSON codecs come from those schemas:

```scala mdoc:silent
import zio.blocks.schema.Schema
import zio.blocks.schema.json.JsonCodec

final case class Book(
  id: Long,
  title: String,
  firstAuthor: String,
  year: Int,
  isbn: Option[String],
  stock: Int,
  priceCents: Int
)
object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

final case class NewBook(
  title: String,
  firstAuthor: String,
  year: Int,
  isbn: Option[String],
  stock: Int,
  priceCents: Int
)
object NewBook {
  implicit val schema: Schema[NewBook] = Schema.derived
}

final case class ApiError(error: String)
object ApiError {
  implicit val schema: Schema[ApiError] = Schema.derived
}

object Json {
  val book: JsonCodec[Book]        = Schema[Book].jsonCodec
  val books: JsonCodec[List[Book]] = Schema[List[Book]].jsonCodec
  val newBook: JsonCodec[NewBook]  = Schema[NewBook].jsonCodec
  val error: JsonCodec[ApiError]   = Schema[ApiError].jsonCodec
}
```

The code above:

- `Schema.derived` describes each case class: its fields, their names and their types. We write no JSON code at all.
- `firstAuthor` holds the first author only, and `isbn` is an `Option`, because not every book in our catalog has an ISBN on record. Stock and prices are made up; prices are whole cents, so no rounding sneaks in.
- `Schema[Book].jsonCodec` asks the schema for its JSON codec. The `Json` object keeps one codec per type, so the routes don't look them up on every request.

Let's encode a book, decode a request body, and decode a body that is missing fields:

```scala mdoc
val zionomicon = Book(1, "Zionomicon", "John A. De Goes", 2024, None, 3, 4500)
println(Json.book.encodeToString(zionomicon))

Json.newBook.decode("""{"title":"Scala with Cats","firstAuthor":"Noel Welsh","year":2017,"stock":2,"priceCents":3500}""")

Json.newBook.decode("""{"title":"Scala with Cats"}""") match {
  case Right(book) => println(book)
  case Left(error) => println(error.message)
}
```

Notice that the encoded `Zionomicon` has no `isbn` field: a `None` is left out of the JSON, and a missing optional field decodes back to `None`. A missing required field is an error with a readable message, which is exactly what we send back to a client as a 400 in section 7. The [Your First Schema](./your-first-schema.md) tutorial explains what else a schema gives you.

## 4. Load the Settings

The backend needs three settings: the HTTP port, the database file and the default page size of the catalog. Each service gets its own settings type. That keeps every service's constructor honest about what it reads, and it matters for the wiring in section 10: the dependency graph finds values by type, so two services that both took a plain `Int` would silently share one value.

We read the settings from three layers. Environment variables win over a YAML file, which wins over built-in defaults:

```scala mdoc:silent
import zio.blocks.config._
import zio.blocks.config.yaml._

final case class ServerConfig(port: Int)
object ServerConfig {
  implicit val schema: Schema[ServerConfig] = Schema.derived
}

final case class DbConfig(file: String)
object DbConfig {
  implicit val schema: Schema[DbConfig] = Schema.derived
}

final case class CatalogConfig(pageSize: Int)
object CatalogConfig {
  implicit val schema: Schema[CatalogConfig] = Schema.derived
}

final case class BookstoreConfig(server: ServerConfig, db: DbConfig, catalog: CatalogConfig)
object BookstoreConfig {
  implicit val schema: Schema[BookstoreConfig] = Schema.derived
}

object Settings {
  val bookstoreYaml: String =
    """server:
      |  port: 0
      |db:
      |  file: bookstore.db
      |""".stripMargin

  val yaml: ConfigSource = ConfigSource.fromYaml(bookstoreYaml, "bookstore.yaml") match {
    case Right(source) => source
    case Left(error)   => throw error
  }

  val defaults: ConfigSource =
    ConfigSource.fromMap(Map("server.port" -> "8080", "catalog.pageSize" -> "2"), "defaults")

  val environment: ConfigSource = EnvSource.keyFormat(KeyFormat.UpperSnakeCase).prefix("bookstore")

  def layered(env: ConfigSource): ConfigSource = env.orElse(yaml).orElse(defaults)
}
```

The code above:

- `ServerConfig`, `DbConfig` and `CatalogConfig` are one settings section each. `BookstoreConfig` groups them so we can check all settings at once at startup.
- The YAML file sets `server.port` to `0`, which asks the operating system for any free port. That keeps the examples from clashing with whatever already runs on your machine; production sets the port through the environment.
- `ConfigSource.fromMap` holds the defaults. The default page size is `2` only so the output in this tutorial stays short.
- `EnvSource.keyFormat(KeyFormat.UpperSnakeCase).prefix("bookstore")` reads `catalog.pageSize` from `BOOKSTORE_CATALOG_PAGE_SIZE`, `server.port` from `BOOKSTORE_SERVER_PORT` and `db.file` from `BOOKSTORE_DB_FILE`. The prefix matters: it keeps the bookstore from reading, or printing, any other variable of the process.
- `ConfigSource#orElse` layers two sources: a key comes from the first source that has it. `Settings.layered` takes the environment as a parameter, so the rest of this page and the tests can pass a stand-in.

To see the layers at work, let's stand in for the environment with a map that sets `db.file`, load the whole `BookstoreConfig`, and print where each setting came from:

```scala mdoc
val demoEnv = ConfigSource.fromMap(Map("db.file" -> "/tmp/bookstore-tutorial.db"), "env")

Config.loadWithProvenance[BookstoreConfig](Settings.layered(demoEnv)) match {
  case Right(loaded) =>
    println(loaded.value)
    println(loaded.dump())
  case Left(errors) => errors.foreach(error => println(error.message))
}
```

Each layer wins once: the page size comes from the defaults, the port from the YAML file, and the database file from the environment, which overrides the YAML file's `bookstore.db`. `Config.loadWithProvenance` returns the decoded value together with the sources it came from, and `ProvenanceMap#dump` prints them as a table. A program can print this table at startup, so whoever runs it sees which settings are in effect. The [Build a Typed Configuration Loader](./typed-config-loader.md) tutorial covers sources, formats and secrets in depth.

## 5. Store Books in SQLite

Now the books need somewhere to live. The `sql` module turns the same `Book` schema into a table mapping and a repository with ready-made reads and writes. We split storage into two services: `Db` owns the database file, and `BookRepo` owns the queries.

`Db` is the first service with something to close, so it extends `AutoCloseable`:

```scala mdoc:silent
import java.nio.file.Files
import zio.blocks.sql._

class Db(config: DbConfig) extends AutoCloseable {
  val transactor: JdbcTransactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:${config.file}", SqlDialect.SQLite)

  transactor.connect {
    sql"""CREATE TABLE IF NOT EXISTS book (
      id INTEGER PRIMARY KEY,
      title TEXT NOT NULL,
      first_author TEXT NOT NULL,
      year INTEGER NOT NULL,
      isbn TEXT,
      stock INTEGER NOT NULL,
      price_cents INTEGER NOT NULL
    )""".update
  }
  println("Db: opened")

  def close(): Unit = println("Db: closed")
}

class BookRepo(db: Db) {
  private val books = Repo.derived[Book, Long]

  db.transactor.transact {
    if (books.count == 0) books.insertBatch(BookRepo.starterCatalog)
  }

  def page(limit: Int, offset: Int): List[Book] =
    db.transactor.connect {
      sql"""SELECT id, title, first_author, year, isbn, stock, price_cents FROM book
            ORDER BY id LIMIT $limit OFFSET $offset""".query[Book]
    }

  def find(id: Long): Option[Book] =
    db.transactor.connect(books.find(id).toOption)

  def add(book: NewBook): Book =
    db.transactor.transact {
      val id = sql"SELECT COALESCE(MAX(id), 0) + 1 FROM book".queryOne[Long].getOrElse(1L)
      books.insertReturning(
        Book(id, book.title, book.firstAuthor, book.year, book.isbn, book.stock, book.priceCents)
      )
    }
}

object BookRepo {
  // Stock and prices are made up.
  val starterCatalog: List[Book] = List(
    Book(1, "Zionomicon", "John A. De Goes", 2024, None, 3, 4500),
    Book(2, "Programming in Scala", "Martin Odersky", 2008, Some("9780981531601"), 5, 3999),
    Book(3, "Functional Programming in Scala", "Paul Chiusano", 2014, Some("9781617290657"), 4, 4499)
  )
}

object TempDb {
  def file(): String = {
    val path = Files.createTempFile("bookstore", ".db")
    path.toFile.deleteOnExit()
    path.toString
  }
}
```

```scala mdoc:invisible
// sql-zio is on the docs classpath: `import zio.blocks.sql._` above brings its
// subpackage `zio` into scope; restore the root package for the blocks below
import _root_.zio
```

The code above:

- `JdbcTransactor.fromUrl` points at the SQLite file named in `DbConfig`. It opens a connection for each `JdbcTransactor#connect` or `JdbcTransactor#transact` call and closes it afterwards, so `Db#close` has nothing to release here and only reports that it ran. A connection pool, such as the one in [How to Connect to PostgreSQL with a Connection Pool](./connect-to-postgresql-with-a-connection-pool.md), would be closed in `Db#close`.
- We write the `CREATE TABLE` statement by hand, because it declares `id` as the `PRIMARY KEY`; `Table#createTable` doesn't, a known bug in version 0.0.56. The column names are the field names in snake case, which is the naming `Repo.derived` expects.
- `Repo.derived[Book, Long]` derives a repository for `Book`, keyed by its `id` field. `BookRepo` fills an empty catalog with three starter books in one transaction.
- `BookRepo#page` writes its own query, because `LIMIT` and `OFFSET` paging isn't one of the ready-made operations. The `$limit` and `$offset` values become query parameters, not text spliced into the SQL.
- `BookRepo#add` picks the next free id and inserts the book in one transaction, so two clients adding books at once can't get the same id.
- `TempDb.file()` creates an empty temporary file. SQLite's `jdbc:sqlite::memory:` would give each connection its own empty database, so a temporary file is the simplest way to start every run from the starter catalog.

Let's open a fresh database:

```scala mdoc:silent
val storageDb = new Db(DbConfig(TempDb.file()))
val repo      = new BookRepo(storageDb)
```

Now we can try the repository:

```scala mdoc
repo.page(limit = 2, offset = 0).foreach(println)
repo.find(3)
repo.find(42)
repo.add(NewBook("Scala with Cats", "Noel Welsh", 2017, None, 2, 3500))

storageDb.close()
```

The starter catalog is there, a missing id gives `None`, and the new book gets id `4`. The [Build Your First Database Repository on SQLite](./your-first-sqlite-repository.md) tutorial covers tables, repositories, queries and transactions step by step.

## 6. Check the Query Parameters

Before we route any request, let's decide how to read its input. `GET /books` takes two optional query parameters, `limit` and `offset`. A missing parameter falls back to a default; a malformed or out-of-range one must be rejected with a clear message, never silently replaced.

The `http-model-schema` module reads a query parameter as a typed value with `request.query[Int](name)`. It returns `Right` with the value, or `Left` with a `QueryParamError` that says whether the parameter was missing or malformed:

```scala mdoc:silent
import zio.http._
import zio.http.schema._

final case class Paging(limit: Int, offset: Int)

object Paging {
  val maxLimit = 50

  def from(request: Request, pageSize: Int): Either[ApiError, Paging] =
    for {
      limit  <- intParam(request, "limit", pageSize)
      offset <- intParam(request, "offset", 0)
      _      <- check(limit >= 1 && limit <= maxLimit, s"limit must be between 1 and $maxLimit, got $limit")
      _      <- check(offset >= 0, s"offset must not be negative, got $offset")
    } yield Paging(limit, offset)

  private def intParam(request: Request, name: String, default: Int): Either[ApiError, Int] =
    request.query[Int](name) match {
      case Right(value)                     => Right(value)
      case Left(QueryParamError.Missing(_)) => Right(default)
      case Left(malformed)                  => Left(ApiError(malformed.message))
    }

  private def check(ok: Boolean, message: => String): Either[ApiError, Unit] =
    if (ok) Right(()) else Left(ApiError(message))
}

object Demo {
  def get(target: String): Request = Request.get(url(target))

  def post(target: String, json: String): Request =
    Request.post(url(target), Body.fromArray(json.getBytes("UTF-8"), ContentType.`application/json`))

  def show(response: Response): Unit =
    println(s"${response.status.code} ${response.body.text}")

  private def url(target: String): URL =
    URL.parse(target).fold(error => throw new IllegalArgumentException(error), identity)
}
```

The code above:

- `Paging.intParam` turns `QueryParamError.Missing` into the default and every other error into an `ApiError` with the error's own message.
- `Paging.check` adds the range checks. No decoder checks ranges for us, so `Paging.from` does it after decoding.
- `Paging.from` chains the steps in a `for` comprehension: the first `Left` stops it, and that error is what the client sees.
- `Demo` builds requests from strings and prints responses, so we can try code that takes a `Request` without starting a server. A JSON body needs `Body.fromArray` with the `application/json` content type; `Body.fromString` is always `text/plain`.

Let's read the paging of a few request targets, good and bad:

```scala mdoc
List("/books", "/books?limit=1&offset=2", "/books?limit=abc", "/books?limit=500", "/books?offset=-1")
  .foreach(target => println(s"$target -> ${Paging.from(Demo.get(target), pageSize = 2)}"))
```

A plain `/books` gets the default page, and every bad value gets its own message.

:::caution
`request.queryOrElse("limit", 20)` looks like a shorter way to write `Paging.intParam`, but it also falls back to the default when the value is malformed, so `?limit=abc` would quietly return the first page. Match on `request.query` when bad input must be rejected.
:::

The query-parameter guide, H-36 "How to Validate Query Parameters and Headers", is still being written; until it lands, the [Schema-Based Typed Access](../reference/http-model/schema.md) reference covers `RequestSchemaOps#query`, `RequestSchemaOps#queryAll` and `RequestSchemaOps#queryOrElse`.

## 7. Route Requests

Now we connect requests to the repository. The routes are one function from `Request` to `Response`. It matches the method and the path segments, calls `BookRepo`, and writes JSON with the codecs from section 3. Bad input gets a 400, and a missing book gets a 404, each with an `ApiError` body:

```scala mdoc:silent
class Routes(repo: BookRepo, catalog: CatalogConfig) {

  def handle(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, List("books")) =>
        Paging.from(request, catalog.pageSize) match {
          case Right(paging) => Response.json(Json.books.encodeToString(repo.page(paging.limit, paging.offset)))
          case Left(error)   => Routes.error(Status.BadRequest, error)
        }

      case (Method.GET, List("books", id)) =>
        id.toLongOption.flatMap(repo.find) match {
          case Some(book) => Response.json(Json.book.encodeToString(book))
          case None       => Routes.error(Status.NotFound, ApiError(s"no book with id $id"))
        }

      case (Method.POST, List("books")) =>
        Json.newBook.decode(request.body.toArray) match {
          case Right(newBook) => Response.json(Status.Created, Json.book.encodeToString(repo.add(newBook)))
          case Left(error)    => Routes.error(Status.BadRequest, ApiError(error.message))
        }

      case _ =>
        Routes.error(Status.NotFound, ApiError(s"no route for ${request.method} ${request.path}"))
    }
}

object Routes {
  def error(status: Status, error: ApiError): Response =
    Response.json(status, Json.error.encodeToString(error))
}
```

The code above:

- `request.path.segments` is the path split at `/`, so `/books/2` matches `List("books", "2")` and binds `id` to `"2"`.
- `id.toLongOption.flatMap(repo.find)` turns a non-numeric id into the same 404 as an unknown one.
- `Response.json` sets the status, the `application/json` content type and the body in one call. It takes the JSON as a `String`, so we encode with the derived codec first.
- `Json.newBook.decode(request.body.toArray)` decodes the request body. A body that isn't a valid `NewBook` becomes a 400 with the decoder's message.

The routes are an ordinary function, so we can call them directly, with no server and no sockets. First, let's build them on a fresh database:

```scala mdoc:silent
val routesDb = new Db(DbConfig(TempDb.file()))
val routes   = new Routes(new BookRepo(routesDb), CatalogConfig(pageSize = 2))
```

Then we send them a few requests, good and bad:

```scala mdoc
Demo.show(routes.handle(Demo.get("/books")))
Demo.show(routes.handle(Demo.get("/books/2")))
Demo.show(routes.handle(Demo.get("/books/42")))
Demo.show(
  routes.handle(
    Demo.post(
      "/books",
      """{"title":"Scala with Cats","firstAuthor":"Noel Welsh","year":2017,"stock":2,"priceCents":3500}"""
    )
  )
)
Demo.show(routes.handle(Demo.get("/books?limit=abc")))
Demo.show(routes.handle(Demo.post("/books", """{"title":"Scala with Cats"}""")))

routesDb.close()
```

Each line shows the status code and the body. The first page holds two books, because the catalog's page size is `2`; the new book comes back with status `201` and its id; and both kinds of bad input get a `400` with a JSON message. This is the whole backend's behavior. Everything that follows adds operations around it: telemetry, a server, wiring and shutdown.

## 8. Log, Trace and Count Requests

A backend that runs unattended needs to leave a record of what it did. The `telemetry` module gives us three global entry points: `log` for log lines, `trace` for spans and `metric` for counters. Instead of adding telemetry calls to every route, we wrap the routes once. `RequestTelemetry#wrap` takes any `Request => Response` function and returns one that runs each request in its own span, writes one structured log line, and adds one to a counter:

```scala mdoc:silent
import zio.blocks.telemetry.{log, metric, trace, AttributeKey, Counter, MetricData}

class RequestTelemetry {
  val requests: Counter = metric.counter("bookstore.http.requests")

  def wrap(app: Request => Response): Request => Response = request =>
    trace.span(s"${request.method} ${request.path}") { _ =>
      val response = app(request)
      requests.add(1L, "status" -> response.status.code)
      log.info(
        "Handled request",
        "method" -> request.method.toString,
        "path"   -> request.path.toString,
        "status" -> response.status.code
      )
      response
    }

  def countsByStatus(): List[(Long, Long)] =
    requests.collect() match {
      case MetricData.SumData(points) =>
        points.map(p => p.attributes.get(RequestTelemetry.status).getOrElse(0L) -> p.value).sorted
      case _ => Nil
    }
}

object RequestTelemetry {
  val status: AttributeKey[Long] = AttributeKey.long("status")
}
```

The code above:

- We import `log`, `metric`, `trace` and the few types we need by name. A wildcard `import zio.blocks.telemetry._` would also bring in telemetry's own `Resource` type, which clashes with the `Resource` from the `scope` module that we use in section 10, a known bug in version 0.0.56.
- `metric.counter("bookstore.http.requests")` creates the counter once, when `RequestTelemetry` is built. Each call to `metric.counter` registers a new instrument, so calling it per request would scatter the counts over many counters.
- `trace.span(...)` runs the request inside a span named after the method and the path, and ends the span when the response is ready.
- `requests.add(1L, "status" -> ...)` adds one to the series for the response's status code.
- `log.info(message, key -> value, ...)` writes one log line with structured fields that a log tool can search without parsing the text.
- `RequestTelemetry#countsByStatus` reads the counter back as `(status, count)` pairs, sorted by status.

Let's wrap a fresh set of routes:

```scala mdoc:silent
val telemetryDb = new Db(DbConfig(TempDb.file()))
val telemetry   = new RequestTelemetry
val app         = telemetry.wrap(new Routes(new BookRepo(telemetryDb), CatalogConfig(pageSize = 2)).handle)
```

Now we send three requests through the wrapper, and then read the spans and the counter back:

```scala mdoc
Demo.show(app(Demo.get("/books/1")))
Demo.show(app(Demo.get("/books/42")))
Demo.show(app(Demo.get("/books?limit=abc")))

println(trace.collectedSpans.map(_.name))
println(telemetry.countsByStatus())

telemetryDb.close()
```

Each request writes one log line with its fields, just above the response it describes. By default the lines go to standard output; this page shows the time as `<timestamp>`, because it changes on every run. The spans are kept in memory, and `trace.collectedSpans` returns the finished ones, one per request. The counter has one series per status code.

:::caution
The brackets of a log line should hold the source location: the short class name, the method and the line, such as `[RequestTelemetry.wrap:20]`. In version 0.0.56, a class inside a package prints its full name followed by two numbers instead, as in `[(smalljsonbackendfromblocks.RequestTelemetry,27,43).wrap:20]` from the companion example. On this page, the class also sits inside the `repl.MdocSession` wrapper that renders the examples. This is a known bug; the message and the fields are not affected.
:::

The [Telemetry Guide](./telemetry-guide.md) explains how to send logs as JSON, export spans and metrics, and set log levels. The tutorial [Instrument a Program with Logs, Traces and Metrics](./instrument-a-program-with-logs-traces-and-metrics.md) teaches the three signals step by step.

## 9. Serve the Routes over HTTP

So far we've called the routes ourselves. To serve real clients, something has to accept TCP connections, parse HTTP, and write responses back. ZIO Blocks has no server, but the JDK has one built in: `com.sun.net.httpserver.HttpServer`. A small adapter, `JdkHttp`, converts each exchange of that server into a `Request`, calls our function, and writes the `Response` back. We copy it from the guide [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md), which explains every line; it is about sixty lines, so it is folded here:

<details>
  <summary>The JdkHttp adapter</summary>

The adapter, as the guide ships it, without its comments:

```scala mdoc:silent
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import java.net.{InetAddress, InetSocketAddress}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

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
```

</details>

What matters for the bookstore is `JdkHttp.start(app, port)`: it starts a server on the loopback interface that sends every request to our function, turns an exception thrown by the function into a 500 response, and returns the JDK's `HttpServer`. To call the server, we also copy the [guide's](./serve-http-model-requests.md) tiny test client. `Client.call(port, method, target, body)` sends one request with the JDK's `HttpClient` and prints the status code, the content type and length, and the body:

<details>
  <summary>The test client</summary>

The client, as the guide ships it, without its comments:

```scala mdoc:silent
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

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

</details>

The server is our second service with something to close. `HttpService` starts the server with the instrumented routes on the configured port, and stops it in `HttpService#close`:

```scala mdoc:silent
class HttpService(routes: Routes, val telemetry: RequestTelemetry, config: ServerConfig) extends AutoCloseable {
  private val server = JdkHttp.start(telemetry.wrap(routes.handle), config.port)
  println("HttpService: started")

  def port: Int = server.getAddress.getPort

  def close(): Unit = {
    server.stop(0)
    println("HttpService: stopped")
  }
}
```

The code above:

- `telemetry.wrap(routes.handle)` is the application the server runs: the routes from section 7 inside the telemetry from section 8.
- `HttpService#port` reads the real port back from the server, because `ServerConfig(port = 0)` lets the operating system pick one.
- `server.stop(0)` stops accepting connections and returns without waiting for requests in flight. A production service would pass a few seconds, so that requests in progress can finish.

Let's build the services by hand and start the server:

```scala mdoc:silent
val serverDb = new Db(DbConfig(TempDb.file()))
val service  = new HttpService(new Routes(new BookRepo(serverDb), CatalogConfig(pageSize = 2)), new RequestTelemetry, ServerConfig(port = 0))
```

Now we send two real HTTP requests, and then close everything:

```scala mdoc
Client.call(service.port, "GET", "/books/1")
Client.call(service.port, "GET", "/books?limit=abc")

service.close()
serverDb.close()
```

The server writes its log line before the response goes out, so each log line appears just above the client's view of the same request. Notice the closing at the end: we must stop the server before closing the database, or a request could still arrive while the database is closing. Getting that order right by hand is exactly what the next section automates.

## 10. Wire Everything Together and Shut Down

Building the services by hand works, but `main` must know every constructor, call them in the right order, pass each one its settings, and close them in the reverse order. The `scope` module does that job. `Resource.from[HttpService]` reads the constructors of `HttpService` and everything it needs, builds each service once, and registers `AutoCloseable#close` for every service that is `AutoCloseable`. The `config` module's `Config.wire` turns one section of the settings into a value the graph can use:

```scala mdoc:silent
import zio.blocks.scope._

object Bookstore {
  def app(settings: ConfigSource): Resource[HttpService] =
    Resource.from[HttpService](
      Wire(settings),
      Config.wire[ServerConfig]("server"),
      Config.wire[DbConfig]("db"),
      Config.wire[CatalogConfig]("catalog")
    )
}
```

The code above:

- `Wire(settings)` puts the layered `ConfigSource` into the graph, so `Config.wire` can read from it.
- `Config.wire[ServerConfig]("server")` reads the `server` section as a `ServerConfig` when a service asks for one; the other two lines do the same for the `db` and `catalog` sections.
- Everything else, `Db`, `BookRepo`, `Routes`, `RequestTelemetry` and `HttpService`, is built from its constructor. We never call `new` on a service.
- `Bookstore.app` is a `def`, not a `val`. A `Resource` built by `Resource.from` and stored in a `val` can be used only once, a known bug in version 0.0.56; a `def` gives each caller a fresh one.

Now let's run the whole backend. The stand-in environment points `db.file` at a fresh temporary file:

```scala mdoc:silent
val wiredEnv = ConfigSource.fromMap(Map("db.file" -> TempDb.file()), "env")
```

`Scope.global.scoped` opens a scope, `Scope#allocate` builds the graph inside it, and when the block ends, the scope closes everything it built, last built first:

```scala mdoc
val counts = Scope.global.scoped { scope =>
  import scope._
  val backend = allocate(Bookstore.app(Settings.layered(wiredEnv)))
  val port    = $(backend)(_.port)

  Client.call(port, "GET", "/books")
  Client.call(port, "POST", "/books", """{"title":"Scala with Cats","firstAuthor":"Noel Welsh","year":2017,"stock":2,"priceCents":3500}""")
  Client.call(port, "GET", "/books?offset=2")

  $(backend)(_.telemetry.countsByStatus())
}
println(s"Requests by status: $counts")
```

Read the output from the top. `Db: opened` comes first, because everything else depends on the database; `HttpService: started` comes last. After the three requests, the scope closes the services in reverse: the server stops before the database closes, so no request can reach a closed database. That's the order we write by hand in section 9, now guaranteed by the graph.

A few details are worth a second look:

- `Scope#allocate` returns the service wrapped as `$[HttpService]`, a value that can't leave the scope. `$(backend)(_.port)` reads a plain value out of it.
- The block returns the request counts, a `List` of numbers. A scope can return plain data, but not a service: the compiler rejects a block that tries to return `backend`, because the service is closed once the block ends.
- The `POST` adds a fourth book, and `?offset=2` then shows the third and the fourth: the page size from the defaults still applies.

Finally, let's see what happens when a setting is wrong. We set the page size to `ten`:

```scala mdoc:silent
val badEnv = ConfigSource.fromMap(Map("db.file" -> TempDb.file(), "catalog.pageSize" -> "ten"), "env")
```

Then we try to start the backend with it:

```scala mdoc
try {
  Scope.global.scoped { scope =>
    import scope._
    allocate(Bookstore.app(Settings.layered(badEnv)))
    ()
  }
} catch {
  case error: ConfigLoadException => println(error.getMessage)
}
```

The server never starts. The graph builds `Db` before it reaches the catalog settings, and when `Config.wire` fails, the scope closes the database it has already opened. Then the error report names the bad value, the expected type and the sources it searched. Notice that the key in the report is `pageSize`, not `catalog.pageSize`: `Config.wire` reports keys without their section prefix, a known bug in version 0.0.56. Checking every setting at once with `Config.loadWithProvenance[BookstoreConfig]` before building the graph, as the complete example below does, reports full keys and every bad setting at the same time.

The [How to Feed Configuration into a Wire Dependency Graph](./feed-config-into-a-wire-graph.md) guide shows more of this pattern, and [Compile-Time Resource Safety with Scope](./compile-time-resource-safety-with-scope.md) explains scopes, `$` and `Unscoped` in depth.

## 11. Test the Backend

The routes are a plain function, so most tests need no server at all: build the routes on a fresh database file, call `Routes#handle`, and compare the whole status and body. Let's write the helpers the tests use and run one check here on the page:

```scala mdoc
def withRoutes[A](use: Routes => A): A = {
  val db = new Db(DbConfig(TempDb.file()))
  try use(new Routes(new BookRepo(db), CatalogConfig(pageSize = 2)))
  finally db.close()
}

def call(routes: Routes, request: Request): (Int, String) = {
  val response = routes.handle(request)
  (response.status.code, response.body.text)
}

withRoutes(call(_, Demo.get("/books/42"))) == (404, """{"error":"no book with id 42"}""")
```

The code above:

- `withRoutes` gives each test its own database file with the starter catalog, and closes the database even when the test throws, so tests can't see each other's books.
- `call(routes, request)` reduces a response to its status code and its body as text.
- The check compares the full body with `==`. Checking that the body merely contains `42` would still pass if the message, the field name or the JSON shape changed.

The examples project turns these checks into a ZIO Test spec. Four tests call the routes directly; the last one builds the real graph from the settings and sends one request through the server, to prove the pieces are connected:

```scala mdoc:embed:capstone-examples/src/test/scala/smalljsonbackendfromblocks/BookstoreSpec.scala
```

Run it from the `capstone-examples` directory with `sbt test`. The [How to Test Database Code Against SQLite](./test-database-code-against-sqlite.md) guide shows more ways to set up and seed test databases.

```scala mdoc:invisible
// Restore the streams the page plumbing replaced.
System.setOut(System.getProperties.get("docs.capstone.realOut").asInstanceOf[java.io.PrintStream])
System.setErr(System.getProperties.get("docs.capstone.realErr").asInstanceOf[java.io.PrintStream])
```

## 12. Putting It Together

This example combines all the concepts we've learned into a program: it layers the real `BOOKSTORE_*` environment variables over the YAML file over the defaults, prints the startup report (or every bad setting, and stops), builds the whole service graph in one scope, sends a few requests through the real server, and shuts down in reverse order:

```scala mdoc:embed:capstone-examples/src/main/scala/smalljsonbackendfromblocks/CompleteExample.scala:show-line-numbers
```

The classes it wires live in the files next to it, one file per section of this tutorial: `Model.scala`, `Settings.scala`, `Storage.scala`, `Paging.scala`, `Routes.scala`, `RequestTelemetry.scala` and `HttpService.scala`, plus the adapter `JdkHttp.scala` and the client `Client.scala`. Its database file is `bookstore.db` in the working directory, so books added in one run are still there in the next; set `BOOKSTORE_DB_FILE` to use another file, or `BOOKSTORE_SERVER_PORT` to pick the port.

## 13. Running the Examples

All examples in this tutorial have corresponding runnable Scala files in the `capstone-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/capstone-examples
```

Each example except the complete one works on a fresh temporary database file, so you can run them in any order and as often as you like. We recommend running them in order to follow the tutorial step by step.

### Model the Books

This example encodes a book to JSON, decodes a request body, and shows the error for a body with a missing field.

<details>
  <summary>capstone-examples/src/main/scala/smalljsonbackendfromblocks/Concept1ModelExample.scala</summary>

```scala mdoc:embed:capstone-examples/src/main/scala/smalljsonbackendfromblocks/Concept1ModelExample.scala:show-line-numbers
```

</details>

Run it from the `capstone-examples` directory:

```bash
sbt "runMain smalljsonbackendfromblocks.Concept1ModelExample"
```

### Load the Settings

This example layers a stand-in environment over the YAML file over the defaults, and prints where each setting came from.

<details>
  <summary>capstone-examples/src/main/scala/smalljsonbackendfromblocks/Concept2SettingsExample.scala</summary>

```scala mdoc:embed:capstone-examples/src/main/scala/smalljsonbackendfromblocks/Concept2SettingsExample.scala:show-line-numbers
```

</details>

Run it from the `capstone-examples` directory:

```bash
sbt "runMain smalljsonbackendfromblocks.Concept2SettingsExample"
```

### Store Books in SQLite

This example opens a fresh database with the starter catalog, reads a page and single books, and adds one.

<details>
  <summary>capstone-examples/src/main/scala/smalljsonbackendfromblocks/Concept3StorageExample.scala</summary>

```scala mdoc:embed:capstone-examples/src/main/scala/smalljsonbackendfromblocks/Concept3StorageExample.scala:show-line-numbers
```

</details>

Run it from the `capstone-examples` directory:

```bash
sbt "runMain smalljsonbackendfromblocks.Concept3StorageExample"
```

### Check the Query Parameters

This example reads `limit` and `offset` from good and bad request targets.

<details>
  <summary>capstone-examples/src/main/scala/smalljsonbackendfromblocks/Concept4PagingExample.scala</summary>

```scala mdoc:embed:capstone-examples/src/main/scala/smalljsonbackendfromblocks/Concept4PagingExample.scala:show-line-numbers
```

</details>

Run it from the `capstone-examples` directory:

```bash
sbt "runMain smalljsonbackendfromblocks.Concept4PagingExample"
```

### Route Requests

This example calls the routes directly, with no server, for good and bad requests.

<details>
  <summary>capstone-examples/src/main/scala/smalljsonbackendfromblocks/Concept5RoutesExample.scala</summary>

```scala mdoc:embed:capstone-examples/src/main/scala/smalljsonbackendfromblocks/Concept5RoutesExample.scala:show-line-numbers
```

</details>

Run it from the `capstone-examples` directory:

```bash
sbt "runMain smalljsonbackendfromblocks.Concept5RoutesExample"
```

### Log, Trace and Count Requests

This example wraps the routes with `RequestTelemetry` and prints the log lines, the spans and the request counts.

<details>
  <summary>capstone-examples/src/main/scala/smalljsonbackendfromblocks/Concept6TelemetryExample.scala</summary>

```scala mdoc:embed:capstone-examples/src/main/scala/smalljsonbackendfromblocks/Concept6TelemetryExample.scala:show-line-numbers
```

</details>

Run it from the `capstone-examples` directory:

```bash
sbt "runMain smalljsonbackendfromblocks.Concept6TelemetryExample"
```

### Serve the Routes over HTTP

This example starts `HttpService` by hand, sends two real HTTP requests, and stops it.

<details>
  <summary>capstone-examples/src/main/scala/smalljsonbackendfromblocks/Concept7ServerExample.scala</summary>

```scala mdoc:embed:capstone-examples/src/main/scala/smalljsonbackendfromblocks/Concept7ServerExample.scala:show-line-numbers
```

</details>

Run it from the `capstone-examples` directory:

```bash
sbt "runMain smalljsonbackendfromblocks.Concept7ServerExample"
```

### Wire Everything Together and Shut Down

This example builds the whole graph from layered settings in a scope, serves three requests, closes the services in reverse order, and then shows the report for a bad setting.

<details>
  <summary>capstone-examples/src/main/scala/smalljsonbackendfromblocks/Concept8WiringExample.scala</summary>

```scala mdoc:embed:capstone-examples/src/main/scala/smalljsonbackendfromblocks/Concept8WiringExample.scala:show-line-numbers
```

</details>

Run it from the `capstone-examples` directory:

```bash
sbt "runMain smalljsonbackendfromblocks.Concept8WiringExample"
```

### The Complete Example

This example runs the backend with the real environment, as shown in [Putting It Together](#12-putting-it-together).

Run it from the `capstone-examples` directory:

```bash
sbt "runMain smalljsonbackendfromblocks.CompleteExample"
```

To check that every example compiles without running any of them, use this command from the `capstone-examples` directory:

```bash
sbt compile
```

The tests from section 11 run with:

```bash
sbt test
```

## 14. What You've Learned

In this tutorial, you learned:

- How one derived `Schema` per case class gives the backend its JSON codecs, and how the same case classes map to SQLite rows and typed settings
- How to layer settings from the environment, a YAML file and defaults, give each service its own settings type, and see where every value came from
- How `Resource.from` builds a service graph from constructors and settings sections, and how a scope closes it in reverse order, even when startup fails halfway
- How a plain `Request => Response` function handles routing, JSON bodies and checked query parameters, and how a wrapper adds logs, spans and a request counter around it
- How to serve that function with the JDK's built-in HTTP server through a small adapter, and test it both directly and through the server

You now have a small backend built entirely from ZIO Blocks, and you know where each block's own guide goes deeper.

## 15. Where to Go Next

- **Go to production storage.** [How to Connect to PostgreSQL with a Connection Pool](./connect-to-postgresql-with-a-connection-pool.md) replaces SQLite with PostgreSQL and a pool that `Db#close` would shut down.
- **Serve a bigger catalog.** [How to Stream Large Query Results](./stream-large-query-results.md) reads a large table as a stream of small row chunks, instead of loading every row into a `List` at once.
- **Test more of the database code.** [How to Test Database Code Against SQLite](./test-database-code-against-sqlite.md) covers fixtures, schema setup and rolling back instead of cleaning up.
- **Go deeper on settings.** [How to Feed Configuration into a Wire Dependency Graph](./feed-config-into-a-wire-graph.md) and [Build a Typed Configuration Loader](./typed-config-loader.md) cover secrets, formats and testing settings.
- **Go deeper on resources.** [Compile-Time Resource Safety with Scope](./compile-time-resource-safety-with-scope.md) explains how scopes keep services from escaping.
- **Protect the endpoints.** [How to Issue and Verify JWTs](./issue-and-verify-jwts.md) shows how to sign and check tokens; [How to Protect an Endpoint with JWT Bearer Tokens](./protect-endpoints-with-jwt.md) shows how to check them on each request.
- **Look up the APIs.** The reference pages for [the HTTP model](../reference/http-model/index.md), [config](../reference/config/index.md), [sql](../reference/sql/index.md), [Resource](../reference/resource-management/resource.md) and [telemetry](../reference/telemetry/index.md) list every operation.

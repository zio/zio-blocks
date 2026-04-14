---
id: http-model-schema
title: "Schema-Based Typed Access"
---

The `zio-http-model-schema` module provides schema-based extraction of query parameters and headers with automatic decoding and validation.

## Installation

Add the following to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-http-model-schema" % "<version>"
```

For cross-platform projects (Scala.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-http-model-schema" % "<version>"
```

## Query Parameter Extraction

Extract and decode query parameters with schema validation:

```scala mdoc:compile-only
import zio.http.{QueryParams, URL}
import zio.http.schema._
import zio.blocks.schema.Schema

val url = URL.parse("/api/users?page=2&tag=scala&tag=fp").toOption.get
val params = url.queryParams

// Extract single value with automatic decoding
params.query[Int]("page")
// Right(2)

// Extract all values for a key
params.queryAll[String]("tag")
// Right(Chunk("scala", "fp"))

// Extract with default fallback
params.queryOrElse[Int]("limit", 10)
// 10 - uses default since "limit" not present
```

## Header Extraction

Extract and decode headers with schema validation:

```scala mdoc:compile-only
import zio.http.{Headers, Request, URL}
import zio.http.schema._
import zio.blocks.schema.Schema

val request = Request.get(URL.parse("/").toOption.get)
  .addHeader("x-page", "5")
  .addHeader("x-tag", "scala")
  .addHeader("x-tag", "functional")

val headers = request.headers

// Extract single header value
headers.header[Int]("x-page")
// Right(5)

// Extract all header values
headers.headerAll[String]("x-tag")
// Right(Chunk("scala", "functional"))

// Extract with default fallback
headers.headerOrElse[Int]("x-limit", 100)
// 100
```

## Request and Response Extensions

`Request` and `Response` gain schema-based extraction methods:

```scala mdoc:compile-only
import zio.http.{Request, Response, URL}
import zio.http.schema._
import zio.blocks.schema.Schema

// Request query parameter extraction
val request = Request.get(URL.parse("/search?q=zio&limit=20").toOption.get)

request.query[String]("q")
// Right("zio")

request.query[Int]("limit")
// Right(20)

// Response objects provide the same extraction capabilities
val response = Response.ok.addHeader("x-request-id", "12345")
response.headers
// Headers instance with added header
```

## Error Handling

Schema-based extraction returns `Either` for explicit error handling:

```scala mdoc:compile-only
import zio.http.{QueryParams, URL}
import zio.http.schema._
import zio.blocks.schema.Schema

val params = QueryParams("name" -> "Alice", "age" -> "invalid")

params.query[String]("name") match {
  case Right(name) => println(s"Name: $name")
  case Left(QueryParamError.Missing(key)) => println(s"Missing key: $key")
  case Left(QueryParamError.Malformed(key, value, cause)) =>
    println(s"Failed to parse $key=$value: $cause")
}

params.query[Int]("age") match {
  case Right(age) => println(s"Age: $age")
  case Left(QueryParamError.Missing(key)) => println(s"Missing key: $key")
  case Left(QueryParamError.Malformed(key, value, cause)) =>
    println(s"Failed to parse $key=$value: $cause")
}
```

## Supported Types

The schema module provides built-in `Schema` instances for common types:

- **Primitives**: `String`, `Int`, `Long`, `Boolean`, `Double`, `Float`, `Short`, `Byte`, `Char`
- **Big Numbers**: `BigInt`, `BigDecimal`
- **UUID**: `java.util.UUID`

For custom types, define a `Schema[T]` instance using schema derivation or manual construction.

The module is designed for **composition without coupling** — higher-level libraries (HTTP clients, servers, middleware) can import and use only the types they need without being forced to depend on unrelated types. The zero-dependency design means this module can be used in any Scala project without dragging in the ZIO ecosystem.

## Integration with HTTP Model

This schema module is designed to work seamlessly with the core [HTTP Model](./http-model.md) types. Use schema-based extraction to add type-safe, validated access to HTTP query parameters and headers on top of the basic `Request` and `Response` types.

**Example workflow:**
1. Create a `Request` with query parameters or headers (using core HTTP model types)
2. Use schema-based extraction methods to get typed, validated values
3. Handle errors with `Either` for explicit error management

For comprehensive examples of how the HTTP model types work together, refer to the [HTTP Model Running the Examples](./http-model.md#running-the-examples) section.

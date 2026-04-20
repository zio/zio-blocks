---
id: http-model-schema
title: "Schema-Based Typed Access"
---

`zio-http-model-schema` adds **type-safe, validated extraction** of query parameters and headers to the core HTTP model. It provides extension methods on `QueryParams`, `Headers`, `Request`, and `Response` that automatically decode string values to typed objects using schema-based decoding with comprehensive error reporting.

Core features: `QueryParamsSchemaOps`, `HeadersSchemaOps`, `RequestSchemaOps`, `ResponseSchemaOps`, `QueryParamError`, `HeaderError`, with automatic decoding for 11 primitive types and extensible support for custom types via `Schema[T]`.

## Motivation

Building HTTP handlers often requires extracting and validating query parameters or headers — "get the `userId` query parameter as a `UUID`." Without schema-based extraction, this becomes tedious and error-prone:

```scala mdoc:compile-only
import zio.http.QueryParams

// Manual extraction (error-prone, repetitive)
val params = QueryParams("userId" -> "550e8400-e29b-41d4-a716-446655440000")
val userIdStr = params.getFirst("userId")
val userId = userIdStr match {
  case None => Left("Missing userId")
  case Some(s) =>
    try Right(java.util.UUID.fromString(s))
    catch { case e: IllegalArgumentException => Left(s"Invalid UUID format: ${e.getMessage}") }
}
```

Every parameter requires 8+ lines of boilerplate with manual exception handling, error message creation, and type-specific parsing. UUID parsing alone involves `IllegalArgumentException` handling; multiply this across dozens of handlers extracting `UUID`, `Int`, `Boolean` parameters, and you have duplicated extraction logic everywhere — inconsistent error messages, risk of forgotten error handling, and no compile-time guarantees on correctness.

The solution is to use schema-based extraction for clean, declarative code:

```scala mdoc:compile-only
import zio.http.QueryParams
import zio.http.schema._
import zio.blocks.schema.Schema

val params = QueryParams("userId" -> "550e8400-e29b-41d4-a716-446655440000")
val userId = params.query[java.util.UUID]("userId")  // 1 line, automatic UUID parsing + errors
```

`zio-http-model-schema` separates **extraction logic from business logic**:

- **Automatic decoding** — Pass a `Schema[T]`, get `Either[Error, T]` back. Works for 11 primitive types out of the box.
- **Explicit error handling** — `Either` forces error handling. `QueryParamError` and `HeaderError` distinguish "missing" from "malformed" cases.
- **Composable** — Works directly on `QueryParams`, `Headers`, `Request`, `Response` with zero configuration.
- **Zero-dependency** — Pure extraction layer; doesn't pull in ZIO, async runtimes, or HTTP client libraries.

This keeps HTTP request handling clean, testable, and portable across different effect systems.

## Installation

Add the following to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-http-model-schema" % "@VERSION@"
```

For cross-platform projects (Scala.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-http-model-schema" % "@VERSION@"
```

Supported Scala versions: 2.13.x and 3.x. Requires `zio-http-model` and `zio-blocks-schema` as dependencies.

## How They Work Together

The module adds a **schema-based extraction layer** on top of core HTTP model types:

```
HTTP Model Types (from zio-http-model)
    ├─ QueryParams: raw string key-value pairs
    ├─ Headers: raw string header name-value pairs
    ├─ Request: contains queryParams and headers
    └─ Response: contains headers

Schema-Based Extraction (this module)
    ├─ QueryParamsSchemaOps.query[T](key) ─────┐
    ├─ HeadersSchemaOps.header[T](name) ───────┼──> StringDecoder.decode(raw, Schema[T])
    ├─ RequestSchemaOps.query[T](key) ─────────┤   ├─> Right(typedValue)
    ├─ RequestSchemaOps.header[T](name) ───────┤   └─> Left(error)
    └─ ResponseSchemaOps.header[T](name) ──────┘

Typical Workflow:
1. Parse URL or receive Request (core HTTP model)
2. Extract queryParams or headers (access raw strings)
3. Use schema methods to decode to typed values (this module)
4. Handle Either[Error, T] in business logic
```

**Example flow:**

```scala
import zio.http.{Request, URL}
import zio.http.schema._
import zio.blocks.schema.Schema

val url = URL.parse("/api/users?page=2&limit=50&sort=name").toOption.get
val request = Request.get(url)

// Step 1: Access raw query parameters (core HTTP model)
val params = request.queryParams

// Step 2: Extract with type safety (schema extraction)
val pageResult = request.query[Int]("page")      // Right(2)
val limitResult = request.query[Int]("limit")    // Right(50)
val sortResult = request.query[String]("sort")   // Right("name")

// Step 3: Handle errors
pageResult match {
  case Right(page) => println(s"Page: $page")
  case Left(QueryParamError.Missing(key)) => println(s"Missing $key")
  case Left(QueryParamError.Malformed(key, value, cause)) => println(s"Bad $key: $cause")
}
```

## Error Handling

The module provides two error types for explicit error handling: `QueryParamError` and `HeaderError`.

### QueryParamError

Error type for query parameter extraction failures:

```scala
sealed trait QueryParamError extends Product with Serializable {
  def message: String
}

object QueryParamError {
  final case class Missing(key: String) extends QueryParamError
  final case class Malformed(key: String, value: String, cause: String) extends QueryParamError
}
```

**Variants:**

- **`Missing(key)`** — Query parameter with name `key` was not present in the parameters
  - Example: `QueryParamError.Missing("page")` when accessing a non-existent parameter
  - Message: `"Missing query parameter: page"`

- **`Malformed(key, value, cause)`** — Query parameter with name `key` was present but decoding the `value` to the requested type failed
  - Example: `QueryParamError.Malformed("age", "abc", "Cannot parse 'abc' as Int")` when `age=abc` but `Int` was requested
  - Message: `"Malformed query parameter 'age' value 'abc': Cannot parse 'abc' as Int"`

**Accessing error messages:**

All `QueryParamError` subtypes have a `message` property for user-friendly error reporting:

```scala
import zio.http.schema._

val error: QueryParamError = QueryParamError.Malformed("page", "invalid", "Cannot parse 'invalid' as Int")
println(error.message)
// Malformed query parameter 'page' value 'invalid': Cannot parse 'invalid' as Int
```

### HeaderError

Error type for header extraction failures (identical structure to `QueryParamError`):

```scala
sealed trait HeaderError extends Product with Serializable {
  def message: String
}

object HeaderError {
  final case class Missing(name: String) extends HeaderError
  final case class Malformed(name: String, value: String, cause: String) extends HeaderError
}
```

**Variants:**

- **`Missing(name)`** — Header with name `name` was not present
  - Example: `HeaderError.Missing("authorization")` when accessing a non-existent header
  - Message: `"Missing header: authorization"`

- **`Malformed(name, value, cause)`** — Header with name `name` was present but decoding failed
  - Example: `HeaderError.Malformed("x-count", "notanumber", "Cannot parse 'notanumber' as Int")`
  - Message: `"Malformed header 'x-count' value 'notanumber': Cannot parse 'notanumber' as Int"`

**Handling patterns:**

Pattern-match on error type to distinguish "missing" from "malformed":

```scala
import zio.http.{Request, URL}
import zio.http.schema._
import zio.blocks.schema.Schema

val request = Request.get(URL.parse("/").toOption.get)
  .addHeader("x-token", "invalid-token")

request.header[Int]("x-token") match {
  case Right(token) => println(s"Token: $token")
  case Left(HeaderError.Missing(name)) => println(s"Missing required header: $name")
  case Left(HeaderError.Malformed(name, value, cause)) => println(s"Bad header: $cause")
}
```

## Extension Classes

### QueryParamsSchemaOps

Extension methods for `QueryParams` to extract and decode query parameters with type safety.

**Methods:**

#### `query[T](key: String)(implicit schema: Schema[T]): Either[QueryParamError, T]`

Extract a single query parameter value and decode it to type `T`.

Returns `Right(value)` if parameter exists and decoding succeeds. Returns `Left(QueryParamError.Missing(key))` if parameter is missing. Returns `Left(QueryParamError.Malformed(...))` if parameter exists but decoding fails.

```scala
import zio.http.QueryParams
import zio.http.schema._
import zio.blocks.schema.Schema

val params = QueryParams("page" -> "2", "limit" -> "50")

params.query[Int]("page")      // Right(2)
params.query[String]("page")   // Right("2")
params.query[Int]("missing")   // Left(QueryParamError.Missing("missing"))
params.query[Int]("limit")     // Right(50)
```

#### `queryAll[T](key: String)(implicit schema: Schema[T]): Either[QueryParamError, Chunk[T]]`

Extract all values for a query parameter key and decode them to type `T`.

Returns `Right(chunk)` with all decoded values if parameter exists and all values decode successfully. Returns `Left(QueryParamError.Missing(key))` if no values exist for the key. Returns `Left(QueryParamError.Malformed(...))` if any value fails to decode.

```scala
import zio.http.QueryParams
import zio.http.schema._
import zio.blocks.schema.Schema

val params = QueryParams("tag" -> "scala", "tag" -> "fp", "tag" -> "zio")

params.queryAll[String]("tag")  // Right(Chunk("scala", "fp", "zio"))

val params2 = QueryParams("id" -> "1", "id" -> "2", "id" -> "abc")
params2.queryAll[Int]("id")     // Left(Malformed("id", "abc", "Cannot parse..."))
```

**Short-circuit behavior:** Decoding stops at the first malformed value; only the first error is reported.

#### `queryOrElse[T](key: String, default: => T)(implicit schema: Schema[T]): T`

Extract a query parameter with a default fallback.

Returns the decoded value if parameter exists and decodes successfully. Returns `default` if parameter is missing or decoding fails (errors are silently ignored).

```scala
import zio.http.QueryParams
import zio.http.schema._
import zio.blocks.schema.Schema

val params = QueryParams("page" -> "2")

params.queryOrElse[Int]("page", 1)     // 2 (from param)
params.queryOrElse[Int]("limit", 10)   // 10 (default, param missing)

val params2 = QueryParams("page" -> "invalid")
params2.queryOrElse[Int]("page", 1)    // 1 (default, decoding failed)
```

### HeadersSchemaOps

Extension methods for `Headers` to extract and decode header values with type safety. API identical to `QueryParamsSchemaOps`.

**Methods:**

#### `header[T](name: String)(implicit schema: Schema[T]): Either[HeaderError, T]`

Extract a single header value and decode it to type `T`.

Header name matching is **case-insensitive** (HTTP spec). Returns `Right(value)` on success, `Left(HeaderError.Missing(name))` if header not found, or `Left(HeaderError.Malformed(...))` if decoding fails.

```scala
import zio.http.Headers
import zio.http.schema._
import zio.blocks.schema.Schema

val headers = Headers()
  .addHeader("x-user-id", "42")
  .addHeader("x-api-version", "2")

headers.header[Int]("x-user-id")        // Right(42)
headers.header[Int]("X-User-ID")        // Right(42) - case-insensitive
headers.header[Int]("x-missing")        // Left(HeaderError.Missing("x-missing"))
headers.header[Int]("x-api-version")    // Right(2)
```

#### `headerAll[T](name: String)(implicit schema: Schema[T]): Either[HeaderError, Chunk[T]]`

Extract all values for a header name and decode them to type `T`.

HTTP allows multiple headers with the same name; this method collects and decodes all of them. Returns `Right(chunk)` with all decoded values, `Left(HeaderError.Missing(name))` if no headers exist for the name, or `Left(HeaderError.Malformed(...))` if any value fails to decode.

```scala
import zio.http.Headers
import zio.http.schema._
import zio.blocks.schema.Schema

val headers = Headers()
  .addHeader("x-tag", "scala")
  .addHeader("x-tag", "functional")
  .addHeader("x-tag", "zio")

headers.headerAll[String]("x-tag")      // Right(Chunk("scala", "functional", "zio"))
headers.headerAll[String]("x-missing")  // Left(HeaderError.Missing("x-missing"))
```

#### `headerOrElse[T](name: String, default: => T)(implicit schema: Schema[T]): T`

Extract a header with a default fallback (errors are silently ignored).

```scala
import zio.http.Headers
import zio.http.schema._
import zio.blocks.schema.Schema

val headers = Headers().addHeader("x-count", "5")

headers.headerOrElse[Int]("x-count", 0)     // 5 (from header)
headers.headerOrElse[Int]("x-missing", 0)   // 0 (default)
```

### RequestSchemaOps

Extension methods for `Request` to extract query parameters and headers using the same schema-based API.

**Methods:**

#### Query parameter methods (delegate to `QueryParamsSchemaOps`)

- **`query[T](key: String)(implicit schema: Schema[T]): Either[QueryParamError, T]`** — Extract single query parameter
- **`queryAll[T](key: String)(implicit schema: Schema[T]): Either[QueryParamError, Chunk[T]]`** — Extract all values for key
- **`queryOrElse[T](key: String, default: => T)(implicit schema: Schema[T]): T`** — Extract with fallback

#### Header methods (delegate to `HeadersSchemaOps`)

- **`header[T](name: String)(implicit schema: Schema[T]): Either[HeaderError, T]`** — Extract single header
- **`headerAll[T](name: String)(implicit schema: Schema[T]): Either[HeaderError, Chunk[T]]`** — Extract all header values
- **`headerOrElse[T](name: String, default: => T)(implicit schema: Schema[T]): T`** — Extract header with fallback

All methods work identically to their corresponding `QueryParamsSchemaOps` and `HeadersSchemaOps` versions but operate directly on the `Request` object:

```scala
import zio.http.{Request, URL}
import zio.http.schema._
import zio.blocks.schema.Schema

val request = Request.get(URL.parse("/api/users?page=1&limit=10").toOption.get)
  .addHeader("x-token", "secret123")
  .addHeader("x-user-id", "42")

// Query parameters
val page = request.query[Int]("page")              // Right(1)
val limit = request.queryOrElse[Int]("limit", 20) // 10

// Headers
val token = request.header[String]("x-token")      // Right("secret123")
val userId = request.header[Int]("x-user-id")      // Right(42)
```

**Composing Multiple Extractions:**

Combine query and header extractions using `Either` operations:

```scala mdoc:compile-only
import zio.http.{Request, URL}
import zio.http.schema._
import zio.blocks.schema.Schema

val request = Request.get(URL.parse("/api/posts?userId=5&page=2").toOption.get)

val result = for {
  userId <- request.query[Int]("userId")
  page <- request.query[Int]("page")
} yield (userId, page)

result match {
  case Right((userId, page)) => println(s"User $userId, page $page")
  case Left(error) => println(s"Extraction failed: ${error.message}")
}
```

This pattern is powerful for request handlers that need to extract multiple parameters and stop at the first error.

### ResponseSchemaOps

Extension methods for `Response` to extract headers using the schema-based API.

**Methods:**

- **`header[T](name: String)(implicit schema: Schema[T]): Either[HeaderError, T]`** — Extract single header
- **`headerAll[T](name: String)(implicit schema: Schema[T]): Either[HeaderError, Chunk[T]]`** — Extract all header values
- **`headerOrElse[T](name: String, default: => T)(implicit schema: Schema[T]): T`** — Extract header with fallback

Note: `Response` does not have `query*` methods (responses don't have query parameters).

```scala
import zio.http.Response
import zio.http.schema._
import zio.blocks.schema.Schema

val response = Response.ok
  .addHeader("x-request-id", "req-12345")
  .addHeader("x-ratelimit-remaining", "99")

val requestId = response.header[String]("x-request-id")      // Right("req-12345")
val remaining = response.headerOrElse[Int]("x-ratelimit-remaining", 100) // 99
```

## Supported Types

The module supports decoding to any type with a `Schema[T]` instance. Built-in support includes:

### Primitives

- **`String`** — No decoding, raw string value
- **`Int`** — Parsed via `String#toInt`, error on invalid format
- **`Long`** — Parsed via `String#toLong`, error on invalid format
- **`Boolean`** — Parsed via `String#toBoolean` (accepts "true", "false", case-insensitive)
- **`Double`** — Parsed via `String#toDouble`, error on invalid format
- **`Float`** — Parsed via `String#toFloat`, error on invalid format
- **`Short`** — Parsed via `String#toShort`, error on invalid format
- **`Byte`** — Parsed via `String#toByte`, error on invalid format
- **`Char`** — Parses single character; error if string length ≠ 1

### Big Numbers

- **`BigInt`** — Parsed via `scala.BigInt(string)`, error on invalid format
- **`BigDecimal`** — Parsed via `scala.BigDecimal(string)`, error on invalid format

### UUID

- **`java.util.UUID`** — Parsed via `java.util.UUID.fromString(string)`, error on invalid format (must be standard UUID format: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`)

### Error Messages

All decoding errors follow the pattern: `"Cannot parse 'value' as TypeName"`. Example error messages:

```
Cannot parse 'abc' as Int
Cannot parse 'notaboolean' as Boolean
Cannot parse 'not-a-uuid' as UUID
Cannot parse '12.34.56' as BigDecimal
```

### Custom Types

To support custom types, provide a `Schema[T]` instance. The module automatically uses the schema's primitive type information via `StringDecoder`. For case classes or other compound types, manually create a `Schema[T]` using the schema module's derivation tools or manual construction.

## Integration with HTTP Model

This module extends the core [HTTP Model](./http-model.md) with extraction capabilities:

- **Base layer:** Core types (`Request`, `Response`, `URL`, `Headers`, `QueryParams`, `Body`) — pure data
- **Schema layer:** Extraction methods (`query[T]`, `header[T]`, etc.) — type-safe access with automatic decoding
- **Integration:** Use schema methods to extract and validate parameters/headers, then pass typed values to business logic

**Typical request-handling flow:**

```scala
import zio.http.{Request, Response, URL}
import zio.http.schema._
import zio.blocks.schema.Schema

// Step 1: Create a request (core HTTP model)
val request = Request.get(URL.parse("/api/users?userId=42&includeDetails=true").toOption.get)

// Step 2: Extract and validate parameters (this module)
val userId = request.query[Int]("userId")
val includeDetails = request.queryOrElse[Boolean]("includeDetails", false)

// Step 3: Handle extraction results in business logic
val response = for {
  id <- userId
  details <- Right(includeDetails)
} yield {
  // Business logic with typed values
  Response.ok.addHeader("x-processed-user-id", id.toString)
}

// Step 4: Return response (core HTTP model)
response match {
  case Right(r) => r
  case Left(error) => Response.badRequest.addHeader("x-error", error.message)
}
```

For comprehensive examples of the core HTTP model, refer to the [HTTP Model documentation](./http-model.md).

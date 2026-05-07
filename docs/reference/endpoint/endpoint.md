---
id: endpoint
title: "Endpoint"
---

`Endpoint[PathInput, Input, Err, Output, Auth]` is the top-level descriptor for an HTTP endpoint. It holds a typed route, three independent codec channels (request input, error output, success output), an authentication type, and documentation metadata. `Endpoint` is pure data — it carries no server or client logic and imposes no effect type. Its full shape is:

```scala
final case class Endpoint[PathInput, Input, Err, Output, Auth <: AuthType](
  route: RoutePattern[PathInput],
  input: HttpCodec[CodecKind.Request, Input],
  error: HttpCodec[CodecKind.Response, Err],
  output: HttpCodec[CodecKind.Response, Output],
  auth: Auth,
  doc: Doc
)
```

## Motivation

An endpoint descriptor separates the **shape** of an HTTP surface from its **execution**. A single `Endpoint` value can be interpreted by a server to generate routes, by a client generator to produce typed API calls, or by an OpenAPI renderer to produce specification documents. This means the endpoint definition is the single source of truth — change it once and every interpreter updates.

The five type parameters track everything the compiler needs to enforce consistency across the whole stack:

| Parameter   | Meaning                                                    |
|-------------|------------------------------------------------------------|
| `PathInput` | Type of values extracted from path segments                |
| `Input`     | Aggregate type of all request inputs (query, header, body) |
| `Err`       | Aggregate type of all error response shapes                |
| `Output`    | Aggregate type of all success response shapes              |
| `Auth`      | Authentication scheme, carries the client requirement      |

## Construction

We create an `Endpoint` from a `RoutePattern` using `Endpoint.apply`:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val ep = Endpoint(Method.GET / "users" / PathCodec.int("id"))
```

The route can also be built from a separate `RoutePattern` value:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val route = Method.POST / "orders" / PathCodec.uuid("orderId")
val ep    = Endpoint(route)
```

The initial `Endpoint` starts with `Unit` for all three codec channels and `AuthType.None` for auth, so further builder calls always widen the types additively.

## Request Input Builders

Every `.in`, `.query`, and `.header` call adds another input component to the endpoint, widening the `Input` type parameter.

### Body input

To add a request body typed by a `Schema`, use `.in(schema)`:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.Method

val ep = Endpoint(Method.POST / "users")
  .in(Schema.string)
```

To specify the content type explicitly, pass a `MediaType` as well:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.mediatype.MediaTypes
import zio.blocks.schema.Schema
import zio.http.Method

val ep = Endpoint(Method.POST / "users")
  .in(MediaTypes.application.`json`, Schema.string)
```

To add a raw `HttpCodec.Body` node directly, use `.in(codec)`:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.Method

val ep = Endpoint(Method.POST / "users")
  .in(HttpCodec.requestBody(Schema.string))
```

### Query parameters

To add a query parameter by name and schema, use `.query(name, schema)`:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.Method

val ep = Endpoint(Method.GET / "users")
  .query("page", Schema.int)
  .query("limit", Schema.int)
```

To add a pre-built `HttpCodec.Query` node, use `.query(codec)`:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.Method

val pageCodec = HttpCodec.query("page", Schema.int)
val ep        = Endpoint(Method.GET / "users").query(pageCodec)
```

### Request headers

To add a request header by name and schema, use `.header(name, schema)`:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.Method

val ep = Endpoint(Method.GET / "users")
  .header("X-Trace-Id", Schema.string)
```

To add a pre-built `HttpCodec.Header` node, use `.header(codec)`:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.Method

val traceCodec = HttpCodec.requestHeader("X-Trace-Id", Schema.string)
val ep         = Endpoint(Method.GET / "users").header(traceCodec)
```

## Success Output Builders

Every `.out` and `.outHeader` call adds a success response alternative or header component, widening `Output`.

### Response body

To add a 200 OK response body, use `.out(schema)`:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.Method

val ep = Endpoint(Method.GET / "users")
  .out(Schema.string)
```

To specify a non-200 status code, use `.out(status, schema)`:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.{Method, Status}

val ep = Endpoint(Method.POST / "users")
  .out(Status.Created, Schema.int)
```

To add content-type negotiation, pass a `MediaType`:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.mediatype.MediaTypes
import zio.blocks.schema.Schema
import zio.http.{Method, Status}

val ep = Endpoint(Method.GET / "users")
  .out(MediaTypes.application.`json`, Schema.string)
  .out(Status.Created, MediaTypes.text.`plain`, Schema.int)
```

Multiple `.out` calls produce alternatives. The output type widens from `Unit` to the first schema type, then to a nested `Either` for each additional alternative.

### Response headers

To add a typed response header, use `.outHeader(name, schema)`:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.Method

val ep = Endpoint(Method.GET / "users")
  .out(Schema.string)
  .outHeader("X-Total-Count", Schema.int)
```

## Error Output Builders

Error channels work like success channels but populate the `Err` type parameter. Two variants exist: `outError` (cross-version) and `orOutError` (Scala 3 unions).

### `outError` — cross-version additive errors

To add an error response with a status code and body schema, use `.outError(status, schema)`:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.{Method, Status}

val ep = Endpoint(Method.GET / "users")
  .out(Schema.string)
  .outError(Status.NotFound, Schema.string)
  .outError(Status.BadRequest, Schema.string)
```

Each `.outError` call widens `Err` by one nested `Either` layer.

### `orOutError` — Scala 3 union errors

On Scala 3, `.orOutError` accumulates error types as a native union type instead of nested `Either`s. The first call replaces the initial `Unit` error outright; subsequent calls build a `Fallback` codec backed by `Unions` derivation:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.{Method, Status}

val ep = Endpoint(Method.GET / "users")
  .orOutError(Status.NotFound, Schema.string)
  .orOutError(Status.Conflict, Schema.int)

val typed: Endpoint[Unit, Unit, String | Int, Unit, AuthType.None.type] = ep
```

The compiler rejects overlapping union members — `.orOutError(Status.NotFound, Schema.string)` followed by `.orOutError(Status.BadRequest, Schema.string)` is a compile error because `String | String` is not a valid discriminated union.

## Authentication

To attach an authentication scheme, use `.auth(authType)`:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val secured = Endpoint(Method.GET / "me")
  .auth(AuthType.Bearer)
```

The `Auth` type parameter carries the `ClientRequirement` associated type, so a bearer-secured endpoint exposes `auth.codec` typed as `HttpCodec[CodecKind.Request, headers.Authorization.Bearer]`. See [AuthType](./auth-type.md) for all variants.

To override the HTTP status the server sends when the client does not meet the auth requirement, use `Endpoint#unauthorizedStatus`:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.{Method, Status}

val secured = Endpoint(Method.GET / "me")
  .auth(AuthType.Bearer)
  .unauthorizedStatus(Status.Unauthorized)
```

## Documentation

To attach a `Doc` value to the endpoint as a whole, use `.doc(documentation)`:

```scala mdoc:compile-only
import zio.blocks.docs.Doc
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val ep = Endpoint(Method.GET / "users")
  .doc(Doc.empty)
```

Documentation attached here flows through to OpenAPI generation and any other documentation interpreters.

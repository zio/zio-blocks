---
id: endpoint
title: "Endpoint"
---

`Endpoint[PathInput, Input, Err, Output, Auth]` is a pure descriptor for HTTP endpoints. It combines a route pattern with typed request input, typed error output, typed success output, authentication requirements, and documentation metadata.

At a high level, the DSL is designed to stay close to zio-http where that improves ergonomics:

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

## Overview

Endpoints are pure data. They describe an HTTP surface without committing to server or client interpretation up front.

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern.*
import zio.blocks.schema.Schema
import zio.http.{Method, Status}

val getUser = Endpoint(Method.GET / "users" / PathCodec.int("userId"))
  .query("verbose", Schema.boolean)
  .out(Schema.string)
  .outError(Status.NotFound, Schema.string)
  .auth(AuthType.Bearer)
```

## Route DSL

The primary route syntax is method-first:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern.*
import zio.http.Method

val route = Method.GET / "users" / PathCodec.uuid("userId")
```

This keeps the route readable and matches the shape users already expect from zio-http.

## Additive request and response builders

Request inputs, outputs, and error outputs are additive. Calling a builder adds another part instead of replacing the previous one.

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern.*
import zio.blocks.schema.Schema
import zio.http.{Method, Status}

val endpoint = Endpoint(Method.POST / "users")
  .in(Schema.string)
  .header("X-Trace", Schema.string)
  .out(Status.Created, Schema.int)
  .outError(Status.BadRequest, Schema.string)
```

## Scala 3 union errors with `orOutError`

On Scala 3, `orOutError` lets you accumulate error outputs into a real union type instead of nested `Either`s.

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern.*
import zio.blocks.schema.Schema
import zio.http.{Method, Status}

val endpoint = Endpoint(Method.GET / "users")
  .orOutError(Status.BadRequest, Schema.string)
  .orOutError(Status.Conflict, Schema.int)

val typed: Endpoint[Unit, Unit, String | Int, Unit, AuthType.None.type] = endpoint
```

This improves on zio-http's current Scala 3-only error DSL by making the fallback combinator itself union-aware internally instead of only relying on type inference at the call site.

Two constraints are intentional:

- `outError(...)` remains the cross-version additive API
- `orOutError(...)` rejects overlapping union members such as `String | String`

## Typed authentication

Authentication is part of the endpoint type. Built-in auth constructors keep the request requirement precise:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern.*
import zio.http.Method

val secured = Endpoint(Method.GET / "me").auth(AuthType.Bearer)

val authCodec = secured.auth.codec
```

This makes it possible to require bearer, basic, or digest auth without dropping down to raw string headers.

## Bulk endpoint creation with `endpoints { ... }`

The `endpoints` macro (Scala 3.7+) lets you define multiple `Endpoint` values in a block and access them by name on the returned `NamedTuple`. Member names are either explicit `val` names or auto-generated from the `RoutePattern.render` string (method prefix + path template).

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern.*
import zio.http.Method
import zio.blocks.schema.Schema
import zio.http.Status

val api = "api" / endpoints {
  val customer = Endpoint(Method.GET / "customers")
  Endpoint(Method.GET / "health")
}
```

Member access is static (zero runtime cost):

```scala mdoc:compile-only
val c: Endpoint[Unit, Unit, Unit, Unit, AuthType.None.type] = api.customer
val h: Endpoint[Unit, Unit, Unit, Unit, AuthType.None.type] = api.`GET /health`
```

Auto-naming follows `RoutePattern.render` exactly:

- `GET /user/{userId}` for path variables (RFC 6570 `{var}`)
- `GET#|POST /orders` for multi-method
- `v{major}` for `~` concat segments
- `...` for trailing segments
- `.unused` renders as `{name}`

Constant-prefix nesting bakes the prefix into each child's `RoutePattern` at the description level; grouping nodes have no path themselves:

```scala mdoc:compile-only
val v1 = "api" / endpoints {
  "v1" / endpoints {
    val users = Endpoint(Method.GET / "users")
  }
}
val u = v1.v1.users  // route.render == "GET /api/v1/users"
```

Path-variable prefixes are implemented the same way. A capturing prefix contributes its segment to every child's path, while children keep their relative auto-names:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern.*
import zio.http.Method

val byId = PathCodec.int("id") / endpoints {
  val get = Endpoint(Method.GET / "orders")
  Endpoint(Method.DELETE / "orders") // auto-named `DELETE /orders`
}

val getOrder: Endpoint[Int, Unit, Unit, Unit, AuthType.None.type] = byId.get
val delOrder = byId.`DELETE /orders`
```

Both children carry the captured segment: `byId.get.route.render == "GET /{id}/orders"` and the auto-named member renders `"DELETE /{id}/orders"`. Static types are preserved — `byId.get` is an `Endpoint[Int, Unit, Unit, Unit, AuthType.None.type]`, so the captured `id` will be delivered to handlers when routes are created on the zio-http side. A child with its own path variables composes positionally: under `int("id")`, `Endpoint(Method.GET / "orders" / PathCodec.int("orderId"))` renders as `GET /{id}/orders/{orderId}` and carries `(Int, Int)`:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern.*
import zio.http.Method

val ordersById = PathCodec.int("id") / endpoints {
  val o = Endpoint(Method.GET / "orders" / PathCodec.int("orderId"))
}

val lookup: Endpoint[(Int, Int), Unit, Unit, Unit, AuthType.None.type] = ordersById.o
// lookup.route.render == "GET /{id}/orders/{orderId}"
```

Variable prefixes should be bound to an explicit `val` — the val names the subgroup whose members you access through it (`byId.get`, `ordersById.o`). One known limitation: a constant-prefix subgroup directly inside a path-variable-prefixed block aborts compilation with guidance to use a flat block instead.

The returned type is a Scala 3 `NamedTuple` — static member access, erased at runtime. The DSL is Scala 3 only (3.7+ named tuples). All examples above compile against the `endpoint` module on Scala 3.8.3.

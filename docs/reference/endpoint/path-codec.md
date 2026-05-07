---
id: path-codec
title: "PathCodec"
---

`PathCodec[A]` is a composable descriptor for URL path structures. It holds a tree of segment codecs connected by concatenation and fallback nodes, and provides bidirectional path conversion: `PathCodec#decode` extracts a typed value from a `Path`, and `PathCodec#format` formats a typed value back to a `Path`. Its definition is:

```scala
sealed trait PathCodec[A]
```

## Motivation

URL paths need to be both matched and generated. A routing library that only matches paths requires a separate URL-builder for links and redirects, leading to duplication and drift. `PathCodec` is bidirectional: every codec that can decode `/users/42` into `42: Int` can also format `42` back into `/users/42`. This makes it safe to use the same path definition for routing, link generation, and OpenAPI path parameter documentation.

## Structure

`PathCodec` is an ADT with four node types:

| Node        | Meaning                                                        |
| ----------- | -------------------------------------------------------------- |
| `Segment`   | A single path segment, described by a `SegmentCodec[A]`        |
| `Concat`    | Two path codecs composed sequentially with `/` or `++`         |
| `Transform` | Bidirectional type mapping over an existing codec              |
| `Fallback`  | Two literal alternatives (used only with `orElse`)             |

## Construction

### Predefined segment constructors

The most common path building blocks are available as smart constructors on the companion:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._

val literalUsers: PathCodec[Unit]          = PathCodec.literal("users")
val intId: PathCodec[Int]                  = PathCodec.int("id")
val longId: PathCodec[Long]               = PathCodec.long("id")
val stringSlug: PathCodec[String]         = PathCodec.string("slug")
val uuidId: PathCodec[java.util.UUID]     = PathCodec.uuid("id")
val boolFlag: PathCodec[Boolean]          = PathCodec.bool("enabled")
val rest: PathCodec[zio.http.Path]        = PathCodec.trailing
```

`PathCodec.literal` is a macro that validates the value at compile time — it rejects empty strings and strings containing `/` or characters requiring URL encoding.

### From a string

To build a codec from a slash-separated string of literal segments, use `PathCodec.apply`:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._

val path: PathCodec[Unit] = PathCodec("/api/v1/users")
```

This is equivalent to concatenating `PathCodec.literal` for each segment.

### From a `SegmentCodec`

To wrap a custom `SegmentCodec[A]` into a `PathCodec[A]`, use `PathCodec.apply(segment)`:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._

val combined = PathCodec(SegmentCodec.literal("v") ~ SegmentCodec.int("version"))
```

There is also an implicit conversion from `SegmentCodec[A]` to `PathCodec[A]` and from `String` to `PathCodec[Unit]`, so both can appear directly in `/` expressions.

## Composition

### Sequential composition with `/` and `++`

`/` and `++` are equivalent: both concatenate two path codecs. The result type is flattened by `Tuples.WithOut` (so `Unit / Int` gives `Int`, not `(Unit, Int)`):

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._

val route: PathCodec[Int] = PathCodec.literal("users") / PathCodec.int("id")
```

In the context of a `RoutePattern`, the same operator works directly:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val pattern = Method.GET / "users" / PathCodec.int("id") / "posts"
```

### Literal alternatives with `orElse`

To match either of two literal segments, use `orElse`:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._

val either: PathCodec[Unit] =
  PathCodec.literal("users").orElse(PathCodec.literal("members"))
```

`orElse` is restricted to `PathCodec[Unit]` (literal-only) segments. `RouteTree` expands alternatives into separate trie branches via `PathCodec#alternatives`.

## Decoding and Formatting

### `PathCodec#decode`

To extract a typed value from a runtime `Path`:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Path

val codec = PathCodec.int("id")

val result: Either[String, Int] = codec.decode(Path("/42"))
```

`decode` returns `Left(message)` when no segment matches or a segment cannot be parsed.

### `PathCodec#format`

To turn a typed value into a `Path`:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Path

val codec = PathCodec.uuid("id")

val path: Either[String, Path] =
  codec.format(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
```

### `PathCodec#matches`

To test whether a `Path` matches without extracting a value:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Path

val codec   = PathCodec.literal("users")
val matched = codec.matches(Path("/users"))
```

## Type Transformations

### `PathCodec#transform`

To map the decoded value to a different type without changing the path structure, use `transform`. Both directions must be total:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.endpoint.PathCodec._

final case class UserId(value: Int)

val userIdCodec: PathCodec[UserId] =
  PathCodec.int("id").transform[UserId](UserId(_), _.value)
```

### `PathCodec#transformOrFail`

When decoding or encoding can fail, use `transformOrFail`. A `Left` from the decode function causes the path not to match:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._

val nonNegativeInt: PathCodec[Int] =
  PathCodec.int("count").transformOrFail[Int](
    n => if (n >= 0) Right(n) else Left(s"Expected non-negative, got $n"),
    n => Right(n)
  )
```

## Rendering

`PathCodec#render` produces a human-readable path string. Dynamic segments appear as `{name}` by default:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._

val codec = PathCodec.literal("users") / PathCodec.int("id")
val rendered: String = codec.render
```

To use a different prefix/suffix for dynamic segments (e.g., `:id` for Express-style paths), call the lower-level `PathCodec.render(codec, prefix = ":", suffix = "")`.

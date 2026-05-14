---
id: json-selection
title: "JsonSelection"
---

`JsonSelection` is a fluent wrapper type that enables composable, chainable navigation through JSON structures. It wraps `Either[SchemaError, Chunk[Json]]`, allowing operations that may fail gracefully or return multiple values.

## Overview

`JsonSelection` makes it easy to navigate unknown or deeply nested JSON at runtime without needing to match on `Either` at each step. Chain operations like `.get(key)`, `.apply(index)`, `.filter()`, and `.as[Type]` to build powerful queries.

**Key characteristics:**
- **Fluent chaining:** Operations chain naturally without unwrapping intermediate results
- **Multi-value support:** Can contain zero, one, or many `Json` values
- **Error propagation:** Errors short-circuit further operations; querying an error selection returns the same error
- **Type extraction:** Use `.as[Type]` to decode to Scala types with Schema-based derivation

## Creating JsonSelection

### From Json Values

**Direct construction:**

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonSelection}

val json = Json.parseUnsafe("""{"name": "Alice"}""")

// Get a single field
val name: JsonSelection = json.get("name")  // Right(Chunk(Json.String("Alice")))

// Get multiple array elements
val values = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
val selected: JsonSelection = values.apply(0)  // Right(Chunk(Json.Number(1)))
```

### From Companion Object

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.{Json, JsonSelection}

// Empty successful selection
val empty = JsonSelection.empty

// Succeed with a single value
val success = JsonSelection.succeed(Json.String("hello"))

// Succeed with multiple values
val many = JsonSelection.succeedMany(
  zio.blocks.chunk.Chunk.from(Seq(Json.Number(1), Json.Number(2)))
)

// Fail with an error
val failure = JsonSelection.fail(SchemaError("not found"))
```

## Navigation Operations

### Field Navigation

```scala mdoc:compile-only
import zio.blocks.schema.json.Json

val data = Json.Object(
  "user" -> Json.Object(
    "name" -> Json.String("Bob"),
    "email" -> Json.String("bob@example.com")
  )
)

// Single field access
val user = data.get("user")

// Chained field access
val name = data.get("user").get("name")  // JsonSelection

// Access with String key (shorthand)
val email = data("user")("email")
```

### Array Navigation

```scala mdoc:compile-only
import zio.blocks.schema.json.Json

val data = Json.Array(
  Json.Object("id" -> Json.Number(1), "name" -> Json.String("Alice")),
  Json.Object("id" -> Json.Number(2), "name" -> Json.String("Bob"))
)

// Index access (0-based)
val first = data(0)  // First array element

// Chained index and field access
val firstName = data(0).get("name")  // "Alice"
val secondId = data(1).get("id")     // 2
```

### Path-Based Navigation

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.Json

val data = Json.Object(
  "company" -> Json.Object(
    "employees" -> Json.Array(
      Json.Object("name" -> Json.String("Alice"), "department" -> Json.String("Engineering")),
      Json.Object("name" -> Json.String("Bob"), "department" -> Json.String("Sales"))
    )
  )
)

// Navigate using path interpolator
val path = p".company.employees[0].name"
val firstEmpName = data.get(path)  // JsonSelection(Right(Chunk(Json.String("Alice"))))
```

## Filtering and Querying

### Filter by Type

```scala mdoc:compile-only
import zio.blocks.schema.json.Json

val mixed = Json.Array(
  Json.String("text"),
  Json.Number(42),
  Json.Boolean(true),
  Json.String("more text")
)

// Keep only strings
val strings = mixed.strings    // JsonSelection with two strings
val numbers = mixed.numbers    // JsonSelection with one number
val booleans = mixed.booleans  // JsonSelection with one boolean
```

### Filter with Predicate

```scala mdoc:compile-only
import zio.blocks.schema.json.Json

val data = Json.Array(
  Json.Number(1),
  Json.Number(2),
  Json.Number(3),
  Json.Number(4)
)

// Filter elements (select only even numbers)
val evenOnly = data.filter { json =>
  json match {
    case Json.Number(n) => n.toInt % 2 == 0
    case _ => false
  }
}
```

## Extracting Values

### Type Decoding

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.Json

// Decode to Scala types
val selection = Json.parseUnsafe("""{"count": 42}""")

val count: Either[SchemaError, Int] = selection.get("count").as[Int]
val str: Either[SchemaError, String] = selection.get("count").as[String]  // Left (type mismatch)
```

### Multiple Decoding

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.Json

val data = Json.Array(
  Json.String("Alice"),
  Json.String("Bob"),
  Json.String("Charlie")
)

// Decode all strings
val names: Either[SchemaError, Seq[String]] = data
  .strings
  .chunk
  .map(_.map {
    case Json.String(s) => s
  }.toSeq)
```

### Extract Single Value

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.Json

val arr = Json.Array(Json.Number(1), Json.Number(2))

// Get exactly one value (fails if 0 or more than 1)
val single = arr(0).one  // Right(Json.Number(1))
val multiple = arr.one   // Left(SchemaError("expected single value but got 2"))
```

### Check and Get

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonSelection}

val data = Json.Object("x" -> Json.Number(1))

// Safe option extraction
val x: Option[Json] = data.get("x").values.flatMap(_.headOption)

// Check if selection succeeded
val success = data.get("exists").isSuccess    // true if "exists" field found
val failed = data.get("notFound").isFailure   // true if field not found
```

## Size and Existence Checks

```scala mdoc:compile-only
import zio.blocks.schema.json.Json

val data = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))

val size = data.size        // 3
val isEmpty = data.isEmpty  // false
val nonEmpty = data.nonEmpty  // true
```

## Modifying Selections

### Updating Values

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.Json

val original = Json.Object("count" -> Json.Number(0))

// Set a new value
val updated = original.set(p".count", Json.Number(1))

// Modify with a function
val modified = original.modify(p".count") {
  case Json.Number(n) => Json.Number(n + 1)
  case other => other
}
```

## Error Handling

Selections propagate errors through the chain:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.Json

val data = Json.Object("name" -> Json.String("Alice"))

// Missing field returns error
val missing = data.get("age").error  // Some(SchemaError("field not found"))

// Chain continues with error
val stillMissing = data.get("age").get("nested")  // Still carries the error

// Check error state
val result = data.get("x")
val maybeError: Option[SchemaError] = result.error
val maybeValues = result.values      // None if error
```

## Integration with Codecs

`JsonSelection` integrates seamlessly with `JsonCodec` and `Schema`:

```scala mdoc:compile-only
import zio.blocks.schema._

case class User(name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val users = Json.Array(
  Json.Object("name" -> Json.String("Alice"), "email" -> Json.String("alice@example.com")),
  Json.Object("name" -> Json.String("Bob"), "email" -> Json.String("bob@example.com"))
)

// Navigate and decode in one chain
val firstUser: Either[SchemaError, User] = users(0).as[User]
val allUsers: Either[SchemaError, Seq[User]] = users
  .chunk
  .map(_.flatMap {
    case obj: Json.Object => Some(obj)
    case _ => None
  })
```

## Performance Notes

- **Zero-allocation navigation:** `JsonSelection` itself is a value type (AnyVal) and compiles to no allocation
- **Lazy chaining:** Operations chain without intermediate allocations; only final extraction materializes values
- **Error short-circuiting:** Failed selections don't execute further operations
- **Streaming-friendly:** For large JSON, use `get` selectively rather than iterating over all values

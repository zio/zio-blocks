---
id: json
title: "Json"
---

`Json` is a simple, immutable Abstract Syntax Tree (AST) for representing JSON data in ZIO Blocks. It serves as a user-friendly bridge between raw JSON strings and the internal `DynamicValue` representation.

```scala
sealed trait Json
```

## The Json ADT

The `Json` data type is an algebraic data type (ADT) with six cases, corresponding to the standard JSON types:

```scala
object Json {
  final case class Object(fields: Vector[(String, Json)]) extends Json
  final case class Array(elements: Vector[Json])          extends Json
  final case class String(value: java.lang.String)        extends Json
  final case class Number(value: java.lang.String)        extends Json
  final case class Boolean(value: scala.Boolean)          extends Json
  case object Null                                        extends Json
}
```

### Construction

You can construct `Json` values manually using the case classes, or more conveniently using the `json""` interpolator (see below).

```scala mdoc:compile-only
import zio.blocks.schema.json.Json

val manual = Json.Object(Vector(
  "name" -> Json.String("Alice"),
  "age"  -> Json.Number("30"),
  "tags" -> Json.Array(Vector(Json.String("developer"), Json.String("scala")))
))
```

## JSON Interpolator

ZIO Blocks provides a powerful `json""` string interpolator that allows you to write JSON directly in your code. It supports variable embedding and ensures safety.

```scala mdoc:compile-only
import zio.blocks.schema.json.Json
import zio.blocks.schema.json.JsonInterpolators._

val name = "Alice"
val age  = 30
val tags = Vector("developer", "scala")

val user: Json = json"""
  {
    "name": $name,
    "age": $age,
    "tags": $tags,
    "active": true
  }
"""
```

The interpolator automatically handles:
- **Escaping**: Strings containing quotes are safely escaped.
- **Type Conversion**: Scala types (`Int`, `String`, `Boolean`, `Seq`, etc.) are automatically converted to their `Json` equivalents.
- **Nesting**: You can embed `Json` objects inside other `Json` objects.

## Parsing and Encoding

You can parse raw JSON strings into `Json` values and encode `Json` values back to strings.

```scala mdoc:compile-only
import zio.blocks.schema.json.Json

// Parsing
val raw = """{"foo": "bar"}"""
val parsed: Either[JsonError, Json] = Json.parse(raw)

// Encoding
val json = Json.Object(Vector("foo" -> Json.String("bar")))
val string: String = Json.encode(json)
```

These operations use the high-performance `JsonBinaryCodec` under the hood, ensuring efficiency.

## Typed API

The `Json` data type provides convenience methods for converting to and from typed values using `JsonDecoder` and `JsonEncoder`.

```scala mdoc:compile-only
import zio.blocks.schema.json.Json

// Decode: Json -> A
val json = Json.Number("42")
val intVal: Either[JsonError, Int] = json.as[Int]

// Encode: A -> Json
val jsonVal: Json = Json.from(42)
```

## Navigation and Modification

`Json` supports powerful navigation and modification using `DynamicOptic`. You can drill down into nested structures to retrieve or update values.

### Selection

```scala mdoc:compile-only
import zio.blocks.schema.json.Json
import zio.blocks.schema.DynamicOptic

val json = json"""{"users": [{"name": "Alice"}]}"""

// Simple access
val users = json("users") // JsonSelection

// Path-based access
val firstUserName = json.get(
  DynamicOptic.Field("users") / DynamicOptic.Index(0) / DynamicOptic.Field("name")
)
```

### Modification

Since `Json` is immutable, modification methods return a *new* `Json` instance with the changes applied.

```scala mdoc:compile-only
import zio.blocks.schema.json.Json
import zio.blocks.schema.DynamicOptic

val json = json"""{"count": 1}"""
val path = DynamicOptic.Field("count")

// Set a value
val updated = json.set(path, Json.Number("2"))

// Modify a value
val incremented = json.modify(path, {
  case Json.Number(s) => Json.Number((s.toInt + 1).toString)
  case other          => other
})
```

## DynamicValue Conversion

`Json` serves as a bridge to ZIO Blocks' `DynamicValue`. You can losslessly convert between them.

```scala mdoc:compile-only
import zio.blocks.schema.json.Json
import zio.blocks.schema.DynamicValue

val json = json"""{"a": 1}"""

// Convert to DynamicValue
val dynamic: DynamicValue = json.toDynamicValue

// Convert from DynamicValue
val backToJson: Json = Json.fromDynamicValue(dynamic)
```

This integration allows `Json` to be used seamlessly with other ZIO Blocks features that rely on `DynamicValue`, such as schema derivation and transformation.

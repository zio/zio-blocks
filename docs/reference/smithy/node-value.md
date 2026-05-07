---
id: node-value
title: "NodeValue"
---

`NodeValue` is a sealed trait representing JSON-like values that appear in Smithy models. You use `NodeValue` when working with trait attributes (the parameters passed to `@trait` declarations), model metadata, and any other node structures. It supports primitives (strings, numbers, booleans, null) and collections (arrays and objects).

## Motivation

Smithy traits are metadata annotations that attach structured data to shapes and members. For example, `@range(min: 0, max: 100)` attaches a range constraint, and `@http(method: "GET", uri: "/users/{id}")` specifies HTTP binding information. These trait values are arbitrary JSON-like structures, so `NodeValue` provides a type-safe way to represent them without parsing JSON strings every time.

`NodeValue` is also used for:
- **Model-level metadata** — Key-value pairs stored in `SmithyModel.metadata`
- **Trait attributes** — Parameters passed to trait applications
- **Example values** — Example payloads in documentation traits

## NodeValue Variants

`NodeValue` is a sealed trait with six subtypes:

| Subtype | Purpose |
| ------- | ------- |
| `NodeValue.String` | UTF-8 text |
| `NodeValue.Number` | Numeric values (BigDecimal for arbitrary precision) |
| `NodeValue.Boolean` | `true` or `false` |
| `NodeValue.Null` | The null value (singleton) |
| `NodeValue.Array` | Ordered list of `NodeValue`s |
| `NodeValue.Object` | Key-value pairs (list of tuples) |

## Creating NodeValues

### Primitive Values

```scala mdoc:compile-only
import zio.blocks.smithy._

val stringValue = NodeValue.String("example API")
val numberValue = NodeValue.Number(BigDecimal(42))
val boolValue = NodeValue.Boolean(true)
val nullValue = NodeValue.Null
```

### Collections

Create arrays and objects by nesting `NodeValue`s:

```scala mdoc:compile-only
import zio.blocks.smithy._

val arrayValue = NodeValue.Array(List(
  NodeValue.String("GET"),
  NodeValue.String("POST"),
  NodeValue.String("PUT")
))

val objectValue = NodeValue.Object(List(
  "method" -> NodeValue.String("GET"),
  "uri" -> NodeValue.String("/users/{id}"),
  "responseCode" -> NodeValue.Number(BigDecimal(200))
))
```

## Using NodeValue in Models

### Model Metadata

Store key-value pairs in `SmithyModel.metadata`:

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel(
  version = "2.0",
  namespace = "com.example.api",
  useStatements = Nil,
  metadata = Map(
    "apiVersion" -> NodeValue.String("1.0.0"),
    "title" -> NodeValue.String("User Management API"),
    "maxRetries" -> NodeValue.Number(BigDecimal(3)),
    "experimental" -> NodeValue.Boolean(false)
  ),
  shapes = Nil
)
```

### Trait Attributes

Traits apply structured metadata to shapes using `NodeValue`:

```scala mdoc:compile-only
import zio.blocks.smithy._

val trait1 = TraitApplication(
  id = ShapeId("smithy.api", "range"),
  value = Some(NodeValue.Object(List(
    "min" -> NodeValue.Number(BigDecimal(0)),
    "max" -> NodeValue.Number(BigDecimal(100))
  )))
)

val trait2 = TraitApplication.documentation("This is a documented shape")

val shapeDef = ShapeDefinition(
  "Age",
  StringShape("Age", traits = List(trait1, trait2))
)
```

## Inspecting NodeValues

### Pattern Matching

Use pattern matching to extract values from `NodeValue`:

```scala mdoc:compile-only
import zio.blocks.smithy._

def describeValue(value: NodeValue): String = value match {
  case NodeValue.String(s)   => s"String: '$s'"
  case NodeValue.Number(n)   => s"Number: $n"
  case NodeValue.Boolean(b)  => s"Boolean: $b"
  case NodeValue.Null        => "Null"
  case NodeValue.Array(vs)   => s"Array[${vs.length}]"
  case NodeValue.Object(fs)  => s"Object{${fs.length} fields}"
}

val examples = List(
  NodeValue.String("hello"),
  NodeValue.Number(BigDecimal(42)),
  NodeValue.Boolean(true)
)

examples.foreach(v => println(describeValue(v)))
```

### Extracting Nested Values

For objects and arrays, recursively extract nested values:

```scala mdoc:compile-only
import zio.blocks.smithy._

val metadata = NodeValue.Object(List(
  "version" -> NodeValue.String("1.0"),
  "limits" -> NodeValue.Object(List(
    "maxSize" -> NodeValue.Number(BigDecimal(1000)),
    "maxItems" -> NodeValue.Number(BigDecimal(100))
  ))
))

def getStringField(obj: NodeValue.Object, key: String): Option[String] = {
  obj.fields
    .find(_._1 == key)
    .flatMap {
      case (_, NodeValue.String(s)) => Some(s)
      case _                         => None
    }
}

metadata match {
  case obj: NodeValue.Object =>
    getStringField(obj, "version").foreach(v => println(s"Version: $v"))
  case _ => ()
}
```

## Common Patterns

### Building Trait Values

Construct complex trait attributes step by step:

```scala mdoc:compile-only
import zio.blocks.smithy._

def httpTrait(method: String, uri: String): TraitApplication = {
  TraitApplication.http(method, uri)
}

val getTrait = httpTrait("GET", "/users/{id}")
val postTrait = httpTrait("POST", "/users")
```

### Representing Enum Traits

Trait values that represent fixed sets:

```scala mdoc:compile-only
import zio.blocks.smithy._

val timeFormatTrait = TraitApplication(
  id = ShapeId("smithy.api", "timestampFormat"),
  value = Some(NodeValue.String("dateTime"))
)

val jsonNameTrait = TraitApplication(
  id = ShapeId("smithy.api", "jsonName"),
  value = Some(NodeValue.String("user_id"))
)
```

### Metadata Collections

Store multiple metadata values as arrays:

```scala mdoc:compile-only
import zio.blocks.smithy._

val tagsTrait = TraitApplication(
  id = ShapeId("smithy.api", "tags"),
  value = Some(NodeValue.Array(List(
    NodeValue.String("internal"),
    NodeValue.String("deprecated"),
    NodeValue.String("api-v1")
  )))
)
```

## Working with Parsed Models

When parsing Smithy IDL, trait values are automatically extracted as `NodeValue`s:

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel.parse("""$version: "2"
namespace com.example

@range(min: 0, max: 120)
integer Age

@http(method: "GET", uri: "/users")
operation GetUsers {
  output: UserList
}

list UserList {
  member: User
}

structure User {
  id: String
}
""").toOption.get

model.findShape("Age").foreach { shapeDef =>
  shapeDef.shape.traits.foreach { trait_ =>
    println(s"Trait: ${trait_.id}")
    println(s"Value: ${trait_.value}")
  }
}
```

## Integration with Other Module Types

`NodeValue` appears throughout the smithy-blocks module:

- **`SmithyModel.metadata`** — Stores model-level metadata as `Map[String, NodeValue]`
- **`TraitApplication`** — Trait values are `Option[NodeValue]`, representing attribute data
- **`Shape.traits`** — Every shape has traits; each trait's value is a `NodeValue`
- **`SmithyParser`** — Converts Smithy IDL trait syntax into `NodeValue` structures
- **`SmithyPrinter`** — Serializes `NodeValue` instances back to valid IDL syntax

Typical workflow:

```
Smithy IDL (@range(min: 0, max: 100))
            ↓
SmithyParser (extracts trait value)
            ↓
NodeValue.Object([
  ("min", NodeValue.Number(0)),
  ("max", NodeValue.Number(100))
])
            ↓
TraitApplication (wraps the value)
            ↓
Shape.traits (attached to the shape)
```

## Type Safety and Precision

`NodeValue.Number` uses `BigDecimal` to support arbitrary-precision arithmetic. This ensures that large or fractional numbers are represented accurately:

```scala mdoc:compile-only
import zio.blocks.smithy._

val largeNumber = NodeValue.Number(BigDecimal("999999999999999999999"))
val fraction = NodeValue.Number(BigDecimal("3.14159265359"))

println(s"Large: $largeNumber")
println(s"Fraction: $fraction")
```

## Related Documentation

- [TraitApplication](./trait-application.md) — Containers for trait values (which are `NodeValue`s)
- [SmithyModel](./smithy-model.md) — Contains metadata stored as `Map[String, NodeValue]`
- [Shape](./shape.md) — All shapes have traits; trait values are `NodeValue`s
- [SmithyParser](./smithy-parser.md) — Extracts `NodeValue` from IDL trait syntax
- [SmithyPrinter](./smithy-printer.md) — Serializes `NodeValue` back to IDL

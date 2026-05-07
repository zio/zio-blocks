---
id: smithy-model
title: "SmithyModel"
---

`SmithyModel` is the root container for a complete Smithy API definition. It holds the Smithy specification version, target namespace, all shape definitions, metadata, and trait applications. As the core entry point, `SmithyModel` ties together all other smithy-blocks types: you parse IDL text into a `SmithyModel`, query and traverse shapes within it, and serialize it back to valid IDL.

## Motivation

When building tools that work with Smithy API models (code generators, validators, documentation generators), you need a structured representation of the entire model in memory. `SmithyModel` provides that: a strongly-typed container that keeps versions, namespaces, shapes, and traits organized and queryable.

Rather than working with raw JSON or unstructured text, `SmithyModel` lets you:
- **Parse IDL programmatically** — Load and validate models at runtime
- **Query shapes by name** — Find any shape definition instantly
- **Traverse relationships** — Walk from services to operations to types
- **Serialize back to text** — Round-trip models with `prettyPrint()`
- **Attach metadata** — Store key-value pairs and apply trait statements

## Construction

### Direct Construction

Create a `SmithyModel` by providing all required fields:

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel(
  version = "2.0",
  namespace = "com.example.api",
  useStatements = List(ShapeId("smithy.api", "String")),
  metadata = Map(
    "apiVersion" -> NodeValue.String("1.0.0"),
    "title" -> NodeValue.String("Example API")
  ),
  shapes = List(
    ShapeDefinition("User", StructureShape("User", Nil, Nil)),
    ShapeDefinition("UserId", StringShape("UserId", Nil))
  ),
  applyStatements = Nil
)
```

### Parsing from IDL Text

The most common way to construct a `SmithyModel` is by parsing Smithy IDL text:

```scala mdoc:compile-only
import zio.blocks.smithy._

val smithyText = """$version: "2"
namespace com.example.api

structure User {
  @required
  id: String
  name: String
}
"""

val result = SmithyModel.parse(smithyText)
result match {
  case Right(model) => println(s"Parsed ${model.shapes.length} shapes")
  case Left(error)  => println(s"Parse error: ${error.message}")
}
```

## Core Operations

### Querying Shapes

Find shapes by name using `findShape`:

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel.parse("""$version: "2"
namespace com.example

structure User { id: String }
operation GetUser { input: UserId, output: User }
""").toOption.get

val userShape = model.findShape("User")
userShape match {
  case Some(definition) => println(s"Found shape: ${definition.name}")
  case None             => println("User shape not found")
}
```

### Getting All Shape IDs

Retrieve a list of all shapes in the model as `ShapeId`s (namespace + name):

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel(
  version = "2.0",
  namespace = "com.example",
  useStatements = Nil,
  metadata = Map.empty,
  shapes = List(
    ShapeDefinition("User", StringShape("User", Nil)),
    ShapeDefinition("Order", StringShape("Order", Nil))
  )
)

val ids = model.allShapeIds
ids.foreach(id => println(s"${id.namespace}#${id.name}"))
```

### Pretty-Printing Back to IDL

Serialize the model back to valid Smithy IDL text:

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel(
  version = "2.0",
  namespace = "com.example",
  useStatements = Nil,
  metadata = Map.empty,
  shapes = List(ShapeDefinition("Id", StringShape("Id", Nil)))
)

val idlText = model.prettyPrint
println(idlText)
```

You can also control indentation:

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel(
  version = "2.0",
  namespace = "example",
  useStatements = Nil,
  metadata = Map.empty,
  shapes = Nil
)

val indented = model.prettyPrint(indent = 2)
```

## Key Fields and Their Roles

| Field | Type | Purpose |
| ----- | ---- | ------- |
| `version` | `String` | Smithy spec version (e.g., `"2.0"`) |
| `namespace` | `String` | Namespace for all shapes (e.g., `"com.example.api"`) |
| `useStatements` | `List[ShapeId]` | Shapes imported via `use` statements |
| `metadata` | `Map[String, NodeValue]` | Model-level metadata (title, version, etc.) |
| `shapes` | `List[ShapeDefinition]` | All defined shapes in the model |
| `applyStatements` | `List[ApplyStatement]` | Trait applications added via `apply` blocks |

## Integration with Other Module Types

`SmithyModel` is the integration hub for the entire smithy-blocks module:

- **`SmithyParser`** — Converts Smithy IDL text into a `SmithyModel`
- **`SmithyPrinter`** — Converts a `SmithyModel` back to valid IDL text
- **`Shape`** — Sealed trait representing the 25+ Smithy shape types; accessed via `ShapeDefinition.shape`
- **`ShapeId`** — Identifies shapes by namespace and name; used in `useStatements` and returned by `allShapeIds`
- **`NodeValue`** — Represents trait attribute values; used in the `metadata` map
- **`TraitApplication`** — Attaches metadata to shapes; used in `applyStatements`

Typical workflow:

```
Smithy IDL Text
    ↓
SmithyParser.parse()  [converts text to model]
    ↓
SmithyModel  [root container]
    ├─ findShape(name)  [query operation]
    ├─ allShapeIds  [introspection]
    └─ prettyPrint()  [serialize back to text]
```

## Accessing Shape Details

To work with individual shapes, use `findShape` and pattern-match on the `Shape` type:

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel.parse("""$version: "2"
namespace com.example

structure User {
  @required
  id: String
  name: String
}
""").toOption.get

model.findShape("User") match {
  case Some(shapeDef) =>
    shapeDef.shape match {
      case struct: StructureShape =>
        println(s"User has ${struct.members.length} members")
      case _ =>
        println("User is not a structure")
    }
  case None =>
    println("User shape not found")
}
```

## Related Documentation

- [Shape](./shape.md) — Sealed trait for Smithy shape types
- [ShapeId](./shape-id.md) — Shape identifiers (namespace + name)
- [SmithyParser](./smithy-parser.md) — Parsing IDL text to models
- [SmithyPrinter](./smithy-printer.md) — Serializing models to IDL

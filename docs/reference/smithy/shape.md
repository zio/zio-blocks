---
id: shape
title: "Shape"
---

`Shape` is a sealed trait representing all 25+ shape types defined by the Smithy specification. Every shape has a name and an optional list of traits applied to it. Shapes are categorized into four main groups: simple shapes (primitives), enum shapes, aggregate shapes (collections), and service shapes (API definitions). You use `Shape` when inspecting the structure of a parsed model or building shapes programmatically.

## Motivation

Smithy defines over 25 different shape types, each representing a different semantic concept (a string, a structure, an HTTP service, a resource, etc.). Rather than parsing these types as unstructured data, `Shape` provides a strongly-typed sealed trait hierarchy that groups related types and ensures exhaustiveness when pattern-matching. This makes it safe to inspect and transform shape definitions without runtime errors.

## Shape Categories

### Simple Shapes

Primitive types representing fundamental scalar values:

| Type | Purpose |
| ---- | ------- |
| `StringShape` | UTF-8 text |
| `BlobShape` | Binary data |
| `BooleanShape` | True/false values |
| `ByteShape` | 8-bit signed integer |
| `ShortShape` | 16-bit signed integer |
| `IntegerShape` | 32-bit signed integer |
| `LongShape` | 64-bit signed integer |
| `FloatShape` | Single-precision floating point |
| `DoubleShape` | Double-precision floating point |
| `BigIntegerShape` | Arbitrary-precision integer |
| `BigDecimalShape` | Arbitrary-precision decimal |
| `TimestampShape` | Point in time |
| `DocumentShape` | Protocol-agnostic open content |

### Enum Shapes

Fixed sets of predefined values:

| Type | Purpose |
| ---- | ------- |
| `EnumShape` | String enumeration (variants with optional values) |
| `IntEnumShape` | Integer enumeration (variants with integer values) |

### Aggregate Shapes

Collections of heterogeneous or homogeneous members:

| Type | Purpose |
| ---- | ------- |
| `ListShape` | Ordered homogeneous collection |
| `MapShape` | Key-value pairs with typed keys and values |
| `StructureShape` | Fixed set of named, heterogeneous members |
| `UnionShape` | Tagged union — exactly one member is set |

### Service Shapes

API definitions and operations:

| Type | Purpose |
| ---- | ------- |
| `ServiceShape` | API service entry point with operations and resources |
| `OperationShape` | Single API operation with input, output, and errors |
| `ResourceShape` | RESTful resource with lifecycle operations and child resources |

## Common Shape Fields

Every `Shape` subclass has at least these fields:

| Field | Type | Purpose |
| ----- | ---- | ------- |
| `name` | `String` | The shape name (e.g., `"User"`, `"GetUserInput"`) |
| `traits` | `List[TraitApplication]` | Traits attached to this shape |

Aggregate and service shapes have additional fields specific to their category (e.g., `members` for `StructureShape`, `operations` for `ServiceShape`).

## Inspecting Shapes via Pattern Matching

To work with a `Shape`, use pattern matching to determine its type:

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel.parse("""$version: "2"
namespace example

structure User {
  id: String
  name: String
}

list UserIds {
  member: String
}
""").toOption.get

model.findShape("User") match {
  case Some(shapeDef) =>
    shapeDef.shape match {
      case struct: StructureShape =>
        println(s"${struct.name} has ${struct.members.length} members")
      case _ =>
        println(s"${shapeDef.shape.name} is not a structure")
    }
  case None =>
    println("Shape not found")
}
```

## Constructing Shapes Programmatically

Create shape instances directly for building models:

```scala mdoc:compile-only
import zio.blocks.smithy._

val stringShape = StringShape("Description")

val userStructure = StructureShape(
  "User",
  traits = Nil,
  members = List(
    MemberDefinition("id", ShapeId("smithy.api", "String")),
    MemberDefinition("name", ShapeId("smithy.api", "String"))
  )
)

val model = SmithyModel(
  version = "2.0",
  namespace = "example",
  useStatements = Nil,
  metadata = Map.empty,
  shapes = List(
    ShapeDefinition("User", userStructure)
  )
)
```

## Accessing Members (Collections and Structures)

For shapes that contain members, access them directly:

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel.parse("""$version: "2"
namespace example

structure User {
  id: String
  name: String
}
""").toOption.get

model.findShape("User").foreach { shapeDef =>
  shapeDef.shape match {
    case struct: StructureShape =>
      struct.members.foreach { member =>
        println(s"Member: ${member.name} -> ${member.target}")
      }
    case _ => ()
  }
}
```

## Working with Service Shapes

Service shapes bind operations and resources:

```scala mdoc:compile-only
import zio.blocks.smithy._

val serviceShape = ServiceShape(
  "MyService",
  traits = Nil,
  version = Some("1.0"),
  operations = List(
    ShapeId("example", "GetUser"),
    ShapeId("example", "CreateUser")
  ),
  resources = Nil,
  errors = Nil
)
```

## Working with Enum Shapes

Enum shapes define a fixed set of values:

```scala mdoc:compile-only
import zio.blocks.smithy._

val statusEnum = EnumShape(
  "Status",
  traits = Nil,
  members = List(
    EnumMember("Active"),
    EnumMember("Inactive"),
    EnumMember("Pending")
  )
)

val httpMethodEnum = IntEnumShape(
  "HttpMethod",
  traits = Nil,
  members = List(
    IntEnumMember("GET", 1),
    IntEnumMember("POST", 2),
    IntEnumMember("PUT", 3)
  )
)
```

## Traversing the Shape Hierarchy

For aggregate shapes, traverse members and resolve their targets:

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel.parse("""$version: "2"
namespace example

structure User {
  id: String
  profile: Profile
}

structure Profile {
  bio: String
}
""").toOption.get

def traverseShape(shape: Shape, model: SmithyModel): Unit = {
  shape match {
    case struct: StructureShape =>
      struct.members.foreach { member =>
        println(s"Resolving ${member.name}...")
        model.findShape(member.target.name).foreach { targetDef =>
          traverseShape(targetDef.shape, model)
        }
      }
    case other =>
      println(s"Leaf shape: ${other.name}")
  }
}

model.findShape("User").foreach { userDef =>
  traverseShape(userDef.shape, model)
}
```

## Checking for Traits

Inspect which traits are applied to a shape:

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel.parse("""$version: "2"
namespace example

@deprecated
structure User {
  @required
  id: String
  name: String
}
""").toOption.get

model.findShape("User").foreach { shapeDef =>
  val traits = shapeDef.shape.traits
  val hasDeprecated = traits.exists(_.id.name == "deprecated")
  println(s"User is deprecated: $hasDeprecated")
}
```

## Integration with Other Module Types

`Shape` is central to the smithy-blocks architecture:

- **`SmithyModel`** — Contains `ShapeDefinition` objects that wrap each `Shape`
- **`ShapeId`** — Used to reference shapes within aggregate and service shapes
- **`TraitApplication`** — Attached to every shape via the `traits` field
- **`MemberDefinition`** — Defines members within structures, lists, maps, and unions
- **`SmithyParser`** — Converts Smithy IDL text into concrete `Shape` instances
- **`SmithyPrinter`** — Serializes `Shape` instances back to valid IDL

Typical workflow:

```
Smithy IDL Text → SmithyParser → SmithyModel
                                    ↓
                            List[ShapeDefinition]
                                    ↓
                          ShapeDefinition.shape
                                    ↓
                              Shape (sealed trait)
                                    ├─ Pattern match on subtype
                                    ├─ Inspect members, traits
                                    └─ Transform or query
```

## Related Documentation

- [SmithyModel](./smithy-model.md) — The root container for all shapes
- [ShapeId](./shape-id.md) — Shape identifiers used to reference shapes
- [MemberDefinition](./shape.md#working-with-aggregate-shapes) — Members within shapes
- [TraitApplication](./trait-application.md) — Metadata attachments to shapes
- [SmithyParser](./smithy-parser.md) — Parsing IDL to shapes
- [SmithyPrinter](./smithy-printer.md) — Serializing shapes to IDL

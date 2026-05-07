---
id: smithy
title: "Smithy"
---

`zio-blocks-smithy` is a **Smithy IDL parser and AST library** providing a complete representation of Smithy 2.0 API models. It enables parsing Smithy IDL text into rich data structures, querying shape definitions, and pretty-printing models back to valid IDL syntax—all without external dependencies.

## Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-smithy" % "@VERSION@"
```

Supported Scala versions: 2.13.x and 3.x

## Quick Start

Parse Smithy IDL text into a model, query shapes, and serialize back:

```scala mdoc:compile-only
import zio.blocks.smithy._

val smithyText = """$version: "2"
namespace com.example.api

structure User {
  @required
  id: String
  name: String
}

operation GetUser {
  input: GetUserInput
  output: User
}

structure GetUserInput {
  @required
  id: String
}
"""

// Parse IDL text into a model
val result = SmithyModel.parse(smithyText)

// Access shapes and data
result match {
  case Right(model) =>
    model.findShape("User").foreach { userDef =>
      println(s"Found shape: ${userDef.name}")
    }
  case Left(error) =>
    println(s"Parse error: ${error.message}")
}

// Serialize back to IDL
result.foreach { model =>
  val idlText = model.prettyPrint
  println(idlText)
}
```

## Core Types

The library provides five core types that work together to parse, query, and serialize Smithy models.

### SmithyModel

The root container for a Smithy model. Contains version, namespace, shapes, metadata, and trait applications.

**Key Methods**:
- `parse(input: String): Either[SmithyError, SmithyModel]` — Parse IDL text
- `findShape(name: String): Option[ShapeDefinition]` — Find shape by name
- `allShapeIds: List[ShapeId]` — Get all shape identifiers
- `prettyPrint: String` — Serialize to IDL text
- `prettyPrint(indent: Int): String` — Serialize with custom indentation

### Shape (Sealed Trait)

Represents the 25+ Smithy shape types, organized in four categories:

**Simple Shapes** (primitives): `StringShape`, `BlobShape`, `BooleanShape`, `ByteShape`, `ShortShape`, `IntegerShape`, `LongShape`, `FloatShape`, `DoubleShape`, `BigIntegerShape`, `BigDecimalShape`, `TimestampShape`, `DocumentShape`

**Aggregate Shapes** (collections): `ListShape`, `MapShape`, `StructureShape`, `UnionShape`

**Enum Shapes**: `EnumShape`, `IntEnumShape`

**Service Shapes** (API definitions): `ServiceShape`, `OperationShape`, `ResourceShape`

Every shape has: `name: String` and `traits: List[TraitApplication]`

### ShapeId

A globally unique identifier for shapes: `namespace#name` (e.g., `"smithy.api#String"`, `"com.example#User"`).

**Also supports member references**: `namespace#shape$member` for referencing specific members within shapes.

**Construction**:
```scala mdoc:compile-only
import zio.blocks.smithy._

val userId = ShapeId("com.example", "UserId")
val stringId = ShapeId("smithy.api", "String")

// Parse from string
ShapeId.parse("com.example#User") match {
  case Right(id: ShapeId) => println(s"Shape ID: ${id.namespace}#${id.name}")
  case Right(member: ShapeId.Member) => println(s"Member: ${member.shape}$${member.memberName}")
  case Left(error) => println(s"Parse error: $error")
}
```

### NodeValue

Represents JSON-like values used in trait attributes and metadata. Variants: `String`, `Number`, `Boolean`, `Null`, `Array`, `Object`.

```scala mdoc:compile-only
import zio.blocks.smithy._

val stringVal = NodeValue.String("example")
val numberVal = NodeValue.Number(BigDecimal(42))
val boolVal = NodeValue.Boolean(true)

val arrayVal = NodeValue.Array(List(
  NodeValue.String("GET"),
  NodeValue.String("POST")
))

val objectVal = NodeValue.Object(List(
  "method" -> NodeValue.String("GET"),
  "uri" -> NodeValue.String("/users/{id}")
))
```

### TraitApplication

Metadata annotation attached to shapes. Identifies the trait (by `ShapeId`) and optionally provides configuration.

**Convenience Factories**:
- `TraitApplication.required` — `@required` trait
- `TraitApplication.documentation(text)` — `@documentation` trait
- `TraitApplication.http(method, uri)` — `@http(method: "...", uri: "...")`
- `TraitApplication.error(value)` — `@error` trait

```scala mdoc:compile-only
import zio.blocks.smithy._

val requiredTrait = TraitApplication.required
val httpTrait = TraitApplication.http("GET", "/users/{id}")
val docTrait = TraitApplication.documentation("Get a user by ID")
```

## Parsing Models

Parse Smithy IDL text into structured models using `SmithyModel.parse`, handle errors, and validate round-trips.

### Basic Parsing

```scala mdoc:compile-only
import zio.blocks.smithy._

val smithyText = """$version: "2"
namespace com.example

string Name
"""

SmithyModel.parse(smithyText) match {
  case Right(model) =>
    println(s"Parsed ${model.shapes.length} shapes")
  case Left(error) =>
    println(s"Error: ${error.message}")
}
```

### Handling Parse Errors

```scala mdoc:compile-only
import zio.blocks.smithy._

val invalidSmithy = """$version: "2"
namespace com.example

structure User {
  invalid syntax
}
"""

SmithyModel.parse(invalidSmithy) match {
  case Right(_) =>
    println("Unexpected success")
  case Left(error) =>
    println(s"Error at line ${error.line}, column ${error.column}: ${error.message}")
}
```

### Round-Trip Validation

Verify a model parses correctly by round-tripping:

```scala mdoc:compile-only
import zio.blocks.smithy._

val original = """$version: "2"
namespace com.example

string MyString
"""

val parsed = SmithyModel.parse(original)
val reprinted = parsed.map(_.prettyPrint)
val reparsed = reprinted.flatMap(SmithyModel.parse)

println(reparsed.isRight)  // true if round-trip succeeds
```

## Querying & Traversing Shapes

Once you have a parsed model, query shapes by name, pattern match on shape types, and traverse their members.

### Finding Shapes

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel.parse("""$version: "2"
namespace example

structure User {
  id: String
  name: String
}
""").toOption.get

// Find by name
model.findShape("User").foreach { shapeDef =>
  println(s"Found: ${shapeDef.name}")
}

// Get all shape IDs
val allIds = model.allShapeIds
println(s"Total shapes: ${allIds.length}")
```

### Pattern Matching on Shapes

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel.parse("""$version: "2"
namespace example

structure User { id: String }
list UserIds { member: String }
""").toOption.get

model.findShape("User").foreach { shapeDef =>
  shapeDef.shape match {
    case struct: StructureShape =>
      println(s"Structure with ${struct.members.length} members")
    case list: ListShape =>
      println(s"List of ${list.member.target}")
    case _ =>
      println("Other shape type")
  }
}
```

### Traversing Members

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel.parse("""$version: "2"
namespace example

structure User {
  @required
  id: String
  name: String
}
""").toOption.get

model.findShape("User").foreach { shapeDef =>
  shapeDef.shape match {
    case struct: StructureShape =>
      struct.members.foreach { member =>
        val required = member.traits.exists(_.id.name == "required")
        println(s"${member.name}: ${member.target} (required: $required)")
      }
    case _ => ()
  }
}
```

## Building Models Programmatically

Construct Smithy models in code by creating shapes, adding traits, and assembling them into a complete model.

### Creating Shapes

```scala mdoc:compile-only
import zio.blocks.smithy._

val userStructure = StructureShape(
  "User",
  traits = Nil,
  members = List(
    MemberDefinition(
      "id",
      ShapeId("smithy.api", "String"),
      traits = List(TraitApplication.required)
    ),
    MemberDefinition(
      "name",
      ShapeId("smithy.api", "String"),
      traits = Nil
    )
  )
)

val model = SmithyModel(
  version = "2.0",
  namespace = "com.example",
  useStatements = Nil,
  metadata = Map.empty,
  shapes = List(ShapeDefinition("User", userStructure))
)
```

### Adding Traits

```scala mdoc:compile-only
import zio.blocks.smithy._

val serviceShape = ServiceShape(
  "UserService",
  traits = List(
    TraitApplication.documentation("User management API")
  ),
  version = Some("1.0"),
  operations = List(
    ShapeId("com.example", "GetUser"),
    ShapeId("com.example", "CreateUser")
  ),
  resources = Nil,
  errors = Nil
)
```

## Serializing Models

Convert models back to valid Smithy IDL text using `prettyPrint`, with options for custom formatting.

### Basic Serialization

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel(
  version = "2.0",
  namespace = "com.example",
  useStatements = Nil,
  metadata = Map.empty,
  shapes = List(
    ShapeDefinition("Name", StringShape("Name"))
  )
)

val idlText = model.prettyPrint
println(idlText)
```

### Custom Indentation

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel(
  version = "2.0",
  namespace = "com.example",
  useStatements = Nil,
  metadata = Map.empty,
  shapes = List(
    ShapeDefinition("Data", StructureShape(
      "Data",
      traits = Nil,
      members = List(
        MemberDefinition("field1", ShapeId("smithy.api", "String")),
        MemberDefinition("field2", ShapeId("smithy.api", "String"))
      )
    ))
  )
)

val compact = model.prettyPrint(indent = 2)
val verbose = model.prettyPrint(indent = 8)
```

## Common Use-Cases

See how to apply Smithy parsing and querying to real-world workflows: code generation, validation, and model transformation.

### Use-Case 1: Code Generation

Load a Smithy model and generate code for each operation:

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel.parse("""$version: "2"
namespace api

service MyService {
  operations: [GetUser, CreateUser]
}

operation GetUser {
  @http(method: "GET", uri: "/users/{id}")
  input: GetUserInput
  output: User
}

operation CreateUser {
  @http(method: "POST", uri: "/users")
  input: CreateUserInput
  output: User
}

structure User { id: String, name: String }
structure GetUserInput { @required id: String }
structure CreateUserInput { @required name: String }
""").toOption.get

// Generate code for each operation
model.shapes.foreach { shapeDef =>
  shapeDef.shape match {
    case op: OperationShape =>
      println(s"// Generate operation: ${op.name}")
      op.input.foreach(in => println(s"//   input: ${in.name}"))
      op.output.foreach(out => println(s"//   output: ${out.name}"))
    case _ => ()
  }
}
```

### Use-Case 2: Validation & Analysis

Analyze shapes for completeness:

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel.parse("""$version: "2"
namespace example

@deprecated
structure LegacyUser { id: String }

structure ModernUser {
  @required
  id: String
  email: String
}
""").toOption.get

// Find all deprecated shapes
val deprecated = model.shapes.filter { shapeDef =>
  shapeDef.shape.traits.exists(_.id.name == "deprecated")
}

println(s"Deprecated shapes: ${deprecated.map(_.name)}")
```

### Use-Case 3: Model Transformation

Parse, modify, and re-serialize a model:

```scala mdoc:compile-only
import zio.blocks.smithy._

val original = """$version: "2"
namespace com.example

string UserId
"""

val modified = SmithyModel.parse(original).map { model =>
  // Add metadata to the model
  val newMetadata = model.metadata + ("version" -> NodeValue.String("1.0"))
  model.copy(metadata = newMetadata)
}

modified.foreach { model =>
  println(model.prettyPrint)
}
```

## API Reference

Concise reference for all public methods and types, organized by component.

### SmithyModel

| Method | Returns | Purpose |
|--------|---------|---------|
| `parse(input: String)` | `Either[SmithyError, SmithyModel]` | Parse IDL text to model |
| `findShape(name: String)` | `Option[ShapeDefinition]` | Find shape by name |
| `allShapeIds` | `List[ShapeId]` | Get all shape identifiers |
| `prettyPrint` | `String` | Serialize to IDL (4-space indent) |
| `prettyPrint(indent: Int)` | `String` | Serialize to IDL with custom indent |

### Shape

All shapes have `name: String` and `traits: List[TraitApplication]`.

**Aggregate Shapes** contain members:
- `StructureShape`: `members: List[MemberDefinition]`
- `UnionShape`: `members: List[MemberDefinition]`
- `ListShape`: `member: MemberDefinition`
- `MapShape`: `key: MemberDefinition`, `value: MemberDefinition`

**Service Shapes**:
- `ServiceShape`: `operations: List[ShapeId]`, `resources: List[ShapeId]`, `errors: List[ShapeId]`
- `OperationShape`: `input: Option[ShapeId]`, `output: Option[ShapeId]`, `errors: List[ShapeId]`

**Enum Shapes**:
- `EnumShape`: `members: List[EnumMember]`
- `IntEnumShape`: `members: List[IntEnumMember]`

### MemberDefinition

```scala
case class MemberDefinition(
  name: String,
  target: ShapeId,
  traits: List[TraitApplication] = Nil
)
```

### TraitApplication

```scala
case class TraitApplication(
  id: ShapeId,
  value: Option[NodeValue]
)
```

**Factories**:
- `required` — `@required` (no value)
- `documentation(text: String)` — `@documentation` with text
- `http(method: String, uri: String)` — `@http` with method and URI
- `error(value: String)` — `@error` with classification

## Type Integration

The main types work together in a parsing → querying → serialization pipeline:

```
Smithy IDL Text
      ↓
SmithyParser (parses text)
      ↓
SmithyModel (contains shapes, metadata, traits)
      ├─ findShape(name) → ShapeDefinition → Shape (sealed trait)
      ├─ Shape members reference ShapeId
      ├─ TraitApplication with NodeValue attributes
      └─ allShapeIds → List[ShapeId]
            ↓
SmithyPrinter (serializes model)
      ↓
Smithy IDL Text
```

**Key Relationships**:
- `SmithyModel` contains all `ShapeDefinition`s and metadata
- `Shape` is a sealed trait—pattern match to determine type
- `ShapeId` uniquely identifies shapes by namespace + name
- `TraitApplication` attaches metadata via `ShapeId` and `NodeValue`
- `TraitApplication.id` is always a `ShapeId`, not a string name

## Performance Notes

- **Parsing** is linear in IDL text size
- **Shape lookup** via `findShape` is O(n) on number of shapes (use for one-off queries)
- **Serialization** is linear in model size
- No lazy evaluation—everything is eagerly loaded into memory

For large models (1000+ shapes), consider batching operations rather than repeated `findShape` calls.

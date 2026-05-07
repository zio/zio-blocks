---
id: shape-id
title: "ShapeId"
---

`ShapeId` is a globally unique identifier for a Smithy shape, consisting of a namespace and a name. You use `ShapeId` to reference shapes across the module: in use statements, within aggregate shapes (list member types, map keys/values, structure members), in service shapes (operations, resources, errors), and in trait applications. `ShapeId` also has a `Member` subtype for referencing specific members within shapes.

## Motivation

Smithy shapes are organized by namespace, and multiple shapes can share the same name in different namespaces. `ShapeId` uniquely identifies a shape by combining its namespace and name (format: `"namespace#name"`). This allows you to:
- **Unambiguously reference shapes** — No chance of name collisions
- **Import shapes** — Reference external shapes via namespace qualification
- **Cross-module integration** — Link to shapes defined in other modules or packages
- **Member references** — Optionally reference a specific member within a shape (format: `"namespace#shape$member"`)

## ShapeId Construction

### Direct Construction

Create a `ShapeId` by providing namespace and name:

```scala mdoc:compile-only
import zio.blocks.smithy._

val userId = ShapeId("com.example", "UserId")
val stringId = ShapeId("smithy.api", "String")

println(userId)  // Prints: com.example#UserId
```

### Parsing from String

Parse a string into `ShapeId` or `ShapeId.Member`:

```scala mdoc:compile-only
import zio.blocks.smithy._

val parsed1 = ShapeId.parse("com.example#User")
val parsed2 = ShapeId.parse("com.example#User$profile")

parsed1 match {
  case Right(id: ShapeId) =>
    println(s"Shape: ${id.namespace}#${id.name}")
  case Right(member: ShapeId.Member) =>
    println(s"Member: ${member.shape}$${member.memberName}")
  case Left(error) =>
    println(s"Parse error: $error")
}
```

## Using ShapeId in Shapes

### Referencing Members in Structures

`StructureShape` members use `ShapeId` to reference their target types:

```scala mdoc:compile-only
import zio.blocks.smithy._

val userStructure = StructureShape(
  "User",
  traits = Nil,
  members = List(
    MemberDefinition("id", ShapeId("smithy.api", "String")),
    MemberDefinition("age", ShapeId("smithy.api", "Integer")),
    MemberDefinition("email", ShapeId("smithy.api", "String"))
  )
)
```

### Referencing Elements in Collections

`ListShape` and `MapShape` use `ShapeId` to specify element and key/value types:

```scala mdoc:compile-only
import zio.blocks.smithy._

val stringList = ListShape(
  "StringList",
  traits = Nil,
  member = MemberDefinition("member", ShapeId("smithy.api", "String"))
)

val userMap = MapShape(
  "UserMap",
  traits = Nil,
  key = MemberDefinition("key", ShapeId("smithy.api", "String")),
  value = MemberDefinition("value", ShapeId("com.example", "User"))
)
```

### Referencing Operations in Services

`ServiceShape` uses `ShapeId` to reference operation and resource definitions:

```scala mdoc:compile-only
import zio.blocks.smithy._

val exampleService = ServiceShape(
  "ExampleService",
  traits = Nil,
  version = Some("1.0"),
  operations = List(
    ShapeId("com.example", "GetUser"),
    ShapeId("com.example", "CreateUser"),
    ShapeId("com.example", "DeleteUser")
  ),
  resources = List(
    ShapeId("com.example", "UserResource")
  ),
  errors = List(
    ShapeId("com.example", "NotFound"),
    ShapeId("com.example", "Unauthorized")
  )
)
```

### Referencing Errors in Operations

`OperationShape` uses `ShapeId` to reference its input, output, and error types:

```scala mdoc:compile-only
import zio.blocks.smithy._

val getOperation = OperationShape(
  "GetUser",
  traits = Nil,
  input = Some(ShapeId("com.example", "GetUserInput")),
  output = Some(ShapeId("com.example", "User")),
  errors = List(
    ShapeId("com.example", "NotFound"),
    ShapeId("com.example", "Unauthorized")
  )
)
```

## ShapeId.Member — Member References

`ShapeId.Member` references a specific member within a shape. This is used in some Smithy features for fine-grained targeting:

```scala mdoc:compile-only
import zio.blocks.smithy._

val userIdMember = ShapeId.Member(
  shape = ShapeId("com.example", "User"),
  memberName = "id"
)

println(userIdMember)  // Prints: com.example#User$id
```

## Resolving ShapeIds to Shapes

When you have a `ShapeId`, resolve it to a `ShapeDefinition` within a `SmithyModel`:

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel.parse("""$version: "2"
namespace com.example

structure User {
  id: String
  name: String
}

operation GetUser {
  input: UserId
  output: User
}
""").toOption.get

val targetId = ShapeId("com.example", "User")
val resolved = model.findShape(targetId.name)

resolved match {
  case Some(definition) =>
    println(s"Found shape: ${definition.name} of type ${definition.shape.getClass.getSimpleName}")
  case None =>
    println(s"Shape not found: $targetId")
}
```

## Parsing and Error Handling

The `ShapeId.parse` function validates format and provides detailed error messages:

```scala mdoc:compile-only
import zio.blocks.smithy._

val examples = List(
  "com.example#User",           // Valid ShapeId
  "com.example#User$profile",   // Valid Member
  "invalid",                     // Missing '#'
  "com.example#",               // Empty name
  "#User",                       // Empty namespace
  "com.example##User"           // Multiple '#'
)

examples.foreach { s =>
  ShapeId.parse(s) match {
    case Right(id: ShapeId) =>
      println(s"✓ $s → ShapeId(${id.namespace}, ${id.name})")
    case Right(member: ShapeId.Member) =>
      println(s"✓ $s → Member(${member.shape}, ${member.memberName})")
    case Left(error) =>
      println(s"✗ $s → $error")
  }
}
```

## Common Patterns

### Storing Qualified Names

Use `ShapeId` to store fully-qualified shape references in data structures:

```scala mdoc:compile-only
import zio.blocks.smithy._

case class ServiceMetadata(
  name: String,
  operations: List[ShapeId],
  errors: List[ShapeId]
)

val metadata = ServiceMetadata(
  name = "UserService",
  operations = List(
    ShapeId("com.example.api", "GetUser"),
    ShapeId("com.example.api", "CreateUser")
  ),
  errors = List(
    ShapeId("smithy.api", "NotFound"),
    ShapeId("smithy.api", "Unauthorized")
  )
)
```

### Comparing Shape References

`ShapeId` is a case class, so equality checking works naturally:

```scala mdoc:compile-only
import zio.blocks.smithy._

val id1 = ShapeId("com.example", "User")
val id2 = ShapeId("com.example", "User")
val id3 = ShapeId("com.other", "User")

println(id1 == id2)  // true — same namespace and name
println(id1 == id3)  // false — different namespace
```

## Integration with Other Module Types

`ShapeId` is used throughout the smithy-blocks module:

- **`Shape` (aggregate shapes)** — `ListShape`, `MapShape`, `StructureShape`, `UnionShape` use `ShapeId` in member definitions
- **`Shape` (service shapes)** — `ServiceShape`, `OperationShape`, `ResourceShape` use `ShapeId` to reference operations, resources, and error types
- **`SmithyModel`** — Uses `ShapeId` in `useStatements` and provides `allShapeIds` to list all shapes
- **`MemberDefinition`** — Stores target type as `ShapeId`
- **Trait values** — `NodeValue` can contain `ShapeId` references in trait attribute objects

Typical workflow:

```
SmithyModel.parse() → SmithyModel
                          ↓
                  findShape(name) → ShapeDefinition
                          ↓
                  ShapeDefinition.shape → Shape (aggregate)
                          ↓
                  Access member.target → ShapeId
                          ↓
                  model.findShape(shapeId.name) → resolved type
```

## Related Documentation

- [SmithyModel](./smithy-model.md) — Container for shapes; provides lookup by name
- [Shape](./shape.md) — All shape types that reference `ShapeId` for members and targets
- [MemberDefinition](./shape.md#working-with-aggregate-shapes) — Contains target `ShapeId`
- [NodeValue](./node-value.md) — Can represent shape references in trait values

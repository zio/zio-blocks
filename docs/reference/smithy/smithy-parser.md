---
id: smithy-parser
title: "SmithyParser"
---

`SmithyParser` converts Smithy IDL (Interface Definition Language) text into a `SmithyModel`. The parser handles all Smithy 2.x constructs: version declarations, namespaces, metadata, use statements, shape definitions (simple, aggregate, enum, service), trait applications, documentation comments, and apply statements. It reports detailed error information when parsing fails.

## Motivation

Smithy API models are written as declarative IDL text. To build tools that programmatically work with these models (code generators, validators, documentation generators, schema analyzers), you need to parse the IDL text into an in-memory AST. `SmithyParser` provides this conversion, transforming unstructured text into a strongly-typed `SmithyModel` that you can safely inspect and transform.

Common use cases:
- **Load models at runtime** — Parse configuration files or user input
- **Validate Smithy syntax** — Check that IDL is well-formed
- **Build tools** — Create code generators, formatters, or analyzers on top of parsed models
- **Schema transformation** — Parse, modify, and re-serialize models

## Parsing Models

### Basic Parsing

Parse a Smithy IDL string using `SmithyModel.parse`:

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

val result = SmithyModel.parse(smithyText)
result match {
  case Right(model) =>
    println(s"Parsed successfully: ${model.shapes.length} shapes")
  case Left(error) =>
    println(s"Parse error: ${error.message}")
}
```

### Handling Parse Errors

When parsing fails, `SmithyError` provides details:

```scala mdoc:compile-only
import zio.blocks.smithy._

val invalidSmithy = """$version: "2"
namespace com.example

structure User {
  id: String
  invalid syntax here
}
"""

SmithyModel.parse(invalidSmithy) match {
  case Right(model) =>
    println("Unexpected success")
  case Left(error) =>
    println(s"Error: ${error.message}")
    println(s"  at line ${error.line}")
    println(s"  at column ${error.column}")
}
```

## Parsing Different Shape Types

### Simple Shapes

The parser recognizes all 13 simple shape types:

```scala mdoc:compile-only
import zio.blocks.smithy._

val simpleShapes = """$version: "2"
namespace com.example

string Name
blob Data
boolean Flag
byte Byte
short Short
integer Integer
long Long
float Float
double Double
bigInteger BigInt
bigDecimal BigDec
timestamp Timestamp
document Metadata
"""

val model = SmithyModel.parse(simpleShapes).toOption.get
println(s"Parsed ${model.shapes.length} simple shapes")
```

### Aggregate Shapes

The parser handles all four aggregate shape types:

```scala mdoc:compile-only
import zio.blocks.smithy._

val aggregateShapes = """$version: "2"
namespace com.example

structure User {
  id: String
  name: String
}

list UserIds {
  member: String
}

map UserMap {
  key: String
  value: User
}

union Result {
  success: String
  error: String
}
"""

val model = SmithyModel.parse(aggregateShapes).toOption.get
model.shapes.foreach { shapeDef =>
  println(s"${shapeDef.name}: ${shapeDef.shape.getClass.getSimpleName}")
}
```

### Enum Shapes

The parser supports both string and integer enums:

```scala mdoc:compile-only
import zio.blocks.smithy._

val enumShapes = """$version: "2"
namespace com.example

enum Status {
  ACTIVE
  INACTIVE
  PENDING
}

intEnum HttpMethod {
  GET = 1
  POST = 2
  PUT = 3
  DELETE = 4
}
"""

val model = SmithyModel.parse(enumShapes).toOption.get
model.shapes.foreach { shapeDef =>
  println(s"${shapeDef.name}: ${shapeDef.shape.getClass.getSimpleName}")
}
```

### Service Shapes

The parser handles services, operations, and resources:

```scala mdoc:compile-only
import zio.blocks.smithy._

val serviceShapes = """$version: "2"
namespace com.example.api

service UserService {
  version: "1.0"
  operations: [GetUser, CreateUser]
  resources: [UserResource]
}

operation GetUser {
  input: GetUserInput
  output: User
  errors: [NotFound]
}

operation CreateUser {
  input: CreateUserInput
  output: User
}

resource UserResource {
  identifiers: {userId: String}
  read: GetUser
  update: UpdateUser
  delete: DeleteUser
}

operation UpdateUser {
  input: UpdateUserInput
  output: User
}

operation DeleteUser {
  input: DeleteUserInput
  output: EmptyOutput
}

structure User {
  id: String
  name: String
}

structure GetUserInput {
  @required
  id: String
}

structure CreateUserInput {
  @required
  name: String
}

structure UpdateUserInput {
  @required
  id: String
  name: String
}

structure DeleteUserInput {
  @required
  id: String
}

structure EmptyOutput {}

structure NotFound {
  message: String
}
"""

val model = SmithyModel.parse(serviceShapes).toOption.get
println(s"Parsed service model with ${model.shapes.length} shapes")
```

## Parsing Traits and Metadata

### Trait Applications

The parser extracts traits applied to shapes and members:

```scala mdoc:compile-only
import zio.blocks.smithy._

val withTraits = """$version: "2"
namespace com.example

@deprecated
structure LegacyUser {
  @required
  id: String
  
  @range(min: 0, max: 120)
  age: Integer
}

@http(method: "GET", uri: "/users/{id}")
operation GetUser {
  input: GetUserInput
  output: LegacyUser
}

structure GetUserInput {
  @required
  id: String
}
"""

val model = SmithyModel.parse(withTraits).toOption.get
model.findShape("LegacyUser").foreach { shapeDef =>
  println(s"Traits on User: ${shapeDef.shape.traits.map(_.id)}")
}
```

### Model Metadata

The parser extracts metadata key-value pairs:

```scala mdoc:compile-only
import zio.blocks.smithy._

val withMetadata = """$version: "2"
metadata apiVersion = "1.0.0"
metadata title = "User Management API"
metadata maxRetries = 3

namespace com.example

string Name
"""

val model = SmithyModel.parse(withMetadata).toOption.get
println(s"Metadata keys: ${model.metadata.keys}")
model.metadata.foreach { case (key, value) =>
  println(s"$key = $value")
}
```

### Use Statements

The parser handles imports via use statements:

```scala mdoc:compile-only
import zio.blocks.smithy._

val withUse = """$version: "2"
use smithy.api#required
use smithy.api#http

namespace com.example

structure User {
  id: String
}
"""

val model = SmithyModel.parse(withUse).toOption.get
println(s"Use statements: ${model.useStatements}")
```

## Parsing Documentation Comments

Documentation comments (triple-slash) are attached as traits:

```scala mdoc:compile-only
import zio.blocks.smithy._

val withDocs = """$version: "2"
namespace com.example

/// This is a user account
structure User {
  /// The unique user identifier
  @required
  id: String
  
  /// The user's display name
  name: String
}
"""

val model = SmithyModel.parse(withDocs).toOption.get
model.findShape("User").foreach { shapeDef =>
  shapeDef.shape.traits.foreach { trait_ =>
    println(s"Trait: ${trait_.id} = ${trait_.value}")
  }
}
```

## Round-Trip Parsing

Validate that a model can be parsed, serialized, and re-parsed identically:

```scala mdoc:compile-only
import zio.blocks.smithy._

val original = """$version: "2"
namespace com.example

structure User {
  id: String
}
"""

val step1 = SmithyModel.parse(original)
val step2 = step1.map(_.prettyPrint)
val step3 = step2.flatMap(SmithyModel.parse)

step1 match {
  case Right(model1) =>
    step3 match {
      case Right(model3) =>
        if (model1.shapes.length == model3.shapes.length) {
          println("Round-trip successful")
        } else {
          println("Round-trip mismatch")
        }
      case Left(error) =>
        println(s"Re-parse failed: ${error.message}")
    }
  case Left(error) =>
    println(s"Initial parse failed: ${error.message}")
}
```

## Error Recovery and Reporting

Parse errors include location information:

```scala mdoc:compile-only
import zio.blocks.smithy._

val malformed = """$version: "2"
namespace com.example

structure User {
  id: String
  invalid syntax
}
"""

SmithyModel.parse(malformed) match {
  case Right(_) =>
    println("Unexpected success")
  case Left(error) =>
    println(s"Error: ${error.message}")
    println(s"  at line ${error.line}")
    println(s"  at column ${error.column}")
}
```

## Integration with Other Module Types

`SmithyParser` is the entry point for working with Smithy models programmatically:

- **`SmithyModel`** — Parser output; accessed via `SmithyModel.parse()`
- **`Shape`** — Parser creates concrete shape instances for all 25+ shape types
- **`TraitApplication`** — Parser extracts traits from IDL syntax
- **`NodeValue`** — Parser converts trait attribute syntax into `NodeValue`s
- **`ShapeId`** — Parser resolves namespace and shape names to `ShapeId`s
- **`SmithyPrinter`** — Round-trip: parse → model → print → text

Typical workflow:

```
Smithy IDL Text
      ↓
SmithyParser.parse() (text → model)
      ↓
SmithyModel (AST with all shapes and traits)
      ├─ Query shapes: findShape(name)
      ├─ Inspect traits: shape.traits
      ├─ Traverse members: members, operations, resources
      └─ Transform: modify model and re-serialize
            ↓
SmithyPrinter.print() (model → text)
```

## Related Documentation

- [SmithyModel](./smithy-model.md) — The output of parsing; root container for shapes
- [Shape](./shape.md) — Concrete shape types created by parser
- [TraitApplication](./trait-application.md) — Traits extracted from IDL syntax
- [NodeValue](./node-value.md) — Trait attribute values parsed from IDL
- [SmithyPrinter](./smithy-printer.md) — Serializes parsed models back to IDL
- [SmithyError](./smithy-error.md) — Error reporting with location information

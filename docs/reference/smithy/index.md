---
id: index
title: "Smithy"
---

`zio-blocks-smithy` is a **Smithy IDL parser and AST library** providing a complete representation of Smithy 2.0 API models. It enables parsing Smithy IDL text into rich data structures, querying shape definitions, and pretty-printing models back to valid IDL syntax.

Core types: `SmithyModel`, `Shape`, `ShapeId`, `NodeValue`, `TraitApplication`, `SmithyParser`, `SmithyPrinter`, `SmithyError`.

## Motivation

Smithy is an open-source API modeling language that separates API structure from protocol bindings. Rather than embedding API definitions in code, Smithy models are declarative, language-agnostic specifications that code generators and tools consume.

`zio-blocks-smithy` solves three problems:

- **Parse Smithy IDL at runtime** — Load and validate API models programmatically without external dependencies
- **Represent Smithy models in Scala** — Work with strongly-typed ADTs instead of JSON or raw text
- **Generate valid Smithy output** — Serialize models back to IDL with proper formatting

This enables use cases like code generation, model transformation, validation, and documentation generation.

## Installation

Add to `build.sbt`:

```
libraryDependencies += "dev.zio" %% "zio-blocks-smithy" % "@VERSION@"
```

Supported Scala versions: 2.13.x and 3.x

## Overview

The module is built around five core operations:

1. **Parse** Smithy IDL text → `SmithyModel`
2. **Query** shapes by name, type, or trait
3. **Traverse** shape hierarchies (structures contain members, services contain operations, etc.)
4. **Transform** shapes and apply traits programmatically
5. **Render** models back to valid Smithy IDL syntax

All operations are composable and designed for use in tool pipelines.

## How the Types Work Together

Typical workflow:

```
Smithy IDL Text
      ↓
 SmithyParser.parse()
      ↓
 SmithyModel (version, namespace, shapes, traits)
      ↓
 Query & Traverse
 ├─ SmithyModel.findShape(name) → ShapeDefinition
 ├─ ShapeDefinition.shape → Shape (sealed trait)
 │  ├─ StructureShape (members: List[Member])
 │  ├─ ListShape, MapShape, UnionShape
 │  ├─ ServiceShape (operations: List[ShapeId])
 │  └─ ... 20+ shape types
 ├─ Member/Operation contain ShapeIds
 │  └─ ShapeId is resolved via model.findShape()
 └─ TraitApplications attach metadata
      ↓
 SmithyModel.prettyPrint()
      ↓
 Valid Smithy IDL Text
```

**Key relationships:**

- `SmithyModel` is the root; it contains all shapes and metadata
- `Shape` is a sealed trait representing 25+ Smithy shape types
- `ShapeId` identifies shapes (namespace + name)
- `NodeValue` represents trait attribute values (strings, numbers, objects, arrays)
- `TraitApplication` attaches traits to shapes (e.g., `@http`, `@required`)
- `SmithyParser` transforms text → `SmithyModel`
- `SmithyPrinter` transforms `SmithyModel` → text

## Common Patterns

### Pattern 1: Parse and Extract Operations

Load a model and find all operations in a service:

```scala mdoc:compile-only
import zio.blocks.smithy._

val smithyText = """$version: "2"
namespace example.api

service MyService {
  operations: [GetUser, CreateUser]
}

@http(method: "GET", uri: "/users/{id}")
operation GetUser {
  input: GetUserInput
  output: GetUserOutput
}

operation CreateUser {
  input: CreateUserInput
  output: User
}

structure User { id: String, name: String }
structure GetUserInput { @required id: String }
structure GetUserOutput { @required user: User }
structure CreateUserInput { @required name: String }
"""

val model = SmithyModel.parse(smithyText).toOption.get
val service = model.findShape("MyService").get
```

### Pattern 2: Traverse Shape Members

Inspect the fields of a structure:

```scala mdoc:compile-only
import zio.blocks.smithy._

val smithyText = """$version: "2"
namespace example

structure User {
  @required
  id: String
  name: String
  @range(min: 0, max: 120)
  age: Integer
}
"""

val model = SmithyModel.parse(smithyText).toOption.get
val userShape = model.findShape("User").flatMap { shapeDef =>
  shapeDef.shape match {
    case s: StructureShape => Some(s)
    case _ => None
  }
}
```

### Pattern 3: Round-Trip Parse and Pretty-Print

Validate that a model parses correctly by round-tripping:

```scala mdoc:compile-only
import zio.blocks.smithy._

val originalText = """$version: "2"
namespace example

string MyString
"""

val parsed = SmithyModel.parse(originalText)
val reprinted = parsed.map(_.prettyPrint)
val reparsed = reprinted.flatMap(SmithyModel.parse)

// reparsed contains the same model as parsed
```

### Pattern 4: Check for Required Traits

Find all shapes with a particular trait:

```scala mdoc:compile-only
import zio.blocks.smithy._

val smithyText = """$version: "2"
namespace example

structure User {
  @required
  id: String
  name: String
}

operation GetUser {
  @http(method: "GET", uri: "/users/{id}")
}
"""

val model = SmithyModel.parse(smithyText).toOption.get
val shapesWithRequired = model.shapes.filter { shapeDef =>
  shapeDef.shape.traits.exists(_.id.name == "required")
}
```

## Key Files

- [SmithyModel](./smithy-model.md) — Top-level model and parsing API
- [Shape](./shape.md) — Core sealed trait with 25+ shape types
- [ShapeId](./shape-id.md) — Identifiers for shapes
- [NodeValue](./node-value.md) — Trait attribute values
- [TraitApplication](./trait-application.md) — Trait attachments
- [SmithyParser](./smithy-parser.md) — Parsing IDL to models
- [SmithyPrinter](./smithy-printer.md) — Serializing models to IDL
- [SmithyError](./smithy-error.md) — Structured error reporting

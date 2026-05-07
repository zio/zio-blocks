---
id: smithy-printer
title: "SmithyPrinter"
---

`SmithyPrinter` converts a `SmithyModel` back to valid Smithy IDL text. The printer serializes all model components—version, namespace, use statements, metadata, shape definitions, and apply statements—in canonical order with proper formatting. Use it for round-trip validation (parse → modify → serialize), generating Smithy files from models, and displaying models in human-readable form.

## Motivation

When you build tools that work with Smithy models (code generators, schema transformers, validators), you often need to output the modified or analyzed model back to valid IDL text. `SmithyPrinter` handles the serialization:
- **Round-trip validation** — Parse text, modify, serialize, and re-parse to ensure model integrity
- **Code generation** — Generate complete Smithy models programmatically and export them
- **Model display** — Show parsed models in human-readable IDL form for debugging
- **Schema transformation** — Accept IDL, transform the model, output modified IDL

## Basic Serialization

### Default Formatting

Serialize a model using default 4-space indentation:

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel(
  version = "2.0",
  namespace = "com.example",
  useStatements = Nil,
  metadata = Map.empty,
  shapes = List(
    ShapeDefinition("User", StructureShape("User", Nil, List(
      MemberDefinition("id", ShapeId("smithy.api", "String")),
      MemberDefinition("name", ShapeId("smithy.api", "String"))
    )))
  )
)

val idlText = model.prettyPrint
println(idlText)
```

### Custom Indentation

Control indentation width:

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel(
  version = "2.0",
  namespace = "com.example",
  useStatements = Nil,
  metadata = Map.empty,
  shapes = List(
    ShapeDefinition("Data", StructureShape("Data", Nil, List(
      MemberDefinition("field1", ShapeId("smithy.api", "String")),
      MemberDefinition("field2", ShapeId("smithy.api", "String"))
    )))
  )
)

val compactOutput = model.prettyPrint(indent = 2)
val verboseOutput = model.prettyPrint(indent = 8)

println("Compact (2 spaces):")
println(compactOutput)
println("\nVerbose (8 spaces):")
println(verboseOutput)
```

## Serializing Different Shape Types

### Simple Shapes

Simple shapes are rendered with their name only (unless they have traits):

```scala mdoc:compile-only
import zio.blocks.smithy._

val simpleModel = SmithyModel(
  version = "2.0",
  namespace = "com.example",
  useStatements = Nil,
  metadata = Map.empty,
  shapes = List(
    ShapeDefinition("Name", StringShape("Name")),
    ShapeDefinition("Id", StringShape("Id", List(
      TraitApplication.documentation("Unique identifier")
    ))),
    ShapeDefinition("Count", IntegerShape("Count")),
    ShapeDefinition("Data", BlobShape("Data"))
  )
)

println(simpleModel.prettyPrint)
```

### Aggregate Shapes

Aggregate shapes are rendered with their members:

```scala mdoc:compile-only
import zio.blocks.smithy._

val aggregateModel = SmithyModel(
  version = "2.0",
  namespace = "com.example",
  useStatements = Nil,
  metadata = Map.empty,
  shapes = List(
    ShapeDefinition("User", StructureShape(
      "User",
      traits = Nil,
      members = List(
        MemberDefinition(
          "id",
          ShapeId("smithy.api", "String"),
          traits = List(TraitApplication.required)
        ),
        MemberDefinition("name", ShapeId("smithy.api", "String")),
        MemberDefinition("email", ShapeId("smithy.api", "String"))
      )
    )),
    ShapeDefinition("UserIds", ListShape(
      "UserIds",
      traits = Nil,
      member = MemberDefinition("member", ShapeId("smithy.api", "String"))
    ))
  )
)

println(aggregateModel.prettyPrint)
```

### Service Shapes

Service shapes are rendered with their operations and resources:

```scala mdoc:compile-only
import zio.blocks.smithy._

val serviceModel = SmithyModel(
  version = "2.0",
  namespace = "com.example.api",
  useStatements = Nil,
  metadata = Map.empty,
  shapes = List(
    ShapeDefinition("UserService", ServiceShape(
      "UserService",
      traits = Nil,
      version = Some("1.0"),
      operations = List(
        ShapeId("com.example.api", "GetUser"),
        ShapeId("com.example.api", "CreateUser")
      ),
      resources = List(
        ShapeId("com.example.api", "UserResource")
      ),
      errors = Nil
    )),
    ShapeDefinition("GetUser", OperationShape(
      "GetUser",
      traits = List(TraitApplication.http("GET", "/users/{id}")),
      input = Some(ShapeId("com.example.api", "GetUserInput")),
      output = Some(ShapeId("com.example.api", "User")),
      errors = List(ShapeId("com.example.api", "NotFound"))
    ))
  )
)

println(serviceModel.prettyPrint)
```

## Serializing Metadata and Traits

### Model-Level Metadata

The printer includes metadata in the canonical order:

```scala mdoc:compile-only
import zio.blocks.smithy._

val modelWithMetadata = SmithyModel(
  version = "2.0",
  namespace = "com.example",
  useStatements = Nil,
  metadata = Map(
    "apiVersion" -> NodeValue.String("1.0.0"),
    "title" -> NodeValue.String("User API"),
    "maxRetries" -> NodeValue.Number(BigDecimal(3))
  ),
  shapes = List(
    ShapeDefinition("User", StringShape("User"))
  )
)

println(modelWithMetadata.prettyPrint)
```

### Shape-Level Traits

Traits applied to shapes are rendered with `@` syntax:

```scala mdoc:compile-only
import zio.blocks.smithy._

val modelWithTraits = SmithyModel(
  version = "2.0",
  namespace = "com.example",
  useStatements = Nil,
  metadata = Map.empty,
  shapes = List(
    ShapeDefinition("User", StructureShape(
      "User",
      traits = List(
        TraitApplication.documentation("Represents a user")
      ),
      members = List(
        MemberDefinition(
          "id",
          ShapeId("smithy.api", "String"),
          traits = List(
            TraitApplication.required,
            TraitApplication.documentation("Unique user ID")
          )
        )
      )
    ))
  )
)

println(modelWithTraits.prettyPrint)
```

## Round-Trip Validation

Verify that a model can be parsed, serialized, and re-parsed identically:

```scala mdoc:compile-only
import zio.blocks.smithy._

val originalText = """$version: "2"
namespace com.example

@required
string UserId

structure User {
  id: UserId
  name: String
}
"""

val parsed1 = SmithyModel.parse(originalText)
val serialized = parsed1.map(_.prettyPrint)
val reparsed = serialized.flatMap(SmithyModel.parse)

(parsed1, reparsed) match {
  case (Right(model1), Right(model2)) =>
    val match1 = model1.namespace == model2.namespace
    val match2 = model1.shapes.length == model2.shapes.length
    if (match1 && match2) {
      println("Round-trip successful: models are equivalent")
    } else {
      println("Round-trip mismatch")
    }
  case _ =>
    println("Parse or serialize failed")
}
```

## Programmatic Model Generation

Generate complete Smithy models from code:

```scala mdoc:compile-only
import zio.blocks.smithy._

def generateUserServiceModel(): String = {
  val model = SmithyModel(
    version = "2.0",
    namespace = "com.example.api",
    useStatements = List(ShapeId("smithy.api", "String")),
    metadata = Map(
      "apiVersion" -> NodeValue.String("1.0.0"),
      "title" -> NodeValue.String("User Service API")
    ),
    shapes = List(
      ShapeDefinition("User", StructureShape(
        "User",
        traits = List(TraitApplication.documentation("A user account")),
        members = List(
          MemberDefinition(
            "id",
            ShapeId("smithy.api", "String"),
            traits = List(TraitApplication.required)
          ),
          MemberDefinition(
            "email",
            ShapeId("smithy.api", "String"),
            traits = List(TraitApplication.required)
          )
        )
      )),
      ShapeDefinition("GetUserInput", StructureShape(
        "GetUserInput",
        traits = Nil,
        members = List(
          MemberDefinition(
            "id",
            ShapeId("smithy.api", "String"),
            traits = List(TraitApplication.required)
          )
        )
      )),
      ShapeDefinition("GetUser", OperationShape(
        "GetUser",
        traits = List(TraitApplication.http("GET", "/users/{id}")),
        input = Some(ShapeId("com.example.api", "GetUserInput")),
        output = Some(ShapeId("com.example.api", "User")),
        errors = Nil
      ))
    )
  )
  
  model.prettyPrint
}

println(generateUserServiceModel())
```

## Output Format

The printer follows this canonical structure:

```
$version: "<version>"

namespace <namespace>

use <shape-id>
use <shape-id>
...

metadata <key> = <value>
metadata <key> = <value>
...

<shape-definition>

<shape-definition>
...

apply <shape-name> <trait>
apply <shape-name> <trait>
...
```

Each component is separated by blank lines for readability.

## Indentation Behavior

The indentation parameter controls spacing within shapes:

```scala mdoc:compile-only
import zio.blocks.smithy._

val complexShape = SmithyModel(
  version = "2.0",
  namespace = "com.example",
  useStatements = Nil,
  metadata = Map.empty,
  shapes = List(
    ShapeDefinition("Response", StructureShape(
      "Response",
      traits = Nil,
      members = List(
        MemberDefinition("status", ShapeId("smithy.api", "Integer")),
        MemberDefinition("data", ShapeId("smithy.api", "String")),
        MemberDefinition("errors", ShapeId("com.example", "ErrorList"))
      )
    ))
  )
)

// Compare different indentation widths
println("2-space indent:")
println(complexShape.prettyPrint(indent = 2))
println("\n4-space indent:")
println(complexShape.prettyPrint(indent = 4))
```

## Integration with Other Module Types

`SmithyPrinter` is the inverse of `SmithyParser`, enabling round-trip workflows:

- **`SmithyModel`** — Input to serialization; accessed via `model.prettyPrint()`
- **`Shape`** — All shape types are serialized (simple, aggregate, enum, service)
- **`TraitApplication`** — Traits are rendered with `@` syntax
- **`NodeValue`** — Metadata and trait values are serialized as JSON-like syntax
- **`ShapeId`** — Shape and trait identifiers are rendered in `namespace#name` format
- **`SmithyParser`** — Round-trip: parse → model → print → re-parse

Typical workflow:

```
SmithyModel (in memory)
      ↓
SmithyPrinter.print() (model → text)
      ↓
Smithy IDL Text
      ├─ Display to user
      ├─ Write to file
      ├─ Transmit over network
      └─ Re-parse if needed
            ↓
SmithyParser.parse() (text → model)
```

## Performance Notes

- **Serialization is linear in model size** — Time scales with total number of shapes and traits
- **No optimization needed for typical models** — Even large models (1000+ shapes) serialize quickly
- **Memory-efficient** — Uses `StringBuilder` for efficient string concatenation

## Related Documentation

- [SmithyModel](./smithy-model.md) — The model being serialized
- [SmithyParser](./smithy-parser.md) — Inverse operation; parses IDL back to models
- [Shape](./shape.md) — Concrete shape types that are serialized
- [TraitApplication](./trait-application.md) — Traits rendered in output
- [NodeValue](./node-value.md) — Metadata and trait values in output

---
id: trait-application
title: "TraitApplication"
---

`TraitApplication` represents a trait (metadata annotation) applied to a shape or member in Smithy. Every trait consists of a `ShapeId` identifying the trait (e.g., `"smithy.api#required"`) and an optional `NodeValue` containing the trait's configuration. Traits are how you attach constraints, documentation, protocol bindings, and other metadata to shapes without changing their structure.

## Motivation

In Smithy, traits are how you annotate shapes with metadata. For example:
- `@required` marks a structure member as mandatory
- `@http(method: "GET", uri: "/users/{id}")` specifies HTTP binding
- `@documentation` attaches prose documentation
- `@range(min: 0, max: 100)` constrains numeric values

`TraitApplication` packages these into a type-safe representation, allowing you to:
- **Attach metadata to shapes** — Add constraints, docs, and bindings programmatically
- **Inspect traits on parsed shapes** — Query which traits a shape has and their values
- **Create new traits** — Use convenience factories for common traits
- **Serialize to IDL** — Traits are automatically rendered back to valid Smithy syntax

## Construction

### Direct Construction

Create a `TraitApplication` by providing a `ShapeId` and optional `NodeValue`:

```scala mdoc:compile-only
import zio.blocks.smithy._

val requiredTrait = TraitApplication(
  id = ShapeId("smithy.api", "required"),
  value = None
)

val documentedTrait = TraitApplication(
  id = ShapeId("smithy.api", "documentation"),
  value = Some(NodeValue.String("This field is required for all operations"))
)

val httpTrait = TraitApplication(
  id = ShapeId("smithy.api", "http"),
  value = Some(NodeValue.Object(List(
    "method" -> NodeValue.String("GET"),
    "uri" -> NodeValue.String("/users/{id}")
  )))
)
```

### Using Convenience Factories

The `TraitApplication` object provides factory methods for common traits:

```scala mdoc:compile-only
import zio.blocks.smithy._

val required = TraitApplication.required

val documented = TraitApplication.documentation("User ID, must be a valid UUID")

val getOperation = TraitApplication.http("GET", "/users/{id}")

val clientError = TraitApplication.error("client")
```

## Attaching Traits to Shapes

Traits are attached to shapes via the `traits` parameter:

```scala mdoc:compile-only
import zio.blocks.smithy._

val userIdShape = StringShape(
  "UserId",
  traits = List(
    TraitApplication.documentation("Unique user identifier")
  )
)

val userStructure = StructureShape(
  "User",
  traits = Nil,
  members = List(
    MemberDefinition(
      "id",
      ShapeId("com.example", "UserId"),
      traits = List(TraitApplication.required)
    ),
    MemberDefinition(
      "name",
      ShapeId("smithy.api", "String"),
      traits = Nil
    )
  )
)
```

## Attaching Traits to Members

Structure and union members can have their own traits:

```scala mdoc:compile-only
import zio.blocks.smithy._

val rangeTrait = TraitApplication(
  id = ShapeId("smithy.api", "range"),
  value = Some(NodeValue.Object(List(
    "min" -> NodeValue.Number(BigDecimal(0)),
    "max" -> NodeValue.Number(BigDecimal(120))
  )))
)

val ageStructure = StructureShape(
  "User",
  traits = Nil,
  members = List(
    MemberDefinition(
      "age",
      ShapeId("smithy.api", "Integer"),
      traits = List(
        TraitApplication.required,
        rangeTrait
      )
    )
  )
)
```

## Inspecting Traits

### Checking for Trait Presence

Query whether a shape has a specific trait:

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel.parse("""$version: "2"
namespace com.example

@required
string UserId

structure User {
  @required
  id: UserId
  name: String
}
""").toOption.get

model.findShape("User").foreach { shapeDef =>
  val hasTraits = shapeDef.shape.traits.nonEmpty
  println(s"User has traits: $hasTraits")
  
  shapeDef.shape.traits.foreach { trait_ =>
    println(s"Trait: ${trait_.id}")
  }
}
```

### Extracting Trait Values

For traits with configuration, extract and inspect the values:

```scala mdoc:compile-only
import zio.blocks.smithy._

val model = SmithyModel.parse("""$version: "2"
namespace com.example

@range(min: 0, max: 100)
integer Percentage
""").toOption.get

model.findShape("Percentage").foreach { shapeDef =>
  shapeDef.shape.traits.foreach { trait_ =>
    trait_.value match {
      case Some(NodeValue.Object(fields)) =>
        fields.foreach { case (key, value) =>
          println(s"$key = $value")
        }
      case Some(value) =>
        println(s"Trait value: $value")
      case None =>
        println("Trait with no value")
    }
  }
}
```

## Common Trait Patterns

### Required Members

Mark structure members as required:

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
      "email",
      ShapeId("smithy.api", "String"),
      traits = List(TraitApplication.required)
    ),
    MemberDefinition(
      "phone",
      ShapeId("smithy.api", "String"),
      traits = Nil  // Optional
    )
  )
)
```

### HTTP Bindings for Operations

Bind operations to HTTP methods and URIs:

```scala mdoc:compile-only
import zio.blocks.smithy._

val getOperationShape = OperationShape(
  "GetUser",
  traits = List(
    TraitApplication.http("GET", "/users/{id}")
  ),
  input = Some(ShapeId("com.example", "GetUserInput")),
  output = Some(ShapeId("com.example", "User")),
  errors = Nil
)

val createOperationShape = OperationShape(
  "CreateUser",
  traits = List(
    TraitApplication.http("POST", "/users")
  ),
  input = Some(ShapeId("com.example", "CreateUserInput")),
  output = Some(ShapeId("com.example", "User")),
  errors = Nil
)
```

### Error Classification

Mark error structures with error classification:

```scala mdoc:compile-only
import zio.blocks.smithy._

val notFoundError = StructureShape(
  "NotFound",
  traits = List(
    TraitApplication.error("client"),
    TraitApplication.documentation("The requested resource was not found")
  ),
  members = List(
    MemberDefinition(
      "message",
      ShapeId("smithy.api", "String"),
      traits = List(TraitApplication.required)
    )
  )
)
```

### Documentation

Attach documentation to shapes and members:

```scala mdoc:compile-only
import zio.blocks.smithy._

val documentedStructure = StructureShape(
  "User",
  traits = List(
    TraitApplication.documentation("Represents a user in the system")
  ),
  members = List(
    MemberDefinition(
      "id",
      ShapeId("smithy.api", "String"),
      traits = List(
        TraitApplication.required,
        TraitApplication.documentation("The unique user identifier, in UUID format")
      )
    )
  )
)
```

## Integration with Other Module Types

`TraitApplication` is tightly integrated with shapes and models:

- **`Shape.traits`** — Every shape has a `List[TraitApplication]` representing applied traits
- **`MemberDefinition.traits`** — Structure and union members can have their own traits
- **`SmithyModel`** — Contains `applyStatements` for trait applications added via apply blocks
- **`NodeValue`** — Used to represent trait attribute values
- **`ShapeId`** — Identifies which trait is being applied (e.g., `"smithy.api#required"`)
- **`SmithyParser`** — Extracts trait applications from IDL syntax and converts them to `TraitApplication`s
- **`SmithyPrinter`** — Serializes `TraitApplication`s back to valid IDL syntax

Typical workflow:

```
Smithy IDL (@required, @http(...))
      ↓
SmithyParser (extracts trait syntax)
      ↓
TraitApplication (id + optional value)
      ↓
Shape.traits (list of applications)
      ↓
SmithyPrinter (serializes back to IDL)
```

## Trait Namespacing

Traits are identified by `ShapeId`, allowing custom traits to be defined in different namespaces. Built-in Smithy traits are in the `smithy.api` namespace:

```scala mdoc:compile-only
import zio.blocks.smithy._

val builtinTraits = List(
  ShapeId("smithy.api", "required"),
  ShapeId("smithy.api", "documentation"),
  ShapeId("smithy.api", "http"),
  ShapeId("smithy.api", "error")
)

val customTraits = List(
  ShapeId("com.example", "deprecated"),
  ShapeId("com.example", "experimental")
)
```

## Related Documentation

- [Shape](./shape.md) — Shapes contain traits via `Shape.traits`
- [NodeValue](./node-value.md) — Trait values are `NodeValue`s (e.g., configuration objects)
- [ShapeId](./shape-id.md) — Traits are identified by `ShapeId`
- [SmithyModel](./smithy-model.md) — Contains apply statements for trait applications
- [SmithyParser](./smithy-parser.md) — Parses trait syntax into `TraitApplication`s
- [SmithyPrinter](./smithy-printer.md) — Serializes `TraitApplication`s to IDL

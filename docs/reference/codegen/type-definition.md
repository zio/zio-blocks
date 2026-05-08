---
id: type-definition
title: "TypeDefinition"
---

`TypeDefinition` is a sealed trait that represents any Scala type definition—case classes, sealed traits, enums, objects, newtypes, type aliases, abstract classes, and regular traits. It's the foundation for modeling Scala's type system in the IR.

## Use Cases

- As the common interface for all compound types when building a `ScalaFile`
- When you need to handle multiple type kinds uniformly (e.g., iterate over all types in a file)
- As a base for specific type definitions like `CaseClass`, `SealedTrait`, `Enum`, etc.

## Variants

`TypeDefinition` has 9 concrete implementations:

| Type            | Purpose                     | Scala Feature                              |
|-----------------|-----------------------------|--------------------------------------------|
| `CaseClass`     | Product types with fields   | Scala 2 & 3                                |
| `SealedTrait`   | Sum types (ADTs) with cases | Scala 2 & 3                                |
| `Trait`         | Open trait definitions      | Scala 2 & 3                                |
| `AbstractClass` | Abstract class definitions  | Scala 2 & 3                                |
| `Enum`          | Scala 3 enum definitions    | Scala 3 only                               |
| `ObjectDef`     | Singleton objects           | Scala 2 & 3                                |
| `OpaqueType`    | Opaque type aliases         | Scala 3 (Scala 2 fallback to `type` alias) |
| `Newtype`       | Zero-cost wrappers          | Scala 2 & 3                                |
| `TypeAlias`     | Type aliases                | Scala 2 & 3                                |

## Common Interface

Every `TypeDefinition` provides:

```scala
sealed trait TypeDefinition {
  def name: String              // The name of the type
  def annotations: List[Annotation]  // Annotations applied to it
  def doc: Option[String]       // Optional documentation
}
```

## Choosing the Right Variant

Choose the right `TypeDefinition` for your use case:

### Data Structures

**Case Class** — Immutable record with named fields and automatic `equals`, `hashCode`, `copy`:

```scala
import zio.blocks.codegen.ir._

val user = CaseClass(
  name = "User",
  fields = List(
    Field("id", TypeRef.Long),
    Field("name", TypeRef.String)
  )
)
```

**Newtype** — Zero-cost wrapper for a single underlying type (e.g., `UserId` wrapping `Long`):

```scala
val userId = Newtype("UserId", TypeRef.Long)
```

**Type Alias** — Synonym for an existing type:

```scala
val stringId = TypeAlias("StringId", typeRef = TypeRef.String)
```

### Sum Types (ADTs)

**Sealed Trait** — Discriminated union type with cases:

```scala
val payment = SealedTrait(
  name = "Payment",
  cases = List(
    SealedTraitCase.CaseClassCase(
      CaseClass("Card", List(Field("number", TypeRef.String)))
    ),
    SealedTraitCase.CaseObjectCase("Cash")
  )
)
```

**Enum** (Scala 3 only) — Enumeration with simple or parameterized cases:

```scala
val color = Enum(
  name = "Color",
  cases = List(
    EnumCase.SimpleCase("Red"),
    EnumCase.SimpleCase("Green"),
    EnumCase.ParameterizedCase("Custom", List(Field("rgb", TypeRef.Int)))
  )
)
```

### Abstractions

**Trait** — Open trait for behavior and contracts:

```scala
val comparable = Trait(
  name = "Comparable",
  members = List(
    ObjectMember.DefMember(
      Method("compare", params = Nil, returnType = TypeRef.Int)
    )
  )
)
```

**Abstract Class** — Mix of fields and methods, but not instantiable:

```scala
val entity = AbstractClass(
  name = "Entity",
  fields = List(Field("id", TypeRef.Long))
)
```

**Object** — Singleton object with static-like members:

```scala
val utils = ObjectDef(
  name = "Utils",
  members = List(
    ObjectMember.ValMember("Version", TypeRef.String, "\"1.0.0\"")
  )
)
```

### Type Aliases (Advanced)

**Opaque Type** (Scala 3) — Type-safe wrapper with an underlying type (distinct from `Newtype`):

```scala
val userId2 = OpaqueType("UserId", underlyingType = TypeRef.Long)
```

## Common Operations

All `TypeDefinition` variants support these core operations:

### Accessing Components

```scala
val cc = CaseClass("User", List(Field("id", TypeRef.Long)))

cc.name            // "User"
cc.annotations     // List[Annotation] (empty by default)
cc.doc             // Option[String] (documentation)
```

### With Annotations

Add annotations (like `@deprecated` or custom annotations):

```scala
val annotated = CaseClass(
  name = "OldAPI",
  fields = Nil,
  annotations = List(
    Annotation("deprecated")
  )
)
```

### With Documentation

Include scaladoc or javadoc comments:

```scala
val documented = CaseClass(
  name = "User",
  fields = List(Field("id", TypeRef.Long)),
  doc = Some("Represents a user in the system")
)
```

### With Derives

Add derives clauses for automatic typeclass derivation:

```scala
val derived = CaseClass(
  name = "Point",
  fields = List(
    Field("x", TypeRef.Int),
    Field("y", TypeRef.Int)
  ),
  derives = List("Show", "Eq")
)
```

## Examples

Practical examples demonstrate common usage:

### Example 1: Combining Types in a File

A file with multiple `TypeDefinition` variants:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  types = List(
    // Case class
    CaseClass("Person", List(Field("name", TypeRef.String))),
    
    // Sealed trait (sum type)
    SealedTrait(
      "Result",
      cases = List(
        SealedTraitCase.CaseClassCase(
          CaseClass("Success", List(Field("value", TypeRef("T"))))
        ),
        SealedTraitCase.CaseObjectCase("Failure")
      )
    ),
    
    // Object with static members
    ObjectDef(
      "Config",
      members = List(
        ObjectMember.ValMember("MaxRetries", TypeRef.Int, "3")
      )
    )
  )
)

ScalaEmitter.emit(file, EmitterConfig())
```

### Example 2: Type Parameters and Generic Types

Defining a polymorphic case class:

```scala
import zio.blocks.codegen.ir._

val box = CaseClass(
  name = "Box",
  fields = List(
    Field("value", TypeRef("A"))
  ),
  typeParams = List(
    TypeParam("A")
  )
)
```

Emits:

```scala
final case class Box[A](value: A)
```


---
id: case-class
title: "CaseClass"
---

`CaseClass` represents an immutable case class in the IR. Use it when generating Scala code from data models, APIs, or structured data—it's the most frequently chosen type definition.

## Use Cases

- Modeling product types (records) from OpenAPI schemas, JSON Schema, or data formats
- Defining data transfer objects (DTOs) with named fields
- Creating immutable value types with automatic `equals`, `hashCode`, `copy`

## Construction

Build a case class with a name and field list:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._

val person = CaseClass(
  name = "Person",
  fields = List(
    Field("id", TypeRef.Long),
    Field("name", TypeRef.String),
    Field("email", TypeRef.String)
  )
)
```

With optional type parameters:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._

val box = CaseClass(
  name = "Box",
  fields = List(Field("value", TypeRef("T"))),
  typeParams = List(TypeParam("T"))
)
```

With derives clauses:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._

val user = CaseClass(
  name = "User",
  fields = List(
    Field("id", TypeRef.Long),
    Field("name", TypeRef.String)
  ),
  derives = List("Show", "Eq", "Codec")
)
```

## Key Operations

All `CaseClass` instances support these core operations:

### Accessing Components

Extract parts of a case class:

```scala mdoc:silent
import zio.blocks.codegen.ir._

val person = CaseClass(
  name = "Person",
  fields = List(
    Field("id", TypeRef.Long),
    Field("name", TypeRef.String),
    Field("email", TypeRef.String)
  )
)
```

Access each component:

```scala mdoc
person.name            // "Person"
person.fields          // List[Field]
person.typeParams      // List[TypeParam] (empty if not generic)
person.derives         // List[String]
person.annotations     // List[Annotation]
person.isValueClass    // Boolean
```

### Building with Copy

Modify a case class:

```scala mdoc
val extended = person.copy(
  fields = person.fields :+ Field("phone", TypeRef.String),
  derives = List("Show")
)
```

### Adding a Companion Object

Case classes can have companion objects with static members:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._

val withCompanion = CaseClass(
  name = "Config",
  fields = List(Field("timeout", TypeRef.Long)),
  companion = Some(
    CompanionObject(
      members = List(
        ObjectMember.ValMember("Default", TypeRef("Config"), "Config(5000L)")
      )
    )
  )
)
```

## Examples

Here are concrete examples demonstrating typical `CaseClass` usage patterns:

### Example 1: Simple Case Class

A basic case class with primitive fields:

```scala mdoc:silent:reset
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val order = CaseClass(
  name = "Order",
  fields = List(
    Field("id", TypeRef.Long),
    Field("total", TypeRef("BigDecimal")),
    Field("status", TypeRef.String)
  )
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.shop"),
  types = List(order)
)
```

Emits:

```scala mdoc
ScalaEmitter.emit(file, EmitterConfig())
```

### Example 2: Nested Types

A case class with optional and collection fields:

```scala mdoc:silent:reset
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val user = CaseClass(
  name = "User",
  fields = List(
    Field("id", TypeRef.Long),
    Field("name", TypeRef.String),
    Field("email", TypeRef("Option", List(TypeRef.String))),
    Field("tags", TypeRef("List", List(TypeRef.String)))
  ),
  derives = List("Show")
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  types = List(user)
)
```

Emits:

```scala mdoc
ScalaEmitter.emit(file, EmitterConfig())
```

### Example 3: Generic Case Class

A polymorphic case class with type parameters:

```scala mdoc:silent:reset
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val page = CaseClass(
  name = "Page",
  fields = List(
    Field("items", TypeRef("List", List(TypeRef("T")))),
    Field("total", TypeRef.Long),
    Field("pageSize", TypeRef.Int)
  ),
  typeParams = List(TypeParam("T"))
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  types = List(page)
)
```

Emits:

```scala mdoc
ScalaEmitter.emit(file, EmitterConfig())
```

### Example 4: Case Class with Derives

Automatic typeclass derivation:

```scala mdoc:silent:reset
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val product = CaseClass(
  name = "Product",
  fields = List(
    Field("id", TypeRef.String),
    Field("name", TypeRef.String),
    Field("price", TypeRef("java.math.BigDecimal"))
  ),
  derives = List("Schema", "Codec", "Show")
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  types = List(product)
)
```

Emits:

```scala mdoc
ScalaEmitter.emit(file, EmitterConfig())
```

### Example 5: Case Class with Companion

A case class with factory methods in the companion:

```scala mdoc:silent:reset
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val config = CaseClass(
  name = "AppConfig",
  fields = List(
    Field("host", TypeRef.String),
    Field("port", TypeRef.Int)
  ),
  companion = Some(
    CompanionObject(
      members = List(
        ObjectMember.ValMember(
          "Default",
          TypeRef("AppConfig"),
          "AppConfig(\"localhost\", 8080)"
        )
      )
    )
  )
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  types = List(config)
)
```

Emits:

```scala mdoc
ScalaEmitter.emit(file, EmitterConfig())
```

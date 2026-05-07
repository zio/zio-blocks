---
id: scala-emitter
title: "ScalaEmitter"
---

`ScalaEmitter` is the core emission engine that converts IR models to formatted Scala source code. It provides the main entry point and methods to emit any IR construct as a properly formatted string.

## Use Cases

- Converting a complete `ScalaFile` IR to a Scala source string
- Emitting individual type definitions as Scala code
- Generating imports, annotations, methods, and type references
- Formatting code with consistent indentation and style

## Main Emission Method

Emit a complete Scala file:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  types = List(
    CaseClass("User", List(Field("id", TypeRef.Long)))
  )
)

val config = EmitterConfig()
val sourceCode = ScalaEmitter.emit(file, config)
// sourceCode is a String ready to write to a .scala file
```

## Key Operations
\nAll core operations are shown below:

### Emitting Type References

Convert a type reference to Scala syntax:

```scala
// These methods are used internally by emit, but available if you need them:
ScalaEmitter.emitTypeRef(TypeRef.String)
// Returns: "String"

ScalaEmitter.emitTypeRef(TypeRef.list(TypeRef.Int))
// Returns: "List[Int]"

ScalaEmitter.emitTypeRef(TypeRef.map(TypeRef.String, TypeRef.Int))
// Returns: "Map[String, Int]"
```

### Emitting Type Parameters

Emit generic type parameters with bounds:

```scala
val unbounded = TypeParam("T")
// Emits: "T"

val bounded = TypeParam("T", upperBound = Some(TypeRef("Serializable")))
// Emits: "T <: Serializable"

val covariant = TypeParam("T", variance = "+")
// Emits: "+T"
```

### Emitting Annotations

Convert annotations to Scala syntax:

```scala
val deprecated = Annotation("deprecated")
// Emits: "@deprecated"

val withArg = Annotation("Deprecated", args = List("\"Use newMethod instead\""))
// Emits: "@Deprecated(\"Use newMethod instead\")"
```

## Configuration

The emitter respects `EmitterConfig` settings:

```scala
val config = EmitterConfig(
  indentWidth = 2,              // Spaces per indent level
  sortImports = true,           // Sort import statements
  trailingCommas = true,        // Add trailing commas in collections
  scalaVersion = EmitterConfig.ScalaVersion.Scala3  // Scala version target
)

ScalaEmitter.emit(file, config)
```

See [EmitterConfig](./emitter-config.md) for all available options.

## Examples
\nPractical examples demonstrate common usage:

### Example 1: Emit a Complete File

Generate a Scala file with multiple types:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val file = ScalaFile(
  packageDecl = PackageDecl("com.api"),
  imports = List(
    Import.WildcardImport("zio"),
    Import.SingleImport("scala.collection", "Seq")
  ),
  types = List(
    CaseClass(
      "ApiResponse",
      List(
        Field("status", TypeRef.Int),
        Field("data", TypeRef.optional(TypeRef.String))
      ),
      derives = List("Show")
    )
  )
)

val config = EmitterConfig(indentWidth = 2)
val sourceCode = ScalaEmitter.emit(file, config)

// sourceCode is:
// package com.api
//
// import scala.collection.Seq
// import zio._
//
// final case class ApiResponse(
//   status: Int,
//   data: Option[String]
// ) derives Show
```

### Example 2: Cross-Scala Compatibility

Generate code for different Scala versions:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  types = List(
    Enum(
      name = "Status",
      cases = List(
        EnumCase.SimpleCase("Active"),
        EnumCase.SimpleCase("Inactive")
      )
    )
  )
)

// For Scala 3: emits actual enum
val scala3Config = EmitterConfig(
  scalaVersion = EmitterConfig.ScalaVersion.Scala3
)
val scala3Code = ScalaEmitter.emit(file, scala3Config)

// For Scala 2: emits sealed trait (fallback)
val scala2Config = EmitterConfig(
  scalaVersion = EmitterConfig.ScalaVersion.Scala2
)
val scala2Code = ScalaEmitter.emit(file, scala2Config)
```

Scala 3 emits:

```scala
package com.example

enum Status {
  case Active
  case Inactive
}
```

Scala 2 emits:

```scala
package com.example

sealed trait Status

object Status {
  case object Active extends Status
  case object Inactive extends Status
}
```

### Example 3: Sealed Trait with Multiple Cases

Emit a complete ADT:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val file = ScalaFile(
  packageDecl = PackageDecl("com.errors"),
  types = List(
    SealedTrait(
      name = "DomainError",
      cases = List(
        SealedTraitCase.CaseClassCase(
          CaseClass("ValidationError", List(
            Field("field", TypeRef.String),
            Field("message", TypeRef.String)
          ))
        ),
        SealedTraitCase.CaseClassCase(
          CaseClass("NotFound", List(
            Field("resource", TypeRef.String),
            Field("id", TypeRef.Long)
          ))
        ),
        SealedTraitCase.CaseObjectCase("Unauthorized"),
        SealedTraitCase.CaseObjectCase("InternalError")
      ),
      derives = List("Show")
    )
  )
)

val config = EmitterConfig(trailingCommas = true)
ScalaEmitter.emit(file, config)
```

### Example 4: Generic Types

Emit polymorphic types with proper type parameter syntax:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val file = ScalaFile(
  packageDecl = PackageDecl("com.containers"),
  types = List(
    CaseClass(
      name = "Wrapper",
      fields = List(Field("value", TypeRef("A"))),
      typeParams = List(
        TypeParam("A")
      )
    ),
    CaseClass(
      name = "Pair",
      fields = List(
        Field("left", TypeRef("A")),
        Field("right", TypeRef("B"))
      ),
      typeParams = List(
        TypeParam("A"),
        TypeParam("B")
      )
    )
  )
)

ScalaEmitter.emit(file, EmitterConfig())
```

Emits:

```scala
package com.containers

final case class Wrapper[A](value: A)

final case class Pair[A, B](
  left: A,
  right: B
)
```

### Example 5: Formatting Customization

Control code style with configuration:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  imports = List(
    Import.WildcardImport("scala.collection"),
    Import.SingleImport("java.time", "Instant")
  ),
  types = List(
    CaseClass(
      name = "Record",
      fields = List(
        Field("a", TypeRef.String),
        Field("b", TypeRef.Int),
        Field("c", TypeRef.Boolean)
      )
    )
  )
)

// Wide indentation, sorted imports, trailing commas
val config = EmitterConfig(
  indentWidth = 4,
  sortImports = true,
  trailingCommas = true
)

ScalaEmitter.emit(file, config)
```

## Design Philosophy

`ScalaEmitter` follows three principles:

1. **Pure**: No side effects. Takes IR + config, returns a string. Your code writes files.
2. **Configurable**: `EmitterConfig` controls formatting. Add options as needed for your generator.
3. **Cross-Scala**: Targets both Scala 3 (enums, derives, `*` imports) and Scala 2 (sealed traits, `_` imports) from the same IR.

## Integration

`ScalaEmitter` consumes:

- **[`ScalaFile`](./scala-file.md)** — the complete file IR
- **[`TypeDefinition`](./type-definition.md)** — any type variant
- **[`TypeRef`](./type-ref.md)** — type references
- **[`Field`](./field.md)** — case class fields
- **[`Method`](./method.md)** — methods in objects/classes
- **[`Annotation`](./annotation.md)** — annotations
- **[`EmitterConfig`](./emitter-config.md)** — formatting configuration

In typical usage, you build a complete IR model, then call `ScalaEmitter.emit(file, config)` to get a ready-to-write Scala source string.


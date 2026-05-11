---
id: examples
title: "Complete Examples"
---

This page shows complete, runnable examples demonstrating realistic code generation workflows. Each example builds IR models from scratch and emits formatted Scala code.

## Example 1: Simple Domain Model

Build a complete domain model with a case class and sealed trait:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

// Define error types
val error = SealedTrait(
  name = "Error",
  cases = List(
    SealedTraitCase.CaseObjectCase("NotFound"),
    SealedTraitCase.CaseObjectCase("Unauthorized"),
    SealedTraitCase.CaseClassCase(
      CaseClass("ValidationError", List(
        Field("field", TypeRef.String),
        Field("message", TypeRef.String)
      ))
    )
  ),
  derives = List("Show")
)

// Define a user
val user = CaseClass(
  name = "User",
  fields = List(
    Field("id", TypeRef.Long),
    Field("email", TypeRef.String),
    Field("name", TypeRef.String),
    Field("active", TypeRef.Boolean, defaultValue = Some("true"))
  ),
  derives = List("Show", "Eq")
)

// Assemble into a file
val file = ScalaFile(
  packageDecl = PackageDecl("com.example.domain"),
  imports = List(
    Import.WildcardImport("zio")
  ),
  types = List(error, user)
)

val config = EmitterConfig(indentWidth = 2)
val code = ScalaEmitter.emit(file, config)
```

This generates:

```scala
package com.example.domain

import zio._

sealed trait Error

object Error {
  case object NotFound extends Error
  case object Unauthorized extends Error
  final case class ValidationError(
    field: String,
    message: String
  ) extends Error
}
derives Show

final case class User(
  id: Long,
  email: String,
  name: String,
  active: Boolean = true
) derives Show, Eq
```

## Example 2: Generic Container Types

Build polymorphic types with type parameters:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

// A generic wrapper
val container = CaseClass(
  name = "Container",
  fields = List(
    Field("value", TypeRef("T")),
    Field("metadata", TypeRef("Map", List(TypeRef.String, TypeRef.String)))
  ),
  typeParams = List(TypeParam("T")),
  derives = List("Show")
)

// A generic result type
val result = SealedTrait(
  name = "Result",
  typeParams = List(TypeParam("A")),
  cases = List(
    SealedTraitCase.CaseClassCase(
      CaseClass("Success", List(
        Field("value", TypeRef("A"))
      ))
    ),
    SealedTraitCase.CaseClassCase(
      CaseClass("Failure", List(
        Field("error", TypeRef.String)
      ))
    )
  )
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.example.types"),
  types = List(container, result)
)

ScalaEmitter.emit(file, EmitterConfig())
```

Generates:

```scala
package com.example.types

final case class Container[T](
  value: T,
  metadata: Map[String, String]
) derives Show

sealed trait Result[A]

object Result {
  final case class Success[A](value: A) extends Result[A]
  final case class Failure[A](error: String) extends Result[A]
}
```

## Example 3: API Request/Response Models

Generate HTTP API models from scratch:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

// Request model
val createUserReq = CaseClass(
  name = "CreateUserRequest",
  fields = List(
    Field("email", TypeRef.String),
    Field("name", TypeRef.String),
    Field("password", TypeRef.String)
  ),
  derives = List("Codec")
)

// Response model
val user = CaseClass(
  name = "User",
  fields = List(
    Field("id", TypeRef.Long),
    Field("email", TypeRef.String),
    Field("name", TypeRef.String),
    Field("createdAt", TypeRef.String)
  ),
  derives = List("Codec", "Show")
)

// Error responses
val apiError = SealedTrait(
  name = "ApiError",
  cases = List(
    SealedTraitCase.CaseClassCase(
      CaseClass("BadRequest", List(
        Field("message", TypeRef.String)
      ))
    ),
    SealedTraitCase.CaseClassCase(
      CaseClass("Conflict", List(
        Field("resource", TypeRef.String),
        Field("details", TypeRef.String)
      ))
    ),
    SealedTraitCase.CaseObjectCase("InternalServerError")
  ),
  derives = List("Codec", "Show")
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.example.api.models"),
  imports = List(
    Import.SingleImport("zio.json", "JsonCodec")
  ),
  types = List(createUserReq, user, apiError)
)

ScalaEmitter.emit(file, EmitterConfig())
```

## Example 4: Cross-Scala Version Generation

Emit code for both Scala 3 and Scala 2:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

// An enum type
val status = Enum(
  name = "OrderStatus",
  cases = List(
    EnumCase.SimpleCase("Pending"),
    EnumCase.SimpleCase("Shipped"),
    EnumCase.SimpleCase("Delivered"),
    EnumCase.SimpleCase("Cancelled")
  ),
  derives = List("Show")
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.shop.models"),
  types = List(status)
)

// Generate for Scala 3
val scala3Code = ScalaEmitter.emit(
  file,
  EmitterConfig(scala3Syntax = true)
)

// Generate for Scala 2
val scala2Code = ScalaEmitter.emit(
  file,
  EmitterConfig(scala3Syntax = false)
)
```

Scala 3 output:

```scala
package com.shop.models

enum OrderStatus {
  case Pending
  case Shipped
  case Delivered
  case Cancelled
}
derives Show
```

Scala 2 output:

```scala
package com.shop.models

sealed trait OrderStatus

object OrderStatus {
  case object Pending extends OrderStatus
  case object Shipped extends OrderStatus
  case object Delivered extends OrderStatus
  case object Cancelled extends OrderStatus
}
derives Show
```

## Example 5: Object with Static Methods and Values

Generate a companion object with utility methods:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val config = CaseClass(
  name = "AppConfig",
  fields = List(
    Field("host", TypeRef.String),
    Field("port", TypeRef.Int),
    Field("timeout", TypeRef.Long)
  ),
  companion = Some(
    CompanionObject(
      members = List(
        ObjectMember.ValMember(
          "Default",
          TypeRef("AppConfig"),
          "AppConfig(\"localhost\", 8080, 30000L)"
        ),
        ObjectMember.ValMember(
          "Production",
          TypeRef("AppConfig"),
          "AppConfig(\"api.example.com\", 443, 60000L)"
        )
      )
    )
  ),
  derives = List("Show")
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.example.config"),
  types = List(config)
)

ScalaEmitter.emit(file, EmitterConfig())
```

Generates:

```scala
package com.example.config

final case class AppConfig(
  host: String,
  port: Int,
  timeout: Long
) derives Show

object AppConfig {
  val Default: AppConfig = AppConfig("localhost", 8080, 30000L)
  val Production: AppConfig = AppConfig("api.example.com", 443, 60000L)
}
```

## Example 6: Complex Nested Types

Model deeply nested generic structures:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

// Pagination wrapper
val page = CaseClass(
  name = "Page",
  fields = List(
    Field(
      "items",
      TypeRef("List", List(TypeRef("T")))
    ),
    Field("total", TypeRef.Long),
    Field("page", TypeRef.Int),
    Field("pageSize", TypeRef.Int),
    Field(
      "errors",
      TypeRef("List", List(
        TypeRef("Map", List(
          TypeRef.String,
          TypeRef.String
        ))
      ))
    )
  ),
  typeParams = List(TypeParam("T"))
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.example.pagination"),
  imports = List(
    Import.WildcardImport("scala.collection")
  ),
  types = List(page)
)

ScalaEmitter.emit(file, EmitterConfig(indentWidth = 2))
```

Generates:

```scala
package com.example.pagination

import scala.collection._

final case class Page[T](
  items: List[T],
  total: Long,
  page: Int,
  pageSize: Int,
  errors: List[Map[String, String]]
)
```

## Example 7: Building Files Incrementally

Add types to a file step by step:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

// Build types
val user = CaseClass("User", List(Field("id", TypeRef.Long)))
val order = CaseClass("Order", List(
  Field("id", TypeRef.Long),
  Field("userId", TypeRef.Long)
))
val status = Enum(
  "OrderStatus",
  cases = List(
    EnumCase.SimpleCase("Pending"),
    EnumCase.SimpleCase("Complete")
  )
)

// Assemble into a file
val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  imports = List(Import.WildcardImport("zio")),
  types = List(user, order, status)
)

// Emit the complete file
ScalaEmitter.emit(file, EmitterConfig())
```

## Example 8: Multi-File Generation

Generate multiple files from a single data model:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val config = EmitterConfig()

// Models file
val modelsFile = ScalaFile(
  packageDecl = PackageDecl("com.example.models"),
  types = List(
    CaseClass("User", List(
      Field("id", TypeRef.Long),
      Field("name", TypeRef.String)
    )),
    CaseClass("Product", List(
      Field("id", TypeRef.Long),
      Field("price", TypeRef("BigDecimal"))
    ))
  )
)

// Errors file
val errorsFile = ScalaFile(
  packageDecl = PackageDecl("com.example.errors"),
  types = List(
    SealedTrait(
      "DomainError",
      cases = List(
        SealedTraitCase.CaseObjectCase("NotFound"),
        SealedTraitCase.CaseObjectCase("Unauthorized")
      )
    )
  )
)

// Emit both
val modelsCode = ScalaEmitter.emit(modelsFile, config)
val errorsCode = ScalaEmitter.emit(errorsFile, config)

// In real usage, you'd write these to separate files
// IO.writeFile("src/main/scala/com/example/models.scala", modelsCode)
// IO.writeFile("src/main/scala/com/example/errors.scala", errorsCode)
```

## Key Patterns

These patterns recur across all code generation workflows:

### Building Incrementally

When generating code, you'll often build types incrementally:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._

val types = List(
  CaseClass("User", List()),
  CaseClass("Product", List())
)

// Create file with all types
val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  types = types
)
```

### Reusing Type Definitions

Define common types once and compose them:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._

// Common error type
val baseError = SealedTrait(
  "Error",
  cases = List(
    SealedTraitCase.CaseObjectCase("NotFound")
  )
)

// File 1
val file1 = ScalaFile(
  packageDecl = PackageDecl("com.module1"),
  types = List(baseError)
)

// File 2 (same error, different package)
val file2 = ScalaFile(
  packageDecl = PackageDecl("com.module2"),
  types = List(baseError)
)
```

### Configuration Strategies

Choose configuration once and apply to all files:

```scala mdoc:compile-only
import zio.blocks.codegen.emit._

// Your project's standard config
val projectConfig = EmitterConfig(
  indentWidth = 2,
  trailingCommas = true,
  sortImports = true,
  scala3Syntax = true
)

// Apply to all generated files
// ScalaEmitter.emit(file1, projectConfig)
// ScalaEmitter.emit(file2, projectConfig)
// ScalaEmitter.emit(file3, projectConfig)
```

## Integration with Generators

In a real code generator, you'd:

1. **Parse** your source format (OpenAPI, Smithy, Protobuf, etc.)
2. **Build IR** models from parsed data
3. **Configure** the emitter once
4. **Emit** files in a loop
5. **Write** strings to disk

Example structure:

```scala mdoc:compile-only
// Pseudocode: real generators follow this pattern

// def generateFromOpenAPI(spec: OpenAPI): Unit = {
//   val config = EmitterConfig(...)
//
//   for (schema <- spec.schemas) {
//     val irType = openAPISchemaToIR(schema)  // Parse → IR
//     val file = ScalaFile(
//       packageDecl = PackageDecl("com.generated"),
//       types = List(irType)
//     )
//     val code = ScalaEmitter.emit(file, config)  // IR → Scala
//     writeFile(s"generated/${schema.name}.scala", code)  // Write
//   }
// }
```

This separation of concerns (format-specific parsing → generic IR → emission) is the core value of `zio-blocks-codegen`.


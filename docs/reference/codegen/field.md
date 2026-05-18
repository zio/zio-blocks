---
id: field
title: "Field"
---

`Field` represents a class field (constructor parameter) in the IR. Combine a name with a type reference, optionally adding default values and modifiers.

## Use Cases

- Defining constructor parameters for case classes
- Specifying fields in abstract classes
- Modeling data structure fields with optional defaults

## Construction

Build a field with a name and type:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._

val id = Field("id", TypeRef.Long)
val name = Field("name", TypeRef.String)
val email = Field("email", TypeRef.optional(TypeRef.String))
```

With default value:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._

val timeout = Field("timeout", TypeRef.Long, defaultValue = Some("5000L"))
val retries = Field("retries", TypeRef.Int, defaultValue = Some("3"))
```

With annotations:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._

val annotated = Field(
  "id",
  TypeRef.Long,
  annotations = List(Annotation("id"))
)
```

## Key Operations

All `Field` instances support these operations:

### Accessing Components

Extract parts of a field:

```scala mdoc:silent:reset
import zio.blocks.codegen.ir._

val field = Field("age", TypeRef.Int, defaultValue = Some("0"))
```

View field properties:

```scala mdoc
import zio.blocks.codegen.ir._

field.name           // "age"
field.typeRef        // TypeRef.Int
field.defaultValue   // Some("0")
field.annotations    // List[Annotation]
```

### Building with Copy

Modify a field using copy:

```scala mdoc:silent:reset
import zio.blocks.codegen.ir._

val field = Field("age", TypeRef.Int, defaultValue = Some("0"))
```

Apply modifications:

```scala mdoc
import zio.blocks.codegen.ir._

val updated = field.copy(
  defaultValue = Some("18"),
  annotations = List(Annotation("min"))
)
```

## Examples

These examples show practical usage patterns for `Field`:

### Example 1: Simple Fields

Create fields for a case class:

```scala mdoc:silent:reset
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val order = CaseClass(
  name = "Order",
  fields = List(
    Field("id", TypeRef.Long),
    Field("customerId", TypeRef.String),
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
import zio.blocks.codegen.emit._

ScalaEmitter.emit(file, EmitterConfig())
```

### Example 2: Fields with Defaults

Case class fields with default values:

```scala mdoc:silent:reset
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val config = CaseClass(
  name = "DatabaseConfig",
  fields = List(
    Field("host", TypeRef.String, defaultValue = Some("\"localhost\"")),
    Field("port", TypeRef.Int, defaultValue = Some("5432")),
    Field("timeout", TypeRef.Long, defaultValue = Some("30000L"))
  )
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  types = List(config)
)
```

Emits:

```scala mdoc
import zio.blocks.codegen.emit._

ScalaEmitter.emit(file, EmitterConfig())
```

### Example 3: Optional and Collection Fields

Fields with generic types:

```scala mdoc:silent:reset
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val article = CaseClass(
  name = "Article",
  fields = List(
    Field("title", TypeRef.String),
    Field("content", TypeRef.String),
    Field("author", TypeRef.optional(TypeRef.String)),
    Field("tags", TypeRef.list(TypeRef.String)),
    Field("metadata", TypeRef.map(TypeRef.String, TypeRef.String))
  )
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  types = List(article)
)
```

Emits:

```scala mdoc
import zio.blocks.codegen.emit._

ScalaEmitter.emit(file, EmitterConfig())
```

### Example 4: Nested Generic Types

Complex field types:

```scala mdoc:silent:reset
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val response = CaseClass(
  name = "Response",
  fields = List(
    Field(
      "data",
      TypeRef("Option", List(
        TypeRef("List", List(TypeRef.String))
      ))
    ),
    Field(
      "errors",
      TypeRef("List", List(
        TypeRef("Map", List(TypeRef.String, TypeRef.String))
      ))
    )
  )
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  types = List(response)
)
```

Emits:

```scala mdoc
import zio.blocks.codegen.emit._

ScalaEmitter.emit(file, EmitterConfig())
```

### Example 5: Fields with Type Parameters

Fields using generic type variables:

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
import zio.blocks.codegen.emit._

ScalaEmitter.emit(file, EmitterConfig())
```

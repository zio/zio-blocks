---
id: emitter-config
title: "EmitterConfig"
---

`EmitterConfig` controls how `ScalaEmitter` formats the generated Scala code. Customize indentation, import sorting, trailing commas, and target Scala version (2 vs 3) by creating a configured instance.

## Use Cases

- Configuring code style (indentation, commas) to match your project conventions
- Targeting Scala 3 features (enums, derives) or Scala 2 fallback syntax
- Controlling import statement ordering
- Emitting code for different codebases with different style preferences

## Configuration Fields

| Field            | Type         | Default | Description                             |
|------------------|--------------|---------|-----------------------------------------|
| `indentWidth`    | Int          | 2       | Spaces per indentation level            |
| `sortImports`    | Boolean      | true    | Sort import statements alphabetically   |
| `trailingCommas` | Boolean      | true    | Add trailing commas in collections      |
| `scalaVersion`   | ScalaVersion | Scala3  | Target Scala version (Scala2 or Scala3) |

## ScalaVersion Enum

The `ScalaVersion` enum controls which Scala version syntax the emitter uses:

```scala
import zio.blocks.codegen.emit._

// Target Scala 3: uses enums, derives, * imports, as renames
val scala3 = EmitterConfig.ScalaVersion.Scala3

// Target Scala 2: uses sealed traits, _ imports, => renames
val scala2 = EmitterConfig.ScalaVersion.Scala2
```

## Construction

Create a default configuration:

```scala
import zio.blocks.codegen.emit._

val default = EmitterConfig()
// indentWidth=2, sortImports=true, trailingCommas=true, scalaVersion=Scala3
```

With custom settings:

```scala
val custom = EmitterConfig(
  indentWidth = 4,
  sortImports = true,
  trailingCommas = false,
  scalaVersion = EmitterConfig.ScalaVersion.Scala2
)
```

## Key Operations

All `EmitterConfig` instances provide these operations:

### Accessing Configuration

Read configuration values:

```scala
val config = EmitterConfig(indentWidth = 4)

config.indentWidth      // 4
config.sortImports      // true (default)
config.trailingCommas   // true (default)
config.scalaVersion     // ScalaVersion.Scala3 (default)
```

### Building with Copy

Modify a configuration:

```scala
val base = EmitterConfig()

val scala2Config = base.copy(
  scalaVersion = EmitterConfig.ScalaVersion.Scala2
)

val wideIndentConfig = base.copy(
  indentWidth = 4
)
```

## Examples

Here are practical examples showing different configuration scenarios:

### Example 1: Standard Configuration

The default configuration for most projects:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  types = List(
    CaseClass("User", List(
      Field("id", TypeRef.Long),
      Field("name", TypeRef.String)
    ))
  )
)

val config = EmitterConfig()
ScalaEmitter.emit(file, config)
```

### Example 2: Wide Indentation (4 spaces)

For projects preferring 4-space indentation:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  types = List(
    SealedTrait(
      "Result",
      cases = List(
        SealedTraitCase.CaseClassCase(
          CaseClass("Success", List(
            Field("value", TypeRef.String)
          ))
        ),
        SealedTraitCase.CaseObjectCase("Failure")
      )
    )
  )
)

val config = EmitterConfig(indentWidth = 4)
ScalaEmitter.emit(file, config)
```

Emits with 4-space indentation:

```scala
package com.example

sealed trait Result

object Result {
    final case class Success(
        value: String
    ) extends Result
    case object Failure extends Result
}
```

### Example 3: Scala 2 Compatibility

Target Scala 2 syntax for older codebases:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val file = ScalaFile(
  packageDecl = PackageDecl("com.legacy"),
  imports = List(
    Import.WildcardImport("scala.collection")
  ),
  types = List(
    Enum(
      name = "Color",
      cases = List(
        EnumCase.SimpleCase("Red"),
        EnumCase.SimpleCase("Blue")
      )
    )
  )
)

val scala2Config = EmitterConfig(
  scalaVersion = EmitterConfig.ScalaVersion.Scala2
)

ScalaEmitter.emit(file, scala2Config)
```

Emits sealed trait syntax (Scala 2 compatible):

```scala
package com.legacy

import scala.collection._

sealed trait Color

object Color {
  case object Red extends Color
  case object Blue extends Color
}
```

### Example 4: No Trailing Commas

For projects with strict no-trailing-commas rules:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val file = ScalaFile(
  packageDecl = PackageDecl("com.strict"),
  types = List(
    CaseClass(
      "Options",
      List(
        Field("a", TypeRef.String),
        Field("b", TypeRef.Int),
        Field("c", TypeRef.Boolean)
      )
    )
  )
)

val config = EmitterConfig(
  trailingCommas = false
)

ScalaEmitter.emit(file, config)
```

Emits without trailing comma on last field:

```scala
package com.strict

final case class Options(
  a: String,
  b: Int,
  c: Boolean
)
```

### Example 5: Unsorted Imports

For projects that manage imports manually:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  imports = List(
    Import.WildcardImport("zio"),
    Import.SingleImport("scala.collection", "Seq"),
    Import.WildcardImport("scala")
  ),
  types = List(
    CaseClass("Data", List(
      Field("items", TypeRef("Seq", List(TypeRef.String)))
    ))
  )
)

val config = EmitterConfig(
  sortImports = false  // Keep imports in declaration order
)

ScalaEmitter.emit(file, config)
```

Preserves import order:

```scala
package com.example

import zio._
import scala.collection.Seq
import scala._

final case class Data(
  items: Seq[String]
)
```

### Example 6: Custom Combination

Combining multiple preferences:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val file = ScalaFile(
  packageDecl = PackageDecl("com.myproject"),
  imports = List(
    Import.WildcardImport("zio")
  ),
  types = List(
    SealedTrait(
      "Response",
      cases = List(
        SealedTraitCase.CaseClassCase(
          CaseClass("Ok", List(
            Field("data", TypeRef.String),
            Field("status", TypeRef.Int)
          ))
        ),
        SealedTraitCase.CaseObjectCase("Error")
      )
    )
  )
)

val config = EmitterConfig(
  indentWidth = 4,
  sortImports = true,
  trailingCommas = false,
  scalaVersion = EmitterConfig.ScalaVersion.Scala2
)

ScalaEmitter.emit(file, config)
```

## Cross-Scala Behavior

The `scalaVersion` field affects code generation:

### Scala 3 Features

When targeting Scala 3, the emitter uses:

- **Enums**: `enum Color { case Red; case Blue }`
- **Derives**: `derives Show, Eq`
- **Wildcard imports**: `import scala.collection.*`
- **Rename imports**: `import foo.bar as baz`
- **Opaque types**: `opaque type UserId = Long`

### Scala 2 Fallback

When targeting Scala 2, the emitter uses:

- **Sealed traits** instead of enums: `sealed trait Color` + `case object Red`
- **Implicit/given** syntax converts to Scala 2 compatible form
- **Wildcard imports**: `import scala.collection._`
- **Rename imports**: `import foo.bar => baz`
- **Type aliases** instead of opaque types: `type UserId = Long`

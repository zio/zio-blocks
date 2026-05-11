---
id: sealed-trait
title: "SealedTrait"
---

`SealedTrait` represents a sealed trait in the IR—a sum type (algebraic data type) that enumerates all possible cases. It's essential for modeling discriminated unions and exhaustive pattern matching.

## Use Cases

- Modeling sum types (ADTs) from API responses or domain models
- Creating hierarchies of case classes and case objects
- Defining error types with multiple failure cases
- Implementing discriminated unions with exhaustive matching

## Construction

Build a sealed trait with cases:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._

val httpStatus = SealedTrait(
  name = "HttpStatus",
  cases = List(
    SealedTraitCase.CaseObjectCase("Ok"),
    SealedTraitCase.CaseObjectCase("NotFound"),
    SealedTraitCase.CaseObjectCase("ServerError")
  )
)
```

With case classes as cases:

```scala mdoc:silent
import zio.blocks.codegen.ir._

val result = SealedTrait(
  name = "Result",
  cases = List(
    SealedTraitCase.CaseClassCase(
      CaseClass("Success", List(Field("value", TypeRef.String)))
    ),
    SealedTraitCase.CaseClassCase(
      CaseClass("Failure", List(Field("error", TypeRef.String)))
    )
  )
)
```

With type parameters:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._

val option = SealedTrait(
  name = "Option",
  typeParams = List(TypeParam("A")),
  cases = List(
    SealedTraitCase.CaseObjectCase("None"),
    SealedTraitCase.CaseClassCase(
      CaseClass("Some", List(Field("value", TypeRef("A"))))
    )
  )
)
```

## Key Operations

All core operations are shown below:

### Accessing Components

Extract parts of a sealed trait:

```scala mdoc
result.name          // "Result"
result.cases         // List[SealedTraitCase]
result.typeParams    // List[TypeParam] (empty if not generic)
result.derives       // List[String]
result.annotations   // List[Annotation]
```

### Working with Cases

Each case is either a `CaseObjectCase` or `CaseClassCase`:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._

val caseObj = SealedTraitCase.CaseObjectCase("Unknown")

val caseClass = SealedTraitCase.CaseClassCase(
  CaseClass("Error", List(Field("msg", TypeRef.String)))
)
```

### Building with Copy

Modify a sealed trait:

```scala mdoc
val updated = result.copy(
  cases = result.cases :+ SealedTraitCase.CaseObjectCase("Pending"),
  derives = List("Show")
)
```

## Examples

Practical examples demonstrate common usage:

### Example 1: Simple Enum-Like Sealed Trait

A sealed trait with only case objects:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val color = SealedTrait(
  name = "Color",
  cases = List(
    SealedTraitCase.CaseObjectCase("Red"),
    SealedTraitCase.CaseObjectCase("Green"),
    SealedTraitCase.CaseObjectCase("Blue")
  )
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.graphics"),
  types = List(color)
)

ScalaEmitter.emit(file, EmitterConfig())
```

Emits:

```scala
package com.graphics

sealed trait Color

object Color {
  case object Red extends Color
  case object Green extends Color
  case object Blue extends Color
}
```

### Example 2: Mixed Case Objects and Case Classes

A sealed trait with both simple and complex cases:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._

val payment = SealedTrait(
  name = "Payment",
  cases = List(
    SealedTraitCase.CaseClassCase(
      CaseClass("CreditCard", List(
        Field("number", TypeRef.String),
        Field("expiry", TypeRef.String)
      ))
    ),
    SealedTraitCase.CaseClassCase(
      CaseClass("BankTransfer", List(
        Field("accountNumber", TypeRef.String)
      ))
    ),
    SealedTraitCase.CaseObjectCase("Cash"),
    SealedTraitCase.CaseObjectCase("Check")
  ),
  derives = List("Show")
)
```

Emits:

```scala
sealed trait Payment

object Payment {
  case class CreditCard(
    number: String,
    expiry: String
  ) extends Payment
  
  case class BankTransfer(
    accountNumber: String
  ) extends Payment
  
  case object Cash extends Payment
  case object Check extends Payment
}
```

### Example 3: Generic Sealed Trait

A polymorphic sealed trait with type parameters:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._

val either = SealedTrait(
  name = "Either",
  typeParams = List(TypeParam("L"), TypeParam("R")),
  cases = List(
    SealedTraitCase.CaseClassCase(
      CaseClass("Left", List(Field("value", TypeRef("L"))))
    ),
    SealedTraitCase.CaseClassCase(
      CaseClass("Right", List(Field("value", TypeRef("R"))))
    )
  )
)
```

Emits:

```scala
sealed trait Either[L, R]

object Either {
  final case class Left[L, R](value: L) extends Either[L, R]
  final case class Right[L, R](value: R) extends Either[L, R]
}
```

### Example 4: Error ADT

A sealed trait for error handling:

```scala mdoc:compile-only
import zio.blocks.codegen.ir._

val appError = SealedTrait(
  name = "AppError",
  cases = List(
    SealedTraitCase.CaseClassCase(
      CaseClass("ValidationError", List(
        Field("field", TypeRef.String),
        Field("reason", TypeRef.String)
      ))
    ),
    SealedTraitCase.CaseClassCase(
      CaseClass("NotFound", List(
        Field("id", TypeRef.Long)
      ))
    ),
    SealedTraitCase.CaseObjectCase("Unauthorized"),
    SealedTraitCase.CaseObjectCase("InternalServerError")
  ),
  derives = List("Show")
)
```

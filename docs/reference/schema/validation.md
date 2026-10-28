---
id: validation
title: "Validation"
---

`Validation` is a sealed trait in ZIO Blocks that represents declarative constraints on primitive values. Validations are attached to `PrimitiveType` instances and are checked by `DynamicSchema#check`. Decoding does not check them; a wrapper type built with `Schema#transform` is the way to enforce a rule while decoding.

## Overview

The validation system in ZIO Blocks provides:

- **Declarative constraints** on numeric and string values, checked by `DynamicSchema#check`
- **Enforcement during decoding** for wrapper types built with `Schema#transform`, whose check runs when a value is decoded
- **Integration with wrapper types** via `Schema#transform` for custom validation logic
- **Schema error reporting** with path information for debugging via [`SchemaError`](./schema-error.md)

The `Validation` cases form a tree of numeric and string constraints:

```
Validation[A]
 ├── Validation.None                    (no constraint)
 │
 ├── Validation.Numeric[A]              (numeric constraints)
 │    ├── Positive                      (> 0)
 │    ├── Negative                      (< 0)
 │    ├── NonPositive                   (<= 0)
 │    ├── NonNegative                   (>= 0)
 │    ├── Range[A](min, max)            (within bounds)
 │    └── Set[A](values)                (one of specific values)
 │
 └── Validation.String                  (string constraints)
      ├── NonEmpty                      (length > 0)
      ├── Empty                         (length == 0)
      ├── Blank                         (whitespace only)
      ├── NonBlank                      (has non-whitespace)
      ├── Length(min, max)              (length bounds)
      └── Pattern(regex)                (regex match)
```

## Built-in Validations

`Validation` has two families of built-in constraints, one for numeric primitives and one for strings.

### Numeric Validations

Numeric validations apply to `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `BigInt`, and `BigDecimal`. The sign constraints are objects with no arguments, and the range and set constraints take their bounds as arguments:

```scala mdoc:compile-only
import zio.blocks.schema.Validation

// Sign constraints
val positive    = Validation.Numeric.Positive     // value > 0
val negative    = Validation.Numeric.Negative     // value < 0
val nonPositive = Validation.Numeric.NonPositive  // value <= 0
val nonNegative = Validation.Numeric.NonNegative  // value >= 0

// Range constraint (inclusive bounds)
val between    = Validation.Numeric.Range(Some(1), Some(100))  // 1 <= value <= 100
val atLeast    = Validation.Numeric.Range(Some(0), None)       // value >= 0 (no upper bound)
val atMost     = Validation.Numeric.Range(None, Some(1000))    // value <= 1000 (no lower bound)

// Set constraint (value must be one of the specified values)
val smallPrimes = Validation.Numeric.Set(Set(1, 2, 3, 5, 8, 13))
```

### String Validations

String validations apply to `String` primitive types. The `Length` and `Pattern` constraints take a bound or a regular expression:

```scala mdoc:compile-only
import zio.blocks.schema.Validation

// Content constraints
val nonEmpty = Validation.String.NonEmpty  // string.nonEmpty (length > 0)
val empty    = Validation.String.Empty     // string.isEmpty (length == 0)
val blank    = Validation.String.Blank     // string.trim.isEmpty (whitespace only)
val nonBlank = Validation.String.NonBlank  // string.trim.nonEmpty (has non-whitespace)

// Length constraint (inclusive bounds)
val lengthBetween = Validation.String.Length(Some(1), Some(255))  // 1 <= length <= 255
val lengthAtLeast = Validation.String.Length(Some(3), None)       // length >= 3
val lengthAtMost  = Validation.String.Length(None, Some(100))     // length <= 100

// Pattern constraint (regex)
val lowercase = Validation.String.Pattern("^[a-z]+$")   // lowercase letters only
val zipCode   = Validation.String.Pattern("^\\d{5}$")   // exactly 5 digits
```

## Validation with PrimitiveType

Validations are attached to `PrimitiveType` instances. Each primitive type carries its validation constraint:

```scala mdoc:compile-only
import zio.blocks.schema.{PrimitiveType, Validation}

// Int with no validation
val intType = PrimitiveType.Int(Validation.None)

// Positive Int
val positiveIntType = PrimitiveType.Int(Validation.Numeric.Positive)

// Non-empty String
val nonEmptyStringType = PrimitiveType.String(Validation.String.NonEmpty)

// String matching email pattern
val emailPattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"
val emailStringType = PrimitiveType.String(Validation.String.Pattern(emailPattern))

// Int in range 1-10
val rangeIntType = PrimitiveType.Int(Validation.Numeric.Range(Some(1), Some(10)))
```

## Error Handling with SchemaError

When validation fails, ZIO Blocks returns a `SchemaError` that provides detailed information about what went wrong and where.

### SchemaError Structure

A `SchemaError` wraps a non-empty list of `Single` errors. The cases that matter for validation are these; `SchemaError.Single` also has `MigrationError` and the other `IntoError` cases:

```text
SchemaError.Single
 ├── ConversionFailed(source, details, cause)  // transformation/validation failure
 ├── MissingField(source, fieldName)           // required field not present
 ├── DuplicatedField(source, fieldName)        // field appears multiple times
 ├── ExpectationMismatch(source, expectation)  // type mismatch
 ├── UnknownCase(source, caseName)             // unknown variant case
 └── Message(source, details)                  // generic message
```

### Creating Validation Errors

Use the factory methods on `SchemaError` to create errors:

```scala mdoc:compile-only
import zio.blocks.schema.SchemaError

// For validation failures in transform
val error = SchemaError.validationFailed("must be positive")

// Generic message error
val msgError = SchemaError("Invalid input")

// With path information
import zio.blocks.schema.DynamicOptic
val pathError = SchemaError.message("Value out of range", DynamicOptic.root.field("age"))

// Conversion failure with details
val convError = SchemaError.conversionFailed(Nil, "Expected ISO date format")
```

### Error Messages and Paths

`SchemaError` includes path information showing where in the data structure the error occurred. `SchemaError#message` prints the errors together with their paths, and the path methods add a segment to the front of every error:

```scala mdoc:compile-only
import zio.blocks.schema.SchemaError

val error: SchemaError = SchemaError.validationFailed("must be positive")

// Get the full error message
val msg: String = error.message

// Add path context to errors
val atField = error.atField("name")    // prepend .name to path
val atIndex = error.atIndex(0)         // prepend [0] to path
val atCase  = error.atCase("Some")     // prepend case context

// Combine multiple errors
val combined = error ++ SchemaError("Invalid input")
```

The decoding examples below show real messages with their paths.

## Integration with Wrapper Types

The most common way to use validation in ZIO Blocks is through `Schema#transform`, which creates a schema for a wrapper type with validation logic. The check runs when a value is decoded, so an invalid input never builds the wrapper.

### Basic Wrapper with Validation

To reject non-positive integers in a wrapper type, throw a `SchemaError` in the function that builds the wrapper:

```scala mdoc:silent:reset
import zio.blocks.schema.{Schema, SchemaError}
import zio.blocks.schema.json.JsonFormat

case class PositiveInt private (value: Int)

object PositiveInt {
  def unsafeMake(n: Int): PositiveInt =
    if (n > 0) PositiveInt(n)
    else throw SchemaError.validationFailed("must be positive")

  implicit val schema: Schema[PositiveInt] =
    Schema[Int].transform(unsafeMake, _.value)
}
```

Decoding a positive number builds the wrapper, and decoding a negative number returns `Left`:

```scala mdoc
println(PositiveInt.schema.derive(JsonFormat).decode("5").map(_.value))
println(PositiveInt.schema.derive(JsonFormat).decode("-5").left.map(_.message))
```

### Email Type with Regex Validation

To check a string against a regular expression before building the wrapper:

```scala mdoc:silent:reset
import zio.blocks.schema.{Schema, SchemaError}

case class Email private (value: String)

object Email {
  private val EmailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".r

  def unsafeMake(s: String): Email =
    s match {
      case EmailRegex(_*) => Email(s)
      case _ => throw SchemaError.validationFailed("Invalid email format")
    }

  implicit val schema: Schema[Email] =
    Schema[String].transform(unsafeMake, _.value)
}
```

### NonEmptyString with a Non-Empty Check

To reject an empty string in a wrapper type:

```scala mdoc:silent:reset
import zio.blocks.schema.{Schema, SchemaError}

case class NonEmptyString private (value: String)

object NonEmptyString {
  def unsafeMake(s: String): NonEmptyString =
    if (s.nonEmpty) NonEmptyString(s)
    else throw SchemaError.validationFailed("String must not be empty")

  implicit val schema: Schema[NonEmptyString] =
    Schema[String].transform(unsafeMake, _.value)
}
```

### Range-Bounded Integer

To keep an integer between 0 and 100, with the bounds in the message:

```scala mdoc:silent:reset
import zio.blocks.schema.{Schema, SchemaError}

case class Percentage private (value: Int)

object Percentage {
  def unsafeMake(n: Int): Percentage =
    if (n >= 0 && n <= 100) Percentage(n)
    else throw SchemaError.validationFailed(s"Percentage must be 0-100, got $n")

  implicit val schema: Schema[Percentage] =
    Schema[Int].transform(unsafeMake, _.value)
}
```

### Bidirectional Validation

Use the two-function `Schema#transform` when both building and unwrapping the value need a check. The `to` function builds the wrapper from the raw value, and the `from` function unwraps it:

```scala mdoc:silent:reset
import zio.blocks.schema.{Schema, SchemaError}

case class BoundedValue(value: Int)

object BoundedValue {
  implicit val schema: Schema[BoundedValue] = Schema[Int].transform(
    to = n =>
      if (n >= 0 && n < 100) BoundedValue(n)
      else throw SchemaError.validationFailed("Value must be in [0, 100)"),
    from = v =>
      if (v.value >= 0) v.value
      else throw SchemaError.validationFailed("Corrupted value")
  )
}
```

## Validation at Decode Time

Decoding runs the check of a wrapper built with `Schema#transform`, but it does not enforce `Validation` constraints; `DynamicSchema#check` does.

### Decoding with Validation

A wrapper built with `Schema#transform` runs its check when a value is decoded from JSON or a `DynamicValue`. In the example below, `Order` contains a `PositiveInt`, so decoding an order with a quantity of `-5` fails inside the wrapper:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.json.JsonFormat

case class PositiveInt(value: Int)
object PositiveInt {
  def unsafeMake(n: Int): PositiveInt =
    if (n > 0) PositiveInt(n)
    else throw SchemaError.validationFailed("must be positive")

  implicit val schema: Schema[PositiveInt] =
    Schema[Int].transform(unsafeMake, _.value)
}

case class Order(quantity: PositiveInt, price: BigDecimal)
object Order {
  implicit val schema: Schema[Order] = Schema.derived
}
```

Decoding the JSON with the codec of `Order` reports the path of the failed check, which ends in `.wrapped`:

```scala mdoc
println(Order.schema.derive(JsonFormat).decode("""{"quantity": -5, "price": 99.99}""").left.map(_.message))
```

### DynamicValue Validation

Use `DynamicSchema#check` to validate `DynamicValue` instances against the structure of a schema, and to check its `Validation` constraints. Decoding does not run these checks, so call `DynamicSchema#check` on a value before you use it:

```scala mdoc:silent:reset
import zio.blocks.schema._

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Create a DynamicSchema for validation
val dynamicSchema: DynamicSchema = Schema[Person].toDynamicSchema

// Create a DynamicValue to validate
val value = DynamicValue.Record(
  "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
  "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
)
```

`DynamicSchema#check` returns `None` when the value conforms, and `Some` with the error otherwise:

```scala mdoc
println(dynamicSchema.check(value).map(_.message))
println(dynamicSchema.conforms(value))
```

### Converting DynamicSchema to Validating Schema

`DynamicSchema#toSchema` creates a `Schema[DynamicValue]` that rejects values that don't conform to the structure. Here the `name` field holds an `Int` instead of a `String`:

```scala mdoc:silent:reset
import zio.blocks.schema._

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val dynamicSchema: DynamicSchema = Schema[Person].toDynamicSchema
val validatingSchema: Schema[DynamicValue] = dynamicSchema.toSchema

val invalidValue = DynamicValue.Record(
  "name" -> DynamicValue.Primitive(PrimitiveValue.Int(42))  // wrong type!
)
```

Converting `invalidValue` with `validatingSchema` returns `Left` with the error:

```scala mdoc
println(validatingSchema.fromDynamicValue(invalidValue).left.map(_.message))
```

## Validation in JSON Schema

`Schema#toJsonSchema` derives a JSON Schema from a schema, and it does not include the `Validation` constraints in the output. A `minimum`, `maxLength` or `pattern` keyword is therefore absent from a JSON Schema that ZIO Blocks derives, even when the type has a constraint.

The constraints do come back in the other direction. Parsing a JSON Schema with `Schema.fromJsonSchema` maps keywords such as `minimum`, `maxLength` and `pattern` to `Validation` instances.

## Composing Validations

The current `Validation` ADT does not support combining multiple validations on a single primitive (e.g., both `NonEmpty` and `Pattern`). For complex validation logic, use `Schema#transform`:

```scala mdoc:silent:reset
import zio.blocks.schema.{Schema, SchemaError}

case class Username private (value: String)

object Username {
  private val UsernameRegex = "^[a-z][a-z0-9_]{2,19}$".r

  def unsafeMake(s: String): Username = {
    if (s.isEmpty)
      throw SchemaError.validationFailed("Username cannot be empty")
    else if (s.length < 3)
      throw SchemaError.validationFailed("Username must be at least 3 characters")
    else if (s.length > 20)
      throw SchemaError.validationFailed("Username cannot exceed 20 characters")
    else if (!s.matches(UsernameRegex.regex))
      throw SchemaError.validationFailed("Username must start with a letter and contain only lowercase letters, numbers, and underscores")
    else
      Username(s)
  }

  implicit val schema: Schema[Username] =
    Schema[String].transform(unsafeMake, _.value)
}
```

## Best Practices

The practices below keep validation rules enforced in one place and make their failures easy to act on.

### 1. Use Wrapper Types for Domain Validation

Prefer creating dedicated wrapper types with `Schema#transform` over relying solely on `Validation` constraints:

```scala mdoc:silent:reset
import zio.blocks.schema.{Schema, SchemaError}

// Good: Explicit domain type with validation
case class OrderId private (value: String)
object OrderId {
  def unsafeMake(s: String): OrderId =
    if (s.matches("^ORD-\\d{8}$")) OrderId(s)
    else throw SchemaError.validationFailed("Invalid order ID format")

  implicit val schema: Schema[OrderId] =
    Schema[String].transform(unsafeMake, _.value)
}
```

The alternative keeps the raw `String` and checks it in a separate step, which lets any code path skip the check.

### 2. Provide Clear Error Messages

Include context in error messages to help users understand what went wrong:

```scala mdoc:compile-only
import zio.blocks.schema.SchemaError

def requireAge(age: Int): Unit =
  if (age < 0 || age > 150)
    throw SchemaError.validationFailed(s"Age must be between 0 and 150, got $age")
```

A message such as `Invalid age` gives the user no value or range to correct.

### 3. Combine Errors When Possible

For multiple validation failures, combine them into a single `SchemaError`:

```scala mdoc:silent:reset
import zio.blocks.schema.SchemaError

case class Input(name: String, age: Int)
case class ValidInput(input: Input)

def validate(input: Input): Either[SchemaError, ValidInput] = {
  val errors = List.newBuilder[SchemaError]

  if (input.name.isEmpty)
    errors += SchemaError.validationFailed("name is required")
  if (input.age < 0)
    errors += SchemaError.validationFailed("age must be non-negative")

  val allErrors = errors.result()
  if (allErrors.isEmpty) Right(ValidInput(input))
  else Left(allErrors.reduce(_ ++ _))
}
```

Validating an `Input` with an empty name and a negative age reports both failures in one `SchemaError`:

```scala mdoc
println(validate(Input("", -1)).left.map(_.message))
```

See also the [How to Define Validated Newtypes and Collect Every Validation Error](../../guides/validated-newtypes.md) guide, which builds validated wrapper types with smart constructors and collects every failing check.

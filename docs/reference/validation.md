---
id: validation
title: "Validation"
---

`Validation` is a sealed trait in ZIO Blocks that represents declarative constraints on primitive values. Validations are attached to `PrimitiveType` instances and are checked during schema operations like decoding from `DynamicValue` or validating against a `DynamicSchema`.

## Overview

The validation system in ZIO Blocks provides:

- **Declarative constraints** on numeric and string values
- **Automatic enforcement** during schema-based decoding
- **Integration with wrapper types** via `transformOrFail` for custom validation logic
- **Schema error reporting** with path information for debugging

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

### Numeric Validations

Numeric validations apply to `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `BigInt`, and `BigDecimal`.

```scala
import zio.blocks.schema.Validation

// Sign constraints
Validation.Numeric.Positive     // value > 0
Validation.Numeric.Negative     // value < 0
Validation.Numeric.NonPositive  // value <= 0
Validation.Numeric.NonNegative  // value >= 0

// Range constraint (inclusive bounds)
Validation.Numeric.Range(Some(1), Some(100))   // 1 <= value <= 100
Validation.Numeric.Range(Some(0), None)        // value >= 0 (no upper bound)
Validation.Numeric.Range(None, Some(1000))     // value <= 1000 (no lower bound)

// Set constraint (value must be one of the specified values)
Validation.Numeric.Set(Set(1, 2, 3, 5, 8, 13))
```

### String Validations

String validations apply to `String` primitive types.

```scala
import zio.blocks.schema.Validation

// Content constraints
Validation.String.NonEmpty   // string.nonEmpty (length > 0)
Validation.String.Empty      // string.isEmpty (length == 0)
Validation.String.Blank      // string.trim.isEmpty (whitespace only)
Validation.String.NonBlank   // string.trim.nonEmpty (has non-whitespace)

// Length constraint (inclusive bounds)
Validation.String.Length(Some(1), Some(255))   // 1 <= length <= 255
Validation.String.Length(Some(3), None)        // length >= 3
Validation.String.Length(None, Some(100))      // length <= 100

// Pattern constraint (regex)
Validation.String.Pattern("^[a-z]+$")          // lowercase letters only
Validation.String.Pattern("^\\d{5}$")          // exactly 5 digits
```

## Validation with PrimitiveType

Validations are attached to `PrimitiveType` instances. Each primitive type carries its validation constraint:

```scala
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

`SchemaError` is an exception that contains one or more error details:

```scala
import zio.blocks.schema.SchemaError

// SchemaError wraps a non-empty list of Single errors
final case class SchemaError(errors: ::[SchemaError.Single]) extends Exception

// Single error types
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

```scala
import zio.blocks.schema.SchemaError

// For validation failures in transformOrFail
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

`SchemaError` includes path information showing where in the data structure the error occurred:

```scala
import zio.blocks.schema.SchemaError

val error: SchemaError = ???

// Get the full error message
val msg: String = error.message

// Add path context to errors
val atField = error.atField("name")    // prepend .name to path
val atIndex = error.atIndex(0)         // prepend [0] to path
val atCase = error.atCase("Some")      // prepend case context

// Combine multiple errors
val combined = error1 ++ error2
```

Example error message:
```
Validation failed: value must be positive at: $.user.age
```

## Integration with Wrapper Types

The most common way to use validation in ZIO Blocks is through `transformOrFail`, which creates a schema for a wrapper type with validation logic.

### Basic Wrapper with Validation

```scala
import zio.blocks.schema.{Schema, SchemaError}

case class PositiveInt private (value: Int)

object PositiveInt {
  def make(n: Int): Either[SchemaError, PositiveInt] =
    if (n > 0) Right(PositiveInt(n))
    else Left(SchemaError.validationFailed("must be positive"))

  implicit val schema: Schema[PositiveInt] =
    Schema[Int].transformOrFail(make, _.value)
}
```

### Email Type with Regex Validation

```scala
import zio.blocks.schema.{Schema, SchemaError}

case class Email private (value: String)

object Email {
  private val EmailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".r

  def make(s: String): Either[SchemaError, Email] =
    s match {
      case EmailRegex(_*) => Right(Email(s))
      case _ => Left(SchemaError.validationFailed("Invalid email format"))
    }

  implicit val schema: Schema[Email] =
    Schema[String].transformOrFail(make, _.value).withTypeName[Email]
}
```

### NonEmptyString with Length Validation

```scala
import zio.blocks.schema.{Schema, SchemaError}

case class NonEmptyString private (value: String)

object NonEmptyString {
  def make(s: String): Either[SchemaError, NonEmptyString] =
    if (s.nonEmpty) Right(NonEmptyString(s))
    else Left(SchemaError.validationFailed("String must not be empty"))

  implicit val schema: Schema[NonEmptyString] =
    Schema[String].transformOrFail(make, _.value).withTypeName[NonEmptyString]
}
```

### Range-Bounded Integer

```scala
import zio.blocks.schema.{Schema, SchemaError}

case class Percentage private (value: Int)

object Percentage {
  def make(n: Int): Either[SchemaError, Percentage] =
    if (n >= 0 && n <= 100) Right(Percentage(n))
    else Left(SchemaError.validationFailed(s"Percentage must be 0-100, got $n"))

  implicit val schema: Schema[Percentage] =
    Schema[Int].transformOrFail(make, _.value).withTypeName[Percentage]
}
```

### Bidirectional Validation

Use the two-argument `transform` for cases where both encoding and decoding need validation:

```scala
import zio.blocks.schema.{Schema, SchemaError}

case class BoundedValue(value: Int)

object BoundedValue {
  implicit val schema: Schema[BoundedValue] = Schema[Int].transform(
    wrap = n =>
      if (n >= 0 && n < 100) Right(BoundedValue(n))
      else Left(SchemaError.validationFailed("Value must be in [0, 100)")),
    unwrap = v =>
      if (v.value >= 0) Right(v.value)
      else Left(SchemaError.validationFailed("Corrupted value"))
  )
}
```

## Validation at Encode/Decode Time

### Decoding with Validation

When decoding from `DynamicValue` or JSON, validations in wrapper schemas are automatically enforced:

```scala
import zio.blocks.schema._
import zio.blocks.schema.json._

case class PositiveInt(value: Int)
object PositiveInt {
  def make(n: Int): Either[SchemaError, PositiveInt] =
    if (n > 0) Right(PositiveInt(n))
    else Left(SchemaError.validationFailed("must be positive"))

  implicit val schema: Schema[PositiveInt] =
    Schema[Int].transformOrFail(make, _.value)
}

case class Order(quantity: PositiveInt, price: BigDecimal)
object Order {
  implicit val schema: Schema[Order] = Schema.derived
}

// JSON decoding will validate PositiveInt
val json = """{"quantity": -5, "price": 99.99}"""
val result = JsonDecoder[Order].decodeString(json)
// result: Left(SchemaError: must be positive at $.quantity)
```

### DynamicValue Validation

Use `DynamicSchema` to validate `DynamicValue` instances:

```scala
import zio.blocks.schema._

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Create a DynamicSchema for validation
val dynamicSchema: DynamicSchema = Schema[Person].toDynamicSchema

// Create a DynamicValue to validate
val value = DynamicValue.Record(Vector(
  "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
  "age" -> DynamicValue.Primitive(PrimitiveValue.Int(30))
))

// Validate the value
val checkResult: Option[SchemaError] = dynamicSchema.check(value)
// None if valid, Some(error) if invalid

val isValid: Boolean = dynamicSchema.conforms(value)
// true if valid
```

### Converting DynamicSchema to Validating Schema

`DynamicSchema.toSchema` creates a `Schema[DynamicValue]` that rejects non-conforming values:

```scala
import zio.blocks.schema._

val dynamicSchema: DynamicSchema = Schema[Person].toDynamicSchema
val validatingSchema: Schema[DynamicValue] = dynamicSchema.toSchema

// Now any decoding through this schema will validate structure
val invalidValue = DynamicValue.Record(Vector(
  "name" -> DynamicValue.Primitive(PrimitiveValue.Int(42))  // wrong type!
))

val result = validatingSchema.fromDynamicValue(invalidValue)
// Left(SchemaError: Expected String, got Int at $.name)
```

## Validation in JSON Schema

When deriving JSON Schema from a ZIO Blocks schema, validations are reflected in the output:

```scala
import zio.blocks.schema._
import zio.blocks.schema.json.JsonSchema

// Numeric validations become JSON Schema constraints
// Validation.Numeric.Range(Some(0), Some(100)) → "minimum": 0, "maximum": 100

// String validations become JSON Schema constraints
// Validation.String.NonEmpty → "minLength": 1
// Validation.String.Length(Some(1), Some(255)) → "minLength": 1, "maxLength": 255
// Validation.String.Pattern("^[a-z]+$") → "pattern": "^[a-z]+$"
```

When parsing JSON Schema, these constraints are converted back to `Validation` instances.

## Composing Validations

The current `Validation` ADT does not support combining multiple validations on a single primitive (e.g., both `NonEmpty` and `Pattern`). For complex validation logic, use `transformOrFail`:

```scala
import zio.blocks.schema.{Schema, SchemaError}

case class Username private (value: String)

object Username {
  private val UsernameRegex = "^[a-z][a-z0-9_]{2,19}$".r

  def make(s: String): Either[SchemaError, Username] = {
    if (s.isEmpty)
      Left(SchemaError.validationFailed("Username cannot be empty"))
    else if (s.length < 3)
      Left(SchemaError.validationFailed("Username must be at least 3 characters"))
    else if (s.length > 20)
      Left(SchemaError.validationFailed("Username cannot exceed 20 characters"))
    else if (!s.matches(UsernameRegex.regex))
      Left(SchemaError.validationFailed(
        "Username must start with a letter and contain only lowercase letters, numbers, and underscores"
      ))
    else
      Right(Username(s))
  }

  implicit val schema: Schema[Username] =
    Schema[String].transformOrFail(make, _.value).withTypeName[Username]
}
```

## Best Practices

### 1. Use Wrapper Types for Domain Validation

Prefer creating dedicated wrapper types with `transformOrFail` over relying solely on `Validation` constraints:

```scala
// Good: Explicit domain type with validation
case class OrderId private (value: String)
object OrderId {
  def make(s: String): Either[SchemaError, OrderId] =
    if (s.matches("^ORD-\\d{8}$")) Right(OrderId(s))
    else Left(SchemaError.validationFailed("Invalid order ID format"))

  implicit val schema: Schema[OrderId] =
    Schema[String].transformOrFail(make, _.value).withTypeName[OrderId]
}

// Less ideal: Raw String with separate validation
val orderIdString: String = ???
```

### 2. Provide Clear Error Messages

Include context in error messages to help users understand what went wrong:

```scala
// Good: Specific, actionable error message
Left(SchemaError.validationFailed(
  s"Age must be between 0 and 150, got $age"
))

// Less helpful: Generic message
Left(SchemaError.validationFailed("Invalid age"))
```

### 3. Use withTypeName for Better Debugging

Always call `withTypeName` after `transformOrFail` to ensure error messages reference the correct type:

```scala
implicit val schema: Schema[Email] =
  Schema[String]
    .transformOrFail(make, _.value)
    .withTypeName[Email]  // Error messages will reference "Email"
```

### 4. Combine Errors When Possible

For multiple validation failures, combine them into a single `SchemaError`:

```scala
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

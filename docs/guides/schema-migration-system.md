---
id: schema-migration-system
title: "Schema Migration System"
---

# Schema Migration System

The schema migration system provides a pure, serializable way to transform data between different schema versions. This is essential for evolving data models over time while maintaining backward compatibility.

## Overview

The migration system consists of three layers:

1. **DynamicMigration** - The core, untyped migration engine that operates on `DynamicValue`
2. **Migration[A, B]** - A typed wrapper providing compile-time type safety
3. **MigrationBuilder[A, B]** - A fluent DSL for constructing migrations

### Key Properties

- **Pure Data** - No user functions, closures, or reflection
- **Serializable** - Can be stored in registries and applied dynamically
- **Introspectable** - The ADT is fully inspectable for DDL generation
- **Composable** - Migrations can be composed sequentially
- **Reversible** - Structural reverse is supported

## Quick Start

### Basic Migration

```scala
import zio.blocks.schema._
import zio.blocks.chunk.Chunk

// Define versioned types
case class PersonV1(name: String, age: Int)
case class PersonV2(name: String, age: Int, country: String)

object PersonV1 {
  implicit val schema: Schema[PersonV1] = Schema.derived[PersonV1]
}

object PersonV2 {
  implicit val schema: Schema[PersonV2] = Schema.derived[PersonV2]
}

// Create a migration using the builder DSL
val migration = MigrationBuilder[PersonV1, PersonV2]
  .addField(DynamicOptic.root.field("country"), "US")
  .build

// Apply the migration
val v1 = PersonV1("Alice", 30)
val v2: Either[MigrationError, PersonV2] = migration(v1)
// Right(PersonV2("Alice", 30, "US"))
```

### Multiple Transformations

```scala
val migration = MigrationBuilder[PersonV1, PersonV2]
  .addField(DynamicOptic.root.field("country"), "US")
  .transformValue(DynamicOptic.root.field("name"), DynamicTransform.StringUpperCase)
  .build

val result = migration(PersonV1("alice", 30))
// Right(PersonV2("ALICE", 30, "US"))
```

## Migration Actions

### Record Operations

#### AddField

Adds a new field with a default value:

```scala
MigrationBuilder[PersonV1, PersonV2]
  .addField(DynamicOptic.root.field("country"), "US")
```

#### DropField

Removes a field from the record:

```scala
MigrationBuilder[PersonV2, PersonV1]
  .dropField(DynamicOptic.root.field("country"), "US")
```

#### Rename

Renames a field:

```scala
MigrationBuilder[OldRecord, NewRecord]
  .renameField(DynamicOptic.root.field("oldName"), "newName")
```

#### TransformValue

Transforms a value at a specific path:

```scala
MigrationBuilder[Person, Person]
  .transformValue(DynamicOptic.root.field("name"), DynamicTransform.StringUpperCase)
```

#### Mandate

Converts an optional field to required:

```scala
MigrationBuilder[WithOption, WithoutOption]
  .mandate(DynamicOptic.root.field("nickname"), "Unknown")
```

#### Optionalize

Converts a required field to optional:

```scala
MigrationBuilder[WithoutOption, WithOption]
  .optionalize(DynamicOptic.root.field("nickname"))
```

#### ChangeType

Changes the type of a field:

```scala
case class StringPerson(name: String, age: String)
case class IntPerson(name: String, age: Int)

MigrationBuilder[StringPerson, IntPerson]
  .changeType(DynamicOptic.root.field("age"), DynamicTransform.StringToInt)
  .buildPartial
```

### Collection Operations

#### TransformElements

Transforms each element in a sequence:

```scala
case class NamesHolder(names: Chunk[String])

MigrationBuilder[NamesHolder, NamesHolder]
  .transformElements(DynamicOptic.root.field("names"), DynamicTransform.StringUpperCase)
  .build
```

#### TransformKeys

Transforms all keys in a map:

```scala
MigrationBuilder[RecordWithMap, RecordWithMap]
  .transformKeys(DynamicOptic.root.field("mapping"), DynamicTransform.StringUpperCase)
```

#### TransformValues

Transforms all values in a map:

```scala
MigrationBuilder[RecordWithMap, RecordWithMap]
  .transformValues(DynamicOptic.root.field("mapping"), DynamicTransform.IntToString)
```

### Enum Operations

#### RenameCase

Renames a case in a sealed trait:

```scala
sealed trait Status
case class Active(since: Long) extends Status
case object Inactive extends Status

MigrationBuilder[Status, Status]
  .renameCase(DynamicOptic.root, "Active", "Enabled")
```

#### TransformCase

Transforms the contents of a specific case:

```scala
MigrationBuilder[Status, Status]
  .transformCase(
    DynamicOptic.root,
    "Active",
    MigrationAction.TransformValue(
      DynamicOptic.root.field("since"),
      DynamicTransform.LongToString
    )
  )
```

## Transforms

DynamicTransform provides primitive-to-primitive transformations:

### String Transforms

```scala
DynamicTransform.StringUpperCase   // "hello" -> "HELLO"
DynamicTransform.StringLowerCase   // "HELLO" -> "hello"
DynamicTransform.StringTrim        // "  hello  " -> "hello"
DynamicTransform.StringToInt       // "42" -> 42
DynamicTransform.StringToLong      // "42" -> 42L
DynamicTransform.StringToDouble    // "3.14" -> 3.14
DynamicTransform.StringToBoolean   // "true" -> true
```

### Numeric Transforms

```scala
DynamicTransform.IntToString       // 42 -> "42"
DynamicTransform.LongToString      // 42L -> "42"
DynamicTransform.DoubleToString    // 3.14 -> "3.14"
DynamicTransform.IntToLong         // 42 -> 42L
DynamicTransform.LongToInt         // 42L -> 42
DynamicTransform.FloatToDouble     // 3.14f -> 3.14
DynamicTransform.DoubleToFloat     // 3.14 -> 3.14f

// Parameterized
DynamicTransform.NumericAdd(amount)     // 10 + 5 = 15
DynamicTransform.NumericSubtract(amount) // 10 - 5 = 5
DynamicTransform.NumericMultiply(factor) // 10 * 2 = 20
DynamicTransform.NumericDivide(divisor)  // 10 / 2 = 5
```

### String Operations

```scala
DynamicTransform.StringConcat           // ["Hello", "World"] -> "HelloWorld"
DynamicTransform.StringSplit(",")       // "Hello,World" -> ["Hello", "World"]
DynamicTransform.StringConcatWith("-")  // ["a", "b"] -> "a-b"
```

### Composition

```scala
// Compose transforms
val stringToIntToString = DynamicTransform.Compose(
  DynamicTransform.StringToInt,
  DynamicTransform.IntToString
)

// Transform each element in a sequence
DynamicTransform.MapElements(DynamicTransform.StringUpperCase)
```

## Typed Transform API

The `Transform[A, B]` class provides a type-safe wrapper:

```scala
// Predefined transforms
val stringToInt: Transform[String, Int] = Transform.stringToInt
val intToString: Transform[Int, String] = Transform.intToString
val upperCase: Transform[String, String] = Transform.stringToUpperCase

// Compose with >>>
val composed: Transform[String, Int] = stringToInt
val roundTrip: Transform[String, String] = stringToInt >>> intToString

// Apply transform
val result: Either[MigrationError, Int] = stringToInt("42")
// Right(42)

// Reverse transform
val reverse: Transform[Int, String] = stringToInt.reverse
```

## Migration Composition

### Sequential Composition

```scala
val m1: Migration[A, B] = ...
val m2: Migration[B, C] = ...

// Compose sequentially
val composed: Migration[A, C] = m1.andThen(m2)
// Or using >>>
val composed2: Migration[A, C] = m1 >>> m2
```

### Composition Properties

```scala
// Associativity
(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)

// Identity
Migration.identity[A] ++ m == m
m ++ Migration.identity[B] == m

// Reverse composition
(m1 ++ m2).reverse == m2.reverse ++ m1.reverse
```

## Reverse Migration

Every migration has a structural reverse:

```scala
val forward: Migration[PersonV1, PersonV2] = 
  MigrationBuilder[PersonV1, PersonV2]
    .addField(DynamicOptic.root.field("country"), "US")
    .build

val reverse: Migration[PersonV2, PersonV1] = forward.reverse

// The reverse of AddField is DropField
// The reverse of StringToInt is IntToString
```

:::note
The reverse is structural, not necessarily semantic. If information is lost in the forward migration (e.g., dropping a field), the reverse may not perfectly restore the original value.
:::

## Error Handling

Migration errors are captured in the `MigrationError` sealed trait:

```scala
val result: Either[MigrationError, PersonV2] = migration(v1)

result match {
  case Right(v2) => // Success
  case Left(error) => error match
    case MigrationError.NotFound(path, details) => 
      // Value not found at path
    case MigrationError.TypeMismatch(path, expected, actual) =>
      // Type mismatch
    case MigrationError.TransformFailed(path, details) =>
      // Transform failed
    case MigrationError.Multiple(errors) =>
      // Multiple errors aggregated
    // ... other error types
}
```

### Error Types

| Error Type | Description |
|------------|-------------|
| `NotFound` | Value not found at the specified path |
| `TypeMismatch` | Value has an unexpected type |
| `MissingField` | Required field is missing |
| `UnknownCase` | Unknown case in a variant |
| `TransformFailed` | Transformation failed |
| `IndexOutOfBounds` | Index out of bounds in sequence |
| `KeyNotFound` | Key not found in map |
| `DefaultFailed` | Failed to compute default value |
| `InvalidAction` | Action is invalid at the path |
| `Multiple` | Multiple errors aggregated |

## Serialization

All migration types have `Schema` instances and can be serialized:

```scala
val migration: DynamicMigration = DynamicMigration.addField(
  DynamicOptic.root.field("country"),
  DynamicValue.Primitive(PrimitiveValue.String("US"))
)

// Serialize
val encoded: DynamicValue = Schema[DynamicMigration].toDynamicValue(migration)

// Deserialize
val decoded: Either[SchemaError, DynamicMigration] = 
  Schema[DynamicMigration].fromDynamicValue(encoded)
```

This enables:
- Storing migrations in databases
- Transmitting migrations over networks
- Loading migrations dynamically at runtime

## Advanced Usage

### Nested Record Operations

```scala
case class Address(street: String, city: String)
case class Person(name: String, address: Address)

MigrationBuilder[Person, Person]
  .transformValue(
    DynamicOptic.root.field("address").field("city"),
    DynamicTransform.StringUpperCase
  )
  .build
```

### Using DynamicMigration Directly

For dynamic scenarios where types aren't known at compile time:

```scala
val dynamicMigration = DynamicMigration.addField(
  DynamicOptic.root.field("country"),
  DynamicValue.Primitive(PrimitiveValue.String("US"))
)

val input = DynamicValue.Record(Chunk(
  "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
  "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
))

val result: Either[MigrationError, DynamicValue] = dynamicMigration(input)
```

### SchemaExpr Integration

Use SchemaExpr for more complex transformations:

```scala
import zio.blocks.schema.SchemaExprTransform

// Create a constant transform from a literal
val constant: DynamicTransform = SchemaExprTransform.literal("hello")

// Create a transform from the schema's default value
val default: DynamicTransform = SchemaExprTransform.defaultValue[Person]
```

## Best Practices

1. **Use Typed API When Possible** - `Migration[A, B]` provides compile-time safety
2. **Prefer buildPartial for Complex Migrations** - Skip validation when confident
3. **Test Round-Trip** - Verify forward and reverse migrations work correctly
4. **Keep Migrations Small** - Smaller migrations are easier to reason about
5. **Document Breaking Changes** - Note when information may be lost
6. **Version Your Schemas** - Use clear naming conventions (V1, V2, etc.)

## API Reference

### Migration[A, B]

```scala
trait Migration[A, B] {
  def apply(value: A): Either[MigrationError, B]
  def andThen[C](that: Migration[B, C]): Migration[A, C]
  def >>>[C](that: Migration[B, C]): Migration[A, C]
  def reverse: Migration[B, A]
  def toDynamic: DynamicMigration
  def size: Int
  def isEmpty: Boolean
  def nonEmpty: Boolean
}

object Migration {
  def identity[A](implicit schema: Schema[A]): Migration[A, A]
  def fromDynamic[A, B](...): Migration[A, B]
  def builder[A, B](...): MigrationBuilder[A, B]
}
```

### MigrationBuilder[A, B]

```scala
class MigrationBuilder[A, B] {
  // Record operations
  def addField(at: DynamicOptic, default: DynamicValue): MigrationBuilder[A, B]
  def addField[T](at: DynamicOptic, default: T)(implicit schema: Schema[T]): MigrationBuilder[A, B]
  def dropField(at: DynamicOptic, defaultForReverse: DynamicValue): MigrationBuilder[A, B]
  def renameField(from: DynamicOptic, to: String): MigrationBuilder[A, B]
  def transformValue(at: DynamicOptic, transform: DynamicTransform): MigrationBuilder[A, B]
  def mandate(at: DynamicOptic, default: DynamicValue): MigrationBuilder[A, B]
  def optionalize(at: DynamicOptic): MigrationBuilder[A, B]
  def changeType(at: DynamicOptic, converter: DynamicTransform): MigrationBuilder[A, B]
  
  // Collection operations
  def transformElements(at: DynamicOptic, transform: DynamicTransform): MigrationBuilder[A, B]
  def transformKeys(at: DynamicOptic, transform: DynamicTransform): MigrationBuilder[A, B]
  def transformValues(at: DynamicOptic, transform: DynamicTransform): MigrationBuilder[A, B]
  
  // Enum operations
  def renameCase(at: DynamicOptic, from: String, to: String): MigrationBuilder[A, B]
  def transformCase(at: DynamicOptic, caseName: String, caseActions: MigrationAction*): MigrationBuilder[A, B]
  
  // Build methods
  def build: Migration[A, B]
  def buildPartial: Migration[A, B]
  def toDynamic: DynamicMigration
}
```

## Related Documentation

- [DynamicValue Reference](../reference/dynamic-value.md)
- [DynamicOptic Reference](../reference/dynamic-optic.md)
- [Schema Reference](../reference/schema.md)

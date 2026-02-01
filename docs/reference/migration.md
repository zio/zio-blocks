---
id: migration
title: "Schema Migration"
sidebar_label: "Migration"
---

# Schema Migration System

ZIO Blocks provides a pure, algebraic migration system for transforming data between schema versions. Unlike traditional migration approaches that rely on functions or closures, migrations are represented as **first-class, serializable data** that can be stored, transmitted, and inspected.

## Why Pure Data Migrations?

Migrations represented as pure data offer significant advantages:

- **Serializable**: Store migrations in registries, databases, or configuration files
- **Introspectable**: Analyze migration actions to generate DDL, documentation, or validation rules
- **Composable**: Chain migrations together with type-safe composition
- **Reversible**: Automatically generate reverse migrations for rollback scenarios
- **Portable**: Apply the same migration logic across services, languages, or platforms

## Core Concepts

### Migration[A, B]

The typed migration wrapper that transforms values from type `A` to type `B`:

```scala
case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {
  def apply(value: A): Either[MigrationError, B]
  def ++[C](that: Migration[B, C]): Migration[A, C]
  def reverse: Migration[B, A]
}
```

### DynamicMigration

The untyped, serializable core that operates on `DynamicValue`:

```scala
case class DynamicMigration(actions: Chunk[MigrationAction]) {
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue]
  def ++(that: DynamicMigration): DynamicMigration
  def reverse: DynamicMigration
}

// Fully serializable via Schema instance
implicit val schema: Schema[DynamicMigration] = Schema[Chunk[MigrationAction]].transform(...)
```

### MigrationAction

The sealed trait representing all possible migration operations:

| Action | Description |
|--------|-------------|
| `AddField` | Add a new field with a default value |
| `DropField` | Remove a field from a record |
| `Rename` | Rename a field |
| `TransformValue` | Transform a field's value using Resolved expressions |
| `Mandate` | Convert `Option[A]` to `A` with a default |
| `Optionalize` | Convert `A` to `Option[A]` |
| `ChangeType` | Convert between primitive types |
| `Join` | Combine multiple source fields into a single target field |
| `Split` | Split a single field into multiple target fields |
| `RenameCase` | Rename an enum case |
| `TransformCase` | Apply nested migrations to an enum case |
| `TransformElements` | Transform all elements in a collection |
| `TransformKeys` | Transform all keys in a map |
| `TransformValues` | Transform all values in a map |


### Resolved Expressions

`Resolved` is a serializable expression type for value transformations:

```scala
sealed trait Resolved

object Resolved {
  case class Literal(value: DynamicValue)              extends Resolved
  case object Identity                                  extends Resolved
  case class FieldAccess(fieldName: String)            extends Resolved
  case class Convert(fromType: String, toType: String, inner: Resolved) extends Resolved
  case class Concat(separator: String, parts: Chunk[Resolved]) extends Resolved
  // ... additional cases
}
```

**Helper methods for literals:**

```scala
Resolved.Literal.string("hello")
Resolved.Literal.int(42)
Resolved.Literal.boolean(true)
Resolved.Literal.long(123L)
Resolved.Literal.double(3.14)
```

## Quick Start

### Basic Field Operations

```scala
import zio.blocks.schema._
import zio.blocks.schema.migration._

// Source type
case class PersonV1(firstName: String, lastName: String)
object PersonV1 {
  implicit val schema: Schema[PersonV1] = Schema.derived
}

// Target type
case class PersonV2(fullName: String, age: Int)
object PersonV2 {
  implicit val schema: Schema[PersonV2] = Schema.derived
}

// Build migration (runtime validation)
val migration = MigrationBuilder[PersonV1, PersonV2]
  .addField("age", 0)
  .renameField("firstName", "fullName")
  .dropField("lastName")
  .buildStrict  // runtime validation; use tracked[...].build for compile-time

// Apply migration
val old = PersonV1("John", "Doe")
val result = migration(old) // Right(PersonV2("John", 0))
```

### Using Selector Macros

The macros extract `DynamicOptic` paths from lambda selectors:

```scala
import zio.blocks.schema.migration.MigrationBuilderMacros._

// Extract paths from lambda selectors
val namePath = extractPath[PersonV2, String](_.fullName)
// Equivalent to: DynamicOptic.root.field("fullName")

// Nested paths
val cityPath = extractPath[Address, String](_.city)

// Collection access
val itemsPath = extractPath[Order, Vector[Item]](_.items)
```

### Selector-Based Builder Methods

For convenience, `MigrationBuilderSyntax` provides extension methods that combine
path extraction with migration operations:

```scala
// Scala 3
import zio.blocks.schema.migration.MigrationBuilderSyntax.*

val migration = MigrationBuilder[PersonV1, PersonV2]
  .dropFieldWithSelector(_.legacyField)
  .renameFieldWithSelector(_.firstName, "fullName")
  .buildStrict

// Scala 2
import zio.blocks.schema.migration.MigrationBuilderSyntax._

val migration = MigrationBuilder[PersonV1, PersonV2]
  .dropFieldWithSelector(_.legacyField)
  .renameFieldWithSelector(_.firstName, "fullName")
  .buildStrict
```

Available selector-based methods:

| Method | Description |
|--------|-------------|
| `dropFieldWithSelector` | Drop a field using a selector |
| `dropFieldWithSelectorDefault` | Drop with a reverse default |
| `renameFieldWithSelector` | Rename a field using a selector |
| `mandateFieldWithSelector` | Make optional field mandatory |
| `optionalizeFieldWithSelector` | Make mandatory field optional |

### Enum Case Migrations

```scala
sealed trait PaymentV1
case class CreditCard(number: String) extends PaymentV1
case class BankTransfer(account: String) extends PaymentV1

sealed trait PaymentV2
case class Card(cardNumber: String) extends PaymentV2
case class Wire(accountNumber: String) extends PaymentV2

val paymentMigration = MigrationBuilder[PaymentV1, PaymentV2]
  .renameCase("CreditCard", "Card")
  .renameCase("BankTransfer", "Wire")
  .transformCase("Card") { nested =>
    nested.renameField("number", "cardNumber")
  }
  .buildStrict
```

### Collection Transformations

```scala
case class OrderV1(items: Vector[String])
case class OrderV2(items: Vector[String])

val orderMigration = MigrationBuilder[OrderV1, OrderV2]
  .transformElementsResolved(
    DynamicOptic.root.field("items"),
    Resolved.Identity,
    Resolved.Identity
  )
  .buildStrict
```

### Join and Split Operations

Join combines multiple source fields into a single target field:

```scala
case class PersonV1(firstName: String, lastName: String)
case class PersonV2(fullName: String)

val joinMigration = MigrationBuilder[PersonV1, PersonV2]
  .join(
    targetFieldName = "fullName",
    sourcePaths = Chunk(
      DynamicOptic.root.field("firstName"),
      DynamicOptic.root.field("lastName")
    ),
    combiner = Resolved.Concat(
      Vector(
        Resolved.FieldAccess("firstName", Resolved.Identity),
        Resolved.Literal.string(" "),
        Resolved.FieldAccess("lastName", Resolved.Identity)
      ),
      ""
    ),
    splitter = Resolved.SplitString(Resolved.Identity, " ", 0) // for reverse
  )
  .buildStrict
```

Split separates a single field into multiple target fields:

```scala
val splitMigration = MigrationBuilder[PersonV2, PersonV1]
  .split(
    sourceFieldName = "fullName",
    targetPaths = Chunk(
      DynamicOptic.root.field("firstName"),
      DynamicOptic.root.field("lastName")
    ),
    splitter = Resolved.SplitString(Resolved.Identity, " ", 0),
    combiner = Resolved.Concat(Vector(...), " ")
  )
  .buildStrict
```

### Primitive Type Conversions

The `PrimitiveConversions` module provides comprehensive type conversions:

```scala
import zio.blocks.schema.migration.PrimitiveConversions

// Numeric widening (always succeeds)
PrimitiveConversions.convert(dynamicInt(42), "Int", "Long")
// => Right(DynamicValue.Primitive(PrimitiveValue.Long(42L)))

// Numeric narrowing (may fail for out-of-range values)
PrimitiveConversions.convert(dynamicLong(128), "Long", "Byte")
// => Left("Value 128 out of Byte range [-128, 127]")

// String parsing
PrimitiveConversions.convert(dynamicString("123"), "String", "Int")
// => Right(DynamicValue.Primitive(PrimitiveValue.Int(123)))

// Temporal parsing (ISO-8601)
PrimitiveConversions.convert(dynamicString("2024-01-15"), "String", "LocalDate")
// => Right(DynamicValue.Primitive(PrimitiveValue.LocalDate(LocalDate.of(2024, 1, 15))))
```

Use with `ChangeType` action or `Resolved.Convert`:

```scala
// Using ChangeType action
MigrationBuilder[OrderV1, OrderV2]
  .changeFieldType("count", "Int", "Long")
  .buildStrict

// Using Resolved.Convert in expressions
Resolved.Convert("Int", "Long", Resolved.Identity)
```

**Supported conversions:**

| Category | Examples |
|----------|----------|
| Numeric widening | Byte → Short → Int → Long → Float → Double → BigDecimal |
| Numeric narrowing | With bounds checking (may fail) |
| String parsing | All primitives, UUID, temporal types |
| Temporal types | Instant, LocalDate, LocalDateTime, ZonedDateTime, Duration, Period, etc. |
| Boolean/Char | Int ↔ Boolean, Int ↔ Char |

### Nested Migrations

All actions support nested paths via `DynamicOptic`, enabling migrations on deeply nested structures:

```scala
case class Company(name: String, address: Address)
case class Address(street: String, city: City)
case class City(name: String, zip: String)

// Migrate a deeply nested field
val migration = MigrationBuilder[CompanyV1, CompanyV2]
  .renameFieldResolved(
    DynamicOptic.root.field("address").field("city"),
    "name", 
    "cityName"
  )
  .buildStrict
```

The `prefixPath` method on all actions allows scoping:

```scala
// Create nested action
val action = MigrationAction.Rename(DynamicOptic.root, "old", "new")

// Scope to nested path
val nestedAction = action.prefixPath(DynamicOptic.root.field("address"))
// Now operates at _.address.old -> _.address.new
```

## Migration Builder API

### Record Operations

```scala
class MigrationBuilder[A, B] {
  // Add a field with a literal default
  def addField[T](fieldName: String, default: T)(implicit schema: Schema[T]): MigrationBuilder[A, B]
  
  // Add a field with a Resolved expression
  def addFieldResolved(at: DynamicOptic, fieldName: String, default: Resolved): MigrationBuilder[A, B]
  
  // Drop a field
  def dropField(fieldName: String): MigrationBuilder[A, B]
  
  // Drop a field with a reverse default
  def dropField[T](fieldName: String, defaultForReverse: T)(implicit schema: Schema[T]): MigrationBuilder[A, B]
  
  // Rename a field
  def renameField(from: String, to: String): MigrationBuilder[A, B]
  
  // Make optional field mandatory
  def mandateField[T](fieldName: String, default: T)(implicit schema: Schema[T]): MigrationBuilder[A, B]
  
  // Make mandatory field optional  
  def optionalizeField(fieldName: String): MigrationBuilder[A, B]
  
  // Change field type
  def changeFieldType(fieldName: String, fromType: String, toType: String): MigrationBuilder[A, B]
}
```

### Enum Operations

```scala
class MigrationBuilder[A, B] {
  // Rename an enum case
  def renameCase(from: String, to: String): MigrationBuilder[A, B]
  
  // Transform case contents (nested migrations)
  def transformCase[C, D](caseName: String)(
    f: MigrationBuilder[C, D] => MigrationBuilder[C, D]
  )(implicit cSchema: Schema[C], dSchema: Schema[D]): MigrationBuilder[A, B]
}
```

### Collection Operations

```scala
class MigrationBuilder[A, B] {
  // Transform elements in a sequence
  def transformElementsResolved(
    at: DynamicOptic,
    transform: Resolved,
    reverseTransform: Resolved
  ): MigrationBuilder[A, B]
  
  // Transform map keys
  def transformKeysResolved(at: DynamicOptic, transform: Resolved, reverse: Resolved): MigrationBuilder[A, B]
  
  // Transform map values
  def transformValuesResolved(at: DynamicOptic, transform: Resolved, reverse: Resolved): MigrationBuilder[A, B]
}
```

### Building Migrations

```scala
// Untracked builder (runtime validation only)
class MigrationBuilder[A, B] {
  // Build with strict runtime validation (throws on failure)
  def buildStrict: Migration[A, B]
  
  // Build with path validation only (less strict)
  def buildPathsOnly: Migration[A, B]
  
  // Build without validation
  def buildPartial: Migration[A, B]
  
  // Build just the untyped migration
  def buildDynamic: DynamicMigration
}

// Tracked builder (TRUE compile-time validation)
class TrackedMigrationBuilder[A, B, Handled, Provided] {
  // Build with compile-time validation - fails at compile time if incomplete
  def build(using ValidationProof[A, B, Handled, Provided]): Migration[A, B]
  
  def buildStrict: Migration[A, B]
  def buildPartial: Migration[A, B]
}
```

> [!IMPORTANT]
> The `.build` method is only available on `TrackedMigrationBuilder` and requires
> compile-time proof that all fields are handled. For untracked builders, use
> `buildStrict` (runtime validation) or `buildPartial` (no validation).

## Path Expressions (DynamicOptic)

Paths identify locations within data structures:

```scala
// Field access
DynamicOptic.root.field("name")              // _.name

// Nested fields  
DynamicOptic.root.field("address").field("city")  // _.address.city

// Enum case
DynamicOptic.root.caseOf("CreditCard")        // _.when[CreditCard]

// Sequence elements
DynamicOptic.root.field("items").elements    // _.items.each

// Map keys
DynamicOptic.root.field("data").mapKeys      // _.data.eachKey

// Map values
DynamicOptic.root.field("data").mapValues    // _.data.eachValue

// Specific index
DynamicOptic.root.at(0)                      // _.at(0)
```

## Laws

Migrations satisfy the following algebraic laws:

### Identity

```scala
Migration.identity[A].apply(a) == Right(a)
```

### Associativity

```scala
(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)
```

### Structural Reverse

```scala
m.reverse.reverse == m
```

### Best-Effort Semantic Inverse

```scala
// When sufficient information exists:
m.apply(a) == Right(b) ==> m.reverse.apply(b) == Right(a)
```

## Error Handling

All errors include path information for diagnostics:

```scala
sealed trait MigrationError {
  def path: DynamicOptic
  def render: String
}

case class PathNotFound(path: DynamicOptic, actualFields: Seq[String]) extends MigrationError
case class CaseNotFound(path: DynamicOptic, caseName: String, actualCases: Seq[String]) extends MigrationError
case class TypeMismatch(path: DynamicOptic, expected: String, actual: String) extends MigrationError
case class TransformFailed(path: DynamicOptic, reason: String) extends MigrationError
```

## Validation

Use `MigrationValidator` to validate migrations before execution:

```scala
val result = MigrationValidator.validate(migration)
result match {
  case MigrationValidator.ValidationResult.Valid => 
    // Migration is structurally sound
  case MigrationValidator.ValidationResult.Invalid(errors) =>
    // Handle validation errors
    errors.foreach(e => println(e.render))
}
```

The `buildStrict` method validates at runtime; use `tracked[A,B].build` for compile-time validation, or `buildPartial` to skip validation.

## Serialization

`DynamicMigration` and all its components are fully serializable:

```scala
import zio.blocks.schema.Schema

// Schema instances are provided
val migrationSchema: Schema[DynamicMigration] = DynamicMigration.schema
val actionSchema: Schema[MigrationAction] = MigrationAction.schema
val resolvedSchema: Schema[Resolved] = Resolved.schema

// Serialize to JSON
val migration: DynamicMigration = ???
val json = migrationSchema.encode(JsonFormat)(jsonOutput)(migration)

// Deserialize from JSON
val restored = migrationSchema.decode(JsonFormat)(jsonInput)
```

## Best Practices

1. **Use `tracked[A,B].build` for compile-time validation** in production migrations
2. **Test both forward and reverse** migrations in your test suite
3. **Store migrations as data** for audit trails and rollback capability
4. **Compose migrations** rather than building monolithic ones
5. **Use `Resolved.Literal` helpers** for type-safe literal construction

## See Also

- [Schema Evolution](schema-evolution.md) - For compile-time verified type conversions using `Into` and `As`
- [Dynamic Values](dynamic-value.md) - For understanding `DynamicValue` representation

## Comparison with Other Approaches

| Feature | ZIO Blocks | Traditional |
|---------|-----------|-------------|
| Serializable | Yes | No |
| Introspectable | Yes | No |
| Pure data | Yes | Functions |
| Reversible | Automatic | Manual |
| Type-safe | Yes | Varies |
| Runtime overhead | Low | Varies |

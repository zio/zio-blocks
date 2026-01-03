---
id: schema-evolution
title: "Schema Evolution"
sidebar_label: "Schema Evolution"
---

Schema evolution is a common challenge in distributed systems where data structures change over time. ZIO Blocks provides two type classes—`Into` and `As`—that enable type-safe, compile-time verified transformations between different versions of your data types.

## Overview

When your application evolves, you often need to:

- Add new fields to existing types
- Remove deprecated fields
- Rename fields
- Change field types (e.g., `Int` → `Long`)
- Convert between different representations of the same concept

ZIO Blocks handles these transformations with:

| Type Class | Direction | Use Case |
|------------|-----------|----------|
| `Into[A, B]` | One-way (A → B) | Migrations, API responses, data import |
| `As[A, B]` | Bidirectional (A ↔ B) | Round-trip serialization, data sync |

## Into[A, B] - One-Way Conversion

`Into[A, B]` represents a one-way conversion from type `A` to type `B` with validation:

```scala
trait Into[-A, +B] {
  def into(a: A): Either[SchemaError, B]
}
```

### Basic Usage

```scala
import zio.blocks.schema.Into

// Version 1 of our API
case class PersonV1(name: String, age: Int)

// Version 2 adds email and changes age to Long
case class PersonV2(name: String, age: Long, email: Option[String])

// Derive the conversion automatically
val migrate: Into[PersonV1, PersonV2] = Into.derived[PersonV1, PersonV2]

// Use it
val v1 = PersonV1("Alice", 30)
val v2 = migrate.into(v1)
// Right(PersonV2("Alice", 30L, None))
```

### Field Matching Rules

The macro matches fields using the following priority:

1. **Exact match**: Same name + same type
2. **Name match with coercion**: Same name + convertible type (e.g., `Int` → `Long`)
3. **Unique type match**: Type appears only once in both source and target
4. **Position + type match**: Positional correspondence with matching type

### Handling Missing Fields

When the target has fields not present in the source:

```scala
case class Source(name: String)
case class Target(name: String, age: Int = 25, nickname: Option[String])

val convert = Into.derived[Source, Target]
convert.into(Source("Bob"))
// Right(Target("Bob", 25, None))
//                     ↑    ↑
//               default   Option defaults to None
```

- **Default values**: Used when target field has a default
- **Option types**: Default to `None` when not present in source

### Numeric Conversions

Built-in support for numeric widening and narrowing:

```scala
// Widening (lossless) - always succeeds
Into[Byte, Short]   // Byte → Short
Into[Int, Long]     // Int → Long 
Into[Float, Double] // Float → Double

// Narrowing (with validation) - may fail at runtime
Into[Long, Int]     // Fails if value > Int.MaxValue or < Int.MinValue
Into[Double, Float] // Fails if value out of Float range
```

Example with validation failure:

```scala
case class BigNumbers(value: Long)
case class SmallNumbers(value: Int)

val narrow = Into.derived[BigNumbers, SmallNumbers]

narrow.into(BigNumbers(42L))
// Right(SmallNumbers(42))

narrow.into(BigNumbers(Long.MaxValue))
// Left(SchemaError: "Value 9223372036854775807 is out of range for Int")
```

### Collection Conversions

Automatic conversion between collection types:

```scala
case class ListData(items: List[Int])
case class VectorData(items: Vector[Long])

Into.derived[ListData, VectorData]
// Converts List → Vector AND Int → Long
```

Supported conversions:
- `List`, `Vector`, `Set`, `Seq` (interchangeable)
- `Array` ↔ `Iterable`
- `Map[K1, V1]` → `Map[K2, V2]`
- `Option[A]` → `Option[B]`
- `Either[L1, R1]` → `Either[L2, R2]`

:::note
Converting to `Set` may remove duplicates. Converting from `Set` does not preserve any particular ordering.
:::

### Sealed Trait / Enum Conversions

Convert between coproduct types (sealed traits, enums):

```scala
// Scala 2
sealed trait StatusV1
object StatusV1 {
  case object Active extends StatusV1
  case object Inactive extends StatusV1
}

sealed trait StatusV2
object StatusV2 {
  case object Active extends StatusV2
  case object Inactive extends StatusV2
  case object Pending extends StatusV2  // New case added
}

// Scala 3
enum StatusV1 { case Active, Inactive }
enum StatusV2 { case Active, Inactive, Pending }

// Works - all V1 cases exist in V2
Into.derived[StatusV1, StatusV2]
```

Cases are matched by:
1. **Name**: Case names must match
2. **Signature**: For case classes, field types must be convertible

### Nested Type Conversions

For nested types, provide implicit `Into` instances:

```scala
case class AddressV1(street: String, zip: Int)
case class AddressV2(street: String, zip: Long)

case class PersonV1(name: String, address: AddressV1)
case class PersonV2(name: String, address: AddressV2)

// The macro automatically uses Into[AddressV1, AddressV2] for the nested field
val convert = Into.derived[PersonV1, PersonV2]
```

### Error Accumulation

When multiple fields fail validation, all errors are accumulated:

```scala
case class Source(a: Long, b: Long, c: Long)
case class Target(a: Int, b: Int, c: Int)

val convert = Into.derived[Source, Target]
convert.into(Source(Long.MaxValue, Long.MinValue, 42L))
// Left(SchemaError containing errors for BOTH 'a' and 'b')
```

## As[A, B] - Bidirectional Conversion

`As[A, B]` extends `Into[A, B]` with a reverse conversion:

```scala
trait As[A, B] extends Into[A, B] {
  def from(input: B): Either[SchemaError, A]
  def reverse: As[B, A]
}
```

### Basic Usage

```scala
import zio.blocks.schema.As

case class Point2D(x: Int, y: Int)
case class Coordinate(x: Int, y: Int)

val convert: As[Point2D, Coordinate] = As.derived[Point2D, Coordinate]

// Both directions work
convert.into(Point2D(1, 2))      // Right(Coordinate(1, 2))
convert.from(Coordinate(3, 4))   // Right(Point2D(3, 4))

// Swap directions
val reversed: As[Coordinate, Point2D] = convert.reverse
```

### Restrictions for As

`As` has stricter requirements than `Into` to guarantee round-trip safety:

#### ❌ No Default Values

Default values break round-trip because we can't distinguish between explicitly set defaults and omitted values:

```scala
case class WithDefault(name: String, age: Int = 25)
case class NoDefault(name: String, age: Int)

// This will NOT compile:
As.derived[WithDefault, NoDefault]
// Error: "Cannot derive As[...]: Default values break round-trip guarantee"

// Use Into instead for one-way conversion:
Into.derived[NoDefault, WithDefault]  // ✓ Works
```

#### ✅ Option Fields Are Allowed

Option fields work because `None` round-trips correctly:

```scala
case class TypeA(name: String, nickname: Option[String])
case class TypeB(name: String, nickname: Option[String])

As.derived[TypeA, TypeB]  // ✓ Works
```

#### ✅ Numeric Coercions Must Be Invertible

Numeric types can be coerced if the conversion works in both directions:

```scala
case class IntVersion(value: Int)
case class LongVersion(value: Long)

As.derived[IntVersion, LongVersion]
// ✓ Works: Int → Long (widening) and Long → Int (narrowing with validation)
```

### Using As Where Into Is Expected

Since `As[A, B]` extends `Into[A, B]`, you can use it anywhere an `Into` is required:

```scala
def migrate[A, B](data: A)(implicit into: Into[A, B]): Either[SchemaError, B] =
  into.into(data)

implicit val as: As[Point2D, Coordinate] = As.derived

migrate(Point2D(1, 2))  // Uses As as an Into
```

## ZIO Prelude Newtype Support

Both `Into` and `As` automatically detect and validate ZIO Prelude newtypes:

```scala
import zio.prelude._

object Age extends Subtype[Int] {
  override def assertion = assert(between(0, 150))
}
type Age = Age.Type

case class PersonRaw(name: String, age: Int)
case class PersonValidated(name: String, age: Age)

val validate = Into.derived[PersonRaw, PersonValidated]

validate.into(PersonRaw("Alice", 30))
// Right(PersonValidated("Alice", Age(30)))

validate.into(PersonRaw("Bob", -5))
// Left(SchemaError: "Validation failed for field 'age': ...")
```

The macro automatically:
1. Detects that `Age` is a ZIO Prelude newtype
2. Calls `Age.make(value)` for validation
3. Converts `Validation` result to `Either[SchemaError, _]`

## Scala 3 Opaque Type Support

In Scala 3, opaque types with companion validation are supported:

```scala
opaque type Email = String
object Email {
  def apply(value: String): Either[String, Email] =
    if (value.contains("@")) Right(value)
    else Left(s"Invalid email: $value")
    
  def unsafe(value: String): Email = value
}

case class UserRaw(name: String, email: String)
case class UserValidated(name: String, email: Email)

Into.derived[UserRaw, UserValidated]
// Automatically uses Email.apply for validation
```

The macro looks for:
1. `apply(value: Underlying): Either[_, OpaqueType]` - validation method
2. `unsafe(value: Underlying): OpaqueType` - fallback without validation

## Structural Type Support

ZIO Blocks supports conversions involving structural types with platform-specific constraints.

### Platform Compatibility Matrix

| Conversion | JVM | JS | Native | Notes |
|------------|-----|-----|--------|-------|
| Product → Selectable/Dynamic | ✅ | ✅ | ✅ | Cross-platform with Map constructor |
| Product → Pure Structural | ✅ | ❌ | ❌ | JVM only (reflection) |
| Selectable/Dynamic → Product | ✅ | ✅ | ✅ | Cross-platform via `selectDynamic` |
| Pure Structural → Product | ✅ | ❌ | ❌ | JVM only (reflection) |

**Key insight**: If your structural type extends `Selectable` (Scala 3) or `Dynamic` (Scala 2), conversions work on **all platforms** in **both directions**. Pure structural types (without these traits) require reflection and only work on JVM.

### Scala 3: Selectable Types (Cross-Platform)

For cross-platform structural type support in Scala 3, implement `Selectable` with a `Map[String, Any]` constructor:

```scala
class Record(fields: Map[String, Any]) extends Selectable {
  def selectDynamic(name: String): Any = fields(name)
}

type PersonLike = Record { def name: String; def age: Int }

case class Person(name: String, age: Int)

// Works on JVM, JS, and Native!
val as = As.derived[Person, PersonLike]
```

**Requirements for Selectable targets:**
- Companion object with `apply(Map[String, Any]): T`, OR
- Public constructor taking `Map[String, Any]`

### Scala 2: Dynamic Types (Cross-Platform)

For cross-platform support in Scala 2, use `scala.Dynamic`:

```scala
import scala.language.dynamics

class DynamicRecord(val fields: Map[String, Any]) extends Dynamic {
  def selectDynamic(name: String): Any = fields(name)
}

object DynamicRecord {
  def apply(map: Map[String, Any]): DynamicRecord = new DynamicRecord(map)
}

type PersonLike = DynamicRecord { def name: String; def age: Int }

// Works on JVM and JS
val as = As.derived[Person, PersonLike]
```

### Pure Structural Types (JVM Only)

Pure structural types without Selectable/Dynamic require reflection:

```scala
// JVM ONLY - will fail at compile time on JS/Native
case class Person(name: String, age: Int)

val into = Into.derived[{ def name: String; def age: Int }, Person]
```

**Compile-time error on non-JVM platforms:**
```
Cannot derive Into[..., Person]: Structural type conversions are not supported on JS.

Structural types require reflection APIs which are only available on JVM.

Consider:
  - Implementing Selectable trait to enable cross-platform support
  - Using a case class instead of a structural type
  - Only using structural type conversions in JVM-only code
```

## Scala 2 vs Scala 3 Differences

| Feature | Scala 2 | Scala 3 |
|---------|---------|---------|
| Derivation syntax | `Into.derived[A, B]` | `Into.derived[A, B]` |
| Enum support | Sealed traits only | Scala 3 enums + sealed traits |
| Opaque types | N/A | ✅ Supported |
| Structural types | Dynamic-based | Selectable-based |
| ZIO Prelude newtypes | ✅ `assert { ... }` syntax | ✅ `override def assertion` syntax |
| Error messages | Detailed macro errors | Detailed macro errors |

### ZIO Prelude Newtype Syntax

**Scala 2:**
```scala
object Age extends Subtype[Int] {
  override def assertion = assert {
    between(0, 150)
  }
}
```

**Scala 3:**
```scala
object Age extends Subtype[Int] {
  override def assertion: Assertion[Int] =
    zio.prelude.Assertion.between(0, 150)
}
```

## Best Practices

### 1. Prefer As When Round-Trip Is Required

```scala
// For data sync, use As
val sync: As[LocalModel, RemoteModel] = As.derived

// For one-way migrations, use Into
val migrate: Into[OldFormat, NewFormat] = Into.derived
```

### 2. Use Option for Truly Optional Fields

```scala
// Good: Optional field with Option
case class V2(name: String, email: Option[String])

// Avoid: Default values break As derivation
case class V2(name: String, email: String = "")
```

### 3. Provide Explicit Instances for Complex Nested Types

```scala
// When nested types need custom logic
implicit val addressConvert: Into[AddressV1, AddressV2] = 
  Into.derived[AddressV1, AddressV2]

// Now this works automatically
val personConvert = Into.derived[PersonV1, PersonV2]
```

### 4. Use Platform-Appropriate Structural Types

```scala
// Cross-platform: Use Selectable (Scala 3) or Dynamic (Scala 2)
class Record(fields: Map[String, Any]) extends Selectable { ... }

// JVM-only: Pure structural types
type PersonLike = { def name: String }  // Requires reflection
```

## Complete Example

Here's a complete schema evolution example:

```scala
import zio.blocks.schema._

// API v1
object V1 {
  case class Address(street: String, city: String)
  case class Person(name: String, age: Int, address: Address)
}

// API v2 - adds fields, changes types
object V2 {
  case class Address(street: String, city: String, country: String = "US")
  case class Person(
    name: String, 
    age: Long,  // Changed from Int
    address: Address,
    email: Option[String]  // New field
  )
}

// Define conversions
object Migrations {
  // Address: one-way (v2 has default for country)
  implicit val addressMigrate: Into[V1.Address, V2.Address] = 
    Into.derived[V1.Address, V2.Address]
  
  // Person: one-way (v2 has new optional field)
  implicit val personMigrate: Into[V1.Person, V2.Person] = 
    Into.derived[V1.Person, V2.Person]
}

// Usage
import Migrations._

val oldPerson = V1.Person("Alice", 30, V1.Address("123 Main St", "NYC"))
val newPerson = personMigrate.into(oldPerson)
// Right(V2.Person("Alice", 30L, V2.Address("123 Main St", "NYC", "US"), None))
```

## Error Handling

All conversions return `Either[SchemaError, B]` for explicit error handling:

```scala
val result = migrate.into(oldData)

result match {
  case Right(newData) => 
    // Success - use newData
    
  case Left(error) => 
    // Handle validation/conversion failure
    println(s"Migration failed: ${error.message}")
}
```

`SchemaError` provides:
- Detailed error messages
- Field path information
- Error accumulation (multiple errors combined)


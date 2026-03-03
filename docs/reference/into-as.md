---
id: into-as
title: "Into and As Type Classes"
sidebar_label: "Into & As"
---

`Into[A, B]` and `As[A, B]` are **type class macros** that derive type-safe conversions between schema-compatible types. They enable seamless transformations across product types (case classes, tuples), coproducts (sealed traits), collections, and primitives — including special support for `DynamicValue` as a semi-structured representation.

**Core distinction:**
- **`Into[A, B]`** — One-way conversion from `A` to `B`
- **`As[A, B]`** — Bidirectional conversion: includes both `Into[A, B]` (via `into`) and the reverse direction (via `from`)

## Motivation

Converting between types is fundamental in software: domain objects cross API boundaries, data structures evolve over time, and schema-driven systems need bridges between typed and semi-structured data. ZIO Blocks' Into/As macros address this by:

1. **Eliminating boilerplate:** Derive conversions automatically using schema information
2. **Handling complex cases:** Automatically wire conversions through nested structures, collections, and sealed traits
3. **Providing validation:** Numeric narrowing, structure mismatches, and conversion errors are surfaced as `SchemaError` results
4. **Supporting DynamicValue:** Convert seamlessly between any schema-compatible type and `DynamicValue`, enabling polyglot data handling and multiple serialization formats

This is critical for building flexible APIs that work with both type-safe domain objects and semi-structured data representations.

```scala
trait Into[-A, +B] {
  def into(a: A): Either[SchemaError, B]
}

trait As[A, B] extends Into[A, B] {
  def from(input: B): Either[SchemaError, A]
  def reverse: As[B, A]
}
```

## Construction / Creating Instances

### Deriving Conversions with Macros

The primary way to create `Into` and `As` instances is via the macro-based `derived` method:

```scala
object Into extends IntoVersionSpecific {
  inline def derived[A, B]: Into[A, B] = ${ /* macro implementation */ }
  def apply[A, B](implicit ev: Into[A, B]): Into[A, B] = ev
}

object As extends AsVersionSpecific {
  inline def derived[A, B]: As[A, B] = ${ /* macro implementation */ }
  def apply[A, B](implicit ev: As[A, B]): As[A, B] = ev
}
```

**How it works:**

The macro examines the schema structure of `A` and `B`, then generates an `Into` or `As` instance that:
1. Attempts to summon an implicit `Schema[A]` and `Schema[B]`
2. If schemas don't exist, derives them automatically
3. For DynamicValue conversions, uses `Schema[A].toDynamicValue` or `Schema[A].fromDynamicValue` for consistency
4. Wires conversions through nested products, collections, and coproducts

#### Example: Case Class to Case Class

```scala mdoc:compile-only
import zio.blocks.schema.Into

case class PersonDTO(name: String, age: Int)
case class Person(name: String, age: Long)

// Macro generates a conversion that widens age from Int to Long
val intoPerson = Into.derived[PersonDTO, Person]

val dto = PersonDTO("Alice", 30)
val result: Either[SchemaError, Person] = intoPerson.into(dto)
// result == Right(Person("Alice", 30L))
```

### Manual Construction

You can also construct `As` from two separate `Into` instances:

```scala
object As {
  def apply[A, B](intoAB: Into[A, B], intoBA: Into[B, A]): As[A, B] = ???
}
```

This is useful when you already have custom `Into` instances and want to combine them into a bidirectional conversion:

```scala mdoc:compile-only
import zio.blocks.schema.{Into, As, SchemaError}

case class PersonDTO(name: String, age: Int)
case class Person(name: String, age: Long)

// Suppose we have custom Into instances
implicit val dtoToPerson: Into[PersonDTO, Person] =
  dto => Right(Person(dto.name, dto.age.toLong))

implicit val personToDTO: Into[Person, PersonDTO] =
  person => Right(PersonDTO(person.name, person.age.toInt))

// Combine them into a bidirectional As
val as: As[PersonDTO, Person] = As(dtoToPerson, personToDTO)

val result1 = as.into(PersonDTO("Bob", 25))   // PersonDTO → Person
val result2 = as.from(Person("Bob", 25L))     // Person → PersonDTO
```

## Predefined Instances

### Numeric Conversions

ZIO Blocks provides built-in `Into` instances for safe numeric conversions:

| From | To | Type | Notes |
|------|-----|------|-------|
| Byte, Short, Int, Long, Float | Wider types | Widening | Lossless, always succeeds |
| Long, Int, Short, Float, Double | Narrower types | Narrowing | Validates at runtime, fails if out of range |

**Widening (lossless):**

```scala mdoc:compile-only
import zio.blocks.schema.Into

val byteToLong: Into[Byte, Long] = Into[Byte, Long]
val result = byteToLong.into(42.toByte)
// result == Right(42L)
```

**Narrowing (with validation):**

```scala mdoc:compile-only
import zio.blocks.schema.Into

val longToInt: Into[Long, Int] = Into[Long, Int]

// Success: value in range
val ok = longToInt.into(42L)
// ok == Right(42)

// Failure: value out of range
val fail = longToInt.into(Long.MaxValue)
// fail == Left(SchemaError(...message about range...))
```

### Identity Conversion

Every type supports identity conversion to itself:

```scala mdoc:compile-only
import zio.blocks.schema.Into

val id: Into[String, String] = Into[String, String]
val result = id.into("hello")
// result == Right("hello")
```

### Container Conversions

ZIO Blocks provides instances for common containers, allowing conversions between compatible types:

```scala mdoc:compile-only
import zio.blocks.schema.Into

// Option[A] → Option[B] (if Into[A, B] exists)
val optInto: Into[Option[Int], Option[Long]] = Into.derived
val result = optInto.into(Some(42))
// result == Right(Some(42L))

// Either[L1, R1] → Either[L2, R2] (if Into[L1, L2] and Into[R1, R2] exist)
val eitherInto: Into[Either[Int, String], Either[Long, String]] = Into.derived
val result2 = eitherInto.into(Right("hello"))
// result2 == Right(Right("hello"))

// Map[K1, V1] → Map[K2, V2]
val mapInto: Into[Map[Int, String], Map[Long, String]] = Into.derived
val result3 = mapInto.into(Map(1 -> "a", 2 -> "b"))
// result3 == Right(Map(1L -> "a", 2L -> "b"))

// Iterable/Array conversions
val listToSet: Into[List[Int], Set[Int]] = Into.derived
val result4 = listToSet.into(List(1, 2, 3))
// result4 == Right(Set(1, 2, 3))
```

## Core Operations

### One-Way Conversions with Into

#### `into` — Convert A to B

Performs the conversion, returning either a successful result or a `SchemaError`:

```scala
trait Into[-A, +B] {
  def into(a: A): Either[SchemaError, B]
}
```

```scala mdoc:compile-only
import zio.blocks.schema.Into

case class Source(value: Int)
case class Target(value: Long)

val convert = Into.derived[Source, Target]
val result = convert.into(Source(42))
// result == Right(Target(42L))
```

### Bidirectional Conversions with As

#### `into` — Forward Direction (A to B)

```scala
trait As[A, B] extends Into[A, B] {
  def into(input: A): Either[SchemaError, B]
}
```

#### `from` — Reverse Direction (B to A)

Converts in the opposite direction:

```scala
trait As[A, B] extends Into[A, B] {
  def from(input: B): Either[SchemaError, A]
}
```

```scala mdoc:compile-only
import zio.blocks.schema.As

case class Person(name: String, age: Int)
case class PersonDTO(name: String, age: Int)

val as = As.derived[Person, PersonDTO]

// Forward: Person → PersonDTO
val forward = as.into(Person("Alice", 30))
// forward == Right(PersonDTO("Alice", 30))

// Reverse: PersonDTO → Person
val reverse = as.from(PersonDTO("Bob", 25))
// reverse == Right(Person("Bob", 25))
```

#### `reverse` — Swap Direction

Returns an `As[B, A]` that reverses the direction:

```scala
trait As[A, B] extends Into[A, B] {
  def reverse: As[B, A]
}
```

```scala mdoc:compile-only
import zio.blocks.schema.As

case class Inner(x: Int)
case class Outer(inner: Inner)

val as: As[Inner, Outer] = As.derived

// Forward: Inner → Outer
val forward = as.into(Inner(1))

// Create reversed conversion: Outer → Inner
val reversed = as.reverse
val backward = reversed.into(Outer(Inner(1)))
```

## DynamicValue Conversions

A key feature of Into/As is seamless conversion to and from `DynamicValue`, a semi-structured representation that captures any schema-compatible type.

### Converting To DynamicValue

Any type `A` with a `Schema[A]` can be converted to `DynamicValue`:

```scala mdoc:compile-only
import zio.blocks.schema.{Into, DynamicValue}

case class Person(name: String, age: Int)

val toDynamic = Into.derived[Person, DynamicValue]
val result = toDynamic.into(Person("Alice", 30))
// result == Right(DynamicValue.Record(
//   "name" -> DynamicValue.string("Alice"),
//   "age"  -> DynamicValue.int(30)
// ))
```

This is especially useful for:
- **Polyglot serialization:** Convert typed data to DynamicValue, then serialize to JSON, Avro, Protobuf, etc.
- **Flexible APIs:** Accept both typed data and semi-structured input
- **Schema-driven transformations:** Bridge between type-safe code and data handling systems

### Converting From DynamicValue

Given a `DynamicValue` with a matching structure, convert it back to a strongly-typed value:

```scala mdoc:compile-only
import zio.blocks.schema.{Into, DynamicValue}

case class Person(name: String, age: Int)

val fromDynamic = Into.derived[DynamicValue, Person]
val dv = DynamicValue.Record(
  "name" -> DynamicValue.string("Bob"),
  "age"  -> DynamicValue.int(25)
)
val result = fromDynamic.into(dv)
// result == Right(Person("Bob", 25))
```

Conversion fails gracefully if the structure doesn't match:

```scala mdoc:compile-only
import zio.blocks.schema.{Into, DynamicValue, SchemaError}

case class Person(name: String, age: Int)

val fromDynamic = Into.derived[DynamicValue, Person]
val badDV = DynamicValue.Primitive(PrimitiveValue.String("not a record"))
val result = fromDynamic.into(badDV)
// result == Left(SchemaError(...))
```

### Collections and DynamicValue

Conversions work through collections as well:

```scala mdoc:compile-only
import zio.blocks.schema.{Into, DynamicValue}

case class Item(id: Int, name: String)

// List[Item] → DynamicValue.Sequence
val listToDynamic = Into.derived[List[Item], DynamicValue]
val items = List(Item(1, "A"), Item(2, "B"))
val result = listToDynamic.into(items)
// result == Right(DynamicValue.Sequence(...))
```

## Compatibility Rules

### Into Derivation

`Into[A, B]` can be derived when:
- Both `A` and `B` are schemas or have associated `Schema` instances
- Field mappings are resolvable (by name or position)
- Type conversions exist (either built-in or via implicit `Into` instances)

### As Derivation

`As[A, B]` is stricter because it requires bidirectional compatibility:
- Field mappings must be invertible
- Numeric coercions must be safe in both directions (narrowing requires runtime validation)
- Optional fields can be added in one direction (become `None` in reverse) but must be consistent
- Collections may be lossy (e.g., Set ↔ List) — the conversion succeeds but may not round-trip perfectly

**Example: invertible conversions**

```scala mdoc:compile-only
import zio.blocks.schema.As

case class Source(x: Int, y: Option[String])
case class Target(x: Long, y: Option[String], z: Option[Int])

// As can be derived: x widens (invertible), y is consistent, z is optional (becomes None in reverse)
val as = As.derived[Source, Target]

val forward = as.into(Source(1, Some("hello")))
// forward == Right(Target(1L, Some("hello"), None))

val reverse = as.from(Target(2L, Some("world"), Some(99)))
// reverse == Right(Source(2, Some("world")))
// Note: z is dropped; no bidirectional guarantee for lossy fields
```

## Common Patterns

### Schema Evolution (Adding Fields)

Convert between two versions of a type when a new field is added:

```scala mdoc:compile-only
import zio.blocks.schema.Into

case class PersonV1(name: String, age: Int)
case class PersonV2(name: String, age: Int, email: Option[String])

val upgradeSchema = Into.derived[PersonV1, PersonV2]
val v1 = PersonV1("Alice", 30)
val result = upgradeSchema.into(v1)
// result == Right(PersonV2("Alice", 30, None))
```

### Custom Validations with Implicit Into

Provide a custom `Into` instance for a domain type so the macro wires it into larger conversions:

```scala mdoc:compile-only
import zio.blocks.schema.{Into, SchemaError}

final class Email private (val value: String)
object Email {
  def apply(value: String): Email = new Email(value)

  implicit val stringToEmail: Into[String, Email] = { s =>
    if (s.contains("@")) Right(Email(s))
    else Left(SchemaError(s"Invalid email: '$s'"))
  }
}

case class UserDTO(name: String, email: String)
case class User(name: String, email: Email)

val toUser = Into.derived[UserDTO, User]

// Validation happens automatically
val result = toUser.into(UserDTO("Bob", "invalid-email"))
// result == Left(SchemaError(...))
```

### Numeric Coercions

Convert between numeric types with automatic validation for narrowing:

```scala mdoc:compile-only
import zio.blocks.schema.Into

case class Dimensions(width: Int, height: Int)
case class CompactDimensions(width: Byte, height: Byte)

val toCompact = Into.derived[Dimensions, CompactDimensions]

val small = toCompact.into(Dimensions(100, 200))
// small == Right(CompactDimensions(100.toByte, 200.toByte))

val tooLarge = toCompact.into(Dimensions(1000, 2000))
// tooLarge == Left(SchemaError(...out of range...))
```

## Error Handling

Conversions return `Either[SchemaError, B]`, giving you precise control over failure handling:

```scala mdoc:compile-only
import zio.blocks.schema.Into

case class Source(value: Int)
case class Target(value: Byte)

val convert = Into.derived[Source, Target]

val success = convert.into(Source(42))
  .map { target => println(s"Success: $target") }
  .leftMap { err => println(s"Conversion failed: $err") }

val failure = convert.into(Source(1000))
  .map { target => println(s"Success: $target") }
  .leftMap { err => println(s"Conversion failed: $err") }
```

Errors are accumulated across nested structures:

```scala mdoc:compile-only
import zio.blocks.schema.Into

case class Item(id: Int, value: Byte)

// Converting a list of Items where conversion might fail
val convert = Into.derived[List[(Int, Int)], List[Item]]

val data = List((1, 50), (2, 300), (3, 75))
val result = convert.into(data)
// result == Left(SchemaError(...Item at index 1 failed: value 300 out of range...))
```

## Integration with Schema

`Into` and `As` are deeply integrated with `Schema`. When you derive an `Into[A, B]`:

1. The macro summons or derives `Schema[A]` and `Schema[B]`
2. For DynamicValue conversions, it uses the schema's `toDynamicValue` and `fromDynamicValue` methods
3. This ensures that conversions through DynamicValue are **consistent with schema semantics**

This consistency is critical for polyglot systems where data flows between different representations and formats.

## Running the Examples

All code from this guide is available as runnable examples in the `schema-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

**Converting between case classes with numeric widening**
([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/into/IntoDomainBoundaryExample.scala))

```bash
sbt "schema-examples/runMain into.IntoDomainBoundaryExample"
```

**Bidirectional conversion with reverse and round-trip guarantees**
([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/as/AsNumericRoundTripExample.scala))

```bash
sbt "schema-examples/runMain as.AsNumericRoundTripExample"
```

**Combining two Into instances to create an As**
([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/as/AsManualConstructionExample.scala))

```bash
sbt "schema-examples/runMain as.AsManualConstructionExample"
```

**Schema evolution: converting between versions with added optional fields**
([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/as/AsSchemaEvolutionExample.scala))

```bash
sbt "schema-examples/runMain as.AsSchemaEvolutionExample"
```

**Using reverse to create the inverse conversion**
([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/as/AsReverseExample.scala))

```bash
sbt "schema-examples/runMain as.AsReverseExample"
```

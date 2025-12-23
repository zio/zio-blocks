# Into[A, B] - Usage Guide

## Overview

`Into[A, B]` is a type class that provides type-safe, one-way conversions from type `A` to type `B` with runtime validation. It's designed for schema evolution scenarios where you need to convert between different versions of your data types.

## Basic Usage

### Automatic Derivation

The simplest way to use `Into` is with automatic derivation:

```scala
import zio.blocks.schema.Into

case class UserV1(name: String, age: Int)
case class UserV2(name: String, age: Long)

val into = Into.derived[UserV1, UserV2]
val userV1 = UserV1("Alice", 30)
val result = into.into(userV1) // Right(UserV2("Alice", 30L))
```

### Manual Instance

You can also provide your own `Into` instance:

```scala
implicit val customInto: Into[String, Int] = new Into[String, Int] {
  def into(input: String): Either[SchemaError, Int] = 
    input.toIntOption.toRight(
      SchemaError.expectationMismatch(Nil, s"Cannot convert '$input' to Int")
    )
}
```

## Numeric Conversions

### Widening (Lossless)

```scala
val byteToLong = Into.derived[Byte, Long]
byteToLong.into(42.toByte) // Right(42L)
```

### Narrowing (with Validation)

```scala
val longToInt = Into.derived[Long, Int]
longToInt.into(Int.MaxValue.toLong) // Right(Int.MaxValue)
longToInt.into(Long.MaxValue) // Left(SchemaError) - overflow
```

## Product Types (Case Classes)

### Field Mapping

`Into` automatically maps fields by:
1. **Exact name + type match** (highest priority)
2. **Name match with type coercion**
3. **Unique type match** (when types appear only once)
4. **Positional match** (for tuples)

```scala
case class PersonV1(name: String, age: Int)
case class PersonV2(fullName: String, years: Int) // Renamed fields

// Works because types are unique
val into = Into.derived[PersonV1, PersonV2]
```

### Schema Evolution

#### Adding Optional Fields

```scala
case class UserV1(id: Int, name: String)
case class UserV2(id: Int, name: String, email: Option[String])

val into = Into.derived[UserV1, UserV2]
into.into(UserV1(1, "Alice")) // Right(UserV2(1, "Alice", None))
```

#### Adding Required Fields with Defaults

```scala
case class ProductV1(name: String, price: Double)
case class ProductV2(name: String, price: Double, available: Boolean = true)

val into = Into.derived[ProductV1, ProductV2]
into.into(ProductV1("Widget", 10.0)) // Right(ProductV2("Widget", 10.0, true))
```

#### Removing Fields

```scala
case class UserV2(id: Int, name: String, email: Option[String])
case class UserV1(id: Int, name: String)

val into = Into.derived[UserV2, UserV1]
into.into(UserV2(1, "Alice", Some("alice@example.com"))) 
// Right(UserV1(1, "Alice")) - email is dropped
```

## Coproduct Types (Sealed Traits)

### Name-Based Matching

```scala
sealed trait Color
object Color {
  case object Red extends Color
  case object Green extends Color
  case class Blue(intensity: Int) extends Color
}

sealed trait Colour // British spelling
object Colour {
  case object Red extends Colour
  case object Green extends Colour
  case class Blue(intensity: Int) extends Colour
}

val into = Into.derived[Color, Colour]
into.into(Color.Red) // Right(Colour.Red)
into.into(Color.Blue(255)) // Right(Colour.Blue(255))
```

### Signature-Based Matching

When case names differ but constructor signatures match:

```scala
sealed trait EventV1
object EventV1 {
  case class Created(name: String, timestamp: Long) extends EventV1
}

sealed trait EventV2
object EventV2 {
  case class Spawned(name: String, timestamp: Long) extends EventV2
}

val into = Into.derived[EventV1, EventV2]
into.into(EventV1.Created("test", 123L)) // Right(EventV2.Spawned("test", 123L))
```

## Collections

### Element Type Coercion

```scala
val listInto = Into.derived[List[Int], List[Long]]
listInto.into(List(1, 2, 3)) // Right(List(1L, 2L, 3L))
```

### Collection Type Conversion

```scala
val listToVector = Into.derived[List[String], Vector[String]]
listToVector.into(List("a", "b", "c")) // Right(Vector("a", "b", "c"))

val listToSet = Into.derived[List[Int], Set[Int]]
listToSet.into(List(1, 2, 2, 3)) // Right(Set(1, 2, 3)) - duplicates removed
```

### Combined Conversions

```scala
val combined = Into.derived[List[Int], Vector[Long]]
combined.into(List(1, 2, 3)) // Right(Vector(1L, 2L, 3L))
```

## Nested Conversions

### Nested Products

```scala
case class AddressV1(street: String, number: Int)
case class PersonV1(name: String, address: AddressV1)

case class AddressV2(street: String, number: Long)
case class PersonV2(name: String, address: AddressV2)

val into = Into.derived[PersonV1, PersonV2]
// Automatically converts nested AddressV1 to AddressV2
```

### Collections of Complex Types

```scala
val into = Into.derived[List[PersonV1], Vector[PersonV2]]
// Converts each PersonV1 to PersonV2, and List to Vector
```

## Opaque Types (Scala 3)

```scala
opaque type UserId = String
object UserId {
  def apply(s: String): Either[String, UserId] = 
    if (s.nonEmpty) Right(s) else Left("UserId cannot be empty")
}

val into = Into.derived[String, UserId]
into.into("user123") // Right(UserId("user123"))
into.into("") // Left(SchemaError) - validation failed
```

## Structural Types (Scala 3)

### Case Class to Structural Type

```scala
case class Point(x: Int, y: Int)

type PointStruct = {
  def x: Int
  def y: Int
}

val into = Into.derived[Point, PointStruct]
into.into(Point(10, 20)) // Creates a Selectable proxy
```

## Error Handling

### Using Either

```scala
val into = Into.derived[Long, Int]
into.into(Int.MaxValue.toLong) match {
  case Right(value) => println(s"Converted: $value")
  case Left(error) => println(s"Error: ${error.message}")
}
```

### Using intoOrThrow

```scala
val into = Into.derived[Long, Int]
try {
  val value = into.intoOrThrow(42L) // Returns Int directly
} catch {
  case e: SchemaError => println(s"Conversion failed: ${e.message}")
}
```

## Best Practices

1. **Use automatic derivation** when possible - it handles most cases
2. **Provide explicit instances** for custom conversions or performance-critical paths
3. **Handle errors** - always check `Either` results or use try-catch with `intoOrThrow`
4. **Test edge cases** - especially for narrowing conversions and schema evolution
5. **Document custom instances** - explain why manual conversion is needed

## Limitations

- Structural types in Scala 2 are not supported (use Scala 3)
- Very large products (100+ fields) may have slower compilation
- Mutually recursive types work but may have performance implications


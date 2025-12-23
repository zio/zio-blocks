# As[A, B] - Usage Guide

## Overview

`As[A, B]` provides bidirectional type-safe conversions between types `A` and `B`. It ensures that conversions work in both directions and are compatible for round-trip conversions.

## Key Invariant

For `As[A, B]`:
- `from(into(a))` should succeed and produce an equivalent value
- `into(from(b))` should succeed and produce an equivalent value

## Basic Usage

### Automatic Derivation

```scala
import zio.blocks.schema.As

case class UserV1(name: String, age: Int)
case class UserV2(name: String, age: Long)

val as = As.derived[UserV1, UserV2]
val userV1 = UserV1("Alice", 30)

// Convert V1 to V2
val userV2 = as.into(userV1) // Right(UserV2("Alice", 30L))

// Convert V2 back to V1
val backToV1 = as.from(userV2) // Right(UserV1("Alice", 30))
```

## Round-Trip Guarantees

`As` ensures that round-trip conversions preserve data:

```scala
val as = As.derived[PersonV1, PersonV2]

// Forward and back
val original = PersonV1("Alice", 30)
val converted = as.into(original).getOrElse(???)
val restored = as.from(converted).getOrElse(???)

// restored should be equivalent to original
assert(restored == original)
```

## Compatibility Checks

`As` automatically checks that conversions are compatible:

### ✅ Allowed

- Widening conversions (Byte → Long)
- Field reordering
- Adding optional fields
- Type refinements (Int → Long)

### ❌ Not Allowed

- Narrowing conversions in both directions (would lose data)
- Using default values (breaks round-trip)

```scala
// This will fail at compile time:
// case class V1(name: String)
// case class V2(name: String, age: Int = 0) // Default value not allowed
// val as = As.derived[V1, V2] // Compile error!
```

## Schema Evolution Patterns

### Adding Optional Fields (Bidirectional)

```scala
case class UserV1(id: Int, name: String)
case class UserV2(id: Int, name: String, email: Option[String])

val as = As.derived[UserV1, UserV2]

// V1 → V2: email becomes None
as.into(UserV1(1, "Alice")) // Right(UserV2(1, "Alice", None))

// V2 → V1: email is dropped
as.from(UserV2(1, "Alice", Some("alice@example.com"))) 
// Right(UserV1(1, "Alice"))
```

### Field Reordering

```scala
case class PersonV1(name: String, age: Int)
case class PersonV2(age: Int, name: String) // Reordered

val as = As.derived[PersonV1, PersonV2]
// Works in both directions
```

## Collections

```scala
val as = As.derived[List[Int], Vector[Long]]

// List[Int] → Vector[Long]
as.into(List(1, 2, 3)) // Right(Vector(1L, 2L, 3L))

// Vector[Long] → List[Int] (with validation)
as.from(Vector(1L, 2L, 3L)) // Right(List(1, 2, 3))
```

## Converting to Into

You can extract `Into` instances from `As`:

```scala
val as = As.derived[UserV1, UserV2]

// Get Into[UserV1, UserV2]
val into = as.asInto

// Get Into[UserV2, UserV1]
val reverseInto = as.asIntoReverse
```

## Error Handling

```scala
val as = As.derived[Long, Int]

// Using Either
as.into(Int.MaxValue.toLong) match {
  case Right(value) => println(s"Converted: $value")
  case Left(error) => println(s"Error: ${error.message}")
}

// Using intoOrThrow / fromOrThrow
try {
  val value = as.intoOrThrow(42L)
  val back = as.fromOrThrow(value)
} catch {
  case e: SchemaError => println(s"Conversion failed: ${e.message}")
}
```

## Best Practices

1. **Use `As` for bidirectional conversions** - ensures round-trip compatibility
2. **Use `Into` for one-way conversions** - more flexible, allows data loss
3. **Avoid default values** - they break round-trip guarantees
4. **Test round-trips** - verify that `from(into(a)) == a` for your types
5. **Prefer optional fields** over defaults for schema evolution

## When to Use As vs Into

- **Use `As`** when:
  - You need bidirectional conversion
  - Round-trip preservation is important
  - Both types are equally valid representations

- **Use `Into`** when:
  - Conversion is one-way only
  - You're migrating from old to new schema
  - You need more flexibility (defaults, narrowing, etc.)


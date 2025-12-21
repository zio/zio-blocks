# Migration Guide - Schema Evolution with Into & As

This guide helps you migrate between different versions of your data types using `Into[A, B]` and `As[A, B]`.

## Table of Contents

1. [Basic Migration Patterns](#basic-migration-patterns)
2. [Field-Level Changes](#field-level-changes)
3. [Type-Level Changes](#type-level-changes)
4. [Structural Changes](#structural-changes)
5. [Breaking Changes and Workarounds](#breaking-changes-and-workarounds)
6. [Best Practices](#best-practices)
7. [Common Pitfalls](#common-pitfalls)

---

## Basic Migration Patterns

### Simple Field Addition

**Scenario:** Adding a new optional field to your schema.

```scala
// Version 1
case class UserV1(id: Int, name: String)

// Version 2 - Add optional email
case class UserV2(id: Int, name: String, email: Option[String])

// Migration
val into = Into.derived[UserV1, UserV2]
val userV1 = UserV1(1, "Alice")
val userV2 = into.into(userV1) // Right(UserV2(1, "Alice", None))
```

### Field Type Widening

**Scenario:** Changing a field type to a wider type (e.g., Int → Long).

```scala
// Version 1
case class ProductV1(id: Int, price: Double)

// Version 2 - Widen id to Long
case class ProductV2(id: Long, price: Double)

// Migration - automatic, lossless
val into = Into.derived[ProductV1, ProductV2]
val product = ProductV1(42, 99.99)
val migrated = into.into(product) // Right(ProductV2(42L, 99.99))
```

### Field Removal

**Scenario:** Removing a field that's no longer needed.

```scala
// Version 1
case class UserV1(id: Int, name: String, deprecatedField: String)

// Version 2 - Remove deprecated field
case class UserV2(id: Int, name: String)

// Migration - field is automatically dropped
val into = Into.derived[UserV1, UserV2]
val user = UserV1(1, "Alice", "old")
val migrated = into.into(user) // Right(UserV2(1, "Alice"))
```

---

## Field-Level Changes

### Field Renaming

**Scenario:** Renaming a field while keeping the same type.

```scala
// Version 1
case class PersonV1(name: String, age: Int)

// Version 2 - Rename fields
case class PersonV2(fullName: String, years: Int)

// Migration - works if types are unique
val into = Into.derived[PersonV1, PersonV2]
// Automatically maps by unique type
```

**Note:** If multiple fields have the same type, you may need to provide an explicit `Into` instance.

### Field Reordering

**Scenario:** Changing the order of fields.

```scala
// Version 1
case class AddressV1(street: String, city: String, zip: String)

// Version 2 - Reordered
case class AddressV2(city: String, zip: String, street: String)

// Migration - fields matched by name, order doesn't matter
val into = Into.derived[AddressV1, AddressV2]
```

### Adding Required Fields with Defaults

**Scenario:** Adding a new required field with a default value (Scala 3 only).

```scala
// Version 1
case class ProductV1(name: String, price: Double)

// Version 2 - Add required field with default
case class ProductV2(name: String, price: Double, available: Boolean = true)

// Migration - default value is used
val into = Into.derived[ProductV1, ProductV2]
val product = ProductV1("Widget", 10.0)
val migrated = into.into(product) // Right(ProductV2("Widget", 10.0, true))
```

**Important:** This pattern works with `Into` but **not** with `As` (round-trip would fail).

---

## Type-Level Changes

### Numeric Narrowing

**Scenario:** Converting to a narrower numeric type (requires validation).

```scala
// Version 1
case class ConfigV1(maxConnections: Long, timeout: Double)

// Version 2 - Narrow types
case class ConfigV2(maxConnections: Int, timeout: Float)

// Migration - includes runtime validation
val into = Into.derived[ConfigV1, ConfigV2]
val config = ConfigV1(Int.MaxValue.toLong, 5.0)
val migrated = into.into(config) // Right(ConfigV2(Int.MaxValue, 5.0f))

// Fails if value is out of range
val invalid = ConfigV1(Long.MaxValue, 5.0)
val result = into.into(invalid) // Left(SchemaError) - overflow
```

### Collection Type Changes

**Scenario:** Changing collection types or element types.

```scala
// Version 1
case class DataV1(items: List[Int], tags: Set[String])

// Version 2 - Change collection and element types
case class DataV2(items: Vector[Long], tags: List[String])

// Migration - handles both collection and element conversion
val into = Into.derived[DataV1, DataV2]
val data = DataV1(List(1, 2, 3), Set("a", "b"))
val migrated = into.into(data) 
// Right(DataV2(Vector(1L, 2L, 3L), List("a", "b")))
```

---

## Structural Changes

### Nested Type Evolution

**Scenario:** Migrating nested structures.

```scala
// Version 1
case class AddressV1(street: String, number: Int)
case class PersonV1(name: String, address: AddressV1)

// Version 2 - Evolve nested type
case class AddressV2(street: String, number: Long)
case class PersonV2(name: String, address: AddressV2)

// Migration - automatically handles nesting
val into = Into.derived[PersonV1, PersonV2]
val person = PersonV1("Alice", AddressV1("Main St", 123))
val migrated = into.into(person)
// Right(PersonV2("Alice", AddressV2("Main St", 123L)))
```

### Coproduct Evolution

**Scenario:** Migrating sealed trait hierarchies.

```scala
// Version 1
sealed trait EventV1
object EventV1 {
  case class Created(name: String, timestamp: Long) extends EventV1
  case class Updated(name: String) extends EventV1
  case object Deleted extends EventV1
}

// Version 2 - Rename cases, change types
sealed trait EventV2
object EventV2 {
  case class Spawned(name: String, timestamp: Long) extends EventV2 // Renamed
  case class Modified(name: String) extends EventV2 // Renamed
  case object Removed extends EventV2 // Renamed
}

// Migration - matches by signature when names differ
val into = Into.derived[EventV1, EventV2]
val event = EventV1.Created("test", 123L)
val migrated = into.into(event) // Right(EventV2.Spawned("test", 123L))
```

---

## Breaking Changes and Workarounds

### Incompatible Type Changes

**Problem:** Changing a field to an incompatible type.

```scala
// Version 1
case class UserV1(id: String, age: Int)

// Version 2 - Incompatible change
case class UserV2(id: Int, age: String) // Types swapped

// This won't work automatically - provide custom instance
implicit val customInto: Into[UserV1, UserV2] = new Into[UserV1, UserV2] {
  def into(input: UserV1): Either[SchemaError, UserV2] = {
    for {
      id <- input.id.toIntOption.toRight(
        SchemaError.expectationMismatch(Nil, s"Cannot convert '${input.id}' to Int")
      )
      age = input.age.toString
    } yield UserV2(id, age)
  }
}
```

### Ambiguous Field Mapping

**Problem:** Multiple fields with the same type, making mapping ambiguous.

```scala
// Version 1
case class PointV1(x: Int, y: Int)

// Version 2 - Renamed but both are Int
case class PointV2(horizontal: Int, vertical: Int)

// This may fail - provide explicit mapping
implicit val pointInto: Into[PointV1, PointV2] = new Into[PointV1, PointV2] {
  def into(input: PointV1): Either[SchemaError, PointV2] =
    Right(PointV2(input.x, input.y))
}
```

### Missing Required Fields

**Problem:** Target has a required field that source doesn't have.

```scala
// Version 1
case class UserV1(name: String)

// Version 2 - Added required field
case class UserV2(name: String, email: String) // No default!

// Solution 1: Make it optional
case class UserV2(name: String, email: Option[String])

// Solution 2: Add default (Scala 3)
case class UserV2(name: String, email: String = "")

// Solution 3: Provide custom instance with fallback
implicit val userInto: Into[UserV1, UserV2] = new Into[UserV1, UserV2] {
  def into(input: UserV1): Either[SchemaError, UserV2] =
    Right(UserV2(input.name, "")) // Use empty string as fallback
}
```

---

## Best Practices

### 1. Prefer Optional Fields for New Fields

```scala
// ✅ Good - backward compatible
case class UserV2(id: Int, name: String, email: Option[String])

// ⚠️ Less flexible - requires default
case class UserV2(id: Int, name: String, email: String = "")
```

### 2. Use Type Widening When Possible

```scala
// ✅ Good - lossless, automatic
case class V1(id: Int)
case class V2(id: Long)

// ⚠️ Requires validation, may fail
case class V1(id: Long)
case class V2(id: Int)
```

### 3. Document Breaking Changes

```scala
// Add comments explaining migration
case class UserV2(
  id: Int,
  name: String,
  // MIGRATION: email field added in V2, use None for V1 data
  email: Option[String]
)
```

### 4. Test Round-Trips for As

```scala
val as = As.derived[UserV1, UserV2]

// Test round-trip
val original = UserV1(1, "Alice")
val converted = as.into(original).getOrElse(???)
val restored = as.from(converted).getOrElse(???)
assert(restored == original)
```

### 5. Handle Errors Gracefully

```scala
val into = Into.derived[Long, Int]

into.into(value) match {
  case Right(intValue) => 
    // Success - use intValue
  case Left(error) =>
    // Handle error - log, use default, etc.
    logger.warn(s"Conversion failed: ${error.message}")
    // Use fallback value
}
```

---

## Common Pitfalls

### Pitfall 1: Using Defaults with As

```scala
// ❌ This won't compile with As
case class V1(name: String)
case class V2(name: String, count: Int = 0) // Default breaks round-trip

// val as = As.derived[V1, V2] // Compile error!

// ✅ Use Into instead, or make it optional
case class V2(name: String, count: Option[Int])
val as = As.derived[V1, V2] // Works!
```

### Pitfall 2: Narrowing in Both Directions

```scala
// ❌ This won't work with As
case class V1(value: Long)
case class V2(value: Int) // Narrowing

// val as = As.derived[V1, V2] // Compile error - narrowing in both directions

// ✅ Use Into for one-way migration, or widen both
case class V2(value: Long) // Widening works
val as = As.derived[V1, V2] // Works!
```

### Pitfall 3: Ambiguous Field Mapping

```scala
// ⚠️ May fail if types aren't unique
case class V1(a: Int, b: Int)
case class V2(x: Int, y: Int) // Both Int, mapping ambiguous

// Provide explicit instance
implicit val customInto: Into[V1, V2] = new Into[V1, V2] {
  def into(input: V1): Either[SchemaError, V2] =
    Right(V2(input.a, input.b))
}
```

### Pitfall 4: Forgetting Error Handling

```scala
// ❌ May throw exception
val value = into.intoOrThrow(data) // Throws if conversion fails

// ✅ Better - handle errors
into.into(data) match {
  case Right(value) => // Success
  case Left(error) => // Handle error
}
```

---

## Migration Checklist

When migrating between schema versions:

- [ ] Identify all field changes (added, removed, renamed, reordered)
- [ ] Check type compatibility (widening vs narrowing)
- [ ] Decide on `Into` vs `As` (one-way vs bidirectional)
- [ ] Handle new required fields (optional vs default vs custom)
- [ ] Test conversions with sample data
- [ ] Test error cases (invalid values, missing fields)
- [ ] If using `As`, test round-trips
- [ ] Document breaking changes
- [ ] Provide custom instances for complex cases
- [ ] Add error handling in production code

---

## Real-World Example

### Migrating a User Service

```scala
// Version 1 - Initial schema
case class UserV1(
  id: Int,
  username: String,
  email: String,
  createdAt: Long
)

// Version 2 - Enhanced schema
case class UserV2(
  userId: Long,        // Widened, renamed
  username: String,    // Same
  email: Option[String], // Made optional
  profile: Option[UserProfile], // New nested type
  createdAt: Long,     // Same
  lastLogin: Option[Long] // New optional field
)

case class UserProfile(
  firstName: String,
  lastName: String,
  age: Int
)

// Migration strategy
val into = Into.derived[UserV1, UserV2]

// Handle migration
def migrateUser(userV1: UserV1): Either[SchemaError, UserV2] = {
  into.into(userV1).map { userV2 =>
    // Additional business logic if needed
    userV2
  }
}

// Batch migration
def migrateUsers(users: List[UserV1]): Either[SchemaError, List[UserV2]] = {
  val intoList = Into.derived[List[UserV1], List[UserV2]]
  intoList.into(users)
}
```

---

## Additional Resources

- [Into Usage Guide](INTO_USAGE.md) - Detailed Into examples
- [As Usage Guide](AS_USAGE.md) - Bidirectional conversion guide
- [API Reference](API.md) - Complete API documentation
- [Test Suite](../../schema/shared/src/test/scala-3/zio/blocks/schema/IntoSpec.scala) - Comprehensive examples


# Best Practices - Into & As

## Overview

This guide provides best practices for using `Into[A, B]` and `As[A, B]` effectively in your projects.

## Choosing Between Into and As

### Use `Into` When:
- ✅ Converting from old schema to new (one-way migration)
- ✅ You need flexibility (defaults, narrowing, etc.)
- ✅ Data loss is acceptable
- ✅ You're migrating legacy data

### Use `As` When:
- ✅ You need bidirectional conversion
- ✅ Round-trip preservation is critical
- ✅ Both schemas are equally valid
- ✅ You're building a new system with multiple representations

## Schema Evolution Patterns

### ✅ Recommended: Add Optional Fields

```scala
// Version 1
case class UserV1(id: Int, name: String)

// Version 2 - Add optional field
case class UserV2(id: Int, name: String, email: Option[String])

// ✅ Works with both Into and As
val into = Into.derived[UserV1, UserV2]
val as = As.derived[UserV1, UserV2] // Also works!
```

### ✅ Recommended: Use Type Widening

```scala
// ✅ Good - lossless, automatic
case class V1(id: Int)
case class V2(id: Long)

// Works perfectly with both Into and As
```

### ⚠️ Use With Caution: Default Values

```scala
// ⚠️ Works with Into, but NOT with As
case class V1(name: String)
case class V2(name: String, count: Int = 0)

val into = Into.derived[V1, V2] // ✅ Works
// val as = As.derived[V1, V2] // ❌ Compile error!
```

### ❌ Avoid: Narrowing in Both Directions

```scala
// ❌ Won't work with As
case class V1(value: Long)
case class V2(value: Int)

// val as = As.derived[V1, V2] // ❌ Compile error!
// Use Into for one-way migration instead
```

## Performance Best Practices

### 1. Cache Derived Instances

```scala
// ❌ Slow - derives every time
def process(data: UserV1): UserV2 = {
  Into.derived[UserV1, UserV2].intoOrThrow(data)
}

// ✅ Fast - derive once
object Conversions {
  val userInto = Into.derived[UserV1, UserV2]
}

def process(data: UserV1): UserV2 = {
  Conversions.userInto.intoOrThrow(data)
}
```

### 2. Batch Conversions

```scala
// ✅ Fast - single derivation, processes all at once
val into = Into.derived[List[UserV1], List[UserV2]]
into.into(users)

// ⚠️ Slower - multiple derivations
users.map(u => Into.derived[UserV1, UserV2].intoOrThrow(u))
```

### 3. Prefer Widening

```scala
// ✅ Fast - no validation overhead
case class V1(id: Int)
case class V2(id: Long)

// ⚠️ Slower - validation overhead
case class V1(id: Long)
case class V2(id: Int) // Requires range checks
```

## Error Handling

### ✅ Always Handle Errors

```scala
// ✅ Good - explicit error handling
into.into(data) match {
  case Right(value) => 
    // Success path
  case Left(error) =>
    // Error handling - log, retry, use default, etc.
    logger.error(s"Conversion failed: ${error.message}")
    // Handle appropriately
}

// ⚠️ Use with caution - throws on error
try {
  val value = into.intoOrThrow(data)
  // Success path
} catch {
  case e: SchemaError =>
    // Error handling
}
```

### ✅ Provide Meaningful Context

```scala
// When providing custom instances, include context in errors
implicit val customInto: Into[String, Int] = new Into[String, Int] {
  def into(input: String): Either[SchemaError, Int] = 
    input.toIntOption.toRight(
      SchemaError.expectationMismatch(
        Nil,
        s"Cannot convert '$input' to Int in user ID conversion"
      )
    )
}
```

## Type Safety

### ✅ Leverage Compile-Time Safety

```scala
// ✅ Compile-time checked
val into = Into.derived[UserV1, UserV2]
// If types are incompatible, this won't compile

// ❌ Runtime errors
val into = Into.derived[UserV1, UserV2]
// If this compiles, conversion is type-safe
```

### ✅ Use Type Aliases for Clarity

```scala
// ✅ Clear intent
type UserId = Int
type UserIdV2 = Long
val into = Into.derived[UserId, UserIdV2]

// ⚠️ Less clear
val into = Into.derived[Int, Long]
```

## Testing

### ✅ Test Round-Trips for As

```scala
test("round-trip conversion") {
  val as = As.derived[UserV1, UserV2]
  val original = UserV1(1, "Alice")
  
  val roundTrip = for {
    v2 <- as.into(original)
    back <- as.from(v2)
  } yield back
  
  assertTrue(roundTrip == Right(original))
}
```

### ✅ Test Error Cases

```scala
test("handles invalid data") {
  val into = Into.derived[Long, Int]
  
  assertTrue(
    into.into(Long.MaxValue).isLeft,
    into.into(42L) == Right(42)
  )
}
```

### ✅ Test Edge Cases

```scala
test("handles empty collections") {
  val into = Into.derived[List[Int], Vector[Long]]
  assertTrue(into.into(Nil) == Right(Vector.empty))
}

test("handles None values") {
  val into = Into.derived[Option[Int], Option[Long]]
  assertTrue(into.into(None) == Right(None))
}
```

## Code Organization

### ✅ Group Related Conversions

```scala
object UserConversions {
  val v1ToV2 = Into.derived[UserV1, UserV2]
  val v2ToV3 = Into.derived[UserV2, UserV3]
  val v1ToV3 = Into.derived[UserV1, UserV3]
}
```

### ✅ Use Companion Objects

```scala
case class UserV1(id: Int, name: String)
object UserV1 {
  implicit val toV2: Into[UserV1, UserV2] = Into.derived
}
```

## Migration Strategies

### ✅ Gradual Migration

```scala
// Support both versions during migration
def processUser(user: Either[UserV1, UserV2]): UserV3 = {
  val v2 = user.fold(
    v1 => Into.derived[UserV1, UserV2].intoOrThrow(v1),
    identity
  )
  Into.derived[UserV2, UserV3].intoOrThrow(v2)
}
```

### ✅ Version Detection

```scala
// Detect version and convert accordingly
def normalizeUser(user: Any): UserV2 = user match {
  case v1: UserV1 => Into.derived[UserV1, UserV2].intoOrThrow(v1)
  case v2: UserV2 => v2
  case _ => throw new IllegalArgumentException("Unknown user version")
}
```

## Common Pitfalls to Avoid

### ❌ Don't Derive in Hot Loops

```scala
// ❌ Very slow
users.foreach { user =>
  val into = Into.derived[UserV1, UserV2] // Derives every iteration!
  process(into.intoOrThrow(user))
}

// ✅ Fast
val into = Into.derived[UserV1, UserV2]
users.foreach { user =>
  process(into.intoOrThrow(user))
}
```

### ❌ Don't Ignore Errors

```scala
// ❌ Dangerous - silently fails
val result = into.into(data).getOrElse(defaultValue)

// ✅ Better - explicit handling
into.into(data) match {
  case Right(value) => use(value)
  case Left(error) => 
    logger.warn(s"Conversion failed: ${error.message}")
    useFallback()
}
```

### ❌ Don't Use Defaults with As

```scala
// ❌ Won't compile
case class V1(name: String)
case class V2(name: String, count: Int = 0)
// val as = As.derived[V1, V2] // Error!

// ✅ Use optional instead
case class V2(name: String, count: Option[Int])
val as = As.derived[V1, V2] // Works!
```

## Advanced Patterns

### Custom Instances for Complex Cases

```scala
// For cases where automatic derivation doesn't work
implicit val complexInto: Into[LegacyFormat, NewFormat] = 
  new Into[LegacyFormat, NewFormat] {
    def into(input: LegacyFormat): Either[SchemaError, NewFormat] = {
      // Custom conversion logic
      // Can include business rules, validation, etc.
    }
  }
```

### Composition of Conversions

```scala
// Chain conversions through intermediate versions
val v1ToV2 = Into.derived[V1, V2]
val v2ToV3 = Into.derived[V2, V3]

def v1ToV3(v1: V1): Either[SchemaError, V3] = {
  for {
    v2 <- v1ToV2.into(v1)
    v3 <- v2ToV3.into(v2)
  } yield v3
}
```

## Summary

1. **Choose the right type class** - `Into` for one-way, `As` for bidirectional
2. **Cache instances** - Most important performance optimization
3. **Handle errors** - Always check `Either` results
4. **Test thoroughly** - Especially round-trips and error cases
5. **Use optional fields** - For schema evolution
6. **Prefer widening** - Over narrowing when possible
7. **Document custom instances** - Explain why they're needed
8. **Profile before optimizing** - Measure first, optimize second





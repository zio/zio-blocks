# ZIO Blocks Schema - Into & As API Reference

## Type Classes

### `Into[A, B]`

Type class for one-way type-safe conversions from `A` to `B` with runtime validation.

#### Methods

```scala
trait Into[A, B] {
  def into(input: A): Either[SchemaError, B]
  def intoOrThrow(input: A): B
}
```

- **`into(input: A): Either[SchemaError, B]`**
  - Converts a value of type `A` to type `B`
  - Returns `Right(b)` on success, `Left(error)` on failure

- **`intoOrThrow(input: A): B`**
  - Converts a value, throwing `SchemaError` on failure
  - Convenience method for when you want exceptions instead of `Either`

#### Companion Object

```scala
object Into {
  def apply[A, B](using into: Into[A, B]): Into[A, B]
  inline given derived[A, B]: Into[A, B]
  given identity[A]: Into[A, A]
}
```

- **`apply[A, B]`** - Summon an `Into[A, B]` instance
- **`derived[A, B]`** - Automatically derive an `Into[A, B]` instance
- **`identity[A]`** - Identity conversion (A → A)

---

### `As[A, B]`

Type class for bidirectional type-safe conversions between `A` and `B`.

#### Methods

```scala
trait As[A, B] {
  def into(input: A): Either[SchemaError, B]
  def from(input: B): Either[SchemaError, A]
  def intoOrThrow(input: A): B
  def fromOrThrow(input: B): A
  def asInto: Into[A, B]
  def asIntoReverse: Into[B, A]
}
```

- **`into(input: A): Either[SchemaError, B]`** - Convert A to B
- **`from(input: B): Either[SchemaError, A]`** - Convert B to A
- **`intoOrThrow(input: A): B`** - Convert A to B, throwing on failure
- **`fromOrThrow(input: B): A`** - Convert B to A, throwing on failure
- **`asInto: Into[A, B]`** - Get `Into[A, B]` instance
- **`asIntoReverse: Into[B, A]`** - Get `Into[B, A]` instance

#### Companion Object

```scala
object As {
  def apply[A, B](using as: As[A, B]): As[A, B]
  inline given derived[A, B]: As[A, B]
}
```

- **`apply[A, B]`** - Summon an `As[A, B]` instance
- **`derived[A, B]`** - Automatically derive an `As[A, B]` instance

---

## Error Types

### `SchemaError`

Represents a conversion error with detailed information.

```scala
final case class SchemaError(errors: ::[SchemaError.Single]) extends Exception
```

#### Factory Methods

- **`SchemaError.expectationMismatch(trace, expectation)`** - Type mismatch error
- **`SchemaError.missingField(trace, fieldName)`** - Missing required field
- **`SchemaError.duplicatedField(trace, fieldName)`** - Duplicate field error
- **`SchemaError.unknownCase(trace, caseName)`** - Unknown coproduct case

---

## Supported Conversions

### Numeric Types

- **Widening:** Byte → Short → Int → Long, Float → Double
- **Narrowing:** Long → Int/Short/Byte, Int → Short/Byte, Short → Byte, Double → Float
  - Narrowing includes overflow/range validation

### Product Types

- Case classes
- Tuples
- Field mapping by name, type, or position
- Schema evolution (add/remove fields, reorder, rename)

### Coproduct Types

- Sealed traits
- Enums (Scala 3)
- Case matching by name or constructor signature

### Collections

- List, Vector, Set, Seq
- Option, Either, Map
- Array (Scala 2 & 3)
- Element type coercion
- Collection type conversion

### Special Types

- **Opaque types** (Scala 3) - with validation support
- **ZIO Prelude newtypes** (Scala 3) - Newtype and Subtype (temporarily disabled due to API compatibility)
- **Structural types** (Scala 3) - Selectable support

---

## Platform Support

| Feature | Scala 2.13 | Scala 3 |
|---------|-----------|---------|
| Core type classes | ✅ | ✅ |
| Numeric coercions | ✅ | ✅ |
| Product types | ✅ | ✅ |
| Coproduct types | ✅ | ✅ |
| Collections | ✅ | ✅ |
| Opaque types | ❌ | ✅ |
| Structural types | ✅ | ✅ |
| ZIO Prelude newtypes | ❌ | ⚠️ |

### ZIO Prelude Newtypes Note

**Scala 2 Support:** Not implemented due to macro system limitations. Manual `Into` instances can be provided as a workaround.

**Scala 3 Support:** Implementation exists (`NewtypeMacros`) but is temporarily disabled in tests due to API compatibility issues with the current version of ZIO Prelude. The macro implementation supports `make`, `apply`, `validate`, and other ZIO Prelude newtype methods.

### Structural Types Note

**Scala 2 Support:** Structural types are now fully supported in Scala 2 using runtime reflection. Both directions are supported:
- ✅ Case class → Structural type (with validation)
- ✅ Structural type → Case class (with field extraction)

**Implementation Details:**
- Scala 2 uses `RefinedType` to represent structural types
- Runtime reflection is used for field extraction and validation
- Compile-time validation ensures required methods exist
- Performance is slightly slower than Scala 3 due to reflection overhead

**Scala 3 Support:** Uses `Selectable` for optimal performance with compile-time guarantees.

---

## Examples

### Basic Usage

```scala
import zio.blocks.schema.Into

case class UserV1(name: String, age: Int)
case class UserV2(name: String, age: Long)

// Automatic derivation
val into = Into.derived[UserV1, UserV2]
val userV1 = UserV1("Alice", 30)
val result = into.into(userV1) // Right(UserV2("Alice", 30L))
```

### Error Handling

```scala
val into = Into.derived[Long, Int]

// Using Either
into.into(Int.MaxValue.toLong) match {
  case Right(value) => println(s"Converted: $value")
  case Left(error) => println(s"Error: ${error.message}")
}

// Using intoOrThrow
try {
  val value = into.intoOrThrow(42L)
} catch {
  case e: SchemaError => println(s"Failed: ${e.message}")
}
```

### Bidirectional Conversion

```scala
import zio.blocks.schema.As

val as = As.derived[UserV1, UserV2]

// Forward
val v2 = as.into(userV1) // Right(UserV2(...))

// Backward
val v1 = as.from(v2) // Right(UserV1(...))
```

## Additional Resources

See:
- [Into Usage Guide](INTO_USAGE.md) - Comprehensive usage examples
- [As Usage Guide](AS_USAGE.md) - Bidirectional conversion guide
- [Migration Guide](MIGRATION_GUIDE.md) - Schema evolution patterns
- [Advanced Examples](ADVANCED_EXAMPLES.md) - Complex conversion scenarios
- [Test Suite](../../schema/shared/src/test/scala-3/zio/blocks/schema/IntoSpec.scala) - Comprehensive test examples


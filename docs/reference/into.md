---
id: into
title: "Into"
---

`Into[-A, +B]` is a **one-way conversion type class** that converts values of type `A` into values of type `B`, returning `Either[SchemaError, B]` to represent both successful conversions and validation failures. The fundamental operation is `into`, which performs the conversion at runtime.

`Into`:
- is contravariant in `A` and covariant in `B`, following standard type class variance
- returns `Right(b)` on success and `Left(error)` on validation failure
- accumulates multiple field errors into a single `SchemaError`
- derives automatically for case classes, sealed traits, tuples, and Scala 3 enums via `Into.derived`

```scala
trait Into[-A, +B] {
  def into(a: A): Either[SchemaError, B]
}
```

The variance and data flow can be visualised as:

```
  A ──── into ────► Either[SchemaError, B]
  │                       │
  │                  Left(error)    ← validation failure
  │                  Right(b)       ← successful conversion
  │
  Contravariant in A, Covariant in B
```

## Motivation

`Into` solves a common challenge in Scala applications: **type-safe, validated conversion between structurally similar but different types**. This arises in:

- **Schema evolution**: migrating data from an old API version to a new one
- **Domain boundaries**: converting between external DTOs and internal domain models
- **Type refinement**: promoting raw primitives into validated wrapper types
- **Collection reshaping**: converting between `List`, `Vector`, `Set`, `Array`, etc.

Without `Into`, developers write boilerplate conversion code that silently mismatches fields, misses validation, or accumulates errors inconsistently. `Into.derived` generates all of this automatically at compile time.

```scala mdoc:silent:nest
import zio.blocks.schema.Into

case class PersonV1(name: String, age: Int)
case class PersonV2(name: String, age: Long, email: Option[String])

val migrate = Into.derived[PersonV1, PersonV2]
```

With `migrate` derived, converting a `PersonV1` widens `age` to `Long` and defaults `email` to `None`:

```scala mdoc
migrate.into(PersonV1("Alice", 30))
```

Compare this to a manual implementation:

| Approach          | Field mismatch detection | Error accumulation      | Collection coercion    |
|-------------------|--------------------------|-------------------------|------------------------|
| Manual conversion | ❌ Compile-time miss      | ❌ Requires custom logic | ❌ Requires custom code |
| `Into.derived`    | ✅ Compile-time check     | ✅ Automatic             | ✅ Automatic            |

## Installation

`Into` is part of the `zio-blocks-schema` module:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"
```

For Scala.js:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-schema" % "@VERSION@"
```

Supported Scala versions: 2.13.x and 3.x.

## Creating Instances

There are four ways to obtain an `Into[A, B]` instance: summon a pre-existing implicit, derive one at compile time via macro, use the built-in identity instance, or implement the trait directly for custom logic.

### `Into.apply` — Summoning

Summons an implicit `Into[A, B]` instance from the implicit scope. This is the standard way to access a pre-existing instance:

```scala
object Into {
  def apply[A, B](implicit ev: Into[A, B]): Into[A, B]
}
```

We summon the pre-existing `Into[Int, Long]` widening instance and call `into` on it:

```scala mdoc:silent:nest
import zio.blocks.schema.Into

val intToLong: Into[Int, Long] = Into[Int, Long]
```

With `intToLong` in scope, `into` converts the value and returns a `Right`:

```scala mdoc
intToLong.into(42)
```

### `Into.derived` — Macro Derivation

Generates an `Into[A, B]` instance at compile time using a macro. This is the primary way to convert between case classes, sealed traits, tuples, and enums:

```scala
object Into {
  def derived[A, B]: Into[A, B] // macro
}
```

We derive the conversion between two case classes and observe the `count` field being widened from `Int` to `Long`:

```scala mdoc:silent:nest
import zio.blocks.schema.Into

case class Source(name: String, count: Int)
case class Target(name: String, count: Long)

val conv = Into.derived[Source, Target]
```

The derived `conv` maps each field by name, coercing types where needed:

```scala mdoc
conv.into(Source("events", 100))
```

### `Into.identity` — Identity Conversion

A pre-provided implicit `Into[A, A]` that always succeeds. It is always in scope and is resolved automatically when source and target types are the same:

```scala
object Into {
  implicit def identity[A]: Into[A, A]
}
```

Any `Into[A, A]` resolves to this built-in — there is nothing to configure:

```scala mdoc:silent:nest
import zio.blocks.schema.Into

val same: Into[String, String] = Into[String, String]
```

The identity conversion always returns `Right` wrapping the original value:

```scala mdoc
same.into("hello")
```

### Custom Instances

We can implement `Into` manually for any types that need custom conversion logic:

```scala mdoc:silent:nest
import zio.blocks.schema.Into

case class Celsius(value: Double)
case class Fahrenheit(value: Double)

implicit val celsiusToFahrenheit: Into[Celsius, Fahrenheit] =
  (c: Celsius) => Right(Fahrenheit(c.value * 9.0 / 5.0 + 32.0))
```

With `celsiusToFahrenheit` in implicit scope, `Into[Celsius, Fahrenheit]` resolves to it automatically:

```scala mdoc
Into[Celsius, Fahrenheit].into(Celsius(100.0))
```

## Predefined Instances

ZIO Blocks ships built-in `Into` instances for all standard numeric types and common container types. These are resolved automatically from implicit scope — no import or explicit call is needed.

### Numeric Widening (Lossless)

These instances always succeed because the conversion cannot lose information:

| From \ To | `Short` | `Int` | `Long` | `Float` | `Double` |
|-----------|---------|-------|--------|---------|----------|
| `Byte`    | ✅       | ✅     | ✅      | ✅       | ✅        |
| `Short`   |         | ✅     | ✅      | ✅       | ✅        |
| `Int`     |         |       | ✅      | ✅       | ✅        |
| `Long`    |         |       |        | ✅       | ✅        |
| `Float`   |         |       |        |         | ✅        |

Each widening conversion always returns `Right` since no information is lost:
```scala mdoc:invisible
import zio.blocks.schema.Into
```
```scala mdoc
Into[Byte, Int].into(42.toByte)
Into[Int, Long].into(100)
Into[Float, Double].into(3.14f)
```

### Numeric Narrowing (With Validation)

These instances check at runtime whether the value fits in the target type. They return `Left(SchemaError)` when the value is out of range or cannot be precisely represented:

| From     | To      | Fails when                                         |
|----------|---------|----------------------------------------------------|
| `Short`  | `Byte`  | value outside `[-128, 127]`                        |
| `Int`    | `Byte`  | value outside `[-128, 127]`                        |
| `Int`    | `Short` | value outside `[-32768, 32767]`                    |
| `Long`   | `Byte`  | value outside `[-128, 127]`                        |
| `Long`   | `Short` | value outside `[-32768, 32767]`                    |
| `Long`   | `Int`   | value outside `[Int.MinValue, Int.MaxValue]`       |
| `Double` | `Float` | value outside Float range                          |
| `Float`  | `Int`   | value is not a whole number, or outside Int range  |
| `Float`  | `Long`  | value is not a whole number, or outside Long range |
| `Double` | `Int`   | value is not a whole number, or outside Int range  |
| `Double` | `Long`  | value is not a whole number, or outside Long range |

A value within range returns `Right`; an overflow or fractional value returns `Left`:
```scala mdoc
Into[Long, Int].into(42L)
Into[Long, Int].into(Long.MaxValue)
Into[Double, Int].into(3.14)
```

### Container Instances

`Into` composes through standard container types automatically:

#### `Option`

```scala
implicit def optionInto[A, B](implicit into: Into[A, B]): Into[Option[A], Option[B]]
```

Both `Some` and `None` are handled — `None` passes through unchanged:

```scala mdoc
Into[Option[Int], Option[Long]].into(Some(42))
Into[Option[Int], Option[Long]].into(None)
```

#### `Either`

```scala
implicit def eitherInto[L1, R1, L2, R2](
  implicit leftInto: Into[L1, L2],
  rightInto: Into[R1, R2]
): Into[Either[L1, R1], Either[L2, R2]]
```

Both `Left` and `Right` branches are coerced independently:

```scala mdoc
Into[Either[Int, Int], Either[Long, Long]].into(Right(1))
Into[Either[Int, Int], Either[Long, Long]].into(Left(2))
```

#### `Map`

```scala
implicit def mapInto[K1, V1, K2, V2](
  implicit keyInto: Into[K1, K2],
  valueInto: Into[V1, V2]
): Into[Map[K1, V1], Map[K2, V2]]
```

Both keys and values are coerced element-by-element:

```scala mdoc
Into[Map[String, Int], Map[String, Long]].into(Map("a" -> 1, "b" -> 2))
```

#### Iterables and Arrays

```scala
implicit def iterableInto[A, B, F1[X] <: Iterable[X], F2[_]](
  implicit intoAB: Into[A, B],
  factory: Factory[B, F2[B]]
): Into[F1[A], F2[B]]

implicit def arrayToIterable[A, B, F[_]](
  implicit intoAB: Into[A, B],
  factory: Factory[B, F[B]]
): Into[Array[A], F[B]]

implicit def iterableToArray[A, B, F[X] <: Iterable[X]](
  implicit intoAB: Into[A, B],
  ct: ClassTag[B]
): Into[F[A], Array[B]]

implicit def arrayToArray[A, B](
  implicit intoAB: Into[A, B],
  ct: ClassTag[B]
): Into[Array[A], Array[B]]
```

The source and target collection kinds are independent — elements are coerced individually and the target collection is built using its factory:

```scala mdoc
Into[List[Int], Vector[Long]].into(List(1, 2, 3))
Into[List[Int], Set[Long]].into(List(1, 2, 2, 3))
```

:::note
Converting to `Set` removes duplicates. Converting from `Set` does not guarantee any particular element order.
:::

## Core Operation

`Into` exposes a single abstract method, `into`. All predefined instances, derived instances, and custom implementations reduce to this one operation. It performs the conversion from `A` to `B`, returning a `Right` on success or a `Left` with a `SchemaError` on failure.

```scala
trait Into[-A, +B] {
  def into(a: A): Either[SchemaError, B]
}
```

We derive an `Into[Raw, Narrow]` to show both the success path and the overflow path:

```scala mdoc:silent:nest
import zio.blocks.schema.Into

case class Raw(value: Long)
case class Narrow(value: Int)

val conv = Into.derived[Raw, Narrow]
```

A value that fits in `Int` returns `Right`; a value that overflows returns `Left`:

```scala mdoc
conv.into(Raw(42L))
conv.into(Raw(Long.MaxValue))
```

## Macro Derivation Rules

`Into.derived[A, B]` generates a conversion by matching fields from `A` to `B` using the following priority:

1. **Exact match**: same field name and same type
2. **Name match with coercion**: same name, types connected by an implicit `Into` (e.g. `Int` → `Long`)
3. **Unique type match**: the type appears exactly once in both `A` and `B`
4. **Position + type match**: fields in the same position with matching types

### Products (Case Classes and Tuples)

Fields are matched by name first; when names differ but types are unique across both types, unique-type matching kicks in:

```scala mdoc:silent:nest
import zio.blocks.schema.Into

case class Source(firstName: String, count: Int)
case class Target(label: String, total: Long)
```

Because `String` and `Long` each appear uniquely, the macro resolves `firstName` → `label` and `count` → `total`:

```scala mdoc
Into.derived[Source, Target].into(Source("events", 5))
```

Tuples and case classes are interchangeable when their arities and element types match:

```scala mdoc:silent:nest
import zio.blocks.schema.Into

case class Point(x: Int, y: Int)
```

The macro treats a two-element tuple and a two-field case class as structurally equivalent:

```scala mdoc
Into.derived[(Int, Int), Point].into((3, 4))
Into.derived[Point, (Int, Int)].into(Point(3, 4))
```

Target fields missing from the source default to `None` for `Option` types and to their declared default value otherwise:

```scala mdoc:silent:nest
import zio.blocks.schema.Into

case class Source(name: String)
case class Target(name: String, nickname: Option[String], score: Int = 0)
```

Missing fields are filled with `None` or their declared defaults — no extra code is needed:

```scala mdoc
Into.derived[Source, Target].into(Source("Alice"))
```

### Coproducts (Sealed Traits and Enums)

Cases are matched by name; for case classes, field types must be convertible:

```scala mdoc:silent:nest
import zio.blocks.schema.Into

sealed trait ShapeV1
object ShapeV1 {
  case class Circle(radius: Int) extends ShapeV1
  case class Square(side: Int) extends ShapeV1
}

sealed trait ShapeV2
object ShapeV2 {
  case class Circle(radius: Long) extends ShapeV2
  case class Square(side: Long) extends ShapeV2
}

val conv = Into.derived[ShapeV1, ShapeV2]
```

Each case is matched by name and its fields are coerced from `Int` to `Long`:

```scala mdoc
conv.into(ShapeV1.Circle(5))
conv.into(ShapeV1.Square(3))
```

### ZIO Prelude Newtypes

`Into.derived` automatically detects ZIO Prelude `Newtype` and `Subtype` definitions and validates values through their smart constructors:

```scala mdoc:silent:nest
import zio.blocks.schema.Into
import zio.prelude._

object Age extends Subtype[Int] {
  override def assertion: zio.prelude.Assertion[Int] =
    zio.prelude.Assertion.between(0, 150)
}
type Age = Age.Type

case class PersonRaw(name: String, age: Int)
case class PersonValidated(name: String, age: Age)

val validate = Into.derived[PersonRaw, PersonValidated]
```

Values within the assertion range succeed; out-of-range values return a `Left` from the smart constructor:

```scala mdoc
validate.into(PersonRaw("Alice", 30))
validate.into(PersonRaw("Bob", 200))
```

### Scala 3 Opaque Types

In Scala 3, `Into.derived` detects opaque types with companion `apply` or `unsafe` methods:

```scala mdoc:silent:nest
import zio.blocks.schema.Into

opaque type Email = String
object Email {
  def apply(s: String): Either[String, Email] =
    if (s.contains("@")) Right(s) else Left(s"Invalid email: $s")
  def unsafe(s: String): Email = s
}

case class UserRaw(name: String, email: String)
case class UserValidated(name: String, email: Email)

val validate = Into.derived[UserRaw, UserValidated]
```

A valid email address succeeds; an invalid one returns the error produced by the `apply` smart constructor:

```scala mdoc
validate.into(UserRaw("Alice", "alice@example.com"))
validate.into(UserRaw("Alice", "not-an-email"))
```

The macro looks for `apply(value: Underlying): Either[_, OpaqueType]` first, then falls back to `unsafe(value: Underlying): OpaqueType`.

### Structural Types (JVM Only)

On JVM, `Into.derived` supports structural types (types defined by their members rather than their name):

```scala
// JVM ONLY — structural types require reflection
import scala.language.reflectiveCalls

case class Person(name: String, age: Int)

val into = Into.derived[{ def name: String; def age: Int }, Person]
```

:::warning
Structural type conversions are not available on Scala.js or Scala Native. `Into.derived` fails at compile time with a descriptive error message on non-JVM platforms:

```

Cannot derive Into[..., Person]: Structural type conversions are not supported on JS.

```

For cross-platform code, use case classes or tuples instead of structural types.
:::

## Error Handling

All `Into` conversions return `Either[SchemaError, B]`. `SchemaError` carries:

- A human-readable message via `.message` / `.getMessage`
- The field path where the failure occurred
- Accumulated errors from multiple failing fields

```scala mdoc:silent:nest
import zio.blocks.schema.Into

case class Source(a: Long, b: Long, c: Long)
case class Target(a: Int,  b: Int,  c: Int)

val conv    = Into.derived[Source, Target]
val result  = conv.into(Source(Long.MaxValue, Long.MinValue, 42L))
```

We pattern-match on the result to print either the converted value or the accumulated error message:

```scala mdoc
result match {
  case Right(t)    => println(s"OK: $t")
  case Left(error) => println(s"Failed:\n${error.message}")
}
```

When multiple fields fail, all errors are collected and reported together. The field `c` above succeeds (42 fits in `Int`), so only errors for `a` and `b` appear.

```scala mdoc:silent:nest
import zio.blocks.schema.Into

case class UserRaw(id: Long, email: String, age: Long)

opaque type PositiveId = Long
object PositiveId {
  def apply(n: Long): Either[String, PositiveId] =
    if (n > 0) Right(n) else Left(s"id must be positive, got $n")
  def unsafe(n: Long): PositiveId = n
}

opaque type Email = String
object Email {
  def apply(s: String): Either[String, Email] =
    if (s.contains("@")) Right(s) else Left(s"Invalid email: $s")
  def unsafe(s: String): Email = s
}

case class UserValidated(id: PositiveId, email: Email, age: Int)

val conv = Into.derived[UserRaw, UserValidated]
val err  = conv.into(UserRaw(-1L, "not-an-email", 200L)).swap.toOption.get
```

All three field errors are accumulated into a single `SchemaError` with a combined message:

```scala mdoc
println(err.message)
```

## Related Type: `As[A, B]`

`As[A, B]` extends `Into[A, B]` with a reverse direction `from(b: B): Either[SchemaError, A]`. Use `As` when you need bidirectional, round-trip safe conversions:

```scala
trait As[A, B] extends Into[A, B] {
  def from(b: B): Either[SchemaError, A]
  def reverse: As[B, A]
}
```

We derive an `As` for two case classes and obtain its reverse using `as.reverse`:

```scala mdoc:silent:nest
import zio.blocks.schema.As

case class IntBox(value: Int)
case class LongBox(value: Long)

val as  = As.derived[IntBox, LongBox]
val rev = as.reverse
```

`into` converts forward, `from` converts backward, and `reverse` gives a flipped `As[LongBox, IntBox]`:

```scala mdoc
as.into(IntBox(42))
as.from(LongBox(99L))
rev.into(LongBox(5L))
```

Since `As[A, B]` extends `Into[A, B]`, any `As` instance can be used where an `Into` is expected:

```scala mdoc:silent:nest
import zio.blocks.schema.{Into, As, SchemaError}

def migrate[A, B](data: A)(implicit into: Into[A, B]): Either[SchemaError, B] =
  into.into(data)

case class P2D(x: Int, y: Int)
case class Coord(x: Int, y: Int)

implicit val as: As[P2D, Coord] = As.derived[P2D, Coord]
```

Passing `as` where an `Into` is expected works without any cast since `As` is a subtype of `Into`:

```scala mdoc
migrate(P2D(1, 2))
```

See [Schema Evolution](./schema-evolution.md) for a detailed guide on using `Into` and `As` together for schema migration scenarios.

## Scala 2 vs Scala 3 Differences

| Feature | Scala 2 | Scala 3 |
|---------|---------|---------|
| Derivation syntax | `Into.derived[A, B]` | `Into.derived[A, B]` |
| Enum support | Sealed traits only | Scala 3 enums + sealed traits |
| Opaque types | N/A | ✅ Supported |
| Structural types | JVM only (reflection) | JVM only (reflection) |
| ZIO Prelude newtypes | ✅ | ✅ |

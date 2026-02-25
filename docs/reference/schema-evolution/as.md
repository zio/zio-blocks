---
id: as
title: "As"
---

`As[A, B]` is a **bidirectional conversion type class** that extends `Into[A, B]` with a reverse direction. In addition to converting `A → B` via `into`, it also converts `B → A` via `from`, providing a round-trip guarantee.

`As`:
- extends `Into[A, B]`, so every `As` can be used wherever an `Into` is expected
- returns `Right(b)` or `Right(a)` on success and `Left(error)` on validation failure in both directions
- derives automatically for case classes, sealed traits, tuples, and Scala 3 enums via `As.derived`
- enforces stricter derivation constraints than `Into` to guarantee that `A → B → A` always restores the original value

```scala
trait As[A, B] extends Into[A, B] {
  def from(input: B): Either[SchemaError, A]
  def reverse: As[B, A]
}
```

The bidirectional data flow looks like this:

```
  ┌──────────────────────────────────────────────────┐
  │               As[A, B]                           │
  │                                                  │
  │     into(a: A) ──────────────────────► B         │
  │                                                  │
  │     from(b: B) ◄────────────────────── B         │
  │                                                  │
  │     reverse: As[B, A]  (flips directions)        │
  └──────────────────────────────────────────────────┘
```

`As` is the right choice when the conversion must be safe to run in both directions — for example when synchronising data between a local model and a remote representation, or when migrating a database schema that must remain rollback-capable.

## Installation

`As` is part of `zio-blocks-schema`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"
```

For Scala.js and Scala Native, use `%%%`:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-schema" % "@VERSION@"
```

Supported Scala versions: 2.13.x and 3.x.

## Creating Instances

There are three ways to obtain an `As[A, B]`: construct it from a pair of `Into` instances, derive it automatically with the macro, or summon an implicit already in scope.

### `As.apply` — Manual Construction

`As.apply(intoAB, intoBA)` composes two `Into` instances into one `As`:

```scala
object As {
  def apply[A, B](intoAB: Into[A, B], intoBA: Into[B, A]): As[A, B]
}
```

We build an `As[Int, Long]` by supplying both directions explicitly:

```scala mdoc:silent
import zio.blocks.schema.{As, Into, SchemaError}

val intoAB: Into[Int, Long] = a => Right(a.toLong)
val intoBA: Into[Long, Int] = b =>
  if (b >= Int.MinValue && b <= Int.MaxValue) Right(b.toInt)
  else Left(SchemaError.validationFailed("overflow"))

val manualAs: As[Int, Long] = As(intoAB, intoBA)
```

With `manualAs` in scope we can convert in both directions and verify overflow detection:

```scala mdoc
manualAs.into(42)
manualAs.from(100L)
manualAs.from(Long.MaxValue)
```

### `As.derived` — Macro Derivation

`As.derived[A, B]` generates an `As[A, B]` at compile time by deriving both `Into[A, B]` and `Into[B, A]` and running bidirectional compatibility checks:

```scala
object As {
  def derived[A, B]: As[A, B]
}
```

The macro works with case classes, sealed traits, Scala 3 enums, tuples, ZIO Prelude newtypes, Scala 3 opaque types, and structural types (JVM only). We derive an `As` for two case classes with matching fields:

```scala mdoc:silent
import zio.blocks.schema.As

case class PersonA(name: String, age: Int)
case class PersonB(name: String, age: Long)

val personAs: As[PersonA, PersonB] = As.derived[PersonA, PersonB]
```

Both `into` and `from` are now available, and we can verify that `A → B → A` restores the original value:

```scala mdoc
personAs.into(PersonA("Alice", 30))
personAs.from(PersonB("Bob", 25L))
personAs.into(PersonA("Alice", 30)).flatMap(personAs.from)
```

### `As.apply[A, B]` — Summoning

`As.apply[A, B]` (with no arguments) summons an implicit `As[A, B]` already in scope — the same pattern used by `Into.apply`:

```scala
object As {
  def apply[A, B](implicit ev: As[A, B]): As[A, B]
}
```

This is useful when you want to retrieve a type-class instance by type rather than by variable name:

```scala mdoc:compile-only
import zio.blocks.schema.As

case class Foo(x: Int)
case class Bar(x: Int)

implicit val fooBarAs: As[Foo, Bar] = As.derived[Foo, Bar]

// Summon the implicit instance
val summoned = As[Foo, Bar]
summoned.into(Foo(1))
summoned.from(Bar(2))
```

## Core Operations

`As` exposes three operations: `into`, `from`, and `reverse`.

### `into` — Forward Conversion

`into` is inherited from `Into[A, B]` and converts an `A` into `Either[SchemaError, B]`:

```scala
trait As[A, B] extends Into[A, B] {
  def into(a: A): Either[SchemaError, B]
}
```

### `from` — Reverse Conversion

`from` is the operation that distinguishes `As` from `Into`. It converts a `B` back to `Either[SchemaError, A]`:

```scala
trait As[A, B] {
  def from(b: B): Either[SchemaError, A]
}
```

We define two simple wrapper types and derive an `As` between them to show both directions:

```scala mdoc:silent
import zio.blocks.schema.As

case class IntBox(value: Int)
case class LongBox(value: Long)

val boxAs: As[IntBox, LongBox] = As.derived[IntBox, LongBox]
```

`into` widens the value while `from` narrows it, validating that the result fits in the target type:

```scala mdoc
boxAs.into(IntBox(42))
boxAs.from(LongBox(99L))
boxAs.from(LongBox(Long.MaxValue))
```

### `reverse` — Flipping Directions

`reverse` returns an `As[B, A]` whose `into` and `from` are swapped:

```scala
trait As[A, B] {
  def reverse: As[B, A]
}
```

`reverse` creates a new `As` without touching the original:

```scala mdoc
val revAs: As[LongBox, IntBox] = boxAs.reverse

revAs.into(LongBox(5L))
revAs.from(IntBox(10))
```

## Using `As` as `Into`

Because `As[A, B]` extends `Into[A, B]`, any `As` instance can be passed wherever an `Into` is expected — with no casts or wrapping needed.

We write a generic migration helper that requires only an `Into`, then pass an `As` directly:

```scala mdoc:silent
import zio.blocks.schema.{Into, As, SchemaError}

case class P2D(x: Int, y: Int)
case class Coord(x: Int, y: Int)

def migrate[A, B](data: A)(implicit into: Into[A, B]): Either[SchemaError, B] =
  into.into(data)

implicit val pointAs: As[P2D, Coord] = As.derived[P2D, Coord]
```

Passing `pointAs` where the function expects `Into[P2D, Coord]` works because `As` is a subtype of `Into`:

```scala mdoc
migrate(P2D(1, 2))
```

## `reverseInto` Implicit

`AsLowPriorityImplicits` provides a `reverseInto` implicit that materialises an `Into[B, A]` from any `As[A, B]` in scope. This lets libraries that only require `Into` automatically benefit from `As` instances without any extra wiring:

```scala
trait AsLowPriorityImplicits {
  implicit def reverseInto[A, B](implicit as: As[A, B]): Into[B, A]
}
```

With an `As[String, Int]` in scope, `reverseInto` synthesises `Into[Int, String]` automatically:

```scala mdoc:silent
import zio.blocks.schema.{As, Into, SchemaError}

implicit val stringIntAs: As[String, Int] = new As[String, Int] {
  def into(s: String): Either[SchemaError, Int] =
    try Right(s.toInt)
    catch { case _: NumberFormatException => Left(SchemaError.validationFailed("not an int")) }
  def from(n: Int): Either[SchemaError, String] = Right(n.toString)
}
```

We import `reverseInto` and use it to obtain the reverse `Into[Int, String]`:

```scala mdoc
import As.reverseInto

val intToStr: Into[Int, String] = reverseInto[String, Int]
intToStr.into(42)
```

## Derivation Rules

`As.derived` applies the same rules as `Into.derived` in both directions and adds bidirectional compatibility checks on top. The derivation supports the same type categories as `Into`.

### Products (Case Classes and Tuples)

For two case classes `A` and `B`, the macro checks:
- fields with matching names must be convertible in **both** directions
- fields present in one type but absent from the other must be `Option` (defaults are not allowed — see [Restrictions](#restrictions))

We derive `As` for two structurally compatible case classes:

```scala mdoc:compile-only
import zio.blocks.schema.As

case class UserV1(name: String, age: Int)
case class UserV2(name: String, age: Long)

val userAs: As[UserV1, UserV2] = As.derived[UserV1, UserV2]
```

Tuples are matched positionally, so field name checks are skipped:

```scala mdoc:compile-only
import zio.blocks.schema.As

val tupleAs: As[(Int, String), (Long, String)] = As.derived[(Int, String), (Long, String)]
```

### Coproducts (Sealed Traits and Enums)

`As.derived` handles sealed traits and Scala 3 enums the same way `Into.derived` does — each subtype is matched by name and derived recursively:

```scala mdoc:compile-only
import zio.blocks.schema.As

sealed trait ShapeV1
object ShapeV1 {
  case class Circle(radius: Int)  extends ShapeV1
  case class Rect(w: Int, h: Int) extends ShapeV1
}

sealed trait ShapeV2
object ShapeV2 {
  case class Circle(radius: Long) extends ShapeV2
  case class Rect(w: Long, h: Long) extends ShapeV2
}

val shapeAs: As[ShapeV1, ShapeV2] = As.derived[ShapeV1, ShapeV2]
```

### Numeric Coercions

All numeric primitive types (`Byte`, `Short`, `Int`, `Long`, `Float`, `Double`) are bidirectionally coercible. Widening always succeeds; narrowing validates at runtime and returns a `Left` on overflow:

```scala mdoc:silent
import zio.blocks.schema.As

case class IntModel(value: Int)
case class LongModel(value: Long)

val numericAs: As[IntModel, LongModel] = As.derived[IntModel, LongModel]
```

A value within `Int` range round-trips without loss; one outside it fails on the way back:

```scala mdoc
numericAs.into(IntModel(1000)).flatMap(numericAs.from)
numericAs.from(LongModel(Long.MaxValue))
```

## Restrictions

`As` enforces constraints that `Into` does not. Because `As.derived` must produce valid conversions in both directions, it rejects configurations that would silently lose data during a round-trip.

**Default values on asymmetric fields are rejected.** A field with a default that has no counterpart in the other type cannot be round-tripped: when converting back, the field is missing and there is no way to distinguish a real default from a missing value:

```scala mdoc:compile-only
import zio.blocks.schema.As

case class WithDefault(name: String, age: Int = 25)
case class NoDefault(name: String)

// Does NOT compile — age has a default but is absent from NoDefault:
// As.derived[WithDefault, NoDefault]
```

Default values are allowed when the field exists in **both** types, because the value is never discarded during the round-trip:

```scala mdoc:compile-only
import zio.blocks.schema.As

case class PersonA(name: String, age: Int = 25)
case class PersonB(name: String, age: Int)

As.derived[PersonA, PersonB]  // compiles — age is present in both types
```

**`Option` fields on one side are allowed.** An `Option` field absent from the other type round-trips cleanly: `Some(v)` becomes `None` after a round-trip, which is the only safe behaviour for a missing field:

```scala mdoc:compile-only
import zio.blocks.schema.As

case class TypeA(name: String, nickname: Option[String])
case class TypeB(name: String)

As.derived[TypeA, TypeB]  // compiles
```

**Numeric coercions must be invertible in both directions.** Widening `Int → Long` is automatically paired with narrowing `Long → Int`. The narrowing validates at runtime, so the round-trip is safe even though it can fail:

```scala mdoc:compile-only
import zio.blocks.schema.As

case class IntVersion(value: Int)
case class LongVersion(value: Long)

As.derived[IntVersion, LongVersion]  // compiles — widening + narrowing form a valid pair
```

**Fields present in one type but absent from the other must be `Option`.** A non-optional field that exists only on one side cannot be populated in the reverse direction:

```scala mdoc:compile-only
import zio.blocks.schema.As

case class Short_(name: String)
case class Long_(name: String, extra: String)

// Does NOT compile — extra is not Optional and does not exist in Short_:
// As.derived[Short_, Long_]

case class Long2_(name: String, extra: Option[String])

As.derived[Short_, Long2_]  // compiles — extra is Optional
```

## Scala 2 vs Scala 3 Differences

| Feature | Scala 2 | Scala 3 |
|---------|---------|---------|
| Derivation syntax | `As.derived[A, B]` | `As.derived[A, B]` |
| Enum support | Sealed traits only | Scala 3 enums + sealed traits |
| Opaque types | N/A | ✅ Supported |
| Structural types | JVM only (reflection) | JVM only (reflection) |
| ZIO Prelude newtypes | ✅ `assert { between(...) }` | ✅ `override def assertion` |
| Error messages | Detailed macro errors | Detailed macro errors |

## Integration

`As[A, B]` is defined in `zio.blocks.schema` alongside `Into[A, B]`. Because `As` is a subtype of `Into`, the two type classes compose naturally: you can derive an outer `As` from inner `As` instances, or mix `As` and custom `Into` instances when some fields need one-way or custom logic.

For a full reference on one-way conversions and the derivation rules that `As` builds on, see [Into](./into.md).

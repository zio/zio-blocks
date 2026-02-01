---
id: combinators
title: "Combinators"
---

The `combinators` module provides compile-time typeclasses for composing values in type-safe ways. These typeclasses enable powerful abstractions for combining tuples, zipping values, and alternating between sum types, with automatic flattening and identity handling.

## Overview

The combinators module consists of five core typeclasses:

- **Combiner** - Bidirectional tuple composition with automatic flattening
- **Zippable** - One-way tuple zipping with discard tracking
- **EitherAlternator** - Either-based sum type alternation (cross-version compatible)
- **UnionAlternator** - Union type alternation for Scala 3 (prevents same-type unions)
- **StructuralCombiner** - Macro-based structural type merging (JVM-only, Scala 3)

All typeclasses are derived automatically via compile-time resolution and provide zero-cost abstractions for common composition patterns found in schema derivation, optics, and effect composition.

## Installation

Add the following to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-combinators" % "<version>"
```

For cross-platform projects (Scala.js or Scala Native):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-combinators" % "<version>"
```

Supported platforms:
- **Combiner, Zippable, EitherAlternator**: JVM, Scala.js, Scala Native
- **UnionAlternator**: JVM, Scala.js, Scala Native (Scala 3 only)
- **StructuralCombiner**: JVM only (Scala 3 only)

Supported Scala versions: 2.13.x and 3.x

## Combiner

`Combiner[L, R]` combines two values into a flattened tuple with bidirectional support. It automatically handles unit identity, empty tuple identity, and nested tuple flattening.

### Core Operations

```scala mdoc:compile-only
import zio.blocks.combinators.Combiner

// Combine two values
val combined: (Int, String, Boolean) = Combiner.combiner[((Int, String)), Boolean].combine((1, "hello"), true)

// Separate back to original parts
val (tuple, bool) = Combiner.combiner[(Int, String), Boolean].separate(combined)
// tuple = (1, "hello"), bool = true
```

### Identity Handling

Unit and EmptyTuple values are automatically eliminated:

```scala mdoc:compile-only
import zio.blocks.combinators.Combiner

// Unit on left - returns right value
val combiner1 = Combiner.combiner[Unit, Int]
val result1: Int = combiner1.combine((), 42)  // 42

// Unit on right - returns left value
val combiner2 = Combiner.combiner[String, Unit]
val result2: String = combiner2.combine("hello", ())  // "hello"

// EmptyTuple identity
val combiner3 = Combiner.combiner[EmptyTuple, String]
val result3: String = combiner3.combine(EmptyTuple, "world")  // "world"
```

### Tuple Flattening

Nested tuples are automatically flattened:

```scala mdoc:compile-only
import zio.blocks.combinators.Combiner

// Tuple + value flattens to larger tuple
val combiner1 = Combiner.combiner[(Int, String), Boolean]
val result1: (Int, String, Boolean) = combiner1.combine((1, "a"), true)

// Tuple + tuple concatenates
val combiner2 = Combiner.combiner[(Int, String), (Boolean, Double)]
val result2: (Int, String, Boolean, Double) = combiner2.combine((1, "a"), (true, 3.14))

// Separation maintains structure
val (left, right) = combiner2.separate(result2)
// left = (1, "a"), right = (true, 3.14)
```

### Type-Level Output

The output type is computed at compile time via the `Out` type member:

```scala mdoc:compile-only
import zio.blocks.combinators.Combiner

type Result1 = Combiner.WithOut[Int, String, ?]
// Result1 has Out = (Int, String)

type Result2 = Combiner.WithOut[(Int, String), Boolean, ?]
// Result2 has Out = (Int, String, Boolean)

type Result3 = Combiner.WithOut[Unit, String, ?]
// Result3 has Out = String
```

### Use Cases

Combiner is primarily used in:
- **Schema field composition** - Combining record fields into tuples
- **Optics composition** - Building lenses for nested structures
- **Effect composition** - ZIO-style `<*>` operators

## Zippable

`Zippable[L, R]` zips two values into a flattened tuple with one-way composition. Unlike `Combiner`, it provides discard flags and has no separation operation.

### Core Operations

```scala mdoc:compile-only
import zio.blocks.combinators.Zippable

// Zip two values
val zipped: (Int, String) = Zippable.zippable[Int, String].zip(42, "hello")

// Check discard flags
val zippable1 = Zippable.zippable[Unit, Int]
zippable1.discardsLeft   // true
zippable1.discardsRight  // false

val zippable2 = Zippable.zippable[String, Unit]
zippable2.discardsLeft   // false
zippable2.discardsRight  // true
```

### Identity Handling

Like Combiner, Unit and EmptyTuple are discarded:

```scala mdoc:compile-only
import zio.blocks.combinators.Zippable

// Unit on left
val result1: String = Zippable.zippable[Unit, String].zip((), "hello")

// Unit on right
val result2: Int = Zippable.zippable[Int, Unit].zip(42, ())

// EmptyTuple identity
val result3: Boolean = Zippable.zippable[EmptyTuple, Boolean].zip(EmptyTuple, true)
```

### Tuple Flattening

Tuples are flattened identically to Combiner:

```scala mdoc:compile-only
import zio.blocks.combinators.Zippable

// Tuple + value
val result1: (Int, String, Boolean) = 
  Zippable.zippable[(Int, String), Boolean].zip((1, "a"), true)

// Tuple + tuple
val result2: (Int, String, Boolean, Double) = 
  Zippable.zippable[(Int, String), (Boolean, Double)].zip((1, "a"), (true, 3.14))
```

### Discard Flags

The discard flags enable optimization in effect composition:

```scala mdoc:compile-only
import zio.blocks.combinators.Zippable

def zipWithDiscard[A, B](a: A, b: B)(using z: Zippable[A, B]): z.Out = {
  if (z.discardsLeft) {
    // Skip computing left value
    z.zip(a, b)
  } else if (z.discardsRight) {
    // Skip computing right value
    z.zip(a, b)
  } else {
    // Both values needed
    z.zip(a, b)
  }
}
```

### Use Cases

Zippable is primarily used in:
- **Effect sequencing** - ZIO's `*>`, `<*`, `<*>` operators
- **Parser combinators** - Combining parsers with selective results
- **Builder patterns** - Accumulating values with optional discards

## EitherAlternator

`EitherAlternator[L, R]` provides bidirectional conversion between sum types and `Either[L, R]`. Unlike `UnionAlternator`, it allows same-type combinations because `Left` and `Right` wrappers preserve positional information.

### Core Operations

```scala mdoc:compile-only
import zio.blocks.combinators.EitherAlternator

val alternator = EitherAlternator.alternator[Int, String]

// Create left and right values
val leftVal: Either[Int, String] = alternator.left(42)
val rightVal: Either[Int, String] = alternator.right("hello")

// Extract values
val maybeInt: Option[Int] = alternator.unleft(leftVal)        // Some(42)
val maybeStr1: Option[String] = alternator.unright(leftVal)   // None
val maybeStr2: Option[String] = alternator.unright(rightVal)  // Some("hello")
```

### Same-Type Support

EitherAlternator allows same-type combinations:

```scala mdoc:compile-only
import zio.blocks.combinators.EitherAlternator

val alternator = EitherAlternator.alternator[Int, Int]

val left: Either[Int, Int] = alternator.left(1)    // Left(1)
val right: Either[Int, Int] = alternator.right(2)  // Right(2)

// Distinguishable due to Left/Right wrappers
alternator.unleft(left)    // Some(1)
alternator.unright(left)   // None
alternator.unleft(right)   // None
alternator.unright(right)  // Some(2)
```

### Pattern Matching

Use with standard Either pattern matching:

```scala mdoc:compile-only
import zio.blocks.combinators.EitherAlternator

def process[L, R](value: Either[L, R])(using alt: EitherAlternator.WithOut[L, R, Either[L, R]]): String =
  value match {
    case Left(l) => s"Left: $l"
    case Right(r) => s"Right: $r"
  }
```

### Cross-Version Compatibility

EitherAlternator works identically on Scala 2.13 and Scala 3, making it ideal for cross-version libraries.

### Use Cases

EitherAlternator is primarily used in:
- **Schema sum type encoding** - Representing sealed traits and enums
- **Error handling** - Left for errors, Right for success
- **Cross-version code** - Works on Scala 2.13 and 3.x

## UnionAlternator

`UnionAlternator[L, R]` provides bidirectional conversion for Scala 3's union types (`L | R`). It rejects same-type combinations at compile time to prevent ambiguity.

### Core Operations

```scala mdoc:compile-only
import zio.blocks.combinators.UnionAlternator

val alternator = UnionAlternator.alternator[Int, String]

// Create union values
val intVal: Int | String = alternator.left(42)
val strVal: Int | String = alternator.right("hello")

// Extract values
val maybeInt: Option[Int] = alternator.unleft(intVal)        // Some(42)
val maybeStr1: Option[String] = alternator.unright(intVal)   // None
val maybeStr2: Option[String] = alternator.unright(strVal)   // Some("hello")
```

### Same-Type Rejection

Union types collapse same types (`A | A` = `A`), so UnionAlternator rejects them:

```scala mdoc:compile-only
import zio.blocks.combinators.UnionAlternator

// Compile error: Cannot alternate same types
// val bad = UnionAlternator.alternator[Int, Int]
// Use Either[Int, Int] or wrap in distinct types instead
```

### Nothing Handling

UnionAlternator handles `Nothing` gracefully:

```scala mdoc:compile-only
import zio.blocks.combinators.UnionAlternator

// Nothing on left - output is right type
val alternator1 = UnionAlternator.alternator[Nothing, String]
type Out1 = alternator1.Out  // String

// Nothing on right - output is left type
val alternator2 = UnionAlternator.alternator[Int, Nothing]
type Out2 = alternator2.Out  // Int
```

### Pattern Matching

Use with Scala 3's union type pattern matching:

```scala mdoc:compile-only
import zio.blocks.combinators.UnionAlternator

def process[L, R](value: L | R)(using alt: UnionAlternator[L, R]): String =
  value match {
    case l: L => s"Left: $l"
    case r: R => s"Right: $r"
  }
```

### Use Cases

UnionAlternator is primarily used in:
- **Scala 3 sealed traits** - Representing sum types with union types
- **Type-safe alternation** - Preventing ambiguous same-type unions
- **Schema encoding** - Compact sum type representation

## StructuralCombiner

`StructuralCombiner` combines two structural types into their intersection type (`A & B`) using compile-time macros. It validates that no member names conflict and generates a wrapper that delegates to the appropriate source value.

### Basic Usage

```scala mdoc:compile-only
import zio.blocks.combinators.StructuralCombiner

type HasName = { def name: String }
type HasAge = { def age: Int }

val a: HasName = new { def name = "Alice" }
val b: HasAge = new { def age = 30 }

val combined: HasName & HasAge = StructuralCombiner.combine(a, b)
combined.name  // "Alice"
combined.age   // 30
```

### Compile-Time Validation

Conflicting member names are detected at compile time:

```scala mdoc:compile-only
import zio.blocks.combinators.StructuralCombiner

type HasId1 = { def id: Int }
type HasId2 = { def id: String }

val a: HasId1 = new { def id = 42 }
val b: HasId2 = new { def id = "abc" }

// Compile error: Conflicting members found in types A and B: id
// val bad = StructuralCombiner.combine(a, b)
```

### Empty Combinations

When one type has no members, the result is simply cast:

```scala mdoc:compile-only
import zio.blocks.combinators.StructuralCombiner

type Empty = Any
type HasValue = { def value: Int }

val empty: Empty = new {}
val hasValue: HasValue = new { def value = 42 }

val result: Empty & HasValue = StructuralCombiner.combine(empty, hasValue)
result.value  // 42
```

### Reflection-Based Implementation

StructuralCombiner uses Java reflection via `Selectable`:

```scala mdoc:compile-only
import zio.blocks.combinators.StructuralCombiner

// Generated wrapper delegates to source values
type A = { def foo: String }
type B = { def bar: Int }

val a: A = new { def foo = "hello" }
val b: B = new { def bar = 42 }

val combined: A & B = StructuralCombiner.combine(a, b)
// Internally creates: StructuralWrapper(a, b, List("foo"), List("bar"))
// combined.foo calls a.foo via reflection
// combined.bar calls b.bar via reflection
```

### Platform Restrictions

StructuralCombiner requires JVM platform and Scala 3:

```scala
// JVM-only - does not compile on Scala.js or Scala Native
// Scala 3 only - does not compile on Scala 2.13
```

### Use Cases

StructuralCombiner is primarily used in:
- **Schema record merging** - Combining structural record types
- **Optics composition** - Building lenses for intersection types
- **Type-safe configuration** - Merging config objects without case classes

## Advanced Patterns

### Chaining Combiners

Combine multiple values by chaining:

```scala mdoc:compile-only
import zio.blocks.combinators.Combiner

val step1 = Combiner.combiner[Int, String].combine(1, "a")
val step2 = Combiner.combiner[(Int, String), Boolean].combine(step1, true)
val step3 = Combiner.combiner[(Int, String, Boolean), Double].combine(step2, 3.14)
// step3: (Int, String, Boolean, Double) = (1, "a", true, 3.14)
```

### Effect Sequencing with Zippable

Implement ZIO-style operators:

```scala mdoc:compile-only
import zio.blocks.combinators.Zippable

trait Effect[+A] {
  def zipWith[B](that: Effect[B])(using z: Zippable[A, B]): Effect[z.Out] = {
    // Implement using z.zip(this.run, that.run)
    ???
  }

  def *>[B](that: Effect[B])(using z: Zippable[A, B]): Effect[z.Out] = {
    // Optimize: skip left value if z.discardsLeft
    ???
  }

  def <*[B](that: Effect[B])(using z: Zippable[A, B]): Effect[z.Out] = {
    // Optimize: skip right value if z.discardsRight
    ???
  }
}
```

### Generic Sum Type Handling

Use EitherAlternator or UnionAlternator generically:

```scala mdoc:compile-only
import zio.blocks.combinators.{EitherAlternator, UnionAlternator}

def encodeSumType[L, R, Out](value: Either[L, R])(using alt: EitherAlternator.WithOut[L, R, Out]): Out =
  value match {
    case Left(l) => alt.left(l)
    case Right(r) => alt.right(r)
  }

// Scala 3 variant with union types
def encodeUnion[L, R](value: Either[L, R])(using alt: UnionAlternator[L, R]): alt.Out =
  value match {
    case Left(l) => alt.left(l)
    case Right(r) => alt.right(r)
  }
```

### Structural Type Builders

Build complex structural types incrementally:

```scala mdoc:compile-only
import zio.blocks.combinators.StructuralCombiner

type Step1 = { def name: String }
type Step2 = { def age: Int }
type Step3 = { def email: String }

val s1: Step1 = new { def name = "Alice" }
val s2: Step2 = new { def age = 30 }
val s3: Step3 = new { def email = "alice@example.com" }

val combined12: Step1 & Step2 = StructuralCombiner.combine(s1, s2)
val combined123: Step1 & Step2 & Step3 = StructuralCombiner.combine(combined12, s3)

combined123.name   // "Alice"
combined123.age    // 30
combined123.email  // "alice@example.com"
```

## Performance Characteristics

| Typeclass | Time Complexity | Notes |
|-----------|-----------------|-------|
| Combiner | O(1) | Tuple operations are constant time |
| Zippable | O(1) | Identical to Combiner |
| EitherAlternator | O(1) | Direct Either construction |
| UnionAlternator | O(1) | Union values are unboxed |
| StructuralCombiner | O(m) per call | Reflection lookup; m = number of members accessed |

### Optimization Notes

- **Combiner/Zippable**: Zero-cost abstractions, compiled to direct tuple operations
- **EitherAlternator**: Same cost as manual Either construction
- **UnionAlternator**: Same cost as union type casting (zero overhead in Scala 3)
- **StructuralCombiner**: Reflection overhead; cache the wrapper if used frequently

## Integration with Schema

The combinators module is heavily used in ZIO Blocks Schema:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.combinators.Combiner

// Schema uses Combiner for field composition
case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
  
  // Internally uses:
  // Combiner.combiner[String, Int].combine(name, age)
}
```

### Schema Optics

Optics use Combiner for path composition:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.combinators.Combiner

case class Address(street: String, city: String)
case class Person(name: String, address: Address)

object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived

  // Uses Combiner to flatten path: Person -> Address -> street
  val streetName: Lens[Person, String] = $(_.address.street)
}
```

## Cross-Version Considerations

### Scala 2.13
- **Combiner**: Maximum tuple arity is 22
- **Zippable**: Maximum tuple arity is 22
- **EitherAlternator**: Fully supported
- **UnionAlternator**: Not available (union types require Scala 3)
- **StructuralCombiner**: Not available (macro API requires Scala 3)

### Scala 3
- **Combiner**: No arity limits
- **Zippable**: No arity limits
- **EitherAlternator**: Fully supported
- **UnionAlternator**: Fully supported
- **StructuralCombiner**: JVM only

## Common Patterns

### Type-Safe Field Accumulation

```scala mdoc:compile-only
import zio.blocks.combinators.Combiner

def buildRecord[A, B](a: A, b: B)(using c: Combiner[A, B]): c.Out =
  c.combine(a, b)

val r1 = buildRecord("Alice", 30)
val r2 = buildRecord(r1, true)
val r3 = buildRecord(r2, "Engineer")
// r3: (String, Int, Boolean, String)
```

### Generic Effect Composition

```scala mdoc:compile-only
import zio.blocks.combinators.Zippable

trait F[+A] {
  def flatMap[B](f: A => F[B]): F[B]
  def map[B](f: A => B): F[B]
}

extension [A](fa: F[A]) {
  def zip[B](fb: F[B])(using z: Zippable[A, B]): F[z.Out] =
    fa.flatMap(a => fb.map(b => z.zip(a, b)))
}
```

### Sealed Trait Encoding

```scala mdoc:compile-only
import zio.blocks.combinators.EitherAlternator

sealed trait Result[+E, +A]
case class Failure[E](error: E) extends Result[E, Nothing]
case class Success[A](value: A) extends Result[Nothing, A]

def toEither[E, A](result: Result[E, A])(using alt: EitherAlternator[E, A]): alt.Out =
  result match {
    case Failure(e) => alt.left(e)
    case Success(a) => alt.right(a)
  }
```

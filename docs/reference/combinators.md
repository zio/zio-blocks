---
id: combinators
title: "Combinators"
---

The `combinators` module provides compile-time typeclasses for composing and decomposing values in type-safe ways. Each module focuses on a specific domain: tuples, Either types, and union types.

## Overview

The combinators module consists of three core modules:

- **Tuples** - Tuple composition with automatic flattening and separation
- **Eithers** - Either canonicalization to left-nested form
- **Unions** - Union type operations (Scala 3 only)

Each module provides:
- A `Combiner[L, R]` typeclass for combining values
- A `Separator[A]` typeclass for separating combined values
- Convenience methods `combine` and `separate`

All typeclasses are derived automatically via compile-time resolution and provide zero-cost abstractions.

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
- **Tuples, Eithers**: JVM, Scala.js (Scala 2.13 and 3.x)
- **Unions**: JVM, Scala.js (Scala 3 only)

## Tuples

The `Tuples` module combines values into flat tuples and separates them back.

### Combiner

`Tuples.Combiner[L, R]` combines two values into a flattened tuple.

```scala
import zio.blocks.combinators.Tuples

// Basic combination
val result1: (Int, String) = Tuples.combine(1, "hello")

// Tuple flattening
val result2: (Int, String, Boolean) = Tuples.combine((1, "hello"), true)

// Deep flattening (Scala 3)
val result3: (Int, String, Boolean, Double) = Tuples.combine((1, "hello"), (true, 3.14))
```

#### Identity Handling

Unit and EmptyTuple values are automatically eliminated:

```scala
import zio.blocks.combinators.Tuples

// Unit on left - returns right value
val result1: Int = Tuples.combine((), 42)

// Unit on right - returns left value
val result2: String = Tuples.combine("hello", ())

// EmptyTuple identity (Scala 3)
val result3: String = Tuples.combine(EmptyTuple, "world")
```

#### Tuple Flattening

Nested tuples are automatically flattened:

```scala
import zio.blocks.combinators.Tuples

// Tuple + value flattens to larger tuple
val result1: (Int, String, Boolean) = Tuples.combine((1, "a"), true)

// Tuple + tuple concatenates (Scala 3 - recursive flattening)
val result2: (Int, String, Boolean, Double) = Tuples.combine((1, "a"), (true, 3.14))

// Scala 2 - flattens left tuple only
val result2: (Int, String, (Boolean, Double)) = Tuples.combine((1, "a"), (true, 3.14))
```

### Separator

`Tuples.Separator[A]` separates a tuple into its init (all but last) and last element.

```scala
import zio.blocks.combinators.Tuples

// 2-tuple separation
val (left1, right1): (Int, String) = Tuples.separate((1, "hello"))
// left1 = 1, right1 = "hello"

// 3-tuple separation
val (left2, right2): ((Int, String), Boolean) = Tuples.separate((1, "hello", true))
// left2 = (1, "hello"), right2 = true

// 4-tuple separation
val (left3, right3): ((Int, String, Boolean), Double) = Tuples.separate((1, "hello", true, 3.14))
// left3 = (1, "hello", true), right3 = 3.14
```

### Type-Level Operations

The output type is computed at compile time via the `Out` type member:

```scala
import zio.blocks.combinators.Tuples

// Access the combiner with explicit output type
val combiner: Tuples.Combiner.WithOut[Int, String, (Int, String)] = 
  summon[Tuples.Combiner[Int, String]]

// Access the separator with explicit types
val separator: Tuples.Separator.WithTypes[(Int, String, Boolean), (Int, String), Boolean] =
  summon[Tuples.Separator[(Int, String, Boolean)]]
```

### Scala 2 vs Scala 3 Differences

| Feature | Scala 2.13 | Scala 3.x |
|---------|------------|-----------|
| Maximum tuple arity | 22 | Unlimited |
| Tuple flattening | Left tuple only | Recursive both sides |
| EmptyTuple identity | Not available | Supported |

## Eithers

The `Eithers` module canonicalizes Either types to left-nested form and separates them.

### Combiner

`Eithers.Combiner[L, R]` transforms an `Either[L, R]` into its left-nested canonical form.

```scala
import zio.blocks.combinators.Eithers

// Atomic Either - unchanged
val result1: Either[Int, String] = Eithers.combine(Left(42): Either[Int, String])

// Right-nested Either - reassociates to left-nested
val input: Either[Int, Either[String, Boolean]] = Right(Right(true))
val result2: Either[Either[Int, String], Boolean] = Eithers.combine(input)
// Right(Right(true)) becomes Right(true)

// Left(42) becomes Left(Left(42))
val input2: Either[Int, Either[String, Boolean]] = Left(42)
val result3: Either[Either[Int, String], Boolean] = Eithers.combine(input2)
```

#### Canonical Form

The canonical form is always left-nested:

```
Right-nested input:          Left-nested output:
Either[A, Either[B, C]]  =>  Either[Either[A, B], C]
Either[A, Either[B, Either[C, D]]]  =>  Either[Either[Either[A, B], C], D]
```

This transformation preserves values while reassociating the structure:
- `Left(a)` → `Left(Left(a))`
- `Right(Left(b))` → `Left(Right(b))`
- `Right(Right(c))` → `Right(c)`

### Separator

`Eithers.Separator[A]` peels the rightmost alternative from a canonical Either:

```scala
import zio.blocks.combinators.Eithers

val input: Either[Int, String] = Left(42)
val result: Either[Int, String] = Eithers.separate(input)
```

### Use Cases

Eithers canonicalization is useful for:
- **Schema sum type encoding** - Uniform representation of sealed traits
- **Error handling composition** - Combining error types systematically
- **Cross-version compatibility** - Works identically on Scala 2 and 3

## Unions (Scala 3 Only)

The `Unions` module converts between Either types and Scala 3 union types.

### Combiner

`Unions.Combiner[L, R]` converts an `Either[L, R]` to a union type `L | R`:

```scala
import zio.blocks.combinators.Unions

val either: Either[Int, String] = Left(42)
val union: Int | String = Unions.combine(either)
// Result: 42 (typed as Int | String)

val either2: Either[Int, String] = Right("hello")
val union2: Int | String = Unions.combine(either2)
// Result: "hello" (typed as Int | String)
```

### Separator

`Unions.Separator[A]` discriminates a union type back to Either:

```scala
import zio.blocks.combinators.Unions

val value: Int | String = 42
val result: Either[Int, String] = Unions.separate(value)(using summon[Unions.Separator.WithTypes[Int | String, Int, String]])
// Result: Left(42)

val value2: Int | String = "hello"
val result2: Either[Int, String] = Unions.separate(value2)(using summon[Unions.Separator.WithTypes[Int | String, Int, String]])
// Result: Right("hello")
```

### Same-Type Rejection

Union types collapse same types (`A | A` = `A`), making them ambiguous. The separator rejects overlapping types at compile time:

```scala
import zio.blocks.combinators.Unions

// Compile error: Union types must contain unique types
// val sep = summon[Unions.Separator.WithTypes[Int | Int, Int, Int]]

// Use Either for same-type alternation instead:
import zio.blocks.combinators.Eithers
val either: Either[Int, Int] = Left(1)  // Distinguishable via Left/Right
```

### Type Erasure Caveat

Union discrimination relies on runtime type tests, which are fragile for erased types:

```scala
// Problematic: List[Int] and List[String] erase to List
val value: List[Int] | List[String] = List(1, 2, 3)
// Runtime cannot distinguish List[Int] from List[String]

// Safe: Use distinct concrete types
val value: Int | String = 42  // Works reliably
```

## Generic Usage Patterns

### With Implicit Parameters (Scala 2)

```scala
import zio.blocks.combinators.Tuples

def combineAll[A, B, C](a: A, b: B, c: C)(
  implicit ab: Tuples.Combiner[A, B],
           abc: Tuples.Combiner[ab.Out, C]
): abc.Out = {
  val step1 = ab.combine(a, b)
  abc.combine(step1, c)
}

val result = combineAll(1, "hello", true)
// result: (Int, String, Boolean)
```

### With Context Parameters (Scala 3)

```scala
import zio.blocks.combinators.Tuples

def combineAll[A, B, C](a: A, b: B, c: C)(using
  ab: Tuples.Combiner[A, B],
  abc: Tuples.Combiner[ab.Out, C]
): abc.Out =
  val step1 = ab.combine(a, b)
  abc.combine(step1, c)

val result = combineAll(1, "hello", true)
// result: (Int, String, Boolean)
```

### Path-Dependent Types

The `Out`, `Left`, and `Right` type members are path-dependent:

```scala
import zio.blocks.combinators.Tuples

def process[A](a: A)(using s: Tuples.Separator[A]): (s.Left, s.Right) =
  s.separate(a)

val result: (Int, String) = process((1, "hello"))
```

### Type Aliases for Clarity

```scala
import zio.blocks.combinators.Tuples

// Combiner with known output type
type IntStringCombiner = Tuples.Combiner.WithOut[Int, String, (Int, String)]

// Separator with known left/right types
type TripleSeparator = Tuples.Separator.WithTypes[(Int, String, Boolean), (Int, String), Boolean]
```

## Performance Characteristics

| Module | Time Complexity | Notes |
|--------|-----------------|-------|
| Tuples.combine | O(1) to O(n) | O(1) for small tuples; O(n) for flattening nested tuples |
| Tuples.separate | O(n) | Splits tuple at size-1 position |
| Eithers.combine | O(d) | d = nesting depth of right-nested Either |
| Eithers.separate | O(d) | Same as combine (delegates to combiner) |
| Unions.combine | O(1) | Direct Either fold |
| Unions.separate | O(1) | Single type test |

All operations are pure and allocation-minimal.

## Cross-Version Summary

| Feature | Scala 2.13 | Scala 3.x |
|---------|------------|-----------|
| Tuples.Combiner | Yes (max 22) | Yes (unlimited) |
| Tuples.Separator | Yes (max 22) | Yes (unlimited) |
| Eithers.Combiner | Yes | Yes |
| Eithers.Separator | Yes | Yes |
| Unions.Combiner | No | Yes |
| Unions.Separator | No | Yes |
| Recursive tuple flattening | No | Yes |
| EmptyTuple handling | No | Yes |

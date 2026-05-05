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
- A unified typeclass (e.g., `Tuples.Tuples[L, R]`) that provides both `Tuples.Tuples#combine` and `Tuples.Tuples#separate` operations
- A convenience function like `Tuples.combine` (the `Tuples#separate` operation is available on the typeclass instance)

All typeclasses are derived automatically via compile-time resolution and provide zero-cost abstractions.

## Installation

Add the following to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-blocks-combinators" % "@VERSION@"
```

For cross-platform projects (Scala.js):

```sbt
libraryDependencies += "dev.zio" %%% "zio-blocks-combinators" % "@VERSION@"
```

Supported platforms:
- **Tuples, Eithers**: JVM, Scala.js (Scala 2.13 and 3.x)
- **Unions**: JVM, Scala.js (Scala 3 only)

## How They Work Together

The three combinator types solve distinct composition problems, each with a specific architectural role:

```
Your values → Tuples → Eithers → Unions (Scala 3)
  (Any)        (Group)   (Flatten)  (Convert)
```

**Tuples** aggregates heterogeneous values into a single product type, automatically flattening nested structures for ergonomics. Use Tuples when you need to combine independent values or build up multi-element results.

**Eithers** canonicalizes nested choice types (Either) into a uniform left-nested form, making error accumulation and pattern matching systematic. Use Eithers when composing error types, building sum types for schemas, or needing predictable Either nesting.

**Unions** (Scala 3 only) bridges the gap between Either's runtime disjoint semantics and Scala 3's native union types (`|`), enabling type-safe two-way conversion. Use Unions when you want to leverage Scala 3's union syntax for cleaner API design while maintaining Either-compatible serialization or error handling.

**Typical workflows:**

1. **Building a result**: Combine independent values with `Tuples.combine` to aggregate results, then flatten automatically.
2. **Handling alternatives**: Use `Eithers.combine` to normalize error types in a chain of operations, ensuring all Eithers nest consistently.
3. **Scala 3 API design**: Convert a polymorphic result to a union type with `Unions.combine` for idiomatic Scala 3 code, or convert back to Either for interop.

Here is how to combine multiple values and canonicalize error types:

```scala mdoc
import zio.blocks.combinators.{Tuples, Eithers}

// Aggregate three values into a flattened tuple
val username: String = "alice"
val userId: Int = 42
val email: String = "alice@example.com"
val userTuple = Tuples.combine(username, Tuples.combine(userId, email))

// Canonicalize nested Either types to left-nested form
val validationError: Either[String, Either[String, Boolean]] = Right(Left("invalid email"))
val canonical = Eithers.combine(validationError)
```

## Tuples

The `Tuples` module combines values into flat tuples and separates them back.

### combine

To combine two values into a flattened tuple:

```scala mdoc:compile-only
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

```scala mdoc:compile-only
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

```scala mdoc:compile-only
import zio.blocks.combinators.Tuples

// Tuple + value flattens to larger tuple
val result1: (Int, String, Boolean) = Tuples.combine((1, "a"), true)

// Tuple + tuple concatenates (Scala 3 - recursive flattening)
val result2: (Int, String, Boolean, Double) = Tuples.combine((1, "a"), (true, 3.14))

// Deep flattening with tuples
val result3: (Int, String, Boolean, Double) = Tuples.combine((1, "a"), (true, 3.14))
```

### separate

To split a tuple into its init (all but last) and last element, access `Tuples#separate` via the unified typeclass instance:

```scala mdoc:compile-only
import zio.blocks.combinators.Tuples

// 2-tuple separation
val t2 = summon[Tuples.Tuples[Int, String]]  // Scala 3
// or: implicitly[Tuples.Tuples[Int, String]]  // Scala 2
val (left1, right1): (Int, String) = t2.separate((1, "hello"))
// left1 = 1, right1 = "hello"

// 3-tuple separation
val t3 = summon[Tuples.Tuples[(Int, String), Boolean]]
val (left2, right2): ((Int, String), Boolean) = t3.separate((1, "hello", true))
// left2 = (1, "hello"), right2 = true

// 4-tuple separation
val t4 = summon[Tuples.Tuples[(Int, String, Boolean), Double]]
val (left3, right3): ((Int, String, Boolean), Double) = t4.separate((1, "hello", true, 3.14))
// left3 = (1, "hello", true), right3 = 3.14


```
### Type-Level Operations

Compile-time resolution computes the output type via the `Out` type member:

```scala mdoc:compile-only
import zio.blocks.combinators.Tuples

// Access the combiner with explicit output type
val combiner: Tuples.Tuples.WithOut[Int, String, (Int, String)] = 
  summon[Tuples.Tuples[Int, String]]

// Access with explicit types
val instance: Tuples.Tuples.WithOut[Int, String, (Int, String)] = 
  summon[Tuples.Tuples[Int, String]]
```

### Scala 2 vs Scala 3 Differences

| Feature | Scala 2.13 | Scala 3.x |
|---------|------------|-----------|
| Maximum tuple arity | 22 | Unlimited |
| Tuple flattening | Left tuple only | Recursive both sides |
| EmptyTuple identity | Not available | Supported |

## Eithers

The `Eithers` module canonicalizes Either types to left-nested form and separates them.

### combine

To transform an `Either[L, R]` into its left-nested canonical form:

```scala mdoc:compile-only
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

### separate

`Eithers#separate` is accessed via the unified typeclass instance and peels the rightmost alternative from a canonical Either:

```scala mdoc:compile-only
import zio.blocks.combinators.Eithers

val e = summon[Eithers.Eithers[Int, String]]
val input: Either[Int, String] = Left(42)
val result: Either[Int, String] = e.separate(e.combine(input))
```

### Use Cases

Eithers canonicalization is useful for:
- **Schema sum type encoding** - Uniform representation of sealed traits
- **Error handling composition** - Combining error types systematically
- **Cross-version compatibility** - Works identically on Scala 2 and 3

## Unions (Scala 3 Only)

The `Unions` module converts between Either types and Scala 3 union types.

### combine

`Unions.Unions[L, R]` converts an `Either[L, R]` to a union type `L | R`:

```scala mdoc:compile-only
import zio.blocks.combinators.Unions

val either: Either[Int, String] = Left(42)
val union: Int | String = Unions.combine(either)
// Result: 42 (typed as Int | String)

val either2: Either[Int, String] = Right("hello")
val union2: Int | String = Unions.combine(either2)
// Result: "hello" (typed as Int | String)
```

### separate

`Unions#separate` is accessed via the unified typeclass instance and discriminates a union type back to Either:

```scala mdoc:compile-only
import zio.blocks.combinators.Unions

val u = summon[Unions.Unions.WithOut[Int, String, Int | String]]
val result: Either[Int, String] = u.separate(42: Int | String)
// Result: Left(42)

val result2: Either[Int, String] = u.separate("hello": Int | String)
// Result: Right("hello")
```
### Same-Type Rejection

Union types collapse same types (`A | A` = `A`), making them ambiguous. The separator rejects overlapping types at compile time:

```scala mdoc:compile-only
import zio.blocks.combinators.Unions

// Compile error: Union types must contain unique types
// val u = summon[Unions.Unions.WithOut[Int, Int, Int | Int]]

// Use Either for same-type alternation instead:
import zio.blocks.combinators.Eithers
val either: Either[Int, Int] = Left(1)  // Distinguishable via Left/Right
```

### Type Erasure Caveat

Union discrimination relies on runtime type tests, which are fragile for erased types:

```scala mdoc:compile-only
import scala.collection.immutable.List

// Problematic: List[Int] and List[String] erase to List
val problematicValue: List[Int] | List[String] = List(1, 2, 3)
// Runtime cannot distinguish List[Int] from List[String]

// Safe: Use distinct concrete types
val value: Int | String = 42  // Works reliably
```

## Generic Usage Patterns

The combinators module supports both Scala 2's implicit parameters and Scala 3's context parameters. Here are idiomatic usage patterns for each:

### With Implicit Parameters (Scala 2)

To combine multiple values using implicit typeclass resolution:

```scala mdoc:compile-only
import zio.blocks.combinators.Tuples

def combineAll[A, B, C](a: A, b: B, c: C)(
  implicit ab: Tuples.Tuples[A, B],
           abc: Tuples.Tuples[ab.Out, C]
): abc.Out = {
  val step1 = ab.combine(a, b)
  abc.combine(step1, c)
}

val result = combineAll(1, "hello", true)
// result: (Int, String, Boolean)
```

### With Context Parameters (Scala 3)

To combine multiple values using context parameters:

```scala mdoc:compile-only
import zio.blocks.combinators.Tuples

def combineAll[A, B, C](a: A, b: B, c: C)(using
  ab: Tuples.Tuples[A, B],
  abc: Tuples.Tuples[ab.Out, C]
): abc.Out =
  val step1 = ab.combine(a, b)
  abc.combine(step1, c)

val result = combineAll(1, "hello", true)
// result: (Int, String, Boolean)
```

### Path-Dependent Types

The `Out`, `Left`, and `Right` type members are path-dependent:

```scala mdoc:compile-only
import zio.blocks.combinators.Tuples

def process[L, R](l: L, r: R)(using t: Tuples.Tuples[L, R]): (L, R) =
  t.separate(t.combine(l, r))

val result: (Int, String) = process(1, "hello")
```

### Type Aliases for Clarity

To improve readability when working with complex typeclass bounds:

```scala mdoc:compile-only
import zio.blocks.combinators.Tuples

// Typeclass with known output type
type IntStringTuples = Tuples.Tuples.WithOut[Int, String, (Int, String)]

// Typeclass with known left/right types
type TripleTuples = Tuples.Tuples.WithOut[(Int, String), Boolean, (Int, String, Boolean)]
```

## Integration Points

The combinator types integrate with other ZIO Blocks modules through systematic composition:

**Schema Evolution**: The `Eithers` canonicalization strategy directly supports schema sum type encoding. When deriving schemas for sealed trait hierarchies, the combinator ensures all Either encodings use the same left-nested form, enabling consistent serialization across schema variants.

**Error Handling**: `Eithers` provides a foundation for systematic error composition. Libraries building polymorphic error types can leverage canonicalization to ensure uniform error nesting, preventing subtle bugs from inconsistent Either structure.

**Scala 3 APIs**: The `Unions` type enables idiomatic Scala 3 DSLs and API designs that use native union syntax. Gateway types that convert between union-based and Either-based representations (e.g., for serialization compatibility) can use `Unions` for zero-cost interop.

**Tuple-Based Builders**: The `Tuples` module supports builder patterns and accumulator-based APIs that need to combine heterogeneous values step-by-step. By flattening automatically, it eliminates the ergonomic burden of manual nesting, making fluent builder chains natural.

## Performance Characteristics

All operations maintain predictable performance characteristics:

| Module                | Time Complexity | Notes                                                                    |
| --------------------- | --------------- | ------------------------------------------------------------------------ |
| Tuples.combine        | O(1) to O(n)    | O(1) for small tuples; O(n) for flattening nested tuples                |
| Tuples.separate       | O(n)            | Splits tuple at size-1 position                                         |
| Eithers.combine       | O(d)            | d = nesting depth of right-nested Either                                |
| Eithers.separate      | O(d)            | Same as combine (delegates to combiner)                                 |
| Unions.combine        | O(1)            | Direct Either fold                                                       |
| Unions.separate       | O(1)            | Single type test                                                         |

All operations are pure and allocation-minimal.

## Cross-Version Summary

| Feature                      | Scala 2.13       | Scala 3.x        |
| ---------------------------- | ---------------- | ---------------- |
| Tuples.Tuples                | Yes (max 22)     | Yes (unlimited)  |
| Eithers.Eithers              | Yes              | Yes              |
| Unions.Unions                | No               | Yes              |
| Recursive tuple flattening   | No               | Yes              |
| EmptyTuple handling          | No               | Yes              |

## See Also

- [Schema](./schema/schema.md) — The Schema module uses `Eithers` canonicalization for encoding sealed trait hierarchies and sum types with consistent Either nesting.
- **HTTP Model Schema** — When extracting multiple typed query parameters or headers in the HTTP Model schema module, `Eithers` provides systematic composition of error types for uniform error handling.

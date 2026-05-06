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

## Motivation

Building type-safe, composable systems requires managing values and types at both runtime and compile time. The combinators module solves three distinct problems that arise in complex Scala applications:

### The Tuple Nesting Problem

When building up results step-by-step—aggregating function parameters, accumulating intermediate results, or constructing compound values—you often end up with deeply nested tuples:

```scala
// Manual nesting is tedious and error-prone
val step1 = (1, "a")
val step2 = (step1, true)           // ((1, "a"), true)
val step3 = (step2, 3.14)           // (((1, "a"), true), 3.14)
val step4 = (step3, 'x')            // ((((1, "a"), true), 3.14), 'x')
```

This creates two problems:
1. **Ergonomic burden**: Consumers of compound values must destructure deeply nested structures
2. **Inconsistency**: Different code paths produce different tuple shapes, making composition fragile

The `Tuples` combinator automatically flattens these structures, producing clean, predictable tuples at each step.

### The Either Canonicalization Problem

Error handling often involves composing multiple error types through Either chains. Without systematic canonicalization, Either types nest unpredictably:

```scala
// Inconsistent nesting across code paths
val result1: Either[E1, V] = Left(e1)
val result2: Either[E1, Either[E2, V]] = Right(Left(e2))
val result3: Either[Either[E1, E2], V] = Right(Right(v))
// Each path has a different structure!
```

This causes problems when:
1. **Serializing error types** for schemas (each variant has a different shape)
2. **Accumulating errors** (inconsistent nesting makes aggregation complex)
3. **Pattern matching** (must handle multiple nesting patterns)

The `Eithers` combinator canonicalizes all Either types to a uniform left-nested form, ensuring systematic error composition.

### The Scala 3 Union Type Gap

Scala 3 introduces native union types (`A | B`) that are more idiomatic than `Either[A, B]`. However, existing code, libraries, and serialization infrastructure are built around `Either`. When adopting Scala 3, you face a choice:

1. Stick with `Either` for compatibility (missing idiomatic Scala 3 syntax)
2. Switch to union types (breaking compatibility with Either-based code)
3. Maintain two parallel type systems (duplication and cognitive overhead)

The `Unions` combinator bridges this gap, enabling bidirectional conversion between `Either[L, R]` and `L | R` with zero runtime overhead. Use union types idiomatically in your APIs while maintaining Either compatibility at serialization boundaries.

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

```scala mdoc
import zio.blocks.combinators.Tuples

val result1 = Tuples.combine(1, "hello")
val result2 = Tuples.combine((1, "hello"), true)
val result3 = Tuples.combine((1, "hello"), (true, 3.14))
```

#### Identity Handling

Unit and EmptyTuple values are automatically eliminated:

```scala mdoc
import zio.blocks.combinators.Tuples

Tuples.combine((), 42)
Tuples.combine("hello", ())
Tuples.combine(EmptyTuple, "world")
```

#### Tuple Flattening

Nested tuples are automatically flattened:

```scala mdoc
import zio.blocks.combinators.Tuples

Tuples.combine((1, "a"), true)
Tuples.combine((1, "a"), (true, 3.14))
```

### separate

To split a tuple into its init (all but last) and last element, access `Tuples#separate` via the unified typeclass instance:

```scala mdoc
import zio.blocks.combinators.Tuples

val t2 = summon[Tuples.Tuples[Int, String]]
t2.separate((1, "hello"))

val t3 = summon[Tuples.Tuples[(Int, String), Boolean]]
t3.separate((1, "hello", true))

val t4 = summon[Tuples.Tuples[(Int, String, Boolean), Double]]
t4.separate((1, "hello", true, 3.14))
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

## Eithers

The `Eithers` module canonicalizes Either types to left-nested form and separates them.

### combine

To transform an `Either[L, R]` into its left-nested canonical form:

```scala mdoc
import zio.blocks.combinators.Eithers

Eithers.combine(Left(42): Either[Int, String])

val input2 = Right(Right(true)): Either[Int, Either[String, Boolean]]
Eithers.combine(input2)

val input3 = Left(42): Either[Int, Either[String, Boolean]]
Eithers.combine(input3)
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

```scala mdoc
import zio.blocks.combinators.Eithers

val e = summon[Eithers.Eithers[Int, String]]
val input = Left(42): Either[Int, String]
e.separate(e.combine(input))
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

```scala mdoc
import zio.blocks.combinators.Unions

val either1 = Left(42): Either[Int, String]
Unions.combine(either1)

val either2 = Right("hello"): Either[Int, String]
Unions.combine(either2)
```

### separate

`Unions#separate` is accessed via the unified typeclass instance and discriminates a union type back to Either:

```scala mdoc
import zio.blocks.combinators.Unions

val u = summon[Unions.Unions.WithOut[Int, String, Int | String]]
u.separate(42: Int | String)
u.separate("hello": Int | String)
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

## Scala 2 vs Scala 3: Compatibility and Differences

The combinators module works across Scala 2.13 and Scala 3.x with **full source compatibility**. Write your code once; it compiles on both versions. However, certain features are version-specific due to language capabilities:

### Tuples: Version Differences

**Scala 2.13 Limitations:**
- Maximum arity of 22 (the standard library tuple limit)
- Tuple flattening only works when the left argument is a tuple (right argument cannot be recursively flattened)
- No `EmptyTuple` type (use `Unit` as identity instead)

**Scala 3.x Enhancements:**
- Unlimited arity (tuples are truly variable-length)
- Recursive flattening on both sides: `Tuples.combine((1, "a"), (true, 3.14))` flattens both tuples into a 4-tuple
- `EmptyTuple` as a first-class type with proper identity semantics

**Example: The difference in practice**

In Scala 2.13, this fails to compile:
```scala
// Scala 2.13 - ERROR: right side not flattened
val result = Tuples.combine((1, 2), (3, 4))  // Type mismatch
```

In Scala 3.x, it works seamlessly:
```scala
// Scala 3.x - OK: both sides flattened
val result = Tuples.combine((1, 2), (3, 4))  // (1, 2, 3, 4)
```

### Eithers: Full Cross-Version Support

`Eithers` canonicalization works identically on Scala 2.13 and 3.x. No version-specific behavior. Use with confidence across versions—canonicalization is deterministic.

### Unions: Scala 3 Only

`Unions` requires Scala 3 because:
- Union types (`A | B`) are a Scala 3 language feature
- Runtime type tests (via `TypeTest`) are only available in Scala 3
- Scala 2 has no native union syntax

For Scala 2.13 codebases, use `Either` directly or `Eithers` canonicalization instead.

### Feature Matrix

| Feature                        | Scala 2.13 | Scala 3.x | Notes                         |
|--------------------------------|------------|-----------|-------------------------------|
| **Tuples.combine**             | ✅          | ✅         | Left-only flattening in 2.13  |
| **Tuples.separate**            | ✅          | ✅         | Works identically on both     |
| **Eithers.combine**            | ✅          | ✅         | No differences                |
| **Eithers.separate**           | ✅          | ✅         | No differences                |
| **Unions.combine**             | ❌          | ✅         | Requires Scala 3              |
| **Unions.separate**            | ❌          | ✅         | Requires Scala 3              |
| **EmptyTuple as identity**     | ❌          | ✅         | Use `Unit` in Scala 2         |
| **Unlimited tuple arity**      | ❌          | ✅         | Limited to 22 in Scala 2      |
| **Recursive tuple flattening** | ❌          | ✅         | Right side not flattened in 2 |

### Migration Path from Scala 2 to 3

When adopting Scala 3, no changes are required for existing `Tuples` and `Eithers` code. Your code continues to work without modification. However, you can take advantage of new capabilities:

1. **Adopt `EmptyTuple` idiom**: Use `EmptyTuple` instead of `Unit` when combining with `Tuples` in Scala 3 for consistency with modern tuple syntax. Note that `Unit` remains fully supported and valid—`EmptyTuple` is a stylistic enhancement, not a replacement.
2. **Simplify tuple builders**: Leverage recursive flattening on both sides to remove manual nesting. In Scala 3, `Tuples.combine((1, "a"), (true, 3.14))` automatically flattens to `(1, "a", true, 3.14)`.
3. **Adopt `Unions`**: Replace `Either` with union types in new Scala 3-only code for idiomatic syntax using the `Unions` combinator.
4. **Gradual adoption**: Use `Either` in some modules and `Unions` in others during migration. Convert between them at module boundaries using `Unions.combine` and `Unions.separate` as needed.

## See Also

- [Schema](./schema/schema.md) — The Schema module uses `Eithers` canonicalization for encoding sealed trait hierarchies and sum types with consistent Either nesting.
- **HTTP Model Schema** — When extracting multiple typed query parameters or headers in the HTTP Model schema module, `Eithers` provides systematic composition of error types for uniform error handling.

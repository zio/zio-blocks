# Scope Implementation Guide

**Spec:** [docs/scope.md](../docs/scope.md)

**Target:** 100% code coverage (statements AND branches), Scala 2.13 + Scala 3.x, JVM + JS

---

## Overview

Implement ZIO Blocks Scope — a compile-time verified dependency injection library with **lifecycle safety**. The key differentiator from other DI libraries: resources are part of the scope's type, so lifecycle errors become compile-time errors.

**Any deviations from the spec surface area require approval from @john.**

---

## Project Structure

```
scope/
├── shared/
│   ├── src/main/scala/zio/blocks/scope/
│   │   ├── Scope.scala           # Core trait + companion
│   │   ├── Wire.scala            # Wire[-In, +Out] trait + Shared/Unique
│   │   ├── Wireable.scala        # Wireable[T] typeclass
│   │   ├── InStack.scala         # Type-level evidence
│   │   ├── package.scala         # Top-level functions: injected, shared, unique, defer, get
│   │   └── internal/
│   │       ├── ScopeImpl.scala   # Runtime implementation
│   │       └── Finalizers.scala  # Finalizer management
│   ├── src/main/scala-2/zio/blocks/scope/
│   │   ├── ScopeMacros.scala     # Scala 2 macro implementations
│   │   └── InStackInstances.scala
│   ├── src/main/scala-3/zio/blocks/scope/
│   │   ├── ScopeMacros.scala     # Scala 3 inline/macro implementations
│   │   └── InStackInstances.scala
│   └── src/test/scala/zio/blocks/scope/
│       ├── ScopeSpec.scala
│       ├── WireSpec.scala
│       ├── WireableSpec.scala
│       ├── InjectedSpec.scala
│       ├── LifecycleSpec.scala
│       ├── TypeSafetySpec.scala
│       └── ErrorMessageSpec.scala
├── jvm/src/main/scala/zio/blocks/scope/
│   └── PlatformScope.scala       # JVM-specific (thread safety, etc.)
└── js/src/main/scala/zio/blocks/scope/
    └── PlatformScope.scala       # JS-specific
```

---

## Dependencies

Scope depends on existing zio-blocks modules:

- **`zio-blocks-context`**: For `Context[+R]`, `IsNominalType[A]`, `IsNominalIntersection[A]`
- **`zio-blocks-typeid`**: For `TypeId[A]` (type identity, subtype checking)

Study these modules thoroughly before implementing:

```scala
// Context: type-indexed heterogeneous collection
final class Context[+R] {
  def get[A >: R](implicit ev: IsNominalType[A]): A
  def add[A](a: A)(implicit ev: IsNominalType[A]): Context[R & A]
  def ++[R1](that: Context[R1]): Context[R & R1]
}

// IsNominalType: evidence that A is a nominal (non-intersection) type
trait IsNominalType[A] {
  def typeId: TypeId[A]
  def typeIdErased: TypeId.Erased
}

// TypeId: supports isSubtypeOf for variance checking
sealed trait TypeId[A] {
  def isSubtypeOf(other: TypeId[?]): Boolean
}
```

---

## Comparison with Similar Systems

### vs. ZIO ZLayer

| Aspect | ZIO ZLayer | ZIO Blocks Scope |
|--------|------------|------------------|
| Type signature | `ZLayer[-RIn, +E, +ROut]` | `Scope[+Stack]` with `Wire[-In, +Out]` |
| Composition | `>>>` (sequential), `++` (parallel) | `injected[T]` (fluent chaining) |
| Lifecycle | `ZIO.acquireRelease` with `Scope` | `defer` with structured scoping |
| Error type | Explicit `E` parameter | Throws exceptions (sync) |
| Async | Built-in via ZIO effect | Not in v1 (sync only) |
| Memoization | Automatic per layer | Per `injected` call |
| Key feature | Effect system integration | **Typed scope identity** — scopes have different types based on contents |

**Key difference:** ZLayer's `RIn`/`ROut` track dependencies but scopes are anonymous. Scope's `Stack` type parameter tracks what each scope contains, so you can't pass the wrong scope to a function.

### vs. Effect TS Layer

| Aspect | Effect TS Layer | ZIO Blocks Scope |
|--------|-----------------|------------------|
| Type signature | `Layer<ROut, E, RIn>` | `Scope[+Stack]` |
| Construction | `Layer.scoped`, `Layer.succeed` | `injected[T]`, `Wire.Shared` |
| Composition | `Layer.provide`, `Layer.merge` | Fluent chaining with `injected` |
| Tags | Explicit `Context.Tag` | Implicit via `IsNominalType` |
| Error handling | Effect-based | Exceptions (sync) |

---

## Core Types to Implement

### 1. Scope[+Stack]

```scala
sealed trait Scope[+Stack] {
  def get[T](using InStack[T, Stack], IsNominalType[T]): T
  def defer(finalizer: => Unit): Unit
  def injected[T](wires: Wire[?,?]*): Scope.Closeable[Context[T] :: Stack]
}

object Scope {
  val global: Scope[TNil]  // Root scope, closes on JVM shutdown
  
  type Any = Scope[?]                    // Scope-polymorphic
  type Has[+T] = Scope[Context[T] :: ?]  // Scope that has T available
  
  trait Closeable[+Stack] extends Scope[Stack] with AutoCloseable {
    def close(): Unit
    def run[B](f: Scope[Stack] ?=> Context[CurrentLayer] => B): B
  }
}
```

**Implementation notes:**
- `Stack` is an HList-like structure: `Context[A] :: Context[B] :: TNil`
- Covariant in `Stack` so `Scope[Context[Dog] :: TNil]` <: `Scope[Context[Animal] :: TNil]`
- `global` registers a JVM shutdown hook for finalization
- `Closeable.run` creates child scope, executes block, closes child

### 2. Wire[-In, +Out]

```scala
sealed trait Wire[-In, +Out] {
  def construct: Scope.Has[In] ?=> Context[Out]  // Scala 3
  // def construct(implicit scope: Scope.Has[In]): Context[Out]  // Scala 2
}

object Wire {
  final case class Shared[-In, +Out](/* ... */) extends Wire[In, Out]
  final case class Unique[-In, +Out](/* ... */) extends Wire[In, Out]
  
  def value[T](t: T)(implicit ev: IsNominalType[T]): Wire[Any, T]
}
```

**Implementation notes:**
- `Shared` wires are memoized within a single `injected` call
- `Unique` wires create fresh instances each time
- Variance: contravariant in `In`, covariant in `Out`

### 3. InStack[-T, +Stack]

Type-level evidence that `T` exists somewhere in `Stack`:

```scala
trait InStack[-T, +Stack]

object InStack {
  // Base case: T is in the head
  implicit def head[T, Tail]: InStack[T, Context[T] :: Tail] = ???
  
  // Recursive case: T is in the tail
  implicit def tail[T, H, Tail](implicit ev: InStack[T, Tail]): InStack[T, H :: Tail] = ???
  
  // Subtype case: if Stack has Animal and we want Dog, check subtyping
  // (This requires TypeId.isSubtypeOf)
}
```

**Variance:**
- Contravariant in `T`: `InStack[Animal, S]` implies `InStack[Dog, S]` (if Dog <: Animal)
- Covariant in `Stack`: `InStack[T, BigStack]` implies `InStack[T, SmallStack]` if BigStack <: SmallStack

### 4. Wireable[T]

```scala
trait Wireable[T] {
  def wire: Wire[?, T]
}

object Wireable {
  inline def from[Impl <: T, T]: Wireable[T]  // Scala 3
  // def from[Impl <: T, T]: Wireable[T] = macro ...  // Scala 2
}
```

### 5. Top-Level Functions

```scala
// package.scala
package object scope {
  inline def injected[T](wires: Wire[?,?]*)(using Scope.Any): Scope.Closeable[...]
  inline def shared[T]: Wire[???, T]
  inline def unique[T]: Wire[???, T]
  def defer(finalizer: => Unit)(using Scope.Any): Unit
  def get[T](using Scope.Has[T], IsNominalType[T]): T
}
```

---

## Macro Implementation

### `injected[T]` Macro

The macro must:

1. Inspect `T`'s primary constructor to find dependencies
2. For each dependency `D`:
   - If a wire is provided that outputs `D` → use it
   - Otherwise → require `InStack[D, Stack]` evidence at call site
3. Handle `AutoCloseable`: generate `defer(instance.close())`
4. Handle constructor with `Scope` parameter: pass current scope
5. Generate construction code with proper memoization for `Shared` wires
6. Return `Scope.Closeable[Context[T] :: Stack]`

### `shared[T]` / `unique[T]` Macros

1. Check for `Wireable[T]` in implicit scope → use it
2. Otherwise, inspect `T`'s constructor:
   - Extract parameter types as `In`
   - Handle `AutoCloseable` for cleanup
   - Handle implicit `Scope` parameter
3. Generate `Wire.Shared[In, T]` or `Wire.Unique[In, T]`

### Error Messages

Generate clear compile-time errors for:

- **Missing dependency**: Show what's missing and where it's required
- **Ambiguous provider**: Multiple wires produce the same type
- **Cycle detection**: Show the cycle path
- **Non-nominal type**: `T` is an intersection type without `Wireable`

---

## Test Suites

**Coverage target: 100% statements AND branches**

### ScopeSpec.scala

```scala
suite("Scope")(
  suite("global")(
    test("global is singleton"),
    test("global registers shutdown hook"),
    test("global.defer registers finalizer"),
  ),
  suite("get")(
    test("get retrieves from current layer"),
    test("get retrieves from parent layer"),
    test("get retrieves by supertype"),
    test("get fails at compile time for missing type") {
      typeCheck("...").map(r => assertTrue(r.isLeft))
    },
  ),
  suite("defer")(
    test("defer registers finalizer"),
    test("finalizers run in LIFO order"),
    test("finalizers run even on exception"),
    test("multiple defers accumulate"),
  ),
  suite("close")(
    test("close runs finalizers"),
    test("close is idempotent"),
    test("close children before self"),
    test("close handles finalizer exceptions"),
  ),
  suite("injected")(
    test("injected creates child scope"),
    test("injected wires dependencies"),
    test("injected handles AutoCloseable"),
    test("injected handles Scope parameter"),
    test("nested injected creates nested scopes"),
  ),
)
```

### LifecycleSpec.scala

```scala
suite("Lifecycle")(
  test("resources cleaned up in reverse order") {
    val order = mutable.Buffer[String]()
    injected[A].injected[B].injected[C].run { _ =>
      // A, B, C acquired in order
    }
    // Assert order is C, B, A (reverse)
  },
  test("cleanup runs on exception") {
    var cleaned = false
    try {
      injected[Resource].run { _ => throw new Exception() }
    } catch { case _: Exception => }
    assertTrue(cleaned)
  },
  test("parent close closes all children") {
    // Create parent with multiple children, close parent
  },
  test("child close does not close parent") {
    // Create parent and child, close child, verify parent still works
  },
)
```

### TypeSafetySpec.scala

```scala
suite("Type Safety")(
  test("different scopes have different types") {
    // Scope[Context[A] :: TNil] != Scope[Context[B] :: TNil]
  },
  test("cannot access resource after scope closes") {
    typeCheck("""
      def bad()(using Scope.Has[TempFile]): Unit = ...
      injected[Socket].run { _ =>
        injected[TempFile].run { _ => }
        bad()  // Should not compile - TempFile out of scope
      }
    """).map(r => assertTrue(r.isLeft))
  },
  test("Scope.Has variance allows supertype access") {
    def needsAnimal(using Scope.Has[Animal]): Unit = get[Animal]
    injected[Dog].run { _ =>
      needsAnimal  // Should compile: Scope.Has[Dog] <: Scope.Has[Animal]
    }
  },
  test("InStack variance works correctly") {
    // InStack[Animal, Stack] should find Dog in Stack
  },
)
```

### WireSpec.scala

```scala
suite("Wire")(
  suite("Shared")(
    test("memoized within injected call"),
    test("cleanup registered"),
  ),
  suite("Unique")(
    test("fresh instance each time"),
    test("cleanup registered for each instance"),
  ),
  suite("value")(
    test("injects existing value"),
    test("no cleanup registered"),
  ),
  suite("composition")(
    test("multiple wires combine"),
    test("later wire overrides earlier"),
  ),
)
```

### WireableSpec.scala

```scala
suite("Wireable")(
  test("from derives for concrete class"),
  test("from derives for class with dependencies"),
  test("from registers cleanup for AutoCloseable"),
  test("companion Wireable takes precedence"),
  test("explicit wire overrides Wireable"),
)
```

### InjectedSpec.scala

```scala
suite("injected macro")(
  test("discovers constructor dependencies"),
  test("flattens multiple parameter lists"),
  test("handles no-arg constructors"),
  test("handles generic types"),
  test("handles default parameters"),
  test("generates cleanup for AutoCloseable"),
  test("passes Scope to constructor"),
  test("uses provided wires"),
  test("uses Wireable for traits"),
  test("error: missing dependency") {
    typeCheck("injected[NeedsFoo]").map(r => assertTrue(r.isLeft))
  },
  test("error: ambiguous wires") {
    typeCheck("injected[A](shared[Foo], shared[Foo])").map(r => assertTrue(r.isLeft))
  },
  test("error: cycle detection"),
)
```

### ErrorMessageSpec.scala

Test that compile errors are informative (may require manual verification):

```scala
suite("Error Messages")(
  test("missing dependency shows what's missing"),
  test("missing dependency shows required by"),
  test("ambiguous shows conflicting wires"),
  test("cycle shows cycle path"),
)
```

---

## Edge Cases to Handle

1. **Diamond dependencies**: A needs B and C, both need D
   - `Shared[D]` should create one instance
   - `Unique[D]` should create two instances

2. **Intersection types in constructor**: `class Foo(bc: B & C)` — should fail with clear error

3. **Recursive types**: Handle potential infinite loops in macro expansion

4. **Generic constructors**: `class Foo[T](t: T)` — may need special handling

5. **By-name parameters**: `class Foo(f: => Int)` — should work

6. **Implicit parameters**: Constructor with implicits other than Scope

7. **Private constructors**: Should fail with clear error

8. **Multiple constructors**: Use primary constructor only

9. **Companion Wireable vs explicit wire**: Explicit wins

10. **Subtype wiring**: `shared[DatabaseImpl]` satisfies `Database` dependency

11. **Empty Stack**: `Scope[TNil]` — can't `get` anything

12. **Thread safety**: `defer` and `close` must be thread-safe

---

## Implementation Order

1. **Phase 1: Core runtime**
   - `Scope` trait and `ScopeImpl`
   - `Finalizers` management
   - `global` singleton with shutdown hook
   - Basic tests

2. **Phase 2: Wire types**
   - `Wire.Shared`, `Wire.Unique`, `Wire.value`
   - Manual construction tests

3. **Phase 3: Type-level stack**
   - `TNil`, `::` HList types
   - `InStack` evidence with variance
   - Type safety tests

4. **Phase 4: Macros (Scala 3)**
   - `injected[T]` macro
   - `shared[T]` / `unique[T]` macros
   - `Wireable.from` macro
   - Full integration tests

5. **Phase 5: Macros (Scala 2)**
   - Port macros to Scala 2 macro paradise
   - Cross-version tests

6. **Phase 6: Coverage & Polish**
   - Fill coverage gaps
   - Error message quality
   - Documentation

---

## Commands

```bash
# Test Scala 3
sbt "++3.7.4; scopeJVM/test"

# Test Scala 2
sbt "++2.13.x; scopeJVM/test"  # Replace x with actual version

# Coverage (Scala 3)
sbt "++3.7.4; project scopeJVM; coverage; test; coverageReport"

# Format
sbt "++3.7.4; scopeJVM/scalafmt; scopeJVM/Test/scalafmt"
```

---

## Definition of Done

- [ ] All types from spec implemented
- [ ] All top-level functions from spec implemented
- [ ] 100% statement coverage
- [ ] 100% branch coverage
- [ ] Scala 2.13 tests pass
- [ ] Scala 3.x tests pass
- [ ] JVM tests pass
- [ ] JS tests pass
- [ ] Error messages are clear and helpful
- [ ] No deviations from spec without approval

# Scala 2 Scope Improvement Plan

**STATUS: ✅ COMPLETE**

This document describes the work that was done to enable ergonomic `scoped { child => ... }` syntax in Scala 2, matching Scala 3 behavior.

## Problem Statement

The current `ScopeUser` SAM trait has a **path-dependent return type**:

```scala
trait ScopeUser[P <: Scope, +A] {
  def use(child: Scope.Child[P]): child.$[A]
}
```

**Scala 3**: SAM conversion works naturally with dependent function types.

**Scala 2**: SAM conversion fails for path-dependent return types. The compiler sees `found: child.$[Int], required: child.$[Int]` (same text but different paths) and rejects it.

## Solution: Macro-Based Approach for Scala 2

We developed a working prototype that uses a **whitebox macro** to:

1. Accept a lambda with `Any` return type
2. Type-check the lambda body to extract the actual return type
3. Validate scope ownership (child.$[A] must belong to the current child)
4. Validate `Unscoped[A]` evidence
5. Generate properly typed code

### Mock Implementation (Working Prototype)

**Location**: `scope/shared/src/test/scala-2/zio/blocks/scope/SamSyntaxSpec.scala`

```scala
sealed abstract class MockScope extends Finalizer { self =>
  import scala.language.experimental.macros
  
  type $[+A]
  protected def wrap[A](a: A): $[A]
  
  object wrapValue {
    def apply[A](a: A): $[A] = wrap(a)
  }
  
  // scoped is a macro - inspects lambda body and extracts the underlying type
  final def scoped(f: MockScope.Child[self.type] => Any): Any = macro ScopeMacros.mockScopedImpl
}

object MockScope {
  object global extends MockScope {
    type $[+A] = A
    protected def wrap[A](a: A): $[A] = a
    def defer(f: => Unit): Unit = ()
  }
  
  final class Child[P <: MockScope](val parent: P) extends MockScope {
    type $[+A]
    protected def wrap[A](a: A): $[A] = a.asInstanceOf[$[A]]
    def defer(f: => Unit): Unit = ()
  }
}
```

**Macro Implementation**: `scope/shared/src/main/scala-2/zio/blocks/scope/ScopeMacros.scala`

```scala
def mockScopedImpl(c: whitebox.Context)(f: c.Tree): c.Tree = {
  import c.universe._
  
  val self = c.prefix.tree
  val fTyped = c.typecheck(f)
  
  // Extract return type from lambda body
  val (bodyType, lambdaParamName) = fTyped match {
    case Function(List(param), body) =>
      (body.tpe.widen, Some(param.name))
    case Block(_, Function(List(param), body)) =>
      (body.tpe.widen, Some(param.name))
    case _ =>
      c.abort(c.enclosingPosition,
        "In Scala 2, scoped blocks must be lambda literals: `.scoped { child => ... }`. " +
        "Passing a variable or method reference is not supported.")
  }
  
  // Extract underlying type from child.$[T] -> T
  // Validate scope ownership
  val underlyingType = bodyType match {
    case TypeRef(pre, sym, args) if sym.name.toString == "$" && args.nonEmpty =>
      val underlying = args.head
      
      // Check if prefix matches lambda parameter
      val prefixName = pre match {
        case SingleType(_, termName) => Some(termName.name)
        case _ => None
      }
      
      // Validate: prefix must be the lambda parameter
      (prefixName, lambdaParamName) match {
        case (Some(pn), Some(lpn)) if pn != lpn =>
          c.abort(c.enclosingPosition,
            s"Scope mismatch: returning $bodyType but expected ${lpn}.$$[...]. " +
            s"Cannot return a value from scope '$pn' inside scope '$lpn'.")
        case _ => // OK
      }
      underlying
      
    case _ => bodyType  // Plain type
  }
  
  // Validate Unscoped[underlyingType]
  val unscopedType = appliedType(typeOf[Unscoped[_]].typeConstructor, underlyingType)
  val unscopedInstance = c.inferImplicitValue(unscopedType)
  if (unscopedInstance == EmptyTree) {
    c.abort(c.enclosingPosition, 
      s"Cannot return $bodyType from scoped block: no Unscoped[$underlyingType] instance found.")
  }
  
  // Generate typed code
  q"""
    val parent = $self
    val child = new MockScope.Child[parent.type](parent)
    val rawResult: Any = $f(child)
    rawResult.asInstanceOf[$underlyingType]
  """
}
```

### Test Results (All Passing)

```scala
test("extracts Int from child.$[Int]") {
  val result: Int = MockScope.global.scoped { child =>
    child.wrapValue(42)
  }
  assertTrue(result == 42)
}

test("nested scopes with correct returns compile") {
  val result: Int = MockScope.global.scoped { outer =>
    val x: Int = MockScope.global.scoped { inner =>
      inner.wrapValue(10)
    }
    outer.wrapValue(x + 5)
  }
  assertTrue(result == 15)
}

test("returning plain Int works (macro validates Unscoped[Int])") {
  val result: Int = MockScope.global.scoped { _ =>
    42
  }
  assertTrue(result == 42)
}
```

### Compile-Time Error Detection

**1. Wrong scope $ type (returning outer.$[Int] from inner scope):**
```
error: Scope mismatch: returning outer.$[Int] but expected x$2.$[...].
       Cannot return a value from scope 'outer' inside scope 'x$2'.
```

**2. Non-lambda argument:**
```
error: In Scala 2, scoped blocks must be lambda literals: `.scoped { child => ... }`.
       Passing a variable or method reference is not supported.
```

**3. Non-Unscoped return type:**
```
error: Cannot return child.$[Database] from scoped block: no Unscoped[Database] instance found.
       Only types with Unscoped evidence can escape scope boundaries.
```

---

## Production Implementation Tasks

### Task 1: Port Macro to Real Scope

**Files to modify:**
- `scope/shared/src/main/scala-2/zio/blocks/scope/ScopeMacros.scala`
  - Add `scopedImpl` macro (similar to `mockScopedImpl`)
  - Reference `Scope.Child` instead of `MockScope.Child`
  - Include proper finalizer handling in generated code

**Current mock:**
```scala
q"""
  val parent = $self
  val child = new MockScope.Child[parent.type](parent)
  val rawResult: Any = $f(child)
  rawResult.asInstanceOf[$underlyingType]
"""
```

**Production version needs:**
```scala
q"""
  val parent = $self
  val child = new _root_.zio.blocks.scope.Scope.Child[parent.type](
    parent, 
    new _root_.zio.blocks.scope.internal.Finalizers
  )
  var primary: Throwable = null
  var unwrapped: $underlyingType = null.asInstanceOf[$underlyingType]
  try {
    val rawResult: Any = $f(child)
    unwrapped = rawResult.asInstanceOf[$underlyingType]
  } catch {
    case t: Throwable =>
      primary = t
      throw t
  } finally {
    val errors = child.close()
    if (primary != null) {
      errors.foreach(primary.addSuppressed)
    } else if (errors.nonEmpty) {
      val first = errors.head
      errors.tail.foreach(first.addSuppressed)
      throw first
    }
  }
  unwrapped
"""
```

### Task 2: Version-Specific `scoped` Method

**Scala 2** (`scope/shared/src/main/scala-2/`):
```scala
// In Scope trait or via extension
final def scoped(f: Scope.Child[self.type] => Any): Any = macro ScopeMacros.scopedImpl
```

**Scala 3** (`scope/shared/src/main/scala-3/`):
```scala
// Use dependent function type directly - no ScopeUser trait needed!
final def scoped[A](f: (child: Scope.Child[self.type]) => child.$[A])(using ev: Unscoped[A]): A = {
  val child = new Scope.Child[self.type](self, new Finalizers)
  // ... finalizer handling ...
  val result = f(child)
  child.scoped.run(result)
}
```

### Task 3: Delete ScopeUser Trait

Once both versions work with their respective approaches:
- **Delete** `ScopeUser` trait from `scope/shared/src/main/scala/zio/blocks/scope/Scope.scala`
- The trait was a workaround for Scala 2 SAM limitations - no longer needed

### Task 4: Evaluate `createTestableScope` Usage in Tests

After analysis, most tests **legitimately need** `createTestableScope` because they verify:
- Resource state BEFORE close (e.g., `assertTrue(!closed)`)
- Resource state AFTER close (e.g., `assertTrue(closed)`)

The `scoped` method automatically closes at block exit, so tests that need to verify
"not closed yet" intermediate state cannot use `scoped`.

**Decision: KEEP `createTestableScope` as `private[scope]` for legitimate testing needs.**

**Files using `createTestableScope` legitimately:**
- `ScopeSpec.scala` - Tests finalizer execution timing
- `ResourceSpec.scala` - Tests resource lifecycle (before/after close assertions)
- `WireSpec.scala` - Tests AutoCloseable finalization timing
- `DependencySharingSpec.scala` - Tests sharing behavior across scope closes
- `FinalizerInjectionSpec.scala` - Tests cleanup execution timing
- `FinalizersConcurrencySpec.scala` - Tests concurrent scope operations

**Tests that CAN be ported to `scoped`** (only verify final state):
- Some ScopeScala2Spec/ScopeScala3Spec tests that don't check intermediate state
- Tests that only verify "did it work" without timing concerns

### Task 5: Keep `createTestableScope` as Private API

The method is already `private[scope]` - this is correct. It should remain available
for testing scope lifecycle behavior. The production API is `scoped { ... }`.

### Task 6: Update Documentation

**Files to update:**
- `docs/scope.md` - Update examples, remove outdated ScopeUser references
- `docs/index.md` - Update scope module description if needed

**Key doc changes:**
- Syntax is now `scoped { child => ... }` (same in Scala 2 and 3)
- No SAM trait needed
- Scala 2 has restriction: must be lambda literal (no method refs)

### Task 7: Delete Mock Test Code

After production implementation is complete:
- Delete `SamSyntaxSpec.scala` (mock is no longer needed)
- Or rename to `ScopeScala2MacroSpec.scala` and test real Scope

---

## Syntax Parity: Scala 2 vs Scala 3

The goal is **identical syntax** in both versions:

```scala
// Works in both Scala 2 and Scala 3
Scope.global.scoped { child =>
  import child._
  val db: $[Database] = allocate(Resource(new Database))
  val result: String = scope.$(db)(_.query("SELECT 1"))
  result  // String is Unscoped, can be returned
}
```

**Scala 2 restriction**: Must be lambda literal. This fails:
```scala
val f: Scope.Child[_] => Any = child => child.scoped(42)
Scope.global.scoped(f)  // ERROR: must be lambda literal
```

**Scala 3**: No such restriction (dependent function types work with any callable).

---

## Implementation Order

1. ✅ **Port macro** - Created production `scopedImpl` in ScopeMacros.scala
2. ✅ **Add Scala 2 scoped** - Version-specific in scala-2/ScopeVersionSpecific.scala
3. ✅ **Add Scala 3 scoped** - Version-specific in scala-3/ScopeVersionSpecific.scala with dependent function type
4. ✅ **Delete ScopeUser** - Removed from shared code, renamed `scoped` object to `wrap`
5. ✅ **Evaluate tests** - Most tests legitimately need `createTestableScope` for timing assertions
6. ✅ **Keep createTestableScope** - Retained as `private[scope]` for testing
7. ✅ **Update docs** - Added Scala 2 lambda syntax restriction note to scope.md
8. ✅ **Keep mock spec** - SamSyntaxSpec.scala retained as regression test for macro

---

## Verification

**All complete:**
- ✅ All existing tests pass with new `scoped` syntax (both Scala 2.13 and Scala 3.7)
- ✅ Scala 2 and Scala 3 have identical user-facing syntax: `scoped { child => ... }`
- ✅ Compile-time errors are clear and actionable
- ✅ `createTestableScope` retained as `private[scope]` for legitimate test-only usage
- ✅ Documentation reflects current design with Scala 2 restrictions noted

**Test commands:**
```bash
sbt "++2.13.18; scopeJVM/test"  # Scala 2
sbt "++3.7.4; scopeJVM/test"     # Scala 3
```

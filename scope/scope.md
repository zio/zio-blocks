# Scope: Leak-Free Resource Safety

## Problem

The current `use`/`map`/`flatMap` API on scoped values (`$[A]`) temporarily exposes
the raw resource to an arbitrary user function:

```scala
def use[A, B](scoped: $[A])(f: A => B): $[B]
```

Nothing prevents `f` from capturing, storing, or passing the raw resource to another
thread. For example:

```scala
Scope.global.scoped { scope =>
  import scope.*
  val is = allocate(new FileInputStream("file.json"))
  use(is)(acceptInputStream(_))   // stores the InputStream — leaked!
  $("done")
}
```

The `map`/`flatMap` enrichment has the same problem. A user can also construct a
closure that outlives the scope:

```scala
use(is)(is => () => is.read())   // closure captures the resource
```

Because of these leak vectors, it was historically unsound to allow unwrapping
`$[A]` even for pure-data types (`Unscoped`), since a user could wrap a
resource-capturing closure in `$` and then unwrap it.

## Solution

A three-part design that makes non-malicious leaks impossible at compile time:

### 1. Macro-enforced `use`

Replace the current `use` with a macro that **verifies the lambda body at compile
time**. The macro ensures the receiver parameter is only ever used in method-receiver
position — never captured, stored, passed as an argument, or used in a nested lambda.

```scala
// Allowed — receiver is a method target:
use(is)(_.read())
use(is)(is => is.read() + is.available())
use(is)(_.getChannel.size)

// Rejected at compile time — receiver escapes:
use(is)(is => { someVar = is; 42 })        // assignment
use(is)(is => store(is))                    // passed as argument
use(is)(is => () => is.read())             // captured in nested lambda
```

The macro expands to the same runtime code as today (unwrap → call → rewrap), but
with compile-time AST validation.

### 2. `.get` on `$[A]` for Unscoped types

Add a method to `$[A]` that unwraps the scoped value when `A` has an `Unscoped`
instance:

```scala
implicit class ScopedOps[A](sa: $[A]) {
  def get(implicit ev: Unscoped[A]): A = $unwrap(sa)
}
```

This is sound **because the macro-enforced `use` prevents creating `$[A]` values
where `A: Unscoped` but the value secretly holds a resource reference**. The only
ways to produce `$[A]` are:

- `$(value)` — wrapping raw data, trivially safe
- `use(resource)(_.method(...))` — macro guarantees the receiver isn't captured in
  the result, so if the return type is `Unscoped`, it's genuinely pure data
- `allocate(resource)` — resource types don't have `Unscoped` instances
- `lower(parentValue)` — re-tags, same safety as the original

### 3. Simplified `scoped`

Since the user can call `.get` to extract values, `scoped` no longer needs to unwrap
`$[A]` at the boundary. The return type changes from `child.$[A]` to plain `A`:

```scala
// Before:
def scoped[A](f: (child: Scope.Child[self.type]) => child.$[A])(using Unscoped[A]): A

// After:
def scoped[A](f: (child: Scope.Child[self.type]) => A)(using Unscoped[A]): A
```

This has a major benefit: **Scala 2 no longer needs a macro for `scoped`**. The old
macro existed solely because Scala 2 lacks dependent function types (the return type
`child.$[A]` depended on the lambda parameter `child`). With the return type now
plain `A`, it's a regular function in both Scala 2 and 3.

## Usage Under the New Design

```scala
Scope.global.scoped { scope =>
  import scope._

  val conn = allocate(Resource.fromAutoCloseable(new DbConnection("db-001")))
  val tx   = allocate(use(conn)(_.beginTransaction("tx-001")).get)

  val rows1 = use(tx)(_.execute("UPDATE accounts SET balance = balance - 100 WHERE id = 1")).get
  val rows2 = use(tx)(_.execute("UPDATE accounts SET balance = balance + 100 WHERE id = 2")).get
  use(tx)(_.commit())

  TxResult(success = true, affectedRows = rows1 + rows2)
}
```

Key observations:
- No `for`/`yield` needed — plain imperative style
- Every resource interaction goes through `use`, which enforces safety
- `.get` extracts pure data (`Int`, `String`, etc.) from `$[A]`
- `Resource[A]` has an `Unscoped` instance — it's a lazy description, not a live resource
- `allocate(use(conn)(_.beginTransaction("tx-001")).get)` chains naturally: `use` returns
  `$[Resource[DbTransaction]]`, `.get` extracts the `Resource`, `allocate` acquires it
- Resources (`$[DbConnection]`, `$[DbTransaction]`) can never be `.get`-ed
- The return value is plain `TxResult` (which has `Unscoped`), no unwrapping at boundary

## `.get` — Extracting Pure Data from `$[A]`

The `.get` method on `$[A]` unwraps the scoped value when `A` has an `Unscoped`
instance, returning the underlying `A` directly:

```scala
val name: String = scope.use(db)(_.query("SELECT name")).get
val count: Int   = scope.use(db)(_.rowCount).get
```

### When to use `.get` vs `leak()`

| Method | Purpose | Safety |
|--------|---------|--------|
| `.get` | Extract pure data (`String`, `Int`, case classes) from `$[A]` | **Safe** — only works when `A: Unscoped`, so the value cannot hold resource references |
| `leak()` | Extract a raw resource (`$[Socket]` → `Socket`) for third-party interop | **Unsafe** — bypasses compile-time safety; emits a compiler warning |

Use `.get` whenever the result is pure data. Use `leak()` only when you must pass
a raw resource to a third-party API that cannot accept scoped types — and ensure the
scope outlives all usage of the leaked value.

### Example

```scala
Scope.global.scoped { scope =>
  import scope._
  val db: $[Database] = allocate(Resource.from[Database])

  // .get extracts pure data — safe, no warnings
  val result: String = scope.use(db)(_.query("SELECT 1")).get
  val count: Int     = scope.use(db)(_.rowCount).get

  // leak() extracts the raw resource — unsafe, emits compiler warning
  // Only needed for third-party APIs that require raw types
  @nowarn("msg=.*leak.*")
  val raw: Database = scope.leak(db)
  legacyLibrary.doSomething(raw)

  result
}
```

## `Unscoped[Resource[A]]` — Resource Descriptions as Pure Data

`Resource[A]` has an `Unscoped` instance because it is a **lazy description** of how
to acquire and release a resource — not a live resource itself. This means:

- `use(conn)(_.beginTransaction("tx-001"))` returns `$[Resource[DbTransaction]]`
- `.get` extracts the `Resource[DbTransaction]` from the scoped wrapper
- `allocate(...)` acquires the resource and registers its finalizer

This enables a natural pattern for methods that return `Resource[A]`:

```scala
Scope.global.scoped { scope =>
  import scope._
  val conn: $[DbConnection] = allocate(Resource.fromAutoCloseable(new DbConnection("db-001")))

  scope.scoped { txScope =>
    import txScope._
    val c: $[DbConnection]   = lower(conn)
    val tx: $[DbTransaction] = allocate(txScope.use(c)(_.beginTransaction("tx-001")).get)
    // use the transaction...
    txScope.use(tx)(_.commit())
    TxResult(success = true)
  }
}
```

Without `Unscoped[Resource[A]]`, this pattern would require `leak()` to extract the
raw connection before calling `beginTransaction`. With it, the entire flow stays
within the type-safe `use` + `.get` + `allocate` pipeline.

Note: `Unscoped[Resource[A]]` does **not** require `A: Unscoped`. A `Resource[Socket]`
is still just a description — extracting it from `$` doesn't give you a live socket,
only a recipe for creating one (which still requires `allocate` in a scope).

## Safety Analysis

| Leak vector | Prevention |
|---|---|
| `f` captures receiver in var/field | Macro rejects: receiver not in receiver position |
| `f` passes receiver as argument | Macro rejects: receiver not in receiver position |
| `f` captures receiver in closure | Macro rejects: receiver appears in nested lambda |
| `f` returns closure over receiver | Macro rejects: receiver appears in nested lambda |
| Scoped resource passed as arg to method | Type error: `$[Socket]` ≠ `Socket`, must `leak()` |
| Scoped pure data passed as arg to method | `.get` unwraps it; safe because `Unscoped` = pure data |
| Resource escapes `scoped` boundary | `Unscoped[A]` required; resources have no instance |

**Not in scope** (accepted limitations of zero-cost tagging):
- `asInstanceOf` casts, reflection, pattern matching on runtime types
- Deliberately lying with bogus `Unscoped` instances
- `package zio.blocks.scope` access to private members

These are the same limitations any zero-cost wrapper has. Only capability-based type
systems (e.g. Scala 3 capture checking) could address them.

### Unsound `Unscoped[DeferHandle]`

`DeferHandle` currently has an `Unscoped` instance (in `Unscoped.scala`), likely
added to simplify tests where `scoped` blocks return a `DeferHandle`. This is
**unsound**: `DeferHandle` wraps a closure (the cancel function) which may reference
scope-managed resources. With `.get` enabled, a user could extract a `DeferHandle`
from a scope and call `.cancel()` after the scope has closed, or worse, the closure
could hold a reference to a resource.

**Action**: Delete `given Unscoped[DeferHandle]` / `implicit val ... : Unscoped[DeferHandle]`.
Refactor any tests that break as a result — they should return a genuinely unscoped
value (e.g. `Boolean`, `Int`, `String`) instead of `DeferHandle`.

## Implementation Plan

### Phase 1: Add `.get` and macro `use` (additive, no breakage)

1. **Add `get` to `ScopedOps`** in `Scope.scala`.
   Requires `Unscoped[A]` evidence. Simple: `def get(implicit ev: Unscoped[A]): A`.

2. **Implement macro `use` in Scala 3** (`ScopeVersionSpecific.scala` in `scala-3/`).
   - `transparent inline def use[A, B](inline sa: $[A])(inline f: A => B): $[B]`
   - Macro inspects the lambda AST, verifies every occurrence of the lambda
     parameter is in receiver position of a `Select` (method call / field access).
   - Rejects: assignment, argument position, nested lambdas, constructor calls,
     pattern matching on the parameter.
   - Expansion: `if (isClosed) $wrap(null.asInstanceOf[B]) else $wrap(f($unwrap(sa)))`

3. **Implement macro `use` in Scala 2** (`ScopeMacros.scala` in `scala-2/`).
   - Whitebox macro with the same AST validation rules.
   - Same expansion as Scala 3.

4. **Tests**: Comprehensive compile-time and runtime tests.
   - Positive: all safe usage patterns compile and work.
   - Negative: all unsafe patterns produce compile errors.
   - Verify existing tests still pass with old `use` still present.

### Phase 2: Simplify `scoped` (breaking change)

5. **Change `scoped` signature** in both Scala 2 and 3:
   - Return type of `f` changes from `child.$[A]` to `A`.
   - Remove the `$run` unwrap step in the implementation — `f` already returns `A`.

6. **Scala 2: Replace `scoped` macro with a regular method**.
   - No more dependent function types needed.
   - `def scoped[A](f: Scope.Child[self.type] => A)(implicit ev: Unscoped[A]): A`
   - The body is the same try/finally/finalizers pattern, just without `$run`.

7. **Update all call sites** across scope-examples, tests, and downstream modules.
   - Remove `import scope._` patterns that existed solely for `$` access.
   - Rewrite for-comprehensions to imperative style with `use` + `.get`.

### Phase 3: Remove old API (cleanup)

8. **Remove `map`/`flatMap`** from `ScopedOps`.

9. **Remove old non-macro `use` overloads** (arity 1-5) from `Scope.scala`.

10. **Remove `wrapUnscoped` implicit conversion** — no longer needed since `scoped`
    doesn't return `$[A]`.

11. **Remove `$run`** — no longer called.

12. **Remove `$(a: A): $[A]` method** — users call `allocate` for resources. Pure
    data doesn't need wrapping since `scoped` returns `A` directly. Evaluate whether
    any remaining use cases exist.

13. **Clean up `ScopeMacros.scala`** — remove the old `scopedImpl` macro, leaving
    only the `use` macro, `leak` macro, and Wire macros.

### Phase 4: Update downstream

14. **Update scope-examples**: Rewrite all examples to the new style.

15. **Update docs**: Document the new safety model, `.get`, macro `use`.

## Macro `use` — AST Rules (Detail)

Given `use(sa)(param => body)`, the macro traverses `body` and checks every
reference to `param`:

**Allowed positions** (param appears as `.receiver`):
- `param.method(args...)` — direct method call
- `param.field` — field access
- `param.method(args...).chain(args...)` — chained calls (param is root receiver)

**Rejected positions** (compile error with clear message):
- `someFunction(param)` — param in argument position
- `val x = param` — param bound to a name (can escape)
- `var x = param` or `x = param` — assignment
- `() => param.method()` — param captured in nested lambda
- `new Foo(param)` — param in constructor
- `(param, other)` — param in tuple construction
- `if (cond) param else other` — param as expression result (could be stored)
- `param` as the body itself (identity) — returns raw resource as `$[A]`

The rule is simple: **every occurrence of `param` must be the leftmost element of a
select chain**. The result of the select chain (the method return value) is fine — 
it's not `param` itself.

## Guidelines & Requirements

1. **Uniform syntax across Scala 2 / 3.** The user-facing API must look and behave
   identically. Macro implementations differ (whitebox vs transparent inline), but
   the call-site syntax, semantics, and error messages must be consistent.

2. **Tests for all new functionality.** Every new feature (macro `use`, `.get`,
   simplified `scoped`) must have comprehensive tests covering:
   - Positive cases: all safe usage patterns compile and produce correct results.
   - Negative cases: all unsafe patterns produce compile-time errors (use
     `assertDoesNotCompile` / `illTyped` or equivalent).
   - Edge cases: closed scopes, chained method calls, multiple uses of receiver,
     interaction with `allocate`, `lower`, `leak`, `defer`.

3. **No bulk deleting old tests.** Tests for removed features (`map`/`flatMap`, old
   arity-N `use`) are deleted, but all other existing tests must be migrated to the
   new API and continue to pass. Do not delete a test simply because it fails under
   the new design — rewrite it.

4. **`scope-examples` fully updated.** All examples in `scope-examples/` must be
   rewritten to use the new API (`use` macro + `.get`, no `map`/`flatMap`, simplified
   `scoped`). They serve as documentation and must reflect the current design.

5. **Documentation updated.** `scope/scope.md` kept current as implementation
   proceeds. `docs/` updated for any user-facing changes. `index.md` updated, with a
   new README generated via `sbt docs/generateReadme`.

6. **All tests passing across Scala 2 / 3.** Run cross-version tests before
   declaring any phase complete:
   ```
   sbt "++3.x; scopeJVM/test; ++2.13.x; scopeJVM/test"
   ```

7. **Format, add, commit.** After each phase:
   ```
   sbt "++3.x; scopeJVM/scalafmt; scopeJVM/Test/scalafmt; scopeJS/scalafmt; scopeJS/Test/scalafmt; scalafmtSbt"
   git add -A && git commit
   ```

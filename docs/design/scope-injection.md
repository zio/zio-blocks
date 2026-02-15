# Design: Scope Injection for DI

## Status

**Proposed** — under review.

---

## Problem

Today, the DI system (`Wire` and `Resource`) injects a `Finalizer` into class
constructors, giving them the ability to register cleanup actions via `defer`.
This is sufficient for simple lifecycle management:

```scala
class Database(config: Config)(implicit finalizer: Finalizer) {
  val conn = DriverManager.getConnection(config.url)
  finalizer.defer(conn.close())
}
```

But `Finalizer` only supports `defer`. A class that needs to manage
sub-resources across method calls — creating child scopes, allocating temporary
resources per-request, or performing structured cleanup — cannot do so. It would
need a `Scope`, not just a `Finalizer`:

```scala
class WebServer(routes: Routes, pool: ConnectionPool)(implicit scope: Scope) {
  def handleRequest(req: Request): Response = scope.scoped { child =>
    import child._
    val conn = allocate(pool.acquire())
    routes.dispatch(req, conn)
  } // conn released here

  scope.defer(println("WebServer shutting down"))
}
```

Today, the only way to get a `Scope` inside a class is to use `Scope.global`,
which is wrong — it ties the resource's lifetime to the entire process, not to
the scope that owns the resource.

---

## Goals

1. Allow classes to receive a `Scope` (not just `Finalizer`) via DI.
2. The injected scope's lifetime must match the resource's lifetime.
3. The injected scope must support multi-threaded access (class methods can be
   called from any thread).
4. Closing the injected scope must be automatic and safe (no user
   responsibility).
5. `ProxyFinalizer` (an ad-hoc half-scope) must be eliminated in favor of a
   unified scope mechanism.
6. Support cancellable finalizers so that closing a child scope can remove its
   finalizer from the parent, preventing finalizer list leaks.

---

## Key Insight: Two Lifetime Models

The existing `scoped { }` API creates a **lexical scope**: created, used, and
closed within a single block on a single thread.

Resources need a **class scope**: created during construction, used across many
method calls from any thread, closed when the parent scope finalizes the
resource.

```
Lexical scope:    create ── use ── close     (one block, one thread)

Class scope:      create ──────────────── close    (parent finalizer)
                    │                       ↑
                    ├─ method call 1         │
                    │   └─ child scope       │
                    ├─ method call 2         │
                    │   └─ child scope       │
                    └─ ...                   │
```

Both are children of some parent scope. Both use the same `Finalizers`
machinery. The differences are:

| Property         | Lexical scope (today's `Child`)     | Class scope (new)                  |
| ---------------- | ----------------------------------- | ---------------------------------- |
| Created by       | User via `scope.scoped { }`         | DI system via `allocate(resource)` |
| `isOwner`        | Checks thread identity              | Always `true` (any thread)         |
| Closed by        | End of `scoped` block               | Parent scope's finalizer           |
| User-constructible | Yes                               | No (`private[scope]`)              |

Users must not be able to create class scopes directly. They are always created
by the DI system and closed by the parent, ensuring correct lifecycle
management. This is what makes the design safe despite the relaxed thread
ownership.

---

## Design

### 1. `Scope#open()`: The Primitive

The core addition is a method on `Scope` that creates a non-lexical child
scope:

```scala
sealed abstract class Scope extends Finalizer { self =>
  // ...existing methods...

  private[scope] def open(): $[OpenScope] = {
    val fins       = new internal.Finalizers
    val childScope = new Scope.Child(self, fins, unowned = true)
    val handle     = self.defer(fins.runAll().orThrow())
    $(OpenScope(childScope, handle))
  }
}
```

The return type is `$[OpenScope]` — scoped to the parent. On `Scope.global`,
`$[A] = A`, so `global.open()` returns a plain `OpenScope`. On a child scope,
`open()` returns a scoped `OpenScope` tied to that child's lifetime.

The existing `scoped` method is sugar over the same primitive of creating a
child scope:

```scala
def scoped[A](f: Child => child.$[A])(using Unscoped[A]): A = {
  // Conceptually:
  val child = new Scope.Child(self, new Finalizers, unowned = false)
  try f(child) finally child.close()
}
```

Both `scoped` and `open` create child scopes. `scoped` is lexical (single
thread, closed at block end). `open` is non-lexical (any thread, closed
explicitly or by parent).

`open()` is `private[scope]` — only the DI system uses it. Users create lexical
scopes via `scoped { }` as before.

### 2. `OpenScope`: Separating Scope from Close Handle

```scala
case class OpenScope private[scope] (scope: Scope, handle: DeferHandle)
```

The `scope` field is what gets passed to the class constructor. The `handle`
field stays with the lifecycle manager (the DI system). The class cannot close
its own scope — it only receives a `Scope`.

When the parent scope closes, the finalizer registered by `open()` closes the
child scope automatically. If early close is needed (e.g., `Resource.Shared`
refcount reaching zero), `handle.cancel()` removes the finalizer from the
parent and `scope.close()` runs the child's finalizers.

### 3. `DeferHandle`: Cancellable Finalizers

Today, `defer` is fire-and-forget — once registered, a finalizer cannot be
removed. This causes a leak when a child scope is closed early: the parent still
holds a finalizer that will try to close an already-closed child.

```scala
abstract class DeferHandle {
  def cancel(): Unit
}
```

`defer` always returns a `DeferHandle`. Callers that don't need cancellation
simply ignore the return value. Calling `handle.cancel()` **removes the entry
from the finalizer collection** — not just marks it as "don't call me." This
is critical for memory safety: without true removal, long-lived scopes
(especially `Scope.global`, whose `runAll()` only executes at JVM shutdown)
would accumulate dead entries forever.

This is necessary for:

- **`Resource.Shared`**: When refcount reaches zero, close the shared scope and
  remove the (now-unnecessary) finalizer from `Scope.global`.
- **Early release**: Any case where a resource is released before the parent
  scope closes.
- **Preventing memory leaks**: Every shared resource that is created and
  destroyed would otherwise add a permanently-retained entry to the parent's
  finalizer collection.

#### Changes to `Finalizer` trait

```scala
trait Finalizer {
  def defer(f: => Unit): DeferHandle   // was: def defer(f: => Unit): Unit
}
```

All existing call sites that ignore the return value continue to work.

#### Implementation in `Finalizers`

The current `Finalizers` uses a lock-free singly-linked list of
`Node(run, next)` behind an `AtomicReference`. This structure does not support
true removal — a cancelled node remains allocated and referenced, leaking
memory in long-lived scopes.

**Chosen strategy: `ConcurrentHashMap[Long, () => Unit]`**

Each `add` generates a monotonically increasing ID (via `AtomicLong`). The
finalizer thunk is stored under that ID. `cancel()` removes by ID — O(1),
lock-free, true removal with immediate GC eligibility. During `runAll()`,
entries are sorted by ID descending (LIFO order) and executed.

```scala
private[scope] final class Finalizers {
  private val counter = new AtomicLong(0L)
  private val entries = new ConcurrentHashMap[Long, () => Unit]()
  private val closed  = new AtomicBoolean(false)

  def add(finalizer: => Unit): DeferHandle = {
    if (closed.get()) return DeferHandle.Noop
    val id    = counter.getAndIncrement()
    val thunk = () => finalizer
    entries.put(id, thunk)
    // Double-check: if closed between get() and put(), remove and don't run
    if (closed.get()) { entries.remove(id); return DeferHandle.Noop }
    new DeferHandle.Live(id, entries)
  }

  def runAll(): Finalization = {
    if (!closed.compareAndSet(false, true)) return Finalization.empty
    val ids = entries.keySet().asScala.toArray.sorted(Ordering[Long].reverse) // LIFO
    val errorsBuilder = Chunk.newBuilder[Throwable]
    for (id <- ids) {
      val thunk = entries.remove(id)
      if (thunk != null) {
        try thunk()
        catch { case t: Throwable => errorsBuilder += t }
      }
    }
    Finalization(errorsBuilder.result())
  }

  def isClosed: Boolean = closed.get()
}
```

**Complexity:**

- `add`: O(1) amortized (ConcurrentHashMap put + AtomicLong increment)
- `cancel`: O(1) (ConcurrentHashMap remove)
- `runAll`: O(n log n) for the sort, O(n) for execution. This runs once per
  scope close, so the sort cost is acceptable.

**`DeferHandle`:**

```scala
abstract class DeferHandle {
  def cancel(): Unit
}

object DeferHandle {
  private[scope] object Noop extends DeferHandle {
    def cancel(): Unit = ()
  }

  private[scope] final class Live(
    id: Long,
    entries: ConcurrentHashMap[Long, () => Unit]
  ) extends DeferHandle {
    def cancel(): Unit = { entries.remove(id); () }
  }
}
```

### 4. Thread Ownership: `unowned` Flag on `Child`

Rather than introducing a separate `ClassScope` class, we add a private flag
to the existing `Scope.Child`:

```scala
object Scope {
  final class Child[P <: Scope] private[scope] (
    val parent: P,
    protected val finalizers: Finalizers,
    private[scope] val owner: AnyRef,
    private[scope] val unowned: Boolean = false   // NEW
  ) extends Scope {
    type Parent = P

    def isOwner: Boolean =
      if (unowned) true                           // class scope: any thread
      else PlatformScope.isOwner(owner)           // lexical scope: creating thread only

    private[scope] def close(): Finalization = finalizers.runAll()

    type $[+A]
    protected def $wrap[A](a: A): $[A]    = a.asInstanceOf[$[A]]
    protected def $unwrap[A](sa: $[A]): A = sa.asInstanceOf[A]
  }
}
```

**Why a flag, not a subclass?**

- `Child` and the hypothetical `ClassScope` are structurally identical except
  for `isOwner`. A boolean flag avoids a second class, simplifies pattern
  matching, and keeps the sealed hierarchy small.
- The flag is `private[scope]` — users cannot set it. Only the DI system (via
  `open()`) creates unowned children. Users create owned children via
  `scoped { }`.

**Why unowned?**

A class's methods can be called from any thread. If the scope injected into a
class checked thread identity (like a lexical scope), then calling
`scope.scoped { child => ... }` from a different thread would fail the
`isOwner` check. Unowned scopes allow any thread to create child scopes.

**Why private?**

Unowned scopes are safe only when their lifecycle is managed by the DI system
(closed by parent's finalizer or by refcounting). If users could create unowned
scopes, they'd be responsible for closing them — which is error-prone and would
undermine the structured lifetime guarantee. By keeping the flag
`private[scope]`, we ensure unowned scopes are only created in controlled
contexts (inside `Resource.make`).

### 5. Changes to `Resource`

#### `Resource.make`: `Finalizer` → `Scope`

```scala
// Before
sealed trait Resource[+A] {
  private[scope] def make(finalizer: Finalizer): A
}

// After
sealed trait Resource[+A] {
  private[scope] def make(scope: Scope): A
}
```

Since `Scope extends Finalizer`, all existing code that only calls `defer` on
the parameter continues to work with a rename.

#### `Resource.Unique`

```scala
// Before
private[scope] final class Unique[+A](
  private[scope] val makeFn: Finalizer => A
) extends Resource[A] {
  private[scope] def make(finalizer: Finalizer): A = makeFn(finalizer)
}

// After
private[scope] final class Unique[+A](
  private[scope] val makeFn: Scope => A
) extends Resource[A] {
  private[scope] def make(scope: Scope): A = makeFn(scope)
}
```

For unique resources, `make` receives the allocating scope directly. The
`makeFn` for DI-constructed classes creates an `OpenScope` internally (see
macro changes below). For simple resources (`Resource(value)`,
`Resource.acquireRelease`, etc.), the `makeFn` only calls `defer` on the scope,
which is a no-op change since `Scope extends Finalizer`.

#### `Resource.Shared`

```scala
// Before
private[scope] final class Shared[A](
  private[scope] val makeFn: Finalizer => A
) extends Resource[A] {
  private[scope] def make(realFinalizer: Finalizer): A = {
    // First call: create ProxyFinalizer, call makeFn(proxy)
    // Subsequent calls: return cached value, increment refcount
    // On refcount → 0: proxy.runAll()
  }
}

// After
private[scope] final class Shared[A](
  private[scope] val makeFn: Scope => A
) extends Resource[A] {
  private[scope] def make(scope: Scope): A = {
    // First call: Scope.global.open(), call makeFn(openScope.scope)
    // Subsequent calls: return cached value, increment refcount
    // On refcount → 0: openScope.scope.close(), openScope.handle.cancel()
  }
}
```

The `ProxyFinalizer` is replaced by a real `OpenScope` parented to
`Scope.global`. The shared resource's `ClassScope` outlives any individual
allocating scope. Its lifetime is governed by refcounting: when the last
reference drops, the scope is closed and the finalizer handle is cancelled
(removing it from `Scope.global`'s finalizer list).

**Why `Scope.global` as parent?** A shared resource outlives every individual
scope that allocates it. The only scope guaranteed to outlive all of them is
`Scope.global`. Since `Scope.global` has `type $[+A] = A`, calling
`Scope.global.open()` returns an `OpenScope` with no wrapping — the
`$[OpenScope]` is just `OpenScope`.

**`SharedState` changes:**

```scala
// Before
final case class Created[A](value: A, proxy: ProxyFinalizer, refCount: Int)

// After
final case class Created[A](value: A, openScope: OpenScope, refCount: Int)
```

#### `Resource` Factory Methods

These use the scope only for `defer`, so the change is a parameter rename:

```scala
// Before
def apply[A](value: => A): Resource[A] = new Unique[A](finalizer => { ... finalizer.defer(...) ... })
def acquireRelease[A](acquire: => A)(release: A => Unit): Resource[A] = new Unique[A](finalizer => { ... })
def fromAutoCloseable[A <: AutoCloseable](thunk: => A): Resource[A] = new Unique[A](finalizer => { ... })
def shared[A](f: Finalizer => A): Resource[A] = new Shared(f)
def unique[A](f: Finalizer => A): Resource[A] = new Unique(f)

// After
def apply[A](value: => A): Resource[A] = new Unique[A](scope => { ... scope.defer(...) ... })
def acquireRelease[A](acquire: => A)(release: A => Unit): Resource[A] = new Unique[A](scope => { ... })
def fromAutoCloseable[A <: AutoCloseable](thunk: => A): Resource[A] = new Unique[A](scope => { ... })
def shared[A](f: Scope => A): Resource[A] = new Shared(f)
def unique[A](f: Scope => A): Resource[A] = new Unique(f)
```

### 6. Changes to `Wire`

```scala
// Before
final case class Shared[-In, +Out](makeFn: (Finalizer, Context[In]) => Out) extends Wire[In, Out] {
  def make(finalizer: Finalizer, ctx: Context[In]): Out = makeFn(finalizer, ctx)
  def toResource(deps: Context[In]): Resource[Out] =
    Resource.shared[Out](finalizer => this.makeFn(finalizer, deps))
}

final case class Unique[-In, +Out](makeFn: (Finalizer, Context[In]) => Out) extends Wire[In, Out] {
  def make(finalizer: Finalizer, ctx: Context[In]): Out = makeFn(finalizer, ctx)
  def toResource(deps: Context[In]): Resource[Out] =
    Resource.unique[Out](finalizer => this.makeFn(finalizer, deps))
}

// After
final case class Shared[-In, +Out](makeFn: (Scope, Context[In]) => Out) extends Wire[In, Out] {
  def make(scope: Scope, ctx: Context[In]): Out = makeFn(scope, ctx)
  def toResource(deps: Context[In]): Resource[Out] =
    Resource.shared[Out](scope => this.makeFn(scope, deps))
}

final case class Unique[-In, +Out](makeFn: (Scope, Context[In]) => Out) extends Wire[In, Out] {
  def make(scope: Scope, ctx: Context[In]): Out = makeFn(scope, ctx)
  def toResource(deps: Context[In]): Resource[Out] =
    Resource.unique[Out](scope => this.makeFn(scope, deps))
}
```

### 7. Macro Changes

The macros (`WireCodeGen`, `ResourceMacros`, and their Scala 2 equivalents)
need to recognize **both** `Finalizer` and `Scope` as injectable parameters
(neither counts as a dependency in the `In` type).

**Parameter classification** (in `MacroCore`):

```scala
// Before: only Finalizer is special
def classifyParam(paramType: TypeRepr): Option[TypeRepr] =
  if (isFinalizerType(paramType)) None
  else Some(paramType)

// After: both Finalizer and Scope are special
def classifyParam(paramType: TypeRepr): Option[TypeRepr] =
  if (isFinalizerType(paramType) || isScopeType(paramType)) None
  else Some(paramType)
```

Note: `Scope extends Finalizer`, so `isFinalizerType` would match `Scope` too.
We add an explicit `isScopeType` check for clarity in generated code (Scope
params receive a `Scope`, Finalizer params receive the scope-as-Finalizer).

**Code generation** (in `WireCodeGen.generateArgTerm`):

```scala
// Before
if (MacroCore.isFinalizerType(paramType)) finalizerExpr.asTerm

// After
if (MacroCore.isScopeType(paramType)) scopeExpr.asTerm
else if (MacroCore.isFinalizerType(paramType)) scopeExpr.asTerm  // Scope IS-A Finalizer
```

Both `Scope` and `Finalizer` params receive the same scope object. Classes
that only need `defer` can continue declaring `implicit finalizer: Finalizer`.
Classes that need full scope access declare `implicit scope: Scope`.

**`ResourceMacros` changes**: Where `Resource.shared` / `Resource.unique` are
generated with `Finalizer => A` lambdas, change to `Scope => A`.

### 8. `$[A]` for Class Scopes

The class scope has its own path-dependent `$[+A]` type, just like `Child`. But
this type is never exposed to users:

- Classes receive `Scope` (abstract), not `ClassScope` (concrete).
- Classes should **not** call `scope.allocate(...)` in their body — that would
  produce `scope.$[A]`, which is sticky (every `use` returns another
  `scope.$[B]`) and cannot escape the class without the scope reference.
- Instead, long-lived resources should be **constructor parameters** (the DI
  system handles their lifecycle). The scope is used for:
  - `scope.defer { ... }` — registering cleanup.
  - `scope.scoped { child => ... }` — per-method child scopes.
- The `$` algebra works out at the DI level: `Resource.make` commutes and fuses
  the `$` wrappers so that the parent sees a single `$[T]` for the whole
  resource, not nested `$[$[...]]`.

### 9. Files Changed

| File | Change |
| ---- | ------ |
| **`Finalizer.scala`** | Change `defer` return type from `Unit` to `DeferHandle`. Add `DeferHandle` abstract class and companion. |
| **`Finalizers.scala`** | Rewrite from lock-free linked list to `ConcurrentHashMap[Long, () => Unit]` + `AtomicLong` counter. `add` returns `DeferHandle`. `cancel` does true O(1) removal. `runAll` sorts by ID descending for LIFO order. |
| **`Scope.scala`** | Add `unowned` flag to `Child` constructor. Add `private[scope] def open(): $[OpenScope]`. Update `defer` override to match new `DeferHandle` return type. Add `OpenScope` case class to companion. |
| **`Resource.scala`** | Change `make(Finalizer)` → `make(Scope)`. Change `Unique`/`Shared` `makeFn` from `Finalizer => A` to `Scope => A`. Replace `ProxyFinalizer` usage in `Shared` with `OpenScope`. Update factory methods (`apply`, `acquireRelease`, `fromAutoCloseable`, `shared`, `unique`). |
| **`Wire.scala`** | Change `makeFn` from `(Finalizer, Context[In]) => Out` to `(Scope, Context[In]) => Out`. Update `make`, `toResource`. |
| **`ProxyFinalizer.scala`** | **Delete.** Replaced by `OpenScope` + unowned `Child`. |
| **`WireVersionSpecific.scala`** (both Scala 2 & 3) | Change `Finalizer` → `Scope` in `make` method and `fromFunction` signatures. |
| **`WireCodeGen.scala`** (Scala 3) | Rename `finalizerExpr` to `scopeExpr`. Add `isScopeType` check in `generateArgTerm`. Change generated wire lambdas from `(Finalizer, Context) =>` to `(Scope, Context) =>`. |
| **`MacroCore.scala`** (both Scala 2 & 3) | Add `isScopeType` helper (Scala 3 already has `isFinalizerType` which matches `Scope` too, but explicit `isScopeType` needed for code gen to pass correct type). Update `classifyParam` / `classifyAndExtractDep` to exclude `Scope`-typed params from dependencies. |
| **`ResourceMacros.scala`** (Scala 3) | Change generated `Resource.shared`/`Resource.unique` lambdas from `Finalizer => A` to `Scope => A`. Rename `finalizer` to `scope` in generated code. |
| **`ResourceVersionSpecific.scala`** (Scala 2) | Same changes as Scala 3 `ResourceMacros`. |
| **`ScopeVersionSpecific.scala`** (both Scala 2 & 3) | No change to `scoped` — it still creates lexical (owned) `Child` scopes. |
| **`ScopeImplVersionSpecific.scala`** (both) | No structural changes needed — `Child` retains same `$[A]` type mechanics. |
| **Tests** | Update `FinalizerInjectionSpec` to also test `Scope` injection. Update `ResourceSpec` and `WireSpec` for new `makeFn` signatures. Add tests for `DeferHandle.cancel()` (true removal), unowned `Child` multi-threaded access, and `Resource.Shared` with `OpenScope`. |

---

## Alternatives Considered

### A. Create a child scope via `scoped { }` for each resource

**Rejected.** `scoped` is lexical — it creates a scope, runs a block, and
closes the scope. A resource needs a scope that outlives the constructor call.
`scoped` would close the scope immediately after construction, making it useless
for method calls.

### B. `ProxyScope` (a `Scope` subclass that mimics `ProxyFinalizer`)

**Rejected in favor of `open()` on existing `Child`.** A `ProxyScope` would
have been a `Scope` with no meaningful parent, detached from the scope
hierarchy. Using `open()` to create an unowned `Child` keeps the scope tree
well-formed with correct parent linkage, and subsumes what `ProxyScope` would
have provided.

### C. Keep `Finalizer` for `Resource.Shared`, use `Scope` only for `Resource.Unique`

**Rejected.** This would mean shared services cannot create child scopes.
A shared `Database` pool should be able to use `scope.scoped { }` for
per-connection cleanup, just like a unique service. Splitting the types creates
a confusing asymmetry and limits what shared services can do.

### D. Inject the parent scope directly (no separate child scope)

**Rejected.** The parent scope enforces single-thread ownership via `isOwner`.
If a class receives the parent's lexical scope and calls `scope.scoped { }`
from a different thread (e.g., a request handler), the ownership check fails.
The `unowned` flag on a dedicated child scope relaxes thread ownership for
class-internal usage while keeping the parent's lexical scope safe.

### F. Separate `ClassScope` subclass instead of `unowned` flag

**Rejected.** `ClassScope` and `Child` are structurally identical except for
`isOwner` behavior. A boolean flag avoids a redundant class, simplifies
pattern matching, and keeps the sealed hierarchy small. The flag is
`private[scope]` so users cannot create unowned scopes.

### E. Let users create unowned scopes manually

**Rejected.** A manually created unowned scope requires the user to ensure it
is closed. Forgetting to close it leaks resources. By keeping the `unowned`
flag `private[scope]` and tying the scope's lifecycle to the DI system (via
`open()` which registers a parent finalizer), we guarantee cleanup.

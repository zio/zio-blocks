---
id: scope
title: "Scope"
---

ZIO Blocks' `zio.blocks.scope` module is a **compile-time safe, zero-cost** resource management library for **Scala 3** (and Scala 2.13). It prevents a large class of lifetime bugs by tagging allocated values with an *unnameable*, scope-specific type and restricting how those values may be used.

Each scope instance has a distinct `$[A]` type that is unique to that scope and cannot be named or manipulated directly. This means values allocated in one scope have a structurally incompatible type from values in another scope — attempting to use a resource outside its owning scope is a **compile-time type error**, not a runtime crash. The `$` operator macro further restricts how you can use these values: it only allows using them as method/field receivers, preventing accidental capture in closures or escape to outer scopes. Combined with the `Unscoped` typeclass that marks pure data safe to return from a scope, this creates multiple layers of compile-time protection.

At runtime the model stays simple:

- **Allocate eagerly** (no lazy thunks) — When you call `allocate(resource)`, the resource is acquired immediately, not deferred to some later point. This makes resource lifetimes predictable and matches your mental model of when acquisition happens.
- **Register finalizers** — As each resource is acquired, its cleanup function (or `close()` method for `AutoCloseable`) is registered in a stack-like registry. This registry is part of every scope.
- **Run finalizers deterministically** when a scope closes (**LIFO** order) — When a scope exits (normally or via exception), all registered finalizers execute in reverse order (last-registered-first-executed). This ensures that resources that depend on each other close in the correct order.
- **Collect finalizer failures** into a `Finalization` — If a finalizer throws an exception, Scope doesn't stop; it collects all exceptions and either wraps them in a `Finalization` or suppresses them depending on context. This ensures all cleanup runs even if some finalizers fail.

## Why Scope?

Most resource bugs in Scala are "escape" bugs—scenarios where a resource is used outside of its intended lifetime, leading to undefined behavior, crashes, or data corruption:

- **Storing in fields:** You open a database connection and store it in a field, intending to close it in a finalizer. But if the finalizer runs before you're truly done with the connection, or if you forget to close it, the connection is silently used after closure.
- **Capturing in closures:** You create a file handle and pass it to an async framework via a callback. The callback might be invoked long after your scope has closed and the file has been released, causing the program to crash or silently read/write corrupted data.
- **Passing to untrusted code:** You pass a resource to a library function that might store a reference and use it later, outside your scope. You have no way to know when it's safe to close.
- **Mixing lifetimes:** In large codebases, it becomes unclear which scope owns which resource. A developer might use a resource in the wrong scope, or two scopes might try to close the same resource.

Scope addresses these with a *tight* design. Each design choice solves a specific problem and works together with the others:

1. **Compile-time leak prevention via type tagging** — Every scope has its own `$[A]` type, combined with the `$` macro that restricts how you can use values and the `Unscoped` typeclass that marks safe return types. Together, these prevent returning resources from their scope at compile time. No runtime wrapper objects needed.

2. **Zero runtime overhead** — Scoped values erase to the underlying type `A` at runtime (via casts). There's no boxing, no extra objects, no GC pressure. The compile-time safety is "free."

3. **Eager allocation** — Resources are acquired immediately when you call `allocate`, not deferred to some later point. This makes lifetimes predictable and your code matches your mental model.

4. **Deterministic, LIFO finalization** — Finalizers are guaranteed to run in reverse order of allocation when a scope closes. If acquisition order implies dependencies (common in resource hierarchies), cleanup order is automatically correct. Exceptions in finalizers are collected rather than stopping cleanup.

5. **Structured scopes with parent-child relationships** — Scopes form a hierarchy; children always close before parents. The `lower` operator lets you safely use parent-scoped values in children, since parent will outlive child.

6. **Escape hatch for interop** — The `leak` function lets you break the type system when integrating with legacy code, but it emits a compiler warning so you don't accidentally bypass safety by mistake.

If you've used `try/finally`, `Using`, or ZIO's `Scope`, this is the same problem space—but optimized for **synchronous code** with **compile-time boundaries**.

---

## Getting Started

If you're new to Scope, the [Scope Tutorial](../../../guides/compile-time-resource-safety-with-scope.md) provides a comprehensive step-by-step introduction with realistic examples and explanations of the core concepts. This reference page covers the API details; the tutorial covers the "why" and "how."

## Quick start (Scala 3)

Here's a minimal example showing resource allocation, usage, and cleanup:

```scala
import zio.blocks.scope.*

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

object QuickStart {
  def quickStart(): Unit = {
    val out: String =
      Scope.global.scoped { scope =>
        import scope.*

        val db: $[Database] =
          Resource.fromAutoCloseable(new Database).allocate

        // Safe access: the lambda parameter can only be used as a receiver
        $(db)(_.query("SELECT 1"))
      }

    println(out)
  }
}
```

What's happening in this code:

**Allocating resources in a scope.** When you call `Resource.fromAutoCloseable(new Database).allocate`, you're acquiring a database connection. The `allocate` method returns a **scoped value** of type `scope.$[Database]`—notice the `$` wrapper. This type is unique to the `scope` instance. You can import the scope to use the short form `$[Database]`.

**The `$` operator restricts access.** You cannot call `db.query(...)` directly on `$[Database]` because the methods are hidden at the type level. Instead, you use the `$` access operator: `$(db)(f)`, which takes a lambda. The lambda's parameter must be used only as a receiver (for method/field access), preventing accidental capture or escape.

**Safe return from scoped.** The `scoped` block returns a plain `String` (the result of `_.query("SELECT 1")`). This is safe because `String` is marked as `Unscoped`—a typeclass that says "this type is pure data, safe to leave a scope." If you tried to return `db` instead, the compiler would error.

**LIFO cleanup.** When the `scoped` block exits (normally or via exception), all finalizers run in reverse order. The database's `close()` method was registered automatically because `Database` extends `AutoCloseable`. So cleanup happens at the right time, in the right order, even if an exception occurred.

---

## Core mental model

### 1) `Scope`: finalizers + type identity

`Scope` is a finalizer registry plus a unique type identity:

- `type $[+A]` — a scope-tagged, path-dependent type (erases to `A` at runtime)
- `type Parent <: Scope` / `val parent: Parent` — the scope hierarchy

Every scope instance defines a **different** `$` type, so values from different scopes don't accidentally mix:

```scala
Scope.global.scoped { scope =>
  import scope.*
  val x: $[Int] = 1 // ok (in global, $[A] = A)
}
```

#### Global scope

`Scope.global` is the root:

- In the global scope: `type $[+A] = A` (identity)
- On the JVM: global finalizers run on shutdown via a shutdown hook
- On Scala.js: there is no shutdown hook, so global finalizers are **not** run automatically

---

### 2) Scoped values: `scope.$[A]` / `$[A]`

A value of type `scope.$[A]` means:

> "This is an `A`, but it is only valid while `scope` is alive."

Properties:

- **Zero-cost**: `$[A]` is just `A` at runtime (casts/identity)
- **Incompatible across scopes**: `outer.$[A]` is not `inner.$[A]`
- **Methods are hidden** at the type level; you must use `$` to access

#### Access operator: `(scope $ value)(f)`

The intended way to use a scoped value is:

```scala
(scope $ scopedValue)(a => a.method(...))
```

This is enforced by a macro that checks the lambda uses its parameter only in **receiver position**.

Allowed patterns:

```scala
(scope $ db)(_.query("SELECT 1"))
(scope $ db)(d => d.query("a") + d.query("b"))
(scope $ db)(_.query("x").toUpperCase)
(scope $ db)(_.field) // field access is allowed
```

Patterns rejected at compile time:

```scala
(scope $ db)(d => store(d))            // parameter used as an argument
(scope $ db)(d => () => d.query("x"))  // captured in a nested lambda
(scope $ db)(d => d)                   // returning the parameter
(scope $ db)(d => { val x = d; 1 })    // binding/storing the parameter itself
```

##### "Auto-unwrap" rule (`Unscoped`)

`$` *auto-unwraps* when the result type is known to be safe data:

- if `B: Unscoped` → `(scope $ sa)(f)` returns **`B`**
- otherwise → it returns **`scope.$[B]`**

```scala
Scope.global.scoped { scope =>
  import scope.*

  val db: $[Database] = Resource.from[Database].allocate

  val s: String = $(db)(_.query("SELECT 1"))      // String is Unscoped => unwrapped
  val n: Int    = $(db)(_.query("x").length)      // Int is Unscoped => unwrapped
}
```

##### N-ary `$`: accessing multiple scoped values at once

When a result depends on **two or more** scoped values simultaneously, use the N-ary overloads (`N = 2..5`):

```scala
$(sa1, sa2)((v1, v2) => v1.method(v2.result()))
$(sa1, sa2, sa3)((v1, v2, v3) => v1.query(v2.key()) + v3.tag())
```

The same receiver-only grammar applies to every parameter: each `vi` may only appear as a method receiver (e.g., `vi.method()`). Feeding the *result* of one parameter to a method of another is permitted:

```scala
Scope.global.scoped { scope =>
  import scope.*
  val db:    $[Database]   = Resource.from[Database].allocate
  val cache: $[Cache]      = Resource.from[Cache].allocate

  // d1 and d2 are both receivers; d2.key() produces a plain String arg
  val result: String = $(db, cache)((d1, d2) => d1.query(d2.key()))
}
```

Patterns rejected at compile time (same rules as N=1, applied to each parameter independently):

```scala
$(db, cache)((d1, d2) => d2)              // d2 returned directly
$(db, cache)((d1, d2) => store(d1))       // d1 passed as argument
$(db, cache)((d1, d2) => d1.method(d2))   // d2 as bare arg (not a receiver)
$(db, cache)((d1, d2) => () => d2.query()) // d2 captured in closure
```

The error messages name the offending parameter:

```text
Parameter 2 ('d2') cannot be passed as an argument to a function or method.
Scoped values may only be used as a method receiver (e.g., d2.method()).
```

**Infix syntax** (`scope $ sa`) is only available for N=1. For N≥2, use unqualified syntax after `import scope.*`:

```scala
$(db, cache)((d, c) => d.query(c.key()))   // ✓ unqualified
```

**For N>5**, extract each value in sequence (all results are `Unscoped` strings/values and can be freely combined):

```scala
val q1 = $(db1)(_.query("a"))
val q2 = $(db2)(_.query("b"))
q1 + q2
```

---

### 3) `Resource[A]`: acquisition + finalization

A `Resource[A]` is a **lazy description** of how to acquire a value and register cleanup in a scope. Nothing happens until you call `scope.allocate(resource)` (or `.allocate` syntax). When allocated, the resource is acquired immediately and its cleanup is registered with the scope, guaranteeing LIFO finalization.

For comprehensive documentation of `Resource` constructors, composition, sharing vs uniqueness, and all constructor patterns, see the [Resource reference](../resource.md).

---

### 4) `Unscoped[A]`: types that may escape a scope

`Unscoped[A]` is a marker typeclass for *pure data*. It's used in two places:

1. `Scope.scoped` requires `Unscoped[A]` for the block's result type
   ⇒ prevents returning resources, closures, or scoped values.
2. `$` auto-unwraps results of type `B` when `B: Unscoped`.

Built-in instances include primitives, `String`, many collections/containers, time values, `java.util.UUID`, and `zio.blocks.chunk.Chunk` (when element types are unscoped).

For information on deriving your own `Unscoped` instances and a complete list of built-in instances, see the [Unscoped reference](./unscoped.md).

#### Scope boundary example

Here's how compile-time boundaries prevent leaking:

```scala
import zio.blocks.scope.*

Scope.global.scoped { parent =>
  import parent.*

  val ok: String =
    parent.scoped { child =>
      "hello" // String is Unscoped
    }

  // Does not compile: returning a resourceful value from a scoped block
  // val leaked: Database =
  //   parent.scoped { child =>
  //     import child.*
  //     Resource.fromAutoCloseable(new Database).allocate
  //   }

  ok
}
```

---

### 5) `lower`: using a parent-scoped value in a child scope

Because each scope has its own `$[A]` type, a child cannot directly use a parent's `$[A]`. Use `lower` to retag a parent-scoped value into the child:

```scala
import zio.blocks.scope.*

Scope.global.scoped { outer =>
  import outer.*

  val db: $[Database] = Resource.fromAutoCloseable(new Database).allocate

  outer.scoped { inner =>
    import inner.*
    val innerDb: $[Database] = lower(db)
    $(innerDb)(_.query("child"))
  }
}
```

This is safe because **parents always outlive children** (child finalizers run before the parent closes).

---

### 6) `defer`: manual finalizers (+ cancellation)

Use `defer` to register cleanup. It returns a `DeferHandle` you can cancel:

```scala
import zio.blocks.scope.*

Scope.global.scoped { scope =>
  import scope.*

  val in = new java.io.ByteArrayInputStream(Array[Byte](1, 2, 3))

  val h: DeferHandle =
    defer(in.close())

  val first = in.read()
  println(first)

  // If you already cleaned up manually:
  // h.cancel() // thread-safe, idempotent
}
```

There is also a **package-level** helper that only requires a `Finalizer`:

```scala
import zio.blocks.scope.*

Scope.global.scoped { scope =>
  import scope.*
  implicit val _: Finalizer = scope

  defer(println("cleanup")) // uses the package-level helper
}
```

---

### 7) `open()`: non-lexical, explicitly-managed child scopes

`scoped` ties lifetime to a block. `open()` creates a child scope you close explicitly.

- The child scope is **unowned** (can be used from any thread)
- Still **linked to the parent**: parent closing will also close the child
- You must call `close()` on the handle to detach + finalize now

From `Scope.global` the returned type is `Scope.OpenScope` directly (because global `$[A] = A`):

```scala
import zio.blocks.scope.*

val os: Scope.OpenScope = Scope.global.open()

val db = os.scope.allocate(Resource.fromAutoCloseable(new Database))

// ... use db ...

os.close().orThrow()
```

Inside a child scope, `open()` returns `$[Scope.OpenScope]`. Prefer using it safely via `$`:

```scala
import zio.blocks.scope.*

Scope.global.scoped { parent =>
  import parent.*

  val os: $[Scope.OpenScope] = open()

  $(os) { h =>
    val child = h.scope
    val db    = child.allocate(Resource.fromAutoCloseable(new Database))

    // ...
    h.close().orThrow()
  }
}
```

---

### 8) Escape hatch: `leak`

Sometimes you must hand a raw value to code that cannot work with `$[A]`. Use `leak`:

```scala
import zio.blocks.scope.*

Scope.global.scoped { scope =>
  import scope.*

  val db: $[Database] = Resource.fromAutoCloseable(new Database).allocate

  val raw: Database = leak(db) // emits a compiler warning
  // thirdParty(raw)
}
```

`leak` bypasses compile-time guarantees—use only for unavoidable interop. If the type is genuinely pure data, prefer adding `Unscoped` so you don't need to leak.

---

## Safety model (why leaking is prevented)

Scope's safety comes from *three reinforcing layers*.

### 1) Type barrier: scope-specific `$[A]`

Every scope has a distinct `$[A]` type. You cannot accidentally use values across scopes without an explicit conversion (`lower` for parent → child).

### 2) Controlled access: `$` macro restricts lambda usage

The `$` operator only allows using the unwrapped value as a **method/field receiver**. This prevents:

- returning the resource
- storing it in a local val/var
- passing it as an argument
- capturing it in a closure

Also note: `$` requires a **lambda literal**. Method references / variables are rejected:

```scala
// does not compile:
val f: Database => String = _.query("x")
(scope $ db)(f) // "$ requires a lambda literal ..."
```

### 3) Scope boundary rule: `scoped` requires `Unscoped[A]`

A `scoped { ... }` block can only return pure data (or `Nothing`). Resources and closures cannot escape.

**Pragmatic safety.** The type-level tagging prevents *accidental* scope misuse in normal code, but it is not a security boundary. A determined developer can bypass it via `leak` (which emits a compiler warning), unsafe casts (`asInstanceOf`), or storing scoped references in mutable state (`var`).

### Closed-scope safety (runtime)

If a scope reference escapes its `scoped { }` block and an operation is attempted after closing, Scope throws `IllegalStateException` with a detailed, actionable error message:

- **`allocate`** on a closed scope:

  ```text
  ── Scope Error ─────────────────────────────────────────────────────────────────

    Cannot allocate resource: scope is already closed.

    Scope: Scope.Child

    What happened:
      A call to allocate was made on a scope whose finalizers have
      already run. The resource was never acquired.

    Common causes:
      • A scope reference escaped a scoped { } block (e.g. stored in a
        field, captured in a Future or passed to another thread).
      • close() was called on an OpenScope before all
        allocations inside it completed.

    Fix:
      Call allocate only inside a live scoped { } block, or before
      calling close() on an OpenScope.

      // Correct usage:
      Scope.global.scoped { scope =>
        import scope.*
        val db = allocate(Resource(new Database))
        $(db)(_.query("SELECT 1"))
      }

  ────────────────────────────────────────────────────────────────────────────────
  ```

- **`open()`** on a closed scope gives the same treatment, explaining that no child scope was created and directing the user to call `open()` only on a live scope.

- **`$`** on a closed scope explains that the resource may have already been released and accessing it would be undefined behaviour.

The following operations on a closed scope do **not** throw:

- `defer` — silently ignored (no-op)
- `scoped` — runs normally but creates a born-closed child scope
- `lower` — zero-cost cast, no closed check needed

### Thread Ownership

Scopes enforce **thread affinity** to prevent cross-thread scope misuse. The thread that calls `scoped` becomes the owner of the resulting child scope; only that thread may call `scoped` on it to create grandchild scopes.

#### Ownership rules by scope type

- `Scope.global` — `isOwner` always returns `true`; any thread may create children from it.
- `Scope.Child` — captures the calling thread at construction; `isOwner` checks `Thread.currentThread() eq owner`.
- `Scope.open()` — creates an **unowned** child scope; `isOwner` always returns `true` from any thread (for explicitly managed, cross-thread scopes).

#### Violation error

Calling `scoped` on a `Scope.Child` from the wrong thread throws `IllegalStateException`. The message names both the current thread and the owning thread:

```text
Cannot create child scope: current thread 'pool-1-thread-1' does not own this scope (owner: 'main')
```

This check runs *before* the closed-scope check, so even a closed scope on the wrong thread reports an ownership error.

#### Platform notes

On the JVM, `isOwner` uses `Thread` identity. On Scala.js (single-threaded), `isOwner` always returns `true`.

#### Code example

The following example shows correct single-thread usage. Scope ownership prevents accidentally passing a child scope to another thread:

```scala
import zio.blocks.scope.*

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

object ThreadOwnershipExample {
  // Correct usage: child scopes must be used on the creating thread
  def example(): Unit = {
    Scope.global.scoped { parentScope =>
      import parentScope.*

      val parentDb: $[Database] =
        Resource.fromAutoCloseable(new Database).allocate

      parentScope.scoped { childScope =>
        import childScope.*

        val childDb: $[Database] =
          Resource.fromAutoCloseable(new Database).allocate

        // This is safe: child is created and used on the same thread
        $(childDb)(_.query("SELECT 1"))
      }

      // If you passed childScope to another thread and tried to call scoped on it,
      // you would get IllegalStateException about thread ownership mismatch
    }
  }
}
```

If you need a scope that crosses thread boundaries, use `open()` instead; it creates an unowned scope that any thread may use.

---

## See Also

- [Usage Patterns](./patterns.md)
- [Error Reference](./errors.md)
- [API Reference](./api.md)

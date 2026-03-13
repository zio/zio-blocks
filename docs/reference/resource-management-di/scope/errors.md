---
id: scope-errors
title: "Scope — Error Reference"
---

## Common runtime errors (and what they mean)

These `IllegalStateException`s are thrown when a scope operation is attempted on a closed scope. Each message identifies the scope type, explains what went wrong, lists common causes, and shows a correct usage example.

### `allocate` on a closed scope

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

### `open()` on a closed scope

```text
── Scope Error ─────────────────────────────────────────────────────────────────

  Cannot open child scope: scope is already closed.

  Scope: Scope.Child

  What happened:
    A call to open() was made on a scope whose finalizers have
    already run. No child scope was created.

  Common causes:
    • A scope reference escaped a scoped { } block and open()
      was called after the block exited.
    • close() was called on the parent OpenScope before
      open() was called on it.

  Fix:
    Call open() only on a live (not yet closed) scope.

    // Correct usage:
    Scope.global.scoped { scope =>
      import scope.*
      val child = open()
      $(child)(_.scope.allocate(Resource(new Database)))
    }

────────────────────────────────────────────────────────────────────────────────
```

### `$` on a closed scope

```text
── Scope Error ─────────────────────────────────────────────────────────────────

  Cannot access scoped value: scope is already closed.

  Scope: Scope.Child

  What happened:
    The $ operator was called on a scope whose finalizers have
    already run. The underlying resource may have been released.
    Accessing it would be undefined behavior.

  Common causes:
    • A $[A] value or its owning scope escaped a scoped { }
      block (e.g. captured in a Future, stored in a field, or
      passed to another thread).
    • close() was called on an OpenScope that still has
      live $[A] values being accessed.

  Fix:
    Ensure all $ calls occur strictly within the scoped { }
    block that owns the value, and that the scope has not been closed.

    // Correct usage:
    Scope.global.scoped { scope =>
      import scope.*
      val db = allocate(Resource(new Database))
      $(db)(_.query("SELECT 1"))  // $ used inside the block
    }

────────────────────────────────────────────────────────────────────────────────
```

---

## Common compile errors (and what they mean)

This module produces two kinds of compile-time feedback:

- **Plain macro aborts** for unsafe `$` usage
- **ASCII-rendered** errors/warnings for DI derivation + leak warnings (via `internal.ErrorMessages`)

### Unsafe use inside `$`

All messages name the offending parameter by its 1-based index and source name, and end with the receiver-only reminder. Typical messages:

```text
Parameter 1 ('d') cannot be passed as an argument to a function or method.
Scoped values may only be used as a method receiver (e.g., d.method()).
```

```text
Parameter 1 ('d') must only be used as a method receiver.
It cannot be returned, stored, passed as an argument, or captured.
Scoped values may only be used as a method receiver (e.g., d.method()).
```

```text
Parameter 1 ('d') cannot be captured in a nested lambda, def, or anonymous class.
Scoped values may only be used as a method receiver (e.g., d.method()).
```

```text
Parameter 2 ('cache') cannot be passed as an argument to a function or method.
Scoped values may only be used as a method receiver (e.g., cache.method()).
```

```
$ requires a lambda literal, e.g. $(x)(a => a.method()).
Method references and variables are not supported.
```

### Not a class (`Wire.shared/unique` on a trait / abstract)

```text
── Scope Error ─────────────────────────────────────────────────────────────────

  Cannot derive Wire for MyTrait: not a class.

  Hint: Use Wire.Shared / Wire.Unique directly.

───────────────────────────────────────────────────────────────────────────────
```

### No primary constructor

```text
── Scope Error ─────────────────────────────────────────────────────────────────

  MyType has no primary constructor.

  Hint: Use Wire.Shared / Wire.Unique directly
        with a custom construction strategy.

───────────────────────────────────────────────────────────────────────────────
```

### `Resource.from[T]` used when `T` has dependencies

```text
── Scope Error ─────────────────────────────────────────────────────────────────

  Resource.from[MyService] cannot be derived.

  MyService has dependencies that must be provided:
    • Config
    • Logger

  Hint: Use Resource.from[MyService](wire1, wire2, ...)
        to provide wires for all dependencies.

───────────────────────────────────────────────────────────────────────────────
```

### Unmakeable type (primitives, functions, collections)

```text
── Scope Error ─────────────────────────────────────────────────────────────────

  Cannot auto-create String

  This type (primitive, collection, or function) cannot be auto-created.

  Required by:
  ├── Config
    └── App

  Fix: Provide Wire(value) with the desired value:

    Resource.from[...](
      Wire(...),  // provide a value for String
      ...
    )

───────────────────────────────────────────────────────────────────────────────
```

### Abstract type (trait / abstract class dependency)

```text
── Scope Error ─────────────────────────────────────────────────────────────────

  Cannot auto-create Logger

  This type is abstract (trait or abstract class).

  Required by:
  └── App

  Fix: Provide a wire for a concrete implementation:

    Resource.from[...](
      Wire.shared[ConcreteImpl],  // provides Logger
      ...
    )

───────────────────────────────────────────────────────────────────────────────
```

### Duplicate providers (ambiguous wires)

```text
── Scope Error ────────────────────────────────────────────────────────────────

  Multiple providers for Service

  Conflicting wires:
    1. LiveService
    2. TestService

  Hint: Remove duplicate wires or use distinct wrapper types.

───────────────────────────────────────────────────────────────────────────────
```

### Dependency cycle

```text
── Scope Error ────────────────────────────────────────────────────────────────

  Dependency cycle detected

  Cycle:
    ┌───────────┐
    │           ▼
    A ──► B ──► C
    ▲           │
    └───────────┘

  Break the cycle by:
    • Introducing an interface/trait
    • Using lazy initialization
    • Restructuring dependencies

───────────────────────────────────────────────────────────────────────────────
```

### Subtype conflict (related dependency types)

```text
── Scope Error ────────────────────────────────────────────────────────────────

  Dependency type conflict in MyService

  FileInputStream is a subtype of InputStream.

  When both types are dependencies, Context cannot reliably distinguish
  them. The more specific type may be retrieved when the more general
  type is requested.

  To fix this, wrap one or both types in a distinct wrapper:

    case class WrappedInputStream(value: InputStream)
  or
    opaque type WrappedInputStream = InputStream

───────────────────────────────────────────────────────────────────────────────
```

### Duplicate parameter types in a constructor

```text
── Scope Error ────────────────────────────────────────────────────────────────

  Constructor of App has multiple parameters of type String

  Context is type-indexed and cannot supply distinct values for the same type.

  Fix: Wrap one parameter in an opaque type to distinguish them:

    opaque type FirstString = String
  or
    case class FirstString(value: String)

───────────────────────────────────────────────────────────────────────────────
```

### Leak warning

```text
── Scope Warning ───────────────────────────────────────────────────────────────

  leak(db)
       ^
       |

  Warning: db is being leaked from scope zio.blocks.scope.Scope.Child[...].
  This may result in undefined behavior.

  Hint:
     If you know this data type is not resourceful, then add an Unscoped
     instance for it so you do not need to leak it.

───────────────────────────────────────────────────────────────────────────────
```

---

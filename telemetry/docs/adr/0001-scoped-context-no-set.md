# ADR 0001: ContextStorage without set() — immutable context propagation

## Status

Accepted

## Context

The telemetry module needs to propagate contextual data (SpanContext, log
annotations) through call stacks across JVM (ScopedValue on JDK 25+) and
Scala.js (global var).

The initial design used a `ContextStorage` trait with three methods:

- `get()` — read current value
- `set(value)` — imperatively change current value
- `scoped(value)(f)` — run block with temporary value, auto-restore

The `set()` method caused problems:

1. JDK 25's `ScopedValue` is immutable — it has no `set()`. We had to add a
   ThreadLocal fallback alongside ScopedValue, creating a hybrid that was
   confusing and error-prone.
2. `set()` enables context leaks — a caller can change context without
   restoring it.
3. Code review questioned why we had both ThreadLocal AND ScopedValue.

## Decision

Remove `set()` from the `ContextStorage` trait. The trait has only:

- `get(): A` — read current value
- `scoped[B](value: A)(f: => B): B` — run block with value, auto-restore

Platform implementations:

- **JVM**: Pure `ScopedValue` (JDK 25+). No ThreadLocal anywhere.
  Uses `ScopedValue.where(sv, value).call(...)`.
- **JS**: Simple `var` with save/restore in `scoped()`.

LogAnnotations (JVM) now delegates to `ContextStorage` instead of maintaining
its own ScopedValue+ThreadLocal hybrid.

## Consequences

### Positive

- JVM implementation is pure ScopedValue — aligns with JDK 25 design
- No ThreadLocal anywhere — eliminates virtual thread context pollution
- Impossible to leak context — `scoped()` always restores
- Each platform implementation reads naturally in isolation
- LogAnnotations simplified — delegates to ContextStorage

### Negative

- Code that previously used `set()` must restructure to use `scoped()`.
  This means wrapping the relevant code block in a scoped call instead
  of imperatively setting and unsetting.

### Neutral

- The shared trait is minimal (2 methods). Platform implementations
  decide everything else.
- `ContextStorage` name kept (not renamed to `ScopedContext`) to minimize
  diff size and preserve familiarity.

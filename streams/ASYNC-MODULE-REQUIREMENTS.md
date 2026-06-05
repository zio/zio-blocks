# Async-module additions required by async streams

> **Status: SPEC — not yet implemented.** None of A1/A2/A3 exist in the module
> source yet; this is the spec to implement. The streams plan
> (`streams/ASYNC-READERS-PLAN.md`) is written as if these are **delivered before
> streams Phase 0 starts** — that is a sequencing assumption, not a claim about
> current source.
>
> Audience: whoever extends `zio-blocks-async`. This is the **minimum** surface
> the async-streams work needs from the `Async` module. Grounded in the current
> source — `Async` is already a real poll/waker runtime with a tested algebra
> (`map`/`flatMap`/`catchAll`/`zipWith`/`tap`/`ensuring`/`collectAll`) and real
> drivers (`awaitSuspended`/`.block` on JVM, `AsyncInterop.toFuture`/`drive` on
> JS, `Async.async { … await … }`, and the `Completer`/`Waker` push bridge). The
> items below are the few things that runtime does **not** yet expose.

## Why anything is needed at all

The `Async` representation is deliberately sealed: `AsyncEncoding` declares
`Async[+A] >: Pollable[A]` with the `= Any` rep hidden. A `Pollable` therefore
flows **into** an `Async` (the lower bound), but callers **cannot pull a
`Pollable` back out** or drive an arbitrary `Async` without the internal
`asInstanceOf[Any]` hack. Streams must not depend on that hack, so the module has
to expose a sanctioned way to *run* an `Async`. That single entrypoint also
happens to be where cancellation and `fork` naturally live.

---

## 1. `unsafeRunAsync` — cancellable, callback-based runner (REQUIRED, core)

```scala
// in object Async (or a sanctioned runner object), both JVM and JS
def unsafeRunAsync[A](fa: Async[A])(cb: Either[Throwable, A] => Unit): Cancelable

trait Cancelable {
  /** Idempotent, synchronous. Stops the driver loop; a no-op after completion. */
  def cancel(): Unit
}
```

This is the keystone — one primitive serving three roles:

- **Sanctioned driver.** The only legal way for streams to run an arbitrary
  `Async` without reaching through the hidden `= Any` encoding.
- **Cancellation.** The returned `Cancelable` stops the run.
- **`fork` base.** A fiber is built on this run, but `unsafeRunAsync` alone is NOT
  a fiber — it takes one callback and has no stored result/listener registry. The
  streams `AsyncFiber` wraps one `unsafeRunAsync` run with an atomic one-shot
  result cell + listener list (see the streams plan §5.3). No separate module
  `fork` primitive is needed, but the wrapper is mandatory.

### Required semantics (these are the test obligations)

1. **Callback is AT-MOST-once** (not "exactly once"). It is invoked exactly once
   **iff** the run reaches success/failure before cancellation wins. If
   `cancel()` wins first, `cb` is **never** invoked.
2. **Cancel vs completion linearize through one atomic terminal state.** If
   completion linearizes first, `cb` may run; if `cancel()` linearizes first, `cb`
   must not run. `cancel()` after completion is a no-op. `cancel()` is idempotent.
3. **Callback execution context.** `cb` may run **synchronously on the caller
   thread** for an already-ready `Async` (callers must tolerate `cb` firing
   *before* `unsafeRunAsync` returns its `Cancelable`), or on the driver
   worker/microtask for a suspended `Async`. `cb` is invoked only after the run
   has recorded a terminal state, and **never while holding an internal driver
   lock**.
4. **No poll reentrancy.** `wake()` only *schedules* a re-poll — it never polls
   inline and never mutates driver state. The driver never re-enters `poll` while
   a `poll` is executing, and never polls concurrently. `cb` may start another
   `unsafeRunAsync` run, but must not be invoked from inside a `poll` call before
   the driver has recorded terminal state.
5. **`Failure` is terminal** — surfaced as `cb(Left(cause))`, not treated as a
   pending `Pollable`.
6. **Thrown `poll` ⇒ failure.** A `Throwable` escaping `poll` becomes
   `cb(Left(t))`. (Note: the current `AsyncInterop.drive` has no try/catch around
   `poll` — the runner must add one.)
7. **`cancel()` does NOT guarantee quiescence.** It prevents *new* polls and
   suppresses `cb`, but does **not** guarantee an already-running `poll` has
   returned, nor that an external leaf callback won't fire later. (This is the
   contract streams must design their readers against — see the streams plan's
   `AsyncReader` lifecycle.) It does **not** abort an in-flight leaf
   (socket/promise); that is the source's job (see "Out of scope").
8. **No lost wakeups.** A `wake()` that races the poll loop must still cause a
   re-poll (the parker already does this via reset-under-lock; the JS loop via
   the microtask schedule).
9. **JVM memory visibility.** Run state is guarded by volatile/atomic/synchronized
   semantics: a successful completion safely publishes the result to `cb`, and
   `cancel()` safely publishes the cancelled state to the driver loop.

### Implementation sketch (you already have most of it)

- **JVM:** run an interruptible `awaitSuspended`/`.block` on a (virtual) thread;
  `cancel()` sets a flag and `interrupt()`s the thread so a parked `poll` unparks.
  (This is `AsyncInterop.toFuture`'s worker pattern + interruption + a callback.)
- **JS:** the existing `AsyncInterop.drive` microtask loop, plus a `cancelled`
  flag checked (a) before scheduling the next microtask, (b) at the top of the
  microtask, (c) before each `poll`, and (d) before invoking `cb`. **Do not** make
  streams call `toFuture`/`toJsPromise` for cancellable runs — those own a private
  loop with no stop hook; expose the cancellable variant instead.

---

## 2. `raceAll` / select — first-of-N (REQUIRED only for `merge`, deferrable)

Needed for `merge`/`mapPar`, which must wait on N pendings at once — impossible
with the sequential `zipWith`/`collectAll` and a single-waker `poll`. Must live
in the module because it needs the `Pollable`/`Waker` internals (one shared inner
waker re-armed across children; the latest downstream waker stored in an
`AtomicReference` so a completion/replacement race never drops the value or wake).

**A one-shot `raceAll(Iterable[Async[A]]): Async[A]` is NOT sufficient for
`merge`.** `merge` re-races on every emitted element, so a slow child that keeps
losing would accumulate stale loser listeners/wakers on each re-race — an O(wins)
listener leak. Choose ONE of:

### Option 1 — leak-safe repeatable `raceAll` (weaker API, stronger contract)
```scala
def raceAll[A](as: Iterable[Async[A]]): Async[A]
```
Contract: safe to call repeatedly over overlapping pending children **without
accumulating stale loser listeners** (each re-race must *replace*, not append,
the waker it registered on a still-pending child).

### Option 2 — reusable `Select` (preferred for `merge`)
```scala
/** Winner identity + value WITHOUT a tuple (merge must know which child won so it
  * can re-arm/remove it). */
final class Selected[+A](val key: Int, val value: A)

trait Select[A] {
  def await: Async[Selected[A]]     // completes with the next ready child + its key
  def replace(key: Int, next: Async[A]): Unit
  def remove(key: Int): Unit
  def cancelRegistrations(): Unit   // drop all wakers this select placed on children
}
```
`await` returns the winning child's `key` so `merge` can re-arm it via
`replace(key, …)` (O(1) registration — no per-element listener growth) or drop it
via `remove(key)` at EOS. A bare `Async[A]` would lose the key and is insufficient.

### Required guarantees (either option)
- **Empty input** → `raceAll(Nil) = Async.fail(IllegalArgumentException)` (streams
  handle empty-merge separately). Define it; don't leave it UB.
- **Failure** → the first child to complete (success **or** `Failure`) wins;
  `Failure` is terminal and returned as the race failure. Loser cleanup on failure
  is the caller's job (via child fibers/readers).
- **Losers are untouched** — `raceAll`/select must NOT cancel, interrupt, close,
  or complete losing children. (`merge` needs them alive.)
- **Tie-breaking** — input-order tie-break is acceptable; callers needing fairness
  must rotate input order (`merge` rotates child order after each win to avoid
  starving high-index children). State this explicitly.
- **Complexity** — O(n) per race in v1; acceptable if documented.

The non-concurrent reader core (Phases 0–6) does **not** need this; defer it to
the concurrency phase.

---

## 3. Null-completion fix (CORRECTNESS, not a feature)

Two slow paths use `null` as "not completed yet", so a value that legitimately
completes with `null` misfires:

- `Completer.succeed(null)` → `settle(null)`; on the empty branch,
  `compareAndSet(null, null)` is a no-op, state stays empty, the `Async`
  **never completes**.
- `EnsuringPollable.outcome` starts `null` meaning "`pa` still pending"; when
  `pa` completes with raw `null`, `outcome = next` (= `null`) is re-read as
  pending **forever** — finalizer never runs, result never propagates.

**Fix:** a single internal `NullValue` sentinel for completed-null, routed
through `Completer.settle`/`peek`/`poll`, `EnsuringPollable`, and any other
slow-path that overloads `null`. Add a regression test per site.

Streams can *avoid* this by never handing `null` to `Async` (they use a non-null
internal EOS sentinel), so it is not strictly blocking — but it is a real latent
bug worth fixing while the module is open.

---

## Out of scope (do NOT add for streams)

- **Leaf/resource cancellation / drop hooks.** Aborting an in-flight socket,
  file read, or JS promise is handled at the stream layer via
  `AsyncReader.cancel()`/`close()`. `Async` cancellation is driver-level only.
- **Timers / `sleep`, scheduler configuration, changed error type.** Readers
  carry `Throwable` (or a stream-level `StreamError`) over the existing surface;
  none of this is required.

---

## Summary

| # | Addition | Status | Needed for |
|---|----------|--------|-----------|
| 1 | `unsafeRunAsync(fa)(cb): Cancelable` (JVM + JS) | **required** | the entire core — driver, cancellation, fork |
| 2 | `raceAll` / public select | required, deferrable | `merge` / `mapPar` (concurrency) |
| 3 | null-completion fix (`Completer` + `EnsuringPollable`) | correctness | hygiene; avoidable streams-side |

Bluntly: **add #1** and the async-streams core is unblocked. Add #2 when
concurrency starts. Fix #3 for hygiene.

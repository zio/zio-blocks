/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.async

import scala.annotation.tailrec

import zio.blocks.async.internal.{AsyncRunner, PlatformAsync}

/**
 * Constructors for [[Async]] values.
 *
 * An `Async[A]` is created with one of [[succeed]], [[fail]], [[attempt]], or
 * [[promise]] (a bare `A` is not itself an `Async[A]`). The transformation and
 * combination operators (`map`, `flatMap`, `catchAll`, `await`, ...) become
 * available as extension methods on `Async[A]` after importing
 * `zio.blocks.async._`.
 */
object Async extends AsyncCompanionVersionSpecific {

  /**
   * A "fiber" / green-thread handle: an in-flight [[Async]] that is being
   * driven eagerly, can be [[Cancelable.cancel]]led, and can be
   * [[Pollable.poll]] ed to observe (or join) its outcome. A [[Running]] is
   * itself an `Async[A]`.
   */
  abstract class Running[+A] extends Pollable[A] with Cancelable

  /**
   * Lift an already-available value into a successful [[Async]].
   *
   * The value may itself be an `Async` (or a [[Pollable]]): such a value is
   * carried as '''data''', one nesting level at a time — `map`/`flatMap`
   * continuations, `.block`, and the drivers deliver it unwrapped exactly one
   * layer per delivery (`flatten` peels one level), never silently running it
   * as a computation. Sequencing effects is `flatMap`'s job; reach for nesting
   * only when an effect value is genuinely the payload.
   */
  def succeed[A](a: A): Async[A] = AsyncEncoding.liftSuccess(a)

  /**
   * Create a failed [[Async]] carrying `cause`. A failed value short-circuits
   * `map` / `flatMap` without invoking the continuation, is recoverable with
   * `.catchAll`, and is re-thrown by `.block`.
   */
  def fail(cause: Throwable): Async[Nothing] = new Failure(cause)

  /**
   * Evaluate `body` eagerly, capturing any thrown [[Throwable]] as a failed
   * [[Async]] (see [[fail]]). The standard way to bridge throw-based code into
   * `Async` so that `.catchAll` can recover the error.
   *
   * Note: `attempt` catches every `Throwable`, with no non-fatal/fatal
   * distinction. Callers who want fatal errors to propagate should rethrow them
   * from their recovery handler.
   */
  def attempt[A](body: => A): Async[A] =
    try succeed(body)
    catch { case t: Throwable => fail(t) }

  // The public `promise` lives in `AsyncCompanionVersionSpecific` (mixed in
  // above) so Scala 3 can offer a `Completer[A] ?=> Unit` context-function
  // body while Scala 2 keeps the `Completer[A] => Unit` shape — single API
  // name across versions, version-appropriate parameter shape. Both
  // implementations delegate to the package-private helper below.

  /**
   * Cross-version implementation of [[promise]]. Takes a plain
   * `Completer[A] => Unit` body so it is callable from `private[async]` code
   * (e.g. `AsyncInterop`) regardless of Scala version, even though the public
   * `promise` shape differs between Scala 2 and Scala 3.
   */
  private[async] def promiseInternal[A](body: Completer[A] => Unit): Async[A] = {
    val c = new Completer[A]
    body(c)
    c.peek
  }

  /**
   * Evaluate `body` on a background worker (JVM) or microtask (JS) and return a
   * [[Running]] for the result — the `Async` analogue of `Future.apply`.
   *
   * A single by-name entry point (no by-value overload): the worker evaluates
   * `body`, capturing a throw — including a statically `Nothing`-typed body
   * (`Async.start { ...; throw e }`, `Async.start(???)`) — as a failed run
   * rather than letting it escape at the call site, and lifts the result with
   * the same runtime encoding as every other value received from the user (a
   * `Pollable` success value is wrapped, a [[AsyncEncoding.WrappedPollable]]
   * carrier has its depth incremented).
   *
   * To eagerly drive an '''already-built''' `Async` instead — composing with
   * `either`, `tap`, `foldCause`, ... before driving (e.g.
   * `fa.either.tap(record).start`) — use the `fa.start` extension method. A
   * [[Running]] is itself an `Async[A]` and may be polled,
   * [[Cancelable.cancel]] led (a no-op once completed), or further composed; on
   * the JVM a suspended run proceeds on a background worker, on Scala.js via
   * microtasks.
   */
  def start[A](body: => A): Running[A] =
    AsyncRunner.startEval(body)

  /**
   * An [[Async]] that never completes. Useful as a sentinel in tests and as the
   * right-zero of `orElse`-style operations.
   */
  val never: Async[Nothing] = new Pollable[Nothing] {
    def poll(onComplete: Runnable): Async[Nothing] = this
  }

  /**
   * Sequentially run `as` and collect their values into a [[List]] in input
   * order. A failure short-circuits — subsequent inputs are not driven and the
   * failure is propagated.
   */
  def collectAll[A](as: IterableOnce[Async[A]]): Async[List[A]] =
    // Reify a fault in the source itself (acquiring the iterator, `hasNext`,
    // `next`) through the failure channel rather than letting it escape the
    // construction call — matching the deferred drain, where a throw from
    // inside `CollectAllPollable.poll` is reified by the driver. A `try` with
    // no throw is free on the JVM happy path (exception-table metadata only).
    try
      as match {
        case list: List[?] => collectAllList[A](list.asInstanceOf[List[Async[A]]])
        case _             => drainCollectAll[A](as.iterator, new scala.collection.mutable.ListBuffer[A])
      }
    catch { case t: Throwable => fail(t) }

  /**
   * Fast path for immutable lists of already-ready values. In that case the
   * input spine is already the result spine, so copying into a new List is pure
   * allocation. Wrapped pollable success values still need unwrapping, and any
   * suspended/failed element needs normal sequencing, so those shapes fall back
   * to the iterator implementation.
   */
  private def collectAllList[A](list: List[Async[A]]): Async[List[A]] = {
    var rem = list
    while (rem.nonEmpty) {
      val any = rem.head.asInstanceOf[Any]
      if (any.isInstanceOf[Pollable[?]] || any.isInstanceOf[AsyncEncoding.WrappedPollable])
        return drainCollectAll[A](list.iterator, new scala.collection.mutable.ListBuffer[A])
      rem = rem.tail
    }
    list.asInstanceOf[Async[List[A]]]
  }

  /**
   * Drain `it`, appending into `buf`. While inputs are raw values we stay in a
   * tight loop; on the first [[Pollable]] the rest is handed to a single
   * [[CollectAllPollable]] that reuses the same iterator and buffer — one
   * allocation for the whole batch, and (unlike a per-element `flatMap` chain)
   * iterative draining: a batch of already-settled pollables consumes constant
   * stack regardless of size.
   */
  private def drainCollectAll[A](
    it: Iterator[Async[A]],
    buf: scala.collection.mutable.ListBuffer[A]
  ): Async[List[A]] = {
    while (it.hasNext) {
      val any = it.next().asInstanceOf[Any]
      if (any.isInstanceOf[Failure]) return any.asInstanceOf[Async[List[A]]]
      else if (any.isInstanceOf[Pollable[_]])
        return new CollectAllPollable[A](any.asInstanceOf[Pollable[A]], it, buf)
      else buf += AsyncEncoding.deliverSuccess[A](any)
    }
    succeed(buf.toList)
  }

  /**
   * Sequencing continuation for [[collectAll]]: drive `cur` to a value, append,
   * then keep draining ready elements in place. A failure — whether an element
   * is already failed or `cur` resolves to one — short-circuits without driving
   * the remaining inputs. The drain is a flat loop: completed elements never
   * re-enter `poll`, so stack depth and re-poll cost stay constant no matter
   * how many elements settle between wakeups.
   */
  private final class CollectAllPollable[A](
    private var cur: Pollable[A],
    it: Iterator[Async[A]],
    buf: scala.collection.mutable.ListBuffer[A]
  ) extends Pollable[List[A]] {

    def poll(onComplete: Runnable): Async[List[A]] = {
      while (true) {
        val res = cur.poll(onComplete)
        if (res.isInstanceOf[Failure]) return res.asInstanceOf[Async[List[A]]]
        if (res.isInstanceOf[Pollable[?]]) {
          cur = res.asInstanceOf[Pollable[A]]
          return this
        }
        buf += AsyncEncoding.deliverSuccess[A](res)
        cur = null
        while ((cur eq null) && it.hasNext) {
          val any = it.next().asInstanceOf[Any]
          if (any.isInstanceOf[Failure]) return any.asInstanceOf[Async[List[A]]]
          else if (any.isInstanceOf[Pollable[_]]) cur = any.asInstanceOf[Pollable[A]]
          else buf += AsyncEncoding.deliverSuccess[A](any)
        }
        if (cur eq null) return Async.succeed(buf.toList)
      }
      throw new IllegalStateException("unreachable")
    }
  }

  /**
   * Slow-path implementation for suspended [[Async]] combinators. Called from
   * the inline extension methods after they establish
   * `fa.isInstanceOf[Pollable[?]]`.
   */
  private[async] object slowPath {

    /**
     * One [[AsyncEncoding.unwrapLayer]] before invoking a user continuation.
     */
    private def terminalValue(any: Any): Any = AsyncEncoding.unwrapLayer(any)

    // Poll-protocol note shared by every continuation pollable below: a `poll`
    // that returns a non-[[Failure]] [[Pollable]] is STILL PENDING — the result
    // is either the same instance (re-armed) or a replacement pollable that the
    // caller must advance to, exactly as the top-level drivers (`block`,
    // `start`, the interop runners) do. A terminal success is never encoded as
    // a bare `Pollable`: pollable-as-value successes travel as
    // [[AsyncEncoding.WrappedPollable]] carriers (which do not extend
    // [[Pollable]]). Combinators that have applied their user function and owe
    // the caller nothing further return their child's pending pollable
    // directly (a replacement), which keeps resume O(1) and collapses
    // continuation chains instead of growing one nesting level per suspension.

    /**
     * Sentinel for [[EnsuringPollable]]'s `outcome` slot meaning "`pa` has not
     * yet resolved". A raw `null` cannot be used: `pa` may legitimately resolve
     * to a `null` value, which would otherwise be re-read as "still pending"
     * forever.
     */
    private val NotResolved: AnyRef = new AnyRef

    /**
     * Slow-path implementation of `Async#map` when the input is suspended (or
     * failed). A [[Failure]] is propagated unchanged; any other [[Pollable]] is
     * wrapped in a continuation pollable.
     */
    def mapAsync[A, B](fa: Any, f: A => B): Async[B] =
      if (fa.isInstanceOf[Failure]) fa.asInstanceOf[Async[B]]
      else new MapPollable[A, B](fa.asInstanceOf[Pollable[A]], f)

    /**
     * Slow-path implementation of `Async#flatMap` when the input is suspended
     * (or failed). A [[Failure]] is propagated unchanged; any other
     * [[Pollable]] is wrapped in a continuation pollable.
     */
    def flatMapAsync[A, B](fa: Any, f: A => Async[B]): Async[B] =
      if (fa.isInstanceOf[Failure]) fa.asInstanceOf[Async[B]]
      else new FlatMapPollable[A, B](fa.asInstanceOf[Pollable[A]], f)

    /**
     * Slow-path implementation of `Async#catchAll` when the input is suspended
     * (or failed). A [[Failure]] applies the handler; any other [[Pollable]] is
     * wrapped so the handler runs only if the pollable eventually fails.
     */
    def catchAllAsync[A, B >: A](fa: Any, f: Throwable => Async[B]): Async[B] =
      if (fa.isInstanceOf[Failure]) f(fa.asInstanceOf[Failure].cause)
      else new CatchAllPollable[A, B](fa.asInstanceOf[Pollable[A]], f)

    /**
     * Slow-path `zipWith`: at least one input is suspended (or failed).
     * Allocate a [[Pollable]] that drives `fa` then `fb`. Failures from either
     * side are propagated. Both inputs are typed `Any` to absorb whichever
     * encoding case they happen to be (value, Pollable, or Failure).
     *
     * `zipWith` is strictly sequential left-to-right: `fa` is driven first and
     * `fb`'s failure is surfaced only once `fa` has succeeded. So a right that
     * is already a [[Failure]] short-circuits eagerly ONLY when the left is not
     * still pending (a `Failure` is itself a `Pollable`, so
     * `!fa.isInstanceOf[Pollable]` means `fa` is a ready value); a pending left
     * is driven first via [[ZipWithPollable]]. This keeps the (left-failed) and
     * (left-ready, right-failed) cases immediate while giving a pending left
     * the same left-to-right ordering as a pending-then-failing right.
     */
    def zipWithAsync[A, B, C](fa: Any, fb: Any, f: (A, B) => C): Async[C] =
      if (fa.isInstanceOf[Failure]) fa.asInstanceOf[Async[C]]
      else if (fb.isInstanceOf[Failure] && !fa.isInstanceOf[Pollable[?]]) fb.asInstanceOf[Async[C]]
      else new ZipWithPollable[A, B, C](fa, fb, f)

    /**
     * Ready-path `tap` after [[AsyncEncoding.deliverSuccess]] has unwrapped
     * carriers.
     */
    def tapReady[A](a: A, f: A => Async[Any]): Async[A] = {
      val aAny = a.asInstanceOf[Any]
      if (aAny.isInstanceOf[Pollable[?]] && !aAny.isInstanceOf[Failure])
        tapAsync(new ObservedPollable(aAny.asInstanceOf[Pollable[A]], a), f)
      else runThenValue(f(a), a, suppressFailure = false)
    }

    /** Slow-path `tap`: input is suspended (or failed). */
    def tapAsync[A](fa: Any, f: A => Async[Any]): Async[A] =
      if (fa.isInstanceOf[Failure]) fa.asInstanceOf[Async[A]]
      else
        new FlatMapPollable[A, A](
          fa.asInstanceOf[Pollable[A]],
          (a: A) => runThenValue[A](f(a), a, suppressFailure = false)
        )

    /**
     * Drive `target` for effects but settle to `observed` (pollable-as-value
     * tap must not replace the user [[Pollable]] with its polled scalar).
     */
    private final class ObservedPollable[A](target0: Pollable[A], observed: A) extends Pollable[A] {
      private var target: Pollable[A]          = target0
      def poll(onComplete: Runnable): Async[A] = {
        val res = target.poll(onComplete)
        if (res.isInstanceOf[Failure]) res.asInstanceOf[Async[A]]
        else if (res.isInstanceOf[Pollable[?]]) {
          target = res.asInstanceOf[Pollable[A]]
          this
        } else Async.succeed(observed).asInstanceOf[Async[A]]
      }
    }

    /**
     * Drive the user [[Pollable]] stored as a ready success value for its
     * effects, settling to the pollable itself. Used by the platform `start`
     * runners so a pollable-as-value input is driven on the background
     * worker/microtask instead of blocking (or failing) the caller.
     */
    private[async] def observe[A](target: Pollable[A], observed: A): Pollable[A] =
      new ObservedPollable(target, observed)

    /**
     * Drive `fin` for its effect, then yield `a`. `fin` may be a value, a
     * [[Pollable]], or a [[Failure]]. If `suppressFailure` is `true`, a
     * `Failure` in `fin` is dropped (ensuring semantics). Otherwise it is
     * propagated as the overall result (tap semantics).
     */
    def runThenValue[A](fin: Any, a: A, suppressFailure: Boolean): Async[A] =
      if (fin.isInstanceOf[Failure])
        if (suppressFailure) Async.succeed(a).asInstanceOf[Async[A]]
        else fin.asInstanceOf[Async[A]]
      else if (fin.isInstanceOf[Pollable[?]])
        new RunThenValuePollable[A](fin.asInstanceOf[Pollable[Any]], a, suppressFailure)
      else Async.succeed(a).asInstanceOf[Async[A]]

    /**
     * Slow-path `ensuring`: drive `fa` to completion (value or failure), then
     * run `finalizer` (ignoring its failures), then propagate the original
     * outcome.
     */
    def ensuringAsync[A](fa: Any, finalizer: Async[Any]): Async[A] =
      new EnsuringPollable[A](fa.asInstanceOf[Pollable[A]], finalizer)

    /**
     * Drive a suspended pollable to its value, parking the calling thread
     * between polls. Allocated once per `await` call (not per poll iteration).
     * If the pollable ever resolves to a [[Failure]], its `cause` is thrown.
     *
     *   - '''JVM:''' parks on a [[java.util.concurrent.locks.ReentrantLock]]
     *     `Condition`, which (unlike `synchronized`) does not pin a virtual
     *     thread's carrier under Project Loom.
     *   - '''Scala.js:''' there is no thread to park. Each suspension is given
     *     one chance to complete synchronously inside `poll`; if the waker has
     *     not fired by the time `poll` returns, throws
     *     [[IllegalStateException]].
     *
     * Must run on a non-reactor thread on the JVM — calling it from inside a
     * `poll` deadlocks the loop.
     */
    /**
     * Drive `target` for effects; return `observed` (pollable-as-value
     * `.block`).
     */
    private def awaitObservedPollable[A](target: Pollable[A], observed: A): A =
      awaitSuspended(new ObservedPollable(target, observed))

    /**
     * Drive `fa` to its value. Bare [[Pollable]] encodings are suspended
     * computations; [[AsyncEncoding.WrappedPollable]] drives the stored user
     * [[Pollable]] for effects but exits with pollable identity.
     */
    def block[A](fa: Any): A =
      if (fa.isInstanceOf[AsyncEncoding.WrappedPollable]) {
        val w        = fa.asInstanceOf[AsyncEncoding.WrappedPollable]
        val out: Any = AsyncEncoding.unwrapLayer(fa) // one delivery layer, depth-aware
        // Only a depth-1 carrier exposes the user pollable itself as the
        // delivered value, so only then is it driven for effects. A deeper
        // carrier (nested `succeed`) delivers a shallower carrier untouched —
        // peeling more than one layer here would skip nesting levels.
        if (w.depth > 1 || w.value.isInstanceOf[Failure]) out.asInstanceOf[A]
        else awaitObservedPollable(w.value.asInstanceOf[Pollable[A]], out.asInstanceOf[A])
      } else if (fa.isInstanceOf[Failure])
        Failure.throwCause(fa.asInstanceOf[Failure].cause)
      else if (fa.isInstanceOf[Pollable[?]])
        awaitSuspended(fa.asInstanceOf[Pollable[A]])
      else fa.asInstanceOf[A]

    /** Alias for CPS / interop call sites that take `Any`. */
    def blockGeneric[A](fa: Any): A = block[A](fa)

    def awaitSuspended[A](pa0: Pollable[A]): A = {
      if (pa0.isInstanceOf[Failure]) Failure.throwCause(pa0.asInstanceOf[Failure].cause)

      val parker = PlatformAsync.newParker()

      @tailrec def loop(cur: Pollable[A]): A = {
        parker.reset() // reset under the parker's lock — no lost wakeup
        val next: Async[A] = cur.poll(parker.onComplete)
        if (next.isInstanceOf[Failure])
          Failure.throwCause(next.asInstanceOf[Failure].cause)
        else if (next.isInstanceOf[Pollable[?]]) {
          parker.park() // JVM: blocks until wake(); JS: throws if not already woken
          loop(next.asInstanceOf[Pollable[A]])
        } else AsyncEncoding.deliverSuccess[A](next)
      }

      loop(pa0)
    }

    /**
     * `map` continuation: `f` produces a plain '''value''' (never a nested
     * `Async` to flatten), so the result is always lifted via [[Async.succeed]]
     * — exactly like the ready path. A [[Pollable]] returned by `f` (including
     * a combinator-built `Async` held as data) is a pollable-as-value, never a
     * computation to drive; flattening belongs to `flatMap`.
     */
    private final class MapPollable[A, B](pa0: Pollable[A], f: A => B) extends Pollable[B] {

      private var pa: Pollable[A] = pa0

      def poll(onComplete: Runnable): Async[B] = {
        val res = pa.poll(onComplete)
        if (res.isInstanceOf[Failure]) res.asInstanceOf[Async[B]]
        else if (res.isInstanceOf[Pollable[?]]) {
          pa = res.asInstanceOf[Pollable[A]]
          this
        } else Async.succeed(f(terminalValue(res).asInstanceOf[A])).asInstanceOf[Async[B]]
      }
    }

    private final class FlatMapPollable[A, B](pa0: Pollable[A], f: A => Async[B]) extends Pollable[B] {

      private var pa: Pollable[A] = pa0

      def poll(onComplete: Runnable): Async[B] = {
        val res = pa.poll(onComplete)
        if (res.isInstanceOf[Failure]) res.asInstanceOf[Async[B]] // pa failed: propagate
        else if (res.isInstanceOf[Pollable[?]]) {                 // pa still pending; it re-armed onComplete itself
          pa = res.asInstanceOf[Pollable[A]]
          this
        } else {
          val a    = terminalValue(res).asInstanceOf[A]
          val fRes = f(a) // f(a): Async[B] — may be ready, suspend, or fail
          if (fRes.isInstanceOf[Failure]) fRes.asInstanceOf[Async[B]]
          else if (fRes.isInstanceOf[Pollable[?]])
            // f suspended: drive it once; a pending result is handed to the
            // caller as a replacement (this pollable has no further role).
            fRes.asInstanceOf[Pollable[B]].poll(onComplete)
          else fRes // f finished synchronously (may carry WrappedPollable for pollable-as-value)
        }
      }
    }

    /**
     * Recovery continuation: drive `pa` to a value or failure; on success
     * propagate the value; on failure invoke `f` — guarded like the ready path,
     * so a synchronously-throwing handler surfaces as a [[Failure]] — and hand
     * any pending recovery pollable to the caller as a replacement.
     */
    private final class CatchAllPollable[A, B >: A](pa0: Pollable[A], f: Throwable => Async[B]) extends Pollable[B] {

      private var pa: Pollable[A] = pa0

      def poll(onComplete: Runnable): Async[B] = {
        val res = pa.poll(onComplete)
        if (res.isInstanceOf[Failure]) {
          val fRes = // handler can produce a value, pollable, another failure, or throw
            try f(res.asInstanceOf[Failure].cause)
            catch { case t: Throwable => new Failure(t) }
          if (fRes.isInstanceOf[Failure]) fRes.asInstanceOf[Async[B]]
          else if (fRes.isInstanceOf[Pollable[?]]) fRes.asInstanceOf[Pollable[B]].poll(onComplete)
          else fRes.asInstanceOf[Async[B]]
        } else if (res.isInstanceOf[Pollable[?]]) { // pa still pending
          pa = res.asInstanceOf[Pollable[A]]
          this
        } else res.asInstanceOf[Async[B]] // pa succeeded: terminal encoding propagates as-is
      }
    }

    /**
     * Drive `fa` to a value, then `fb` to a value, then `f(a, b)`. Either side
     * being a [[Failure]] is propagated immediately. `faSt` keeps the resolved
     * left side's '''raw terminal encoding''' (value or `WrappedPollable`
     * carrier) — `faResolved` marks it settled so a pollable-as-value left is
     * never re-dispatched as a suspended computation on a later poll, and the
     * leaf for `fa` is never re-polled after it yields its value, even while
     * `fb` is still pending. Unwrapping to user values happens once, at the
     * combine.
     */
    private final class ZipWithPollable[A, B, C](fa0: Any, fb0: Any, f: (A, B) => C) extends Pollable[C] {

      // `Any` because either field can hold value | Pollable | Failure at start.
      private var faSt: Any  = fa0
      private var fbSt: Any  = fb0
      private var faResolved = false

      def poll(onComplete: Runnable): Async[C] = {
        // Resolve fa first to a terminal encoding or surface a failure/suspension.
        if (!faResolved) {
          if (faSt.isInstanceOf[Failure]) return faSt.asInstanceOf[Async[C]]
          if (faSt.isInstanceOf[Pollable[?]]) {
            val next = faSt.asInstanceOf[Pollable[A]].poll(onComplete)
            if (next.isInstanceOf[Failure]) return next.asInstanceOf[Async[C]]
            if (next.isInstanceOf[Pollable[?]]) { faSt = next; return this } // still pending
            faSt = next
          }
          faResolved = true
        }
        // fa is terminal now; resolve fb the same way (poll is one-shot, so this
        // section runs at most until fb settles — no flag needed).
        if (fbSt.isInstanceOf[Failure]) return fbSt.asInstanceOf[Async[C]]
        if (fbSt.isInstanceOf[Pollable[?]]) {
          val next = fbSt.asInstanceOf[Pollable[B]].poll(onComplete)
          if (next.isInstanceOf[Failure]) return next.asInstanceOf[Async[C]]
          if (next.isInstanceOf[Pollable[?]]) { fbSt = next; return this } // still pending
          fbSt = next
        }
        Async
          .succeed(f(terminalValue(faSt).asInstanceOf[A], terminalValue(fbSt).asInstanceOf[B]))
          .asInstanceOf[Async[C]]
      }
    }

    /**
     * Drive a Pollable `fin` for its effect, then yield `a`. If
     * `suppressFailure` is true and `fin` resolves to a [[Failure]], the
     * failure is suppressed; otherwise it is propagated as the result.
     */
    private final class RunThenValuePollable[A](fin: Pollable[Any], a: A, suppressFailure: Boolean)
        extends Pollable[A] {

      private var st: Any = fin

      def poll(onComplete: Runnable): Async[A] = {
        if (st.isInstanceOf[Pollable[?]] && !st.isInstanceOf[Failure]) {
          try {
            val next = st.asInstanceOf[Pollable[Any]].poll(onComplete)
            if (!next.isInstanceOf[Failure] && next.isInstanceOf[Pollable[?]]) {
              st = next // still pending (possibly a replacement pollable)
              return this
            }
            st = next
          } catch {
            case t: Throwable =>
              // A throwing finalizer poll is the finalizer's failure channel:
              // suppressed (encoded, so a pollable-as-value `a` stays a settled
              // carrier) or propagated, mirroring the resolved-Failure arm below.
              if (suppressFailure) return Async.succeed(a).asInstanceOf[Async[A]]
              else throw t
          }
        }
        if (st.isInstanceOf[Failure])
          if (suppressFailure) Async.succeed(a).asInstanceOf[Async[A]]
          else st.asInstanceOf[Async[A]]
        else Async.succeed(a).asInstanceOf[Async[A]]
      }
    }

    /**
     * Drive `pa` to a value or failure, run `finalizer`, then propagate the
     * original outcome. Failures from the finalizer are suppressed. The
     * `outcome` memo holds the resolved value-or-failure so that, once the
     * finalizer is being driven, we don't re-poll `pa`.
     */
    private final class EnsuringPollable[A](pa0: Pollable[A], finalizer: Async[Any]) extends Pollable[A] {

      private var pa: Pollable[A] = pa0
      // NotResolved while pa is still pending; otherwise holds the resolved
      // Async[A] (which is either a raw value — possibly null — or a Failure).
      private var outcome: Any = NotResolved
      // null until we start driving the finalizer; then holds its current state.
      private var finSt: Any = null

      def poll(onComplete: Runnable): Async[A] = {
        if (outcome.asInstanceOf[AnyRef] eq NotResolved) {
          // A throw escaping `pa.poll` is the primary's failure channel (the
          // top-level drivers reify it the same way); the finalizer must still
          // run before that failure propagates.
          val next =
            try pa.poll(onComplete)
            catch { case t: Throwable => new Failure(Failure.unwindCause(t)) }
          if (!next.isInstanceOf[Failure] && next.isInstanceOf[Pollable[?]]) {
            pa = next.asInstanceOf[Pollable[A]]
            return this // still pending; pa re-armed waker
          }
          outcome = next
          finSt = finalizer
        }
        // pa is done; drive finalizer (ignoring its failure) and propagate outcome.
        if (finSt.isInstanceOf[Pollable[?]]) {
          // including a Failure pollable, which we want to *suppress*
          if (!finSt.isInstanceOf[Failure]) {
            try {
              val next = finSt.asInstanceOf[Pollable[Any]].poll(onComplete)
              if (!next.isInstanceOf[Failure] && next.isInstanceOf[Pollable[?]]) {
                finSt = next // still pending (possibly a replacement pollable)
                return this
              }
              finSt = next
            } catch {
              case t: Throwable =>
                if (outcome.isInstanceOf[Failure]) {
                  val primaryCause = outcome.asInstanceOf[Failure].cause
                  if ((primaryCause ne null) && (primaryCause ne t) && (t ne null))
                    try primaryCause.addSuppressed(t)
                    catch { case _: Throwable => () }
                }
                finSt = null // finalizer defect suppressed; primary outcome wins
            }
          }
        }
        // If the original outcome was itself a failure and the finalizer also
        // failed, keep the finalizer's cause reachable as a suppressed exception
        // on the primary rather than dropping it silently. `Throwable
        // .addSuppressed` itself throws — `IllegalArgumentException` on self-
        // suppression (same instance) and `NullPointerException` on a null
        // argument — and `fail(null)` accepts a null cause, so guard all three:
        // distinct, non-null primary, non-null finalizer. The primary outcome
        // must always win; a finalizer-cause we cannot attach is simply dropped.
        if (finSt.isInstanceOf[Failure] && outcome.isInstanceOf[Failure]) {
          val primaryCause   = outcome.asInstanceOf[Failure].cause
          val finalizerCause = finSt.asInstanceOf[Failure].cause
          if ((primaryCause ne finalizerCause) && (primaryCause ne null) && (finalizerCause ne null))
            primaryCause.addSuppressed(finalizerCause)
        }
        // `outcome` is a terminal encoding — a Failure, a raw value, or a
        // settled WrappedPollable carrier — and propagates as-is.
        outcome.asInstanceOf[Async[A]]
      }
    }
  }
}

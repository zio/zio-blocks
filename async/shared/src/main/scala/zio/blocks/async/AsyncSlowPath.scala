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
import zio.blocks.async.internal.PlatformAsync

/**
 * Slow-path implementation for the [[Async]] surface. Called from the `else`
 * branch of the inline extension methods (`map`, `flatMap`, `await`,
 * `catchAll`) after they have established `fa.isInstanceOf[Pollable[?]]`.
 * Concentrating the slow path here keeps the inline-expanded fast path in
 * callers small and lets the JIT recognize the cold side as an out-of-line
 * branch.
 *
 * The slow path is also where [[Failure]] propagation happens: each helper
 * checks `isInstanceOf[Failure]` first so a failed value short-circuits past
 * `f` (for `map` / `flatMap`) or surfaces to the handler (for `catchAll`). The
 * fast path never sees a `Failure` because `Failure <: Pollable`.
 */
private[async] object AsyncSlowPath {

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
    else new FlatMapPollable[A, B](fa.asInstanceOf[Pollable[A]], (a: A) => f(a).asInstanceOf[Async[B]])

  /**
   * Slow-path implementation of `Async#flatMap` when the input is suspended (or
   * failed). A [[Failure]] is propagated unchanged; any other [[Pollable]] is
   * wrapped in a continuation pollable.
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
   * Slow-path `zipWith`: at least one input is suspended (or failed). Allocate
   * a [[Pollable]] that drives `fa` then `fb`. Failures from either side are
   * propagated. Both inputs are typed `Any` to absorb whichever encoding case
   * they happen to be (value, Pollable, or Failure).
   */
  def zipWithAsync[A, B, C](fa: Any, fb: Any, f: (A, B) => C): Async[C] =
    if (fa.isInstanceOf[Failure]) fa.asInstanceOf[Async[C]]
    else if (fb.isInstanceOf[Failure]) fb.asInstanceOf[Async[C]]
    else new ZipWithPollable[A, B, C](fa, fb, f)

  /** Slow-path `tap`: input is suspended (or failed). */
  def tapAsync[A](fa: Any, f: A => Async[Any]): Async[A] =
    if (fa.isInstanceOf[Failure]) fa.asInstanceOf[Async[A]]
    else
      new FlatMapPollable[A, A](
        fa.asInstanceOf[Pollable[A]],
        (a: A) => runThenValue[A](f(a), a, suppressFailure = false)
      )

  /**
   * Drive `fin` for its effect, then yield `a`. `fin` may be a value, a
   * [[Pollable]], or a [[Failure]]. If `suppressFailure` is `true`, a `Failure`
   * in `fin` is dropped (ensuring semantics). Otherwise it is propagated as the
   * overall result (tap semantics).
   */
  def runThenValue[A](fin: Any, a: A, suppressFailure: Boolean): Async[A] =
    if (fin.isInstanceOf[Failure])
      if (suppressFailure) a.asInstanceOf[Async[A]]
      else fin.asInstanceOf[Async[A]]
    else if (fin.isInstanceOf[Pollable[?]])
      new RunThenValuePollable[A](fin.asInstanceOf[Pollable[Any]], a, suppressFailure)
    else a.asInstanceOf[Async[A]]

  /**
   * Slow-path `ensuring`: drive `fa` to completion (value or failure), then run
   * `finalizer` (ignoring its failures), then propagate the original outcome.
   */
  def ensuringAsync[A](fa: Any, finalizer: Async[Any]): Async[A] =
    new EnsuringPollable[A](fa.asInstanceOf[Pollable[A]], finalizer)

  /**
   * Drive a suspended pollable to its value, parking the calling thread between
   * polls. Allocated once per `await` call (not per poll iteration). If the
   * pollable ever resolves to a [[Failure]], its `cause` is thrown.
   *
   *   - '''JVM:''' parks on a [[java.util.concurrent.locks.ReentrantLock]]
   *     `Condition`, which (unlike `synchronized`) does not pin a virtual
   *     thread's carrier under Project Loom.
   *   - '''Scala.js:''' there is no thread to park. Each suspension is given
   *     one chance to complete synchronously inside `poll`; if the waker has
   *     not fired by the time `poll` returns, throws [[IllegalStateException]].
   *
   * Must run on a non-reactor thread on the JVM — calling it from inside a
   * `poll` deadlocks the loop.
   */
  def awaitSuspended[A](pa0: Pollable[A]): A = {
    if (pa0.isInstanceOf[Failure]) throw pa0.asInstanceOf[Failure].cause

    val parker = PlatformAsync.newParker()

    @tailrec def loop(cur: Pollable[A]): A = {
      parker.reset() // reset under the parker's lock — no lost wakeup
      val next: Async[A] = cur.poll(parker.waker)
      if (next.isInstanceOf[Failure])
        throw next.asInstanceOf[Failure].cause
      else if (next.isInstanceOf[Pollable[?]]) {
        parker.park() // JVM: blocks until wake(); JS: throws if not already woken
        loop(next.asInstanceOf[Pollable[A]])
      } else next.asInstanceOf[A]
    }

    loop(pa0)
  }

  /**
   * Sequences `pa` then `f`. The `stage` memo is what makes resume O(1): once
   * `pa` completes we latch onto `f`'s pollable and never re-poll `pa` (which
   * also stops a consumed leaf — e.g. a socket read — from re-running). A
   * [[Failure]] surfaced at any stage is propagated as-is.
   */
  private final class FlatMapPollable[A, B](pa: Pollable[A], f: A => Async[B]) extends Pollable[B] {

    private var stage: Pollable[B] = null

    def poll(w: Waker): Async[B] =
      if (stage != null) stage.poll(w) // already past pa; drive f's pollable
      else {
        val res = pa.poll(w)
        if (res.isInstanceOf[Failure]) res.asInstanceOf[Async[B]] // pa failed: propagate
        else if (res.isInstanceOf[Pollable[?]]) this              // pa still pending; pa re-armed w itself
        else {
          val a    = res.asInstanceOf[A]
          val fRes = f(a) // f(a): Async[B] — may be ready, suspend, or fail
          if (fRes.isInstanceOf[Failure]) fRes.asInstanceOf[Async[B]]
          else if (fRes.isInstanceOf[Pollable[?]]) { // f suspended: latch and advance
            val pb = fRes.asInstanceOf[Pollable[B]]
            stage = pb
            pb.poll(w)
          } else fRes // f finished synchronously
        }
      }
  }

  /**
   * Recovery continuation: drive `pa` to a value or failure; on success
   * propagate the value; on failure invoke `f` and (if `f` returns a pollable)
   * latch onto it. The `stage` memo makes resume after recovery O(1).
   */
  private final class CatchAllPollable[A, B >: A](pa: Pollable[A], f: Throwable => Async[B]) extends Pollable[B] {

    private var stage: Pollable[B] = null

    def poll(w: Waker): Async[B] =
      if (stage != null) stage.poll(w)
      else {
        val res = pa.poll(w)
        if (res.isInstanceOf[Failure]) {
          val fRes = f(res.asInstanceOf[Failure].cause) // handler can produce value, pollable, or another failure
          if (fRes.isInstanceOf[Pollable[?]] && !fRes.isInstanceOf[Failure]) {
            val pb = fRes.asInstanceOf[Pollable[B]]
            stage = pb
            pb.poll(w)
          } else fRes                                  // value or another failure: propagate
        } else if (res.isInstanceOf[Pollable[?]]) this // pa still pending
        else res.asInstanceOf[Async[B]]                // pa succeeded: propagate value
      }
  }

  /**
   * Drive `fa` to a value, then `fb` to a value, then `f(a, b)`. Either side
   * being a [[Failure]] is propagated immediately. The two-stage memo
   * (`aReady`, `aValue`) means a leaf for `fa` is never re-polled after it
   * yields its value, even while `fb` is still pending.
   */
  private final class ZipWithPollable[A, B, C](fa0: Any, fb0: Any, f: (A, B) => C) extends Pollable[C] {

    // `Any` because either field can hold value | Pollable | Failure at start.
    private var faSt: Any = fa0
    private var fbSt: Any = fb0

    def poll(w: Waker): Async[C] = {
      // Resolve fa first to a value or surface a failure/suspension.
      if (faSt.isInstanceOf[Pollable[?]] && !faSt.isInstanceOf[Failure]) {
        val next = faSt.asInstanceOf[Pollable[A]].poll(w)
        if (next.isInstanceOf[Failure]) return next.asInstanceOf[Async[C]]
        faSt = next
        if (faSt.isInstanceOf[Pollable[?]]) return this // still pending
      } else if (faSt.isInstanceOf[Failure]) return faSt.asInstanceOf[Async[C]]
      // fa is a value now; resolve fb the same way.
      if (fbSt.isInstanceOf[Pollable[?]] && !fbSt.isInstanceOf[Failure]) {
        val next = fbSt.asInstanceOf[Pollable[B]].poll(w)
        if (next.isInstanceOf[Failure]) return next.asInstanceOf[Async[C]]
        fbSt = next
        if (fbSt.isInstanceOf[Pollable[?]]) return this
      } else if (fbSt.isInstanceOf[Failure]) return fbSt.asInstanceOf[Async[C]]
      f(faSt.asInstanceOf[A], fbSt.asInstanceOf[B]).asInstanceOf[Async[C]]
    }
  }

  /**
   * Drive a Pollable `fin` for its effect, then yield `a`. If `suppressFailure`
   * is true and `fin` resolves to a [[Failure]], the failure is suppressed;
   * otherwise it is propagated as the result.
   */
  private final class RunThenValuePollable[A](fin: Pollable[Any], a: A, suppressFailure: Boolean) extends Pollable[A] {

    private var st: Any = fin

    def poll(w: Waker): Async[A] = {
      if (st.isInstanceOf[Pollable[?]] && !st.isInstanceOf[Failure]) {
        val next = st.asInstanceOf[Pollable[Any]].poll(w)
        st = next
        if (st.isInstanceOf[Pollable[?]] && !st.isInstanceOf[Failure]) return this
      }
      if (st.isInstanceOf[Failure])
        if (suppressFailure) a.asInstanceOf[Async[A]]
        else st.asInstanceOf[Async[A]]
      else a.asInstanceOf[Async[A]]
    }
  }

  /**
   * Drive `pa` to a value or failure, run `finalizer`, then propagate the
   * original outcome. Failures from the finalizer are suppressed. The `outcome`
   * memo holds the resolved value-or-failure so that, once the finalizer is
   * being driven, we don't re-poll `pa`.
   */
  private final class EnsuringPollable[A](pa: Pollable[A], finalizer: Async[Any]) extends Pollable[A] {

    // NotResolved while pa is still pending; otherwise holds the resolved
    // Async[A] (which is either a raw value — possibly null — or a Failure).
    private var outcome: Any = NotResolved
    // null until we start driving the finalizer; then holds its current state.
    private var finSt: Any = null

    def poll(w: Waker): Async[A] = {
      if (outcome.asInstanceOf[AnyRef] eq NotResolved) {
        val next = pa.poll(w)
        if (next.isInstanceOf[Pollable[?]] && !next.isInstanceOf[Failure])
          return this // still pending; pa re-armed waker
        outcome = next
        finSt = finalizer
      }
      // pa is done; drive finalizer (ignoring its failure) and propagate outcome.
      if (finSt.isInstanceOf[Pollable[?]]) {
        // including a Failure pollable, which we want to *suppress*
        if (!finSt.isInstanceOf[Failure]) {
          val next = finSt.asInstanceOf[Pollable[Any]].poll(w)
          finSt = next
          if (finSt.isInstanceOf[Pollable[?]] && !finSt.isInstanceOf[Failure]) return this
        }
      }
      // If the original outcome was itself a failure and the finalizer also
      // failed, keep the finalizer's cause reachable as a suppressed exception
      // on the primary rather than dropping it silently. Guard against
      // self-suppression: when both carry the SAME throwable instance,
      // `Throwable.addSuppressed(self)` throws `IllegalArgumentException`, which
      // would replace the primary outcome — the primary must always win.
      if (finSt.isInstanceOf[Failure] && outcome.isInstanceOf[Failure]) {
        val primaryCause   = outcome.asInstanceOf[Failure].cause
        val finalizerCause = finSt.asInstanceOf[Failure].cause
        if (primaryCause ne finalizerCause) primaryCause.addSuppressed(finalizerCause)
      }
      outcome.asInstanceOf[Async[A]]
    }
  }
}

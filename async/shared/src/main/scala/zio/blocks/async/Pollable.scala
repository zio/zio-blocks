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

/**
 * The extension point for a source of results that are not there yet: a timer,
 * a non-blocking socket read, a job you check by asking. A `Pollable[A]` is
 * itself an `Async[A]`, so it works with every operator. Most code never
 * implements one: for a callback API, `Async.promise` and [[Completer]] are
 * simpler.
 *
 * An implementation defines [[poll]]. When the value is ready, `poll` returns
 * it (`Async.succeed(a)`) or a failure (`Async.fail(t)`). When it is not,
 * `poll` arranges for the supplied `onComplete` to run once there is something
 * new to see, and returns itself. A driver polls again only after `onComplete`
 * runs, so a `poll` that returns itself and never runs it waits forever, as
 * `Async.never` does. Override [[cancel]] when the work can be aborted.
 *
 * {{{
 * final class Countdown(ticks: Int) extends Pollable[String] {
 *   private var remaining = ticks
 *   def poll(onComplete: Runnable): Async[String] =
 *     if (remaining <= 0) Async.succeed("timer expired")
 *     else { remaining -= 1; onComplete.run(); this }
 * }
 *
 * new Countdown(2).map(_.toUpperCase).block // TIMER EXPIRED
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/async/#pollable Pollable]]
 */
abstract class Pollable[+A] {

  /**
   * Attempt to produce this computation's value. Returns the completed result
   * if it is ready; otherwise registers `onComplete` (to be invoked when the
   * value becomes available) and returns a still-pending `Async`.
   *
   * A still-pending result is either this pollable itself (the common case) or
   * a '''replacement''' pollable that represents the rest of the computation;
   * the caller must direct its next poll at whatever pollable was returned. The
   * built-in drivers (`block`, `start`, the interop runners) and every
   * combinator follow this, so a conforming `poll` may hand back a new pollable
   * at any suspension.
   *
   * Returning the same identity means the caller may wait for the registered
   * callback. Returning a distinct identity is synchronous progress: the caller
   * must poll that replacement without waiting. An identity change is not a
   * readiness event, so a caller or wrapper must not synthesize `onComplete`
   * for it. Real callbacks may be stale, reentrant, or duplicated and are
   * therefore coalescible wake permits rather than proof that a particular
   * generation has completed.
   *
   * Polling is a '''one-shot driver protocol''': a driver should keep polling
   * only while `poll` returns a still-pending `Pollable`, and must stop as soon
   * as it returns a terminal result — a raw value or a failed [[Async]]. The
   * built-in drivers (`block`, `start`, and the interop runners) all follow
   * this, so they never poll a settled computation again.
   *
   * Re-polling a `Pollable` after it has returned a terminal result is outside
   * the contract and is '''not''' guaranteed to be a pure re-observation: a
   * continuation may run again and diagnostic state may be repeated. Drive
   * computations through the built-in runners unless you are implementing this
   * protocol yourself, in which case honor the stop-at-terminal rule.
   */
  def poll(onComplete: Runnable): Async[A]

  /**
   * Signals that the pending operation is no longer wanted. The default does
   * nothing; override it, idempotent and non-blocking, when the work can be
   * aborted. A driver calls it on its pending leaf when `cancel()` on the run
   * wins the race against completion, and `AsyncSelector.shutdown` calls it on
   * the inputs still armed. If it throws, the failure goes to the run's
   * cleanup-failure reporter (see `Async.Running#cancel`).
   *
   * {{{
   * import java.util.concurrent.atomic.AtomicBoolean
   *
   * final class PortScan(register: Runnable => Unit) extends Pollable[Int] {
   *   val aborted = new AtomicBoolean(false)
   *   def poll(onComplete: Runnable): Async[Int] =
   *     if (aborted.get()) Async.fail(new java.io.IOException("scan aborted"))
   *     else { register(onComplete); this }
   *   override def cancel(): Unit = aborted.set(true)
   * }
   *
   * val scan = new PortScan(_ => ())
   * scan.start.cancel()
   * scan.aborted.get() // true
   * }}}
   */
  def cancel(): Unit = ()

  /**
   * Internal cancellation traversal used by drivers and built-in combinators.
   * The synchronous [[cancel]] signal happens before this method returns; the
   * returned effect represents any cleanup that must still be driven.
   */
  private[async] def cancelWithCleanup(): Async[Unit] =
    try {
      cancel()
      Async.succeed(())
    } catch { case t: Throwable => Async.fail(t) }
}

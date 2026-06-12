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

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

/**
 * The completion capability handed to an [[Async.promise]] block: complete the
 * resulting [[Async]] by calling [[succeed]] (with a value) or [[fail]] (with a
 * cause), typically from an I/O callback.
 *
 * Thread-safe and '''one-shot''': the first call to `succeed` or `fail` wins;
 * all subsequent calls are silent no-ops, regardless of which thread they come
 * from.
 */
final class Completer[A] extends Pollable[A] {
  import Completer.{NullValue, WaitingMarker}

  // null = empty; WaitingMarker = waiting; anything else = the value (or Failure)
  private val state = new AtomicReference[AnyRef](null)

  /** Complete successfully with `a`. Idempotent — the first completion wins. */
  def succeed(a: A): Unit = {
    val any = a.asInstanceOf[Any]
    // A raw `null` value must be stored as the `NullValue` sentinel: storing
    // plain `null` would collide with the empty state, so `compareAndSet(null,
    // null)` would be a silent no-op and the completer would never settle.
    // Pollable success values are WrappedPollable-encoded (same as [[Async.succeed]])
    // so combinator slow paths do not misread them as suspended computations.
    val stored: AnyRef =
      if (any == null) NullValue
      else AsyncEncoding.liftSuccess(a).asInstanceOf[AnyRef] // mirrors [[Async.succeed]]
    settle(stored)
  }

  /**
   * Complete with the failure `cause`. Idempotent — the first completion wins.
   */
  def fail(cause: Throwable): Unit = settle(new Failure(cause))

  @tailrec
  private def settle(value: AnyRef): Unit = {
    val s = state.get
    if (s == null) {
      if (!state.compareAndSet(null, value)) settle(value)
    } else if (s.isInstanceOf[WaitingMarker]) {
      if (state.compareAndSet(s, value)) {
        // Wake every registered waiter: one promise-backed Async may be
        // observed by several independent drivers (fan-out), each of which
        // registered its own onComplete while pending. A throwing waker is a
        // defect of that one driver — it must not starve the rest of the
        // chain, nor surface inside the completing I/O callback.
        var w = s.asInstanceOf[WaitingMarker]
        while (w ne null) {
          try w.onComplete.run()
          catch { case _: Throwable => () }
          w = w.next
        }
      } else settle(value)
    }
    // else: already settled — first writer wins, ignore subsequent attempts.
  }

  @tailrec
  def poll(onComplete: Runnable): Async[A] = {
    val s = state.get
    if (s == null) {
      if (state.compareAndSet(null, new WaitingMarker(onComplete, null))) this
      else poll(onComplete)
    } else if (s.isInstanceOf[WaitingMarker]) {
      // Re-poll while pending: an identical runnable is already armed (a
      // driver's re-poll — coalesce, no-op); a distinct runnable is another
      // driver observing the same completer — add it so completion wakes all.
      val head = s.asInstanceOf[WaitingMarker]
      if (head.contains(onComplete)) this
      else if (state.compareAndSet(s, new WaitingMarker(onComplete, head))) this
      else poll(onComplete)
    } else if (s eq NullValue) null.asInstanceOf[Async[A]] // settled with a raw null
    else s.asInstanceOf[Async[A]]                          // settled: the value (or Failure)
  }

  /**
   * A snapshot of this completer as an [[Async]] that does '''not''' register a
   * onComplete: if it has already been completed, the resulting `Async` is
   * already completed with that value or failure; otherwise it is the
   * still-pending computation backed by this completer.
   */
  def peek: Async[A] = {
    val s = state.get
    if (s == null || s.isInstanceOf[WaitingMarker]) this
    else if (s eq NullValue) null.asInstanceOf[Async[A]]
    else s.asInstanceOf[Async[A]]
  }
}

private[async] object Completer {

  /**
   * Wraps an `onComplete` [[Runnable]] so it is distinguishable from a
   * completed value (which is also stored as an `AnyRef`). Allocated only on a
   * `poll` that arrives before completion — the sync-complete hot path never
   * sees it. `next` chains additional waiters when more than one driver
   * observes the same completer (fan-out); the chain is almost always length
   * one.
   */
  final class WaitingMarker(val onComplete: Runnable, val next: WaitingMarker) {

    /** True when `r` is already registered in this chain (identity). */
    def contains(r: Runnable): Boolean = {
      var w = this
      while (w ne null) {
        if (w.onComplete eq r) return true
        w = w.next
      }
      false
    }
  }

  /**
   * Sentinel stored in the state slot for a completion with a raw `null` value,
   * distinguishing "completed with null" from the empty (`null`) state. Never
   * escapes the completer: [[Completer.poll]] / [[Completer.peek]] hand it back
   * out as a bare `null` (a ready value).
   */
  private[async] val NullValue: AnyRef = new AnyRef
}

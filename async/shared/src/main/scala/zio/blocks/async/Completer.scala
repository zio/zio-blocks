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
    val v = a.asInstanceOf[AnyRef]
    // A raw `null` value must be stored as the `NullValue` sentinel: storing
    // plain `null` would collide with the empty state, so `compareAndSet(null,
    // null)` would be a silent no-op and the completer would never settle.
    settle(if (v == null) NullValue else v)
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
      val w = s.asInstanceOf[WaitingMarker].waker
      if (state.compareAndSet(s, value)) w.wake()
      else settle(value)
    }
    // else: already settled — first writer wins, ignore subsequent attempts.
  }

  @tailrec
  def poll(waker: Waker): Async[A] = {
    val s = state.get
    if (s == null) {
      if (state.compareAndSet(null, new WaitingMarker(waker))) this
      else poll(waker)
    } else if (s.isInstanceOf[WaitingMarker]) {
      // Re-poll with a (possibly new) waker: replace the waiter.
      if (state.compareAndSet(s, new WaitingMarker(waker))) this
      else poll(waker)
    } else if (s eq NullValue) null.asInstanceOf[Async[A]] // settled with a raw null
    else s.asInstanceOf[Async[A]]                          // settled: the value (or Failure)
  }

  /**
   * A snapshot of this completer as an [[Async]] that does '''not''' register a
   * waker: if it has already been completed, the resulting `Async` is already
   * completed with that value or failure; otherwise it is the still-pending
   * computation backed by this completer.
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
   * Wraps a `Waker` so it is distinguishable from a completed value (which is
   * also stored as an `AnyRef`). Allocated only on the first `poll` that
   * arrives before completion — the sync-complete hot path never sees it.
   */
  final class WaitingMarker(val waker: Waker)

  /**
   * Sentinel stored in the state slot for a completion with a raw `null` value,
   * distinguishing "completed with null" from the empty (`null`) state. Never
   * escapes the completer: [[Completer.poll]] / [[Completer.peek]] hand it back
   * out as a bare `null` (a ready value).
   */
  private[async] val NullValue: AnyRef = new AnyRef
}

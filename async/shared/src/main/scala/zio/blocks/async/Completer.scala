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
 * Completion capability handed to an [[Async.promise]] block. Thread-safe: a
 * small CAS state machine makes the poll/complete handoff lossless across
 * threads (`succeed`/`fail` from an I/O callback, `poll` from the scheduler).
 *
 * A completer IS a [[Pollable]], so it can be returned directly as the
 * `Async[A]` of a `promise { }` block via [[peek]] (without an extra wrapper).
 *
 * Completion is one-shot — the first call to `succeed` or `fail` wins, all
 * subsequent calls are silent no-ops.
 *
 * ==Encoding==
 *
 * The internal state is a single `AnyRef` slot, distinguished without any
 * per-completion wrapper allocation:
 *
 *   - `null` — empty (no completion, no poll yet).
 *   - an instance of [[Completer.WaitingMarker]] — a `poll` is parked on the
 *     wrapped `waker`. Allocated lazily on the first `poll` that arrives before
 *     `succeed`/`fail`.
 *   - anything else — the completed value (boxed `A`) or a [[Failure]] for a
 *     failed completion. No wrapper allocation in the synchronous-completion
 *     hot path (the body completed the completer before `promise` peeked).
 */
final class Completer[A] extends Pollable[A] {
  import Completer.WaitingMarker

  // null = empty; WaitingMarker = waiting; anything else = the value (or Failure)
  private val state = new AtomicReference[AnyRef](null)

  /** Complete with a value. Idempotent (first call wins). */
  def succeed(a: A): Unit = settle(a.asInstanceOf[AnyRef])

  /**
   * Complete with a failure. Idempotent (first call wins). Stored as a
   * [[Failure]] so the slow-path discriminator picks it up identically to a
   * value produced by [[Async.fail]].
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
    } else s.asInstanceOf[Async[A]] // settled: the value (or Failure)
  }

  /**
   * Pure read — does NOT register a waker. Used by [[Async.promise]] for the
   * synchronous fast path: if the body completed the completer before
   * returning, `peek` yields the bare value (which collapses into the
   * `Async[A]` happy path) instead of a `Pollable`.
   *
   * On the empty / waiting path returns `this` (the completer itself is a
   * `Pollable`); on the completed path the stored value is returned directly,
   * with no wrapper unwrap.
   */
  def peek: Async[A] = {
    val s = state.get
    if (s == null || s.isInstanceOf[WaitingMarker]) this
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
}

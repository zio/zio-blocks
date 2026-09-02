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
 * Handle returned by [[Async.start]] (via [[Async.Running]]) that stops a
 * running [[Async]].
 *
 * [[cancel]] is idempotent and synchronous. It prevents a terminal value from
 * being published if cancellation linearizes before the run reaches completion,
 * and it is a no-op once the run has completed.
 *
 * Cancellation is '''driver-level only''': it stops the poll loop and
 * suppresses the callback, but does NOT guarantee that an already-running
 * `poll` has returned, nor does it abort an in-flight leaf (socket read, timer,
 * JS promise). Aborting a leaf is the source's responsibility.
 *
 * Extends [[java.lang.AutoCloseable]] so a [[Async.Running]] handle can be used
 * as a managed resource (`scala.util.Using`, Java try-with-resources):
 * [[close]] simply delegates to [[cancel]]. `cancel` remains the canonical verb
 * — it names the domain operation (stop an in-flight computation) and, unlike
 * `AutoCloseable.close`, declares no checked exception.
 */
trait Cancelable extends AutoCloseable {

  /**
   * Stop the driver loop. Idempotent and synchronous; a no-op after the run has
   * completed. See the trait documentation for the cancellation contract.
   */
  def cancel(): Unit

  /** Alias for [[cancel]] so a running handle works as an `AutoCloseable`. */
  final def close(): Unit = cancel()
}

/** Companion for [[Cancelable]], providing the no-op handle. */
object Cancelable {

  /**
   * A [[Cancelable]] whose [[Cancelable.cancel]] does nothing. Returned for
   * runs that complete synchronously (an already-ready or already-failed
   * [[Async]]), where there is no driver loop left to stop.
   */
  val noop: Cancelable = new Cancelable {
    def cancel(): Unit = ()
  }
}

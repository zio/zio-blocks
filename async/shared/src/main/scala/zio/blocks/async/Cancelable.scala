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
 * Something that can be stopped: the cancellation side of an `Async.Running`
 * and of an [[AsyncSelector]]. It extends `AutoCloseable`, so
 * `scala.util.Using` or a Java try-with-resources block cancels it on exit.
 *
 * For an `Async.Running`, `cancel()` stops the driver. If the call wins the
 * race against completion, no outcome is ever published, and the driver calls
 * [[Pollable.cancel]] on the pending leaf before `cancel()` returns. Only a
 * leaf that overrides that hook aborts its work (a socket read, a timer); the
 * default hook does nothing. Cancellation does not wait for a `poll` already in
 * progress, and it interrupts no thread. Once the run has completed, `cancel()`
 * does nothing.
 *
 * {{{
 * import scala.util.Using
 *
 * def watchPrices(): Int = { Thread.sleep(50); 0 }
 *
 * Using(Async.start(watchPrices())) { _ =>
 *   // other work while the watcher runs
 * } // leaving the block closes the handle, which cancels it
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/async/#cancelable Cancelable]]
 */
trait Cancelable extends AutoCloseable {

  /**
   * Requests cancellation and returns without waiting for cleanup to finish. A
   * second call, or a call after completion, does nothing.
   */
  def cancel(): Unit

  /** Calls `cancel()`, so a `Cancelable` works as an `AutoCloseable`. */
  final def close(): Unit = cancel()
}

/** Companion for [[Cancelable]], providing a handle that does nothing. */
object Cancelable {

  /**
   * A [[Cancelable]] whose `cancel()` does nothing: a placeholder where an API
   * needs a `Cancelable` but there is nothing to stop.
   *
   * {{{
   * val nothingToStop: Cancelable = Cancelable.noop
   * nothingToStop.cancel() // does nothing
   * }}}
   */
  val noop: Cancelable = new Cancelable {
    def cancel(): Unit = ()
  }
}

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
 * The representation of a failed [[Async]]: a [[Pollable]] that is already
 * finished, carrying its `cause`. `Async.fail`, `Completer#fail` and a throw
 * inside `Async.attempt` produce one. `map`, `flatMap` and `zipWith` pass it on
 * without calling their functions; `catchAll`, `orElse`, `either` and
 * `foldCause` recover it, `mapError` replaces its cause, and `block` throws the
 * cause.
 *
 * A throw inside a function passed to `map` or `flatMap` is not caught by a
 * later `catchAll` or `either` in the same chain. On a ready value it escapes
 * at the call site; on a pending value it escapes from the driver: `block`
 * throws it, and a run started with `start` fails with it. An `ensuring`
 * between the two is the exception: it turns the throw into a failure that the
 * later handler sees. Wrap throwing code in `Async.attempt` to turn it into a
 * failure the chain can recover.
 *
 * {{{
 * val timeout: Async[Int] = Async.fail(new java.util.concurrent.TimeoutException("quote service"))
 * timeout.either.block // Left(java.util.concurrent.TimeoutException: quote service)
 *
 * val parsed: Async[Int] = Async.succeed("8o80").flatMap(s => Async.attempt(s.toInt))
 * parsed.catchAll(_ => Async.succeed(8080)).block // 8080
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/async/#failure Failure]]
 */
final class Failure private (val cause: Throwable, private[async] val trusted: Boolean) extends Pollable[Nothing] {

  /** Public construction is deliberately an ordinary, untrusted failure. */
  def this(cause: Throwable) = this(cause, false)

  /** Returns this failure; it never registers `onComplete`. */
  def poll(onComplete: Runnable): Async[Nothing] = this
}

private[async] object Failure {

  /**
   * Internal typed failures can only enter through Async's blocks-private API.
   */
  def trusted(cause: Throwable): Failure = new Failure(cause, true)

  /**
   * Thrown by [[throwCause]] when the logical failure cause is `null`. The JVM
   * and Scala.js backends cannot `throw null`, so this marker reifies the same
   * channel `.either` exposes as `Left(null)`.
   */
  object NullCauseMarker extends Throwable(null, null, false, false) with scala.util.control.NoStackTrace {
    override def fillInStackTrace(): Throwable = this
  }

  /**
   * Re-throw a failure cause for `.block` / `awaitSuspended`, including `null`.
   */
  def throwCause(cause: Throwable): Nothing =
    if (cause eq null) throw NullCauseMarker
    else throw cause

  /** Decode a thrown transport marker back to the logical failure cause. */
  def unwindCause(t: Throwable): Throwable =
    if (t eq NullCauseMarker) null else t
}

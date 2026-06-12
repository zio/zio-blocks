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

import scala.concurrent.ExecutionContext
import scala.scalajs.concurrent.JSExecutionContext

import zio._
import zio.test.Live

/**
 * Shared Scala.js-only test fixtures: an [[ExecutionContext]] for the microtask
 * queue, a `drain` effect that lets queued microtasks/timers run, the eager-run
 * helpers, and the family of hand-rolled [[Pollable]]s used to probe the JS
 * driver. Consolidated here so `AsyncJsSpec` and `AsyncInteropSpec` share one
 * definition instead of each re-deriving them.
 */
private[async] object AsyncJsTestSupport {

  private val boom: Throwable = AsyncTestSupport.boom

  /** Implicit EC for `AsyncInterop.toFuture` / `ZIO.fromFuture`. */
  implicit val queue: ExecutionContext = JSExecutionContext.queue

  /** Let queued microtasks / timers run to completion before asserting. */
  val drain: UIO[Unit] = Live.live(ZIO.sleep(Duration.fromMillis(50)))

  /** Eagerly run `fa` and observe its terminal `Either`. */
  def runEither(fa: Async[Int]): UIO[Either[Throwable, Int]] =
    ZIO.async[Any, Nothing, Either[Throwable, Int]] { k =>
      AsyncTestSupport.startEither(fa)(res => k(ZIO.succeed(res)))
      ()
    }

  /** A pollable whose poll throws the null-cause marker (a failure with a `null` cause). */
  def markerThrowingPollable: Pollable[Int] = new Pollable[Int] {
    def poll(onComplete: Runnable): Async[Int] = throw Failure.NullCauseMarker
  }

  /** Never becomes ready: stays pending on every poll. */
  final class NeverReady extends Pollable[Int] {
    def poll(onComplete: Runnable): Async[Int] = this
  }

  /**
   * Fires `onComplete` more than once, spread across MULTIPLE polls: 3x on poll
   * #1, 2x on poll #2, then ready with value `3` on poll #3. The driver must
   * coalesce redundant resumptions and not re-poll a completed pollable.
   */
  final class MultiWake extends Pollable[Int] {
    var polls = 0
    def poll(onComplete: Runnable): Async[Int] = {
      polls += 1
      if (polls == 1) { onComplete.run(); onComplete.run(); onComplete.run(); this }
      else if (polls == 2) { onComplete.run(); onComplete.run(); this }
      else Async.succeed(polls)
    }
  }

  /** Always pending; always fires `onComplete` (used by the cancellation-race probe). */
  final class AlwaysWake extends Pollable[Int] {
    var polls = 0
    def poll(onComplete: Runnable): Async[Int] = { polls += 1; onComplete.run(); this }
  }

  /** Captures the `onComplete`; after completion the test fires the stale runnable. */
  final class StaleWaker extends Pollable[Int] {
    var captured: Runnable = null
    var polls              = 0
    def poll(onComplete: Runnable): Async[Int] = {
      polls += 1
      captured = onComplete
      if (polls == 1) { onComplete.run(); this }
      else Async.succeed(5)
    }
  }

  /** Suspends on poll #1 (fires `onComplete`), THROWS on the resumption poll #2. */
  final class ResumeThrow extends Pollable[Int] {
    var polls = 0
    def poll(onComplete: Runnable): Async[Int] = {
      polls += 1
      if (polls == 1) { onComplete.run(); this }
      else throw boom
    }
  }

  /** Suspends on poll #1 (fires `onComplete`), FAILS on the resumption poll #2. */
  final class ResumeFail extends Pollable[Int] {
    var polls = 0
    def poll(onComplete: Runnable): Async[Int] = {
      polls += 1
      if (polls == 1) { onComplete.run(); this }
      else Async.fail(boom)
    }
  }

  /** Throws on the very first poll. */
  final class ThrowFirst extends Pollable[Int] {
    def poll(onComplete: Runnable): Async[Int] = throw boom
  }

  /**
   * A chain of distinct pollables: each `poll` arms `onComplete` and returns the
   * NEXT, brand-new pollable, never `this`. The driver only walks the chain to
   * completion if it advances to the pollable returned by `poll` rather than
   * re-polling the original.
   */
  final class StepChain(remaining: Int, taken: Int) extends Pollable[Int] {
    def poll(onComplete: Runnable): Async[Int] =
      if (remaining <= 0) Async.succeed(taken)
      else {
        onComplete.run()
        new StepChain(remaining - 1, taken + 1)
      }
  }

  /**
   * Fires `onComplete` TWICE on the first poll then stays pending; ready on every
   * later poll. The driver must coalesce the redundant resumption: a completed
   * pollable must not be re-polled.
   */
  final class DoubleWake extends Pollable[Int] {
    var polls = 0
    def poll(onComplete: Runnable): Async[Int] = {
      polls += 1
      if (polls == 1) { onComplete.run(); onComplete.run(); this }
      else Async.succeed(42)
    }
  }
}

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

import java.util.concurrent.atomic.AtomicBoolean

import zio._
import zio.test._

/**
 * Cross-platform coverage of [[Async.unsafeRunAsync]] and [[Cancelable]]. The
 * callback is bridged into ZIO via `ZIO.async` so the same suite drives the JVM
 * worker-thread path and the Scala.js microtask path identically. Suspended
 * inputs are completed from a forked fiber (a real thread on the JVM, a
 * microtask on JS), exercising the no-lost-wakeup resume path on both.
 *
 * Cancellation-suppression assertions wait on the '''live''' clock (real time
 * on both platforms) so a worker/microtask that erroneously delivered would be
 * observed before the assertion.
 */
object AsyncRunSpec extends ZIOSpecDefault {

  private val boom = new RuntimeException("boom")

  /** Bridge a run into ZIO; fails the effect if the run fails. */
  private def runAsync[A](fa: Async[A]): Task[A] =
    ZIO.async[Any, Throwable, A] { k =>
      Async.unsafeRunAsync(fa)(e => k(ZIO.fromEither(e)))
      ()
    }

  /**
   * A chain of '''distinct''' pollables: each `poll` arms the waker and returns
   * the NEXT, brand-new pollable in the chain (never `this`), accumulating the
   * step count, until the chain is exhausted and it yields the count as a
   * value.
   *
   * A driver that resumes by re-polling the pollable it originally received
   * would loop forever on the head and never deliver; one that advances to the
   * pollable returned by `poll` (the JVM `awaitSuspended` semantic the JS
   * driver is aligned to) walks the chain to completion. Driving this to
   * `length` is therefore a falsifiable assertion of that alignment on both
   * platforms.
   */
  private final class StepChain(remaining: Int, taken: Int) extends Pollable[Int] {
    def poll(waker: Waker): Async[Int] =
      if (remaining <= 0) Async.succeed(taken)
      else {
        waker.wake()
        new StepChain(remaining - 1, taken + 1)
      }
  }

  def spec = suite("AsyncRunSpec")(
    suite("synchronous fast path")(
      test("ready value invokes cb with Right on the caller thread") {
        var fired = Option.empty[Either[Throwable, Int]]
        Async.unsafeRunAsync(Async.succeed(42))(e => fired = Some(e))
        assertTrue(fired == Some(Right(42)))
      },
      test("ready failure invokes cb with Left on the caller thread") {
        var fired = Option.empty[Either[Throwable, Int]]
        Async.unsafeRunAsync[Int](Async.fail(boom))(e => fired = Some(e))
        assertTrue(fired == Some(Left(boom)))
      },
      test("ready value resolves through the ZIO bridge") {
        runAsync(Async.succeed(7)).map(v => assertTrue(v == 7))
      },
      test("ready failure resolves through the ZIO bridge") {
        runAsync[Int](Async.fail(boom)).either.map(e => assertTrue(e == Left(boom)))
      }
    ),
    suite("suspended path")(
      test("completes when a fiber settles the Completer (no lost wakeup)") {
        val c = new Completer[Int]
        for {
          fiber <- runAsync(c.peek).fork
          _     <- ZIO.succeed(c.succeed(99))
          v     <- fiber.join
        } yield assertTrue(v == 99)
      },
      test("fails when a fiber fails the Completer") {
        val c = new Completer[Int]
        for {
          fiber <- runAsync(c.peek).either.fork
          _     <- ZIO.succeed(c.fail(boom))
          e     <- fiber.join
        } yield assertTrue(e == Left(boom))
      },
      test("driver advances to the pollable returned by poll (not re-polling the original)") {
        // Would never deliver under a re-poll-the-original driver; completes
        // only because the runner walks the chain of distinct pollables.
        runAsync(new StepChain(5, 0)).map(v => assertTrue(v == 5))
      }
    ),
    suite("failure surfacing")(
      test("a Throwable escaping poll becomes cb(Left)") {
        val thrower: Async[Int] = new Pollable[Int] {
          def poll(waker: Waker): Async[Int] = throw boom
        }
        runAsync(thrower).either.map(e => assertTrue(e == Left(boom)))
      }
    ),
    suite("cancellation")(
      test("cancel is idempotent and a no-op after synchronous completion") {
        var count      = 0
        val cancelable = Async.unsafeRunAsync(Async.succeed(1))(_ => count += 1)
        cancelable.cancel()
        cancelable.cancel()
        assertTrue(count == 1)
      },
      test("cancel before completion suppresses cb") {
        val c          = new Completer[Int]
        val fired      = new AtomicBoolean(false)
        val cancelable = Async.unsafeRunAsync(c.peek)(_ => fired.set(true))
        cancelable.cancel()
        c.succeed(1)
        Live.live(ZIO.sleep(100.millis)).as(assertTrue(!fired.get()))
      },
      test("cancel is idempotent on a genuinely suspended run") {
        // `Async.never` always takes the suspended `Run` path on both platforms,
        // so the second `cancel()` exercises the already-terminated no-op branch
        // (not the synchronous `Cancelable.noop` path).
        val fired      = new AtomicBoolean(false)
        val cancelable = Async.unsafeRunAsync[Nothing](Async.never)(_ => fired.set(true))
        cancelable.cancel()
        cancelable.cancel()
        Live.live(ZIO.sleep(100.millis)).as(assertTrue(!fired.get()))
      }
    )
  )
}

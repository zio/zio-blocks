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
import zio.test._

/**
 * Adversarial convergence probes for the BUG-D1 fix (JS drivers coalesce
 * redundant wakeups). Attacks both JS drivers — `AsyncInterop.toFuture`
 * (microtask `drive`) and `Async.unsafeRunAsync` (`AsyncRunner.Run.step`) —
 * with multi-wake, stale-waker, cancellation-race, re-entrant-wake, and
 * no-suspension shapes, and checks the two drivers agree.
 */
object AsyncDriverConvergenceProbeSpec extends ZIOSpecDefault {

  private implicit val ec: ExecutionContext = JSExecutionContext.queue

  private def drain = Live.live(ZIO.sleep(Duration.fromMillis(50)))

  // (a) Fires N>2 wakes spread across MULTIPLE polls, not just the first.
  private final class MultiWake extends Pollable[Int] {
    var polls = 0
    def poll(w: Waker): Async[Int] = {
      polls += 1
      if (polls == 1) { w.wake(); w.wake(); w.wake(); this }
      else if (polls == 2) { w.wake(); w.wake(); this }
      else Async.succeed(polls)
    }
  }

  // Always pending; always wakes (used by the cancellation-race probe).
  private final class AlwaysWake extends Pollable[Int] {
    var polls                      = 0
    def poll(w: Waker): Async[Int] = { polls += 1; w.wake(); this }
  }

  // (b) Captures the waker; after completion the test fires the stale waker.
  private final class StaleWaker extends Pollable[Int] {
    var captured: Waker = null
    var polls           = 0
    def poll(w: Waker): Async[Int] = {
      polls += 1
      captured = w
      if (polls == 1) { w.wake(); this }
      else Async.succeed(5)
    }
  }

  def spec = suite("AsyncDriverConvergenceProbeSpec")(
    // -------- (a) multi-wake across multiple polls --------
    test("toFuture: N>2 wakes across multiple polls — completes once, polled exactly 3x") {
      val p = new MultiWake
      for {
        v <- ZIO.fromFuture(_ => AsyncInterop.toFuture(p))
        _ <- drain
      } yield assertTrue(v == 3, p.polls == 3)
    },
    test("unsafeRunAsync: N>2 wakes across multiple polls — completes once, polled exactly 3x") {
      val p = new MultiWake
      for {
        v <- ZIO.async[Any, Throwable, Int] { k =>
               Async.unsafeRunAsync[Int](p)(e => k(ZIO.fromEither(e))); ()
             }
        _ <- drain
      } yield assertTrue(v == 3, p.polls == 3)
    },
    // -------- (b) stale waker fired after completion --------
    test("toFuture: stale waker fired after completion does not re-poll") {
      val p = new StaleWaker
      for {
        v <- ZIO.fromFuture(_ => AsyncInterop.toFuture(p))
        _ <- drain
        _  = p.captured.wake()
        _ <- drain
      } yield assertTrue(v == 5, p.polls == 2)
    },
    test("unsafeRunAsync: stale waker fired after completion does not re-poll") {
      val p = new StaleWaker
      for {
        v <- ZIO.async[Any, Throwable, Int] { k =>
               Async.unsafeRunAsync[Int](p)(e => k(ZIO.fromEither(e))); ()
             }
        _ <- drain
        _  = p.captured.wake()
        _ <- drain
      } yield assertTrue(v == 5, p.polls == 2)
    },
    // -------- (c) cancellation racing the coalescing guard --------
    test("unsafeRunAsync: cancel after a queued wake but before the microtask — cb never fires, no re-poll") {
      val p       = new AlwaysWake
      var cbCount = 0
      for {
        c <- ZIO.succeed(Async.unsafeRunAsync[Int](p)(_ => cbCount += 1))
        _  = c.cancel() // poll #1 already ran synchronously and queued a microtask
        _ <- drain
      } yield assertTrue(cbCount == 0, p.polls == 1)
    },
    // -------- (d) driver agreement on the same multi-wake shape --------
    test("driver agreement: toFuture and unsafeRunAsync yield identical value+poll-count") {
      val pf = new MultiWake
      val pr = new MultiWake
      for {
        vf <- ZIO.fromFuture(_ => AsyncInterop.toFuture(pf))
        vr <- ZIO.async[Any, Throwable, Int] { k =>
                Async.unsafeRunAsync[Int](pr)(e => k(ZIO.fromEither(e))); ()
              }
        _  <- drain
      } yield assertTrue(vf == vr, pf.polls == pr.polls, vf == 3)
    },
    // -------- (e) value on poll #1 (no suspension) through the suspended entry path --------
    test("toFuture: pollable that returns a value on the first poll resolves") {
      val p = new Pollable[Int] { def poll(w: Waker): Async[Int] = Async.succeed(7) }
      for {
        v <- ZIO.fromFuture(_ => AsyncInterop.toFuture(p))
        _ <- drain
      } yield assertTrue(v == 7)
    },
    test("unsafeRunAsync: pollable that returns a value on the first poll resolves") {
      val p = new Pollable[Int] { def poll(w: Waker): Async[Int] = Async.succeed(7) }
      for {
        v <- ZIO.async[Any, Throwable, Int] { k =>
               Async.unsafeRunAsync[Int](p)(e => k(ZIO.fromEither(e))); ()
             }
        _ <- drain
      } yield assertTrue(v == 7)
    },
    // -------- (f) re-entrant wake() from inside terminate's callback --------
    test("unsafeRunAsync: re-entrant wake() from the completion callback does not re-poll or re-deliver") {
      val p = new StaleWaker
      var cbCount = 0
      for {
        v <- ZIO.async[Any, Throwable, Int] { k =>
               Async.unsafeRunAsync[Int](p) { e =>
                 cbCount += 1
                 p.captured.wake() // re-entrant: fired from inside the terminating callback
                 k(ZIO.fromEither(e))
               }
               ()
             }
        _ <- drain
      } yield assertTrue(v == 5, p.polls == 2, cbCount == 1)
    },
    // -------- sweep: throwing-poll driver parity (DEFECT PROBE) --------
    test("toFuture: a throwing poll yields a failed Future, never a synchronous throw (JVM/unsafeRunAsync parity)") {
      val boom = new RuntimeException("poll-boom")
      val p    = new Pollable[Int] { def poll(w: Waker): Async[Int] = throw boom }
      // Oracle: JVM toFuture catches a thrown poll -> p.failure(t); JS unsafeRunAsync
      // catches a thrown poll -> terminate(Left(t)). JS toFuture must not throw synchronously.
      assertTrue(scala.util.Try(AsyncInterop.toFuture(p)).isSuccess)
    },
    test("unsafeRunAsync: a throwing poll is delivered as Left, not thrown (control)") {
      val boom = new RuntimeException("poll-boom")
      val p    = new Pollable[Int] { def poll(w: Waker): Async[Int] = throw boom }
      for {
        e <- ZIO.async[Any, Throwable, Either[Throwable, Int]] { k =>
               Async.unsafeRunAsync[Int](p)(res => k(ZIO.succeed(res))); ()
             }
        _ <- drain
      } yield assertTrue(e == Left(boom))
    }
  )
}

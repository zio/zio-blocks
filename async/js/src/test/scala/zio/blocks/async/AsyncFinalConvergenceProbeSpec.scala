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

import scala.concurrent.{ExecutionContext, Promise => SPromise}
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext

import zio._
import zio.test._

/**
 * FINAL convergence probes for the cross-platform JS driver/interop surface.
 * Attacks the shapes the regular suites do not yet probe deeply:
 *   - throwing poll on the RESUMPTION microtask (not just the first poll)
 *   - toJsPromise error/rejection parity (delegates to toFuture)
 *   - fromFuture/fromJsPromise: failed, never-completing, racing resolution
 *   - tri-driver agreement (unsafeRunAsync vs toFuture vs toJsPromise)
 * Every hang-risk probe is bounded by an internal max-iteration counter and a
 * zio-test timeout so an orphaned future fails fast instead of hanging.
 */
object AsyncFinalConvergenceProbeSpec extends ZIOSpecDefault {

  private implicit val ec: ExecutionContext = JSExecutionContext.queue

  private def drain = Live.live(ZIO.sleep(Duration.fromMillis(50)))

  private val boom = new RuntimeException("boom")

  // Suspends on poll #1 (wakes), THROWS on the resumption poll #2.
  private final class ResumeThrow extends Pollable[Int] {
    var polls = 0
    def poll(w: Waker): Async[Int] = {
      polls += 1
      if (polls == 1) { w.wake(); this }
      else throw boom
    }
  }

  // Suspends on poll #1 (wakes), FAILS (returns Failure) on resumption poll #2.
  private final class ResumeFail extends Pollable[Int] {
    var polls = 0
    def poll(w: Waker): Async[Int] = {
      polls += 1
      if (polls == 1) { w.wake(); this }
      else Async.fail(boom)
    }
  }

  // Throws on the very first poll.
  private final class ThrowFirst extends Pollable[Int] {
    def poll(w: Waker): Async[Int] = throw boom
  }

  // Fires its waker TWICE across two polls, ready on poll #3 with value 3.
  private final class MultiWake extends Pollable[Int] {
    var polls = 0
    def poll(w: Waker): Async[Int] = {
      polls += 1
      if (polls == 1) { w.wake(); w.wake(); w.wake(); this }
      else if (polls == 2) { w.wake(); w.wake(); this }
      else Async.succeed(polls)
    }
  }

  private def runEither(fa: Async[Int]): ZIO[Any, Nothing, Either[Throwable, Int]] =
    ZIO.async[Any, Nothing, Either[Throwable, Int]] { k =>
      Async.unsafeRunAsync[Int](fa)(res => k(ZIO.succeed(res))); ()
    }

  def spec = suite("AsyncFinalConvergenceProbeSpec")(
    // ---- (1) throwing poll on the RESUMPTION microtask ----
    test("toFuture: poll throwing on the resumption microtask yields a failed Future (no hang)") {
      val p = new ResumeThrow
      for {
        e <- ZIO.fromFuture(_ => AsyncInterop.toFuture(p)).either
        _ <- drain
      } yield assertTrue(e == Left(boom), p.polls == 2)
    },
    test("unsafeRunAsync: poll throwing on the resumption microtask is delivered as Left (control)") {
      val p = new ResumeThrow
      for {
        e <- runEither(p)
        _ <- drain
      } yield assertTrue(e == Left(boom), p.polls == 2)
    },
    test("toJsPromise: poll throwing on the resumption microtask rejects the promise") {
      val p = new ResumeThrow
      for {
        e <- ZIO.fromFuture(_ => AsyncInterop.toJsPromise(p).toFuture).either
        _ <- drain
      } yield assertTrue(e == Left(boom))
    },
    // ---- (2) suspended-then-Failure on resumption ----
    test("toFuture: a pollable that returns Failure on the resumption poll fails the Future") {
      val p = new ResumeFail
      for {
        e <- ZIO.fromFuture(_ => AsyncInterop.toFuture(p)).either
        _ <- drain
      } yield assertTrue(e == Left(boom), p.polls == 2)
    },
    test("driver agreement: ResumeFail surfaces Left(boom) on unsafeRunAsync and toFuture alike") {
      val pr = new ResumeFail
      val pf = new ResumeFail
      for {
        er <- runEither(pr)
        ef <- ZIO.fromFuture(_ => AsyncInterop.toFuture(pf)).either
        _  <- drain
      } yield assertTrue(er == Left(boom), ef == Left(boom), pr.polls == pf.polls)
    },
    // ---- (3) toJsPromise error parity ----
    test("toJsPromise: throwing poll (initial) rejects the promise with the cause") {
      val p = new ThrowFirst
      for {
        e <- ZIO.fromFuture(_ => AsyncInterop.toJsPromise(p).toFuture).either
        _ <- drain
      } yield assertTrue(e == Left(boom))
    },
    test("toJsPromise: a suspended pollable that later fails rejects the promise") {
      val a = Async.promiseInternal[Int] { c =>
        js.timers.setTimeout(0.0)(c.fail(boom)); ()
      }
      for {
        e <- ZIO.fromFuture(_ => AsyncInterop.toJsPromise(a).toFuture).either
        _ <- drain
      } yield assertTrue(e == Left(boom))
    },
    test("toJsPromise: multi-wake pollable resolves exactly once and is not re-polled") {
      val p = new MultiWake
      for {
        v <- ZIO.fromFuture(_ => AsyncInterop.toJsPromise(p).toFuture)
        _ <- drain
      } yield assertTrue(v == 3, p.polls == 3)
    },
    // ---- (4) fromFuture / fromJsPromise ----
    test("fromFuture: a pending future that fails surfaces the cause via toFuture") {
      val sp = SPromise[Int]()
      js.timers.setTimeout(0.0)(sp.failure(boom))
      val a = AsyncInterop.fromFuture(sp.future)
      for {
        e <- ZIO.fromFuture(_ => AsyncInterop.toFuture(a)).either
        _ <- drain
      } yield assertTrue(e == Left(boom))
    },
    test("fromFuture: a never-completing future yields an Async that never completes (times out, no spurious value)") {
      val sp = SPromise[Int]() // never completed
      val a  = AsyncInterop.fromFuture(sp.future)
      for {
        o <- Live.live(ZIO.fromFuture(_ => AsyncInterop.toFuture(a)).timeout(Duration.fromMillis(200)))
      } yield assertTrue(o.isEmpty)
    },
    test("fromJsPromise: a rejected promise surfaces the cause") {
      val rejected: js.Promise[Int] =
        js.Promise.reject(boom).asInstanceOf[js.Promise[Int]]
      val a = AsyncInterop.fromJsPromise(rejected)
      for {
        e <- ZIO.fromFuture(_ => AsyncInterop.toFuture(a)).either
        _ <- drain
      } yield assertTrue(e.isLeft)
    },
    test("fromJsPromise: a resolved promise surfaces the value (resolution racing the poll)") {
      val resolved: js.Promise[Int] = js.Promise.resolve[Int](123)
      val a                         = AsyncInterop.fromJsPromise(resolved)
      for {
        v <- ZIO.fromFuture(_ => AsyncInterop.toFuture(a))
        _ <- drain
      } yield assertTrue(v == 123)
    },
    // ---- (5) tri-driver agreement on a failed pollable ----
    test("tri-driver agreement: a suspended-then-fail pollable surfaces Left(boom) on all three drivers") {
      def mk = Async.promiseInternal[Int] { c =>
        js.timers.setTimeout(0.0)(c.fail(boom)); ()
      }
      for {
        eRun <- runEither(mk)
        eFut <- ZIO.fromFuture(_ => AsyncInterop.toFuture(mk)).either
        eJs  <- ZIO.fromFuture(_ => AsyncInterop.toJsPromise(mk).toFuture).either
        _    <- drain
      } yield assertTrue(eRun == Left(boom), eFut == Left(boom), eJs == Left(boom))
    },
    // ---- (6) tri-driver agreement on a throwing initial poll ----
    test("tri-driver agreement: a throwing initial poll surfaces Left(boom) on all three drivers") {
      for {
        eRun <- runEither(new ThrowFirst)
        eFut <- ZIO.fromFuture(_ => AsyncInterop.toFuture(new ThrowFirst)).either
        eJs  <- ZIO.fromFuture(_ => AsyncInterop.toJsPromise(new ThrowFirst).toFuture).either
        _    <- drain
      } yield assertTrue(eRun == Left(boom), eFut == Left(boom), eJs == Left(boom))
    }
  )
}

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

import zio._
import zio.test._

import scala.concurrent.{Future, Promise => SPromise}
import scala.scalajs.js
import scala.util.Try

import AsyncJsTestSupport._

/**
 * Scala.js cannot block.
 */
object AsyncJsSpec extends ZIOSpecDefault {

  private val boom = AsyncTestSupport.boom
  private val Boom = AsyncTestSupport.boom

  def spec = suite("AsyncJsSpec")(
    suite("cannot block")(
      test("await on a truly async pollable throws IllegalStateException") {
        val async  = new NeverReady: Async[Int]
        val thrown = scala.util.Try(async.block).failed.toOption
        assertTrue(
          thrown.exists(_.isInstanceOf[IllegalStateException]),
          thrown.map(_.getMessage.contains("cannot block")).getOrElse(false)
        )
      },
      test("await on an already-ready value still works on JS") {
        val r = Async.succeed(99).block
        assertTrue(r == 99)
      },
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
          v <- AsyncTestSupport.runAsync(p)
          _ <- drain
        } yield assertTrue(v == 3, p.polls == 3)
      },
      // -------- (b) stale waker fired after completion --------
      test("toFuture: stale waker fired after completion does not re-poll") {
        val p = new StaleWaker
        for {
          v <- ZIO.fromFuture(_ => AsyncInterop.toFuture(p))
          _ <- drain
          _  = p.captured.run()
          _ <- drain
        } yield assertTrue(v == 5, p.polls == 2)
      },
      test("unsafeRunAsync: stale waker fired after completion does not re-poll") {
        val p = new StaleWaker
        for {
          v <- AsyncTestSupport.runAsync(p)
          _ <- drain
          _  = p.captured.run()
          _ <- drain
        } yield assertTrue(v == 5, p.polls == 2)
      },
      // -------- (c) cancellation racing the coalescing guard --------
      test("unsafeRunAsync: cancel after a queued wake but before the microtask — cb never fires, no re-poll") {
        val p       = new AlwaysWake
        var cbCount = 0
        for {
          c <- ZIO.succeed(AsyncTestSupport.startTap(p)(_ => cbCount += 1))
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
          vr <- AsyncTestSupport.runAsync(pr)
          _  <- drain
        } yield assertTrue(vf == vr, pf.polls == pr.polls, vf == 3)
      },
      // -------- (e) value on poll #1 (no suspension) through the suspended entry path --------
      test("toFuture: pollable that returns a value on the first poll resolves") {
        val p = new Pollable[Int] { def poll(onComplete: Runnable): Async[Int] = Async.succeed(7) }
        for {
          v <- ZIO.fromFuture(_ => AsyncInterop.toFuture(p))
          _ <- drain
        } yield assertTrue(v == 7)
      },
      test("unsafeRunAsync: pollable that returns a value on the first poll resolves") {
        val p = new Pollable[Int] { def poll(onComplete: Runnable): Async[Int] = Async.succeed(7) }
        for {
          v <- AsyncTestSupport.runAsync(p)
          _ <- drain
        } yield assertTrue(v == 7)
      },
      // -------- (f) re-entrant wake() from inside terminate's callback --------
      test("unsafeRunAsync: re-entrant wake() from the completion callback does not re-poll or re-deliver") {
        val p       = new StaleWaker
        var cbCount = 0
        for {
          v <- ZIO.async[Any, Throwable, Int] { k =>
                 AsyncTestSupport.startEither(p) { res =>
                   cbCount += 1
                   p.captured.run() // re-entrant: fired from inside the terminating callback
                   k(ZIO.fromEither(res))
                 }
                 ()
               }
          _ <- drain
        } yield assertTrue(v == 5, p.polls == 2, cbCount == 1)
      },
      // -------- sweep: throwing-poll driver parity (DEFECT PROBE) --------
      test("toFuture: a throwing poll yields a failed Future, never a synchronous throw (JVM/unsafeRunAsync parity)") {
        val boom = new RuntimeException("poll-boom")
        val p    = new Pollable[Int] { def poll(onComplete: Runnable): Async[Int] = throw boom }
        // Oracle: JVM toFuture catches a thrown poll -> p.failure(t); JS unsafeRunAsync
        // catches a thrown poll -> terminate(Left(t)). JS toFuture must not throw synchronously.
        assertTrue(scala.util.Try(AsyncInterop.toFuture(p)).isSuccess)
      },
      test("unsafeRunAsync: a throwing poll is delivered as Left, not thrown (control)") {
        val boom = new RuntimeException("poll-boom")
        val p    = new Pollable[Int] { def poll(onComplete: Runnable): Async[Int] = throw boom }
        for {
          e <- ZIO.async[Any, Throwable, Either[Throwable, Int]] { k =>
                 AsyncTestSupport.startEither(p)(res => k(ZIO.succeed(res))); ()
               }
          _ <- drain
        } yield assertTrue(e == Left(boom))
      },
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
      test(
        "fromFuture: a never-completing future yields an Async that never completes (times out, no spurious value)"
      ) {
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
  )
}

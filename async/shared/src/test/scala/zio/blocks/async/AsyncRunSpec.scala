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

import zio.ZIO
import zio.test._

import zio.durationInt
import zio.test.Live
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

/**
 * start, driver parity, cancellation.
 */
object AsyncRunSpec extends ZIOSpecDefault {

  private final class StepChain(remaining: Int, taken: Int) extends Pollable[Int] {
    def poll(onComplete: Runnable): Async[Int] =
      if (remaining <= 0) Async.succeed(taken)
      else {
        onComplete.run()
        new StepChain(remaining - 1, taken + 1)
      }
  }

  private final class DoubleWake extends Pollable[Int] {
    val polls                                  = new AtomicInteger(0)
    def poll(onComplete: Runnable): Async[Int] = {
      val n = polls.incrementAndGet()
      if (n == 1) { onComplete.run(); onComplete.run(); this }
      else Async.succeed(42)
    }
  }

  private val boom = AsyncTestSupport.boom
  private val Boom = AsyncTestSupport.boom

  def spec = suite("AsyncRunSpec")(
    suite("start")(
      suite("synchronous fast path")(
        test("ready value invokes observer with Right on the caller thread") {
          var fired = Option.empty[Either[Throwable, Int]]
          AsyncTestSupport.startEither(Async.succeed(42))(e => fired = Some(e))
          assertTrue(fired == Some(Right(42)))
        },
        test("ready failure invokes observer with Left on the caller thread") {
          var fired = Option.empty[Either[Throwable, Int]]
          AsyncTestSupport.startEither[Int](Async.fail(boom))(e => fired = Some(e))
          assertTrue(fired == Some(Left(boom)))
        },
        test("ready value resolves through the ZIO bridge") {
          AsyncTestSupport.runAsync(Async.succeed(7)).map(v => assertTrue(v == 7))
        },
        test("ready failure resolves through the ZIO bridge") {
          AsyncTestSupport.runAsync[Int](Async.fail(boom)).either.map(e => assertTrue(e == Left(boom)))
        }
      ),
      suite("suspended path")(
        test("completes when a fiber settles the Completer (no lost wakeup)") {
          val c = new Completer[Int]
          for {
            fiber <- AsyncTestSupport.runAsync(c.peek).fork
            _     <- ZIO.succeed(c.succeed(99))
            v     <- fiber.join
          } yield assertTrue(v == 99)
        },
        test("fails when a fiber fails the Completer") {
          val c = new Completer[Int]
          for {
            fiber <- AsyncTestSupport.runAsync(c.peek).either.fork
            _     <- ZIO.succeed(c.fail(boom))
            e     <- fiber.join
          } yield assertTrue(e == Left(boom))
        },
        test("driver advances to the pollable returned by poll (not re-polling the original)") {
          // Would never deliver under a re-poll-the-AsyncTestSupport.original driver; completes
          // only because the runner walks the chain of distinct pollables.
          AsyncTestSupport.runAsync(new StepChain(5, 0)).map(v => assertTrue(v == 5))
        },
        test("map composed over a pollable that advances by returning a new pollable") {
          // Same conforming leaf as above (poll returns a NEW pending pollable, per
          // the Pollable contract: "returns a pending Async (typically itself)").
          // The combinator must keep driving it, exactly as the bare driver does,
          // rather than misreading the fresh pending pollable as a terminal value.
          val direct = AsyncTestSupport.fromPollable(new StepChain(2, 0)).block
          val mapped = scala.util.Try(AsyncTestSupport.fromPollable(new StepChain(2, 0)).map(_ + 1).block)
          assertTrue(direct == 2, mapped == scala.util.Success(3))
        }
      ),
      suite("failure surfacing")(
        test("a Throwable escaping poll becomes Left") {
          val thrower: Async[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = throw boom
          }
          AsyncTestSupport.runAsync(thrower).either.map(e => assertTrue(e == Left(boom)))
        },
        test("a throwing finalizer on a pending failure still surfaces the primary") {
          val c = new Completer[Int]
          val a = c.peek.ensuring(AsyncTestSupport.throwingFinalizer)
          for {
            out <- ZIO.async[Any, Nothing, Either[Throwable, Int]] { k =>
                     AsyncTestSupport.startEither(a)(res => k(ZIO.succeed(res)))
                     c.fail(AsyncTestSupport.primary)
                   }
          } yield assertTrue(out == Left(AsyncTestSupport.primary))
        }
      ),
      suite("cancellation")(
        test("cancel is idempotent and a no-op after synchronous completion") {
          var count   = 0
          val running = AsyncTestSupport.startTap(Async.succeed(1))(_ => count += 1)
          running.cancel()
          running.cancel()
          assertTrue(count == 1)
        },
        test("cancel before completion suppresses observer") {
          val c       = new Completer[Int]
          val fired   = new AtomicBoolean(false)
          val running = AsyncTestSupport.startTap(c.peek)(_ => fired.set(true))
          running.cancel()
          c.succeed(1)
          Live.live(ZIO.sleep(100.millis)).as(assertTrue(!fired.get()))
        },
        test("cancel is idempotent on a genuinely suspended run") {
          // `Async.never` always takes the suspended path on both platforms,
          // so the second `cancel()` exercises the already-settled no-op branch
          // (not the synchronous `CompletedRunning` path).
          val fired   = new AtomicBoolean(false)
          val running = AsyncTestSupport.startTap[Nothing](Async.never)(_ => fired.set(true))
          running.cancel()
          running.cancel()
          Live.live(ZIO.sleep(100.millis)).as(assertTrue(!fired.get()))
        }
      ),
      test("a multi-wake pollable is not re-polled after completion (JVM/JS parity)") {
        val p = new DoubleWake
        for {
          result <- AsyncTestSupport.runAsync(p)
          // Let any stray resumptions (extra microtasks on JS) drain before we read.
          _ <- Live.live(ZIO.sleep(50.millis))
        } yield assertTrue(result == 42, p.polls.get() == 2)
      },
      test("start of a nested succeed carrier preserves depth (flatten agrees with the unstarted value)") {
        // A Running is itself an Async[A], so composing the same operator over
        // the started and unstarted value must agree. `flatten` peels exactly
        // one succeed layer, and `deliverSuccess` peels nested layers one at a
        // time — so a double-succeed pollable-as-value carrier must come out of
        // `start` with its depth intact. Collapsing it to depth 1 makes the
        // post-start `flatten` re-expose the user pollable as a suspended
        // computation, replacing the value with its polled scalar.
        val inner                               = AsyncTestSupport.pollableSuccessValue
        val nested: Async[Async[Pollable[Int]]] = Async.succeed(Async.succeed(inner))
        val direct: AnyRef                      = (nested.flatten.block: AnyRef)
        val started: AnyRef                     = (Async.start(nested).flatten.block: AnyRef)
        assertTrue(direct eq inner, started eq inner)
      }
    ),
    suite("cancel")(
      test("cancel is idempotent and a no-op after synchronous completion") {
        var count   = 0
        val running = AsyncTestSupport.startTap(Async.succeed(1))(_ => count += 1)
        running.cancel()
        running.cancel()
        assertTrue(count == 1)
      },
      test("cancel before completion suppresses observer") {
        val c       = new Completer[Int]
        val fired   = new AtomicBoolean(false)
        val running = AsyncTestSupport.startTap(c.peek)(_ => fired.set(true))
        running.cancel()
        c.succeed(1)
        Live.live(ZIO.sleep(100.millis)).as(assertTrue(!fired.get()))
      },
      test("cancel is idempotent on a genuinely suspended run") {
        val fired   = new AtomicBoolean(false)
        val running = AsyncTestSupport.startTap[Nothing](Async.never)(_ => fired.set(true))
        running.cancel()
        running.cancel()
        Live.live(ZIO.sleep(100.millis)).as(assertTrue(!fired.get()))
      }
    )
  )
}

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
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}

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
        test("completes when a fiber settles the Completer with null (terminal is published)") {
          // A suspended run that resolves to a raw null success must still
          // publish its terminal: the runner cannot reuse null as its own
          // "not yet settled" sentinel (the JS runner keeps an explicit
          // has-terminal flag; the JVM runner must agree — JVM/JS parity).
          val c = new Completer[String]
          for {
            fiber <- AsyncTestSupport.runAsync(c.peek).fork
            _     <- ZIO.succeed(c.succeed(null))
            v     <- Live.live(
                   fiber.join
                     .timeoutFail(new RuntimeException("Running never published the null terminal"))(5.seconds)
                 )
          } yield assertTrue(v == null)
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
      suite("eval runner (Async.start(body))")(
        test("evaluates the thunk off the caller and publishes its value") {
          AsyncTestSupport.runAsync(Async.start(21 * 2)).map(v => assertTrue(v == 42))
        },
        test("reifies a thrown body as a failure") {
          val running = Async.start[Int] {
            val n = 0
            if (n == 0) throw boom
            n
          }
          AsyncTestSupport.runAsync(running).either.map(e => assertTrue(e == Left(boom)))
        },
        test("reifies a Nothing-typed throwing body as a failure (no eager throw at the call site)") {
          // `Async.start(body)` is documented as "the `Async` analogue of
          // `Future.apply`", which captures a throwing body. When the by-name
          // body is statically `Nothing`-typed (e.g. `{ setup(); throw e }`,
          // `sys.error(...)`, `???`), overload resolution must still pick the
          // by-name `start(body: => A)` entry point — NOT the by-value
          // `start(fa: Async[A])` one (a bare `throw` is `Nothing <: Async[A]`),
          // which would force the body eagerly and throw at the call site.
          val started =
            try Right(Async.start { val _ = 0; throw boom })
            catch { case t: Throwable => Left(t) }
          started match {
            case Right(running) =>
              AsyncTestSupport.runAsync(running).either.map(e => assertTrue(e == Left(boom)))
            case Left(eager) =>
              // The body was forced eagerly at the call site (overload picked the
              // by-value `Async[A]` arm) instead of being captured into a Running.
              ZIO.succeed(assertTrue((eager: Throwable) == null, eager != boom))
          }
        },
        test("publishes a null body result (terminal is published)") {
          // Same publication contract as the Completer-settled null above, via
          // the by-name `Async.start(body)` entry point. Observed by polling
          // the Running directly (the standard Pollable driver protocol) so a
          // defective run fails the assertion after the bounded wait instead of
          // parking a second runner forever.
          val running = Async.start {
            val s: String = null
            s
          }
          def settled = !AsyncTestSupport.isPending(running.poll(AsyncTestSupport.noopRunnable))
          for {
            published <- Live.live(
                           (ZIO.sleep(10.millis) *> ZIO.succeed(settled))
                             .repeatUntil(identity)
                             .timeoutTo(false)(identity)(5.seconds)
                         )
            out = running.poll(AsyncTestSupport.noopRunnable)
          } yield assertTrue(published, out == null)
        },
        test("Async.start(body) evaluating to a suspended Async wraps it (the body's tap is NOT driven)") {
          // `Async.start(body)` is `Future.apply`-shaped: it evaluates the
          // by-name body and lifts its RESULT with the standard encoding. When
          // the body is itself a suspended `Async` (a `tap` over a pending
          // pollable), the result is carried as a pollable-as-value — never run
          // as a computation. So the `tap` effect must NOT fire: driving an
          // already-built `Async` is the `fa.start` extension's job (see the
          // "drive" suite below), not the companion `Async.start(body)`.
          val fired = new AtomicBoolean(false)
          val c     = new Completer[Int]
          val body  = c.peek.tap { _ => fired.set(true); Async.succeed(()) }
          Async.start[Async[Int]](body)
          c.succeed(1)
          Live.live(ZIO.sleep(200.millis)).as(assertTrue(!fired.get()))
        }
      ),
      suite("drive extension (fa.start)")(
        test("fa.start drives a tap over a pending pollable to completion (the effect fires)") {
          // The differential partner of the eval-runner test above: the `fa.start`
          // extension DRIVES an already-built `Async`, so a `tap` composed before
          // `start` runs its effect once the leaf settles.
          val fired  = new AtomicBoolean(false)
          val c      = new Completer[Int]
          val driven = c.peek.tap { _ => fired.set(true); Async.succeed(()) }
          driven.start
          c.succeed(1)
          for {
            ok <- Live.live(
                    (ZIO.sleep(5.millis) *> ZIO.succeed(fired.get()))
                      .repeatUntil(identity)
                      .timeoutTo(false)(identity)(5.seconds)
                  )
          } yield assertTrue(ok)
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
        },
        test("cancel invoked from inside poll suppresses the terminal that same poll returns") {
          // `cancel()` is driver-level and always wins: a leaf that cancels its
          // own Running handle mid-poll (e.g. observing shutdown) must not have
          // the value it then returns published as a terminal — the handle
          // stays pending forever, exactly like a cancel from outside.
          val handle = new AtomicReference[Async.Running[Int]](null)
          val stash  = new AtomicReference[Runnable](null)
          val polls  = new AtomicInteger(0)
          val leaf   = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] =
              if (polls.incrementAndGet() == 1) { stash.set(onComplete); this }
              else { handle.get().cancel(); Async.succeed(42) }
          }
          val running = AsyncTestSupport.fromPollable(leaf).start
          handle.set(running)
          for {
            armed <- Live.live(
                       (ZIO.sleep(1.millis) *> ZIO.succeed(stash.get() ne null))
                         .repeatUntil(identity)
                         .timeoutTo(false)(identity)(5.seconds)
                     )
            _         = stash.get().run() // resume the driver: the next poll cancels mid-flight
            repolled <- Live.live(
                          (ZIO.sleep(10.millis) *> ZIO.succeed(polls.get() >= 2))
                            .repeatUntil(identity)
                            .timeoutTo(false)(identity)(5.seconds)
                        )
            _ <- Live.live(ZIO.sleep(50.millis)) // allow any (defective) publish to land
          } yield assertTrue(
            armed,
            repolled,
            AsyncTestSupport.isPending(running.poll(AsyncTestSupport.noopRunnable))
          )
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
        // Observe through Async[AnyRef] (covariance) so the regression fails as
        // an assertion on every platform: blocking at the precise Pollable type
        // makes the depth-collapsed scalar trip Scala.js's CHECKED class cast
        // (an uncatchable UndefinedBehaviorError that kills the JS runner).
        val direct: AnyRef  = (nested.flatten: Async[AnyRef]).block
        val started: AnyRef = (nested.start.flatten: Async[AnyRef]).block
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

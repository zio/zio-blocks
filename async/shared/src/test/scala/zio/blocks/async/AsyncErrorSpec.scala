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

import zio.test._

/**
 * Cross-platform, cross-version semantics for the error channel: `Async.fail`,
 * `Async.attempt`, `.catchAll`, `.mapError`, `.orElse`, `.foldCause`,
 * `.either`. Mirrors the [[AsyncSpec]] style — only synchronously-resolvable
 * scenarios are tested here; thread-blocking variants live in
 * `AsyncBlockingSpec`.
 */
object AsyncErrorSpec extends ZIOSpecDefault {

  private val Boom: Throwable  = new RuntimeException("boom")
  private val Boom2: Throwable = new RuntimeException("boom2")

  /** A pollable that resolves to a failure on its first poll. */
  private final class FailAfter(t: Throwable, pollsNeeded: Int) extends Pollable[Nothing] {
    private var remaining                  = pollsNeeded
    def poll(waker: Waker): Async[Nothing] =
      if (remaining <= 0) Async.fail(t)
      else { remaining -= 1; waker.wake(); this }
  }

  /** A pollable that resolves to a success on its first poll. */
  private final class SucceedAfter[A](value: A, pollsNeeded: Int) extends Pollable[A] {
    private var remaining            = pollsNeeded
    def poll(waker: Waker): Async[A] =
      if (remaining <= 0) Async.succeed(value)
      else { remaining -= 1; waker.wake(); this }
  }

  def spec = suite("AsyncErrorSpec")(
    suite("Async.fail / await")(
      test("await on a failed value throws the cause") {
        val thrown = scala.util.Try(Async.fail(Boom).block).failed.toOption
        assertTrue(thrown.contains(Boom))
      },
      test("await on a fail pollable also throws the cause") {
        val pa: Async[Int] = new FailAfter(Boom, 0)
        val thrown         = scala.util.Try(pa.block).failed.toOption
        assertTrue(thrown.contains(Boom))
      },
      test("await on a fail pollable that suspends once still throws") {
        val pa: Async[Int] = new FailAfter(Boom, 1)
        val thrown         = scala.util.Try(pa.block).failed.toOption
        assertTrue(thrown.contains(Boom))
      }
    ),
    suite("Async.attempt")(
      test("captures a thrown exception as a failure") {
        val thrown = scala.util.Try(Async.attempt[Int](throw Boom).block).failed.toOption
        assertTrue(thrown.contains(Boom))
      },
      test("propagates a successful body as a value") {
        val r = Async.attempt(7).block
        assertTrue(r == 7)
      }
    ),
    suite(".catchAll")(
      test("recovers from a synchronous failure") {
        val r = Async.fail(Boom).catchAll(_ => Async.succeed(42)).block
        assertTrue(r == 42)
      },
      test("does not invoke the handler on success") {
        val r = Async.succeed(1).catchAll(_ => Async.succeed(99)).block
        assertTrue(r == 1)
      },
      test("the handler can itself fail") {
        val thrown = scala.util.Try(Async.fail(Boom).catchAll(_ => Async.fail(Boom2)).block).failed.toOption
        assertTrue(thrown.contains(Boom2))
      },
      test("recovers from a suspended failure") {
        val pa: Async[Int] = new FailAfter(Boom, 1)
        val r              = pa.catchAll(_ => Async.succeed(7)).block
        assertTrue(r == 7)
      },
      test("passes through a suspended success") {
        val pa: Async[Int] = new SucceedAfter(5, 1)
        val r              = pa.catchAll(_ => Async.succeed(99)).block
        assertTrue(r == 5)
      }
    ),
    suite("map / flatMap propagate failure")(
      test("map past a fail does not invoke f") {
        var called = false
        val r      = Async.fail(Boom).map { (_: Any) => called = true; 0 }
        val thrown = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(Boom), !called)
      },
      test("flatMap past a fail does not invoke f") {
        var called = false
        val r      = Async.fail(Boom).flatMap { (_: Any) => called = true; Async.succeed(0) }
        val thrown = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(Boom), !called)
      },
      test("flatMap that itself returns a failure propagates the new cause") {
        val r      = Async.succeed(1).flatMap(_ => Async.fail(Boom))
        val thrown = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(Boom))
      }
    ),
    suite(".mapError")(
      test("transforms the cause") {
        val r      = Async.fail(Boom).mapError(_ => Boom2)
        val thrown = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(Boom2))
      },
      test("does not run on success") {
        val r = Async.succeed(10).mapError(_ => Boom2).block
        assertTrue(r == 10)
      }
    ),
    suite(".orElse")(
      test("falls back to `that` on failure") {
        val r = Async.fail(Boom).orElse(Async.succeed(99)).block
        assertTrue(r == 99)
      },
      test("ignores `that` on success") {
        val r = Async.succeed(1).orElse(Async.succeed(99)).block
        assertTrue(r == 1)
      }
    ),
    suite(".foldCause / .either")(
      test("foldCause applies onFailure on failure") {
        val r = Async.fail(Boom).foldCause(t => s"err:${t.getMessage}")(_ => "ok").block
        assertTrue(r == "err:boom")
      },
      test("foldCause applies onSuccess on success") {
        val r = Async.succeed(7).foldCause(_ => -1)(x => x * 2).block
        assertTrue(r == 14)
      },
      test("either returns Left on failure") {
        val r = Async.fail(Boom).either.block
        assertTrue(r == Left(Boom))
      },
      test("either returns Right on success") {
        val r: Either[Throwable, Int] = Async.succeed(3).either.block
        assertTrue(r == Right(3))
      }
    ),
    // Uses `Async.promiseInternal` (cross-version `=>` shape) — see the
    // matching comment in `AsyncSpec` for why.
    suite("Completer.fail")(
      test("synchronous completer fail surfaces as a Failure") {
        val a      = Async.promiseInternal[Int](c => c.fail(Boom))
        val thrown = scala.util.Try(a.block).failed.toOption
        assertTrue(thrown.contains(Boom))
      },
      test("succeed-then-fail keeps the first outcome (succeed)") {
        val r = Async
          .promiseInternal[Int] { c =>
            c.succeed(1)
            c.fail(Boom)
          }
          .block
        assertTrue(r == 1)
      },
      test("fail-then-succeed keeps the first outcome (fail)") {
        val a = Async.promiseInternal[Int] { c =>
          c.fail(Boom)
          c.succeed(1)
        }
        val thrown = scala.util.Try(a.block).failed.toOption
        assertTrue(thrown.contains(Boom))
      }
    )
  )
}

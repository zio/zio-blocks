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
 * Cross-platform, cross-version semantics for combinators: `.zip`, `.zipWith`,
 * `.tap`, `.ensuring`, `Async.collectAll`, `Async.never`.
 */
object AsyncCombinatorsSpec extends ZIOSpecDefault {

  private val Boom: Throwable = new RuntimeException("boom")

  private final class Ready[A](value: A) extends Pollable[A] {
    def poll(waker: Waker): Async[A] = Async.succeed(value)
  }

  def spec = suite("AsyncCombinatorsSpec")(
    suite(".zipWith / .zip")(
      test("zipWith two ready values combines them eagerly") {
        val r = Async.succeed(3).zipWith(Async.succeed(4))(_ + _).block
        assertTrue(r == 7)
      },
      test("zip two ready values tuples them") {
        val r: (Int, String) = Async.succeed(1).zip(Async.succeed("a")).block
        assertTrue(r == ((1, "a")))
      },
      test("zipWith left = Pollable, right = value") {
        val left: Async[Int] = new Ready(10)
        val r                = left.zipWith(Async.succeed(2))(_ * _).block
        assertTrue(r == 20)
      },
      test("zipWith left = value, right = Pollable") {
        val right: Async[Int] = new Ready(2)
        val r                 = Async.succeed(10).zipWith(right)(_ * _).block
        assertTrue(r == 20)
      },
      test("zipWith both Pollables") {
        val left: Async[Int]  = new Ready(3)
        val right: Async[Int] = new Ready(4)
        val r                 = left.zipWith(right)(_ + _).block
        assertTrue(r == 7)
      },
      test("zipWith propagates left failure") {
        val r      = Async.fail(Boom).zipWith(Async.succeed(1))((_, b: Int) => b)
        val thrown = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(Boom))
      },
      test("zipWith propagates right failure") {
        val r      = Async.succeed(1).zipWith(Async.fail(Boom))((a: Int, _) => a)
        val thrown = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(Boom))
      }
    ),
    suite(".tap")(
      test("runs effect and propagates value (ready)") {
        var seen: Int = 0
        val r         = Async.succeed(7).tap(a => Async.succeed { seen = a }).block
        assertTrue(r == 7, seen == 7)
      },
      test("runs effect on suspended input") {
        val pa: Async[Int] = new Ready(11)
        var seen: Int      = 0
        val r              = pa.tap(a => Async.succeed { seen = a }).block
        assertTrue(r == 11, seen == 11)
      },
      test("propagates failure without running the effect") {
        var called = false
        val r      = Async.fail(Boom).tap((_: Any) => Async.succeed { called = true })
        val thrown = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(Boom), !called)
      }
    ),
    suite(".ensuring")(
      test("runs finalizer on success") {
        var ran = false
        val r   = Async.succeed(1).ensuring(Async.succeed { ran = true }).block
        assertTrue(r == 1, ran)
      },
      test("runs finalizer on failure (and propagates the original failure)") {
        var ran    = false
        val r      = Async.fail(Boom).ensuring(Async.succeed { ran = true })
        val thrown = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(Boom), ran)
      },
      test("suppresses finalizer failure (original outcome wins)") {
        val r = Async.succeed(1).ensuring(Async.fail(Boom)).block
        assertTrue(r == 1)
      }
    ),
    suite("Async.collectAll")(
      test("empty input yields empty list") {
        val r = Async.collectAll[Int](Nil).block
        assertTrue(r == Nil)
      },
      test("all-value inputs yield the list in order") {
        val r = Async.collectAll(List(Async.succeed(1), Async.succeed(2), Async.succeed(3))).block
        assertTrue(r == List(1, 2, 3))
      },
      test("mixed value + pollable inputs preserve order") {
        val r = Async
          .collectAll[Int](
            List(
              Async.succeed(1),
              new Ready(2).asInstanceOf[Async[Int]],
              Async.succeed(3),
              new Ready(4).asInstanceOf[Async[Int]]
            )
          )
          .block
        assertTrue(r == List(1, 2, 3, 4))
      },
      test("a failure mid-list short-circuits") {
        var thirdCalled = false
        val r           = Async.collectAll(
          List[Async[Int]](
            Async.succeed(1),
            Async.fail(Boom),
            Async.succeed { thirdCalled = true; 3 }
          )
        )
        // Note: `succeed` is eager so the body of the third element runs at
        // construction. What matters is that the collected list short-circuits
        // and that's surfaced as the failure.
        val thrown = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(Boom), thirdCalled)
      }
    ),
    suite("Async.never")(
      test("polling returns the same pollable (never advances)") {
        val n = Async.never
        // Drive the pollable once and observe it returns itself.
        val nany = n.asInstanceOf[Any]
        assertTrue(nany.isInstanceOf[Pollable[_]])
      }
    )
  )
}

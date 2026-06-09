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
 * `zipWith` is strictly sequential left-to-right: `fa` is driven to a value
 * first, and only then is `fb` driven. A failure in `fa` short-circuits without
 * driving `fb`; a failure in `fb` is surfaced only after `fa` has succeeded.
 *
 * This pins the fix for the former asymmetry where an already-constructed
 * [[Failure]] right operand short-circuited the whole zip eagerly — skipping a
 * still-pending left — while a pending-then-failing right correctly deferred
 * until the left resolved. Both shapes now behave identically (left first).
 */
object AsyncZipWithSequencingSpec extends ZIOSpecDefault {

  private val noWaker: Waker = new Waker { def wake(): Unit = () }

  private def pollOnce[A](fa: Async[A]): Async[A] =
    fa.asInstanceOf[Pollable[A]].poll(noWaker)

  private def isPending(fa: Async[Any]): Boolean = {
    val any: Any = fa
    any.isInstanceOf[Pollable[?]] && !any.isInstanceOf[Failure]
  }

  private def pending[A]: (Completer[A], Async[A]) = {
    val c = new Completer[A]
    (c, c.peek)
  }

  private val boom = new RuntimeException("boom")

  def spec = suite("AsyncZipWithSequencingSpec")(
    test("an already-failed right does not short-circuit a pending left (drives left first)") {
      val (c, fa) = pending[Int]
      val z       = fa.zipWith(Async.fail(boom))((a, _) => a)

      // Left is pending, so the zip is pending even though the right is failed.
      val r1 = pollOnce(z)
      // Resolve the left; only now may the right's failure surface.
      c.succeed(1)
      val r2: Any = pollOnce(z)

      assertTrue(
        isPending(z),
        isPending(r1),
        r2.isInstanceOf[Failure],
        r2.asInstanceOf[Failure].cause eq boom
      )
    },
    test("a pending-then-failing right also defers until the left resolves") {
      val (cl, fa) = pending[Int]
      val (cr, fb) = pending[Int]
      val z        = fa.zipWith(fb)((a, _) => a)

      val r1 = pollOnce(z)
      cr.fail(boom)            // right fails while left still pending
      val r2 = pollOnce(z)     // still pending: failure deferred
      cl.succeed(1)            // left resolves
      val r3: Any = pollOnce(z) // now the right's failure surfaces

      assertTrue(
        isPending(r1),
        isPending(r2),
        r3.isInstanceOf[Failure],
        r3.asInstanceOf[Failure].cause eq boom
      )
    },
    test("a ready left with an already-failed right fails immediately (fast path preserved)") {
      val z: Any = Async.succeed(1).zipWith(Async.fail(boom))((a, _) => a)
      assertTrue(z.isInstanceOf[Failure], z.asInstanceOf[Failure].cause eq boom)
    },
    test("a failed left short-circuits without driving the right") {
      var rightDriven = false
      val right: Async[Int] = new Pollable[Int] {
        def poll(waker: Waker): Async[Int] = { rightDriven = true; Async.succeed(2) }
      }
      val z: Any = Async.fail(boom).zipWith(right)((a, _) => a)
      assertTrue(z.isInstanceOf[Failure], z.asInstanceOf[Failure].cause eq boom, !rightDriven)
    },
    test("collectAll preserves order across interleaved pending/ready inputs settled out of order") {
      val (c1, p1) = pending[Int]
      val (c2, p2) = pending[Int]
      val all      = Async.collectAll(List[Async[Int]](Async.succeed(1), p1, Async.succeed(3), p2, Async.succeed(5)))
      c2.succeed(4)
      c1.succeed(2)
      assertTrue(all.block == List(1, 2, 3, 4, 5))
    }
  )
}

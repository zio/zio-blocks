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
import zio.test.Assertion._

import scala.util.Try

/**
 * Combinators: zip, zipWith, tap, ensuring, collectAll, when/unless.
 */
object AsyncCombinatorsSpec extends ZIOSpecDefault {

  private val boom = AsyncTestSupport.boom
  private val Boom = AsyncTestSupport.boom

  def spec = suite("AsyncCombinatorsSpec")(
    suite("zipWith")(
      test("zipWith two ready values combines them eagerly") {
        val r = Async.succeed(3).zipWith(Async.succeed(4))(_ + _).block
        assertTrue(r == 7)
      },
      test("zip two ready values tuples them") {
        val r: (Int, String) = Async.succeed(1).zip(Async.succeed("a")).block
        assertTrue(r == ((1, "a")))
      },
      test("zipWith left = Pollable, right = value") {
        val left: Async[Int] = AsyncTestSupport.syncReadyPollable(10)
        val r                = left.zipWith(Async.succeed(2))(_ * _).block
        assertTrue(r == 20)
      },
      test("zipWith left = value, right = Pollable") {
        val right: Async[Int] = AsyncTestSupport.syncReadyPollable(2)
        val r                 = Async.succeed(10).zipWith(right)(_ * _).block
        assertTrue(r == 20)
      },
      test("zipWith both Pollables") {
        val left: Async[Int]  = AsyncTestSupport.syncReadyPollable(3)
        val right: Async[Int] = AsyncTestSupport.syncReadyPollable(4)
        val r                 = left.zipWith(right)(_ + _).block
        assertTrue(r == 7)
      },
      test("zipWith propagates left failure") {
        val r      = Async.fail(AsyncTestSupport.boom).zipWith(Async.succeed(1))((_, b: Int) => b)
        val thrown = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.boom))
      },
      test("zipWith propagates right failure") {
        val r      = Async.succeed(1).zipWith(Async.fail(AsyncTestSupport.boom))((a: Int, _) => a)
        val thrown = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.boom))
      },
      test("an already-failed right does not short-circuit a pending left (drives left first)") {
        val (c, fa) = AsyncTestSupport.pending[Int]
        val z       = fa.zipWith(Async.fail(boom))((a, _) => a)

        // Left is pending, so the zip is pending even though the right is failed.
        val r1 = AsyncTestSupport.pollOnce(z)
        // Resolve the left; only now may the right's failure surface.
        c.succeed(1)
        val thrown = scala.util.Try(z.block).failed.toOption

        assertTrue(
          AsyncTestSupport.isPending(z),
          AsyncTestSupport.isPending(r1),
          thrown.contains(boom)
        )
      },
      test("a pending-then-failing right also defers until the left resolves") {
        val (cl, fa) = AsyncTestSupport.pending[Int]
        val (cr, fb) = AsyncTestSupport.pending[Int]
        val z        = fa.zipWith(fb)((a, _) => a)

        val r1 = AsyncTestSupport.pollOnce(z)
        cr.fail(boom) // right fails while left still pending
        val r2     = AsyncTestSupport.pollOnce(z) // still pending: failure deferred
        cl.succeed(1) // left resolves
        val thrown = scala.util.Try(z.block).failed.toOption

        assertTrue(
          AsyncTestSupport.isPending(r1),
          AsyncTestSupport.isPending(r2),
          thrown.contains(boom)
        )
      },
      test("a ready left with an already-failed right fails immediately (fast path preserved)") {
        val z      = Async.succeed(1).zipWith(Async.fail(boom))((a, _) => a)
        val thrown = scala.util.Try(z.block).failed.toOption
        assertTrue(thrown.contains(boom))
      },
      test("a failed left short-circuits without driving the right") {
        var rightDriven       = false
        val right: Async[Int] = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = { rightDriven = true; Async.succeed(2) }
        }
        val z      = Async.fail(boom).zipWith(right)((a, _) => a)
        val thrown = scala.util.Try(z.block).failed.toOption
        assertTrue(thrown.contains(boom), !rightDriven)
      },
      test("collectAll preserves order across interleaved pending/ready inputs settled out of order") {
        val (c1, p1) = AsyncTestSupport.pending[Int]
        val (c2, p2) = AsyncTestSupport.pending[Int]
        val all      = Async.collectAll(List[Async[Int]](Async.succeed(1), p1, Async.succeed(3), p2, Async.succeed(5)))
        c2.succeed(4)
        c1.succeed(2)
        assertTrue(all.block == List(1, 2, 3, 4, 5))
      },
      test("zipWith_failedLeft_doesNotInvokeCombine") {
        var invoked = false
        val (_, fb) = {
          val c = new Completer[Int]
          (c, c.peek)
        }
        val z      = Async.fail(boom).zipWith(fb) { (_, _) => invoked = true; 0 }
        val thrown = scala.util.Try(z.block).failed.toOption
        assertTrue(thrown.contains(boom), !invoked)
      },
      test("zipWith_pendingLeft_readyRightFail_doesNotSurfaceRightUntilLeftCompletes") {
        var rightPolled = false
        val (c, left)   = {
          val c = new Completer[Int]
          (c, c.peek)
        }
        val right: Async[Int] = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = { rightPolled = true; Async.fail(AsyncTestSupport.rightBoom) }
        }
        val z = left.zipWith(right)((_, _) => 0)
        c.fail(AsyncTestSupport.leftBoom)
        val thrown = Try(z.block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.leftBoom), !rightPolled)
      },
      test("zipWith left advancing through a replacement pollable to a pollable-as-value carrier combines once") {
        // Poll protocol: a pending poll may return a REPLACEMENT pollable. A
        // left that hands back a replacement and then settles to a carrier
        // (Async.succeed of a user pollable) must mark the left resolved with
        // its raw terminal encoding — the combine sees the bare user pollable
        // (one unwrap), and the left leaf is never re-polled afterwards.
        val inner: Pollable[Int]       = AsyncTestSupport.syncReadyPollable(99)
        var leafPolls                  = 0
        val left: Async[Pollable[Int]] = new Pollable[Pollable[Int]] {
          def poll(onComplete: Runnable): Async[Pollable[Int]] = {
            leafPolls += 1
            onComplete.run()
            new Pollable[Pollable[Int]] {
              def poll(onComplete: Runnable): Async[Pollable[Int]] = Async.succeed(inner)
            }
          }
        }
        val (cr, right)  = AsyncTestSupport.pending[Int]
        var seen: AnyRef = null
        var combines     = 0
        val z            = left.zipWith(right) { (p, b) => seen = p; combines += 1; b }
        val r1           = AsyncTestSupport.pollOnce(z) // left advances to its replacement
        cr.succeed(7)
        val r = z.block
        assertTrue(
          AsyncTestSupport.isPending(r1),
          r == 7,
          seen eq inner,
          combines == 1,
          leafPolls == 1
        )
      },
      test("zipWith_pendingLeft_readyRightFail_doesNotInvokeCombineOnLeftFail") {
        var invoked   = false
        val (c, left) = {
          val c = new Completer[Int]
          (c, c.peek)
        }
        val z = left.zipWith(Async.fail(AsyncTestSupport.rightBoom)) { (_, _) => invoked = true; 0 }
        c.fail(AsyncTestSupport.leftBoom)
        val thrown = Try(z.block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.leftBoom), !invoked)
      }
    ),
    suite("zip")(
      test("a zip b yields a Tuple2") {
        val r: (Int, String) = Async.succeed(1).zip(Async.succeed("a")).block
        assertTrue(r == ((1, "a")))
      },
      test("a zip b zip c flattens to a Tuple3") {
        val r: (Int, String, Boolean) =
          Async.succeed(1).zip(Async.succeed("a")).zip(Async.succeed(true)).block
        assertTrue(r == ((1, "a", true)))
      },
      test("a zip b zip c zip d flattens to a Tuple4") {
        val r: (Int, String, Boolean, Double) =
          Async
            .succeed(1)
            .zip(Async.succeed("a"))
            .zip(Async.succeed(true))
            .zip(Async.succeed(1.5))
            .block
        assertTrue(r == ((1, "a", true, 1.5)))
      },
      test("Unit on the right is erased") {
        val r: Int = Async.succeed(1).zip(Async.succeed(())).block
        assertTrue(r == 1)
      },
      test("Unit on the left is erased") {
        val r: Int = Async.succeed(()).zip(Async.succeed(1)).block
        assertTrue(r == 1)
      }
    ),
    suite("tap")(
      test("runs effect and propagates value (ready)") {
        var seen: Int = 0
        val r         = Async.succeed(7).tap(a => Async.succeed { seen = a }).block
        assertTrue(r == 7, seen == 7)
      },
      test("runs effect on suspended input") {
        val pa: Async[Int] = AsyncTestSupport.syncReadyPollable(11)
        var seen: Int      = 0
        val r              = pa.tap(a => Async.succeed { seen = a }).block
        assertTrue(r == 11, seen == 11)
      },
      test("propagates failure without running the effect") {
        var called = false
        val r      = Async.fail(AsyncTestSupport.boom).tap((_: Any) => Async.succeed { called = true })
        val thrown = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.boom), !called)
      },
      test("tap_readyFail_doesNotInvokeEffect") {
        var invoked = false
        val a       = Async.fail(boom).tap((_: Nothing) => Async.attempt { invoked = true; () })
        val thrown  = Try(a.block).failed.toOption
        assertTrue(thrown.contains(boom), !invoked)
      },
      test("tap_pendingFail_doesNotInvokeEffect") {
        var invoked      = false
        val (c, pending) = {
          val c = new Completer[Int]
          (c, c.peek)
        }
        val a = pending.tap(_ => Async.attempt { invoked = true; () })
        c.fail(boom)
        val thrown = Try(a.block).failed.toOption
        assertTrue(thrown.contains(boom), !invoked)
      },
      test("tap_readyValue_finalizerPollThrow_propagatesThrow") {
        val thrown = Try(Async.succeed(1).tap(_ => AsyncTestSupport.throwingTapEffect).block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.finPoll))
      },
      test("tap_pendingPrimary_finalizerPollThrow_propagatesThrow") {
        val c = new Completer[Int]
        val a = c.peek.tap(_ => AsyncTestSupport.throwingTapEffect)
        c.succeed(1)
        val thrown = Try(a.block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.finPoll))
      }
    ),
    suite("ensuring")(
      test("runs finalizer on success") {
        var ran = false
        val r   = Async.succeed(1).ensuring(Async.succeed { ran = true }).block
        assertTrue(r == 1, ran)
      },
      test("runs finalizer on failure (and propagates the original failure)") {
        var ran    = false
        val r      = Async.fail(AsyncTestSupport.boom).ensuring(Async.succeed { ran = true })
        val thrown = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.boom), ran)
      },
      test("suppresses finalizer failure (original outcome wins)") {
        val r = Async.succeed(1).ensuring(Async.fail(AsyncTestSupport.boom)).block
        assertTrue(r == 1)
      }
    ),
    suite("collectAll")(
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
              AsyncTestSupport.syncReadyPollable(2),
              Async.succeed(3),
              AsyncTestSupport.syncReadyPollable(4)
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
            Async.fail(AsyncTestSupport.boom),
            Async.succeed { thirdCalled = true; 3 }
          )
        )
        // Note: `succeed` is eager so the body of the third element runs at
        // construction. What matters is that the collected list short-circuits
        // and that's surfaced as the failure.
        val thrown = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.boom), thirdCalled)
      },
      test("collectAll does not drive a Pollable input that follows a failure") {
        val boom             = AsyncTestSupport.boom
        var polledAfter      = false
        val tail: Async[Int] = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = { polledAfter = true; Async.succeed(0) }
        }
        val r      = Async.collectAll(List[Async[Int]](Async.succeed(1), Async.fail(boom), tail))
        val thrown = Try(r.block).failed.toOption
        assertTrue(thrown.contains(boom), !polledAfter)
      },
      test("collectAll over many settled promise-backed elements is stack-safe") {
        // A batch of promise-backed elements that have all completed by the time
        // the result is driven (e.g. a fan-in of finished callbacks) must drain
        // iteratively: one stack frame chain per element overflows on real-world
        // batch sizes.
        val n      = 20000
        val cs     = List.fill(n)(new Completer[Int])
        val asyncs = cs.map(_.peek) // captured while pending, so each is a genuine Pollable
        cs.zipWithIndex.foreach { case (c, i) => c.succeed(i) }
        val r = Async.collectAll(asyncs).block
        assertTrue(r.length == n, r.take(3) == List(0, 1, 2))
      },
      test("collectAll should accept a single-pass Iterator source (only .iterator is used)") {
        typeCheck("""
              import zio.blocks.async._
              val it: Iterator[Async[Int]] = Iterator(Async.succeed(1), Async.succeed(2))
              val r: Async[List[Int]] = Async.collectAll(it)
              r
            """).map(r => assert(r)(isRight))
      },
      test("CONVERGENCE: collectAll accepts an Iterable source (LazyList)") {
        typeCheck("""
              import zio.blocks.async._
              val ll: LazyList[Async[Int]] = LazyList(Async.succeed(1))
              val r: Async[List[Int]] = Async.collectAll(ll)
              r
            """).map(r => assert(r)(isRight))
      },
      test("collectAll drives elements that advance through replacement pollables, preserving order") {
        def chain(n: Int, value: Int): Pollable[Int] = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] =
            if (n <= 0) Async.succeed(value)
            else {
              onComplete.run()
              chain(n - 1, value) // a replacement, never `this`
            }
        }
        val r = Async.collectAll(List[Async[Int]](chain(3, 1), Async.succeed(2), chain(2, 3))).block
        assertTrue(r == List(1, 2, 3))
      },
      test("collectAll delivers a pollable-as-value element as the bare user pollable (one unwrap)") {
        val inner: Pollable[Int] = AsyncTestSupport.syncReadyPollable(99)
        val (c, p)               = AsyncTestSupport.pending[Pollable[Int]]
        val all                  = Async.collectAll(List[Async[Pollable[Int]]](p))
        c.succeed(inner)
        val r = all.block
        assertTrue(r.length == 1, r.head.asInstanceOf[AnyRef] eq inner)
      },
      test("collectAll propagates a failure surfaced by a replacement pollable without driving the tail") {
        var tailPolled       = false
        val tail: Async[Int] = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = { tailPolled = true; Async.succeed(9) }
        }
        val failingChain: Pollable[Int] = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = {
            onComplete.run()
            new Pollable[Int] {
              def poll(onComplete: Runnable): Async[Int] = Async.fail(boom)
            }
          }
        }
        val all    = Async.collectAll(List[Async[Int]](failingChain, tail))
        val thrown = Try(all.block).failed.toOption
        assertTrue(thrown.contains(boom), !tailPolled)
      },
      test("collectAll_pendingThenFail_doesNotDriveTail") {
        var tailPolled       = false
        val tail: Async[Int] = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = { tailPolled = true; Async.succeed(0) }
        }
        val (c, pending) = {
          val c = new Completer[Int]
          (c, c.peek)
        }
        val r = Async.collectAll(List[Async[Int]](Async.succeed(1), pending, Async.fail(boom), tail))
        c.fail(boom)
        val thrown = Try(r.block).failed.toOption
        assertTrue(thrown.contains(boom), !tailPolled)
      },
      test("collectAll_firstPendingFails_doesNotDriveRest") {
        var secondPolled = false
        val (c, pending) = {
          val c = new Completer[Int]
          (c, c.peek)
        }
        val second: Async[Int] = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = { secondPolled = true; Async.succeed(2) }
        }
        val r = Async.collectAll(List[Async[Int]](pending, second))
        c.fail(boom)
        val thrown = Try(r.block).failed.toOption
        assertTrue(thrown.contains(boom), !secondPolled)
      },
      test("collectAll_readyFailFirst_doesNotDriveTail") {
        var tailPolled       = false
        val tail: Async[Int] = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = { tailPolled = true; Async.succeed(0) }
        }
        val r      = Async.collectAll(List[Async[Int]](Async.fail(boom), Async.succeed(1), tail))
        val thrown = Try(r.block).failed.toOption
        assertTrue(thrown.contains(boom), !tailPolled)
      }
    ),
    suite("when / unless")(
      test("when(true) runs the effect") {
        var ran = false
        when(true)(Async.succeed { ran = true }).block
        assertTrue(ran)
      },
      test("when(false) does not run the effect") {
        var ran = false
        when(false)(Async.succeed { ran = true }).block
        assertTrue(!ran)
      },
      test("unless(true) does not run the effect") {
        var ran = false
        unless(true)(Async.succeed { ran = true }).block
        assertTrue(!ran)
      },
      test("unless(false) runs the effect") {
        var ran = false
        unless(false)(Async.succeed { ran = true }).block
        assertTrue(ran)
      }
    ),
    suite("as / unit / *> / <*")(
      test("replaces the value (ready)") {
        val r = Async.succeed(1).as("x").block
        assertTrue(r == "x")
      },
      test("replaces the value (suspended)") {
        val r = (AsyncTestSupport.syncReadyPollable(1): Async[Int]).as("x").block
        assertTrue(r == "x")
      },
      test("propagates failure") {
        val r      = Async.fail(new RuntimeException("boom")).as("x")
        val thrown = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.exists(_.getMessage == "boom"))
      },
      test("discards the value (ready)") {
        val r = Async.succeed("anything").unit.block
        assertTrue(r == (()))
      },
      test("discards the value (suspended)") {
        val r = (AsyncTestSupport.syncReadyPollable(42): Async[Int]).unit.block
        assertTrue(r == (()))
      },
      test("returns rhs (both ready)") {
        val r = (Async.succeed(1) *> Async.succeed(2)).block
        assertTrue(r == 2)
      },
      test("returns rhs (lhs suspended)") {
        val l: Async[Int] = AsyncTestSupport.syncReadyPollable(1)
        val r             = (l *> Async.succeed(2)).block
        assertTrue(r == 2)
      },
      test("returns rhs (rhs suspended)") {
        val rh: Async[Int] = AsyncTestSupport.syncReadyPollable(2)
        val r              = (Async.succeed(1) *> rh).block
        assertTrue(r == 2)
      },
      test("propagates lhs failure") {
        val boom            = AsyncTestSupport.boom
        val lhs: Async[Int] = Async.fail(boom)
        val r: Async[Int]   = lhs *> Async.succeed(2)
        val thrown          = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(boom))
      },
      test("propagates rhs failure") {
        val boom            = AsyncTestSupport.boom
        val rhs: Async[Int] = Async.fail(boom)
        val r: Async[Int]   = Async.succeed(1) *> rhs
        val thrown          = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(boom))
      },
      test("returns lhs (both ready)") {
        val r = (Async.succeed(1) <* Async.succeed(2)).block
        assertTrue(r == 1)
      },
      test("returns lhs (lhs suspended)") {
        val l: Async[Int] = AsyncTestSupport.syncReadyPollable(1)
        val r             = (l <* Async.succeed(2)).block
        assertTrue(r == 1)
      },
      test("returns lhs (rhs suspended)") {
        val rh: Async[Int] = AsyncTestSupport.syncReadyPollable(2)
        val r              = (Async.succeed(1) <* rh).block
        assertTrue(r == 1)
      },
      test("propagates lhs failure") {
        val boom            = AsyncTestSupport.boom
        val lhs: Async[Int] = Async.fail(boom)
        val r: Async[Int]   = lhs <* Async.succeed(2)
        val thrown          = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(boom))
      },
      test("propagates rhs failure") {
        val boom            = AsyncTestSupport.boom
        val rhs: Async[Int] = Async.fail(boom)
        val r: Async[Int]   = Async.succeed(1) <* rhs
        val thrown          = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(boom))
      }
    )
  )
}

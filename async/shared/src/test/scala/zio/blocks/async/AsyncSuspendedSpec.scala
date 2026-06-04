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
 * Cross-platform coverage of the '''suspended''' slow path in
 * [[AsyncSlowPath]]. The inline fast paths only fire for ready values; the
 * `Pollable` continuation classes (`FlatMapPollable`, `CatchAllPollable`,
 * `ZipWithPollable`, `RunThenValuePollable`, `EnsuringPollable`) only run when
 * an input is genuinely pending.
 *
 * Rather than rely on thread timing (impossible on JS, flaky on the JVM), these
 * tests drive the continuation `Pollable`s '''deterministically''': a
 * [[Completer]] starts empty (so its `peek` is the pending pollable `this`),
 * the combinator is applied to produce the slow-path pollable, it is polled
 * once to register the waker (exercising the "still pending" branch), the
 * completer is then settled, and the pollable is re-polled to drive it to its
 * value or failure (exercising the resume branch). The [[Waker]] is a no-op
 * because the driver re-polls manually — this reproduces the exact sequence the
 * runtime performs (`poll` → `wake()` → re-`poll`) without any concurrency.
 *
 * This runs on both JVM and JS, so it also covers the JS resume machinery that
 * the Future-driven specs reach only indirectly.
 */
object AsyncSuspendedSpec extends ZIOSpecDefault {

  private val noWaker: Waker = new Waker { def wake(): Unit = () }

  private def isPending(fa: Async[Any]): Boolean = {
    val any: Any = fa
    any.isInstanceOf[Pollable[?]] && !any.isInstanceOf[Failure]
  }

  /** Poll once (does not assume readiness). */
  private def pollOnce[A](fa: Async[A]): Async[A] =
    fa.asInstanceOf[Pollable[A]].poll(noWaker)

  /** Drive a (possibly pending) async to a settled value-or-failure. */
  private def driveToEnd[A](start: Async[A], maxPolls: Int = 32): Async[A] = {
    var cur: Any = start
    var i        = 0
    while (cur.isInstanceOf[Pollable[?]] && !cur.isInstanceOf[Failure] && i < maxPolls) {
      cur = cur.asInstanceOf[Pollable[A]].poll(noWaker)
      i += 1
    }
    cur.asInstanceOf[Async[A]]
  }

  /** Interpret a settled async as an `Either`. */
  private def outcome[A](fa: Async[A]): Either[Throwable, A] = {
    val any: Any = fa
    if (any.isInstanceOf[Failure]) Left(any.asInstanceOf[Failure].cause)
    else Right(any.asInstanceOf[A])
  }

  private def pending[A]: (Completer[A], Async[A]) = {
    val c = new Completer[A]
    (c, c.peek)
  }

  private val boom = new RuntimeException("boom")

  def spec = suite("AsyncSuspendedSpec")(
    suite("map (FlatMapPollable)")(
      test("pending input then success") {
        val (c, fa) = pending[Int]
        val m       = fa.map(_ + 1)
        val r1      = pollOnce(m)
        c.succeed(5)
        assertTrue(isPending(m), outcome(driveToEnd(r1)) == Right(6))
      },
      test("pending input that fails propagates the cause") {
        val (c, fa) = pending[Int]
        val m       = fa.map(_ + 1)
        val r1      = pollOnce(m)
        c.fail(boom)
        assertTrue(outcome(driveToEnd(r1)) == Left(boom))
      }
    ),
    suite("flatMap (FlatMapPollable)")(
      test("pending input, f returns a ready value") {
        val (c, fa) = pending[Int]
        val fm      = fa.flatMap(v => Async.succeed(v * 2))
        val r1      = pollOnce(fm)
        c.succeed(10)
        assertTrue(outcome(driveToEnd(r1)) == Right(20))
      },
      test("pending input, f returns another pending async (stage latch)") {
        val (c1, fa) = pending[Int]
        val (c2, fb) = pending[Int]
        val fm       = fa.flatMap(_ => fb)
        val r1       = pollOnce(fm)
        c1.succeed(1)
        val r2 = pollOnce(r1) // pa done; f(a) returns fb (pending) -> stage latched
        c2.succeed(99)
        assertTrue(outcome(driveToEnd(r2)) == Right(99))
      },
      test("pending input where f itself fails") {
        val (c, fa) = pending[Int]
        val fm      = fa.flatMap(_ => Async.fail(boom))
        val r1      = pollOnce(fm)
        c.succeed(1)
        assertTrue(outcome(driveToEnd(r1)) == Left(boom))
      },
      test("pending input that fails short-circuits f") {
        val (c, fa) = pending[Int]
        var ran     = false
        val fm = fa.flatMap { v =>
          ran = true; Async.succeed(v)
        }
        val r1 = pollOnce(fm)
        c.fail(boom)
        assertTrue(outcome(driveToEnd(r1)) == Left(boom), !ran)
      }
    ),
    suite("catchAll (CatchAllPollable)")(
      test("pending input that fails, handler returns a value") {
        val (c, fa) = pending[Int]
        val ca      = fa.catchAll(_ => Async.succeed(-1))
        val r1      = pollOnce(ca)
        c.fail(boom)
        assertTrue(outcome(driveToEnd(r1)) == Right(-1))
      },
      test("pending input that fails, handler returns another pending async") {
        val (c1, fa) = pending[Int]
        val (c2, fb) = pending[Int]
        val ca       = fa.catchAll(_ => fb)
        val r1       = pollOnce(ca)
        c1.fail(boom)
        val r2 = pollOnce(r1) // handler fired, returns fb pending -> stage latched
        c2.succeed(7)
        assertTrue(outcome(driveToEnd(r2)) == Right(7))
      },
      test("pending input that fails, handler returns another failure") {
        val (c, fa)   = pending[Int]
        val boom2     = new RuntimeException("boom2")
        val ca        = fa.catchAll(_ => Async.fail(boom2))
        val r1        = pollOnce(ca)
        c.fail(boom)
        assertTrue(outcome(driveToEnd(r1)) == Left(boom2))
      },
      test("pending input that succeeds passes through unchanged") {
        val (c, fa) = pending[Int]
        val ca      = fa.catchAll(_ => Async.succeed(-1))
        val r1      = pollOnce(ca)
        c.succeed(42)
        assertTrue(outcome(driveToEnd(r1)) == Right(42))
      }
    ),
    suite("zipWith (ZipWithPollable)")(
      test("both inputs pending then succeed") {
        val (c1, fa) = pending[Int]
        val (c2, fb) = pending[Int]
        val z        = fa.zipWith(fb)(_ + _)
        val r1       = pollOnce(z)
        c1.succeed(3)
        c2.succeed(4)
        assertTrue(isPending(z), outcome(driveToEnd(r1)) == Right(7))
      },
      test("left input fails while pending") {
        val (c1, fa) = pending[Int]
        val (_, fb)  = pending[Int]
        val z        = fa.zipWith(fb)(_ + _)
        val r1       = pollOnce(z)
        c1.fail(boom)
        assertTrue(outcome(driveToEnd(r1)) == Left(boom))
      },
      test("right input fails after left succeeds") {
        val (c1, fa) = pending[Int]
        val (c2, fb) = pending[Int]
        val z        = fa.zipWith(fb)(_ + _)
        val r1       = pollOnce(z)
        c1.succeed(1)
        c2.fail(boom)
        assertTrue(outcome(driveToEnd(r1)) == Left(boom))
      },
      test("left already failed is short-circuited") {
        val (_, fb) = pending[Int]
        val z       = Async.fail(boom).zipWith(fb)((_, b) => b)
        assertTrue(outcome(driveToEnd(z)) == Left(boom))
      },
      test("right already failed is short-circuited after left ready") {
        val z = Async.succeed(1).zipWith(Async.fail(boom))((a, _) => a)
        assertTrue(outcome(driveToEnd(z)) == Left(boom))
      }
    ),
    suite("tap (RunThenValuePollable)")(
      test("pending input, tap effect runs then yields the original value") {
        val (c, fa) = pending[Int]
        var seen    = 0
        val t       = fa.tap { v => seen = v; Async.succeed(()) }
        val r1      = pollOnce(t)
        c.succeed(11)
        assertTrue(outcome(driveToEnd(r1)) == Right(11), seen == 11)
      },
      test("pending input, tap effect itself is pending then completes") {
        val (c1, fa) = pending[Int]
        val (c2, eff) = pending[Unit]
        val t         = fa.tap(_ => eff)
        val r1        = pollOnce(t)
        c1.succeed(8)
        val r2 = pollOnce(r1)
        c2.succeed(())
        assertTrue(outcome(driveToEnd(r2)) == Right(8))
      },
      test("pending input, a failing tap effect propagates the failure") {
        val (c, fa) = pending[Int]
        val t       = fa.tap(_ => Async.fail(boom))
        val r1      = pollOnce(t)
        c.succeed(8)
        assertTrue(outcome(driveToEnd(r1)) == Left(boom))
      }
    ),
    suite("ensuring (EnsuringPollable)")(
      test("pending input succeeds, finalizer runs, value propagates") {
        val (c, fa) = pending[Int]
        var ran     = false
        val e       = fa.ensuring(Async.succeed { ran = true })
        val r1      = pollOnce(e)
        c.succeed(5)
        assertTrue(outcome(driveToEnd(r1)) == Right(5), ran)
      },
      test("pending input fails, finalizer still runs, failure propagates") {
        val (c, fa) = pending[Int]
        var ran     = false
        val e       = fa.ensuring(Async.succeed { ran = true })
        val r1      = pollOnce(e)
        c.fail(boom)
        assertTrue(outcome(driveToEnd(r1)) == Left(boom), ran)
      },
      test("pending input, finalizer is itself pending then completes") {
        val (c1, fa)  = pending[Int]
        val (c2, fin) = pending[Unit]
        val e         = fa.ensuring(fin)
        val r1        = pollOnce(e)
        c1.succeed(9)
        val r2 = pollOnce(r1)
        c2.succeed(())
        assertTrue(outcome(driveToEnd(r2)) == Right(9))
      },
      test("a failing finalizer is suppressed; original outcome wins") {
        val (c, fa) = pending[Int]
        val e       = fa.ensuring(Async.fail(boom))
        val r1      = pollOnce(e)
        c.succeed(3)
        assertTrue(outcome(driveToEnd(r1)) == Right(3))
      }
    ),
    suite("derived combinators over pending inputs")(
      test("mapError transforms a pending failure") {
        val (c, fa) = pending[Int]
        val wrapped = new RuntimeException("wrapped")
        val me      = fa.mapError(_ => wrapped)
        val r1      = pollOnce(me)
        c.fail(boom)
        assertTrue(outcome(driveToEnd(r1)) == Left(wrapped))
      },
      test("orElse recovers a pending failure") {
        val (c, fa) = pending[Int]
        val oe      = fa.orElse(Async.succeed(123))
        val r1      = pollOnce(oe)
        c.fail(boom)
        assertTrue(outcome(driveToEnd(r1)) == Right(123))
      },
      test("either reifies a pending success") {
        val (c, fa) = pending[Int]
        val ei      = fa.either
        val r1      = pollOnce(ei)
        c.succeed(5)
        assertTrue(outcome(driveToEnd(r1)) == Right(Right(5)))
      },
      test("either reifies a pending failure") {
        val (c, fa) = pending[Int]
        val ei      = fa.either
        val r1      = pollOnce(ei)
        c.fail(boom)
        assertTrue(outcome(driveToEnd(r1)) == Right(Left(boom)))
      },
      test("foldCause folds a pending failure") {
        val (c, fa) = pending[Int]
        val fc      = fa.foldCause(_ => -7)(v => v)
        val r1      = pollOnce(fc)
        c.fail(boom)
        assertTrue(outcome(driveToEnd(r1)) == Right(-7))
      }
    ),
    suite("ready Failure routed through the slow path")(
      test("catchAll on a ready Failure invokes the handler") {
        // Async.fail is a Failure, which IS a Pollable, so .catchAll takes the
        // slow path (catchAllAsync's Failure branch) even though it is ready.
        val ca = Async.fail(boom).catchAll(_ => Async.succeed(99))
        assertTrue(outcome(driveToEnd(ca)) == Right(99))
      }
    ),
    suite("latched stage re-poll (nested continuations)")(
      test("nested flatMap re-polls a latched FlatMapPollable.stage") {
        // Wrapping the inner flatMap in an outer one forces the driver to
        // re-enter the inner pollable after its `stage` has been latched,
        // exercising the `if (stage != null) stage.poll(w)` fast path.
        val (c1, fa) = pending[Int]
        val (c2, fb) = pending[Int]
        val inner    = fa.flatMap(_ => fb)
        val outer    = inner.flatMap(v => Async.succeed(v + 1))
        var cur      = pollOnce(outer) // pending
        c1.succeed(1)
        cur = pollOnce(cur)            // inner latches stage = c2; still pending
        c2.succeed(10)
        assertTrue(outcome(driveToEnd(cur)) == Right(11))
      },
      test("nested catchAll re-polls a latched CatchAllPollable.stage") {
        val (c1, fa) = pending[Int]
        val (c2, fb) = pending[Int]
        val inner    = fa.catchAll(_ => fb)
        val outer    = inner.flatMap(v => Async.succeed(v + 1))
        var cur      = pollOnce(outer)
        c1.fail(boom)
        cur = pollOnce(cur)            // handler fired, stage = c2; still pending
        c2.succeed(10)
        assertTrue(outcome(driveToEnd(cur)) == Right(11))
      }
    ),
    suite("zipWith: second side pending across an extra round")(
      test("fa resolves first, fb stays pending one more poll") {
        val (c1, fa) = pending[Int]
        val (c2, fb) = pending[Int]
        val z        = fa.zipWith(fb)(_ + _)
        var cur      = pollOnce(z) // both pending
        c1.succeed(3)
        cur = pollOnce(cur)        // fa done; fb still pending -> returns this
        c2.succeed(4)
        assertTrue(outcome(driveToEnd(cur)) == Right(7))
      }
    ),
    suite("RunThenValuePollable failure resolution")(
      test("tap effect pending then fails propagates the failure") {
        val (c1, fa)  = pending[Int]
        val (c2, eff) = pending[Unit]
        val t         = fa.tap(_ => eff)
        var cur       = pollOnce(t)
        c1.succeed(8)
        cur = pollOnce(cur) // tap effect latched, still pending
        c2.fail(boom)
        assertTrue(outcome(driveToEnd(cur)) == Left(boom))
      },
      test("ensuring finalizer pending then fails is suppressed") {
        val (c1, fa)  = pending[Int]
        val (c2, fin) = pending[Unit]
        val e         = fa.ensuring(fin)
        var cur       = pollOnce(e)
        c1.succeed(5)
        cur = pollOnce(cur) // finalizer latched, still pending
        c2.fail(boom)
        assertTrue(outcome(driveToEnd(cur)) == Right(5))
      }
    ),
    suite("Completer state machine")(
      test("re-poll while waiting replaces the registered waker") {
        val c = new Completer[Int]
        val r1: Async[Int] = c.poll(noWaker) // null -> WaitingMarker
        val r2: Async[Int] = c.poll(noWaker) // WaitingMarker -> replace
        assertTrue(isPending(r1), isPending(r2)) && {
          c.succeed(7)
          assertTrue(outcome(c.poll(noWaker)) == Right(7)) // settled -> value
        }
      },
      test("peek does not register a waker and reflects later completion") {
        val c = new Completer[Int]
        val p = c.peek // empty -> this (pending)
        assertTrue(isPending(p)) && {
          c.succeed(3)
          assertTrue(outcome(c.peek) == Right(3))
        }
      },
      test("fail then poll yields the failure") {
        val c = new Completer[Int]
        c.fail(boom)
        assertTrue(outcome(c.poll(noWaker)) == Left(boom))
      },
      test("second completion is a silent no-op (first writer wins)") {
        val c = new Completer[Int]
        c.succeed(1)
        c.succeed(2)
        c.fail(boom)
        assertTrue(outcome(c.poll(noWaker)) == Right(1))
      }
    )
  )
}

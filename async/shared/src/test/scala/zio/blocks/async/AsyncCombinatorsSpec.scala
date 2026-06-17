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

  /**
   * A pending leaf that advances by returning a brand-new pollable each poll.
   */
  private def replacementChain(value: Int, steps: Int): Pollable[Int] = new Pollable[Int] {
    def poll(onComplete: Runnable): Async[Int] =
      if (steps <= 0) Async.succeed(value)
      else {
        onComplete.run()
        replacementChain(value, steps - 1)
      }
  }

  /**
   * The five runtime encodings a combinator operand can arrive in: a ready
   * value, a ready succeed-carrier holding a user [[Pollable]] as data, a ready
   * [[Failure]], a pending pollable that re-arms itself, and a pending pollable
   * that advances through replacement pollables. Paired with the outcome
   * `.block` must deliver for the operand alone (the bare user pollable for the
   * carrier, by identity).
   */
  private val encodingShapes: List[String] = List("value", "carrier", "failure", "pending", "replacement")

  private def encodedOperand(shape: String, tag: Int): (Async[Any], Either[Throwable, Any]) = shape match {
    case "value"   => (Async.succeed(tag), Right(tag))
    case "carrier" =>
      val p = AsyncTestSupport.syncReadyPollable(tag)
      (Async.succeed(p), Right(p))
    case "failure" =>
      val t = new RuntimeException(s"boom-$shape-$tag")
      (Async.fail(t), Left(t))
    case "pending" => (AsyncTestSupport.fromPollable(AsyncTestSupport.succeedAfter(tag, 2)), Right(tag))
    case _         => (AsyncTestSupport.fromPollable(replacementChain(tag, 2)), Right(tag))
  }

  private def blockOutcome(fa: Async[Any]): Either[Throwable, Any] =
    try Right(fa.block)
    catch { case t: Throwable => Left(t) }

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
      test("three-way zip agrees with a hand flatMap chain on value and exact poll order/count") {
        // Metamorphic: the same logical sequencing built two ways must drive the
        // leaves in the same order the same number of times and yield the same
        // value. Each leaf re-arms its waker until it settles after `polls` polls.
        def counted(
          name: String,
          log: scala.collection.mutable.ArrayBuffer[String],
          polls: Int,
          v: Int
        ): Pollable[Int] =
          new Pollable[Int] {
            private var remaining                      = polls
            def poll(onComplete: Runnable): Async[Int] = {
              log += s"$name.poll"
              if (remaining <= 0) Async.succeed(v)
              else { remaining -= 1; onComplete.run(); this }
            }
          }
        val logZ           = scala.collection.mutable.ArrayBuffer.empty[String]
        val za: Async[Int] = counted("a", logZ, 1, 1)
        val zb: Async[Int] = counted("b", logZ, 2, 2)
        val zc: Async[Int] = counted("c", logZ, 1, 3)
        val zr             = za.zip(zb).zip(zc).block

        val logF           = scala.collection.mutable.ArrayBuffer.empty[String]
        val fa: Async[Int] = counted("a", logF, 1, 1)
        val fb: Async[Int] = counted("b", logF, 2, 2)
        val fc: Async[Int] = counted("c", logF, 1, 3)
        val fr             = fa.flatMap(a => fb.flatMap(b => fc.map(c => (a, b, c)))).block

        assertTrue(zr == ((1, 2, 3)), fr == ((1, 2, 3)), logZ.toList == logF.toList)
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
      },
      test("zipWith combines every left/right pairing of the five operand encodings") {
        // Full cartesian over the runtime encodings: any failed side surfaces
        // its own cause (left first), and any successful pairing combines the
        // DELIVERED values — the bare user pollable, by identity, for a
        // carrier; the plain value for the rest.
        val anomalies = for {
          ls       <- encodingShapes
          rs       <- encodingShapes
          (l, lExp) = encodedOperand(ls, 1)
          (r, rExp) = encodedOperand(rs, 2)
          got       = blockOutcome(l.zipWith(r)((a, b) => (a, b)))
          want      = (lExp, rExp) match {
                   case (Left(t), _)         => Left(t)
                   case (_, Left(t))         => Left(t)
                   case (Right(a), Right(b)) => Right((a, b))
                 }
          if got != want
        } yield s"$ls zipWith $rs: got $got, want $want"
        assertTrue(anomalies == Nil)
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
      },
      test("a tap effect that is a pollable-as-value carrier is data, not a computation (not driven)") {
        // Parity with the ensuring sibling: a `tap` whose effect resolves to a
        // pollable-as-value carrier treats it as the (discarded) effect value,
        // never driving the user pollable.
        var driven            = false
        val p: Pollable[Unit] = new Pollable[Unit] {
          def poll(onComplete: Runnable): Async[Unit] = { driven = true; Async.succeed(()) }
        }
        val r = Async.succeed(5).tap(_ => Async.succeed(p)).block
        assertTrue(r == 5, !driven)
      },
      test("tap over each of the five operand encodings observes the delivered value and preserves the outcome") {
        val anomalies = encodingShapes.flatMap { shape =>
          val (fa, exp) = encodedOperand(shape, 9)
          var seen      = List.empty[Any]
          val got       = blockOutcome(fa.tap { a => seen = a :: seen; Async.succeed(()) })
          val effectOk  = exp match {
            case Right(v) => seen == List(v)
            case Left(_)  => seen.isEmpty
          }
          if (effectOk && got == exp) Nil
          else List(s"$shape: got $got (effect saw $seen), want $exp")
        }
        assertTrue(anomalies == Nil)
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
      },
      test("a finalizer that is a pollable-as-value carrier is data, not a computation (not driven)") {
        // `Async.succeed(p)` is a SUCCESSFUL finalizer whose value happens to be a
        // Pollable. `ensuring` runs the finalizer for effect-then-discard, but a
        // pollable-as-value carrier is data — driving it would run the user
        // pollable for effects it never asked to run, diverging from `tap`'s
        // identical treatment of an effect-as-value.
        var driven           = false
        val p: Pollable[Unit] = new Pollable[Unit] {
          def poll(onComplete: Runnable): Async[Unit] = { driven = true; Async.succeed(()) }
        }
        val r = Async.succeed(5).ensuring(Async.succeed(p)).block
        assertTrue(r == 5, !driven)
      },
      test("bracket: a resource closed via ensuring is released exactly once on success and on failure") {
        // ensuring as the cleanup half of acquire/use/release: the finalizer
        // must run exactly once whether `use` succeeds or fails, with no leak
        // (acquire-without-release) and no double-close.
        def runBracket(use: Async[Int]): (Int, Either[Throwable, Int]) = {
          var closes = 0
          val a      = use.ensuring(Async.succeed { closes += 1 })
          val out    =
            try Right(a.block)
            catch { case t: Throwable => Left(t) }
          (closes, out)
        }
        val (cSucc, rSucc)               = runBracket(Async.succeed(1))
        val (cFail, rFail)               = runBracket(Async.fail(boom))
        val (cPend, rPend)               = {
          val (c, p) = AsyncTestSupport.pending[Int]
          var closes = 0
          val a      = p.ensuring(Async.succeed { closes += 1 })
          c.succeed(7)
          (closes, scala.util.Try(a.block).toEither)
        }
        assertTrue(
          rSucc == Right(1) && cSucc == 1,
          rFail.left.toOption.contains(boom) && cFail == 1,
          rPend == Right(7) && cPend == 1
        )
      },
      test("a pending finalizer is awaited before the primary success propagates") {
        val (c, fin) = AsyncTestSupport.pending[Unit]
        val a        = Async.succeed(5).ensuring(fin)
        val r1       = AsyncTestSupport.pollOnce(a)
        c.succeed(())
        assertTrue(AsyncTestSupport.isPending(r1), a.block == 5)
      },
      test("a pending finalizer is awaited before the primary failure propagates") {
        val (c, fin) = AsyncTestSupport.pending[Unit]
        val a        = Async.fail(boom).ensuring(fin)
        val r1       = AsyncTestSupport.pollOnce(a)
        c.succeed(())
        val thrown = scala.util.Try(a.block).failed.toOption
        assertTrue(AsyncTestSupport.isPending(r1), thrown.contains(boom))
      },
      test("a never finalizer keeps the whole ensuring pending (success primary)") {
        val a = Async.succeed(1).ensuring(Async.never)
        val r = AsyncTestSupport.driveToEnd(a, 16)
        assertTrue(AsyncTestSupport.isPending(a), AsyncTestSupport.isPending(r))
      },
      test("nested ensuring finalizers run inner-before-outer, each exactly once, on failure") {
        // Stacked brackets must unwind innermost-first; both finalizers run once
        // and the original failure is preserved. The finalizer effect fires when
        // the finalizer is DRIVEN (a poll-time effect), not at eager construction,
        // so the recorded order is the genuine unwind order.
        val order                     = scala.collection.mutable.ArrayBuffer.empty[String]
        def closer(label: String): Async[Unit] = new Pollable[Unit] {
          def poll(onComplete: Runnable): Async[Unit] = { order += label; Async.succeed(()) }
        }
        val a =
          (Async.fail(boom): Async[Int])
            .ensuring(closer("close-inner"))
            .ensuring(closer("close-outer"))
        val thrown = scala.util.Try(a.block).failed.toOption
        assertTrue(thrown.contains(boom), order.toList == List("close-inner", "close-outer"))
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
      test("collectAll over a single-element Iterator yields a singleton list") {
        val r = Async.collectAll(Iterator(Async.succeed(42))).block
        assertTrue(r == List(42))
      },
      test("collectAll over a single-element pending Iterator completes when the element settles") {
        val (c, p)                   = AsyncTestSupport.pending[Int]
        val all                      = Async.collectAll(Iterator(p))
        val pendingAtStart           = AsyncTestSupport.isPending(all)
        c.succeed(7)
        assertTrue(pendingAtStart, all.block == List(7))
      },
      test("collectAll pulls the source iterator exactly once per element (single-pass)") {
        var nextCalls = 0
        val src       = new IterableOnce[Async[Int]] {
          def iterator: Iterator[Async[Int]] = new Iterator[Async[Int]] {
            private val backing  = List(Async.succeed(1), Async.succeed(2), Async.succeed(3)).iterator
            def hasNext: Boolean = backing.hasNext
            def next(): Async[Int] = { nextCalls += 1; backing.next() }
          }
        }
        val r = Async.collectAll(src).block
        assertTrue(r == List(1, 2, 3), nextCalls == 3)
      },
      test("collectAll containing never stays pending without spurious completion") {
        val all = Async.collectAll(List[Async[Int]](Async.succeed(1), Async.never, Async.succeed(3)))
        val r   = AsyncTestSupport.driveToEnd(all, 16)
        assertTrue(AsyncTestSupport.isPending(all), AsyncTestSupport.isPending(r))
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
      },
      test("collectAll reifies a source iterator that throws mid-drive rather than leaking it") {
        // A throw escaping `poll` (here from `iterator.next()` while draining past
        // a pending element) is the same failure channel every driver reifies;
        // `.block` must rethrow the cause, not let it escape the encoding.
        val itBoom = new RuntimeException("iterator-boom")
        val (c, p) = AsyncTestSupport.pending[Int]
        val source = new IterableOnce[Async[Int]] {
          def iterator: Iterator[Async[Int]] = new Iterator[Async[Int]] {
            private var idx        = 0
            def hasNext: Boolean   = true
            def next(): Async[Int] = { val i = idx; idx += 1; if (i == 0) p else throw itBoom }
          }
        }
        val all            = Async.collectAll(source)
        val pendingAtStart = AsyncTestSupport.isPending(all)
        c.succeed(1)
        val thrown = Try(all.block).failed.toOption
        assertTrue(pendingAtStart, thrown.contains(itBoom))
      },
      test("collectAll reifies a source iterator that throws while draining all-ready elements (no eager leak)") {
        // Parity with the mid-drive-past-a-pending-element case above: a source
        // iterator that throws must surface through the Async failure channel,
        // not leak at the `collectAll` call site. When every element is ready
        // the drain is eager (`drainCollectAll` / the `List` fast path), so the
        // iterator throw escapes the construction call unless it is reified —
        // an inconsistency with the deferred (pending-element) drain, which
        // does reify it.
        val itBoom = new RuntimeException("eager-iterator-boom")
        val source = new IterableOnce[Async[Int]] {
          def iterator: Iterator[Async[Int]] = new Iterator[Async[Int]] {
            private var idx        = 0
            def hasNext: Boolean   = true
            def next(): Async[Int] = { val i = idx; idx += 1; if (i == 0) Async.succeed(1) else throw itBoom }
          }
        }
        val constructed =
          try Right(Async.collectAll(source))
          catch { case t: Throwable => Left(t) }
        val outcome = constructed match {
          case Right(all)  => Try(all.block).failed.toOption
          case Left(eager) => Some(eager) // leaked at the call site (the defect)
        }
        assertTrue(constructed.isRight, outcome.contains(itBoom))
      },
      test("collectAll over many mixed ready+pending elements is stack-safe and order-preserving") {
        val n                       = 50000
        val completers              = scala.collection.mutable.ArrayBuffer.empty[Completer[Int]]
        val items: List[Async[Int]] = (0 until n).map { i =>
          if (i % 1000 == 0) {
            val (c, fa) = AsyncTestSupport.pending[Int]
            completers += c
            fa
          } else Async.succeed(i)
        }.toList
        val all = Async.collectAll(items)
        var ci  = 0
        (0 until n).foreach(i => if (i % 1000 == 0) { completers(ci).succeed(i); ci += 1 })
        val r = all.block
        assertTrue(r == (0 until n).toList)
      }
    ),
    suite("fan-out idempotency")(
      // A single `Async` value may be driven by more than one consumer (two
      // `.start`s, `.start` + `.block`, an interop runner + a `.block`). Each
      // such driver polls the SAME combinator pollable: while pending it
      // registers its own waker; when the shared completer settles, every
      // registered driver is woken and polls once more to observe the value.
      // That second driver's settling poll must be a pure observation — it must
      // NOT re-invoke the user function or re-run the side effect. `ensuring`'s
      // finalizer was already locked against this (BUG-041); the same contract
      // holds for `map` / `flatMap` / `tap` / `zipWith` / `catchAll`.
      //
      // `twoDriverFanOut` reproduces exactly the driver interleaving above with
      // two distinct wakers over one pending completer: poll-A (pending),
      // poll-B (pending), settle, poll-A (observe), poll-B (observe). It returns
      // the two observed outcomes so each combinator test can assert the user
      // effect ran exactly once.
      {
        def twoDriverFanOut[A](build: Completer[Int] => Async[A]): (Async[A], Async[A]) = {
          val c        = new Completer[Int]
          val fa       = build(c)
          val pa       = fa.asInstanceOf[Pollable[A]]
          val wakerA: Runnable = () => ()
          val wakerB: Runnable = () => ()
          pa.poll(wakerA) // driver A registers
          pa.poll(wakerB) // driver B registers (distinct waker -> fan-out)
          c.succeed(1)
          val a = pa.poll(wakerA) // driver A observes terminal
          val b = pa.poll(wakerB) // driver B observes terminal (must not re-fire)
          (a, b)
        }

        suite("two consumers driving one settled value run the user effect exactly once")(
          test("map") {
            var calls    = 0
            val (a, b)   = twoDriverFanOut(c => c.peek.map(x => { calls += 1; x + 1 }))
            assertTrue(calls == 1, a.block == 2, b.block == 2)
          },
          test("flatMap") {
            var calls  = 0
            val (a, b) = twoDriverFanOut(c => c.peek.flatMap(x => { calls += 1; Async.succeed(x + 1) }))
            assertTrue(calls == 1, a.block == 2, b.block == 2)
          },
          test("tap") {
            var calls  = 0
            val (a, b) = twoDriverFanOut(c => c.peek.tap(_ => Async.succeed { calls += 1; () }))
            assertTrue(calls == 1, a.block == 1, b.block == 1)
          },
          test("zipWith") {
            var calls  = 0
            val (a, b) = twoDriverFanOut(c => c.peek.zipWith(Async.succeed(3))((x, y) => { calls += 1; x + y }))
            assertTrue(calls == 1, a.block == 4, b.block == 4)
          },
          test("catchAll") {
            var calls  = 0
            val (a, b) = twoDriverFanOut { c =>
              c.peek.flatMap(_ => Async.fail(boom)).catchAll(_ => { calls += 1; Async.succeed(0) })
            }
            assertTrue(calls == 1, a.block == 0, b.block == 0)
          },
          test("flatMap whose continuation suspends runs the continuation exactly once under fan-out") {
            // Same fan-out as the other cases, but `f` returns a PENDING pollable
            // (not a ready value). Driver A polls `pa` to its value, runs `f`, and
            // drives `f`'s pending result once — handing it back as a replacement.
            // Because that replacement is still pending, the FlatMapPollable's `done`
            // memo is not set. Driver B then re-polls the SAME FlatMapPollable, which
            // re-polls the already-settled `pa` and runs `f` a SECOND time.
            var calls       = 0
            val (innerC, _) = AsyncTestSupport.pending[Int]
            val (a, b)      = twoDriverFanOut { c =>
              c.peek.flatMap { x => calls += 1; innerC.peek.map(_ => x + 1) }
            }
            innerC.succeed(100)
            assertTrue(calls == 1, a.block == 2, b.block == 2)
          },
          test("catchAll whose recovery suspends runs the handler exactly once under fan-out") {
            // Mirror of the flatMap case for the recovery channel: the handler
            // returns a PENDING pollable, so CatchAllPollable hands back the pending
            // replacement without memoizing. A second fan-out driver re-polls and
            // re-invokes the handler.
            var calls       = 0
            val (innerC, _) = AsyncTestSupport.pending[Int]
            val (a, b)      = twoDriverFanOut { c =>
              c.peek.flatMap(_ => Async.fail(boom)).catchAll { _ => calls += 1; innerC.peek.map(_ => 0) }
            }
            innerC.succeed(100)
            assertTrue(calls == 1, a.block == 0, b.block == 0)
          },
          test("tap whose side effect suspends runs the effect exactly once under fan-out") {
            // `tap` routes through a FlatMapPollable whose continuation is
            // `runThenValue(f(a), a, ...)`. When `f(a)` itself suspends (returns a
            // PENDING pollable), the FlatMapPollable memoizes the pending
            // replacement in `done`, so a second fan-out driver follows that same
            // replacement instead of re-invoking `f`. Sibling of the flatMap /
            // catchAll suspended-continuation cases above, for the tap channel.
            var calls       = 0
            val (innerC, _) = AsyncTestSupport.pending[Int]
            val (a, b)      = twoDriverFanOut { c =>
              c.peek.tap { _ => calls += 1; innerC.peek.map(_ => ()) }
            }
            innerC.succeed(100)
            assertTrue(calls == 1, a.block == 1, b.block == 1)
          }
        )
      },
      test("collectAll: a second driver observing one settled batch sees the same list (no crash, no double-append)") {
        // collectAll's continuation mutates a shared ListBuffer and nulls `cur`
        // when the batch settles. A second fan-out driver that polls after the
        // batch settles must observe the SAME completed list — not crash on the
        // nulled `cur`, nor re-drain the spent iterator/buffer.
        val c1   = new Completer[Int]
        val all  = Async.collectAll(List[Async[Int]](Async.succeed(0), c1.peek, Async.succeed(2)))
        val pa   = all.asInstanceOf[Pollable[List[Int]]]
        val wakerA: Runnable = () => ()
        val wakerB: Runnable = () => ()
        pa.poll(wakerA)
        pa.poll(wakerB)
        c1.succeed(1)
        val a = pa.poll(wakerA) // driver A drains the batch
        val b =
          try Right(pa.poll(wakerB)) // driver B observes the settled batch
          catch { case t: Throwable => Left(t) }
        assertTrue(
          a.block == List(0, 1, 2),
          b.isRight,
          b.toOption.map(_.block).contains(List(0, 1, 2))
        )
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
      },
      test("when(true) over a failing fa propagates the failure") {
        val thrown = scala.util.Try(when(true)(Async.fail(boom)).block).failed.toOption
        assertTrue(thrown.contains(boom))
      },
      test("when(false) over a failing fa neither runs nor fails (by-name fa not constructed)") {
        var constructed = false
        val r           = when(false) { constructed = true; Async.fail(boom) }.block
        assertTrue(r == (()), !constructed)
      },
      test("unless(false) over a failing fa propagates the failure") {
        val thrown = scala.util.Try(unless(false)(Async.fail(boom)).block).failed.toOption
        assertTrue(thrown.contains(boom))
      },
      test("unless(true) over a failing fa neither runs nor fails (by-name fa not constructed)") {
        var constructed = false
        val r           = unless(true) { constructed = true; Async.fail(boom) }.block
        assertTrue(r == (()), !constructed)
      },
      test("when(true) over a pending-then-failing fa awaits then propagates the failure") {
        val (c, fa) = AsyncTestSupport.pending[Int]
        val w       = when(true)(fa)
        val r1      = AsyncTestSupport.pollOnce(w)
        c.fail(boom)
        val thrown = scala.util.Try(w.block).failed.toOption
        assertTrue(AsyncTestSupport.isPending(r1), thrown.contains(boom))
      },
      test("when(true) over a pollable-as-value carrier discards the value without driving it") {
        var driven            = false
        val p: Pollable[Unit] = new Pollable[Unit] {
          def poll(onComplete: Runnable): Async[Unit] = { driven = true; Async.succeed(()) }
        }
        val r = when(true)(Async.succeed(p)).block
        assertTrue(r == (()), !driven)
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
      },
      test("*> sequences every left/right pairing of the five operand encodings and keeps the right value") {
        val anomalies = for {
          ls       <- encodingShapes
          rs       <- encodingShapes
          (l, lExp) = encodedOperand(ls, 1)
          (r, rExp) = encodedOperand(rs, 2)
          got       = blockOutcome(l *> r)
          want      = (lExp, rExp) match {
                   case (Left(t), _)  => Left(t)
                   case (_, Left(t))  => Left(t)
                   case (_, Right(b)) => Right(b)
                 }
          if got != want
        } yield s"$ls *> $rs: got $got, want $want"
        assertTrue(anomalies == Nil)
      },
      test("<* sequences every left/right pairing of the five operand encodings and keeps the left value") {
        val anomalies = for {
          ls       <- encodingShapes
          rs       <- encodingShapes
          (l, lExp) = encodedOperand(ls, 1)
          (r, rExp) = encodedOperand(rs, 2)
          got       = blockOutcome(l <* r)
          want      = (lExp, rExp) match {
                   case (Left(t), _)  => Left(t)
                   case (_, Left(t))  => Left(t)
                   case (Right(a), _) => Right(a)
                 }
          if got != want
        } yield s"$ls <* $rs: got $got, want $want"
        assertTrue(anomalies == Nil)
      },
      test("*> with a never left stays pending and never observes the right") {
        var rightDriven       = false
        val right: Async[Int] = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = { rightDriven = true; Async.succeed(2) }
        }
        val z = Async.never *> right
        val r = AsyncTestSupport.driveToEnd(z, 16)
        assertTrue(AsyncTestSupport.isPending(z), AsyncTestSupport.isPending(r), !rightDriven)
      },
      test("<* with a never right stays pending after the left resolves (right never settles)") {
        val z = Async.succeed(1) <* Async.never
        val r = AsyncTestSupport.driveToEnd(z, 16)
        assertTrue(AsyncTestSupport.isPending(z), AsyncTestSupport.isPending(r))
      },
      test("as and unit over each of the five operand encodings replace or discard only successes") {
        val anomalies = encodingShapes.flatMap { shape =>
          val (fa1, exp1) = encodedOperand(shape, 3)
          val (fa2, exp2) = encodedOperand(shape, 3)
          val gotAs       = blockOutcome(fa1.as("x"))
          val gotUnit     = blockOutcome(fa2.unit)
          val wantAs      = exp1.map(_ => "x")
          val wantUnit    = exp2.map(_ => ())
          if (gotAs == wantAs && gotUnit == wantUnit) Nil
          else List(s"$shape: as → $gotAs (want $wantAs), unit → $gotUnit (want $wantUnit)")
        }
        assertTrue(anomalies == Nil)
      }
    )
  )
}

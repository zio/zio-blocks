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

package zio.blocks.streams

import zio.ZIO
import zio.durationInt
import zio.test._
import zio.blocks.chunk.Chunk
import java.util.concurrent.atomic.AtomicInteger

object StreamJvmSpec extends StreamsBaseSpec {

  private def isExactRange(chunk: zio.blocks.chunk.Chunk[Int], n: Int): Boolean =
    if (chunk.length != n) false
    else {
      val iterator = chunk.iterator
      var i        = 0
      var ok       = true
      while (ok && iterator.hasNext) {
        if (iterator.next() != i) ok = false
        i += 1
      }
      ok && i == n
    }

  private def sInt(xs: Seq[Int]): Stream[Nothing, Int]    = Stream.fromChunk(Chunk.fromArray(xs.toArray))
  private def sLong(xs: Seq[Long]): Stream[Nothing, Long] = Stream.fromChunk(Chunk.fromArray(xs.toArray))

  def spec: Spec[TestEnvironment, Any] = suite("Stream (JVM)")(
    suite("platform")(
      suite("virtual threads")(
        test("startVirtualThread runs the task") {
          ZIO.attemptBlocking {
            val flag = new java.util.concurrent.atomic.AtomicBoolean(false)
            val t    = Platform.startVirtualThread("test-vthread", () => flag.set(true))
            t.join(5000)
            assertTrue(flag.get())
          }
        }
      ),
      suite("buffer")(
        test("buffer collects range with full fidelity") {
          ZIO.attemptBlocking {
            val n      = 200
            val result = Stream.range(0, n).buffer(8).runCollect
            assertTrue(result match {
              case Right(chunk) => isExactRange(chunk, n)
              case _            => false
            })
          }
        },
        test("buffer propagates stream failures") {
          ZIO.attemptBlocking {
            val result = Stream.fail("boom").buffer(16).runCollect
            assertTrue(result == Left("boom"))
          }
        },
        test("buffer supports early termination") {
          ZIO.attemptBlocking {
            val result = Stream.range(0, Int.MaxValue).buffer(64).take(10).runCollect
            assertTrue(result match {
              case Right(chunk) => isExactRange(chunk, 10)
              case _            => false
            })
          }
        }
      )
    ) @@ TestAspect.timeout(60.seconds) @@ TestAspect.timed @@ TestAspect.sequential,
    suite("regressions")(
      // ---- recovery + resource + early-termination + restart combos -----------
      test("acquireRelease + take + repeated: release count == acquire count [AdversarialFinalConvergenceSpec]") {
        val opens  = new AtomicInteger(0)
        val closes = new AtomicInteger(0)
        val s      = Stream
          .fromAcquireRelease(
            acquire = { opens.incrementAndGet(); () },
            release = (_: Unit) => { closes.incrementAndGet(); () }
          )(_ => sInt(List(1, 2, 3)))
          .take(2)
          .repeated
          .take(5)
        val out = s.runCollect.toOption.get.toList
        assertTrue(out == List(1, 2, 1, 2, 1)) &&
        assertTrue(opens.get() == closes.get()) &&
        assertTrue(closes.get() >= 1)
      },
      test("ensuring + take + repeated: finalizer runs (balanced) [AdversarialFinalConvergenceSpec]") {
        val fin = new AtomicInteger(0)
        val s   = sInt(List(1, 2, 3)).ensuring(fin.incrementAndGet()).take(2).repeated.take(5)
        val out = s.runCollect.toOption.get.toList
        assertTrue(out == List(1, 2, 1, 2, 1)) && assertTrue(fin.get() >= 1)
      },
      test(
        "catchAll + ensuring (both branches) + take after recovery: each finalizer once [AdversarialFinalConvergenceSpec]"
      ) {
        val finA                         = new AtomicInteger(0)
        val finB                         = new AtomicInteger(0)
        val failing: Stream[String, Int] =
          sInt(List(1, 2)).ensuring(finA.incrementAndGet()).concat(Stream.fail("boom"))
        val recovered = failing.catchAll(_ => sInt(List(7, 8, 9)).ensuring(finB.incrementAndGet())).take(4)
        val out       = recovered.runCollect.toOption.get.toList
        assertTrue(out == List(1, 2, 7, 8)) && assertTrue(finA.get() == 1) && assertTrue(finB.get() == 1)
      },
      // ---- specialized primitive lanes carrying sentinel-valued real elements -
      test("takeWhile Long lane preserves a real Long.MaxValue element [AdversarialFinalConvergenceSpec]") {
        val data = List(1L, 2L, Long.MaxValue, 3L)
        val out  = sLong(data).takeWhile(_ != 3L).runCollect.toOption.get.toList
        assertTrue(out == List(1L, 2L, Long.MaxValue))
      },
      test(
        "deep Long-lane chain preserves Long.MaxValue and matches List reference [AdversarialFinalConvergenceSpec]"
      ) {
        val data     = List(1L, Long.MaxValue, 2L, Long.MaxValue, 3L)
        val expected = data.filter(_ > 0L).map(_ + 1L).take(4)
        val out      = sLong(data).filter(_ > 0L).map(_ + 1L).take(4).runCollect.toOption.get.toList
        assertTrue(out == expected)
      },
      test("mapAccum -> intersperse -> take differential vs List reference [AdversarialFinalConvergenceSpec]") {
        val data     = (1 to 8).toList
        val accumExp = data.scanLeft((0, 0)) { case ((s, _), a) => (s + a, s + a) }.drop(1).map(_._2)
        val expected = accumExp.flatMap(x => List(x, 0)).dropRight(1).take(7)
        val out      = sInt(data)
          .mapAccum(0) { (s, a) =>
            val s2 = s + a; (s2, s2)
          }
          .intersperse(0)
          .take(7)
          .runCollect
          .toOption
          .get
          .toList
        assertTrue(out == expected)
      },
      test(
        "distinct -> repeated -> take on Long lane with Long.MaxValue sentinel value [AdversarialFinalConvergenceSpec]"
      ) {
        val data = List(1L, Long.MaxValue, 1L, 2L, Long.MaxValue)
        val out  = sLong(data).distinct.repeated.take(7).runCollect.toOption.get.toList
        assertTrue(out == List(1L, Long.MaxValue, 2L, 1L, Long.MaxValue, 2L, 1L))
      },
      // ---- unordered concurrency: deterministic multiset + leak invariant -----
      test("mapPar multiset completeness across a run/width matrix [AdversarialFinalConvergenceSpec]") {
        val bad = scala.collection.mutable.ListBuffer.empty[String]
        for (run <- 0 until 50; n <- List(1, 2, 4, 8)) {
          val len  = run % 40
          val data = (1 to len).toList
          val got  = sInt(data).mapPar(n)(_ * 2).runCollect.toOption.get.toList.sorted
          val exp  = data.map(_ * 2).sorted
          if (got != exp) bad += s"run=$run n=$n len=$len"
        }
        assertTrue(bad.toList == Nil)
      },
      test("flatMapPar multiset completeness + open==close leak invariant [AdversarialFinalConvergenceSpec]") {
        val bad = scala.collection.mutable.ListBuffer.empty[String]
        for (run <- 0 until 40; n <- List(1, 2, 4)) {
          val opens  = new AtomicInteger(0)
          val closes = new AtomicInteger(0)
          val len    = run % 30
          val data   = (1 to len).toList
          val got    = sInt(data)
            .flatMapPar(n)(a =>
              Stream.fromAcquireRelease(
                acquire = { opens.incrementAndGet(); () },
                release = (_: Unit) => { closes.incrementAndGet(); () }
              )(_ => sInt(List(a, a * 10)))
            )
            .runCollect
            .toOption
            .get
            .toList
            .sorted
          val exp = data.flatMap(a => List(a, a * 10)).sorted
          if (got != exp) bad += s"run=$run n=$n len=$len multiset"
          if (opens.get() != closes.get()) bad += s"run=$run n=$n len=$len leak ${opens.get()}/${closes.get()}"
        }
        assertTrue(bad.toList == Nil)
      },
      test("mergeAll multiset completeness + open==close leak invariant [AdversarialFinalConvergenceSpec]") {
        val bad = scala.collection.mutable.ListBuffer.empty[String]
        for (run <- 0 until 40; maxOpen <- List(1, 2, 4)) {
          val opens                              = new AtomicInteger(0)
          val closes                             = new AtomicInteger(0)
          val nInner                             = run % 12
          val inners: List[Stream[Nothing, Int]] = (0 until nInner).toList.map { i =>
            Stream.fromAcquireRelease(
              acquire = { opens.incrementAndGet(); () },
              release = (_: Unit) => { closes.incrementAndGet(); () }
            )(_ => sInt(List(i, i + 100)))
          }
          val got = Stream.mergeAll(maxOpen)(Stream.fromIterable(inners)).runCollect.toOption.get.toList.sorted
          val exp = (0 until nInner).toList.flatMap(i => List(i, i + 100)).sorted
          if (got != exp) bad += s"run=$run maxOpen=$maxOpen multiset"
          if (opens.get() != closes.get()) bad += s"run=$run leak ${opens.get()}/${closes.get()}"
        }
        assertTrue(bad.toList == Nil)
      },
      // ---- randomized deep op-chain fuzz vs List reference (seed recorded) -----
      test("randomized deep chain fuzz (seed=20260610) vs List reference [AdversarialFinalConvergenceSpec]") {
        val rng = new scala.util.Random(20260610L)
        val bad = scala.collection.mutable.ListBuffer.empty[String]
        sealed trait Op
        final case class MapOp(k: Int)    extends Op
        final case class FilterOp(m: Int) extends Op
        final case class TakeOp(k: Int)   extends Op
        final case class DropOp(k: Int)   extends Op
        case object DistinctOp            extends Op
        def randOp(): Op = rng.nextInt(6) match {
          case 0 => MapOp(rng.nextInt(7) - 3)
          case 1 => FilterOp(rng.nextInt(3) + 2)
          case 2 => TakeOp(rng.nextInt(8))
          case 3 => DropOp(rng.nextInt(5))
          case 4 => DistinctOp
          case _ => MapOp(rng.nextInt(5))
        }
        for (_ <- 0 until 300) {
          val len                        = rng.nextInt(20)
          val data                       = List.fill(len)(rng.nextInt(10) - 5)
          val ops                        = List.fill(rng.nextInt(6))(randOp())
          var refL: List[Int]            = data
          var strm: Stream[Nothing, Int] = sInt(data)
          ops.foreach {
            case MapOp(k)    => refL = refL.map(_ + k); strm = strm.map(_ + k)
            case FilterOp(m) => refL = refL.filter(_ % m == 0); strm = strm.filter(_ % m == 0)
            case TakeOp(k)   => refL = refL.take(k); strm = strm.take(k.toLong)
            case DropOp(k)   => refL = refL.drop(k); strm = strm.drop(k.toLong)
            case DistinctOp  => refL = refL.distinct; strm = strm.distinct
          }
          val got = strm.runCollect.toOption.get.toList
          if (got != refL) bad += s"data=$data ops=$ops exp=$refL got=$got"
        }
        assertTrue(bad.toList == Nil)
      },
      // ---- lossless error identity through Pipeline -> Sink and via -----------
      test(
        "Pipeline.applyToSink: user-fn defect propagates by identity; finalizer once [AdversarialFinalConvergenceSpec]"
      ) {
        final class Tag(msg: String) extends RuntimeException(msg)
        val tag                      = new Tag("pipe-sink-fault")
        val fin                      = new AtomicInteger(0)
        val pipe: Pipeline[Int, Int] = Pipeline.map[Int, Int](x => if (x == 3) throw tag else x * 2)
        val composed                 = pipe.applyToSink(Sink.collectAll[Int])
        val src                      = sInt(List(1, 2, 3, 4)).ensuring(fin.incrementAndGet())
        val caught                   =
          try { src.run(composed); null: Throwable }
          catch { case t: Throwable => t }
        assertTrue(caught eq tag) && assertTrue(fin.get() == 1)
      },
      test(
        "via(Pipeline).run(Sink): defect identity preserved (never retyped to typed error) [AdversarialFinalConvergenceSpec]"
      ) {
        final class Tag(msg: String) extends RuntimeException(msg)
        val tag                      = new Tag("via-fault")
        val pipe: Pipeline[Int, Int] = Pipeline.map[Int, Int](x => if (x == 2) throw tag else x)
        val caught                   =
          try { sInt(List(1, 2, 3)).via(pipe).run(Sink.collectAll[Int]); null: Throwable }
          catch { case t: Throwable => t }
        assertTrue(caught eq tag)
      },
      test(
        "composed Pipeline andThen -> Sink: fault in 2nd stage keeps defect identity [AdversarialFinalConvergenceSpec]"
      ) {
        final class Tag(msg: String) extends RuntimeException(msg)
        val tag                    = new Tag("second-stage-fault")
        val p1: Pipeline[Int, Int] = Pipeline.map[Int, Int](_ + 1)
        val p2: Pipeline[Int, Int] = Pipeline.map[Int, Int](x => if (x == 3) throw tag else x)
        val composed               = p1.andThen(p2).applyToSink(Sink.collectAll[Int])
        val caught                 =
          try { sInt(List(1, 2, 9)).run(composed); null: Throwable }
          catch { case t: Throwable => t }
        assertTrue(caught eq tag)
      }
    )
  )
}

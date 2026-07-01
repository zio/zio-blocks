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

import zio._
import zio.blocks.chunk.Chunk
import zio.test._

import java.util.concurrent.atomic.AtomicInteger
import scala.util.Try

object StreamConcurrencySpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("Stream concurrency")(
    bufferSuite,
    mapParSuite,
    mergeAllSuite,
    flatMapParSuite,
    concurrentLawsSuite,
    jvmTypePropagationSuite
  )

  // Helper from AdversarialConcurrentLifecycleConvergenceSpec.
  private def managed(opens: AtomicInteger, closes: AtomicInteger, n: Int): Stream[Nothing, Int] =
    Stream.fromAcquireRelease({ opens.incrementAndGet(); () }, (_: Unit) => { closes.incrementAndGet(); () })(_ =>
      Stream.range(0, n)
    )

  // =========================================================================
  //  buffer
  // =========================================================================

  private val bufferSuite = suite("buffer")(
    suite("correctness")(
      test("empty stream yields empty chunk") {
        val result = Stream.empty.buffer(8).runCollect
        assertTrue(result == Right(Chunk.empty))
      },
      test("single element") {
        val result = Stream.succeed(42).buffer(4).runCollect
        assertTrue(result == Right(Chunk(42)))
      },
      test("multiple elements preserve order") {
        val result = Stream.range(1, 6).buffer(8).runCollect
        assertTrue(result == Right(Chunk(1, 2, 3, 4, 5)))
      },
      test("ordering preserved for larger range") {
        val result = Stream.range(0, 50).buffer(64).runCollect
        assertTrue(result == Right(Chunk.fromIterable(0 until 50)))
      },
      test("buffer larger than stream") {
        val result = Stream(1, 2, 3).buffer(1024).runCollect
        assertTrue(result == Right(Chunk(1, 2, 3)))
      },
      test("buffer equal to stream size") {
        val result = Stream.range(0, 8).buffer(16).runCollect
        assertTrue(result == Right(Chunk.fromIterable(0 until 8)))
      },
      test("string elements preserve order") {
        val result = Stream("a", "b", "c", "d").buffer(8).runCollect
        assertTrue(result == Right(Chunk("a", "b", "c", "d")))
      }
    ),
    suite("error handling")(
      test("error-only stream propagates error") {
        val result = Stream.fail("boom").buffer(16).runCollect
        assertTrue(result == Left("boom"))
      },
      test("error after elements propagates error") {
        val result = Stream(1, 2, 3).concat(Stream.fail[String]("mid")).buffer(16).runCollect
        assertTrue(result == Left("mid"))
      },
      // BUG-R5-05 (JS): the synchronous prefetch buffer rethrows an upstream
      // error IMMEDIATELY from inside its refill loop, abandoning the elements
      // it already prefetched, so a downstream `catchAll` never sees them. The
      // JVM concurrent buffer delivers the buffered prefix first and only then
      // surfaces the error. `buffer(n)` is a pure decoupling transform: it must
      // not change which elements precede an error.
      test("buffer_errorAfterElements_deliversBufferedPrefixToRecovery") {
        val s      = (Stream(1, 2): Stream[Nothing, Int]) ++ (Stream.fail("boom"): Stream[String, Int])
        val result = s.buffer(8).catchAll(_ => Stream(9)).runCollect
        assertTrue(result == Right(Chunk(1, 2, 9)))
      },
      test("invalid buffer size 0 throws IllegalArgumentException") {
        assertTrue {
          try { Pipeline.buffer[Int](0); false }
          catch { case _: IllegalArgumentException => true }
        }
      },
      test("invalid buffer size -1 throws IllegalArgumentException") {
        assertTrue {
          try { Pipeline.buffer[Int](-1); false }
          catch { case _: IllegalArgumentException => true }
        }
      },
      test("Stream#buffer(0) throws IllegalArgumentException") {
        assertTrue {
          try { Stream.succeed(1).buffer(0); false }
          catch { case _: IllegalArgumentException => true }
        }
      }
    ),
    suite("pipeline composition")(
      test("via Pipeline.buffer(n)") {
        val result = Stream.range(0, 5).via(Pipeline.buffer(8)).runCollect
        assertTrue(result == Right(Chunk.fromIterable(0 until 5)))
      },
      test("andThen composition: buffer then map") {
        val result =
          Stream.range(0, 5).via(Pipeline.buffer[Int](8).andThen(Pipeline.map[Int, Int](_ * 2))).runCollect
        assertTrue(result == Right(Chunk(0, 2, 4, 6, 8)))
      },
      test("andThen composition: map then buffer") {
        val result =
          Stream.range(0, 5).via(Pipeline.map[Int, Int](_ + 10).andThen(Pipeline.buffer[Int](8))).runCollect
        assertTrue(result == Right(Chunk(10, 11, 12, 13, 14)))
      },
      test("render contains .buffer(n)") {
        assertTrue(Stream.succeed(1).buffer(8).render.contains(".buffer(8)"))
      }
    ),
    suite("resource safety")(
      test("ensuring finalizer runs after buffer") {
        val ref = new java.util.concurrent.atomic.AtomicBoolean(false)
        val _   = Stream.succeed(1).ensuring(ref.set(true)).buffer(8).runCollect
        assertTrue(ref.get())
      },
      test("ensuring finalizer runs on error after buffer") {
        val ref = new java.util.concurrent.atomic.AtomicBoolean(false)
        val _   = Stream.fail("err").ensuring(ref.set(true)).buffer(8).runCollect
        assertTrue(ref.get())
      },
      // Lossless errors (Principle 4): an `ensuring` finalizer failure upstream
      // of `buffer` must propagate as a thrown defect, exactly as it does
      // without `buffer` (control below) and as it does on JS (SyncBufferedReader
      // closes the upstream on the consumer's close path). On JVM the producer
      // thread closes the upstream in its `finally` and swallows the throwable
      // (`catch { case _: Throwable => () }` in ConcurrentBufferedReader),
      // silently discarding the finalizer failure — a platform divergence.
      test("buffer_upstreamCloseFailure_propagates [AdversarialCloseFailureSwallowSpec]") {
        val control =
          Try(Stream(1, 2, 3).ensuring(throw new RuntimeException("close-boom")).runDrain)
        assertTrue(control.isFailure) && {
          val got =
            Try(Stream(1, 2, 3).ensuring(throw new RuntimeException("close-boom")).buffer(4).runDrain)
          assertTrue(got.isFailure)
        }
      }
    ),
    suite("early termination")(
      test("take(5) after buffer") {
        val result = Stream.range(0, 1000).buffer(16).take(5).runCollect
        assertTrue(result == Right(Chunk.fromIterable(0 until 5)))
      },
      test("head after buffer") {
        val result = Stream.range(0, 1000).buffer(16).head
        assertTrue(result == Right(Some(0)))
      }
    ),
    suite("platform detection")(
      test("Platform.supportsConcurrency is consistent with TestPlatform") {
        assertTrue(Platform.supportsConcurrency == TestPlatform.isJVM)
      }
    )
  ) @@ TestAspect.sequential

  // =========================================================================
  //  mapPar
  // =========================================================================

  private val mapParSuite = suite("mapPar")(
    test("basic correctness") {
      val result      = Stream.range(0, 100).mapPar(4)(_ * 2).runCollect
      val expectedSum = 2 * (0 until 100).sum

      assertTrue(result.exists(_.toList.sum == expectedSum))
    },
    test("mapPar(1) has same elements as map") {
      val par = Stream.range(0, 100).mapPar(1)(_ * 2).runCollect
      val seq = Stream.range(0, 100).map(_ * 2).runCollect

      assertTrue(
        par match {
          case Right(p) =>
            seq match {
              case Right(s) => p.toSet == s.toSet
              case Left(_)  => false
            }
          case Left(_) => false
        }
      )
    },
    test("empty stream") {
      val empty: Stream[Nothing, Int] = Stream.empty
      val result                      = empty.mapPar(4)(identity).runCollect
      assertTrue(result == Right(Chunk.empty))
    },
    test("error in f propagates") {
      val result = Try {
        Stream
          .range(0, 100)
          .mapPar(4)(i => if (i == 50) throw new RuntimeException("boom") else i)
          .runCollect
      }

      assertTrue(result.isFailure)
    },
    test("early termination with take(5) completes") {
      val result = Stream.range(0, 100000).mapPar(4)(_ * 2).take(5).runCollect
      assertTrue(result.exists(_.length == 5))
    },
    test("null element preservation") {
      val result = Stream("a", null.asInstanceOf[String], "b").mapPar(4)(identity).runCollect
      assertTrue(result.exists(_.toList.contains(null)))
    },
    suite("regressions")(
      test("mapPar_completeness [AdversarialConcurrencyConvergence]") {
        val got = Stream.range(0, 100).mapPar(4)(_ * 2).runCollect
        assertTrue(got.map(_.toSet) == Right((0 until 100).map(_ * 2).toSet))
      },
      test("mapPar_int_lane [AdversarialConcurrencyConvergence]") {
        val got = Stream.range(0, 50).mapPar(3)(_ + 1).runCollect
        assertTrue(got.map(_.toSet) == Right((1 to 50).toSet))
      },
      test("mapPar_defect_propagates [AdversarialConcurrencyConvergence]") {
        val boom = new RuntimeException("defect")
        val got  = Try(Stream.range(0, 20).mapPar(4)(x => if (x == 5) throw boom else x).runCollect)
        assertTrue(got.isFailure)
      },
      test("mapPar_take_closesUpstreamOnce [AdversarialConcurrencyConvergence]") {
        val closes = new AtomicInteger(0)
        val s      = Stream
          .fromAcquireRelease((), (_: Unit) => { closes.incrementAndGet(); () })(_ => Stream.range(0, 1000))
          .mapPar(4)(_ + 1)
          .take(5)
        val r = s.runCollect
        assertTrue(r.map(_.length) == Right(5)) && assertTrue(closes.get() == 1)
      },
      test("mapPar_long_maxvalue_completeness [AdversarialConcurrencyConvergence]") {
        val got = Stream(1L, Long.MaxValue, Long.MinValue, 0L).mapPar(2)(x => x).runCollect
        assertTrue(got.map(_.toSet) == Right(Set(1L, Long.MaxValue, Long.MinValue, 0L)))
      },
      test("mapPar_double_completeness [AdversarialConcurrencyConvergence]") {
        val got = Stream(1.0, Double.MaxValue, Double.NaN, 0.0).mapPar(2)(x => x).runCollect
        assertTrue(got.map(c => c.toList.count(_.isNaN)) == Right(1)) &&
        assertTrue(got.map(c => c.toList.filterNot(_.isNaN).toSet) == Right(Set(1.0, Double.MaxValue, 0.0)))
      },
      test("mapPar preserves the multiset and n=1 preserves order [AdversarialConcurrentLifecycleConvergence]") {
        val in  = (1 to 50).toList
        val par = Stream.fromIterable(in).mapPar(4)(_ * 2).runCollect.map(_.toList.sorted)
        val ord = Stream.fromIterable((1 to 20).toList).mapPar(1)(_ + 1).runCollect
        assertTrue(par == Right(in.map(_ * 2).sorted)) &&
        assertTrue(ord == Right(Chunk.fromIterable((1 to 20).map(_ + 1))))
      },
      test(
        "mapPar early-termination via take releases the source (no leak) [AdversarialConcurrentLifecycleConvergence]"
      ) {
        val opens  = new AtomicInteger(0)
        val closes = new AtomicInteger(0)
        val got    = managed(opens, closes, 1000).mapPar(4)(_ * 2).take(5).runCollect
        assertTrue(got.map(_.length) == Right(5)) && assertTrue(opens.get() == closes.get())
      },
      // The primitive-lane mapPar coordinators pull the upstream through the
      // bulk array reads (readInts/readLongs/...). ConcatReader's bulk reads
      // advance past an exhausted segment only ONCE (unlike its fixed, looped
      // readUpToN), so an empty MIDDLE concat segment makes the coordinator see
      // a premature EOF and every element after the empty segment is silently
      // dropped. Control oracle: the sequential scalar path is correct.
      test("mapPar_int_concatWithEmptyMiddle_losesNoElements [AdversarialConcatBulkReadSpec]") {
        val control = (Stream(1, 2) ++ Stream.empty ++ Stream(3)).runCollect
        assertTrue(control == Right(Chunk(1, 2, 3))) && {
          val got = (Stream(1, 2) ++ Stream.empty ++ Stream(3)).mapPar(2)(x => x).runCollect
          assertTrue(got.map(_.toList.sorted) == Right(List(1, 2, 3)))
        }
      },
      test("mapPar_long_concatWithEmptyMiddle_losesNoElements [AdversarialConcatBulkReadSpec]") {
        val got = (Stream(1L, 2L) ++ Stream.empty ++ Stream(3L)).mapPar(2)(x => x).runCollect
        assertTrue(got.map(_.toList.sorted) == Right(List(1L, 2L, 3L)))
      },
      // Lossless errors (Principle 4): an `ensuring` finalizer failure upstream
      // of `mapPar` must propagate as a thrown defect, exactly as the sequential
      // `map` equivalent does (control). On JVM the mapPar coordinator closes
      // the upstream in its `finally` and swallows the throwable
      // (`catch { case _: Throwable => () }` in *ConcurrentMapParReader),
      // silently discarding the finalizer failure (JS propagates it).
      test("mapPar_upstreamCloseFailure_propagates [AdversarialCloseFailureSwallowSpec]") {
        val control =
          Try(Stream(1, 2).ensuring(throw new RuntimeException("close-boom")).map(_ + 1).runDrain)
        assertTrue(control.isFailure) && {
          val got =
            Try(Stream(1, 2).ensuring(throw new RuntimeException("close-boom")).mapPar(2)(_ + 1).runDrain)
          assertTrue(got.isFailure)
        }
      },
      test("null elements survive map/chunked/sliding [AdversarialConcurrentLifecycleConvergence]") {
        val in = List[String]("a", null, "b", null)
        assertTrue(Stream.fromIterable(in).map(x => x).runCollect == Right(Chunk.fromIterable(in))) &&
        assertTrue(
          Stream.fromIterable(in).chunked(2).runCollect ==
            Right(Chunk(Chunk[String]("a", null), Chunk[String]("b", null)))
        ) &&
        assertTrue(
          Stream.fromIterable(in).sliding(2).runCollect ==
            Right(
              Chunk(Chunk[String]("a", null), Chunk[String](null, "b"), Chunk[String]("b", null))
            )
        )
      }
    )
  ) @@ TestAspect.sequential

  // =========================================================================
  //  mergeAll
  // =========================================================================

  private val mergeAllSuite = suite("mergeAll")(
    test("basic mergeAll: all elements from all inner streams are present") {
      val streams = Stream(
        Stream.range(0, 100),
        Stream.range(100, 200),
        Stream.range(200, 300)
      )

      val result = Stream.mergeAll(3)(streams).runCollect

      assertTrue(
        result match {
          case Right(values) =>
            val set = values.toSet
            set.size == 300 && (0 until 300).forall(set.contains)
          case Left(_) => false
        }
      )
    },
    test("empty outer stream") {
      val emptyOuter: Stream[Nothing, Stream[Nothing, Int]] = Stream.empty
      val result                                            = Stream.mergeAll(4)(emptyOuter).runCollect
      assertTrue(result == Right(Chunk.empty))
    },
    test("single inner stream") {
      val result = Stream.mergeAll(1)(Stream(Stream(1, 2, 3))).runCollect
      assertTrue(result == Right(Chunk(1, 2, 3)))
    },
    test("maxOpen larger than number of inner streams") {
      val streams = Stream(
        Stream.range(0, 10),
        Stream.range(10, 20),
        Stream.range(20, 30)
      )

      val result = Stream.mergeAll(100)(streams).runCollect

      assertTrue(
        result match {
          case Right(values) =>
            val set = values.toSet
            set.size == 30 && (0 until 30).forall(set.contains)
          case Left(_) => false
        }
      )
    },
    test("error propagation from inner stream") {
      val streams: Stream[Nothing, Stream[String, Int]] = Stream(
        Stream(1, 2),
        Stream.fail("boom"),
        Stream(3, 4)
      )

      val result = Stream.mergeAll(3)(streams).runCollect
      assertTrue(result == Left("boom"))
    },
    test("early termination with take(5) completes") {
      val streams = Stream(
        Stream.range(0, 100000),
        Stream.range(100000, 200000),
        Stream.range(200000, 300000)
      )

      val result = Stream.mergeAll(3)(streams).take(5).runCollect
      assertTrue(result.exists(_.length == 5))
    },
    suite("regressions")(
      test("mergeAll_completeness [AdversarialConcurrencyConvergence]") {
        val streams = Stream(Stream(1, 2, 3), Stream(4, 5, 6), Stream(7, 8, 9))
        val got     = Stream.mergeAll(2)(streams).runCollect
        assertTrue(got.map(_.toSet) == Right(Set(1, 2, 3, 4, 5, 6, 7, 8, 9)))
      },
      test(
        "mergeAll early-termination via take releases every opened inner (no leak) [AdversarialConcurrentLifecycleConvergence]"
      ) {
        val opens  = new AtomicInteger(0)
        val closes = new AtomicInteger(0)
        val inners = Stream(managed(opens, closes, 1000), managed(opens, closes, 1000), managed(opens, closes, 1000))
        val got    = Stream.mergeAll(3)(inners).take(5).runCollect
        assertTrue(got.map(_.length) == Right(5)) && assertTrue(opens.get() == closes.get())
      },
      // Lossless errors (Principle 4): an inner stream's `ensuring` finalizer
      // failure must propagate as a thrown defect, exactly as the sequential
      // flatten (`flattenAll`) equivalent does (control). Both the JVM
      // ConcurrentMergeReader drainer and the JS sequential merge reader close
      // the exhausted inner with `catch { case _: Throwable => () }`, silently
      // discarding the finalizer failure on BOTH platforms.
      test("mergeAll_innerCloseFailure_propagates [AdversarialCloseFailureSwallowSpec]") {
        def inner(): Stream[Nothing, Int] =
          Stream(1, 2).ensuring(throw new RuntimeException("inner-close-boom"))
        val control = Try(Stream.flattenAll(Stream(inner())).runDrain)
        assertTrue(control.isFailure) && {
          val got = Try(Stream.mergeAll(1)(Stream(inner())).runDrain)
          assertTrue(got.isFailure)
        }
      }
    )
  ) @@ TestAspect.sequential

  // =========================================================================
  //  flatMapPar
  // =========================================================================

  private val flatMapParSuite = suite("flatMapPar")(
    test("flatMapPar basic") {
      val result   = Stream.range(0, 10).flatMapPar(4)(i => Stream(i, i * 10)).runCollect
      val expected = Chunk.fromIterable(
        (0 until 10).flatMap(i => List(i, i * 10))
      )

      assertTrue(result.exists(values => values.length == 20 && values.toList.sorted == expected.toList.sorted))
    },
    test("flatMapPar equals mergeAll(n)(map(f))") {
      val source = Stream.range(0, 50)
      val f      = (i: Int) => Stream(i, i + 1000)

      val par       = source.flatMapPar(8)(f).runCollect
      val desugared = Stream.mergeAll(8)(source.map(f)).runCollect

      assertTrue(
        par match {
          case Right(p) =>
            desugared match {
              case Right(m) => p.toList.sorted == m.toList.sorted
              case Left(_)  => false
            }
          case Left(_) => false
        }
      )
    },
    suite("regressions")(
      test("flatMapPar_completeness [AdversarialConcurrencyConvergence]") {
        val got = Stream.range(0, 100).flatMapPar(4)(x => Stream(x, x + 1000)).runCollect
        val exp = (0 until 100).flatMap(x => List(x, x + 1000)).toSet
        assertTrue(got.map(_.toSet) == Right(exp))
      },
      test("flatMapPar_error_surfaces [AdversarialConcurrencyConvergence]") {
        val got = Stream
          .range(0, 20)
          .flatMapPar(4)(x => if (x == 7) Stream.fail("boom") else Stream(x))
          .runCollect
        assertTrue(got == Left("boom"))
      },
      test("flatMapPar_empty_inners [AdversarialConcurrencyConvergence]") {
        val got = Stream.range(0, 10).flatMapPar(3)(_ => (Stream.empty: Stream[Nothing, Int])).runCollect
        assertTrue(got == Right(Chunk.empty[Int]))
      },
      test("flatMapPar_long_lane_completeness [AdversarialConcurrencyConvergence]") {
        val got = Stream.range(0, 50).flatMapPar(4)(x => Stream(x.toLong, Long.MaxValue)).runCollect
        val exp = (0 until 50).flatMap(x => List(x.toLong, Long.MaxValue)).groupBy(identity).map { case (k, v) =>
          (k, v.size)
        }
        assertTrue(got.map(_.toList.groupBy(identity).map { case (k, v) => (k, v.size) }) == Right(exp))
      },
      test("flatMapPar and mergeAll preserve the multiset [AdversarialConcurrentLifecycleConvergence]") {
        val fmp = Stream
          .fromIterable((1 to 10).toList)
          .flatMapPar(3)(x => Stream(x, x * 10))
          .runCollect
          .map(_.toList.sorted)
        val ma = Stream.mergeAll(2)(Stream(Stream(1, 2, 3), Stream(4, 5), Stream(6))).runCollect.map(_.toList.sorted)
        assertTrue(fmp == Right((1 to 10).flatMap(x => List(x, x * 10)).sorted.toList)) &&
        assertTrue(ma == Right(List(1, 2, 3, 4, 5, 6)))
      },
      test(
        "flatMapPar early-termination via take releases every opened inner (no leak) [AdversarialConcurrentLifecycleConvergence]"
      ) {
        val opens  = new AtomicInteger(0)
        val closes = new AtomicInteger(0)
        val got    = Stream.range(0, 4).flatMapPar(4)(_ => managed(opens, closes, 1000)).take(5).runCollect
        assertTrue(got.map(_.length) == Right(5)) && assertTrue(opens.get() == closes.get())
      }
    )
  ) @@ TestAspect.sequential

  // =========================================================================
  //  concurrent laws
  // =========================================================================

  private val concurrentLawsSuite = suite("concurrent laws")(
    test("mergeAll(1) ≡ flatMap(identity) as sets") {
      check(Gen.listOfBounded(0, 5)(Gen.listOfBounded(0, 5)(Gen.int(0, 100)))) { lists =>
        ZIO.attemptBlocking {
          val chunks          = Chunk.fromIterable(lists.map(Chunk.fromIterable(_)))
          val streamOfStreams = Stream.fromChunk(chunks.map(Stream.fromChunk(_)))

          val merged = Stream
            .mergeAll(1)(streamOfStreams)
            .runCollect
            .fold(_ => Chunk.empty[Int], identity)
            .toSet

          val flatMapped = Stream
            .fromChunk(chunks.map(Stream.fromChunk(_)))
            .flatMap(identity)
            .runCollect
            .fold(_ => Chunk.empty[Int], identity)
            .toSet

          assertTrue(merged == flatMapped)
        }
      }
    },
    test("mapPar(1)(f) ≡ map(f) as sets") {
      check(Gen.listOf(Gen.int(0, 100))) { values =>
        ZIO.attemptBlocking {
          val chunk  = Chunk.fromIterable(values)
          val stream = Stream.fromChunk(chunk)

          val byMapPar = stream.mapPar(1)(_ * 2).runCollect.fold(_ => Chunk.empty[Int], identity).toSet
          val byMap    = stream.map(_ * 2).runCollect.fold(_ => Chunk.empty[Int], identity).toSet

          assertTrue(byMapPar == byMap)
        }
      }
    },
    test("flatMapPar(n)(f) ≡ mergeAll(n)(map(f)) as sets") {
      check(Gen.listOf(Gen.int(0, 10))) { values =>
        ZIO.attemptBlocking {
          val chunk  = Chunk.fromIterable(values)
          val stream = Stream.fromChunk(chunk)
          val f      = (i: Int) => Stream(i, i * 10)

          val byFlatMapPar = stream.flatMapPar(4)(f).runCollect.fold(_ => Chunk.empty[Int], identity).toSet
          val byMergeAll   = Stream.mergeAll(4)(stream.map(f)).runCollect.fold(_ => Chunk.empty[Int], identity).toSet

          assertTrue(byFlatMapPar == byMergeAll)
        }
      }
    }
  ) @@ TestAspect.sequential @@ TestAspect.timeout(120.seconds) @@ TestAspect.timed

  // =========================================================================
  //  jvmType propagation
  // =========================================================================

  private val jvmTypePropagationSuite = suite("jvmType propagation")(
    test("mapPar Int=>Int reader propagates JvmType.Int (#5 regression)") {
      val reader = Stream.range(0, 10).mapPar(2)(identity).compile(0, Stream.DefaultBufferSize)
      try assertTrue(reader.jvmType eq JvmType.Int)
      finally reader.close()
    },
    test("mapPar Int=>Long reader propagates JvmType.Long") {
      val reader = Stream.range(0, 10).mapPar(2)(_.toLong).compile(0, Stream.DefaultBufferSize)
      try assertTrue(reader.jvmType eq JvmType.Long)
      finally reader.close()
    },
    test("mapPar Int=>String reader propagates JvmType.AnyRef") {
      val reader = Stream.range(0, 10).mapPar(2)(_.toString).compile(0, Stream.DefaultBufferSize)
      try assertTrue(reader.jvmType eq JvmType.AnyRef)
      finally reader.close()
    }
  )
}

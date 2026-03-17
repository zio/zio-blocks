package zio.blocks.streams

import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
import zio.test._
import zio.test.Assertion._
import StreamsGen._

/**
 * Comprehensive tests for [[Stream#repeated]] — the combinator that restarts a
 * stream from the beginning each time it completes cleanly.
 *
 * Covers:
 *   - basic repetition semantics (single element, multiple elements)
 *   - empty stream repetition
 *   - composed pipelines (concat + map + filter) with repeated
 *   - repeated + take (to prevent infinite)
 *   - nested repeats (repeated.repeated)
 *   - repeated after flatMap
 *   - repeated with resource management (ensuring)
 *   - repeated + error handling (catchAll, catchDefect)
 *   - repeated + drop
 *   - element order preservation across repetitions
 *   - property: first N elements of s.repeated == (s ++ s ++ ... ++ s).take(N)
 */
object StreamRepeatSpec extends StreamsBaseSpec {

  private def collect[A](s: Stream[Nothing, A]): Chunk[A] =
    s.runCollect.fold(_ => Chunk.empty, identity)

  def spec: Spec[TestEnvironment, Any] = suite("Stream#repeated")(
    // ---- Basic repetition ---------------------------------------------------

    suite("basic repetition")(
      test("single element stream repeats that element") {
        val result = Stream.succeed(42).repeated.take(5).runCollect
        assert(result)(equalTo(Right(Chunk(42, 42, 42, 42, 42))))
      },
      test("multi-element stream repeats the full sequence") {
        val result = Stream.fromIterable(List(1, 2, 3)).repeated.take(9).runCollect
        assert(result)(equalTo(Right(Chunk(1, 2, 3, 1, 2, 3, 1, 2, 3))))
      },
      test("take fewer than one full cycle") {
        val result = Stream.fromIterable(List(1, 2, 3)).repeated.take(2).runCollect
        assert(result)(equalTo(Right(Chunk(1, 2))))
      },
      test("take exactly one full cycle") {
        val result = Stream.fromIterable(List(1, 2, 3)).repeated.take(3).runCollect
        assert(result)(equalTo(Right(Chunk(1, 2, 3))))
      },
      test("take across cycle boundary") {
        val result = Stream.fromIterable(List(1, 2, 3)).repeated.take(7).runCollect
        assert(result)(equalTo(Right(Chunk(1, 2, 3, 1, 2, 3, 1))))
      },
      test("range stream repeats correctly") {
        val result = Stream.range(0, 3).repeated.take(9).runCollect
        assert(result)(equalTo(Right(Chunk(0, 1, 2, 0, 1, 2, 0, 1, 2))))
      },
      test("fromChunk stream repeats correctly") {
        val result = Stream.fromChunk(Chunk(10, 20)).repeated.take(6).runCollect
        assert(result)(equalTo(Right(Chunk(10, 20, 10, 20, 10, 20))))
      },
      test("unfold stream repeats correctly") {
        val nats3  = Stream.unfold(0)(n => if (n < 3) Some((n, n + 1)) else None)
        val result = nats3.repeated.take(9).runCollect
        assert(result)(equalTo(Right(Chunk(0, 1, 2, 0, 1, 2, 0, 1, 2))))
      }
    ),

    // ---- Empty stream -------------------------------------------------------

    suite("empty stream")(
      test("repeated empty stream with take(0) yields empty") {
        val result = Stream.empty.repeated.take(0).runCollect
        assert(result)(equalTo(Right(Chunk.empty)))
      }
    ),

    // ---- Composed pipelines -------------------------------------------------

    suite("composed pipelines")(
      test("map then repeated") {
        val result = Stream.fromIterable(List(1, 2, 3)).map(_ * 10).repeated.take(6).runCollect
        assert(result)(equalTo(Right(Chunk(10, 20, 30, 10, 20, 30))))
      },
      test("repeated then map") {
        val result = Stream.fromIterable(List(1, 2, 3)).repeated.map(_ * 10).take(6).runCollect
        assert(result)(equalTo(Right(Chunk(10, 20, 30, 10, 20, 30))))
      },
      test("filter then repeated") {
        // filter evens from [1,2,3,4] = [2,4], repeated
        val result = Stream.fromIterable(List(1, 2, 3, 4)).filter(_ % 2 == 0).repeated.take(6).runCollect
        assert(result)(equalTo(Right(Chunk(2, 4, 2, 4, 2, 4))))
      },
      test("repeated then filter") {
        // repeat [1,2,3], then filter evens → [2, 2, 2, ...]
        val result = Stream.fromIterable(List(1, 2, 3)).repeated.filter(_ % 2 == 0).take(4).runCollect
        assert(result)(equalTo(Right(Chunk(2, 2, 2, 2))))
      },
      test("concat then repeated replays all segments on each cycle") {
        // ConcatReader resets all segments: each cycle replays 1,2,3
        val s      = Stream.fromIterable(List(1, 2)) ++ Stream.fromIterable(List(3))
        val result = s.repeated.take(9).runCollect
        assert(result)(equalTo(Right(Chunk(1, 2, 3, 1, 2, 3, 1, 2, 3))))
      },
      test("concat + map + filter with repeated replays all segments") {
        // (1,2) ++ (3,4) → map *2 → (2,4,6,8) → filter even → all pass
        // On repeat, all segments reset → full cycle: 2,4,6,8
        val s = (Stream.fromIterable(List(1, 2)) ++ Stream.fromIterable(List(3, 4)))
          .map(_ * 2)
          .filter(_ % 2 == 0)
          .repeated
          .take(8)
        val result = s.runCollect
        assert(result)(equalTo(Right(Chunk(2, 4, 6, 8, 2, 4, 6, 8))))
      },
      test("repeated with drop") {
        // [1,2,3].repeated.drop(2) skips 1,2 → 3,1,2,3,1,2,3,...
        val result = Stream.fromIterable(List(1, 2, 3)).repeated.drop(2).take(7).runCollect
        assert(result)(equalTo(Right(Chunk(3, 1, 2, 3, 1, 2, 3))))
      },
      test("repeated with drop larger than one cycle") {
        // [1,2,3].repeated.drop(5) skips 1,2,3,1,2 → 3,1,2,3,...
        val result = Stream.fromIterable(List(1, 2, 3)).repeated.drop(5).take(4).runCollect
        assert(result)(equalTo(Right(Chunk(3, 1, 2, 3))))
      },
      test("repeated with takeWhile") {
        // [1,2,3].repeated → takeWhile(_ < 3) → stops at first 3: [1,2]
        val result = Stream.fromIterable(List(1, 2, 3)).repeated.takeWhile(_ < 3).runCollect
        assert(result)(equalTo(Right(Chunk(1, 2))))
      }
    ),

    // ---- Nested repeats -----------------------------------------------------

    suite("nested repeats")(
      test("repeated.repeated behaves like repeated (take terminates)") {
        val single = collect(Stream.fromIterable(List(1, 2)).repeated.take(8))
        val nested = collect(Stream.fromIterable(List(1, 2)).repeated.repeated.take(8))
        assert(nested)(equalTo(single))
      },
      test("nested repeat element order is preserved") {
        val result = Stream.fromIterable(List(1, 2, 3)).repeated.repeated.take(9).runCollect
        assert(result)(equalTo(Right(Chunk(1, 2, 3, 1, 2, 3, 1, 2, 3))))
      }
    ),

    // ---- Repeat after flatMap -----------------------------------------------

    suite("repeat after flatMap")(
      test("flatMap then take works for finite case") {
        // flatMap produces a finite inner stream per element; repeat restarts the outer
        // But flatMap reader doesn't support reset, so this should throw UnsupportedOperationException
        val result =
          try {
            Stream
              .fromIterable(List(1, 2))
              .flatMap(i => Stream.succeed(i * 10))
              .repeated
              .take(6)
              .runCollect
          } catch {
            case _: UnsupportedOperationException => Left("unsupported")
          }
        assert(result)(equalTo(Left("unsupported")))
      }
    ),

    // ---- Resource management ------------------------------------------------

    suite("resource management")(
      test("ensuring finalizer runs when repeated stream is closed via take") {
        var count  = 0
        val result = Stream
          .fromIterable(List(1, 2))
          .ensuring(count += 1)
          .repeated
          .take(6)
          .runCollect
        assert(result)(equalTo(Right(Chunk(1, 2, 1, 2, 1, 2)))) &&
        assertTrue(count == 1)
      },
      test("ensuring finalizer runs when repeated stream is closed early via take") {
        var finalized = false
        val result    = Stream
          .fromIterable(List(1, 2, 3))
          .ensuring { finalized = true }
          .repeated
          .take(2)
          .runCollect
        assert(result)(equalTo(Right(Chunk(1, 2)))) &&
        assertTrue(finalized)
      },
      test("ensuring works on non-repeated stream (sanity check)") {
        var finalized = false
        val result    = Stream
          .fromIterable(List(1, 2, 3))
          .ensuring { finalized = true }
          .runCollect
        assert(result)(equalTo(Right(Chunk(1, 2, 3)))) &&
        assertTrue(finalized)
      }
    ),

    // ---- Error handling -----------------------------------------------------

    suite("error handling")(
      test("repeated stream that fails propagates error") {
        // fail(e).repeated should propagate the error (not silently restart)
        val result = Stream.fail("boom").repeated.take(3).runCollect
        assert(result)(equalTo(Left("boom")))
      },
      test("repeated stream where error occurs mid-stream propagates error") {
        val s      = (Stream.fromIterable(List(1, 2)) ++ Stream.fail("err")).repeated.take(10)
        val result = s.runCollect
        assert(result)(equalTo(Left("err")))
      },
      test("catchAll after repeated catches repeated stream error") {
        val result = (Stream.fromIterable(List(1, 2)) ++ Stream.fail("err")).repeated
          .take(10)
          .catchAll((_: String) => Stream.succeed(99))
          .runCollect
        assert(result)(equalTo(Right(Chunk(1, 2, 99))))
      },
      test("catchDefect after repeated catches defect") {
        val defectStream = Stream.fromReader[Nothing, Int](new Reader[Int] {
          private var emitted                   = 0
          def isClosed: Boolean                 = false
          def read[A1 >: Int](sentinel: A1): A1 = {
            emitted += 1
            if (emitted <= 2) Int.box(emitted).asInstanceOf[A1]
            else throw new RuntimeException("defect")
          }
          def close(): Unit = ()
        })
        val result = defectStream.repeated
          .take(10)
          .catchDefect { case _: RuntimeException => Stream.succeed(99) }
          .runCollect
        assert(result)(equalTo(Right(Chunk(1, 2, 99))))
      }
    ),

    // ---- Element order preservation -----------------------------------------

    suite("element order preservation")(
      test("elements repeat in original order across cycles") {
        val data     = List(10, 20, 30, 40, 50)
        val result   = collect(Stream.fromIterable(data).repeated.take(15))
        val expected = Chunk.fromIterable(
          List.fill(3)(data).flatten
        )
        assert(result)(equalTo(expected))
      },
      test("string elements repeat in order") {
        val data   = List("a", "b", "c")
        val result = collect(Stream.fromIterable(data).repeated.take(9))
        assert(result)(equalTo(Chunk.fromIterable(List("a", "b", "c", "a", "b", "c", "a", "b", "c"))))
      }
    ),

    // ---- Property: s.repeated.take(N) == (s ++ s ++ ...).take(N) -----------

    suite("property: repeated ≡ concat repetitions")(
      test("first N elements of s.repeated == (s ++ s ++ ... ++ s).take(N)") {
        check(Gen.chunkOfBounded(1, 10)(genInt), Gen.int(1, 30)) { (zChunk, n) =>
          val chunk          = Chunk.fromIterable(zChunk)
          val repeatedResult = collect(Stream.fromChunk(chunk).repeated.take(n))
          // Build the expected by manual concatenation
          val copies       = (n / math.max(chunk.length, 1)) + 1
          val concatStream = (0 until copies).foldLeft(Stream.empty: Stream[Nothing, Int]) { (acc, _) =>
            acc ++ Stream.fromChunk(chunk)
          }
          val concatResult = collect(concatStream.take(n))
          assert(repeatedResult)(equalTo(concatResult))
        }
      },
      test("property with varying chunk sizes") {
        check(Gen.chunkOfBounded(1, 5)(genInt), Gen.int(1, 20)) { (zChunk, n) =>
          val chunk          = Chunk.fromIterable(zChunk)
          val repeatedResult = collect(Stream.fromChunk(chunk).repeated.take(n))
          // verify length
          val expectedLen = math.min(n, Int.MaxValue).toInt
          assertTrue(repeatedResult.length == expectedLen) &&
          // verify cycling: element at index i == chunk(i % chunk.length)
          assertTrue(repeatedResult.toList.zipWithIndex.forall { case (elem, i) =>
            elem == chunk(i % chunk.length)
          })
        }
      }
    ),

    // ---- Stream.repeat(a) (infinite single value) --------------------------

    suite("Stream.repeat companion")(
      test("Stream.repeat(a) emits infinite a's (take first 5)") {
        assert(Stream.repeat(7).take(5).runCollect)(equalTo(Right(Chunk(7, 7, 7, 7, 7))))
      },
      test("Stream.repeat(a).map(f).take(n) works") {
        assert(Stream.repeat(3).map(_ * 2).take(4).runCollect)(equalTo(Right(Chunk(6, 6, 6, 6))))
      },
      test("Stream.repeat(a).filter(pred).take(n) works") {
        // filter always true — should emit the value
        assert(Stream.repeat(5).filter(_ > 0).take(3).runCollect)(equalTo(Right(Chunk(5, 5, 5))))
      },
      test("Stream.repeat(a).drop(n).take(m) works") {
        assert(Stream.repeat(1).drop(100).take(3).runCollect)(equalTo(Right(Chunk(1, 1, 1))))
      }
    ),

    // ---- Edge cases ---------------------------------------------------------

    suite("edge cases")(
      test("take(0) on repeated yields empty") {
        assert(Stream.fromIterable(List(1, 2, 3)).repeated.take(0).runCollect)(
          equalTo(Right(Chunk.empty))
        )
      },
      test("repeated with take(1) yields first element") {
        assert(Stream.fromIterable(List(10, 20, 30)).repeated.take(1).runCollect)(
          equalTo(Right(Chunk(10)))
        )
      },
      test("single-element chunk repeated many cycles") {
        val result = Stream.fromChunk(Chunk(42)).repeated.take(100).runCollect
        assert(result)(equalTo(Right(Chunk.fromIterable(List.fill(100)(42)))))
      },
      test("large chunk repeated a few cycles") {
        val data     = Chunk.fromIterable(1 to 50)
        val result   = collect(Stream.fromChunk(data).repeated.take(150))
        val expected = Chunk.fromIterable(List.fill(3)((1 to 50).toList).flatten)
        assert(result)(equalTo(expected))
      }
    )
  )
}

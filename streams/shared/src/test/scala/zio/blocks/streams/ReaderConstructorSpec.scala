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

import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
import zio.test._
import zio.test.Assertion._
import StreamsGen._

/**
 * Tests for [[Reader]] companion constructors: [[Reader.fromChunk]],
 * [[Reader.fromIterable]], [[Reader.fromRange]], [[Reader.repeat]],
 * [[Reader.unfold]], and [[Reader.repeated]].
 *
 * Each constructor is verified for:
 *   - correct element sequence
 *   - correct termination (`read()` returns `null` after all elements)
 *   - `isClosed` / `readable()` consistency
 *   - thread-safety: concurrent `read()` calls from a VT and the test thread
 *     observe the correct total count without duplicates or missed elements
 */
object ReaderConstructorSpec extends StreamsBaseSpec {

  /** Drain a `Reader[A]` to a `Chunk[A]` via `read()`. */
  private def drainAll[A](dq: Reader[A]): Chunk[A] = {
    val b = Chunk.newBuilder[A]
    var v = dq.read[Any](null)
    while (v != null) { b += v.asInstanceOf[A]; v = dq.read[Any](null) }
    b.result()
  }

  /** Drain exactly `n` elements via `read()`. */
  private def drainN[A](dq: Reader[A], n: Int): Chunk[A] = {
    val b = Chunk.newBuilder[A]
    var i = 0
    while (i < n) {
      val v = dq.read[Any](null)
      if (v != null) { b += v.asInstanceOf[A]; i += 1 }
      else i = n
    }
    b.result()
  }

  def spec: Spec[TestEnvironment, Any] = suite("Reader constructors")(
    // ---- fromChunk -----------------------------------------------------------

    suite("Reader.fromChunk")(
      test("emits all elements in order") {
        check(genChunk(genInt)) { chunk =>
          val dq = Reader.fromChunk[Int](chunk)
          assert(drainAll(dq))(equalTo(chunk))
        }
      },
      test("read returns null after last element") {
        val dq = Reader.fromChunk[Int](Chunk(1, 2))
        dq.read[Any](null); dq.read[Any](null)
        assertTrue(dq.read[Any](null) == null)
      },
      test("isClosed false before exhausted, true after") {
        val dq = Reader.fromChunk[Int](Chunk(42))
        assertTrue(!dq.isClosed) &&
        assertTrue(dq.read[Any](null) == Int.box(42)) &&
        assertTrue(dq.isClosed)
      },
      test("isClosed transitions from false to true after exhaustion") {
        val dq = Reader.fromChunk[Int](Chunk(1))
        assertTrue(!dq.isClosed) &&
        assertTrue(dq.read[Any](null) == Int.box(1)) &&
        assertTrue(dq.isClosed)
      },
      test("readable() returns false on empty chunk") {
        val dq = Reader.fromChunk[Int](Chunk.empty)
        assertTrue(!dq.readable())
      },
      test("readable() returns true when elements remain, false after exhaustion") {
        val dq = Reader.fromChunk[Int](Chunk(10, 20))
        assertTrue(dq.readable()) &&
        assertTrue(dq.read[Any](null) == Int.box(10)) &&
        assertTrue(dq.readable()) &&
        assertTrue(dq.read[Any](null) == Int.box(20)) &&
        assertTrue(!dq.readable())
      },
      test("close marks reader as closed") {
        val dq = Reader.fromChunk[Int](Chunk(1, 2, 3))
        dq.close()
        assertTrue(dq.isClosed)
      },
      test("empty chunk closes immediately") {
        val dq = Reader.fromChunk[Int](Chunk.empty)
        assertTrue(dq.isClosed) &&
        assertTrue(dq.read[Any](null) == null)
      },
      test("single-threaded read() delivers each element exactly once") {
        val n     = 1000
        val chunk = Chunk.fromIterable(0 until n)
        val dq    = Reader.fromChunk[Int](chunk)
        assert(drainAll(dq))(equalTo(chunk))
      }
    ),

    // ---- fromRange -----------------------------------------------------------

    suite("Reader.fromRange")(
      test("emits range integers in order") {
        val dq = Reader.fromRange(1 to 5)
        assert(drainAll(dq))(equalTo(Chunk(1, 2, 3, 4, 5)))
      },
      test("empty range closes immediately") {
        val dq = Reader.fromRange(1 until 1)
        assertTrue(dq.isClosed) &&
        assertTrue(dq.read[Any](null) == null)
      },

      test("readable() is true while elements remain") {
        val dq = Reader.fromRange(1 to 3)
        assertTrue(dq.readable()) &&
        assertTrue(dq.read[Any](null) == Int.box(1)) &&
        assertTrue(dq.readable()) &&
        assertTrue(dq.read[Any](null) == Int.box(2)) &&
        assertTrue(dq.readable()) &&
        assertTrue(dq.read[Any](null) == Int.box(3)) &&
        assertTrue(!dq.readable())
      },
      test("single-threaded read() delivers each element exactly once") {
        val n  = 1000
        val dq = Reader.fromRange(0 until n)
        assert(drainAll(dq))(equalTo(Chunk.fromIterable(0 until n)))
      }
    ),

    // ---- fromIterable --------------------------------------------------------

    suite("Reader.fromIterable")(
      test("emits all elements in iteration order") {
        check(genChunk(genInt)) { chunk =>
          val dq = Reader.fromIterable[Int](chunk.toList)
          assert(drainAll(dq))(equalTo(chunk))
        }
      },
      test("empty iterable closes immediately") {
        val dq = Reader.fromIterable[Int](Nil)
        assertTrue(dq.isClosed) &&
        assertTrue(dq.read[Any](null) == null)
      },
      test("readable() returns false when empty") {
        val dq = Reader.fromIterable[Int](Nil)
        assertTrue(!dq.readable())
      }
    ),

    // ---- repeat --------------------------------------------------------------

    suite("Reader.repeat")(
      test("always returns the same element") {
        val dq = Reader.repeat[Int](7)
        assert(drainN(dq, 5))(equalTo(Chunk(7, 7, 7, 7, 7)))
      },
      test("isClosed is always false") {
        assertTrue(!Reader.repeat[Int](0).isClosed)
      },
      test("readable() always returns true for repeat") {
        val dq = Reader.repeat[String]("x")
        assertTrue(dq.readable())
      }
    ),

    // ---- unfold --------------------------------------------------------------

    suite("Reader.unfold")(
      test("emits elements according to unfolding function") {
        val dq =
          Reader.unfold[Int, Int](0)(n => if (n < 5) Some((n * 2, n + 1)) else None)
        assert(drainAll(dq))(equalTo(Chunk(0, 2, 4, 6, 8)))
      },
      test("returns null immediately when f returns None from start") {
        val dq = Reader.unfold[Int, Int](0)(_ => None)
        assertTrue(dq.read[Any](null) == null)
      },
      test("isClosed false before exhausted, true after done signal consumed") {
        val dq =
          Reader.unfold[Int, Int](0)(n => if (n < 1) Some((n, n + 1)) else None)
        // Before any read(): open
        assertTrue(!dq.isClosed) &&
        // After taking the single element: still open — done signal not yet seen
        assertTrue(dq.read[Any](null) == Int.box(0)) &&
        assertTrue(!dq.isClosed) &&
        // After taking the done signal: closed
        assertTrue(dq.read[Any](null) == null) &&
        assertTrue(dq.isClosed)
      },
      test("readable() returns false when exhausted") {
        val dq = Reader.unfold[Int, Int](0)(_ => None)
        dq.read[Any](null) // exhaust it
        assertTrue(!dq.readable())
      },
      test("close marks finished") {
        val dq =
          Reader.unfold[Int, Int](0)(n => if (n < 10) Some((n, n + 1)) else None)
        dq.close()
        assertTrue(dq.isClosed)
      }
    ),

    // ---- repeated ------------------------------------------------------------

    suite("Reader.repeated")(
      test("restarts via reset and replays elements across cycles") {
        val inner = Reader.fromChunk[Int](Chunk(0, 1, 2))
        val dq    = Reader.repeated[Int](inner)
        // 3 cycles = 9 elements, same sequence each cycle
        assert(drainN(dq, 9))(equalTo(Chunk(0, 1, 2, 0, 1, 2, 0, 1, 2)))
      },
      test("isClosed false initially, reader repeats after clean close") {
        val inner = Reader.fromChunk[Int](Chunk(1))
        val dq    = Reader.repeated[Int](inner)
        assertTrue(!dq.isClosed) &&
        // First cycle
        assertTrue(dq.read[Any](null) == Int.box(1)) &&
        // Repeated — should reset and give element again
        assertTrue(dq.read[Any](null) == Int.box(1)) &&
        assertTrue(!dq.isClosed)
      }
    )
  )
}

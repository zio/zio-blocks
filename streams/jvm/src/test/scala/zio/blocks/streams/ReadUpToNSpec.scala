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
import zio.test._

object ReadUpToNSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("Reader#readUpToN")(
    suite("FromChunk[String] (generic)")(
      test("readUpToN(0) returns empty chunk") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk("a", "b", "c")))
        assertTrue(reader.readUpToN[String](0) == Chunk.empty)
      },
      test("readUpToN(1) returns single element") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk("a", "b", "c")))
        assertTrue(reader.readUpToN[String](1) == Chunk("a"))
      },
      test("readUpToN(n < total) returns exactly n elements") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk("a", "b", "c", "d", "e")))
        assertTrue(reader.readUpToN[String](3) == Chunk("a", "b", "c"))
      },
      test("readUpToN(n > total) returns all remaining elements") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk("a", "b", "c")))
        assertTrue(reader.readUpToN[String](100) == Chunk("a", "b", "c"))
      },
      test("readUpToN on exhausted reader returns empty chunk") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk("a", "b")))
        val _      = reader.readUpToN[String](2)
        assertTrue(reader.readUpToN[String](5) == Chunk.empty)
      }
    ),
    suite("FromChunkInt")(
      test("readUpToN(0) returns empty chunk") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1, 2, 3)))
        assertTrue(reader.readUpToN[Int](0) == Chunk.empty)
      },
      test("readUpToN(1) returns single element") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1, 2, 3)))
        assertTrue(reader.readUpToN[Int](1) == Chunk(1))
      },
      test("readUpToN(n < total) returns exactly n elements") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1, 2, 3, 4, 5)))
        assertTrue(reader.readUpToN[Int](3) == Chunk(1, 2, 3))
      },
      test("readUpToN(n > total) returns all remaining elements") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1, 2, 3)))
        assertTrue(reader.readUpToN[Int](100) == Chunk(1, 2, 3))
      },
      test("readUpToN on exhausted reader returns empty chunk") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1, 2, 3)))
        val _      = reader.readUpToN[Int](3)
        assertTrue(reader.readUpToN[Int](1) == Chunk.empty)
      },
      test("successive readUpToN calls advance position") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(10, 20, 30, 40, 50)))
        val c1     = reader.readUpToN[Int](2)
        val c2     = reader.readUpToN[Int](2)
        val c3     = reader.readUpToN[Int](5)
        assertTrue(c1 == Chunk(10, 20)) &&
        assertTrue(c2 == Chunk(30, 40)) &&
        assertTrue(c3 == Chunk(50))
      }
    ),
    suite("FromChunkLong")(
      test("readUpToN(0) returns empty chunk") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1L, 2L, 3L)))
        assertTrue(reader.readUpToN[Long](0) == Chunk.empty)
      },
      test("readUpToN(1) returns single element") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1L, 2L, 3L)))
        assertTrue(reader.readUpToN[Long](1) == Chunk(1L))
      },
      test("readUpToN(n < total) returns exactly n elements") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(10L, 20L, 30L, 40L, 50L)))
        assertTrue(reader.readUpToN[Long](3) == Chunk(10L, 20L, 30L))
      },
      test("readUpToN(n > total) returns all remaining elements") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1L, 2L)))
        assertTrue(reader.readUpToN[Long](100) == Chunk(1L, 2L))
      },
      test("readUpToN on exhausted reader returns empty chunk") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1L, 2L)))
        val _      = reader.readUpToN[Long](2)
        assertTrue(reader.readUpToN[Long](1) == Chunk.empty)
      }
    ),
    suite("FromChunkDouble")(
      test("readUpToN(0) returns empty chunk") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0, 2.0, 3.0)))
        assertTrue(reader.readUpToN[Double](0) == Chunk.empty)
      },
      test("readUpToN(1) returns single element") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0, 2.0, 3.0)))
        assertTrue(reader.readUpToN[Double](1) == Chunk(1.0))
      },
      test("readUpToN(n < total) returns exactly n elements") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.5, 2.5, 3.5, 4.5)))
        assertTrue(reader.readUpToN[Double](2) == Chunk(1.5, 2.5))
      },
      test("readUpToN(n > total) returns all remaining elements") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0, 2.0)))
        assertTrue(reader.readUpToN[Double](100) == Chunk(1.0, 2.0))
      },
      test("readUpToN on exhausted reader returns empty chunk") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0, 2.0)))
        val _      = reader.readUpToN[Double](2)
        assertTrue(reader.readUpToN[Double](1) == Chunk.empty)
      }
    ),
    suite("FromChunkFloat")(
      test("readUpToN(0) returns empty chunk") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0f, 2.0f, 3.0f)))
        assertTrue(reader.readUpToN[Float](0) == Chunk.empty)
      },
      test("readUpToN(1) returns single element") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0f, 2.0f, 3.0f)))
        assertTrue(reader.readUpToN[Float](1) == Chunk(1.0f))
      },
      test("readUpToN(n < total) returns exactly n elements") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.1f, 2.2f, 3.3f, 4.4f)))
        assertTrue(reader.readUpToN[Float](2) == Chunk(1.1f, 2.2f))
      },
      test("readUpToN(n > total) returns all remaining elements") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0f, 2.0f)))
        assertTrue(reader.readUpToN[Float](100) == Chunk(1.0f, 2.0f))
      },
      test("readUpToN on exhausted reader returns empty chunk") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0f, 2.0f)))
        val _      = reader.readUpToN[Float](2)
        assertTrue(reader.readUpToN[Float](1) == Chunk.empty)
      }
    ),
    suite("FromRange")(
      test("readUpToN(0) returns empty chunk") {
        val reader = Stream.compileToReader(Stream.range(0, 10))
        assertTrue(reader.readUpToN[Int](0) == Chunk.empty)
      },
      test("readUpToN(1) returns single element") {
        val reader = Stream.compileToReader(Stream.range(0, 10))
        assertTrue(reader.readUpToN[Int](1) == Chunk(0))
      },
      test("readUpToN(n < total) returns at most n elements") {
        val reader = Stream.compileToReader(Stream.range(0, 10))
        val chunk  = reader.readUpToN[Int](5)
        assertTrue(chunk.length <= 5 && chunk.length >= 1)
      },
      test("readUpToN(n > total) returns all remaining elements") {
        val reader = Stream.compileToReader(Stream.range(0, 3))
        val chunk  = reader.readUpToN[Int](100)
        assertTrue(chunk == Chunk(0, 1, 2))
      },
      test("readUpToN on exhausted reader returns empty chunk") {
        val reader = Stream.compileToReader(Stream.range(0, 2))
        val _      = reader.readUpToN[Int](2)
        assertTrue(reader.readUpToN[Int](1) == Chunk.empty)
      },
      test("successive readUpToN calls consume range in order") {
        val reader = Stream.compileToReader(Stream.range(0, 7))
        val c1     = reader.readUpToN[Int](3)
        val c1len  = c1.length
        val c2     = reader.readUpToN[Int](3)
        val c2len  = c2.length
        val c3     = reader.readUpToN[Int](10)
        assertTrue(c1 == Chunk.fromIterable(0 until c1len)) &&
        assertTrue(c2 == Chunk.fromIterable(c1len until (c1len + c2len))) &&
        assertTrue((c1 ++ c2 ++ c3) == Chunk(0, 1, 2, 3, 4, 5, 6))
      }
    ),
    suite("Filtered (Int)")(
      test("readUpToN returns at most n filtered elements") {
        val reader = Stream.compileToReader(Stream.range(0, 20).filter(_ % 2 == 0))
        val chunk  = reader.readUpToN[Int](5)
        assertTrue(chunk.length <= 5 && chunk.length >= 1) &&
        assertTrue(chunk.toList.forall(_ % 2 == 0))
      },
      test("readUpToN on empty filter result returns empty") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1, 3, 5)).filter(_ % 2 == 0))
        assertTrue(reader.readUpToN[Int](5) == Chunk.empty)
      }
    ),
    suite("Mapped (Int)")(
      test("readUpToN returns at most n mapped elements") {
        val reader = Stream.compileToReader(Stream.range(0, 10).map(_ * 10))
        val chunk  = reader.readUpToN[Int](5)
        assertTrue(chunk.length <= 5 && chunk.length >= 1) &&
        assertTrue(chunk.toList.forall(_ % 10 == 0))
      },
      test("readUpToN on mapped reader successive calls consume in order") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1, 2, 3, 4, 5)).map(_ * 2))
        val c1     = reader.readUpToN[Int](3)
        val c2     = reader.readUpToN[Int](3)
        assertTrue((c1 ++ c2) == Chunk(2, 4, 6, 8, 10))
      }
    ),
    suite("ConcatReader (boundary spanning)")(
      test("readUpToN spans concatenation boundary") {
        val s1     = Stream.fromChunk(Chunk(1, 2, 3))
        val s2     = Stream.fromChunk(Chunk(4, 5, 6))
        val reader = Stream.compileToReader(s1 ++ s2)
        val c1     = reader.readUpToN[Int](3)
        val c2     = reader.readUpToN[Int](3)
        val c3     = reader.readUpToN[Int](3)
        assertTrue((c1 ++ c2 ++ c3) == Chunk(1, 2, 3, 4, 5, 6))
      },
      test("readUpToN returns at least one element from each call until exhausted") {
        val s1     = Stream.fromChunk(Chunk("a", "b"))
        val s2     = Stream.fromChunk(Chunk("c", "d"))
        val reader = Stream.compileToReader(s1 ++ s2)
        var all    = Chunk.empty: Chunk[String]
        var done   = false
        while (!done) {
          val chunk = reader.readUpToN[String](2)
          if (chunk.isEmpty) done = true
          else all = all ++ chunk
        }
        assertTrue(all == Chunk("a", "b", "c", "d"))
      }
    ),
    suite("Taken")(
      test("readUpToN respects take limit") {
        val reader = Stream.compileToReader(Stream.range(0, 100).take(5))
        var all    = Chunk.empty: Chunk[Int]
        var done   = false
        while (!done) {
          val chunk = reader.readUpToN[Int](10)
          if (chunk.isEmpty) done = true
          else all = all ++ chunk
        }
        assertTrue(all == Chunk(0, 1, 2, 3, 4))
      },
      test("readUpToN(3) on take(3) returns exactly 3 elements") {
        val reader = Stream.compileToReader(Stream.range(0, 100).take(3))
        val chunk  = reader.readUpToN[Int](3)
        assertTrue(chunk == Chunk(0, 1, 2))
      }
    ),
    suite("TakenWhile")(
      test("readUpToN respects takeWhile predicate") {
        val reader = Stream.compileToReader(Stream.range(0, 100).takeWhile(_ < 5))
        var all    = Chunk.empty: Chunk[Int]
        var done   = false
        while (!done) {
          val chunk = reader.readUpToN[Int](10)
          if (chunk.isEmpty) done = true
          else all = all ++ chunk
        }
        assertTrue(all == Chunk(0, 1, 2, 3, 4))
      },
      test("readUpToN on takeWhile that immediately fails returns empty") {
        val reader = Stream.compileToReader(Stream.range(10, 20).takeWhile(_ < 5))
        assertTrue(reader.readUpToN[Int](5) == Chunk.empty)
      }
    ),
    suite("SkipLimitReader")(
      test("readUpToN respects skip and limit") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk.fromIterable(0 until 20)).drop(5).take(5))
        var all    = Chunk.empty: Chunk[Int]
        var done   = false
        while (!done) {
          val chunk = reader.readUpToN[Int](10)
          if (chunk.isEmpty) done = true
          else all = all ++ chunk
        }
        assertTrue(all == Chunk(5, 6, 7, 8, 9))
      },
      test("readUpToN(2) on skip+limit reader returns at most 2 elements") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk.fromIterable(0 until 20)).drop(3).take(10))
        val chunk  = reader.readUpToN[Int](2)
        assertTrue(chunk.length <= 2 && chunk.length >= 1)
      }
    ),
    suite("ConcurrentBufferedReader (JVM)")(
      test("readUpToN returns available elements") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1, 2, 3, 4, 5)).buffer(4))
        Thread.sleep(50)
        var all  = Chunk.empty: Chunk[Int]
        var done = false
        while (!done) {
          val chunk = reader.readUpToN[Int](10)
          if (chunk.isEmpty) done = true
          else all = all ++ chunk
        }
        reader.close()
        assertTrue(all == Chunk(1, 2, 3, 4, 5))
      },
      test("readUpToN(0) returns empty on buffered reader") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1, 2, 3)).buffer(4))
        val result = reader.readUpToN[Int](0)
        reader.close()
        assertTrue(result == Chunk.empty)
      }
    ),
    suite("ConcurrentMergeReader (JVM)")(
      test("readUpToN returns elements from merged streams") {
        val inner1 = Stream.fromChunk(Chunk(1, 2, 3))
        val inner2 = Stream.fromChunk(Chunk(4, 5, 6))
        val merged = Stream.mergeAll(2)(Stream.fromIterable(List(inner1, inner2)))
        val reader = Stream.compileToReader(merged)
        Thread.sleep(100)
        var all  = Chunk.empty: Chunk[Int]
        var done = false
        while (!done) {
          val chunk = reader.readUpToN[Int](10)
          if (chunk.isEmpty) done = true
          else all = all ++ chunk
        }
        reader.close()
        assertTrue(all.toSet == Set(1, 2, 3, 4, 5, 6)) &&
        assertTrue(all.length == 6)
      }
    )
  )
}

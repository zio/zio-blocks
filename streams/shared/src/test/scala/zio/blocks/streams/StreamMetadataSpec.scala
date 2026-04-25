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

/**
 * Tests for [[Stream.knownChunk]] and [[Stream.knownLength]] metadata API.
 *
 * These methods allow stream consumers to inspect materialized data without
 * consuming the stream. `knownChunk` returns `Some(chunk)` if the stream wraps
 * a known `Chunk`, `None` otherwise. `knownLength` returns `Some(n)` if the
 * element count is known at O(1), `None` otherwise.
 */
object StreamMetadataSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("Stream metadata")(
    knownChunkSuite,
    knownLengthSuite,
    knownLengthPropagationSuite,
    fromArraySuite,
    fromIterableMetadataSuite,
    fromRangeMetadataSuite
  )

  private val chunk3: Chunk[Int] = Chunk(1, 2, 3)
  private val chunk5: Chunk[Int] = Chunk(1, 2, 3, 4, 5)
  private val chunk2: Chunk[Int] = Chunk(10, 20)

  private val knownChunkSuite = suite("knownChunk")(
    test("fromChunk returns Some(chunk)") {
      val chunk = Chunk(1, 2, 3)
      assertTrue(Stream.fromChunk(chunk).knownChunk == Some(chunk))
    },
    test("fromChunk with empty chunk returns Some(empty)") {
      assertTrue(Stream.fromChunk(Chunk.empty[Int]).knownChunk == Some(Chunk.empty[Int]))
    },
    test("fromChunk with Byte chunk returns Some(chunk)") {
      val chunk = Chunk[Byte](1, 2)
      assertTrue(Stream.fromChunk(chunk).knownChunk == Some(chunk))
    },
    test("fromArray returns Some(chunk)") {
      val arr = Array(1, 2, 3)
      assertTrue(Stream.fromArray(arr).knownChunk == Some(Chunk.fromArray(arr)))
    },
    test("fromIterable returns None") {
      assertTrue(Stream.fromIterable(List(1, 2, 3)).knownChunk == None)
    },
    test("fromIterator returns None") {
      assertTrue(Stream.fromIterator(Iterator(1, 2, 3)).knownChunk == None)
    },
    test("fromRange returns None") {
      assertTrue(Stream.fromRange(0 until 10).knownChunk == None)
    },
    test("empty returns Some(Chunk.empty)") {
      assertTrue(Stream.empty.knownChunk == Some(Chunk.empty))
    },
    test("succeed returns None") {
      assertTrue(Stream.succeed(42).knownChunk == None)
    },
    test("map invalidates knownChunk") {
      assertTrue(Stream.fromChunk(chunk3).map(_ + 1).knownChunk == None)
    },
    test("filter invalidates knownChunk") {
      assertTrue(Stream.fromChunk(chunk3).filter(_ > 1).knownChunk == None)
    },
    test("take invalidates knownChunk") {
      assertTrue(Stream.fromChunk(chunk3).take(2).knownChunk == None)
    },
    test("drop invalidates knownChunk") {
      assertTrue(Stream.fromChunk(chunk3).drop(1).knownChunk == None)
    },
    test("concat invalidates knownChunk") {
      assertTrue((Stream.fromChunk(chunk3) ++ Stream.fromChunk(chunk2)).knownChunk == None)
    }
  )

  private val knownLengthSuite = suite("knownLength")(
    test("fromChunk returns Some(length)") {
      assertTrue(Stream.fromChunk(Chunk(1, 2, 3)).knownLength == Some(3L))
    },
    test("fromChunk with empty chunk returns Some(0)") {
      assertTrue(Stream.fromChunk(Chunk.empty[Int]).knownLength == Some(0L))
    },
    test("fromArray returns Some(length)") {
      assertTrue(Stream.fromArray(Array(1, 2, 3)).knownLength == Some(3L))
    },
    test("fromIterable with known size returns Some(length)") {
      assertTrue(Stream.fromIterable(Vector(1, 2, 3)).knownLength == Some(3L))
    },
    test("fromRange returns Some(length)") {
      assertTrue(Stream.fromRange(0 until 10).knownLength == Some(10L))
    },
    test("fromIterator returns None") {
      assertTrue(Stream.fromIterator(Iterator(1)).knownLength == None)
    },
    test("empty returns Some(0)") {
      assertTrue(Stream.empty.knownLength == Some(0L))
    },
    test("succeed returns Some(1)") {
      assertTrue(Stream.succeed(42).knownLength == Some(1L))
    }
  )

  private val knownLengthPropagationSuite = suite("knownLength propagation")(
    test("map preserves knownLength") {
      assertTrue(Stream.fromChunk(chunk3).map(_ + 1).knownLength == Some(3L))
    },
    test("filter invalidates knownLength") {
      assertTrue(Stream.fromChunk(chunk3).filter(_ > 1).knownLength == None)
    },
    test("take clamps to min of n and knownLength") {
      assertTrue(Stream.fromChunk(chunk5).take(2).knownLength == Some(2L))
    },
    test("take with n > length clamps to length") {
      assertTrue(Stream.fromChunk(chunk5).take(100).knownLength == Some(5L))
    },
    test("drop subtracts from knownLength") {
      assertTrue(Stream.fromChunk(chunk5).drop(2).knownLength == Some(3L))
    },
    test("drop more than length clamps to 0") {
      assertTrue(Stream.fromChunk(chunk5).drop(100).knownLength == Some(0L))
    },
    test("take with negative n clamps knownLength to 0") {
      assertTrue(Stream.fromChunk(chunk5).take(-1).knownLength == Some(0L))
    },
    test("take with negative n produces empty stream") {
      assertTrue(Stream.fromChunk(chunk5).take(-1).runCollect == Right(Chunk.empty[Int]))
    },
    test("drop with negative n preserves knownLength (no-op)") {
      assertTrue(Stream.fromChunk(chunk5).drop(-1).knownLength == Some(5L))
    },
    test("drop with negative n produces all elements") {
      assertTrue(Stream.fromChunk(chunk5).drop(-1).runCollect == Right(chunk5))
    },
    test("take on unknown-length stream returns None") {
      assertTrue(Stream.fromIterator(Iterator(1, 2, 3)).take(2).knownLength == None)
    },
    test("concat sums both known lengths") {
      assertTrue((Stream.fromChunk(chunk3) ++ Stream.fromChunk(chunk2)).knownLength == Some(5L))
    },
    test("concat with one unknown returns None") {
      assertTrue((Stream.fromChunk(chunk3) ++ Stream.fromIterator(Iterator(1))).knownLength == None)
    },
    test("repeated returns None") {
      assertTrue(Stream.fromChunk(chunk3).repeated.knownLength == None)
    },
    test("flatMap invalidates knownLength") {
      assertTrue(Stream.fromChunk(chunk3).flatMap(i => Stream.succeed(i)).knownLength == None)
    },
    test("map then take chains correctly") {
      assertTrue(Stream.fromChunk(chunk3).map(_ + 1).take(2).knownLength == Some(2L))
    },
    test("drop then map chains correctly") {
      assertTrue(Stream.fromChunk(chunk3).drop(1).map(_ + 1).knownLength == Some(2L))
    }
  )

  private val fromArraySuite = suite("fromArray")(
    test("knownChunk returns Some(chunk)") {
      val arr = Array(1, 2, 3)
      assertTrue(Stream.fromArray(arr).knownChunk == Some(Chunk.fromArray(arr)))
    },
    test("knownLength returns Some(length)") {
      assertTrue(Stream.fromArray(Array(1, 2, 3)).knownLength == Some(3L))
    },
    test("empty array has knownLength Some(0)") {
      assertTrue(Stream.fromArray(Array.empty[Int]).knownLength == Some(0L))
    },
    test("Byte array specialization works") {
      val arr = Array[Byte](1, 2, 3)
      assertTrue(Stream.fromArray(arr).knownChunk == Some(Chunk.fromArray(arr)))
    },
    test("roundtrip collects correctly") {
      val arr = Array[Byte](1, 2, 3)
      assertTrue(Stream.fromArray(arr).runCollect == Right(Chunk.fromArray(arr)))
    }
  )

  private val fromIterableMetadataSuite = suite("fromIterable metadata")(
    test("Vector has knownLength") {
      assertTrue(Stream.fromIterable(Vector(1, 2, 3)).knownLength == Some(3L))
    },
    test("ArraySeq has knownLength") {
      assertTrue(Stream.fromIterable(scala.collection.immutable.ArraySeq(1, 2)).knownLength == Some(2L))
    },
    test("List has no knownLength") {
      assertTrue(Stream.fromIterable(List(1, 2, 3)).knownLength == None)
    },
    test("knownChunk is None") {
      assertTrue(Stream.fromIterable(Vector(1, 2, 3)).knownChunk == None)
    },
    test("LazyList has no knownLength") {
      assertTrue(Stream.fromIterable(LazyList(1, 2, 3)).knownLength == None)
    },
    test("roundtrip collects correctly") {
      assertTrue(Stream.fromIterable(List(1, 2, 3)).runCollect == Right(Chunk(1, 2, 3)))
    }
  )

  private val fromRangeMetadataSuite = suite("fromRange metadata")(
    test("exclusive range has knownLength") {
      assertTrue(Stream.fromRange(0 until 10).knownLength == Some(10L))
    },
    test("inclusive range has knownLength") {
      assertTrue(Stream.fromRange(1 to 5).knownLength == Some(5L))
    },
    test("empty range has knownLength Some(0)") {
      assertTrue(Stream.fromRange(Range(0, 0)).knownLength == Some(0L))
    },
    test("knownChunk is None") {
      assertTrue(Stream.fromRange(0 until 10).knownChunk == None)
    },
    test("roundtrip collects correctly") {
      assertTrue(Stream.fromRange(1 to 3).runCollect == Right(Chunk(1, 2, 3)))
    }
  )
}

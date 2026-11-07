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

import zio.blocks.streams.io.Reader
import zio.test._

object StrictVectorLaneSpec extends StreamsBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("strict Vector lanes")(
    test("reader preserves every physical lane") {
      val booleans = Reader.fromIterable(Vector(false, true))
      val bytes    = Reader.fromIterable(Vector(1.toByte, 2.toByte))
      val chars    = Reader.fromIterable(Vector('a', 'b'))
      val shorts   = Reader.fromIterable(Vector(3.toShort, 4.toShort))
      val ints     = Reader.fromIterable(Vector(5, 6))
      val longs    = Reader.fromIterable(Vector(7L, 8L))
      val floats   = Reader.fromIterable(Vector(1.25f, 2.5f))
      val doubles  = Reader.fromIterable(Vector(3.25, 4.5))
      val refs     = Reader.fromIterable(Vector("a", "b"))
      assertTrue(
        booleans.readBoolean(-1) == 0,
        booleans.readBoolean(-1) == 1,
        bytes.readByte() == 1,
        bytes.readByte() == 2,
        chars.readChar(-1) == 'a'.toInt,
        chars.readChar(-1) == 'b'.toInt,
        shorts.readShort(-1) == 3,
        shorts.readShort(-1) == 4,
        ints.readInt(-1L) == 5L,
        ints.readInt(-1L) == 6L,
        longs.readLong(-1L) == 7L,
        longs.readLong(-1L) == 8L,
        floats.readFloat(Double.NaN) == 1.25,
        floats.readFloat(Double.NaN) == 2.5,
        doubles.readDouble(Double.NaN) == 3.25,
        doubles.readDouble(Double.NaN) == 4.5,
        refs.read(null) == "a",
        refs.read(null) == "b"
      )
    },
    test("stream slices and accumulators are lane-independent") {
      val longs = Stream.fromIterable(Vector(1L, 2L, 3L, 4L)).drop(1).take(2)
      val refs  = Stream.fromIterable(Vector("a", "bb", "ccc")).takeWhile(_.length < 3)
      assertTrue(
        longs.runFoldLongBlocking(0L, _ + _) == Right(5L),
        longs.runFoldIntBlocking(0, (acc, value) => acc + value.toInt) == Right(5),
        longs.runFoldDoubleBlocking(0d, _ + _) == Right(5d),
        refs.runFoldLongBlocking(0L, (acc, value) => acc + value.length) == Right(3L),
        refs.runFoldIntBlocking(0, (acc, value) => acc + value.length) == Right(3),
        refs.runFoldDoubleBlocking(0d, (acc, value) => acc + value.length) == Right(3d)
      )
    } @@ TestAspect.jvmOnly,
    test("skip, limit, and reset retain strict source bounds") {
      val reader  = Reader.fromIterable(Vector(1L, 2L, 3L, 4L))
      val skipped = reader.setSkip(1)
      val limited = reader.setLimit(2)
      val first   = reader.readLong(-1L)
      val second  = reader.readLong(-1L)
      reader.reset()
      val resetFirst  = reader.readLong(-1L)
      val resetSecond = reader.readLong(-1L)
      val eof         = reader.readLong(-1L)
      assertTrue(
        skipped,
        limited,
        first == 2L,
        second == 3L,
        resetFirst == 2L,
        resetSecond == 3L,
        eof == -1L
      )
    }
  )
}

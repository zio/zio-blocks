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
import zio.blocks.async.Async
import zio.test._

object ConcurrentFullDomainSpec extends StreamsBaseSpec {
  private val doubleBits = Vector(
    0xfff8000000000001L,
    0xfff8000000000002L,
    java.lang.Double.doubleToRawLongBits(Double.MaxValue),
    java.lang.Double.doubleToRawLongBits(-0.0d)
  )
  private val doubles = doubleBits.map(java.lang.Double.longBitsToDouble)
  private val longs   = Vector(Long.MinValue, Long.MinValue + 1L, Long.MaxValue, 0L)

  def spec: Spec[TestEnvironment, Any] = suite("Concurrent full-domain primitive transport")(
    test("mapPar preserves every Long value for Long and Int inputs") {
      ZIO.attemptBlocking {
        val longInput = Stream.fromIterable(longs).mapPar(2)(identity).runCollect
        val intInput  = Stream.fromIterable(longs.indices).mapPar(2)(longs).runCollect
        assertTrue(
          longInput.exists(_.toList.sorted == longs.sorted),
          intInput.exists(_.toList.sorted == longs.sorted)
        )
      }
    },
    test("mapPar preserves raw Double bits for Double and Float inputs") {
      ZIO.attemptBlocking {
        val floatSource = Stream.fromIterable(doubles.indices.map(_.toFloat)).runCollect
        val doubleInput = Stream.fromIterable(doubles).mapPar(2)(identity).runCollect
        val floatMapped = Stream.fromIterable(doubles.indices.map(_.toFloat)).mapPar(2)(f => doubles(f.toInt))
        val direct      = Stream.compileToReader(floatMapped).asInstanceOf[zio.blocks.streams.io.Reader.SyncReader[Double]]
        val directData  = new Array[Double](4)
        var directCount = 0
        var read        = 0
        while (directCount < directData.length && read >= 0) {
          read = direct.readDoubles(directData, directCount, directData.length - directCount)
          if (read > 0) directCount += read
        }
        direct.close()
        val floatInput                                                               = Stream.fromIterable(doubles.indices.map(_.toFloat)).mapPar(2)(f => doubles(f.toInt)).runCollect
        def raw(result: Either[Nothing, zio.blocks.chunk.Chunk[Double]]): List[Long] =
          result.fold(_ => Nil, _.toList.map(java.lang.Double.doubleToRawLongBits).sorted)
        val floatError = floatInput.asInstanceOf[Either[Any, Any]].left.toOption
        assertTrue(
          floatSource.exists(_.toList == List(0.0f, 1.0f, 2.0f, 3.0f)),
          direct.jvmType == JvmType.Double,
          directCount == 4,
          floatError.isEmpty,
          raw(doubleInput) == doubleBits.sorted.toList,
          raw(floatInput) == doubleBits.sorted.toList
        )
      }
    },
    test("mergeAll preserves every Long value and raw Double bit pattern") {
      ZIO.attemptBlocking {
        val longResult   = Stream.mergeAll(2)(Stream.fromIterable(longs.map(Stream.succeed))).runCollect
        val doubleResult = Stream
          .mergeAll(2)(Stream.fromIterable(doubles.map(Stream.succeed)))
          .runCollect
        val rawDoubles = doubleResult.fold(_ => Nil, _.toList.map(java.lang.Double.doubleToRawLongBits).sorted)
        assertTrue(
          longResult.exists(_.toList.sorted == longs.sorted),
          rawDoubles == doubleBits.sorted.toList
        )
      }
    },
    test("ready asynchronous merge preserves every primitive and reference lane") {
      ZIO.attemptBlocking {
        def merged[A](values: Vector[A])(implicit jt: JvmType.Infer[A]): List[A] = {
          val inner = Stream.fromIterable(values).mapAsync(Async.succeed)
          Stream.bufferSize(2)(Stream.mergeAll(2)(Stream(inner))).runCollect.toOption.get.toList
        }
        val booleans = Vector(false, true, false)
        val bytes    = Vector(Byte.MinValue, -1.toByte, 0.toByte, Byte.MaxValue)
        val shorts   = Vector(Short.MinValue, -1.toShort, 0.toShort, Short.MaxValue)
        val chars    = Vector(Char.MinValue, 'x', Char.MaxValue)
        val ints     = Vector(Int.MinValue, -1, 0, Int.MaxValue)
        val floats   = Vector(Float.NegativeInfinity, -0.0f, Float.MaxValue, Float.PositiveInfinity)
        val refs     = Vector("first", "second", "third")
        assertTrue(
          merged(booleans) == booleans.toList,
          merged(bytes) == bytes.toList,
          merged(shorts) == shorts.toList,
          merged(chars) == chars.toList,
          merged(ints) == ints.toList,
          merged(longs) == longs.toList,
          merged(floats).map(java.lang.Float.floatToRawIntBits) == floats.map(java.lang.Float.floatToRawIntBits).toList,
          merged(doubles).map(java.lang.Double.doubleToRawLongBits) == doubleBits.toList,
          merged(refs) == refs.toList
        )
      }
    }
  )
}

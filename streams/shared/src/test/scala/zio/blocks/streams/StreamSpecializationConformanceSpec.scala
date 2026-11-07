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

import zio.blocks.async.Async
import zio.blocks.chunk.Chunk
import zio.blocks.combinators.Concat
import zio.test._

object StreamSpecializationConformanceSpec extends StreamsBaseSpec {
  private def collect[A](stream: Stream[Nothing, A]) =
    runAsync(stream.runCollectAsync).map(_.fold[Chunk[A]](_ => Chunk.empty[A], identity))

  def spec: Spec[TestEnvironment, Any] = suite("Stream specialization conformance")(
    test("stateful and windowed Stream pull families execute on primitive inputs") {
      var tapped = Chunk.empty[Int]
      for {
        chunked     <- collect(Stream(1, 2, 3, 4).chunked(2))
        grouped     <- collect(Stream(1, 2, 3, 4).grouped(2))
        intersperse <- collect(Stream(1, 2, 3).intersperse(0))
        sliding     <- collect(Stream(1, 2, 3, 4).sliding(3, 2))
        distinct    <- collect(Stream(1, 1, 2, 2, 3).distinctBy(identity))
        accumulated <- collect(Stream(1, 2, 3).mapAccum(0)((state, value) => (state + value, state + value)))
        parallel    <- collect(Stream(1, 2, 3).mapPar(2)(_ * 10))
        scanned     <- collect(Stream(1, 2, 3).scan(0)(_ + _))
        tappedOut   <- collect(Stream(1, 2, 3).tapEach(value => tapped = tapped :+ value))
      } yield assertTrue(
        chunked == Chunk(Chunk(1, 2), Chunk(3, 4)),
        grouped == Chunk(Chunk(1, 2), Chunk(3, 4)),
        intersperse == Chunk(1, 0, 2, 0, 3),
        sliding == Chunk(Chunk(1, 2, 3), Chunk(3, 4)),
        distinct == Chunk(1, 2, 3),
        accumulated == Chunk(1, 3, 6),
        parallel.toSet == Set(10, 20, 30),
        scanned == Chunk(0, 1, 3, 6),
        tappedOut == Chunk(1, 2, 3),
        tapped == Chunk(1, 2, 3)
      )
    },
    test("stateful and windowed Stream readers execute every primitive lane") {
      def exercise[A](values: Chunk[A])(implicit jt: JvmType.Infer[A]) = {
        var tapped = Chunk.empty[A]
        for {
          chunked     <- collect(Stream.fromChunk(values).chunked(2))
          grouped     <- collect(Stream.fromChunk(values).grouped(2))
          intersperse <- collect(Stream.fromChunk(values).intersperse(values(0)))
          sliding     <- collect(Stream.fromChunk(values).sliding(2))
          distinct    <- collect(Stream.fromChunk(values).distinctBy(identity))
          accumulated <- collect(Stream.fromChunk(values).mapAccum(0)((state, value) => (state + 1, value)))
          parallel    <- collect(Stream.fromChunk(values).mapPar(2)(identity))
          scanned     <- collect(Stream.fromChunk(values).scan(0)((state, _) => state + 1))
          tappedOut   <- collect(Stream.fromChunk(values).tapEach(value => tapped = tapped :+ value))
        } yield chunked == Chunk(values) && grouped == Chunk(values) &&
          intersperse == Chunk(values(0), values(0), values(1)) && sliding == Chunk(values) &&
          distinct == values && accumulated == values && parallel.toSet == values.toSet && scanned == Chunk(0, 1, 2) &&
          tappedOut == values && tapped == values
      }
      for {
        boolean <- exercise(Chunk(false, true))
        byte    <- exercise(Chunk[Byte](1, 2))
        char    <- exercise(Chunk('a', 'b'))
        short   <- exercise(Chunk[Short](1, 2))
        int     <- exercise(Chunk(1, 2))
        long    <- exercise(Chunk(1L, 2L))
        float   <- exercise(Chunk(1.0f, 2.0f))
        double  <- exercise(Chunk(1.0d, 2.0d))
      } yield assertTrue(boolean, byte, char, short, int, long, float, double)
    },
    test("widened intersperse dispatches by its output lane") {
      val values: Concat.WithOut[Int, Double, Int | Double] = implicitly
      implicit val output: JvmType.Infer[Int | Double]      = JvmType.Infer.boxed[Int | Double]
      val stream: Stream[Nothing, Int | Double]             = Stream(1, 3).intersperse[Double, Int | Double](2.0)(values, output)
      collect(stream).map(result => assertTrue(result == Chunk(values.left(1), values.right(2.0), values.left(3))))
    },
    test("intersperse preserves Long and Double scalar sentinel collisions") {
      for {
        longs   <- collect(Stream(Long.MinValue, Long.MaxValue).intersperse(Long.MinValue))
        doubles <- collect(Stream(Double.MaxValue, Double.MinValue).intersperse(Double.MaxValue))
      } yield assertTrue(
        longs == Chunk(Long.MinValue, Long.MinValue, Long.MaxValue),
        doubles == Chunk(Double.MaxValue, Double.MaxValue, Double.MinValue)
      )
    },
    test("scan exposes every primitive output lane while pulling a primitive input lane") {
      for {
        boolean <- collect(Stream(1, 2).scan(false)((state, value) => state ^ (value == 1)))
        byte    <- collect(Stream(1, 2).scan(0.toByte)((state, value) => (state + value).toByte))
        char    <- collect(Stream(1, 2).scan('a')((state, value) => (state + value).toChar))
        short   <- collect(Stream(1, 2).scan(0.toShort)((state, value) => (state + value).toShort))
        int     <- collect(Stream(1, 2).scan(0)(_ + _))
        long    <- collect(Stream(1, 2).scan(0L)(_ + _))
        float   <- collect(Stream(1, 2).scan(0.0f)(_ + _))
        double  <- collect(Stream(1, 2).scan(0.0)(_ + _))
      } yield assertTrue(
        boolean == Chunk(false, true, true),
        byte == Chunk[Byte](0, 1, 3),
        char == Chunk('a', 'b', 'd'),
        short == Chunk[Short](0, 1, 3),
        int == Chunk(0, 1, 3),
        long == Chunk(0L, 1L, 3L),
        float == Chunk(0.0f, 1.0f, 3.0f),
        double == Chunk(0.0, 1.0, 3.0)
      )
    },
    test("Stream error mapping executes synchronous and asynchronous pull routes") {
      for {
        sync  <- runAsync(Stream.fail("error").mapError(_.length).runCollectAsync)
        async <- runAsync(Stream.fail("error").mapErrorAsync(value => Async.succeed(value.length)).runCollectAsync)
      } yield assertTrue(sync == Left(5), async == Left(5))
    },
    test("non-collect Stream terminals execute specialized readers") {
      for {
        folded <- runAsync(Stream(1, 2, 3).runFoldAsync(0)((state, value) => Async.succeed(state + value)))
        head   <- runAsync(Stream(1, 2, 3).headAsync)
        found  <- runAsync(Stream(1, 2, 3).findAsync(value => Async.succeed(value == 2)))
        reader <- runAsync(Stream(1, 2, 3).useReaderAsync(_.readN[Int](3)))
      } yield assertTrue(folded == Right(6), head == Right(Some(1)), found == Right(Some(2)), reader == Chunk(1, 2, 3))
    },
    test("iterator and Java I/O Stream sources execute their exact primitive lanes") {
      val bytes = new java.io.ByteArrayInputStream(Array[Byte](1, 2, 3))
      val chars = new java.io.StringReader("abc")
      for {
        iterator <- collect(Stream.fromIterator(Iterator(1, 2, 3)))
        input    <- runAsync(Stream.fromInputStream(bytes).runCollectAsync)
        reader   <- runAsync(Stream.fromJavaReader(chars).runCollectAsync)
      } yield assertTrue(
        iterator == Chunk(1, 2, 3),
        input == Right(Chunk[Byte](1, 2, 3)),
        reader == Right(Chunk('a', 'b', 'c'))
      )
    }
  )
}

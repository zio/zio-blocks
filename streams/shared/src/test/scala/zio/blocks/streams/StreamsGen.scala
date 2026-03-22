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
import zio.test.Gen

/** Generators shared across all spec files. */
object StreamsGen {

  val genInt: Gen[Any, Int]       = Gen.int(-1000, 1000)
  val genByte: Gen[Any, Byte]     = Gen.byte
  val genString: Gen[Any, String] = Gen.alphaNumericStringBounded(0, 20)

  def genChunk[A](gen: Gen[Any, A]): Gen[Any, Chunk[A]] =
    Gen.chunkOfBounded(0, 50)(gen).map(zc => Chunk.fromIterable(zc))

  val genIntStream: Gen[Any, Stream[Nothing, Int]] =
    genChunk(genInt).map(c => Stream.fromChunk(c))

  val genByteStream: Gen[Any, Stream[Nothing, Byte]] =
    genChunk(genByte).map(c => Stream.fromChunk(c))

  val genStringStream: Gen[Any, Stream[Nothing, String]] =
    genChunk(genString).map(c => Stream.fromChunk(c))

  def genStream[A](gen: Gen[Any, A]): Gen[Any, Stream[Nothing, A]] =
    genChunk(gen).map(c => Stream.fromChunk(c))

  // All pipes in genIntPipeline are Pipeline[Int, Int].
  val genIntPipeline: Gen[Any, Pipeline[Int, Int]] =
    Gen.oneOf(
      Gen.const(Pipeline.identity[Int]),
      Gen.function(genInt).map(f => Pipeline.map[Int, Int](f)),
      Gen.int(0, 20).map(n => Pipeline.take[Int](n.toLong)),
      Gen.int(0, 20).map(n => Pipeline.drop[Int](n.toLong)),
      Gen.function(Gen.boolean).map(p => Pipeline.filter[Int](p))
    )

  val genIntSink: Gen[Any, Sink[Nothing, Int, Any]] =
    Gen.oneOf(
      Gen.const(Sink.drain.asInstanceOf[Sink[Nothing, Int, Any]]),
      Gen.const(Sink.count.asInstanceOf[Sink[Nothing, Int, Any]]),
      Gen.const(Sink.sumInt.asInstanceOf[Sink[Nothing, Int, Any]]),
      Gen.const(Sink.collectAll[Int].asInstanceOf[Sink[Nothing, Int, Any]]),
      Gen.int(0, 20).map(n => Sink.take[Int](n).asInstanceOf[Sink[Nothing, Int, Any]])
    )
}

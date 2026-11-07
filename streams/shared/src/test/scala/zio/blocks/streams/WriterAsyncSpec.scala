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

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Writer
import zio.test._

object WriterAsyncSpec extends StreamsBaseSpec {
  private final class RecordingWriter[A] extends Writer[A] {
    var values: List[A]                   = Nil
    var closes                            = 0
    var failures: List[Throwable]         = Nil
    def close(): Unit                     = closes += 1
    def isClosed: Boolean                 = closes > 0 || failures.nonEmpty
    def write(value: A): Boolean          = if (isClosed) false else { values = values :+ value; true }
    override def fail(t: Throwable): Unit = failures = failures :+ t
  }

  def spec = suite("Writer deferred async operations")(
    test("construction is effect-free and each drive performs exactly one scalar write") {
      val writer = new RecordingWriter[Int]
      val effect = writer.writeAsync(1)
      val before = writer.values
      val first  = effect.block
      val second = effect.block
      assertTrue(before.isEmpty, first, second, writer.values == List(1))
    },
    test("thrown operations are captured with identity") {
      val failure = new RuntimeException("write")
      val writer  = new Writer[Int] {
        def close(): Unit              = ()
        def isClosed: Boolean          = false
        def write(value: Int): Boolean = throw failure
      }
      val observed = writer.writeAsync(1).either.block
      assertTrue(observed == Left(failure))
    },
    test("cancellation before drive closes ownership without invoking the operation") {
      val writer = new RecordingWriter[Int]
      val effect = writer.writeAsync(1).asInstanceOf[Pollable[Boolean]]
      Async.cancelWithCleanup(effect).block
      assertTrue(writer.values.isEmpty, writer.closes == 1, writer.isClosed)
    },
    test("jvmTypeAsync reads metadata only when driven") {
      var queries = 0
      val writer  = new Writer[Int] {
        def close(): Unit              = ()
        def isClosed: Boolean          = false
        def write(value: Int): Boolean = true
        override def jvmType: JvmType  = { queries += 1; JvmType.Int }
      }
      val effect = writer.jvmTypeAsync
      val before = queries
      val result = effect.block
      assertTrue(before == 0, result == JvmType.Int, queries == 1)
    },
    test("lifecycle and state methods defer and preserve writer ownership") {
      val writer  = new RecordingWriter[Int]
      val failure = new RuntimeException("boom")
      val ready   = writer.writeableAsync()
      val failed  = writer.failAsync(failure)
      assertTrue(
        writer.failures.isEmpty,
        ready.block,
        failed.block == (),
        writer.failures == List(failure),
        writer.isClosedAsync.block
      )
    },
    test("closeAsync is deferred and delegates once per drive") {
      val writer = new RecordingWriter[Int]
      val close  = writer.closeAsync()
      val before = writer.closes
      close.block
      assertTrue(before == 0, writer.closes == 1, writer.isClosedAsync.block)
    },
    test("generic bulk write returns the undelivered suffix") {
      val writer = Writer.single[Int]
      assertTrue(writer.writeAllAsync(Chunk(1, 2, 3)).block == Chunk(2, 3))
    },
    test("all primitive scalar mirrors preserve values") {
      val booleans = new RecordingWriter[Boolean]
      val bytes    = new RecordingWriter[Byte]
      val chars    = new RecordingWriter[Char]
      val doubles  = new RecordingWriter[Double]
      val floats   = new RecordingWriter[Float]
      val ints     = new RecordingWriter[Int]
      val longs    = new RecordingWriter[Long]
      val shorts   = new RecordingWriter[Short]
      val results  = List(
        booleans.writeBooleanAsync(true).block,
        bytes.writeByteAsync(1.toByte).block,
        chars.writeCharAsync('x').block,
        doubles.writeDoubleAsync(2.5).block,
        floats.writeFloatAsync(3.5f).block,
        ints.writeIntAsync(4).block,
        longs.writeLongAsync(5L).block,
        shorts.writeShortAsync(6.toShort).block
      )
      assertTrue(
        results.forall(identity),
        booleans.values == List(true),
        bytes.values == List(1.toByte),
        chars.values == List('x'),
        doubles.values == List(2.5),
        floats.values == List(3.5f),
        ints.values == List(4),
        longs.values == List(5L),
        shorts.values == List(6.toShort)
      )
    },
    test("primitive bulk byte mirror preserves offset, length, and result") {
      val writer = new RecordingWriter[Byte]
      val result = writer.writeBytesAsync(Array[Byte](0, 1, 2, 3), 1, 2).block
      assertTrue(result == 2, writer.values == List[Byte](1, 2))
    },
    test("every scalar and bulk deferred twin captures a drive-time throw by identity") {
      val failure = new RuntimeException("deferred-twin")
      var calls   = 0
      val writer  = new Writer[Any] {
        def close(): Unit              = ()
        def isClosed: Boolean          = false
        def write(value: Any): Boolean = { calls += 1; throw failure }
      }
      val effects: List[Async[Any]] = List(
        writer.writeAsync("x"),
        writer.writeAllAsync(Chunk("x")),
        writer.writeBooleanAsync(true),
        writer.writeByteAsync(1.toByte),
        writer.writeBytesAsync(Array[Byte](1), 0, 1),
        writer.writeCharAsync('x'),
        writer.writeDoubleAsync(1d),
        writer.writeFloatAsync(1f),
        writer.writeIntAsync(1),
        writer.writeLongAsync(1L),
        writer.writeShortAsync(1.toShort)
      ).map(_.asInstanceOf[Async[Any]])
      val before  = calls
      val results = effects.map(_.either.block)
      assertTrue(before == 0, calls == effects.length, results.forall(_ == Left(failure)))
    },
    test("cancelling every scalar and bulk deferred twin before drive closes its writer without writing") {
      def cancel(make: RecordingWriter[Any] => Async[Any]): (List[Any], Int) = {
        val writer = new RecordingWriter[Any]
        Async.cancelWithCleanup(make(writer).asInstanceOf[Pollable[Any]]).block
        (writer.values, writer.closes)
      }
      val results = List[RecordingWriter[Any] => Async[Any]](
        writer => writer.writeAsync("x").asInstanceOf[Async[Any]],
        writer => writer.writeAllAsync(Chunk("x")).asInstanceOf[Async[Any]],
        writer => writer.writeBooleanAsync(true).asInstanceOf[Async[Any]],
        writer => writer.writeByteAsync(1.toByte).asInstanceOf[Async[Any]],
        writer => writer.writeBytesAsync(Array[Byte](1), 0, 1).asInstanceOf[Async[Any]],
        writer => writer.writeCharAsync('x').asInstanceOf[Async[Any]],
        writer => writer.writeDoubleAsync(1d).asInstanceOf[Async[Any]],
        writer => writer.writeFloatAsync(1f).asInstanceOf[Async[Any]],
        writer => writer.writeIntAsync(1).asInstanceOf[Async[Any]],
        writer => writer.writeLongAsync(1L).asInstanceOf[Async[Any]],
        writer => writer.writeShortAsync(1.toShort).asInstanceOf[Async[Any]]
      ).map(cancel)
      assertTrue(results.forall { case (values, closes) => values.isEmpty && closes == 1 })
    },
    test("bulk byte validation is eager and consumes no input") {
      val writer          = new RecordingWriter[Byte]
      val nullFailure     = scala.util.Try(writer.writeBytes(null, 0, 0)).failed.toOption
      val rangeFailure    = scala.util.Try(writer.writeBytes(Array[Byte](1, 2), 1, 2)).failed.toOption
      val negativeFailure = scala.util.Try(writer.writeBytes(Array[Byte](1, 2), 0, -1)).failed.toOption
      assertTrue(
        nullFailure.exists(_.isInstanceOf[NullPointerException]),
        rangeFailure.exists(_.isInstanceOf[IndexOutOfBoundsException]),
        negativeFailure.exists(_.isInstanceOf[IndexOutOfBoundsException]),
        writer.values.isEmpty
      )
    }
  )
}

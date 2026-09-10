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

import scala.util.Try
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
import zio.test._

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels

object ReaderBulkValidationSpec extends StreamsBaseSpec {
  private def invalidCalls(reader: Reader.SyncReader[_]): List[() => Any] = reader.jvmType match {
    case JvmType.Byte =>
      val r = reader.asInstanceOf[Reader.SyncReader[Byte]]
      List(
        () => r.readBytes(null, 0, 0),
        () => r.readBytes(new Array[Byte](1), -1, 0),
        () => r.readBytes(new Array[Byte](1), 0, -1),
        () => r.readBytes(new Array[Byte](1), 2, 0),
        () => r.readBytes(new Array[Byte](1), Int.MaxValue, 2)
      )
    case JvmType.Int =>
      val r = reader.asInstanceOf[Reader.SyncReader[Int]]
      List(
        () => r.readInts(null, 0, 0),
        () => r.readInts(new Array[Int](1), -1, 0),
        () => r.readInts(new Array[Int](1), 0, -1),
        () => r.readInts(new Array[Int](1), 2, 0),
        () => r.readInts(new Array[Int](1), Int.MaxValue, 2)
      )
    case JvmType.Long =>
      val r = reader.asInstanceOf[Reader.SyncReader[Long]]
      List(
        () => r.readLongs(null, 0, 0),
        () => r.readLongs(new Array[Long](1), -1, 0),
        () => r.readLongs(new Array[Long](1), 0, -1),
        () => r.readLongs(new Array[Long](1), 2, 0),
        () => r.readLongs(new Array[Long](1), Int.MaxValue, 2)
      )
    case JvmType.Float =>
      val r = reader.asInstanceOf[Reader.SyncReader[Float]]
      List(
        () => r.readFloats(null, 0, 0),
        () => r.readFloats(new Array[Float](1), -1, 0),
        () => r.readFloats(new Array[Float](1), 0, -1),
        () => r.readFloats(new Array[Float](1), 2, 0),
        () => r.readFloats(new Array[Float](1), Int.MaxValue, 2)
      )
    case JvmType.Double =>
      val r = reader.asInstanceOf[Reader.SyncReader[Double]]
      List(
        () => r.readDoubles(null, 0, 0),
        () => r.readDoubles(new Array[Double](1), -1, 0),
        () => r.readDoubles(new Array[Double](1), 0, -1),
        () => r.readDoubles(new Array[Double](1), 2, 0),
        () => r.readDoubles(new Array[Double](1), Int.MaxValue, 2)
      )
    case other => throw new IllegalArgumentException(s"Unsupported bulk lane: $other")
  }

  private def allInvalid(reader: Reader.SyncReader[_]): Boolean =
    invalidCalls(reader).forall(call =>
      Try(call()).failed.toOption.exists {
        case _: NullPointerException      => true
        case _: IndexOutOfBoundsException => true
        case _                            => false
      }
    )

  private def invalidAsyncCalls(reader: Reader.AsyncReader[_]): List[() => Async[Int]] = reader.jvmType match {
    case JvmType.Byte =>
      val r = reader.asInstanceOf[Reader.AsyncReader[Byte]]
      List(() => r.readBytes(null, 0, 0), () => r.readBytes(new Array[Byte](1), Int.MaxValue, 2))
    case JvmType.Int =>
      val r = reader.asInstanceOf[Reader.AsyncReader[Int]]
      List(() => r.readInts(null, 0, 0), () => r.readInts(new Array[Int](1), Int.MaxValue, 2))
    case JvmType.Long =>
      val r = reader.asInstanceOf[Reader.AsyncReader[Long]]
      List(() => r.readLongs(null, 0, 0), () => r.readLongs(new Array[Long](1), Int.MaxValue, 2))
    case JvmType.Float =>
      val r = reader.asInstanceOf[Reader.AsyncReader[Float]]
      List(() => r.readFloats(null, 0, 0), () => r.readFloats(new Array[Float](1), Int.MaxValue, 2))
    case JvmType.Double =>
      val r = reader.asInstanceOf[Reader.AsyncReader[Double]]
      List(() => r.readDoubles(null, 0, 0), () => r.readDoubles(new Array[Double](1), Int.MaxValue, 2))
    case other => throw new IllegalArgumentException(s"Unsupported bulk lane: $other")
  }

  private def allInvalidAsync(reader: Reader.AsyncReader[_]): Boolean =
    invalidAsyncCalls(reader).forall(call =>
      Try(call()).toOption.exists(_.either.block.left.exists {
        case _: NullPointerException      => true
        case _: IndexOutOfBoundsException => true
        case _                            => false
      })
    )

  def spec = suite("Reader bulk caller-array validation")(
    test("all primitive sync lanes validate every invalid range without consuming") {
      val bytes   = Reader.fromChunk(Chunk[Byte](1))
      val ints    = Reader.fromChunk(Chunk(2))
      val longs   = Reader.fromChunk(Chunk(3L))
      val floats  = Reader.fromChunk(Chunk(4.0f))
      val doubles = Reader.fromChunk(Chunk(5.0))
      val valid   =
        allInvalid(bytes) && allInvalid(ints) && allInvalid(longs) && allInvalid(floats) && allInvalid(doubles)
      assertTrue(
        valid,
        bytes.readByte() == 1,
        ints.readInt(-1) == 2,
        longs.readLong(-1) == 3,
        floats.readFloat(-1) == 4.0,
        doubles.readDouble(-1) == 5.0
      )
    },
    test("closed readers validate before EOF and zero-length results") {
      val readers = List(
        Reader.fromChunk(Chunk.empty[Byte]),
        Reader.fromChunk(Chunk.empty[Int]),
        Reader.fromChunk(Chunk.empty[Long]),
        Reader.fromChunk(Chunk.empty[Float]),
        Reader.fromChunk(Chunk.empty[Double])
      )
      assertTrue(readers.forall(allInvalid))
    },
    test("all primitive async lanes return failures without eager throws or consumption") {
      val bytes   = Reader.fromChunk(Chunk[Byte](1)).toAsync
      val ints    = Reader.fromChunk(Chunk(2)).toAsync
      val longs   = Reader.fromChunk(Chunk(3L)).toAsync
      val floats  = Reader.fromChunk(Chunk(4.0f)).toAsync
      val doubles = Reader.fromChunk(Chunk(5.0)).toAsync
      assertTrue(
        allInvalidAsync(bytes),
        allInvalidAsync(ints),
        allInvalidAsync(longs),
        allInvalidAsync(floats),
        allInvalidAsync(doubles),
        bytes.readByte().block == 1,
        ints.readInt(-1).block == 2,
        longs.readLong(-1).block == 3,
        floats.readFloat(-1).block == 4.0,
        doubles.readDouble(-1).block == 5.0
      )
    },
    test("release and concat wrappers return failed Async values and consume nothing") {
      def check(make: => Reader.AsyncReader[Int]): Boolean = {
        val reader = make
        val eager  = Try(reader.readInts(new Array[Int](1), Int.MaxValue, 2))
        eager.isSuccess && eager.get.either.block.left.exists(_.isInstanceOf[IndexOutOfBoundsException]) &&
        reader.readInt(-1).block == 7
      }
      assertTrue(
        check(Reader.singleInt(7).toAsync.withReleaseAsync(() => Async.succeed(()))),
        check(Reader.closed.toAsync.concatReaderWithJvmType(() => Reader.singleInt(7), JvmType.Int))
      )
    },
    test("JVM async-to-sync validates before driving and preserves input") {
      val reader = Reader.singleInt(9).toAsync.toSync
      assertTrue(allInvalid(reader), reader.readInt(-1) == 9)
    },
    test("ByteBuffer and channel overrides validate before consuming input") {
      val byteBuffer = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](11)))
      val channel    = NioReaders.fromChannel(Channels.newChannel(new ByteArrayInputStream(Array[Byte](12))), 1)
      assertTrue(allInvalid(byteBuffer), allInvalid(channel), byteBuffer.readByte() == 11, channel.readByte() == 12)
    }
  )
}

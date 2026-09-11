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

import java.util.concurrent.atomic.AtomicInteger

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
import zio.test._

object AsyncReaderResidualCoverageSpec extends StreamsBaseSpec {
  private def async[A: JvmType.Infer](values: A*): Reader.AsyncReader[A] =
    Reader.fromChunk(Chunk.fromIterable(values)).toAsync

  def spec = suite("async Reader residual coverage")(
    test("release wrappers delegate every remaining scalar lane and release once") {
      val releases                                                          = new AtomicInteger
      def released[A](reader: Reader.AsyncReader[A]): Reader.AsyncReader[A] =
        reader.withReleaseAsync(() => Async.succeed { releases.incrementAndGet(); () })
      val ref  = released(async("value")); val char = released(async('x')); val short   = released(async[Short](7))
      val long = released(async(8L)); val float     = released(async(1.5f)); val double = released(async(2.5d))
      for {
        rv <- runAsync(ref.read[String]("eof")); cv    <- runAsync(char.readChar(-1)); sv    <- runAsync(short.readShort(-1))
        lv <- runAsync(long.readLong(-1L)); fv         <- runAsync(float.readFloat(-1d)); dv <- runAsync(double.readDouble(-1d))
        _  <- runAsync(ref.close()); _                 <- runAsync(char.close()); _          <- runAsync(short.close())
        _  <- runAsync(long.close()); _                <- runAsync(float.close()); _         <- runAsync(double.close());
        _  <- runAsync(double.close())
        re <- runAsync(ref.read[String]("closed")); ce <- runAsync(char.readChar(-2));
        se <- runAsync(short.readShort(-2))
        le <- runAsync(long.readLong(-2L)); fe         <- runAsync(float.readFloat(-2d)); de <- runAsync(double.readDouble(-2d))
      } yield assertTrue(
        rv == "value",
        cv == 'x'.toInt,
        sv == 7,
        lv == 8L,
        fv == 1.5d,
        dv == 2.5d,
        re == "closed",
        ce == -2,
        se == -2,
        le == -2L,
        fe == -2d,
        de == -2d,
        releases.get() == 6
      )
    },
    test("async concatenation appends while pristine and traverses synchronous and asynchronous tails") {
      val first = async(1).concatAsyncWithJvmType(() => Async.succeed(Reader.singleInt(2)), JvmType.Int)
      val all   = first.concatAsyncWithJvmType(() => Async.succeed(async(3)), JvmType.Int)
      for {
        values <- runAsync(all.readN[Int](4)); ready <- runAsync(all.readable()); end <- runAsync(all.readInt(-1L))
      } yield assertTrue(values == Chunk(1, 2, 3), !ready, end == -1L, all.jvmType == JvmType.Int)
    },
    test("default asynchronous reader chunk and bulk loops reach exact limits") {
      final class DefaultReader(initial: List[Int]) extends Reader.AsyncReader[Int] {
        private var values                        = initial
        override def jvmType                      = JvmType.Int
        override private[streams] def tryReadable = if (values.nonEmpty) Reader.Available else Reader.Unavailable
        def close()                               = Async.succeed(())
        def isClosed                              = Async.succeed(values.isEmpty)
        def readable()                            = Async.succeed(values.nonEmpty)
        def read[A >: Int](sentinel: A)           = values match {
          case head :: tail => values = tail; Async.succeed(head.asInstanceOf[A])
          case Nil          => Async.succeed(sentinel)
        }
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = values match {
          case head :: tail => values = tail; Async.succeed(head.toLong)
          case Nil          => Async.succeed(sentinel)
        }
      }
      val chunkReader = new DefaultReader(List(1, 2))
      val bulkReader  = new DefaultReader(List(3, 4))
      val dest        = Array.fill(2)(-1)
      for {
        chunk <- runAsync(chunkReader.readN[Int](2))
        count <- runAsync(bulkReader.readInts(dest, 0, 2))
      } yield assertTrue(chunk == Chunk(1, 2), count == 2, dest.sameElements(Array(3, 4)))
    },
    test("default asynchronous chunk loops cover zero EOF and cooperative rescheduling") {
      final class ReadyReader(limit: Int) extends Reader.AsyncReader[Int] {
        private var next                          = 0
        override def jvmType                      = JvmType.Int
        override private[streams] def tryReadable = if (next < limit) Reader.Available else Reader.Unavailable
        def close()                               = Async.succeed { next = limit; () }
        def isClosed                              = Async.succeed(next >= limit)
        def readable()                            = Async.succeed(next < limit)
        def read[A >: Int](sentinel: A)           =
          if (next >= limit) Async.succeed(sentinel)
          else { val value = next; next += 1; Async.succeed(value.asInstanceOf[A]) }
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
          if (next >= limit) Async.succeed(sentinel)
          else { val value = next; next += 1; Async.succeed(value.toLong) }
      }
      val reader = new ReadyReader(258)
      for {
        zero   <- runAsync(reader.readN[Int](0))
        values <- runAsync(reader.readN[Int](258))
        eof    <- runAsync(reader.readN[Int](1))
        upTo   <- runAsync(reader.readUpToN[Int](1))
      } yield assertTrue(zero.isEmpty, values == Chunk.fromIterable(0 until 258), eof.isEmpty, upTo.isEmpty)
    },
    test("started concatenation uses a fresh outer node when appending another tail") {
      val first = async(1).concatAsyncWithJvmType(() => Async.succeed(async(2)), JvmType.Int)
      for {
        head <- runAsync(first.readInt(-1L))
        outer = first.concatAsyncWithJvmType(() => Async.succeed(async(3)), JvmType.Int)
        rest <- runAsync(outer.readAll[Int]())
      } yield assertTrue(head == 1L, rest == Chunk(2, 3))
    },
    test("sync-to-async marks EOF through empty chunks and byte reads") {
      val chunks = Reader.fromChunk(Chunk.empty[Int]).toAsync
      val upTo   = Reader.fromChunk(Chunk.empty[Int]).toAsync
      val bytes  = Reader.fromChunk(Chunk.empty[Byte]).toAsync
      for {
        chunk   <- runAsync(chunks.readN[Int](1)); chunkClosed     <- runAsync(chunks.isClosed)
        partial <- runAsync(upTo.readUpToN[Int](1)); partialClosed <- runAsync(upTo.isClosed)
        byte    <- runAsync(bytes.readByte()); byteClosed          <- runAsync(bytes.isClosed)
      } yield assertTrue(chunk.isEmpty, chunkClosed, partial.isEmpty, partialClosed, byte == -1, byteClosed)
    },
    test("async repeated reports activity, resets after EOF, rejects overlap, and stops after close") {
      val gate    = new Completer[String]
      val pending = new Reader.AsyncReader[String] {
        def close()                        = Async.succeed(())
        def isClosed                       = Async.succeed(false)
        def readable()                     = Async.succeed(true)
        def read[A >: String](sentinel: A) = gate.asInstanceOf[Async[A]]
      }
      val repeated    = Reader.repeated(async(1, 2))
      val overlapping = Reader.repeated(pending)
      val first       = overlapping.read[String]("eof").start
      for {
        values  <- runAsync(repeated.readN[Int](5)); ready              <- runAsync(repeated.readable())
        overlap <- runAsync(overlapping.read[String]("other").either); _ = gate.succeed("value");
        value   <- runAsync(first)
        _       <- runAsync(repeated.close()); closedReady              <- runAsync(repeated.readable());
        end     <- runAsync(repeated.readInt(-1L))
      } yield assertTrue(
        values == Chunk(1, 2, 1, 2, 1),
        ready,
        overlap.left.exists(_.isInstanceOf[IllegalStateException]),
        value == "value",
        !closedReady,
        end == -1L
      )
    },
    test("unfoldAsync preserves state, exposes overlap, terminal failure, EOF, and reset") {
      val gate    = new Completer[Option[(Int, Int)]]
      val entered = new Completer[Unit]
      val pending = Reader.unfoldAsync(0) { _ => entered.succeed(()); gate }
      val first   = pending.read[Int](-1).start
      val failure = new IllegalStateException("unfold")
      val failed  = Reader.unfoldAsync[Int, Int](0)(_ => Async.fail(failure))
      val finite  = Reader.unfoldAsync[Int, Int](0)(n => Async.succeed(if (n < 2) Some((n, n + 1)) else None))
      for {
        _            <- runAsync(entered.peek); overlap               <- runAsync(pending.read[Int](-2).either)
        _             = gate.succeed(Some((7, 1))); value             <- runAsync(first)
        failedResult <- runAsync(failed.read[Int](-1).either); replay <- runAsync(failed.read[Int](-2).either)
        values       <- runAsync(finite.readN[Int](3)); closed        <- runAsync(finite.isClosed); _ <- runAsync(finite.reset())
        again        <- runAsync(finite.read[Int](-1))
      } yield assertTrue(
        overlap.left.exists(_.isInstanceOf[IllegalStateException]),
        value == 7,
        failedResult == Left(failure),
        replay == Left(failure),
        values == Chunk(0, 1),
        closed,
        again == 0
      )
    }
  ) @@ TestAspect.sequential
}

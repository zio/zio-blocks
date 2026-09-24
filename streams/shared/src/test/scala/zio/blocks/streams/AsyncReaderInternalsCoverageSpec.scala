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
import zio.blocks.streams.internal.{AsyncConcurrentReaders, AsyncStatefulReader}
import zio.blocks.streams.io.Reader
import zio.test._

/** Focused state-machine tests for the package-private asynchronous readers. */
object AsyncReaderInternalsCoverageSpec extends StreamsBaseSpec {
  private def source[A: JvmType.Infer](values: A*): Reader.AsyncReader[A] =
    Reader.fromChunk(Chunk.fromIterable(values)).toAsync

  private final class PendingInt(closeResult: Async[Unit] = Async.succeed(())) extends Reader.AsyncReader[Int] {
    val entered                               = new Completer[Unit]
    val value                                 = new Completer[Long]
    val closes                                = new AtomicInteger
    override def jvmType                      = JvmType.Int
    def close(): Async[Unit]                  = { closes.incrementAndGet(); value.succeed(Long.MinValue); closeResult }
    def isClosed: Async[Boolean]              = Async.succeed(closes.get() != 0)
    def readable(): Async[Boolean]            = Async.succeed(true)
    def read[A >: Int](sentinel: A): Async[A] =
      readInt(Long.MinValue).map(v => if (v == Long.MinValue) sentinel else v.toInt)
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = { entered.succeed(()); value }
  }

  def spec = suite("async reader internals coverage")(
    test("buffered primitive caches expose all availability states and exact/partial/empty reads") {
      def exercise[A: JvmType.Infer](values: Chunk[A]): Async[Chunk[A]] = {
        val reader = AsyncStatefulReader.buffered(Reader.fromChunk(values).toAsync, 2)
        reader.readN[A](-1).flatMap(_ => reader.readN[A](0)).flatMap(_ => reader.readN[A](values.length + 2))
      }
      val ints = AsyncStatefulReader.buffered(source(1, 2, 3), 2)
      val dest = Array.fill(5)(-9)
      for {
        before <- runAsync(ints.readable()); first           <- runAsync(ints.readInts(dest, 1, 1));
        cached <- runAsync(ints.readable())
        second <- runAsync(ints.readInts(dest, 2, 2)); third <- runAsync(ints.readInts(dest, 3, 1))
        eof    <- runAsync(ints.readInts(dest, 0, 1)); after <- runAsync(ints.readable())
        bs     <- runAsync(exercise(Chunk[Byte](1, 2))); ls  <- runAsync(exercise(Chunk(1L, 2L)))
        fs     <- runAsync(exercise(Chunk(1f, 2f))); ds      <- runAsync(exercise(Chunk(1d, 2d)))
        zs     <- runAsync(exercise(Chunk(true, false))); cs <- runAsync(exercise(Chunk('a', 'b')))
        ss     <- runAsync(exercise(Chunk[Short](1, 2))); rs <- runAsync(exercise(Chunk("a", "b")))
        _      <- runAsync(ints.close()); closedTry           = ints.tryReadable
      } yield assertTrue(
        before,
        first == 1,
        cached,
        second == 1,
        third == 1,
        eof == -1,
        !after,
        dest.toVector == Vector(-9, 1, 2, 3, -9),
        bs == Chunk[Byte](1, 2),
        ls == Chunk(1L, 2L),
        fs == Chunk(1f, 2f),
        ds == Chunk(1d, 2d),
        zs == Chunk(true, false),
        cs == Chunk('a', 'b'),
        ss == Chunk[Short](1, 2),
        rs == Chunk("a", "b"),
        closedTry == Reader.Unavailable
      )
    },
    test("primitive intersperse retains a pending value across one-element reads in every lane") {
      def all[A: JvmType.Infer](a: A, separator: A, b: A): Async[Chunk[A]] = {
        val reader = AsyncStatefulReader.intersperse(source(a, b), separator)
        reader.readN[A](1).flatMap(x => reader.readN[A](1).flatMap(y => reader.readN[A](3).map(z => x ++ y ++ z)))
      }
      for {
        z <- runAsync(all(true, false, true)); b <- runAsync(all[Byte](1, 0, 2)); c <- runAsync(all('a', '|', 'b'))
        s <- runAsync(all[Short](1, 0, 2)); i    <- runAsync(all(1, 0, 2)); l       <- runAsync(all(1L, 0L, 2L))
        f <- runAsync(all(1f, 0f, 2f)); d        <- runAsync(all(1d, 0d, 2d)); r    <- runAsync(all("a", "|", "b"))
      } yield assertTrue(
        z == Chunk(true, false, true),
        b == Chunk[Byte](1, 0, 2),
        c == Chunk('a', '|', 'b'),
        s == Chunk[Short](1, 0, 2),
        i == Chunk(1, 0, 2),
        l == Chunk(1L, 0L, 2L),
        f == Chunk(1f, 0f, 2f),
        d == Chunk(1d, 0d, 2d),
        r == Chunk("a", "|", "b")
      )
    },
    test("close deterministically cancels a stateful pending operation and is idempotent") {
      val upstream = new PendingInt
      val reader   = AsyncStatefulReader.buffered(upstream, 2)
      val running  = reader.readInt(-7L).start
      for {
        _      <- runAsync(upstream.entered); _ <- runAsync(reader.close()); _          <- runAsync(reader.close())
        result <- runAsync(running); closed     <- runAsync(reader.isClosed); available <- runAsync(reader.readable())
      } yield assertTrue(result == -7L, closed, !available, upstream.closes.get() == 1)
    },
    test("concurrent pull cancellation before polling and after completion is stable") {
      val neverStarted = AsyncConcurrentReaders.mapPar[Int, Int](source(1), 1, Async.succeed, JvmType.Int)
      val unpolled     = neverStarted.readInt(-1L)
      for {
        _              <- runAsync(Async.cancelWithCleanup(unpolled.asInstanceOf[Pollable[Long]]))
        cancelledValue <- runAsync(unpolled)
        completed       = AsyncConcurrentReaders.mapPar[Int, Int](source(3), 1, Async.succeed, JvmType.Int)
        operation       = completed.readInt(-3L).start
        value          <- runAsync(operation); _         <- runAsync(Async.cancelWithCleanup(operation))
        _              <- runAsync(completed.close()); _ <- runAsync(completed.close())
      } yield assertTrue(cancelledValue == -1L, value == 3L)
    }
  )
}

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

/** Public-behavior regression tests for residual Reader state-machine paths. */
object ReaderCriticalResidualAug20Spec extends StreamsBaseSpec {
  private def source[A: JvmType.Infer](values: A*): Reader.AsyncReader[A] =
    Reader.fromChunk(Chunk.fromIterable(values)).toAsync

  def spec = suite("Reader critical residual Aug 20")(
    test("generic async readN crosses its fairness boundary without dropping or duplicating values") {
      val expected = Chunk.fromIterable(0 until 300)
      val reader   = new Reader.AsyncReader[Int] {
        private var index               = 0
        def close()                     = Async.succeed { index = expected.length; () }
        def isClosed                    = Async.succeed(index >= expected.length)
        def readable()                  = Async.succeed(index < expected.length)
        def read[A >: Int](sentinel: A) = Async.succeed {
          if (index >= expected.length) sentinel
          else { val value = expected(index); index += 1; value.asInstanceOf[A] }
        }
      }
      for {
        values <- runAsync(reader.readN[Int](300))
        eof    <- runAsync(reader.readN[Int](1))
      } yield assertTrue(values == expected, eof == Chunk.empty)
    },
    test("sync-to-async adapter delegates bounded reads, byte reads, and reopens EOF on repeat") {
      val reader    = Reader.fromChunk(Chunk[Byte](0, -1, 127)).toAsync
      val repeating = Reader.singleByte(23).toAsync
      for {
        first  <- runAsync(reader.readN[Byte](2))
        last   <- runAsync(reader.readUpToN[Byte](2))
        eof    <- runAsync(reader.readByte())
        once   <- runAsync(repeating.readByte())
        repeat <- runAsync(repeating.setRepeat())
        again  <- runAsync(repeating.readByte())
      } yield assertTrue(
        first == Chunk[Byte](0, -1),
        last == Chunk[Byte](127),
        eof == -1,
        once == 23,
        repeat,
        again == 23
      )
    },
    test("release wrapper delegates every residual scalar lane and generic read") {
      val releases                            = new AtomicInteger
      def release[A: JvmType.Infer](value: A) =
        source(value).withReleaseAsync(() => Async.succeed { releases.incrementAndGet(); () })
      for {
        a <- runAsync(release("a").read("eof"))
        c <- runAsync(release('x').readChar(-1))
        s <- runAsync(release(12.toShort).readShort(-1))
        l <- runAsync(release(13L).readLong(-1L))
        f <- runAsync(release(1.5f).readFloat(-1d))
        d <- runAsync(release(2.5d).readDouble(-1d))
      } yield assertTrue(a == "a", c == 'x'.toInt, s == 12, l == 13L, f == 1.5d, d == 2.5d)
    },
    test("concat traverses empty segments, reports callback exceptions, and resets consumed chain") {
      val boom  = new IllegalStateException("tail")
      val chain =
        source[Int]().concatAsync(() => Async.succeed(source[Int]())).concatAsync(() => Async.succeed(source(7)))
      val bad = source[Int]().concatAsync(() => throw boom)
      for {
        value   <- runAsync(chain.read[Int](-1))
        eof     <- runAsync(chain.read[Int](-2))
        _       <- runAsync(chain.reset())
        replay  <- runAsync(chain.read[Int](-3))
        failure <- runAsync(bad.read[Int](-4)).either
        _       <- runAsync(chain.close())
        reset   <- runAsync(chain.reset()).either
      } yield assertTrue(value == 7, eof == -2, replay == 7, failure.left.toOption.contains(boom), reset.isLeft)
    },
    test("repeated preserves primitive metadata, rejects concurrent pull, and replays terminal failure") {
      val gate  = new Completer[Int]
      val inner = new Reader.AsyncReader[Int] {
        override def jvmType                                                        = JvmType.Int
        def close()                                                                 = Async.succeed(())
        def isClosed                                                                = Async.succeed(false)
        def readable()                                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A)                                             = gate.map(_.asInstanceOf[A])
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = gate.map(_.toLong)
      }
      val reader = Reader.repeated(inner)
      val first  = reader.read[Int](-1).start
      for {
        second <- runAsync(reader.read[Int](-2)).either
        _       = gate.succeed(9)
        value  <- runAsync(first)
      } yield assertTrue(
        reader.jvmType == JvmType.Int,
        second.left.exists(_.isInstanceOf[IllegalStateException]),
        value == 9
      )
    },
    test("unfold exposes readable/closed transitions and serializes callback state") {
      val calls  = new AtomicInteger
      val reader = Reader.unfoldAsync(0) { state =>
        calls.incrementAndGet()
        Async.succeed(if (state < 2) Some((state, state + 1)) else None)
      }
      for {
        ready0 <- runAsync(reader.readable())
        a      <- runAsync(reader.read[Int](-1))
        ready1 <- runAsync(reader.readable())
        b      <- runAsync(reader.read[Int](-2))
        eof    <- runAsync(reader.read[Int](-3))
        closed <- runAsync(reader.isClosed)
        ready2 <- runAsync(reader.readable())
      } yield assertTrue(ready0, a == 0, ready1, b == 1, eof == -3, closed, !ready2, calls.get == 3)
    }
  )
}

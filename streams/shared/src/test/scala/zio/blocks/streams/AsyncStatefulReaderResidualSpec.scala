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
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.internal.AsyncStatefulReader
import zio.blocks.streams.io.Reader
import zio.test._

object AsyncStatefulReaderResidualSpec extends ZIOSpecDefault {
  private def runAsync[A](effect: Async[A]): ZIO[Any, Throwable, A] = {
    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.parasitic
    ZIO.fromFuture(_ => effect.toFuture)
  }

  private def source[A: JvmType.Infer](values: A*): Reader.AsyncReader[A] =
    Reader.fromChunk(Chunk.fromIterable(values)).toAsync

  private final class ResetGate extends Reader.AsyncReader[Int] {
    val resetEntered                                               = new Completer[Unit]
    val resetGate                                                  = new Completer[Unit]
    var closes                                                     = 0
    override def jvmType                                           = JvmType.Int
    def close()                                                    = { closes += 1; Async.succeed(()) }
    override def reset()                                           = { resetEntered.succeed(()); resetGate }
    def isClosed                                                   = Async.succeed(closes != 0)
    def readable()                                                 = Async.succeed(false)
    def read[A >: Int](sentinel: A)                                = Async.succeed(sentinel)
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int) = Async.succeed(sentinel)
  }

  private final class ReentrantInt(values: Vector[Int], closeAt: Int = 1) extends Reader.AsyncReader[Int] {
    var boundary: Reader.AsyncReader[_]                            = null
    private var index                                              = 0
    override def jvmType                                           = JvmType.Int
    def close()                                                    = Async.succeed(())
    def isClosed                                                   = Async.succeed(false)
    def readable()                                                 = Async.succeed(index < values.length)
    override private[streams] def tryReadable                      = if (index < values.length) Reader.Available else Reader.Unavailable
    def read[A >: Int](sentinel: A)                                = readInt(Long.MinValue).map(n => if (n == Long.MinValue) sentinel else n.toInt)
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int) =
      if (index >= values.length) Async.succeed(sentinel)
      else {
        val value = values(index)
        index += 1
        if (index == closeAt) boundary.close().map(_ => value.toLong) else Async.succeed(value.toLong)
      }
  }

  private final class ReentrantRef(value: String) extends Reader.AsyncReader[String] {
    var boundary: Reader.AsyncReader[_] = null
    private var readOnce                = false
    def close()                         = Async.succeed(())
    def isClosed                        = Async.succeed(false)
    def readable()                      = Async.succeed(!readOnce)
    def read[A >: String](sentinel: A)  =
      if (readOnce) Async.succeed(sentinel)
      else {
        readOnce = true
        boundary.close().map(_ => value.asInstanceOf[A])
      }
  }

  def spec = suite("AsyncStatefulReader residual branches")(
    test("close claims and invalidates a reset which is already in progress") {
      val inner  = new ResetGate
      val reader = AsyncStatefulReader.buffered(inner, 2)
      val reset  = reader.reset().start
      for {
        _      <- runAsync(inner.resetEntered)
        close   = reader.close()
        _       = close.asInstanceOf[Pollable[Unit]].poll(new Runnable { def run(): Unit = () })
        _       = inner.resetGate.succeed(())
        result <- runAsync(reset).either
        _      <- runAsync(close)
        closed <- runAsync(reader.isClosed)
      } yield assertTrue(
        result.left.exists(_.getMessage == "Reader was closed during reset"),
        closed,
        inner.closes == 2
      )
    },
    test("availability distinguishes delegated, pending, cached, done, and closed states") {
      val emptyBuffered = AsyncStatefulReader.buffered(source[Int](), 2)
      val primitive     = AsyncStatefulReader.intersperse(source(1, 2), 0)
      val refs          = AsyncStatefulReader.intersperse(source("a", "b"), "|")
      val chunks        = AsyncStatefulReader.chunked(source[Int](), 2)
      val windows       = AsyncStatefulReader.sliding(source(1), 2, 1)
      val one           = new Array[Int](2)
      for {
        eb <- runAsync(emptyBuffered.readable())
        _  <- runAsync(primitive.readInts(one, 0, 2))
        pa  = primitive.tryReadable
        pr <- runAsync(primitive.readable())
        _  <- runAsync(refs.read[String](null)); _               <- runAsync(refs.read[String](null))
        rr <- runAsync(refs.readable())
        cr <- runAsync(chunks.readable())
        _  <- runAsync(windows.read[Chunk[Int]](Chunk.empty)); _ <- runAsync(windows.read[Chunk[Int]](Chunk.empty))
        wr <- runAsync(windows.readable())
        _  <- runAsync(primitive.close()); pc                     = primitive.tryReadable
      } yield assertTrue(!eb, pa == Reader.Available, pr, rr, !cr, !wr, pc == Reader.Unavailable)
    },
    test("primitive intersperse resumes with a pending value at the cooperative-yield boundary") {
      val reader = AsyncStatefulReader.intersperse(source((0 until 260): _*), -1)
      val prefix = new Array[Int](2)
      for {
        p    <- runAsync(reader.readInts(prefix, 0, 2))
        rest <- runAsync(reader.readN[Int](517))
        e    <- runAsync(reader.readInt(Long.MinValue))
      } yield assertTrue(
        p == 2,
        prefix.sameElements(Array(0, -1)),
        rest.length == 517,
        rest.head == 1,
        rest.last == 259,
        e == Long.MinValue
      )
    },
    test("reentrant close makes primitive and reference intersperse commits stale") {
      val ints = new ReentrantInt(Vector(1))
      val pi   = AsyncStatefulReader.intersperse(ints, 0)
      ints.boundary = pi
      val refs = new ReentrantRef("a")
      val ri   = AsyncStatefulReader.intersperse(refs, "|")
      refs.boundary = ri
      for {
        i <- runAsync(pi.readInt(-7L))
        r <- runAsync(ri.read[String]("closed"))
      } yield assertTrue(i == -7L, r == "closed")
    },
    test("reentrant close interrupts sliding while discarding and while filling") {
      val fillSource = new ReentrantRef("x")
      val fill       = AsyncStatefulReader.sliding(fillSource, 2, 1)
      fillSource.boundary = fill

      val discardSource = new ReentrantInt(Vector(1, 2, 3), closeAt = 2)
      val discard       = AsyncStatefulReader.sliding(discardSource, 1, 3)
      discardSource.boundary = discard
      for {
        f     <- runAsync(fill.read[Chunk[String]](Chunk.empty))
        first <- runAsync(discard.read[Chunk[Int]](Chunk.empty))
        d     <- runAsync(discard.read[Chunk[Int]](Chunk.empty))
      } yield assertTrue(f.isEmpty, first == Chunk(1), d.isEmpty)
    }
  ) @@ TestAspect.sequential
}

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

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.scalajs.concurrent.QueueExecutionContext
import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array

import zio.ZIO
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.internal.StreamError
import zio.test._

object ReadableStreamReadersSpec extends StreamsBaseSpec {
  private implicit val executionContext: ExecutionContext = QueueExecutionContext.timeouts()

  private final class TestReader(responses: mutable.Queue[js.Promise[ReadableStreamReaders.ReadResult]])
      extends js.Object {
    var reads                                                = 0
    var cancels                                              = 0
    var releases                                             = 0
    val cancelSignal                                         = new Completer[Unit]
    def read(): js.Promise[ReadableStreamReaders.ReadResult] = {
      reads += 1
      responses.dequeue()
    }
    def cancel(): js.Promise[Unit] = {
      cancels += 1
      cancelSignal.succeed(())
      js.Promise.resolve[Unit](())
    }
    def releaseLock(): Unit = releases += 1
  }

  private final class TestStream(reader: TestReader) extends js.Object {
    def getReader(): ReadableStreamReaders.StreamReader =
      reader.asInstanceOf[ReadableStreamReaders.StreamReader]
  }

  private final class ReleasingReader extends js.Object {
    var reads                                                = 0
    var cancels                                              = 0
    var releases                                             = 0
    private var rejectRead: js.Function1[Any, Any]           = null
    def read(): js.Promise[ReadableStreamReaders.ReadResult] = {
      reads += 1
      new js.Promise[ReadableStreamReaders.ReadResult]((_, reject) => rejectRead = reject)
    }
    def cancel(): js.Promise[Unit] = { cancels += 1; js.Promise.resolve[Unit](()) }
    def releaseLock(): Unit        = {
      releases += 1
      rejectRead(new RuntimeException("released"))
      ()
    }
  }

  private final class NeverSettlingReader extends js.Object {
    var reads                                                 = 0
    var cancels                                               = 0
    var releases                                              = 0
    var resolveRead: ReadableStreamReaders.ReadResult => Unit = null
    def read(): js.Promise[ReadableStreamReaders.ReadResult]  = {
      reads += 1
      new js.Promise[ReadableStreamReaders.ReadResult]((resolve, _) => resolveRead = value => { resolve(value); () })
    }
    def cancel(): js.Promise[Unit] = {
      cancels += 1
      if (resolveRead ne null) resolveRead(result(done = true))
      js.Promise.resolve[Unit](())
    }
    def releaseLock(): Unit = releases += 1
  }

  private def result(done: Boolean, bytes: Int*): ReadableStreamReaders.ReadResult = {
    val value = new Uint8Array(bytes.length)
    var i     = 0
    while (i < bytes.length) { value(i) = bytes(i).toShort; i += 1 }
    js.Dynamic.literal(done = done, value = value).asInstanceOf[ReadableStreamReaders.ReadResult]
  }

  private def resolved(done: Boolean, bytes: Int*): js.Promise[ReadableStreamReaders.ReadResult] =
    js.Promise.resolve[ReadableStreamReaders.ReadResult](result(done, bytes: _*))

  private def rejected(cause: Throwable): js.Promise[ReadableStreamReaders.ReadResult] =
    new js.Promise[ReadableStreamReaders.ReadResult]((_, reject) => reject(cause))

  private def stream(reader: TestReader): ReadableStreamReaders.ReadableStream =
    new TestStream(reader).asInstanceOf[ReadableStreamReaders.ReadableStream]

  private def run[A](effect: Async[A]): ZIO[Any, Throwable, A] = ZIO.fromFuture(_ => effect.toFuture)

  def spec = suite("ReadableStream readers")(
    test("undriven pulls are inert and observe drive-time lifecycle state") {
      val underlying = new TestReader(mutable.Queue(resolved(false, 7), resolved(false, 8)))
      val reader     = ReadableStreamReaders.fromReadableStream(stream(underlying))
      val first      = reader.readByte()
      val second     = reader.readByte()
      val before     = underlying.reads
      for {
        a   <- run(first)
        b   <- run(second)
        late = reader.readByte()
        _   <- run(reader.close())
        c   <- run(late)
      } yield assertTrue(before == 0, a == 7, b == 8, c == -1, underlying.reads == 2)
    },
    test("skips empty chunks, preserves buffered bytes, and reaches EOF without prefetch") {
      val underlying = new TestReader(
        mutable.Queue(resolved(false), resolved(false, 255, 1, 2), resolved(true))
      )
      val reader = ReadableStreamReaders.fromReadableStream(stream(underlying))
      val dest   = new Array[Byte](3)
      for {
        first    <- run(reader.readByte())
        count    <- run(reader.readBytes(dest, 1, 1))
        last     <- run(reader.readByte())
        eof      <- run(reader.readByte())
        closed   <- run(reader.isClosed)
        readable <- run(reader.readable())
      } yield assertTrue(
        first == 255,
        count == 1,
        dest.toList == List(0.toByte, 1.toByte, 0.toByte),
        last == 2,
        eof == -1,
        underlying.reads == 3,
        closed,
        !readable
      )
    },
    test("managed and unmanaged close perform exactly their owned action") {
      val managedUnderlying   = new TestReader(mutable.Queue.empty)
      val unmanagedUnderlying = new TestReader(mutable.Queue.empty)
      val managed             = ReadableStreamReaders.fromReadableStream(stream(managedUnderlying))
      val unmanaged           = ReadableStreamReaders.fromReadableStreamUnmanaged(stream(unmanagedUnderlying))
      for {
        _ <- run(managed.close())
        _ <- run(managed.close())
        _ <- run(unmanaged.close())
        _ <- run(unmanaged.close())
      } yield assertTrue(
        managedUnderlying.cancels == 1,
        managedUnderlying.releases == 1,
        unmanagedUnderlying.cancels == 0,
        unmanagedUnderlying.releases == 1
      )
    },
    test("managed close joins a pending native read and survives an abandoned waiter") {
      var complete: ReadableStreamReaders.ReadResult => Unit = null
      val delayed                                            = new js.Promise[ReadableStreamReaders.ReadResult]((resolve, _) => {
        complete = value => { resolve(value); () }
      })
      val underlying = new TestReader(mutable.Queue(delayed))
      val reader     = ReadableStreamReaders.fromReadableStream(stream(underlying))
      val pending    = reader.readByte().start
      val abandoned  = reader.close().start
      abandoned.cancel()
      val joined                   = reader.close().start
      val before                   = joined.poll(new Runnable { def run(): Unit = () })
      val releasedBeforeSettlement = underlying.releases
      for {
        _     <- ZIO.succeed(complete(result(false, 42)))
        value <- run(pending)
        _     <- run(joined)
      } yield assertTrue(
        before.isInstanceOf[Pollable[?]],
        releasedBeforeSettlement == 0,
        value == -1,
        underlying.cancels == 1,
        underlying.releases == 1
      )
    },
    test("unmanaged close releases a pending read without cancelling its stream") {
      val underlying = new ReleasingReader
      val reader     = ReadableStreamReaders.fromReadableStreamUnmanaged(
        new js.Object {
          def getReader(): ReadableStreamReaders.StreamReader =
            underlying.asInstanceOf[ReadableStreamReaders.StreamReader]
        }.asInstanceOf[ReadableStreamReaders.ReadableStream]
      )
      val pending = reader.readByte().start
      for {
        _     <- ZIO.succeed(assertTrue(underlying.reads == 1))
        _     <- run(reader.close())
        value <- run(pending)
      } yield assertTrue(value == -1, underlying.cancels == 0, underlying.releases == 1)
    },
    test("unmanaged close does not await a caller-owned pending read and rejects its late completion") {
      val underlying = new NeverSettlingReader
      val reader     = ReadableStreamReaders.fromReadableStreamUnmanaged(
        new js.Object {
          def getReader(): ReadableStreamReaders.StreamReader =
            underlying.asInstanceOf[ReadableStreamReaders.StreamReader]
        }.asInstanceOf[ReadableStreamReaders.ReadableStream]
      )
      val pending = reader.readByte().start
      for {
        _     <- ZIO.succeed(assertTrue(underlying.reads == 1))
        _     <- run(reader.close())
        value <- run(pending)
        _     <- ZIO.succeed(underlying.resolveRead(result(false, 42)))
        later <- run(reader.readByte())
      } yield assertTrue(
        value == -1,
        later == -1,
        underlying.cancels == 0,
        underlying.releases == 1,
        underlying.reads == 1
      )
    },
    test("a concurrent second pull fails without issuing another native read") {
      val underlying = new NeverSettlingReader
      val reader     = ReadableStreamReaders.fromReadableStream(
        new js.Object {
          def getReader(): ReadableStreamReaders.StreamReader =
            underlying.asInstanceOf[ReadableStreamReaders.StreamReader]
        }.asInstanceOf[ReadableStreamReaders.ReadableStream]
      )
      val first = reader.readByte().start
      for {
        second <- run(reader.readByte().either)
        _      <- run(reader.close())
        eof    <- run(first)
      } yield assertTrue(
        second.left.exists(_.isInstanceOf[IllegalStateException]),
        underlying.reads == 1,
        underlying.cancels == 1,
        underlying.releases == 1,
        eof == -1
      )
    },
    test("managed close wins a promise rejection race and rejects the stale settlement") {
      var rejectRead: Any => Unit = null
      val promise                 =
        new js.Promise[ReadableStreamReaders.ReadResult]((_, reject) => rejectRead = cause => { reject(cause); () })
      val underlying = new TestReader(mutable.Queue(promise))
      val reader     = ReadableStreamReaders.fromReadableStream(stream(underlying))
      val pending    = reader.readByte().start
      val late       = new RuntimeException("late-rejection")
      val closing    = reader.close().start
      for {
        _     <- run(underlying.cancelSignal)
        _      = rejectRead(late)
        _     <- run(closing)
        value <- run(pending)
        eof   <- run(reader.readByte())
      } yield assertTrue(
        value == -1,
        eof == -1,
        underlying.reads == 1,
        underlying.cancels == 1,
        underlying.releases == 1
      )
    },
    test("promise rejection is trusted, sticky, and replayed by identity") {
      val cause      = new RuntimeException("rejected")
      val underlying = new TestReader(mutable.Queue(rejected(cause)))
      val reader     = ReadableStreamReaders.fromReadableStream(stream(underlying))
      for {
        first    <- run(reader.readByte().either)
        second   <- run(reader.readByte().either)
        closed   <- run(reader.isClosed)
        readable <- run(reader.readable())
      } yield {
        val one = first.swap.toOption.get
        val two = second.swap.toOption.get
        assertTrue(
          one.isInstanceOf[StreamError],
          one.asInstanceOf[StreamError].isTrusted,
          one eq two,
          underlying.reads == 1,
          closed,
          !readable
        )
      }
    },
    test("sticky failure precedes valid zero-length reads") {
      val cause      = new RuntimeException("zero")
      val underlying = new TestReader(mutable.Queue(rejected(cause)))
      val reader     = ReadableStreamReaders.fromReadableStream(stream(underlying))
      val dest       = new Array[Byte](1)
      for {
        failed <- run(reader.readByte().either)
        chunk  <- run(reader.readN[Byte](0).either)
        bulk   <- run(reader.readBytes(dest, 0, 0).either)
      } yield {
        val failure = failed.swap.toOption.get
        assertTrue(chunk == Left(failure), bulk == Left(failure))
      }
    },
    test("specialized chunk pulls preserve bytes, zero length, and sticky EOF without repulling") {
      val underlying = new TestReader(mutable.Queue(resolved(false, 1, 2, 255), resolved(true)))
      val reader     = ReadableStreamReaders.fromReadableStream(stream(underlying))
      for {
        head     <- run(reader.readUpToN[Byte](2))
        tail     <- run(reader.readN[Byte](1))
        empty    <- run(reader.readUpToN[Byte](0))
        eof      <- run(reader.readByte())
        eofAgain <- run(reader.readByte())
      } yield assertTrue(
        head == Chunk[Byte](1, 2),
        tail == Chunk[Byte](255.toByte),
        empty == Chunk.empty,
        eof == -1,
        eofAgain == -1,
        underlying.reads == 2
      )
    }
  )
}

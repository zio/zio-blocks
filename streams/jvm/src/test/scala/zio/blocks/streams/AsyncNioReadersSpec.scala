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
import zio.blocks.streams.internal.StreamError
import zio.blocks.streams.io.Reader
import zio.test._

import java.io.IOException
import java.net.{SocketAddress, SocketOption}
import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousByteChannel, AsynchronousSocketChannel, CompletionHandler}
import java.nio.channels.spi.AsynchronousChannelProvider
import java.util.{Collections, Set}
import java.util.concurrent.{CompletableFuture, CountDownLatch, Future, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger

object AsyncNioReadersSpec extends StreamsBaseSpec {
  private final class TestChannel(
    closeFailure: Throwable = null,
    readFailure: Throwable = null,
    futureReadFailure: Throwable = null,
    inlineByte: Int = -1,
    onRead: () => Unit = () => (),
    onFutureRead: () => Unit = () => (),
    onFutureCancel: () => Unit = () => ()
  ) extends AsynchronousByteChannel {
    private var open                                                        = true
    private var pending: (ByteBuffer, CompletionHandler[Integer, Any], Any) = null
    private var futurePending: (ByteBuffer, CompletableFuture[Integer])     = null
    @volatile private var latestFuture: CompletableFuture[Integer]          = null
    val reads                                                               = new AtomicInteger(0)
    val closes                                                              = new AtomicInteger(0)

    def awaitReads(expected: Int): Unit  = awaitCount(reads, expected, "channel reads")
    def awaitCloses(expected: Int): Unit = awaitCount(closes, expected, "channel closes")
    def futureCancelled: Boolean         = (latestFuture ne null) && latestFuture.isCancelled

    private def awaitCount(counter: AtomicInteger, expected: Int, label: String): Unit = {
      val deadline = System.nanoTime() + 5000000000L
      while (counter.get() < expected && System.nanoTime() < deadline) Thread.`yield`()
      if (counter.get() < expected)
        throw new AssertionError(s"timed out waiting for $expected $label; observed ${counter.get()}")
    }

    def isOpen: Boolean = synchronized(open)
    def close(): Unit   = synchronized {
      if (open) {
        open = false
        closes.incrementAndGet()
        if (closeFailure ne null) throw closeFailure
      }
    }

    def read[A](buffer: ByteBuffer, attachment: A, handler: CompletionHandler[Integer, ? >: A]): Unit =
      synchronized {
        reads.incrementAndGet()
        onRead()
        if (readFailure ne null) throw readFailure
        else if (inlineByte >= 0) {
          buffer.put(inlineByte.toByte)
          handler.completed(Integer.valueOf(1), attachment)
        } else pending = (buffer, handler.asInstanceOf[CompletionHandler[Integer, Any]], attachment)
      }

    def complete(bytes: Byte*): Unit = {
      val operation = synchronized { val value = pending; pending = null; value }
      if (operation ne null) {
        bytes.foreach(operation._1.put)
        operation._2.completed(Integer.valueOf(bytes.length), operation._3)
      } else {
        val future = synchronized { val value = futurePending; futurePending = null; value }
        bytes.foreach(future._1.put)
        future._2.complete(Integer.valueOf(bytes.length))
      }
    }

    def zero(): Unit = {
      val operation = synchronized { val value = pending; pending = null; value }
      operation._2.completed(Integer.valueOf(0), operation._3)
    }

    def eof(): Unit = {
      val operation = synchronized { val value = pending; pending = null; value }
      if (operation ne null) operation._2.completed(Integer.valueOf(-1), operation._3)
      else {
        val future = synchronized { val value = futurePending; futurePending = null; value }
        future._2.complete(Integer.valueOf(-1))
      }
    }

    def fail(cause: Throwable): Unit = {
      val operation = synchronized { val value = pending; pending = null; value }
      if (operation ne null) operation._2.failed(cause, operation._3)
      else {
        val future = synchronized { val value = futurePending; futurePending = null; value }
        future._2.completeExceptionally(cause)
      }
    }

    def read(buffer: ByteBuffer): Future[Integer] = synchronized {
      reads.incrementAndGet()
      onRead()
      onFutureRead()
      if (futureReadFailure ne null) throw futureReadFailure
      val future = new CompletableFuture[Integer] {
        override def cancel(mayInterruptIfRunning: Boolean): Boolean = {
          onFutureCancel()
          super.cancel(mayInterruptIfRunning)
        }
      }
      latestFuture = future
      futurePending = (buffer, future)
      future
    }
    def write[A](buffer: ByteBuffer, attachment: A, handler: CompletionHandler[Integer, ? >: A]): Unit =
      handler.failed(new UnsupportedOperationException, attachment)
    def write(buffer: ByteBuffer): Future[Integer] =
      CompletableFuture.failedFuture(new UnsupportedOperationException)
  }

  private final class TestSocketChannel extends AsynchronousSocketChannel(AsynchronousChannelProvider.provider()) {
    private val channel = new TestChannel

    def awaitReads(expected: Int): Unit = channel.awaitReads(expected)
    def complete(bytes: Byte*): Unit    = channel.complete(bytes: _*)
    def eof(): Unit                     = channel.eof()

    def isOpen: Boolean = channel.isOpen
    def close(): Unit   = channel.close()

    def bind(local: SocketAddress): AsynchronousSocketChannel                    = this
    def setOption[T](name: SocketOption[T], value: T): AsynchronousSocketChannel = this
    def getOption[T](name: SocketOption[T]): T                                   = null.asInstanceOf[T]
    def supportedOptions(): Set[SocketOption[?]]                                 = Collections.emptySet()
    def shutdownInput(): AsynchronousSocketChannel                               = this
    def shutdownOutput(): AsynchronousSocketChannel                              = this
    def getRemoteAddress(): SocketAddress                                        = null
    def getLocalAddress(): SocketAddress                                         = null

    def connect[A](remote: SocketAddress, attachment: A, handler: CompletionHandler[Void, ? >: A]): Unit =
      handler.completed(null, attachment)
    def connect(remote: SocketAddress): Future[Void] = CompletableFuture.completedFuture(null)

    def read[A](
      buffer: ByteBuffer,
      timeout: Long,
      unit: TimeUnit,
      attachment: A,
      handler: CompletionHandler[Integer, ? >: A]
    ): Unit                                       = channel.read(buffer, attachment, handler)
    def read(buffer: ByteBuffer): Future[Integer] = channel.read(buffer)
    def read[A](
      buffers: Array[ByteBuffer],
      offset: Int,
      length: Int,
      timeout: Long,
      unit: TimeUnit,
      attachment: A,
      handler: CompletionHandler[java.lang.Long, ? >: A]
    ): Unit = handler.failed(new UnsupportedOperationException, attachment)

    def write[A](
      buffer: ByteBuffer,
      timeout: Long,
      unit: TimeUnit,
      attachment: A,
      handler: CompletionHandler[Integer, ? >: A]
    ): Unit                                        = handler.failed(new UnsupportedOperationException, attachment)
    def write(buffer: ByteBuffer): Future[Integer] =
      CompletableFuture.failedFuture(new UnsupportedOperationException)
    def write[A](
      buffers: Array[ByteBuffer],
      offset: Int,
      length: Int,
      timeout: Long,
      unit: TimeUnit,
      attachment: A,
      handler: CompletionHandler[java.lang.Long, ? >: A]
    ): Unit = handler.failed(new UnsupportedOperationException, attachment)
  }

  private def await[A](effect: Async[A]): ZIO[Any, Throwable, A] = ZIO.attemptBlocking(effect.block)

  def spec = suite("Async NIO readers")(
    test("channel readers validate buffer size and expose the Byte lane") {
      val channel = new TestChannel
      val invalid = scala.util.Try(AsyncNioReaders.fromChannel(channel, 0)).failed.toOption
      val reader  = AsyncNioReaders.fromChannel(channel, 1)
      assertTrue(invalid.exists(_.isInstanceOf[IllegalArgumentException]), reader.jvmType == JvmType.Byte)
    },
    test("managed socket reader preserves partial data, EOF, and socket ownership") {
      val socket = new TestSocketChannel
      val reader = AsyncNioReaders.fromSocket(socket, bufferSize = 4)
      val first  = reader.readUpToN[Byte](2).start
      socket.awaitReads(1)
      socket.complete(1, 2, 3)
      for {
        chunk  <- await(first)
        second <- await(reader.readByte())
        end     = reader.readByte().start
        _       = socket.awaitReads(2)
        _       = socket.eof()
        eof    <- await(end)
        _      <- await(reader.close())
      } yield assertTrue(chunk == Chunk[Byte](1, 2), second == 3, eof == -1, !socket.isOpen)
    },
    test("socket factories use their default buffer size and preserve lifecycle ownership") {
      val managedSocket   = new TestSocketChannel
      val unmanagedSocket = new TestSocketChannel
      val managed         = AsyncNioReaders.fromSocket(managedSocket)
      val unmanaged       = AsyncNioReaders.fromSocketUnmanaged(unmanagedSocket)
      for {
        _            <- await(unmanaged.close())
        unmanagedOpen = unmanagedSocket.isOpen
        _            <- await(managed.close())
      } yield assertTrue(unmanagedOpen, !managedSocket.isOpen, unmanagedSocket.isOpen)
    },
    test("unmanaged socket close cancels a pending read without taking socket ownership") {
      val socket  = new TestSocketChannel
      val reader  = AsyncNioReaders.fromSocketUnmanaged(socket, bufferSize = 4)
      val pending = reader.readByte().start
      socket.awaitReads(1)
      for {
        _     <- await(reader.close())
        value <- await(pending)
      } yield assertTrue(value == -1, socket.isOpen)
    },
    test("undriven pulls are inert and observe drive-time lifecycle state") {
      val channel = new TestChannel(inlineByte = 7)
      val reader  = AsyncNioReaders.fromChannel(channel, 4)
      val first   = reader.readByte()
      val second  = reader.readByte()
      val before  = channel.reads.get()
      for {
        a   <- await(first)
        b   <- await(second)
        late = reader.readByte()
        _   <- await(reader.close())
        c   <- await(late)
      } yield assertTrue(before == 0, a == 7, b == 7, c == -1, channel.reads.get() == 2)
    },
    test("preserves partial chunks, zero-byte completions, EOF, and state queries") {
      val channel = new TestChannel
      val reader  = AsyncNioReaders.fromChannel(channel, 4)
      val first   = reader.readByte().start
      channel.awaitReads(1)
      channel.zero()
      channel.awaitReads(2)
      channel.complete(0xff.toByte, 1.toByte, 2.toByte)
      val tail = new Array[Byte](4)
      for {
        a        <- await(first)
        n        <- await(reader.readBytes(tail, 1, 1))
        b        <- await(reader.readByte())
        eofValue <- {
          val end = reader.readByte().start
          channel.awaitReads(3)
          channel.eof()
          await(end)
        }
        closed   <- await(reader.isClosed)
        readable <- await(reader.readable())
      } yield assertTrue(
        a == 255,
        n == 1,
        tail.toList == List[Byte](0, 1, 0, 0),
        b == 2,
        eofValue == -1,
        channel.reads.get() == 3,
        closed,
        !readable
      )
    },
    test("large chunk reads yield cooperatively and generic reads consume prefetched bytes") {
      val channel = new TestChannel
      val reader  = AsyncNioReaders.fromChannel(channel, 300)
      val pending = reader.readN[Byte](257).start
      val bytes   = Array.tabulate[Byte](258)(_.toByte)
      channel.awaitReads(1)
      channel.complete(bytes.toSeq: _*)
      for {
        values  <- await(pending)
        generic <- await(reader.read[Byte]((-1).toByte))
      } yield assertTrue(values == Chunk.fromArray(bytes).take(257), generic == 1.toByte, channel.reads.get() == 1)
    },
    test("channel submission and inline completion run outside the reader monitor") {
      val held                             = new java.util.concurrent.atomic.AtomicBoolean(false)
      var reader: Reader.AsyncReader[Byte] = null
      val channel                          = new TestChannel(
        inlineByte = 7,
        onRead = () => held.set(Thread.holdsLock(reader.asInstanceOf[AnyRef]))
      )
      reader = AsyncNioReaders.fromChannel(channel, 4)
      for {
        value <- await(reader.readByte())
        _     <- await(reader.close())
      } yield assertTrue(value == 7, !held.get())
    },
    test("managed and unmanaged submission failures are sticky and replayed") {
      val managedFailure   = new IOException("managed submit")
      val unmanagedFailure = new IOException("unmanaged submit")
      val managedChannel   = new TestChannel(readFailure = managedFailure)
      val unmanagedChannel = new TestChannel(futureReadFailure = unmanagedFailure)
      val managedReader    = AsyncNioReaders.fromChannel(managedChannel)
      val unmanagedReader  = AsyncNioReaders.fromChannelUnmanaged(unmanagedChannel)
      for {
        managedFirst    <- await(managedReader.readByte().either)
        managedReplay   <- await(managedReader.readByte().either)
        unmanagedFirst  <- await(unmanagedReader.readByte().either)
        unmanagedReplay <- await(unmanagedReader.readByte().either)
      } yield {
        val managedError   = managedFirst.swap.toOption.get
        val unmanagedError = unmanagedFirst.swap.toOption.get
        assertTrue(
          managedError.isInstanceOf[StreamError],
          managedReplay == Left(managedError),
          unmanagedError.isInstanceOf[StreamError],
          unmanagedReplay == Left(unmanagedError),
          managedChannel.reads.get() == 1,
          unmanagedChannel.reads.get() == 1
        )
      }
    },
    test("managed close completes an active read, closes once, and rejects stale completion") {
      val channel = new TestChannel
      val reader  = AsyncNioReaders.fromChannel(channel)
      val pending = reader.readByte().start
      channel.awaitReads(1)
      val close1 = reader.close().start
      val close2 = reader.close()
      channel.awaitCloses(1)
      channel.complete(42.toByte)
      for {
        value <- await(pending)
        _     <- await(close1)
        _     <- await(close2)
        end   <- await(reader.readByte())
      } yield assertTrue(value == -1, end == -1, channel.closes.get() == 1)
    },
    test("close discards unread prefetched bytes and reports unavailable") {
      val channel = new TestChannel
      val reader  = AsyncNioReaders.fromChannel(channel, 4)
      val first   = reader.readByte().start
      channel.awaitReads(1)
      channel.complete(1.toByte, 2.toByte, 3.toByte)
      for {
        value           <- await(first)
        availableBefore <- await(reader.readable())
        _               <- await(reader.close())
        availableAfter  <- await(reader.readable())
        end             <- await(reader.readByte())
      } yield assertTrue(value == 1, availableBefore, !availableAfter, end == -1, channel.closes.get() == 1)
    },
    test("close clears native buffer after an outstanding operation even when channel close fails") {
      val failure = new IOException("close")
      val channel = new TestChannel(failure)
      val reader  = AsyncNioReaders.fromChannel(channel, 4)
      val pending = reader.readByte().start
      channel.awaitReads(1)
      val closing = reader.close().either.start
      channel.awaitCloses(1)
      channel.complete(1.toByte, 2.toByte)
      for {
        value    <- await(pending)
        result   <- await(closing)
        readable <- await(reader.readable())
        end      <- await(reader.readByte())
      } yield assertTrue(value == -1, result.left.exists(_ eq failure), !readable, end == -1)
    },
    test("managed close replays an already trusted close failure without changing identity") {
      val failure = StreamError.source(new IOException("trusted close"))
      val channel = new TestChannel(closeFailure = failure)
      val reader  = AsyncNioReaders.fromChannel(channel)
      for {
        result <- await(reader.close().either)
        again  <- await(reader.close().either)
      } yield assertTrue(result.left.exists(_ eq failure), again.left.exists(_ eq failure), channel.closes.get() == 1)
    },
    test("managed close and later waiters join the outstanding handler after one waiter cancels") {
      val channel = new TestChannel
      val reader  = AsyncNioReaders.fromChannel(channel)
      val pending = reader.readByte().start
      channel.awaitReads(1)
      val abandoned = reader.close().start
      channel.awaitCloses(1)
      abandoned.cancel()
      val joined = reader.close().start
      val before = joined.poll(new Runnable { def run(): Unit = () })
      channel.complete(42.toByte)
      for {
        value <- await(pending)
        _     <- await(joined)
      } yield assertTrue(
        before.isInstanceOf[Pollable[?]],
        value == -1,
        channel.closes.get() == 1
      )
    },
    test("unmanaged close leaves the channel open") {
      val channel = new TestChannel
      val reader  = AsyncNioReaders.fromChannelUnmanaged(channel)
      for {
        _   <- await(reader.close())
        end <- await(reader.readByte())
      } yield assertTrue(end == -1, channel.isOpen, channel.closes.get() == 0)
    },
    test("unmanaged close cancels and joins an outstanding channel read without closing the channel") {
      val channel = new TestChannel
      val reader  = AsyncNioReaders.fromChannelUnmanaged(channel)
      val pending = reader.readByte().start
      channel.awaitReads(1)
      for {
        _     <- await(reader.close())
        value <- await(pending)
      } yield assertTrue(value == -1, channel.isOpen, channel.closes.get() == 0)
    },
    test("unmanaged close cancels the native Future outside the reader monitor") {
      val monitorHeld                      = new java.util.concurrent.atomic.AtomicBoolean(false)
      var reader: Reader.AsyncReader[Byte] = null
      val channel                          = new TestChannel(onFutureCancel = () => {
        monitorHeld.set(Thread.holdsLock(reader.asInstanceOf[AnyRef]))
        reader.isClosed.block
        ()
      })
      reader = AsyncNioReaders.fromChannelUnmanaged(channel)
      val pending = reader.readByte().start
      channel.awaitReads(1)
      for {
        _     <- await(reader.close())
        value <- await(pending)
      } yield assertTrue(!monitorHeld.get(), value == -1, channel.isOpen)
    },
    test("unmanaged close records cancellation before the native Future is published") {
      val admitted = new CountDownLatch(1)
      val publish  = new CountDownLatch(1)
      val channel  = new TestChannel(onFutureRead = () => {
        admitted.countDown()
        if (!publish.await(10, TimeUnit.SECONDS)) throw new AssertionError("future publication was not released")
      })
      val reader  = AsyncNioReaders.fromChannelUnmanaged(channel)
      val pending = reader.readByte().start
      if (!admitted.await(5, TimeUnit.SECONDS)) throw new AssertionError("channel read was not admitted")
      val closing                 = reader.close().start
      val deadline                = System.nanoTime() + 5000000000L
      var closedBeforePublication = reader.isClosed.block
      while (!closedBeforePublication && System.nanoTime() < deadline) {
        Thread.`yield`()
        closedBeforePublication = reader.isClosed.block
      }
      publish.countDown()
      if (!closedBeforePublication) throw new AssertionError("reader did not record close before future publication")
      for {
        _     <- await(closing)
        value <- await(pending)
      } yield assertTrue(value == -1, channel.futureCancelled, channel.isOpen, channel.closes.get() == 0)
    },
    test("I/O failure is trusted, sticky, and replayed by identity") {
      val channel = new TestChannel
      val reader  = AsyncNioReaders.fromChannel(channel)
      val first   = reader.read[Any]("eof").either.start
      channel.awaitReads(1)
      channel.fail(new IOException("boom"))
      for {
        one      <- await(first)
        two      <- await(reader.read[Any]("eof").either)
        closed   <- await(reader.isClosed)
        readable <- await(reader.readable())
      } yield {
        val firstFailure  = one.swap.toOption.get
        val secondFailure = two.swap.toOption.get
        assertTrue(
          firstFailure.isInstanceOf[StreamError],
          firstFailure.asInstanceOf[StreamError].isTrusted,
          firstFailure eq secondFailure,
          closed,
          !readable
        )
      }
    },
    test("failure delivered after close is ignored and only settles the stale operation") {
      val channel = new TestChannel
      val reader  = AsyncNioReaders.fromChannel(channel)
      val pending = reader.readByte().start
      channel.awaitReads(1)
      val closing = reader.close().start
      channel.awaitCloses(1)
      channel.fail(new IOException("stale"))
      for {
        value <- await(pending)
        _     <- await(closing)
        end   <- await(reader.readByte())
      } yield assertTrue(value == -1, end == -1, channel.closes.get() == 1)
    },
    test("sticky failure precedes valid zero-length reads") {
      val channel = new TestChannel
      val reader  = AsyncNioReaders.fromChannel(channel)
      val first   = reader.readByte().either.start
      channel.awaitReads(1)
      channel.fail(new IOException("zero"))
      val dest = new Array[Byte](1)
      for {
        failed <- await(first)
        chunk  <- await(reader.readN[Byte](0).either)
        bulk   <- await(reader.readBytes(dest, 0, 0).either)
      } yield {
        val cause = failed.swap.toOption.get
        assertTrue(chunk == Left(cause), bulk == Left(cause))
      }
    },
    test("a null channel failure is sticky and prevents another native read") {
      val channel = new TestChannel
      val reader  = AsyncNioReaders.fromChannel(channel)
      val first   = reader.readByte().either.start
      channel.awaitReads(1)
      channel.fail(null)
      for {
        one      <- await(first)
        two      <- await(reader.readByte().either)
        closed   <- await(reader.isClosed)
        readable <- await(reader.readable())
      } yield assertTrue(
        one == Left(null),
        two == Left(null),
        closed,
        !readable,
        channel.reads.get() == 1
      )
    },
    test("a callback-delivered trusted StreamError is copied as an untrusted failure") {
      val trusted = StreamError.source(new IOException("forged"))
      val channel = new TestChannel
      val reader  = AsyncNioReaders.fromChannel(channel)
      val first   = reader.readByte().either.start
      channel.awaitReads(1)
      channel.fail(trusted)
      for {
        one <- await(first)
        two <- await(reader.readByte().either)
      } yield {
        val firstFailure  = one.swap.toOption.get.asInstanceOf[StreamError]
        val secondFailure = two.swap.toOption.get
        assertTrue(
          (firstFailure ne trusted) && !firstFailure.isTrusted,
          firstFailure.value == trusted.value,
          secondFailure.asInstanceOf[AnyRef] eq firstFailure,
          channel.reads.get() == 1
        )
      }
    },
    test("direct cancellation of a pending read starts and shares reader close") {
      val channel = new TestChannel
      val reader  = AsyncNioReaders.fromChannel(channel)
      val pending = reader.readByte().start
      channel.awaitReads(1)
      pending.asInstanceOf[Pollable[Int]].cancel()
      val closeDeadline = System.nanoTime() + 5000000000L
      while (!reader.isClosed.block && System.nanoTime() < closeDeadline) Thread.`yield`()
      val closedBeforeCompletion = reader.isClosed.block
      val joined                 = reader.close().start
      val before                 = joined.poll(new Runnable { def run(): Unit = () })
      channel.complete(7.toByte)
      for {
        _   <- await(joined)
        end <- await(reader.readByte())
      } yield assertTrue(
        before.isInstanceOf[Pollable[?]],
        closedBeforeCompletion,
        end == -1,
        channel.closes.get() == 1
      )
    },
    test("managed close failure does not wait forever for a completion handler that never returns") {
      val failure = new IOException("close")
      val channel = new TestChannel(closeFailure = failure)
      val reader  = AsyncNioReaders.fromChannel(channel)
      val pending = reader.readByte().start
      channel.awaitReads(1)
      for {
        closed <- await(reader.close().either)
        value  <- await(pending)
      } yield assertTrue(closed == Left(failure), value == -1, channel.closes.get() == 1)
    },
    test("a second active read fails without disturbing the first") {
      val channel = new TestChannel
      val reader  = AsyncNioReaders.fromChannel(channel)
      val first   = reader.readByte().start
      channel.awaitReads(1)
      val second   = reader.readByte().either.start
      val rejected = second.block
      channel.complete(7.toByte)
      for {
        value <- await(first)
      } yield assertTrue(rejected.left.exists(_.isInstanceOf[IllegalStateException]), value == 7)
    },
    test("specialized chunk pulls preserve bytes, zero length, and sticky EOF without repulling") {
      val channel = new TestChannel
      val reader  = AsyncNioReaders.fromChannel(channel, 4)
      val first   = reader.readUpToN[Byte](2).start
      channel.awaitReads(1)
      channel.complete(1.toByte, 2.toByte, 0xff.toByte)
      for {
        head  <- await(first)
        tail  <- await(reader.readN[Byte](1))
        empty <- await(reader.readUpToN[Byte](0))
        eof   <- {
          val pending = reader.readByte().start
          channel.awaitReads(2)
          channel.eof()
          await(pending)
        }
        eofAgain <- await(reader.readByte())
      } yield assertTrue(
        head == Chunk[Byte](1, 2),
        tail == Chunk[Byte](0xff.toByte),
        empty == Chunk.empty,
        eof == -1,
        eofAgain == -1,
        channel.reads.get() == 2
      )
    },
    test("generic and bulk reads validate arguments and reject concurrent operations") {
      val channel = new TestChannel
      val reader  = AsyncNioReaders.fromChannel(channel, 4)
      val before  = reader.tryReadable
      val first   = reader.read[Any]("eof").start
      channel.awaitReads(1)
      val genericConflict = reader.read[Any]("eof").either.block
      val bulkConflict    = reader.readBytes(new Array[Byte](1), 0, 1).either.block
      val nullFailure     = reader.readBytes(null, 0, 0).either.block
      val rangeFailure    = reader.readBytes(new Array[Byte](1), 1, 1).either.block
      channel.complete(9.toByte, 10.toByte)
      val value = first.block
      assertTrue(
        value == 9.toByte,
        before == Reader.Unknown,
        reader.tryReadable == Reader.Available,
        genericConflict.left.exists(_.isInstanceOf[IllegalStateException]),
        bulkConflict.left.exists(_.isInstanceOf[IllegalStateException]),
        nullFailure.left.exists(_.isInstanceOf[NullPointerException]),
        rangeFailure.left.exists(_.isInstanceOf[IndexOutOfBoundsException])
      )
    },
    test("generic and bulk reads expose zero, direct submission, and terminal states") {
      val directChannel = new TestChannel
      val directReader  = AsyncNioReaders.fromChannel(directChannel, 4)
      val directValues  = new Array[Byte](4)
      val direct        = directReader.readBytes(directValues, 1, 2).start
      directChannel.awaitReads(1)
      directChannel.complete(4.toByte, 5.toByte, 6.toByte)

      val closedChannel = new TestChannel
      val closedReader  = AsyncNioReaders.fromChannel(closedChannel, 4)
      val closedValues  = new Array[Byte](1)
      for {
        directCount <- await(direct)
        zero        <- await(closedReader.readBytes(closedValues, 0, 0))
        _           <- await(closedReader.close())
        generic     <- await(closedReader.read[Any]("closed"))
        bulk        <- await(closedReader.readBytes(closedValues, 0, 1))
        _           <- await(directReader.close())
      } yield assertTrue(
        directCount == 2,
        directValues.toList == List[Byte](0, 4, 5, 0),
        directChannel.reads.get() == 1,
        zero == 0,
        generic == "closed",
        bulk == -1,
        closedReader.tryReadable == Reader.Unavailable
      )
    },
    test("large exact chunk reads cross the reschedule budget") {
      val channel = new TestChannel(inlineByte = 1)
      val reader  = AsyncNioReaders.fromChannel(channel, 8)
      for {
        values <- await(reader.readN[Byte](257))
        _      <- await(reader.close())
      } yield assertTrue(values.length == 257, values.forall(_ == 1.toByte), channel.reads.get() == 257)
    },
    test("unmanaged future completion and failure are observed by the poller") {
      val successChannel = new TestChannel
      val successReader  = AsyncNioReaders.fromChannelUnmanaged(successChannel)
      val successful     = successReader.readByte().start
      successChannel.awaitReads(1)
      successChannel.complete(12.toByte)
      val failedChannel = new TestChannel
      val failedReader  = AsyncNioReaders.fromChannelUnmanaged(failedChannel)
      val failed        = failedReader.readByte().either.start
      failedChannel.awaitReads(1)
      val failure = new IOException("future")
      failedChannel.fail(failure)
      for {
        value  <- await(successful)
        result <- await(failed)
        _      <- await(successReader.close())
        _      <- await(failedReader.close().either)
      } yield assertTrue(value == 12, result.left.exists(_.isInstanceOf[StreamError]))
    }
  )
}

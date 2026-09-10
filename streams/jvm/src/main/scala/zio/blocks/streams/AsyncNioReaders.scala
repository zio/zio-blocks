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
import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.streams.internal.StreamError
import zio.blocks.streams.io.Reader

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousByteChannel, AsynchronousSocketChannel, CompletionHandler}
import java.util.concurrent.{CancellationException, ExecutionException, Executors, Future, TimeUnit}

/**
 * Genuinely non-blocking JVM reader factories. [[NioReaders]] remains the API
 * for blocking `ReadableByteChannel`s.
 */
object AsyncNioReaders {
  private val futurePoller = Executors.newSingleThreadScheduledExecutor { (r: Runnable) =>
    val thread = new Thread(r, "zio-blocks-async-nio-future-poller")
    thread.setDaemon(true)
    thread
  }

  private[streams] def pollFuture(operation: Runnable): Unit = {
    futurePoller.schedule(operation, 1L, TimeUnit.MILLISECONDS)
    ()
  }

  def fromChannel(channel: AsynchronousByteChannel, bufferSize: Int = 8192): Reader.AsyncReader[Byte] =
    new AsyncChannelReader(channel, bufferSize, closeChannel = true)

  def fromChannelUnmanaged(channel: AsynchronousByteChannel, bufferSize: Int = 8192): Reader.AsyncReader[Byte] =
    new AsyncChannelReader(channel, bufferSize, closeChannel = false)

  def fromSocket(socket: AsynchronousSocketChannel, bufferSize: Int = 8192): Reader.AsyncReader[Byte] =
    fromChannel(socket, bufferSize)

  def fromSocketUnmanaged(socket: AsynchronousSocketChannel, bufferSize: Int = 8192): Reader.AsyncReader[Byte] =
    fromChannelUnmanaged(socket, bufferSize)
}

private final class AsyncChannelReader(channel: AsynchronousByteChannel, bufferSize: Int, closeChannel: Boolean)
    extends Reader.AsyncReader[Byte] {
  require(bufferSize > 0, "bufferSize must be positive")

  private val buffer = ByteBuffer.allocate(bufferSize)
  buffer.limit(0)
  private var closed                     = false
  private var eof                        = false
  private var failed                     = false
  private var terminalFailure: Throwable = null
  private var epoch                      = 0L
  private var active: ReadOperation      = null
  private val closeOwner                 = new Reader.MemoizedClose(() => (), () => closeNow())

  override def jvmType: JvmType = JvmType.Byte

  private def replayFailure[A]: Async[A] = terminalFailure match {
    case error: StreamError if error.isTrusted => Async.failTrusted(error)
    case cause                                 => Async.fail(cause)
  }

  private def deferred[A](effect: => Async[A]): Async[A] =
    Async.deferCancelable(() => effect, () => ()).flatten

  def isClosed: Async[Boolean]   = deferred(synchronized(Async.succeed(closed || eof || failed)))
  def readable(): Async[Boolean] =
    deferred(synchronized(Async.succeed(!closed && !eof && !failed && buffer.hasRemaining)))

  def close(): Async[Unit] = closeOwner.close()

  private def closeNow(): Async[Unit] = {
    val pending = synchronized {
      closed = true
      eof = true
      epoch += 1
      val pending = active
      active = null
      pending
    }
    if (pending ne null) pending.cancelUnderlying()
    if (pending ne null) pending.finishEof()
    val channelClose =
      if (closeChannel)
        Async.attempt {
          if (channel.isOpen) channel.close()
        }
      else Async.succeed(())
    channelClose.either.flatMap {
      case Left(cause) =>
        if (pending eq null) clearBuffer()
        replay(cause)
      case Right(_) =>
        val settled = if (pending eq null) Async.succeed(()) else pending.awaitUnderlying
        settled.either.flatMap { result =>
          clearBuffer()
          result match {
            case Right(_)    => Async.succeed(())
            case Left(cause) => replay(cause)
          }
        }
    }
  }

  private def clearBuffer(): Unit = synchronized {
    buffer.clear()
    buffer.limit(0)
  }

  def read[A >: Byte](sentinel: A): Async[A] = deferred(readNow(sentinel))

  private def readNow[A >: Byte](sentinel: A): Async[A] = {
    val operation = synchronized {
      if (failed) return replayFailure
      else if (closed || eof) return Async.succeed(sentinel)
      else if (active ne null) return Async.fail(new IllegalStateException("only one generic read may be active"))
      else if (buffer.hasRemaining) return Async.succeed(buffer.get().asInstanceOf[A])
      else reserveRead()
    }
    operation.submit()
    operation.map { count =>
      synchronized {
        if (count < 0 || closed || eof) sentinel
        else buffer.get().asInstanceOf[A]
      }
    }
  }

  override def readByte(): Async[Int] = deferred(readByteNow())

  private def readByteNow(): Async[Int] = {
    val operation = synchronized {
      if (failed) return replayFailure
      else if (closed || eof) return Async.succeed(-1)
      else if (active ne null) return Async.fail(new IllegalStateException("only one byte read may be active"))
      else if (buffer.hasRemaining) return Async.succeed(buffer.get().toInt & 0xff)
      else reserveRead()
    }
    operation.submit()
    operation.map(count => synchronized(if (count < 0 || closed || eof) -1 else buffer.get().toInt & 0xff))
  }

  override private[streams] def tryReadable: Reader.Availability = synchronized {
    if (closed || eof || failed) Reader.Unavailable
    else if (buffer.hasRemaining) Reader.Available
    else Reader.Unknown
  }

  override def readN[A >: Byte](n: Int): Async[Chunk[A]] = deferred(readBytesChunk[A](n, stopWhenDrained = false))

  override def readUpToN[A >: Byte](n: Int): Async[Chunk[A]] = deferred(readBytesChunk[A](n, stopWhenDrained = true))

  private def readBytesChunk[A >: Byte](n: Int, stopWhenDrained: Boolean): Async[Chunk[A]] =
    if (synchronized(failed)) replayFailure
    else if (n <= 0) Async.succeed(Chunk.empty)
    else {
      val builder = new ChunkBuilder.Byte()
      builder.sizeHint(math.min(n, 64))
      def loop(count: Int, budget: Int): Async[Chunk[A]] =
        readByteNow().flatMap { value =>
          if (value < 0) Async.succeed(builder.result().asInstanceOf[Chunk[A]])
          else {
            builder.addOne(value.toByte)
            if (count + 1 >= n || (stopWhenDrained && !synchronized(buffer.hasRemaining)))
              Async.succeed(builder.result().asInstanceOf[Chunk[A]])
            else if (budget > 0) loop(count + 1, budget - 1)
            else Async.reschedule(() => loop(count + 1, 255))
          }
        }
      loop(0, 255)
    }

  override def readBytes(dest: Array[Byte], offset: Int, length: Int)(implicit ev: Byte <:< Byte): Async[Int] =
    deferred(readBytesNow(dest, offset, length))

  private def readBytesNow(dest: Array[Byte], offset: Int, length: Int): Async[Int] = {
    if (dest eq null) return Async.fail(new NullPointerException("dest"))
    if (offset < 0 || length < 0 || offset > dest.length - length)
      return Async.fail(new IndexOutOfBoundsException(s"offset=$offset length=$length size=${dest.length}"))
    val operation = synchronized {
      if (failed) return replayFailure
      else if (length == 0) return Async.succeed(0)
      else if (closed || eof) return Async.succeed(-1)
      else if (active ne null) return Async.fail(new IllegalStateException("only one bulk read may be active"))
      else if (buffer.hasRemaining) return Async.succeed(copyBuffered(dest, offset, length))
      else reserveRead()
    }
    operation.submit()
    operation.map(count => synchronized(if (count < 0 || closed || eof) -1 else copyBuffered(dest, offset, length)))
  }

  private def copyBuffered(dest: Array[Byte], offset: Int, length: Int): Int = {
    val count = math.min(length, buffer.remaining)
    buffer.get(dest, offset, count)
    count
  }

  private def reserveRead(): ReadOperation = {
    buffer.clear()
    val token = epoch
    val op    = new ReadOperation(token)
    active = op
    op
  }

  private final class ReadOperation(token: Long)
      extends Async.Operation[Int]
      with CompletionHandler[Integer, ReadOperation]
      with Runnable {
    private val completion                        = new Completer[Int]
    private val underlyingDone                    = new Completer[Unit]
    private var emptyRetries                      = 0
    private var submission                        = 0 // 0 = reserved, 1 = admitted to the channel, 2 = settled/cancelled
    @volatile private var future: Future[Integer] = null
    private var cancelRequested                   = false

    def awaitUnderlying: Async[Unit] = underlyingDone

    def submit(): Unit = {
      val admitted = AsyncChannelReader.this.synchronized {
        if ((active eq this) && token == epoch && !closed && submission == 0) {
          submission = 1
          true
        } else false
      }
      if (admitted)
        try {
          if (closeChannel) channel.read(buffer, this, this)
          else {
            val submitted = channel.read(buffer)
            val cancelNow = AsyncChannelReader.this.synchronized {
              future = submitted
              cancelRequested || closed || token != epoch || (active ne this)
            }
            if (cancelNow) submitted.cancel(false)
            AsyncNioReaders.pollFuture(this)
          }
        } catch { case cause: Throwable => failed(cause, this) }
      else underlyingDone.succeed(())
    }

    def cancelUnderlying(): Unit = {
      val (current, settle) = AsyncChannelReader.this.synchronized {
        cancelRequested = true
        if (submission == 0) { submission = 2; (null, true) }
        else (future, false)
      }
      if (current ne null) current.cancel(false)
      if (settle) underlyingDone.succeed(())
    }

    def run(): Unit = {
      val current = future
      if (current eq null) ()
      else if (!current.isDone) AsyncNioReaders.pollFuture(this)
      else
        try completed(current.get(), this)
        catch {
          case _: CancellationException =>
            AsyncChannelReader.this.synchronized {
              submission = 2
              if (closed) clearBuffer()
            }
            underlyingDone.succeed(())
          case error: ExecutionException => failed(error.getCause, this)
          case cause: Throwable          => failed(cause, this)
        }
    }

    def poll(onComplete: Runnable): Async[Int] = {
      val result = completion.poll(onComplete)
      if (result.asInstanceOf[AnyRef] eq completion) this else result
    }

    def completed(value: Integer, attachment: ReadOperation): Unit = {
      val n                 = value.intValue
      val (accepted, retry) = AsyncChannelReader.this.synchronized {
        if ((active ne this) || token != epoch || closed) {
          if (closed) clearBuffer()
          (false, false)
        } else {
          if (n < 0) { submission = 2; active = null; eof = true; buffer.limit(0) }
          else if (n == 0) {
            submission = 0
            buffer.clear()
            emptyRetries += 1
          } else { submission = 2; active = null; buffer.flip() }
          (true, n == 0)
        }
      }
      if (retry) {
        Async.schedule(
          new Runnable {
            def run(): Unit =
              submit()
          },
          forceMacrotask = (emptyRetries & 255) == 0
        )
      } else {
        underlyingDone.succeed(())
        if (accepted) completion.succeed(n)
      }
    }

    def failed(cause: Throwable, attachment: ReadOperation): Unit = {
      val failure = cause match {
        case error: StreamError => StreamError.untrusted(error)
        case io: IOException    => StreamError.source(io)
        case other              => other
      }
      val accepted = AsyncChannelReader.this.synchronized {
        if ((active ne this) || token != epoch || closed) {
          if (closed) clearBuffer()
          false
        } else {
          submission = 2
          active = null
          buffer.limit(0)
          AsyncChannelReader.this.failed = true
          terminalFailure = failure
          true
        }
      }
      underlyingDone.succeed(())
      if (accepted) completion.fail(failure)
    }

    def finishEof(): Unit = completion.succeed(-1)

    protected def cancelOperation(): Async[Unit] = closeOwner.closeClaimed()
  }

  private def replay[A](cause: Throwable): Async[A] = cause match {
    case error: StreamError if error.isTrusted => Async.failTrusted(error)
    case other                                 => Async.fail(other)
  }
}

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

import scala.scalajs.js
import scala.scalajs.concurrent.QueueExecutionContext
import scala.scalajs.js.typedarray.Uint8Array
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/**
 * Minimal facades for the WHATWG byte-stream API. They avoid imposing a DOM
 * library on users of the streams module.
 */
object ReadableStreamReaders {
  @js.native
  trait ReadResult extends js.Object {
    val done: Boolean     = js.native
    val value: Uint8Array = js.native
  }
  @js.native
  trait StreamReader extends js.Object {
    def read(): js.Promise[ReadResult] = js.native
    def cancel(): js.Promise[Unit]     = js.native
    def releaseLock(): Unit            = js.native
  }
  @js.native
  trait ReadableStream extends js.Object {
    def getReader(): StreamReader = js.native
  }

  /** Owns the acquired reader and cancels the JavaScript stream on close. */
  def fromReadableStream(stream: ReadableStream): Reader.AsyncReader[Byte] =
    new JsReadableStreamReader(stream.getReader(), managed = true)

  /** Releases, but never cancels, the caller-owned JavaScript reader. */
  def fromReadableStreamUnmanaged(stream: ReadableStream): Reader.AsyncReader[Byte] =
    new JsReadableStreamReader(stream.getReader(), managed = false)
}

private final class JsReadableStreamReader(
  reader: ReadableStreamReaders.StreamReader,
  managed: Boolean
) extends Reader.AsyncReader[Byte] {
  private implicit val promiseExecutionContext: ExecutionContext = QueueExecutionContext.timeouts()

  private var chunk: Uint8Array          = null
  private var index                      = 0
  private var closed                     = false
  private var eof                        = false
  private var terminalFailure: Throwable = null
  private var epoch                      = 0L
  private var active: ReadOperation      = null
  private val closeOwner                 = new Reader.MemoizedClose(() => (), () => closeNow())

  override def jvmType: JvmType          = JvmType.Byte
  private def replayFailure[A]: Async[A] = terminalFailure match {
    case error: StreamError if error.isTrusted => Async.failTrusted(error)
    case cause                                 => Async.fail(cause)
  }

  private def deferred[A](effect: => Async[A]): Async[A] =
    Async.deferCancelable(() => effect, () => ()).flatten

  def isClosed: Async[Boolean]   = deferred(synchronized(Async.succeed(closed || eof || (terminalFailure ne null))))
  def readable(): Async[Boolean] =
    deferred(synchronized(Async.succeed(hasBuffered)))

  private def releaseLock(): Async[Unit] =
    Async.reschedule(() =>
      try { reader.releaseLock(); Async.succeed(()) }
      catch { case cause: Throwable => Async.fail(cause) }
    )

  def close(): Async[Unit] = closeOwner.close()

  private def closeNow(): Async[Unit] = {
    val pending = synchronized {
      closed = true
      eof = true
      chunk = null
      index = 0
      epoch += 1
      val pending = active
      active = null
      pending
    }
    if (pending ne null) pending.finishEof()
    val relinquish =
      try if (managed) Async.fromJsPromise(reader.cancel()).map(_ => ()) else Async.succeed(())
      catch { case cause: Throwable => Async.fail(cause) }
    val beforePending = if (managed) relinquish else join(relinquish, releaseLock())
    val settled       = if (!managed || (pending eq null)) beforePending else join(beforePending, pending.awaitUnderlying)
    if (managed) join(settled, releaseLock()) else settled
  }

  def read[A >: Byte](sentinel: A): Async[A] = deferred(readNow(sentinel))

  private def readNow[A >: Byte](sentinel: A): Async[A] = {
    val (hasImmediate, immediate) = synchronized {
      if (hasBuffered) (true, Async.succeed(nextByte().asInstanceOf[A]))
      else if (terminalFailure ne null) (true, replayFailure)
      else if (closed || eof) (true, Async.succeed(sentinel))
      else if (active ne null) (true, Async.fail(new IllegalStateException("only one read may be active")))
      else (false, Async.succeed(sentinel))
    }
    if (hasImmediate) immediate
    else startRead().map(_ => synchronized(if (hasBuffered) nextByte().asInstanceOf[A] else sentinel))
  }

  override def readByte(): Async[Int] = deferred(readByteNow())

  private def readByteNow(): Async[Int] = {
    val (hasImmediate, immediate) = synchronized {
      if (hasBuffered) (true, Async.succeed(nextByte().toInt & 0xff))
      else if (terminalFailure ne null) (true, replayFailure)
      else if (closed || eof) (true, Async.succeed(-1))
      else if (active ne null) (true, Async.fail(new IllegalStateException("only one byte read may be active")))
      else (false, Async.succeed(-1))
    }
    if (hasImmediate) immediate
    else startRead().map(_ => synchronized(if (hasBuffered) nextByte().toInt & 0xff else -1))
  }

  override private[streams] def tryReadable: Reader.Availability = synchronized {
    if (hasBuffered) Reader.Available
    else if (closed || eof || (terminalFailure ne null)) Reader.Unavailable
    else Reader.Unknown
  }

  override def readN[A >: Byte](n: Int): Async[Chunk[A]] = deferred(readBytesChunk[A](n, stopWhenDrained = false))

  override def readUpToN[A >: Byte](n: Int): Async[Chunk[A]] = deferred(readBytesChunk[A](n, stopWhenDrained = true))

  private def readBytesChunk[A >: Byte](n: Int, stopWhenDrained: Boolean): Async[Chunk[A]] =
    if (synchronized(terminalFailure ne null)) replayFailure
    else if (n <= 0) Async.succeed(Chunk.empty)
    else {
      val builder = new ChunkBuilder.Byte()
      builder.sizeHint(math.min(n, 64))
      def loop(count: Int, budget: Int): Async[Chunk[A]] =
        if (count >= n) Async.succeed(builder.result().asInstanceOf[Chunk[A]])
        else
          readByteNow().flatMap { value =>
            if (value < 0) Async.succeed(builder.result().asInstanceOf[Chunk[A]])
            else {
              builder.addOne(value.toByte)
              if (count + 1 >= n || (stopWhenDrained && !synchronized(hasBuffered)))
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
    if (synchronized(terminalFailure ne null)) return replayFailure
    if (length == 0) return Async.succeed(0)
    val (hasImmediate, immediate) = synchronized {
      if (hasBuffered) (true, Async.succeed(copy(dest, offset, length)))
      else if (terminalFailure ne null) (true, replayFailure)
      else if (closed || eof) (true, Async.succeed(-1))
      else if (active ne null) (true, Async.fail(new IllegalStateException("only one read may be active")))
      else (false, Async.succeed(-1))
    }
    if (hasImmediate) immediate
    else startRead().map(_ => synchronized(if (hasBuffered) copy(dest, offset, length) else -1))
  }

  private def hasBuffered: Boolean                                   = (chunk ne null) && index < chunk.length
  private def nextByte(): Byte                                       = { val value = chunk(index).toByte; index += 1; value }
  private def copy(dest: Array[Byte], offset: Int, length: Int): Int = {
    val count = math.min(length, chunk.length - index)
    var i     = 0
    while (i < count) { dest(offset + i) = chunk(index + i).toByte; i += 1 }
    index += count
    count
  }

  private def startRead(): Async[Unit] = {
    val reserved = synchronized {
      if (closed || eof) Left(Async.succeed(()))
      else if (terminalFailure ne null) Left(replayFailure[Unit])
      else if (active ne null) Left(Async.fail(new IllegalStateException("only one read may be active")))
      else {
        val op = new ReadOperation(epoch)
        active = op
        Right(op)
      }
    }
    reserved match {
      case Left(result) => result
      case Right(op)    => op.submit(); op
    }
  }

  private final class ReadOperation(token: Long) extends Async.Operation[Unit] {
    private val completion     = new Completer[Unit]
    private val underlyingDone = new Completer[Unit]
    private var emptyRetries   = 0

    def awaitUnderlying: Async[Unit] = underlyingDone

    def submit(): Unit = {
      val admitted = JsReadableStreamReader.this.synchronized {
        (active eq this) && token == epoch && !closed
      }
      if (admitted)
        try attach(reader.read())
        catch { case cause: Throwable => reject(cause) }
      else underlyingDone.succeed(())
    }

    def attach(promise: js.Promise[ReadableStreamReaders.ReadResult]): Unit = {
      promise.toFuture.onComplete {
        case Success(value) => complete(value)
        case Failure(cause) => reject(cause)
      }
      ()
    }
    def poll(onComplete: Runnable): Async[Unit] = {
      val result = completion.poll(onComplete)
      if (result.asInstanceOf[AnyRef] eq completion) this else result
    }
    private def complete(value: ReadableStreamReaders.ReadResult): Unit = {
      val (accepted, retry) = JsReadableStreamReader.this.synchronized {
        if ((active ne this) || token != epoch || closed) (false, false)
        else {
          if (value.done) { active = null; eof = true; (true, false) }
          else if ((value.value ne null) && value.value.length > 0) {
            active = null
            chunk = value.value
            index = 0
            (true, false)
          } else {
            emptyRetries += 1
            (true, true)
          }
        }
      }
      if (retry) {
        Async.schedule(
          new Runnable {
            def run(): Unit = {
              val current = JsReadableStreamReader.this.synchronized {
                (active eq ReadOperation.this) && token == epoch && !closed
              }
              if (current) submit()
              else underlyingDone.succeed(())
            }
          },
          forceMacrotask = (emptyRetries & 255) == 0
        )
      } else {
        underlyingDone.succeed(())
        if (accepted) completion.succeed(())
      }
    }
    def reject(cause: Throwable): Unit = {
      val failure  = StreamError.source(cause)
      val accepted = JsReadableStreamReader.this.synchronized {
        if ((active ne this) || token != epoch || closed) false
        else {
          active = null
          chunk = null
          index = 0
          if (terminalFailure eq null) terminalFailure = failure
          true
        }
      }
      underlyingDone.succeed(())
      if (accepted) completion.fail(failure)
    }
    def finishEof(): Unit                        = completion.succeed(())
    protected def cancelOperation(): Async[Unit] = JsReadableStreamReader.this.close()
  }

  private def join(left: Async[Unit], right: => Async[Unit]): Async[Unit] =
    left.either.flatMap {
      case Right(_)      => right
      case Left(primary) =>
        right.either.flatMap {
          case Right(_)        => replay(primary)
          case Left(secondary) => replay(StreamError.attachCleanupReplay(primary, secondary))
        }
    }

  private def replay[A](cause: Throwable): Async[A] = cause match {
    case error: StreamError if error.isTrusted => Async.failTrusted(error)
    case other                                 => Async.fail(other)
  }
}

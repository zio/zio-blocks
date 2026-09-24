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

package zio.blocks.streams.internal

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.JvmType
import zio.blocks.streams.io.Reader

/**
 * Stateful boundaries used when a stateful Stream operator meets a native
 * AsyncReader.
 */
private[streams] object AsyncStatefulReader {

  /**
   * A lane-selected mutable buffer. Primitive arrays are held as AnyRef, but
   * values never cross an erased element carrier.
   */
  private final class LaneBuffer[A](source: Reader.AsyncReader[A], initialCapacity: Int) {
    private val lane         = source.jvmType
    private var data: AnyRef = newArray(math.max(1, initialCapacity))
    private var used         = 0

    private def newArray(n: Int): AnyRef = lane match {
      case JvmType.Boolean => new Array[Boolean](n)
      case JvmType.Byte    => new Array[Byte](n)
      case JvmType.Char    => new Array[Char](n)
      case JvmType.Short   => new Array[Short](n)
      case JvmType.Int     => new Array[Int](n)
      case JvmType.Long    => new Array[Long](n)
      case JvmType.Float   => new Array[Float](n)
      case JvmType.Double  => new Array[Double](n)
      case _               => new Array[AnyRef](n)
    }
    private def ensure(): Unit = if (used == java.lang.reflect.Array.getLength(data)) {
      val next = newArray(used << 1)
      System.arraycopy(data, 0, next, 0, used)
      data = next
    }
    def length: Int        = used
    def clear(): Unit      = used = 0
    def drop(n: Int): Unit = {
      val count = math.min(n, used)
      System.arraycopy(data, count, data, 0, used - count)
      used -= count
    }
    def pull(): Async[Int] = {
      ensure()
      lane match {
        case JvmType.Boolean =>
          source
            .readBooleanPhysical(-1)
            .map(v => if (v < 0) -1 else { data.asInstanceOf[Array[Boolean]](used) = v != 0; used += 1; 1 })
        case JvmType.Byte =>
          source.readBytesPhysical(data.asInstanceOf[Array[Byte]], used, 1).map { n => if (n > 0) used += 1; n }
        case JvmType.Char =>
          source
            .readCharPhysical(-1)
            .map(v => if (v < 0) -1 else { data.asInstanceOf[Array[Char]](used) = v.toChar; used += 1; 1 })
        case JvmType.Short =>
          source
            .readShortPhysical(Int.MinValue)
            .map(v =>
              if (v == Int.MinValue) -1 else { data.asInstanceOf[Array[Short]](used) = v.toShort; used += 1; 1 }
            )
        case JvmType.Int =>
          source.readIntsPhysical(data.asInstanceOf[Array[Int]], used, 1).map { n => if (n > 0) used += 1; n }
        case JvmType.Long =>
          source.readLongsPhysical(data.asInstanceOf[Array[Long]], used, 1).map { n => if (n > 0) used += 1; n }
        case JvmType.Float =>
          source.readFloatsPhysical(data.asInstanceOf[Array[Float]], used, 1).map { n => if (n > 0) used += 1; n }
        case JvmType.Double =>
          source.readDoublesPhysical(data.asInstanceOf[Array[Double]], used, 1).map { n => if (n > 0) used += 1; n }
        case _ =>
          source
            .read[Any](EndOfStream)
            .map(v =>
              if (v.asInstanceOf[AnyRef] eq EndOfStream) -1
              else { data.asInstanceOf[Array[AnyRef]](used) = v.asInstanceOf[AnyRef]; used += 1; 1 }
            )
      }
    }
    def chunk(n: Int): Chunk[A] = {
      val count = math.min(n, used)
      val copy  = newArray(count)
      System.arraycopy(data, 0, copy, 0, count)
      (lane match {
        case JvmType.Boolean => Chunk.fromArray(copy.asInstanceOf[Array[Boolean]])
        case JvmType.Byte    => Chunk.fromArray(copy.asInstanceOf[Array[Byte]])
        case JvmType.Char    => Chunk.fromArray(copy.asInstanceOf[Array[Char]])
        case JvmType.Short   => Chunk.fromArray(copy.asInstanceOf[Array[Short]])
        case JvmType.Int     => Chunk.fromArray(copy.asInstanceOf[Array[Int]])
        case JvmType.Long    => Chunk.fromArray(copy.asInstanceOf[Array[Long]])
        case JvmType.Float   => Chunk.fromArray(copy.asInstanceOf[Array[Float]])
        case JvmType.Double  => Chunk.fromArray(copy.asInstanceOf[Array[Double]])
        case _               => Chunk.fromArray(copy.asInstanceOf[Array[AnyRef]])
      }).asInstanceOf[Chunk[A]]
    }
  }

  /**
   * Pulls an already-reference-valued lane. Primitive sources must never use
   * this carrier.
   */
  private final class RefPull(source: Reader.AsyncReader[AnyRef]) {
    require(source.jvmType eq JvmType.AnyRef, s"RefPull requires a reference lane, got ${source.jvmType}")

    def apply(): Async[AnyRef] = source.read[AnyRef](EndOfStream)
  }

  /**
   * Pulls one primitive through its exact physical lane and deliberately
   * normalizes it to the boxed representation advertised by a widened stream.
   */
  private final class BoxedPhysicalPull[A](source: Reader.AsyncReader[A]) {
    private val byte   = new Array[Byte](1)
    private val int    = new Array[Int](1)
    private val long   = new Array[Long](1)
    private val float  = new Array[Float](1)
    private val double = new Array[Double](1)

    require(source.jvmType ne JvmType.AnyRef, s"BoxedPhysicalPull requires a primitive lane, got ${source.jvmType}")

    def apply(): Async[AnyRef] = source.jvmType match {
      case JvmType.Boolean =>
        source.readBooleanPhysical(-1).map(v => if (v < 0) EndOfStream else java.lang.Boolean.valueOf(v != 0))
      case JvmType.Byte =>
        source.readBytesPhysical(byte, 0, 1).map(n => if (n < 0) EndOfStream else java.lang.Byte.valueOf(byte(0)))
      case JvmType.Int =>
        source.readIntsPhysical(int, 0, 1).map(n => if (n < 0) EndOfStream else java.lang.Integer.valueOf(int(0)))
      case JvmType.Long =>
        source.readLongsPhysical(long, 0, 1).map(n => if (n < 0) EndOfStream else java.lang.Long.valueOf(long(0)))
      case JvmType.Float =>
        source.readFloatsPhysical(float, 0, 1).map(n => if (n < 0) EndOfStream else java.lang.Float.valueOf(float(0)))
      case JvmType.Double =>
        source
          .readDoublesPhysical(double, 0, 1)
          .map(n => if (n < 0) EndOfStream else java.lang.Double.valueOf(double(0)))
      case JvmType.Char =>
        source.readCharPhysical(-1).map(v => if (v < 0) EndOfStream else java.lang.Character.valueOf(v.toChar))
      case JvmType.Short =>
        source
          .readShortPhysical(Int.MinValue)
          .map(v => if (v == Int.MinValue) EndOfStream else java.lang.Short.valueOf(v.toShort))
      case lane => throw new IllegalStateException(s"unexpected reference lane $lane")
    }
  }

  private abstract class Boundary[A, B](protected val source: Reader.AsyncReader[A]) extends Reader.AsyncReader[B] {
    protected var closed                               = false
    private var resetting                              = false
    private var generation                             = 0L
    private var reading                                = false
    private var activeRead: Completer[Unit]            = null
    private var activeRun: Async.Running[_]            = null
    private var activeOperation: Pollable[_]           = null
    private var closingOperation: Pollable[_]          = null
    private var closingRead: Async[Unit]               = Async.succeed(())
    private def makeCloseOwner(): Reader.MemoizedClose =
      new Reader.MemoizedClose(
        () =>
          synchronized {
            closed = true
            generation += 1L
            onCloseState()
            val reentrant = (activeRun ne null) && activeRun.isDriverThread
            closingOperation = if (reentrant) null else activeOperation
            closingRead = if ((activeRead eq null) || reentrant) Async.succeed(()) else activeRead
          },
        () => {
          if (closingOperation ne null) {
            val cancellation =
              try Async.cancelWithCleanup(closingOperation)
              catch { case cause: Throwable => Async.fail(cause) }
            join(cancellation, source.close())
          } else join(source.close(), closingRead)
        }
      )
    private var closeOwner = makeCloseOwner()
    protected def onCloseState(): Unit
    protected final def current(token: Long): Boolean                   = synchronized(!closed && token == generation)
    protected final def commit[C](token: Long, stale: => C)(f: => C): C = {
      runBeforeCommitForTest(this)
      synchronized {
        if (closed || token != generation) stale else f
      }
    }
    protected final def publicRead[C](stale: => C)(f: Long => Async[C]): Async[C] =
      Async
        .acquireCancelable[Pollable[C]](
          () => {
            var started: Async.Running[C] = null
            var operation: Pollable[C]    = null
            started = Async.startRegistered(
              Async.bracketSync[Long, C](
                () =>
                  synchronized {
                    if (reading) throw new IllegalStateException(SyncInterpreter.ConcurrentOperationMessage)
                    reading = true
                    activeRun = started
                    activeOperation = operation
                    activeRead = new Completer[Unit]
                    if (closed) -1L else generation
                  },
                token => if (token < 0L) Async.succeed(stale) else f(token),
                _ => {
                  val completion = synchronized {
                    reading = false
                    if (activeRun eq started) activeRun = null
                    if (activeOperation eq operation) activeOperation = null
                    val current = activeRead
                    activeRead = null
                    current
                  }
                  completion.succeed(())
                  Async.succeed(())
                }
              )
            ) { running =>
              started = running
              operation = Async.cancelTo(running, () => Async.succeed(stale))
              synchronized {
                if (reading && (activeRun eq null)) activeRun = running
                if (reading && (activeOperation eq null)) activeOperation = operation
              }
            }
            operation
          },
          operation => Async.cancelWithCleanup(operation)
        )
        .flatten
    protected final def publicReadable(effect: => Async[Boolean]): Async[Boolean] =
      publicRead(false)(token => effect.map(value => commit(token, false)(value)))
    private def claimClose(): Async[Unit] = {
      val owner = synchronized {
        if (resetting) {
          resetting = false
          generation += 1L
        }
        closed = true
        closeOwner
      }
      owner.close()
    }
    final def close(): Async[Unit] =
      Async.deferCancelable(() => claimClose(), () => ()).flatten
    final def isClosed: Async[Boolean] =
      if (synchronized(closed)) Async.succeed(true) else source.isClosed

    override final def reset(): Async[Unit] = {
      var token                          = Long.MinValue
      var previous: Reader.MemoizedClose = null
      Async
        .deferCancelableWithCleanup(
          () => {
            val claimed = synchronized {
              if (resetting) Left(new java.io.IOException("Reader reset is already in progress"))
              else {
                resetting = true
                closed = true
                generation += 1L
                token = generation
                previous = closeOwner
                Right(())
              }
            }
            claimed match {
              case Left(cause) => Async.fail(cause)
              case Right(_)    =>
                previous.closeClaimed().either.flatMap {
                  case Left(cause) =>
                    synchronized { if (resetting) resetting = false }
                    Async.fail(cause)
                  case Right(_) =>
                    synchronized { if (resetting) token = generation }
                    val stillCurrent = synchronized(resetting && generation == token)
                    if (!stillCurrent) Async.fail(new java.io.IOException("Reader was closed during reset"))
                    else {
                      val supervised = Async.superviseResetUnit(
                        () => source.reset(),
                        () => source.close()
                      )
                      val nextOwner = new Reader.MemoizedClose(
                        () =>
                          synchronized {
                            closed = true
                            generation += 1L
                            onCloseState()
                          },
                        () => Async.cancelWithCleanup(supervised)
                      )
                      val published = synchronized {
                        if (!resetting || generation != token) false
                        else { closeOwner = nextOwner; true }
                      }
                      if (!published) Async.fail(new java.io.IOException("Reader was closed during reset"))
                      else
                        supervised.either.flatMap {
                          case Left(cause) =>
                            synchronized { if (generation == token) resetting = false }
                            joinPrimary(cause, nextOwner.closeClaimed())
                          case Right(_) =>
                            val committed = synchronized {
                              if (generation != token || !resetting) false
                              else {
                                closed = false
                                resetting = false
                                reading = false
                                activeRead = null
                                activeRun = null
                                activeOperation = null
                                closingOperation = null
                                closingRead = Async.succeed(())
                                true
                              }
                            }
                            if (committed) Async.succeed(())
                            else Async.fail(new java.io.IOException("Reader was closed during reset"))
                        }
                    }
                }
            }
          },
          () => synchronized(closeOwner).closeClaimed()
        )
        .flatten
    }

    private def join(left: Async[Unit], right: => Async[Unit]): Async[Unit] =
      left.either.flatMap {
        case Right(_)      => right
        case Left(primary) =>
          right.either.flatMap {
            case Right(_)        => Async.fail(primary)
            case Left(secondary) =>
              Async.fail(
                if ((primary eq null) || (secondary eq null)) primary
                else StreamError.attachCleanupReplay(primary, secondary)
              )
          }
      }

    private def joinPrimary(primary: Throwable, cleanup: => Async[Unit]): Async[Unit] =
      cleanup.either.flatMap {
        case Right(_)        => Async.fail(primary)
        case Left(secondary) =>
          Async.fail(
            if ((primary eq null) || (secondary eq null)) primary
            else StreamError.attachCleanupReplay(primary, secondary)
          )
      }
  }

  private abstract class PrimitiveBuffered[A](upstream: Reader.AsyncReader[A], size: Int)
      extends Boundary[A, A](upstream) {
    protected val cache: AnyRef
    private var index = 0
    private var count = 0
    protected def readSource(dest: AnyRef, offset: Int, length: Int): Async[Int]
    protected def copy(src: AnyRef, srcOffset: Int, dest: AnyRef, destOffset: Int, length: Int): Unit
    override protected def onCloseState(): Unit = { index = 0; count = 0 }
    final def readable(): Async[Boolean]        = {
      val state = synchronized(if (closed) -1 else if (index < count) 1 else 0)
      if (state < 0) Async.succeed(false) else if (state > 0) Async.succeed(true) else publicReadable(source.readable())
    }
    override private[streams] final def tryReadable: Reader.Availability = {
      val state = synchronized(if (closed) -1 else if (index < count) 1 else 0)
      if (state < 0) Reader.Unavailable else if (state > 0) Reader.Available else source.tryReadable
    }
    protected final def readPrimitive(dest: AnyRef, offset: Int, length: Int): Async[Int] = {
      if (length == 0) return Async.succeed(0)
      publicRead(-1) { token =>
        val copied = commit(token, -1) {
          val n = math.min(length, count - index)
          if (n > 0) { copy(cache, index, dest, offset, n); index += n }
          n
        }
        if (copied != 0) Async.succeed(copied)
        else
          readSource(cache, 0, size).map { n =>
            commit(token, -1) {
              index = 0; count = math.max(n, 0)
              if (n <= 0) -1
              else {
                val copied = math.min(n, length)
                copy(cache, 0, dest, offset, copied); index = copied; copied
              }
            }
          }
      }
    }
  }

  private final class ByteBuffered(upstream: Reader.AsyncReader[Byte], size: Int)
      extends PrimitiveBuffered[Byte](upstream, size) {
    private val scalar                                  = new Array[Byte](1)
    protected val cache: AnyRef                         = new Array[Byte](size)
    override def jvmType                                = JvmType.Byte
    protected def readSource(d: AnyRef, o: Int, n: Int) =
      source.readBytesPhysical(d.asInstanceOf[Array[Byte]], o, n)
    protected def copy(s: AnyRef, so: Int, d: AnyRef, doff: Int, n: Int): Unit                     = System.arraycopy(s, so, d, doff, n)
    override def readBytes(d: Array[Byte], o: Int, n: Int)(implicit ev: Byte <:< Byte): Async[Int] = {
      Reader.validateArrayRange(d, o, n); readPrimitive(d, o, n)
    }
    override def readByte(): Async[Int]        = readBytes(scalar, 0, 1).map(n => if (n < 0) -1 else scalar(0) & 0xff)
    def read[B >: Byte](sentinel: B): Async[B] = readByte().map(n => if (n < 0) sentinel else n.toByte)
  }

  private final class IntBuffered(upstream: Reader.AsyncReader[Int], size: Int)
      extends PrimitiveBuffered[Int](upstream, size) {
    private val scalar                                  = new Array[Int](1)
    protected val cache: AnyRef                         = new Array[Int](size)
    override def jvmType                                = JvmType.Int
    protected def readSource(d: AnyRef, o: Int, n: Int) =
      source.readIntsPhysical(d.asInstanceOf[Array[Int]], o, n)
    protected def copy(s: AnyRef, so: Int, d: AnyRef, doff: Int, n: Int): Unit                 = System.arraycopy(s, so, d, doff, n)
    override def readInts(d: Array[Int], o: Int, n: Int)(implicit ev: Int <:< Int): Async[Int] = {
      Reader.validateArrayRange(d, o, n); readPrimitive(d, o, n)
    }
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
      readInts(scalar, 0, 1).map(n => if (n < 0) sentinel else scalar(0).toLong)
    def read[B >: Int](sentinel: B): Async[B] =
      readInt(Long.MinValue).map(n => if (n == Long.MinValue) sentinel else n.toInt)
  }

  private final class LongBuffered(upstream: Reader.AsyncReader[Long], size: Int)
      extends PrimitiveBuffered[Long](upstream, size) {
    private val scalar                                  = new Array[Long](1)
    protected val cache: AnyRef                         = new Array[Long](size)
    override def jvmType                                = JvmType.Long
    protected def readSource(d: AnyRef, o: Int, n: Int) =
      source.readLongsPhysical(d.asInstanceOf[Array[Long]], o, n)
    protected def copy(s: AnyRef, so: Int, d: AnyRef, doff: Int, n: Int): Unit                     = System.arraycopy(s, so, d, doff, n)
    override def readLongs(d: Array[Long], o: Int, n: Int)(implicit ev: Long <:< Long): Async[Int] = {
      Reader.validateArrayRange(d, o, n); readPrimitive(d, o, n)
    }
    override def readLong(sentinel: Long)(implicit ev: Long <:< Long): Async[Long] =
      readLongs(scalar, 0, 1).map(n => if (n < 0) sentinel else scalar(0))
    def read[B >: Long](sentinel: B): Async[B] =
      readLongs(scalar, 0, 1).map(n => if (n < 0) sentinel else scalar(0))
  }

  private final class FloatBuffered(upstream: Reader.AsyncReader[Float], size: Int)
      extends PrimitiveBuffered[Float](upstream, size) {
    private val scalar                                  = new Array[Float](1)
    protected val cache: AnyRef                         = new Array[Float](size)
    override def jvmType                                = JvmType.Float
    protected def readSource(d: AnyRef, o: Int, n: Int) =
      source.readFloatsPhysical(d.asInstanceOf[Array[Float]], o, n)
    protected def copy(s: AnyRef, so: Int, d: AnyRef, doff: Int, n: Int): Unit                         = System.arraycopy(s, so, d, doff, n)
    override def readFloats(d: Array[Float], o: Int, n: Int)(implicit ev: Float <:< Float): Async[Int] = {
      Reader.validateArrayRange(d, o, n); readPrimitive(d, o, n)
    }
    override def readFloat(sentinel: Double)(implicit ev: Float <:< Float): Async[Double] =
      readFloats(scalar, 0, 1).map(n => if (n < 0) sentinel else scalar(0).toDouble)
    def read[B >: Float](sentinel: B): Async[B] =
      readFloats(scalar, 0, 1).map(n => if (n < 0) sentinel else scalar(0))
  }

  private final class DoubleBuffered(upstream: Reader.AsyncReader[Double], size: Int)
      extends PrimitiveBuffered[Double](upstream, size) {
    private val scalar                                  = new Array[Double](1)
    protected val cache: AnyRef                         = new Array[Double](size)
    override def jvmType                                = JvmType.Double
    protected def readSource(d: AnyRef, o: Int, n: Int) =
      source.readDoublesPhysical(d.asInstanceOf[Array[Double]], o, n)
    protected def copy(s: AnyRef, so: Int, d: AnyRef, doff: Int, n: Int): Unit                             = System.arraycopy(s, so, d, doff, n)
    override def readDoubles(d: Array[Double], o: Int, n: Int)(implicit ev: Double <:< Double): Async[Int] = {
      Reader.validateArrayRange(d, o, n); readPrimitive(d, o, n)
    }
    override def readDouble(sentinel: Double)(implicit ev: Double <:< Double): Async[Double] =
      readDoubles(scalar, 0, 1).map(n => if (n < 0) sentinel else scalar(0))
    def read[B >: Double](sentinel: B): Async[B] =
      readDoubles(scalar, 0, 1).map(n => if (n < 0) sentinel else scalar(0))
  }

  private abstract class IntCarrierBuffered[A](upstream: Reader.AsyncReader[A], size: Int)
      extends PrimitiveBuffered[A](upstream, size) {
    private val scalar: AnyRef  = new Array[Int](1)
    protected val cache: AnyRef = new Array[Int](size)
    protected def readOne(): Async[Int]
    protected def end(value: Int): Boolean
    protected def decode(value: Int): A
    protected final def readSource(d: AnyRef, offset: Int, length: Int): Async[Int] = {
      val dest                                        = d.asInstanceOf[Array[Int]]
      def loop(written: Int, budget: Int): Async[Int] =
        readOne().flatMap { value =>
          if (end(value)) Async.succeed(if (written == 0) -1 else written)
          else {
            dest(offset + written) = value
            if (written + 1 == length || (source.tryReadable ne Reader.Available)) Async.succeed(written + 1)
            else if (budget > 0) loop(written + 1, budget - 1)
            else Async.reschedule(() => loop(written + 1, 255))
          }
        }
      loop(0, 255)
    }
    protected final def copy(s: AnyRef, so: Int, d: AnyRef, doff: Int, n: Int): Unit =
      System.arraycopy(s, so, d, doff, n)
    protected final def readCarrier(sentinel: Int): Async[Int] =
      readPrimitive(scalar, 0, 1).map(n => if (n < 0) sentinel else scalar.asInstanceOf[Array[Int]](0))
    protected final def readGeneric[B >: A](sentinel: B): Async[B] =
      readCarrier(Int.MinValue).map(value => if (value == Int.MinValue) sentinel else decode(value))
  }

  private final class BooleanBuffered(upstream: Reader.AsyncReader[Boolean], size: Int)
      extends IntCarrierBuffered[Boolean](upstream, size) {
    override def jvmType                                                      = JvmType.Boolean
    protected def readOne()                                                   = source.readBooleanPhysical(-1)
    protected def end(value: Int)                                             = value < 0
    protected def decode(value: Int)                                          = value != 0
    override def readBoolean(sentinel: Int)(implicit ev: Boolean <:< Boolean) = readCarrier(sentinel)
    def read[B >: Boolean](sentinel: B)                                       = readGeneric(sentinel)
  }

  private final class CharBuffered(upstream: Reader.AsyncReader[Char], size: Int)
      extends IntCarrierBuffered[Char](upstream, size) {
    override def jvmType                                             = JvmType.Char
    protected def readOne()                                          = source.readCharPhysical(-1)
    protected def end(value: Int)                                    = value < 0
    protected def decode(value: Int)                                 = value.toChar
    override def readChar(sentinel: Int)(implicit ev: Char <:< Char) = readCarrier(sentinel)
    def read[B >: Char](sentinel: B)                                 = readGeneric(sentinel)
  }

  private final class ShortBuffered(upstream: Reader.AsyncReader[Short], size: Int)
      extends IntCarrierBuffered[Short](upstream, size) {
    override def jvmType                                                = JvmType.Short
    protected def readOne()                                             = source.readShortPhysical(Int.MinValue)
    protected def end(value: Int)                                       = value == Int.MinValue
    protected def decode(value: Int)                                    = value.toShort
    override def readShort(sentinel: Int)(implicit ev: Short <:< Short) = readCarrier(sentinel)
    def read[B >: Short](sentinel: B)                                   = readGeneric(sentinel)
  }

  private abstract class PrimitiveIntersperse[A](upstream: Reader.AsyncReader[A]) extends Boundary[A, A](upstream) {
    protected val pending: AnyRef
    protected val separatorValue: AnyRef
    private var first      = true
    private var hasPending = false
    protected def readSource(dest: AnyRef): Async[Int]
    protected def copy(src: AnyRef, srcOffset: Int, dest: AnyRef, destOffset: Int, length: Int): Unit
    override protected def onCloseState(): Unit = { first = true; hasPending = false }
    final def readable(): Async[Boolean]        = {
      val state = synchronized(if (closed) -1 else if (hasPending) 1 else 0)
      if (state < 0) Async.succeed(false) else if (state > 0) Async.succeed(true) else publicReadable(source.readable())
    }
    override private[streams] final def tryReadable: Reader.Availability = {
      val state = synchronized(if (closed) -1 else if (hasPending) 1 else 0)
      if (state < 0) Reader.Unavailable else if (state > 0) Reader.Available else source.tryReadable
    }
    protected final def readPrimitive(dest: AnyRef, offset: Int, length: Int): Async[Int] = {
      if (length == 0) return Async.succeed(0)
      publicRead(-1) { token =>
        def loop(written: Int, budget: Int): Async[Int] =
          if (!current(token)) Async.succeed(if (written == 0) -1 else written)
          else {
            val emitted = commit(token, false) {
              if (hasPending) { copy(pending, 0, dest, offset + written, 1); hasPending = false; true }
              else false
            }
            if (emitted) {
              if (written + 1 == length || (source.tryReadable ne Reader.Available)) Async.succeed(written + 1)
              else if (budget > 0) loop(written + 1, budget - 1)
              else Async.reschedule(() => loop(written + 1, 255))
            } else
              readSource(pending).flatMap { n =>
                if (n < 0) Async.succeed(if (written == 0) -1 else written)
                else if (!current(token)) Async.succeed(if (written == 0) -1 else written)
                else {
                  commit(token, ()) {
                    if (first) { first = false; copy(pending, 0, dest, offset + written, 1) }
                    else { copy(separatorValue, 0, dest, offset + written, 1); hasPending = true }
                  }
                  if (written + 1 == length || (source.tryReadable ne Reader.Available)) Async.succeed(written + 1)
                  else if (budget > 0) loop(written + 1, budget - 1)
                  else Async.reschedule(() => loop(written + 1, 255))
                }
              }
          }
        loop(0, 255).map(n => commit(token, -1)(n))
      }
    }
  }

  private final class ByteIntersperse(upstream: Reader.AsyncReader[Byte], separator: Byte)
      extends PrimitiveIntersperse[Byte](upstream) {
    private val scalar                                                                 = new Array[Byte](1)
    protected val pending: AnyRef                                                      = new Array[Byte](1); protected val separatorValue: AnyRef = Array(separator)
    override def jvmType                                                               = JvmType.Byte
    protected def readSource(d: AnyRef)                                                = source.readBytesPhysical(d.asInstanceOf[Array[Byte]], 0, 1)
    protected def copy(s: AnyRef, so: Int, d: AnyRef, doff: Int, n: Int): Unit         = System.arraycopy(s, so, d, doff, n)
    override def readBytes(d: Array[Byte], o: Int, n: Int)(implicit ev: Byte <:< Byte) = {
      Reader.validateArrayRange(d, o, n); readPrimitive(d, o, n)
    }
    override def readByte()          = readBytes(scalar, 0, 1).map(n => if (n < 0) -1 else scalar(0) & 0xff)
    def read[B >: Byte](sentinel: B) = readByte().map(n => if (n < 0) sentinel else n.toByte)
  }
  private final class IntIntersperse(upstream: Reader.AsyncReader[Int], separator: Int)
      extends PrimitiveIntersperse[Int](upstream) {
    private val scalar                                                             = new Array[Int](1)
    protected val pending: AnyRef                                                  = new Array[Int](1); protected val separatorValue: AnyRef = Array(separator)
    override def jvmType                                                           = JvmType.Int
    protected def readSource(d: AnyRef)                                            = source.readIntsPhysical(d.asInstanceOf[Array[Int]], 0, 1)
    protected def copy(s: AnyRef, so: Int, d: AnyRef, doff: Int, n: Int): Unit     = System.arraycopy(s, so, d, doff, n)
    override def readInts(d: Array[Int], o: Int, n: Int)(implicit ev: Int <:< Int) = {
      Reader.validateArrayRange(d, o, n); readPrimitive(d, o, n)
    }
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int) =
      readInts(scalar, 0, 1).map(n => if (n < 0) sentinel else scalar(0).toLong)
    def read[B >: Int](sentinel: B) = readInt(Long.MinValue).map(n => if (n == Long.MinValue) sentinel else n.toInt)
  }
  private final class LongIntersperse(upstream: Reader.AsyncReader[Long], separator: Long)
      extends PrimitiveIntersperse[Long](upstream) {
    private val scalar                                                                 = new Array[Long](1)
    protected val pending: AnyRef                                                      = new Array[Long](1); protected val separatorValue: AnyRef = Array(separator)
    override def jvmType                                                               = JvmType.Long
    protected def readSource(d: AnyRef)                                                = source.readLongsPhysical(d.asInstanceOf[Array[Long]], 0, 1)
    protected def copy(s: AnyRef, so: Int, d: AnyRef, doff: Int, n: Int): Unit         = System.arraycopy(s, so, d, doff, n)
    override def readLongs(d: Array[Long], o: Int, n: Int)(implicit ev: Long <:< Long) = {
      Reader.validateArrayRange(d, o, n); readPrimitive(d, o, n)
    }
    override def readLong(sentinel: Long)(implicit ev: Long <:< Long) =
      readLongs(scalar, 0, 1).map(n => if (n < 0) sentinel else scalar(0))
    def read[B >: Long](sentinel: B) =
      readLongs(scalar, 0, 1).map(n => if (n < 0) sentinel else scalar(0))
  }
  private final class FloatIntersperse(upstream: Reader.AsyncReader[Float], separator: Float)
      extends PrimitiveIntersperse[Float](upstream) {
    private val scalar                                                                     = new Array[Float](1)
    protected val pending: AnyRef                                                          = new Array[Float](1); protected val separatorValue: AnyRef = Array(separator)
    override def jvmType                                                                   = JvmType.Float
    protected def readSource(d: AnyRef)                                                    = source.readFloatsPhysical(d.asInstanceOf[Array[Float]], 0, 1)
    protected def copy(s: AnyRef, so: Int, d: AnyRef, doff: Int, n: Int): Unit             = System.arraycopy(s, so, d, doff, n)
    override def readFloats(d: Array[Float], o: Int, n: Int)(implicit ev: Float <:< Float) = {
      Reader.validateArrayRange(d, o, n); readPrimitive(d, o, n)
    }
    override def readFloat(sentinel: Double)(implicit ev: Float <:< Float) =
      readFloats(scalar, 0, 1).map(n => if (n < 0) sentinel else scalar(0).toDouble)
    def read[B >: Float](sentinel: B) =
      readFloats(scalar, 0, 1).map(n => if (n < 0) sentinel else scalar(0))
  }
  private final class DoubleIntersperse(upstream: Reader.AsyncReader[Double], separator: Double)
      extends PrimitiveIntersperse[Double](upstream) {
    private val scalar                                                                         = new Array[Double](1)
    protected val pending: AnyRef                                                              = new Array[Double](1); protected val separatorValue: AnyRef = Array(separator)
    override def jvmType                                                                       = JvmType.Double
    protected def readSource(d: AnyRef)                                                        = source.readDoublesPhysical(d.asInstanceOf[Array[Double]], 0, 1)
    protected def copy(s: AnyRef, so: Int, d: AnyRef, doff: Int, n: Int): Unit                 = System.arraycopy(s, so, d, doff, n)
    override def readDoubles(d: Array[Double], o: Int, n: Int)(implicit ev: Double <:< Double) = {
      Reader.validateArrayRange(d, o, n); readPrimitive(d, o, n)
    }
    override def readDouble(sentinel: Double)(implicit ev: Double <:< Double) =
      readDoubles(scalar, 0, 1).map(n => if (n < 0) sentinel else scalar(0))
    def read[B >: Double](sentinel: B) =
      readDoubles(scalar, 0, 1).map(n => if (n < 0) sentinel else scalar(0))
  }

  private abstract class IntCarrierIntersperse[A](upstream: Reader.AsyncReader[A], separator: Int)
      extends PrimitiveIntersperse[A](upstream) {
    private val scalar: AnyRef           = new Array[Int](1)
    protected val pending: AnyRef        = new Array[Int](1)
    protected val separatorValue: AnyRef = Array(separator)
    protected def readOne(): Async[Int]
    protected def end(value: Int): Boolean
    protected def decode(value: Int): A
    protected final def readSource(d: AnyRef): Async[Int] =
      readOne().map { value =>
        if (end(value)) -1
        else { d.asInstanceOf[Array[Int]](0) = value; 1 }
      }
    protected final def copy(s: AnyRef, so: Int, d: AnyRef, doff: Int, n: Int): Unit =
      System.arraycopy(s, so, d, doff, n)
    protected final def readCarrier(sentinel: Int): Async[Int] =
      readPrimitive(scalar, 0, 1).map(n => if (n < 0) sentinel else scalar.asInstanceOf[Array[Int]](0))
    protected final def readGeneric[B >: A](sentinel: B): Async[B] =
      readCarrier(Int.MinValue).map(value => if (value == Int.MinValue) sentinel else decode(value))
  }

  private final class BooleanIntersperse(upstream: Reader.AsyncReader[Boolean], separator: Boolean)
      extends IntCarrierIntersperse[Boolean](upstream, if (separator) 1 else 0) {
    override def jvmType                                                      = JvmType.Boolean
    protected def readOne()                                                   = source.readBooleanPhysical(-1)
    protected def end(value: Int)                                             = value < 0
    protected def decode(value: Int)                                          = value != 0
    override def readBoolean(sentinel: Int)(implicit ev: Boolean <:< Boolean) = readCarrier(sentinel)
    def read[B >: Boolean](sentinel: B)                                       = readGeneric(sentinel)
  }

  private final class CharIntersperse(upstream: Reader.AsyncReader[Char], separator: Char)
      extends IntCarrierIntersperse[Char](upstream, separator.toInt) {
    override def jvmType                                             = JvmType.Char
    protected def readOne()                                          = source.readCharPhysical(-1)
    protected def end(value: Int)                                    = value < 0
    protected def decode(value: Int)                                 = value.toChar
    override def readChar(sentinel: Int)(implicit ev: Char <:< Char) = readCarrier(sentinel)
    def read[B >: Char](sentinel: B)                                 = readGeneric(sentinel)
  }

  private final class ShortIntersperse(upstream: Reader.AsyncReader[Short], separator: Short)
      extends IntCarrierIntersperse[Short](upstream, separator.toInt) {
    override def jvmType                                                = JvmType.Short
    protected def readOne()                                             = source.readShortPhysical(Int.MinValue)
    protected def end(value: Int)                                       = value == Int.MinValue
    protected def decode(value: Int)                                    = value.toShort
    override def readShort(sentinel: Int)(implicit ev: Short <:< Short) = readCarrier(sentinel)
    def read[B >: Short](sentinel: B)                                   = readGeneric(sentinel)
  }

  def buffered[A](upstream: Reader.AsyncReader[A], size: Int): Reader.AsyncReader[A] = upstream.jvmType match {
    case JvmType.Boolean =>
      new BooleanBuffered(upstream.asInstanceOf[Reader.AsyncReader[Boolean]], size).asInstanceOf[Reader.AsyncReader[A]]
    case JvmType.Byte =>
      new ByteBuffered(upstream.asInstanceOf[Reader.AsyncReader[Byte]], size).asInstanceOf[Reader.AsyncReader[A]]
    case JvmType.Int =>
      new IntBuffered(upstream.asInstanceOf[Reader.AsyncReader[Int]], size).asInstanceOf[Reader.AsyncReader[A]]
    case JvmType.Long =>
      new LongBuffered(upstream.asInstanceOf[Reader.AsyncReader[Long]], size).asInstanceOf[Reader.AsyncReader[A]]
    case JvmType.Float =>
      new FloatBuffered(upstream.asInstanceOf[Reader.AsyncReader[Float]], size).asInstanceOf[Reader.AsyncReader[A]]
    case JvmType.Double =>
      new DoubleBuffered(upstream.asInstanceOf[Reader.AsyncReader[Double]], size).asInstanceOf[Reader.AsyncReader[A]]
    case JvmType.Char =>
      new CharBuffered(upstream.asInstanceOf[Reader.AsyncReader[Char]], size).asInstanceOf[Reader.AsyncReader[A]]
    case JvmType.Short =>
      new ShortBuffered(upstream.asInstanceOf[Reader.AsyncReader[Short]], size).asInstanceOf[Reader.AsyncReader[A]]
    case _ =>
      new Boundary[A, A](upstream) {
        private val pull                            = new RefPull(upstream.asInstanceOf[Reader.AsyncReader[AnyRef]])
        private var cached: Chunk[A]                = Chunk.empty
        private var index                           = 0
        override def jvmType: JvmType               = source.jvmType
        override protected def onCloseState(): Unit = { cached = Chunk.empty; index = 0 }
        def readable(): Async[Boolean]              = {
          val state = synchronized(if (closed) -1 else if (index < cached.length) 1 else 0)
          if (state < 0) Async.succeed(false)
          else if (state > 0) Async.succeed(true)
          else publicReadable(source.readable())
        }
        override private[streams] def tryReadable: Reader.Availability = {
          val state = synchronized(if (closed) -1 else if (index < cached.length) 1 else 0)
          if (state < 0) Reader.Unavailable else if (state > 0) Reader.Available else source.tryReadable
        }
        def read[A1 >: A](sentinel: A1): Async[A1] = publicRead(sentinel) { token =>
          def fill(left: Int, budget: Int): Async[Unit] =
            if (left == 0) Async.succeed(())
            else if (!current(token)) Async.succeed(())
            else
              pull().flatMap {
                case value if value eq EndOfStream => Async.succeed(())
                case _ if !current(token)          => Async.succeed(())
                case value                         =>
                  commit(token, ()) { cached = cached ++ Chunk.single(value.asInstanceOf[A]) }
                  if (left == 1 || !current(token)) Async.succeed(())
                  else if (source.tryReadable ne Reader.Available) Async.succeed(())
                  else if (budget > 0) fill(left - 1, budget - 1)
                  else Async.reschedule(() => fill(left - 1, 255))
              }
          val cachedValue = commit(token, Option.empty[A]) {
            if (index < cached.length) { val value = cached(index); index += 1; Some(value) }
            else None
          }
          if (cachedValue.isDefined) Async.succeed(cachedValue.get)
          else {
            commit(token, ()) { cached = Chunk.empty; index = 0 }
            fill(size, 255).map { _ =>
              commit(token, sentinel) {
                if (cached.isEmpty) sentinel
                else { val value = cached(0); index = 1; value }
              }
            }
          }
        }
      }
  }

  def chunked[A](upstream: Reader.AsyncReader[A], size: Int): Reader.AsyncReader[Chunk[A]] =
    new Boundary[A, Chunk[A]](upstream) {
      private val pending                         = new LaneBuffer(upstream, size)
      private var eof                             = false
      override def jvmType: JvmType               = JvmType.AnyRef
      override protected def onCloseState(): Unit = { pending.clear(); eof = false }
      def readable(): Async[Boolean]              =
        if (synchronized(closed)) Async.succeed(false) else publicReadable(source.readable())
      def read[B >: Chunk[A]](sentinel: B): Async[B] = publicRead(sentinel) { token =>
        def loop(budget: Int): Async[B] = {
          val ready = commit(token, Option.empty[B]) {
            if (pending.length >= size) {
              val result = pending.chunk(size)
              pending.drop(size)
              Some(result)
            } else if (eof) {
              if (pending.length == 0) Some(sentinel)
              else {
                val result = pending.chunk(pending.length)
                pending.clear()
                Some(result)
              }
            } else None
          }
          ready match {
            case Some(value)             => Async.succeed(value)
            case None if !current(token) => Async.succeed(sentinel)
            case None                    =>
              pending.pull().flatMap { count =>
                if (count < 0) {
                  commit(token, ()) { eof = true }
                  loop(budget)
                } else if (!current(token)) {
                  pending.clear()
                  Async.succeed(sentinel)
                } else if (budget > 0) loop(budget - 1)
                else Async.reschedule(() => loop(255))
              }
          }
        }
        loop(255).map(value => commit(token, sentinel)(value))
      }
    }

  def intersperse[A](upstream: Reader.AsyncReader[A], separator: A): Reader.AsyncReader[A] =
    intersperse(upstream, separator, upstream.jvmType)

  def intersperse[A, B >: A](
    upstream: Reader.AsyncReader[A],
    separator: B,
    outputType: JvmType
  ): Reader.AsyncReader[B] =
    if (upstream.jvmType ne outputType) new Boundary[A, B](upstream) {
      private val pull                            = new BoxedPhysicalPull(upstream)
      private var first                           = true
      private var cached: AnyRef                  = null
      private var hasCached                       = false
      override def jvmType: JvmType               = outputType
      override protected def onCloseState(): Unit = { first = true; cached = null; hasCached = false }
      def readable(): Async[Boolean]              = {
        val state = synchronized(if (closed) -1 else if (hasCached) 1 else 0)
        if (state < 0) Async.succeed(false)
        else if (state > 0) Async.succeed(true)
        else publicReadable(source.readable())
      }
      def read[C >: B](sentinel: C): Async[C] = publicRead(sentinel) { token =>
        var saved: AnyRef = null
        val hadSaved      = commit(token, false) {
          if (hasCached) { saved = cached; cached = null; hasCached = false; true }
          else false
        }
        if (hadSaved) Async.succeed(saved.asInstanceOf[C])
        else
          pull().map { value =>
            if (value eq EndOfStream) sentinel
            else
              commit(token, sentinel) {
                if (first) { first = false; value.asInstanceOf[C] }
                else { cached = value; hasCached = true; separator }
              }
          }
      }
    }
    else
      (upstream.jvmType match {
        case JvmType.Boolean =>
          new BooleanIntersperse(upstream.asInstanceOf[Reader.AsyncReader[Boolean]], separator.asInstanceOf[Boolean])
            .asInstanceOf[Reader.AsyncReader[A]]
        case JvmType.Byte =>
          new ByteIntersperse(upstream.asInstanceOf[Reader.AsyncReader[Byte]], separator.asInstanceOf[Byte])
            .asInstanceOf[Reader.AsyncReader[A]]
        case JvmType.Int =>
          new IntIntersperse(upstream.asInstanceOf[Reader.AsyncReader[Int]], separator.asInstanceOf[Int])
            .asInstanceOf[Reader.AsyncReader[A]]
        case JvmType.Long =>
          new LongIntersperse(upstream.asInstanceOf[Reader.AsyncReader[Long]], separator.asInstanceOf[Long])
            .asInstanceOf[Reader.AsyncReader[A]]
        case JvmType.Float =>
          new FloatIntersperse(upstream.asInstanceOf[Reader.AsyncReader[Float]], separator.asInstanceOf[Float])
            .asInstanceOf[Reader.AsyncReader[A]]
        case JvmType.Double =>
          new DoubleIntersperse(upstream.asInstanceOf[Reader.AsyncReader[Double]], separator.asInstanceOf[Double])
            .asInstanceOf[Reader.AsyncReader[A]]
        case JvmType.Char =>
          new CharIntersperse(upstream.asInstanceOf[Reader.AsyncReader[Char]], separator.asInstanceOf[Char])
            .asInstanceOf[Reader.AsyncReader[A]]
        case JvmType.Short =>
          new ShortIntersperse(upstream.asInstanceOf[Reader.AsyncReader[Short]], separator.asInstanceOf[Short])
            .asInstanceOf[Reader.AsyncReader[A]]
        case _ =>
          new Boundary[A, A](upstream) {
            private val pull                            = new RefPull(upstream.asInstanceOf[Reader.AsyncReader[AnyRef]])
            private var first                           = true
            private var cached: AnyRef                  = null
            private var hasCached                       = false
            override def jvmType: JvmType               = source.jvmType
            override protected def onCloseState(): Unit = { first = true; cached = null; hasCached = false }
            def readable(): Async[Boolean]              = {
              val state = synchronized(if (closed) -1 else if (hasCached) 1 else 0)
              if (state < 0) Async.succeed(false)
              else if (state > 0) Async.succeed(true)
              else publicReadable(source.readable())
            }
            def read[B >: A](sentinel: B): Async[B] = publicRead(sentinel) { token =>
              var saved: AnyRef = null
              val hadSaved      = commit(token, false) {
                if (hasCached) { saved = cached; cached = null; hasCached = false; true }
                else false
              }
              if (hadSaved) Async.succeed(saved.asInstanceOf[B])
              else
                pull().map { value =>
                  if (value eq EndOfStream) sentinel
                  else
                    commit(token, sentinel) {
                      if (first) { first = false; value.asInstanceOf[B] }
                      else { cached = value; hasCached = true; separator.asInstanceOf[B] }
                    }
                }
            }
          }
      }).asInstanceOf[Reader.AsyncReader[B]]

  def sliding[A](upstream: Reader.AsyncReader[A], size: Int, step: Int): Reader.AsyncReader[Chunk[A]] =
    new Boundary[A, Chunk[A]](upstream) {
      private val window                          = new LaneBuffer(upstream, size)
      private var first                           = true
      private var done                            = false
      private var advancing                       = false
      private var discardRemaining                = 0
      private var fillStartSize                   = 0
      override def jvmType: JvmType               = JvmType.AnyRef
      override protected def onCloseState(): Unit = {
        window.clear()
        first = true
        done = false
        advancing = false
        discardRemaining = 0
        fillStartSize = 0
      }
      def readable(): Async[Boolean] =
        if (synchronized(closed || done)) Async.succeed(false) else publicReadable(source.readable())
      def read[B >: Chunk[A]](sentinel: B): Async[B] = publicRead(sentinel) { token =>
        def continue(budget: Int): Async[B] = {
          val discard = commit(token, Int.MinValue)(discardRemaining)
          if (discard == Int.MinValue) Async.succeed(sentinel)
          else if (discard > 0) {
            val before = window.length
            window.pull().flatMap { count =>
              if (count < 0) {
                commit(token, ()) { done = true; advancing = false; discardRemaining = 0 }
                Async.succeed(sentinel)
              } else if (!current(token)) {
                window.clear()
                Async.succeed(sentinel)
              } else {
                window.drop(window.length - before)
                discardRemaining -= 1
                if (budget > 0) continue(budget - 1) else Async.reschedule(() => continue(255))
              }
            }
          } else
            commit(token, (false, Option.empty[Chunk[A]])) {
              (true, if (window.length == size) Some(window.chunk(size)) else None)
            } match {
              case (false, _)         => Async.succeed(sentinel)
              case (true, Some(full)) =>
                commit(token, ()) { advancing = false }
                Async.succeed(full)
              case (true, None) =>
                window.pull().flatMap { count =>
                  if (count >= 0) {
                    if (!current(token)) { window.clear(); Async.succeed(sentinel) }
                    else if (budget > 0) continue(budget - 1)
                    else Async.reschedule(() => continue(255))
                  } else
                    commit(token, Option.empty[B]) {
                      done = true
                      advancing = false
                      if (window.length == 0 || window.length == fillStartSize) Some(sentinel)
                      else Some(window.chunk(window.length))
                    }.fold[Async[B]](Async.succeed(sentinel))(Async.succeed)
                }
            }
        }
        val result = {
          val prepared = commit(token, false) {
            if (done) false
            else if (advancing) true
            else {
              if (first) {
                first = false
                fillStartSize = 0
              } else if (step <= size) {
                window.drop(step)
                fillStartSize = window.length
              } else {
                window.clear()
                discardRemaining = step - size
                fillStartSize = 0
              }
              advancing = true
              true
            }
          }
          if (!prepared) Async.succeed(sentinel) else continue(255)
        }
        result.map(value => commit(token, sentinel)(value))
      }
    }

  private var commitHookReader: Reader.AsyncReader[_] = null
  private var commitHook: () => Unit                  = null

  private[streams] def beforeCommitForTest(reader: Reader.AsyncReader[_], hook: () => Unit): Unit = synchronized {
    commitHookReader = reader
    commitHook = hook
  }

  private def runBeforeCommitForTest(reader: Reader.AsyncReader[_]): Unit = {
    val hook = synchronized {
      if (commitHookReader ne reader) null
      else {
        val current = commitHook
        commitHookReader = null
        commitHook = null
        current
      }
    }
    if (hook ne null) hook()
  }
}

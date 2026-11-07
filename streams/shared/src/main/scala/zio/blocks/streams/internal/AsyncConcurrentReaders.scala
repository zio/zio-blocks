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

import java.util.concurrent.atomic.AtomicInteger

import zio.blocks.async._
import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.streams.{JvmType, Stream}
import zio.blocks.streams.io.Reader

/** Native, platform-independent asynchronous concurrency boundaries. */
private[streams] object AsyncConcurrentReaders {
  private def lowByte(value: Any): Int = value match {
    case char: java.lang.Character => char.charValue().toInt & 0xff
    case bool: java.lang.Boolean   => if (bool.booleanValue()) 1 else 0
    case number: java.lang.Number  => number.intValue() & 0xff
  }

  private sealed trait MapResult[+A, +B]
  private final class MapIntValue(var value: Int) extends MapResult[Nothing, Int]
  private final case class MapValue[B](value: B)  extends MapResult[Nothing, B]
  private final case class MapSource[A](value: A) extends MapResult[A, Nothing]
  private case object MapSourceEnd                extends MapResult[Nothing, Nothing]
  private case object MapWorkerEnd                extends MapResult[Nothing, Nothing]
  private case object MapWorkerClosed             extends MapResult[Nothing, Nothing]

  private object FoldStep {
    final val Success = 0
    final val Failure = 1
    final val Pending = 2
  }

  /** Accumulator representation is separate from concurrent scheduling. */
  private sealed trait ConcurrentAccumulator[-A, Z] {
    def invoke(value: A): Async[Z]
    def consumeInt(value: Int): Int
    def pending: Async[Z]
    def observe(effect: Async[Z]): Int
    def commit(value: Z): Unit
    def result: Z
    def failure: Throwable
    def trusted: Boolean
  }

  private object ConcurrentAccumulator {
    def make[A, Z](zero: Z, accumulatorType: JvmType, fold: (Z, A) => Async[Z]): ConcurrentAccumulator[A, Z] =
      if (accumulatorType eq JvmType.Int)
        new IntAccumulator(zero.asInstanceOf[Int], fold.asInstanceOf[(Int, A) => Async[Int]])
          .asInstanceOf[ConcurrentAccumulator[A, Z]]
      else if (accumulatorType eq JvmType.Long)
        new LongAccumulator(zero.asInstanceOf[Long], fold.asInstanceOf[(Long, A) => Async[Long]])
          .asInstanceOf[ConcurrentAccumulator[A, Z]]
      else if (accumulatorType eq JvmType.Float)
        new FloatAccumulator(zero.asInstanceOf[Float], fold.asInstanceOf[(Float, A) => Async[Float]])
          .asInstanceOf[ConcurrentAccumulator[A, Z]]
      else if (accumulatorType eq JvmType.Double)
        new DoubleAccumulator(zero.asInstanceOf[Double], fold.asInstanceOf[(Double, A) => Async[Double]])
          .asInstanceOf[ConcurrentAccumulator[A, Z]]
      else new RefAccumulator[A, Z](zero, fold)

    private[AsyncConcurrentReaders] abstract class BaseAccumulator[-A, Z](fold: AnyRef)
        extends ConcurrentAccumulator[A, Z] {
      protected val callback: AnyRef  = fold
      protected var cause: Throwable  = null
      protected var isTrusted         = false
      protected var suspended: AnyRef = null

      protected final def kind[Z1](effect: Async[Z1]): Int = {
        val result = Async.stepKind(effect)
        if (result == FoldStep.Failure) {
          cause = Async.stepCause(effect)
          isTrusted = Async.stepTrusted(effect)
        }
        result
      }

      final def failure: Throwable = cause
      final def trusted: Boolean   = isTrusted
      final def pending: Async[Z]  = {
        val effect = suspended.asInstanceOf[Async[Z]]
        suspended = null
        effect
      }
      def consumeInt(value: Int): Int = {
        val effect = invoke(value.asInstanceOf[A])
        val result = observe(effect)
        if (result == FoldStep.Pending) suspended = effect.asInstanceOf[AnyRef]
        result
      }
    }

    private[AsyncConcurrentReaders] final class IntAccumulator[A](private var acc: Int, fold: (Int, A) => Async[Int])
        extends BaseAccumulator[A, Int](fold.asInstanceOf[AnyRef])
        with Async.IntStepFold[Unit] {
      private var outcome              = FoldStep.Pending
      def invoke(value: A): Async[Int] =
        try StreamError.callbackAsync(callback.asInstanceOf[(Int, A) => Async[Int]](acc, value))
        catch { case failure: Throwable => Async.fail(StreamError.callbackFailure(failure)) }
      def observe(effect: Async[Int]): Int = {
        val result = kind(effect)
        if (result == FoldStep.Success) acc = Async.stepInt(effect)
        result
      }
      override def consumeInt(value: Int): Int = {
        val effect =
          try StreamError.callbackAsync(callback.asInstanceOf[(Int, Int) => Async[Int]](acc, value))
          catch { case failure: Throwable => Async.fail(StreamError.callbackFailure(failure)) }
        outcome = FoldStep.Pending
        Async.foldIntStep(effect)(this)
        outcome
      }
      def failureInt(failure: Throwable): Unit                 = { cause = failure; isTrusted = false; outcome = FoldStep.Failure }
      def pendingInt(pollable: Pollable[Int]): Unit            = { suspended = pollable; outcome = FoldStep.Pending }
      def successInt(value: Int): Unit                         = { acc = value; outcome = FoldStep.Success }
      override def trustedFailureInt(failure: Throwable): Unit = {
        cause = failure; isTrusted = true; outcome = FoldStep.Failure
      }
      def commit(value: Int): Unit = acc = value
      def result: Int              = acc
    }

    private[AsyncConcurrentReaders] final class LongAccumulator[A](
      private var acc: Long,
      fold: (Long, A) => Async[Long]
    ) extends BaseAccumulator[A, Long](fold.asInstanceOf[AnyRef])
        with Async.LongStepFold[Unit] {
      private var outcome               = FoldStep.Pending
      def invoke(value: A): Async[Long] =
        try StreamError.callbackAsync(callback.asInstanceOf[(Long, A) => Async[Long]](acc, value))
        catch { case failure: Throwable => Async.fail(StreamError.callbackFailure(failure)) }
      def observe(effect: Async[Long]): Int = {
        val result = kind(effect)
        if (result == FoldStep.Success) acc = Async.stepLong(effect)
        result
      }
      override def consumeInt(value: Int): Int = {
        val effect =
          try StreamError.callbackAsync(callback.asInstanceOf[(Long, Int) => Async[Long]](acc, value))
          catch { case failure: Throwable => Async.fail(StreamError.callbackFailure(failure)) }
        outcome = FoldStep.Pending
        Async.foldLongStep(effect)(this)
        outcome
      }
      def failureLong(failure: Throwable): Unit                 = { cause = failure; isTrusted = false; outcome = FoldStep.Failure }
      def pendingLong(pollable: Pollable[Long]): Unit           = { suspended = pollable; outcome = FoldStep.Pending }
      def successLong(value: Long): Unit                        = { acc = value; outcome = FoldStep.Success }
      override def trustedFailureLong(failure: Throwable): Unit = {
        cause = failure; isTrusted = true; outcome = FoldStep.Failure
      }
      def commit(value: Long): Unit = acc = value
      def result: Long              = acc
    }

    private[AsyncConcurrentReaders] final class FloatAccumulator[A](
      private var acc: Float,
      fold: (Float, A) => Async[Float]
    ) extends BaseAccumulator[A, Float](fold.asInstanceOf[AnyRef]) {
      def invoke(value: A): Async[Float] =
        try StreamError.callbackAsync(callback.asInstanceOf[(Float, A) => Async[Float]](acc, value))
        catch { case failure: Throwable => Async.fail(StreamError.callbackFailure(failure)) }
      def observe(effect: Async[Float]): Int = {
        val result = kind(effect)
        if (result == FoldStep.Success) acc = Async.stepValue(effect)
        result
      }
      override def consumeInt(value: Int): Int = {
        val effect =
          try StreamError.callbackAsync(callback.asInstanceOf[(Float, Int) => Async[Float]](acc, value))
          catch { case failure: Throwable => Async.fail(StreamError.callbackFailure(failure)) }
        val result = Async.stepKind(effect)
        if (result == FoldStep.Failure) {
          cause = Async.stepCause(effect)
          isTrusted = Async.stepTrusted(effect)
        }
        if (result == FoldStep.Success) acc = Async.stepValue(effect)
        else if (result == FoldStep.Pending) suspended = effect.asInstanceOf[AnyRef]
        result
      }
      def commit(value: Float): Unit = acc = value
      def result: Float              = acc
    }

    private[AsyncConcurrentReaders] final class DoubleAccumulator[A](
      private var acc: Double,
      fold: (Double, A) => Async[Double]
    ) extends BaseAccumulator[A, Double](fold.asInstanceOf[AnyRef]) {
      def invoke(value: A): Async[Double] =
        try StreamError.callbackAsync(callback.asInstanceOf[(Double, A) => Async[Double]](acc, value))
        catch { case failure: Throwable => Async.fail(StreamError.callbackFailure(failure)) }
      def observe(effect: Async[Double]): Int = {
        val result = kind(effect)
        if (result == FoldStep.Success) acc = Async.stepValue(effect)
        result
      }
      override def consumeInt(value: Int): Int = {
        val effect =
          try StreamError.callbackAsync(callback.asInstanceOf[(Double, Int) => Async[Double]](acc, value))
          catch { case failure: Throwable => Async.fail(StreamError.callbackFailure(failure)) }
        val result = Async.stepKind(effect)
        if (result == FoldStep.Failure) {
          cause = Async.stepCause(effect)
          isTrusted = Async.stepTrusted(effect)
        }
        if (result == FoldStep.Success) acc = Async.stepValue(effect)
        else if (result == FoldStep.Pending) suspended = effect.asInstanceOf[AnyRef]
        result
      }
      def commit(value: Double): Unit = acc = value
      def result: Double              = acc
    }

    private final class RefAccumulator[A, Z](private var acc: Z, fold: (Z, A) => Async[Z])
        extends BaseAccumulator[A, Z](fold.asInstanceOf[AnyRef]) {
      def invoke(value: A): Async[Z] =
        try StreamError.callbackAsync(callback.asInstanceOf[(Z, A) => Async[Z]](acc, value))
        catch { case failure: Throwable => Async.fail(StreamError.callbackFailure(failure)) }
      def observe(effect: Async[Z]): Int = {
        val result = kind(effect)
        if (result == FoldStep.Success) acc = Async.stepValue(effect)
        result
      }
      def commit(value: Z): Unit = acc = value
      def result: Z              = acc
    }
  }

  private final class LanePuller[A](reader: Reader.AsyncReader[A]) {
    private val longValue   = new Array[Long](1)
    private val doubleValue = new Array[Double](1)

    def apply[R](end: => R, value: A => R): Async[R] = reader.jvmType match {
      case JvmType.Boolean =>
        reader.readBooleanPhysical(-1).map(v => if (v < 0) end else value((v != 0).asInstanceOf[A]))
      case JvmType.Byte =>
        reader.readBytePhysical().map(v => if (v < 0) end else value(v.toByte.asInstanceOf[A]))
      case JvmType.Char =>
        reader
          .readCharPhysical(Int.MinValue)
          .map(v => if (v == Int.MinValue) end else value(v.toChar.asInstanceOf[A]))
      case JvmType.Short =>
        reader
          .readShortPhysical(Int.MinValue)
          .map(v => if (v == Int.MinValue) end else value(v.toShort.asInstanceOf[A]))
      case JvmType.Int =>
        reader
          .readIntPhysical(Long.MinValue)
          .map(v => if (v == Long.MinValue) end else value(v.toInt.asInstanceOf[A]))
      case JvmType.Long =>
        reader
          .readLongsPhysical(longValue, 0, 1)
          .map(n => if (n < 0) end else value(longValue(0).asInstanceOf[A]))
      case JvmType.Float =>
        reader
          .readFloatPhysical(Double.MaxValue)
          .map(v => if (v == Double.MaxValue) end else value(v.toFloat.asInstanceOf[A]))
      case JvmType.Double =>
        reader
          .readDoublesPhysical(doubleValue, 0, 1)
          .map(n => if (n < 0) end else value(doubleValue(0).asInstanceOf[A]))
      case JvmType.AnyRef =>
        reader
          .read[Any](EndOfStream)
          .map(v => if (v.asInstanceOf[AnyRef] eq EndOfStream) end else value(v.asInstanceOf[A]))
    }
  }

  /**
   * Physical source representation for the selector-free concurrent terminal
   * loop.
   */
  private final class DirectPuller[A](reader: Reader.AsyncReader[A]) {
    private val longs   = new Array[Long](1)
    private val doubles = new Array[Double](1)
    private var long    = 0L
    private var double  = 0.0d
    private var ref: A  = null.asInstanceOf[A]
    private var end     = false

    def read(): Async[Unit] = reader.jvmType match {
      case JvmType.Boolean => reader.readBooleanPhysical(-1).map { value => end = value < 0; long = value.toLong; () }
      case JvmType.Byte    => reader.readBytePhysical().map { value => end = value < 0; long = value.toLong; () }
      case JvmType.Char    =>
        reader.readCharPhysical(Int.MinValue).map { value => end = value == Int.MinValue; long = value.toLong; () }
      case JvmType.Short =>
        reader.readShortPhysical(Int.MinValue).map { value => end = value == Int.MinValue; long = value.toLong; () }
      case JvmType.Int =>
        reader.readIntPhysical(Long.MinValue).map { value => end = value == Long.MinValue; long = value; () }
      case JvmType.Long =>
        reader.readLongsPhysical(longs, 0, 1).map { count => end = count < 0; if (!end) long = longs(0); () }
      case JvmType.Float =>
        reader.readFloatPhysical(Double.MaxValue).map { value => end = value == Double.MaxValue; double = value; () }
      case JvmType.Double =>
        reader.readDoublesPhysical(doubles, 0, 1).map { count => end = count < 0; if (!end) double = doubles(0); () }
      case JvmType.AnyRef =>
        reader.read[Any](EndOfStream).map { value =>
          end = value.asInstanceOf[AnyRef] eq EndOfStream
          if (!end) ref = value.asInstanceOf[A]
          ()
        }
    }

    def ended: Boolean = end

    def invoke[B](f: A => Async[B]): Async[B] = reader.jvmType match {
      case JvmType.Boolean => f((long != 0L).asInstanceOf[A])
      case JvmType.Byte    => f(long.toByte.asInstanceOf[A])
      case JvmType.Char    => f(long.toChar.asInstanceOf[A])
      case JvmType.Short   => f(long.toShort.asInstanceOf[A])
      case JvmType.Int     => f(long.toInt.asInstanceOf[A])
      case JvmType.Long    => f(long.asInstanceOf[A])
      case JvmType.Float   => f(double.toFloat.asInstanceOf[A])
      case JvmType.Double  => f(double.asInstanceOf[A])
      case JvmType.AnyRef  => f(ref)
    }
  }

  private final class OwnedReader[A](val reader: Reader.AsyncReader[A]) {
    val pull                  = new LanePuller(reader)
    val close                 = new Reader.MemoizedClose(() => (), () => reader.close())
    private val intResult     = new MapIntValue(0)
    private val intResultPull = new IntResultPull

    def pullResult(end: MapResult[Nothing, A]): Async[MapResult[Nothing, A]] =
      if (reader.jvmType eq JvmType.Int) intResultPull.prepare(end)
      else pull(end, value => MapValue(value))

    private final class IntResultPull extends Pollable[MapResult[Nothing, A]] with Async.LongStepFold[Unit] {
      private var cause: Throwable           = null
      private var end: MapResult[Nothing, A] = null
      private var kind                       = FoldStep.Pending
      private var pending: Pollable[Long]    = null
      private var trusted                    = false
      private var value                      = 0L

      def failureLong(failure: Throwable): Unit                 = { cause = failure; kind = FoldStep.Failure; trusted = false }
      def pendingLong(pollable: Pollable[Long]): Unit           = { pending = pollable; kind = FoldStep.Pending }
      def successLong(result: Long): Unit                       = { value = result; kind = FoldStep.Success }
      override def trustedFailureLong(failure: Throwable): Unit = {
        cause = failure; kind = FoldStep.Failure; trusted = true
      }

      def prepare(result: MapResult[Nothing, A]): Async[MapResult[Nothing, A]] = {
        end = result
        this
      }

      def poll(onComplete: Runnable): Async[MapResult[Nothing, A]] = {
        resetProbe()
        reader.pollIntPhysical(Long.MinValue, onComplete, this)
        if (kind == FoldStep.Success) Async.succeed(result(value))
        else if (kind == FoldStep.Failure) {
          if (trusted) Async.failTrusted(cause) else Async.fail(cause)
        } else pending.map(result)
      }

      private def resetProbe(): Unit = {
        cause = null
        kind = FoldStep.Pending
        pending = null
        trusted = false
      }

      private def result(value: Long): MapResult[Nothing, A] =
        if (value == Long.MinValue) end
        else {
          intResult.value = value.toInt
          intResult.asInstanceOf[MapResult[Nothing, A]]
        }
    }
  }

  private final class AcquiredReader[A](@volatile var owned: OwnedReader[A]) {
    @volatile var transferred = false
  }

  private def async[A](reader: Reader[A]): Reader.AsyncReader[A] = reader match {
    case value: Reader.AsyncReader[A @unchecked] => value
    case value: Reader.SyncReader[A @unchecked]  => value.toAsync
  }

  private def failure[A](cause: Throwable): Async[A] = cause match {
    case error: StreamError if error.isTrusted => Async.failTrusted(error)
    case other                                 => Async.fail(other)
  }

  private abstract class ConcurrentReader[A](override val jvmType: JvmType)
      extends Reader.AsyncReader[A]
      with AsyncInterpreter.TerminalDriver {
    private var closed                                  = false
    private var eof                                     = false
    private var terminalFailure: Throwable              = null
    private var failed                                  = false
    private var generation                              = 0L
    private var reading                                 = false
    private var activeRead: Completer[Unit]             = null
    private var activeRun: Async.Running[_]             = null
    @volatile private var activeOwner: PullOperation[_] = null
    private var staleCancellationFailure: Throwable     = null
    private var closingRead: Async[Unit]                = Async.succeed(())
    private val cleanup                                 = new Reader.MemoizedClose(() => (), () => closeOwned())
    private val closeOwner                              = new Reader.MemoizedClose(
      () =>
        synchronized {
          closed = true
          generation += 1
          closingRead =
            if ((activeRead eq null) || ((activeRun ne null) && activeRun.isDriverThread)) Async.succeed(())
            else activeRead
        },
      () => join(cleanup.close(), closingRead)
    )

    private var invalidateBeforeCommit             = false
    private var invalidateBeforeFinish             = false
    private var afterAcquisitionHook: () => Unit   = null
    private var afterBeginHandoffHook: () => Unit  = null
    private var afterYieldHook: () => Unit         = null
    private var beforeRegistrationHook: () => Unit = null

    final def isClosed: Async[Boolean]   = Async.succeed(synchronized(closed || eof || failed))
    final def readable(): Async[Boolean] =
      Async.succeed(synchronized(!closed && !eof && !failed))

    protected def closeOwned(): Async[Unit]
    protected def readRaw[B >: A](sentinel: B, expected: Long): Async[B]
    protected def concurrentFold[Z](
      expected: Long,
      zero: Z,
      accumulatorType: JvmType,
      fold: (Z, A) => Async[Z]
    ): Async[Z]

    final def foldAsync[A1, Z](zero: Z, accumulatorType: JvmType, fold: (Z, A1) => Async[Z]): Async[Z] =
      publicRead(() => zero)(expected =>
        concurrentFold(expected, zero, accumulatorType, fold.asInstanceOf[(Z, A) => Async[Z]])
      )

    final def read[B >: A](sentinel: B): Async[B]                                         = publicRead(() => sentinel)(readRaw(sentinel, _))
    private def ended(value: A): Boolean                                                  = value.asInstanceOf[AnyRef] eq EndOfStream
    final override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Async[Int] =
      publicRead(() => sentinel)(expected =>
        readRaw[A](EndOfStream.asInstanceOf[A], expected).map(v => if (ended(v)) sentinel else if (ev(v)) 1 else 0)
      )
    final override def readByte(): Async[Int] =
      publicRead(() => -1)(expected =>
        readRaw[A](EndOfStream.asInstanceOf[A], expected).map { v =>
          if (ended(v)) -1
          else
            jvmType match {
              case JvmType.Boolean => if (v.asInstanceOf[Boolean]) 1 else 0
              case JvmType.Char    => v.asInstanceOf[Char].toInt & 0xff
              case _               => lowByte(v)
            }
        }
      )
    final override def readChar(sentinel: Int)(implicit ev: A <:< Char): Async[Int] =
      publicRead(() => sentinel)(expected =>
        readRaw[A](EndOfStream.asInstanceOf[A], expected).map(v => if (ended(v)) sentinel else ev(v).toInt)
      )
    final override def readShort(sentinel: Int)(implicit ev: A <:< Short): Async[Int] =
      publicRead(() => sentinel)(expected =>
        readRaw[A](EndOfStream.asInstanceOf[A], expected).map(v => if (ended(v)) sentinel else ev(v).toInt)
      )
    final override def readInt(sentinel: Long)(implicit ev: A <:< Int): Async[Long] =
      publicRead(() => sentinel)(expected =>
        readRaw[A](EndOfStream.asInstanceOf[A], expected).map(v => if (ended(v)) sentinel else ev(v).toLong)
      )
    final override def readLong(sentinel: Long)(implicit ev: A <:< Long): Async[Long] =
      publicRead(() => sentinel)(expected =>
        readRaw[A](EndOfStream.asInstanceOf[A], expected).map(v => if (ended(v)) sentinel else ev(v))
      )
    final override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Async[Double] =
      publicRead(() => sentinel)(expected =>
        readRaw[A](EndOfStream.asInstanceOf[A], expected).map(v => if (ended(v)) sentinel else ev(v).toDouble)
      )
    final override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Async[Double] =
      publicRead(() => sentinel)(expected =>
        readRaw[A](EndOfStream.asInstanceOf[A], expected).map(v => if (ended(v)) sentinel else ev(v))
      )

    private def validate(array: Array[_], offset: Int, length: Int): Async[Unit] =
      try { Reader.validateArrayRange(array, offset, length); Async.succeed(()) }
      catch { case cause: Throwable => Async.fail(cause) }

    private def bulk(length: Int, count: () => Int)(pull: (Long, Int) => Async[Boolean]): Async[Int] =
      publicRead(count) { expected =>
        if (length == 0) Async.succeed(0)
        else {
          def loop(index: Int, budget: Int): Async[Int] =
            pull(expected, index).flatMap { present =>
              if (!present) Async.succeed(if (index == 0) -1 else index)
              else if (index + 1 >= length) Async.succeed(index + 1)
              else if (tryReadable ne Reader.Available) Async.succeed(index + 1)
              else if (budget > 0) loop(index + 1, budget - 1)
              else Async.reschedule(() => loop(index + 1, 255))
            }
          loop(0, 255)
        }
      }

    final override def readN[B >: A](n: Int): Async[Chunk[B]] = {
      val builder            = ChunkBuilder.make[B](math.min(math.max(n, 0), 16))
      def result(): Chunk[B] = builder.result()
      publicRead(() => result()) { expected =>
        if (n <= 0) Async.succeed(result())
        else {
          def loop(index: Int, budget: Int): Async[Chunk[B]] =
            if (index >= n) Async.succeed(result())
            else
              readRaw[Any](EndOfStream, expected).flatMap { value =>
                if (value.asInstanceOf[AnyRef] eq EndOfStream) Async.succeed(result())
                else {
                  commit(expected)(builder += value.asInstanceOf[B])
                  if (budget > 0) loop(index + 1, budget - 1)
                  else Async.reschedule(() => loop(index + 1, 255))
                }
              }
          loop(0, 255)
        }
      }
    }

    final override def readUpToN[B >: A](n: Int): Async[Chunk[B]] = {
      val builder            = ChunkBuilder.make[B](math.min(math.max(n, 0), 64))
      def result(): Chunk[B] = builder.result()
      publicRead(() => result()) { expected =>
        if (n <= 0) Async.succeed(result())
        else
          readRaw[Any](EndOfStream, expected).map { value =>
            if (!(value.asInstanceOf[AnyRef] eq EndOfStream)) commit(expected)(builder += value.asInstanceOf[B])
            result()
          }
      }
    }

    final override def readBytes(dest: Array[Byte], offset: Int, length: Int)(implicit ev: A <:< Byte): Async[Int] =
      validate(dest, offset, length).flatMap { _ =>
        var count = 0
        bulk(length, () => count)((expected, i) =>
          readRaw[A](EndOfStream.asInstanceOf[A], expected).map { v =>
            if (ended(v)) false else commit(expected) { dest(offset + i) = ev(v); count += 1 }
          }
        )
      }
    final override def readInts(dest: Array[Int], offset: Int, length: Int)(implicit ev: A <:< Int): Async[Int] =
      validate(dest, offset, length).flatMap { _ =>
        var count = 0
        bulk(length, () => count)((expected, i) =>
          readRaw[A](EndOfStream.asInstanceOf[A], expected).map { v =>
            if (ended(v)) false else commit(expected) { dest(offset + i) = ev(v); count += 1 }
          }
        )
      }
    final override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: A <:< Long): Async[Int] =
      validate(dest, offset, length).flatMap { _ =>
        var count = 0
        bulk(length, () => count)((expected, i) =>
          readRaw[A](EndOfStream.asInstanceOf[A], expected).map { v =>
            if (ended(v)) false else commit(expected) { dest(offset + i) = ev(v); count += 1 }
          }
        )
      }
    final override def readFloats(dest: Array[Float], offset: Int, length: Int)(implicit ev: A <:< Float): Async[Int] =
      validate(dest, offset, length).flatMap { _ =>
        var count = 0
        bulk(length, () => count)((expected, i) =>
          readRaw[A](EndOfStream.asInstanceOf[A], expected).map { v =>
            if (ended(v)) false else commit(expected) { dest(offset + i) = ev(v); count += 1 }
          }
        )
      }
    final override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit
      ev: A <:< Double
    ): Async[Int] =
      validate(dest, offset, length).flatMap { _ =>
        var count = 0
        bulk(length, () => count)((expected, i) =>
          readRaw[A](EndOfStream.asInstanceOf[A], expected).map { v =>
            if (ended(v)) false else commit(expected) { dest(offset + i) = ev(v); count += 1 }
          }
        )
      }

    protected final def isCurrent(expected: Long): Boolean = synchronized {
      expected == generation && !closed && !eof && !failed
    }

    protected final def pullCurrent(expected: Long): Boolean = synchronized {
      val owner = activeOwner
      expected == generation && !closed && !eof && !failed && (owner ne null) && owner.commitOpen
    }

    protected final def beginHandoff(expected: Long): Boolean = {
      val owner = activeOwner
      (owner ne null) && expected == generation && owner.beginHandoff()
    }

    protected final def endHandoff(): Boolean = {
      val owner = activeOwner
      (owner ne null) && owner.endHandoff()
    }

    private def commit(expected: Long)(action: => Unit): Boolean = synchronized {
      if (takeCommitInvalidationForTest()) { closed = true; generation += 1 }
      val owner = activeOwner
      if (expected == generation && !closed && !eof && !failed && (owner ne null) && owner.commitOpen) {
        action
        true
      } else false
    }

    private def commitTerminal(expected: Long)(action: => Unit): Boolean = synchronized {
      val owner = activeOwner
      if (expected == generation && !closed && !eof && !failed && (owner ne null) && owner.commitOpen) {
        action
        owner.terminalCommitted = true
        true
      } else false
    }

    private def cleanupOnce(): Async[Unit] = cleanup.close()

    protected final def publicRead[B](cancelResult: () => B)(effect: Long => Async[B]): Async[B] =
      new PullOperation[B](cancelResult, effect)

    private final class ConcurrentLongFold(
      expected: Long,
      private var acc: Long,
      fold: (Long, A) => Async[Long]
    ) extends Pollable[Long]
        with Runnable
        with Async.StepFold[A, Unit]
        with Async.LongStepFold[Unit] {
      private var cause: Throwable = null
      private var kind             = FoldStep.Pending
      private var longValue        = 0L
      private var scheduled        = false
      private var trusted          = false
      private var value: A         = null.asInstanceOf[A]
      private var wake: Runnable   = null

      def failure(failure: Throwable): Unit                 = { cause = failure; kind = FoldStep.Failure; trusted = false }
      def failureLong(failure: Throwable): Unit             = { cause = failure; kind = FoldStep.Failure; trusted = false }
      def pending(pollable: Pollable[A]): Unit              = { val _ = pollable; kind = FoldStep.Pending }
      def pendingLong(pollable: Pollable[Long]): Unit       = { val _ = pollable; kind = FoldStep.Pending }
      def success(result: A): Unit                          = { value = result; kind = FoldStep.Success }
      def successLong(result: Long): Unit                   = { longValue = result; kind = FoldStep.Success }
      override def trustedFailure(failure: Throwable): Unit = {
        cause = failure; kind = FoldStep.Failure; trusted = true
      }
      override def trustedFailureLong(failure: Throwable): Unit = {
        cause = failure; kind = FoldStep.Failure; trusted = true
      }

      def poll(onComplete: Runnable): Async[Long] = {
        var budget = 1024
        while (budget > 0) {
          val read = readRaw[A](EndOfStream.asInstanceOf[A], expected)
          reset()
          Async.foldStep(read)(this)
          if (kind == FoldStep.Pending)
            return read.flatMap { result =>
              if (ended(result)) finish(acc, expected)
              else StreamError.callbackAsync(fold(acc, result)).flatMap { next => acc = next; this }
            }
          else if (kind == FoldStep.Failure)
            return if (trusted) Async.failTrusted(cause) else Async.fail(cause)
          else if (ended(value)) return finish(acc, expected)

          val reduced = StreamError.callbackAsync(fold(acc, value))
          reset()
          Async.foldLongStep(reduced)(this)
          if (kind == FoldStep.Success) acc = longValue
          else if (kind == FoldStep.Pending) return reduced.flatMap { next => acc = next; this }
          else return if (trusted) Async.failTrusted(cause) else Async.fail(cause)
          budget -= 1
        }
        val submit = synchronized {
          wake = onComplete
          if (scheduled) false
          else { scheduled = true; true }
        }
        if (submit) Async.schedule(this, forceMacrotask = true)
        this
      }

      def run(): Unit = {
        val callback = synchronized {
          val current = wake
          wake = null
          scheduled = false
          current
        }
        callback.run()
      }

      private def reset(): Unit = {
        cause = null
        kind = FoldStep.Pending
        trusted = false
        value = null.asInstanceOf[A]
      }
    }

    final def foldLong(zero: Long, fold: (Long, A) => Async[Long]): Async[Long] =
      publicRead(() => zero) { expected =>
        val direct = foldLongDirect(expected, zero, fold)
        if (direct.asInstanceOf[AnyRef] eq null) continueLongFold(expected, zero, fold) else direct
      }

    final def foldLongInt(zero: Long, fold: (Long, Int) => Async[Long]): Async[Long] =
      publicRead(() => zero) { expected =>
        val direct = foldLongIntDirect(expected, zero, fold)
        if (direct.asInstanceOf[AnyRef] eq null)
          continueLongFold(expected, zero, fold.asInstanceOf[(Long, A) => Async[Long]])
        else direct
      }

    protected final def continueLongFold(expected: Long, acc: Long, fold: (Long, A) => Async[Long]): Async[Long] =
      new ConcurrentLongFold(expected, acc, fold)

    protected def foldLongDirect(expected: Long, zero: Long, fold: (Long, A) => Async[Long]): Async[Long] = null

    protected def foldLongIntDirect(
      expected: Long,
      zero: Long,
      fold: (Long, Int) => Async[Long]
    ): Async[Long] = null

    private[AsyncConcurrentReaders] def invalidateBeforeCommitForTest(): Unit = synchronized {
      invalidateBeforeCommit = true
    }

    private[AsyncConcurrentReaders] def invalidateBeforeFinishForTest(): Unit = synchronized {
      invalidateBeforeFinish = true
    }

    private[AsyncConcurrentReaders] def afterBeginHandoffForTest(hook: () => Unit): Unit = synchronized {
      afterBeginHandoffHook = hook
    }

    private[AsyncConcurrentReaders] def afterAcquisitionForTest(hook: () => Unit): Unit = synchronized {
      afterAcquisitionHook = hook
    }

    private[AsyncConcurrentReaders] def afterYieldForTest(hook: () => Unit): Unit = synchronized {
      afterYieldHook = hook
    }

    private[AsyncConcurrentReaders] def beforeRegistrationForTest(hook: () => Unit): Unit = synchronized {
      beforeRegistrationHook = hook
    }

    private[AsyncConcurrentReaders] def handoffCancellationClaimedForTest(): Boolean = {
      val owner = synchronized(activeOwner)
      (owner ne null) && owner.handoffCancellationClaimed
    }

    private def takeHook(select: => (() => Unit), clear: => Unit): () => Unit = synchronized {
      val hook = select
      clear
      hook
    }

    private def takeCommitInvalidationForTest(): Boolean = synchronized {
      val invalidate = invalidateBeforeCommit
      invalidateBeforeCommit = false
      invalidate
    }

    private def takeFinishInvalidationForTest(): Boolean = synchronized {
      val invalidate = invalidateBeforeFinish
      invalidateBeforeFinish = false
      invalidate
    }

    private def runAfterBeginHandoffHook(): Unit = {
      val hook = takeHook(afterBeginHandoffHook, { afterBeginHandoffHook = null })
      if (hook ne null) hook()
    }

    protected final def runAfterAcquisitionHook(): Unit = {
      val hook = takeHook(afterAcquisitionHook, { afterAcquisitionHook = null })
      if (hook ne null) hook()
    }

    protected final def runAfterYieldHook(): Unit = {
      val hook = takeHook(afterYieldHook, { afterYieldHook = null })
      if (hook ne null) hook()
    }

    private def runBeforeRegistrationHook(): Unit = {
      val hook = takeHook(beforeRegistrationHook, { beforeRegistrationHook = null })
      if (hook ne null) hook()
    }

    private final class PullOperation[B](cancelResult: () => B, effect: Long => Async[B]) extends Async.Operation[B] {
      private val completion                       = new Completer[B]
      private var started                          = false
      private var cancelled                        = false
      private var finished                         = false
      private val handoffState                     = new AtomicInteger(0)
      var terminalCommitted                        = false
      private var running: Async.Running[_]        = null
      private var cancellation: Async.Cancellation = null
      private var cancellationJoin: Async[Unit]    = null

      def poll(onComplete: Runnable): Async[B] = {
        val expected = ConcurrentReader.this.synchronized {
          if (started || cancelled) Long.MinValue
          else {
            started = true
            if (reading) {
              finished = true
              completion.fail(new IllegalStateException(SyncInterpreter.ConcurrentOperationMessage))
              Long.MinValue
            } else {
              reading = true
              activeRead = new Completer[Unit]
              activeOwner = this
              generation
            }
          }
        }
        if (expected != Long.MinValue) {
          val stopped   = terminal(cancelResult())
          val attempted =
            if (stopped.asInstanceOf[AnyRef] eq null) protectRead(effect(expected), cancelResult(), expected)
            else stopped
          val publish = attempted.foldCause { cause => finishFailure(cause); () } { value => finishSuccess(value); () }
          runBeforeRegistrationHook()
          Async.startRegistered(publish) { child =>
            val owner = ConcurrentReader.this.synchronized {
              if (!finished) { running = child; activeRun = child }
              if (cancelled) cancellation else null
            }
            if (owner ne null) {
              owner.primary(Async.cancelWithCleanup(child))
              owner.noReplacement()
            }
          }
        }
        val result = completion.poll(onComplete)
        if (result.asInstanceOf[AnyRef] eq completion) this else result
      }

      def commitOpen: Boolean     = !cancelled && !finished
      def beginHandoff(): Boolean = {
        val claimed = handoffState.compareAndSet(0, 1)
        if (claimed) runAfterBeginHandoffHook()
        claimed
      }
      def endHandoff(): Boolean =
        if (handoffState.compareAndSet(1, 0)) true
        else if (handoffState.compareAndSet(3, 0)) false
        else true

      def handoffCancellationClaimed: Boolean = handoffState.get == 3

      private def claimCancellation(): Boolean = {
        var decided = false
        var join    = false
        while (!decided) {
          handoffState.get match {
            case 0 => decided = handoffState.compareAndSet(0, 2)
            case 1 =>
              if (handoffState.compareAndSet(1, 3)) { join = true; decided = true }
            case 2 => decided = true
            case 3 => join = true; decided = true
            case _ => throw new IllegalStateException("invalid concurrent-reader handoff state")
          }
        }
        join
      }

      private def claim(): Completer[Unit] = {
        finished = true
        if (activeOwner eq this) {
          reading = false
          activeRun = null
          activeOwner = null
          val value = activeRead
          activeRead = null
          value
        } else null
      }

      private def finishSuccess(value: B): Unit = {
        val claimed = ConcurrentReader.this.synchronized {
          if (!cancelled && !finished && (activeOwner eq this)) Some(claim()) else None
        }
        claimed.foreach { done => if (done ne null) done.succeed(()); completion.succeed(value) }
      }

      private def finishFailure(cause: Throwable): Unit = {
        val claimed = ConcurrentReader.this.synchronized {
          if (!cancelled && !finished && (activeOwner eq this)) Some(claim()) else None
        }
        claimed.foreach { done => if (done ne null) done.succeed(()); completion.fail(cause) }
      }

      protected def cancelOperation(): Async[Unit] = {
        val (joined, child, settle) = ConcurrentReader.this.synchronized {
          if (cancellation ne null) (cancellationJoin, null, false)
          else if (finished) (Async.succeed(()), null, false)
          else if (terminalCommitted) (completion.peek.map(_ => ()), null, false)
          else if (claimCancellation()) (completion.peek.map(_ => ()), null, false)
          else {
            cancelled = true
            val result = cancelResult()
            cancellation = Async.cancellation()
            cancellationJoin = cancellation.effect.either.flatMap {
              case Left(cause) =>
                val done = ConcurrentReader.this.synchronized(claim())
                if (done ne null) done.succeed(())
                if (ConcurrentReader.this.synchronized(closed && !failed)) {
                  ConcurrentReader.this.synchronized { staleCancellationFailure = cause }
                  completion.succeed(result)
                  Async.fail(cause)
                } else {
                  completion.fail(cause)
                  Async.fail(cause)
                }
              case Right(_) =>
                val done = ConcurrentReader.this.synchronized(claim())
                if (done ne null) done.succeed(())
                completion.succeed(result)
                Async.succeed(())
            }
            (cancellationJoin, running, !started)
          }
        }
        if (child ne null) {
          cancellation.primary(Async.cancelWithCleanup(child))
          cancellation.noReplacement()
        } else if (settle) {
          cancellation.primary(Async.succeed(()))
          cancellation.noReplacement()
        }
        joined
      }
    }

    final def close(): Async[Unit] = closeOwner.close().catchAll { cause =>
      if (synchronized(cause eq staleCancellationFailure)) Async.succeed(()) else Async.fail(cause)
    }

    protected final def terminal[B](sentinel: B): Async[B] = {
      val state = synchronized((failed, terminalFailure, closed || eof))
      if (state._1) failure(state._2)
      else if (state._3) Async.succeed(sentinel)
      else null
    }

    protected final def finish[B](sentinel: B, expected: Long): Async[B] = {
      if (takeFinishInvalidationForTest()) synchronized { closed = true; generation += 1 }
      val accepted = commitTerminal(expected) {
        eof = true
        generation += 1
      }
      if (!accepted) return Async.succeed(sentinel)
      cleanupOnce().either.flatMap {
        case Right(_)             => Async.succeed(sentinel)
        case Left(cleanupFailure) =>
          val primary = synchronized {
            if (!failed) {
              failed = true
              terminalFailure = StreamError.attachCleanup(null, cleanupFailure)
            }
            terminalFailure
          }
          failure(primary)
      }
    }

    protected final def protectRead[B](effect: => Async[B], sentinel: B, expected: Long): Async[B] = {
      val attempted =
        try effect
        catch { case cause: Throwable => Async.fail(cause) }
      attempted.catchAll { cause =>
        if (synchronized(closed && !failed)) Async.succeed(sentinel)
        else fail(cause, expected)
      }
    }

    protected final def fail[B](cause: Throwable, expected: Long): Async[B] = {
      val accepted = commitTerminal(expected) {
        if (!failed) {
          failed = true
          terminalFailure = cause
          generation += 1
        }
      }
      if (!accepted) return failure(cause)
      val primary = synchronized(terminalFailure)
      cleanupOnce().either.flatMap {
        case Right(_)        => failure(primary)
        case Left(secondary) =>
          val combined = synchronized {
            // `failed` is the presence bit: a null primary is still a real
            // failure and must not be replaced by later cleanup failure.
            terminalFailure = if (primary eq null) null else StreamError.attachCleanupReplay(primary, secondary)
            terminalFailure
          }
          failure(combined)
      }
    }
  }

  private[streams] def foldLong(
    reader: Reader.AsyncReader[Int],
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ): Async[Long] = reader match {
    case concurrent: ConcurrentReader[Int @unchecked] => concurrent.foldAsync(zero, JvmType.Long, fold)
    case _                                            => null
  }

  def mapPar[A, B](
    upstream0: Reader[A],
    n: Int,
    f: A => Async[B],
    outType: JvmType,
    closeUpstream: Boolean = true
  ): Reader.AsyncReader[B] =
    new ConcurrentReader[B](outType) {
      private val upstream                                 = async(upstream0)
      private val sourcePull                               = new LanePuller(upstream)
      private val directPull                               = new DirectPuller(upstream)
      private var selector: AsyncSelector[MapResult[A, B]] = null
      private val occupied                                 = Array.fill(n)(false)
      private var active                                   = 0
      private var sourceDone                               = false
      private var sourceArmed                              = false
      private var stepCause: Throwable                     = null
      private var stepKind                                 = 0
      private var stepValue: MapResult[A, B]               = null
      private val stepProbe                                = new Async.StepFold[MapResult[A, B], Unit] {
        def failure(cause: Throwable): Unit                    = { stepCause = cause; stepKind = 1 }
        def pending(pollable: Pollable[MapResult[A, B]]): Unit = { val _ = pollable; stepKind = 2 }
        def success(value: MapResult[A, B]): Unit              = { stepValue = value; stepKind = 0 }
      }

      private def task(value: A): Async[MapResult[A, B]] =
        try
          f(value).foldCause[MapResult[A, B]](cause => throw StreamError.callbackFailure(cause))(value =>
            MapValue(value)
          )
        catch { case cause: Throwable => Async.fail(StreamError.callbackFailure(cause)) }

      private def sourceTask: Async[MapResult[A, B]] =
        sourcePull(MapSourceEnd, MapSource(_))

      private def install(expected: Long): Async[Unit] = synchronized {
        if (!isCurrent(expected) || (selector ne null)) Async.succeed(())
        else {
          selector = Async.selectorWithCapacity(n + 1, Vector((n, Async.reschedule(() => sourceTask))))
          sourceArmed = true
          Async.succeed(())
        }
      }

      private def installPending(expected: Long, index: Int, effect: Async[MapResult[A, B]]): Async[Unit] =
        synchronized {
          if (!isCurrent(expected) || (selector ne null)) Async.succeed(())
          else {
            val entries =
              if (index == n) Vector((n, effect))
              else if (n > 1) Vector((index, effect), (n, Async.reschedule(() => sourceTask)))
              else Vector((index, effect))
            selector = Async.selectorWithCapacity(n + 1, entries)
            sourceArmed = index == n || n > 1
            if (index < n) { occupied(index) = true; active = 1 }
            Async.succeed(())
          }
        }

      private def probe(effect: Async[MapResult[A, B]]): Int = {
        stepCause = null
        stepValue = null
        Async.foldStep(effect)(stepProbe)
        stepKind
      }

      private def directMapped[B1 >: B](value: A, sentinel: B1, expected: Long): Async[B1] = {
        val mapped = task(value)
        probe(mapped) match {
          case 1 => fail(stepCause, expected)
          case 2 => installPending(expected, 0, mapped).flatMap(_ => readLoop(sentinel, expected))
          case _ =>
            stepValue match {
              case MapValue(result) => Async.succeed(result)
              case _                => fail(new IllegalStateException("mapPar callback produced an invalid result"), expected)
            }
        }
      }

      private def directSource[B1 >: B](result: MapResult[A, B], sentinel: B1, expected: Long): Async[B1] =
        result match {
          case MapSourceEnd =>
            synchronized { sourceDone = true }
            finish(sentinel, expected)
          case MapSource(value) => directMapped(value, sentinel, expected)
          case _                => fail(new IllegalStateException("mapPar source produced an invalid result"), expected)
        }

      private def armSource(expected: Long): Unit = synchronized {
        if (isCurrent(expected) && !sourceDone && !sourceArmed && active < n && (selector ne null)) {
          selector.replace(n, sourceTask)
          sourceArmed = true
        }
      }

      private def freeSlot(): Int = {
        var index = 0
        while (index < n && occupied(index)) index += 1
        index
      }

      private def readLoop[B1 >: B](sentinel: B1, expected: Long): Async[B1] = {
        if ((outType eq JvmType.Int) && synchronized(selector eq null)) {
          val source = sourceTask
          probe(source) match {
            case 1 => return fail(stepCause, expected)
            case 2 => return source.flatMap(result => directSource(result, sentinel, expected))
            case _ => return directSource(stepValue, sentinel, expected)
          }
        }
        val ready = synchronized(if (selector eq null) install(expected) else Async.succeed(()))
        ready.flatMap { _ =>
          val stopped = terminal(sentinel)
          if (stopped.asInstanceOf[AnyRef] ne null) stopped
          else if (synchronized(sourceDone && active == 0)) finish(sentinel, expected)
          else {
            val selected = synchronized(if (pullCurrent(expected)) selector else null)
            if (selected eq null) Async.succeed(sentinel)
            else
              selected.selectClaim(() => beginHandoff(expected)).flatMap { case (index, result) =>
                result match {
                  case MapValue(value) =>
                    val current = synchronized {
                      if (pullCurrent(expected)) { occupied(index) = false; active -= 1; armSource(expected); true }
                      else false
                    }
                    val continue = endHandoff()
                    if (current && continue) Async.succeed(value) else Async.succeed(sentinel)
                  case MapSource(value) =>
                    val slot = synchronized {
                      if (!pullCurrent(expected)) -2
                      else {
                        sourceArmed = false
                        val free = freeSlot()
                        if (free < n) {
                          occupied(free) = true
                          active += 1
                          selector.replace(free, task(value))
                          armSource(expected)
                        }
                        free
                      }
                    }
                    val continue = endHandoff()
                    if (!continue || slot == -2) Async.succeed(sentinel)
                    else if (slot >= n)
                      fail(new IllegalStateException("mapPar source completed without a free slot"), expected)
                    else readLoop(sentinel, expected)
                  case MapSourceEnd =>
                    val current = synchronized {
                      if (pullCurrent(expected)) { sourceArmed = false; sourceDone = true; true }
                      else false
                    }
                    val continue = endHandoff()
                    if (current && continue) readLoop(sentinel, expected) else Async.succeed(sentinel)
                  case _: MapIntValue =>
                    fail(new IllegalStateException("mapPar selected an invalid primitive worker result"), expected)
                  case MapWorkerEnd | MapWorkerClosed =>
                    fail(new IllegalStateException("mapPar selected an invalid worker result"), expected)
                }
              }
          }
        }
      }

      private final class MapParFold[Z](
        expected: Long,
        terminal: ConcurrentAccumulator[B, Z]
      ) extends Pollable[Z]
          with Async.StepFold[Unit, Unit]
          with Async.IntStepFold[Unit]
          with AsyncSelector.ReadyHandler[MapResult[A, B]] {
        private var cause: Throwable      = null
        private var completed             = false
        private var failureSet            = false
        private var kind                  = FoldStep.Pending
        private var pendingFold: Async[Z] = null
        private var mappedInt             = 0
        private var mappedKind            = FoldStep.Pending
        private var trusted               = false

        def apply(index: Int, result: MapResult[A, B]): Boolean =
          if (!synchronized(pullCurrent(expected))) {
            endHandoff()
            false
          } else {
            result match {
              case MapValue(value)  => handleValue(index, value)
              case int: MapIntValue => handleValue(index, int.value.asInstanceOf[B])
              case MapSource(value) =>
                val slot = synchronized {
                  sourceArmed = false
                  val free = freeSlot()
                  if (free < n) {
                    occupied(free) = true
                    active += 1
                    selector.replace(free, task(value))
                    armSource(expected)
                  }
                  free
                }
                if (slot >= n)
                  setFailure(new IllegalStateException("mapPar source completed without a free slot"), false)
              case MapSourceEnd                   => synchronized { sourceArmed = false; sourceDone = true }
              case MapWorkerEnd | MapWorkerClosed =>
                setFailure(new IllegalStateException("mapPar selected an invalid worker result"), false)
            }
            if (!failureSet && (pendingFold.asInstanceOf[AnyRef] eq null) && synchronized(sourceDone && active == 0))
              completed = true
            val continue = endHandoff()
            continue && !completed && !failureSet && (pendingFold.asInstanceOf[AnyRef] eq null)
          }

        def failure(failure: Throwable): Unit       = setFailure(failure, false)
        def failureInt(failure: Throwable): Unit    = { mappedKind = FoldStep.Failure; setFailure(failure, false) }
        def pending(pollable: Pollable[Unit]): Unit = {
          val _ = pollable
          kind = FoldStep.Pending
        }
        def success(value: Unit): Unit = {
          val _ = value
          kind = FoldStep.Success
        }
        override def trustedFailure(failure: Throwable): Unit    = setFailure(failure, true)
        def pendingInt(pollable: Pollable[Int]): Unit            = { val _ = pollable; mappedKind = FoldStep.Pending }
        def successInt(value: Int): Unit                         = { mappedInt = value; mappedKind = FoldStep.Success }
        override def trustedFailureInt(failure: Throwable): Unit = {
          mappedKind = FoldStep.Failure
          setFailure(failure, true)
        }

        def poll(onComplete: Runnable): Async[Z] = {
          if (failureSet) return failed()
          if (pendingFold.asInstanceOf[AnyRef] ne null) {
            val pending = pendingFold
            pendingFold = null
            return pending.flatMap { next => terminal.commit(next); this }
          }
          if (completed) return finish(terminal.result, expected)
          if (synchronized(selector eq null)) return pollDirect(onComplete)
          pollConcurrent()
        }

        private def pollConcurrent(): Async[Z] = {
          var budget = 4
          while (budget > 0) {
            if (synchronized(sourceDone && active == 0)) return finish(terminal.result, expected)
            val selected = synchronized(if (pullCurrent(expected)) selector else null)
            if (selected eq null) return Async.succeed(terminal.result)
            resetProbe()
            val batch = selected.selectClaimReadyRepeat(() => beginHandoff(expected), n, this)
            Async.foldStep(batch)(this)
            if (kind == FoldStep.Failure) return failed()
            if (kind == FoldStep.Pending) return batch.flatMap(_ => this)
            if (failureSet) return failed()
            if (pendingFold.asInstanceOf[AnyRef] ne null) {
              val pending = pendingFold
              pendingFold = null
              return pending.flatMap { next => terminal.commit(next); this }
            }
            if (completed) return finish(terminal.result, expected)
            budget -= 1
          }
          yieldNow()
        }

        private def pollDirect(onComplete: Runnable): Async[Z] = {
          if (upstream.jvmType eq JvmType.Int) return pollDirectInt(onComplete)
          var budget = 1024
          while (budget > 0) {
            val source = directPull.read()
            Async.stepKind(source) match {
              case FoldStep.Failure => return directFailure(source)
              case FoldStep.Pending => return source.flatMap(_ => continueDirectSource(null))
              case _                =>
                continueDirectSource(onComplete) match {
                  case next if next.asInstanceOf[AnyRef] eq this =>
                  case result                                    => return result
                }
            }
            budget -= 1
          }
          yieldNow()
        }

        private def pollDirectInt(onComplete: Runnable): Async[Z] = {
          var budget = 1024
          while (budget > 0) {
            val source = upstream.readIntPhysical(Long.MinValue)
            Async.stepKind(source) match {
              case FoldStep.Failure => return directFailure(source)
              case FoldStep.Pending => return source.flatMap(value => continueDirectInt(value, null))
              case _                =>
                continueDirectInt(Async.stepLong(source), onComplete) match {
                  case next if next.asInstanceOf[AnyRef] eq this =>
                  case result                                    => return result
                }
            }
            budget -= 1
          }
          yieldNow()
        }

        private def continueDirectInt(value: Long, onComplete: Runnable): Async[Z] =
          if (value == Long.MinValue) {
            synchronized { sourceDone = true }
            finish(terminal.result, expected)
          } else if (outType eq JvmType.Int) {
            val mapped =
              try StreamError.callbackAsync(f.asInstanceOf[Int => Async[Int]](value.toInt))
              catch { case failure: Throwable => return fail(StreamError.callbackFailure(failure), expected) }
            mappedKind = FoldStep.Pending
            Async.foldIntStep(mapped)(this)
            mappedKind match {
              case FoldStep.Failure => directFailure(mapped)
              case FoldStep.Pending =>
                installPending(expected, 0, mapped.map(result => new MapIntValue(result).asInstanceOf[MapResult[A, B]]))
                if (onComplete eq null) this else pollConcurrent()
              case _ => continueDirectIntValue(mappedInt)
            }
          } else {
            val mapped =
              try StreamError.callbackAsync(f(value.toInt.asInstanceOf[A]))
              catch { case failure: Throwable => return fail(StreamError.callbackFailure(failure), expected) }
            Async.stepKind(mapped) match {
              case FoldStep.Failure => directFailure(mapped)
              case FoldStep.Pending =>
                installPending(expected, 0, mapped.map(result => MapValue(result): MapResult[A, B]))
                if (onComplete eq null) this else pollConcurrent()
              case _ => continueDirectValue(Async.stepValue(mapped))
            }
          }

        private def continueDirectSource(onComplete: Runnable): Async[Z] =
          if (directPull.ended) {
            synchronized { sourceDone = true }
            finish(terminal.result, expected)
          } else {
            continueDirectMap(directPull.invoke(f), onComplete)
          }

        private def continueDirectMap(mapped0: => Async[B], onComplete: Runnable): Async[Z] = {
          val mapped =
            try StreamError.callbackAsync(mapped0)
            catch { case failure: Throwable => return fail(StreamError.callbackFailure(failure), expected) }
          Async.stepKind(mapped) match {
            case FoldStep.Failure => directFailure(mapped)
            case FoldStep.Pending =>
              installPending(expected, 0, mapped.map(result => MapValue(result): MapResult[A, B]))
              if (onComplete eq null) this else pollConcurrent()
            case _ => continueDirectValue(Async.stepValue(mapped))
          }
        }

        private def continueDirectValue(value: B): Async[Z] = {
          val reduced = terminal.invoke(value)
          terminal.observe(reduced) match {
            case FoldStep.Success => this
            case FoldStep.Pending => reduced.flatMap { next => terminal.commit(next); this }
            case _                => failedAccumulator()
          }
        }

        private def continueDirectIntValue(value: Int): Async[Z] =
          terminal.consumeInt(value) match {
            case FoldStep.Success => this
            case FoldStep.Pending => terminal.pending.flatMap { next => terminal.commit(next); this }
            case _                => failedAccumulator()
          }

        private def directFailure[X](effect: Async[X]): Async[Z] =
          if (Async.stepTrusted(effect)) Async.failTrusted(Async.stepCause(effect))
          else fail(Async.stepCause(effect), expected)

        private def failedAccumulator(): Async[Z] =
          if (terminal.trusted) Async.failTrusted(terminal.failure) else fail(terminal.failure, expected)

        private def handleValue(index: Int, value: B): Unit = {
          val current = synchronized {
            if (pullCurrent(expected) && index >= 0 && index < n && occupied(index)) {
              occupied(index) = false
              active -= 1
              armSource(expected)
              true
            } else false
          }
          if (!current) setFailure(new IllegalStateException("mapPar selected an uninstalled worker"), false)
          else {
            val reduced = terminal.invoke(value)
            terminal.observe(reduced) match {
              case FoldStep.Pending => pendingFold = reduced
              case FoldStep.Failure => setFailure(terminal.failure, terminal.trusted)
              case _                =>
            }
          }
        }

        private def resetProbe(): Unit = {
          cause = null
          failureSet = false
          kind = FoldStep.Pending
          trusted = false
        }

        private def setFailure(failure: Throwable, isTrusted: Boolean): Unit = {
          cause = failure
          failureSet = true
          kind = FoldStep.Failure
          trusted = isTrusted
        }

        private def failed(): Async[Z] =
          if (trusted) Async.failTrusted(cause) else fail(cause, expected)

        private def yieldNow(): Async[Z] = {
          runAfterYieldHook()
          // Publish the next fold through an owned scheduler boundary. A
          // self-wake can race its same-identity return and let a later read
          // overtake an already-armed selector winner.
          Async.reschedule(() => this)
        }
      }

      protected def concurrentFold[Z](
        expected: Long,
        zero: Z,
        accumulatorType: JvmType,
        fold: (Z, B) => Async[Z]
      ): Async[Z] = {
        val _ = upstream.jvmType
        new MapParFold(expected, ConcurrentAccumulator.make(zero, accumulatorType, fold))
      }

      protected def readRaw[B1 >: B](sentinel: B1, expected: Long): Async[B1] = readLoop(sentinel, expected)

      protected def closeOwned(): Async[Unit] = {
        val selected = synchronized(selector)
        val first    = if (selected eq null) Async.succeed(()) else selected.shutdown
        if (closeUpstream) join(first, upstream.close()) else first
      }
    }

  def merge[A](outer0: Reader[?], n: Int, bufferSize: Int, elemType: JvmType): Reader.AsyncReader[A] =
    new ConcurrentReader[A](elemType) {
      private val outer                                                 = async(outer0.asInstanceOf[Reader[Stream[Any, A]]])
      private val outerPull                                             = new LanePuller(outer)
      private var selector: AsyncSelector[MapResult[Stream[Any, A], A]] = null
      private var inners                                                = Vector.fill[OwnedReader[A]](n)(null)
      private val occupied                                              = Array.fill(n)(false)
      private var active                                                = 0
      private var outerDone                                             = false
      private var sourceArmed                                           = false

      private final class MergeFold[Z](
        expected: Long,
        terminal: ConcurrentAccumulator[A, Z]
      ) extends Pollable[Z]
          with Async.StepFold[Unit, Unit]
          with AsyncSelector.ReadyHandler[MapResult[Stream[Any, A], A]] {
        private var cause: Throwable      = null
        private var completed             = false
        private var failureSet            = false
        private var kind                  = FoldStep.Pending
        private var pendingFold: Async[Z] = null
        private var remaining             = 0
        private var trusted               = false

        def apply(index: Int, result: MapResult[Stream[Any, A], A]): Boolean =
          if (!synchronized(pullCurrent(expected))) {
            endHandoff()
            false
          } else {
            result match {
              case MapValue(value)   => handleValue(index, value)
              case int: MapIntValue  => handleValue(index, int.value.asInstanceOf[A])
              case MapWorkerEnd      => synchronized(closeSlot(index))
              case MapWorkerClosed   => synchronized { releaseSlot(index); armSource(expected) }
              case MapSource(stream) =>
                val slot = synchronized {
                  sourceArmed = false
                  val free = freeSlot()
                  if (free < n) {
                    occupied(free) = true
                    active += 1
                    selector.replace(free, start(stream, free, expected))
                    armSource(expected)
                  }
                  free
                }
                if (slot >= n)
                  setFailure(new IllegalStateException("merge source completed without a free slot"), false)
              case MapSourceEnd => synchronized { sourceArmed = false; outerDone = true }
            }
            if (!failureSet && (pendingFold.asInstanceOf[AnyRef] eq null) && synchronized(outerDone && active == 0))
              completed = true
            val continue = endHandoff()
            continue && remaining > 0 && !completed && !failureSet && (pendingFold.asInstanceOf[AnyRef] eq null)
          }

        def failure(failure: Throwable): Unit       = setFailure(failure, false)
        def pending(pollable: Pollable[Unit]): Unit = {
          val _ = pollable
          kind = FoldStep.Pending
        }
        def success(value: Unit): Unit = {
          val _ = value
          kind = FoldStep.Success
        }
        override def trustedFailure(failure: Throwable): Unit = setFailure(failure, true)

        def poll(onComplete: Runnable): Async[Z] = {
          if (failureSet) return failed()
          if (pendingFold.asInstanceOf[AnyRef] ne null) {
            val pending = pendingFold
            pendingFold = null
            return pending.flatMap { next => terminal.commit(next); this }
          }
          if (completed) return finish(terminal.result, expected)
          val ready = synchronized(if (selector eq null) install(expected) else Async.succeed(()))
          if (Async.stepKind(ready) != FoldStep.Success) return ready.flatMap(_ => this)
          if (synchronized(outerDone && active == 0)) return finish(terminal.result, expected)
          val selected = synchronized(if (pullCurrent(expected)) selector else null)
          if (selected eq null) return Async.succeed(terminal.result)
          resetProbe()
          remaining = 256
          val batch = selected.selectClaimReadyRepeat(() => beginHandoff(expected), 256, this)
          Async.foldStep(batch)(this)
          if (kind == FoldStep.Failure) failed()
          else if (kind == FoldStep.Pending) batch.flatMap(_ => this)
          else if (failureSet) failed()
          else if (pendingFold.asInstanceOf[AnyRef] ne null) {
            val pending = pendingFold
            pendingFold = null
            pending.flatMap { next => terminal.commit(next); this }
          } else if (completed) finish(terminal.result, expected)
          else yieldNow()
        }

        private def handleValue(index: Int, value: A): Unit = {
          val inner = synchronized {
            val current = inners(index)
            if (current ne null) selector.replaceKnown(index, current.pullResult(MapWorkerEnd))
            current
          }
          if (inner eq null) setFailure(new IllegalStateException("merge selected an uninstalled inner"), false)
          else {
            consume(value)
            remaining -= 1
          }
        }

        private def consume(value: A): Unit = {
          val reduced = terminal.invoke(value)
          terminal.observe(reduced) match {
            case FoldStep.Pending => pendingFold = reduced
            case FoldStep.Failure => setFailure(terminal.failure, terminal.trusted)
            case _                =>
          }
        }

        private def resetProbe(): Unit = {
          cause = null
          failureSet = false
          kind = FoldStep.Pending
          trusted = false
        }

        private def setFailure(failure: Throwable, isTrusted: Boolean): Unit = {
          cause = failure
          failureSet = true
          kind = FoldStep.Failure
          trusted = isTrusted
        }

        private def failed(): Async[Z] =
          if (trusted) Async.failTrusted(cause) else fail(cause, expected)

        private def yieldNow(): Async[Z] = {
          runAfterYieldHook()
          // Preserve every ready selector winner across the fairness handoff;
          // see the equivalent mapPar boundary above.
          Async.reschedule(() => this)
        }
      }

      protected def concurrentFold[Z](
        expected: Long,
        zero: Z,
        accumulatorType: JvmType,
        fold: (Z, A) => Async[Z]
      ): Async[Z] = new MergeFold(expected, ConcurrentAccumulator.make(zero, accumulatorType, fold))

      private def start(stream: Stream[Any, A], index: Int, expected: Long): Async[MapResult[Stream[Any, A], A]] =
        (stream match {
          case unwrapped: Stream.Unwrapped[Any @unchecked, A @unchecked] =>
            unwrapped.evaluateDirect().flatMap(stream => acquireAndInstall(stream.acquireReader, index, expected))
          case _: Stream.Mapped[_, _, _] | _: Stream.AsyncMapped[_, _, _] =>
            acquireAndInstall(stream.acquireReader, index, expected)
          case _ =>
            Async.bracketSync[AcquiredReader[A], MapResult[Stream[Any, A], A]](
              () => new AcquiredReader(new OwnedReader(async(stream.compile(0, bufferSize)))),
              acquired => installAcquired(acquired, index, expected),
              releaseAcquired
            )
        })
          .catchAll(cause =>
            if (synchronized(!isCurrent(expected))) Async.succeed(MapWorkerClosed: MapResult[Stream[Any, A], A])
            else Async.fail(cause)
          )

      private def acquireAndInstall(
        acquisition: Async[Reader[A]],
        index: Int,
        expected: Long
      ): Async[MapResult[Stream[Any, A], A]] = {
        val acquired = new AcquiredReader[A](null)
        Async.bracketAsync[Reader[A], MapResult[Stream[Any, A], A]](
          () => acquisition,
          reader => {
            acquired.owned = new OwnedReader(async(reader))
            runAfterAcquisitionHook()
            installAcquired(acquired, index, expected)
          },
          reader => {
            val owned = acquired.owned
            if (acquired.transferred) Async.succeed(())
            else if (owned ne null) owned.close.close()
            else
              try
                reader match {
                  case sync: Reader.SyncReader[A @unchecked]   => Async.succeed(sync.close())
                  case async: Reader.AsyncReader[A @unchecked] => async.close()
                }
              catch { case cause: Throwable => Async.fail(cause) }
          }
        )
      }

      private def installAcquired(
        acquired: AcquiredReader[A],
        index: Int,
        expected: Long
      ): Async[MapResult[Stream[Any, A], A]] = {
        val accepted = synchronized {
          if (isCurrent(expected) && occupied(index) && (inners(index) eq null)) {
            inners = inners.updated(index, acquired.owned)
            acquired.transferred = true
            true
          } else false
        }
        if (accepted) acquired.owned.pullResult(MapWorkerEnd)
        else Async.fail(new IllegalStateException("reader was closed during materialization"))
      }

      private def releaseAcquired(acquired: AcquiredReader[A]): Async[Unit] =
        if (acquired.transferred) Async.succeed(()) else acquired.owned.close.close()

      private def sourceTask: Async[MapResult[Stream[Any, A], A]] =
        outerPull(MapSourceEnd, MapSource(_))

      private def install(expected: Long): Async[Unit] = synchronized {
        if (!isCurrent(expected) || (selector ne null)) Async.succeed(())
        else {
          selector = Async.selectorWithCapacity(n + 1, Vector((n, Async.reschedule(() => sourceTask))))
          sourceArmed = true
          Async.succeed(())
        }
      }

      private def armSource(expected: Long): Unit = synchronized {
        if (isCurrent(expected) && !outerDone && !sourceArmed && active < n && (selector ne null)) {
          selector.replace(n, sourceTask)
          sourceArmed = true
        }
      }

      private def freeSlot(): Int = {
        var index = 0
        while (index < n && occupied(index)) index += 1
        index
      }

      private def closeSlot(index: Int): Unit = synchronized {
        val owned  = inners(index)
        val effect =
          if (owned eq null) Async.succeed(MapWorkerClosed: MapResult[Stream[Any, A], A])
          else owned.close.close().map(_ => MapWorkerClosed: MapResult[Stream[Any, A], A])
        selector.replace(index, effect)
      }

      private def releaseSlot(index: Int): Unit = synchronized {
        inners = inners.updated(index, null)
        occupied(index) = false
        active -= 1
      }

      private def readLoop[A1 >: A](sentinel: A1, expected: Long): Async[A1] = {
        val ready = synchronized(if (selector eq null) install(expected) else Async.succeed(()))
        ready.flatMap { _ =>
          if (synchronized(outerDone && active == 0)) finish(sentinel, expected)
          else {
            val selected = synchronized(if (pullCurrent(expected)) selector else null)
            if (selected eq null) Async.succeed(sentinel)
            else
              selected.selectClaimReady(() => beginHandoff(expected)).flatMap { case (index, result) =>
                result match {
                  case MapValue(value) =>
                    val state = synchronized {
                      if (!pullCurrent(expected)) (false, null)
                      else {
                        val inner = inners(index)
                        if (inner ne null) selector.replace(index, inner.pull(MapWorkerEnd, MapValue(_)))
                        (true, inner)
                      }
                    }
                    val owned    = state._2
                    val continue = endHandoff()
                    if (!state._1 || !continue) Async.succeed(sentinel)
                    else if (owned eq null)
                      fail(new IllegalStateException("merge selected an uninstalled inner"), expected)
                    else Async.succeed(value)
                  case int: MapIntValue =>
                    val state = synchronized {
                      if (!pullCurrent(expected)) (false, null)
                      else {
                        val inner = inners(index)
                        if (inner ne null) selector.replace(index, inner.pullResult(MapWorkerEnd))
                        (true, inner)
                      }
                    }
                    val owned    = state._2
                    val continue = endHandoff()
                    if (!state._1 || !continue) Async.succeed(sentinel)
                    else if (owned eq null)
                      fail(new IllegalStateException("merge selected an uninstalled inner"), expected)
                    else Async.succeed(int.value.asInstanceOf[A1])
                  case MapWorkerEnd =>
                    synchronized(if (pullCurrent(expected)) closeSlot(index))
                    if (endHandoff()) readLoop(sentinel, expected) else Async.succeed(sentinel)
                  case MapWorkerClosed =>
                    synchronized(if (pullCurrent(expected)) { releaseSlot(index); armSource(expected) })
                    if (endHandoff()) readLoop(sentinel, expected) else Async.succeed(sentinel)
                  case MapSource(stream) =>
                    val slot = synchronized {
                      if (!pullCurrent(expected)) -2
                      else {
                        sourceArmed = false
                        val free = freeSlot()
                        if (free < n) {
                          occupied(free) = true
                          active += 1
                          selector.replace(free, start(stream, free, expected))
                          armSource(expected)
                        }
                        free
                      }
                    }
                    val continue = endHandoff()
                    if (!continue || slot == -2) Async.succeed(sentinel)
                    else if (slot >= n)
                      fail(new IllegalStateException("merge source completed without a free slot"), expected)
                    else readLoop(sentinel, expected)
                  case MapSourceEnd =>
                    val current = synchronized {
                      if (pullCurrent(expected)) { sourceArmed = false; outerDone = true; true }
                      else false
                    }
                    val continue = endHandoff()
                    if (current && continue) readLoop(sentinel, expected) else Async.succeed(sentinel)
                }
              }
          }
        }
      }

      protected def readRaw[A1 >: A](sentinel: A1, expected: Long): Async[A1] = readLoop(sentinel, expected)

      protected def closeOwned(): Async[Unit] = {
        val (selected, owned)   = synchronized((selector, inners))
        var result: Async[Unit] =
          if (selected eq null) Async.succeed(()) else selected.shutdown
        owned.foreach(inner => if (inner ne null) result = join(result, inner.close.close()))
        join(result, outer.close())
      }
    }

  private[streams] def invalidateBeforeCommitForTest(reader: Reader.AsyncReader[_]): Unit =
    reader.asInstanceOf[ConcurrentReader[_]].invalidateBeforeCommitForTest()

  private[streams] def invalidateBeforeFinishForTest(reader: Reader.AsyncReader[_]): Unit =
    reader.asInstanceOf[ConcurrentReader[_]].invalidateBeforeFinishForTest()

  private[streams] def afterAcquisitionForTest(reader: Reader.AsyncReader[_])(hook: () => Unit): Unit =
    reader.asInstanceOf[ConcurrentReader[_]].afterAcquisitionForTest(hook)

  private[streams] def afterYieldForTest(reader: Reader.AsyncReader[_])(hook: () => Unit): Unit =
    reader.asInstanceOf[ConcurrentReader[_]].afterYieldForTest(hook)

  private[streams] def afterBeginHandoffForTest(reader: Reader.AsyncReader[_])(hook: () => Unit): Unit =
    reader.asInstanceOf[ConcurrentReader[_]].afterBeginHandoffForTest(hook)

  private[streams] def beforeRegistrationForTest(reader: Reader.AsyncReader[_])(hook: () => Unit): Unit =
    reader.asInstanceOf[ConcurrentReader[_]].beforeRegistrationForTest(hook)

  private[streams] def handoffCancellationClaimedForTest(reader: Reader.AsyncReader[_]): Boolean =
    reader.asInstanceOf[ConcurrentReader[_]].handoffCancellationClaimedForTest()

  private def join(left: Async[Unit], right: => Async[Unit]): Async[Unit] =
    left.either.flatMap {
      case Right(_)      => right
      case Left(primary) =>
        right.either.flatMap {
          case Right(_)        => failure(primary)
          case Left(secondary) =>
            failure(if (primary eq null) null else StreamError.attachCleanupReplay(primary, secondary))
        }
    }
}

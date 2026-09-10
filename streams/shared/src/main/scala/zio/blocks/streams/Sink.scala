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

import zio.blocks.async.{Async, _}
import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.streams.internal.{AsyncConcurrentReaders, AsyncInterpreter, EndOfStream, SyncInterpreter, StreamError}
import zio.blocks.streams.io.Reader

/**
 * A consumer of elements of type `A` that may fail with `E` and, on completion,
 * produces a result `Z`. Sinks are consumed by passing them to
 * [[Stream.runAsync]] (or a platform-specific terminal): the stream compiles
 * into a [[io.Reader]] and drains it once to produce the final result.
 *
 * @tparam E
 *   Error type.
 * @tparam A
 *   Input element type (contravariant).
 * @tparam Z
 *   Result type.
 */
abstract class Sink[+E, -A, +Z] {

  /**
   * Applies `g` to each element as this sink requests it, then feeds the
   * transformed element to this sink. Consumption and short-circuiting are
   * therefore unchanged; `g` is not evaluated for unread input. Exceptions
   * thrown by `g` are defects, not values in the sink's typed error channel.
   */
  def contramap[A0 <: A, A2](g: A2 => A0)(implicit jtA0: JvmType.Infer[A0]): Sink[E, A2, Z] =
    new Sink.Contramapped(this, g, jtA0.jvmType)

  /**
   * Sequentially applies the asynchronous function `g` to each element as this
   * sink requests it. Consumption and short-circuiting are unchanged, so `g` is
   * not started for unread input. Failure or cancellation of `g` fails or
   * cancels the run as a defect and stops pulling input.
   */
  def contramapAsync[A0 <: A, A2](g: A2 => Async[A0])(implicit jtA0: JvmType.Infer[A0]): Sink[E, A2, Z] =
    new Sink.ContramappedAsync(this, g, jtA0.jvmType)

  /**
   * Transforms this sink's successful result after it completes. This does not
   * change input consumption or typed errors. Exceptions thrown by `f` are
   * defects.
   */
  def map[Z2](f: Z => Z2): Sink[E, A, Z2] = new Sink.Mapped(this, f)

  /**
   * Asynchronously transforms this sink's successful result after it completes.
   * The effect is not started when the sink fails, and it does not change input
   * consumption. Effect failure or cancellation fails or cancels the run as a
   * defect.
   */
  def mapAsync[Z2](f: Z => Async[Z2]): Sink[E, A, Z2] = new Sink.MappedAsync(this, f)

  /**
   * Transforms a typed sink error with `f`, without changing consumption or
   * successful results. Upstream stream errors and defects are not transformed.
   * For an infallible sink (`E = Nothing`), the evidence avoids allocating a
   * wrapper and `f` can never be evaluated.
   */
  def mapError[E2](f: E => E2)(implicit isNothing: Sink.IsNothing[E]): Sink[E2, A, Z] =
    if (isNothing.value) this.asInstanceOf[Sink[E2, A, Z]]
    else Sink.mkErrorMapped(this, f)

  /**
   * Asynchronously transforms a typed sink error with `f`. Upstream stream
   * errors, successful results, and defects are unchanged. For `E = Nothing`,
   * `f` can never be evaluated; otherwise failure or cancellation of `f` fails
   * or cancels the run as a defect.
   */
  def mapErrorAsync[E2](f: E => Async[E2])(implicit isNothing: Sink.IsNothing[E]): Sink[E2, A, Z] =
    if (isNothing.value) this.asInstanceOf[Sink[E2, A, Z]]
    else new Sink.ErrorMappedAsync(this, f)

  /**
   * Drains the reader, consuming all elements and producing a result Z.
   */
  private[streams] def drain(reader: Reader.SyncReader[_]): Z

  /**
   * Drains an asynchronous reader without converting it to a blocking reader.
   */
  private[streams] def drain(reader: Reader.AsyncReader[_]): Async[Z]

  /** Drains a synchronous reader through this sink's asynchronous contract. */
  private[streams] def drainAsync(reader: Reader.SyncReader[_]): Async[Z] =
    drain(reader.toAsync)
}

/**
 * Companion object for [[Sink]]. Provides factory constructors for common
 * consumers: `fail`, `create`, `drain`, `count`, `collectAll`, `foldLeft`,
 * `foreach`, `head`, `last`, `take`, `exists`, `forall`, `find`,
 * `fromOutputStream`, `fromJavaWriter`, `sumInt`, `sumLong`, `sumFloat`, and
 * `sumDouble`.
 */
object Sink extends SinkCompanionPlatformSpecific {

  private final class ReaderFailure(val error: StreamError) extends Exception(null, null, true, false)

  /**
   * Consumes the entire input and returns its elements, in order, in a `Chunk`.
   * Empty input yields an empty chunk. Primitive inputs use specialized chunk
   * builders. Collection stops if the upstream stream fails or the run is
   * cancelled.
   */
  def collectAll[A]: Sink[Nothing, A, Chunk[A]] =
    new Sink[Nothing, A, Chunk[A]] {
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Chunk[A]] = reader.jvmType match {
        case JvmType.Boolean =>
          foldAsyncReader[Boolean, ChunkBuilder.Boolean](reader, new ChunkBuilder.Boolean()) { (b, a) =>
            b.addOne(a); Async.succeed(b)
          }.map(_.result().asInstanceOf[Chunk[A]])
        case JvmType.Int =>
          collectNativeAsyncInt(reader.asInstanceOf[Reader.AsyncReader[Int]]).asInstanceOf[Async[Chunk[A]]]
        case JvmType.Long =>
          foldAsyncReader[Long, ChunkBuilder.Long](reader, new ChunkBuilder.Long()) { (b, a) =>
            b.addOne(a); Async.succeed(b)
          }
            .map(_.result().asInstanceOf[Chunk[A]])
        case JvmType.Float =>
          foldAsyncReader[Float, ChunkBuilder.Float](reader, new ChunkBuilder.Float()) { (b, a) =>
            b.addOne(a); Async.succeed(b)
          }
            .map(_.result().asInstanceOf[Chunk[A]])
        case JvmType.Double =>
          foldAsyncReader[Double, ChunkBuilder.Double](reader, new ChunkBuilder.Double()) { (b, a) =>
            b.addOne(a); Async.succeed(b)
          }
            .map(_.result().asInstanceOf[Chunk[A]])
        case JvmType.Byte =>
          foldAsyncReader[Byte, ChunkBuilder.Byte](reader, new ChunkBuilder.Byte()) { (b, a) =>
            b.addOne(a); Async.succeed(b)
          }
            .map(_.result().asInstanceOf[Chunk[A]])
        case JvmType.Char =>
          foldAsyncReader[Char, ChunkBuilder.Char](reader, new ChunkBuilder.Char()) { (b, a) =>
            b.addOne(a); Async.succeed(b)
          }.map(_.result().asInstanceOf[Chunk[A]])
        case JvmType.Short =>
          foldAsyncReader[Short, ChunkBuilder.Short](reader, new ChunkBuilder.Short()) { (b, a) =>
            b.addOne(a); Async.succeed(b)
          }.map(_.result().asInstanceOf[Chunk[A]])
        case _ =>
          foldAsyncReader[A, ChunkBuilder[A]](reader, ChunkBuilder.make[A](16)) { (b, a) => b += a; Async.succeed(b) }
            .map(_.result())
      }
      private[streams] def drain(reader: Reader.SyncReader[_]): Chunk[A] =
        reader.jvmType match {
          case JvmType.Int     => collectAllInt(reader).asInstanceOf[Chunk[A]]
          case JvmType.Long    => collectAllLong(reader).asInstanceOf[Chunk[A]]
          case JvmType.Float   => collectAllFloat(reader).asInstanceOf[Chunk[A]]
          case JvmType.Double  => collectAllDouble(reader).asInstanceOf[Chunk[A]]
          case JvmType.Byte    => collectAllByte(reader).asInstanceOf[Chunk[A]]
          case JvmType.Boolean => collectAllBoolean(reader).asInstanceOf[Chunk[A]]
          case JvmType.Char    => collectAllChar(reader).asInstanceOf[Chunk[A]]
          case JvmType.Short   => collectAllShort(reader).asInstanceOf[Chunk[A]]
          case _               =>
            val b = ChunkBuilder.make[A](16)
            var v = reader.read(EndOfStream)
            while (v.asInstanceOf[AnyRef] ne EndOfStream) { b += v.asInstanceOf[A]; v = reader.read(EndOfStream) }
            b.result()
        }
    }

  /**
   * Consumes the entire input and returns its element count as a `Long` (`0L`
   * for empty input). The count wraps on `Long` overflow. Primitive reader
   * lanes are consumed without boxing where supported.
   */
  val count: Sink[Nothing, Any, Long] =
    new Sink[Nothing, Any, Long] {
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Long] =
        foldAsyncReader[Any, Long](reader, 0L)((n, _) => Async.succeed(n + 1L))
      private[streams] def drain(reader: Reader.SyncReader[_]): Long =
        foldSyncReader[Any, Long](reader, 0L)((n, _) => n + 1L)
    }

  /**
   * Creates a sink whose callback receives the run's asynchronous reader and
   * decides how much input to consume and what result to produce. Returning
   * early leaves the remaining input unread; the enclosing stream run then
   * closes its reader, so it is not exposed as reusable leftover input.
   *
   * The callback and reader are valid only for the run and must not be
   * retained. Reader failures preserve the stream's typed error; callback
   * exceptions or failed effects are defects. Cancellation is propagated to
   * pending reader operations and closes the run's resources. On the JVM, a
   * synchronous terminal blocks on this asynchronous implementation.
   */
  def createAsync[E, A, Z](f: Reader.AsyncReader[A] => Async[Z]): Sink[E, A, Z] =
    new Sink[E, A, Z] {
      private[streams] def drain(reader: Reader.SyncReader[_]): Z = {
        val asyncReader = reader.toAsync.asInstanceOf[Reader.AsyncReader[A]]
        blockOnJvm(readerCallbackAsync(asyncReader)(f))
      }
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Z] = {
        val asyncReader = reader.asInstanceOf[Reader.AsyncReader[A]]
        readerCallbackAsync(asyncReader)(f)
      }
    }

  /**
   * Creates a sink with independent native synchronous and asynchronous
   * callbacks. The terminal selects exactly one callback; both must implement
   * the same consumption and result semantics.
   *
   * Each callback controls how much input it consumes. Input left unread is
   * closed with the enclosing run rather than returned as leftovers, and the
   * reader must not escape the callback. Reader failures remain typed stream
   * errors; callback exceptions and failed asynchronous effects are defects.
   * Cancellation of the asynchronous callback propagates through its reader.
   */
  def createBoth[E, A, Z](
    sync: Reader.SyncReader[A] => Z,
    async: Reader.AsyncReader[A] => Async[Z]
  ): Sink[E, A, Z] =
    new Sink[E, A, Z] {
      private[streams] def drain(reader: Reader.SyncReader[_]): Z =
        readerCallbackSync(reader.asInstanceOf[Reader.SyncReader[A]])(sync)
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Z] = {
        val asyncReader = reader.asInstanceOf[Reader.AsyncReader[A]]
        readerCallbackAsync(asyncReader)(async)
      }
    }

  private[streams] final class AsyncPuller[A](reader: Reader.AsyncReader[_]) {
    private val longs   = if (reader.jvmType eq JvmType.Long) new Array[Long](1) else null
    private val floats  = if (reader.jvmType eq JvmType.Float) new Array[Float](1) else null
    private val doubles = if (reader.jvmType eq JvmType.Double) new Array[Double](1) else null
    private val bytes   = if (reader.jvmType eq JvmType.Byte) new Array[Byte](1) else null

    private val pullOne: () => Async[Option[A]] = reader.jvmType match {
      case JvmType.Boolean =>
        () => reader.readBooleanPhysical(-1).map(v => if (v < 0) None else Some((v != 0).asInstanceOf[A]))
      case JvmType.Int =>
        () =>
          reader
            .readIntPhysical(Long.MinValue)
            .map(v => if (v == Long.MinValue) None else Some(v.toInt.asInstanceOf[A]))
      case JvmType.Long =>
        () => reader.readLongsPhysical(longs, 0, 1).map(n => if (n < 0) None else Some(longs(0).asInstanceOf[A]))
      case JvmType.Float =>
        () => reader.readFloatsPhysical(floats, 0, 1).map(n => if (n < 0) None else Some(floats(0).asInstanceOf[A]))
      case JvmType.Double =>
        () => reader.readDoublesPhysical(doubles, 0, 1).map(n => if (n < 0) None else Some(doubles(0).asInstanceOf[A]))
      case JvmType.Byte =>
        () => reader.readBytesPhysical(bytes, 0, 1).map(n => if (n < 0) None else Some(bytes(0).asInstanceOf[A]))
      case JvmType.Char =>
        () => reader.readCharPhysical(-1).map(v => if (v < 0) None else Some(v.toChar.asInstanceOf[A]))
      case JvmType.Short =>
        () =>
          reader
            .readShortPhysical(Int.MinValue)
            .map(v => if (v == Int.MinValue) None else Some(v.toShort.asInstanceOf[A]))
      case _ =>
        () =>
          reader
            .read[Any](EndOfStream)
            .map(v => if (v.asInstanceOf[AnyRef] eq EndOfStream) None else Some(v.asInstanceOf[A]))
    }

    def pull(): Async[Option[A]] = pullOne()
  }

  /**
   * Consumes and discards the entire input, returning `Unit`. It stops only at
   * end-of-stream, upstream failure, or cancellation, and uses specialized
   * primitive reader lanes where supported.
   */
  val drain: Sink[Nothing, Any, Unit] =
    new Sink[Nothing, Any, Unit] {
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Unit] =
        if (reader.jvmType eq JvmType.Int) drainNativeAsyncInt(reader.asInstanceOf[Reader.AsyncReader[Int]])
        else foldAsyncReader[Any, Unit](reader, ())((_, _) => Async.succeed(()))
      private[streams] def drain(reader: Reader.SyncReader[_]): Unit = reader match {
        case range: Reader.FromRange          => range.discardRemaining()
        case vector: Reader.FromVector[_]     => vector.discardRemaining()
        case iterable: Reader.FromIterable[_] => iterable.discardRemaining()
        case _                                => foldSyncReader[Any, Unit](reader, ())((_, _) => ())
      }
    }

  /**
   * Tests elements in order and returns `true` at the first one satisfying
   * `pred`; otherwise consumes the entire input and returns `false` (including
   * for empty input). After a match, no further elements are pulled and unread
   * input is closed with the run. Exceptions from `pred` are defects.
   */
  def exists[A](pred: A => Boolean): Sink[Nothing, A, Boolean] = {
    val safePred: A => Boolean = a =>
      try pred(a)
      catch { case error: StreamError => throw StreamError.untrusted(error) }
    new Sink[Nothing, A, Boolean] {
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Boolean] =
        predicateAsync[A](reader, eof = false)(a => Async.succeed(safePred(a)))
      private[streams] def drain(reader: Reader.SyncReader[_]): Boolean = {
        val et = reader.jvmType
        if (et eq JvmType.Boolean) {
          val f = safePred.asInstanceOf[Boolean => Boolean]; var v = reader.readBooleanPhysical(-1)
          while (v >= 0) { if (f(v != 0)) return true; v = reader.readBooleanPhysical(-1) }
        } else if (et eq JvmType.Byte) {
          val f = safePred.asInstanceOf[Byte => Boolean]; var v = reader.readBytePhysical()
          while (v >= 0) { if (f(v.toByte)) return true; v = reader.readBytePhysical() }
        } else if (et eq JvmType.Char) {
          val f = safePred.asInstanceOf[Char => Boolean]; var v = reader.readCharPhysical(-1)
          while (v >= 0) { if (f(v.toChar)) return true; v = reader.readCharPhysical(-1) }
        } else if (et eq JvmType.Short) {
          val f = safePred.asInstanceOf[Short => Boolean]; var v = reader.readShortPhysical(Int.MinValue)
          while (v != Int.MinValue) {
            if (f(v.toShort)) return true; v = reader.readShortPhysical(Int.MinValue)
          }
        } else if (et eq JvmType.Int) {
          val fi = safePred.asInstanceOf[Int => Boolean]; val s = Long.MinValue
          var v  = reader.readIntPhysical(s)
          while (v != s) { if (fi(v.toInt)) return true; v = reader.readIntPhysical(s) }
        } else if (et eq JvmType.Long) {
          val fl = safePred.asInstanceOf[Long => Boolean]; val one = new Array[Long](1)
          var n  = reader.readLongsPhysical(one, 0, 1)
          while (n >= 0) { if (fl(one(0))) return true; n = reader.readLongsPhysical(one, 0, 1) }
        } else if (et eq JvmType.Float) {
          val ff = safePred.asInstanceOf[Float => Boolean]; val s = Double.MaxValue
          var v  = reader.readFloatPhysical(s)
          while (v != s) { if (ff(v.toFloat)) return true; v = reader.readFloatPhysical(s) }
        } else if (et eq JvmType.Double) {
          val fd = safePred.asInstanceOf[Double => Boolean]; val one = new Array[Double](1)
          var n  = reader.readDoublesPhysical(one, 0, 1)
          while (n >= 0) { if (fd(one(0))) return true; n = reader.readDoublesPhysical(one, 0, 1) }
        } else {
          var v = reader.read(EndOfStream)
          while (v.asInstanceOf[AnyRef] ne EndOfStream) {
            if (safePred(v.asInstanceOf[A])) return true
            v = reader.read(EndOfStream)
          }
        }
        false
      }
    }
  }

  /**
   * Sequentially tests elements with `pred`, returning `true` and stopping at
   * the first successful predicate, or `false` at end-of-stream. At most one
   * predicate effect runs at a time; no effect is started for unread input.
   * Predicate failure or cancellation fails or cancels the run as a defect.
   */
  def existsAsync[A](pred: A => Async[Boolean]): Sink[Nothing, A, Boolean] =
    createAsync(reader => predicateAsync[A](reader, eof = false)(a => StreamError.callbackAsync(pred(a))))

  /**
   * A sink that immediately fails with error `e` without consuming any
   * elements. The failure is reported in the typed error channel; the enclosing
   * run closes the unread input and its resources.
   */
  def fail[E](e: E): Sink[E, Any, Nothing] =
    new Sink[E, Any, Nothing] {
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Nothing] =
        Async.failTrusted(StreamError.sink(e))
      private[streams] def drain(reader: Reader.SyncReader[_]): Nothing =
        throw StreamError.sink(e)
    }

  private[streams] trait IsNothing[-A] {
    def value: Boolean
  }

  private[streams] object IsNothing extends LowPriorityIsNothing {
    implicit val isNothing: IsNothing[Nothing] = new IsNothing[Nothing] {
      val value = true
    }
  }

  private[streams] trait LowPriorityIsNothing {
    implicit def isNotNothing[A]: IsNothing[A] = new IsNothing[A] {
      val value = false
    }
  }

  /**
   * Tests elements in order and returns the first one satisfying `pred`.
   * Returns `None` only after consuming an input with no match. A match
   * short-circuits without pulling later elements; unread input is closed with
   * the run. Exceptions from `pred` are defects.
   */
  def find[A](pred: A => Boolean): Sink[Nothing, A, Option[A]] = {
    val safePred: A => Boolean = a =>
      try pred(a)
      catch { case error: StreamError => throw StreamError.untrusted(error) }
    new Sink[Nothing, A, Option[A]] {
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Option[A]] = {
        val puller                              = new AsyncPuller[A](reader)
        def loop(budget: Int): Async[Option[A]] = puller.pull().flatMap {
          case None    => Async.succeed(None)
          case Some(a) =>
            if (safePred(a)) Async.succeed(Some(a))
            else if (budget > 0) loop(budget - 1)
            else yieldEffect(loop(255))
        }
        loop(255)
      }
      private[streams] def drain(reader: Reader.SyncReader[_]): Option[A] = {
        val et = reader.jvmType
        if (et eq JvmType.Boolean) {
          val f = safePred.asInstanceOf[Boolean => Boolean]; var v = reader.readBooleanPhysical(-1)
          while (v >= 0) {
            val a = v != 0; if (f(a)) return Some(a.asInstanceOf[A]); v = reader.readBooleanPhysical(-1)
          }
          None
        } else if (et eq JvmType.Byte) {
          val f = safePred.asInstanceOf[Byte => Boolean]; var v = reader.readBytePhysical()
          while (v >= 0) { val a = v.toByte; if (f(a)) return Some(a.asInstanceOf[A]); v = reader.readBytePhysical() }
          None
        } else if (et eq JvmType.Char) {
          val f = safePred.asInstanceOf[Char => Boolean]; var v = reader.readCharPhysical(-1)
          while (v >= 0) {
            val a = v.toChar; if (f(a)) return Some(a.asInstanceOf[A]); v = reader.readCharPhysical(-1)
          }
          None
        } else if (et eq JvmType.Short) {
          val f = safePred.asInstanceOf[Short => Boolean]; var v = reader.readShortPhysical(Int.MinValue)
          while (v != Int.MinValue) {
            val a = v.toShort; if (f(a)) return Some(a.asInstanceOf[A]);
            v = reader.readShortPhysical(Int.MinValue)
          }
          None
        } else if (et eq JvmType.Int) {
          val fi = safePred.asInstanceOf[Int => Boolean]; val s = Long.MinValue
          var v  = reader.readIntPhysical(s)
          while (v != s) {
            if (fi(v.toInt)) return Some(v.toInt.asInstanceOf[A]); v = reader.readIntPhysical(s)
          }
          None
        } else if (et eq JvmType.Long) {
          val fl = safePred.asInstanceOf[Long => Boolean]; val one = new Array[Long](1)
          var n  = reader.readLongsPhysical(one, 0, 1)
          while (n >= 0) {
            val a = one(0)
            if (fl(a)) return Some(a.asInstanceOf[A])
            n = reader.readLongsPhysical(one, 0, 1)
          }
          None
        } else if (et eq JvmType.Float) {
          val ff = safePred.asInstanceOf[Float => Boolean]; val s = Double.MaxValue
          var v  = reader.readFloatPhysical(s)
          while (v != s) {
            if (ff(v.toFloat)) return Some(v.toFloat.asInstanceOf[A]); v = reader.readFloatPhysical(s)
          }
          None
        } else if (et eq JvmType.Double) {
          val fd = safePred.asInstanceOf[Double => Boolean]; val one = new Array[Double](1)
          var n  = reader.readDoublesPhysical(one, 0, 1)
          while (n >= 0) {
            val a = one(0)
            if (fd(a)) return Some(a.asInstanceOf[A])
            n = reader.readDoublesPhysical(one, 0, 1)
          }
          None
        } else {
          var v = reader.read(EndOfStream)
          while (v.asInstanceOf[AnyRef] ne EndOfStream) {
            val a = v.asInstanceOf[A]
            if (safePred(a)) return Some(a)
            v = reader.read(EndOfStream)
          }
          None
        }
      }
    }
  }

  /**
   * Sequentially tests elements and returns the first one for which `pred`
   * produces `true`, or `None` at end-of-stream. It short-circuits with at most
   * one predicate effect running; predicate failure or cancellation fails or
   * cancels the run as a defect.
   */
  def findAsync[A](pred: A => Async[Boolean]): Sink[Nothing, A, Option[A]] =
    createAsync { reader =>
      val puller                              = new AsyncPuller[A](reader)
      def loop(budget: Int): Async[Option[A]] = puller.pull().flatMap {
        case None    => Async.succeed(None)
        case Some(a) =>
          StreamError
            .callbackAsync(pred(a))
            .flatMap(ok =>
              if (ok) Async.succeed(Some(a))
              else if (budget > 0) loop(budget - 1)
              else yieldEffect(loop(255))
            )
      }
      loop(255)
    }

  /**
   * Strictly left-folds the entire input from `z`, invoking `f` in encounter
   * order. Empty input returns `z`. Exceptions from `f` are defects. The
   * `JvmType.Infer` evidence selects unboxed primitive input/accumulator paths
   * where supported; it does not alter the result.
   */
  def foldLeft[A, Z](z: Z)(f: (Z, A) => Z)(implicit jtZ: JvmType.Infer[Z]): Sink[Nothing, A, Z] =
    if (jtZ.jvmType eq JvmType.Double)
      new FoldLeftDouble[A](z.asInstanceOf[Double], f.asInstanceOf[(Double, A) => Double])
        .asInstanceOf[Sink[Nothing, A, Z]]
    else if (jtZ.jvmType eq JvmType.Int)
      new FoldLeftInt[A](z.asInstanceOf[Int], f.asInstanceOf[(Int, A) => Int]).asInstanceOf[Sink[Nothing, A, Z]]
    else if (jtZ.jvmType eq JvmType.Long)
      new FoldLeftLong[A](z.asInstanceOf[Long], f.asInstanceOf[(Long, A) => Long]).asInstanceOf[Sink[Nothing, A, Z]]
    else {
      val safeF: (Z, A) => Z = (z, a) =>
        try f(z, a)
        catch { case error: StreamError => throw StreamError.untrusted(error) }
      new Sink[Nothing, A, Z] {
        private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Z] =
          foldAsyncReader[A, Z](reader, z)((acc, a) => Async.succeed(safeF(acc, a)))
        private[streams] def drain(reader: Reader.SyncReader[_]): Z =
          foldSyncReader[A, Z](reader, z)(safeF)
      }
    }

  /**
   * Strictly left-folds the entire input from `z`, running one `f` effect at a
   * time in encounter order. Empty input returns `z`. Effect failure or
   * cancellation fails or cancels the run as a defect. `JvmType.Infer` keeps
   * the API aligned with specialized synchronous folds; semantics are
   * identical.
   */
  def foldLeftAsync[A, Z](z: Z)(f: (Z, A) => Async[Z])(implicit
    jtZ: JvmType.Infer[Z]
  ): Sink[Nothing, A, Z] =
    if (jtZ.jvmType eq JvmType.Long)
      new FoldLeftAsyncLong[A](z.asInstanceOf[Long], f.asInstanceOf[(Long, A) => Async[Long]])
        .asInstanceOf[Sink[Nothing, A, Z]]
    else
      createAsync { reader =>
        val fold = (acc: Z, value: A) => StreamError.callbackAsync(f(acc, value))
        reader match {
          case terminal: AsyncInterpreter.TerminalDriver => terminal.foldAsync(z, jtZ.jvmType, fold)
          case _                                         => foldAsyncReader[A, Z](reader, z)(fold)
        }
      }

  /**
   * Tests elements in order and returns `false` at the first one that does not
   * satisfy `pred`; otherwise consumes the entire input and returns `true`
   * (including for empty input). After a mismatch, unread input is closed with
   * the run. Exceptions from `pred` are defects.
   */
  def forall[A](pred: A => Boolean): Sink[Nothing, A, Boolean] = {
    val safePred: A => Boolean = a =>
      try pred(a)
      catch { case error: StreamError => throw StreamError.untrusted(error) }
    new Sink[Nothing, A, Boolean] {
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Boolean] =
        predicateAsync[A](reader, eof = true)(a => Async.succeed(safePred(a)))
      private[streams] def drain(reader: Reader.SyncReader[_]): Boolean = {
        val et = reader.jvmType
        if (et eq JvmType.Boolean) {
          val f = safePred.asInstanceOf[Boolean => Boolean]; var v = reader.readBooleanPhysical(-1)
          while (v >= 0) { if (!f(v != 0)) return false; v = reader.readBooleanPhysical(-1) }
        } else if (et eq JvmType.Byte) {
          val f = safePred.asInstanceOf[Byte => Boolean]; var v = reader.readBytePhysical()
          while (v >= 0) { if (!f(v.toByte)) return false; v = reader.readBytePhysical() }
        } else if (et eq JvmType.Char) {
          val f = safePred.asInstanceOf[Char => Boolean]; var v = reader.readCharPhysical(-1)
          while (v >= 0) { if (!f(v.toChar)) return false; v = reader.readCharPhysical(-1) }
        } else if (et eq JvmType.Short) {
          val f = safePred.asInstanceOf[Short => Boolean]; var v = reader.readShortPhysical(Int.MinValue)
          while (v != Int.MinValue) {
            if (!f(v.toShort)) return false; v = reader.readShortPhysical(Int.MinValue)
          }
        } else if (et eq JvmType.Int) {
          val fi = safePred.asInstanceOf[Int => Boolean]; val s = Long.MinValue
          var v  = reader.readIntPhysical(s)
          while (v != s) { if (!fi(v.toInt)) return false; v = reader.readIntPhysical(s) }
        } else if (et eq JvmType.Long) {
          val fl = safePred.asInstanceOf[Long => Boolean]; val one = new Array[Long](1)
          var n  = reader.readLongsPhysical(one, 0, 1)
          while (n >= 0) { if (!fl(one(0))) return false; n = reader.readLongsPhysical(one, 0, 1) }
        } else if (et eq JvmType.Float) {
          val ff = safePred.asInstanceOf[Float => Boolean]; val s = Double.MaxValue
          var v  = reader.readFloatPhysical(s)
          while (v != s) { if (!ff(v.toFloat)) return false; v = reader.readFloatPhysical(s) }
        } else if (et eq JvmType.Double) {
          val fd = safePred.asInstanceOf[Double => Boolean]; val one = new Array[Double](1)
          var n  = reader.readDoublesPhysical(one, 0, 1)
          while (n >= 0) { if (!fd(one(0))) return false; n = reader.readDoublesPhysical(one, 0, 1) }
        } else {
          var v = reader.read(EndOfStream)
          while (v.asInstanceOf[AnyRef] ne EndOfStream) {
            if (!safePred(v.asInstanceOf[A])) return false
            v = reader.read(EndOfStream)
          }
        }
        true
      }
    }
  }

  /**
   * Sequentially tests elements, returning `false` and stopping at the first
   * predicate result of `false`, or `true` at end-of-stream. At most one effect
   * runs at a time; predicate failure or cancellation fails or cancels the run
   * as a defect.
   */
  def forallAsync[A](pred: A => Async[Boolean]): Sink[Nothing, A, Boolean] =
    createAsync(reader => predicateAsync[A](reader, eof = true)(a => StreamError.callbackAsync(pred(a))))

  /**
   * Consumes the entire input, invoking `f` once per element in encounter
   * order, and returns `Unit`. An exception from `f` is a defect and stops
   * consumption. Primitive reader lanes avoid boxing where supported.
   */
  def foreach[A](f: A => Unit): Sink[Nothing, A, Unit] = {
    val safeF: A => Unit = a =>
      try f(a)
      catch { case error: StreamError => throw StreamError.untrusted(error) }
    new Sink[Nothing, A, Unit] {
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Unit] =
        foldAsyncReader[A, Unit](reader, ()) { (_, a) => safeF(a); Async.succeed(()) }
      private[streams] def drain(reader: Reader.SyncReader[_]): Unit =
        foldSyncReader[A, Unit](reader, ())((_, value) => safeF(value))
    }
  }

  /**
   * Consumes the entire input, invoking `f` sequentially in encounter order,
   * and returns `Unit`. At most one callback effect runs at a time. Callback
   * failure or cancellation fails or cancels the run as a defect and stops
   * pulling input.
   */
  def foreachAsync[A](f: A => Async[Unit]): Sink[Nothing, A, Unit] =
    new Sink[Nothing, A, Unit] {
      private[streams] def drain(reader: Reader.SyncReader[_]): Unit                  = blockOnJvm(drain(reader.toAsync))
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Unit] =
        if (reader.jvmType eq JvmType.Int)
          foldNativeAsyncIntUnit(reader.asInstanceOf[Reader.AsyncReader[Int]], f.asInstanceOf[Int => Async[Unit]])
        else foldAsyncReader[A, Unit](reader, ())((_, a) => StreamError.callbackAsync(f(a)))
    }

  /**
   * Consumes the entire input and writes each character, in order, to `w`, then
   * returns `Unit`. Writes are synchronous even during an asynchronous stream
   * run. This sink neither flushes nor closes the writer; its owner remains
   * responsible for both. An I/O exception is a defect and stops consumption.
   */
  def fromJavaWriter(w: java.io.Writer): Sink[Nothing, Char, Unit] =
    new Sink[Nothing, Char, Unit] {
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Unit] =
        foldAsyncReader[Char, Unit](reader, ()) { (_, c) => w.write(c.toInt); Async.succeed(()) }
      private[streams] def drain(reader: Reader.SyncReader[_]): Unit =
        foldSyncReader[Char, Unit](reader, ())((_, c) => w.write(c.toInt))
    }

  /**
   * Consumes the entire input and writes each byte, in order, to `os`, then
   * returns `Unit`. Bytes are written as unsigned values from `0` through
   * `255`. Writes are synchronous even during an asynchronous stream run. This
   * sink neither flushes nor closes the stream; its owner remains responsible
   * for both. An I/O exception is a defect and stops consumption.
   */
  def fromOutputStream(os: java.io.OutputStream): Sink[Nothing, Byte, Unit] =
    new Sink[Nothing, Byte, Unit] {
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Unit] =
        foldAsyncReader[Byte, Unit](reader, ()) { (_, b) => os.write(b & 0xff); Async.succeed(()) }
      private[streams] def drain(reader: Reader.SyncReader[_]): Unit =
        foldSyncReader[Byte, Unit](reader, ())((_, b) => os.write(b & 0xff))
    }

  /**
   * Pulls at most one element, returning it in `Some`, or `None` for empty
   * input. It short-circuits after the first element; remaining input is not
   * pulled and is closed with the enclosing run. Primitive reader lanes are
   * used where supported.
   */
  def head[A]: Sink[Nothing, A, Option[A]] =
    new Sink[Nothing, A, Option[A]] {
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Option[A]] =
        new AsyncPuller[A](reader).pull()
      private[streams] def drain(reader: Reader.SyncReader[_]): Option[A] =
        pullSync[A](reader)
    }

  /**
   * Consumes the entire input and returns its last element in `Some`, or `None`
   * for empty input. Primitive reader lanes are consumed without boxing where
   * supported.
   */
  def last[A]: Sink[Nothing, A, Option[A]] =
    new Sink[Nothing, A, Option[A]] {
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Option[A]] =
        foldAsyncReader[A, Option[A]](reader, None)((_, a) => Async.succeed(Some(a)))
      private[streams] def drain(reader: Reader.SyncReader[_]): Option[A] =
        foldSyncReader[A, Option[A]](reader, None)((_, value) => Some(value))
    }

  /**
   * Consumes all `Double` elements and sums them from `0.0` using `Double`
   * arithmetic. Empty input yields `0.0`; overflow, infinities, and `NaN`
   * follow normal IEEE 754 semantics.
   */
  val sumDouble: Sink[Nothing, Double, Double] = loopDoubleToDouble(0.0)((acc, a) => acc + a)

  /**
   * Consumes all `Float` elements and sums them from `0.0` in a `Double`
   * accumulator, reducing intermediate rounding loss. Empty input yields `0.0`;
   * infinities and `NaN` follow normal IEEE 754 semantics. The specialized
   * float reader path avoids boxing.
   */
  val sumFloat: Sink[Nothing, Float, Double] = loopFloatToDouble(0.0)((acc, a) => acc + a.toDouble)

  /**
   * Consumes all `Int` elements and sums them from `0L` in a `Long`
   * accumulator. Empty input yields `0L`; overflow wraps according to `Long`
   * arithmetic. The specialized int reader path avoids boxing.
   */
  val sumInt: Sink[Nothing, Int, Long] = loopIntToLong(0L)((acc, a) => acc + a.toLong)

  /**
   * Consumes all `Long` elements and sums them from `0L`. Empty input yields
   * `0L`; overflow wraps according to `Long` arithmetic.
   */
  val sumLong: Sink[Nothing, Long, Long] = loopLongToLong(0L)((acc, a) => acc + a)

  /**
   * Collects at most the first `n` elements, in order, into a `Chunk`. If
   * `n <= 0`, it returns an empty chunk without pulling input; if the input
   * ends first, it returns all available elements. Reaching `n` short-circuits,
   * and remaining input is closed with the enclosing run rather than returned
   * as leftovers. Primitive inputs use specialized chunk builders.
   */
  def take[A](n: Int): Sink[Nothing, A, Chunk[A]] =
    new Sink[Nothing, A, Chunk[A]] {
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Chunk[A]] = {
        def takeAsync[B](b: ChunkBuilder[B]): Async[Chunk[A]] = {
          val puller                                     = new AsyncPuller[B](reader)
          def loop(i: Int, budget: Int): Async[Chunk[A]] = if (i >= n) Async.succeed(b.result().asInstanceOf[Chunk[A]])
          else
            puller.pull().flatMap {
              case None    => Async.succeed(b.result().asInstanceOf[Chunk[A]])
              case Some(a) =>
                b += a
                if (budget > 0) loop(i + 1, budget - 1)
                else yieldEffect(loop(i + 1, 255))
            }
          loop(0, 255)
        }
        if (n <= 0) Async.succeed(Chunk.empty)
        else
          reader.jvmType match {
            case JvmType.Boolean => takeAsync(new ChunkBuilder.Boolean())
            case JvmType.Byte    => takeAsync(new ChunkBuilder.Byte())
            case JvmType.Char    => takeAsync(new ChunkBuilder.Char())
            case JvmType.Short   => takeAsync(new ChunkBuilder.Short())
            case JvmType.Int     => takeAsync(new ChunkBuilder.Int())
            case JvmType.Long    => takeAsync(new ChunkBuilder.Long())
            case JvmType.Float   => takeAsync(new ChunkBuilder.Float())
            case JvmType.Double  => takeAsync(new ChunkBuilder.Double())
            case _               => takeAsync(ChunkBuilder.make[A](n))
          }
      }
      private[streams] def drain(reader: Reader.SyncReader[_]): Chunk[A] = {
        val et = reader.jvmType
        if (n <= 0) Chunk.empty
        else if (et eq JvmType.Int) takeInt(reader)
        else if (et eq JvmType.Long) takeLong(reader)
        else if (et eq JvmType.Float) takeFloat(reader)
        else if (et eq JvmType.Double) takeDouble(reader)
        else if (et eq JvmType.Byte) takeByte(reader)
        else if (et eq JvmType.Boolean) takeBoolean(reader)
        else if (et eq JvmType.Char) takeChar(reader)
        else if (et eq JvmType.Short) takeShort(reader)
        else takeGeneric(reader)
      }
      private def takeBoolean(reader: Reader.SyncReader[_]): Chunk[A] = {
        val b = new ChunkBuilder.Boolean(); var count = 0; var v = reader.readBooleanPhysical(-1)
        while (count < n && v >= 0) {
          b.addOne(v != 0); count += 1; if (count < n) v = reader.readBooleanPhysical(-1)
        }
        b.result().asInstanceOf[Chunk[A]]
      }
      private def takeByte(reader: Reader.SyncReader[_]): Chunk[A] = {
        val b = new ChunkBuilder.Byte(); var count = 0; var v = reader.readBytePhysical()
        while (count < n && v >= 0) { b.addOne(v.toByte); count += 1; if (count < n) v = reader.readBytePhysical() }
        b.result().asInstanceOf[Chunk[A]]
      }
      private def takeChar(reader: Reader.SyncReader[_]): Chunk[A] = {
        val b = new ChunkBuilder.Char(); var count = 0; var v = reader.readCharPhysical(-1)
        while (count < n && v >= 0) {
          b.addOne(v.toChar); count += 1; if (count < n) v = reader.readCharPhysical(-1)
        }
        b.result().asInstanceOf[Chunk[A]]
      }
      private def takeDouble(reader: Reader.SyncReader[_]): Chunk[A] = {
        val b    = new ChunkBuilder.Double(); val one = new Array[Double](1); var count = 0
        var read = reader.readDoublesPhysical(one, 0, 1)
        while (count < n && read >= 0) {
          b.addOne(one(0)); count += 1
          if (count < n) read = reader.readDoublesPhysical(one, 0, 1)
        }
        b.result().asInstanceOf[Chunk[A]]
      }
      private def takeFloat(reader: Reader.SyncReader[_]): Chunk[A] = {
        val b = new ChunkBuilder.Float(); var count = 0; val s = Double.MaxValue
        while (count < n) {
          val v = reader.readFloatPhysical(s)
          if (v == s) return b.result().asInstanceOf[Chunk[A]]
          b.addOne(v.toFloat); count += 1
        }
        b.result().asInstanceOf[Chunk[A]]
      }
      private def takeGeneric(reader: Reader.SyncReader[_]): Chunk[A] = {
        val buf = ChunkBuilder.make[A](n); var count = 0
        while (count < n) {
          val v = reader.read(EndOfStream)
          if (v.asInstanceOf[AnyRef] eq EndOfStream) return buf.result()
          buf += v.asInstanceOf[A]; count += 1
        }
        buf.result()
      }
      private def takeInt(reader: Reader.SyncReader[_]): Chunk[A] = {
        val b = new ChunkBuilder.Int(); var count = 0; val s = Long.MinValue
        while (count < n) {
          val v = reader.readIntPhysical(s)
          if (v == s) return b.result().asInstanceOf[Chunk[A]]
          b.addOne(v.toInt); count += 1
        }
        b.result().asInstanceOf[Chunk[A]]
      }
      private def takeLong(reader: Reader.SyncReader[_]): Chunk[A] = {
        val b    = new ChunkBuilder.Long(); val one = new Array[Long](1); var count = 0
        var read = reader.readLongsPhysical(one, 0, 1)
        while (count < n && read >= 0) {
          b.addOne(one(0)); count += 1
          if (count < n) read = reader.readLongsPhysical(one, 0, 1)
        }
        b.result().asInstanceOf[Chunk[A]]
      }
      private def takeShort(reader: Reader.SyncReader[_]): Chunk[A] = {
        val b = new ChunkBuilder.Short(); var count = 0; var v = reader.readShortPhysical(Int.MinValue)
        while (count < n && v != Int.MinValue) {
          b.addOne(v.toShort); count += 1; if (count < n) v = reader.readShortPhysical(Int.MinValue)
        }
        b.result().asInstanceOf[Chunk[A]]
      }
    }

  private def asyncMapReader[A, B](
    source: Reader.AsyncReader[_],
    outputType: JvmType,
    f: A => Async[B]
  ): Reader.AsyncReader[B] = {
    val fused = AsyncInterpreter.fuseAsyncMap[A, B](source, source.jvmType, outputType, f)
    if (fused ne null) fused
    else
      AsyncInterpreter
        .transform(source.asInstanceOf[Reader.AsyncReader[Any]])(
          _.addAsyncMap[A, B](source.jvmType, outputType)(f)
        )
        .toReader[B]
  }

  private def collectAllBoolean(reader: Reader.SyncReader[_]): Chunk[Boolean] = {
    val b = new ChunkBuilder.Boolean()
    foldSyncReader[Boolean, Unit](reader, ())((_, v) => b.addOne(v))
    b.result()
  }

  private def collectAllByte(reader: Reader.SyncReader[_]): Chunk[Byte] = {
    val b = new ChunkBuilder.Byte()
    var v = reader.readBytePhysical()
    while (v >= 0) { b.addOne(v.toByte); v = reader.readBytePhysical() }
    b.result()
  }

  private def collectAllChar(reader: Reader.SyncReader[_]): Chunk[Char] = {
    val b = new ChunkBuilder.Char()
    foldSyncReader[Char, Unit](reader, ())((_, v) => b.addOne(v))
    b.result()
  }

  private def collectAllDouble(reader: Reader.SyncReader[_]): Chunk[Double] = {
    val b = new ChunkBuilder.Double()
    foldSyncReader[Double, Unit](reader, ())((_, v) => b.addOne(v))
    b.result()
  }

  private def collectAllFloat(reader: Reader.SyncReader[_]): Chunk[Float] = {
    val b = new ChunkBuilder.Float()
    val s = Double.MaxValue; var v = reader.readFloatPhysical(s)
    while (v != s) { b.addOne(v.toFloat); v = reader.readFloatPhysical(s) }
    b.result()
  }

  private def collectAllInt(reader: Reader.SyncReader[_]): Chunk[Int] = {
    val b = new ChunkBuilder.Int()
    val s = Long.MinValue
    var v = reader.readIntPhysical(s)
    while (v != s) { b.addOne(v.toInt); v = reader.readIntPhysical(s) }
    b.result()
  }

  private def collectAllLong(reader: Reader.SyncReader[_]): Chunk[Long] = {
    val b = new ChunkBuilder.Long()
    foldSyncReader[Long, Unit](reader, ())((_, v) => b.addOne(v))
    b.result()
  }

  private def collectAllShort(reader: Reader.SyncReader[_]): Chunk[Short] = {
    val b = new ChunkBuilder.Short()
    foldSyncReader[Short, Unit](reader, ())((_, v) => b.addOne(v))
    b.result()
  }

  private[streams] def collectNativeAsyncInt(reader: Reader.AsyncReader[Int]): Async[Chunk[Int]] =
    new NativeAsyncIntCollect(reader)

  private[streams] def collectTakenFilteredNativeAsyncInt(
    reader: Reader.AsyncReader[Int],
    limit: Long,
    predicate: Int => Async[Boolean]
  ): Async[Chunk[Int]] =
    new TakenFilteredNativeAsyncIntCollect(reader, math.max(0L, limit), predicate)

  private[streams] def drainNativeAsyncInt(reader: Reader.AsyncReader[Int]): Async[Unit] =
    new NativeAsyncIntDrain(reader)

  private[streams] def foldAsyncReader[A, Z](reader: Reader.AsyncReader[_], zero: Z)(
    f: (Z, A) => Async[Z]
  ): Async[Z] = {
    val protectedReader = reader match {
      case protectedView: ProtectedAsyncReader[_] => protectedView.underlying.asInstanceOf[Reader.AsyncReader[A]]
      case _                                      => null
    }
    val sync = Reader.borrowedSync(
      if (protectedReader eq null) reader.asInstanceOf[Reader.AsyncReader[A]] else protectedReader
    )
    if (sync ne null) foldBorrowedSyncReaderAsync(sync, zero, protectedReader ne null)(f)
    else {
      val puller                              = new AsyncPuller[A](reader)
      def loop(acc: Z, budget: Int): Async[Z] =
        puller.pull().flatMap {
          case None    => Async.succeed(acc)
          case Some(a) =>
            f(acc, a).flatMap(next =>
              if (budget > 0) loop(next, budget - 1)
              else yieldEffect(loop(next, 255))
            )
        }
      loop(zero, 255)
    }
  }

  private def foldBorrowedSyncIntLongAsync(
    reader: Reader.SyncReader[Int],
    zero: Long,
    protectReaderFailures: Boolean,
    f: (Long, Int) => Async[Long]
  ): Async[Long] =
    new BorrowedSyncIntLongFold(reader, zero, protectReaderFailures, f)

  private def foldBorrowedSyncLongLongAsync(
    reader: Reader.SyncReader[Long],
    zero: Long,
    protectReaderFailures: Boolean,
    f: (Long, Long) => Async[Long]
  ): Async[Long] =
    new BorrowedSyncLongLongFold(reader, zero, protectReaderFailures, f)

  private def foldBorrowedSyncReaderAsync[A, Z](
    reader: Reader.SyncReader[A],
    zero: Z,
    protectReaderFailures: Boolean
  )(
    f: (Z, A) => Async[Z]
  ): Async[Z] = {
    val BooleanLane   = 0
    val ByteLane      = 1
    val CharLane      = 2
    val DoubleLane    = 3
    val FloatLane     = 4
    val IntLane       = 5
    val LongLane      = 6
    val ReferenceLane = 7
    val ShortLane     = 8
    val observed      = new AsyncStep[Z]
    val doubles       = if (reader.jvmType eq JvmType.Double) new Array[Double](1) else null
    val floats        = if (reader.jvmType eq JvmType.Float) new Array[Float](1) else null
    val longs         = if (reader.jvmType eq JvmType.Long) new Array[Long](1) else null

    def continue(callback: Async[Z], budget: Int, lane: Int): Async[Z] = {
      observed.reset()
      Async.foldStep(callback)(observed)
      if (observed.kind == AsyncStep.Success) {
        val next = observed.value
        if (budget > 0) resume(lane, next, budget - 1)
        else yieldEffect(resume(lane, next, 255))
      } else if (observed.kind == AsyncStep.Pending)
        callback
          .flatMap(next => if (budget > 0) resume(lane, next, budget - 1) else yieldEffect(resume(lane, next, 255)))
      else callback
    }

    def failed(cause: Throwable): Async[Z] = cause match {
      case error: StreamError if error.isTrusted && protectReaderFailures => Async.fail(new ReaderFailure(error))
      case error: StreamError if error.isTrusted                          => Async.failTrusted(error)
      case _                                                              => Async.fail(cause)
    }

    def loopBoolean(acc: Z, budget: Int): Async[Z] =
      try {
        val value = reader.readBooleanPhysical(-1)
        if (value < 0) Async.succeed(acc)
        else continue(f(acc, (value != 0).asInstanceOf[A]), budget, BooleanLane)
      } catch { case cause: Throwable => failed(cause) }

    def loopByte(acc: Z, budget: Int): Async[Z] =
      try {
        val value = reader.readBytePhysical()
        if (value < 0) Async.succeed(acc)
        else continue(f(acc, value.toByte.asInstanceOf[A]), budget, ByteLane)
      } catch { case cause: Throwable => failed(cause) }

    def loopChar(acc: Z, budget: Int): Async[Z] =
      try {
        val value = reader.readCharPhysical(-1)
        if (value < 0) Async.succeed(acc)
        else continue(f(acc, value.toChar.asInstanceOf[A]), budget, CharLane)
      } catch { case cause: Throwable => failed(cause) }

    def loopDouble(acc: Z, budget: Int): Async[Z] =
      try {
        val count = reader.readDoublesPhysical(doubles, 0, 1)
        if (count < 0) Async.succeed(acc)
        else continue(f(acc, doubles(0).asInstanceOf[A]), budget, DoubleLane)
      } catch { case cause: Throwable => failed(cause) }

    def loopFloat(acc: Z, budget: Int): Async[Z] =
      try {
        val count = reader.readFloatsPhysical(floats, 0, 1)
        if (count < 0) Async.succeed(acc)
        else continue(f(acc, floats(0).asInstanceOf[A]), budget, FloatLane)
      } catch { case cause: Throwable => failed(cause) }

    def loopInt(acc: Z, budget: Int): Async[Z] =
      try {
        val value = reader.readIntPhysical(Long.MinValue)
        if (value == Long.MinValue) Async.succeed(acc)
        else continue(f(acc, value.toInt.asInstanceOf[A]), budget, IntLane)
      } catch { case cause: Throwable => failed(cause) }

    def loopLong(acc: Z, budget: Int): Async[Z] =
      try {
        val count = reader.readLongsPhysical(longs, 0, 1)
        if (count < 0) Async.succeed(acc)
        else continue(f(acc, longs(0).asInstanceOf[A]), budget, LongLane)
      } catch { case cause: Throwable => failed(cause) }

    def loopReference(acc: Z, budget: Int): Async[Z] =
      try {
        val value = reader.read[Any](EndOfStream)
        if (value.asInstanceOf[AnyRef] eq EndOfStream) Async.succeed(acc)
        else continue(f(acc, value.asInstanceOf[A]), budget, ReferenceLane)
      } catch { case cause: Throwable => failed(cause) }

    def loopShort(acc: Z, budget: Int): Async[Z] =
      try {
        val value = reader.readShortPhysical(Int.MinValue)
        if (value == Int.MinValue) Async.succeed(acc)
        else continue(f(acc, value.toShort.asInstanceOf[A]), budget, ShortLane)
      } catch { case cause: Throwable => failed(cause) }

    def resume(lane: Int, acc: Z, budget: Int): Async[Z] = (lane: @scala.annotation.switch) match {
      case 0 => loopBoolean(acc, budget)
      case 1 => loopByte(acc, budget)
      case 2 => loopChar(acc, budget)
      case 3 => loopDouble(acc, budget)
      case 4 => loopFloat(acc, budget)
      case 5 => loopInt(acc, budget)
      case 6 => loopLong(acc, budget)
      case 7 => loopReference(acc, budget)
      case 8 => loopShort(acc, budget)
    }

    reader.jvmType match {
      case JvmType.Boolean => loopBoolean(zero, 255)
      case JvmType.Byte    => loopByte(zero, 255)
      case JvmType.Char    => loopChar(zero, 255)
      case JvmType.Double  => loopDouble(zero, 255)
      case JvmType.Float   => loopFloat(zero, 255)
      case JvmType.Int     => loopInt(zero, 255)
      case JvmType.Long    => loopLong(zero, 255)
      case JvmType.Short   => loopShort(zero, 255)
      case _               => loopReference(zero, 255)
    }
  }

  private[streams] def foldFilteredNativeAsyncIntLong(
    reader: Reader.AsyncReader[Int],
    predicate: Int => Async[Boolean],
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ): Async[Long] =
    new FilteredNativeAsyncIntLongFold(reader, predicate, zero, fold)

  private[streams] def foldMappedNativeAsyncIntLong(
    reader: Reader.AsyncReader[Int],
    map: Int => Async[Int],
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ): Async[Long] =
    new MappedNativeAsyncIntLongFold(reader, map, zero, fold)

  private[streams] def foldMappedSyncAsyncIntLong(
    reader: Reader.SyncReader[Int],
    map: Int => Async[Int],
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ): Async[Long] =
    new MappedSyncAsyncIntLongFold(reader, map, zero, fold)

  private[streams] def foldNativeAsyncIntDouble(
    reader: Reader.AsyncReader[Int],
    zero: Double,
    f: (Double, Int) => Async[Double]
  ): Async[Double] =
    new NativeAsyncIntDoubleFold(reader, zero, f)

  private[streams] def foldNativeAsyncIntFloat(
    reader: Reader.AsyncReader[Int],
    zero: Float,
    f: (Float, Int) => Async[Float]
  ): Async[Float] =
    new NativeAsyncIntFloatFold(reader, zero, f)

  private[streams] def foldNativeAsyncIntInt(
    reader: Reader.AsyncReader[Int],
    zero: Int,
    f: (Int, Int) => Async[Int]
  ): Async[Int] =
    new NativeAsyncIntIntFold(reader, zero, f)

  private[streams] def foldNativeAsyncIntLong(
    reader: Reader.AsyncReader[Int],
    zero: Long,
    f: (Long, Int) => Async[Long]
  ): Async[Long] = {
    val direct = AsyncConcurrentReaders.foldLong(reader, zero, f)
    if (direct.asInstanceOf[AnyRef] ne null) direct
    else new NativeAsyncIntLongFold(reader, zero, f)
  }

  private[streams] def foldNativeAsyncIntUnit(
    reader: Reader.AsyncReader[Int],
    f: Int => Async[Unit]
  ): Async[Unit] =
    new NativeAsyncIntForeach(reader, f)

  private[streams] def foldSyncReader[A, Z](reader: Reader.SyncReader[_], zero: Z)(f: (Z, A) => Z): Z =
    reader.jvmType match {
      case JvmType.Boolean =>
        val step = f.asInstanceOf[(Z, Boolean) => Z]; var acc = zero; var v = reader.readBooleanPhysical(-1)
        while (v >= 0) { acc = step(acc, v != 0); v = reader.readBooleanPhysical(-1) }; acc
      case JvmType.Byte =>
        val step = f.asInstanceOf[(Z, Byte) => Z]; var acc = zero; var v = reader.readBytePhysical()
        while (v >= 0) { acc = step(acc, v.toByte); v = reader.readBytePhysical() }; acc
      case JvmType.Char =>
        val step = f.asInstanceOf[(Z, Char) => Z]; var acc = zero; var v = reader.readCharPhysical(-1)
        while (v >= 0) { acc = step(acc, v.toChar); v = reader.readCharPhysical(-1) }; acc
      case JvmType.Short =>
        val step = f.asInstanceOf[(Z, Short) => Z]; var acc = zero;
        var v    = reader.readShortPhysical(Int.MinValue)
        while (v != Int.MinValue) { acc = step(acc, v.toShort); v = reader.readShortPhysical(Int.MinValue) };
        acc
      case JvmType.Int =>
        val step = f.asInstanceOf[(Z, Int) => Z]; var acc = zero; var v = reader.readIntPhysical(Long.MinValue)
        while (v != Long.MinValue) { acc = step(acc, v.toInt); v = reader.readIntPhysical(Long.MinValue) }; acc
      case JvmType.Long =>
        val step = f.asInstanceOf[(Z, Long) => Z]; val one = new Array[Long](1); var acc = zero
        var n    = reader.readLongsPhysical(one, 0, 1)
        while (n >= 0) { acc = step(acc, one(0)); n = reader.readLongsPhysical(one, 0, 1) }; acc
      case JvmType.Float =>
        val step = f.asInstanceOf[(Z, Float) => Z]; var acc = zero;
        var v    = reader.readFloatPhysical(Double.MaxValue)
        while (v != Double.MaxValue) {
          acc = step(acc, v.toFloat); v = reader.readFloatPhysical(Double.MaxValue)
        };
        acc
      case JvmType.Double =>
        val step = f.asInstanceOf[(Z, Double) => Z]; val one = new Array[Double](1); var acc = zero
        var n    = reader.readDoublesPhysical(one, 0, 1)
        while (n >= 0) { acc = step(acc, one(0)); n = reader.readDoublesPhysical(one, 0, 1) }; acc
      case _ =>
        var acc = zero; var v = reader.read(EndOfStream)
        while (v.asInstanceOf[AnyRef] ne EndOfStream) { acc = f(acc, v.asInstanceOf[A]); v = reader.read(EndOfStream) };
        acc
    }

  private[streams] def foldTakeDropNativeAsyncIntLong(
    reader: Reader.AsyncReader[Int],
    drop: Long,
    limit: Long,
    zero: Long,
    f: (Long, Int) => Async[Long]
  ): Async[Long] =
    new TakeDropNativeAsyncIntLongFold(reader, math.max(0L, drop), math.max(0L, limit), zero, f)

  private[streams] def foldTakenNativeAsyncInt[Z](
    reader: Reader.AsyncReader[Int],
    limit: Long,
    zero: Z,
    f: (Z, Int) => Async[Z]
  ): Async[Z] =
    new TakenNativeAsyncIntFold(reader, math.max(0L, limit), zero, f)

  private[streams] def foldTakenNativeAsyncIntLong(
    reader: Reader.AsyncReader[Int],
    limit: Long,
    zero: Long,
    f: (Long, Int) => Async[Long]
  ): Async[Long] =
    new TakenNativeAsyncIntLongFold(reader, math.max(0L, limit), zero, f)

  private[streams] def foldTakenWhileNativeAsyncIntLong(
    reader: Reader.AsyncReader[Int],
    predicate: Int => Async[Boolean],
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ): Async[Long] =
    new TakenWhileNativeAsyncIntLongFold(reader, predicate, zero, fold)

  private final class AsyncStep[A] extends Async.StepFold[A, Unit] {
    var kind: Int = AsyncStep.Pending
    var value: A  = null.asInstanceOf[A]

    def failure(cause: Throwable): Unit      = { val _ = cause; kind = AsyncStep.Failure }
    def pending(pollable: Pollable[A]): Unit = { val _ = pollable; kind = AsyncStep.Pending }
    def success(result: A): Unit             = { value = result; kind = AsyncStep.Success }

    def reset(): Unit = { kind = AsyncStep.Pending; value = null.asInstanceOf[A] }
  }

  private object AsyncStep {
    final val Failure = 0
    final val Pending = 1
    final val Success = 2
  }

  private final class BorrowedSyncIntLongFold(
    reader: Reader.SyncReader[Int],
    private var acc: Long,
    protectReaderFailures: Boolean,
    f: (Long, Int) => Async[Long]
  ) extends Pollable[Long]
      with Runnable
      with Async.StepFold[Long, Unit] {
    private var cause: Throwable = null
    private var kind: Int        = AsyncStep.Pending
    private var wake: Runnable   = null

    def failure(failure: Throwable): Unit = { cause = failure; kind = AsyncStep.Failure }

    def pending(pollable: Pollable[Long]): Unit = { val _ = pollable; kind = AsyncStep.Pending }

    def poll(onComplete: Runnable): Async[Long] = {
      var budget = 1024
      try {
        var value = reader.readIntPhysical(Long.MinValue)
        while (value != Long.MinValue) {
          val callback =
            try f(acc, value.toInt)
            catch { case error: StreamError => return Async.fail(StreamError.untrusted(error)) }
          cause = null
          kind = AsyncStep.Pending
          Async.foldStep(callback)(this)
          if (kind == AsyncStep.Success) acc = successValue
          else if (kind == AsyncStep.Pending)
            return callback
              .mapError(StreamError.callbackFailure)
              .flatMap { next => acc = next; this }
          else
            return cause match {
              case error: StreamError => Async.fail(StreamError.untrusted(error))
              case _                  => Async.fail(cause)
            }
          budget -= 1
          if (budget == 0) {
            wake = onComplete
            Async.schedule(this, forceMacrotask = true)
            return this
          }
          value = reader.readIntPhysical(Long.MinValue)
        }
        Async.succeed(acc)
      } catch {
        case error: StreamError if error.isTrusted && protectReaderFailures => Async.fail(new ReaderFailure(error))
        case error: StreamError if error.isTrusted                          => Async.failTrusted(error)
        case failure: Throwable                                             => Async.fail(failure)
      }
    }

    def run(): Unit = {
      val callback = wake
      wake = null
      callback.run()
    }

    private var successValue: Long                        = 0L
    def success(result: Long): Unit                       = { successValue = result; kind = AsyncStep.Success }
    override def trustedFailure(failure: Throwable): Unit = { cause = failure; kind = AsyncStep.Failure }
  }

  private final class BorrowedSyncLongLongFold(
    reader: Reader.SyncReader[Long],
    private var acc: Long,
    protectReaderFailures: Boolean,
    f: (Long, Long) => Async[Long]
  ) extends Pollable[Long]
      with Runnable
      with Async.StepFold[Long, Unit] {
    private var cause: Throwable   = null
    private var kind: Int          = AsyncStep.Pending
    private val one: Array[Long]   = new Array[Long](1)
    private var successValue: Long = 0L
    private var wake: Runnable     = null

    def failure(failure: Throwable): Unit = { cause = failure; kind = AsyncStep.Failure }

    def pending(pollable: Pollable[Long]): Unit = { val _ = pollable; kind = AsyncStep.Pending }

    def poll(onComplete: Runnable): Async[Long] = {
      var budget = 1024
      try {
        var count = reader.readLongsPhysical(one, 0, 1)
        while (count >= 0) {
          val callback =
            try f(acc, one(0))
            catch { case error: StreamError => return Async.fail(StreamError.untrusted(error)) }
          cause = null
          kind = AsyncStep.Pending
          Async.foldStep(callback)(this)
          if (kind == AsyncStep.Success) acc = successValue
          else if (kind == AsyncStep.Pending)
            return callback
              .mapError(StreamError.callbackFailure)
              .flatMap { next => acc = next; this }
          else
            return cause match {
              case error: StreamError => Async.fail(StreamError.untrusted(error))
              case _                  => Async.fail(cause)
            }
          budget -= 1
          if (budget == 0) {
            wake = onComplete
            Async.schedule(this, forceMacrotask = true)
            return this
          }
          count = reader.readLongsPhysical(one, 0, 1)
        }
        Async.succeed(acc)
      } catch {
        case error: StreamError if error.isTrusted && protectReaderFailures => Async.fail(new ReaderFailure(error))
        case error: StreamError if error.isTrusted                          => Async.failTrusted(error)
        case failure: Throwable                                             => Async.fail(failure)
      }
    }

    def run(): Unit = {
      val callback = wake
      wake = null
      callback.run()
    }

    def success(result: Long): Unit                       = { successValue = result; kind = AsyncStep.Success }
    override def trustedFailure(failure: Throwable): Unit = { cause = failure; kind = AsyncStep.Failure }
  }

  private final class FilteredNativeAsyncIntLongFold(
    reader: Reader.AsyncReader[Int],
    predicate: Int => Async[Boolean],
    private var acc: Long,
    fold: (Long, Int) => Async[Long]
  ) extends Pollable[Long]
      with Runnable
      with Async.LongStepFold[Unit]
      with Async.StepFold[Boolean, Unit] {
    private var cause: Throwable        = null
    private var kind: Int               = AsyncStep.Pending
    private var longValue: Long         = 0L
    private var predicateValue: Boolean = false
    private var trusted: Boolean        = false
    private var wake: Runnable          = null

    def failure(failure: Throwable): Unit           = failed(failure, isTrusted = false)
    def failureLong(failure: Throwable): Unit       = failed(failure, isTrusted = false)
    def pending(pollable: Pollable[Boolean]): Unit  = { val _ = pollable; kind = AsyncStep.Pending }
    def pendingLong(pollable: Pollable[Long]): Unit = { val _ = pollable; kind = AsyncStep.Pending }

    def poll(onComplete: Runnable): Async[Long] = {
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        resetStep()
        Async.foldLongStep(read)(this)
        if (kind == AsyncStep.Pending)
          return read.flatMap { value =>
            if (value == Long.MinValue) Async.succeed(acc)
            else
              StreamError.callbackAsync(predicate(value.toInt)).flatMap { accepted =>
                if (!accepted) this
                else StreamError.callbackAsync(fold(acc, value.toInt)).flatMap { next => acc = next; this }
              }
          }
        else if (kind == AsyncStep.Failure)
          return if (trusted) Async.failTrusted(cause) else Async.fail(cause)
        else if (longValue == Long.MinValue) return Async.succeed(acc)

        val value    = longValue.toInt
        val accepted = StreamError.callbackAsync(predicate(value))
        resetStep()
        Async.foldStep(accepted)(this)
        if (kind == AsyncStep.Pending)
          return accepted.flatMap { keep =>
            if (!keep) this
            else StreamError.callbackAsync(fold(acc, value)).flatMap { next => acc = next; this }
          }
        else if (kind == AsyncStep.Failure) return Async.fail(cause)

        if (predicateValue) {
          val reduced = StreamError.callbackAsync(fold(acc, value))
          resetStep()
          Async.foldLongStep(reduced)(this)
          if (kind == AsyncStep.Success) acc = longValue
          else if (kind == AsyncStep.Pending) return reduced.flatMap { next => acc = next; this }
          else return Async.fail(cause)
        }
        budget -= 1
      }
      wake = onComplete
      Async.schedule(this, forceMacrotask = true)
      this
    }

    def run(): Unit = {
      val callback = wake
      wake = null
      callback.run()
    }

    def success(result: Boolean): Unit                        = { predicateValue = result; kind = AsyncStep.Success }
    def successLong(result: Long): Unit                       = { longValue = result; kind = AsyncStep.Success }
    override def trustedFailureLong(failure: Throwable): Unit = failed(failure, isTrusted = true)

    private def failed(failure: Throwable, isTrusted: Boolean): Unit = {
      cause = failure
      kind = AsyncStep.Failure
      trusted = isTrusted
    }

    private def resetStep(): Unit = {
      cause = null
      kind = AsyncStep.Pending
      trusted = false
    }
  }

  private final class NativeAsyncIntCollect(reader: Reader.AsyncReader[Int])
      extends Pollable[Chunk[Int]]
      with Runnable
      with Async.StepFold[Chunk[Int], Unit] {
    private var builder: ChunkBuilder.Int = null
    private var cause: Throwable          = null
    private var chunks: Chunk[Int]        = Chunk.empty
    private var kind: Int                 = AsyncStep.Pending
    private var successValue: Chunk[Int]  = null
    private var trusted: Boolean          = false
    private var wake: Runnable            = null

    def failure(failure: Throwable): Unit = failed(failure, isTrusted = false)

    def pending(pollable: Pollable[Chunk[Int]]): Unit = { val _ = pollable; kind = AsyncStep.Pending }

    def poll(onComplete: Runnable): Async[Chunk[Int]] = {
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readUpToN[Int](budget)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        resetStep()
        Async.foldStep(read)(this)
        if (kind == AsyncStep.Pending)
          return read.flatMap { values =>
            if (values.isEmpty) Async.succeed(result())
            else { append(values); this }
          }
        else if (kind == AsyncStep.Failure)
          return if (trusted) Async.failTrusted(cause) else Async.fail(cause)
        else if (successValue.isEmpty) return Async.succeed(result())
        else {
          append(successValue)
          budget -= successValue.length
        }
      }
      wake = onComplete
      Async.schedule(this, forceMacrotask = true)
      this
    }

    def run(): Unit = {
      val callback = wake
      wake = null
      callback.run()
    }

    def success(result: Chunk[Int]): Unit                 = { successValue = result; kind = AsyncStep.Success }
    override def trustedFailure(failure: Throwable): Unit = failed(failure, isTrusted = true)

    private def append(values: Chunk[Int]): Unit =
      if (chunks.isEmpty) chunks = values
      else {
        if (builder eq null) {
          builder = new ChunkBuilder.Int()
          builder.sizeHint(chunks.length + values.length)
          builder.addAll(chunks)
        }
        builder.addAll(values)
      }

    private def failed(failure: Throwable, isTrusted: Boolean): Unit = {
      cause = failure
      kind = AsyncStep.Failure
      trusted = isTrusted
    }

    private def resetStep(): Unit = {
      cause = null
      kind = AsyncStep.Pending
      successValue = null
      trusted = false
    }

    private def result(): Chunk[Int] = if (builder eq null) chunks else builder.result()
  }

  private final class NativeAsyncIntDrain(reader: Reader.AsyncReader[Int])
      extends Pollable[Unit]
      with Runnable
      with Async.LongStepFold[Unit] {
    private var cause: Throwable   = null
    private var kind: Int          = AsyncStep.Pending
    private var successValue: Long = 0L
    private var trusted: Boolean   = false
    private var wake: Runnable     = null

    def failureLong(failure: Throwable): Unit = failed(failure, isTrusted = false)

    def pendingLong(pollable: Pollable[Long]): Unit = { val _ = pollable; kind = AsyncStep.Pending }

    def poll(onComplete: Runnable): Async[Unit] = {
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        resetStep()
        Async.foldLongStep(read)(this)
        if (kind == AsyncStep.Pending)
          return read.flatMap(value => if (value == Long.MinValue) Async.succeed(()) else this)
        else if (kind == AsyncStep.Failure)
          return if (trusted) Async.failTrusted(cause) else Async.fail(cause)
        else if (successValue == Long.MinValue) return Async.succeed(())
        budget -= 1
      }
      wake = onComplete
      Async.schedule(this, forceMacrotask = true)
      this
    }

    def run(): Unit = {
      val callback = wake
      wake = null
      callback.run()
    }

    def successLong(result: Long): Unit                       = { successValue = result; kind = AsyncStep.Success }
    override def trustedFailureLong(failure: Throwable): Unit = failed(failure, isTrusted = true)

    private def failed(failure: Throwable, isTrusted: Boolean): Unit = {
      cause = failure
      kind = AsyncStep.Failure
      trusted = isTrusted
    }

    private def resetStep(): Unit = {
      cause = null
      kind = AsyncStep.Pending
      trusted = false
    }
  }

  private final class NativeAsyncIntForeach(reader: Reader.AsyncReader[Int], f: Int => Async[Unit])
      extends Pollable[Unit]
      with Runnable
      with Async.LongStepFold[Unit]
      with Async.StepFold[Unit, Unit] {
    private var cause: Throwable = null
    private var kind: Int        = AsyncStep.Pending
    private var longValue: Long  = 0L
    private var trusted: Boolean = false
    private var wake: Runnable   = null

    def failure(failure: Throwable): Unit           = failed(failure, isTrusted = false)
    def failureLong(failure: Throwable): Unit       = failed(failure, isTrusted = false)
    def pending(pollable: Pollable[Unit]): Unit     = { val _ = pollable; kind = AsyncStep.Pending }
    def pendingLong(pollable: Pollable[Long]): Unit = { val _ = pollable; kind = AsyncStep.Pending }

    def poll(onComplete: Runnable): Async[Unit] = {
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        resetStep()
        Async.foldLongStep(read)(this)
        if (kind == AsyncStep.Pending)
          return read.flatMap { value =>
            if (value == Long.MinValue) Async.succeed(())
            else StreamError.callbackAsync(f(value.toInt)).flatMap(_ => this)
          }
        else if (kind == AsyncStep.Failure)
          return if (trusted) Async.failTrusted(cause) else Async.fail(cause)
        else if (longValue == Long.MinValue) return Async.succeed(())

        val callback = StreamError.callbackAsync(f(longValue.toInt))
        resetStep()
        Async.foldStep(callback)(this)
        if (kind == AsyncStep.Pending) return callback.flatMap(_ => this)
        else if (kind == AsyncStep.Failure) return Async.fail(cause)
        budget -= 1
      }
      wake = onComplete
      Async.schedule(this, forceMacrotask = true)
      this
    }

    def run(): Unit = {
      val callback = wake
      wake = null
      callback.run()
    }

    def success(result: Unit): Unit                           = { val _ = result; kind = AsyncStep.Success }
    def successLong(result: Long): Unit                       = { longValue = result; kind = AsyncStep.Success }
    override def trustedFailureLong(failure: Throwable): Unit = failed(failure, isTrusted = true)

    private def failed(failure: Throwable, isTrusted: Boolean): Unit = {
      cause = failure
      kind = AsyncStep.Failure
      trusted = isTrusted
    }

    private def resetStep(): Unit = {
      cause = null
      kind = AsyncStep.Pending
      trusted = false
    }
  }

  private final class NativeAsyncIntLongFold(
    reader: Reader.AsyncReader[Int],
    private var acc: Long,
    f: (Long, Int) => Async[Long]
  ) extends Pollable[Long]
      with Runnable
      with Async.LongStepFold[Unit] {
    private var cause: Throwable   = null
    private var kind: Int          = AsyncStep.Pending
    private var successValue: Long = 0L
    private var trusted: Boolean   = false
    private var wake: Runnable     = null

    def failureLong(failure: Throwable): Unit = {
      cause = failure
      kind = AsyncStep.Failure
      trusted = false
    }

    def pendingLong(pollable: Pollable[Long]): Unit = { val _ = pollable; kind = AsyncStep.Pending }

    def poll(onComplete: Runnable): Async[Long] = {
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        resetStep()
        Async.foldLongStep(read)(this)
        if (kind == AsyncStep.Pending)
          return read.flatMap { value =>
            if (value == Long.MinValue) Async.succeed(acc)
            else StreamError.callbackAsync(f(acc, value.toInt)).flatMap { next => acc = next; this }
          }
        else if (kind == AsyncStep.Failure)
          return if (trusted) Async.failTrusted(cause) else Async.fail(cause)
        else if (successValue == Long.MinValue) return Async.succeed(acc)

        val callback = StreamError.callbackAsync(f(acc, successValue.toInt))
        resetStep()
        Async.foldLongStep(callback)(this)
        if (kind == AsyncStep.Success) acc = successValue
        else if (kind == AsyncStep.Pending)
          return callback.flatMap { next => acc = next; this }
        else return Async.fail(cause)
        budget -= 1
      }
      wake = onComplete
      Async.schedule(this, forceMacrotask = true)
      this
    }

    def run(): Unit = {
      val callback = wake
      wake = null
      callback.run()
    }

    def successLong(result: Long): Unit = { successValue = result; kind = AsyncStep.Success }

    override def trustedFailureLong(failure: Throwable): Unit = {
      cause = failure
      kind = AsyncStep.Failure
      trusted = true
    }

    private def resetStep(): Unit = {
      cause = null
      kind = AsyncStep.Pending
      trusted = false
    }
  }

  private final class NativeAsyncIntIntFold(
    reader: Reader.AsyncReader[Int],
    private var acc: Int,
    f: (Int, Int) => Async[Int]
  ) extends Pollable[Int]
      with Runnable
      with Async.IntStepFold[Unit]
      with Async.LongStepFold[Unit] {
    private var cause: Throwable = null
    private var intValue: Int    = 0
    private var kind: Int        = AsyncStep.Pending
    private var longValue: Long  = 0L
    private var trusted: Boolean = false
    private var wake: Runnable   = null

    def failureInt(failure: Throwable): Unit  = failed(failure, isTrusted = false)
    def failureLong(failure: Throwable): Unit = failed(failure, isTrusted = false)

    def pendingInt(pollable: Pollable[Int]): Unit   = { val _ = pollable; kind = AsyncStep.Pending }
    def pendingLong(pollable: Pollable[Long]): Unit = { val _ = pollable; kind = AsyncStep.Pending }

    def poll(onComplete: Runnable): Async[Int] = {
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        resetStep()
        Async.foldLongStep(read)(this)
        if (kind == AsyncStep.Pending)
          return read.flatMap { value =>
            if (value == Long.MinValue) Async.succeed(acc)
            else StreamError.callbackAsync(f(acc, value.toInt)).flatMap { next => acc = next; this }
          }
        else if (kind == AsyncStep.Failure)
          return if (trusted) Async.failTrusted(cause) else Async.fail(cause)
        else if (longValue == Long.MinValue) return Async.succeed(acc)

        val callback = StreamError.callbackAsync(f(acc, longValue.toInt))
        resetStep()
        Async.foldIntStep(callback)(this)
        if (kind == AsyncStep.Success) acc = intValue
        else if (kind == AsyncStep.Pending)
          return callback.flatMap { next => acc = next; this }
        else return Async.fail(cause)
        budget -= 1
      }
      wake = onComplete
      Async.schedule(this, forceMacrotask = true)
      this
    }

    def run(): Unit = {
      val callback = wake
      wake = null
      callback.run()
    }

    def successInt(result: Int): Unit   = { intValue = result; kind = AsyncStep.Success }
    def successLong(result: Long): Unit = { longValue = result; kind = AsyncStep.Success }

    override def trustedFailureLong(failure: Throwable): Unit = failed(failure, isTrusted = true)

    private def failed(failure: Throwable, isTrusted: Boolean): Unit = {
      cause = failure
      kind = AsyncStep.Failure
      trusted = isTrusted
    }

    private def resetStep(): Unit = {
      cause = null
      kind = AsyncStep.Pending
      trusted = false
    }
  }

  private final class NativeAsyncIntFloatFold(
    reader: Reader.AsyncReader[Int],
    private var acc: Float,
    f: (Float, Int) => Async[Float]
  ) extends Pollable[Float]
      with Runnable
      with Async.FloatStepFold[Unit]
      with Async.LongStepFold[Unit] {
    private var cause: Throwable  = null
    private var floatValue: Float = 0.0f
    private var kind: Int         = AsyncStep.Pending
    private var longValue: Long   = 0L
    private var trusted: Boolean  = false
    private var wake: Runnable    = null

    def failureFloat(failure: Throwable): Unit = failed(failure, isTrusted = false)
    def failureLong(failure: Throwable): Unit  = failed(failure, isTrusted = false)

    def pendingFloat(pollable: Pollable[Float]): Unit = { val _ = pollable; kind = AsyncStep.Pending }
    def pendingLong(pollable: Pollable[Long]): Unit   = { val _ = pollable; kind = AsyncStep.Pending }

    def poll(onComplete: Runnable): Async[Float] = {
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        resetStep()
        Async.foldLongStep(read)(this)
        if (kind == AsyncStep.Pending)
          return read.flatMap { value =>
            if (value == Long.MinValue) Async.succeed(acc)
            else StreamError.callbackAsync(f(acc, value.toInt)).flatMap { next => acc = next; this }
          }
        else if (kind == AsyncStep.Failure)
          return if (trusted) Async.failTrusted(cause) else Async.fail(cause)
        else if (longValue == Long.MinValue) return Async.succeed(acc)

        val callback = StreamError.callbackAsync(f(acc, longValue.toInt))
        resetStep()
        Async.foldFloatStep(callback)(this)
        if (kind == AsyncStep.Success) acc = floatValue
        else if (kind == AsyncStep.Pending)
          return callback.flatMap { next => acc = next; this }
        else return Async.fail(cause)
        budget -= 1
      }
      wake = onComplete
      Async.schedule(this, forceMacrotask = true)
      this
    }

    def run(): Unit = {
      val callback = wake
      wake = null
      callback.run()
    }

    def successFloat(result: Float): Unit = { floatValue = result; kind = AsyncStep.Success }
    def successLong(result: Long): Unit   = { longValue = result; kind = AsyncStep.Success }

    override def trustedFailureLong(failure: Throwable): Unit = failed(failure, isTrusted = true)

    private def failed(failure: Throwable, isTrusted: Boolean): Unit = {
      cause = failure
      kind = AsyncStep.Failure
      trusted = isTrusted
    }

    private def resetStep(): Unit = {
      cause = null
      kind = AsyncStep.Pending
      trusted = false
    }
  }

  private final class NativeAsyncIntDoubleFold(
    reader: Reader.AsyncReader[Int],
    private var acc: Double,
    f: (Double, Int) => Async[Double]
  ) extends Pollable[Double]
      with Runnable
      with Async.DoubleStepFold[Unit]
      with Async.LongStepFold[Unit] {
    private var cause: Throwable    = null
    private var doubleValue: Double = 0.0d
    private var kind: Int           = AsyncStep.Pending
    private var longValue: Long     = 0L
    private var trusted: Boolean    = false
    private var wake: Runnable      = null

    def failureDouble(failure: Throwable): Unit = failed(failure, isTrusted = false)
    def failureLong(failure: Throwable): Unit   = failed(failure, isTrusted = false)

    def pendingDouble(pollable: Pollable[Double]): Unit = { val _ = pollable; kind = AsyncStep.Pending }
    def pendingLong(pollable: Pollable[Long]): Unit     = { val _ = pollable; kind = AsyncStep.Pending }

    def poll(onComplete: Runnable): Async[Double] = {
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        resetStep()
        Async.foldLongStep(read)(this)
        if (kind == AsyncStep.Pending)
          return read.flatMap { value =>
            if (value == Long.MinValue) Async.succeed(acc)
            else StreamError.callbackAsync(f(acc, value.toInt)).flatMap { next => acc = next; this }
          }
        else if (kind == AsyncStep.Failure)
          return if (trusted) Async.failTrusted(cause) else Async.fail(cause)
        else if (longValue == Long.MinValue) return Async.succeed(acc)

        val callback = StreamError.callbackAsync(f(acc, longValue.toInt))
        resetStep()
        Async.foldDoubleStep(callback)(this)
        if (kind == AsyncStep.Success) acc = doubleValue
        else if (kind == AsyncStep.Pending)
          return callback.flatMap { next => acc = next; this }
        else return Async.fail(cause)
        budget -= 1
      }
      wake = onComplete
      Async.schedule(this, forceMacrotask = true)
      this
    }

    def run(): Unit = {
      val callback = wake
      wake = null
      callback.run()
    }

    def successDouble(result: Double): Unit = { doubleValue = result; kind = AsyncStep.Success }
    def successLong(result: Long): Unit     = { longValue = result; kind = AsyncStep.Success }

    override def trustedFailureLong(failure: Throwable): Unit = failed(failure, isTrusted = true)

    private def failed(failure: Throwable, isTrusted: Boolean): Unit = {
      cause = failure
      kind = AsyncStep.Failure
      trusted = isTrusted
    }

    private def resetStep(): Unit = {
      cause = null
      kind = AsyncStep.Pending
      trusted = false
    }
  }

  private final class TakeDropNativeAsyncIntLongFold(
    reader: Reader.AsyncReader[Int],
    private var skipRemaining: Long,
    private var takeRemaining: Long,
    private var acc: Long,
    f: (Long, Int) => Async[Long]
  ) extends Pollable[Long]
      with Runnable
      with Async.StepFold[Boolean, Unit]
      with Async.LongStepFold[Unit] {
    private var booleanValue: Boolean  = false
    private var cause: Throwable       = null
    private var kind: Int              = AsyncStep.Pending
    private var skipAttempted: Boolean = false
    private var successValue: Long     = 0L
    private var trusted: Boolean       = false
    private var wake: Runnable         = null

    def failure(failure: Throwable): Unit           = failed(failure, isTrusted = false)
    def failureLong(failure: Throwable): Unit       = failed(failure, isTrusted = false)
    def pending(pollable: Pollable[Boolean]): Unit  = { val _ = pollable; kind = AsyncStep.Pending }
    def pendingLong(pollable: Pollable[Long]): Unit = { val _ = pollable; kind = AsyncStep.Pending }

    def poll(onComplete: Runnable): Async[Long] = {
      if (takeRemaining <= 0L) return Async.succeed(acc)
      if (!skipAttempted) {
        skipAttempted = true
        if (skipRemaining > 0L) {
          val configured =
            try reader.setSkip(skipRemaining)
            catch {
              case error: StreamError if error.isTrusted => return Async.failTrusted(error)
              case failure: Throwable                    => return Async.fail(failure)
            }
          resetStep()
          Async.foldStep(configured)(this)
          if (kind == AsyncStep.Pending)
            return configured.flatMap { accepted =>
              if (accepted) skipRemaining = 0L
              this
            }
          else if (kind == AsyncStep.Failure)
            return if (trusted) Async.failTrusted(cause) else Async.fail(cause)
          else if (booleanValue) skipRemaining = 0L
        }
      }

      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        resetStep()
        Async.foldLongStep(read)(this)
        if (kind == AsyncStep.Pending)
          return read.flatMap { value =>
            if (value == Long.MinValue) Async.succeed(acc)
            else if (skipRemaining > 0L) { skipRemaining -= 1L; this }
            else {
              takeRemaining -= 1L
              StreamError.callbackAsync(f(acc, value.toInt)).flatMap { next => acc = next; this }
            }
          }
        else if (kind == AsyncStep.Failure)
          return if (trusted) Async.failTrusted(cause) else Async.fail(cause)
        else if (successValue == Long.MinValue) return Async.succeed(acc)

        if (skipRemaining > 0L) skipRemaining -= 1L
        else {
          takeRemaining -= 1L
          val callback = StreamError.callbackAsync(f(acc, successValue.toInt))
          resetStep()
          Async.foldLongStep(callback)(this)
          if (kind == AsyncStep.Success) acc = successValue
          else if (kind == AsyncStep.Pending)
            return callback.flatMap { next => acc = next; this }
          else return Async.fail(cause)
        }
        budget -= 1
        if (skipRemaining <= 0L && takeRemaining <= 0L) return Async.succeed(acc)
      }
      wake = onComplete
      Async.schedule(this, forceMacrotask = true)
      this
    }

    def run(): Unit = {
      val callback = wake
      wake = null
      callback.run()
    }

    def success(result: Boolean): Unit                        = { booleanValue = result; kind = AsyncStep.Success }
    def successLong(result: Long): Unit                       = { successValue = result; kind = AsyncStep.Success }
    override def trustedFailure(failure: Throwable): Unit     = failed(failure, isTrusted = true)
    override def trustedFailureLong(failure: Throwable): Unit = failed(failure, isTrusted = true)

    private def failed(failure: Throwable, isTrusted: Boolean): Unit = {
      cause = failure
      kind = AsyncStep.Failure
      trusted = isTrusted
    }

    private def resetStep(): Unit = {
      cause = null
      kind = AsyncStep.Pending
      trusted = false
    }
  }

  private final class TakenFilteredNativeAsyncIntCollect(
    reader: Reader.AsyncReader[Int],
    private var remaining: Long,
    predicate: Int => Async[Boolean]
  ) extends Pollable[Chunk[Int]]
      with Runnable
      with Async.LongStepFold[Unit]
      with Async.StepFold[Boolean, Unit] {
    private val builder           = new ChunkBuilder.Int()
    private var accepted: Boolean = false
    private var cause: Throwable  = null
    private var kind: Int         = AsyncStep.Pending
    private var longValue: Long   = 0L
    private var trusted: Boolean  = false
    private var wake: Runnable    = null

    def failure(failure: Throwable): Unit           = failed(failure, isTrusted = false)
    def failureLong(failure: Throwable): Unit       = failed(failure, isTrusted = false)
    def pending(pollable: Pollable[Boolean]): Unit  = { val _ = pollable; kind = AsyncStep.Pending }
    def pendingLong(pollable: Pollable[Long]): Unit = { val _ = pollable; kind = AsyncStep.Pending }

    def poll(onComplete: Runnable): Async[Chunk[Int]] = {
      if (remaining <= 0L) return Async.succeed(builder.result())
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        resetStep()
        Async.foldLongStep(read)(this)
        if (kind == AsyncStep.Pending)
          return read.flatMap { value =>
            if (value == Long.MinValue) Async.succeed(builder.result())
            else
              StreamError.callbackAsync(predicate(value.toInt)).flatMap { keep =>
                if (keep) { builder.addOne(value.toInt); remaining -= 1L }
                this
              }
          }
        else if (kind == AsyncStep.Failure)
          return if (trusted) Async.failTrusted(cause) else Async.fail(cause)
        else if (longValue == Long.MinValue) return Async.succeed(builder.result())

        val value = longValue.toInt
        val keep  = StreamError.callbackAsync(predicate(value))
        resetStep()
        Async.foldStep(keep)(this)
        if (kind == AsyncStep.Pending)
          return keep.flatMap { result =>
            if (result) { builder.addOne(value); remaining -= 1L }
            this
          }
        else if (kind == AsyncStep.Failure) return Async.fail(cause)
        else if (accepted) { builder.addOne(value); remaining -= 1L }

        budget -= 1
        if (remaining <= 0L) return Async.succeed(builder.result())
      }
      wake = onComplete
      Async.schedule(this, forceMacrotask = true)
      this
    }

    def run(): Unit = {
      val callback = wake
      wake = null
      callback.run()
    }

    def success(result: Boolean): Unit                        = { accepted = result; kind = AsyncStep.Success }
    def successLong(result: Long): Unit                       = { longValue = result; kind = AsyncStep.Success }
    override def trustedFailureLong(failure: Throwable): Unit = failed(failure, isTrusted = true)

    private def failed(failure: Throwable, isTrusted: Boolean): Unit = {
      cause = failure
      kind = AsyncStep.Failure
      trusted = isTrusted
    }

    private def resetStep(): Unit = {
      cause = null
      kind = AsyncStep.Pending
      trusted = false
    }
  }

  private final class TakenNativeAsyncIntFold[Z](
    reader: Reader.AsyncReader[Int],
    private var remaining: Long,
    private var acc: Z,
    f: (Z, Int) => Async[Z]
  ) extends Pollable[Z]
      with Runnable
      with Async.LongStepFold[Unit]
      with Async.StepFold[Z, Unit] {
    private var cause: Throwable = null
    private var kind: Int        = AsyncStep.Pending
    private var longValue: Long  = 0L
    private var trusted: Boolean = false
    private var value: Z         = null.asInstanceOf[Z]
    private var wake: Runnable   = null

    def failure(failure: Throwable): Unit           = failed(failure, isTrusted = false)
    def failureLong(failure: Throwable): Unit       = failed(failure, isTrusted = false)
    def pending(pollable: Pollable[Z]): Unit        = { val _ = pollable; kind = AsyncStep.Pending }
    def pendingLong(pollable: Pollable[Long]): Unit = { val _ = pollable; kind = AsyncStep.Pending }

    def poll(onComplete: Runnable): Async[Z] = {
      var budget = 1024
      while (budget > 0) {
        if (remaining <= 0L) return Async.succeed(acc)
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        resetStep()
        Async.foldLongStep(read)(this)
        if (kind == AsyncStep.Pending)
          return read.flatMap { next =>
            if (next == Long.MinValue) Async.succeed(acc)
            else {
              remaining -= 1L
              StreamError.callbackAsync(f(acc, next.toInt)).flatMap { result => acc = result; this }
            }
          }
        else if (kind == AsyncStep.Failure)
          return if (trusted) Async.failTrusted(cause) else Async.fail(cause)
        else if (longValue == Long.MinValue) return Async.succeed(acc)

        remaining -= 1L
        val callback = StreamError.callbackAsync(f(acc, longValue.toInt))
        resetStep()
        Async.foldStep(callback)(this)
        if (kind == AsyncStep.Success) acc = value
        else if (kind == AsyncStep.Pending) return callback.flatMap { result => acc = result; this }
        else return Async.fail(cause)
        budget -= 1
      }
      wake = onComplete
      Async.schedule(this, forceMacrotask = true)
      this
    }

    def run(): Unit = {
      val callback = wake
      wake = null
      callback.run()
    }

    def success(result: Z): Unit                              = { value = result; kind = AsyncStep.Success }
    def successLong(result: Long): Unit                       = { longValue = result; kind = AsyncStep.Success }
    override def trustedFailureLong(failure: Throwable): Unit = failed(failure, isTrusted = true)

    private def failed(failure: Throwable, isTrusted: Boolean): Unit = {
      cause = failure
      kind = AsyncStep.Failure
      trusted = isTrusted
    }

    private def resetStep(): Unit = {
      cause = null
      kind = AsyncStep.Pending
      trusted = false
      value = null.asInstanceOf[Z]
    }
  }

  private final class TakenNativeAsyncIntLongFold(
    reader: Reader.AsyncReader[Int],
    private var remaining: Long,
    private var acc: Long,
    f: (Long, Int) => Async[Long]
  ) extends Pollable[Long]
      with Runnable
      with Async.LongStepFold[Unit] {
    private var cause: Throwable   = null
    private var kind: Int          = AsyncStep.Pending
    private var successValue: Long = 0L
    private var trusted: Boolean   = false
    private var wake: Runnable     = null

    def failureLong(failure: Throwable): Unit = {
      cause = failure
      kind = AsyncStep.Failure
      trusted = false
    }

    def pendingLong(pollable: Pollable[Long]): Unit = { val _ = pollable; kind = AsyncStep.Pending }

    def poll(onComplete: Runnable): Async[Long] = {
      var budget = 1024
      while (budget > 0) {
        if (remaining <= 0L) return Async.succeed(acc)
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        resetStep()
        Async.foldLongStep(read)(this)
        if (kind == AsyncStep.Pending)
          return read.flatMap { value =>
            if (value == Long.MinValue) Async.succeed(acc)
            else {
              remaining -= 1L
              StreamError.callbackAsync(f(acc, value.toInt)).flatMap { next => acc = next; this }
            }
          }
        else if (kind == AsyncStep.Failure)
          return if (trusted) Async.failTrusted(cause) else Async.fail(cause)
        else if (successValue == Long.MinValue) return Async.succeed(acc)

        remaining -= 1L
        val callback = StreamError.callbackAsync(f(acc, successValue.toInt))
        resetStep()
        Async.foldLongStep(callback)(this)
        if (kind == AsyncStep.Success) acc = successValue
        else if (kind == AsyncStep.Pending)
          return callback.flatMap { next => acc = next; this }
        else return Async.fail(cause)
        budget -= 1
      }
      wake = onComplete
      Async.schedule(this, forceMacrotask = true)
      this
    }

    def run(): Unit = {
      val callback = wake
      wake = null
      callback.run()
    }

    def successLong(result: Long): Unit = { successValue = result; kind = AsyncStep.Success }

    override def trustedFailureLong(failure: Throwable): Unit = {
      cause = failure
      kind = AsyncStep.Failure
      trusted = true
    }

    private def resetStep(): Unit = {
      cause = null
      kind = AsyncStep.Pending
      trusted = false
    }
  }

  private final class TakenWhileNativeAsyncIntLongFold(
    reader: Reader.AsyncReader[Int],
    predicate: Int => Async[Boolean],
    private var acc: Long,
    fold: (Long, Int) => Async[Long]
  ) extends Pollable[Long]
      with Runnable
      with Async.LongStepFold[Unit]
      with Async.StepFold[Boolean, Unit] {
    private var cause: Throwable        = null
    private var kind: Int               = AsyncStep.Pending
    private var longValue: Long         = 0L
    private var predicateValue: Boolean = false
    private var trusted: Boolean        = false
    private var wake: Runnable          = null

    def failure(failure: Throwable): Unit           = failed(failure, isTrusted = false)
    def failureLong(failure: Throwable): Unit       = failed(failure, isTrusted = false)
    def pending(pollable: Pollable[Boolean]): Unit  = { val _ = pollable; kind = AsyncStep.Pending }
    def pendingLong(pollable: Pollable[Long]): Unit = { val _ = pollable; kind = AsyncStep.Pending }

    def poll(onComplete: Runnable): Async[Long] = {
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        resetStep()
        Async.foldLongStep(read)(this)
        if (kind == AsyncStep.Pending)
          return read.flatMap { value =>
            if (value == Long.MinValue) Async.succeed(acc)
            else
              StreamError.callbackAsync(predicate(value.toInt)).flatMap { keep =>
                if (!keep) Async.succeed(acc)
                else StreamError.callbackAsync(fold(acc, value.toInt)).flatMap { next => acc = next; this }
              }
          }
        else if (kind == AsyncStep.Failure)
          return if (trusted) Async.failTrusted(cause) else Async.fail(cause)
        else if (longValue == Long.MinValue) return Async.succeed(acc)

        val value    = longValue.toInt
        val accepted = StreamError.callbackAsync(predicate(value))
        resetStep()
        Async.foldStep(accepted)(this)
        if (kind == AsyncStep.Pending)
          return accepted.flatMap { keep =>
            if (!keep) Async.succeed(acc)
            else StreamError.callbackAsync(fold(acc, value)).flatMap { next => acc = next; this }
          }
        else if (kind == AsyncStep.Failure) return Async.fail(cause)
        else if (!predicateValue) return Async.succeed(acc)

        val reduced = StreamError.callbackAsync(fold(acc, value))
        resetStep()
        Async.foldLongStep(reduced)(this)
        if (kind == AsyncStep.Success) acc = longValue
        else if (kind == AsyncStep.Pending) return reduced.flatMap { next => acc = next; this }
        else return Async.fail(cause)
        budget -= 1
      }
      wake = onComplete
      Async.schedule(this, forceMacrotask = true)
      this
    }

    def run(): Unit = {
      val callback = wake
      wake = null
      callback.run()
    }

    def success(result: Boolean): Unit                        = { predicateValue = result; kind = AsyncStep.Success }
    def successLong(result: Long): Unit                       = { longValue = result; kind = AsyncStep.Success }
    override def trustedFailureLong(failure: Throwable): Unit = failed(failure, isTrusted = true)

    private def failed(failure: Throwable, isTrusted: Boolean): Unit = {
      cause = failure
      kind = AsyncStep.Failure
      trusted = isTrusted
    }

    private def resetStep(): Unit = {
      cause = null
      kind = AsyncStep.Pending
      trusted = false
    }
  }

  private final class MappedNativeAsyncIntLongFold(
    reader: Reader.AsyncReader[Int],
    map: Int => Async[Int],
    private var acc: Long,
    fold: (Long, Int) => Async[Long]
  ) extends Pollable[Long]
      with Runnable
      with Async.IntStepFold[Unit]
      with Async.LongStepFold[Unit] {
    private var cause: Throwable = null
    private var intValue: Int    = 0
    private var kind: Int        = AsyncStep.Pending
    private var longValue: Long  = 0L
    private var trusted: Boolean = false
    private var wake: Runnable   = null

    def failureInt(failure: Throwable): Unit        = failed(failure, isTrusted = false)
    def failureLong(failure: Throwable): Unit       = failed(failure, isTrusted = false)
    def pendingInt(pollable: Pollable[Int]): Unit   = { val _ = pollable; kind = AsyncStep.Pending }
    def pendingLong(pollable: Pollable[Long]): Unit = { val _ = pollable; kind = AsyncStep.Pending }

    def poll(onComplete: Runnable): Async[Long] = {
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        resetStep()
        Async.foldLongStep(read)(this)
        if (kind == AsyncStep.Pending)
          return read.flatMap { value =>
            if (value == Long.MinValue) Async.succeed(acc)
            else
              StreamError
                .callbackAsync(map(value.toInt))
                .flatMap(mapped => StreamError.callbackAsync(fold(acc, mapped)).flatMap { next => acc = next; this })
          }
        else if (kind == AsyncStep.Failure)
          return if (trusted) Async.failTrusted(cause) else Async.fail(cause)
        else if (longValue == Long.MinValue) return Async.succeed(acc)

        val mapped = StreamError.callbackAsync(map(longValue.toInt))
        resetStep()
        Async.foldIntStep(mapped)(this)
        if (kind == AsyncStep.Pending)
          return mapped
            .flatMap(value => StreamError.callbackAsync(fold(acc, value)).flatMap { next => acc = next; this })
        else if (kind == AsyncStep.Failure)
          return Async.fail(cause)

        val reduced = StreamError.callbackAsync(fold(acc, intValue))
        resetStep()
        Async.foldLongStep(reduced)(this)
        if (kind == AsyncStep.Success) acc = longValue
        else if (kind == AsyncStep.Pending)
          return reduced.flatMap { next => acc = next; this }
        else return Async.fail(cause)
        budget -= 1
      }
      wake = onComplete
      Async.schedule(this, forceMacrotask = true)
      this
    }

    def run(): Unit = {
      val callback = wake
      wake = null
      callback.run()
    }

    def successInt(result: Int): Unit                         = { intValue = result; kind = AsyncStep.Success }
    def successLong(result: Long): Unit                       = { longValue = result; kind = AsyncStep.Success }
    override def trustedFailureLong(failure: Throwable): Unit = failed(failure, isTrusted = true)

    private def failed(failure: Throwable, isTrusted: Boolean): Unit = {
      cause = failure
      kind = AsyncStep.Failure
      trusted = isTrusted
    }

    private def resetStep(): Unit = {
      cause = null
      kind = AsyncStep.Pending
      trusted = false
    }
  }

  private final class MappedSyncAsyncIntLongFold(
    reader: Reader.SyncReader[Int],
    map: Int => Async[Int],
    private var acc: Long,
    fold: (Long, Int) => Async[Long]
  ) extends Pollable[Long]
      with Runnable
      with Async.IntStepFold[Unit]
      with Async.LongStepFold[Unit] {
    private var cause: Throwable = null
    private var intValue: Int    = 0
    private var kind: Int        = AsyncStep.Pending
    private var longValue: Long  = 0L
    private var wake: Runnable   = null

    def failureInt(failure: Throwable): Unit        = failed(failure)
    def failureLong(failure: Throwable): Unit       = failed(failure)
    def pendingInt(pollable: Pollable[Int]): Unit   = { val _ = pollable; kind = AsyncStep.Pending }
    def pendingLong(pollable: Pollable[Long]): Unit = { val _ = pollable; kind = AsyncStep.Pending }

    def poll(onComplete: Runnable): Async[Long] = {
      var budget = 1024
      try
        while (budget > 0) {
          val value = reader.readIntPhysical(Long.MinValue)
          if (value == Long.MinValue) return Async.succeed(acc)

          val mapped = StreamError.callbackAsync(map(value.toInt))
          resetStep()
          Async.foldIntStep(mapped)(this)
          if (kind == AsyncStep.Pending)
            return mapped
              .flatMap(value => StreamError.callbackAsync(fold(acc, value)).flatMap { next => acc = next; this })
          else if (kind == AsyncStep.Failure)
            return Async.fail(cause)

          val reduced = StreamError.callbackAsync(fold(acc, intValue))
          resetStep()
          Async.foldLongStep(reduced)(this)
          if (kind == AsyncStep.Success) acc = longValue
          else if (kind == AsyncStep.Pending) return reduced.flatMap { next => acc = next; this }
          else return Async.fail(cause)
          budget -= 1
        }
      catch {
        case error: StreamError if error.isTrusted => return Async.failTrusted(error)
        case failure: Throwable                    => return Async.fail(failure)
      }
      wake = onComplete
      Async.schedule(this, forceMacrotask = true)
      this
    }

    def run(): Unit = {
      val callback = wake
      wake = null
      callback.run()
    }

    def successInt(result: Int): Unit   = { intValue = result; kind = AsyncStep.Success }
    def successLong(result: Long): Unit = { longValue = result; kind = AsyncStep.Success }

    private def failed(failure: Throwable): Unit = {
      cause = failure
      kind = AsyncStep.Failure
    }

    private def resetStep(): Unit = {
      cause = null
      kind = AsyncStep.Pending
    }
  }

  private trait ProtectedAsyncReader[+A] { self: Reader.AsyncReader[A] =>
    def underlying: Reader.AsyncReader[A]
  }

  private[streams] def foldTransformedIntLong(
    reader: Reader.SyncReader[_],
    z: Long,
    map: Int => Int,
    predicate: Int => Boolean,
    fold: (Long, Int) => Long
  ): Long = {
    reader match {
      case range: Reader.FromRange =>
        try {
          if (predicate eq null)
            return range.foldMappedLong(z, map.asInstanceOf[AnyRef], fold.asInstanceOf[AnyRef])
          else return range.foldFilteredLong(z, predicate.asInstanceOf[AnyRef], fold.asInstanceOf[AnyRef])
        } catch { case error: StreamError => throw StreamError.untrusted(error) }
      case _ =>
    }
    var acc   = z
    val end   = Long.MinValue
    var value = reader.readIntPhysical(end)
    while (value != end) {
      val int  = value.toInt
      val keep =
        if (predicate eq null) true
        else predicate(int)
      if (keep) {
        val transformed = if (map eq null) int else map(int)
        try acc = fold(acc, transformed)
        catch { case error: StreamError => throw StreamError.untrusted(error) }
      }
      value = reader.readIntPhysical(end)
    }
    acc
  }

  private[streams] def foldMappedChainIntLong(
    reader: Reader.SyncReader[_],
    z: Long,
    maps: Array[Int => Int],
    fold: (Long, Int) => Long
  ): Long = {
    reader match {
      case range: Reader.FromRange =>
        try return range.foldMappedChainLong(z, maps.asInstanceOf[AnyRef], fold.asInstanceOf[AnyRef])
        catch { case error: StreamError => throw StreamError.untrusted(error) }
      case _ =>
    }
    var acc   = z
    val end   = Long.MinValue
    var value = reader.readIntPhysical(end)
    while (value != end) {
      var transformed = value.toInt
      var index       = 0
      while (index < maps.length) {
        try transformed = maps(index)(transformed)
        catch { case error: StreamError => throw StreamError.untrusted(error) }
        index += 1
      }
      try acc = fold(acc, transformed)
      catch { case error: StreamError => throw StreamError.untrusted(error) }
      value = reader.readIntPhysical(end)
    }
    acc
  }

  /**
   * Creates a Sink that folds Double elements into a Double accumulator with no
   * boxing overhead.
   */
  private[streams] def loopDoubleToDouble(
    zero: Double
  )(step: (Double, Double) => Double): Sink[Nothing, Double, Double] =
    new Sink[Nothing, Double, Double] {
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Double] =
        foldAsyncReader[Double, Double](reader, zero)((acc, a) => Async.succeed(step(acc, a)))
      private[streams] def drain(reader: Reader.SyncReader[_]): Double =
        foldSyncReader[Double, Double](reader, zero)(step)
    }

  /**
   * Creates a Sink that folds Float elements into a Double accumulator with no
   * boxing overhead.
   */
  private[streams] def loopFloatToDouble(zero: Double)(step: (Double, Float) => Double): Sink[Nothing, Float, Double] =
    new Sink[Nothing, Float, Double] {
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Double] =
        foldAsyncReader[Float, Double](reader, zero)((acc, a) => Async.succeed(step(acc, a)))
      private[streams] def drain(reader: Reader.SyncReader[_]): Double =
        foldSyncReader[Float, Double](reader, zero)(step)
    }

  /**
   * Creates a Sink that folds Int elements into a Long accumulator with no
   * boxing overhead.
   */
  private[streams] def loopIntToLong(zero: Long)(step: (Long, Int) => Long): Sink[Nothing, Int, Long] =
    new Sink[Nothing, Int, Long] {
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Long] =
        foldAsyncReader[Int, Long](reader, zero)((acc, a) => Async.succeed(step(acc, a)))
      private[streams] def drain(reader: Reader.SyncReader[_]): Long =
        foldSyncReader[Int, Long](reader, zero)(step)
    }

  /**
   * Creates a Sink that folds Long elements into a Long accumulator with no
   * boxing overhead.
   */
  private[streams] def loopLongToLong(zero: Long)(step: (Long, Long) => Long): Sink[Nothing, Long, Long] =
    new Sink[Nothing, Long, Long] {
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Long] =
        foldAsyncReader[Long, Long](reader, zero)((acc, a) => Async.succeed(step(acc, a)))
      private[streams] def drain(reader: Reader.SyncReader[_]): Long =
        foldSyncReader[Long, Long](reader, zero)(step)
    }

  private def mapReader[A, B](
    source: Reader.AsyncReader[_],
    outputType: JvmType,
    f: A => B
  ): Reader.AsyncReader[B] = {
    val sync = Reader.borrowedSync(source.asInstanceOf[Reader.AsyncReader[A]])
    if (sync ne null) {
      val interpreter = SyncInterpreter(sync)
      interpreter.addMap[A, B](sync.jvmType, outputType)(f)
      interpreter.seal()
      return interpreter.toAsync.asInstanceOf[Reader.AsyncReader[B]]
    }
    val fused = AsyncInterpreter.fuseMap[A, B](source, source.jvmType, outputType, f)
    if (fused ne null) fused
    else
      AsyncInterpreter
        .transform(source.asInstanceOf[Reader.AsyncReader[Any]])(
          _.addMap[A, B](source.jvmType, outputType)(f)
        )
        .toReader[B]
  }

  private[streams] def mkErrorMapped[E, E2, A, Z](self: Sink[E, A, Z], f: E => E2): Sink[E2, A, Z] =
    new ErrorMapped[E, E2, A, Z](self, f)

  private def predicateAsync[A](reader: Reader.AsyncReader[_], eof: Boolean)(f: A => Async[Boolean]): Async[Boolean] = {
    val puller                            = new AsyncPuller[A](reader)
    def loop(budget: Int): Async[Boolean] =
      puller.pull().flatMap {
        case None    => Async.succeed(eof)
        case Some(a) =>
          f(a).flatMap(ok =>
            if (ok != eof) Async.succeed(!eof)
            else if (budget > 0) loop(budget - 1)
            else yieldEffect(loop(255))
          )
      }
    loop(255)
  }

  private def pullSync[A](reader: Reader.SyncReader[_]): Option[A] = reader.jvmType match {
    case JvmType.Boolean => val v = reader.readBooleanPhysical(-1); if (v < 0) None else Some((v != 0).asInstanceOf[A])
    case JvmType.Byte    => val v = reader.readBytePhysical(); if (v < 0) None else Some(v.toByte.asInstanceOf[A])
    case JvmType.Char    => val v = reader.readCharPhysical(-1); if (v < 0) None else Some(v.toChar.asInstanceOf[A])
    case JvmType.Short   =>
      val v = reader.readShortPhysical(Int.MinValue); if (v == Int.MinValue) None else Some(v.toShort.asInstanceOf[A])
    case JvmType.Int =>
      val v = reader.readIntPhysical(Long.MinValue); if (v == Long.MinValue) None else Some(v.toInt.asInstanceOf[A])
    case JvmType.Long =>
      val one = new Array[Long](1); if (reader.readLongsPhysical(one, 0, 1) < 0) None else Some(one(0).asInstanceOf[A])
    case JvmType.Float =>
      val v = reader.readFloatPhysical(Double.MaxValue);
      if (v == Double.MaxValue) None else Some(v.toFloat.asInstanceOf[A])
    case JvmType.Double =>
      val one = new Array[Double](1);
      if (reader.readDoublesPhysical(one, 0, 1) < 0) None else Some(one(0).asInstanceOf[A])
    case _ =>
      val value = reader.read[Any](EndOfStream)
      if (value.asInstanceOf[AnyRef] eq EndOfStream) None else Some(value.asInstanceOf[A])
  }

  private[streams] def readerCallbackAsync[A, B](reader: Reader.AsyncReader[A])(
    callback: Reader.AsyncReader[A] => Async[B]
  ): Async[B] = {
    def mark[C](effect: => Async[C]): Async[C] = {
      val evaluated =
        try effect
        catch {
          case error: StreamError if error.isTrusted => return Async.fail(new ReaderFailure(error))
          case cause: Throwable                      => return Async.fail(cause)
        }
      if (evaluated.asInstanceOf[AnyRef] eq null)
        return Async.fail(new NullPointerException("reader returned null Async"))
      evaluated.mapError {
        case error: StreamError if error.isTrusted => new ReaderFailure(error)
        case cause                                 => cause
      }
    }

    // specialization-id: sink-drain-async-protected-reader
    val protectedReader = new Reader.AsyncReader[A] with ProtectedAsyncReader[A] {
      val underlying: Reader.AsyncReader[A]                                           = reader
      def close(): Async[Unit]                                                        = mark(reader.close())
      def isClosed: Async[Boolean]                                                    = mark(reader.isClosed)
      override def jvmType: JvmType                                                   = reader.jvmType
      def read[C >: A](sentinel: C): Async[C]                                         = mark(reader.read(sentinel))
      override def readAll[C >: A](): Async[Chunk[C]]                                 = mark(reader.readAll())
      override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Async[Int] = mark(
        reader.readBooleanPhysical(sentinel)
      )
      override def readByte(): Async[Int]                                                                  = mark(reader.readBytePhysical())
      override def readBytes(buf: Array[Byte], offset: Int, len: Int)(implicit ev: A <:< Byte): Async[Int] =
        mark(reader.readBytesPhysical(buf, offset, len))
      override def readChar(sentinel: Int)(implicit ev: A <:< Char): Async[Int] = mark(
        reader.readCharPhysical(sentinel)
      )
      override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Async[Double] = mark(
        reader.readDoublePhysical(sentinel)
      )
      override def readDoubles(buf: Array[Double], offset: Int, len: Int)(implicit ev: A <:< Double): Async[Int] =
        mark(reader.readDoublesPhysical(buf, offset, len))
      override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Async[Double] = mark(
        reader.readFloatPhysical(sentinel)
      )
      override def readFloats(buf: Array[Float], offset: Int, len: Int)(implicit ev: A <:< Float): Async[Int] =
        mark(reader.readFloatsPhysical(buf, offset, len))
      override def readInt(sentinel: Long)(implicit ev: A <:< Int): Async[Long]                         = mark(reader.readIntPhysical(sentinel))
      override def readInts(buf: Array[Int], offset: Int, len: Int)(implicit ev: A <:< Int): Async[Int] =
        mark(reader.readIntsPhysical(buf, offset, len))
      override def readLong(sentinel: Long)(implicit ev: A <:< Long): Async[Long] = mark(
        reader.readLongPhysical(sentinel)
      )
      override def readLongs(buf: Array[Long], offset: Int, len: Int)(implicit ev: A <:< Long): Async[Int] =
        mark(reader.readLongsPhysical(buf, offset, len))
      override def readN[C >: A](n: Int): Async[Chunk[C]]                         = mark(reader.readN(n))
      override def readShort(sentinel: Int)(implicit ev: A <:< Short): Async[Int] = mark(
        reader.readShortPhysical(sentinel)
      )
      override def readUpToN[C >: A](n: Int): Async[Chunk[C]] = mark(reader.readUpToN(n))
      def readable(): Async[Boolean]                          = mark(reader.readable())
      override def reset(): Async[Unit]                       = mark(reader.reset())
      override def setLimit(n: Long): Async[Boolean]          = mark(reader.setLimit(n))
      override def setRepeat(): Async[Boolean]                = mark(reader.setRepeat())
      override def setSkip(n: Long): Async[Boolean]           = mark(reader.setSkip(n))
      override def skip(n: Long): Async[Unit]                 = mark(reader.skip(n))
    }

    val callbackEffect =
      try callback(protectedReader)
      catch { case cause: Throwable => return StreamError.callbackAsync(Async.fail(cause)) }
    if (callbackEffect.asInstanceOf[AnyRef] eq null)
      return Async.fail(new NullPointerException("sink callback returned null Async"))
    StreamError.callbackAsync(callbackEffect).catchAll {
      case failure: ReaderFailure => Async.failTrusted(failure.error)
      case cause                  => Async.fail(cause)
    }
  }

  private[streams] def readerCallbackSync[A, B](reader: Reader.SyncReader[A])(
    callback: Reader.SyncReader[A] => B
  ): B = {
    def protect[C](body: => C): C =
      try body
      catch { case error: StreamError if error.isTrusted => throw new ReaderFailure(error) }

    // specialization-id: sink-drain-sync-protected-reader
    val protectedReader = new Reader.SyncReader[A] {
      def close(): Unit                                                        = protect(reader.close())
      def isClosed: Boolean                                                    = protect(reader.isClosed)
      override def jvmType: JvmType                                            = reader.jvmType
      def read[C >: A](sentinel: C): C                                         = protect(reader.read(sentinel))
      override def readAll[C >: A](): Chunk[C]                                 = protect(reader.readAll())
      override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Int = protect(
        reader.readBooleanPhysical(sentinel)
      )
      override def readByte(): Int                                                                  = protect(reader.readBytePhysical())
      override def readBytes(buf: Array[Byte], offset: Int, len: Int)(implicit ev: A <:< Byte): Int =
        protect(reader.readBytesPhysical(buf, offset, len))
      override def readChar(sentinel: Int)(implicit ev: A <:< Char): Int           = protect(reader.readCharPhysical(sentinel))
      override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Double = protect(
        reader.readDoublePhysical(sentinel)
      )
      override def readDoubles(buf: Array[Double], offset: Int, len: Int)(implicit ev: A <:< Double): Int =
        protect(reader.readDoublesPhysical(buf, offset, len))
      override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Double = protect(
        reader.readFloatPhysical(sentinel)
      )
      override def readFloats(buf: Array[Float], offset: Int, len: Int)(implicit ev: A <:< Float): Int =
        protect(reader.readFloatsPhysical(buf, offset, len))
      override def readInt(sentinel: Long)(implicit ev: A <:< Int): Long                         = protect(reader.readIntPhysical(sentinel))
      override def readInts(buf: Array[Int], offset: Int, len: Int)(implicit ev: A <:< Int): Int =
        protect(reader.readIntsPhysical(buf, offset, len))
      override def readLong(sentinel: Long)(implicit ev: A <:< Long): Long                          = protect(reader.readLongPhysical(sentinel))
      override def readLongs(buf: Array[Long], offset: Int, len: Int)(implicit ev: A <:< Long): Int =
        protect(reader.readLongsPhysical(buf, offset, len))
      override def readN[C >: A](n: Int): Chunk[C]                         = protect(reader.readN(n))
      override def readShort(sentinel: Int)(implicit ev: A <:< Short): Int = protect(reader.readShortPhysical(sentinel))
      override def readUpToN[C >: A](n: Int): Chunk[C]                     = protect(reader.readUpToN(n))
      override def readable(): Boolean                                     = protect(reader.readable())
      override def reset(): Unit                                           = protect(reader.reset())
      override def setLimit(n: Long): Boolean                              = protect(reader.setLimit(n))
      override def setRepeat(): Boolean                                    = protect(reader.setRepeat())
      override def setSkip(n: Long): Boolean                               = protect(reader.setSkip(n))
      override def skip(n: Long): Unit                                     = protect(reader.skip(n))
    }

    try StreamError.callback(callback(protectedReader))
    catch { case failure: ReaderFailure => throw failure.error }
  }

  /** Sink that pre-processes incoming elements with `g`. */
  private[streams] final class Contramapped[E, A, A0 <: A, A2, Z](
    self: Sink[E, A, Z],
    g: A2 => A0,
    outputType: JvmType
  ) extends Sink[E, A2, Z] {
    private val safeG: A2 => A0 = a =>
      try g(a)
      catch { case error: StreamError => throw StreamError.untrusted(error) }
    private[streams] def drain(reader: Reader.SyncReader[_]): Z = reader match {
      case p: SyncInterpreter =>
        p.addMap(p.jvmType, outputType)(safeG)
        self.drain(p)
      case r =>
        val interpreter = SyncInterpreter(r)
        interpreter.addMap(interpreter.jvmType, outputType)(safeG)
        self.drain(interpreter)
    }
    private[streams] override def drainAsync(reader: Reader.SyncReader[_]): Async[Z] = reader match {
      case p: SyncInterpreter =>
        p.addMap(p.jvmType, outputType)(safeG)
        self.drainAsync(p)
      case r =>
        val interpreter = SyncInterpreter(r)
        interpreter.addMap(interpreter.jvmType, outputType)(safeG)
        self.drainAsync(interpreter)
    }
    private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Z] =
      self.drain(mapReader[A2, A0](reader, outputType, safeG))
  }

  private[streams] def yieldEffect[A](effect: => Async[A]): Async[A] = Async.reschedule(() => effect)

  private[streams] final class ContramappedAsync[E, A, A0 <: A, A2, Z](
    self: Sink[E, A, Z],
    g: A2 => Async[A0],
    outputType: JvmType
  ) extends Sink[E, A2, Z] {
    private[streams] def drain(reader: Reader.SyncReader[_]): Z                  = blockOnJvm(drain(reader.toAsync))
    private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Z] =
      self.drain(asyncMapReader[A2, A0](reader, outputType, a => StreamError.callbackAsync(g(a))))
  }

  /** Sink that transforms the error channel with `f`. */
  private[streams] final class ErrorMapped[E, E2, A, Z](self: Sink[E, A, Z], f: E => E2) extends Sink[E2, A, Z] {
    private[streams] def drain(reader: Reader.SyncReader[_]): Z =
      try self.drain(reader)
      catch {
        case e: StreamError if e.isTrusted && e.isSinkOrigin && !e.cleanupFailed =>
          throw StreamError.mapped(e, StreamError.callback(f(e.value.asInstanceOf[E])))
      }
    private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Z] =
      self.drain(reader).catchAll {
        case e: StreamError if e.isTrusted && e.isSinkOrigin && !e.cleanupFailed =>
          Async.failTrusted(StreamError.mapped(e, StreamError.callback(f(e.value.asInstanceOf[E]))))
        case cause => Async.fail(cause)
      }
  }

  private[streams] final class ErrorMappedAsync[E, E2, A, Z](self: Sink[E, A, Z], f: E => Async[E2])
      extends Sink[E2, A, Z] {
    private[streams] def drain(reader: Reader.SyncReader[_]): Z                  = blockOnJvm(drain(reader.toAsync))
    private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Z] =
      self.drain(reader).catchAll {
        case e: StreamError if e.isTrusted && e.isSinkOrigin && !e.cleanupFailed =>
          StreamError
            .callbackAsync(f(e.value.asInstanceOf[E]))
            .flatMap(v => Async.failTrusted(StreamError.mapped(e, v)))
        case cause => Async.fail(cause)
      }
  }

  /** Specialized fold accumulating into a Double to avoid boxing. */
  private[streams] final class FoldLeftDouble[A](z: Double, f0: (Double, A) => Double)
      extends Sink[Nothing, A, Double] {
    private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Double] = {
      val fold = (acc: Double, value: A) =>
        try Async.succeed(f0(acc, value))
        catch { case error: StreamError => Async.fail(StreamError.untrusted(error)) }
      reader match {
        case terminal: AsyncInterpreter.TerminalDriver => terminal.foldAsync(z, JvmType.Double, fold)
        case _                                         => foldAsyncReader[A, Double](reader, z)(fold)
      }
    }
    private[streams] def drain(reader: Reader.SyncReader[_]): Double = {
      val et = reader.jvmType
      if (et eq JvmType.Int) foldInt(reader)
      else if (et eq JvmType.Long) foldLong(reader)
      else if (et eq JvmType.Float) foldFloat(reader)
      else if (et eq JvmType.Double) foldDouble(reader)
      else if (et eq JvmType.Byte) foldByte(reader)
      else if (et eq JvmType.Short) foldShort(reader)
      else if (et eq JvmType.Char) foldChar(reader)
      else if (et eq JvmType.Boolean) foldBoolean(reader)
      else foldGeneric(reader)
    }
    private def foldBoolean(reader: Reader.SyncReader[_]): Double = {
      val f = f0.asInstanceOf[(Double, Boolean) => Double]; var acc = z; var v = reader.readBooleanPhysical(-1)
      while (v >= 0) { acc = StreamError.callback(f(acc, v != 0)); v = reader.readBooleanPhysical(-1) }
      acc
    }
    private def foldByte(reader: Reader.SyncReader[_]): Double = {
      val fb = f0.asInstanceOf[(Double, Byte) => Double]; var acc = z; var v = reader.readBytePhysical();
      while (v >= 0) {
        try acc = fb(acc, v.toByte)
        catch { case error: StreamError => throw StreamError.untrusted(error) }
        v = reader.readBytePhysical()
      };
      acc
    }
    private def foldChar(reader: Reader.SyncReader[_]): Double = {
      val f = f0.asInstanceOf[(Double, Char) => Double]; var acc = z; var v = reader.readCharPhysical(-1)
      while (v >= 0) { acc = StreamError.callback(f(acc, v.toChar)); v = reader.readCharPhysical(-1) }
      acc
    }
    private def foldDouble(reader: Reader.SyncReader[_]): Double =
      foldSyncReader[Double, Double](reader, z) { (acc, value) =>
        try f0.asInstanceOf[(Double, Double) => Double](acc, value)
        catch { case error: StreamError => throw StreamError.untrusted(error) }
      }
    private def foldFloat(reader: Reader.SyncReader[_]): Double = {
      val ff = f0.asInstanceOf[(Double, Float) => Double]; var acc = z; val s = Double.MaxValue
      var v  = reader.readFloatPhysical(s);
      while (v != s) {
        try acc = ff(acc, v.toFloat)
        catch { case error: StreamError => throw StreamError.untrusted(error) }
        v = reader.readFloatPhysical(s)
      };
      acc
    }
    private def foldGeneric(reader: Reader.SyncReader[_]): Double = {
      var acc = z; var v = reader.read(EndOfStream)
      while (v.asInstanceOf[AnyRef] ne EndOfStream) {
        try acc = f0(acc, v.asInstanceOf[A])
        catch { case error: StreamError => throw StreamError.untrusted(error) }
        v = reader.read(EndOfStream)
      };
      acc
    }
    private def foldInt(reader: Reader.SyncReader[_]): Double = {
      val fi = f0.asInstanceOf[(Double, Int) => Double]; var acc = z; val s = Long.MinValue
      var v  = reader.readIntPhysical(s);
      while (v != s) {
        try acc = fi(acc, v.toInt)
        catch { case error: StreamError => throw StreamError.untrusted(error) }
        v = reader.readIntPhysical(s)
      };
      acc
    }
    private def foldLong(reader: Reader.SyncReader[_]): Double =
      foldSyncReader[Long, Double](reader, z) { (acc, value) =>
        try f0.asInstanceOf[(Double, Long) => Double](acc, value)
        catch { case error: StreamError => throw StreamError.untrusted(error) }
      }
    private def foldShort(reader: Reader.SyncReader[_]): Double = {
      val f = f0.asInstanceOf[(Double, Short) => Double]; var acc = z;
      var v = reader.readShortPhysical(Int.MinValue)
      while (v != Int.MinValue) {
        acc = StreamError.callback(f(acc, v.toShort)); v = reader.readShortPhysical(Int.MinValue)
      }
      acc
    }
  }

  /** Specialized fold accumulating into an Int to avoid boxing. */
  private[streams] final class FoldLeftInt[A](z: Int, f0: (Int, A) => Int) extends Sink[Nothing, A, Int] {
    private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Int] = {
      val fold = (acc: Int, value: A) =>
        try Async.succeed(f0(acc, value))
        catch { case error: StreamError => Async.fail(StreamError.untrusted(error)) }
      reader match {
        case terminal: AsyncInterpreter.TerminalDriver => terminal.foldAsync(z, JvmType.Int, fold)
        case _                                         => foldAsyncReader[A, Int](reader, z)(fold)
      }
    }
    private[streams] def drain(reader: Reader.SyncReader[_]): Int = {
      val et = reader.jvmType
      if (et eq JvmType.Int) foldInt(reader)
      else if (et eq JvmType.Long) foldLong(reader)
      else if (et eq JvmType.Float) foldFloat(reader)
      else if (et eq JvmType.Double) foldDouble(reader)
      else if (et eq JvmType.Byte) foldByte(reader)
      else if (et eq JvmType.Short) foldShort(reader)
      else if (et eq JvmType.Char) foldChar(reader)
      else if (et eq JvmType.Boolean) foldBoolean(reader)
      else foldGeneric(reader)
    }
    private def foldBoolean(reader: Reader.SyncReader[_]): Int = {
      val f = f0.asInstanceOf[(Int, Boolean) => Int]; var acc = z; var v = reader.readBooleanPhysical(-1)
      while (v >= 0) { acc = StreamError.callback(f(acc, v != 0)); v = reader.readBooleanPhysical(-1) }
      acc
    }
    private def foldByte(reader: Reader.SyncReader[_]): Int = {
      val fb = f0.asInstanceOf[(Int, Byte) => Int]; var acc = z; var v = reader.readBytePhysical();
      while (v >= 0) {
        try acc = fb(acc, v.toByte)
        catch { case error: StreamError => throw StreamError.untrusted(error) }
        v = reader.readBytePhysical()
      };
      acc
    }
    private def foldChar(reader: Reader.SyncReader[_]): Int = {
      val f = f0.asInstanceOf[(Int, Char) => Int]; var acc = z; var v = reader.readCharPhysical(-1)
      while (v >= 0) { acc = StreamError.callback(f(acc, v.toChar)); v = reader.readCharPhysical(-1) }
      acc
    }
    private def foldDouble(reader: Reader.SyncReader[_]): Int = {
      val fd = f0.asInstanceOf[(Int, Double) => Int]; var acc = z; val one = new Array[Double](1)
      var n  = reader.readDoublesPhysical(one, 0, 1);
      while (n >= 0) {
        try acc = fd(acc, one(0))
        catch { case error: StreamError => throw StreamError.untrusted(error) }
        n = reader.readDoublesPhysical(one, 0, 1)
      };
      acc
    }
    private def foldFloat(reader: Reader.SyncReader[_]): Int = {
      val ff = f0.asInstanceOf[(Int, Float) => Int]; var acc = z; val s = Double.MaxValue
      var v  = reader.readFloatPhysical(s);
      while (v != s) {
        try acc = ff(acc, v.toFloat)
        catch { case error: StreamError => throw StreamError.untrusted(error) }
        v = reader.readFloatPhysical(s)
      };
      acc
    }
    private def foldGeneric(reader: Reader.SyncReader[_]): Int = {
      var acc = z; var v = reader.read(EndOfStream)
      while (v.asInstanceOf[AnyRef] ne EndOfStream) {
        try acc = f0(acc, v.asInstanceOf[A])
        catch { case error: StreamError => throw StreamError.untrusted(error) }
        v = reader.read(EndOfStream)
      };
      acc
    }
    private def foldInt(reader: Reader.SyncReader[_]): Int = {
      val fi = f0.asInstanceOf[(Int, Int) => Int]; var acc = z; val s = Long.MinValue
      var v  = reader.readIntPhysical(s);
      while (v != s) {
        try acc = fi(acc, v.toInt)
        catch { case error: StreamError => throw StreamError.untrusted(error) }
        v = reader.readIntPhysical(s)
      };
      acc
    }
    private def foldLong(reader: Reader.SyncReader[_]): Int = {
      val fl = f0.asInstanceOf[(Int, Long) => Int]; var acc = z; val one = new Array[Long](1)
      var n  = reader.readLongsPhysical(one, 0, 1);
      while (n >= 0) {
        try acc = fl(acc, one(0))
        catch { case error: StreamError => throw StreamError.untrusted(error) }
        n = reader.readLongsPhysical(one, 0, 1)
      };
      acc
    }
    private def foldShort(reader: Reader.SyncReader[_]): Int = {
      val f = f0.asInstanceOf[(Int, Short) => Int]; var acc = z; var v = reader.readShortPhysical(Int.MinValue)
      while (v != Int.MinValue) {
        acc = StreamError.callback(f(acc, v.toShort)); v = reader.readShortPhysical(Int.MinValue)
      }
      acc
    }
  }

  /** Specialized fold accumulating into a Long to avoid boxing. */
  private[streams] final class FoldLeftAsyncLong[A](z: Long, f0: (Long, A) => Async[Long])
      extends Sink[Nothing, A, Long] {
    private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Long] = {
      val protectedReader = reader match {
        case protectedView: ProtectedAsyncReader[_] =>
          protectedView.underlying.asInstanceOf[Reader.AsyncReader[A]]
        case _ => null
      }
      val source = if (protectedReader eq null) reader.asInstanceOf[Reader.AsyncReader[A]] else protectedReader
      val sync   = Reader.borrowedSync(source)
      source match {
        case terminal: AsyncInterpreter.TerminalDriver =>
          terminal.foldAsync(z, JvmType.Long, f0)
        case mapped: Reader.MappedAsyncEffectIntInt if protectedReader eq null =>
          foldMappedNativeAsyncIntLong(
            mapped.source,
            mapped.f,
            z,
            f0.asInstanceOf[(Long, Int) => Async[Long]]
          )
        case _ if (sync ne null) && (sync.jvmType eq JvmType.Int) =>
          foldBorrowedSyncIntLongAsync(
            sync.asInstanceOf[Reader.SyncReader[Int]],
            z,
            protectedReader ne null,
            f0.asInstanceOf[(Long, Int) => Async[Long]]
          )
        case _ if (protectedReader eq null) && (source.jvmType eq JvmType.Int) =>
          foldNativeAsyncIntLong(
            source.asInstanceOf[Reader.AsyncReader[Int]],
            z,
            f0.asInstanceOf[(Long, Int) => Async[Long]]
          )
        case _ => foldAsyncReader[A, Long](reader, z)((acc, a) => StreamError.callbackAsync(f0(acc, a)))
      }
    }

    private[streams] def drain(reader: Reader.SyncReader[_]): Long =
      blockOnJvm(drain(reader.toAsync))

    private[streams] override def drainAsync(reader: Reader.SyncReader[_]): Async[Long] =
      if (reader.jvmType eq JvmType.Int)
        foldBorrowedSyncIntLongAsync(
          reader.asInstanceOf[Reader.SyncReader[Int]],
          z,
          protectReaderFailures = false,
          f0.asInstanceOf[(Long, Int) => Async[Long]]
        )
      else if (reader.jvmType eq JvmType.Long)
        foldBorrowedSyncLongLongAsync(
          reader.asInstanceOf[Reader.SyncReader[Long]],
          z,
          protectReaderFailures = false,
          f0.asInstanceOf[(Long, Long) => Async[Long]]
        )
      else drain(reader.toAsync)
  }

  /** Specialized fold accumulating into a Long to avoid boxing. */
  private[streams] final class FoldLeftLong[A](private[streams] val z: Long, private[streams] val f0: (Long, A) => Long)
      extends Sink[Nothing, A, Long] {
    private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Long] = {
      val protectedReader = reader match {
        case protectedView: ProtectedAsyncReader[_] =>
          protectedView.underlying.asInstanceOf[Reader.AsyncReader[A]]
        case _ => null
      }
      val source = if (protectedReader eq null) reader.asInstanceOf[Reader.AsyncReader[A]] else protectedReader
      val sync   = Reader.borrowedSync(source)
      val fold   = (acc: Long, value: Int) => Async.succeed(applyIntCallback(acc, value))
      source match {
        case terminal: AsyncInterpreter.TerminalDriver if source.jvmType eq JvmType.Int =>
          terminal.foldAsync(z, JvmType.Long, fold)
        case terminal: AsyncInterpreter.TerminalDriver =>
          terminal.foldAsync(
            z,
            JvmType.Long,
            (acc: Long, value: A) =>
              try Async.succeed(f0(acc, value))
              catch { case error: StreamError => Async.fail(StreamError.untrusted(error)) }
          )
        case mapped: Reader.MappedAsyncEffectIntInt if protectedReader eq null =>
          foldMappedNativeAsyncIntLong(mapped.source, mapped.f, z, fold)
        case _ if (sync ne null) && (sync.jvmType eq JvmType.Int) =>
          foldBorrowedSyncIntLongAsync(
            sync.asInstanceOf[Reader.SyncReader[Int]],
            z,
            protectedReader ne null,
            fold
          )
        case _ if (protectedReader eq null) && (source.jvmType eq JvmType.Int) =>
          foldNativeAsyncIntLong(source.asInstanceOf[Reader.AsyncReader[Int]], z, fold)
        case _ =>
          foldAsyncReader[A, Long](reader, z)((acc, a) =>
            try Async.succeed(f0(acc, a))
            catch { case error: StreamError => Async.fail(StreamError.untrusted(error)) }
          )
      }
    }
    private[streams] def drain(reader: Reader.SyncReader[_]): Long = {
      val et = reader.jvmType
      if (et eq JvmType.Int) foldInt(reader)
      else if (et eq JvmType.Long) foldLong(reader)
      else if (et eq JvmType.Float) foldFloat(reader)
      else if (et eq JvmType.Double) foldDouble(reader)
      else if (et eq JvmType.Byte) foldByte(reader)
      else if (et eq JvmType.Short) foldShort(reader)
      else if (et eq JvmType.Char) foldChar(reader)
      else if (et eq JvmType.Boolean) foldBoolean(reader)
      else foldGeneric(reader)
    }
    private def applyIntCallback(acc: Long, value: Int): Long =
      try f0.asInstanceOf[(Long, Int) => Long](acc, value)
      catch { case error: StreamError => throw StreamError.untrusted(error) }

    private def foldBoolean(reader: Reader.SyncReader[_]): Long = {
      val f = f0.asInstanceOf[(Long, Boolean) => Long]; var acc = z; var v = reader.readBooleanPhysical(-1)
      while (v >= 0) { acc = StreamError.callback(f(acc, v != 0)); v = reader.readBooleanPhysical(-1) }
      acc
    }
    private def foldByte(reader: Reader.SyncReader[_]): Long = {
      val fb       = f0.asInstanceOf[(Long, Byte) => Long]; var acc = z
      var callback = false
      try {
        var v = reader.readBytePhysical()
        while (v >= 0) {
          callback = true; acc = fb(acc, v.toByte); callback = false
          v = reader.readBytePhysical()
        }
        acc
      } catch {
        case error: StreamError if callback => throw StreamError.untrusted(error)
      }
    }
    private def foldChar(reader: Reader.SyncReader[_]): Long = {
      val f = f0.asInstanceOf[(Long, Char) => Long]; var acc = z; var v = reader.readCharPhysical(-1)
      while (v >= 0) { acc = StreamError.callback(f(acc, v.toChar)); v = reader.readCharPhysical(-1) }
      acc
    }
    private def foldDouble(reader: Reader.SyncReader[_]): Long = {
      val fd       = f0.asInstanceOf[(Long, Double) => Long]; var acc = z; val one = new Array[Double](1)
      var callback = false
      try {
        var n = reader.readDoublesPhysical(one, 0, 1)
        while (n >= 0) {
          callback = true; acc = fd(acc, one(0)); callback = false
          n = reader.readDoublesPhysical(one, 0, 1)
        }
        acc
      } catch {
        case error: StreamError if callback => throw StreamError.untrusted(error)
      }
    }
    private def foldFloat(reader: Reader.SyncReader[_]): Long = {
      val ff       = f0.asInstanceOf[(Long, Float) => Long]; var acc = z; val s = Double.MaxValue
      var callback = false
      try {
        var v = reader.readFloatPhysical(s)
        while (v != s) {
          callback = true; acc = ff(acc, v.toFloat); callback = false
          v = reader.readFloatPhysical(s)
        }
        acc
      } catch {
        case error: StreamError if callback => throw StreamError.untrusted(error)
      }
    }
    private def foldGeneric(reader: Reader.SyncReader[_]): Long = {
      var acc = z; var v = reader.read(EndOfStream)
      while (v.asInstanceOf[AnyRef] ne EndOfStream) {
        try acc = f0(acc, v.asInstanceOf[A])
        catch { case error: StreamError => throw StreamError.untrusted(error) }
        v = reader.read(EndOfStream)
      };
      acc
    }
    private def foldInt(reader: Reader.SyncReader[_]): Long = {
      reader match {
        case filtered: Reader.FilteredIntInt => return filtered.foldLong(z, f0.asInstanceOf[AnyRef])
        case mapped: Reader.MappedIntInt     => return mapped.foldLong(z, f0.asInstanceOf[AnyRef])
        case _                               =>
      }
      val intReader = reader.asInstanceOf[Reader.SyncReader[Int]]
      var acc       = z; val s = Long.MinValue
      var v         = intReader.readInt(s)
      while (v != s) { acc = applyIntCallback(acc, v.toInt); v = intReader.readInt(s) }
      acc
    }
    private def foldLong(reader: Reader.SyncReader[_]): Long = {
      val fl       = f0.asInstanceOf[(Long, Long) => Long]; var acc = z; val one = new Array[Long](1)
      var callback = false
      try {
        var n = reader.readLongsPhysical(one, 0, 1)
        while (n >= 0) {
          callback = true; acc = fl(acc, one(0)); callback = false
          n = reader.readLongsPhysical(one, 0, 1)
        }
        acc
      } catch {
        case error: StreamError if callback => throw StreamError.untrusted(error)
      }
    }
    private def foldShort(reader: Reader.SyncReader[_]): Long = {
      val f = f0.asInstanceOf[(Long, Short) => Long]; var acc = z;
      var v = reader.readShortPhysical(Int.MinValue)
      while (v != Int.MinValue) {
        acc = StreamError.callback(f(acc, v.toShort)); v = reader.readShortPhysical(Int.MinValue)
      }
      acc
    }
  }

  /** Sink that transforms the result with `f`. */
  private[streams] final class Mapped[E, A, Z, Z2](self: Sink[E, A, Z], f: Z => Z2) extends Sink[E, A, Z2] {
    private[streams] def drain(reader: Reader.SyncReader[_]): Z2 = {
      val value = self.drain(reader)
      StreamError.callback(f(value))
    }
    private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Z2] =
      self.drain(reader).map(value => StreamError.callback(f(value)))
  }

  private[streams] final class MappedAsync[E, A, Z, Z2](
    private[streams] val self: Sink[E, A, Z],
    private[streams] val f: Z => Async[Z2]
  ) extends Sink[E, A, Z2] {
    private[streams] def drain(reader: Reader.SyncReader[_]): Z2                  = blockOnJvm(drain(reader.toAsync))
    private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Z2] =
      self.drain(reader).flatMap(value => StreamError.callbackAsync(f(value)))
  }
}

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

import scala.concurrent.ExecutionContext
import scala.util.Try

import zio.ZIO
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.internal.AsyncStatefulReader
import zio.blocks.streams.io.Reader
import zio.test._

/**
 * A compact, data-oriented conformance matrix for the shared asynchronous
 * Reader boundary. Keep values here hostile to primitive EOF sentinels.
 */
object AsyncReaderConformanceSpec extends StreamsBaseSpec {
  private implicit val ec: ExecutionContext = new ExecutionContext {
    def execute(runnable: Runnable): Unit     = Async.schedule(runnable, forceMacrotask = false)
    def reportFailure(cause: Throwable): Unit = throw cause
  }

  private def run[A](effect: Async[A]): ZIO[Any, Throwable, A] = ZIO.fromFuture(_ => effect.toFuture)

  private val floatValues = Chunk(
    Float.NaN,
    Float.NegativeInfinity,
    -0.0f,
    0.0f,
    Float.PositiveInfinity,
    -Float.MaxValue,
    Float.MaxValue,
    Float.MinValue
  )
  private val doubleValues = Chunk(
    Double.NaN,
    Double.NegativeInfinity,
    -0.0d,
    0.0d,
    Double.PositiveInfinity,
    -Double.MaxValue,
    Double.MaxValue,
    Double.MinValue
  )

  private def floatBits(values: Chunk[Float]): Chunk[Int]    = values.map(java.lang.Float.floatToRawIntBits)
  private def doubleBits(values: Chunk[Double]): Chunk[Long] = values.map(java.lang.Double.doubleToRawLongBits)

  private final class AvailabilityIntReader(availability: Reader.Availability, values: Vector[Int])
      extends Reader.AsyncReader[Int] {
    private var index                                                           = 0
    var reads                                                                   = 0
    var readableCalls                                                           = 0
    override def jvmType: JvmType                                               = JvmType.Int
    def close(): Async[Unit]                                                    = Async.succeed(())
    def isClosed: Async[Boolean]                                                = Async.succeed(index >= values.length)
    def readable(): Async[Boolean]                                              = { readableCalls += 1; Async.fail(new AssertionError("readable invoked")) }
    override private[streams] def tryReadable: Reader.Availability              = availability
    def read[A >: Int](sentinel: A): Async[A]                                   = Async.fail(new AssertionError("generic primitive read invoked"))
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = {
      reads += 1
      if (index >= values.length) Async.succeed(sentinel)
      else { val value = values(index); index += 1; Async.succeed(value.toLong) }
    }
  }

  private final class AvailabilityByteReader(availability: Reader.Availability, values: Vector[Byte])
      extends Reader.AsyncReader[Byte] {
    private var index                                              = 0
    var reads                                                      = 0
    var readableCalls                                              = 0
    override def jvmType: JvmType                                  = JvmType.Byte
    def close(): Async[Unit]                                       = Async.succeed(())
    def isClosed: Async[Boolean]                                   = Async.succeed(index >= values.length)
    def readable(): Async[Boolean]                                 = { readableCalls += 1; Async.fail(new AssertionError("readable invoked")) }
    override private[streams] def tryReadable: Reader.Availability = availability
    def read[A >: Byte](sentinel: A): Async[A]                     = Async.fail(new AssertionError("generic primitive read invoked"))
    override def readByte(): Async[Int]                            = {
      reads += 1
      if (index >= values.length) Async.succeed(-1)
      else { val value = values(index); index += 1; Async.succeed(value.toInt & 0xff) }
    }
  }

  private final class BoxedAsyncReader[A](values: Vector[A]) extends Reader.AsyncReader[A] {
    private var index = 0

    def close(): Async[Unit]                = Async.succeed(())
    def isClosed: Async[Boolean]            = Async.succeed(index >= values.length)
    override def jvmType: JvmType           = JvmType.AnyRef
    def readable(): Async[Boolean]          = Async.succeed(index < values.length)
    def read[B >: A](sentinel: B): Async[B] =
      if (index >= values.length) Async.succeed(sentinel)
      else {
        val value = values(index)
        index += 1
        Async.succeed(value)
      }
    override private[streams] def tryReadable: Reader.Availability =
      if (index < values.length) Reader.Available else Reader.Unavailable
  }

  private class UnsupportedLaneReader[A](override val jvmType: JvmType) extends Reader.AsyncReader[A] {
    def close(): Async[Unit]                = Async.succeed(())
    def isClosed: Async[Boolean]            = Async.succeed(false)
    def readable(): Async[Boolean]          = Async.succeed(true)
    def read[B >: A](sentinel: B): Async[B] = Async.fail(new AssertionError(s"generic read: $sentinel"))
  }

  private final class ExactLaneProbe[A](override val jvmType: JvmType, values: Vector[A])
      extends Reader.AsyncReader[A] {
    private var index                                               = 0
    private def wrong[B](lane: String): Async[B]                    = Async.fail(new AssertionError(s"wrong $lane read for $jvmType"))
    private def next[B](lane: JvmType)(f: A => B, eof: B): Async[B] =
      if (jvmType ne lane) wrong(lane.toString)
      else if (index >= values.length) Async.succeed(eof)
      else { val value = f(values(index)); index += 1; Async.succeed(value) }
    def close(): Async[Unit]                                       = Async.succeed(())
    def isClosed: Async[Boolean]                                   = Async.succeed(index >= values.length)
    def readable(): Async[Boolean]                                 = Async.succeed(index < values.length)
    override private[streams] def tryReadable: Reader.Availability =
      if (index < values.length) Reader.Available else Reader.Unavailable
    def read[B >: A](sentinel: B): Async[B]                                         = wrong("generic")
    override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Async[Int] =
      next(JvmType.Boolean)(a => if (ev(a)) 1 else 0, sentinel)
    override def readByte(): Async[Int]                                       = next(JvmType.Byte)(a => a.asInstanceOf[Byte].toInt & 0xff, -1)
    override def readChar(sentinel: Int)(implicit ev: A <:< Char): Async[Int] =
      next(JvmType.Char)(a => ev(a).toInt, sentinel)
    override def readShort(sentinel: Int)(implicit ev: A <:< Short): Async[Int] =
      next(JvmType.Short)(a => ev(a).toInt, sentinel)
    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Async[Long] =
      next(JvmType.Int)(a => ev(a).toLong, sentinel)
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: A <:< Long): Async[Int] =
      if (jvmType ne JvmType.Long) wrong("Long")
      else if (index >= values.length) Async.succeed(-1)
      else { dest(offset) = ev(values(index)); index += 1; Async.succeed(1) }
    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Async[Double] =
      next(JvmType.Float)(a => ev(a).toDouble, sentinel)
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: A <:< Double): Async[Int] =
      if (jvmType ne JvmType.Double) wrong("Double")
      else if (index >= values.length) Async.succeed(-1)
      else { dest(offset) = ev(values(index)); index += 1; Async.succeed(1) }
  }

  def spec = suite("AsyncReader conformance")(
    test("boxed async readers execute every default primitive scalar and bulk route") {
      val booleans = new BoxedAsyncReader(Vector(false, true))
      val bytes    = new BoxedAsyncReader(Vector(Byte.MinValue, Byte.MaxValue))
      val chars    = new BoxedAsyncReader(Vector(Char.MinValue, Char.MaxValue))
      val shorts   = new BoxedAsyncReader(Vector(Short.MinValue, Short.MaxValue))
      val ints     = new BoxedAsyncReader(Vector(Int.MinValue, Int.MaxValue))
      val longs    = new BoxedAsyncReader(Vector(Long.MinValue, Long.MaxValue))
      val floats   = new BoxedAsyncReader(Vector(Float.MinValue, Float.MaxValue))
      val doubles  = new BoxedAsyncReader(Vector(Double.MinValue, Double.MaxValue))
      val longBulk = new BoxedAsyncReader(Vector(Long.MinValue, Long.MaxValue))
      val dblBulk  = new BoxedAsyncReader(Vector(Double.MinValue, Double.MaxValue))
      val longDest = Array.fill(4)(1L)
      val dblDest  = Array.fill(4)(1.0d)
      assertTrue(
        booleans.readBoolean(-1).block == 0,
        booleans.readBoolean(-1).block == 1,
        booleans.readBoolean(-1).block == -1,
        bytes.readByte().block == 128,
        bytes.readByte().block == 127,
        bytes.readByte().block == -1,
        chars.readChar(-1).block == Char.MinValue.toInt,
        chars.readChar(-1).block == Char.MaxValue.toInt,
        chars.readChar(-1).block == -1,
        shorts.readShort(Int.MinValue).block == Short.MinValue.toInt,
        shorts.readShort(Int.MinValue).block == Short.MaxValue.toInt,
        shorts.readShort(Int.MinValue).block == Int.MinValue,
        ints.readInt(Long.MinValue).block == Int.MinValue.toLong,
        ints.readInt(Long.MinValue).block == Int.MaxValue.toLong,
        ints.readInt(Long.MinValue).block == Long.MinValue,
        longs.readLong(7L).block == Long.MinValue,
        longs.readLong(7L).block == Long.MaxValue,
        longs.readLong(7L).block == 7L,
        floats.readFloat(Double.NaN).block == Float.MinValue.toDouble,
        floats.readFloat(Double.NaN).block == Float.MaxValue.toDouble,
        floats.readFloat(Double.NaN).block.isNaN,
        doubles.readDouble(Double.NaN).block == Double.MinValue,
        doubles.readDouble(Double.NaN).block == Double.MaxValue,
        doubles.readDouble(Double.NaN).block.isNaN,
        longBulk.readLongs(longDest, 1, 2).block == 2,
        longBulk.readLongs(longDest, 1, 2).block == -1,
        longDest.toVector == Vector(1L, Long.MinValue, Long.MaxValue, 1L),
        dblBulk.readDoubles(dblDest, 1, 2).block == 2,
        dblBulk.readDoubles(dblDest, 1, 2).block == -1,
        dblDest.toVector == Vector(1.0d, Double.MinValue, Double.MaxValue, 1.0d)
      )
    },
    test("specialized readers fail every unimplemented exact primitive route") {
      def failsWith[A <: Throwable: scala.reflect.ClassTag](value: => Any): Boolean =
        Try(value).failed.toOption.exists(implicitly[scala.reflect.ClassTag[A]].runtimeClass.isInstance)

      val bool   = new UnsupportedLaneReader[Boolean](JvmType.Boolean)
      val byte   = new UnsupportedLaneReader[Byte](JvmType.Byte)
      val char   = new UnsupportedLaneReader[Char](JvmType.Char)
      val short  = new UnsupportedLaneReader[Short](JvmType.Short)
      val int    = new UnsupportedLaneReader[Int](JvmType.Int)
      val long   = new UnsupportedLaneReader[Long](JvmType.Long)
      val float  = new UnsupportedLaneReader[Float](JvmType.Float)
      val double = new UnsupportedLaneReader[Double](JvmType.Double)
      assertTrue(
        failsWith[UnsupportedOperationException](bool.readBoolean(-1).block),
        failsWith[UnsupportedOperationException](byte.readByte().block),
        failsWith[UnsupportedOperationException](char.readChar(-1).block),
        failsWith[UnsupportedOperationException](short.readShort(Int.MinValue).block),
        failsWith[UnsupportedOperationException](int.readInt(Long.MinValue).block),
        failsWith[UnsupportedOperationException](long.readLong(Long.MinValue).block),
        failsWith[UnsupportedOperationException](float.readFloat(Double.NaN).block),
        failsWith[UnsupportedOperationException](double.readDouble(Double.NaN).block),
        failsWith[UnsupportedOperationException](long.readLongs(Array.ofDim[Long](1), 0, 1).block),
        failsWith[UnsupportedOperationException](double.readDoubles(Array.ofDim[Double](1), 0, 1).block),
        failsWith[IllegalStateException](int.readBytePhysical().block)
      )
    },
    test("pollInt converts a throwing poll registration into an observed failure") {
      val failure = new IllegalStateException("poll")
      val reader  = new UnsupportedLaneReader[Int](JvmType.Int) {
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = new Async.Operation[Long] {
          def poll(onComplete: Runnable): Async[Long]  = throw failure
          protected def cancelOperation(): Async[Unit] = Async.succeed(())
        }
      }
      var observed: Throwable = null
      reader.pollInt(
        Long.MinValue,
        () => (),
        new Async.LongStepFold[Unit] {
          def successLong(value: Long): Unit                      = ()
          def failureLong(cause: Throwable): Unit                 = observed = cause
          override def trustedFailureLong(cause: Throwable): Unit = observed = cause
          def pendingLong(pollable: Pollable[Long]): Unit         = ()
        }
      )
      assertTrue(observed eq failure)
    },
    test("repeated readers execute every exact primitive scalar and bulk route") {
      val booleans                     = Reader.repeated(Reader.fromChunk(Chunk(true)).toAsync)
      val bytes                        = Reader.repeated(Reader.fromChunk(Chunk(Byte.MinValue)).toAsync)
      val chars                        = Reader.repeated(Reader.fromChunk(Chunk(Char.MaxValue)).toAsync)
      val shorts                       = Reader.repeated(Reader.fromChunk(Chunk(Short.MinValue)).toAsync)
      val ints                         = Reader.repeated(Reader.fromChunk(Chunk(Int.MaxValue)).toAsync)
      val longs                        = Reader.repeated(Reader.fromChunk(Chunk(Long.MinValue)).toAsync)
      val floats                       = Reader.repeated(Reader.fromChunk(Chunk(-0.0f)).toAsync)
      val doubles                      = Reader.repeated(Reader.fromChunk(Chunk(-0.0d)).toAsync)
      val longBulk                     = Reader.repeated(Reader.fromChunk(Chunk(1L, 2L)).toAsync)
      val dblBulk                      = Reader.repeated(Reader.fromChunk(Chunk(1.0d, 2.0d)).toAsync)
      val longDest                     = new Array[Long](2)
      val dblDest                      = new Array[Double](2)
      val nan: Double                  = Double.NaN
      def rawBits(value: Double): Long = java.lang.Double.doubleToRawLongBits(value)
      val floatExpectedBits            = java.lang.Double.doubleToRawLongBits(-0.0f.toDouble)
      val doubleExpectedBits           = java.lang.Double.doubleToRawLongBits(-0.0d)
      for {
        booleanValues <- ZIO.foreach(List(1, 2))(_ => run(booleans.readBoolean(-1)))
        byteValues    <- ZIO.foreach(List(1, 2))(_ => run(bytes.readByte()))
        charValues    <- ZIO.foreach(List(1, 2))(_ => run(chars.readChar(-1)))
        shortValues   <- ZIO.foreach(List(1, 2))(_ => run(shorts.readShort(Int.MinValue)))
        intValues     <- ZIO.foreach(List(1, 2))(_ => run(ints.readInt(Long.MinValue)))
        longValues    <- ZIO.foreach(List(1, 2))(_ => run(longs.readLong(7L)))
        floatValues   <- ZIO.foreach(List(1, 2))(_ => run(floats.readFloat(nan)))
        doubleValues  <- ZIO.foreach(List(1, 2))(_ => run(doubles.readDouble(nan)))
        longCounts    <- ZIO.foreach(List(1, 2))(_ => run(longBulk.readLongs(longDest, 0, 2)))
        doubleCounts  <- ZIO.foreach(List(1, 2))(_ => run(dblBulk.readDoubles(dblDest, 0, 2)))
      } yield assertTrue(
        booleanValues == List(1, 1),
        byteValues == List(128, 128),
        charValues == List(Char.MaxValue.toInt, Char.MaxValue.toInt),
        shortValues == List(Short.MinValue.toInt, Short.MinValue.toInt),
        intValues == List(Int.MaxValue.toLong, Int.MaxValue.toLong),
        longValues == List(Long.MinValue, Long.MinValue),
        floatValues.map(rawBits) == List(floatExpectedBits, floatExpectedBits),
        doubleValues.map(rawBits) == List(doubleExpectedBits, doubleExpectedBits),
        longCounts == List(2, 2),
        longDest.toVector == Vector(1L, 2L),
        doubleCounts == List(2, 2),
        dblDest.toVector == Vector(1.0d, 2.0d)
      )
    },
    test("unfoldAsync executes every exact primitive scalar and bulk route") {
      def one[A: JvmType.Infer](value: A): Reader.AsyncReader[A] =
        Reader.unfoldAsync[Int, A](0)(state => Async.succeed(if (state == 0) Some((value, 1)) else None))

      val booleans                     = one(true)
      val bytes                        = one(Byte.MinValue)
      val chars                        = one(Char.MaxValue)
      val shorts                       = one(Short.MinValue)
      val ints                         = one(Int.MaxValue)
      val longs                        = one(Long.MinValue)
      val floats                       = one(-0.0f)
      val doubles                      = one(-0.0d)
      val longBulk                     = one(Long.MaxValue)
      val dblBulk                      = one(Double.MaxValue)
      val longDest                     = new Array[Long](1)
      val dblDest                      = new Array[Double](1)
      val nan: Double                  = Double.NaN
      def rawBits(value: Double): Long = java.lang.Double.doubleToRawLongBits(value)
      val floatExpectedBits            = java.lang.Double.doubleToRawLongBits(-0.0f.toDouble)
      val doubleExpectedBits           = java.lang.Double.doubleToRawLongBits(-0.0d)
      for {
        booleanValues <- ZIO.foreach(List(1, 2))(_ => run(booleans.readBoolean(-1)))
        byteValues    <- ZIO.foreach(List(1, 2))(_ => run(bytes.readByte()))
        charValues    <- ZIO.foreach(List(1, 2))(_ => run(chars.readChar(-1)))
        shortValues   <- ZIO.foreach(List(1, 2))(_ => run(shorts.readShort(Int.MinValue)))
        intValues     <- ZIO.foreach(List(1, 2))(_ => run(ints.readInt(Long.MinValue)))
        longValues    <- ZIO.foreach(List(1, 2))(_ => run(longs.readLong(7L)))
        floatValues   <- ZIO.foreach(List(1, 2))(_ => run(floats.readFloat(nan)))
        doubleValues  <- ZIO.foreach(List(1, 2))(_ => run(doubles.readDouble(nan)))
        longCounts    <- ZIO.foreach(List(1, 2))(_ => run(longBulk.readLongs(longDest, 0, 1)))
        doubleCounts  <- ZIO.foreach(List(1, 2))(_ => run(dblBulk.readDoubles(dblDest, 0, 1)))
      } yield assertTrue(
        booleanValues == List(1, -1),
        byteValues == List(128, -1),
        charValues == List(Char.MaxValue.toInt, -1),
        shortValues == List(Short.MinValue.toInt, Int.MinValue),
        intValues == List(Int.MaxValue.toLong, Long.MinValue),
        longValues == List(Long.MinValue, 7L),
        rawBits(floatValues.head) == floatExpectedBits,
        floatValues(1).isNaN,
        rawBits(doubleValues.head) == doubleExpectedBits,
        doubleValues(1).isNaN,
        longCounts == List(1, -1),
        longDest(0) == Long.MaxValue,
        doubleCounts == List(1, -1),
        dblDest(0) == Double.MaxValue
      )
    },
    test("specialized synchronous and effectful Int maps execute values, EOF, failures, bulk, and controls") {
      val syncGeneric    = new Reader.MappedAsyncIntInt(Reader.fromChunk(Chunk(1)).toAsync, _ + 1)
      val syncScalar     = new Reader.MappedAsyncIntInt(Reader.fromChunk(Chunk(2)).toAsync, _ + 1)
      val syncBulk       = new Reader.MappedAsyncIntInt(Reader.fromChunk(Chunk(3, 4)).toAsync, _ + 1)
      val effect         = new Reader.MappedAsyncEffectIntInt(Reader.fromChunk(Chunk(5)).toAsync, n => Async.succeed(n + 1))
      val effectBoom     = new IllegalStateException("effect")
      val thrownBoom     = new IllegalArgumentException("effect construction")
      val failed         = new Reader.MappedAsyncEffectIntInt(Reader.fromChunk(Chunk(6)).toAsync, _ => Async.fail(effectBoom))
      val throwing       = new Reader.MappedAsyncEffectIntInt(Reader.fromChunk(Chunk(7)).toAsync, _ => throw thrownBoom)
      val dest           = new Array[Int](2)
      val generic        = syncGeneric.read[Int](-1).block
      val genericEof     = syncGeneric.read[Int](-1).block
      val scalar         = syncScalar.readInt(Long.MinValue).block
      val scalarEof      = syncScalar.readInt(Long.MinValue).block
      val bulk           = syncBulk.readInts(dest, 0, 2).block
      val ready          = effect.read[Int](-1).block
      val effectEof      = effect.readInt(Long.MinValue).block
      val failedValue    = Try(failed.readInt(Long.MinValue).block).failed.toOption
      val thrownValue    = Try(throwing.readInt(Long.MinValue).block).failed.toOption
      val readable       = syncBulk.readable().block
      val limit          = syncBulk.setLimit(1).block
      val repeat         = syncBulk.setRepeat().block
      val setSkip        = syncBulk.setSkip(1).block
      val effectReadable = effect.readable().block
      val effectLimit    = effect.setLimit(1).block
      val effectRepeat   = effect.setRepeat().block
      val effectSetSkip  = effect.setSkip(1).block
      val effectReset    = Try(effect.reset().block).failed.toOption
      effect.skip(1).block
      effect.close().block
      syncBulk.skip(1).block
      syncBulk.reset().block
      syncBulk.close().block
      assertTrue(
        syncGeneric.jvmType == JvmType.Int,
        effect.jvmType == JvmType.Int,
        generic == 2,
        genericEof == -1,
        scalar == 3L,
        scalarEof == Long.MinValue,
        bulk == 2,
        dest.toVector == Vector(4, 5),
        ready == 6,
        effectEof == Long.MinValue,
        failedValue.contains(effectBoom),
        thrownValue.contains(thrownBoom),
        !readable,
        limit,
        !repeat,
        setSkip,
        !effectReadable,
        effectLimit,
        !effectRepeat,
        effectSetSkip,
        effectReset.isEmpty,
        effect.isClosed.block,
        syncBulk.isClosed.block
      )
    },
    test("specialized Int map polling preserves every Async outcome") {
      final class Observer extends Async.LongStepFold[Unit] {
        var cause: Throwable                                    = null
        var pending: Pollable[Long]                             = null
        var trusted                                             = false
        var value: Long                                         = Long.MinValue
        def failureLong(error: Throwable): Unit                 = { cause = error; trusted = false }
        def pendingLong(effect: Pollable[Long]): Unit           = pending = effect
        def successLong(result: Long): Unit                     = value = result
        override def trustedFailureLong(error: Throwable): Unit = { cause = error; trusted = true }
      }
      def source(result: => Async[Long]): Reader.AsyncReader[Int] = new UnsupportedLaneReader[Int](JvmType.Int) {
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = result
      }

      val failure       = new IllegalStateException("source")
      val trusted       = new IllegalArgumentException("trusted")
      val mapping       = new IllegalStateException("mapping")
      val readyObserver = new Observer
      val eofObserver   = new Observer
      val failObserver  = new Observer
      val trustObserver = new Observer
      val mapObserver   = new Observer
      val pendingSource = new Completer[Long]
      val pendingObs    = new Observer
      val pullFailure   = new IllegalStateException("pull source")
      val pullTrusted   = new IllegalArgumentException("pull trusted")
      val pullMapping   = new IllegalStateException("pull mapping")
      val pollFailure   = new IllegalStateException("pull poll")
      val pullReady     = new Reader.MappedAsyncIntInt(source(Async.succeed(8L)), _ + 1)
        .readInt(Long.MinValue)
        .asInstanceOf[Pollable[Long]]
        .poll(() => ())
      val pullEof = new Reader.MappedAsyncIntInt(source(Async.succeed(Long.MinValue)), _ + 1)
        .readInt(Long.MinValue)
        .asInstanceOf[Pollable[Long]]
        .poll(() => ())
      val pullFailed = new Reader.MappedAsyncIntInt(source(Async.fail(pullFailure)), _ + 1)
        .readInt(Long.MinValue)
        .asInstanceOf[Pollable[Long]]
        .poll(() => ())
      val pullTrustFailed = new Reader.MappedAsyncIntInt(source(Async.failTrusted(pullTrusted)), _ + 1)
        .readInt(Long.MinValue)
        .asInstanceOf[Pollable[Long]]
        .poll(() => ())
      val pullMapFailed = new Reader.MappedAsyncIntInt(source(Async.succeed(9L)), _ => throw pullMapping)
        .readInt(Long.MinValue)
        .asInstanceOf[Pollable[Long]]
        .poll(() => ())
      val pullPollFailed = new Reader.MappedAsyncIntInt(
        source(new Async.Operation[Long] {
          def poll(onComplete: Runnable): Async[Long]  = throw pollFailure
          protected def cancelOperation(): Async[Unit] = Async.succeed(())
        }),
        _ + 1
      ).readInt(Long.MinValue).asInstanceOf[Pollable[Long]].poll(() => ())
      new Reader.MappedAsyncIntInt(source(Async.succeed(1L)), _ + 1)
        .pollInt(Long.MinValue, () => (), readyObserver)
      new Reader.MappedAsyncIntInt(source(Async.succeed(Long.MinValue)), _ + 1)
        .pollInt(Long.MinValue, () => (), eofObserver)
      new Reader.MappedAsyncIntInt(source(Async.fail(failure)), _ + 1)
        .pollInt(Long.MinValue, () => (), failObserver)
      new Reader.MappedAsyncIntInt(source(Async.failTrusted(trusted)), _ + 1)
        .pollInt(Long.MinValue, () => (), trustObserver)
      new Reader.MappedAsyncIntInt(source(Async.succeed(1L)), _ => throw mapping)
        .pollInt(Long.MinValue, () => (), mapObserver)
      new Reader.MappedAsyncIntInt(source(pendingSource), _ + 1)
        .pollInt(Long.MinValue, () => (), pendingObs)
      pendingSource.succeed(2L)
      assertTrue(
        readyObserver.value == 2L,
        eofObserver.value == Long.MinValue,
        failObserver.cause eq failure,
        !failObserver.trusted,
        trustObserver.cause eq trusted,
        trustObserver.trusted,
        mapObserver.cause eq mapping,
        pendingObs.pending.block == 3L,
        pullReady.block == 9L,
        pullEof.block == Long.MinValue,
        Try(pullFailed.block).failed.toOption.contains(pullFailure),
        Try(pullTrustFailed.block).failed.toOption.contains(pullTrusted),
        Try(pullMapFailed.block).failed.toOption.contains(pullMapping),
        Try(pullPollFailed.block).failed.toOption.contains(pollFailure)
      )
    },
    test("specialized Int-to-reference maps preserve EOF and normalize callback failures") {
      val ready              = new Reader.MappedAsyncIntRef(Reader.fromChunk(Chunk(1)).toAsync, n => s"v$n")
      val streamError        = new zio.blocks.streams.internal.StreamError("callback")
      val ordinary           = new IllegalStateException("callback")
      val sourceFailureCause = new IllegalArgumentException("source")
      val trustedSourceCause = zio.blocks.streams.internal.StreamError.source("source")
      val sourceThrow        = new UnsupportedLaneReader[Int](JvmType.Int) {
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = throw sourceFailureCause
      }
      val trustedSourceThrow = new UnsupportedLaneReader[Int](JvmType.Int) {
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = throw trustedSourceCause
      }
      val streamFailure        = new Reader.MappedAsyncIntRef(Reader.fromChunk(Chunk(2)).toAsync, _ => throw streamError)
      val ordinaryFailure      = new Reader.MappedAsyncIntRef(Reader.fromChunk(Chunk(3)).toAsync, _ => throw ordinary)
      val sourceFailure        = new Reader.MappedAsyncIntRef(sourceThrow, _.toString)
      val trustedSourceFailure = new Reader.MappedAsyncIntRef(trustedSourceThrow, _.toString)
      val first                = ready.read[String](null).block
      val eof                  = ready.read[String](null).block
      val streamResult         = Try(streamFailure.read[String](null).block).failed.toOption
      val ordinaryResult       = Try(ordinaryFailure.read[String](null).block).failed.toOption
      val sourceResult         = Try(sourceFailure.read[String](null).block).failed.toOption
      val trustedSourceResult  = Try(trustedSourceFailure.read[String](null).block).failed.toOption
      val readable             = ready.readable().block
      val limit                = ready.setLimit(1).block
      val repeat               = ready.setRepeat().block
      val setSkip              = ready.setSkip(1).block
      ready.skip(1).block
      ready.reset().block
      ready.close().block
      assertTrue(
        first == "v1",
        eof == null,
        streamResult.exists(error =>
          error.isInstanceOf[zio.blocks.streams.internal.StreamError] && (error ne streamError)
        ),
        ordinaryResult.contains(ordinary),
        sourceResult.contains(sourceFailureCause),
        trustedSourceResult.contains(trustedSourceCause),
        !readable,
        limit,
        !repeat,
        setSkip,
        ready.isClosed.block
      )
    },
    test("async concat executes Long and Double scalar and bulk transitions") {
      val longs = Reader
        .fromChunk(Chunk(Long.MinValue, 1L))
        .toAsync
        .concatReaderWithJvmType(() => Reader.fromChunk(Chunk(2L, Long.MaxValue)).toAsync, JvmType.Long)
      val doubles = Reader
        .fromChunk(Chunk(-0.0d, 1.0d))
        .toAsync
        .concatReaderWithJvmType(() => Reader.fromChunk(Chunk(2.0d, Double.MaxValue)).toAsync, JvmType.Double)
      val longBulk = Reader
        .fromChunk(Chunk(1L, 2L))
        .toAsync
        .concatReaderWithJvmType(() => Reader.fromChunk(Chunk(3L, 4L)).toAsync, JvmType.Long)
      val dblBulk = Reader
        .fromChunk(Chunk(1.0d, 2.0d))
        .toAsync
        .concatReaderWithJvmType(() => Reader.fromChunk(Chunk(3.0d, 4.0d)).toAsync, JvmType.Double)
      val longDest           = new Array[Long](4)
      val dblDest            = new Array[Double](4)
      val nan: Double        = Double.NaN
      val doubleValue1       = doubles.readDouble(nan).block
      val doubleValue2       = doubles.readDouble(nan).block
      val doubleValue3       = doubles.readDouble(nan).block
      val doubleValue4       = doubles.readDouble(nan).block
      val doubleEof          = doubles.readDouble(nan).block
      val doubleBits         = java.lang.Double.doubleToRawLongBits(doubleValue1)
      val doubleExpectedBits = java.lang.Double.doubleToRawLongBits(-0.0d)
      assertTrue(
        longs.readLong(7L).block == Long.MinValue,
        longs.readLong(7L).block == 1L,
        longs.readLong(7L).block == 2L,
        longs.readLong(7L).block == Long.MaxValue,
        longs.readLong(7L).block == 7L,
        doubleBits == doubleExpectedBits,
        doubleValue2 == 1.0d,
        doubleValue3 == 2.0d,
        doubleValue4 == Double.MaxValue,
        doubleEof.isNaN,
        longBulk.readLongs(longDest, 0, 4).block == 2,
        longBulk.readLongs(longDest, 2, 2).block == 2,
        longBulk.readLongs(longDest, 0, 4).block == -1,
        longDest.toVector == Vector(1L, 2L, 3L, 4L),
        dblBulk.readDoubles(dblDest, 0, 4).block == 2,
        dblBulk.readDoubles(dblDest, 2, 2).block == 2,
        dblBulk.readDoubles(dblDest, 0, 4).block == -1,
        dblDest.toVector == Vector(1.0d, 2.0d, 3.0d, 4.0d)
      )
    },
    test("sync readers concatenate an asynchronously constructed tail without losing either side") {
      val reader = Reader.fromChunk(Chunk("head")).concatAsync(() => Async.succeed(Reader.fromChunk(Chunk("tail"))))
      assertTrue(
        reader.read[String](null).block == "head",
        reader.read[String](null).block == "tail",
        reader.read[String](null).block == null
      )
    },
    test("async skip handles non-positive counts, exhaustion, and the rescheduling budget") {
      val reader    = Reader.fromChunk(Chunk.fromArray(Array.tabulate(300)(identity))).toAsync
      val exhausted = Reader.fromChunk(Chunk(1, 2)).toAsync
      val generic   = new BoxedAsyncReader(Vector.tabulate(300)(identity))
      for {
        _              <- run(reader.skip(0))
        _              <- run(reader.skip(-1))
        _              <- run(reader.skip(257))
        _              <- run(exhausted.skip(257))
        _              <- run(generic.skip(257))
        readerValue    <- run(reader.readInt(Long.MinValue))
        exhaustedValue <- run(exhausted.readInt(Long.MinValue))
        genericValue   <- run(generic.read[Int](-1))
      } yield assertTrue(readerValue == 257L, exhaustedValue == Long.MinValue, genericValue == 257)
    },
    test("memoized asynchronous close captures synchronous effect construction failure exactly once") {
      val failure = new IllegalStateException("close construction")
      var claims  = 0
      var closes  = 0
      val close   = new Reader.MemoizedClose(
        () => claims += 1,
        () => { closes += 1; throw failure }
      )
      val first  = Try(close.closeClaimed().block).failed.toOption
      val second = Try(close.close().block).failed.toOption
      assertTrue(first.contains(failure), second.contains(failure), claims == 1, closes == 1)
    },
    test("async concat Int polling preserves ready, pending, EOF, failure, trusted failure, and close") {
      final class Observer extends Async.LongStepFold[Unit] {
        var cause: Throwable                                    = null
        var pending: Pollable[Long]                             = null
        var trusted                                             = false
        var value                                               = Long.MinValue
        def failureLong(error: Throwable): Unit                 = { cause = error; trusted = false }
        def pendingLong(effect: Pollable[Long]): Unit           = pending = effect
        def successLong(result: Long): Unit                     = value = result
        override def trustedFailureLong(error: Throwable): Unit = { cause = error; trusted = true }
      }
      def source(result: => Async[Long]): Reader.AsyncReader[Int] = new UnsupportedLaneReader[Int](JvmType.Int) {
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = result
      }
      def concat(head: Reader.AsyncReader[Int], tail: => Reader.AsyncReader[Int]): Reader.AsyncReader[Int] =
        head.concatReaderWithJvmType(() => tail, JvmType.Int)

      val failure       = new IllegalStateException("source")
      val trusted       = new IllegalArgumentException("trusted")
      val readyObserver = new Observer
      val eofObserver   = new Observer
      val failObserver  = new Observer
      val trustObserver = new Observer
      val closeObserver = new Observer
      val pendingSource = new Completer[Long]
      val pendingObs    = new Observer
      concat(source(Async.succeed(1L)), source(Async.succeed(2L)))
        .pollInt(Long.MinValue, () => (), readyObserver)
      concat(source(Async.succeed(Long.MinValue)), source(Async.succeed(2L)))
        .pollInt(Long.MinValue, () => (), eofObserver)
      concat(source(Async.fail(failure)), source(Async.succeed(2L)))
        .pollInt(Long.MinValue, () => (), failObserver)
      concat(source(Async.failTrusted(trusted)), source(Async.succeed(2L)))
        .pollInt(Long.MinValue, () => (), trustObserver)
      concat(source(pendingSource), source(Async.succeed(2L)))
        .pollInt(Long.MinValue, () => (), pendingObs)
      val closed = concat(source(Async.succeed(1L)), source(Async.succeed(2L)))
      closed.close().block
      closed.pollInt(Long.MinValue, () => (), closeObserver)
      pendingSource.succeed(3L)
      val eofValue = if (eofObserver.pending eq null) eofObserver.value else eofObserver.pending.block
      assertTrue(
        readyObserver.value == 1L,
        eofValue == 2L,
        failObserver.cause eq failure,
        !failObserver.trusted,
        trustObserver.cause eq trusted,
        trustObserver.trusted,
        pendingObs.pending.block == 3L,
        closeObserver.value == Long.MinValue
      )
    },
    test("fromIterable executes every exact primitive lane") {
      val bool    = Reader.fromIterable(List(false, true))
      val byte    = Reader.fromIterable(List(Byte.MinValue, Byte.MaxValue))
      val char    = Reader.fromIterable(List(Char.MinValue, Char.MaxValue))
      val short   = Reader.fromIterable(List(Short.MinValue, Short.MaxValue))
      val int     = Reader.fromIterable(List(Int.MinValue, Int.MaxValue))
      val long    = Reader.fromIterable(List(Long.MinValue, Long.MaxValue))
      val float   = Reader.fromIterable(List(Float.MinValue, Float.MaxValue))
      val double  = Reader.fromIterable(List(Double.MinValue, Double.MaxValue))
      val longs   = new Array[Long](2)
      val doubles = new Array[Double](2)
      assertTrue(
        bool.jvmType == JvmType.Boolean,
        byte.jvmType == JvmType.Byte,
        char.jvmType == JvmType.Char,
        short.jvmType == JvmType.Short,
        int.jvmType == JvmType.Int,
        long.jvmType == JvmType.Long,
        float.jvmType == JvmType.Float,
        double.jvmType == JvmType.Double,
        bool.readBoolean(-1) == 0,
        bool.readBoolean(-1) == 1,
        bool.readBoolean(-1) == -1,
        byte.readByte() == 128,
        byte.readByte() == 127,
        byte.readByte() == -1,
        char.readChar(-1) == Char.MinValue.toInt,
        char.readChar(-1) == Char.MaxValue.toInt,
        char.readChar(-1) == -1,
        short.readShort(Int.MaxValue) == Short.MinValue.toInt,
        short.readShort(Int.MinValue) == Short.MaxValue.toInt,
        short.readShort(Int.MinValue) == Int.MinValue,
        int.readInt(Long.MaxValue) == Int.MinValue.toLong,
        int.readInt(Long.MinValue) == Int.MaxValue.toLong,
        int.readInt(Long.MinValue) == Long.MinValue,
        long.readLongs(longs, 0, 2) == 2,
        longs.toVector == Vector(Long.MinValue, Long.MaxValue),
        long.readLongs(longs, 0, 2) == -1,
        long.readLongs(longs, 0, 2) == -1,
        float.readFloat(Double.NaN) == Float.MinValue.toDouble,
        float.readFloat(Double.NaN) == Float.MaxValue.toDouble,
        float.readFloat(Double.NaN).isNaN,
        double.readDoubles(doubles, 0, 2) == 2,
        doubles.toVector == Vector(Double.MinValue, Double.MaxValue),
        double.readDoubles(doubles, 0, 2) == -1,
        double.readDoubles(doubles, 0, 2) == -1
      )
    },
    test("Platform factories execute sync and async reader routes") {
      def readN(reader: Reader[Int], n: Int): ZIO[Any, Throwable, Chunk[Int]] = reader match {
        case sync: Reader.SyncReader[Int @unchecked]   => ZIO.attempt(sync.readN[Int](n))
        case async: Reader.AsyncReader[Int @unchecked] => run(async.readN[Int](n))
      }
      def close(reader: Reader[Int]): ZIO[Any, Throwable, Unit] = reader match {
        case sync: Reader.SyncReader[Int @unchecked]   => ZIO.attempt(sync.close())
        case async: Reader.AsyncReader[Int @unchecked] => run(async.close())
      }
      val bufferedSync  = Platform.createBufferedReader(Reader.fromChunk(Chunk(1, 2)), 2)
      val bufferedAsync = Platform.createBufferedReaderFromReader(Reader.fromChunk(Chunk(3, 4)).toAsync, 2)
      val mappedSync    =
        Platform.createMapParReader(Reader.fromChunk(Chunk(1, 2)), 2, (n: Int) => n + 1, 2, JvmType.Int, JvmType.Int)
      val mappedAsync = Platform.createMapParReaderFromReader(
        Reader.fromChunk(Chunk(3, 4)).toAsync,
        2,
        (n: Int) => n + 1,
        2,
        JvmType.Int,
        JvmType.Int
      )
      val mergedSync = Platform.createMergeReader[Int](
        Reader.fromChunk(Chunk(Stream(1, 2), Stream(3, 4))),
        2,
        2,
        JvmType.Int
      )
      val mergedAsync = Platform.createMergeReaderFromReader[Int](
        Reader.fromChunk(Chunk(Stream(5, 6), Stream(7, 8))).toAsync,
        2,
        2,
        JvmType.Int
      )
      for {
        bufferedSyncValues  <- readN(bufferedSync, 2)
        bufferedAsyncValues <- readN(bufferedAsync, 2)
        mappedSyncValues    <- readN(mappedSync, 2)
        mappedAsyncValues   <- readN(mappedAsync, 2)
        syncMergedValues    <- readN(mergedSync, 4)
        asyncMergedValues   <- readN(mergedAsync, 4)
        _                   <- close(bufferedSync)
        _                   <- close(bufferedAsync)
        _                   <- close(mappedSync)
        _                   <- close(mappedAsync)
        _                   <- close(mergedSync)
        _                   <- close(mergedAsync)
      } yield assertTrue(
        bufferedSyncValues == Chunk(1, 2),
        bufferedAsyncValues == Chunk(3, 4),
        mappedSyncValues.toSet == Set(2, 3),
        mappedAsyncValues.toSet == Set(4, 5),
        syncMergedValues.toSet == Set(1, 2, 3, 4),
        asyncMergedValues.toSet == Set(5, 6, 7, 8)
      )
    },
    test("Platform synchronous map and merge factories execute every exact lane") {
      def rawBits(value: Double): Long = java.lang.Double.doubleToRawLongBits(value)

      val mapBoolean = Platform.createMapParReader(
        Reader.fromChunk(Chunk(true)),
        1,
        (v: Boolean) => v,
        2,
        JvmType.Boolean,
        JvmType.Boolean
      )
      val mapByte = Platform.createMapParReader(
        Reader.fromChunk(Chunk(Byte.MinValue)),
        1,
        (v: Byte) => v,
        2,
        JvmType.Byte,
        JvmType.Byte
      )
      val mapChar = Platform.createMapParReader(
        Reader.fromChunk(Chunk(Char.MaxValue)),
        1,
        (v: Char) => v,
        2,
        JvmType.Char,
        JvmType.Char
      )
      val mapShort = Platform.createMapParReader(
        Reader.fromChunk(Chunk(Short.MinValue)),
        1,
        (v: Short) => v,
        2,
        JvmType.Short,
        JvmType.Short
      )
      val mapInt = Platform.createMapParReader(
        Reader.fromChunk(Chunk(Int.MaxValue)),
        1,
        (v: Int) => v,
        2,
        JvmType.Int,
        JvmType.Int
      )
      val mapLong =
        Platform.createMapParReader(Reader.fromChunk(Chunk(1L, 2L)), 1, (v: Long) => v, 2, JvmType.Long, JvmType.Long)
      val mapFloat =
        Platform.createMapParReader(Reader.fromChunk(Chunk(-0.0f)), 1, (v: Float) => v, 2, JvmType.Float, JvmType.Float)
      val mapDouble = Platform.createMapParReader(
        Reader.fromChunk(Chunk(1.0d, 2.0d)),
        1,
        (v: Double) => v,
        2,
        JvmType.Double,
        JvmType.Double
      )
      val mergeBoolean =
        Platform.createMergeReader[Boolean](Reader.fromChunk(Chunk(Stream(true))), 1, 2, JvmType.Boolean)
      val mergeByte =
        Platform.createMergeReader[Byte](Reader.fromChunk(Chunk(Stream(Byte.MinValue))), 1, 2, JvmType.Byte)
      val mergeChar =
        Platform.createMergeReader[Char](Reader.fromChunk(Chunk(Stream(Char.MaxValue))), 1, 2, JvmType.Char)
      val mergeShort =
        Platform.createMergeReader[Short](Reader.fromChunk(Chunk(Stream(Short.MinValue))), 1, 2, JvmType.Short)
      val mergeInt    = Platform.createMergeReader[Int](Reader.fromChunk(Chunk(Stream(Int.MaxValue))), 1, 2, JvmType.Int)
      val mergeLong   = Platform.createMergeReader[Long](Reader.fromChunk(Chunk(Stream(1L, 2L))), 1, 2, JvmType.Long)
      val mergeFloat  = Platform.createMergeReader[Float](Reader.fromChunk(Chunk(Stream(-0.0f))), 1, 2, JvmType.Float)
      val mergeDouble =
        Platform.createMergeReader[Double](Reader.fromChunk(Chunk(Stream(1.0d, 2.0d))), 1, 2, JvmType.Double)
      val mapLongs          = new Array[Long](1)
      val mapDoubles        = new Array[Double](1)
      val mergeLongs        = new Array[Long](1)
      val mergeDoubles      = new Array[Double](1)
      val mapBooleanValue   = mapBoolean.readBoolean(-1)
      val mapByteValue      = mapByte.readByte()
      val mapCharValue      = mapChar.readChar(-1)
      val mapShortValue     = mapShort.readShort(Int.MaxValue)
      val mapIntValue       = mapInt.readInt(Long.MinValue)
      val mapLongValue      = mapLong.readLong(Long.MinValue)
      val mapLongCount      = mapLong.readLongs(mapLongs, 0, 1)
      val mapFloatValue     = mapFloat.readFloat(Double.NaN)
      val mapDoubleValue    = mapDouble.readDouble(Double.NaN)
      val mapDoubleCount    = mapDouble.readDoubles(mapDoubles, 0, 1)
      val mergeBooleanValue = mergeBoolean.readBoolean(-1)
      val mergeByteValue    = mergeByte.readByte()
      val mergeCharValue    = mergeChar.readChar(-1)
      val mergeShortValue   = mergeShort.readShort(Int.MaxValue)
      val mergeIntValue     = mergeInt.readInt(Long.MinValue)
      val mergeLongValue    = mergeLong.readLong(Long.MinValue)
      val mergeLongCount    = mergeLong.readLongs(mergeLongs, 0, 1)
      val mergeFloatValue   = mergeFloat.readFloat(Double.NaN)
      val mergeDoubleValue  = mergeDouble.readDouble(Double.NaN)
      val mergeDoubleCount  = mergeDouble.readDoubles(mergeDoubles, 0, 1)
      mapBoolean.close()
      mapByte.close()
      mapChar.close()
      mapShort.close()
      mapInt.close()
      mapLong.close()
      mapFloat.close()
      mapDouble.close()
      mergeBoolean.close()
      mergeByte.close()
      mergeChar.close()
      mergeShort.close()
      mergeInt.close()
      mergeLong.close()
      mergeFloat.close()
      mergeDouble.close()
      assertTrue(
        mapBooleanValue == 1,
        mapByteValue == 128,
        mapCharValue == Char.MaxValue.toInt,
        mapShortValue == Short.MinValue.toInt,
        mapIntValue == Int.MaxValue.toLong,
        mapLongValue == 1L,
        mapLongCount == 1,
        mapLongs(0) == 2L,
        rawBits(mapFloatValue) == rawBits(-0.0d),
        mapDoubleValue == 1.0d,
        mapDoubleCount == 1,
        mapDoubles(0) == 2.0d,
        mergeBooleanValue == 1,
        mergeByteValue == 128,
        mergeCharValue == Char.MaxValue.toInt,
        mergeShortValue == Short.MinValue.toInt,
        mergeIntValue == Int.MaxValue.toLong,
        mergeLongValue == 1L,
        mergeLongCount == 1,
        mergeLongs(0) == 2L,
        rawBits(mergeFloatValue) == rawBits(-0.0d),
        mergeDoubleValue == 1.0d,
        mergeDoubleCount == 1,
        mergeDoubles(0) == 2.0d
      )
    },
    test("Platform asynchronous map and merge factories execute every exact lane") {
      def rawBits(value: Double): Long = java.lang.Double.doubleToRawLongBits(value)

      def async[A](reader: Reader[A]): Reader.AsyncReader[A] = reader match {
        case value: Reader.AsyncReader[A @unchecked] => value
        case value: Reader.SyncReader[A @unchecked]  => value.toAsync
      }
      def mapped[A](values: Chunk[A], lane: JvmType): Reader.AsyncReader[A] =
        async(
          Platform.createMapParReaderFromReader(Reader.fromChunk(values).toAsync, 1, (value: A) => value, 2, lane, lane)
        )
      def merged[A](values: Chunk[A], lane: JvmType): Reader.AsyncReader[A] =
        async(
          Platform.createMergeReaderFromReader[A](Reader.fromChunk(Chunk(Stream.fromChunk(values))).toAsync, 1, 2, lane)
        )
      val mapBoolean   = mapped(Chunk(true), JvmType.Boolean)
      val mapByte      = mapped(Chunk(Byte.MinValue), JvmType.Byte)
      val mapChar      = mapped(Chunk(Char.MaxValue), JvmType.Char)
      val mapShort     = mapped(Chunk(Short.MinValue), JvmType.Short)
      val mapInt       = mapped(Chunk(Int.MaxValue), JvmType.Int)
      val mapLong      = mapped(Chunk(1L, 2L), JvmType.Long)
      val mapFloat     = mapped(Chunk(-0.0f), JvmType.Float)
      val mapDouble    = mapped(Chunk(1.0d, 2.0d), JvmType.Double)
      val mergeBoolean = merged(Chunk(true), JvmType.Boolean)
      val mergeByte    = merged(Chunk(Byte.MinValue), JvmType.Byte)
      val mergeChar    = merged(Chunk(Char.MaxValue), JvmType.Char)
      val mergeShort   = merged(Chunk(Short.MinValue), JvmType.Short)
      val mergeInt     = merged(Chunk(Int.MaxValue), JvmType.Int)
      val mergeLong    = merged(Chunk(1L, 2L), JvmType.Long)
      val mergeFloat   = merged(Chunk(-0.0f), JvmType.Float)
      val mergeDouble  = merged(Chunk(1.0d, 2.0d), JvmType.Double)
      val mapLongs     = new Array[Long](1)
      val mapDoubles   = new Array[Double](1)
      val mergeLongs   = new Array[Long](1)
      val mergeDoubles = new Array[Double](1)
      for {
        mapBooleanValue   <- run(mapBoolean.readBoolean(-1))
        mapByteValue      <- run(mapByte.readByte())
        mapCharValue      <- run(mapChar.readChar(-1))
        mapShortValue     <- run(mapShort.readShort(Int.MaxValue))
        mapIntValue       <- run(mapInt.readInt(Long.MinValue))
        mapLongValue      <- run(mapLong.readLong(Long.MinValue))
        mapLongCount      <- run(mapLong.readLongs(mapLongs, 0, 1))
        mapFloatValue     <- run(mapFloat.readFloat(Double.NaN))
        mapDoubleValue    <- run(mapDouble.readDouble(Double.NaN))
        mapDoubleCount    <- run(mapDouble.readDoubles(mapDoubles, 0, 1))
        mergeBooleanValue <- run(mergeBoolean.readBoolean(-1))
        mergeByteValue    <- run(mergeByte.readByte())
        mergeCharValue    <- run(mergeChar.readChar(-1))
        mergeShortValue   <- run(mergeShort.readShort(Int.MaxValue))
        mergeIntValue     <- run(mergeInt.readInt(Long.MinValue))
        mergeLongValue    <- run(mergeLong.readLong(Long.MinValue))
        mergeLongCount    <- run(mergeLong.readLongs(mergeLongs, 0, 1))
        mergeFloatValue   <- run(mergeFloat.readFloat(Double.NaN))
        mergeDoubleValue  <- run(mergeDouble.readDouble(Double.NaN))
        mergeDoubleCount  <- run(mergeDouble.readDoubles(mergeDoubles, 0, 1))
        _                 <- ZIO.foreachDiscard(
               List[Reader.AsyncReader[_]](
                 mapBoolean,
                 mapByte,
                 mapChar,
                 mapShort,
                 mapInt,
                 mapLong,
                 mapFloat,
                 mapDouble,
                 mergeBoolean,
                 mergeByte,
                 mergeChar,
                 mergeShort,
                 mergeInt,
                 mergeLong,
                 mergeFloat,
                 mergeDouble
               )
             )(reader => run(reader.close()))
      } yield assertTrue(
        mapBooleanValue == 1,
        mapByteValue == 128,
        mapCharValue == Char.MaxValue.toInt,
        mapShortValue == Short.MinValue.toInt,
        mapIntValue == Int.MaxValue.toLong,
        mapLongValue == 1L,
        mapLongCount == 1,
        mapLongs(0) == 2L,
        rawBits(mapFloatValue) == rawBits(-0.0d),
        mapDoubleValue == 1.0d,
        mapDoubleCount == 1,
        mapDoubles(0) == 2.0d,
        mergeBooleanValue == 1,
        mergeByteValue == 128,
        mergeCharValue == Char.MaxValue.toInt,
        mergeShortValue == Short.MinValue.toInt,
        mergeIntValue == Int.MaxValue.toLong,
        mergeLongValue == 1L,
        mergeLongCount == 1,
        mergeLongs(0) == 2L,
        rawBits(mergeFloatValue) == rawBits(-0.0d),
        mergeDoubleValue == 1.0d,
        mergeDoubleCount == 1,
        mergeDoubles(0) == 2.0d
      )
    },
    test("sync-to-async scalar reads preserve every primitive lane and sentinel-domain extrema") {
      val bool   = Reader.fromChunk(Chunk(true, false)).toAsync
      val char   = Reader.fromChunk(Chunk(Char.MinValue, Char.MaxValue)).toAsync
      val short  = Reader.fromChunk(Chunk(Short.MinValue, Short.MaxValue)).toAsync
      val int    = Reader.fromChunk(Chunk(Int.MinValue, Int.MaxValue)).toAsync
      val long   = Reader.fromChunk(Chunk(Long.MinValue, Long.MaxValue)).toAsync
      val float  = Reader.fromChunk(floatValues).toAsync
      val double = Reader.fromChunk(doubleValues).toAsync
      val ref    = Reader.fromChunk(Chunk("", null, "end")).toAsync
      val bytes  = Reader.fromChunk(Chunk.fromIterable((-128 to 127).map(_.toByte))).toAsync
      assertTrue(
        bool.readBoolean(-1).block == 1,
        bool.readBoolean(-1).block == 0,
        char.readChar(-1).block == 0,
        char.readChar(-1).block == 65535,
        short.readShort(Int.MaxValue).block == Short.MinValue.toInt,
        short.readShort(Int.MinValue).block == Short.MaxValue.toInt,
        int.readInt(Long.MaxValue).block == Int.MinValue.toLong,
        int.readInt(Long.MinValue).block == Int.MaxValue.toLong,
        long.readLong(0L).block == Long.MinValue,
        long.readLong(0L).block == Long.MaxValue,
        floatBits(float.readN[Float](floatValues.length).block) == floatBits(floatValues),
        doubleBits(double.readN[Double](doubleValues.length).block) == doubleBits(doubleValues),
        ref.readN[String](3).block == Chunk("", null, "end"),
        bytes.readN[Byte](256).block == Chunk.fromIterable((-128 to 127).map(_.toByte))
      )
    },
    test("readN and readUpToN preserve every lane") {
      def pair[A: JvmType.Infer](values: Chunk[A]): (Chunk[A], Chunk[A]) =
        (
          Reader.fromChunk(values).toAsync.readN[A](values.length).block,
          Reader.fromChunk(values).toAsync.readUpToN[A](values.length).block
        )
      val bytes = Chunk.fromIterable((-128 to 127).map(_.toByte))
      assertTrue(
        pair(Chunk(true, false)) == ((Chunk(true, false), Chunk(true, false))),
        pair(bytes) == ((bytes, bytes)),
        pair(Chunk(Char.MinValue, Char.MaxValue)) == ((
          Chunk(Char.MinValue, Char.MaxValue),
          Chunk(Char.MinValue, Char.MaxValue)
        )),
        pair(Chunk(Short.MinValue, Short.MaxValue)) == ((
          Chunk(Short.MinValue, Short.MaxValue),
          Chunk(Short.MinValue, Short.MaxValue)
        )),
        pair(Chunk(Int.MinValue, Int.MaxValue)) == ((
          Chunk(Int.MinValue, Int.MaxValue),
          Chunk(Int.MinValue, Int.MaxValue)
        )),
        pair(Chunk(Long.MinValue, Long.MaxValue)) == ((
          Chunk(Long.MinValue, Long.MaxValue),
          Chunk(Long.MinValue, Long.MaxValue)
        )),
        floatBits(pair(floatValues)._1) == floatBits(floatValues),
        floatBits(pair(floatValues)._2) == floatBits(floatValues),
        doubleBits(pair(doubleValues)._1) == doubleBits(doubleValues),
        doubleBits(pair(doubleValues)._2) == doubleBits(doubleValues),
        pair(Chunk("min", "max")) == ((Chunk("min", "max"), Chunk("min", "max")))
      )
    },
    test("bounded reads and skip use only the advertised exact lane") {
      def check[A](lane: JvmType, values: Vector[A]): Boolean = {
        val readN = new ExactLaneProbe(lane, values).readN[A](values.length).block
        val upTo  = new ExactLaneProbe(lane, values).readUpToN[A](values.length).block
        val skip  = new ExactLaneProbe(lane, values)
        skip.skip(1).block
        def same(actual: Vector[A], expected: Vector[A]): Boolean = lane match {
          case JvmType.Float =>
            actual.map(x => java.lang.Float.floatToRawIntBits(x.asInstanceOf[Float])) == expected.map(x =>
              java.lang.Float.floatToRawIntBits(x.asInstanceOf[Float])
            )
          case JvmType.Double =>
            actual.map(x => java.lang.Double.doubleToRawLongBits(x.asInstanceOf[Double])) == expected.map(x =>
              java.lang.Double.doubleToRawLongBits(x.asInstanceOf[Double])
            )
          case _ => actual == expected
        }
        same(readN.toVector, values) && same(upTo.toVector, values) && same(
          skip.readN[A](values.length).block.toVector,
          values.drop(1)
        )
      }
      val wrong =
        Try(new ExactLaneProbe(JvmType.Int, Vector(1)).asInstanceOf[Reader.AsyncReader[Byte]].readByte().block)
      assertTrue(
        check(JvmType.Boolean, Vector(false, true)),
        check(JvmType.Byte, Vector(Byte.MinValue, Byte.MaxValue)),
        check(JvmType.Char, Vector(Char.MinValue, Char.MaxValue)),
        check(JvmType.Short, Vector(Short.MinValue, Short.MaxValue)),
        check(JvmType.Int, Vector(Int.MinValue, Int.MaxValue)),
        check(JvmType.Long, Vector(Long.MinValue, Long.MaxValue)),
        check(JvmType.Float, Vector(-0.0f, Float.NaN)),
        check(JvmType.Double, Vector(-0.0d, Double.NaN)),
        wrong.isFailure
      )
    },
    test("async chunked and sliding pull all eight probes through exact lanes") {
      def check[A](lane: JvmType, values: Vector[A]): Boolean = {
        val chunked       = AsyncStatefulReader.chunked(new ExactLaneProbe(lane, values), 2)
        val sliding       = AsyncStatefulReader.sliding(new ExactLaneProbe(lane, values), 2, 1)
        val chunkedValues = chunked.readN[Chunk[A]](1).block.head.toVector
        val slidingValues = sliding.readN[Chunk[A]](1).block.head.toVector
        lane match {
          case JvmType.Float =>
            chunkedValues.map(x => java.lang.Float.floatToRawIntBits(x.asInstanceOf[Float])) == values.map(x =>
              java.lang.Float.floatToRawIntBits(x.asInstanceOf[Float])
            ) && slidingValues.map(x => java.lang.Float.floatToRawIntBits(x.asInstanceOf[Float])) == values.map(x =>
              java.lang.Float.floatToRawIntBits(x.asInstanceOf[Float])
            )
          case JvmType.Double =>
            chunkedValues.map(x => java.lang.Double.doubleToRawLongBits(x.asInstanceOf[Double])) == values.map(x =>
              java.lang.Double.doubleToRawLongBits(x.asInstanceOf[Double])
            ) && slidingValues.map(x => java.lang.Double.doubleToRawLongBits(x.asInstanceOf[Double])) == values.map(x =>
              java.lang.Double.doubleToRawLongBits(x.asInstanceOf[Double])
            )
          case _ => chunkedValues == values && slidingValues == values
        }
      }
      assertTrue(
        check(JvmType.Boolean, Vector(false, true)),
        check(JvmType.Byte, Vector(Byte.MinValue, Byte.MaxValue)),
        check(JvmType.Char, Vector(Char.MinValue, Char.MaxValue)),
        check(JvmType.Short, Vector(Short.MinValue, Short.MaxValue)),
        check(JvmType.Int, Vector(Int.MinValue, Int.MaxValue)),
        check(JvmType.Long, Vector(Long.MinValue, Long.MaxValue)),
        check(JvmType.Float, Vector(-0.0f, Float.NaN)),
        check(JvmType.Double, Vector(-0.0d, Double.NaN))
      )
    },
    test("widened async intersperse boxes all eight exact primitive lanes without sentinel collisions") {
      val separator: Any                                      = "separator"
      def check[A](lane: JvmType, values: Vector[A]): Boolean = {
        val reader =
          AsyncStatefulReader.intersperse[A, Any](new ExactLaneProbe(lane, values), separator, JvmType.AnyRef)
        val actual   = reader.readAll[Any]().block.toVector
        val expected = values.zipWithIndex.flatMap { case (value, index) =>
          if (index == values.length - 1) Vector[Any](value) else Vector[Any](value, separator)
        }
        val equal = lane match {
          case JvmType.Float =>
            actual.collect { case value: Float => java.lang.Float.floatToRawIntBits(value) } ==
              expected.collect { case value: Float => java.lang.Float.floatToRawIntBits(value) }
          case JvmType.Double =>
            actual.collect { case value: Double => java.lang.Double.doubleToRawLongBits(value) } ==
              expected.collect { case value: Double => java.lang.Double.doubleToRawLongBits(value) }
          case _ => actual == expected
        }
        equal && actual.length == values.length * 2 - 1 && actual.zipWithIndex.forall { case (value, index) =>
          if ((index & 1) == 0) true else value == separator
        }
      }
      assertTrue(
        check(JvmType.Boolean, Vector(false, true, false)),
        check(JvmType.Byte, Vector(Byte.MinValue, 0.toByte, Byte.MaxValue)),
        check(JvmType.Char, Vector(Char.MinValue, 'A', Char.MaxValue)),
        check(JvmType.Short, Vector(Short.MinValue, 0.toShort, Short.MaxValue)),
        check(JvmType.Int, Vector(Int.MinValue, 0, Int.MaxValue)),
        check(JvmType.Long, Vector(Long.MinValue, 0L, Long.MaxValue)),
        check(JvmType.Float, Vector(Float.NegativeInfinity, -0.0f, Float.NaN)),
        check(JvmType.Double, Vector(Double.NegativeInfinity, -0.0d, Double.NaN))
      )
    },
    test("widened async intersperse exposes delegated, cached, and closed readability states") {
      val reader = AsyncStatefulReader.intersperse[Int, Any](
        new ExactLaneProbe(JvmType.Int, Vector(1, 2)),
        "separator",
        JvmType.AnyRef
      )
      val initialReady = reader.readable().block
      val first        = reader.read[Any](null).block
      val sourceReady  = reader.readable().block
      val separator    = reader.read[Any](null).block
      val cachedReady  = reader.readable().block
      val second       = reader.read[Any](null).block
      reader.close().block
      val closedReady = reader.readable().block
      val closedValue = reader.read[Any](null).block
      assertTrue(
        initialReady,
        first == 1,
        sourceReady,
        separator == "separator",
        cachedReady,
        second == 2,
        !closedReady,
        closedValue == null
      )
    },
    test("primitive intersperse generic reads execute every exact scalar adapter") {
      val bytes   = AsyncStatefulReader.intersperse(Reader.fromChunk(Chunk[Byte](1, 2)).toAsync, 9.toByte)
      val ints    = AsyncStatefulReader.intersperse(Reader.fromChunk(Chunk(1, 2)).toAsync, 9)
      val longs   = AsyncStatefulReader.intersperse(Reader.fromChunk(Chunk(1L, 2L)).toAsync, 9L)
      val floats  = AsyncStatefulReader.intersperse(Reader.fromChunk(Chunk(1.0f, 2.0f)).toAsync, 9.0f)
      val doubles = AsyncStatefulReader.intersperse(Reader.fromChunk(Chunk(1.0d, 2.0d)).toAsync, 9.0d)
      assertTrue(
        bytes.read[Byte]((-1).toByte).block == 1.toByte,
        bytes.read[Byte]((-1).toByte).block == 9.toByte,
        ints.read[Int](-1).block == 1,
        ints.read[Int](-1).block == 9,
        longs.read[Long](-1L).block == 1L,
        longs.read[Long](-1L).block == 9L,
        floats.read[Float](Float.NaN).block == 1.0f,
        floats.read[Float](Float.NaN).block == 9.0f,
        doubles.read[Double](Double.NaN).block == 1.0d,
        doubles.read[Double](Double.NaN).block == 9.0d
      )
    },
    test("specialized bulk reads preserve offsets, extrema, NaN, and signed zero") {
      val bytes   = Array.fill[Byte](258)(42); val ints = Array.fill[Int](4)(7); val longs = Array.fill[Long](4)(7L)
      val floats  = Array.fill[Float](floatValues.length + 2)(1.0f);
      val doubles = Array.fill[Double](doubleValues.length + 2)(1.0d)
      val bn      = Reader.fromChunk(Chunk.fromIterable((-128 to 127).map(_.toByte))).toAsync.readBytes(bytes, 1, 256).block
      val in      = Reader.fromChunk(Chunk(Int.MinValue, Int.MaxValue)).toAsync.readInts(ints, 1, 2).block
      val ln      = Reader.fromChunk(Chunk(Long.MinValue, Long.MaxValue)).toAsync.readLongs(longs, 1, 2).block
      val fn      = Reader.fromChunk(floatValues).toAsync.readFloats(floats, 1, floatValues.length).block
      val dn      = Reader.fromChunk(doubleValues).toAsync.readDoubles(doubles, 1, doubleValues.length).block
      assertTrue(
        bn == 256,
        bytes.toVector == (42.toByte +: (-128 to 127).map(_.toByte).toVector :+ 42.toByte),
        in == 2,
        ints.toVector == Vector(7, Int.MinValue, Int.MaxValue, 7),
        ln == 2,
        longs.toVector == Vector(7L, Long.MinValue, Long.MaxValue, 7L),
        fn == floatValues.length,
        floatBits(Chunk.fromArray(floats).drop(1).take(floatValues.length)) == floatBits(floatValues),
        dn == doubleValues.length,
        doubleBits(Chunk.fromArray(doubles).drop(1).take(doubleValues.length)) == doubleBits(doubleValues)
      )
    },
    test("availability controls short bulk reads without invoking readable") {
      val unknown     = new AvailabilityIntReader(Reader.Unknown, Vector(1, 2, 3))
      val unavailable = new AvailabilityIntReader(Reader.Unavailable, Vector(1, 2, 3))
      val available   = new AvailabilityIntReader(Reader.Available, Vector(1, 2, 3))
      val bytes       = new AvailabilityByteReader(Reader.Available, Vector[Byte](-128, 0, 127))
      val ud          = new Array[Int](3); val nd = new Array[Int](3); val ad = new Array[Int](3); val bd = new Array[Byte](3)
      assertTrue(
        unknown.readInts(ud, 0, 3).block == 1,
        unknown.reads == 1,
        unknown.readableCalls == 0,
        unavailable.readInts(nd, 0, 3).block == 1,
        unavailable.reads == 1,
        unavailable.readableCalls == 0,
        available.readInts(ad, 0, 3).block == 3,
        ad.toVector == Vector(1, 2, 3),
        available.reads == 3,
        available.readableCalls == 0,
        bytes.readBytes(bd, 0, 3).block == 3,
        bd.toVector == Vector[Byte](-128, 0, 127),
        bytes.readableCalls == 0
      )
    },
    test("invalid and zero-length bulk ranges consume no input") {
      val reader   = new AvailabilityIntReader(Reader.Available, Vector(9, 10))
      val dest     = Array(1, 2)
      val negative = Try(reader.readInts(dest, 0, -1).block).isFailure
      val overflow = Try(reader.readInts(dest, 1, Int.MaxValue).block).isFailure
      val outside  = Try(reader.readInts(dest, 3, 0).block).isFailure
      val zero     = reader.readInts(dest, 2, 0).block
      val next     = reader.readInt(-1L).block
      assertTrue(negative, overflow, outside, zero == 0, reader.reads == 1, next == 9L, dest.toVector == Vector(1, 2))
    },
    test("async terminals preserve all lanes") {
      for {
        b <- run(Stream.fromChunk(Chunk(true, false)).runCollectAsync)
        y <- run(Stream.fromChunk(Chunk.fromIterable((-128 to 127).map(_.toByte))).runCollectAsync)
        c <- run(Stream.fromChunk(Chunk(Char.MinValue, Char.MaxValue)).runCollectAsync)
        s <- run(Stream.fromChunk(Chunk(Short.MinValue, Short.MaxValue)).runCollectAsync)
        i <- run(Stream.fromChunk(Chunk(Int.MinValue, Int.MaxValue)).runCollectAsync)
        l <- run(Stream.fromChunk(Chunk(Long.MinValue, Long.MaxValue)).runCollectAsync)
        f <- run(Stream.fromChunk(floatValues).runCollectAsync)
        d <- run(Stream.fromChunk(doubleValues).runCollectAsync)
        r <- run(Stream.fromChunk(Chunk("min", "max")).runCollectAsync)
      } yield assertTrue(
        b == Right(Chunk(true, false)),
        y == Right(Chunk.fromIterable((-128 to 127).map(_.toByte))),
        c == Right(Chunk(Char.MinValue, Char.MaxValue)),
        s == Right(Chunk(Short.MinValue, Short.MaxValue)),
        i == Right(Chunk(Int.MinValue, Int.MaxValue)),
        l == Right(Chunk(Long.MinValue, Long.MaxValue)),
        f.fold(_ => false, x => floatBits(x) == floatBits(floatValues)),
        d.fold(_ => false, x => doubleBits(x) == doubleBits(doubleValues)),
        r == Right(Chunk("min", "max"))
      )
    },
    test("transforms and parallel boundaries preserve sentinel-domain extrema") {
      val extrema     = Chunk(Int.MinValue, -1, 0, 1, Int.MaxValue)
      val transformed = Stream
        .fromChunk(extrema)
        .map(identity)
        .filter(_ != 0)
        .collect { case x if x != -1 => x }
        .takeWhile(_ != Int.MaxValue)
      val merged = Stream.mergeAll(2)(Stream(Stream(Int.MinValue), Stream(Int.MaxValue)))
      for {
        t <- run(transformed.runCollectAsync)
        m <- run(Stream.fromChunk(extrema).mapParAsync(2)(Async.succeed).runCollectAsync)
        f <- run(Stream.fromChunk(extrema).flatMapPar(2)(x => Stream.unwrap(Async.succeed(Stream(x)))).runCollectAsync)
        g <- run(merged.runCollectAsync)
      } yield assertTrue(
        t == Right(Chunk(Int.MinValue, 1)),
        m.fold(_ => false, _.toVector.sorted == extrema.toVector.sorted),
        f.fold(_ => false, _.toVector.sorted == extrema.toVector.sorted),
        g.fold(_ => false, _.toVector.sorted == Vector(Int.MinValue, Int.MaxValue))
      )
    },
    test("flatMap pulls and emits every primitive through its exact lane") {
      val nanFloat  = java.lang.Float.intBitsToFloat(0x7fc00001)
      val nanDouble = java.lang.Double.longBitsToDouble(0x7ff8000000000001L)
      for {
        booleans <- run(Stream(false, true).flatMap(Stream.succeed).runCollectAsync)
        bytes    <- run(Stream(Byte.MinValue, -1.toByte, Byte.MaxValue).flatMap(Stream.succeed).runCollectAsync)
        chars    <- run(Stream(Char.MinValue, 'A', Char.MaxValue).flatMap(Stream.succeed).runCollectAsync)
        shorts   <- run(Stream(Short.MinValue, -1.toShort, Short.MaxValue).flatMap(Stream.succeed).runCollectAsync)
        ints     <- run(Stream(Int.MinValue, -1, Int.MaxValue).flatMap(Stream.succeed).runCollectAsync)
        longs    <- run(Stream(Long.MinValue, -1L, Long.MaxValue).flatMap(Stream.succeed).runCollectAsync)
        floats   <- run(Stream(nanFloat, -0.0f, 0.0f).flatMap(Stream.succeed).runCollectAsync)
        doubles  <- run(Stream(nanDouble, -0.0d, 0.0d).flatMap(Stream.succeed).runCollectAsync)
      } yield assertTrue(
        booleans == Right(Chunk(false, true)),
        bytes == Right(Chunk(Byte.MinValue, -1.toByte, Byte.MaxValue)),
        chars == Right(Chunk(Char.MinValue, 'A', Char.MaxValue)),
        shorts == Right(Chunk(Short.MinValue, -1.toShort, Short.MaxValue)),
        ints == Right(Chunk(Int.MinValue, -1, Int.MaxValue)),
        longs == Right(Chunk(Long.MinValue, -1L, Long.MaxValue)),
        floats.fold(_ => false, values => floatBits(values) == floatBits(Chunk(nanFloat, -0.0f, 0.0f))),
        doubles.fold(_ => false, values => doubleBits(values) == doubleBits(Chunk(nanDouble, -0.0d, 0.0d)))
      )
    },
    test("full-domain downstream pulls do not suppress EOF from a smaller physical source") {
      val bridged = Stream
        .fromChunk(Chunk(-3L, -2L, -1L, 0L, 1L, 2L, 3L, 4L, 5L))
        .map(_.toInt)
        .filterAsync(value => Async.succeed(Math.floorMod(value, 4) == 0))
        .map(_.toDouble)
        .take(5)
        .mapAsync(value => Async.succeed(value))
        .take(3)
        .mapAsync(value => Async.succeed(value.toInt + 2))
      run(bridged.runCollectAsync).map(result => assertTrue(result == Right(Chunk(2, 6))))
    }
  )
}

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

import scala.concurrent.ExecutionContext

import zio.ZIO
import zio.blocks.async._
import zio.blocks.streams.internal.AsyncInterpreter
import zio.blocks.streams.internal.OpTag
import zio.blocks.streams.internal.StreamError
import zio.blocks.streams.io.Reader
import zio.test._

object AsyncInterpreterSpec extends StreamsBaseSpec {
  private implicit val ec: ExecutionContext = new ExecutionContext {
    def execute(runnable: Runnable): Unit     = Async.schedule(runnable, forceMacrotask = false)
    def reportFailure(cause: Throwable): Unit = throw cause
  }
  private val Noop = new Runnable { def run(): Unit = () }

  private sealed trait Folded[+A]
  private final case class Succeeded[A](value: A)                     extends Folded[A]
  private final case class Failed(cause: Throwable, trusted: Boolean) extends Folded[Nothing]
  private final case class Suspended[A](pollable: Pollable[A])        extends Folded[A]

  private def fold[A](async: Async[A]): Folded[A] =
    Async.foldStep(async)(new Async.StepFold[A, Folded[A]] {
      def success(value: A): Folded[A]                         = Succeeded(value)
      def failure(cause: Throwable): Folded[A]                 = Failed(cause, trusted = false)
      override def trustedFailure(cause: Throwable): Folded[A] = Failed(cause, trusted = true)
      def pending(pollable: Pollable[A]): Folded[A]            = Suspended(pollable)
    })

  private def run[A](async: Async[A]): ZIO[Any, Throwable, A] =
    ZIO.fromFuture(_ => async.toFuture)

  private class TestReader(next: () => Async[Any]) extends Reader.AsyncReader[Any] {
    val reads                                                      = new AtomicInteger(0)
    def close(): Async[Unit]                                       = Async.succeed(())
    def isClosed: Async[Boolean]                                   = Async.succeed(false)
    def readable(): Async[Boolean]                                 = Async.succeed(true)
    override private[streams] def tryReadable: Reader.Availability = Reader.Available
    def read[A >: Any](sentinel: A): Async[A]                      = {
      reads.incrementAndGet()
      next().asInstanceOf[Async[A]]
    }
    private def exact[A]: Async[A]                                                                             = { reads.incrementAndGet(); next().asInstanceOf[Async[A]] }
    override def readBoolean(s: Int)(implicit ev: Any <:< Boolean): Async[Int]                                 = exact
    override def readByte(): Async[Int]                                                                        = exact
    override def readChar(s: Int)(implicit ev: Any <:< Char): Async[Int]                                       = exact
    override def readShort(s: Int)(implicit ev: Any <:< Short): Async[Int]                                     = exact
    override def readInt(s: Long)(implicit ev: Any <:< Int): Async[Long]                                       = exact
    override def readLong(s: Long)(implicit ev: Any <:< Long): Async[Long]                                     = exact
    override def readFloat(s: Double)(implicit ev: Any <:< Float): Async[Double]                               = exact
    override def readDouble(s: Double)(implicit ev: Any <:< Double): Async[Double]                             = exact
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Any <:< Long): Async[Int] =
      readLong(Long.MaxValue).map(value => if (value == Long.MaxValue) -1 else { dest(offset) = value; 1 })
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Any <:< Double): Async[Int] =
      readDouble(Double.MaxValue).map(value => if (value == Double.MaxValue) -1 else { dest(offset) = value; 1 })
    def trusted(cause: Throwable): Async[Any] = failSource(cause)
    def typed(value: Any): Async[Any]         = failSource(value)
  }

  private final class TrustedBulkFailureReader(lane: JvmType, value: String) extends Reader.AsyncReader[Any] {
    def close(): Async[Unit]                                                              = Async.succeed(())
    def isClosed: Async[Boolean]                                                          = Async.succeed(false)
    override def jvmType: JvmType                                                         = lane
    def read[A >: Any](sentinel: A): Async[A]                                             = throw new AssertionError("generic read")
    def readable(): Async[Boolean]                                                        = Async.succeed(true)
    override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Async[Double] =
      throw new AssertionError("scalar readDouble")
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Any <:< Double): Async[Int] =
      failSource(value)
    override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Async[Long] =
      throw new AssertionError("scalar readLong")
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Any <:< Long): Async[Int] =
      failSource(value)
  }

  private final class LifecycleReader extends Reader.AsyncReader[Any] {
    private var values                        = List[Any](1, 2, 3)
    var closed                                = false
    val queries                               = new AtomicInteger(0)
    val controls                              = new AtomicInteger(0)
    def close(): Async[Unit]                  = { closed = true; Async.succeed(()) }
    def isClosed: Async[Boolean]              = { queries.incrementAndGet(); Async.succeed(closed) }
    def readable(): Async[Boolean]            = { queries.incrementAndGet(); Async.succeed(!closed && values.nonEmpty) }
    def read[A >: Any](sentinel: A): Async[A] =
      values match {
        case head :: tail => values = tail; Async.succeed(head.asInstanceOf[A])
        case Nil          => Async.succeed(sentinel)
      }
    override def skip(n: Long): Async[Unit] = {
      controls.incrementAndGet(); values = values.drop(math.max(0L, n).min(Int.MaxValue).toInt); Async.succeed(())
    }
    override def setLimit(n: Long): Async[Boolean] = { controls.incrementAndGet(); Async.succeed(n == 3L) }
    override def setRepeat(): Async[Boolean]       = { controls.incrementAndGet(); Async.succeed(true) }
    override def setSkip(n: Long): Async[Boolean]  = { controls.incrementAndGet(); Async.succeed(n == 1L) }
    override def reset(): Async[Unit]              = {
      controls.incrementAndGet(); values = List[Any](1, 2, 3); closed = false; Async.succeed(())
    }
  }

  private final class AvailabilityIntReader extends Reader.AsyncReader[Any] {
    private var values                        = List(1L, 2L, 3L)
    override def jvmType: JvmType             = JvmType.Int
    def close(): Async[Unit]                  = Async.succeed(())
    def isClosed: Async[Boolean]              = Async.succeed(false)
    def readable(): Async[Boolean]            = Async.succeed(false)
    def read[A >: Any](sentinel: A): Async[A] = values match {
      case head :: tail => values = tail; Async.succeed(head.toInt.asInstanceOf[A])
      case Nil          => Async.succeed(sentinel)
    }
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Async[Long] = values match {
      case head :: tail => values = tail; Async.succeed(head)
      case Nil          => Async.succeed(sentinel)
    }
  }

  private final class CountingCompleter extends Pollable[Any] {
    private val underlying                     = new Completer[Any]
    val polls                                  = new AtomicInteger(0)
    def poll(onComplete: Runnable): Async[Any] = {
      polls.incrementAndGet()
      underlying.poll(onComplete)
    }
    def succeed(value: Any): Unit = underlying.succeed(value)
  }

  private final class Replacement(value: Any) extends Pollable[Any] {
    val polls                                  = new AtomicInteger(0)
    def poll(onComplete: Runnable): Async[Any] = {
      polls.incrementAndGet()
      Async.succeed(value)
    }
  }

  private final class ReplaceOnce(next: Pollable[Any]) extends Pollable[Any] {
    val polls                                  = new AtomicInteger(0)
    def poll(onComplete: Runnable): Async[Any] = {
      polls.incrementAndGet()
      next
    }
  }

  private final class ReentrantWake(value: Any) extends Pollable[Any] {
    val polls                                  = new AtomicInteger(0)
    def poll(onComplete: Runnable): Async[Any] =
      if (polls.incrementAndGet() == 1) {
        onComplete.run()
        onComplete.run()
        this
      } else Async.succeed(value)
  }

  private final class CapturingTerminal(value: Any) extends Pollable[Any] {
    var captured: Runnable                     = null
    def poll(onComplete: Runnable): Async[Any] = {
      captured = onComplete
      Async.succeed(value)
    }
  }

  private final class CancelablePending(
    cleanup: Async[Unit],
    pollEntered: Completer[Unit] = null,
    cancelEntered: Completer[Unit] = null
  ) extends Async.Operation[Any] {
    val polls                                  = new AtomicInteger(0)
    val cancels                                = new AtomicInteger(0)
    def poll(onComplete: Runnable): Async[Any] = {
      polls.incrementAndGet()
      if (pollEntered ne null) pollEntered.succeed(())
      this
    }
    protected def cancelOperation(): Async[Unit] = {
      cancels.incrementAndGet()
      if (cancelEntered ne null) cancelEntered.succeed(())
      cleanup
    }
  }

  private final class CancelWithReplacement(onPoll: () => Unit, replacement: Pollable[Any])
      extends Async.Operation[Any] {
    val cancels                                = new AtomicInteger(0)
    def poll(onComplete: Runnable): Async[Any] = {
      onPoll()
      replacement
    }
    protected def cancelOperation(): Async[Unit] = {
      cancels.incrementAndGet()
      Async.succeed(())
    }
  }

  private final class CancelDuringPoll(onPoll: Runnable, result: Async[Any], cleanup: Async[Unit])
      extends Async.Operation[Any] {
    val polls                                  = new AtomicInteger(0)
    val cancels                                = new AtomicInteger(0)
    var captured: Runnable                     = null
    def poll(onComplete: Runnable): Async[Any] = {
      polls.incrementAndGet()
      captured = onComplete
      onPoll.run()
      if (result.asInstanceOf[AnyRef] eq null) this else result
    }
    protected def cancelOperation(): Async[Unit] = {
      cancels.incrementAndGet()
      cleanup
    }
  }

  private final class IntReader(values: List[Long], closeEffect: () => Async[Unit])
      extends TestReader(() => throw new AssertionError("generic read used for an Int reader")) {
    private var remaining                                                       = values
    val closes                                                                  = new AtomicInteger(0)
    override def jvmType: JvmType                                               = JvmType.Int
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Async[Long] = {
      reads.incrementAndGet()
      remaining match {
        case head :: tail => remaining = tail; Async.succeed(head)
        case Nil          => Async.succeed(Long.MinValue)
      }
    }
    override def close(): Async[Unit] = { closes.incrementAndGet(); closeEffect() }
  }

  private final class FailingLaneReader(lane: JvmType, cause: Throwable)
      extends TestReader(() => Async.failTrusted(cause)) {
    override def jvmType: JvmType                                                         = lane
    override def readBoolean(sentinel: Int)(implicit ev: Any <:< Boolean): Async[Int]     = Async.failTrusted(cause)
    override def readByte(): Async[Int]                                                   = Async.failTrusted(cause)
    override def readChar(sentinel: Int)(implicit ev: Any <:< Char): Async[Int]           = Async.failTrusted(cause)
    override def readShort(sentinel: Int)(implicit ev: Any <:< Short): Async[Int]         = Async.failTrusted(cause)
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Async[Long]           = Async.failTrusted(cause)
    override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Async[Long]         = Async.failTrusted(cause)
    override def readFloat(sentinel: Double)(implicit ev: Any <:< Float): Async[Double]   = Async.failTrusted(cause)
    override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Async[Double] =
      Async.failTrusted(cause)
  }

  private def streamForLane(value: Any, lane: JvmType): Stream[Nothing, Any] = (lane match {
    case JvmType.Boolean => Stream(value.asInstanceOf[Boolean])
    case JvmType.Byte    => Stream(value.asInstanceOf[Byte])
    case JvmType.Char    => Stream(value.asInstanceOf[Char])
    case JvmType.Short   => Stream(value.asInstanceOf[Short])
    case JvmType.Int     => Stream(value.asInstanceOf[Int])
    case JvmType.Long    => Stream(value.asInstanceOf[Long])
    case JvmType.Float   => Stream(value.asInstanceOf[Float])
    case JvmType.Double  => Stream(value.asInstanceOf[Double])
    case JvmType.AnyRef  => Stream(value.asInstanceOf[AnyRef])
  }).asInstanceOf[Stream[Nothing, Any]]

  private def twoForLane(value: Any, lane: JvmType): Stream[Nothing, Any] = (lane match {
    case JvmType.Boolean => Stream(value.asInstanceOf[Boolean], value.asInstanceOf[Boolean])
    case JvmType.Byte    => Stream(value.asInstanceOf[Byte], value.asInstanceOf[Byte])
    case JvmType.Char    => Stream(value.asInstanceOf[Char], value.asInstanceOf[Char])
    case JvmType.Short   => Stream(value.asInstanceOf[Short], value.asInstanceOf[Short])
    case JvmType.Int     => Stream(value.asInstanceOf[Int], value.asInstanceOf[Int])
    case JvmType.Long    => Stream(value.asInstanceOf[Long], value.asInstanceOf[Long])
    case JvmType.Float   => Stream(value.asInstanceOf[Float], value.asInstanceOf[Float])
    case JvmType.Double  => Stream(value.asInstanceOf[Double], value.asInstanceOf[Double])
    case JvmType.AnyRef  => Stream(value.asInstanceOf[AnyRef], value.asInstanceOf[AnyRef])
  }).asInstanceOf[Stream[Nothing, Any]]

  private def pullLane(interpreter: AsyncInterpreter, lane: JvmType): Async[Any] = (lane match {
    case JvmType.Boolean => interpreter.readBoolean(-1)
    case JvmType.Byte    => interpreter.readByte()
    case JvmType.Char    => interpreter.readChar(-1)
    case JvmType.Short   => interpreter.readShort(-1)
    case JvmType.Int     => interpreter.readInt(Long.MinValue)
    case JvmType.Long    => interpreter.readLong(Long.MinValue)
    case JvmType.Float   => interpreter.readFloat(Double.NaN)
    case JvmType.Double  => interpreter.readDouble(Double.NaN)
    case JvmType.AnyRef  => interpreter.read[Any]("eof")
  }).asInstanceOf[Async[Any]]

  private final class ClosePending extends Async.Operation[Unit] {
    private val done                            = new Completer[Unit]
    val pollEntered                             = new Completer[Unit]
    val polls                                   = new AtomicInteger(0)
    val cancels                                 = new AtomicInteger(0)
    def poll(onComplete: Runnable): Async[Unit] = {
      polls.incrementAndGet()
      pollEntered.succeed(())
      done.poll(onComplete)
    }
    protected def cancelOperation(): Async[Unit] = { cancels.incrementAndGet(); Async.succeed(()) }
    def succeed(): Unit                          = done.succeed(())
  }

  private final class CloseReplacement(remaining: Int, polls: AtomicInteger) extends Pollable[Unit] {
    def poll(onComplete: Runnable): Async[Unit] = {
      polls.incrementAndGet()
      if (remaining == 0) Async.succeed(()) else new CloseReplacement(remaining - 1, polls)
    }
  }

  def spec = suite("AsyncInterpreter")(
    test("bulk controls preserve trusted Long and Double failures and sticky replay") {
      def check(lane: JvmType, value: String): ZIO[Any, Throwable, TestResult] = {
        val interpreter = new AsyncInterpreter(new TrustedBulkFailureReader(lane, value))
        val first       = lane match {
          case JvmType.Long   => interpreter.readLongs(new Array[Long](1), 0, 1)
          case JvmType.Double => interpreter.readDoubles(new Array[Double](1), 0, 1)
          case other          => throw new AssertionError(s"unexpected lane $other")
        }
        for {
          _        <- run(first.either)
          firstFold = fold(first.asInstanceOf[Pollable[Int]].poll(Noop))
          replay    = lane match {
                     case JvmType.Long   => interpreter.readLongs(new Array[Long](1), 0, 1)
                     case JvmType.Double => interpreter.readDoubles(new Array[Double](1), 0, 1)
                     case other          => throw new AssertionError(s"unexpected lane $other")
                   }
          _         <- run(replay.either)
          replayFold = fold(replay.asInstanceOf[Pollable[Int]].poll(Noop))
        } yield assertTrue(
          firstFold match {
            case Failed(error: StreamError, true) => error.value == value
            case _                                => false
          },
          replayFold == firstFold
        )
      }
      for {
        long   <- check(JvmType.Long, "typed-long")
        double <- check(JvmType.Double, "typed-double")
      } yield long && double
    },
    test("lane-aware readN preserves every logical lane and zero-length ownership") {
      val bools   = AsyncInterpreter.fromStream(Stream(false, true))
      val bytes   = AsyncInterpreter.fromStream(Stream(1.toByte, (-1).toByte))
      val chars   = AsyncInterpreter.fromStream(Stream('a', '\uffff'))
      val shorts  = AsyncInterpreter.fromStream(Stream(1.toShort, (-1).toShort))
      val ints    = AsyncInterpreter.fromStream(Stream(1, 2))
      val longs   = AsyncInterpreter.fromStream(Stream(1L, 2L))
      val floats  = AsyncInterpreter.fromStream(Stream(1.5f, 2.5f))
      val doubles = AsyncInterpreter.fromStream(Stream(1.5d, 2.5d))
      val refs    = AsyncInterpreter.fromStream(Stream("a", "b"))
      val huge    = AsyncInterpreter.fromStream(Stream(1, 2))
      for {
        b  <- run(bools.readN[Boolean](3))
        by <- run(bytes.readN[Byte](3))
        c  <- run(chars.readN[Char](3))
        s  <- run(shorts.readN[Short](3))
        i  <- run(ints.readN[Int](3))
        l  <- run(longs.readN[Long](3))
        f  <- run(floats.readN[Float](3))
        d  <- run(doubles.readN[Double](3))
        r  <- run(refs.readN[String](3))
        z  <- run(refs.readN[String](0))
        h  <- run(huge.readN[Int](Int.MaxValue))
      } yield assertTrue(
        b == zio.blocks.chunk.Chunk(false, true),
        by == zio.blocks.chunk.Chunk(1.toByte, (-1).toByte),
        c == zio.blocks.chunk.Chunk('a', '\uffff'),
        s == zio.blocks.chunk.Chunk(1.toShort, (-1).toShort),
        i == zio.blocks.chunk.Chunk(1, 2),
        l == zio.blocks.chunk.Chunk(1L, 2L),
        f == zio.blocks.chunk.Chunk(1.5f, 2.5f),
        d == zio.blocks.chunk.Chunk(1.5d, 2.5d),
        r == zio.blocks.chunk.Chunk("a", "b"),
        z.isEmpty,
        h == zio.blocks.chunk.Chunk(1, 2)
      )
    },
    test("primitive bulk operations retain specialization, ranges, offsets, and EOF counts") {
      val bytes      = AsyncInterpreter.fromStream(Stream(1.toByte, 2.toByte))
      val ints       = AsyncInterpreter.fromStream(Stream(1, 2))
      val longs      = AsyncInterpreter.fromStream(Stream(1L, 2L))
      val floats     = AsyncInterpreter.fromStream(Stream(1.5f, 2.5f))
      val doubles    = AsyncInterpreter.fromStream(Stream(1.5d, 2.5d))
      val ba         = Array.fill[Byte](4)(9)
      val ia         = Array.fill[Int](4)(9)
      val la         = Array.fill[Long](4)(9L)
      val fa         = Array.fill[Float](4)(9f)
      val da         = Array.fill[Double](4)(9d)
      val invalid    = ints.readInts(ia, -1, 1)
      val maxLong    = AsyncInterpreter.fromStream(Stream(0).map(_ => Long.MaxValue))
      val maxDouble  = AsyncInterpreter.fromStream(Stream(0).map(_ => Double.MaxValue))
      val maxLongs   = new Array[Long](1)
      val maxDoubles = new Array[Double](1)
      for {
        invalidResult <- run(invalid.either)
        bn            <- run(bytes.readBytes(ba, 1, 3))
        in            <- run(ints.readInts(ia, 1, 3))
        ln            <- run(longs.readLongs(la, 1, 3))
        fn            <- run(floats.readFloats(fa, 1, 3))
        dn            <- run(doubles.readDoubles(da, 1, 3))
        bz            <- run(bytes.readBytes(ba, 0, 0))
        mln           <- run(maxLong.readLongs(maxLongs, 0, 1))
        mdn           <- run(maxDouble.readDoubles(maxDoubles, 0, 1))
      } yield assertTrue(
        invalidResult.left.exists(_.isInstanceOf[IndexOutOfBoundsException]),
        bn == 2,
        in == 2,
        ln == 2,
        fn == 2,
        dn == 2,
        bz == 0,
        mln == 1,
        mdn == 1,
        maxLongs(0) == Long.MaxValue,
        maxDoubles(0) == Double.MaxValue,
        ba.sameElements(Array[Byte](9, 1, 2, 9)),
        ia.sameElements(Array(9, 1, 2, 9)),
        la.sameElements(Array(9L, 1L, 2L, 9L)),
        fa.sameElements(Array(9f, 1.5f, 2.5f, 9f)),
        da.sameElements(Array(9d, 1.5d, 2.5d, 9d))
      )
    },
    test("bulk exact and available reads preserve EOF, extrema, specialization, and ready budgets") {
      val exact = new AsyncInterpreter(new AvailabilityIntReader)
      for {
        exactChunk <- run(exact.readN[Int](3))
      } yield assertTrue(
        exactChunk == zio.blocks.chunk.Chunk(1, 2, 3),
        exactChunk.isInstanceOf[zio.blocks.chunk.Chunk.IntArray]
      )
    },
    test("readUpToN stops at an unavailable boundary after its committed prefix") {
      val available = new AsyncInterpreter(new AvailabilityIntReader)
      run(available.readUpToN[Int](3)).map(chunk => assertTrue(chunk == zio.blocks.chunk.Chunk(1)))
    },
    test("bulk operations report an already exhausted reader without sentinel data") {
      val exhausted = AsyncInterpreter.fromStream(Stream(1))
      for {
        _     <- run(exhausted.readInt(-1L))
        empty <- run(exhausted.readN[Int](2))
      } yield assertTrue(empty.isEmpty)
    },
    test("bulk operations report an already closed reader without sentinel writes") {
      val closed = AsyncInterpreter.fromStream(Stream(1L))
      val dest   = Array(7L, 7L)
      for {
        _           <- run(closed.closeForTest())
        closedCount <- run(closed.readLongs(dest, 0, 2))
      } yield assertTrue(closedCount == -1, dest.sameElements(Array(7L, 7L)))
    },
    test("bulk operations retain values equal to primitive EOF sentinels") {
      val maxLong   = AsyncInterpreter.fromStream(Stream(0, 1).map(i => if (i == 0) Long.MaxValue else 1L))
      val maxDouble = AsyncInterpreter.fromStream(Stream(0, 1).map(i => if (i == 0) Double.MaxValue else 1d))
      for {
        maxLongChunk   <- run(maxLong.readN[Long](2))
        maxDoubleChunk <- run(maxDouble.readUpToN[Double](2))
      } yield assertTrue(
        maxLongChunk == zio.blocks.chunk.Chunk(Long.MaxValue, 1L),
        maxDoubleChunk == zio.blocks.chunk.Chunk(Double.MaxValue, 1d),
        implicitly[JvmType.Infer[Long]].jvmType == JvmType.Long,
        implicitly[JvmType.Infer[Double]].jvmType == JvmType.Double,
        maxLong.jvmType == JvmType.Long,
        maxDouble.jvmType == JvmType.Double,
        maxLongChunk.isInstanceOf[zio.blocks.chunk.Chunk.LongArray],
        maxDoubleChunk.isInstanceOf[zio.blocks.chunk.Chunk.DoubleArray]
      )
    },
    test("bulk reads cross ready budgets without recursive continuation growth") {
      val large = AsyncInterpreter.fromStream(Stream.range(0, 600))
      run(large.readN[Int](600)).map(chunk => assertTrue(chunk.length == 600, chunk(599) == 599))
    },
    test("Boolean readUpToN grows beyond its initial packed-byte hint") {
      val interpreter = AsyncInterpreter.fromStream(Stream.range(0, 73).map(_ % 2 == 0))
      run(interpreter.readUpToN[Boolean](73)).map { chunk =>
        assertTrue(chunk == zio.blocks.chunk.Chunk.fromIterable((0 until 73).map(_ % 2 == 0)))
      }
    },
    test("bulk cancellation owns pending pulls and sticky failures include zero-length operations") {
      var calls       = 0
      val pollEntered = new Completer[Unit]
      val leaf        = new CancelablePending(Async.succeed(()), pollEntered)
      val reader      = new TestReader(() => {
        calls += 1
        if (calls == 1) Async.succeed("first")
        else if (calls == 2) leaf.asInstanceOf[Async[Any]]
        else Async.succeed("next")
      })
      val interpreter = new AsyncInterpreter(reader)
      val operation   = interpreter.readN[Any](3)
      operation.toFuture

      lazy val failedReader: TestReader = new TestReader(() => failedReader.typed("bulk-failure"))
      val failedInterpreter             = new AsyncInterpreter(failedReader)
      val firstFailure                  = fold(failedInterpreter.read[Any]("eof").asInstanceOf[Pollable[Any]].poll(Noop))
        .asInstanceOf[Failed]
        .cause
      val zeroDest = new Array[Int](0)
      for {
        _             <- run(pollEntered)
        _             <- run(Async.cancelWithCleanup(operation.asInstanceOf[Pollable[zio.blocks.chunk.Chunk[Any]]]))
        prefix        <- run(operation)
        next          <- run(interpreter.read[Any]("eof"))
        zeroChunk     <- run(failedInterpreter.readN[Any](0).either)
        negativeChunk <- run(failedInterpreter.readUpToN[Any](-1).either)
        zeroArray     <- run(failedInterpreter.readInts(zeroDest, 0, 0).either)
      } yield assertTrue(
        leaf.polls.get() > 0,
        leaf.cancels.get() == 1,
        prefix == zio.blocks.chunk.Chunk("first"),
        next == "next",
        zeroChunk == Left(firstFailure),
        negativeChunk == Left(firstFailure),
        zeroArray == Left(firstFailure)
      )
    },
    test("bulk availability is non-destructive and cancellation-cleanup failures become sticky") {
      val availabilityFailure = new RuntimeException("bulk-readable")
      val availabilityCalls   = new AtomicInteger(0)
      val availabilityReader  = new TestReader(() => Async.succeed("value")) {
        override private[streams] def tryReadable: Reader.Availability = Reader.Unknown
        override def readable(): Async[Boolean]                        = {
          availabilityCalls.incrementAndGet()
          Async.fail(availabilityFailure)
        }
      }
      val availabilityInterpreter = new AsyncInterpreter(availabilityReader)
      val cleanupFailure          = new RuntimeException("bulk-cancel-cleanup")
      val pollEntered             = new Completer[Unit]
      val pending                 = new CancelablePending(Async.fail(cleanupFailure), pollEntered)
      val cleanupInterpreter      = new AsyncInterpreter(new TestReader(() => pending))
      val operation               = cleanupInterpreter.readN[Any](2)
      operation.toFuture
      for {
        availabilityResult <- run(availabilityInterpreter.readUpToN[Any](2).either)
        availabilityReplay <- run(availabilityInterpreter.read[Any]("eof").either)
        _                  <- run(pollEntered)
        cleanupResult      <-
          run(Async.cancelWithCleanup(operation.asInstanceOf[Pollable[zio.blocks.chunk.Chunk[Any]]]).either)
        cleanupReplay <- run(cleanupInterpreter.read[Any]("eof").either)
      } yield assertTrue(
        availabilityResult == Right(zio.blocks.chunk.Chunk("value")),
        availabilityReplay == Right("value"),
        availabilityCalls.get() == 0,
        pending.polls.get() > 0,
        cleanupResult == Left(cleanupFailure),
        cleanupReplay == Left(cleanupFailure)
      )
    },
    test("bulk availability never invokes the potentially asynchronous readable query") {
      var interpreter: AsyncInterpreter = null
      val closes                        = new AtomicInteger(0)
      val queries                       = new AtomicInteger(0)
      val reader                        = new TestReader(() => Async.succeed("value")) {
        override def close(): Async[Unit]       = { closes.incrementAndGet(); Async.succeed(()) }
        override def readable(): Async[Boolean] = {
          queries.incrementAndGet()
          interpreter.closeForTest().map(_ => true)
        }
        override private[streams] def tryReadable: Reader.Availability = Reader.Unknown
      }
      interpreter = new AsyncInterpreter(reader)
      val operation = interpreter.readUpToN[Any](2)
      for {
        prefix <- run(operation)
        open   <- run(interpreter.isClosed)
        _      <- run(interpreter.closeForTest())
      } yield assertTrue(
        prefix == zio.blocks.chunk.Chunk("value"),
        !open,
        queries.get() == 0,
        closes.get() == 1
      )
    },
    test("supported Stream graphs materialize and execute MAP/FILTER") {
      val chunk =
        AsyncInterpreter.fromStream(Stream.fromChunk(zio.blocks.chunk.Chunk(1, 2, 3)).map(_ + 1).filter(_ > 2))
      val made   = new AtomicInteger(0)
      val reader = AsyncInterpreter.fromStream(Stream.fromReader {
        made.incrementAndGet()
        Reader.fromChunk(zio.blocks.chunk.Chunk(4, 5))
      }.map(_ * 2).filter(_ > 8))
      for {
        chunkValue  <- run(chunk.readInt(-1L))
        readerValue <- run(reader.readInt(-1L))
      } yield assertTrue(chunkValue == 3L, readerValue == 10L, made.get() == 1)
    },
    test("COLLECT preserves partial-function semantics across every output lane") {
      val calls = new AtomicInteger(0)
      val ints  = AsyncInterpreter.fromStream(Stream(1, 2, 3).collect {
        case value if { calls.incrementAndGet(); value == 2 } => value * 10
      })
      val longs   = AsyncInterpreter.fromStream(Stream(1, 2).collect { case 2 => 20L })
      val floats  = AsyncInterpreter.fromStream(Stream(1, 2).collect { case 2 => 2.5f })
      val doubles = AsyncInterpreter.fromStream(Stream(1, 2).collect { case 2 => 2.5d })
      val refs    = AsyncInterpreter.fromStream(Stream(1, 2).collect { case 2 => "two" })
      val bools   = AsyncInterpreter.fromStream(Stream(false, true).collect[Boolean] { case true => false })
      for {
        intValue    <- run(ints.readInt(-1L))
        intEnd      <- run(ints.readInt(-1L))
        longValue   <- run(longs.readLong(-1L))
        floatValue  <- run(floats.readFloat(-1.0))
        doubleValue <- run(doubles.readDouble(-1.0))
        refValue    <- run(refs.read[Any]("end"))
        boolValue   <- run(bools.readBoolean(-1))
      } yield assertTrue(
        intValue == 20L,
        intEnd == -1L,
        longValue == 20L,
        floatValue == 2.5,
        doubleValue == 2.5,
        refValue == "two",
        boolValue == 0,
        calls.get() == 3,
        bools.jvmType == JvmType.Boolean
      )
    },
    test("COLLECT callback failure is sticky and preserves identity") {
      val cause       = new RuntimeException("collect")
      val interpreter = AsyncInterpreter.fromStream(Stream(1).collect[Int] { case _ => throw cause })
      val first       = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      val replay      = fold(interpreter.readInt(-2L).asInstanceOf[Pollable[Long]].poll(Noop))
      assertTrue(first == Failed(cause, trusted = false), replay == first)
    },
    test("nested FlatMapped encoding preserves outgoing order and Boolean PUSH adaptation") {
      val calls       = new AtomicInteger(0)
      var sawBoolean  = false
      val interpreter = AsyncInterpreter.fromStream(
        Stream(true).flatMap { value => calls.incrementAndGet(); sawBoolean = value; Stream(1) }
          .map(_ + 1)
          .flatMap { value => calls.incrementAndGet(); Stream(value) }
      )
      val before = calls.get()
      interpreter.incomingOp(1).asInstanceOf[Int => AnyRef](1)
      assertTrue(
        before == 0,
        calls.get() == 1,
        sawBoolean,
        interpreter.incomingCount == 2,
        OpTag.isPush(interpreter.incomingTag(1)),
        interpreter.outgoingCount == 2,
        OpTag.isMap(interpreter.outgoingTag(0)),
        OpTag.isPush(interpreter.outgoingTag(1))
      )
    },
    test("pristine interpreter readers fuse sink maps and started readers fall back") {
      val syncInterpreter  = AsyncInterpreter.fromStream(Stream(1))
      val syncReader       = syncInterpreter.toReader[Int]
      val syncFused        = AsyncInterpreter.fuseMap[Int, Long](syncReader, JvmType.Int, JvmType.Long, _.toLong)
      val asyncInterpreter = AsyncInterpreter.fromStream(Stream(1))
      val asyncReader      = asyncInterpreter.toReader[Int]
      val asyncFused       = AsyncInterpreter.fuseAsyncMap[Int, Long](
        asyncReader,
        JvmType.Int,
        JvmType.Long,
        value => Async.succeed(value.toLong)
      )
      val startedInterpreter = AsyncInterpreter.fromStream(Stream(1))
      val startedReader      = startedInterpreter.toReader[Int]
      val _                  = startedReader.readInt(-1L)
      val fallback           = AsyncInterpreter.fuseMap[Int, Long](startedReader, JvmType.Int, JvmType.Long, _.toLong)
      assertTrue(
        syncFused.asInstanceOf[AnyRef] eq syncReader.asInstanceOf[AnyRef],
        syncInterpreter.incomingCount == 2,
        syncInterpreter.jvmType == JvmType.Long,
        asyncFused.asInstanceOf[AnyRef] eq asyncReader.asInstanceOf[AnyRef],
        asyncInterpreter.incomingCount == 2,
        asyncInterpreter.jvmType == JvmType.Long,
        fallback == null,
        startedInterpreter.incomingCount == 1,
        startedInterpreter.jvmType == JvmType.Int
      )
    },
    test("synchronous PUSH executes incoming and outgoing operations") {
      val order       = new StringBuilder
      val interpreter = AsyncInterpreter.fromStream(
        Stream(1, 2).flatMap { value => order.append('p'); Stream(value + 10) }.map { value =>
          order.append('m'); value * 2
        }.filter { value => order.append('f'); value > 20 }.flatMap { value => order.append('q'); Stream(value + 1) }
      )
      for {
        first  <- run(interpreter.readInt(-1L))
        second <- run(interpreter.readInt(-1L))
        end    <- run(interpreter.readInt(-1L))
      } yield assertTrue(first == 23L, second == 25L, end == -1L, order.toString == "pmfqpmfq")
    },
    test("PUSH bridges to its target type before the final outgoing type") {
      val longs    = AsyncInterpreter.fromStream(Stream(1).flatMap(value => Stream(value + 1)).map(_.toLong + 10L))
      val booleans = AsyncInterpreter.fromStream(
        Stream(1).flatMap(value => Stream(value != 0)).map(value => if (value) 7 else 0)
      )
      for {
        longValue    <- run(longs.readLong(-1L))
        booleanValue <- run(booleans.readInt(-1L))
      } yield assertTrue(longValue == 12L, booleanValue == 7L)
    },
    test("PUSH adapts callback input on every physical lane") {
      val int    = AsyncInterpreter.fromStream(Stream(1).flatMap(value => Stream(value + 1)))
      val long   = AsyncInterpreter.fromStream(Stream(2L).flatMap(value => Stream(value + 1L)))
      val float  = AsyncInterpreter.fromStream(Stream(3.0f).flatMap(value => Stream(value + 1.0f)))
      val double = AsyncInterpreter.fromStream(Stream(4.0).flatMap(value => Stream(value + 1.0)))
      val ref    = AsyncInterpreter.fromStream(Stream("a").flatMap(value => Stream(value + "b")))
      for {
        i <- run(int.readInt(-1L))
        l <- run(long.readLong(-1L))
        f <- run(float.readFloat(-1.0))
        d <- run(double.readDouble(-1.0))
        r <- run(ref.read("end"))
      } yield assertTrue(i == 2L, l == 3L, f == 4.0, d == 5.0, r == "ab")
    },
    test("outgoing PUSH reruns prior outgoing operations for every inner and outer value") {
      val order       = new StringBuilder
      val interpreter = AsyncInterpreter.fromStream(
        Stream(1, 2)
          .flatMap(value => Stream(value, value + 10))
          .map { value => order.append(s"m$value;"); value * 2 }
          .flatMap { value => order.append(s"p$value;"); Stream(value, value + 1) }
      )
      for {
        a <- run(interpreter.readInt(-1L)); b <- run(interpreter.readInt(-1L))
        c <- run(interpreter.readInt(-1L)); d <- run(interpreter.readInt(-1L))
        e <- run(interpreter.readInt(-1L)); f <- run(interpreter.readInt(-1L))
        g <-
          run(interpreter.readInt(-1L));
        h <- run(interpreter.readInt(-1L))
      } yield assertTrue(
        List(a, b, c, d, e, f, g, h) == List(2L, 3L, 22L, 23L, 4L, 5L, 24L, 25L),
        order.toString == "m1;p2;m11;p22;m2;p4;m12;p24;"
      )
    },
    test("ten thousand empty PUSH inners complete stack-safely") {
      val interpreter = AsyncInterpreter.fromStream(
        Stream
          .fromChunk(zio.blocks.chunk.Chunk.fromIterable(0 until 10000))
          .flatMap(_ => Stream.fromChunk(zio.blocks.chunk.Chunk.empty[Int]))
      )
      run(interpreter.readInt(-1L)).map(value => assertTrue(value == -1L))
    },
    test("six thousand nested non-empty PUSH inners complete stack-safely") {
      var stream: Stream[Nothing, Int] = Stream(1)
      var depth                        = 0
      while (depth < 6000) {
        stream = stream.flatMap(value => Stream(value + 1))
        depth += 1
      }
      val interpreter = AsyncInterpreter.fromStream(stream)
      run(interpreter.readInt(-1L)).map(value => assertTrue(value == 6001L))
    },
    test("deep PUSH cancellation closes every installed owner stack-safely") {
      val closes                       = new AtomicInteger(0)
      var stream: Stream[Nothing, Int] = Stream(1)
      var depth                        = 0
      while (depth < 3500) {
        stream = stream.flatMap(value =>
          Stream.fromReader(new Reader.SyncReader[Int] {
            private var ready                  = true
            def isClosed: Boolean              = !ready
            def read[A >: Int](sentinel: A): A = if (ready) { ready = false; value }
            else sentinel
            def close(): Unit = { closes.incrementAndGet(); ready = false }
          })
        )
        depth += 1
      }
      var operation: Async[Long] = null
      var cleanup: Async[Unit]   = null
      val interpreter            = AsyncInterpreter.fromStream(stream.map { value =>
        cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
        Async.startRegistered(cleanup)(_ => ())
        value
      })
      operation = interpreter.readInt(-1L)
      for {
        value <- run(operation)
        _     <- run(cleanup)
      } yield assertTrue(value == -1L, closes.get() == 3500)
    },
    test("PUSH callback failures are sticky and later outgoing callbacks stay silent") {
      val cause       = new RuntimeException("push")
      val later       = new AtomicInteger(0)
      val interpreter = AsyncInterpreter.fromStream(
        Stream(1).flatMap(_ => (throw cause): Stream[Nothing, Int]).map { value => later.incrementAndGet(); value }
      )
      val first  = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      val replay = fold(interpreter.readInt(-2L).asInstanceOf[Pollable[Long]].poll(Noop))
      assertTrue(first == Failed(cause, trusted = false), replay == first, later.get() == 0)
    },
    test("PUSH rejects null and materializes fallback produced streams without corrupting the parent") {
      val fallback = new Stream[Nothing, Int] {
        def render                                                                                           = "fallback"
        private[streams] def compile(depth: Int, bufferSize: Int)                                            = Reader.singleInt(1)
        private[streams] def compileInterpreter(pipeline: zio.blocks.streams.internal.SyncInterpreter): Unit =
          pipeline.appendRead(Reader.singleInt(1))
      }
      val nullInterpreter = AsyncInterpreter.fromStream(
        Stream(1).flatMap(_ => null.asInstanceOf[Stream[Nothing, Int]])
      )
      val fallbackInterpreter = AsyncInterpreter.fromStream(Stream(1).flatMap(_ => fallback))
      for {
        nullFailure <- run(nullInterpreter.readInt(-1L).either)
        value       <- run(fallbackInterpreter.readInt(-1L))
      } yield assertTrue(
        nullFailure.left.exists(_.isInstanceOf[NullPointerException]),
        value == 1L
      )
    },
    test("cancellation during PUSH materialization rejects and closes the late owner before settling") {
      val closes                 = new AtomicInteger(0)
      var operation: Async[Long] = null
      var cleanup: Async[Unit]   = null
      val interpreter            = AsyncInterpreter.fromStream(
        Stream(1, 2).flatMap { value =>
          Stream.fromReader {
            cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
            new Reader.SyncReader[Int] {
              def isClosed: Boolean              = false
              def read[A >: Int](sentinel: A): A = value
              def close(): Unit                  = { closes.incrementAndGet(); () }
            }
          }
        }
      )
      operation = interpreter.readInt(-1L)
      val cancelled = operation.asInstanceOf[Pollable[Long]].poll(Noop)
      for {
        _     <- run(cleanup)
        value <- run(cancelled)
        next  <- run(interpreter.readInt(-2L))
      } yield assertTrue(value == -1L, next == 2L, closes.get() == 1)
    },
    test("rejected PUSH cleanup failure fails cancellation and becomes sticky") {
      val closeFailure           = new RuntimeException("rejected close")
      val closes                 = new AtomicInteger(0)
      var operation: Async[Long] = null
      var cleanup: Async[Unit]   = null
      val interpreter            = AsyncInterpreter.fromStream(
        Stream(1).flatMap { value =>
          Stream.fromReader {
            cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
            new Reader.SyncReader[Int] {
              def isClosed: Boolean              = false
              def read[A >: Int](sentinel: A): A = value
              def close(): Unit                  = { closes.incrementAndGet(); throw closeFailure }
            }
          }
        }
      )
      operation = interpreter.readInt(-1L)
      operation.asInstanceOf[Pollable[Long]].poll(Noop)
      for {
        cancelled <- run(cleanup.either)
        replay    <- run(interpreter.readInt(-2L).either)
      } yield assertTrue(cancelled == Left(closeFailure), replay == Left(closeFailure), closes.get() == 1)
    },
    test("cancellation from an outgoing MAP closes the installed PUSH owner before settling") {
      val closes                 = new AtomicInteger(0)
      val maps                   = new AtomicInteger(0)
      var operation: Async[Long] = null
      var cleanup: Async[Unit]   = null
      val interpreter            = AsyncInterpreter.fromStream(
        Stream(1, 2).flatMap { value =>
          Stream.fromReader(new Reader.SyncReader[Int] {
            private var ready                  = true
            def isClosed: Boolean              = !ready
            def read[A >: Int](sentinel: A): A = if (ready) { ready = false; value }
            else sentinel
            def close(): Unit = { closes.incrementAndGet(); ready = false }
          })
        }.map { value =>
          if (maps.incrementAndGet() == 1)
            cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
          value
        }
      )
      operation = interpreter.readInt(-1L)
      val cancelled = operation.asInstanceOf[Pollable[Long]].poll(Noop)
      for {
        _     <- run(cleanup)
        value <- run(cancelled)
        next  <- run(interpreter.readInt(-2L))
      } yield assertTrue(value == -1L, next == 2L, closes.get() == 1, maps.get() == 2)
    },
    test("PUSH cancellation restores a cross-lane output checkpoint") {
      val maps                  = new AtomicInteger(0)
      var operation: Async[Any] = null
      var cleanup: Async[Unit]  = null
      val interpreter           = AsyncInterpreter.fromStream(
        Stream(1, 2)
          .flatMap(value => Stream(value))
          .map { value =>
            if (maps.incrementAndGet() == 1)
              cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Any]])
            s"v=$value"
          }
      )
      operation = interpreter.read[Any]("cancelled")
      val cancelled = operation.asInstanceOf[Pollable[Any]].poll(Noop)
      for {
        _     <- run(cleanup)
        value <- run(cancelled)
        next  <- run(interpreter.read[Any]("eof"))
      } yield assertTrue(value == "cancelled", next == "v=2", maps.get() == 2)
    },
    test("cancellation during a second PUSH materialization closes installed and rejected owners") {
      val closeOrder                     = new StringBuilder
      val secondCalls                    = new AtomicInteger(0)
      var operation: Async[Long]         = null
      var cleanup: Async[Unit]           = null
      def owner(value: Int, label: Char) = new Reader.SyncReader[Int] {
        private var ready                  = true
        def isClosed: Boolean              = !ready
        def read[A >: Int](sentinel: A): A = if (ready) { ready = false; value }
        else sentinel
        def close(): Unit = { closeOrder.append(label); ready = false }
      }
      val interpreter = AsyncInterpreter.fromStream(
        Stream(1, 2)
          .flatMap(value => Stream.fromReader(owner(value, 'a')))
          .flatMap { value =>
            Stream.fromReader {
              if (secondCalls.incrementAndGet() == 1)
                cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
              owner(value, 'b')
            }
          }
      )
      operation = interpreter.readInt(-1L)
      val cancelled = operation.asInstanceOf[Pollable[Long]].poll(Noop)
      for {
        _     <- run(cleanup)
        value <- run(cancelled)
        next  <- run(interpreter.readInt(-2L))
      } yield assertTrue(value == -1L, next == 2L, closeOrder.toString == "ba")
    },
    test("FromReader materialization bypasses SyncInterpreter-specific subclass hooks") {
      val hookCalls = new AtomicInteger(0)
      val stream    = new Stream.FromReader[Nothing, Int](() => Reader.singleInt(1)) {
        override private[streams] def compileInterpreter(
          pipeline: zio.blocks.streams.internal.SyncInterpreter
        ): Unit = {
          hookCalls.incrementAndGet()
          if (pipeline eq null) throw new NullPointerException("sync pipeline")
          super.compileInterpreter(pipeline)
        }
      }
      val interpreter = AsyncInterpreter.fromStream(stream)
      run(interpreter.readInt(-1L)).map(value => assertTrue(value == 1L, hookCalls.get() == 0))
    },
    test("materialization retains logical Boolean output metadata") {
      val interpreter = AsyncInterpreter.fromStream(Stream(1).map(_ != 0))
      assertTrue(interpreter.jvmType == JvmType.Boolean)
    },
    test("fallback nodes remain lazy until driven") {
      val sourceCalls   = new AtomicInteger(0)
      val callbackCalls = new AtomicInteger(0)
      val source        = Stream.fromReader { sourceCalls.incrementAndGet(); Reader.singleInt(1) }
      val fallback      = new Stream[Nothing, Int] {
        def render: String                                                     = "fallback"
        private[streams] def compile(depth: Int, bufferSize: Int): Reader[Int] = {
          callbackCalls.incrementAndGet()
          source.compile(depth, bufferSize)
        }
        private[streams] def compileInterpreter(pipeline: zio.blocks.streams.internal.SyncInterpreter): Unit = {
          callbackCalls.incrementAndGet()
          source.compileInterpreter(pipeline)
        }
      }
      val interpreter     = AsyncInterpreter.fromStream(fallback)
      val lazyBeforeDrive = sourceCalls.get() == 0 && callbackCalls.get() == 0
      run(interpreter.readInt(-1L)).map(value =>
        assertTrue(lazyBeforeDrive, value == 1L, sourceCalls.get() == 1, callbackCalls.get() == 1)
      )
    },
    test("materialization rollback closes an acquired sync owner once and keeps cleanup suppressed") {
      val primary = new RuntimeException("metadata")
      val cleanup = new RuntimeException("close")
      val closes  = new AtomicInteger(0)
      val owner   = new Reader.SyncReader[Int] {
        override def jvmType: JvmType      = throw primary
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A = sentinel
        def close(): Unit                  = { closes.incrementAndGet(); throw cleanup }
      }
      val interpreter = AsyncInterpreter.fromStream(Stream.fromReader(owner))
      run(interpreter.readInt(-1L).either).map { result =>
        val failure = result.left.toOption.orNull
        assertTrue(failure eq primary, closes.get() == 1, failure.getSuppressed.toList.contains(cleanup))
      }
    },
    test("8,000 supported unary nodes materialize stack-safely within the packed-state limit") {
      var stream: Stream[Nothing, Int] = Stream(1)
      var i                            = 0
      while (i < 8000) { stream = stream.map(_ + 1); i += 1 }
      val interpreter = AsyncInterpreter.fromStream(stream)
      assertTrue(interpreter.incomingCount == 8001, interpreter.jvmType == JvmType.Int)
    },
    test("read construction is lazy and ready null is preserved") {
      val reader      = new TestReader(() => Async.succeed(null))
      val interpreter = new AsyncInterpreter(reader)
      val operation   = interpreter.read[Any](new AnyRef)
      val before      = reader.reads.get()
      val result      = operation.asInstanceOf[Pollable[Any]].poll(Noop)
      assertTrue(before == 0, reader.reads.get() == 1, fold(result) == Succeeded(null))
    },
    test("a Pollable success payload remains data") {
      val payload = new Pollable[Int] { def poll(onComplete: Runnable): Async[Int] = Async.succeed(99) }
      val reader  = new TestReader(() => Async.succeed(payload))
      val result  = new AsyncInterpreter(reader).read[Any](new AnyRef).asInstanceOf[Pollable[Any]].poll(Noop)
      assertTrue(fold(result) == Succeeded(payload))
    },
    test("materialization captures logical source metadata in the READ program") {
      val metadataReads = new AtomicInteger(0)
      val reader        = new TestReader(() => Async.succeed(1L)) {
        override def jvmType: JvmType = {
          metadataReads.incrementAndGet()
          JvmType.Int
        }
      }
      val interpreter = new AsyncInterpreter(reader)
      val before      = metadataReads.get()
      val first       = interpreter.jvmType
      val second      = interpreter.jvmType
      assertTrue(before == 1, first == JvmType.Int, second == JvmType.Int, metadataReads.get() == 1)
    },
    test("all logical and physical source READ lanes preserve values and sentinels") {
      def interpreter[A](reader: Reader.AsyncReader[A]): AsyncInterpreter =
        new AsyncInterpreter(reader.asInstanceOf[Reader.AsyncReader[Any]])
      for {
        booleanValue <- run(interpreter(Reader.singleBoolean(true).toAsync).readBoolean(-1))
        booleanEof   <- run(interpreter(Reader.closed.toAsync).readBoolean(-7))
        byteValue    <- run(interpreter(Reader.singleByte(0xff.toByte).toAsync).readByte())
        byteEof      <- run(interpreter(Reader.closed.toAsync).readByte())
        charValue    <- run(interpreter(Reader.singleChar('A').toAsync).readChar(-1))
        charEof      <- run(interpreter(Reader.closed.toAsync).readChar(-7))
        shortValue   <- run(interpreter(Reader.singleShort((-2).toShort).toAsync).readShort(Int.MinValue))
        shortEof     <- run(interpreter(Reader.closed.toAsync).readShort(-7))
        intValue     <- run(interpreter(Reader.singleInt(3).toAsync).readInt(Long.MinValue))
        intEof       <- run(interpreter(Reader.closed.toAsync).readInt(-7L))
        longValue    <- run(interpreter(Reader.singleLong(4L).toAsync).readLong(Long.MaxValue))
        longEof      <- run(interpreter(Reader.closed.toAsync).readLong(-7L))
        floatValue   <- run(interpreter(Reader.singleFloat(5.0f).toAsync).readFloat(Double.MaxValue))
        floatEof     <- run(interpreter(Reader.closed.toAsync).readFloat(-7.0))
        doubleValue  <- run(interpreter(Reader.singleDouble(6.0).toAsync).readDouble(Double.MaxValue))
        doubleEof    <- run(interpreter(Reader.closed.toAsync).readDouble(-7.0))
      } yield assertTrue(
        booleanValue == 1,
        booleanEof == -7,
        byteValue == 255,
        byteEof == -1,
        charValue == 'A'.toInt,
        charEof == -7,
        shortValue == -2,
        shortEof == -7,
        intValue == 3L,
        intEof == -7L,
        longValue == 4L,
        longEof == -7L,
        floatValue == 5.0,
        floatEof == -7.0,
        doubleValue == 6.0,
        doubleEof == -7.0
      )
    },
    test("root EOF is sticky and replays each caller's sentinel without touching the source") {
      val reader = new TestReader(() => Async.succeed(0)) {
        override def jvmType                                                        = JvmType.Int
        override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Async[Long] = {
          reads.incrementAndGet()
          Async.succeed(Long.MinValue)
        }
      }
      val interpreter = new AsyncInterpreter(reader)
      for {
        first  <- run(interpreter.readInt(-1L))
        second <- run(interpreter.readInt(-2L))
        ref    <- run(interpreter.read[Any]("eof"))
      } yield assertTrue(first == -1L, second == -2L, ref == "eof", reader.reads.get() == 1)
    },
    test("root EOF never closes the root reader") {
      val root        = new IntReader(Nil, () => Async.succeed(()))
      val interpreter = new AsyncInterpreter(root)
      run(interpreter.readInt(-1L)).map(value => assertTrue(value == -1L, root.closes.get() == 0))
    },
    test("ready nested close restores and resumes the outer reader") {
      val root        = new IntReader(List(7L), () => Async.succeed(()))
      val inner       = new IntReader(Nil, () => Async.succeed(()))
      val interpreter = new AsyncInterpreter(root)
      interpreter.installNestedFrameForTest(inner, JvmType.Int)
      run(interpreter.readInt(-1L)).map(value =>
        assertTrue(value == 7L, inner.closes.get() == 1, root.closes.get() == 0, root.reads.get() == 1)
      )
    },
    test("nested EOF restores and resumes every physical lane") {
      def check(
        jvmType: JvmType,
        root: Reader.AsyncReader[Any],
        inner: Reader.AsyncReader[Any],
        pull: AsyncInterpreter => Async[Any]
      ) = {
        val interpreter = new AsyncInterpreter(root)
        interpreter.installNestedFrameForTest(inner, jvmType)
        run(pull(interpreter))
      }
      val close = () => Async.succeed(())
      val roots = List[Reader.AsyncReader[Any]](
        new IntReader(List(1L), close),
        new TestReader(() => Async.succeed(2L)) {
          override def jvmType                                      = JvmType.Long;
          override def readLong(s: Long)(implicit ev: Any <:< Long) = { reads.incrementAndGet(); Async.succeed(2L) }
        },
        new TestReader(() => Async.succeed(3.0)) {
          override def jvmType                                          = JvmType.Float;
          override def readFloat(s: Double)(implicit ev: Any <:< Float) = {
            reads.incrementAndGet(); Async.succeed(3.0)
          }
        },
        new TestReader(() => Async.succeed(4.0)) {
          override def jvmType                                            = JvmType.Double;
          override def readDouble(s: Double)(implicit ev: Any <:< Double) = {
            reads.incrementAndGet(); Async.succeed(4.0)
          }
        },
        new TestReader(() => Async.succeed("r")) { override def jvmType = JvmType.AnyRef }
      )
      val inners = List[Reader.AsyncReader[Any]](
        new IntReader(Nil, close),
        new TestReader(() => Async.succeed(zio.blocks.streams.internal.EndOfStream)) {
          override def jvmType                                      = JvmType.Long;
          override def readLong(s: Long)(implicit ev: Any <:< Long) = Async.succeed(Long.MaxValue)
        },
        new TestReader(() => Async.succeed(Double.MaxValue)) {
          override def jvmType                                          = JvmType.Float;
          override def readFloat(s: Double)(implicit ev: Any <:< Float) = Async.succeed(Double.MaxValue)
        },
        new TestReader(() => Async.succeed(zio.blocks.streams.internal.EndOfStream)) {
          override def jvmType                                            = JvmType.Double;
          override def readDouble(s: Double)(implicit ev: Any <:< Double) = Async.succeed(Double.MaxValue)
        },
        new TestReader(() => Async.succeed(zio.blocks.streams.internal.EndOfStream)) {
          override def jvmType = JvmType.AnyRef
        }
      )
      val pulls: List[AsyncInterpreter => Async[Any]] = List(
        _.readInt(-1L),
        _.readLong(-1L),
        _.readFloat(-1.0),
        _.readDouble(-1.0),
        _.read[Any]("eof")
      )
      val cases = List(JvmType.Int, JvmType.Long, JvmType.Float, JvmType.Double, JvmType.AnyRef)
        .zip(roots)
        .zip(inners)
        .zip(pulls)
      ZIO
        .foreach(cases) { case (((t, root), inner), pull) =>
          check(t, root, inner, pull)
        }
        .map(values => assertTrue(values == List[Any](1L, 2L, 3.0, 4.0, "r")))
    },
    test("nested restore preserves an outer lane-crossing MAP") {
      val root  = new IntReader(List(21L), () => Async.succeed(()))
      val inner = new TestReader(() => Async.succeed(zio.blocks.streams.internal.EndOfStream)) {
        override def jvmType = JvmType.AnyRef
      }
      val interpreter = new AsyncInterpreter(root)
      interpreter.addMap[Int, String](JvmType.Int, JvmType.AnyRef)(value => s"v=$value")
      interpreter.installNestedFrameForTest(inner, JvmType.AnyRef)
      run(interpreter.read[Any]("eof")).map(value => assertTrue(value == "v=21"))
    },
    test("two empty nested frames close inner-first before resuming outer") {
      val order       = new StringBuilder
      val root        = new IntReader(List(9L), () => Async.succeed(()))
      val first       = new IntReader(Nil, () => Async.succeed { order.append('1'); () })
      val second      = new IntReader(Nil, () => Async.succeed { order.append('2'); () })
      val interpreter = new AsyncInterpreter(root)
      interpreter.installNestedFrameForTest(first, JvmType.Int)
      interpreter.installNestedFrameForTest(second, JvmType.Int)
      run(interpreter.readInt(-1L)).map(value => assertTrue(value == 9L, order.toString == "21"))
    },
    test("pending nested close resumes without cancelling its leaf") {
      val closeDone   = new ClosePending
      val root        = new IntReader(List(11L), () => Async.succeed(()))
      val inner       = new IntReader(Nil, () => closeDone)
      val interpreter = new AsyncInterpreter(root)
      interpreter.installNestedFrameForTest(inner, JvmType.Int)
      val operation                 = interpreter.readInt(-1L)
      val before                    = fold(operation.asInstanceOf[Pollable[Long]].poll(Noop))
      val future                    = operation.toFuture
      val suspendedBeforeCompletion = before.isInstanceOf[Suspended[_]] && closeDone.polls.get() > 0
      closeDone.succeed()
      ZIO
        .fromFuture(_ => future)
        .map(value => assertTrue(suspendedBeforeCompletion, value == 11L, closeDone.cancels.get() == 0))
    },
    test("a reentrant close wake retained during polling resumes the same leaf") {
      val close       = new ReentrantWake(())
      val root        = new IntReader(List(12L), () => Async.succeed(()))
      val inner       = new IntReader(Nil, () => close.asInstanceOf[Async[Unit]])
      val interpreter = new AsyncInterpreter(root)
      interpreter.installNestedFrameForTest(inner, JvmType.Int)
      run(interpreter.readInt(-1L)).map(value =>
        assertTrue(value == 12L, root.reads.get() == 1, close.polls.get() == 2)
      )
    },
    test("nested close replacement chains cross the ready budget and make progress") {
      val polls       = new AtomicInteger(0)
      val root        = new IntReader(List(23L), () => Async.succeed(()))
      val inner       = new IntReader(Nil, () => new CloseReplacement(300, polls))
      val interpreter = new AsyncInterpreter(root)
      interpreter.installNestedFrameForTest(inner, JvmType.Int)
      run(interpreter.readInt(-1L)).map(value => assertTrue(value == 23L, polls.get() == 301))
    },
    test("deep nested frame restoration crosses the ready budget") {
      val root        = new IntReader(List(29L), () => Async.succeed(()))
      val interpreter = new AsyncInterpreter(root)
      val closes      = new AtomicInteger(0)
      (0 until 300).foreach(_ =>
        interpreter.installNestedFrameForTest(
          new IntReader(Nil, () => { closes.incrementAndGet(); Async.succeed(()) }),
          JvmType.Int
        )
      )
      run(interpreter.readInt(-1L)).map(value => assertTrue(value == 29L, closes.get() == 300))
    },
    test("nested close failure is sticky, preserves identity, and leaves outer silent") {
      val cause       = new RuntimeException("inner close")
      val root        = new IntReader(List(13L), () => Async.succeed(()))
      val inner       = new IntReader(Nil, () => Async.fail(cause))
      val interpreter = new AsyncInterpreter(root)
      interpreter.installNestedFrameForTest(inner, JvmType.Int)
      val first  = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      val replay = fold(interpreter.readInt(-2L).asInstanceOf[Pollable[Long]].poll(Noop))
      assertTrue(first == Failed(cause, trusted = false), replay == first, root.reads.get() == 0)
    },
    test("cancellation joins a claimed pending close and restores outer for the next pull") {
      val closeDone   = new ClosePending
      val root        = new IntReader(List(17L), () => Async.succeed(()))
      val inner       = new IntReader(Nil, () => closeDone)
      val interpreter = new AsyncInterpreter(root)
      interpreter.installNestedFrameForTest(inner, JvmType.Int)
      val operation = interpreter.readInt(-1L)
      operation.asInstanceOf[Pollable[Long]].poll(Noop)
      val cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
      val before  = fold(operation.asInstanceOf[Pollable[Long]].poll(Noop))
      closeDone.succeed()
      for {
        _    <- run(cleanup)
        next <- run(interpreter.readInt(-2L))
      } yield assertTrue(before.isInstanceOf[Suspended[_]], next == 17L, closeDone.cancels.get() == 0)
    },
    test("cancellation synchronously from nested close returns the sentinel and leaves outer resumable") {
      val root                   = new IntReader(List(18L), () => Async.succeed(()))
      val interpreter            = new AsyncInterpreter(root)
      var operation: Async[Long] = null
      var cleanup: Async[Unit]   = null
      val inner                  = new IntReader(
        Nil,
        () => {
          cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
          Async.succeed(())
        }
      )
      interpreter.installNestedFrameForTest(inner, JvmType.Int)
      operation = interpreter.readInt(-1L)
      val cancelled = operation.asInstanceOf[Pollable[Long]].poll(Noop)
      for {
        value <- run(cancelled)
        _     <- run(cleanup)
        next  <- run(interpreter.readInt(-2L))
      } yield assertTrue(value == -1L, next == 18L, inner.closes.get() == 1)
    },
    test("cancellation during a close poll joins a distinct replacement without cancelling it") {
      val replacement            = new ClosePending
      val root                   = new IntReader(List(19L), () => Async.succeed(()))
      val interpreter            = new AsyncInterpreter(root)
      var operation: Async[Long] = null
      var cleanup: Async[Unit]   = null
      val close                  = new CancelWithReplacement(
        () => cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]]),
        replacement
      )
      val inner = new IntReader(Nil, () => close.asInstanceOf[Async[Unit]])
      interpreter.installNestedFrameForTest(inner, JvmType.Int)
      operation = interpreter.readInt(-1L)
      val cancelled = operation.asInstanceOf[Pollable[Long]].poll(Noop)
      val suspended = fold(cancelled).isInstanceOf[Suspended[_]] && replacement.polls.get() > 0
      replacement.succeed()
      for {
        value <- run(cancelled)
        _     <- run(cleanup)
        next  <- run(interpreter.readInt(-2L))
      } yield assertTrue(
        value == -1L,
        next == 19L,
        suspended,
        close.cancels.get() == 0,
        replacement.cancels.get() == 0
      )
    },
    test("a stale captured close waker is inert after success and a later pull") {
      val close       = new CapturingTerminal(())
      val root        = new IntReader(List(31L, 32L), () => Async.succeed(()))
      val inner       = new IntReader(Nil, () => close.asInstanceOf[Async[Unit]])
      val interpreter = new AsyncInterpreter(root)
      interpreter.installNestedFrameForTest(inner, JvmType.Int)
      for {
        first  <- run(interpreter.readInt(-1L))
        second <- run(interpreter.readInt(-1L))
        _       = close.captured.run()
        third  <- run(interpreter.readInt(-1L))
      } yield assertTrue(first == 31L, second == 32L, third == -1L, root.reads.get() == 3)
    },
    test("FILTER exhaustion becomes sticky only after the physical EOF read") {
      var next   = true
      val reader = new TestReader(() => Async.succeed(0)) {
        override def jvmType                                                        = JvmType.Int
        override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Async[Long] = {
          reads.incrementAndGet()
          if (next) {
            next = false
            Async.succeed(1L)
          } else Async.succeed(Long.MinValue)
        }
      }
      val interpreter = new AsyncInterpreter(reader)
      interpreter.addFilter[Int](JvmType.Int)(_ => false)
      for {
        first  <- run(interpreter.readInt(-1L))
        second <- run(interpreter.readInt(-2L))
      } yield assertTrue(first == -1L, second == -2L, reader.reads.get() == 2)
    },
    test("synchronous MAP executes every physical lane crossing") {
      val types                                      = Array(JvmType.Int, JvmType.Long, JvmType.Float, JvmType.Double, JvmType.AnyRef)
      def reader(lane: Int): Reader.AsyncReader[Any] = lane match {
        case 0 => Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 1 => Reader.singleLong(2L).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 2 => Reader.singleFloat(3.0f).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 3 => Reader.singleDouble(4.0).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case _ => Reader.single[AnyRef](Int.box(5)).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
      }
      def mapValue(out: Int, value: Any): Any = {
        val n = value.asInstanceOf[java.lang.Number].intValue() + 10
        out match {
          case 0 => n
          case 1 => n.toLong
          case 2 => n.toFloat
          case 3 => n.toDouble
          case _ => s"v$n"
        }
      }
      def pull(interpreter: AsyncInterpreter, lane: Int): Async[Any] = lane match {
        case 0 => interpreter.readInt(-101L).map(_.asInstanceOf[Any])
        case 1 => interpreter.readLong(-102L).map(_.asInstanceOf[Any])
        case 2 => interpreter.readFloat(-103.0).map(_.asInstanceOf[Any])
        case 3 => interpreter.readDouble(-104.0).map(_.asInstanceOf[Any])
        case _ => interpreter.read[Any]("eof")
      }
      val eof = Array[Any](-101L, -102L, -103.0, -104.0, "eof")
      ZIO
        .foreach(List.range(0, 25)) { crossing =>
          val in          = crossing / 5
          val out         = crossing % 5
          val interpreter = new AsyncInterpreter(reader(in))
          interpreter.addMap[Any, Any](types(in), types(out))(value => mapValue(out, value))
          for {
            value <- run(pull(interpreter, out))
            end   <- run(pull(interpreter, out))
          } yield assertTrue(value == mapValue(out, in + 1), end == eof(out))
        }
        .map(results => results.reduce(_ && _))
    },
    test("Boolean MAP adapts logical inputs and outputs on the physical I lane") {
      val toInt = new AsyncInterpreter(Reader.singleBoolean(true).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      toInt.addMap[Boolean, Int](JvmType.Boolean, JvmType.Int)(if (_) 7 else 8)
      val toBoolean = new AsyncInterpreter(Reader.singleInt(0).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      toBoolean.addMap[Int, Boolean](JvmType.Int, JvmType.Boolean)(_ != 0)
      for {
        i <- run(toInt.readInt(-1L))
        b <- run(toBoolean.readBoolean(-1))
      } yield assertTrue(i == 7L, b == 0)
    },
    test("mapped logical R-lane outputs preserve specialized scalar encodings") {
      def mapped[A](outType: JvmType, value: A): AsyncInterpreter = {
        val interpreter = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
        interpreter.addMap[Int, A](JvmType.Int, outType)(_ => value)
        interpreter
      }
      for {
        boolByte  <- run(mapped(JvmType.Boolean, true).readByte())
        byte      <- run(mapped(JvmType.Byte, 0xff.toByte).readByte())
        charByte  <- run(mapped(JvmType.Char, '\u01ff').readByte())
        charValue <- run(mapped(JvmType.Char, '\u01ff').readChar(-1))
        short     <- run(mapped(JvmType.Short, (-2).toShort).readShort(Int.MinValue))
      } yield assertTrue(boolByte == 1, byte == 255, charByte == 255, charValue == 511, short == -2)
    },
    test("pending READ resumes into MAP exactly once") {
      val completion  = new Completer[Any]
      val calls       = new AtomicInteger(0)
      val interpreter = new AsyncInterpreter(new TestReader(() => completion) { override def jvmType = JvmType.Int })
      interpreter.addMap[Int, Long](JvmType.Int, JvmType.Long) { i => calls.incrementAndGet(); i.toLong + 1L }
      val future = interpreter.readLong(-1L).toFuture
      completion.succeed(41)
      ZIO.fromFuture(_ => future).map(value => assertTrue(value == 42L, calls.get() == 1))
    },
    test("pending READ resumes through every physical MAP crossing") {
      val types                             = Array(JvmType.Int, JvmType.Long, JvmType.Float, JvmType.Double, JvmType.AnyRef)
      val inputs                            = Array[Any](1, 2L, 3.0f, 4.0, Int.box(5))
      def mapped(out: Int, value: Any): Any = {
        val n = value.asInstanceOf[java.lang.Number].intValue() + 10
        out match {
          case 0 => n
          case 1 => n.toLong
          case 2 => n.toFloat
          case 3 => n.toDouble
          case _ => s"v$n"
        }
      }
      def pull(interpreter: AsyncInterpreter, lane: Int): Async[Any] = lane match {
        case 0 => interpreter.readInt(-1L).map(_.asInstanceOf[Any])
        case 1 => interpreter.readLong(-1L).map(_.asInstanceOf[Any])
        case 2 => interpreter.readFloat(-1.0).map(_.asInstanceOf[Any])
        case 3 => interpreter.readDouble(-1.0).map(_.asInstanceOf[Any])
        case _ => interpreter.read[Any]("eof")
      }
      def pushed(in: Int, value: Any): Any = in match {
        case 0 => Stream.succeed(value.asInstanceOf[Int])
        case 1 => Stream.succeed(value.asInstanceOf[Long])
        case 2 => Stream.succeed(value.asInstanceOf[Float])
        case 3 => Stream.succeed(value.asInstanceOf[Double])
        case _ => Stream.succeed(value.asInstanceOf[AnyRef])
      }
      ZIO
        .foreach(List.range(0, 25)) { crossing =>
          val in          = crossing / 5
          val out         = crossing % 5
          val completion  = new Completer[Any]
          val interpreter = new AsyncInterpreter(new TestReader(() => completion) { override def jvmType = types(in) })
          interpreter.addPush[Any](types(in), types(in))(value => pushed(in, value))
          interpreter.addMap[Any, Any](types(in), types(out))(value => mapped(out, value))
          val future = pull(interpreter, out).toFuture
          completion.succeed(inputs(in))
          ZIO.fromFuture(_ => future).map(value => assertTrue(value == mapped(out, inputs(in))))
        }
        .map(results => results.reduce(_ && _))
    },
    test("thrown MAP callback is sticky, untrusted, and identity-replayed") {
      val cause       = new RuntimeException("map")
      val interpreter = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      interpreter.addMap[Int, Int](JvmType.Int, JvmType.Int)(_ => throw cause)
      val first  = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      val replay = fold(interpreter.readInt(-2L).asInstanceOf[Pollable[Long]].poll(Noop))
      assertTrue(first == Failed(cause, trusted = false), replay == first)
    },
    test("cancellation during MAP prevents later callbacks from running") {
      val laterCalls                = new AtomicInteger(0)
      val interpreter               = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      var operation: Async[Long]    = null
      var cancellation: Async[Unit] = null
      interpreter.addMap[Int, Int](JvmType.Int, JvmType.Int) { value =>
        cancellation = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
        value + 1
      }
      interpreter.addMap[Int, Int](JvmType.Int, JvmType.Int) { value =>
        laterCalls.incrementAndGet()
        value + 1
      }
      operation = interpreter.readInt(-1L)
      operation.asInstanceOf[Pollable[Long]].poll(Noop)
      assertTrue(
        cancellation.block == (),
        laterCalls.get() == 0,
        fold(operation.asInstanceOf[Pollable[Long]].poll(Noop)) == Succeeded(-1L)
      )
    },
    test("white-box async MAP is lazy and executes all 25 lane crossings exactly once") {
      val types                                      = Array(JvmType.Int, JvmType.Long, JvmType.Float, JvmType.Double, JvmType.AnyRef)
      def source(lane: Int): Reader.AsyncReader[Any] = lane match {
        case 0 => Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 1 => Reader.singleLong(2L).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 2 => Reader.singleFloat(3f).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 3 => Reader.singleDouble(4d).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case _ => Reader.single[AnyRef](Int.box(5)).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
      }
      def output(lane: Int, n: Int): Any = lane match {
        case 0 => n
        case 1 => n.toLong
        case 2 => n.toFloat
        case 3 => n.toDouble
        case _ => s"v$n"
      }
      def pull(interpreter: AsyncInterpreter, lane: Int): Async[Any] = lane match {
        case 0 => interpreter.readInt(-1L).map(_.asInstanceOf[Any])
        case 1 => interpreter.readLong(-1L).map(_.asInstanceOf[Any])
        case 2 => interpreter.readFloat(-1d).map(_.asInstanceOf[Any])
        case 3 => interpreter.readDouble(-1d).map(_.asInstanceOf[Any])
        case _ => interpreter.read[Any]("eof")
      }
      ZIO
        .foreach(List.range(0, 25)) { crossing =>
          val in          = crossing / 5
          val out         = crossing % 5
          val calls       = new AtomicInteger(0)
          val interpreter = new AsyncInterpreter(source(in))
          interpreter.addAsyncMap[Any, Any](types(in), types(out)) { value =>
            val n = value.asInstanceOf[java.lang.Number].intValue() + 10
            calls.incrementAndGet()
            Async.succeed(output(out, n))
          }
          val wasLazy = calls.get() == 0
          val pulled  = run(pull(interpreter, out))
          for {
            value <- pulled
          } yield assertTrue(wasLazy, value == output(out, in + 11), calls.get() == 1)
        }
        .map(_.reduce(_ && _))
    },
    test("white-box synchronous MAP executes all 25 lane crossings exactly once") {
      val types                                      = Array(JvmType.Int, JvmType.Long, JvmType.Float, JvmType.Double, JvmType.AnyRef)
      def source(lane: Int): Reader.AsyncReader[Any] = lane match {
        case 0 => Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 1 => Reader.singleLong(2L).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 2 => Reader.singleFloat(3f).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 3 => Reader.singleDouble(4d).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case _ => Reader.single[AnyRef](Int.box(5)).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
      }
      def output(lane: Int, n: Int): Any = lane match {
        case 0 => n
        case 1 => n.toLong
        case 2 => n.toFloat
        case 3 => n.toDouble
        case _ => s"v$n"
      }
      def pull(interpreter: AsyncInterpreter, lane: Int): Async[Any] = lane match {
        case 0 => interpreter.readInt(-1L).map(_.asInstanceOf[Any])
        case 1 => interpreter.readLong(-1L).map(_.asInstanceOf[Any])
        case 2 => interpreter.readFloat(-1d).map(_.asInstanceOf[Any])
        case 3 => interpreter.readDouble(-1d).map(_.asInstanceOf[Any])
        case _ => interpreter.read[Any]("eof")
      }
      ZIO
        .foreach(List.range(0, 25)) { crossing =>
          val in                     = crossing / 5
          val out                    = crossing % 5
          val calls                  = new AtomicInteger(0)
          val interpreter            = new AsyncInterpreter(source(in))
          def called[A](value: A): A = { calls.incrementAndGet(); value }
          crossing match {
            case 0  => interpreter.addMap[Int, Int](types(in), types(out))(value => called(value + 10))
            case 1  => interpreter.addMap[Int, Long](types(in), types(out))(value => called(value.toLong + 10L))
            case 2  => interpreter.addMap[Int, Float](types(in), types(out))(value => called(value.toFloat + 10f))
            case 3  => interpreter.addMap[Int, Double](types(in), types(out))(value => called(value.toDouble + 10d))
            case 4  => interpreter.addMap[Int, AnyRef](types(in), types(out))(value => called(s"v${value + 10}"))
            case 5  => interpreter.addMap[Long, Int](types(in), types(out))(value => called(value.toInt + 10))
            case 6  => interpreter.addMap[Long, Long](types(in), types(out))(value => called(value + 10L))
            case 7  => interpreter.addMap[Long, Float](types(in), types(out))(value => called(value.toFloat + 10f))
            case 8  => interpreter.addMap[Long, Double](types(in), types(out))(value => called(value.toDouble + 10d))
            case 9  => interpreter.addMap[Long, AnyRef](types(in), types(out))(value => called(s"v${value + 10L}"))
            case 10 => interpreter.addMap[Float, Int](types(in), types(out))(value => called(value.toInt + 10))
            case 11 => interpreter.addMap[Float, Long](types(in), types(out))(value => called(value.toLong + 10L))
            case 12 => interpreter.addMap[Float, Float](types(in), types(out))(value => called(value + 10f))
            case 13 => interpreter.addMap[Float, Double](types(in), types(out))(value => called(value.toDouble + 10d))
            case 14 =>
              interpreter.addMap[Float, AnyRef](types(in), types(out))(value => called(s"v${value.toInt + 10}"))
            case 15 => interpreter.addMap[Double, Int](types(in), types(out))(value => called(value.toInt + 10))
            case 16 => interpreter.addMap[Double, Long](types(in), types(out))(value => called(value.toLong + 10L))
            case 17 => interpreter.addMap[Double, Float](types(in), types(out))(value => called(value.toFloat + 10f))
            case 18 => interpreter.addMap[Double, Double](types(in), types(out))(value => called(value + 10d))
            case 19 =>
              interpreter.addMap[Double, AnyRef](types(in), types(out))(value => called(s"v${value.toInt + 10}"))
            case 20 =>
              interpreter.addMap[AnyRef, Int](types(in), types(out))(value =>
                called(value.asInstanceOf[java.lang.Number].intValue() + 10)
              )
            case 21 =>
              interpreter.addMap[AnyRef, Long](types(in), types(out))(value =>
                called(value.asInstanceOf[java.lang.Number].longValue() + 10L)
              )
            case 22 =>
              interpreter.addMap[AnyRef, Float](types(in), types(out))(value =>
                called(value.asInstanceOf[java.lang.Number].floatValue() + 10f)
              )
            case 23 =>
              interpreter.addMap[AnyRef, Double](types(in), types(out))(value =>
                called(value.asInstanceOf[java.lang.Number].doubleValue() + 10d)
              )
            case _ =>
              interpreter.addMap[AnyRef, AnyRef](types(in), types(out))(value =>
                called(s"v${value.asInstanceOf[java.lang.Number].intValue() + 10}")
              )
          }
          for {
            value <- run(pull(interpreter, out))
          } yield assertTrue(value == output(out, in + 11), calls.get() == 1)
        }
        .map(_.reduce(_ && _))
    },
    test("async MAP materialization and Boolean adaptation preserve surrounding operation order") {
      val calls       = new AtomicInteger(0)
      val order       = new StringBuilder
      val interpreter = AsyncInterpreter.fromStreamWithAsyncMapForTest(
        Stream(false, true).map { value => order.append('a'); !value }.filter(identity),
        JvmType.Boolean,
        JvmType.Boolean
      ) { value =>
        calls.incrementAndGet()
        order.append('b')
        Async.succeed(!value)
      }
      interpreter.addMap[Boolean, Int](JvmType.Boolean, JvmType.Int) { value => order.append('c'); if (value) 1 else 0 }
      val wasLazy = calls.get() == 0
      val pulled  = run(interpreter.readInt(-1L))
      for {
        value <- pulled
      } yield assertTrue(wasLazy, value == 0L, calls.get() == 1, order.toString == "abc")
    },
    test("async MAP follows same and distinct pending leaves and reentrant wakes") {
      val same        = new Completer[Int]
      val replacement = new Replacement(12)
      val reentrant   = new ReentrantWake(13)
      val readers     = List[Async[Any]](
        same.asInstanceOf[Async[Any]],
        new ReplaceOnce(replacement),
        reentrant
      )
      ZIO
        .foreach(readers.zipWithIndex) { case (callback, index) =>
          val interpreter = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
          interpreter.addAsyncMap[Int, Int](JvmType.Int, JvmType.Int)(_ => callback.asInstanceOf[Async[Int]])
          val effect = interpreter.readInt(-1L)
          if (index == 0) {
            val future = effect.toFuture
            same.succeed(11)
            ZIO.fromFuture(_ => future).map(value => assertTrue(value == 11L))
          } else run(effect).map(value => assertTrue(value == index + 11L))
        }
        .map(_.reduce(_ && _))
    },
    test("async MAP throw, null, and failed effects are sticky untrusted failures") {
      val thrown                             = new RuntimeException("async-map-throw")
      val failed                             = new RuntimeException("async-map-failed")
      val trusted                            = new RuntimeException("async-map-trusted")
      val trustedStreamError                 = StreamError.source("async-map-stream-error")
      val callbacks: List[Int => Async[Int]] =
        List(
          _ => throw thrown,
          _ => null,
          _ => Async.fail(failed),
          _ => Async.failTrusted(trusted),
          _ => Async.failTrusted(trustedStreamError)
        )
      val expected: List[Throwable => Boolean] =
        List(
          _ eq thrown,
          _.isInstanceOf[NullPointerException],
          _ eq failed,
          _ eq trusted,
          {
            case error: StreamError =>
              (error ne trustedStreamError) && error.value == "async-map-stream-error" && !error.isTrusted
            case _ => false
          }
        )
      val assertions = callbacks.zip(expected).map { case (callback, matches) =>
        val interpreter = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
        interpreter.addAsyncMap(JvmType.Int, JvmType.Int)(callback)
        val first  = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
        val replay = fold(interpreter.readInt(-2L).asInstanceOf[Pollable[Long]].poll(Noop))
        assertTrue(first match { case Failed(cause, false) => matches(cause); case _ => false }, replay == first)
      }
      assertions.reduce(_ && _)
    },
    test("suspended incoming async MAP resumes at its continuation across lanes without rewind or skip") {
      val pending     = new Completer[String]
      val order       = new StringBuilder
      val interpreter = new AsyncInterpreter(Reader.singleInt(2).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      interpreter.addMap[Int, Long](JvmType.Int, JvmType.Long) { value => order.append('a'); value + 3L }
      interpreter.addAsyncMap[Long, String](JvmType.Long, JvmType.AnyRef) { value =>
        order.append('b'); Predef.assert(value == 5L); pending
      }
      interpreter.addMap[String, Int](JvmType.AnyRef, JvmType.Int) { value => order.append('c'); value.toInt + 1 }
      interpreter.addFilter[Int](JvmType.Int) { value => order.append('d'); value == 8 }
      interpreter.addPush[Int](JvmType.Int, JvmType.Int) { value => order.append('e'); Stream(value + 10) }
      val operation = interpreter.readInt(-1L)
      val suspended = fold(operation.asInstanceOf[Pollable[Long]].poll(Noop)).isInstanceOf[Suspended[_]]
      val before    = order.toString
      pending.succeed("7")
      run(operation).map(value => assertTrue(suspended, value == 18L, before == "ab", order.toString == "abcde"))
    },
    test("suspended outgoing async MAP resumes after prior cross-lane MAP and before MAP FILTER PUSH") {
      val pending = new Completer[String]
      val order   = new StringBuilder
      val stream  = Stream(2).flatMap { value =>
        order.append('p')
        Stream(value + 1)
      }.map { value => order.append('a'); value.toLong + 2L }
      val interpreter = AsyncInterpreter.fromStreamWithAsyncMapForTest(stream, JvmType.Long, JvmType.AnyRef) { value =>
        order.append('b'); Predef.assert(value == 5L); pending
      }
      interpreter.addMap[String, Int](JvmType.AnyRef, JvmType.Int) { value => order.append('c'); value.toInt + 1 }
      interpreter.addFilter[Int](JvmType.Int) { value => order.append('d'); value == 8 }
      interpreter.addPush[Int](JvmType.Int, JvmType.Int) { value => order.append('e'); Stream(value + 10) }
      val operation = interpreter.readInt(-1L)
      val suspended = fold(operation.asInstanceOf[Pollable[Long]].poll(Noop)).isInstanceOf[Suspended[_]]
      val before    = order.toString
      pending.succeed("7")
      run(operation).map(value => assertTrue(suspended, value == 18L, before == "pab", order.toString == "pabcde"))
    },
    test("cancellation while polling an async MAP discards ready and replacement results and stale wakes") {
      ZIO
        .foreach(List(false, true)) { replacementResult =>
          val cleanupDone                = new Completer[Unit]
          val replacement                = new Replacement(91)
          val later                      = new AtomicInteger(0)
          val callbacks                  = new AtomicInteger(0)
          val reader                     = new IntReader(List(1L, 7L), () => Async.succeed(()))
          val interpreter                = new AsyncInterpreter(reader)
          var operation: Async[Long]     = null
          var cleanup: Async[Unit]       = null
          var callback: CancelDuringPoll = null
          interpreter.addAsyncMap[Int, Int](JvmType.Int, JvmType.Int) { value =>
            if (callbacks.getAndIncrement() == 0) {
              val result: Async[Any] = if (replacementResult) replacement else Async.succeed(90)
              callback = new CancelDuringPoll(
                new Runnable {
                  def run(): Unit = cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
                },
                result,
                cleanupDone
              )
              callback.asInstanceOf[Async[Int]]
            } else Async.succeed(value)
          }
          interpreter.addMap[Int, Int](JvmType.Int, JvmType.Int) { value => later.incrementAndGet(); value + 1 }
          operation = interpreter.readInt(-1L)
          operation.asInstanceOf[Pollable[Long]].poll(Noop)
          val beforeCleanup = fold(operation.asInstanceOf[Pollable[Long]].poll(Noop))
          cleanupDone.succeed(())
          cleanup.block
          callback.captured.run()
          val cancelled = fold(operation.asInstanceOf[Pollable[Long]].poll(Noop))
          val next      = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
          assertTrue(
            beforeCleanup.isInstanceOf[Suspended[_]],
            cancelled == Succeeded(-1L),
            next == Succeeded(8L),
            callback.cancels.get() == 1,
            callbacks.get() == 2,
            later.get() == 1,
            reader.reads.get() == 2,
            replacement.polls.get() == 0
          )
        }
        .map(_.reduce(_ && _))
    },
    test("cancellation during async MAP invocation discards ready output and joins a late replacement") {
      ZIO
        .foreach(List(false, true)) { pendingResult =>
          val cleanupDone            = new Completer[Unit]
          val replacement            = new CancelablePending(cleanupDone)
          val callbacks              = new AtomicInteger(0)
          val later                  = new AtomicInteger(0)
          val reader                 = new IntReader(List(1L, 7L), () => Async.succeed(()))
          val interpreter            = new AsyncInterpreter(reader)
          var operation: Async[Long] = null
          var cleanup: Async[Unit]   = null
          interpreter.addAsyncMap[Int, Int](JvmType.Int, JvmType.Int) { value =>
            if (callbacks.getAndIncrement() == 0) {
              cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
              if (pendingResult) replacement.asInstanceOf[Async[Int]] else Async.succeed(90)
            } else Async.succeed(value)
          }
          interpreter.addMap[Int, Int](JvmType.Int, JvmType.Int) { value => later.incrementAndGet(); value + 1 }
          operation = interpreter.readInt(-1L)
          operation.asInstanceOf[Pollable[Long]].poll(Noop)
          val before = fold(operation.asInstanceOf[Pollable[Long]].poll(Noop))
          if (pendingResult) cleanupDone.succeed(())
          cleanup.block
          val cancelled = fold(operation.asInstanceOf[Pollable[Long]].poll(Noop))
          val next      = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
          assertTrue(
            before.isInstanceOf[Suspended[_]],
            cancelled == Succeeded(-1L),
            next == Succeeded(8L),
            callbacks.get() == 2,
            later.get() == 1,
            reader.reads.get() == 2,
            replacement.cancels.get() == (if (pendingResult) 1 else 0)
          )
        }
        .map(_.reduce(_ && _))
    },
    test("more than one ready-budget of async MAP operations yields and resumes exactly once per callback") {
      val count       = 263
      val calls       = new AtomicInteger(0)
      val interpreter = new AsyncInterpreter(Reader.singleInt(0).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      var index       = 0
      while (index < count) {
        val expected = index
        interpreter.addAsyncMap[Int, Int](JvmType.Int, JvmType.Int) { value =>
          Predef.assert(calls.getAndIncrement() == expected)
          Async.succeed(value + 1)
        }
        index += 1
      }
      run(interpreter.readInt(-1L)).map(value => assertTrue(value == count.toLong, calls.get() == count))
    },
    test("async MAP cancellation signals and joins its active callback before the sentinel") {
      val cleanupDone = new Completer[Unit]
      val callback    = new CancelablePending(cleanupDone)
      val later       = new AtomicInteger(0)
      val reader      = new TestReader(() => Async.succeed(1)) { override def jvmType = JvmType.Int }
      val interpreter = new AsyncInterpreter(reader)
      interpreter.addAsyncMap[Int, Int](JvmType.Int, JvmType.Int)(_ => callback.asInstanceOf[Async[Int]])
      interpreter.addMap[Int, Int](JvmType.Int, JvmType.Int) { value => later.incrementAndGet(); value + 1 }
      val operation = interpreter.readInt(-1L)
      operation.asInstanceOf[Pollable[Long]].poll(Noop)
      val cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
      val before  = fold(operation.asInstanceOf[Pollable[Long]].poll(Noop))
      cleanupDone.succeed(())
      assertTrue(
        callback.cancels.get() == 1,
        before.isInstanceOf[Suspended[_]],
        cleanup.block == (),
        later.get() == 0,
        reader.reads.get() == 1,
        fold(operation.asInstanceOf[Pollable[Long]].poll(Noop)) == Succeeded(-1L)
      )
    },
    test("async MAP cancellation cleanup cannot retain trusted StreamError provenance") {
      val trusted     = StreamError.source("async-map-cancel")
      val callback    = new CancelablePending(Async.failTrusted(trusted))
      val interpreter = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      interpreter.addAsyncMap[Int, Int](JvmType.Int, JvmType.Int)(_ => callback.asInstanceOf[Async[Int]])
      val operation = interpreter.readInt(-1L)
      operation.asInstanceOf[Pollable[Long]].poll(Noop)
      val cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
      val failure = cleanup.either.block match {
        case Left(cause) => cause
        case Right(_)    => throw new AssertionError("expected cancellation cleanup failure")
      }
      assertTrue(
        callback.cancels.get() == 1,
        failure.isInstanceOf[StreamError],
        failure.asInstanceOf[StreamError].value == "async-map-cancel",
        !failure.asInstanceOf[StreamError].isTrusted,
        failure ne trusted,
        fold(operation.asInstanceOf[Pollable[Long]].poll(Noop)) == Failed(failure, false)
      )
    },
    test("async FILTER preserves accepted values on all lanes and adapts Boolean on I") {
      val types                                      = Array(JvmType.Int, JvmType.Long, JvmType.Float, JvmType.Double, JvmType.AnyRef)
      val inputs                                     = Array[Any](2, 2L, 2.0f, 2.0, "two")
      def source(lane: Int): Reader.AsyncReader[Any] = lane match {
        case 0 => Reader.singleInt(2).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 1 => Reader.singleLong(2L).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 2 => Reader.singleFloat(2.0f).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 3 => Reader.singleDouble(2.0).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case _ => Reader.single("two").toAsync.asInstanceOf[Reader.AsyncReader[Any]]
      }
      def pull(interpreter: AsyncInterpreter, lane: Int): Async[Any] = lane match {
        case 0 => interpreter.readInt(-1L).map(_.asInstanceOf[Any])
        case 1 => interpreter.readLong(-1L).map(_.asInstanceOf[Any])
        case 2 => interpreter.readFloat(-1d).map(_.asInstanceOf[Any])
        case 3 => interpreter.readDouble(-1d).map(_.asInstanceOf[Any])
        case _ => interpreter.read[Any]("eof")
      }
      val lanes = ZIO.foreach(List.range(0, 5)) { lane =>
        val calls       = new AtomicInteger(0)
        val interpreter = new AsyncInterpreter(source(lane))
        interpreter.addAsyncFilter[Any](types(lane)) { value =>
          calls.incrementAndGet(); Async.succeed(value == inputs(lane))
        }
        run(pull(interpreter, lane)).map(value => assertTrue(value == inputs(lane), calls.get() == 1))
      }
      val seen    = new java.util.concurrent.atomic.AtomicReference[java.lang.Boolean]()
      val boolean = new AsyncInterpreter(Reader.singleBoolean(true).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      boolean.addAsyncFilter[Boolean](JvmType.Boolean) { value => seen.set(value); Async.succeed(value) }
      lanes.zipWith(run(boolean.readBoolean(-1))) { case (assertions, value) =>
        assertions.reduce(_ && _) && assertTrue(value == 1, seen.get() == java.lang.Boolean.TRUE)
      }
    },
    test("async FILTER rejects and re-pulls exactly, then resumes later operations without rewinding") {
      val pending     = new Completer[Boolean]
      val order       = new StringBuilder
      val reader      = new IntReader(List(1L, 2L), () => Async.succeed(()))
      val interpreter = new AsyncInterpreter(reader)
      interpreter.addMap[Int, Long](JvmType.Int, JvmType.Long) { value => order.append('a'); value.toLong }
      interpreter.addAsyncFilter[Long](JvmType.Long) { value =>
        order.append('b'); if (value == 1L) pending else Async.succeed(true)
      }
      interpreter.addMap[Long, Int](JvmType.Long, JvmType.Int) { value => order.append('c'); value.toInt + 10 }
      val operation = interpreter.readInt(-1L)
      operation.asInstanceOf[Pollable[Long]].poll(Noop)
      pending.succeed(false)
      run(operation).map(value => assertTrue(value == 12L, reader.reads.get() == 2, order.toString == "ababc"))
    },
    test("outgoing async FILTER suspends around PUSH and preserves its continuation") {
      val pending     = new Completer[Boolean]
      val order       = new StringBuilder
      val interpreter = AsyncInterpreter.fromStream(Stream(2).flatMap { value =>
        order.append('p'); Stream(value + 1)
      })
      interpreter.addMap[Int, Long](JvmType.Int, JvmType.Long) { value => order.append('a'); value + 2L }
      interpreter.addAsyncFilter[Long](JvmType.Long) { value => order.append('b'); Predef.assert(value == 5L); pending }
      interpreter.addMap[Long, Int](JvmType.Long, JvmType.Int) { value => order.append('c'); value.toInt + 1 }
      interpreter.addPush[Int](JvmType.Int, JvmType.Int) { value => order.append('d'); Stream(value + 10) }
      val operation = interpreter.readInt(-1L)
      val suspended = fold(operation.asInstanceOf[Pollable[Long]].poll(Noop)).isInstanceOf[Suspended[_]]
      pending.succeed(true)
      run(operation).map(value => assertTrue(suspended, value == 16L, order.toString == "pabcd"))
    },
    test("async FILTER follows replacement and reentrant leaves and launders every callback failure") {
      val trusted = StreamError.source("async-filter")
      val effects = List[Async[Boolean]](
        new ReplaceOnce(new Replacement(true)).asInstanceOf[Async[Boolean]],
        Async.failTrusted(trusted),
        null
      )
      val results = effects.map { effect =>
        val interpreter = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
        interpreter.addAsyncFilter[Int](JvmType.Int)(_ => effect)
        fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      }
      val reentrant = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      reentrant.addAsyncFilter[Int](JvmType.Int)(_ => new ReentrantWake(true).asInstanceOf[Async[Boolean]])
      run(reentrant.readInt(-1L)).map { value =>
        assertTrue(
          results(0) == Succeeded(1L),
          value == 1L,
          results(1) match {
            case Failed(error: StreamError, false) => (error ne trusted) && !error.isTrusted
            case _                                 => false
          },
          results(2) match { case Failed(_: NullPointerException, false) => true; case _ => false }
        )
      }
    },
    test("async FILTER cancellation joins callback cleanup, rejects stale output, and permits restart") {
      val done        = new Completer[Unit]
      val callback    = new CancelablePending(done)
      val reader      = new IntReader(List(1L, 2L), () => Async.succeed(()))
      val interpreter = new AsyncInterpreter(reader)
      interpreter.addAsyncFilter[Int](JvmType.Int) { value =>
        if (value == 1) callback.asInstanceOf[Async[Boolean]] else Async.succeed(true)
      }
      val operation = interpreter.readInt(-1L)
      operation.asInstanceOf[Pollable[Long]].poll(Noop)
      val cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
      done.succeed(())
      cleanup.block
      val next = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      assertTrue(callback.cancels.get() == 1, next == Succeeded(2L), reader.reads.get() == 2)
    },
    test("more than one ready-budget of false async FILTER callbacks is bounded and stack safe") {
      val accepted    = 10000
      val calls       = new AtomicInteger(0)
      val reader      = Reader.fromChunk(zio.blocks.chunk.Chunk.fromIterable(List.range(0, accepted + 1))).toAsync
      val interpreter = new AsyncInterpreter(reader.asInstanceOf[Reader.AsyncReader[Any]])
      interpreter.addAsyncFilter[Int](JvmType.Int) { value =>
        calls.incrementAndGet(); Async.succeed(value == accepted)
      }
      run(interpreter.readInt(-1L)).map(value => assertTrue(value == accepted.toLong, calls.get() == accepted + 1))
    },
    test("white-box async COLLECT executes all 25 crossings and Boolean adaptation") {
      val types                                      = Array(JvmType.Int, JvmType.Long, JvmType.Float, JvmType.Double, JvmType.AnyRef)
      def source(lane: Int): Reader.AsyncReader[Any] = lane match {
        case 0 => Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 1 => Reader.singleLong(2L).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 2 => Reader.singleFloat(3f).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 3 => Reader.singleDouble(4d).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case _ => Reader.single[AnyRef](Int.box(5)).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
      }
      def output(lane: Int, n: Int): Any = lane match {
        case 0 => n
        case 1 => n.toLong
        case 2 => n.toFloat
        case 3 => n.toDouble
        case _ => s"v$n"
      }
      def pull(interpreter: AsyncInterpreter, lane: Int): Async[Any] = lane match {
        case 0 => interpreter.readInt(-1L).map(_.asInstanceOf[Any])
        case 1 => interpreter.readLong(-1L).map(_.asInstanceOf[Any])
        case 2 => interpreter.readFloat(-1d).map(_.asInstanceOf[Any])
        case 3 => interpreter.readDouble(-1d).map(_.asInstanceOf[Any])
        case _ => interpreter.read[Any]("eof")
      }
      val crossings = ZIO.foreach(List.range(0, 25)) { crossing =>
        val in          = crossing / 5
        val out         = crossing % 5
        val calls       = new AtomicInteger(0)
        val reader      = source(in)
        val interpreter = new AsyncInterpreter(reader)
        interpreter.addAsyncCollect[Any, Any](types(in), types(out)) { value =>
          calls.incrementAndGet()
          Async.succeed(Some(output(out, value.asInstanceOf[java.lang.Number].intValue() + 10)))
        }
        val lazyBeforePull = calls.get() == 0
        run(pull(interpreter, out)).map(value =>
          assertTrue(lazyBeforePull, value == output(out, in + 11), calls.get() == 1)
        )
      }
      val seen    = new java.util.concurrent.atomic.AtomicReference[java.lang.Boolean]()
      val boolean = new AsyncInterpreter(Reader.singleBoolean(true).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      boolean.addAsyncCollect[Boolean, Boolean](JvmType.Boolean, JvmType.Boolean) { value =>
        seen.set(value); Async.succeed(Some(false))
      }
      crossings.zipWith(run(boolean.readBoolean(-1))) { case (assertions, value) =>
        assertions.reduce(_ && _) && assertTrue(value == 0, seen.get() == java.lang.Boolean.TRUE)
      }
    },
    test("async COLLECT None re-pulls exactly and skips the rejected element's continuation") {
      val reader      = new IntReader(List(1L, 2L), () => Async.succeed(()))
      val order       = new StringBuilder
      val interpreter = new AsyncInterpreter(reader)
      interpreter.addMap[Int, Long](JvmType.Int, JvmType.Long) { value => order.append('a'); value.toLong }
      interpreter.addAsyncCollect[Long, String](JvmType.Long, JvmType.AnyRef) { value =>
        order.append('b'); Async.succeed(if (value == 1L) None else Some("7"))
      }
      interpreter.addMap[String, Int](JvmType.AnyRef, JvmType.Int) { value => order.append('c'); value.toInt + 1 }
      run(interpreter.readInt(-1L)).map(value =>
        assertTrue(value == 8L, reader.reads.get() == 2, order.toString == "ababc")
      )
    },
    test("suspended incoming and outgoing async COLLECT resume their exact MAP FILTER PUSH continuations") {
      val incoming    = new Completer[Option[String]]
      val incomingLog = new StringBuilder
      val first       = new AsyncInterpreter(Reader.singleInt(2).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      first.addMap[Int, Long](JvmType.Int, JvmType.Long) { value => incomingLog.append('a'); value + 3L }
      first.addAsyncCollect[Long, String](JvmType.Long, JvmType.AnyRef) { value =>
        incomingLog.append('b'); Predef.assert(value == 5L); incoming
      }
      first.addFilter[String](JvmType.AnyRef) { value => incomingLog.append('c'); value == "7" }
      first.addPush[String](JvmType.AnyRef, JvmType.Int) { value => incomingLog.append('d'); Stream(value.toInt + 10) }
      val firstOp        = first.readInt(-1L)
      val firstSuspended = fold(firstOp.asInstanceOf[Pollable[Long]].poll(Noop)).isInstanceOf[Suspended[_]]
      val firstBefore    = incomingLog.toString
      incoming.succeed(Some("7"))

      val outgoing    = new Completer[Option[String]]
      val outgoingLog = new StringBuilder
      val second      = AsyncInterpreter.fromStreamWithAsyncCollectForTest(
        Stream(2).flatMap { value => outgoingLog.append('p'); Stream(value + 1) }.map { value =>
          outgoingLog.append('a'); value.toLong + 2L
        },
        JvmType.Long,
        JvmType.AnyRef
      ) { value => outgoingLog.append('b'); Predef.assert(value == 5L); outgoing }
      second.addFilter[String](JvmType.AnyRef) { value => outgoingLog.append('c'); value == "7" }
      second.addPush[String](JvmType.AnyRef, JvmType.Int) { value => outgoingLog.append('d'); Stream(value.toInt + 10) }
      val secondOp        = second.readInt(-1L)
      val secondSuspended = fold(secondOp.asInstanceOf[Pollable[Long]].poll(Noop)).isInstanceOf[Suspended[_]]
      val secondBefore    = outgoingLog.toString
      outgoing.succeed(Some("7"))
      run(firstOp).zipWith(run(secondOp)) { case (a, b) =>
        assertTrue(
          firstSuspended,
          secondSuspended,
          firstBefore == "ab",
          secondBefore == "pab",
          a == 17L,
          b == 17L,
          incomingLog.toString == "abcd",
          outgoingLog.toString == "pabcd"
        )
      }
    },
    test("async COLLECT follows same, replacement, and reentrant leaves without transforming them") {
      val same      = new Completer[Option[Int]]
      val callbacks = List[Async[Option[Int]]](
        same,
        new ReplaceOnce(new Replacement(Some(12))).asInstanceOf[Async[Option[Int]]],
        new ReentrantWake(Some(13)).asInstanceOf[Async[Option[Int]]]
      )
      ZIO
        .foreach(callbacks.zipWithIndex) { case (callback, index) =>
          val interpreter = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
          interpreter.addAsyncCollect[Int, Int](JvmType.Int, JvmType.Int)(_ => callback)
          val operation = interpreter.readInt(-1L)
          if (index == 0) {
            val future = operation.toFuture
            same.succeed(Some(11))
            ZIO.fromFuture(_ => future).map(value => assertTrue(value == 11L))
          } else run(operation).map(value => assertTrue(value == index + 11L))
        }
        .map(_.reduce(_ && _))
    },
    test("async COLLECT throw, null Async, null Option, and failed effects are sticky untrusted failures") {
      val thrown                                     = new RuntimeException("async-collect-throw")
      val failed                                     = new RuntimeException("async-collect-failed")
      val trusted                                    = StreamError.source("async-collect-trusted")
      val callbacks: List[Int => Async[Option[Int]]] = List(
        _ => throw thrown,
        _ => null,
        _ => Async.succeed(null.asInstanceOf[Option[Int]]),
        _ => Async.fail(failed),
        _ => Async.failTrusted(trusted)
      )
      val results = callbacks.map { callback =>
        val interpreter = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
        interpreter.addAsyncCollect(JvmType.Int, JvmType.Int)(callback)
        val first  = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
        val replay = fold(interpreter.readInt(-2L).asInstanceOf[Pollable[Long]].poll(Noop))
        (first, replay)
      }
      assertTrue(
        results(0)._1 == Failed(thrown, false),
        results(1)._1 match { case Failed(_: NullPointerException, false) => true; case _ => false },
        results(2)._1 match { case Failed(_: NullPointerException, false) => true; case _ => false },
        results(3)._1 == Failed(failed, false),
        results(4)._1 match {
          case Failed(error: StreamError, false) => (error ne trusted) && !error.isTrusted
          case _                                 => false
        },
        results.forall { case (first, replay) => first == replay }
      )
    },
    test("async COLLECT cancellation joins cleanup, rejects stale result, and restarts") {
      val done        = new Completer[Unit]
      val callback    = new CancelablePending(done)
      val reader      = new IntReader(List(1L, 2L), () => Async.succeed(()))
      val later       = new AtomicInteger(0)
      val interpreter = new AsyncInterpreter(reader)
      interpreter.addAsyncCollect[Int, Int](JvmType.Int, JvmType.Int) { value =>
        if (value == 1) callback.asInstanceOf[Async[Option[Int]]] else Async.succeed(Some(value))
      }
      interpreter.addMap[Int, Int](JvmType.Int, JvmType.Int) { value => later.incrementAndGet(); value + 1 }
      val operation = interpreter.readInt(-1L)
      operation.asInstanceOf[Pollable[Long]].poll(Noop)
      val cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
      done.succeed(())
      cleanup.block
      val next = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      assertTrue(
        callback.cancels.get() == 1,
        fold(operation.asInstanceOf[Pollable[Long]].poll(Noop)) == Succeeded(-1L),
        next == Succeeded(3L),
        reader.reads.get() == 2,
        later.get() == 1
      )
    },
    test("cancellation during async COLLECT invocation discards ready and late replacement outcomes") {
      ZIO
        .foreach(List(false, true)) { replacementResult =>
          val cleanupDone            = new Completer[Unit]
          val replacement            = new CancelablePending(cleanupDone)
          val reader                 = new IntReader(List(1L, 2L), () => Async.succeed(()))
          val interpreter            = new AsyncInterpreter(reader)
          var operation: Async[Long] = null
          var cleanup: Async[Unit]   = null
          interpreter.addAsyncCollect[Int, Int](JvmType.Int, JvmType.Int) { value =>
            if (value == 1) {
              cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
              if (replacementResult) replacement.asInstanceOf[Async[Option[Int]]] else Async.succeed(Some(99))
            } else Async.succeed(Some(value))
          }
          operation = interpreter.readInt(-1L)
          operation.asInstanceOf[Pollable[Long]].poll(Noop)
          if (replacementResult) cleanupDone.succeed(())
          cleanup.block
          val next = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
          assertTrue(
            fold(operation.asInstanceOf[Pollable[Long]].poll(Noop)) == Succeeded(-1L),
            next == Succeeded(2L),
            replacement.cancels.get() == (if (replacementResult) 1 else 0),
            reader.reads.get() == 2
          )
        }
        .map(_.reduce(_ && _))
    },
    test("cancellation while polling async COLLECT discards ready and replacement results and stale wakes") {
      ZIO
        .foreach(List(false, true)) { replacementResult =>
          val cleanupDone                = new Completer[Unit]
          val replacement                = new Replacement(Some(91))
          val reader                     = new IntReader(List(1L, 2L), () => Async.succeed(()))
          val interpreter                = new AsyncInterpreter(reader)
          var operation: Async[Long]     = null
          var cleanup: Async[Unit]       = null
          var callback: CancelDuringPoll = null
          interpreter.addAsyncCollect[Int, Int](JvmType.Int, JvmType.Int) { value =>
            if (value == 1) {
              val result: Async[Any] = if (replacementResult) replacement else Async.succeed(Some(90))
              callback = new CancelDuringPoll(
                new Runnable {
                  def run(): Unit = cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
                },
                result,
                cleanupDone
              )
              callback.asInstanceOf[Async[Option[Int]]]
            } else Async.succeed(Some(value))
          }
          operation = interpreter.readInt(-1L)
          operation.asInstanceOf[Pollable[Long]].poll(Noop)
          cleanupDone.succeed(())
          cleanup.block
          callback.captured.run()
          val next = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
          assertTrue(
            fold(operation.asInstanceOf[Pollable[Long]].poll(Noop)) == Succeeded(-1L),
            next == Succeeded(2L),
            callback.cancels.get() == 1,
            replacement.polls.get() == 0,
            reader.reads.get() == 2
          )
        }
        .map(_.reduce(_ && _))
    },
    test("async COLLECT cancellation cleanup launders trusted callback failures") {
      val trusted     = StreamError.source("async-collect-cancel")
      val callback    = new CancelablePending(Async.failTrusted(trusted))
      val interpreter = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      interpreter.addAsyncCollect[Int, Int](JvmType.Int, JvmType.Int)(_ => callback.asInstanceOf[Async[Option[Int]]])
      val operation = interpreter.readInt(-1L)
      operation.asInstanceOf[Pollable[Long]].poll(Noop)
      val failure = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]]).either.block match {
        case Left(cause) => cause
        case Right(_)    => throw new AssertionError("expected cancellation cleanup failure")
      }
      assertTrue(
        callback.cancels.get() == 1,
        failure.isInstanceOf[StreamError],
        failure.asInstanceOf[StreamError].value == "async-collect-cancel",
        !failure.asInstanceOf[StreamError].isTrusted,
        failure ne trusted,
        fold(operation.asInstanceOf[Pollable[Long]].poll(Noop)) == Failed(failure, false)
      )
    },
    test("async COLLECT shares the ready budget across callbacks and rejected source elements") {
      val accepted    = 10000
      val calls       = new AtomicInteger(0)
      val reader      = Reader.fromChunk(zio.blocks.chunk.Chunk.fromIterable(List.range(0, accepted + 1))).toAsync
      val interpreter = new AsyncInterpreter(reader.asInstanceOf[Reader.AsyncReader[Any]])
      interpreter.addAsyncCollect[Int, Int](JvmType.Int, JvmType.Int) { value =>
        calls.incrementAndGet()
        Async.succeed(if (value == accepted) Some(value + 1) else None)
      }
      run(interpreter.readInt(-1L)).map(value => assertTrue(value == accepted.toLong + 1L, calls.get() == accepted + 1))
    },
    test("white-box async TAP retains all five lanes and adapts logical Boolean") {
      val types                                      = Array(JvmType.Int, JvmType.Long, JvmType.Float, JvmType.Double, JvmType.AnyRef)
      val values                                     = Array[Any](1, 2L, 3f, 4d, "five")
      def source(lane: Int): Reader.AsyncReader[Any] = lane match {
        case 0 => Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 1 => Reader.singleLong(2L).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 2 => Reader.singleFloat(3f).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 3 => Reader.singleDouble(4d).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case _ => Reader.single("five").toAsync.asInstanceOf[Reader.AsyncReader[Any]]
      }
      def pull(interpreter: AsyncInterpreter, lane: Int): Async[Any] = lane match {
        case 0 => interpreter.readInt(-1L).map(_.asInstanceOf[Any])
        case 1 => interpreter.readLong(-1L).map(_.asInstanceOf[Any])
        case 2 => interpreter.readFloat(-1d).map(_.asInstanceOf[Any])
        case 3 => interpreter.readDouble(-1d).map(_.asInstanceOf[Any])
        case _ => interpreter.read[Any]("eof")
      }
      val lanes = ZIO.foreach(List.range(0, 5)) { lane =>
        val seen        = new java.util.concurrent.atomic.AtomicReference[Any]()
        val calls       = new AtomicInteger(0)
        val interpreter = new AsyncInterpreter(source(lane))
        interpreter.addAsyncTap[Any](types(lane)) { value =>
          seen.set(value); calls.incrementAndGet(); Async.succeed(())
        }
        val lazyBeforePull = calls.get() == 0
        run(pull(interpreter, lane)).map(value =>
          assertTrue(lazyBeforePull, value == values(lane), seen.get() == values(lane), calls.get() == 1)
        )
      }
      val booleanSeen = new java.util.concurrent.atomic.AtomicReference[java.lang.Boolean]()
      val boolean     = new AsyncInterpreter(Reader.singleBoolean(true).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      boolean.addAsyncTap[Boolean](JvmType.Boolean) { value => booleanSeen.set(value); Async.succeed(()) }
      lanes.zipWith(run(boolean.readBoolean(-1))) { case (assertions, value) =>
        assertions.reduce(_ && _) && assertTrue(value == 1, booleanSeen.get() == java.lang.Boolean.TRUE)
      }
    },
    test("suspended incoming and outgoing async TAP retain registers and exact MAP FILTER PUSH continuations") {
      val incoming    = new Completer[Unit]
      val incomingLog = new StringBuilder
      val first       = new AsyncInterpreter(Reader.singleInt(2).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      first.addMap[Int, Long](JvmType.Int, JvmType.Long) { value => incomingLog.append('a'); value + 3L }
      first.addAsyncTap[Long](JvmType.Long) { value => incomingLog.append('b'); Predef.assert(value == 5L); incoming }
      first.addFilter[Long](JvmType.Long) { value => incomingLog.append('c'); value == 5L }
      first.addPush[Long](JvmType.Long, JvmType.Int) { value => incomingLog.append('d'); Stream(value.toInt + 10) }
      val firstOp = first.readInt(-1L)
      firstOp.asInstanceOf[Pollable[Long]].poll(Noop)

      val outgoing    = new Completer[Unit]
      val outgoingLog = new StringBuilder
      val second      = AsyncInterpreter.fromStreamWithAsyncTapForTest(
        Stream(2).flatMap { value => outgoingLog.append('p'); Stream(value + 1) }.map { value =>
          outgoingLog.append('a'); value.toLong + 2L
        },
        JvmType.Long
      ) { value => outgoingLog.append('b'); Predef.assert(value == 5L); outgoing }
      second.addFilter[Long](JvmType.Long) { value => outgoingLog.append('c'); value == 5L }
      second.addPush[Long](JvmType.Long, JvmType.Int) { value => outgoingLog.append('d'); Stream(value.toInt + 10) }
      val secondOp = second.readInt(-1L)
      secondOp.asInstanceOf[Pollable[Long]].poll(Noop)
      val before = (incomingLog.toString, outgoingLog.toString)
      incoming.succeed(())
      outgoing.succeed(())
      run(firstOp).zipWith(run(secondOp)) { case (a, b) =>
        assertTrue(
          before == (("ab", "pab")),
          a == 15L,
          b == 15L,
          incomingLog.toString == "abcd",
          outgoingLog.toString == "pabcd"
        )
      }
    },
    test("async TAP drives raw same, replacement, and reentrant callback leaves") {
      val same        = new Completer[Unit]
      val replacement = new Replacement(())
      val replaceOnce = new ReplaceOnce(replacement)
      val reentrant   = new ReentrantWake(())
      val effects     = List[Async[Unit]](same, replaceOnce.asInstanceOf[Async[Unit]], reentrant.asInstanceOf[Async[Unit]])
      ZIO
        .foreach(effects.zipWithIndex) { case (effect, index) =>
          val interpreter =
            new AsyncInterpreter(Reader.singleInt(index + 1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
          interpreter.addAsyncTap[Int](JvmType.Int)(_ => effect)
          val operation = interpreter.readInt(-1L)
          if (index == 0) { val future = operation.toFuture; same.succeed(()); ZIO.fromFuture(_ => future) }
          else run(operation)
        }
        .map { values =>
          assertTrue(
            values == List(1L, 2L, 3L),
            replaceOnce.polls.get() == 1,
            replacement.polls.get() == 1,
            reentrant.polls.get() == 2
          )
        }
    },
    test("async TAP throw, null, ordinary failure, and trusted StreamError are sticky untrusted failures") {
      val thrown                              = new RuntimeException("async-tap-throw")
      val failed                              = new RuntimeException("async-tap-failed")
      val trusted                             = StreamError.source("async-tap-trusted")
      val callbacks: List[Int => Async[Unit]] =
        List(_ => throw thrown, _ => null, _ => Async.fail(failed), _ => Async.failTrusted(trusted))
      val results = callbacks.map { callback =>
        val interpreter = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
        interpreter.addAsyncTap(JvmType.Int)(callback)
        val first  = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
        val replay = fold(interpreter.readInt(-2L).asInstanceOf[Pollable[Long]].poll(Noop))
        (first, replay)
      }
      assertTrue(
        results(0)._1 == Failed(thrown, false),
        results(1)._1 match { case Failed(_: NullPointerException, false) => true; case _ => false },
        results(2)._1 == Failed(failed, false),
        results(3)._1 match {
          case Failed(error: StreamError, false) => (error ne trusted) && !error.isTrusted; case _ => false
        },
        results.forall { case (first, replay) => first == replay }
      )
    },
    test("async TAP cancellation joins cleanup, launders trusted cleanup, rejects stale completion, and restarts") {
      val trusted     = StreamError.source("async-tap-cancel")
      val callback    = new CancelablePending(Async.failTrusted(trusted))
      val reader      = new IntReader(List(1L, 2L), () => Async.succeed(()))
      val interpreter = new AsyncInterpreter(reader)
      interpreter.addAsyncTap[Int](JvmType.Int) { value =>
        if (value == 1) callback.asInstanceOf[Async[Unit]] else Async.succeed(())
      }
      val operation = interpreter.readInt(-1L)
      operation.asInstanceOf[Pollable[Long]].poll(Noop)
      val failure = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]]).either.block match {
        case Left(cause) => cause
        case Right(_)    => throw new AssertionError("expected cancellation cleanup failure")
      }
      val next = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      assertTrue(
        callback.cancels.get() == 1,
        failure.isInstanceOf[StreamError],
        failure.asInstanceOf[StreamError].value == "async-tap-cancel",
        !failure.asInstanceOf[StreamError].isTrusted,
        failure ne trusted,
        fold(operation.asInstanceOf[Pollable[Long]].poll(Noop)) == Failed(failure, false),
        next == Failed(failure, false)
      )
    },
    test("async TAP cancellation during invocation and polling discards ready and replacement outcomes") {
      ZIO
        .foreach(List(false, true).flatMap(replacement => List((false, replacement), (true, replacement)))) {
          case (duringPoll, replacementResult) =>
            val cleanupDone                = new Completer[Unit]
            val replacement                = new CancelablePending(cleanupDone)
            val reader                     = new IntReader(List(1L, 2L), () => Async.succeed(()))
            val interpreter                = new AsyncInterpreter(reader)
            var operation: Async[Long]     = null
            var cleanup: Async[Unit]       = null
            var pollable: CancelDuringPoll = null
            interpreter.addAsyncTap[Int](JvmType.Int) { value =>
              if (value != 1) Async.succeed(())
              else if (duringPoll) {
                val result: Async[Any] = if (replacementResult) replacement else Async.succeed(())
                pollable = new CancelDuringPoll(
                  new Runnable {
                    def run(): Unit = cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
                  },
                  result,
                  cleanupDone
                )
                pollable.asInstanceOf[Async[Unit]]
              } else {
                cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
                if (replacementResult) replacement.asInstanceOf[Async[Unit]] else Async.succeed(())
              }
            }
            operation = interpreter.readInt(-1L)
            operation.asInstanceOf[Pollable[Long]].poll(Noop)
            if (replacementResult || duringPoll) cleanupDone.succeed(())
            cleanup.block
            if (duringPoll) pollable.captured.run()
            val next = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
            assertTrue(
              fold(operation.asInstanceOf[Pollable[Long]].poll(Noop)) == Succeeded(-1L),
              next == Succeeded(2L),
              reader.reads.get() == 2
            )
        }
        .map(_.reduce(_ && _))
    },
    test("more than one ready-budget of async TAP callbacks is bounded and preserves the value") {
      val count       = 263
      val calls       = new AtomicInteger(0)
      val interpreter = new AsyncInterpreter(Reader.singleInt(7).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      var index       = 0
      while (index < count) {
        val expected = index
        interpreter.addAsyncTap[Int](JvmType.Int) { value =>
          Predef.assert(value == 7); Predef.assert(calls.getAndIncrement() == expected); Async.succeed(())
        }
        index += 1
      }
      run(interpreter.readInt(-1L)).map(value => assertTrue(value == 7L, calls.get() == count))
    },
    test("async DISTINCT_KEY retains all input lanes and logical Boolean and accepts null keys") {
      val types                                      = Array(JvmType.Int, JvmType.Long, JvmType.Float, JvmType.Double, JvmType.AnyRef)
      val values                                     = Array[Any](1, 2L, 3f, 4d, "five")
      def source(lane: Int): Reader.AsyncReader[Any] = lane match {
        case 0 => Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 1 => Reader.singleLong(2L).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 2 => Reader.singleFloat(3f).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 3 => Reader.singleDouble(4d).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case _ => Reader.single("five").toAsync.asInstanceOf[Reader.AsyncReader[Any]]
      }
      def pull(interpreter: AsyncInterpreter, lane: Int): Async[Any] = lane match {
        case 0 => interpreter.readInt(-1L).map(_.asInstanceOf[Any])
        case 1 => interpreter.readLong(-1L).map(_.asInstanceOf[Any])
        case 2 => interpreter.readFloat(-1d).map(_.asInstanceOf[Any])
        case 3 => interpreter.readDouble(-1d).map(_.asInstanceOf[Any])
        case _ => interpreter.read[Any]("eof")
      }
      val lanes = ZIO.foreach(List.range(0, 5)) { lane =>
        val seen        = new java.util.concurrent.atomic.AtomicReference[Any]()
        val interpreter = new AsyncInterpreter(source(lane))
        val nullKey     = new Completer[Any]
        nullKey.succeed(null)
        interpreter.addAsyncDistinctKey[Any, Any](types(lane)) { value => seen.set(value); nullKey }
        run(pull(interpreter, lane)).map(value => assertTrue(value == values(lane), seen.get() == values(lane)))
      }
      val booleanSeen = new java.util.concurrent.atomic.AtomicReference[java.lang.Boolean]()
      val boolean     = new AsyncInterpreter(Reader.singleBoolean(true).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      boolean.addAsyncDistinctKey[Boolean, Boolean](JvmType.Boolean) { value =>
        booleanSeen.set(value); Async.succeed(value)
      }
      lanes.zipWith(run(boolean.readBoolean(-1))) { case (assertions, value) =>
        assertions.reduce(_ && _) && assertTrue(value == 1, booleanSeen.get() == java.lang.Boolean.TRUE)
      }
    },
    test("async DISTINCT_KEY rejects duplicates exactly and skips their continuation") {
      val reader      = new IntReader(List(1L, 1L, 2L), () => Async.succeed(()))
      val order       = new StringBuilder
      val interpreter = new AsyncInterpreter(reader)
      interpreter.addMap[Int, Long](JvmType.Int, JvmType.Long) { value => order.append('a'); value.toLong }
      interpreter.addAsyncDistinctKey[Long, Long](JvmType.Long) { value => order.append('b'); Async.succeed(value) }
      interpreter.addMap[Long, Int](JvmType.Long, JvmType.Int) { value => order.append('c'); value.toInt + 10 }
      run(interpreter.readInt(-1L)).flatMap { first =>
        run(interpreter.readInt(-1L)).map { second =>
          assertTrue(first == 11L, second == 12L, reader.reads.get() == 3, order.toString == "abcababc")
        }
      }
    },
    test("suspended incoming and outgoing async DISTINCT_KEY preserve MAP FILTER PUSH continuation") {
      val incoming = new Completer[String]
      val inLog    = new StringBuilder
      val first    = new AsyncInterpreter(Reader.singleInt(2).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      first.addMap[Int, Long](JvmType.Int, JvmType.Long) { value => inLog.append('a'); value + 3L }
      first.addAsyncDistinctKey[Long, String](JvmType.Long) { _ => inLog.append('b'); incoming }
      first.addFilter[Long](JvmType.Long) { value => inLog.append('c'); value == 5L }
      first.addPush[Long](JvmType.Long, JvmType.Int) { value => inLog.append('d'); Stream(value.toInt + 10) }
      val firstOp = first.readInt(-1L)
      firstOp.asInstanceOf[Pollable[Long]].poll(Noop)
      val outgoing = new Completer[String]
      val outLog   = new StringBuilder
      val second   = AsyncInterpreter.fromStream(Stream(2).flatMap { value => outLog.append('p'); Stream(value + 1) })
      second.addMap[Int, Long](JvmType.Int, JvmType.Long) { value => outLog.append('a'); value + 2L }
      second.addAsyncDistinctKey[Long, String](JvmType.Long) { _ => outLog.append('b'); outgoing }
      second.addFilter[Long](JvmType.Long) { value => outLog.append('c'); value == 5L }
      second.addPush[Long](JvmType.Long, JvmType.Int) { value => outLog.append('d'); Stream(value.toInt + 10) }
      val secondOp = second.readInt(-1L)
      secondOp.asInstanceOf[Pollable[Long]].poll(Noop)
      val before = (inLog.toString, outLog.toString)
      incoming.succeed("key")
      outgoing.succeed("key")
      run(firstOp).zipWith(run(secondOp)) { case (a, b) =>
        assertTrue(before == (("ab", "pab")), a == 15L, b == 15L, inLog.toString == "abcd", outLog.toString == "pabcd")
      }
    },
    test("async DISTINCT_KEY drives raw leaves and launders callback failures") {
      val same        = new Completer[Any]
      val replacement = new Replacement("b")
      val effects     = List[Async[Any]](same, new ReplaceOnce(replacement), new ReentrantWake("c"))
      val successes   = ZIO.foreach(effects.zipWithIndex) { case (effect, index) =>
        val interpreter =
          new AsyncInterpreter(Reader.singleInt(index + 1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
        interpreter.addAsyncDistinctKey[Int, Any](JvmType.Int)(_ => effect)
        val operation = interpreter.readInt(-1L)
        if (index == 0) { val future = operation.toFuture; same.succeed("a"); ZIO.fromFuture(_ => future) }
        else run(operation)
      }
      val thrown   = new RuntimeException("distinct-throw")
      val failed   = new RuntimeException("distinct-failed")
      val trusted  = StreamError.source("distinct-trusted")
      val failures = List[Int => Async[Any]](
        _ => throw thrown,
        _ => null,
        _ => Async.fail(failed),
        _ => Async.failTrusted(trusted)
      ).map { callback =>
        val interpreter = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
        interpreter.addAsyncDistinctKey(JvmType.Int)(callback)
        fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      }
      successes.map(values =>
        assertTrue(
          values == List(1L, 2L, 3L),
          failures(0) == Failed(thrown, false),
          failures(1) match { case Failed(_: NullPointerException, false) => true; case _ => false },
          failures(2) == Failed(failed, false),
          failures(3) match {
            case Failed(error: StreamError, false) => (error ne trusted) && !error.isTrusted; case _ => false
          }
        )
      )
    },
    test("async DISTINCT_KEY cancellation before commit leaves the set unchanged") {
      val done        = new Completer[Unit]
      val pending     = new CancelablePending(done)
      val calls       = new AtomicInteger(0)
      val reader      = new IntReader(List(1L, 1L), () => Async.succeed(()))
      val interpreter = new AsyncInterpreter(reader)
      interpreter.addAsyncDistinctKey[Int, Int](JvmType.Int) { value =>
        if (calls.getAndIncrement() == 0) pending.asInstanceOf[Async[Int]] else Async.succeed(value)
      }
      val operation = interpreter.readInt(-1L)
      operation.asInstanceOf[Pollable[Long]].poll(Noop)
      val cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
      done.succeed(())
      cleanup.block
      val next = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      assertTrue(next == Succeeded(1L), calls.get() == 2, reader.reads.get() == 2, pending.cancels.get() == 1)
    },
    test("cancellation during async DISTINCT_KEY invocation discards ready and late replacement keys") {
      ZIO
        .foreach(List(false, true)) { replacementResult =>
          val cleanupDone            = new Completer[Unit]
          val replacement            = new CancelablePending(cleanupDone)
          val callbacks              = new AtomicInteger(0)
          val later                  = new AtomicInteger(0)
          val reader                 = new IntReader(List(1L, 1L), () => Async.succeed(()))
          val interpreter            = new AsyncInterpreter(reader)
          var operation: Async[Long] = null
          var cleanup: Async[Unit]   = null
          interpreter.addAsyncDistinctKey[Int, Int](JvmType.Int) { _ =>
            if (callbacks.getAndIncrement() == 0) {
              cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
              if (replacementResult) replacement.asInstanceOf[Async[Int]] else Async.succeed(1)
            } else Async.succeed(1)
          }
          interpreter.addMap[Int, Int](JvmType.Int, JvmType.Int) { value => later.incrementAndGet(); value + 1 }
          operation = interpreter.readInt(-1L)
          operation.asInstanceOf[Pollable[Long]].poll(Noop)
          if (replacementResult) cleanupDone.succeed(())
          cleanup.block
          val next = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
          assertTrue(
            fold(operation.asInstanceOf[Pollable[Long]].poll(Noop)) == Succeeded(-1L),
            next == Succeeded(2L),
            callbacks.get() == 2,
            later.get() == 1,
            replacement.cancels.get() == (if (replacementResult) 1 else 0),
            reader.reads.get() == 2
          )
        }
        .map(_.reduce(_ && _))
    },
    test("cancellation while polling async DISTINCT_KEY discards ready and replacement keys and stale wakes") {
      ZIO
        .foreach(List(false, true)) { replacementResult =>
          val cleanupDone                = new Completer[Unit]
          val replacement                = new Replacement(1)
          val callbacks                  = new AtomicInteger(0)
          val later                      = new AtomicInteger(0)
          val reader                     = new IntReader(List(1L, 1L), () => Async.succeed(()))
          val interpreter                = new AsyncInterpreter(reader)
          var operation: Async[Long]     = null
          var cleanup: Async[Unit]       = null
          var callback: CancelDuringPoll = null
          interpreter.addAsyncDistinctKey[Int, Int](JvmType.Int) { _ =>
            if (callbacks.getAndIncrement() == 0) {
              val result: Async[Any] = if (replacementResult) replacement else Async.succeed(1)
              callback = new CancelDuringPoll(
                new Runnable {
                  def run(): Unit = cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
                },
                result,
                cleanupDone
              )
              callback.asInstanceOf[Async[Int]]
            } else Async.succeed(1)
          }
          interpreter.addMap[Int, Int](JvmType.Int, JvmType.Int) { value => later.incrementAndGet(); value + 1 }
          operation = interpreter.readInt(-1L)
          operation.asInstanceOf[Pollable[Long]].poll(Noop)
          cleanupDone.succeed(())
          cleanup.block
          callback.captured.run()
          val next = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
          assertTrue(
            fold(operation.asInstanceOf[Pollable[Long]].poll(Noop)) == Succeeded(-1L),
            next == Succeeded(2L),
            callback.cancels.get() == 1,
            callbacks.get() == 2,
            later.get() == 1,
            replacement.polls.get() == 0,
            reader.reads.get() == 2
          )
        }
        .map(_.reduce(_ && _))
    },
    test("async DISTINCT_KEY cancellation cleanup launders trusted callback failures") {
      val trusted     = StreamError.source("async-distinct-key-cancel")
      val callback    = new CancelablePending(Async.failTrusted(trusted))
      val interpreter = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      interpreter.addAsyncDistinctKey[Int, Int](JvmType.Int)(_ => callback.asInstanceOf[Async[Int]])
      val operation = interpreter.readInt(-1L)
      operation.asInstanceOf[Pollable[Long]].poll(Noop)
      val failure = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]]).either.block match {
        case Left(cause) => cause
        case Right(_)    => throw new AssertionError("expected cancellation cleanup failure")
      }
      assertTrue(
        callback.cancels.get() == 1,
        failure.isInstanceOf[StreamError],
        failure.asInstanceOf[StreamError].value == "async-distinct-key-cancel",
        !failure.asInstanceOf[StreamError].isTrusted,
        failure ne trusted,
        fold(operation.asInstanceOf[Pollable[Long]].poll(Noop)) == Failed(failure, false)
      )
    },
    test("async DISTINCT_KEY hashes and compares outside the monitor before epoch-checked commit") {
      final class HostileKey(val id: Int, hashAction: () => Unit, equalsAction: () => Unit) {
        override def hashCode(): Int             = { hashAction(); id }
        override def equals(other: Any): Boolean = {
          equalsAction()
          (other.asInstanceOf[AnyRef] ne null) && other.getClass == getClass &&
          id == other.asInstanceOf[HostileKey].id
        }
      }

      val hashCalls                  = new AtomicInteger(0)
      val hashReader                 = new IntReader(List(1L, 1L), () => Async.succeed(()))
      val hashInterpreter            = new AsyncInterpreter(hashReader)
      var hashOperation: Async[Long] = null
      var hashCleanup: Async[Unit]   = null
      hashInterpreter.addAsyncDistinctKey[Int, HostileKey](JvmType.Int) { value =>
        Async.succeed(
          new HostileKey(
            value,
            () =>
              if (hashCalls.getAndIncrement() == 0)
                hashCleanup = Async.cancelWithCleanup(hashOperation.asInstanceOf[Pollable[Long]]),
            () => ()
          )
        )
      }
      hashOperation = hashInterpreter.readInt(-1L)
      hashOperation.asInstanceOf[Pollable[Long]].poll(Noop)
      hashCleanup.block
      val hashRetry = fold(hashInterpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))

      val equalsCalls                  = new AtomicInteger(0)
      val equalsReader                 = new IntReader(List(1L, 1L, 2L), () => Async.succeed(()))
      val equalsInterpreter            = new AsyncInterpreter(equalsReader)
      var equalsOperation: Async[Long] = null
      var equalsCleanup: Async[Unit]   = null
      equalsInterpreter.addAsyncDistinctKey[Int, HostileKey](JvmType.Int) { value =>
        Async.succeed(
          new HostileKey(
            value,
            () => (),
            () =>
              if (
                value == 1 && equalsCalls.getAndIncrement() == 0 &&
                (equalsOperation.asInstanceOf[AnyRef] ne null)
              )
                equalsCleanup = Async.cancelWithCleanup(equalsOperation.asInstanceOf[Pollable[Long]])
          )
        )
      }
      val equalsFirst = fold(equalsInterpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      equalsOperation = equalsInterpreter.readInt(-1L)
      equalsOperation.asInstanceOf[Pollable[Long]].poll(Noop)
      equalsCleanup.block
      val equalsNext = fold(equalsInterpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))

      val trusted            = StreamError.source("distinct-equals")
      val failureReader      = new IntReader(List(1L, 1L), () => Async.succeed(()))
      val failureInterpreter = new AsyncInterpreter(failureReader)
      var failEquals         = false
      failureInterpreter.addAsyncDistinctKey[Int, HostileKey](JvmType.Int) { value =>
        Async.succeed(new HostileKey(value, () => (), () => if (failEquals) throw trusted))
      }
      val failureFirst = fold(failureInterpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      failEquals = true
      val failureSecond = fold(failureInterpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))

      assertTrue(
        fold(hashOperation.asInstanceOf[Pollable[Long]].poll(Noop)) == Succeeded(-1L),
        hashRetry == Succeeded(1L),
        hashReader.reads.get() == 2,
        equalsFirst == Succeeded(1L),
        fold(equalsOperation.asInstanceOf[Pollable[Long]].poll(Noop)) == Succeeded(-1L),
        equalsNext == Succeeded(2L),
        equalsReader.reads.get() == 3,
        failureFirst == Succeeded(1L),
        failureSecond match {
          case Failed(error: StreamError, false) =>
            (error ne trusted) && error.value == "distinct-equals" && !error.isTrusted
          case _ => false
        }
      )
    },
    test("async DISTINCT_KEY sets are isolated per operation and interpreter") {
      val first = new AsyncInterpreter(
        Reader.fromChunk(zio.blocks.chunk.Chunk(1, 1)).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
      )
      first.addAsyncDistinctKey[Int, Int](JvmType.Int)(_ => Async.succeed(0))
      first.addAsyncDistinctKey[Int, Int](JvmType.Int)(_ => Async.succeed(0))
      val second = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      second.addAsyncDistinctKey[Int, Int](JvmType.Int)(_ => Async.succeed(0))
      run(first.readInt(-1L)).zipWith(run(second.readInt(-1L))) { case (a, b) => assertTrue(a == 1L, b == 1L) }
    },
    test("async DISTINCT_KEY shares bounded ready work across callbacks and duplicate rejections") {
      val accepted    = 10000
      val calls       = new AtomicInteger(0)
      val reader      = Reader.fromChunk(zio.blocks.chunk.Chunk.fromIterable(List.range(0, accepted + 1))).toAsync
      val interpreter = new AsyncInterpreter(reader.asInstanceOf[Reader.AsyncReader[Any]])
      interpreter.addAsyncDistinctKey[Int, Int](JvmType.Int) { value =>
        calls.incrementAndGet(); Async.succeed(if (value == accepted) 1 else 0)
      }
      run(interpreter.readInt(-1L)).flatMap { value =>
        val firstCalls = calls.get()
        run(interpreter.readInt(-1L)).map(second =>
          assertTrue(value == 0L, firstCalls == 1, second == accepted.toLong, calls.get() == accepted + 1)
        )
      }
    },
    test("white-box async MAP_ACCUM transactionally executes all 25 lane crossings and Boolean") {
      val types                                      = Array(JvmType.Int, JvmType.Long, JvmType.Float, JvmType.Double, JvmType.AnyRef)
      def source(lane: Int): Reader.AsyncReader[Any] = lane match {
        case 0 => Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 1 => Reader.singleLong(2L).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 2 => Reader.singleFloat(3f).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 3 => Reader.singleDouble(4d).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case _ => Reader.single[AnyRef](Int.box(5)).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
      }
      def output(lane: Int, n: Int): Any = lane match {
        case 0 => n; case 1 => n.toLong; case 2 => n.toFloat; case 3 => n.toDouble; case _ => s"v$n"
      }
      def pull(i: AsyncInterpreter, lane: Int): Async[Any] = lane match {
        case 0 => i.readInt(-1L).map(_.asInstanceOf[Any]); case 1   => i.readLong(-1L).map(_.asInstanceOf[Any])
        case 2 => i.readFloat(-1d).map(_.asInstanceOf[Any]); case 3 => i.readDouble(-1d).map(_.asInstanceOf[Any])
        case _ => i.read[Any]("eof")
      }
      val lanes = ZIO.foreach(List.range(0, 25)) { crossing =>
        val in          = crossing / 5; val out = crossing % 5; val calls = new AtomicInteger(0)
        val interpreter = new AsyncInterpreter(source(in))
        interpreter.addAsyncMapAccum[Int, Any, Any](10, types(in), types(out)) { (state, value) =>
          calls.incrementAndGet()
          val next = state + value.asInstanceOf[java.lang.Number].intValue()
          Async.succeed((next, output(out, next)))
        }
        run(pull(interpreter, out)).map(value => assertTrue(value == output(out, in + 11), calls.get() == 1))
      }
      val boolean = new AsyncInterpreter(Reader.singleBoolean(true).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      boolean.addAsyncMapAccum[Int, Boolean, Boolean](0, JvmType.Boolean, JvmType.Boolean) { (s, value) =>
        Async.succeed((s + 1, !value))
      }
      lanes.zipWith(run(boolean.readBoolean(-1)))((assertions, value) =>
        assertions.reduce(_ && _) && assertTrue(value == 0)
      )
    },
    test("async MAP_ACCUM cancellation retries old state without committing the candidate") {
      val cleanupDone = new Completer[Unit]
      val pending     = new CancelablePending(cleanupDone)
      val calls       = new AtomicInteger(0)
      val states      = new scala.collection.mutable.ArrayBuffer[Int]
      val reader      = new IntReader(List(1L, 1L, 2L), () => Async.succeed(()))
      val interpreter = new AsyncInterpreter(reader)
      interpreter.addAsyncMapAccum[Int, Int, Int](10, JvmType.Int, JvmType.Int) { (state, value) =>
        states += state
        if (calls.getAndIncrement() == 0) pending.asInstanceOf[Async[(Int, Int)]]
        else Async.succeed((state + value, state + value))
      }
      val operation = interpreter.readInt(-1L)
      operation.asInstanceOf[Pollable[Long]].poll(Noop)
      val cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
      cleanupDone.succeed(())
      cleanup.block
      val retry = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      assertTrue(
        fold(operation.asInstanceOf[Pollable[Long]].poll(Noop)) == Succeeded(-1L),
        retry == Succeeded(11L),
        states.toList == List(10, 10),
        calls.get() == 2,
        pending.cancels.get() == 1,
        reader.reads.get() == 2
      )
    },
    test("async MAP_ACCUM cancellation cleanup normalizes trusted callback provenance") {
      val trusted     = StreamError.source("async-map-accum-cancel")
      val pending     = new CancelablePending(Async.failTrusted(trusted))
      val interpreter = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      interpreter.addAsyncMapAccum[Int, Int, Int](0, JvmType.Int, JvmType.Int) { (_, _) =>
        pending.asInstanceOf[Async[(Int, Int)]]
      }
      val operation = interpreter.readInt(-1L)
      operation.asInstanceOf[Pollable[Long]].poll(Noop)
      val failure = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]]).either.block match {
        case Left(cause) => cause
        case Right(_)    => throw new AssertionError("expected cancellation cleanup failure")
      }
      assertTrue(
        pending.cancels.get() == 1,
        failure.isInstanceOf[StreamError],
        failure.asInstanceOf[StreamError].value == "async-map-accum-cancel",
        !failure.asInstanceOf[StreamError].isTrusted,
        failure ne trusted,
        fold(operation.asInstanceOf[Pollable[Long]].poll(Noop)) == Failed(failure, false)
      )
    },
    test("async MAP_ACCUM rejects nulls and isolates instruction and interpreter state") {
      val nullAsync = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      nullAsync.addAsyncMapAccum[Int, Int, Int](0, JvmType.Int, JvmType.Int)((_, _) => null)
      val nullTuple = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      nullTuple.addAsyncMapAccum[Int, Int, Int](0, JvmType.Int, JvmType.Int)((_, _) => Async.succeed(null))
      def accumulating(): AsyncInterpreter = {
        val i = new AsyncInterpreter(
          Reader.fromChunk(zio.blocks.chunk.Chunk(1, 1)).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        )
        i.addAsyncMapAccum[Int, Int, Int](0, JvmType.Int, JvmType.Int)((s, a) => Async.succeed((s + a, s + a)))
        i
      }
      val a = accumulating(); val b = accumulating()
      for { a1 <- run(a.readInt(-1L)); a2 <- run(a.readInt(-1L)); b1 <- run(b.readInt(-1L)) } yield assertTrue(
        fold(nullAsync.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop)) match {
          case Failed(_: NullPointerException, false) => true; case _ => false
        },
        fold(nullTuple.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop)) match {
          case Failed(_: NullPointerException, false) => true; case _ => false
        },
        a1 == 1L,
        a2 == 2L,
        b1 == 1L
      )
    },
    test("white-box async scan emits init before source and callback, including empty input, then transitions") {
      val calls   = new AtomicInteger(0)
      val reader  = new IntReader(List(2L, 3L), () => Async.succeed(()))
      val pending = new Completer[Int]
      val scan    = AsyncInterpreter.fromReaderWithAsyncScanForTest[Int, Int](
        reader,
        10,
        JvmType.Int,
        JvmType.Int
      ) { (state, value) =>
        calls.incrementAndGet()
        if (value == 2) pending else Async.succeed(state + value)
      }
      scan.addMap[Int, Int](JvmType.Int, JvmType.Int)(_ + 1)
      val first           = fold(scan.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      val secondOperation = scan.readInt(-1L)
      val second          = fold(secondOperation.asInstanceOf[Pollable[Long]].poll(Noop))
      val before          = (reader.reads.get(), calls.get())
      pending.succeed(12)
      val emptyReader = new IntReader(Nil, () => Async.succeed(()))
      val emptyCalls  = new AtomicInteger(0)
      val empty       = AsyncInterpreter.fromReaderWithAsyncScanForTest[Int, Int](
        emptyReader,
        7,
        JvmType.Int,
        JvmType.Int
      ) { (state, value) => emptyCalls.incrementAndGet(); Async.succeed(state + value) }
      val outgoingOrder = new StringBuilder
      val outgoing      = AsyncInterpreter.fromStreamWithAsyncScanForTest[Int, Int](
        Stream(1).flatMap { value => outgoingOrder.append('p'); Stream(value) },
        5,
        JvmType.Int,
        JvmType.Int
      ) { (state, value) => outgoingOrder.append('a'); Async.succeed(state + value) }
      outgoing.addMap[Int, Int](JvmType.Int, JvmType.Int) { value => outgoingOrder.append('m'); value + 1 }
      val outgoingInit = fold(outgoing.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      for {
        secondValue <- run(secondOperation)
        third       <- run(scan.readInt(-1L))
        end         <- run(scan.readInt(-1L))
        emptyInit   <- run(empty.readInt(-1L))
        emptyEnd    <- run(empty.readInt(-1L))
      } yield assertTrue(
        first == Succeeded(11L),
        reader.reads.get() == 3,
        calls.get() == 2,
        second.isInstanceOf[Suspended[_]],
        before == ((1, 1)),
        secondValue == 13L,
        third == 16L,
        end == -1L,
        emptyInit == 7L,
        emptyEnd == -1L,
        emptyReader.reads.get() == 1,
        emptyCalls.get() == 0,
        outgoingInit == Succeeded(6L),
        outgoingOrder.toString == "m"
      )
    },
    test("incoming scans initialize downstream-first through a stateful accumulator") {
      val reader        = new IntReader(List(1L), () => Async.succeed(()))
      val incomingCalls = new AtomicInteger(0)
      val accumCalls    = new AtomicInteger(0)
      val outgoingCalls = new AtomicInteger(0)
      val interpreter   = new AsyncInterpreter(reader)
      interpreter.addAsyncScan[Int, Int](10, JvmType.Int, JvmType.Int) { (state, value) =>
        incomingCalls.incrementAndGet(); Async.succeed(state + value)
      }
      interpreter.addAsyncMapAccum[Int, Int, Int](1000, JvmType.Int, JvmType.Int) { (state, value) =>
        accumCalls.incrementAndGet(); Async.succeed((state + value, state + value))
      }
      interpreter.addAsyncScan[Int, Int](100, JvmType.Int, JvmType.Int) { (state, value) =>
        outgoingCalls.incrementAndGet(); Async.succeed(state + value)
      }
      val outer                 = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      val immediatelyAfterOuter =
        (reader.reads.get(), incomingCalls.get(), accumCalls.get(), outgoingCalls.get())
      for {
        innerThroughOuter <- run(interpreter.readInt(-1L))
        inputThroughBoth  <- run(interpreter.readInt(-1L))
      } yield assertTrue(
        outer == Succeeded(100L),
        immediatelyAfterOuter == ((0, 0, 0, 0)),
        innerThroughOuter == 1110L,
        inputThroughBoth == 2131L,
        reader.reads.get() == 1,
        incomingCalls.get() == 1,
        accumCalls.get() == 2,
        outgoingCalls.get() == 2
      )
    },
    test("outgoing scans after PUSH initialize downstream-first through a stateful accumulator") {
      val reader        = new IntReader(List(1L), () => Async.succeed(()))
      val pushCalls     = new AtomicInteger(0)
      val incomingCalls = new AtomicInteger(0)
      val accumCalls    = new AtomicInteger(0)
      val outgoingCalls = new AtomicInteger(0)
      val interpreter   = new AsyncInterpreter(reader)
      interpreter.addPush[Int](JvmType.Int, JvmType.Int) { value =>
        pushCalls.incrementAndGet(); Stream(value)
      }
      interpreter.addAsyncScan[Int, Int](10, JvmType.Int, JvmType.Int) { (state, value) =>
        incomingCalls.incrementAndGet(); Async.succeed(state + value)
      }
      interpreter.addAsyncMapAccum[Int, Int, Int](1000, JvmType.Int, JvmType.Int) { (state, value) =>
        accumCalls.incrementAndGet(); Async.succeed((state + value, state + value))
      }
      interpreter.addAsyncScan[Int, Int](100, JvmType.Int, JvmType.Int) { (state, value) =>
        outgoingCalls.incrementAndGet(); Async.succeed(state + value)
      }
      val outer                 = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      val immediatelyAfterOuter =
        (reader.reads.get(), pushCalls.get(), incomingCalls.get(), accumCalls.get(), outgoingCalls.get())
      for {
        innerThroughOuter <- run(interpreter.readInt(-1L))
        inputThroughBoth  <- run(interpreter.readInt(-1L))
      } yield assertTrue(
        outer == Succeeded(100L),
        immediatelyAfterOuter == ((0, 0, 0, 0, 0)),
        innerThroughOuter == 1110L,
        inputThroughBoth == 2131L,
        reader.reads.get() == 1,
        pushCalls.get() == 1,
        incomingCalls.get() == 1,
        accumCalls.get() == 2,
        outgoingCalls.get() == 2
      )
    },
    test("two independent MAP_ACCUM instructions retain separate state across inputs") {
      val interpreter = new AsyncInterpreter(
        Reader.fromChunk(zio.blocks.chunk.Chunk(1, 2)).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
      )
      val firstStates  = new scala.collection.mutable.ArrayBuffer[Int]
      val secondStates = new scala.collection.mutable.ArrayBuffer[Int]
      interpreter.addAsyncMapAccum[Int, Int, Int](0, JvmType.Int, JvmType.Int) { (state, value) =>
        firstStates += state; Async.succeed((state + value, state + value))
      }
      interpreter.addAsyncMapAccum[Int, Int, Int](100, JvmType.Int, JvmType.Int) { (state, value) =>
        secondStates += state; Async.succeed((state + value, state + value))
      }
      for {
        first  <- run(interpreter.readInt(-1L))
        second <- run(interpreter.readInt(-1L))
      } yield assertTrue(
        first == 101L,
        second == 104L,
        firstStates.toList == List(0, 1),
        secondStates.toList == List(100, 101)
      )
    },
    test("async MAP_ACCUM follows pending leaves, continuations, failures, and bounded ready chains") {
      val same        = new Completer[(Int, Int)]
      val replacement = new Replacement((2, 2))
      val reentrant   = new ReentrantWake((3, 3))
      val effects     = List[Async[(Int, Int)]](
        same,
        new ReplaceOnce(replacement).asInstanceOf[Async[(Int, Int)]],
        reentrant.asInstanceOf[Async[(Int, Int)]]
      )
      val leaves = ZIO.foreach(effects.zipWithIndex) { case (effect, index) =>
        val i = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
        i.addAsyncMapAccum[Int, Int, Int](0, JvmType.Int, JvmType.Int)((_, _) => effect)
        i.addMap[Int, Int](JvmType.Int, JvmType.Int)(_ + 10)
        val read = i.readInt(-1L)
        if (index == 0) { val future = read.toFuture; same.succeed((1, 1)); ZIO.fromFuture(_ => future) }
        else run(read)
      }
      val thrown  = new RuntimeException("accum-thrown")
      val failed  = new RuntimeException("accum-failed")
      val trusted = StreamError.source("accum-trusted")
      val bad     = List[(Int, Int) => Async[(Int, Int)]](
        (_, _) => throw thrown,
        (_, _) => null,
        (_, _) => Async.succeed(null),
        (_, _) => Async.fail(failed),
        (_, _) => Async.failTrusted(trusted)
      ).map { callback =>
        val i = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
        i.addAsyncMapAccum(0, JvmType.Int, JvmType.Int)(callback)
        fold(i.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      }
      val count   = 263
      val calls   = new AtomicInteger(0)
      val bounded = new AsyncInterpreter(Reader.singleInt(0).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      var n       = 0
      while (n < count) {
        bounded.addAsyncMapAccum[Int, Int, Int](0, JvmType.Int, JvmType.Int) { (s, value) =>
          calls.incrementAndGet(); Async.succeed((s + 1, value + 1))
        }
        n += 1
      }
      for { values <- leaves; value <- run(bounded.readInt(-1L)) } yield assertTrue(
        values == List(11L, 12L, 13L),
        replacement.polls.get() == 1,
        reentrant.polls.get() == 2,
        bad(0) == Failed(thrown, false),
        bad(1).isInstanceOf[Failed],
        bad(2).isInstanceOf[Failed],
        bad(3) == Failed(failed, false),
        bad(4) match {
          case Failed(error: StreamError, false) => (error ne trusted) && !error.isTrusted; case _ => false
        },
        value == count.toLong,
        calls.get() == count
      )
    },
    test("async MAP_ACCUM cancellation covers invocation and poll by ready and replacement results") {
      ZIO
        .foreach(List(false, true).flatMap(replacement => List((false, replacement), (true, replacement)))) {
          case (duringPoll, replacementResult) =>
            val cleanupDone               = new Completer[Unit]
            val replacement               = new CancelablePending(cleanupDone)
            val reader                    = new IntReader(List(1L, 2L), () => Async.succeed(()))
            val states                    = new scala.collection.mutable.ArrayBuffer[Int]
            val downstream                = new AtomicInteger(0)
            val interpreter               = new AsyncInterpreter(reader)
            var operation: Async[Long]    = null
            var cleanup: Async[Unit]      = null
            var polling: CancelDuringPoll = null
            interpreter.addAsyncMapAccum[Int, Int, Int](10, JvmType.Int, JvmType.Int) { (state, value) =>
              states += state
              if (value == 1) {
                if (duringPoll) {
                  val result: Async[Any] = if (replacementResult) replacement else Async.succeed((99, 99))
                  polling = new CancelDuringPoll(
                    new Runnable {
                      def run(): Unit = cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
                    },
                    result,
                    cleanupDone
                  )
                  polling.asInstanceOf[Async[(Int, Int)]]
                } else {
                  cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
                  if (replacementResult) replacement.asInstanceOf[Async[(Int, Int)]] else Async.succeed((99, 99))
                }
              } else Async.succeed((state + value, state + value))
            }
            interpreter.addMap[Int, Int](JvmType.Int, JvmType.Int) { value => downstream.incrementAndGet(); value }
            operation = interpreter.readInt(-1L)
            operation.asInstanceOf[Pollable[Long]].poll(Noop)
            if (replacementResult || duringPoll) cleanupDone.succeed(())
            cleanup.block
            if (duringPoll) polling.captured.run()
            val downstreamBeforeRetry = downstream.get()
            val next                  = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
            assertTrue(
              fold(operation.asInstanceOf[Pollable[Long]].poll(Noop)) == Succeeded(-1L),
              next == Succeeded(12L),
              states.toList == List(10, 10),
              downstreamBeforeRetry == 0,
              downstream.get() == 1,
              reader.reads.get() == 2,
              replacement.polls.get() == 0,
              replacement.cancels.get() == (if (replacementResult) 1 else 0)
            )
        }
        .map(_.reduce(_ && _))
    },
    test("MAP_ACCUM cancellation between candidate conversion and commit retries old state") {
      val reader                 = new IntReader(List(1L, 2L), () => Async.succeed(()))
      val interpreter            = new AsyncInterpreter(reader)
      val states                 = new scala.collection.mutable.ArrayBuffer[Int]
      var operation: Async[Long] = null
      var cleanup: Async[Unit]   = null
      val hostile                = new java.lang.Number {
        def intValue(): Int       = { cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]]); 99 }
        def longValue(): Long     = intValue().toLong
        def floatValue(): Float   = intValue().toFloat
        def doubleValue(): Double = intValue().toDouble
      }
      interpreter.addAsyncMapAccum[Int, Int, Any](10, JvmType.Int, JvmType.Int) { (state, value) =>
        states += state
        if (value == 1) Async.succeed((99, hostile)) else Async.succeed((state + value, state + value))
      }
      operation = interpreter.readInt(-1L)
      operation.asInstanceOf[Pollable[Long]].poll(Noop)
      cleanup.block
      val retry = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      assertTrue(retry == Succeeded(12L), states.toList == List(10, 10), reader.reads.get() == 2)
    },
    test("MAP_ACCUM conversion failures are copied untrusted, sticky, and do not commit state") {
      val trusted = StreamError.source("accum-conversion")
      val hostile = new java.lang.Number {
        def intValue(): Int       = throw trusted
        def longValue(): Long     = throw trusted
        def floatValue(): Float   = throw trusted
        def doubleValue(): Double = throw trusted
      }
      val reader      = new IntReader(List(1L, 1L), () => Async.succeed(()))
      val states      = new scala.collection.mutable.ArrayBuffer[Int]
      val downstream  = new AtomicInteger(0)
      val interpreter = new AsyncInterpreter(reader)
      interpreter.addAsyncMapAccum[Int, Int, Any](10, JvmType.Int, JvmType.Int) { (state, _) =>
        states += state; Async.succeed((99, hostile))
      }
      interpreter.addMap[Int, Int](JvmType.Int, JvmType.Int) { value => downstream.incrementAndGet(); value }
      val first  = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      val replay = fold(interpreter.readInt(-2L).asInstanceOf[Pollable[Long]].poll(Noop))
      assertTrue(
        first match {
          case Failed(error: StreamError, false) => (error ne trusted) && !error.isTrusted
          case _                                 => false
        },
        replay == first,
        states.toList == List(10),
        reader.reads.get() == 1,
        downstream.get() == 0
      )
    },
    test("scan init reentrant cancellation settles without null and retries; throwing conversion fails") {
      var operation: Async[Long] = null
      var cleanup: Async[Unit]   = null
      var conversions            = 0
      val reentrant              = new java.lang.Number {
        def intValue(): Int = {
          conversions += 1;
          if (conversions == 1) cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]]); 7
        }
        def longValue(): Long     = intValue().toLong
        def floatValue(): Float   = intValue().toFloat
        def doubleValue(): Double = intValue().toDouble
      }
      val reader = new IntReader(List(1L), () => Async.succeed(()))
      val scan   = AsyncInterpreter.fromReaderWithAsyncScanForTest[java.lang.Number, Int](
        reader,
        reentrant,
        JvmType.Int,
        JvmType.Int
      )((state, _) => Async.succeed(state))
      operation = scan.readInt(-1L)
      val cancelledResult = operation.asInstanceOf[Pollable[Long]].poll(Noop)
      cleanup.block
      val retried  = fold(scan.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      val boom     = new RuntimeException("scan-init-conversion")
      val throwing = new java.lang.Number {
        def intValue(): Int       = throw boom
        def longValue(): Long     = throw boom
        def floatValue(): Float   = throw boom
        def doubleValue(): Double = throw boom
      }
      val failed = AsyncInterpreter.fromReaderWithAsyncScanForTest[java.lang.Number, Int](
        new IntReader(Nil, () => Async.succeed(())),
        throwing,
        JvmType.Int,
        JvmType.Int
      )((state, _) => Async.succeed(state))
      assertTrue(
        cancelledResult.asInstanceOf[AnyRef] ne null,
        fold(operation.asInstanceOf[Pollable[Long]].poll(Noop)) == Succeeded(-1L),
        retried == Succeeded(7L),
        conversions == 2,
        reader.reads.get() == 0,
        fold(failed.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop)) == Failed(boom, false)
      )
    },
    test("pending scan callback cancellation retries source with old state") {
      val pending = new CancelablePending(Async.succeed(()))
      val states  = new scala.collection.mutable.ArrayBuffer[Int]
      val reader  = new IntReader(List(1L, 2L), () => Async.succeed(()))
      val scan    = AsyncInterpreter.fromReaderWithAsyncScanForTest[Int, Int](reader, 10, JvmType.Int, JvmType.Int) {
        (state, value) =>
          states += state; if (value == 1) pending.asInstanceOf[Async[Int]] else Async.succeed(state + value)
      }
      val initial   = fold(scan.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      val operation = scan.readInt(-1L)
      operation.asInstanceOf[Pollable[Long]].poll(Noop)
      Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]]).block
      val retry = fold(scan.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      assertTrue(
        initial == Succeeded(10L),
        retry == Succeeded(12L),
        states.toList == List(10, 10),
        pending.cancels.get() == 1
      )
    },
    test("white-box async TAKE_WHILE executes every lane, Boolean adaptation, and false is sticky EOF") {
      val types                                      = Array(JvmType.Int, JvmType.Long, JvmType.Float, JvmType.Double, JvmType.AnyRef)
      def source(lane: Int): Reader.AsyncReader[Any] = lane match {
        case 0 => Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 1 => Reader.singleLong(2L).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 2 => Reader.singleFloat(3f).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 3 => Reader.singleDouble(4d).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case _ => Reader.single[AnyRef]("five").toAsync.asInstanceOf[Reader.AsyncReader[Any]]
      }
      def pull(i: AsyncInterpreter, lane: Int): Async[Any] = lane match {
        case 0 => i.readInt(-1L).map(_.asInstanceOf[Any]); case 1   => i.readLong(-1L).map(_.asInstanceOf[Any])
        case 2 => i.readFloat(-1d).map(_.asInstanceOf[Any]); case 3 => i.readDouble(-1d).map(_.asInstanceOf[Any])
        case _ => i.read[Any]("eof")
      }
      ZIO
        .foreach(List.range(0, 5)) { lane =>
          val accepted = new AsyncInterpreter(source(lane))
          accepted.addAsyncTakeWhile[Any](types(lane))(_ => Async.succeed(true))
          val calls   = new AtomicInteger(0)
          val reader  = new IntReader(List(1L, 2L), () => Async.succeed(()))
          val stopped = if (lane == 0) new AsyncInterpreter(reader) else new AsyncInterpreter(source(lane))
          stopped.addAsyncTakeWhile[Any](types(lane)) { _ => calls.incrementAndGet(); Async.succeed(false) }
          for {
            value <- run(pull(accepted, lane))
            end1  <- run(pull(stopped, lane))
            reads <- ZIO.succeed(if (lane == 0) reader.reads.get() else 1)
            end2  <- run(pull(stopped, lane))
          } yield assertTrue(
            value == List[Any](1L, 2L, 3d, 4d, "five")(lane),
            end1 == List[Any](-1L, -1L, -1d, -1d, "eof")(lane),
            end2 == end1,
            calls.get() == 1,
            reads == 1,
            (lane != 0 || reader.reads.get() == 1)
          )
        }
        .map(_.reduce(_ && _))
        .zipWith {
          val boolean = new AsyncInterpreter(Reader.singleBoolean(true).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
          boolean.addAsyncTakeWhile[Boolean](JvmType.Boolean)(value => Async.succeed(value))
          run(boolean.readBoolean(-1)).map(value => assertTrue(value == 1))
        }(_ && _)
    },
    test("suspended incoming async TAKE_WHILE preserves MAP FILTER PUSH continuation") {
      val pending     = new Completer[Boolean]
      val order       = new StringBuilder
      val interpreter = new AsyncInterpreter(Reader.singleInt(2).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      interpreter.addMap[Int, Long](JvmType.Int, JvmType.Long) { value => order.append('a'); value + 1L }
      interpreter.addAsyncTakeWhile[Long](JvmType.Long) { value =>
        order.append('b'); Predef.assert(value == 3L); pending
      }
      interpreter.addMap[Long, Int](JvmType.Long, JvmType.Int) { value => order.append('c'); value.toInt + 1 }
      interpreter.addFilter[Int](JvmType.Int) { value => order.append('d'); value == 4 }
      interpreter.addPush[Int](JvmType.Int, JvmType.Int) { value => order.append('e'); Stream(value + 10) }
      val operation = interpreter.readInt(-1L)
      val suspended = fold(operation.asInstanceOf[Pollable[Long]].poll(Noop)).isInstanceOf[Suspended[_]]
      val before    = order.toString
      pending.succeed(true)
      run(operation).map(value => assertTrue(suspended, before == "ab", value == 14L, order.toString == "abcde"))
    },
    test("suspended outgoing async TAKE_WHILE preserves surrounding MAP FILTER PUSH order") {
      val pending = new Completer[Boolean]
      val order   = new StringBuilder
      val stream  = Stream(1).flatMap { value => order.append('p'); Stream(value + 1) }.map { value =>
        order.append('a'); value.toLong + 1
      }
      val interpreter = AsyncInterpreter.fromStreamWithAsyncTakeWhileForTest(stream, JvmType.Long) { value =>
        order.append('b'); Predef.assert(value == 3L); pending
      }
      interpreter.addMap[Long, Int](JvmType.Long, JvmType.Int) { value => order.append('c'); value.toInt + 1 }
      interpreter.addFilter[Int](JvmType.Int) { value => order.append('d'); value == 4 }
      interpreter.addPush[Int](JvmType.Int, JvmType.Int) { value => order.append('e'); Stream(value + 10) }
      val operation = interpreter.readInt(-1L)
      val suspended = fold(operation.asInstanceOf[Pollable[Long]].poll(Noop))
      val before    = order.toString
      pending.succeed(true)
      run(operation).map(value =>
        assertTrue(
          suspended.isInstanceOf[Suspended[_]],
          before == "pab",
          value == 14L,
          order.toString == "pabcde"
        )
      )
    },
    test("async TAKE_WHILE throw null and failures are sticky, launder trusted provenance, and replay identity") {
      val thrown    = new RuntimeException("take-while")
      val ordinary  = new RuntimeException("take-while-failed")
      val trusted   = StreamError.source("take-while-trusted")
      val callbacks = List[Int => Async[Boolean]](
        _ => throw thrown,
        _ => null,
        _ => Async.fail(ordinary),
        _ => Async.fail(trusted),
        _ => Async.failTrusted(trusted)
      )
      val failures = callbacks.map { callback =>
        val i = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
        i.addAsyncTakeWhile(JvmType.Int)(callback)
        val first = fold(i.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
        (first, fold(i.readInt(-2L).asInstanceOf[Pollable[Long]].poll(Noop)))
      }
      assertTrue(
        failures(0)._1 == Failed(thrown, false),
        failures(1)._1 match { case Failed(_: NullPointerException, false) => true; case _ => false },
        failures(2)._1 == Failed(ordinary, false),
        failures.drop(3).forall {
          case (Failed(error: StreamError, false), _) =>
            (error ne trusted) && error.value == trusted.value && !error.isTrusted
          case _ => false
        },
        failures.forall { case (first, replay) => replay == first },
        failures(2)._1.asInstanceOf[Failed].cause.asInstanceOf[AnyRef] eq ordinary
      )
    },
    test("async TAKE_WHILE drives same replacement and reentrant leaves") {
      val same        = new CountingCompleter
      val replacement = new Replacement(false)
      val replaceOnce = new ReplaceOnce(replacement)
      val reentrant   = new ReentrantWake(false)
      val leaves      = List[Async[Boolean]](
        same.asInstanceOf[Async[Boolean]],
        replaceOnce.asInstanceOf[Async[Boolean]],
        reentrant.asInstanceOf[Async[Boolean]]
      )
      val operations = leaves.map { leaf =>
        val i = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
        i.addAsyncTakeWhile[Int](JvmType.Int)(_ => leaf)
        i.readInt(-1L)
      }
      val sameInitial = fold(operations.head.asInstanceOf[Pollable[Long]].poll(Noop))
      same.succeed(false)
      for {
        a <- run(operations.head)
        b <- run(operations(1))
        c <- run(operations(2))
      } yield assertTrue(
        sameInitial.isInstanceOf[Suspended[_]],
        a == -1L,
        b == -1L,
        c == -1L,
        same.polls.get() == 1,
        replaceOnce.polls.get() == 1,
        replacement.polls.get() == 1,
        reentrant.polls.get() == 2
      )
    },
    test("async TAKE_WHILE cancellation during invocation or poll discards ready and replacement false") {
      ZIO
        .foreach(List(false, true)) { duringPoll =>
          ZIO
            .foreach(List(false, true)) { replacementResult =>
              val reader                 = new IntReader(List(1L, 2L), () => Async.succeed(()))
              val interpreter            = new AsyncInterpreter(reader)
              var operation: Async[Long] = null
              var cleanup: Async[Unit]   = null
              var leaf: CancelDuringPoll = null
              val calls                  = new AtomicInteger(0)
              interpreter.addAsyncTakeWhile[Int](JvmType.Int) { _ =>
                if (calls.getAndIncrement() == 0) {
                  val result: Async[Any] = if (replacementResult) new Replacement(false) else Async.succeed(false)
                  if (duringPoll) {
                    leaf = new CancelDuringPoll(
                      new Runnable {
                        def run(): Unit = cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
                      },
                      result,
                      Async.succeed(())
                    )
                    leaf.asInstanceOf[Async[Boolean]]
                  } else {
                    cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
                    result.asInstanceOf[Async[Boolean]]
                  }
                } else Async.succeed(true)
              }
              operation = interpreter.readInt(-9L)
              val first = operation.asInstanceOf[Pollable[Long]].poll(Noop)
              cleanup.block
              val stale     = if (leaf eq null) () else leaf.captured.run()
              val cancelled = fold(operation.asInstanceOf[Pollable[Long]].poll(Noop))
              run(interpreter.readInt(-1L)).map(next =>
                assertTrue(
                  first.asInstanceOf[AnyRef] ne null,
                  cancelled == Succeeded(-9L),
                  next == 2L,
                  reader.reads.get() == 2,
                  calls.get() == 2,
                  (if (duringPoll) leaf.cancels.get() == 1 else leaf eq null)
                )
              )
            }
            .map(_.reduce(_ && _))
        }
        .map(_.reduce(_ && _))
    },
    test("async TAKE_WHILE cancellation cleanup launders trusted failures") {
      val trusted     = StreamError.source("take-while-cleanup")
      val pending     = new CancelablePending(Async.failTrusted(trusted))
      val interpreter = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      interpreter.addAsyncTakeWhile[Int](JvmType.Int)(_ => pending.asInstanceOf[Async[Boolean]])
      val operation = interpreter.readInt(-1L)
      operation.asInstanceOf[Pollable[Long]].poll(Noop)
      val failure = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]]).either.block match {
        case Left(cause) => cause
        case Right(_)    => throw new AssertionError("expected cancellation cleanup failure")
      }
      assertTrue(failure match {
        case error: StreamError => (error ne trusted) && error.value == trusted.value && !error.isTrusted
        case _                  => false
      })
    },
    test("263 ready async TAKE_WHILE true operations share the bounded ready budget") {
      val calls       = new AtomicInteger(0)
      val interpreter = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      var i           = 0
      while (i < 263) {
        interpreter.addAsyncTakeWhile[Int](JvmType.Int) { _ => calls.incrementAndGet(); Async.succeed(true) }
        i += 1
      }
      val operation = interpreter.readInt(-1L)
      val initial   = fold(operation.asInstanceOf[Pollable[Long]].poll(Noop))
      run(operation).map(value => assertTrue(initial.isInstanceOf[Suspended[_]], value == 1L, calls.get() == 263))
    },
    test("async error maps transform trusted failures in graph order and replay the mapped failure") {
      lazy val reader: TestReader = new TestReader(() => reader.typed("source"))
      val interpreter             = new AsyncInterpreter(reader)
      val calls                   = new AtomicInteger(0)
      interpreter.addAsyncErrorMap[String, String] { value => calls.incrementAndGet(); Async.succeed(value + "-inner") }
      interpreter.addAsyncErrorMap[String, String] { value => calls.incrementAndGet(); Async.succeed(value + "-outer") }
      val first  = fold(interpreter.read[Any]("eof").asInstanceOf[Pollable[Any]].poll(Noop))
      val replay = fold(interpreter.read[Any]("other").asInstanceOf[Pollable[Any]].poll(Noop))
      assertTrue(
        first match {
          case Failed(error: StreamError, true) => error.value == "source-inner-outer"
          case _                                => false
        },
        replay == first,
        calls.get() == 2,
        reader.reads.get() == 1
      )
    },
    test("async error maps suspend, poll replacements, and launder callback failures") {
      val replacement             = new Replacement("mapped")
      val first                   = new ReplaceOnce(replacement)
      lazy val reader: TestReader = new TestReader(() => reader.typed("source"))
      val interpreter             = new AsyncInterpreter(reader)
      interpreter.addAsyncErrorMap[String, String](_ => first.asInstanceOf[Async[String]])
      val mapped = run(interpreter.read[Any]("eof").either)

      val trusted                 = StreamError.source("callback")
      lazy val broken: TestReader = new TestReader(() => broken.typed("source"))
      val brokenInterpreter       = new AsyncInterpreter(broken)
      brokenInterpreter.addAsyncErrorMap[String, String](_ => Async.failTrusted(trusted))
      mapped.map { result =>
        val failed = fold(brokenInterpreter.read[Any]("eof").asInstanceOf[Pollable[Any]].poll(Noop))
        assertTrue(
          result match {
            case Left(error: StreamError) => error.value == "mapped" && error.isTrusted
            case _                        => false
          },
          first.polls.get() == 1,
          replacement.polls.get() == 1,
          failed match {
            case Failed(error: StreamError, false) =>
              (error ne trusted) && error.value == "callback" && !error.isTrusted
            case _ => false
          }
        )
      }
    },
    test("async error maps bypass defects and cleanup-bearing typed failures") {
      val defect       = new RuntimeException("defect")
      val defectCalls  = new AtomicInteger(0)
      val defectReader = new TestReader(() => Async.fail(defect))
      val defects      = new AsyncInterpreter(defectReader)
      defects.addAsyncErrorMap[Any, Any] { value => defectCalls.incrementAndGet(); Async.succeed(value) }

      val cleanup = new RuntimeException("cleanup")
      val typed   = StreamError.source("typed")
      StreamError.attachCleanup(typed, cleanup)
      val cleanupCalls  = new AtomicInteger(0)
      val cleanupReader = new TestReader(() => Async.failTrusted(typed))
      val cleanups      = new AsyncInterpreter(cleanupReader)
      cleanups.addAsyncErrorMap[Any, Any] { value => cleanupCalls.incrementAndGet(); Async.succeed(value) }

      val defectResult  = fold(defects.read[Any]("eof").asInstanceOf[Pollable[Any]].poll(Noop))
      val cleanupResult = fold(cleanups.read[Any]("eof").asInstanceOf[Pollable[Any]].poll(Noop))
      assertTrue(
        defectResult == Failed(defect, trusted = false),
        cleanupResult == Failed(typed, trusted = true),
        defectCalls.get() == 0,
        cleanupCalls.get() == 0
      )
    },
    test("deep ready async error-map chains yield cooperatively and remain stack safe") {
      lazy val reader: TestReader = new TestReader(() => reader.typed(0))
      val interpreter             = new AsyncInterpreter(reader)
      var i                       = 0
      while (i < 2000) {
        interpreter.addAsyncErrorMap[Int, Int](value => Async.succeed(value + 1))
        i += 1
      }
      run(interpreter.read[Any]("eof").either).map(result =>
        assertTrue(result match {
          case Left(error: StreamError) => error.value == 2000 && error.isTrusted
          case _                        => false
        })
      )
    },
    test("async error maps resume genuinely pending and reentrant leaves exactly once") {
      val pending                        = new Completer[String]
      val pendingCalls                   = new AtomicInteger(0)
      lazy val pendingReader: TestReader = new TestReader(() => pendingReader.typed("source"))
      val pendingInterpreter             = new AsyncInterpreter(pendingReader)
      pendingInterpreter.addAsyncErrorMap[String, String] { value => pendingCalls.incrementAndGet(); pending }
      val future = pendingInterpreter.read[Any]("eof").either.toFuture
      pending.succeed("pending-mapped")

      val reentrant                        = new ReentrantWake("reentrant-mapped")
      val reentrantCalls                   = new AtomicInteger(0)
      lazy val reentrantReader: TestReader = new TestReader(() => reentrantReader.typed("source"))
      val reentrantInterpreter             = new AsyncInterpreter(reentrantReader)
      reentrantInterpreter.addAsyncErrorMap[String, String] { _ =>
        reentrantCalls.incrementAndGet(); reentrant.asInstanceOf[Async[String]]
      }
      for {
        pendingResult   <- ZIO.fromFuture(_ => future)
        reentrantResult <- run(reentrantInterpreter.read[Any]("eof").either)
      } yield assertTrue(
        pendingResult.left.exists(_.asInstanceOf[StreamError].value == "pending-mapped"),
        reentrantResult.left.exists(_.asInstanceOf[StreamError].value == "reentrant-mapped"),
        pendingCalls.get() == 1,
        reentrantCalls.get() == 1,
        reentrant.polls.get() == 2
      )
    },
    test("async error-map cancellation during invocation and polling rejects late results and remains usable") {
      ZIO
        .foreach(List(false, true)) { duringPoll =>
          val calls                   = new AtomicInteger(0)
          lazy val reader: TestReader = new TestReader(() => reader.typed("source"))
          val interpreter             = new AsyncInterpreter(reader)
          var operation: Async[Any]   = null
          var cleanup: Async[Unit]    = null
          var leaf: CancelDuringPoll  = null
          interpreter.addAsyncErrorMap[String, String] { value =>
            if (calls.getAndIncrement() == 0) {
              if (duringPoll) {
                leaf = new CancelDuringPoll(
                  new Runnable {
                    def run(): Unit = cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Any]])
                  },
                  new Replacement("late"),
                  Async.succeed(())
                )
                leaf.asInstanceOf[Async[String]]
              } else {
                cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Any]])
                new Replacement("late").asInstanceOf[Async[String]]
              }
            } else Async.succeed(value + "-next")
          }
          operation = interpreter.read[Any]("cancelled")
          operation.asInstanceOf[Pollable[Any]].poll(Noop)
          cleanup.block
          val cancelled = fold(operation.asInstanceOf[Pollable[Any]].poll(Noop))
          run(interpreter.read[Any]("eof").either).map(next =>
            assertTrue(
              cancelled == Succeeded("cancelled"),
              next.left.exists(_.asInstanceOf[StreamError].value == "source-next"),
              calls.get() == 2,
              (if (duringPoll) leaf.cancels.get() == 1 else leaf eq null)
            )
          )
        }
        .map(_.reduce(_ && _))
    },
    test("async error-map throw and null are sticky while reader ownership closes only at terminal close") {
      val closes                    = new AtomicInteger(0)
      val thrown                    = new RuntimeException("mapper")
      lazy val throwing: TestReader = new TestReader(() => throwing.typed("source")) {
        override def close(): Async[Unit] = { closes.incrementAndGet(); Async.succeed(()) }
      }
      val throwingInterpreter = new AsyncInterpreter(throwing)
      throwingInterpreter.addAsyncErrorMap[String, String](_ => throw thrown)
      val first  = fold(throwingInterpreter.read[Any]("eof").asInstanceOf[Pollable[Any]].poll(Noop))
      val replay = fold(throwingInterpreter.read[Any]("eof-2").asInstanceOf[Pollable[Any]].poll(Noop))

      lazy val nullReader: TestReader = new TestReader(() => nullReader.typed("source"))
      val nullInterpreter             = new AsyncInterpreter(nullReader)
      nullInterpreter.addAsyncErrorMap[String, String](_ => null)
      val nullFailure = fold(nullInterpreter.read[Any]("eof").asInstanceOf[Pollable[Any]].poll(Noop))
      for {
        close1 <- run(throwingInterpreter.closeForTest()).either
        close2 <- run(throwingInterpreter.closeForTest()).either
      } yield assertTrue(
        first == Failed(thrown, trusted = false),
        replay == first,
        nullFailure match {
          case Failed(cause: NullPointerException, false) =>
            cause.getMessage == "async error-map callback returned null"
          case _ => false
        },
        closes.get() == 1,
        close1 == Left(thrown),
        close2 == Left(thrown)
      )
    },
    test("synchronous FILTER executes true and false on every physical lane") {
      val types                                      = Array(JvmType.Int, JvmType.Long, JvmType.Float, JvmType.Double, JvmType.AnyRef)
      def reader(lane: Int): Reader.AsyncReader[Any] = lane match {
        case 0 => Reader.fromChunk(zio.blocks.chunk.Chunk(1, 2)).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 1 => Reader.fromChunk(zio.blocks.chunk.Chunk(1L, 2L)).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 2 => Reader.fromChunk(zio.blocks.chunk.Chunk(1.0f, 2.0f)).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 3 => Reader.fromChunk(zio.blocks.chunk.Chunk(1.0, 2.0)).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case _ => Reader.fromIterable(List("one", "two")).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
      }
      def pull(interpreter: AsyncInterpreter, lane: Int): Async[Any] = lane match {
        case 0 => interpreter.readInt(-1L).map(_.asInstanceOf[Any])
        case 1 => interpreter.readLong(-1L).map(_.asInstanceOf[Any])
        case 2 => interpreter.readFloat(-1.0).map(_.asInstanceOf[Any])
        case 3 => interpreter.readDouble(-1.0).map(_.asInstanceOf[Any])
        case _ => interpreter.read[Any]("eof")
      }
      ZIO
        .foreach(List.range(0, 5)) { lane =>
          val calls       = new AtomicInteger(0)
          val interpreter = new AsyncInterpreter(reader(lane))
          interpreter.addFilter[Any](types(lane)) { value =>
            calls.incrementAndGet()
            value match {
              case n: java.lang.Number => n.intValue() == 2
              case text                => text == "two"
            }
          }
          for {
            value <- run(pull(interpreter, lane))
            eof   <- run(pull(interpreter, lane))
          } yield assertTrue(
            value == (if (lane == 4) "two" else 2),
            calls.get() == 2,
            eof == (if (lane == 4) "eof" else -1)
          )
        }
        .map(_.reduce(_ && _))
    },
    test("Boolean FILTER adaptation and map-filter-map order are preserved") {
      val order       = new StringBuilder
      val source      = Reader.fromChunk(zio.blocks.chunk.Chunk(0, 1)).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
      val interpreter = new AsyncInterpreter(source)
      interpreter.addMap[Int, Boolean](JvmType.Int, JvmType.Boolean) { value => order.append('a'); value != 0 }
      interpreter.addFilter[Boolean](JvmType.Boolean) { value => order.append('b'); value }
      interpreter.addMap[Boolean, Int](JvmType.Boolean, JvmType.Int) { value => order.append('c'); if (value) 2 else 0 }
      run(interpreter.readInt(-1L)).map(value => assertTrue(value == 2L, order.toString == "ababc"))
    },
    test("pending source between rejected and accepted FILTER values resumes exactly once") {
      val completion = new Completer[Any]
      var index      = 0
      val reader     = new TestReader(() => { index += 1; if (index == 1) Async.succeed(1) else completion }) {
        override def jvmType = JvmType.Int
      }
      val interpreter = new AsyncInterpreter(reader)
      interpreter.addFilter[Int](JvmType.Int)(_ == 2)
      val future = interpreter.readInt(-1L).toFuture
      completion.succeed(2)
      ZIO.fromFuture(_ => future).map(value => assertTrue(value == 2L, reader.reads.get() == 2))
    },
    test("thrown FILTER callback is sticky and identity-replayed") {
      val cause       = new RuntimeException("filter")
      val interpreter = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      interpreter.addFilter[Int](JvmType.Int)(_ => throw cause)
      val first  = fold(interpreter.readInt(-1L).asInstanceOf[Pollable[Long]].poll(Noop))
      val replay = fold(interpreter.readInt(-2L).asInstanceOf[Pollable[Long]].poll(Noop))
      assertTrue(first == Failed(cause, trusted = false), replay == first)
    },
    test("cancellation inside FILTER prevents later callbacks and another source read") {
      val reader                    = new TestReader(() => Async.succeed(1)) { override def jvmType = JvmType.Int }
      val interpreter               = new AsyncInterpreter(reader)
      val laterCalls                = new AtomicInteger(0)
      var operation: Async[Long]    = null
      var cancellation: Async[Unit] = null
      interpreter.addFilter[Int](JvmType.Int) { _ =>
        cancellation = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
        false
      }
      interpreter.addFilter[Int](JvmType.Int) { _ => laterCalls.incrementAndGet(); true }
      operation = interpreter.readInt(-1L)
      operation.asInstanceOf[Pollable[Long]].poll(Noop)
      assertTrue(
        cancellation.block == (),
        reader.reads.get() == 1,
        laterCalls.get() == 0,
        fold(operation.asInstanceOf[Pollable[Long]].poll(Noop)) == Succeeded(-1L)
      )
    },
    test("cancellation at the FILTER work boundary completes without yielding") {
      val reader                    = new TestReader(() => Async.succeed(1)) { override def jvmType = JvmType.Int }
      val interpreter               = new AsyncInterpreter(reader)
      val calls                     = new AtomicInteger(0)
      var operation: Async[Long]    = null
      var cancellation: Async[Unit] = null
      interpreter.addFilter[Int](JvmType.Int) { _ =>
        if (calls.incrementAndGet() == 129)
          cancellation = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]])
        false
      }
      operation = interpreter.readInt(-1L)
      operation.asInstanceOf[Pollable[Long]].poll(Noop)
      assertTrue(
        cancellation.block == (),
        calls.get() == 129,
        reader.reads.get() == 129,
        fold(operation.asInstanceOf[Pollable[Long]].poll(Noop)) == Succeeded(-1L)
      )
    },
    test("always-false ready FILTER re-drive is stack safe and cooperatively scheduled") {
      val rejected    = 10000
      val reader      = Reader.fromChunk(zio.blocks.chunk.Chunk.fromIterable(List.range(0, rejected) :+ rejected)).toAsync
      val calls       = new AtomicInteger(0)
      val interpreter = new AsyncInterpreter(reader.asInstanceOf[Reader.AsyncReader[Any]])
      interpreter.addFilter[Int](JvmType.Int) { value => calls.incrementAndGet(); value == rejected }
      run(interpreter.readInt(-1L)).map(value => assertTrue(value == rejected.toLong, calls.get() == rejected + 1))
    },
    test("trusted and untrusted failures retain identity and provenance") {
      val trustedCause                   = new RuntimeException("trusted")
      val untrustedCause                 = new RuntimeException("untrusted")
      lazy val trustedReader: TestReader = new TestReader(() => trustedReader.trusted(trustedCause))
      val trustedInterpreter             = new AsyncInterpreter(trustedReader)
      val untrustedReader                = new TestReader(() => Async.fail(untrustedCause))
      val trusted                        = trustedInterpreter.read[Any](new AnyRef).asInstanceOf[Pollable[Any]].poll(Noop)
      val trustedReplay                  = trustedInterpreter.read[Any](new AnyRef).asInstanceOf[Pollable[Any]].poll(Noop)
      val untrusted                      =
        new AsyncInterpreter(untrustedReader).read[Any](new AnyRef).asInstanceOf[Pollable[Any]].poll(Noop)
      assertTrue(
        fold(trusted) match {
          case Failed(error: StreamError, true) => error.value.asInstanceOf[AnyRef] eq trustedCause
          case _                                => false
        },
        fold(trustedReplay) == fold(trusted),
        fold(untrusted) == Failed(untrustedCause, trusted = false)
      )
    },
    test("pending reads resume through their scheduled waker") {
      val completion  = new Completer[Any]
      val interpreter = new AsyncInterpreter(new TestReader(() => completion))
      val effect      = interpreter.read[Any](new AnyRef)
      for {
        future <- ZIO.succeed(effect.toFuture)
        _      <- ZIO.succeed(completion.succeed("done"))
        value  <- ZIO.fromFuture(_ => future)
      } yield assertTrue(value == "done")
    },
    test("a distinct pending replacement is polled without waiting for a spurious wake") {
      val replacement = new Replacement("done")
      val first       = new ReplaceOnce(replacement)
      val reader      = new TestReader(() => first)
      val result      = new AsyncInterpreter(reader).read[Any](new AnyRef).asInstanceOf[Pollable[Any]].poll(Noop)
      assertTrue(fold(result) == Succeeded("done"), first.polls.get() == 1, replacement.polls.get() == 1)
    },
    test("reentrant duplicate wakes are retained and coalesced") {
      val pending     = new ReentrantWake(42)
      val interpreter = new AsyncInterpreter(new TestReader(() => pending))
      run(interpreter.read[Any](-1)).map(value => assertTrue(value == 42, pending.polls.get() == 2))
    },
    test("a completed generation's stale waker cannot poll a later operation") {
      val first  = new CapturingTerminal(1)
      val second = new CountingCompleter
      var index  = 0
      val reader = new TestReader(() => {
        index += 1
        if (index == 1) first else second
      })
      val interpreter = new AsyncInterpreter(reader)
      for {
        one       <- run(interpreter.read[Any](-1))
        operation <- ZIO.succeed(interpreter.read[Any](-1))
        initial   <- ZIO.succeed(fold(operation.asInstanceOf[Pollable[Any]].poll(Noop)))
        before    <- ZIO.succeed(second.polls.get())
        _         <- ZIO.succeed(first.captured.run())
        _         <- ZIO.succeed(second.succeed(2))
        two       <- run(operation)
        after     <- ZIO.succeed(second.polls.get())
      } yield assertTrue(one == 1, initial.isInstanceOf[Suspended[_]], before == 1, two == 2, after == 1)
    },
    test("only the first of two constructed handles can claim the interpreter") {
      val completion  = new Completer[Any]
      val interpreter = new AsyncInterpreter(new TestReader(() => completion))
      val first       = interpreter.read[Any](-1)
      val second      = interpreter.read[Any](-2)
      val firstPoll   = first.asInstanceOf[Pollable[Any]].poll(Noop)
      val secondPoll  = second.asInstanceOf[Pollable[Any]].poll(Noop)
      completion.succeed(1)
      assertTrue(
        fold(firstPoll).isInstanceOf[Suspended[_]],
        fold(secondPoll).asInstanceOf[Failed].cause.getMessage == AsyncInterpreter.ConcurrentOperationMessage
      )
    },
    test("cancellation before drive returns the sentinel without touching the source") {
      val reader      = new TestReader(() => Async.succeed(1))
      val interpreter = new AsyncInterpreter(reader)
      val operation   = interpreter.read[Any](-1)
      val pollable    = operation.asInstanceOf[Pollable[Any]]
      val cleanup     = Async.cancelWithCleanup(pollable)
      val result      = pollable.poll(Noop)
      val next        = interpreter.read[Any](-2).asInstanceOf[Pollable[Any]].poll(Noop)
      assertTrue(
        cleanup.block == (),
        fold(result) == Succeeded(-1),
        fold(next) == Succeeded(1),
        reader.reads.get() == 1
      )
    },
    test("pending cancellation signals immediately and joins cleanup before the sentinel") {
      val cleanupDone = new Completer[Unit]
      val pending     = new CancelablePending(cleanupDone)
      val operation   = new AsyncInterpreter(new TestReader(() => pending)).read[Any](-1)
      val pollable    = operation.asInstanceOf[Pollable[Any]]
      val first       = pollable.poll(Noop)
      val cleanup     = Async.cancelWithCleanup(pollable)
      val before      = pollable.poll(Noop)
      cleanupDone.succeed(())
      assertTrue(
        fold(first).isInstanceOf[Suspended[_]],
        pending.cancels.get() == 1,
        fold(before).isInstanceOf[Suspended[_]],
        cleanup.block == (),
        fold(pollable.poll(Noop)) == Succeeded(-1)
      )
    },
    test("a null cancellation-cleanup failure fails the pull and becomes sticky") {
      val pending     = new CancelablePending(Async.fail(null))
      val interpreter = new AsyncInterpreter(new TestReader(() => pending))
      val operation   = interpreter.read[Any](-1)
      val pollable    = operation.asInstanceOf[Pollable[Any]]
      pollable.poll(Noop)
      val cleanup = Async.cancelWithCleanup(pollable).either.block
      val current = fold(pollable.poll(Noop))
      val replay  = fold(interpreter.read[Any](-2).asInstanceOf[Pollable[Any]].poll(Noop))
      assertTrue(cleanup == Left(null), current == Failed(null, trusted = false), replay == current)
    },
    test("a null cleanup failure never replaces a non-null primary") {
      val primary = new RuntimeException("primary")
      val result  = StreamError.attachCleanupReplay(primary, null)
      assertTrue(result eq primary, primary.getSuppressed.isEmpty)
    },
    test("owner cleanup preserves null failures from transition state and reader close") {
      val closeAttempts = new AtomicInteger(0)
      val failingOwner  = new TestReader(() => Async.succeed(1)) {
        override def close(): Async[Unit] = { closeAttempts.incrementAndGet(); Async.fail(null) }
      }
      val nonNull                 = new RuntimeException("non-null cleanup")
      def owner(cause: Throwable) = new TestReader(() => Async.succeed(1)) {
        override def close(): Async[Unit] = Async.fail(cause)
      }
      val direct = new AsyncInterpreter(Reader.singleInt(1).toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      val sticky = new AsyncInterpreter(new TestReader(() => Async.failTrusted(null)))
      val failed = fold(sticky.read[Any](-1).asInstanceOf[Pollable[Any]].poll(Noop))
      for {
        directFailure     <- run(direct.closeReadersForTest(failingOwner).either)
        nullThenNonNull   <- run(direct.closeReadersForTest(owner(null), owner(nonNull)).either)
        nonNullThenNull   <- run(direct.closeReadersForTest(owner(nonNull), owner(null)).either)
        combinedNullFirst <- run(direct.combineCleanupForTest(Async.fail(null), Async.fail(nonNull)).either)
        combinedNullLast  <- run(direct.combineCleanupForTest(Async.fail(nonNull), Async.fail(null)).either)
        firstClose        <- run(sticky.closeForTest().either)
        replayClose       <- run(sticky.closeForTest().either)
      } yield assertTrue(
        failed == Failed(null, trusted = true),
        directFailure == Left(null),
        nullThenNonNull == Left(null),
        nonNullThenNull == Left(nonNull),
        combinedNullFirst == Left(null),
        combinedNullLast == Left(nonNull),
        closeAttempts.get() == 1,
        firstClose == Left(null),
        replayClose == Left(null)
      )
    },
    test("Reader lifecycle queries and controls delegate and reset sticky EOF") {
      val reader      = new LifecycleReader
      val interpreter = new AsyncInterpreter(reader)
      for {
        open       <- run(interpreter.isClosed)
        ready      <- run(interpreter.readable())
        limited    <- run(interpreter.setLimit(3L))
        configured <- run(interpreter.setSkip(1L))
        _          <- run(interpreter.skip(1L))
        value      <- run(interpreter.read[Any]("eof"))
        eof        <- run(interpreter.read[Any]("eof"))
        sticky     <- run(interpreter.read[Any]("again"))
        _          <- run(interpreter.reset())
        resetValue <- run(interpreter.read[Any]("eof"))
        _          <- run(interpreter.closeForTest())
        closed     <- run(interpreter.isClosed)
        unreadable <- run(interpreter.readable())
      } yield assertTrue(
        !open,
        ready,
        limited,
        configured,
        value == 2,
        eof == 3,
        sticky == "again",
        resetValue == 1,
        closed,
        !unreadable,
        reader.queries.get() == 2,
        reader.controls.get() == 3
      )
    },
    test("lifecycle admission uses drive-time state and logical skip retains exclusive ownership") {
      val reader      = new LifecycleReader
      val interpreter = new AsyncInterpreter(reader)
      val constructed = interpreter.isClosed
      val filtered    = AsyncInterpreter.fromStream(Stream(1, 2, 3, 4).filter(_ % 2 == 0))
      for {
        _          <- run(interpreter.closeForTest())
        closed     <- run(constructed)
        skipClosed <- run(interpreter.skip(Long.MaxValue).either)
        _          <- run(filtered.skip(1L))
        value      <- run(filtered.readInt(-1L))
        end        <- run(filtered.readInt(-1L))
      } yield assertTrue(
        closed,
        skipClosed.left.exists(_.isInstanceOf[java.io.IOException]),
        value == 4L,
        end == -1L
      )
    },
    test("huge ready skip yields to scheduled work and cancellation stops further pulls") {
      val started     = new Completer[Unit]
      val reader      = new TestReader(() => { started.succeed(()); Async.succeed(1) })
      val interpreter = new AsyncInterpreter(reader)
      val marker      = new Completer[Unit]
      var yielded     = false
      val skipping    = interpreter.skip(1000000L).start
      for {
        _ <- run(started)
        _  = Async.schedule(
              new Runnable {
                def run(): Unit = { yielded = true; marker.succeed(()) }
              },
              forceMacrotask = true
            )
        _       <- run(marker)
        observed = reader.reads.get()
        _        = skipping.cancel()
        barrier  = new Completer[Unit]
        _        = Async.schedule(new Runnable { def run(): Unit = barrier.succeed(()) }, forceMacrotask = true)
        _       <- run(barrier)
        stopped  = reader.reads.get()
      } yield assertTrue(yielded, observed > 0, observed < 1000000, stopped < 1000000)
    },
    test("pending lifecycle cancellation joins child cleanup and releases operation ownership") {
      val pollEntered = new Completer[Unit]
      val pending     = new CancelablePending(Async.succeed(()), pollEntered)
      val reader      = new TestReader(() => Async.succeed(1)) {
        override def setLimit(n: Long): Async[Boolean] = {
          val _ = n
          pending.asInstanceOf[Async[Boolean]]
        }
      }
      val interpreter = new AsyncInterpreter(reader)
      val operation   = interpreter.setLimit(1L)
      val first       = operation.asInstanceOf[Pollable[Boolean]].poll(Noop)
      for {
        _      <- run(pollEntered)
        _      <- run(Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Boolean]]))
        closed <- run(interpreter.isClosed)
      } yield assertTrue(
        first.isInstanceOf[Pollable[_]],
        pending.polls.get() > 0,
        pending.cancels.get() == 1,
        !closed
      )
    },
    test("repeated lifecycle cancellation callers share and replay one child cleanup") {
      val pollEntered   = new Completer[Unit]
      val cancelEntered = new Completer[Unit]
      val cleanupDone   = new Completer[Unit]
      val pending       = new CancelablePending(cleanupDone, pollEntered, cancelEntered)
      val reader        = new TestReader(() => Async.succeed(1)) {
        override def setLimit(n: Long): Async[Boolean] = {
          val _ = n
          pending.asInstanceOf[Async[Boolean]]
        }
      }
      val interpreter = new AsyncInterpreter(reader)
      val operation   = interpreter.setLimit(1L).asInstanceOf[Pollable[Boolean]]
      operation.poll(Noop)
      for {
        _      <- run(pollEntered)
        first   = Async.cancelWithCleanup(operation)
        second  = Async.cancelWithCleanup(operation)
        _      <- run(cancelEntered)
        _       = cleanupDone.succeed(())
        _      <- run(first)
        _      <- run(second)
        closed <- run(interpreter.isClosed)
      } yield assertTrue(first.asInstanceOf[AnyRef] eq second.asInstanceOf[AnyRef], pending.cancels.get() == 1, !closed)
    },
    test("cancellation racing poll owns and joins a distinct replacement") {
      val replacementCleanup    = new Completer[Unit]
      val replacement           = new CancelablePending(replacementCleanup)
      var cleanup: Async[Unit]  = null
      var operation: Async[Any] = null
      val first                 = new CancelWithReplacement(
        () => cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Any]]),
        replacement
      )
      operation = new AsyncInterpreter(new TestReader(() => first)).read[Any](-1)
      val result = operation.asInstanceOf[Pollable[Any]].poll(Noop)
      val before = fold(result).isInstanceOf[Suspended[_]]
      replacementCleanup.succeed(())
      assertTrue(
        before,
        first.cancels.get() == 1,
        replacement.cancels.get() == 1,
        cleanup.block == (),
        fold(operation.asInstanceOf[Pollable[Any]].poll(Noop)) == Succeeded(-1)
      )
    },
    test("cancellation of a pushed read joins a distinct replacement before owner cleanup") {
      val replacementFailure    = new RuntimeException("replacement-cleanup")
      val ownerFailure          = new RuntimeException("pushed-owner-cleanup")
      val replacement           = new CancelablePending(Async.fail(replacementFailure))
      var cleanup: Async[Unit]  = null
      var operation: Async[Any] = null
      val ownerCloses           = new AtomicInteger
      val first                 = new CancelWithReplacement(
        () => cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Any]]),
        replacement
      )
      val owner = new TestReader(() => first) {
        override def close(): Async[Unit] = { ownerCloses.incrementAndGet(); Async.fail(ownerFailure) }
      }
      val interpreter = new AsyncInterpreter(new TestReader(() => Async.succeed(1)))
      interpreter.addPush[Any](JvmType.AnyRef, JvmType.AnyRef)(_ => Stream.fromReader[Nothing, Any](owner))
      operation = interpreter.read[Any]("cancelled")
      val polled = operation.asInstanceOf[Pollable[Any]].poll(Noop)
      val result = cleanup.either.block
      assertTrue(
        fold(polled).isInstanceOf[Suspended[_]],
        result.left.exists(_ eq replacementFailure),
        replacementFailure.getSuppressed.toList == List(ownerFailure),
        first.cancels.get() == 1,
        replacement.cancels.get() == 1,
        ownerCloses.get() == 1
      )
    },
    test("lifecycle admission, failure, repetition, and every logical skip lane are behavioral") {
      val pendingRead    = new CancelablePending(Async.succeed(()))
      val reading        = new AsyncInterpreter(new TestReader(() => pendingRead))
      val activeRead     = reading.read[Any]("eof")
      val _              = activeRead.asInstanceOf[Pollable[Any]].poll(Noop)
      val pendingControl = new CancelablePending(Async.succeed(()))
      val controlling    = new AsyncInterpreter(new TestReader(() => Async.succeed(1)) {
        override def setLimit(n: Long): Async[Boolean] = pendingControl.asInstanceOf[Async[Boolean]]
      })
      val activeControl = controlling.setLimit(1L)
      val _             = activeControl.asInstanceOf[Pollable[Boolean]].poll(Noop)
      val thrown        = new RuntimeException("lifecycle construction")
      val throwing      = new AsyncInterpreter(new TestReader(() => Async.succeed(1)) {
        override def readable(): Async[Boolean] = throw thrown
      })
      val nonMaps      = AsyncInterpreter.fromStream(Stream(1, 2).filter(_ => true))
      val repeatReader = new LifecycleReader
      val repeating    = new AsyncInterpreter(repeatReader)
      val skipCases    = List[(JvmType, Any, Any)](
        (JvmType.Boolean, false, 0),
        (JvmType.Byte, 1.toByte, 1),
        (JvmType.Char, 'a', 'a'.toInt),
        (JvmType.Short, 1.toShort, 1),
        (JvmType.Int, 1, 1L),
        (JvmType.Long, 1L, 1L),
        (JvmType.Float, 1.0f, 1.0d),
        (JvmType.Double, 1.0d, 1.0d),
        (JvmType.AnyRef, "one", "one")
      )
      for {
        readConflict    <- run(reading.isClosed.either)
        controlConflict <- run(controlling.readable().either)
        thrownResult    <- run(throwing.readable().either)
        refused         <- run(nonMaps.setRepeat())
        _               <- run(repeating.skip(3L))
        end             <- run(repeating.read[Any]("eof"))
        repeated        <- run(repeating.setRepeat())
        _               <- run(repeating.reset())
        first           <- run(repeating.read[Any]("eof"))
        skipped         <- ZIO.foreach(skipCases) { case (lane, value, _) =>
                     val interpreter = AsyncInterpreter.fromStream(twoForLane(value, lane))
                     for {
                       _    <- run(interpreter.skip(1L))
                       next <- run(pullLane(interpreter, lane))
                     } yield next
                   }
        _ <- run(Async.cancelWithCleanup(activeRead.asInstanceOf[Pollable[Any]]))
        _ <- run(Async.cancelWithCleanup(activeControl.asInstanceOf[Pollable[Boolean]]))
      } yield assertTrue(
        readConflict.left.exists(_.getMessage == "Cannot perform a reentrant reader operation"),
        controlConflict.left.exists(_.getMessage == "Cannot perform a reentrant reader operation"),
        thrownResult == Left(thrown),
        !refused,
        end == "eof",
        repeated,
        first == 1,
        skipped == skipCases.map(_._3)
      )
    },
    test("white-box guards and all primitive bulk validators reject invalid states and inputs") {
      val interpreter = AsyncInterpreter.fromStream(Stream(1))
      val nullReader  = scala.util.Try(interpreter.installNestedFrameForTest(null, JvmType.Int)).failed.toOption
      val nullType    = scala.util
        .Try(
          interpreter.installNestedFrameForTest(new TestReader(() => Async.succeed(1)), null)
        )
        .failed
        .toOption
      val mismatch = scala.util
        .Try(
          interpreter.installNestedFrameForTest(new TestReader(() => Async.succeed(1)), JvmType.AnyRef)
        )
        .failed
        .toOption
      val pendingInterpreter = new AsyncInterpreter(new TestReader(() => new CancelablePending(Async.succeed(()))))
      val active             = pendingInterpreter.read[Any]("eof")
      val _                  = active.asInstanceOf[Pollable[Long]].poll(Noop)
      val activeInstall      = scala.util
        .Try(
          pendingInterpreter.installNestedFrameForTest(new TestReader(() => Async.succeed(1)), JvmType.AnyRef)
        )
        .failed
        .toOption
      val nullTyped     = scala.util.Try(interpreter.addAsyncCatchAll[Any](null)).failed.toOption
      val nullDefect    = scala.util.Try(interpreter.addAsyncCatchDefect(null)).failed.toOption
      val nullFinalizer = scala.util.Try(interpreter.addAsyncFinalizer(null)).failed.toOption
      val byteReader    = AsyncInterpreter.fromStream(Stream[Byte](1))
      val longReader    = AsyncInterpreter.fromStream(Stream(1L))
      val floatReader   = AsyncInterpreter.fromStream(Stream(1f))
      val doubleReader  = AsyncInterpreter.fromStream(Stream(1d))
      for {
        byteInvalid   <- run(byteReader.readBytes(new Array[Byte](1), 1, 1).either)
        longInvalid   <- run(longReader.readLongs(new Array[Long](1), 1, 1).either)
        floatInvalid  <- run(floatReader.readFloats(new Array[Float](1), 1, 1).either)
        doubleInvalid <- run(doubleReader.readDoubles(new Array[Double](1), 1, 1).either)
        _             <- run(Async.cancelWithCleanup(active.asInstanceOf[Pollable[Any]]))
        _             <- run(interpreter.closeForTest())
        lateFinalizer  = scala.util.Try(interpreter.addAsyncFinalizer(() => Async.succeed(()))).failed.toOption
      } yield assertTrue(
        nullReader.exists(_.isInstanceOf[NullPointerException]),
        nullType.exists(_.isInstanceOf[NullPointerException]),
        mismatch.exists(_.isInstanceOf[IllegalArgumentException]),
        activeInstall.exists(_.isInstanceOf[IllegalStateException]),
        nullTyped.exists(_.isInstanceOf[NullPointerException]),
        nullDefect.exists(_.isInstanceOf[NullPointerException]),
        nullFinalizer.exists(_.isInstanceOf[NullPointerException]),
        byteInvalid.left.exists(_.isInstanceOf[IndexOutOfBoundsException]),
        longInvalid.left.exists(_.isInstanceOf[IndexOutOfBoundsException]),
        floatInvalid.left.exists(_.isInstanceOf[IndexOutOfBoundsException]),
        doubleInvalid.left.exists(_.isInstanceOf[IndexOutOfBoundsException]),
        lateFinalizer.exists(_.isInstanceOf[IllegalStateException])
      )
    },
    test("white-box typed and defect recovery classify provenance and install produced streams") {
      val typedCalls  = new AtomicInteger(0)
      val defectCalls = new AtomicInteger(0)
      val typed       = new AsyncInterpreter(new TestReader(() => Async.failTrusted(StreamError.source("boom"))))
      typed.addAsyncCatchAll[String] { error =>
        typedCalls.incrementAndGet()
        Async.succeed(Stream.succeed((error + "!"): Any))
      }
      val defectCause = new RuntimeException("defect")
      val defect      = new AsyncInterpreter(new TestReader(() => Async.fail(defectCause)))
      defect.addAsyncCatchDefect {
        case cause if cause eq defectCause =>
          defectCalls.incrementAndGet()
          Async.succeed(Some(Stream.succeed("recovered": Any)))
      }
      val bypass = new AsyncInterpreter(new TestReader(() => Async.fail(defectCause)))
      bypass.addAsyncCatchAll[Any] { _ => typedCalls.incrementAndGet(); Async.succeed(Stream.succeed("bad": Any)) }
      for {
        a <- run(typed.read[Any]("eof"))
        b <- run(defect.read[Any]("eof"))
        c <- run(bypass.read[Any]("eof").either)
      } yield assertTrue(
        a == "boom!",
        b == "recovered",
        c == Left(defectCause),
        typedCalls.get() == 1,
        defectCalls.get() == 1
      )
    },
    test("typed recovery converts every numeric widening and reference boxing or unboxing lane") {
      val widenings = List[(JvmType, JvmType, Any, Any)](
        (JvmType.Byte, JvmType.Short, 7.toByte, 7),
        (JvmType.Byte, JvmType.Int, 7.toByte, 7L),
        (JvmType.Byte, JvmType.Long, 7.toByte, 7L),
        (JvmType.Byte, JvmType.Float, 7.toByte, 7.0),
        (JvmType.Byte, JvmType.Double, 7.toByte, 7.0),
        (JvmType.Short, JvmType.Int, 8.toShort, 8L),
        (JvmType.Short, JvmType.Long, 8.toShort, 8L),
        (JvmType.Short, JvmType.Float, 8.toShort, 8.0),
        (JvmType.Short, JvmType.Double, 8.toShort, 8.0),
        (JvmType.Char, JvmType.Int, 'A', 65L),
        (JvmType.Char, JvmType.Long, 'A', 65L),
        (JvmType.Char, JvmType.Float, 'A', 65.0),
        (JvmType.Char, JvmType.Double, 'A', 65.0),
        (JvmType.Int, JvmType.Long, 9, 9L),
        (JvmType.Int, JvmType.Float, 9, 9.0),
        (JvmType.Int, JvmType.Double, 9, 9.0),
        (JvmType.Long, JvmType.Float, 10L, 10.0),
        (JvmType.Long, JvmType.Double, 10L, 10.0),
        (JvmType.Float, JvmType.Double, 11.0f, 11.0)
      )
      val referenceConversions = List[(JvmType, JvmType, Any, Any)](
        (JvmType.AnyRef, JvmType.Byte, Int.box(12), 12),
        (JvmType.AnyRef, JvmType.Short, Int.box(13), 13),
        (JvmType.AnyRef, JvmType.Char, Char.box('B'), 66),
        (JvmType.AnyRef, JvmType.Boolean, Boolean.box(true), 1),
        (JvmType.AnyRef, JvmType.Int, Boolean.box(false), 0L),
        (JvmType.AnyRef, JvmType.Int, Char.box('C'), 67L),
        (JvmType.AnyRef, JvmType.Int, Int.box(14), 14L),
        (JvmType.AnyRef, JvmType.Long, Char.box('D'), 68L),
        (JvmType.AnyRef, JvmType.Long, Long.box(15L), 15L),
        (JvmType.AnyRef, JvmType.Float, Char.box('E'), 69.0),
        (JvmType.AnyRef, JvmType.Float, Float.box(16.0f), 16.0),
        (JvmType.AnyRef, JvmType.Double, Char.box('F'), 70.0),
        (JvmType.AnyRef, JvmType.Double, Double.box(17.0), 17.0),
        (JvmType.Byte, JvmType.AnyRef, 18.toByte, Byte.box(18.toByte)),
        (JvmType.Short, JvmType.AnyRef, 19.toShort, Short.box(19.toShort)),
        (JvmType.Char, JvmType.AnyRef, 'G', Char.box('G')),
        (JvmType.Boolean, JvmType.AnyRef, true, Boolean.box(true)),
        (JvmType.Int, JvmType.AnyRef, 20, Int.box(20))
      )

      ZIO
        .foreach(widenings ++ referenceConversions) { case (actual, expected, value, result) =>
          val trigger     = StreamError.source("typed")
          val interpreter = new AsyncInterpreter(new FailingLaneReader(expected, trigger))
          interpreter
            .addAsyncCatchAll[String](_ => Async.succeed(streamForLane(value, actual)), allowReferenceRecovery = true)
          run(pullLane(interpreter, expected)).map(observed => assertTrue(observed == result))
        }
        .map(results => results.reduce(_ && _))
    },
    test("typed recovery rejects every primitive narrowing family") {
      val cases = List(
        (JvmType.Short, JvmType.Byte, 1.toShort),
        (JvmType.Char, JvmType.Short, 'A'),
        (JvmType.Int, JvmType.Char, 1),
        (JvmType.Long, JvmType.Int, 1L),
        (JvmType.Float, JvmType.Long, 1.0f),
        (JvmType.Double, JvmType.Float, 1.0),
        (JvmType.Boolean, JvmType.Int, true)
      )
      ZIO
        .foreach(cases) { case (actual, expected, value) =>
          val interpreter = new AsyncInterpreter(new FailingLaneReader(expected, StreamError.source("typed")))
          interpreter.addAsyncCatchAll[String](_ => Async.succeed(streamForLane(value, actual)))
          run(pullLane(interpreter, expected).either)
            .map(result => assertTrue(result.left.exists(_.isInstanceOf[IllegalArgumentException])))
        }
        .map(results => results.reduce(_ && _))
    },
    test("defect recovery handles a null failure and None replays the original failure directly") {
      val recovered = new AsyncInterpreter(new TestReader(() => Async.fail(null)))
      recovered.addAsyncCatchDefect { case null => Async.succeed(Some(Stream.succeed("recovered": Any))) }
      val trigger  = new RuntimeException("defect")
      val replayed = new AsyncInterpreter(new TestReader(() => Async.fail(trigger)))
      replayed.addAsyncCatchDefect { case cause if cause eq trigger => Async.succeed(None) }
      for {
        value  <- run(recovered.read[Any]("eof"))
        replay <- run(replayed.read[Any]("eof").either)
      } yield assertTrue(value == "recovered", replay == Left(trigger))
    },
    test("a null failed-owner close prevents defect recovery without replacing the null trigger") {
      val calls  = new AtomicInteger(0)
      val source = new TestReader(() => Async.fail(null)) {
        override def close(): Async[Unit] = Async.fail(null)
      }
      val interpreter = new AsyncInterpreter(source)
      interpreter.addAsyncCatchDefect { case null =>
        calls.incrementAndGet()
        Async.succeed(Some(Stream.succeed("not-run": Any)))
      }
      run(interpreter.read[Any]("eof").either).map(result => assertTrue(result == Left(null), calls.get() == 0))
    },
    test("recovery closes failed ownership first and close failure prevents handler") {
      val trigger = StreamError.source("typed")
      val cleanup = new RuntimeException("close")
      val calls   = new AtomicInteger(0)
      val source  = new TestReader(() => Async.failTrusted(trigger)) {
        override def close(): Async[Unit] = Async.fail(cleanup)
      }
      val interpreter = new AsyncInterpreter(source)
      interpreter.addAsyncCatchAll[String] { _ =>
        calls.incrementAndGet(); Async.succeed(Stream.succeed("no": Any))
      }
      run(interpreter.read[Any]("eof").either).map { result =>
        assertTrue(
          result == Left(trigger),
          trigger.getSuppressed.toList == List(cleanup),
          calls.get() == 0
        )
      }
    },
    test("defect classification runs only after failed ownership closes") {
      val trigger = new RuntimeException("defect")
      val closed  = new java.util.concurrent.atomic.AtomicBoolean(false)
      val source  = new TestReader(() => Async.fail(trigger)) {
        override def close(): Async[Unit] = { closed.set(true); Async.succeed(()) }
      }
      val interpreter = new AsyncInterpreter(source)
      interpreter.addAsyncCatchDefect(new PartialFunction[Throwable, Async[Option[Stream[_, Any]]]] {
        def isDefinedAt(cause: Throwable): Boolean                 = closed.get() && (cause eq trigger)
        def apply(cause: Throwable): Async[Option[Stream[_, Any]]] =
          Async.succeed(Some(Stream.succeed("recovered": Any)))
      })
      run(interpreter.read[Any]("eof")).map(result => assertTrue(result == "recovered", closed.get()))
    },
    test("defect classifier throws as callback failure and an outer recovery handles it") {
      val sourceFailure     = new RuntimeException("source")
      val classifierFailure = new RuntimeException("classifier")
      val interpreter       = new AsyncInterpreter(new TestReader(() => Async.fail(sourceFailure)))
      interpreter.addAsyncCatchDefect {
        case cause if cause eq classifierFailure => Async.succeed(Some(Stream.succeed("outer": Any)))
      }
      interpreter.addAsyncCatchDefect(new PartialFunction[Throwable, Async[Option[Stream[_, Any]]]] {
        def isDefinedAt(cause: Throwable): Boolean                 = throw classifierFailure
        def apply(cause: Throwable): Async[Option[Stream[_, Any]]] =
          Async.succeed(Some(Stream.succeed("bad": Any)))
      })
      run(interpreter.read[Any]("eof")).map(result => assertTrue(result == "outer"))
    },
    test("an outer defect recovery handles an inner typed handler failure without re-closing ownership") {
      val trigger        = StreamError.source("typed")
      val handlerFailure = new RuntimeException("handler")
      val closes         = new AtomicInteger(0)
      val source         = new TestReader(() => Async.failTrusted(trigger)) {
        override def close(): Async[Unit] = { closes.incrementAndGet(); Async.succeed(()) }
      }
      val interpreter = new AsyncInterpreter(source)
      interpreter.addAsyncCatchDefect {
        case cause if cause eq handlerFailure =>
          Async.succeed(Some(Stream.succeed("outer": Any)))
      }
      interpreter.addAsyncCatchAll[String](_ => Async.fail(handlerFailure))
      run(interpreter.read[Any]("eof")).map(result => assertTrue(result == "outer", closes.get() == 1))
    },
    test("cancelling recovery joins the detached root close and leaves a safe terminal reader") {
      val trigger      = StreamError.source("typed")
      val closePending = new ClosePending
      val source       = new TestReader(() => Async.failTrusted(trigger)) {
        override def close(): Async[Unit] = closePending
      }
      val interpreter = new AsyncInterpreter(source)
      interpreter.addAsyncCatchAll[String](_ => Async.succeed(Stream.succeed("recovered": Any)))
      val pull    = interpreter.read[Any]("eof")
      val pending = pull.asInstanceOf[Pollable[Any]].poll(Noop)
      val cleanup = Async.cancelWithCleanup(pull.asInstanceOf[Pollable[Any]])
      for {
        fiber <- run(cleanup).fork
        _     <- run(closePending.pollEntered.peek)
        before =
          closePending.polls.get() > 0 && closePending.cancels.get() == 0 && fold(pending).isInstanceOf[Suspended[_]]
        _      = closePending.succeed()
        _     <- fiber.join
        next  <- run(interpreter.read[Any]("eof"))
        reset <- run(interpreter.reset().either)
      } yield assertTrue(
        before,
        closePending.cancels.get() == 0,
        next == "eof",
        reset.left.exists(_.isInstanceOf[java.io.IOException])
      )
    },
    test("recovery rejects an incompatible logical type and closes its owner") {
      val trigger = StreamError.source("typed")
      val closes  = new AtomicInteger(0)
      val source  = new TestReader(() => Async.failTrusted(trigger)) {
        override def jvmType: JvmType                                               = JvmType.Int
        override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Async[Long] = Async.failTrusted(trigger)
      }
      val recovery = Stream.fromReader[Nothing, String](new Reader.SyncReader[String] {
        def isClosed: Boolean                 = false
        def read[A >: String](sentinel: A): A = "bad"
        def close(): Unit                     = { closes.incrementAndGet(); () }
      })
      val interpreter = new AsyncInterpreter(source)
      interpreter.addAsyncCatchAll[String](_ => Async.succeed(recovery.asInstanceOf[Stream[_, Any]]))
      for {
        result <- run(interpreter.readInt(Long.MinValue).either)
        _      <- run(interpreter.closeForTest().either)
      } yield {
        assertTrue(
          result.left.exists(cause =>
            cause.isInstanceOf[IllegalArgumentException] &&
              cause.getMessage == "Async recovery stream has incompatible element type AnyRef; expected Int"
          ),
          closes.get() == 1
        )
      }
    },
    test("finalizer poll-time defects are normalized as untrusted callback failures") {
      val forged = StreamError.source("forged")
      val effect = new Async.Operation[Unit] {
        def poll(onComplete: Runnable): Async[Unit]  = throw forged
        protected def cancelOperation(): Async[Unit] = Async.succeed(())
      }
      val interpreter = new AsyncInterpreter(new TestReader(() => Async.succeed("eof")))
      interpreter.addAsyncFinalizer(() => effect)
      run(interpreter.closeForTest().either).map { result =>
        assertTrue(result.left.exists(cause => (cause ne forged) && cause.isInstanceOf[StreamError]))
      }
    },
    test("cancelling an unstarted lifecycle operation never constructs it or retains admission") {
      val constructions = new AtomicInteger(0)
      val reader        = new TestReader(() => Async.succeed(1)) {
        override def setLimit(n: Long): Async[Boolean] = {
          constructions.incrementAndGet()
          Async.succeed(n == 1L)
        }
      }
      val interpreter = new AsyncInterpreter(reader)
      val operation   = interpreter.setLimit(1L)
      for {
        _      <- run(Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Boolean]]))
        closed <- run(interpreter.isClosed)
      } yield assertTrue(constructions.get() == 0, !closed)
    },
    test("reader close fairness yields after its ready budget and cancellation joins all claimed closes") {
      val closes      = new AtomicInteger(0)
      val gate        = new ClosePending
      val interpreter = new AsyncInterpreter(new TestReader(() => Async.succeed("eof")))
      val readers     = Vector.tabulate(300)(index =>
        new TestReader(() => Async.succeed("eof")) {
          override def close(): Async[Unit] = {
            closes.incrementAndGet()
            if (index == 256) gate else Async.succeed(())
          }
        }
      )
      val operation                          = interpreter.closeReadersForTest(readers: _*)
      val pending                            = operation.asInstanceOf[Pollable[Unit]].poll(Noop)
      def awaitGate: ZIO[Any, Nothing, Unit] =
        if (gate.polls.get() > 0) ZIO.unit else ZIO.yieldNow *> ZIO.suspendSucceed(awaitGate)
      for {
        fiber <- run(Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Unit]])).fork
        _     <- awaitGate
        before = gate.polls.get() > 0 && closes.get() == 257
        _      = gate.succeed()
        _     <- fiber.join
      } yield assertTrue(pending.isInstanceOf[Pollable[_]], before, closes.get() == readers.length)
    },
    test("owned-close cancellation joins a pending finalizer without cancelling or invoking it twice") {
      val finalizer   = new ClosePending
      val calls       = new AtomicInteger(0)
      val interpreter = new AsyncInterpreter(new TestReader(() => Async.succeed("eof")))
      interpreter.addAsyncFinalizer { () => calls.incrementAndGet(); finalizer }
      val close                                   = interpreter.closeForTest()
      val pending                                 = close.asInstanceOf[Pollable[Unit]].poll(Noop)
      def awaitFinalizer: ZIO[Any, Nothing, Unit] =
        if (finalizer.polls.get() > 0) ZIO.unit else ZIO.yieldNow *> ZIO.suspendSucceed(awaitFinalizer)
      for {
        fiber <- run(Async.cancelWithCleanup(close.asInstanceOf[Pollable[Unit]])).fork
        _     <- awaitFinalizer
        before = calls.get() == 1 && finalizer.polls.get() > 0 && finalizer.cancels.get() == 0
        _      = finalizer.succeed()
        _     <- fiber.join
        _     <- run(interpreter.closeForTest())
      } yield assertTrue(
        pending.isInstanceOf[Pollable[_]],
        before,
        calls.get() == 1,
        finalizer.cancels.get() == 0
      )
    },
    test("async finalizer is exactly once, preserves primary, and permits same-driver reentrant close") {
      val primary                       = new RuntimeException("primary")
      val secondary                     = new RuntimeException("finalizer")
      val calls                         = new AtomicInteger(0)
      var interpreter: AsyncInterpreter = null
      interpreter = new AsyncInterpreter(new TestReader(() => Async.fail(primary)))
      interpreter.addAsyncFinalizer { () =>
        calls.incrementAndGet()
        interpreter.closeForTest().flatMap(_ => Async.fail(secondary))
      }
      for {
        _      <- run(interpreter.read[Any]("eof").either)
        first  <- run(interpreter.closeForTest().either)
        replay <- run(interpreter.closeForTest().either)
      } yield assertTrue(
        first == Left(primary),
        replay == Left(primary),
        primary.getSuppressed.toList == List(secondary),
        calls.get() == 1
      )
    }
  )
}

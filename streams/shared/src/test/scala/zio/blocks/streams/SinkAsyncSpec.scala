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
import zio.blocks.chunk.Chunk
import zio.blocks.streams.internal.StreamError
import zio.blocks.streams.io.Reader
import zio.test._

object SinkAsyncSpec extends StreamsBaseSpec {
  private implicit val ec: ExecutionContext = new ExecutionContext {
    def execute(runnable: Runnable): Unit     = Async.schedule(runnable, forceMacrotask = false)
    def reportFailure(cause: Throwable): Unit = throw cause
  }

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

  private def run[A](async: Async[A]): ZIO[Any, Throwable, A]             = ZIO.fromFuture(_ => async.toFuture)
  private def reader[A: JvmType.Infer](values: A*): Reader.AsyncReader[A] =
    Reader.fromChunk(Chunk.fromIterable(values)).toAsync

  private def laneReader[A](lane: JvmType, values: Vector[A], reads: AtomicInteger = new AtomicInteger(0))(
    primitiveRead: (Int, A) => Any
  ): Reader.AsyncReader[A] = new Reader.AsyncReader[A] {
    private var index                                = 0
    override def jvmType: JvmType                    = lane
    def close(): Async[Unit]                         = Async.succeed(())
    def isClosed: Async[Boolean]                     = Async.succeed(false)
    def readable(): Async[Boolean]                   = Async.succeed(index < values.length)
    def read[B >: A](sentinel: B): Async[B]          = Async.fail(new AssertionError("generic read used for primitive lane"))
    private def next[B](eof: B)(f: A => B): Async[B] = {
      reads.incrementAndGet()
      if (index >= values.length) Async.succeed(eof)
      else {
        val value = f(values(index))
        index += 1
        Async.succeed(value)
      }
    }
    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Async[Long] =
      next(sentinel)(value => primitiveRead(index, value).asInstanceOf[Int].toLong)
    override def readLong(sentinel: Long)(implicit ev: A <:< Long): Async[Long] =
      next(sentinel)(value => primitiveRead(index, value).asInstanceOf[Long])
    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Async[Double] =
      next(sentinel)(value => primitiveRead(index, value).asInstanceOf[Float].toDouble)
    override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Async[Double] =
      next(sentinel)(value => primitiveRead(index, value).asInstanceOf[Double])
    override def readByte(): Async[Int] =
      next(-1)(value => primitiveRead(index, value).asInstanceOf[Byte].toInt & 0xff)
    override def readInts(dest: Array[Int], offset: Int, length: Int)(implicit ev: A <:< Int): Async[Int] =
      if (length == 0) Async.succeed(0)
      else next(-1) { value => dest(offset) = primitiveRead(index, value).asInstanceOf[Int]; 1 }
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: A <:< Long): Async[Int] =
      if (length == 0) Async.succeed(0)
      else next(-1) { value => dest(offset) = primitiveRead(index, value).asInstanceOf[Long]; 1 }
    override def readFloats(dest: Array[Float], offset: Int, length: Int)(implicit ev: A <:< Float): Async[Int] =
      if (length == 0) Async.succeed(0)
      else next(-1) { value => dest(offset) = primitiveRead(index, value).asInstanceOf[Float]; 1 }
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: A <:< Double): Async[Int] =
      if (length == 0) Async.succeed(0)
      else next(-1) { value => dest(offset) = primitiveRead(index, value).asInstanceOf[Double]; 1 }
    override def readBytes(dest: Array[Byte], offset: Int, length: Int)(implicit ev: A <:< Byte): Async[Int] =
      if (length == 0) Async.succeed(0)
      else next(-1) { value => dest(offset) = primitiveRead(index, value).asInstanceOf[Byte]; 1 }
  }

  private def intLane(values: Int*): Reader.AsyncReader[Int] =
    laneReader(JvmType.Int, values.toVector)((_, value) => value)
  private def longLane(values: Long*): Reader.AsyncReader[Long] =
    laneReader(JvmType.Long, values.toVector)((_, value) => value)
  private def floatLane(values: Float*): Reader.AsyncReader[Float] =
    laneReader(JvmType.Float, values.toVector)((_, value) => value)
  private def doubleLane(values: Double*): Reader.AsyncReader[Double] =
    laneReader(JvmType.Double, values.toVector)((_, value) => value)
  private def byteLane(values: Byte*): Reader.AsyncReader[Byte] =
    laneReader(JvmType.Byte, values.toVector)((_, value) => value)

  private final class PendingReader extends Reader.AsyncReader[Any] {
    private val next                          = new Completer[Any]
    val closes                                = new AtomicInteger(0)
    def close(): Async[Unit]                  = { closes.incrementAndGet(); Async.succeed(()) }
    def isClosed: Async[Boolean]              = Async.succeed(false)
    def readable(): Async[Boolean]            = Async.succeed(false)
    def read[A >: Any](sentinel: A): Async[A] = next.asInstanceOf[Async[A]]
    def complete(value: Any): Unit            = next.succeed(value)
  }

  private final class CancelledRead extends Async.Operation[Any] {
    val cancelled                                = new AtomicInteger(0)
    val cleanup                                  = new Completer[Unit]
    def poll(onComplete: Runnable): Async[Any]   = this
    protected def cancelOperation(): Async[Unit] = { cancelled.incrementAndGet(); cleanup }
  }

  private final class CancelReader(read: CancelledRead) extends Reader.AsyncReader[Any] {
    def close(): Async[Unit]                  = Async.succeed(())
    def isClosed: Async[Boolean]              = Async.succeed(false)
    def readable(): Async[Boolean]            = Async.succeed(false)
    def read[A >: Any](sentinel: A): Async[A] = read.asInstanceOf[Async[A]]
  }

  def spec = suite("Sink asynchronous drains")(
    test("built-ins drain ready readers") {
      for {
        all   <- run(Sink.collectAll[Int].drain(reader(1, 2, 3)))
        count <- run(Sink.count.drain(reader("a", "b")))
        unit  <- run(Sink.drain.drain(reader(1, 2)))
        head  <- run(Sink.head[Int].drain(reader(1, 2)))
        last  <- run(Sink.last[Int].drain(reader(1, 2)))
        take  <- run(Sink.take[Int](2).drain(reader(1, 2, 3)))
        si    <- run(Sink.sumInt.drain(reader(1, 2, 3)))
        sl    <- run(Sink.sumLong.drain(reader(1L, 2L, 3L)))
        sf    <- run(Sink.sumFloat.drain(reader(1.5f, 2.5f)))
        sd    <- run(Sink.sumDouble.drain(reader(1.5d, 2.5d)))
      } yield assertTrue(
        all == Chunk(1, 2, 3),
        count == 2L,
        unit == (),
        head.contains(1),
        last.contains(2),
        take == Chunk(1, 2),
        si == 6L,
        sl == 6L,
        sf == 4.0,
        sd == 4.0
      )
    },
    test("all JVM lanes preserve values, including logical booleans and signed bytes") {
      for {
        i <- run(Sink.collectAll[Int].drain(reader(1, -1)))
        l <- run(Sink.collectAll[Long].drain(reader(1L, -1L)))
        f <- run(Sink.collectAll[Float].drain(reader(1.5f, -1f)))
        d <- run(Sink.collectAll[Double].drain(reader(1.5d, -1d)))
        r <- run(Sink.collectAll[String].drain(reader("a", "b")))
        b <- run(Sink.collectAll[Boolean].drain(reader(false, true)))
        y <- run(Sink.collectAll[Byte].drain(reader(1.toByte, (-1).toByte)))
      } yield assertTrue(
        i == Chunk(1, -1),
        l == Chunk(1L, -1L),
        f == Chunk(1.5f, -1f),
        d == Chunk(1.5d, -1d),
        r == Chunk("a", "b"),
        b == Chunk(false, true),
        y == Chunk(1.toByte, (-1).toByte)
      )
    },
    test("primitive lanes never use generic read and collect with specialized builders") {
      for {
        i <- run(Sink.collectAll[Int].drain(intLane(Int.MinValue, Int.MaxValue)))
        l <- run(Sink.collectAll[Long].drain(longLane(Long.MinValue, 0L)))
        f <- run(Sink.collectAll[Float].drain(floatLane(Float.MinValue, Float.MaxValue, Float.NaN)))
        d <- run(Sink.collectAll[Double].drain(doubleLane(Double.MinValue, -Double.MaxValue, Double.NaN)))
        b <- run(Sink.collectAll[Byte].drain(byteLane(Byte.MinValue, (-1).toByte, Byte.MaxValue)))
      } yield assertTrue(
        i == Chunk(Int.MinValue, Int.MaxValue),
        l == Chunk(Long.MinValue, 0L),
        f(0) == Float.MinValue,
        f(1) == Float.MaxValue,
        f(2).isNaN,
        d(0) == Double.MinValue,
        d(1) == -Double.MaxValue,
        d(2).isNaN,
        b == Chunk(Byte.MinValue, (-1).toByte, Byte.MaxValue)
      )
    },
    test("total, short-circuit, and specialized fold drains dispatch through the Int lane") {
      val existsReads  = new AtomicInteger(0)
      val existsReader = laneReader(JvmType.Int, Vector(1, 2, 3), existsReads)((_, value) => value)
      for {
        count       <- run(Sink.count.drain(intLane(1, 2, 3)))
        _           <- run(Sink.drain.drain(intLane(1, 2)))
        head        <- run(Sink.head[Int].drain(intLane(1, 2)))
        last        <- run(Sink.last[Int].drain(intLane(1, 2)))
        take        <- run(Sink.take[Int](2).drain(intLane(1, 2, 3)))
        exists      <- run(Sink.exists[Int](_ == 2).drain(existsReader))
        forall      <- run(Sink.forall[Int](_ < 2).drain(intLane(1, 2, 3)))
        find        <- run(Sink.find[Int](_ == 2).drain(intLane(1, 2, 3)))
        genericFold <- run(Sink.foldLeft[Int, String]("")((acc, value) => acc + value).drain(intLane(1, 2)))
        intFold     <- run(Sink.foldLeft[Int, Int](0)(_ + _).drain(intLane(1, 2, 3)))
        longFold    <- run(Sink.foldLeft[Int, Long](0L)(_ + _).drain(intLane(1, 2, 3)))
        doubleFold  <- run(Sink.foldLeft[Int, Double](0.0)(_ + _).drain(intLane(1, 2, 3)))
      } yield assertTrue(
        count == 3L,
        head.contains(1),
        last.contains(2),
        take == Chunk(1, 2),
        exists,
        existsReads.get == 2,
        !forall,
        find.contains(2),
        genericFold == "12",
        intFold == 6,
        longFold == 6L,
        doubleFold == 6.0
      )
    },
    test("specialized sums dispatch through their native primitive lanes") {
      for {
        i <- run(Sink.sumInt.drain(intLane(Int.MinValue, Int.MaxValue)))
        l <- run(Sink.sumLong.drain(longLane(Long.MinValue, 1L)))
        f <- run(Sink.sumFloat.drain(floatLane(Float.MinValue, Float.MaxValue)))
        d <- run(Sink.sumDouble.drain(doubleLane(Double.MinValue, -Double.MaxValue)))
      } yield assertTrue(
        i == -1L,
        l == Long.MinValue + 1L,
        f == Float.MinValue.toDouble + Float.MaxValue.toDouble,
        d == Double.MinValue - Double.MaxValue
      )
    },
    test("pending reader resumes and normal drain does not close it") {
      val pending = new PendingReader
      val effect  = Sink.head[Any].drain(pending)
      val before  = fold(effect).isInstanceOf[Suspended[_]]
      pending.complete("done")
      run(effect).map(value => assertTrue(before, value.contains("done"), pending.closes.get() == 0))
    },
    test("fail is immediate and trusted without reading") {
      fold(Sink.fail("boom").drain(reader(1))) match {
        case Failed(error: StreamError, true) => assertTrue(error.value == "boom", error.isTrusted)
        case _                                => assertTrue(false)
      }
    },
    test("exists, find, and forall short-circuit at the matching element") {
      val existsN = new AtomicInteger; val findN = new AtomicInteger; val forallN = new AtomicInteger
      for {
        exists <- run(Sink.exists[Int] { n => existsN.incrementAndGet(); n == 2 }.drain(reader(1, 2, 3)))
        find   <- run(Sink.find[Int] { n => findN.incrementAndGet(); n == 2 }.drain(reader(1, 2, 3)))
        forall <- run(Sink.forall[Int] { n => forallN.incrementAndGet(); n < 2 }.drain(reader(1, 2, 3)))
      } yield assertTrue(exists, find.contains(2), !forall, existsN.get == 2, findN.get == 2, forallN.get == 2)
    },
    test("foreach and synchronous/asynchronous folds process elements in order") {
      val seen = new StringBuilder
      for {
        _ <- run(Sink.foreach[Int](n => seen.append(n)).drain(reader(1, 2, 3)))
        a <- run(Sink.foldLeft[Int, Int](0)(_ + _).drain(reader(1, 2, 3)))
        b <- run(Sink.foldLeftAsync[Int, Int](0)((x, y) => Async.succeed(x + y)).drain(reader(1, 2, 3)))
      } yield assertTrue(seen.toString == "123", a == 6, b == 6)
    },
    test("Long async fold over a borrowed Int reader is stack safe and cooperatively bounded") {
      val callbacks = new AtomicInteger
      val values    = Chunk.fromIterable(0 until 100000)
      val sink      = Sink.foldLeftAsync[Int, Long](0L) { (acc, value) =>
        callbacks.incrementAndGet()
        Async.succeed(acc + value)
      }
      val firstEffect   = sink.drain(Reader.fromChunk(values).toAsync)
      val firstPollable = fold(firstEffect).asInstanceOf[Suspended[Long]].pollable
      val firstPoll     = fold(firstPollable.poll(() => ()))
      for {
        result <- run(sink.drain(Reader.fromChunk(values).toAsync))
      } yield assertTrue(
        firstPoll.isInstanceOf[Suspended[_]],
        callbacks.get >= 1024,
        result == 4999950000L
      )
    },
    test("Long async fold resumes a genuinely suspended Int callback") {
      val pending = new Completer[Long]
      val effect  = Sink
        .foldLeftAsync[Int, Long](0L) { (acc, value) =>
          if (value == 2) pending else Async.succeed(acc + value)
        }
        .drain(Reader.fromChunk(Chunk(1, 2, 3)).toAsync)
      val running = effect.start
      pending.succeed(3L)
      run(running).map(result => assertTrue(result == 6L))
    },
    test("Long async fold launders thrown and failed callback StreamErrors") {
      val thrown       = StreamError.source("thrown")
      val failed       = StreamError.source("failed")
      val thrownEffect = Sink
        .foldLeftAsync[Int, Long](0L)((_, _) => throw thrown)
        .drain(Reader.fromChunk(Chunk(1)).toAsync)
      val failedEffect = Sink
        .foldLeftAsync[Int, Long](0L)((_, _) => Async.failTrusted(failed))
        .drain(Reader.fromChunk(Chunk(1)).toAsync)
      for {
        thrownResult <- run(thrownEffect).either
        failedResult <- run(failedEffect).either
      } yield assertTrue(
        List(thrownResult, failedResult).forall {
          case Left(error: StreamError) => !error.isTrusted
          case _                        => false
        }
      )
    },
    test("Long async fold preserves ordinary and trusted failures from a native Int reader") {
      def source(failure: Throwable, trusted: Boolean) = new Reader.AsyncReader[Int] {
        def close(): Async[Unit]                  = Async.succeed(())
        override def jvmType: JvmType             = JvmType.Int
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] =
          Async.fail(new AssertionError("generic read used for native Int fold"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
          if (trusted) Async.failTrusted(failure) else Async.fail(failure)
      }
      val ordinary                                = new RuntimeException("native-int-long-fold")
      val trusted                                 = StreamError.source("native-int-long-fold-trusted")
      val sink                                    = Sink.foldLeftAsync[Int, Long](0L)((sum, value) => Async.succeed(sum + value))
      def poll(effect: Async[Long]): Folded[Long] = fold(effect) match {
        case Suspended(pollable) => fold(pollable.poll(() => ()))
        case result              => result
      }
      assertTrue(
        poll(sink.drain(source(ordinary, trusted = false))) == Failed(ordinary, trusted = false),
        poll(sink.drain(source(trusted, trusted = true))) == Failed(trusted, trusted = true)
      )
    },
    test("generic async fold observes ready, pending, and failed callbacks over borrowed readers") {
      val pending = new Completer[String]
      val failure = StreamError.source("generic-borrowed-fold")
      val ready   = Sink.foldLeftAsync[Int, String]("")((acc, value) => Async.succeed(acc + value)).drain(reader(1, 2))
      val waiting = Sink.foldLeftAsync[Int, String]("")((_, _) => pending).drain(reader(1))
      val failed  = Sink.foldLeftAsync[Int, String]("")((_, _) => Async.failTrusted(failure)).drain(reader(1))
      val started = waiting.start
      pending.succeed("done")
      for {
        readyResult   <- run(ready)
        waitingResult <- run(started)
        failedResult  <- run(failed).either
      } yield assertTrue(
        readyResult == "12",
        waitingResult == "done",
        failedResult.left.exists {
          case error: StreamError => (error ne failure) && !error.isTrusted && error.value == "generic-borrowed-fold"
          case _                  => false
        }
      )
    },
    test("interpreter terminal Long fold handles empty, singleton, and many dynamic Int elements") {
      val empty: Stream[Nothing, Int] = Stream.empty.flatMap(Stream.succeed)
      val singleton                   = Stream.succeed(7).flatMap(Stream.succeed)
      val many                        = Stream.range(0, 10000).flatMap(Stream.succeed)
      for {
        a <- run(empty.runFoldAsync(11L)((acc, value) => Async.succeed(acc + value)))
        b <- run(singleton.runFoldAsync(11L)((acc, value) => Async.succeed(acc + value)))
        c <- run(many.runFoldAsync(0L)((acc, value) => Async.succeed(acc + value)))
      } yield assertTrue(a == Right(11L), b == Right(18L), c == Right(49995000L))
    },
    test("interpreter terminal Long fold resumes a suspended callback without reading ahead") {
      val pending   = new Completer[Long]
      val callbacks = new AtomicInteger
      val effect    = Stream
        .range(1, 4)
        .flatMap(Stream.succeed)
        .runFoldAsync(0L) { (acc, value) =>
          callbacks.incrementAndGet()
          if (value == 2) pending else Async.succeed(acc + value)
        }
      val initial = fold(effect).asInstanceOf[Suspended[Either[Nothing, Long]]].pollable
      val waiting = fold(initial.poll(() => ())).isInstanceOf[Suspended[_]]
      val before  = callbacks.get
      pending.succeed(3L)
      run(effect).map(result => assertTrue(waiting, before == 2, callbacks.get == 3, result == Right(6L)))
    },
    test("interpreter terminal Long fold launders thrown and failed callback StreamErrors") {
      val thrown = StreamError.source("terminal-thrown")
      val failed = StreamError.source("terminal-failed")
      val stream = Stream.succeed(1).flatMap(Stream.succeed)
      for {
        thrownResult <- run(stream.runFoldAsync(0L)((_, _) => throw thrown)).either
        failedResult <- run(stream.runFoldAsync(0L)((_, _) => Async.failTrusted(failed))).either
      } yield assertTrue(
        List(thrownResult, failedResult).forall {
          case Left(error: StreamError) => !error.isTrusted
          case _                        => false
        }
      )
    },
    test("createAsync and createBoth select the asynchronous branch") {
      val syncCalls  = new AtomicInteger
      val asyncCalls = new AtomicInteger
      val both       = Sink.createBoth[Nothing, Int, String](
        _ => { syncCalls.incrementAndGet(); "sync" },
        _ => { asyncCalls.incrementAndGet(); Async.succeed("async") }
      )
      val only = Sink.createAsync[Nothing, Int, String](_ => Async.succeed("only"))
      for { a <- run(both.drain(reader(1))); b <- run(only.drain(reader(1))) } yield assertTrue(
        a == "async",
        b == "only",
        syncCalls.get == 0,
        asyncCalls.get == 1
      )
    },
    test("async sink combinators transform input, result, and typed error") {
      val transformed =
        Sink.sumInt.contramapAsync[Int, String](s => Async.succeed(s.toInt)).mapAsync(n => Async.succeed(n.toString))
      val mappedError = Sink.fail[String]("bad").mapErrorAsync(s => Async.succeed(s.length))
      for {
        value <- run(transformed.drain(reader("2", "3")))
        error <- run(mappedError.drain(reader(1)).either)
      } yield assertTrue(
        value == "5",
        error.left.exists {
          case e: StreamError => e.value == 3 && e.isTrusted
          case _              => false
        }
      )
    },
    test("reader failures retain trust while callback failures are laundered") {
      val source       = StreamError.source("source")
      val callback     = StreamError.source("callback")
      val sourceReader = new Reader.AsyncReader[Any] {
        def close()                               = Async.succeed(()); def isClosed = Async.succeed(false); def readable() = Async.succeed(true)
        def read[A >: Any](sentinel: A): Async[A] = Async.failTrusted(source)
      }
      val fromReader = fold(Sink.count.drain(sourceReader))
      val pulled     = fold(
        Sink
          .createAsync[Nothing, Any, Unit](_.read[Any](new Object).map(_ => ()))
          .drain(sourceReader)
      )
      val thrown = fold(Sink.createAsync[Nothing, Any, Unit](_ => throw callback).drain(reader(1)))
      val failed = fold(Sink.createAsync[Nothing, Any, Unit](_ => Async.failTrusted(callback)).drain(reader(1)))
      assertTrue(
        fromReader == Failed(source, trusted = true),
        pulled == Failed(source, trusted = true),
        List(thrown, failed).forall {
          case Failed(e: StreamError, false) => (e ne callback) && e.value == "callback" && !e.isTrusted
          case _                             => false
        }
      )
    },
    test("cancellation joins pending reader cleanup") {
      val read      = new CancelledRead
      val operation = Sink.count.drain(new CancelReader(read))
      val pending   = fold(operation).asInstanceOf[Suspended[Long]].pollable
      val cleanup   = Async.cancelWithCleanup(pending)
      val waiting   = fold(cleanup).isInstanceOf[Suspended[_]]
      read.cleanup.succeed(())
      run(cleanup).map(_ => assertTrue(waiting, read.cancelled.get == 1))
    },
    test("100k all-ready elements are stack safe and yield on the bounded ready loop") {
      val effect  = Sink.count.drain(reader((1 to 100000): _*))
      val yielded = fold(effect).isInstanceOf[Suspended[_]]
      run(effect).map(total => assertTrue(yielded, total == 100000L))
    },
    test("100k primitive bulk elements stay bounded through synchronous and asynchronous contramap") {
      val values = Chunk.fromIterable(0 until 100000)
      val sink   = Sink.createAsync[Nothing, Int, Int] { input =>
        val dest = new Array[Int](values.length)
        input.readInts(dest, 0, dest.length).map(read => if (read == dest.length && dest(99999) == 99999) read else -1)
      }
      val syncEffect  = sink.contramap[Int, Int](identity).drain(Reader.fromChunk(values).toAsync)
      val asyncEffect =
        sink.contramapAsync[Int, Int](value => Async.succeed(value)).drain(Reader.fromChunk(values).toAsync)
      val syncYielded  = fold(syncEffect).isInstanceOf[Suspended[_]]
      val asyncYielded = fold(asyncEffect).isInstanceOf[Suspended[_]]
      for {
        sync  <- run(syncEffect)
        async <- run(asyncEffect)
      } yield assertTrue(syncYielded, asyncYielded, sync == 100000, async == 100000)
    },
    test("asynchronously mapped bulk readers preserve primitive sentinel-domain values") {
      val longValues   = Chunk(Long.MaxValue, Long.MinValue, 0L)
      val doubleValues = Chunk(Double.MaxValue, Double.MinValue, Double.NaN)
      val longSink     = Sink.createAsync[Nothing, Long, (Int, List[Long])] { input =>
        val dest = new Array[Long](longValues.length)
        input.readLongs(dest, 0, dest.length).map(read => (read, dest.toList))
      }
      val doubleSink = Sink.createAsync[Nothing, Double, (Int, List[Double])] { input =>
        val dest = new Array[Double](doubleValues.length)
        input.readDoubles(dest, 0, dest.length).map(read => (read, dest.toList))
      }
      for {
        longs   <- run(longSink.contramapAsync[Long, Long](Async.succeed).drain(Reader.fromChunk(longValues).toAsync))
        doubles <-
          run(
            doubleSink.contramapAsync[Double, Double](Async.succeed).drain(Reader.fromChunk(doubleValues).toAsync)
          )
      } yield assertTrue(
        longs == (3, longValues.toList),
        doubles._1 == 3,
        doubles._2.take(2) == doubleValues.toList.take(2),
        doubles._2.last.isNaN
      )
    },
    test("native Int collect, drain, and foreach preserve synchronous and asynchronous failure provenance") {
      def failingReader(failure: Throwable, trusted: Boolean, synchronous: Boolean) = new Reader.AsyncReader[Int] {
        override def jvmType: JvmType                                               = JvmType.Int
        def close(): Async[Unit]                                                    = Async.succeed(())
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.fail(new AssertionError("boxed read"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
          if (synchronous) throw failure
          else if (trusted) Async.failTrusted(failure)
          else Async.fail(failure)
        override def readUpToN[A >: Int](n: Int): Async[Chunk[A]] = {
          val _ = n
          if (synchronous) throw failure
          else if (trusted) Async.failTrusted(failure)
          else Async.fail(failure)
        }
      }

      val drainFailure      = new IllegalStateException("drain read")
      val collectFailure    = new IllegalArgumentException("collect read")
      val collectAsync      = new IllegalArgumentException("collect async read")
      val collectTyped      = StreamError.source("collect typed")
      val collectAsyncTyped = StreamError.source("collect async typed")
      val foreachFailure    = new UnsupportedOperationException("foreach callback")
      for {
        drain <- run(
                   Sink.drain
                     .drain(failingReader(drainFailure, trusted = false, synchronous = false))
                     .either
                 )
        collect <- run(
                     Sink
                       .collectAll[Int]
                       .drain(failingReader(collectFailure, trusted = false, synchronous = true))
                       .either
                   )
        typed <- run(
                   Sink
                     .collectAll[Int]
                     .drain(failingReader(collectTyped, trusted = true, synchronous = true))
                     .either
                 )
        asyncCollect <- run(
                          Sink
                            .collectAll[Int]
                            .drain(failingReader(collectAsync, trusted = false, synchronous = false))
                            .either
                        )
        asyncTyped <- run(
                        Sink
                          .collectAll[Int]
                          .drain(failingReader(collectAsyncTyped, trusted = true, synchronous = false))
                          .either
                      )
        foreach <- run(
                     Sink
                       .foreachAsync[Int](_ => Async.fail(foreachFailure))
                       .drain(reader(1))
                       .either
                   )
      } yield assertTrue(
        drain.left.exists(_ eq drainFailure),
        collect.left.exists(_ eq collectFailure),
        typed.left.exists(_ eq collectTyped),
        asyncCollect.left.exists(_ eq collectAsync),
        asyncTyped.left.exists(_ eq collectAsyncTyped),
        foreach.left.exists(_ eq foreachFailure)
      )
    },
    test("filtered native Int collect accepts ready values and preserves read failure provenance") {
      def source(failure: Throwable, trusted: Boolean) = new Reader.AsyncReader[Int] {
        override def jvmType: JvmType             = JvmType.Int
        def close(): Async[Unit]                  = Async.succeed(())
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] =
          Async.fail(new AssertionError("generic read used for filtered native Int collect"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
          if (trusted) Async.failTrusted(failure) else Async.fail(failure)
      }
      val ordinary = new RuntimeException("filtered-collect-read")
      val trusted  = StreamError.source("filtered-collect-trusted-read")
      val accepted = Sink.collectTakenFilteredNativeAsyncInt(intLane(1), 1L, _ => Async.succeed(true))
      val rejected = Sink.collectTakenFilteredNativeAsyncInt(intLane(1), 1L, _ => Async.succeed(false))
      for {
        acceptedResult <- run(accepted)
        rejectedResult <- run(rejected)
        ordinaryResult <-
          run(
            Sink
              .collectTakenFilteredNativeAsyncInt(source(ordinary, trusted = false), 1L, _ => Async.succeed(true))
              .either
          )
        trustedResult <-
          run(
            Sink
              .collectTakenFilteredNativeAsyncInt(source(trusted, trusted = true), 1L, _ => Async.succeed(true))
              .either
          )
      } yield assertTrue(
        acceptedResult == Chunk(1),
        rejectedResult == Chunk.empty,
        ordinaryResult.left.exists(_ eq ordinary),
        trustedResult.left.exists(_ eq trusted)
      )
    },
    test("every native Int sink machine captures synchronous physical-read construction failures") {
      def source(failure: Throwable) = new Reader.AsyncReader[Int] {
        override def jvmType: JvmType                                               = JvmType.Int
        def close(): Async[Unit]                                                    = Async.succeed(())
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = throw failure
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = throw failure
        override def readUpToN[A >: Int](n: Int): Async[Chunk[A]]                   = throw failure
      }
      def effects(failure: Throwable): List[Async[Any]] = List(
        Sink.collectNativeAsyncInt(source(failure)),
        Sink.collectTakenFilteredNativeAsyncInt(source(failure), 1L, _ => Async.succeed(true)),
        Sink.drainNativeAsyncInt(source(failure)),
        Sink.foldFilteredNativeAsyncIntLong(
          source(failure),
          _ => Async.succeed(true),
          0L,
          (z, a) => Async.succeed(z + a)
        ),
        Sink.foldMappedNativeAsyncIntLong(source(failure), a => Async.succeed(a), 0L, (z, a) => Async.succeed(z + a)),
        Sink.foldNativeAsyncIntDouble(source(failure), 0.0d, (z, a) => Async.succeed(z + a)),
        Sink.foldNativeAsyncIntFloat(source(failure), 0.0f, (z, a) => Async.succeed(z + a)),
        Sink.foldNativeAsyncIntInt(source(failure), 0, (z, a) => Async.succeed(z + a)),
        Sink.foldNativeAsyncIntLong(source(failure), 0L, (z, a) => Async.succeed(z + a)),
        Sink.foldNativeAsyncIntUnit(source(failure), _ => Async.succeed(())),
        Sink.foldTakeDropNativeAsyncIntLong(source(failure), 1L, 1L, 0L, (z, a) => Async.succeed(z + a)),
        Sink.foldTakenNativeAsyncInt(source(failure), 1L, "", (z: String, a: Int) => Async.succeed(z + a.toString)),
        Sink.foldTakenNativeAsyncIntLong(source(failure), 1L, 0L, (z, a) => Async.succeed(z + a)),
        Sink.foldTakenWhileNativeAsyncIntLong(
          source(failure),
          _ => Async.succeed(true),
          0L,
          (z, a) => Async.succeed(z + a)
        )
      )

      val ordinary = new IllegalStateException("ordinary read construction")
      val trusted  = StreamError.source("trusted read construction")
      val failures = effects(ordinary).map(effect => scala.util.Try(effect.block).failed.toOption) ++
        effects(trusted).map(effect => scala.util.Try(effect.block).failed.toOption)
      assertTrue(
        failures.take(14).forall(_.contains(ordinary)),
        failures.drop(14).forall(_.contains(trusted))
      )
    },
    test("every native Int sink callback machine preserves ordinary and trusted asynchronous failures") {
      def effects(failure: Throwable, trusted: Boolean): List[Async[Any]] = {
        def failed[A]: Async[A] = if (trusted) Async.failTrusted(failure) else Async.fail(failure)
        List(
          Sink.foldFilteredNativeAsyncIntLong(intLane(1), _ => failed[Boolean], 0L, (z, a) => Async.succeed(z + a)),
          Sink.foldFilteredNativeAsyncIntLong(intLane(1), _ => Async.succeed(true), 0L, (_, _) => failed[Long]),
          Sink.foldMappedNativeAsyncIntLong(intLane(1), _ => failed[Int], 0L, (z, a) => Async.succeed(z + a)),
          Sink.foldMappedNativeAsyncIntLong(intLane(1), Async.succeed, 0L, (_, _) => failed[Long]),
          Sink.foldMappedSyncAsyncIntLong(
            Reader.fromChunk(Chunk(1)),
            _ => failed[Int],
            0L,
            (z, a) => Async.succeed(z + a)
          ),
          Sink.foldMappedSyncAsyncIntLong(Reader.fromChunk(Chunk(1)), Async.succeed, 0L, (_, _) => failed[Long]),
          Sink.foldNativeAsyncIntDouble(intLane(1), 0.0d, (_, _) => failed[Double]),
          Sink.foldNativeAsyncIntFloat(intLane(1), 0.0f, (_, _) => failed[Float]),
          Sink.foldNativeAsyncIntInt(intLane(1), 0, (_, _) => failed[Int]),
          Sink.foldNativeAsyncIntLong(intLane(1), 0L, (_, _) => failed[Long]),
          Sink.foldNativeAsyncIntUnit(intLane(1), _ => failed[Unit]),
          Sink.foldTakeDropNativeAsyncIntLong(intLane(1), 0L, 1L, 0L, (_, _) => failed[Long]),
          Sink.collectTakenFilteredNativeAsyncInt(intLane(1), 1L, _ => failed[Boolean]),
          Sink.foldTakenNativeAsyncInt(intLane(1), 1L, "", (_: String, _: Int) => failed[String]),
          Sink.foldTakenNativeAsyncIntLong(intLane(1), 1L, 0L, (_, _) => failed[Long]),
          Sink.foldTakenWhileNativeAsyncIntLong(intLane(1), _ => failed[Boolean], 0L, (z, a) => Async.succeed(z + a)),
          Sink.foldTakenWhileNativeAsyncIntLong(intLane(1), _ => Async.succeed(true), 0L, (_, _) => failed[Long])
        )
      }

      val ordinary = new IllegalArgumentException("ordinary callback")
      val trusted  = new UnsupportedOperationException("trusted callback")
      val failures = effects(ordinary, trusted = false).map(effect => scala.util.Try(effect.block).failed.toOption) ++
        effects(trusted, trusted = true).map(effect => scala.util.Try(effect.block).failed.toOption)
      assertTrue(
        failures.take(17).forall(_.contains(ordinary)),
        failures.drop(17).forall(_.contains(trusted))
      )
    },
    test("native Int foreach preserves ordinary and trusted read and callback failures") {
      def source(effect: Async[Long]) = new Reader.AsyncReader[Int] {
        override def jvmType: JvmType                                               = JvmType.Int
        def close(): Async[Unit]                                                    = Async.succeed(())
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.fail(new AssertionError("boxed read"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = effect
      }
      val ordinaryRead                            = new IllegalStateException("foreach ordinary read")
      val trustedRead                             = StreamError.source("foreach trusted read")
      val ordinaryCallback                        = new IllegalArgumentException("foreach ordinary callback")
      val trustedCallback                         = StreamError.source("foreach trusted callback")
      def poll(effect: Async[Unit]): Folded[Unit] = fold(effect) match {
        case Suspended(operation) => fold(operation.poll(new Runnable { def run(): Unit = () }))
        case result               => result
      }
      val results = List(
        poll(Sink.foldNativeAsyncIntUnit(source(Async.fail(ordinaryRead)), _ => Async.succeed(()))),
        poll(Sink.foldNativeAsyncIntUnit(source(Async.failTrusted(trustedRead)), _ => Async.succeed(()))),
        poll(Sink.foldNativeAsyncIntUnit(intLane(1), _ => Async.fail(ordinaryCallback))),
        poll(Sink.foldNativeAsyncIntUnit(intLane(1), _ => Async.failTrusted(trustedCallback)))
      )
      assertTrue(
        results(0) == Failed(ordinaryRead, trusted = false),
        results(1) == Failed(trustedRead, trusted = true),
        results(2) == Failed(ordinaryCallback, trusted = false),
        results(3) match {
          case Failed(error: StreamError, false) => error.value == "foreach trusted callback"
          case _                                 => false
        }
      )
    },
    test("native Int take folds preserve asynchronous read provenance and early EOF") {
      def source(effects: Vector[Async[Long]]) = new Reader.AsyncReader[Int] {
        private var index                                                           = 0
        override def jvmType: JvmType                                               = JvmType.Int
        def close(): Async[Unit]                                                    = Async.succeed(())
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.fail(new AssertionError("boxed read"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
          if (index >= effects.length) Async.succeed(sentinel)
          else { val effect = effects(index); index += 1; effect }
      }
      def poll[A](effect: Async[A]): Folded[A] = fold(effect) match {
        case Suspended(operation) => fold(operation.poll(new Runnable { def run(): Unit = () }))
        case result               => result
      }
      val ordinary        = new IllegalStateException("take ordinary read")
      val trusted         = StreamError.source("take trusted read")
      val genericOrdinary = poll(
        Sink.foldTakenNativeAsyncInt(
          source(Vector(Async.fail(ordinary))),
          2L,
          "",
          (acc: String, value) => Async.succeed(acc + value)
        )
      )
      val genericTrusted = poll(
        Sink.foldTakenNativeAsyncInt(
          source(Vector(Async.failTrusted(trusted))),
          2L,
          "",
          (acc: String, value) => Async.succeed(acc + value)
        )
      )
      val longOrdinary = poll(
        Sink.foldTakenNativeAsyncIntLong(
          source(Vector(Async.fail(ordinary))),
          2L,
          0L,
          (acc, value) => Async.succeed(acc + value)
        )
      )
      val longTrusted = poll(
        Sink.foldTakenNativeAsyncIntLong(
          source(Vector(Async.failTrusted(trusted))),
          2L,
          0L,
          (acc, value) => Async.succeed(acc + value)
        )
      )
      val genericEof = Sink
        .foldTakenNativeAsyncInt(
          source(Vector(Async.succeed(1L))),
          2L,
          "",
          (acc: String, value) => Async.succeed(acc + value)
        )
        .block
      val longEof = Sink
        .foldTakenNativeAsyncIntLong(
          source(Vector(Async.succeed(1L))),
          2L,
          0L,
          (acc, value) => Async.succeed(acc + value)
        )
        .block
      assertTrue(
        genericOrdinary == Failed(ordinary, trusted = false),
        genericTrusted == Failed(trusted, trusted = true),
        longOrdinary == Failed(ordinary, trusted = false),
        longTrusted == Failed(trusted, trusted = true),
        genericEof == "1",
        longEof == 1L
      )
    },
    test("every native Int sink machine resumes pending physical reads and callbacks") {
      def pendingRead[A](build: Reader.AsyncReader[Int] => Async[A]): Async[A] = {
        val gate   = new Completer[Long]
        val source = new Reader.AsyncReader[Int] {
          private var first                                                           = true
          override def jvmType: JvmType                                               = JvmType.Int
          def close(): Async[Unit]                                                    = Async.succeed(())
          def isClosed: Async[Boolean]                                                = Async.succeed(!first)
          def readable(): Async[Boolean]                                              = Async.succeed(true)
          def read[B >: Int](sentinel: B): Async[B]                                   = Async.fail(new AssertionError("boxed read"))
          override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
            if (first) { first = false; gate }
            else Async.succeed(sentinel)
        }
        val running = build(source).start
        gate.succeed(1L)
        running
      }
      def pendingCollect(): Async[Chunk[Int]] = {
        val gate   = new Completer[Chunk[Int]]
        val source = new Reader.AsyncReader[Int] {
          private var first                                                           = true
          override def jvmType: JvmType                                               = JvmType.Int
          def close(): Async[Unit]                                                    = Async.succeed(())
          def isClosed: Async[Boolean]                                                = Async.succeed(!first)
          def readable(): Async[Boolean]                                              = Async.succeed(true)
          def read[B >: Int](sentinel: B): Async[B]                                   = Async.fail(new AssertionError("boxed read"))
          override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = Async.succeed(sentinel)
          override def readUpToN[B >: Int](n: Int): Async[Chunk[B]]                   =
            if (first) { first = false; gate.asInstanceOf[Async[Chunk[B]]] }
            else Async.succeed(Chunk.empty)
        }
        val running = Sink.collectNativeAsyncInt(source).start
        gate.succeed(Chunk(1))
        running
      }
      def pendingCallback[A, B](value: B)(build: Completer[B] => Async[A]): Async[A] = {
        val gate    = new Completer[B]
        val running = build(gate).start
        gate.succeed(value)
        running
      }

      val reads = List[Async[Any]](
        pendingCollect(),
        pendingRead(Sink.drainNativeAsyncInt),
        pendingRead(r =>
          Sink.foldFilteredNativeAsyncIntLong(r, _ => Async.succeed(true), 0L, (z, a) => Async.succeed(z + a))
        ),
        pendingRead(r => Sink.foldMappedNativeAsyncIntLong(r, Async.succeed, 0L, (z, a) => Async.succeed(z + a))),
        pendingRead(r => Sink.foldNativeAsyncIntDouble(r, 0.0d, (z, a) => Async.succeed(z + a))),
        pendingRead(r => Sink.foldNativeAsyncIntFloat(r, 0.0f, (z, a) => Async.succeed(z + a))),
        pendingRead(r => Sink.foldNativeAsyncIntInt(r, 0, (z, a) => Async.succeed(z + a))),
        pendingRead(r => Sink.foldNativeAsyncIntLong(r, 0L, (z, a) => Async.succeed(z + a))),
        pendingRead(r => Sink.foldNativeAsyncIntUnit(r, _ => Async.succeed(()))),
        pendingRead(r => Sink.foldTakeDropNativeAsyncIntLong(r, 0L, 1L, 0L, (z, a) => Async.succeed(z + a))),
        pendingRead(r => Sink.collectTakenFilteredNativeAsyncInt(r, 1L, _ => Async.succeed(true))),
        pendingRead(r => Sink.foldTakenNativeAsyncInt(r, 1L, "", (z: String, a: Int) => Async.succeed(z + a.toString))),
        pendingRead(r => Sink.foldTakenNativeAsyncIntLong(r, 1L, 0L, (z, a) => Async.succeed(z + a))),
        pendingRead(r =>
          Sink.foldTakenWhileNativeAsyncIntLong(r, _ => Async.succeed(true), 0L, (z, a) => Async.succeed(z + a))
        )
      )
      val callbacks = List[Async[Any]](
        pendingCallback(true)(g =>
          Sink.foldFilteredNativeAsyncIntLong(intLane(1), _ => g, 0L, (z, a) => Async.succeed(z + a))
        ),
        pendingCallback(1L)(g =>
          Sink.foldFilteredNativeAsyncIntLong(intLane(1), _ => Async.succeed(true), 0L, (_, _) => g)
        ),
        pendingCallback(1)(g =>
          Sink.foldMappedNativeAsyncIntLong(intLane(1), _ => g, 0L, (z, a) => Async.succeed(z + a))
        ),
        pendingCallback(1L)(g => Sink.foldMappedNativeAsyncIntLong(intLane(1), Async.succeed, 0L, (_, _) => g)),
        pendingCallback(1)(g =>
          Sink.foldMappedSyncAsyncIntLong(Reader.fromChunk(Chunk(1)), _ => g, 0L, (z, a) => Async.succeed(z + a))
        ),
        pendingCallback(1L)(g =>
          Sink.foldMappedSyncAsyncIntLong(Reader.fromChunk(Chunk(1)), Async.succeed, 0L, (_, _) => g)
        ),
        pendingCallback(1.0d)(g => Sink.foldNativeAsyncIntDouble(intLane(1), 0.0d, (_, _) => g)),
        pendingCallback(1.0f)(g => Sink.foldNativeAsyncIntFloat(intLane(1), 0.0f, (_, _) => g)),
        pendingCallback(1)(g => Sink.foldNativeAsyncIntInt(intLane(1), 0, (_, _) => g)),
        pendingCallback(1L)(g => Sink.foldNativeAsyncIntLong(intLane(1), 0L, (_, _) => g)),
        pendingCallback(())(g => Sink.foldNativeAsyncIntUnit(intLane(1), _ => g)),
        pendingCallback(1L)(g => Sink.foldTakeDropNativeAsyncIntLong(intLane(1), 0L, 1L, 0L, (_, _) => g)),
        pendingCallback(true)(g => Sink.collectTakenFilteredNativeAsyncInt(intLane(1), 1L, _ => g)),
        pendingCallback("1")(g => Sink.foldTakenNativeAsyncInt(intLane(1), 1L, "", (_: String, _: Int) => g)),
        pendingCallback(1L)(g => Sink.foldTakenNativeAsyncIntLong(intLane(1), 1L, 0L, (_, _) => g)),
        pendingCallback(true)(g =>
          Sink.foldTakenWhileNativeAsyncIntLong(intLane(1), _ => g, 0L, (z, a) => Async.succeed(z + a))
        ),
        pendingCallback(1L)(g =>
          Sink.foldTakenWhileNativeAsyncIntLong(intLane(1), _ => Async.succeed(true), 0L, (_, _) => g)
        )
      )
      for {
        readValues     <- ZIO.foreach(reads)(effect => run(effect))
        callbackValues <- ZIO.foreach(callbacks)(effect => run(effect))
      } yield assertTrue(readValues.length == 14, callbackValues.length == 17)
    },
    test("native take-drop negotiates accepted, rejected, pending, and failed bulk skip") {
      def source(configured: Async[Boolean], acceptedSkips: Boolean = false) = new Reader.AsyncReader[Int] {
        private var index                                                           = 0
        override def jvmType: JvmType                                               = JvmType.Int
        def close(): Async[Unit]                                                    = Async.succeed(())
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.fail(new AssertionError("boxed read"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = {
          index += 1
          if (index <= 3) Async.succeed(index.toLong) else Async.succeed(sentinel)
        }
        override def setSkip(n: Long): Async[Boolean] = {
          if (acceptedSkips) index += n.toInt
          configured
        }
      }
      def drain(reader: Reader.AsyncReader[Int]) =
        Sink.foldTakeDropNativeAsyncIntLong(reader, 2L, 1L, 0L, (z, a) => Async.succeed(z + a))

      val pending    = new Completer[Boolean]
      val pendingRun = drain(source(pending)).start
      val ordinary   = new IllegalStateException("skip")
      val trusted    = new UnsupportedOperationException("trusted skip")
      pending.succeed(false)
      for {
        accepted     <- run(drain(source(Async.succeed(true), acceptedSkips = true)))
        rejected     <- run(drain(source(Async.succeed(false))))
        failed       <- run(drain(source(Async.fail(ordinary))).either)
        trustedFail  <- run(drain(source(Async.failTrusted(trusted))).either)
        pendingValue <- run(pendingRun)
      } yield assertTrue(
        accepted == 3L,
        rejected == 3L,
        pendingValue == 3L,
        failed == Left(ordinary),
        trustedFail == Left(trusted)
      )
    },
    test("every native Int sink machine crosses its cooperative fairness boundary") {
      def ints    = intLane((1 to 1025): _*)
      val effects = List[Async[Any]](
        Sink.collectNativeAsyncInt(ints),
        Sink.collectTakenFilteredNativeAsyncInt(ints, 1025L, _ => Async.succeed(true)),
        Sink.drainNativeAsyncInt(ints),
        Sink.foldFilteredNativeAsyncIntLong(ints, _ => Async.succeed(true), 0L, (z, a) => Async.succeed(z + a)),
        Sink.foldMappedNativeAsyncIntLong(ints, Async.succeed, 0L, (z, a) => Async.succeed(z + a)),
        Sink
          .foldMappedSyncAsyncIntLong(
            Reader.fromChunk(Chunk.fromIterable(1 to 1025)),
            Async.succeed,
            0L,
            (z, a) => Async.succeed(z + a)
          ),
        Sink.foldNativeAsyncIntDouble(ints, 0.0d, (z, a) => Async.succeed(z + a)),
        Sink.foldNativeAsyncIntFloat(ints, 0.0f, (z, a) => Async.succeed(z + a)),
        Sink.foldNativeAsyncIntInt(ints, 0, (z, a) => Async.succeed(z + a)),
        Sink.foldNativeAsyncIntLong(ints, 0L, (z, a) => Async.succeed(z + a)),
        Sink.foldNativeAsyncIntUnit(ints, _ => Async.succeed(())),
        Sink.foldTakeDropNativeAsyncIntLong(ints, 0L, 1025L, 0L, (z, a) => Async.succeed(z + a)),
        Sink.foldTakenNativeAsyncInt(ints, 1025L, "", (z: String, _: Int) => Async.succeed(z + "x")),
        Sink.foldTakenNativeAsyncIntLong(ints, 1025L, 0L, (z, a) => Async.succeed(z + a)),
        Sink.foldTakenWhileNativeAsyncIntLong(ints, _ => Async.succeed(true), 0L, (z, a) => Async.succeed(z + a))
      )
      ZIO.foreach(effects)(effect => run(effect)).map(results => assertTrue(results.length == 15))
    },
    test("Pipeline applyToSink uses asynchronous drains for every factory and composition") {
      val input = Stream(1, 2, 3, 4, 5)
      for {
        collected <-
          run(
            input.runAsync(Pipeline.collect[Int, Int] { case n if n % 2 == 1 => n * 10 }.andThenSink(Sink.collectAll))
          )
        dropped  <- run(input.runAsync(Pipeline.drop[Int](2).andThenSink(Sink.collectAll)))
        filtered <- run(input.runAsync(Pipeline.filter[Int](_ % 2 == 0).andThenSink(Sink.collectAll)))
        mapped   <- run(input.runAsync(Pipeline.map[Int, Int](_ + 1).andThenSink(Sink.collectAll)))
        taken    <- run(input.runAsync(Pipeline.take[Int](2).andThenSink(Sink.collectAll)))
        buffered <- run(input.runAsync(Pipeline.buffer[Int](2).andThenSink(Sink.collectAll)))
        chunked  <- run(input.runAsync(Pipeline.chunked[Int](2).andThenSink(Sink.collectAll)))
        composed <- run(
                      input.runAsync(
                        Pipeline
                          .drop[Int](1)
                          .andThen(Pipeline.filter[Int](_ % 2 == 0))
                          .andThen(Pipeline.map[Int, Int](_ * 10))
                          .andThenSink(Sink.collectAll)
                      )
                    )
      } yield assertTrue(
        collected == Right(Chunk(10, 30, 50)),
        dropped == Right(Chunk(3, 4, 5)),
        filtered == Right(Chunk(2, 4)),
        mapped == Right(Chunk(2, 3, 4, 5, 6)),
        taken == Right(Chunk(1, 2)),
        buffered == Right(Chunk(1, 2, 3, 4, 5)),
        chunked == Right(Chunk(Chunk(1, 2), Chunk(3, 4), Chunk(5))),
        composed == Right(Chunk(20, 40))
      )
    },
    test("Pipeline applyToSink closes its derived asynchronous reader and preserves failure precedence") {
      val closes           = new AtomicInteger(0)
      val primary          = new RuntimeException("drain")
      val closeFailure     = new RuntimeException("close")
      val successfulReader = Reader
        .fromChunk(Chunk(1, 2, 3))
        .toAsync
        .withReleaseAsync { () => closes.incrementAndGet(); Async.succeed(()) }
      val failingReader = Reader
        .fromChunk(Chunk(1))
        .toAsync
        .withReleaseAsync(() => Async.fail(closeFailure))
      val successful = Stream
        .fromReader[Nothing, Int](successfulReader)
        .runAsync(Pipeline.filter[Int](_ > 1).andThenSink(Sink.collectAll[Int]))
      val failing = Stream
        .fromReader[Nothing, Int](failingReader)
        .runAsync(
          Pipeline
            .filter[Int](_ => true)
            .andThenSink(Sink.createAsync[Nothing, Int, Unit](_ => Async.fail(primary)))
        )
        .either
      for {
        values <- run(successful)
        error  <- run(failing)
      } yield assertTrue(
        values == Right(Chunk(2, 3)),
        closes.get() == 1,
        error == Left(primary),
        primary.getSuppressed.toList == List(closeFailure)
      )
    }
  )
}

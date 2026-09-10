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

import zio._
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.combinators.Concat
import zio.blocks.streams.internal.{AsyncInterpreter, StreamError, StreamState, SyncInterpreter}
import zio.blocks.streams.io.Reader
import zio.test._

object StreamAsyncTerminalSpec extends StreamsBaseSpec {
  private implicit val ec: ExecutionContext = new ExecutionContext {
    def execute(runnable: Runnable): Unit     = Async.schedule(runnable, forceMacrotask = false)
    def reportFailure(cause: Throwable): Unit = throw cause
  }

  private def run[A](effect: Async[A]): ZIO[Any, Throwable, A] = ZIO.fromFuture(_ => effect.toFuture)

  private sealed trait Folded[+A]
  private final case class Ready[A](value: A)             extends Folded[A]
  private final case class Failed(cause: Throwable)       extends Folded[Nothing]
  private final case class Pending[A](value: Pollable[A]) extends Folded[A]

  private final class Replacing[A] extends Pollable[A] {
    private val result                 = new Completer[A]
    val started                        = new Completer[Unit]
    def poll(wake: Runnable): Async[A] = { started.succeed(()); result }
    def succeed(value: A): Unit        = result.succeed(value)
  }

  private final class Throwing[A](cause: Throwable) extends Pollable[A] {
    def poll(wake: Runnable): Async[A] = throw cause
  }

  private final class CancelOnPoll[A](result: Async[A]) extends Pollable[A] {
    private var armed                    = false
    private var cancelAction: () => Unit = null
    private var wake: Runnable           = null
    val started                          = new Completer[Unit]
    def arm(action: () => Unit): Unit    = {
      val notify = synchronized { cancelAction = action; armed = true; wake }
      if (notify ne null) notify.run()
    }
    def poll(onComplete: Runnable): Async[A] = {
      val action = synchronized {
        if (!armed) { wake = onComplete; started.succeed(()); null }
        else cancelAction
      }
      if (action eq null) this
      else { action(); result }
    }
  }

  private def fold[A](effect: Async[A]): Folded[A] =
    Async.foldStep(effect)(new Async.StepFold[A, Folded[A]] {
      def success(value: A): Folded[A]                         = Ready(value)
      def failure(cause: Throwable): Folded[A]                 = Failed(cause)
      override def trustedFailure(cause: Throwable): Folded[A] = Failed(cause)
      def pending(value: Pollable[A]): Folded[A]               = Pending(value)
    })

  private final class CancelledRead extends Async.Operation[Any] {
    val cancelled                                = new AtomicInteger
    val cleanup                                  = new Completer[Unit]
    val polls                                    = new AtomicInteger
    val started                                  = new Completer[Unit]
    def poll(onComplete: Runnable): Async[Any]   = { polls.incrementAndGet(); started.succeed(()); this }
    protected def cancelOperation(): Async[Unit] = { cancelled.incrementAndGet(); cleanup }
  }

  private final class NativePendingReader extends Reader.AsyncReader[Int] {
    private val first                         = new Completer[Long]
    private var index                         = 0
    val closes                                = new AtomicInteger
    val specializedReads                      = new AtomicInteger
    val started                               = new Completer[Unit]
    def close(): Async[Unit]                  = { closes.incrementAndGet(); Async.succeed(()) }
    override def jvmType: JvmType             = JvmType.Int
    def isClosed: Async[Boolean]              = Async.succeed(closes.get > 0)
    def readable(): Async[Boolean]            = Async.succeed(false)
    def read[A >: Int](sentinel: A): Async[A] = {
      index += 1
      index match {
        case 1 => started.succeed(()); first.map(_.toInt).asInstanceOf[Async[A]]
        case 2 => Async.succeed(2)
        case 3 => Async.succeed(3)
        case _ => Async.succeed(sentinel)
      }
    }
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = {
      specializedReads.incrementAndGet()
      index += 1
      index match {
        case 1 => started.succeed(()); first
        case 2 => Async.succeed(2L)
        case 3 => Async.succeed(3L)
        case _ => Async.succeed(sentinel)
      }
    }
    def eof(): Unit                         = first.succeed(Long.MinValue)
    def fail(cause: Throwable): Unit        = first.fail(cause)
    def failTrusted(cause: Throwable): Unit = first.failTrusted(cause)
    def resume(): Unit                      = first.succeed(1L)
  }

  private final class PendingThenIntReader(next: Long => Async[Long]) extends Reader.AsyncReader[Int] {
    private val first                                                           = new Completer[Long]
    private var reads                                                           = 0
    val started                                                                 = new Completer[Unit]
    override def jvmType: JvmType                                               = JvmType.Int
    def close(): Async[Unit]                                                    = Async.succeed(())
    def isClosed: Async[Boolean]                                                = Async.succeed(false)
    def readable(): Async[Boolean]                                              = Async.succeed(false)
    def read[A >: Int](sentinel: A): Async[A]                                   = readIntPhysical(Long.MinValue).map(_.toInt.asInstanceOf[A])
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = {
      reads += 1
      if (reads == 1) { started.succeed(()); first }
      else next(sentinel)
    }
    def resume(value: Long = 1L): Unit = first.succeed(value)
  }

  private final class TwoPendingIntReader extends Reader.AsyncReader[Int] {
    private val first                                                           = new Completer[Long]
    private val second                                                          = new Completer[Long]
    private var reads                                                           = 0
    val firstStarted                                                            = new Completer[Unit]
    val secondStarted                                                           = new Completer[Unit]
    override def jvmType: JvmType                                               = JvmType.Int
    def close(): Async[Unit]                                                    = Async.succeed(())
    def isClosed: Async[Boolean]                                                = Async.succeed(false)
    def readable(): Async[Boolean]                                              = Async.succeed(false)
    def read[A >: Int](sentinel: A): Async[A]                                   = readIntPhysical(Long.MinValue).map(_.toInt.asInstanceOf[A])
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = {
      reads += 1
      if (reads == 1) { firstStarted.succeed(()); first }
      else if (reads == 2) { secondStarted.succeed(()); second }
      else Async.succeed(sentinel)
    }
    def resumeFirst(value: Long): Unit  = first.succeed(value)
    def resumeSecond(value: Long): Unit = second.succeed(value)
  }

  private final class HostileIntReader(values: Int*) extends Reader.AsyncReader[Int] {
    private var index                                                           = 0
    private var closed                                                          = false
    val specializedReads                                                        = new AtomicInteger
    override def jvmType: JvmType                                               = JvmType.Int
    def close(): Async[Unit]                                                    = { closed = true; Async.succeed(()) }
    def isClosed: Async[Boolean]                                                = Async.succeed(closed || index >= values.length)
    def readable(): Async[Boolean]                                              = Async.succeed(!closed && index < values.length)
    def read[A >: Int](sentinel: A): Async[A]                                   = Async.fail(new AssertionError("generic read used for Int lane"))
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = {
      specializedReads.incrementAndGet()
      if (closed || index >= values.length) Async.succeed(sentinel)
      else {
        val value = values(index)
        index += 1
        Async.succeed(value.toLong)
      }
    }
  }

  private final class GatedIntReader(gateAtRead: Int, values: Int*) extends Reader.AsyncReader[Int] {
    private var index                                                           = 0
    private var closed                                                          = false
    private val gatedValue                                                      = new Completer[Long]
    val gateStarted                                                             = new Completer[Unit]
    val specializedReads                                                        = new AtomicInteger
    override def jvmType: JvmType                                               = JvmType.Int
    def close(): Async[Unit]                                                    = { closed = true; Async.succeed(()) }
    def isClosed: Async[Boolean]                                                = Async.succeed(closed || index >= values.length)
    def readable(): Async[Boolean]                                              = Async.succeed(!closed && index < values.length)
    def read[A >: Int](sentinel: A): Async[A]                                   = Async.fail(new AssertionError("generic read used for Int lane"))
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = {
      val read = specializedReads.incrementAndGet()
      if (closed || index >= values.length) Async.succeed(sentinel)
      else {
        val value = values(index).toLong
        index += 1
        if (read == gateAtRead) { gateStarted.succeed(()); gatedValue }
        else Async.succeed(value)
      }
    }
    def resume(): Unit = gatedValue.succeed(values(gateAtRead - 1).toLong)
  }

  private final class HostileLongReader(values: Long*) extends Reader.AsyncReader[Long] {
    private var index                                                                                           = 0
    private var closed                                                                                          = false
    val specializedReads                                                                                        = new AtomicInteger
    override def jvmType: JvmType                                                                               = JvmType.Long
    def close(): Async[Unit]                                                                                    = { closed = true; Async.succeed(()) }
    def isClosed: Async[Boolean]                                                                                = Async.succeed(closed || index >= values.length)
    def readable(): Async[Boolean]                                                                              = Async.succeed(!closed && index < values.length)
    def read[A >: Long](sentinel: A): Async[A]                                                                  = Async.fail(new AssertionError("generic read used for Long lane"))
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Long <:< Long): Async[Int] = {
      specializedReads.incrementAndGet()
      if (closed || index >= values.length) Async.succeed(-1)
      else { dest(offset) = values(index); index += 1; Async.succeed(1) }
    }
  }

  private final class HostileFloatReader(values: Float*) extends Reader.AsyncReader[Float] {
    private var index                                                                     = 0
    private var closed                                                                    = false
    val specializedReads                                                                  = new AtomicInteger
    override def jvmType: JvmType                                                         = JvmType.Float
    def close(): Async[Unit]                                                              = { closed = true; Async.succeed(()) }
    def isClosed: Async[Boolean]                                                          = Async.succeed(closed || index >= values.length)
    def readable(): Async[Boolean]                                                        = Async.succeed(!closed && index < values.length)
    def read[A >: Float](sentinel: A): Async[A]                                           = Async.fail(new AssertionError("generic read used for Float lane"))
    override def readFloat(sentinel: Double)(implicit ev: Float <:< Float): Async[Double] = {
      specializedReads.incrementAndGet()
      if (closed || index >= values.length) Async.succeed(sentinel)
      else { val value = values(index); index += 1; Async.succeed(value.toDouble) }
    }
  }

  private final class HostileDoubleReader(values: Double*) extends Reader.AsyncReader[Double] {
    private var index                            = 0
    private var closed                           = false
    val specializedReads                         = new AtomicInteger
    override def jvmType: JvmType                = JvmType.Double
    def close(): Async[Unit]                     = { closed = true; Async.succeed(()) }
    def isClosed: Async[Boolean]                 = Async.succeed(closed || index >= values.length)
    def readable(): Async[Boolean]               = Async.succeed(!closed && index < values.length)
    def read[A >: Double](sentinel: A): Async[A] = Async.fail(new AssertionError("generic read used for Double lane"))
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit
      ev: Double <:< Double
    ): Async[Int] = {
      specializedReads.incrementAndGet()
      if (closed || index >= values.length) Async.succeed(-1)
      else { dest(offset) = values(index); index += 1; Async.succeed(1) }
    }
  }

  private final class HostileByteReader(values: Byte*) extends Reader.AsyncReader[Byte] {
    private var index                          = 0
    private var closed                         = false
    val specializedReads                       = new AtomicInteger
    override def jvmType: JvmType              = JvmType.Byte
    def close(): Async[Unit]                   = { closed = true; Async.succeed(()) }
    def isClosed: Async[Boolean]               = Async.succeed(closed || index >= values.length)
    def readable(): Async[Boolean]             = Async.succeed(!closed && index < values.length)
    def read[A >: Byte](sentinel: A): Async[A] = Async.fail(new AssertionError("generic read used for Byte lane"))
    override def readByte(): Async[Int]        = {
      specializedReads.incrementAndGet()
      if (closed || index >= values.length) Async.succeed(-1)
      else { val value = values(index); index += 1; Async.succeed(value.toInt & 0xff) }
    }
  }

  private final class HostileSyncBooleanReader(values: Boolean*) extends Reader.SyncReader[Boolean] {
    private var index                                                              = 0
    val closes                                                                     = new AtomicInteger
    val specializedReads                                                           = new AtomicInteger
    def close(): Unit                                                              = closes.incrementAndGet()
    def isClosed: Boolean                                                          = closes.get > 0 || index >= values.length
    override def jvmType: JvmType                                                  = JvmType.Boolean
    def read[A >: Boolean](sentinel: A): A                                         = throw new AssertionError("generic read used for Boolean lane")
    override def readBoolean(sentinel: Int)(implicit ev: Boolean <:< Boolean): Int = {
      specializedReads.incrementAndGet()
      if (index >= values.length) sentinel
      else {
        val value = values(index)
        index += 1
        if (value) 1 else 0
      }
    }
  }

  private class SkipCapableIntReader(skipResult: Async[Boolean], values: Int*) extends Reader.AsyncReader[Int] {
    private var index                                                           = 0
    private var closed                                                          = false
    val closes                                                                  = new AtomicInteger
    val setSkipCalls                                                            = new AtomicInteger
    val specializedReads                                                        = new AtomicInteger
    override def jvmType: JvmType                                               = JvmType.Int
    def close(): Async[Unit]                                                    = { closed = true; closes.incrementAndGet(); Async.succeed(()) }
    def isClosed: Async[Boolean]                                                = Async.succeed(closed || index >= values.length)
    def readable(): Async[Boolean]                                              = Async.succeed(!closed && index < values.length)
    def read[A >: Int](sentinel: A): Async[A]                                   = Async.fail(new AssertionError("generic read used for Int lane"))
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = {
      specializedReads.incrementAndGet()
      if (closed || index >= values.length) Async.succeed(sentinel)
      else {
        val value = values(index)
        index += 1
        Async.succeed(value.toLong)
      }
    }
    override def setSkip(n: Long): Async[Boolean] = {
      setSkipCalls.incrementAndGet()
      skipResult.map { accepted =>
        if (accepted) index = math.min(values.length.toLong, index.toLong + math.max(0L, n)).toInt
        accepted
      }
    }
  }

  def spec = suite("Stream asynchronous terminals")(
    test("async companion sources initialize on first drive, not compilation or close") {
      val initialized = new AtomicInteger
      val stream      = Stream.unwrap {
        initialized.incrementAndGet()
        Async.succeed(Stream(1, 2))
      }
      val reader = Stream.compileToReader(stream).asInstanceOf[Reader.AsyncReader[Int]]
      for {
        _ <- run(reader.close())
      } yield assertTrue(initialized.get == 0)
    },
    test("unwrap evaluates once per materialization and propagates the active buffer size") {
      val evaluations = new AtomicInteger
      val observed    = new AtomicInteger
      val inner       = new Stream[Nothing, Int] {
        def render: String                                                     = "recording"
        private[streams] def compile(depth: Int, bufferSize: Int): Reader[Int] = {
          observed.set(bufferSize)
          Reader.singleInt(1)
        }
        private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit =
          pipeline.appendRead(Reader.singleInt(1))
      }
      val stream = Stream.bufferSize(8)(Stream.unwrap {
        evaluations.incrementAndGet()
        Async.succeed(inner)
      })
      for {
        first  <- run(stream.runCollectAsync)
        second <- run(stream.runCollectAsync)
      } yield assertTrue(
        first == Right(Chunk(1)),
        second == Right(Chunk(1)),
        evaluations.get == 2,
        observed.get == 8
      )
    },
    test("unwrap direct Long fold remains lazy and resumes stream acquisition") {
      val evaluations = new AtomicInteger
      val acquired    = new Completer[Stream[String, Int]]
      val stream      = Stream.unwrap {
        evaluations.incrementAndGet()
        acquired
      }
      val running = stream.runFoldAsync(10L)((acc, value) => Async.succeed(acc + value)).start
      acquired.succeed(Stream(1, 2, 3))
      for {
        first  <- run(running)
        second <- run(stream.runFoldAsync(0L)((acc, value) => Async.succeed(acc + value)))
      } yield assertTrue(first == Right(16L), second == Right(6L), evaluations.get == 2)
    },
    test("async companion sources produce values and capture attempt failures") {
      val failure = new IllegalStateException("attempt")
      for {
        values <- run(Stream.fromIteratorAsync(Async.succeed(Iterator(1, 2))).runCollectAsync)
        failed <- run(Stream.attemptAsync[Int](Async.fail(failure)).runCollectAsync)
      } yield assertTrue(values == Right(Chunk(1, 2)), failed == Left(failure))
    },
    test(
      "asynchronous sources decline primitive direct terminals for references and specialize unprotected Int folds"
    ) {
      def refs = Stream.fromReaderAsync[Nothing, String](
        Async.succeed(Reader.fromChunk(Chunk("a", "bb")).toAsync: Reader[String])
      )
      for {
        collected <- run(refs.runCollectAsync)
        drained   <- run(refs.runDrainAsync)
        folded    <- run(refs.runFoldAsync(0L)((acc, value) => Async.succeed(acc + value.length)))
        foreach   <- run(refs.runForeachAsync(_ => Async.succeed(())))
        intFold   <-
          run(Stream.attemptAsync(Async.succeed(2)).runFoldAsync(1L)((acc, value) => Async.succeed(acc + value)))
        evaluated <- run(Stream.evalAsync(Async.succeed(())).runDrainAsync)
        attempted <- run(Stream.attemptEvalAsync(Async.succeed(())).runDrainAsync)
      } yield assertTrue(
        collected == Right(Chunk("a", "bb")),
        drained == Right(()),
        folded == Right(3L),
        foreach == Right(()),
        intFold == Right(3L),
        evaluated == Right(()),
        attempted == Right(())
      )
    },
    test("asynchronous source sync-fold mapping preserves synchronous and asynchronous read provenance") {
      val sink                           = Sink.foldLeft[Int, Long](0L)(_ + _).mapAsync(sum => Async.succeed(sum.toString))
      def source(effect: => Async[Long]) = new Reader.AsyncReader[Int] {
        override def jvmType: JvmType                                               = JvmType.Int
        def close(): Async[Unit]                                                    = Async.succeed(())
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.fail(new AssertionError("boxed read"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = effect
      }
      val thrown                                    = new IllegalStateException("sync-fold map thrown read")
      val typed                                     = StreamError.source("sync-fold map typed read")
      val failed                                    = new IllegalArgumentException("sync-fold map failed read")
      val trusted                                   = new UnsupportedOperationException("sync-fold map trusted defect")
      def drain[E](reader: Reader.AsyncReader[Int]) =
        Stream.fromReaderAsync[E, Int](Async.succeed(reader: Reader[Int])).runAsync(sink)
      for {
        thrownResult  <- run(drain[Nothing](source(throw thrown)).either)
        typedResult   <- run(drain[String](source(throw typed)))
        failedResult  <- run(drain[Nothing](source(Async.fail(failed))).either)
        trustedResult <- run(drain[Nothing](source(Async.failTrusted(trusted))).either)
      } yield assertTrue(
        thrownResult == Left(thrown),
        typedResult == Left("sync-fold map typed read"),
        failedResult == Left(failed),
        trustedResult == Left(trusted)
      )
    },
    test("cancelling asynchronous source sync-fold mapping during acquisition closes the acquired reader") {
      val reader    = new HostileIntReader(1)
      val acquire   = new CancelOnPoll[Reader[Int]](Async.succeed(reader: Reader[Int]))
      val sink      = Sink.foldLeft[Int, Long](0L)(_ + _).mapAsync(sum => Async.succeed(sum.toString))
      var running   = null.asInstanceOf[Async.Running[Either[Nothing, String]]]
      var cleanup   = null.asInstanceOf[Async[Unit]]
      val cancelled = new Completer[Unit]
      running = Stream.fromReaderAsync[Nothing, Int](acquire).runAsync(sink).start
      for {
        _      <- run(acquire.started)
        _       = acquire.arm { () => cleanup = Async.cancelWithCleanup(running); cancelled.succeed(()) }
        _      <- run(cancelled)
        _      <- run(cleanup)
        closed <- run(reader.isClosed)
      } yield assertTrue(closed)
    },
    test("async acquire-release is lazy and releases exactly once") {
      val acquired = new AtomicInteger
      val released = new AtomicInteger
      val stream   = Stream.fromAcquireReleaseAsync(
        { acquired.incrementAndGet(); Async.succeed("resource") },
        (_: String) => { released.incrementAndGet(); Async.succeed(()) }
      )(_ => Stream(1))
      val reader = Stream.compileToReader(stream).asInstanceOf[Reader.AsyncReader[Int]]
      for {
        value <- run(reader.read(-1))
        _     <- run(reader.close())
        _     <- run(reader.close())
      } yield assertTrue(value == 1, acquired.get == 1, released.get == 1)
    },
    test("async acquire-release delegates representation, rendering, compilation, and async materialization") {
      val stream = new Stream.FromAcquireReleaseAsync[String, Nothing, Int](
        () => Async.succeed("resource"),
        _ => Async.succeed(()),
        _ => Stream(1),
        JvmType.Infer.int
      )
      val compiled    = stream.compile(0, Stream.DefaultBufferSize).expectedAsync
      val interpreted = AsyncInterpreter.fromStream(stream)
      val rejected    = scala.util
        .Try(
          stream.compileInterpreter(SyncInterpreter.fromStream(Stream.empty.asInstanceOf[Stream[Nothing, Int]]))
        )
        .failed
        .toOption
      for {
        compiledValue    <- run(compiled.readIntPhysical(Long.MinValue))
        interpretedValue <- run(interpreted.readInt(Long.MinValue))
        _                <- run(compiled.close())
        _                <- run(interpreted.closeForTest())
      } yield assertTrue(
        stream.render == "Stream.fromAcquireReleaseAsync(...)",
        stream.elementRepresentation.stableJvmType.contains(JvmType.Int),
        compiledValue == 1L,
        interpretedValue == 1L,
        rejected.contains(Stream.AsyncBoundaryRequired)
      )
    },
    test("async acquire-release protects acquisition throws in compiled and direct-fold paths") {
      val compiledFailure              = new StreamError("compiled acquisition")
      val directFailure                = new StreamError("direct acquisition")
      def stream(failure: StreamError) = Stream.fromAcquireReleaseAsync[Unit, Nothing, Int](
        throw failure,
        (_: Unit) => Async.succeed(())
      )(_ => Stream(1))
      val compiled = Stream.compileToReader(stream(compiledFailure)).expectedAsync
      for {
        compiledResult <- run(compiled.readIntPhysical(Long.MinValue).either)
        directResult   <- run(stream(directFailure).runFoldAsync(0L)((acc, value) => Async.succeed(acc + value)).either)
        _              <- run(compiled.close())
      } yield assertTrue(
        compiledResult.left.exists {
          case error: StreamError => error.value == "compiled acquisition" && !error.isTrusted
          case _                  => false
        },
        directResult.left.exists {
          case error: StreamError => error.value == "direct acquisition" && !error.isTrusted
          case _                  => false
        }
      )
    },
    test("async acquire-release closes a resource acquired after cancellation before installation") {
      val acquisition = new Completer[String]
      val started     = new Completer[Unit]
      val uses        = new AtomicInteger
      val releases    = new AtomicInteger
      val stream      = Stream.fromAcquireReleaseAsync(
        { started.succeed(()); acquisition.peek },
        (_: String) => { releases.incrementAndGet(); Async.succeed(()) }
      ) { _ =>
        uses.incrementAndGet()
        Stream(1)
      }
      val reader                                     = Stream.compileToReader(stream).asInstanceOf[Reader.AsyncReader[Int]]
      val pull                                       = reader.read(-1).start
      def awaitCloseClaim: ZIO[Any, Throwable, Unit] =
        run(reader.isClosed).flatMap(closed =>
          if (closed) ZIO.unit else ZIO.yieldNow *> ZIO.suspendSucceed(awaitCloseClaim)
        )
      for {
        _      <- run(started)
        closing = reader.close().start
        before  = closing.poll(new Runnable { def run(): Unit = () })
        _      <- awaitCloseClaim
        _       = acquisition.succeed("resource")
        value  <- run(pull)
        _      <- run(closing)
      } yield assertTrue(
        before.isInstanceOf[Pollable[?]],
        value == -1,
        uses.get == 0,
        releases.get == 1
      )
    },
    test("construction is lazy and a drive materializes and closes exactly once") {
      val made   = new AtomicInteger
      val closed = new AtomicInteger
      val stream = Stream.fromReader[Nothing, Int] {
        made.incrementAndGet()
        Reader.fromIterable(List(1, 2, 3)).withRelease(() => closed.incrementAndGet())
      }
      val effect = stream.runCollectAsync
      for {
        before <- ZIO.succeed((made.get, closed.get))
        result <- run(effect)
      } yield assertTrue(before == ((0, 0)), result == Right(Chunk(1, 2, 3)), made.get == 1, closed.get == 1)
    },
    test("materialization throws are captured by the returned Async") {
      val failure = new RuntimeException("materialize")
      val effect  = Stream.fromReader[Nothing, Int](throw failure).runCollectAsync
      run(effect.either).map(result => assertTrue(result == Left(failure)))
    },
    test("native pending AsyncReader materializes statically and dispatches async transforms and terminal") {
      val made    = new AtomicInteger
      val seen    = new StringBuilder
      val source1 = new NativePendingReader
      val stream  = Stream
        .fromReader[Nothing, Int] { made.incrementAndGet(); Reader.fromChunk(Chunk(1, 2, 3)).toAsync }
        .mapAsync(i => Async.succeed(i + 1))
        .filterAsync(i => Async.succeed(i % 2 == 0))
        .collectAsync(i => Async.succeed(Some(i * 10): Option[Int]))
        .tapEachAsync(i => Async.succeed(seen.append(i)).map(_ => ()))
        .distinctByAsync(i => Async.succeed(i))
      val compiled      = Stream.compileToReader(Stream.fromReader[Nothing, Int](source1).mapAsync(Async.succeed))
      val compiledAsync = compiled match {
        case reader: Reader.AsyncReader[Int @unchecked] => reader
        case _: Reader.SyncReader[_]                    => throw new AssertionError("Expected native AsyncReader")
      }
      val effect    = stream.runCollectAsync
      val suspended = fold(compiledAsync.read(-1)).isInstanceOf[Pending[_]]
      source1.resume()
      for {
        _      <- run(compiledAsync.close())
        result <- run(effect)
      } yield assertTrue(
        made.get == 1,
        suspended,
        result == Right(Chunk(20, 40)),
        seen.toString == "2040",
        source1.closes.get == 1
      )
    },
    test("zip promotes either asynchronous side and preserves positional tuples") {
      val leftNative  = new HostileIntReader(1, 2, 3)
      val rightNative = Reader.fromChunk(Chunk(10, 20, 30)).toAsync
      val leftZip     = Stream.fromReader[Nothing, Int](leftNative).map[Any](identity) && Stream(10, 20, 30)
      val rightZip    = Stream(1, 2, 3) && Stream.fromReader[Nothing, Int](rightNative).map[Any](identity)
      for {
        left  <- run(leftZip.runCollectAsync)
        right <- run(rightZip.runCollectAsync)
      } yield assertTrue(
        left == Right(Chunk((1, 10), (2, 20), (3, 30))),
        right == Right(Chunk((1, 10), (2, 20), (3, 30))),
        leftNative.isClosed.block
      )
    },
    test("Reader.repeated preserves asynchronous and root reader kinds") {
      val asynchronous           = Reader.repeated(Reader.fromChunk(Chunk(1, 2)).toAsync)
      val rootInput: Reader[Int] = Reader.fromChunk(Chunk(3, 4)).toAsync
      val root                   = Reader.repeated(rootInput)
      for {
        a <- run(asynchronous.read(-1))
        b <- run(asynchronous.read(-1))
        c <- run(asynchronous.read(-1))
        d <- run(root.asInstanceOf[Reader.AsyncReader[Int]].read(-1))
        e <- run(root.asInstanceOf[Reader.AsyncReader[Int]].read(-1))
        f <- run(root.asInstanceOf[Reader.AsyncReader[Int]].read(-1))
        _ <- run(asynchronous.close())
        _ <- run(root.asInstanceOf[Reader.AsyncReader[Int]].close())
      } yield assertTrue(a == 1, b == 2, c == 1, d == 3, e == 4, f == 3)
    },
    test("trusted materialization failures retain the typed source channel") {
      val failure = StreamError.source("materialize-typed")
      val stream  = new Stream[String, Int] {
        def render: String                                                                = "trusted-materialization-failure"
        private[streams] def compile(depth: Int, bufferSize: Int): Reader.SyncReader[Int] = throw failure
        private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit          = throw failure
      }
      run(stream.runCollectAsync).map(result => assertTrue(result == Left("materialize-typed")))
    },
    test("cancellation of first reader use joins active cleanup and closes the reader") {
      val read       = new CancelledRead
      val closeCount = new AtomicInteger
      val effect     = Stream
        .fromReader[Nothing, Any](Reader.closed.withRelease(() => closeCount.incrementAndGet()))
        .useReaderAsync(_ => read)
      val running = effect.start
      for {
        _           <- run(read.started)
        cleanup     <- ZIO.succeed(Async.cancelWithCleanup(running))
        waitsForRead = fold(cleanup).isInstanceOf[Pending[_]]
        _            = read.cleanup.succeed(())
        _           <- run(cleanup)
      } yield assertTrue(read.polls.get() > 0, waitsForRead, read.cancelled.get == 1, closeCount.get == 1)
    },
    test("convenience terminals and short circuiting") {
      val seen = new AtomicInteger
      val s    = Stream(1, 2, 3)
      for {
        count  <- run(s.countAsync)
        head   <- run(s.headAsync)
        last   <- run(s.lastAsync)
        exists <- run(s.existsAsync { i => seen.incrementAndGet(); Async.succeed(i == 2) })
        find   <- run(s.findAsync(i => Async.succeed(i > 1)))
        all    <- run(s.forallAsync(i => Async.succeed(i < 4)))
        drain  <- run(s.runDrainAsync)
      } yield assertTrue(
        count == Right(3L),
        head == Right(Some(1)),
        last == Right(Some(3)),
        exists == Right(true),
        seen.get == 2,
        find == Right(Some(2)),
        all == Right(true),
        drain == Right(())
      )
    },
    test("drain uses specialized native Int reads, resumes suspension, and closes") {
      val ready         = new HostileIntReader(1, 2, 3)
      val pending       = new NativePendingReader
      val pendingResult = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(pending: Reader[Int]))
        .runDrainAsync
        .start
      pending.resume()
      for {
        readyResult <- run(
                         Stream
                           .fromReaderAsync[Nothing, Int](Async.succeed(ready: Reader[Int]))
                           .runDrainAsync
                       )
        readyClosed <- run(ready.isClosed)
        resumed     <- run(pendingResult)
      } yield assertTrue(
        readyResult == Right(()),
        ready.specializedReads.get == 4,
        readyClosed,
        resumed == Right(()),
        pending.closes.get == 1
      )
    },
    test("direct native Int drain preserves typed failures across fairness and cleanup") {
      val afterYield    = StreamError.source("drain-after-yield")
      val yieldedCloses = new AtomicInteger
      val yielded       = new Reader.AsyncReader[Int] {
        private var reads                         = 0
        def close(): Async[Unit]                  = { yieldedCloses.incrementAndGet(); Async.succeed(()) }
        override def jvmType: JvmType             = JvmType.Int
        def isClosed: Async[Boolean]              = Async.succeed(yieldedCloses.get > 0)
        def read[A >: Int](sentinel: A): Async[A] =
          Async.fail(new AssertionError("generic read used for Int lane"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = {
          reads += 1
          if (reads <= 1024) Async.succeed(reads.toLong)
          else Async.failTrusted(afterYield)
        }
        def readable(): Async[Boolean] = Async.succeed(true)
      }
      val useFailure    = StreamError.source("drain-use")
      val closeFailure  = new RuntimeException("drain-close")
      val cleanupCloses = new AtomicInteger
      val cleanup       = new Reader.AsyncReader[Int] {
        def close(): Async[Unit]                                                    = { cleanupCloses.incrementAndGet(); Async.fail(closeFailure) }
        override def jvmType: JvmType                                               = JvmType.Int
        def isClosed: Async[Boolean]                                                = Async.succeed(cleanupCloses.get > 0)
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.failTrusted(useFailure)
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = Async.failTrusted(useFailure)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
      }
      for {
        unprotected <- run(Stream.attemptAsync(Async.succeed(1)).runDrainAsync)
        typed       <- run(Stream.fromReaderAsync[String, Int](Async.succeed(yielded: Reader[Int])).runDrainAsync)
        failed      <- run(
                    Stream
                      .fromReaderAsync[String, Int](Async.succeed(cleanup: Reader[Int]))
                      .runDrainAsync
                      .either
                  )
      } yield assertTrue(
        unprotected == Right(()),
        typed == Left("drain-after-yield"),
        yieldedCloses.get == 1,
        failed.left.exists(_ eq useFailure),
        useFailure.cleanupFailed,
        useFailure.getSuppressed.toList == List(closeFailure),
        cleanupCloses.get == 1
      )
    },
    test("cancelling direct native Int drain joins its read and closes exactly once") {
      val pendingRead = new CancelledRead
      val closes      = new AtomicInteger
      val reader      = new Reader.AsyncReader[Int] {
        def close(): Async[Unit]                                                    = { closes.incrementAndGet(); Async.succeed(()) }
        override def jvmType: JvmType                                               = JvmType.Int
        def isClosed: Async[Boolean]                                                = Async.succeed(closes.get > 0)
        def read[A >: Int](sentinel: A): Async[A]                                   = pendingRead.asInstanceOf[Async[A]]
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
          pendingRead.asInstanceOf[Async[Long]]
        def readable(): Async[Boolean] = Async.succeed(false)
      }
      val running = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(reader: Reader[Int]))
        .runDrainAsync
        .start
      for {
        _           <- run(pendingRead.started)
        cleanup     <- ZIO.succeed(Async.cancelWithCleanup(running))
        waitsForRead = fold(cleanup).isInstanceOf[Pending[_]]
        _            = pendingRead.cleanup.succeed(())
        _           <- run(cleanup)
      } yield assertTrue(waitsForRead, pendingRead.cancelled.get == 1, closes.get == 1)
    },
    test("collect uses the native Int builder, resumes suspension, and closes") {
      val ready         = new HostileIntReader(1, 2, 3)
      val pending       = new NativePendingReader
      val pendingResult = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(pending: Reader[Int]))
        .runCollectAsync
        .start
      pending.resume()
      for {
        readyResult <- run(
                         Stream
                           .fromReaderAsync[Nothing, Int](Async.succeed(ready: Reader[Int]))
                           .runCollectAsync
                       )
        readyClosed <- run(ready.isClosed)
        resumed     <- run(pendingResult)
      } yield assertTrue(
        readyResult == Right(Chunk(1, 2, 3)),
        readyResult.toOption.exists(_.isInstanceOf[Chunk.IntArray]),
        ready.specializedReads.get == 4,
        readyClosed,
        resumed == Right(Chunk(1, 2, 3)),
        pending.closes.get == 1
      )
    },
    test("direct native Int collect preserves typed failures across fairness and cleanup") {
      val afterYield    = StreamError.source("collect-after-yield")
      val yieldedCloses = new AtomicInteger
      val yielded       = new Reader.AsyncReader[Int] {
        private var reads                         = 0
        def close(): Async[Unit]                  = { yieldedCloses.incrementAndGet(); Async.succeed(()) }
        override def jvmType: JvmType             = JvmType.Int
        def isClosed: Async[Boolean]              = Async.succeed(yieldedCloses.get > 0)
        def read[A >: Int](sentinel: A): Async[A] =
          Async.fail(new AssertionError("generic read used for Int lane"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
          Async.fail(new AssertionError("scalar Int read used for bulk collect"))
        override def readUpToN[A >: Int](n: Int): Async[Chunk[A]] = {
          reads += 1
          if (reads == 1) Async.succeed(Chunk.fromArray(Array.fill(math.min(n, 1024))(1)).asInstanceOf[Chunk[A]])
          else Async.failTrusted(afterYield)
        }
        def readable(): Async[Boolean] = Async.succeed(true)
      }
      val useFailure    = StreamError.source("collect-use")
      val closeFailure  = new RuntimeException("collect-close")
      val cleanupCloses = new AtomicInteger
      val cleanup       = new Reader.AsyncReader[Int] {
        def close(): Async[Unit]                                                    = { cleanupCloses.incrementAndGet(); Async.fail(closeFailure) }
        override def jvmType: JvmType                                               = JvmType.Int
        def isClosed: Async[Boolean]                                                = Async.succeed(cleanupCloses.get > 0)
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.failTrusted(useFailure)
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = Async.failTrusted(useFailure)
        override def readUpToN[A >: Int](n: Int): Async[Chunk[A]]                   = {
          val _ = n
          Async.failTrusted(useFailure)
        }
        def readable(): Async[Boolean] = Async.succeed(true)
      }
      for {
        unprotected <- run(Stream.attemptAsync(Async.succeed(1)).runCollectAsync)
        typed       <- run(Stream.fromReaderAsync[String, Int](Async.succeed(yielded: Reader[Int])).runCollectAsync)
        failed      <- run(
                    Stream
                      .fromReaderAsync[String, Int](Async.succeed(cleanup: Reader[Int]))
                      .runCollectAsync
                      .either
                  )
      } yield assertTrue(
        unprotected == Right(Chunk(1)),
        typed == Left("collect-after-yield"),
        yieldedCloses.get == 1,
        failed.left.exists(_ eq useFailure),
        useFailure.cleanupFailed,
        useFailure.getSuppressed.toList == List(closeFailure),
        cleanupCloses.get == 1
      )
    },
    test("cancelling direct native Int collect joins its bulk read and closes exactly once") {
      val pendingRead = new CancelledRead
      val closes      = new AtomicInteger
      val reader      = new Reader.AsyncReader[Int] {
        def close(): Async[Unit]                                                    = { closes.incrementAndGet(); Async.succeed(()) }
        override def jvmType: JvmType                                               = JvmType.Int
        def isClosed: Async[Boolean]                                                = Async.succeed(closes.get > 0)
        def read[A >: Int](sentinel: A): Async[A]                                   = pendingRead.asInstanceOf[Async[A]]
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
          pendingRead.asInstanceOf[Async[Long]]
        override def readUpToN[A >: Int](n: Int): Async[Chunk[A]] = {
          val _ = n
          pendingRead.asInstanceOf[Async[Chunk[A]]]
        }
        def readable(): Async[Boolean] = Async.succeed(false)
      }
      val running = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(reader: Reader[Int]))
        .runCollectAsync
        .start
      for {
        _           <- run(pendingRead.started)
        cleanup     <- ZIO.succeed(Async.cancelWithCleanup(running))
        waitsForRead = fold(cleanup).isInstanceOf[Pending[_]]
        _            = pendingRead.cleanup.succeed(())
        _           <- run(cleanup)
      } yield assertTrue(waitsForRead, pendingRead.cancelled.get == 1, closes.get == 1)
    },
    test("direct native Int collect preserves synchronous and asynchronous bulk read failures") {
      def source(bulkRead: => Async[Chunk[Int]]) = new Reader.AsyncReader[Int] {
        def close(): Async[Unit]                  = Async.succeed(())
        override def jvmType: JvmType             = JvmType.Int
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] =
          Async.fail(new AssertionError("generic read used for native Int collect"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
          Async.fail(new AssertionError("scalar read used for native Int collect"))
        override def readUpToN[A >: Int](n: Int): Async[Chunk[A]] = {
          val _ = n
          bulkRead.asInstanceOf[Async[Chunk[A]]]
        }
      }
      def collect[E](reader: Reader.AsyncReader[Int]) =
        Stream.fromReaderAsync[E, Int](Async.succeed(reader: Reader[Int])).runCollectAsync

      val ordinarySync  = new RuntimeException("collect-sync")
      val trustedSync   = StreamError.source("collect-trusted-sync")
      val ordinaryAsync = new RuntimeException("collect-async")
      for {
        ordinarySyncResult  <- run(collect[Nothing](source(throw ordinarySync)).either)
        trustedSyncResult   <- run(collect[String](source(throw trustedSync)))
        ordinaryAsyncResult <- run(collect[Nothing](source(Async.fail(ordinaryAsync))).either)
      } yield assertTrue(
        ordinarySyncResult == Left(ordinarySync),
        trustedSyncResult == Left("collect-trusted-sync"),
        ordinaryAsyncResult == Left(ordinaryAsync)
      )
    },
    test("filtered take collect short-circuits native Int reads after a pending predicate") {
      val source     = new HostileIntReader(1, 2, 3, 4)
      val zeroSource = new HostileIntReader(1)
      val callbacks  = new AtomicInteger
      val pending    = new Completer[Boolean]
      val running    = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .filterAsync { value =>
          callbacks.incrementAndGet()
          if (value == 2) pending else Async.succeed(value == 3)
        }
        .take(1)
        .runCollectAsync
        .start
      pending.succeed(false)
      for {
        result <- run(running)
        closed <- run(source.isClosed)
        zero   <- run(
                  Stream
                    .fromReaderAsync[Nothing, Int](Async.succeed(zeroSource: Reader[Int]))
                    .filterAsync(_ => Async.fail(new AssertionError("zero take invoked predicate")))
                    .take(0)
                    .runCollectAsync
                )
        zeroClosed <- run(zeroSource.isClosed)
      } yield assertTrue(
        result == Right(Chunk(3)),
        callbacks.get == 3,
        source.specializedReads.get == 3,
        closed,
        zero == Right(Chunk.empty),
        zeroSource.specializedReads.get == 0,
        zeroClosed
      )
    },
    test("filtered take collect preserves native source and predicate failures") {
      val sourceCloses = new AtomicInteger
      val failedSource = new Reader.AsyncReader[Int] {
        override def jvmType: JvmType                                               = JvmType.Int
        def close(): Async[Unit]                                                    = { sourceCloses.incrementAndGet(); Async.succeed(()) }
        def isClosed: Async[Boolean]                                                = Async.succeed(sourceCloses.get > 0)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.failTrusted(StreamError.source("source"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
          Async.failTrusted(StreamError.source("source"))
      }
      val predicateSource  = new HostileIntReader(1, 2)
      val predicateFailure = new RuntimeException("predicate")
      for {
        sourceResult <- run(
                          Stream
                            .fromReaderAsync[String, Int](Async.succeed(failedSource: Reader[Int]))
                            .filterAsync(_ => Async.succeed(true))
                            .take(1)
                            .runCollectAsync
                        )
        predicateResult <- run(
                             Stream
                               .fromReaderAsync[Nothing, Int](Async.succeed(predicateSource: Reader[Int]))
                               .filterAsync(_ => Async.fail(predicateFailure))
                               .take(1)
                               .runCollectAsync
                           ).either
        predicateClosed <- run(predicateSource.isClosed)
      } yield assertTrue(
        sourceResult == Left("source"),
        sourceCloses.get == 1,
        predicateResult.left.exists(_ eq predicateFailure),
        predicateSource.specializedReads.get == 1,
        predicateClosed
      )
    },
    test("direct filtered take collect supports unprotected acquisition") {
      val callbacks = new AtomicInteger
      run(
        Stream
          .attemptAsync(Async.succeed(7))
          .filterAsync { value => callbacks.addAndGet(value); Async.succeed(true) }
          .take(1)
          .runCollectAsync
      ).map(result => assertTrue(result == Right(Chunk(7)), callbacks.get == 7))
    },
    test("direct filtered take collect resumes a pending physical read") {
      val source  = new NativePendingReader
      val running = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .filterAsync(value => Async.succeed(value >= 2))
        .take(2)
        .runCollectAsync
        .start
      source.resume()
      run(running).map(result => assertTrue(result == Right(Chunk(2, 3)), source.closes.get == 1))
    },
    test("direct filtered take collect combines an accepted resumed value with a continuation chunk") {
      val first   = new Completer[Long]
      val started = new Completer[Unit]
      val closes  = new AtomicInteger
      val reads   = new AtomicInteger
      val source  = new Reader.AsyncReader[Int] {
        def close(): Async[Unit]                  = { closes.incrementAndGet(); Async.succeed(()) }
        override def jvmType: JvmType             = JvmType.Int
        def isClosed: Async[Boolean]              = Async.succeed(closes.get > 0)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] =
          Async.fail(new AssertionError("generic read used for filtered native Int collect"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
          reads.incrementAndGet() match {
            case 1 => started.succeed(()); first
            case 2 => Async.succeed(2L)
            case 3 => Async.succeed(3L)
            case _ => Async.succeed(sentinel)
          }
      }
      val running = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .filterAsync(_ => Async.succeed(true))
        .take(3)
        .runCollectAsync
        .start
      for {
        _      <- run(started)
        _       = first.succeed(1L)
        result <- run(running)
      } yield assertTrue(result == Right(Chunk(1, 2, 3)), reads.get == 3, closes.get == 1)
    },
    test("direct filtered take collect covers ready builders, resumed chunks, pending EOF, and thrown reads") {
      def collect(source: Reader.AsyncReader[Int], limit: Long, predicate: Int => Async[Boolean]) =
        Stream
          .fromReaderAsync[String, Int](Async.succeed(source: Reader[Int]))
          .filterAsync(predicate)
          .take(limit)
          .runCollectAsync

      val oneThenPending = new Completer[Boolean]
      val oneThenStarted = new Completer[Unit]
      val oneThen        = collect(
        new HostileIntReader(1, 2, 3),
        3,
        value =>
          if (value == 2) { oneThenStarted.succeed(()); oneThenPending }
          else Async.succeed(true)
      ).start
      val twoThenPending = new Completer[Boolean]
      val twoThenStarted = new Completer[Unit]
      val twoThen        = collect(
        new HostileIntReader(1, 2, 3, 4),
        4,
        value =>
          if (value == 3) { twoThenStarted.succeed(()); twoThenPending }
          else Async.succeed(true)
      ).start
      val pendingEof                   = new NativePendingReader
      val eofResult                    = collect(pendingEof, 1, _ => Async.succeed(true)).start
      val ordinaryRead                 = new RuntimeException("filtered-take-thrown-read")
      val trustedRead                  = StreamError.source("filtered-take-thrown-typed-read")
      def throwing(failure: Throwable) = new Reader.AsyncReader[Int] {
        def close(): Async[Unit]                                                    = Async.succeed(())
        override def jvmType: JvmType                                               = JvmType.Int
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = throw failure
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = throw failure
      }
      for {
        ready    <- run(collect(new HostileIntReader(1, 2, 3), 10, _ => Async.succeed(true)))
        limited  <- run(collect(new HostileIntReader(1, 2, 3), 2, _ => Async.succeed(true)))
        _        <- run(oneThenStarted)
        _         = oneThenPending.succeed(true)
        one      <- run(oneThen)
        _        <- run(twoThenStarted)
        _         = twoThenPending.succeed(true)
        two      <- run(twoThen)
        _        <- run(pendingEof.started)
        _         = pendingEof.eof()
        eof      <- run(eofResult)
        ordinary <- run(collect(throwing(ordinaryRead), 1, _ => Async.succeed(true)).either)
        trusted  <- run(collect(throwing(trustedRead), 1, _ => Async.succeed(true)).either)
      } yield assertTrue(
        ready == Right(Chunk(1, 2, 3)),
        limited == Right(Chunk(1, 2)),
        one == Right(Chunk(1, 2, 3)),
        two == Right(Chunk(1, 2, 3, 4)),
        eof == Right(Chunk.empty),
        ordinary == Left(ordinaryRead),
        trusted == Right(Left("filtered-take-thrown-typed-read"))
      )
    },
    test("direct filtered take collect preserves failure after fairness and cleanup suppression") {
      val afterYield    = StreamError.source("filtered-take-after-yield")
      val yieldedCloses = new AtomicInteger
      val yielded       = new Reader.AsyncReader[Int] {
        private var reads                         = 0
        def close(): Async[Unit]                  = { yieldedCloses.incrementAndGet(); Async.succeed(()) }
        override def jvmType: JvmType             = JvmType.Int
        def isClosed: Async[Boolean]              = Async.succeed(yieldedCloses.get > 0)
        def read[A >: Int](sentinel: A): Async[A] =
          Async.fail(new AssertionError("generic read used for Int lane"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = {
          reads += 1
          if (reads <= 1024) Async.succeed(reads.toLong)
          else Async.failTrusted(afterYield)
        }
        def readable(): Async[Boolean] = Async.succeed(true)
      }
      val useFailure    = StreamError.source("filtered-take-use")
      val closeFailure  = new RuntimeException("filtered-take-close")
      val cleanupCloses = new AtomicInteger
      val cleanup       = new Reader.AsyncReader[Int] {
        def close(): Async[Unit] = {
          cleanupCloses.incrementAndGet()
          Async.fail(closeFailure)
        }
        override def jvmType: JvmType                                               = JvmType.Int
        def isClosed: Async[Boolean]                                                = Async.succeed(cleanupCloses.get > 0)
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.failTrusted(useFailure)
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = Async.failTrusted(useFailure)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
      }
      for {
        typed <- run(
                   Stream
                     .fromReaderAsync[String, Int](Async.succeed(yielded: Reader[Int]))
                     .filterAsync(_ => Async.succeed(false))
                     .take(1)
                     .runCollectAsync
                 )
        failed <- run(
                    Stream
                      .fromReaderAsync[String, Int](Async.succeed(cleanup: Reader[Int]))
                      .filterAsync(_ => Async.succeed(true))
                      .take(1)
                      .runCollectAsync
                      .either
                  )
      } yield assertTrue(
        typed == Left("filtered-take-after-yield"),
        yieldedCloses.get == 1,
        failed.left.exists(_ eq useFailure),
        useFailure.cleanupFailed,
        useFailure.getSuppressed.toList == List(closeFailure),
        cleanupCloses.get == 1
      )
    },
    test("cancelling direct filtered take collect joins its predicate and closes exactly once") {
      val source           = new HostileIntReader(1)
      val pendingPredicate = new CancelledRead
      val running          = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .filterAsync(_ => pendingPredicate.asInstanceOf[Async[Boolean]])
        .take(1)
        .runCollectAsync
        .start
      for {
        _                <- run(pendingPredicate.started)
        cleanup          <- ZIO.succeed(Async.cancelWithCleanup(running))
        waitsForPredicate = fold(cleanup).isInstanceOf[Pending[_]]
        _                 = pendingPredicate.cleanup.succeed(())
        _                <- run(cleanup)
        closed           <- run(source.isClosed)
      } yield assertTrue(
        waitsForPredicate,
        pendingPredicate.cancelled.get == 1,
        source.specializedReads.get == 1,
        closed
      )
    },
    test("foreach uses native Int reads and invokes each asynchronous callback exactly once") {
      val source  = new HostileIntReader(1, 2, 3)
      val seen    = new AtomicInteger
      val pending = new Completer[Unit]
      val running = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .runForeachAsync { value =>
          seen.addAndGet(value)
          if (value == 2) pending else Async.succeed(())
        }
        .start
      pending.succeed(())
      for {
        result <- run(running)
        closed <- run(source.isClosed)
      } yield assertTrue(
        result == Right(()),
        seen.get == 6,
        source.specializedReads.get == 4,
        closed
      )
    },
    test("direct native Int foreach supports unprotected acquisition") {
      val callbacks = new AtomicInteger
      run(
        Stream
          .attemptAsync(Async.succeed(7))
          .runForeachAsync { value => callbacks.addAndGet(value); Async.succeed(()) }
      ).map(result => assertTrue(result == Right(()), callbacks.get == 7))
    },
    test("direct native Int foreach preserves failures across fairness and cleanup") {
      val afterYield    = StreamError.source("foreach-after-yield")
      val yieldedCloses = new AtomicInteger
      val yielded       = new Reader.AsyncReader[Int] {
        private var reads                         = 0
        def close(): Async[Unit]                  = { yieldedCloses.incrementAndGet(); Async.succeed(()) }
        override def jvmType: JvmType             = JvmType.Int
        def isClosed: Async[Boolean]              = Async.succeed(yieldedCloses.get > 0)
        def read[A >: Int](sentinel: A): Async[A] =
          Async.fail(new AssertionError("generic read used for Int lane"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = {
          reads += 1
          if (reads <= 1024) Async.succeed(reads.toLong)
          else Async.failTrusted(afterYield)
        }
        def readable(): Async[Boolean] = Async.succeed(true)
      }
      val callbackSource  = new HostileIntReader((0 until 1026): _*)
      val callbackFailure = new StreamError("foreach-callback-after-yield")
      val useFailure      = StreamError.source("foreach-use")
      val closeFailure    = new RuntimeException("foreach-close")
      val cleanupCloses   = new AtomicInteger
      val cleanup         = new Reader.AsyncReader[Int] {
        def close(): Async[Unit] = {
          cleanupCloses.incrementAndGet()
          Async.fail(closeFailure)
        }
        override def jvmType: JvmType                                               = JvmType.Int
        def isClosed: Async[Boolean]                                                = Async.succeed(cleanupCloses.get > 0)
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.failTrusted(useFailure)
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = Async.failTrusted(useFailure)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
      }
      for {
        typed <- run(
                   Stream
                     .fromReaderAsync[String, Int](Async.succeed(yielded: Reader[Int]))
                     .runForeachAsync(_ => Async.succeed(()))
                 )
        callback <- run(
                      Stream
                        .fromReaderAsync[Nothing, Int](Async.succeed(callbackSource: Reader[Int]))
                        .runForeachAsync(value => if (value == 1024) throw callbackFailure else Async.succeed(()))
                        .either
                    )
        failed <- run(
                    Stream
                      .fromReaderAsync[String, Int](Async.succeed(cleanup: Reader[Int]))
                      .runForeachAsync(_ => Async.succeed(()))
                      .either
                  )
        callbackClosed <- run(callbackSource.isClosed)
      } yield assertTrue(
        typed == Left("foreach-after-yield"),
        yieldedCloses.get == 1,
        callback.left.exists {
          case error: StreamError =>
            (error ne callbackFailure) && error.value == "foreach-callback-after-yield" && !error.isTrusted
          case _ => false
        },
        callbackSource.specializedReads.get == 1025,
        callbackClosed,
        failed.left.exists(_ eq useFailure),
        useFailure.cleanupFailed,
        useFailure.getSuppressed.toList == List(closeFailure),
        cleanupCloses.get == 1
      )
    },
    test("cancelling direct native Int foreach joins its callback and closes exactly once") {
      val source          = new HostileIntReader(1)
      val pendingCallback = new CancelledRead
      val running         = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .runForeachAsync(_ => pendingCallback.asInstanceOf[Async[Unit]])
        .start
      for {
        _               <- run(pendingCallback.started)
        cleanup         <- ZIO.succeed(Async.cancelWithCleanup(running))
        waitsForCallback = fold(cleanup).isInstanceOf[Pending[_]]
        _                = pendingCallback.cleanup.succeed(())
        _               <- run(cleanup)
        closed          <- run(source.isClosed)
      } yield assertTrue(
        waitsForCallback,
        pendingCallback.cancelled.get == 1,
        source.specializedReads.get == 1,
        closed
      )
    },
    test("generic and specialized asynchronous folds and foreach") {
      val sum = new AtomicInteger
      for {
        i <- run(Stream(1, 2, 3).runFoldAsync(0)((z, a) => Async.succeed(z + a)))
        l <- run(Stream(1L, 2L).runFoldAsync(0L)((z, a) => Async.succeed(z + a)))
        d <- run(Stream(1.0, 2.0).runFoldAsync(0.0)((z, a) => Async.succeed(z + a)))
        r <- run(Stream("a", "b").runFoldAsync(List.empty[String])((z, a) => Async.succeed(a :: z)))
        _ <- run(Stream(1, 2, 3).foreachAsync(a => Async.succeed(sum.addAndGet(a)).map(_ => ())))
      } yield assertTrue(i == Right(6), l == Right(3L), d == Right(3.0), r == Right(List("b", "a")), sum.get == 6)
    },
    test("mapped asynchronous sink result follows a specialized Long fold exactly once") {
      val source         = new HostileIntReader(1, 2, 3)
      val defectSource   = new HostileIntReader(4)
      val foldCallbacks  = new AtomicInteger
      val mapCallbacks   = new AtomicInteger
      val pending        = new Completer[Long]
      val mappingFailure = new RuntimeException("sink map")
      val successSink    = Sink
        .foldLeft[Int, Long](0L) { (sum, value) => foldCallbacks.incrementAndGet(); sum + value }
        .mapAsync { value => mapCallbacks.incrementAndGet(); pending.map(_ + value) }
      val failureSink = Sink
        .foldLeft[Int, Long](0L)(_ + _)
        .mapAsync(_ => Async.fail(mappingFailure))
      val running = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .runAsync(successSink)
        .start
      pending.succeed(1L)
      for {
        success       <- run(running)
        successClosed <- run(source.isClosed)
        failed        <- run(
                    Stream
                      .fromReaderAsync[Nothing, Int](Async.succeed(defectSource: Reader[Int]))
                      .runAsync(failureSink)
                  ).either
        defectClosed <- run(defectSource.isClosed)
        typed        <- run(Stream.fail("typed").runAsync(successSink))
      } yield assertTrue(
        success == Right(7L),
        foldCallbacks.get == 3,
        mapCallbacks.get == 1,
        source.specializedReads.get == 4,
        successClosed,
        failed.left.exists(_ eq mappingFailure),
        defectSource.specializedReads.get == 2,
        defectClosed,
        typed == Left("typed"),
        mapCallbacks.get == 1
      )
    },
    test("direct mapped sink Long fold supports unprotected acquisition and pending physical reads") {
      val source       = new NativePendingReader
      val acquired     = new HostileIntReader(4)
      val acquisition  = new Completer[Reader[Int]]
      val mapCallbacks = new AtomicInteger
      val sink         = Sink
        .foldLeft[Int, Long](0L)(_ + _)
        .mapAsync { value => mapCallbacks.incrementAndGet(); Async.succeed(value + 1L) }
      val running = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .runAsync(sink)
        .start
      val acquiring = Stream
        .fromReaderAsync[Nothing, Int](acquisition)
        .runAsync(sink)
        .start
      for {
        _           <- run(source.started)
        _            = source.resume()
        _            = acquisition.succeed(acquired: Reader[Int])
        result      <- run(running)
        acquiredRun <- run(acquiring)
        unprotected <- run(Stream.attemptAsync(Async.succeed(2)).runAsync(sink))
      } yield assertTrue(
        result == Right(7L),
        source.closes.get == 1,
        acquiredRun == Right(5L),
        acquired.specializedReads.get == 2,
        unprotected == Right(3L),
        mapCallbacks.get == 3
      )
    },
    test("direct mapped sink Long fold cancellation joins its result effect before closing") {
      val pendingMap = new CancelledRead
      val closes     = new AtomicInteger
      val reader     = new Reader.AsyncReader[Int] {
        private var emitted                       = false
        def close(): Async[Unit]                  = { closes.incrementAndGet(); Async.succeed(()) }
        override def jvmType: JvmType             = JvmType.Int
        def isClosed: Async[Boolean]              = Async.succeed(closes.get > 0)
        def readable(): Async[Boolean]            = Async.succeed(!emitted)
        def read[A >: Int](sentinel: A): Async[A] =
          Async.fail(new AssertionError("generic read used for Int lane"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
          if (emitted) Async.succeed(sentinel)
          else { emitted = true; Async.succeed(1L) }
      }
      val sink = Sink
        .foldLeft[Int, Long](0L)(_ + _)
        .mapAsync(_ => pendingMap.asInstanceOf[Async[Long]])
      val running = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(reader: Reader[Int]))
        .runAsync(sink)
        .start
      for {
        _              <- run(pendingMap.started)
        cleanup        <- ZIO.succeed(Async.cancelWithCleanup(running))
        waitsForMap     = fold(cleanup).isInstanceOf[Pending[_]]
        closedBeforeMap = closes.get
        _               = pendingMap.cleanup.succeed(())
        _              <- run(cleanup)
      } yield assertTrue(waitsForMap, closedBeforeMap == 0, pendingMap.cancelled.get == 1, closes.get == 1)
    },
    test("direct mapped sink Long fold preserves fairness failures and cleanup suppression") {
      val forged       = new StreamError("sink-fold-after-yield")
      val closeFailure = new RuntimeException("sink-fold-close")
      val closes       = new AtomicInteger
      val reader       = new Reader.AsyncReader[Int] {
        private var next                          = 0
        def close(): Async[Unit]                  = { closes.incrementAndGet(); Async.fail(closeFailure) }
        override def jvmType: JvmType             = JvmType.Int
        def isClosed: Async[Boolean]              = Async.succeed(closes.get > 0)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] =
          Async.fail(new AssertionError("generic read used for Int lane"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = {
          val value = next
          next += 1
          Async.succeed(value.toLong)
        }
      }
      val sink = Sink
        .foldLeft[Int, Long](0L)((sum, value) => if (value == 1024) throw forged else sum + value)
        .mapAsync(value => Async.succeed(value))
      for {
        result <- run(
                    Stream
                      .fromReaderAsync[Nothing, Int](Async.succeed(reader: Reader[Int]))
                      .runAsync(sink)
                      .either
                  )
      } yield assertTrue(
        result.left.exists {
          case error: StreamError =>
            (error ne forged) && error.value == "sink-fold-after-yield" && !error.isTrusted &&
            error.cleanupFailed && error.getSuppressed.toList == List(closeFailure)
          case _ => false
        },
        closes.get == 1
      )
    },
    test("direct mapped sink Long fold preserves typed failures after fairness and result callback provenance") {
      val sourceFailure = StreamError.source("sink-source-after-yield")
      val sourceCloses  = new AtomicInteger
      val source        = new Reader.AsyncReader[Int] {
        private var reads                         = 0
        def close(): Async[Unit]                  = { sourceCloses.incrementAndGet(); Async.succeed(()) }
        override def jvmType: JvmType             = JvmType.Int
        def isClosed: Async[Boolean]              = Async.succeed(sourceCloses.get > 0)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] =
          Async.fail(new AssertionError("generic read used for Int lane"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = {
          reads += 1
          if (reads <= 1024) Async.succeed(reads.toLong)
          else Async.failTrusted(sourceFailure)
        }
      }
      val forged      = new StreamError("sink-result-map")
      val mapSource   = new HostileIntReader()
      val successSink = Sink
        .foldLeft[Int, Long](0L)(_ + _)
        .mapAsync(value => Async.succeed(value))
      val forgedSink = Sink
        .foldLeft[Int, Long](0L)(_ + _)
        .mapAsync(_ => throw forged)
      for {
        typed <- run(
                   Stream
                     .fromReaderAsync[String, Int](Async.succeed(source: Reader[Int]))
                     .runAsync(successSink)
                 )
        defect <- run(
                    Stream
                      .fromReaderAsync[Nothing, Int](Async.succeed(mapSource: Reader[Int]))
                      .runAsync(forgedSink)
                      .either
                  )
      } yield assertTrue(
        typed == Left("sink-source-after-yield"),
        sourceCloses.get == 1,
        defect.left.exists {
          case error: StreamError => (error ne forged) && error.value == "sink-result-map" && !error.isTrusted
          case _                  => false
        },
        mapSource.specializedReads.get == 1
      )
    },
    test("mapped native asynchronous Int continuation covers ready, pending, failed, and fairness paths") {
      def continuation(
        reader: Reader.AsyncReader[Int],
        map: Int => Async[Int] = value => Async.succeed(value),
        fold: (Long, Int) => Async[Long] = (sum, value) => Async.succeed(sum + value)
      ): Async[Either[Nothing, Long]] =
        new MappedNativeAsyncIntEitherLongFold[Nothing](reader, map, 0L, fold)

      def failingReader(failure: Throwable, trusted: Boolean) = new Reader.AsyncReader[Int] {
        def close(): Async[Unit]                                                    = Async.succeed(())
        override def jvmType: JvmType                                               = JvmType.Int
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.fail(new AssertionError("generic read"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
          if (trusted) Async.failTrusted(failure) else Async.fail(failure)
      }

      val readFailure       = new RuntimeException("mapped-continuation-read")
      val trustedRead       = StreamError.source("mapped-continuation-typed-read")
      val thrownRead        = new RuntimeException("mapped-continuation-thrown-read")
      val thrownTrustedRead = StreamError.source("mapped-continuation-thrown-typed-read")
      val throwingReader    = (failure: Throwable) =>
        new Reader.AsyncReader[Int] {
          def close(): Async[Unit]                                                    = Async.succeed(())
          override def jvmType: JvmType                                               = JvmType.Int
          def isClosed: Async[Boolean]                                                = Async.succeed(false)
          def readable(): Async[Boolean]                                              = Async.succeed(true)
          def read[A >: Int](sentinel: A): Async[A]                                   = throw failure
          override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = throw failure
        }
      val mapFailure         = StreamError.source("mapped-continuation-map")
      val foldFailure        = StreamError.source("mapped-continuation-fold")
      val trustedMapFailure  = new RuntimeException("mapped-continuation-trusted-map")
      val trustedFoldFailure = new RuntimeException("mapped-continuation-trusted-fold")
      val pendingRead        = new NativePendingReader
      val mapGate            = new Completer[Int]
      val foldGate           = new Completer[Long]
      val mappedPending      = continuation(new HostileIntReader(1), _ => mapGate).start
      val foldedPending      = continuation(
        new HostileIntReader(1),
        fold = (_, _) => foldGate
      ).start
      val readPending = continuation(pendingRead).start
      for {
        ready            <- run(continuation(new HostileIntReader(1, 2, 3)))
        readFailed       <- run(continuation(failingReader(readFailure, trusted = false)).either)
        readTrusted      <- run(continuation(failingReader(trustedRead, trusted = true)).either)
        readThrown       <- run(continuation(throwingReader(thrownRead)).either)
        trustedThrown    <- run(continuation(throwingReader(thrownTrustedRead)).either)
        mapFailed        <- run(continuation(new HostileIntReader(1), _ => Async.failTrusted(mapFailure)).either)
        trustedMapFailed <- run(
                              continuation(
                                new HostileIntReader(1),
                                _ => Async.failTrusted(trustedMapFailure)
                              ).either
                            )
        foldFailed <- run(
                        continuation(
                          new HostileIntReader(1),
                          fold = (_, _) => Async.failTrusted(foldFailure)
                        ).either
                      )
        trustedFoldFailed <- run(
                               continuation(
                                 new HostileIntReader(1),
                                 fold = (_, _) => Async.failTrusted(trustedFoldFailure)
                               ).either
                             )
        _              = mapGate.succeed(2)
        mappedResult  <- run(mappedPending)
        _              = foldGate.succeed(3L)
        foldedResult  <- run(foldedPending)
        _             <- run(pendingRead.started)
        _              = pendingRead.resume()
        pendingResult <- run(readPending)
        fairResult    <- run(continuation(new HostileIntReader((0 until 1025): _*)))
      } yield assertTrue(
        ready == Right(6L),
        readFailed == Left(readFailure),
        readTrusted == Left(trustedRead),
        readThrown == Left(thrownRead),
        trustedThrown == Left(thrownTrustedRead),
        mapFailed.left.exists {
          case error: StreamError => error.value == mapFailure.value && !error.isTrusted
          case _                  => false
        },
        trustedMapFailed == Left(trustedMapFailure),
        foldFailed.left.exists {
          case error: StreamError => error.value == foldFailure.value && !error.isTrusted
          case _                  => false
        },
        trustedFoldFailed == Left(trustedFoldFailure),
        mappedResult == Right(2L),
        foldedResult == Right(3L),
        pendingResult == Right(6L),
        fairResult == Right((0 until 1025).map(_.toLong).sum)
      )
    },
    test("direct native asynchronous concat materializes fresh children and closes before transition") {
      val firstAcquires                                                                                   = new AtomicInteger
      val secondAcquires                                                                                  = new AtomicInteger
      val firstCloses                                                                                     = new AtomicInteger
      val secondCloses                                                                                    = new AtomicInteger
      def child(acquires: AtomicInteger, closeCounter: AtomicInteger, values: Int*): Stream[Nothing, Int] =
        Stream.fromReaderAsync[Nothing, Int] {
          acquires.incrementAndGet()
          Async.succeed(new SkipCapableIntReader(Async.succeed(false), values: _*) {
            override def close(): Async[Unit] = { closeCounter.incrementAndGet(); super.close() }
          }: Reader[Int])
        }
      val stream = child(firstAcquires, firstCloses, 1, 2) ++ child(secondAcquires, secondCloses, 3, 4)
      for {
        first  <- run(stream.runFoldAsync(0L)((sum, value) => Async.succeed(sum + value)))
        second <- run(stream.runFoldAsync(0L)((sum, value) => Async.succeed(sum + value)))
      } yield assertTrue(
        first == Right(10L),
        second == Right(10L),
        firstAcquires.get == 2,
        secondAcquires.get == 2,
        firstCloses.get == 2,
        secondCloses.get == 2
      )
    },
    test("direct native asynchronous concat transitions to an unprotected asynchronous source") {
      val first  = new HostileIntReader(1)
      val stream =
        Stream.fromReaderAsync[Throwable, Int](Async.succeed(first: Reader[Int])) ++
          Stream.attemptAsync(Async.succeed(2))
      for {
        result      <- run(stream.runFoldAsync(0L)((sum, value) => Async.succeed(sum + value)))
        firstClosed <- run(first.isClosed)
      } yield assertTrue(result == Right(3L), firstClosed)
    },
    test("direct native asynchronous concat protects acquisition throws") {
      val failure = new StreamError("concat acquisition")
      val stream  =
        Stream.fromReaderAsync[Nothing, Int](throw failure) ++
          Stream.fromReaderAsync[Nothing, Int](Async.succeed(new HostileIntReader(1): Reader[Int]))
      run(stream.runFoldAsync(0L)((sum, value) => Async.succeed(sum + value)).either).map { result =>
        assertTrue(result.left.exists {
          case error: StreamError => error.value == "concat acquisition" && !error.isTrusted
          case _                  => false
        })
      }
    },
    test("cancelling direct native asynchronous concat during acquisition closes the acquired reader") {
      val reader    = new HostileIntReader(1)
      val acquire   = new CancelOnPoll[Reader[Int]](Async.succeed(reader: Reader[Int]))
      var running   = null.asInstanceOf[Async.Running[Either[Nothing, Long]]]
      var cleanup   = null.asInstanceOf[Async[Unit]]
      val cancelled = new Completer[Unit]
      running = (
        Stream.fromReaderAsync[Nothing, Int](acquire) ++
          Stream.fromReaderAsync[Nothing, Int](Async.succeed(new HostileIntReader(2): Reader[Int]))
      ).runFoldAsync(0L)((sum, value) => Async.succeed(sum + value)).start
      for {
        _      <- run(acquire.started)
        _       = acquire.arm { () => cleanup = Async.cancelWithCleanup(running); cancelled.succeed(()) }
        _      <- run(cancelled)
        _      <- run(cleanup)
        closed <- run(reader.isClosed)
      } yield assertTrue(closed)
    },
    test("direct native asynchronous concat preserves pending transition, fold, and typed failure semantics") {
      val first          = new NativePendingReader
      val second         = new SkipCapableIntReader(Async.succeed(false), 4, 5)
      val secondAcquires = new AtomicInteger
      val callbacks      = new AtomicInteger
      val pendingFold    = new Completer[Long]
      val success        = (
        Stream.fromReaderAsync[Nothing, Int](Async.succeed(first: Reader[Int])) ++
          Stream.fromReaderAsync[Nothing, Int] {
            secondAcquires.incrementAndGet()
            Async.succeed(second: Reader[Int])
          }
      ).runFoldAsync(0L) { (sum, value) =>
        callbacks.incrementAndGet()
        if (value == 4) pendingFold else Async.succeed(sum + value)
      }.start
      val failingCloses = new AtomicInteger
      val failing       = new Reader.AsyncReader[Int] {
        override def jvmType: JvmType                                               = JvmType.Int
        def close(): Async[Unit]                                                    = { failingCloses.incrementAndGet(); Async.succeed(()) }
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.failTrusted(StreamError.source("concat"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
          Async.failTrusted(StreamError.source("concat"))
      }
      val rejectedAcquires = new AtomicInteger
      val failed           = (
        Stream.fromReaderAsync[String, Int](Async.succeed(failing: Reader[Int])) ++
          Stream.fromReaderAsync[String, Int] {
            rejectedAcquires.incrementAndGet()
            Async.succeed(new HostileIntReader(9): Reader[Int])
          }
      ).runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
      val beforeResume = secondAcquires.get
      first.resume()
      pendingFold.succeed(10L)
      for {
        result       <- run(success)
        failedResult <- run(failed)
      } yield assertTrue(
        beforeResume == 0,
        result == Right(15L),
        callbacks.get == 5,
        first.closes.get == 1,
        secondAcquires.get == 1,
        second.closes.get == 1,
        failedResult == Left("concat"),
        failingCloses.get == 1,
        rejectedAcquires.get == 0
      )
    },
    test("direct native asynchronous concat preserves synchronous physical-read failures") {
      def throwing(failure: Throwable) = new Reader.AsyncReader[Int] {
        override def jvmType: JvmType                                               = JvmType.Int
        def close(): Async[Unit]                                                    = Async.succeed(())
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = throw failure
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = throw failure
      }
      def folded[E](failure: Throwable) = (
        Stream.fromReaderAsync[E, Int](Async.succeed(throwing(failure): Reader[Int])) ++
          Stream.fromReaderAsync[E, Int](Async.succeed(new HostileIntReader(1): Reader[Int]))
      ).runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
      val ordinary = new IllegalStateException("concat synchronous read")
      val trusted  = StreamError.source("concat synchronous trusted read")
      for {
        ordinaryResult <- run(folded[Nothing](ordinary).either)
        trustedResult  <- run(folded[String](trusted))
      } yield assertTrue(ordinaryResult == Left(ordinary), trustedResult == Left("concat synchronous trusted read"))
    },
    test("direct native asynchronous concat preserves trusted non-stream failures") {
      val failure = new IllegalStateException("concat trusted defect")
      val reader  = new Reader.AsyncReader[Int] {
        override def jvmType: JvmType                                               = JvmType.Int
        def close(): Async[Unit]                                                    = Async.succeed(())
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.failTrusted(failure)
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = Async.failTrusted(failure)
      }
      val stream =
        Stream.fromReaderAsync[Nothing, Int](Async.succeed(reader: Reader[Int])) ++
          Stream.fromReaderAsync[Nothing, Int](Async.succeed(new HostileIntReader(1): Reader[Int]))
      run(stream.runFoldAsync(0L)((sum, value) => Async.succeed(sum + value)).either).map(result =>
        assertTrue(result == Left(failure))
      )
    },
    test("direct native asynchronous concat preserves fold defects after a fairness yield") {
      val first          = new HostileIntReader((0 until 1026): _*)
      val secondAcquires = new AtomicInteger
      val forged         = new StreamError("concat-fold-after-yield")
      val effect         = (
        Stream.fromReaderAsync[Nothing, Int](Async.succeed(first: Reader[Int])) ++
          Stream.fromReaderAsync[Nothing, Int] {
            secondAcquires.incrementAndGet()
            Async.succeed(new HostileIntReader(1026): Reader[Int])
          }
      ).runFoldAsync(0L) { (sum, value) =>
        if (value == 1024) throw forged else Async.succeed(sum + value)
      }.either
      for {
        result <- run(effect)
        closed <- run(first.isClosed)
      } yield assertTrue(
        result.left.exists {
          case error: StreamError =>
            (error ne forged) && error.value == "concat-fold-after-yield" && !error.isTrusted
          case _ => false
        },
        first.specializedReads.get == 1025,
        closed,
        secondAcquires.get == 0
      )
    },
    test("direct native asynchronous concat traverses deep trees stack safely and rematerializes every leaf") {
      val depth                      = 10000
      val acquires                   = new AtomicInteger
      val leafCloses                 = new AtomicInteger
      def leaf: Stream[Nothing, Int] = Stream.fromReaderAsync[Nothing, Int] {
        acquires.incrementAndGet()
        Async.succeed(new SkipCapableIntReader(Async.succeed(false), 1) {
          override def close(): Async[Unit] = { leafCloses.incrementAndGet(); super.close() }
        }: Reader[Int])
      }
      var stream = leaf
      var index  = 0
      while (index < depth) { stream = stream ++ leaf; index += 1 }
      for {
        first  <- run(stream.runFoldAsync(0L)((sum, value) => Async.succeed(sum + value)))
        second <- run(stream.runFoldAsync(0L)((sum, value) => Async.succeed(sum + value)))
      } yield assertTrue(
        first == Right(depth.toLong + 1L),
        second == Right(depth.toLong + 1L),
        acquires.get == (depth + 1) * 2,
        leafCloses.get == (depth + 1) * 2
      )
    },
    test("direct native asynchronous concat preserves ordering and ownership for every tree shape") {
      val acquires                               = new AtomicInteger
      val leafCloses                             = new AtomicInteger
      def leaf(value: Int): Stream[Nothing, Int] = Stream.fromReaderAsync[Nothing, Int] {
        acquires.incrementAndGet()
        Async.succeed(new SkipCapableIntReader(Async.succeed(false), value) {
          override def close(): Async[Unit] = { leafCloses.incrementAndGet(); super.close() }
        }: Reader[Int])
      }
      def balanced(streams: Vector[Stream[Nothing, Int]]): Stream[Nothing, Int] =
        if (streams.length == 1) streams.head
        else {
          val (left, right) = streams.splitAt(streams.length >>> 1)
          balanced(left) ++ balanced(right)
        }
      val leaves                                                               = (1 to 17).map(leaf).toVector
      val leftDeep                                                             = leaves.tail.foldLeft(leaves.head)(_ ++ _)
      val rightDeep                                                            = leaves.init.foldRight(leaves.last)(_ ++ _)
      val expected                                                             = (1 to 17).foldLeft(0L)((hash, value) => hash * 31L + value)
      def evaluate(stream: Stream[Nothing, Int]): Async[Either[Nothing, Long]] =
        stream.runFoldAsync(0L)((hash, value) => Async.succeed(hash * 31L + value))
      for {
        left   <- run(evaluate(leftDeep))
        right  <- run(evaluate(rightDeep))
        branch <- run(evaluate(balanced(leaves)))
      } yield assertTrue(
        left == Right(expected),
        right == Right(expected),
        branch == Right(expected),
        acquires.get == leaves.length * 3,
        leafCloses.get == leaves.length * 3
      )
    },
    test("direct native asynchronous flatMap handles arbitrary children and closes them before advancing") {
      val outer         = new HostileIntReader(1, 2, 3)
      val childCloses   = new AtomicInteger
      val childAcquires = new AtomicInteger
      val stream        = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(outer: Reader[Int]))
        .flatMap { value =>
          if (value == 2) Stream.empty
          else
            Stream.fromReaderAsync[Nothing, Int] {
              childAcquires.incrementAndGet()
              Async.succeed(
                new SkipCapableIntReader(Async.succeed(false), value, value + 10) {
                  override def close(): Async[Unit] = {
                    childCloses.incrementAndGet()
                    super.close()
                  }
                }: Reader[Int]
              )
            }
        }
      for {
        result <- run(stream.runFoldAsync(0L)((sum, value) => Async.succeed(sum + value)))
      } yield assertTrue(
        result == Right(28L),
        outer.specializedReads.get == 4,
        childAcquires.get == 2,
        childCloses.get == 2
      )
    },
    test("direct native asynchronous flatMap fuses a mapped outer while preserving pending reads and close order") {
      val outer      = new GatedIntReader(1, 1, 2)
      val lifecycle  = new StringBuilder
      val childCount = new AtomicInteger
      val stream     = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(outer: Reader[Int]))
        .map { value => lifecycle.append(s"map$value;"); value + 10 }
        .flatMap { value =>
          lifecycle.append(s"expand$value;")
          Stream.fromReaderAsync[Nothing, Int] {
            childCount.incrementAndGet()
            Async.succeed(new SkipCapableIntReader(Async.succeed(false), value, value + 1) {
              override def close(): Async[Unit] = {
                lifecycle.append(s"close$value;")
                super.close()
              }
            }: Reader[Int])
          }
        }
      val running = stream.runFoldAsync(0L)((sum, value) => Async.succeed(sum * 31L + value)).start
      for {
        _           <- run(outer.gateStarted)
        _            = outer.resume()
        result      <- run(running)
        outerClosed <- run(outer.isClosed)
      } yield assertTrue(
        result == Right((((0L * 31L + 11L) * 31L + 12L) * 31L + 12L) * 31L + 13L),
        lifecycle.result() == "map1;expand11;close11;map2;expand12;close12;",
        childCount.get == 2,
        outer.specializedReads.get == 3,
        outerClosed
      )
    },
    test("direct native asynchronous flatMap scalar-folds ready and pending unwrapped singleton children") {
      val outer       = new HostileIntReader(1, 2, 3)
      val evaluations = new AtomicInteger
      val pending     = new Completer[Stream[Nothing, Int]]
      val running     = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(outer: Reader[Int]))
        .flatMap { value =>
          Stream.unwrap {
            evaluations.incrementAndGet()
            if (value == 2) pending else Async.succeed(Stream.succeed(value))
          }
        }
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      def awaitPending: ZIO[Any, Nothing, Unit] =
        if (evaluations.get >= 2) ZIO.unit else ZIO.yieldNow *> ZIO.suspendSucceed(awaitPending)
      for {
        _           <- awaitPending
        _            = pending.succeed(Stream.unwrap(Async.succeed(Stream.succeed(2))))
        result      <- run(running)
        outerClosed <- run(outer.isClosed)
      } yield assertTrue(
        result == Right(6L),
        evaluations.get == 3,
        outer.specializedReads.get == 4,
        outerClosed
      )
    },
    test("direct native asynchronous flatMap resumes a pending outer read into an arbitrary unwrapped child") {
      val outer   = new GatedIntReader(1, 1)
      val running = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(outer: Reader[Int]))
        .flatMap(_ => Stream.unwrap(Async.succeed(Stream(2, 3))))
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      for {
        _           <- run(outer.gateStarted)
        _            = outer.resume()
        result      <- run(running)
        outerClosed <- run(outer.isClosed)
      } yield assertTrue(result == Right(5L), outer.specializedReads.get == 2, outerClosed)
    },
    test("direct native asynchronous flatMap releases an acquisition that wins a cancellation race") {
      val acquiredReader       = new HostileIntReader(1)
      val acquisition          = new CancelOnPoll[Reader[Int]](Async.succeed(acquiredReader: Reader[Int]))
      val cancelled            = new Completer[Unit]
      var cleanup: Async[Unit] = null
      val running              = Stream
        .fromReaderAsync[Nothing, Int](acquisition)
        .flatMap(Stream.succeed)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      for {
        _ <- run(acquisition.started)
        _  = acquisition.arm { () =>
              cleanup = Async.cancelWithCleanup(running)
              cancelled.succeed(())
            }
        _      <- run(cancelled)
        _      <- run(cleanup)
        closed <- run(acquiredReader.isClosed)
      } yield assertTrue(closed)
    },
    test("Int terminal routes release a synchronous acquisition that wins a cancellation race") {
      val routes: List[(String, Stream[Nothing, Int] => Async[Any])] = List(
        "collect"         -> (_.runCollectAsync.asInstanceOf[Async[Any]]),
        "drain"           -> (_.runDrainAsync.asInstanceOf[Async[Any]]),
        "filtered-take"   -> (_.filterAsync(_ => Async.succeed(true)).take(1).runCollectAsync.asInstanceOf[Async[Any]]),
        "mapped-int-fold" -> (_.map(_ > 0)
          .runFoldAsync(0)((acc, value) => Async.succeed(acc + value.hashCode))
          .asInstanceOf[Async[Any]]),
        "long-fold"           -> (_.runFoldAsync(0L)((acc, value) => Async.succeed(acc + value)).asInstanceOf[Async[Any]]),
        "async-map-long-fold" -> (_.mapAsync(Async.succeed)
          .runFoldAsync(0L)((acc, value) => Async.succeed(acc + value))
          .asInstanceOf[Async[Any]]),
        "filtered-long-fold" -> (_.filterAsync(_ => Async.succeed(true))
          .runFoldAsync(0L)((acc, value) => Async.succeed(acc + value))
          .asInstanceOf[Async[Any]]),
        "take-drop-long-fold" -> (_.drop(1)
          .take(1)
          .runFoldAsync(0L)((acc, value) => Async.succeed(acc + value))
          .asInstanceOf[Async[Any]]),
        "taken-long-fold" -> (_.take(1)
          .runFoldAsync(0L)((acc, value) => Async.succeed(acc + value))
          .asInstanceOf[Async[Any]]),
        "take-while-long-fold" -> (_.takeWhile(_ => true)
          .runFoldAsync(0L)((acc, value) => Async.succeed(acc + value))
          .asInstanceOf[Async[Any]])
      )
      ZIO
        .foreach(routes) { case (name, route) =>
          val acquiredReader       = Reader.singleInt(1).withRelease(() => ())
          val acquisition          = new CancelOnPoll[Reader[Int]](Async.succeed(acquiredReader: Reader[Int]))
          val cancelled            = new Completer[Unit]
          var cleanup: Async[Unit] = null
          val running              = route(Stream.fromReaderAsync[Nothing, Int](acquisition)).start
          for {
            _ <- run(acquisition.started)
            _  = acquisition.arm { () =>
                  cleanup = Async.cancelWithCleanup(running)
                  cancelled.succeed(())
                }
            _     <- run(cancelled)
            _     <- run(cleanup)
            closed = acquiredReader.isClosed
          } yield assertTrue(closed).label(name)
        }
        .map(results => results.reduce(_ && _))
    },
    test("direct native asynchronous flatMap cancellation joins a pending unwrapped child") {
      val outer   = new HostileIntReader(1)
      val pending = new CancelledRead
      val running = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(outer: Reader[Int]))
        .flatMap(_ => Stream.unwrap(pending.asInstanceOf[Async[Stream[Nothing, Int]]]))
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      for {
        _           <- run(pending.started)
        cleanup      = Async.cancelWithCleanup(running)
        waiting      = fold(cleanup).isInstanceOf[Pending[_]]
        _            = pending.cleanup.succeed(())
        _           <- run(cleanup)
        outerClosed <- run(outer.isClosed)
      } yield assertTrue(waiting, pending.cancelled.get == 1, outerClosed)
    },
    test("direct native asynchronous flatMap preserves typed failure from an unwrapped child") {
      val outer       = new HostileIntReader(1, 2)
      val evaluations = new AtomicInteger
      val stream      = Stream
        .fromReaderAsync[String, Int](Async.succeed(outer: Reader[Int]))
        .flatMap { value =>
          Stream.unwrap {
            evaluations.incrementAndGet()
            Async.succeed(if (value == 1) Stream.succeed(value) else Stream.fail("unwrapped-child"))
          }
        }
      for {
        result      <- run(stream.runFoldAsync(0L)((sum, value) => Async.succeed(sum + value)))
        outerClosed <- run(outer.isClosed)
      } yield assertTrue(
        result == Left("unwrapped-child"),
        evaluations.get == 2,
        outer.specializedReads.get == 2,
        outerClosed
      )
    },
    test("direct native asynchronous flatMap classifies acquisition, read, fold, and close failures") {
      def reader(
        read0: Long => Async[Long],
        close0: () => Async[Unit] = () => Async.succeed(())
      ): Reader.AsyncReader[Int] = new Reader.AsyncReader[Int] {
        override def jvmType: JvmType                                               = JvmType.Int
        def close(): Async[Unit]                                                    = close0()
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.fail(new AssertionError("generic read"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = read0(sentinel)
      }
      def evaluate[E](
        source: => Async[Reader[Int]],
        fold: (Long, Int) => Async[Long] = (sum, value) => Async.succeed(sum + value)
      ): Async[Either[Throwable, Either[E, Long]]] =
        Stream
          .fromReaderAsync[E, Int](source)
          .flatMap(Stream.succeed)
          .runFoldAsync(0L)(fold)
          .either

      val acquisitionThrow   = StreamError.source("flatMap-acquisition-throw")
      val acquisitionFailure = StreamError.source("flatMap-acquisition-failure")
      val readThrow          = new RuntimeException("flatMap-read-throw")
      val readFailure        = new RuntimeException("flatMap-read-failure")
      val trustedRead        = StreamError.source("flatMap-typed-read")
      val trustedDefect      = new RuntimeException("flatMap-trusted-defect")
      val foldFailure        = new StreamError("flatMap-fold-failure")
      val closeThrow         = new RuntimeException("flatMap-close-throw")
      val closeFailure       = new RuntimeException("flatMap-close-failure")
      var emitted            = false
      val useAndClose        = reader(
        sentinel =>
          if (!emitted) { emitted = true; Async.succeed(1L) }
          else Async.succeed(sentinel),
        () => Async.fail(closeFailure)
      )
      for {
        acquisitionThrown <- run(evaluate[String](throw acquisitionThrow))
        acquisitionFailed <- run(evaluate[String](Async.failTrusted(acquisitionFailure)))
        readThrown        <- run(evaluate[Nothing](Async.succeed(reader(_ => throw readThrow): Reader[Int])))
        readFailed        <- run(evaluate[Nothing](Async.succeed(reader(_ => Async.fail(readFailure)): Reader[Int])))
        readThrownTrusted <- run(evaluate[String](Async.succeed(reader(_ => throw trustedRead): Reader[Int])))
        readTrusted       <- run(evaluate[String](Async.succeed(reader(_ => Async.failTrusted(trustedRead)): Reader[Int])))
        readTrustedDefect <-
          run(
            evaluate[Nothing](Async.succeed(reader(_ => Async.failTrusted(trustedDefect)): Reader[Int]))
          )
        foldFailed <- run(
                        evaluate[Nothing](
                          Async.succeed(new HostileIntReader(1): Reader[Int]),
                          (_, _) => Async.failTrusted(foldFailure)
                        )
                      )
        closeFailed <-
          run(
            evaluate[Nothing](
              Async.succeed(reader(sentinel => Async.succeed(sentinel), () => Async.fail(closeFailure)): Reader[Int])
            )
          )
        closeThrown <-
          run(
            evaluate[Nothing](
              Async.succeed(reader(sentinel => Async.succeed(sentinel), () => throw closeThrow): Reader[Int])
            )
          )
        useAndCloseFailed <-
          run(
            evaluate[Nothing](Async.succeed(useAndClose: Reader[Int]), (_, _) => Async.fail(foldFailure))
          )
        syncAcquired <- run(
                          evaluate[Nothing](Async.succeed(Reader.fromIterable(List(1, 2)): Reader[Int]))
                        )
      } yield assertTrue(
        acquisitionThrown.left.exists {
          case error: StreamError =>
            (error ne acquisitionThrow) && error.value == acquisitionThrow.value && !error.isTrusted
          case _ => false
        },
        acquisitionFailed.left.exists {
          case error: StreamError =>
            (error ne acquisitionFailure) && error.value == acquisitionFailure.value && !error.isTrusted
          case _ => false
        },
        readThrown == Left(readThrow),
        readFailed == Left(readFailure),
        readThrownTrusted == Right(Left("flatMap-typed-read")),
        readTrusted == Right(Left("flatMap-typed-read")),
        readTrustedDefect == Left(trustedDefect),
        foldFailed.left.exists {
          case error: StreamError => (error ne foldFailure) && error.value == foldFailure.value && !error.isTrusted
          case _                  => false
        },
        closeFailed == Left(closeFailure),
        closeThrown == Left(closeThrow),
        useAndCloseFailed.left.exists {
          case error: StreamError =>
            error.value == foldFailure.value && error.cleanupFailed && error.getSuppressed.toList == List(closeFailure)
          case _ => false
        },
        syncAcquired == Right(Right(3L))
      )
    },
    test("direct native asynchronous flatMap covers pending and immediate child fold shapes") {
      def evaluate[E](child: Stream[E, Int], fold: (Long, Int) => Async[Long]): Async[Either[E, Long]] =
        Stream
          .fromReaderAsync[Nothing, Int](Async.succeed(new HostileIntReader(1): Reader[Int]))
          .flatMap(_ => child)
          .runFoldAsync(0L)(fold)

      val singletonGate        = new Completer[Long]
      val unwrappedGate        = new Completer[Long]
      val unwrappedFailure     = StreamError.source("flatMap-unwrapped-acquisition")
      val unwrappedFoldFailure = new RuntimeException("flatMap-unwrapped-fold")
      val mappedFoldFailure    = new RuntimeException("flatMap-mapped-fold")
      val singletonRunning     = evaluate(Stream.succeed(1), (_, _) => singletonGate).start
      val unwrappedRunning     = evaluate(Stream.unwrap(Async.succeed(Stream.succeed(1))), (_, _) => unwrappedGate).start
      val unwrappedFailed      = evaluate(
        Stream.unwrap[String, Int](Async.failTrusted(unwrappedFailure)),
        (_, _) => Async.succeed(0L)
      ).either
      val unwrappedFoldFailed =
        evaluate(Stream.unwrap(Async.succeed(Stream.succeed(1))), (_, _) => Async.fail(unwrappedFoldFailure))
      val unwrappedMappedReady = evaluate(
        Stream.unwrap(Async.succeed(Stream.succeed(1).map(_ + 1))),
        (sum, value) => Async.succeed(sum + value)
      )
      val mappedReady      = evaluate(Stream.succeed(1).map(_ + 1), (sum, value) => Async.succeed(sum + value))
      val mappedFailed     = evaluate(Stream.succeed(1).map(_ + 1), (_, _) => Async.fail(mappedFoldFailure)).either
      val unprotectedChild = evaluate(
        Stream.attemptAsync(Async.succeed(2)),
        (sum, value) => Async.succeed(sum + value)
      )
      val unprotectedOuter = Stream
        .attemptAsync(Async.succeed(2))
        .flatMap(Stream.succeed)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
      singletonGate.succeed(1L)
      unwrappedGate.succeed(1L)
      for {
        singleton             <- run(singletonRunning)
        unwrapped             <- run(unwrappedRunning)
        unwrappedAcquireError <- run(unwrappedFailed)
        unwrappedFoldError    <- run(unwrappedFoldFailed.either)
        unwrappedMapped       <- run(unwrappedMappedReady)
        mapped                <- run(mappedReady)
        mappedError           <- run(mappedFailed)
        unprotected           <- run(unprotectedChild)
        unprotectedSource     <- run(unprotectedOuter)
      } yield assertTrue(
        singleton == Right(1L),
        unwrapped == Right(1L),
        unwrappedAcquireError.left.exists {
          case error: StreamError => error.value == "flatMap-unwrapped-acquisition"
          case _                  => false
        },
        unwrappedFoldError == Left(unwrappedFoldFailure),
        unwrappedMapped == Right(2L),
        mapped == Right(2L),
        mappedError == Left(mappedFoldFailure),
        unprotected == Right(2L),
        unprotectedSource == Right(2L)
      )
    },
    test("direct native asynchronous flatMap yields while scalar-folding ready singleton children") {
      val count  = 1026
      val outer  = new HostileIntReader((0 until count): _*)
      val result = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(outer: Reader[Int]))
        .flatMap(Stream.succeed)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
      run(result).map(value => assertTrue(value == Right((0 until count).map(_.toLong).sum)))
    },
    test("nested asynchronous flatMap source folds classify acquisition, read, fold, and close failures") {
      def reader(
        read0: Long => Async[Long],
        close0: () => Async[Unit] = () => Async.succeed(())
      ): Reader.AsyncReader[Int] = new Reader.AsyncReader[Int] {
        override def jvmType: JvmType                                               = JvmType.Int
        def close(): Async[Unit]                                                    = close0()
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.fail(new AssertionError("generic read"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = read0(sentinel)
      }
      def evaluate[E](
        child: Stream[E, Int],
        fold: (Long, Int) => Async[Long] = (sum, value) => Async.succeed(sum + value)
      ): Async[Either[Throwable, Either[E, Long]]] =
        Stream
          .fromReaderAsync[Nothing, Int](Async.succeed(new HostileIntReader(1): Reader[Int]))
          .flatMap(_ => Stream.succeed(0).flatMap(_ => child))
          .runFoldAsync(0L)(fold)
          .either

      val acquisitionFailure = StreamError.source("nested-source-acquisition")
      val readThrow          = StreamError.source("nested-source-read-throw")
      val ordinaryReadThrow  = new RuntimeException("nested-source-ordinary-read-throw")
      val readFailure        = new RuntimeException("nested-source-read-failure")
      val trustedReadFailure = StreamError.source("nested-source-trusted-read-failure")
      val foldFailure        = new RuntimeException("nested-source-fold-failure")
      val closeThrow         = new RuntimeException("nested-source-close-throw")
      val closeFailure       = new RuntimeException("nested-source-close-failure")
      var emitted            = false
      val useAndClose        = reader(
        sentinel =>
          if (!emitted) { emitted = true; Async.succeed(1L) }
          else Async.succeed(sentinel),
        () => Async.fail(closeFailure)
      )
      for {
        acquisitionFailed <-
          run(
            evaluate[String](Stream.fromReaderAsync[String, Int](Async.failTrusted(acquisitionFailure)))
          )
        readThrown <- run(
                        evaluate[String](
                          Stream.fromReaderAsync[String, Int](Async.succeed(reader(_ => throw readThrow): Reader[Int]))
                        )
                      )
        ordinaryReadThrown <-
          run(
            evaluate[Nothing](
              Stream.fromReaderAsync[Nothing, Int](Async.succeed(reader(_ => throw ordinaryReadThrow): Reader[Int]))
            )
          )
        readFailed <-
          run(
            evaluate[Nothing](
              Stream.fromReaderAsync[Nothing, Int](Async.succeed(reader(_ => Async.fail(readFailure)): Reader[Int]))
            )
          )
        trustedReadFailed <- run(
                               evaluate[String](
                                 Stream.fromReaderAsync[String, Int](
                                   Async.succeed(reader(_ => Async.failTrusted(trustedReadFailure)): Reader[Int])
                                 )
                               )
                             )
        foldFailed <- run(
                        evaluate[Nothing](
                          Stream.fromReaderAsync[Nothing, Int](Async.succeed(new HostileIntReader(1): Reader[Int])),
                          (_, _) => Async.fail(foldFailure)
                        )
                      )
        closeThrown <-
          run(
            evaluate[Nothing](
              Stream.fromReaderAsync[Nothing, Int](
                Async.succeed(reader(sentinel => Async.succeed(sentinel), () => throw closeThrow): Reader[Int])
              )
            )
          )
        useAndCloseFailed <- run(
                               evaluate[Nothing](
                                 Stream.fromReaderAsync[Nothing, Int](Async.succeed(useAndClose: Reader[Int])),
                                 (_, _) => Async.fail(foldFailure)
                               )
                             )
        syncAcquired <-
          run(
            evaluate[Nothing](
              Stream.fromReaderAsync[Nothing, Int](Async.succeed(Reader.fromIterable(List(1, 2)): Reader[Int]))
            )
          )
      } yield assertTrue(
        acquisitionFailed.left.exists {
          case error: StreamError => error.value == acquisitionFailure.value && !error.isTrusted
          case _                  => false
        },
        readThrown == Right(Left("nested-source-read-throw")),
        ordinaryReadThrown == Left(ordinaryReadThrow),
        readFailed == Left(readFailure),
        trustedReadFailed == Right(Left("nested-source-trusted-read-failure")),
        foldFailed == Left(foldFailure),
        closeThrown == Left(closeThrow),
        useAndCloseFailed == Left(foldFailure),
        foldFailure.getSuppressed.toList == List(closeFailure),
        syncAcquired == Right(Right(3L))
      )
    },
    test("nested asynchronous flatMap source fold releases an acquisition that races cancellation") {
      val acquiredReader       = new HostileIntReader(1)
      val acquisition          = new CancelOnPoll[Reader[Int]](Async.succeed(acquiredReader: Reader[Int]))
      val cancelled            = new Completer[Unit]
      var cleanup: Async[Unit] = null
      val child                = Stream.fromReaderAsync[Nothing, Int](acquisition)
      val running              = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(new HostileIntReader(1): Reader[Int]))
        .flatMap(_ => Stream.succeed(0).flatMap(_ => child))
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      for {
        _ <- run(acquisition.started)
        _  = acquisition.arm { () =>
              cleanup = Async.cancelWithCleanup(running)
              cancelled.succeed(())
            }
        _      <- run(cancelled)
        _      <- run(cleanup)
        closed <- run(acquiredReader.isClosed)
      } yield assertTrue(closed)
    },
    test("nested asynchronous flatMap source fold resumes pending work and yields fairly") {
      def nested[E](child: Stream[E, Int], fold: (Long, Int) => Async[Long]): Async[Either[E, Long]] =
        Stream
          .fromReaderAsync[Nothing, Int](Async.succeed(new HostileIntReader(1): Reader[Int]))
          .flatMap(_ => Stream.succeed(0).flatMap(_ => child))
          .runFoldAsync(0L)(fold)

      val pendingReader = new NativePendingReader
      val foldGate      = new Completer[Long]
      val foldStarted   = new Completer[Unit]
      val pending       = nested(
        Stream.fromReaderAsync[Nothing, Int](Async.succeed(pendingReader: Reader[Int])),
        (sum, value) => {
          if (value == 1) { foldStarted.succeed(()); foldGate }
          else Async.succeed(sum + value)
        }
      ).start
      val closeFailure       = new RuntimeException("nested-source-pending-close")
      val closeGate          = new Completer[Unit]
      val closeStarted       = new Completer[Unit]
      val pendingCloseReader = new Reader.AsyncReader[Int] {
        override def jvmType: JvmType                                               = JvmType.Int
        def close(): Async[Unit]                                                    = { closeStarted.succeed(()); closeGate }
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.succeed(sentinel)
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = Async.succeed(sentinel)
      }
      val pendingClose = nested(
        Stream.fromReaderAsync[Nothing, Int](Async.succeed(pendingCloseReader: Reader[Int])),
        (sum, value) => Async.succeed(sum + value)
      ).either.start
      val count    = 1026
      val expected = (0 until count).map(_.toLong).sum
      for {
        _             <- run(pendingReader.started)
        _              = pendingReader.resume()
        _             <- run(foldStarted)
        _              = foldGate.succeed(1L)
        pendingResult <- run(pending)
        _             <- run(closeStarted)
        _              = closeGate.fail(closeFailure)
        closeResult   <- run(pendingClose)
        fair          <- run(
                  nested(
                    Stream.fromReaderAsync[Nothing, Int](
                      Async.succeed(new HostileIntReader((0 until count): _*): Reader[Int])
                    ),
                    (sum, value) => Async.succeed(sum + value)
                  )
                )
        unprotected <- run(
                         nested(Stream.attemptAsync(Async.succeed(2)), (sum, value) => Async.succeed(sum + value))
                       )
      } yield assertTrue(
        pendingResult == Right(6L),
        closeResult == Left(closeFailure),
        fair == Right(expected),
        unprotected == Right(2L)
      )
    },
    test("direct synchronous flatMap scalar-folds singleton children and safely drains arbitrary children") {
      val childCloses   = new AtomicInteger
      val childAcquires = new AtomicInteger
      val callbacks     = new AtomicInteger
      val stream        = Stream.range(1, 4).flatMap { value =>
        if (value == 1) Stream.succeed(value)
        else if (value == 2) Stream.empty
        else
          Stream.fromReaderAsync[Nothing, Int] {
            childAcquires.incrementAndGet()
            Async.succeed(
              new SkipCapableIntReader(Async.succeed(false), value, value + 10) {
                override def close(): Async[Unit] = {
                  childCloses.incrementAndGet()
                  super.close()
                }
              }: Reader[Int]
            )
          }
      }
      for {
        first <- run(stream.runFoldAsync(0L) { (sum, value) =>
                   callbacks.incrementAndGet()
                   Async.succeed(sum + value)
                 })
        second <- run(stream.runFoldAsync(0L) { (sum, value) =>
                    callbacks.incrementAndGet()
                    Async.succeed(sum + value)
                  })
      } yield assertTrue(
        first == Right(17L),
        second == Right(17L),
        callbacks.get == 6,
        childAcquires.get == 2,
        childCloses.get == 2
      )
    },
    test("direct native asynchronous flatMap consumes a specialized linear outer graph") {
      val outer         = new HostileIntReader(0, 1, 2, 3)
      val maps          = new AtomicInteger
      val predicates    = new AtomicInteger
      val childAcquires = new AtomicInteger
      val childCloses   = new AtomicInteger
      val childMaps     = new AtomicInteger
      val stream        = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(outer: Reader[Int]))
        .map { value => maps.incrementAndGet(); value + 1 }
        .filter { value => predicates.incrementAndGet(); value % 2 == 0 }
        .flatMap { value =>
          Stream.fromReaderAsync[Nothing, Int] {
            childAcquires.incrementAndGet()
            Async.succeed(
              new SkipCapableIntReader(Async.succeed(false), value, value + 10) {
                override def close(): Async[Unit] = {
                  childCloses.incrementAndGet()
                  super.close()
                }
              }: Reader[Int]
            )
          }
        }
        .map { value => childMaps.incrementAndGet(); value * 3 }
      for {
        result <- run(stream.runFoldAsync(0L)((sum, value) => Async.succeed(sum + value)))
      } yield assertTrue(
        result == Right(96L),
        outer.specializedReads.get == 5,
        maps.get == 4,
        predicates.get == 4,
        childAcquires.get == 2,
        childCloses.get == 2,
        childMaps.get == 4
      )
    },
    test("direct linear flatMap resumes child work and preserves typed child failure") {
      val outer        = new HostileIntReader(0, 1, 2)
      val firstChild   = new NativePendingReader
      val failedCloses = new AtomicInteger
      val pendingFold  = new Completer[Long]
      val failedChild  = new Reader.AsyncReader[Int] {
        override def jvmType: JvmType = JvmType.Int
        def close(): Async[Unit]      = {
          failedCloses.incrementAndGet()
          Async.succeed(())
        }
        def isClosed: Async[Boolean]              = Async.succeed(failedCloses.get > 0)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] =
          Async.failTrusted(StreamError.source("linear-child"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
          Async.failTrusted(StreamError.source("linear-child"))
      }
      val running = Stream
        .fromReaderAsync[String, Int](Async.succeed(outer: Reader[Int]))
        .map(_ + 1)
        .filter(_ % 2 != 0)
        .flatMap { value =>
          if (value == 1) Stream.fromReaderAsync[String, Int](Async.succeed(firstChild: Reader[Int]))
          else Stream.fromReaderAsync[String, Int](Async.succeed(failedChild: Reader[Int]))
        }
        .runFoldAsync(0L)((sum, value) => if (value == 3) pendingFold else Async.succeed(sum + value))
        .start
      firstChild.resume()
      pendingFold.succeed(6L)
      for {
        result      <- run(running)
        outerClosed <- run(outer.isClosed)
      } yield assertTrue(
        result == Left("linear-child"),
        outer.specializedReads.get == 3,
        firstChild.closes.get == 1,
        failedCloses.get == 1,
        outerClosed
      )
    },
    test("direct native asynchronous flatMap composes nested mixed layers") {
      val outer          = new HostileIntReader(0, 1, 2, 3)
      val predicates1    = new AtomicInteger
      val predicates2    = new AtomicInteger
      val childAcquires1 = new AtomicInteger
      val childAcquires2 = new AtomicInteger
      val childCloses1   = new AtomicInteger
      val childCloses2   = new AtomicInteger
      val childMaps1     = new AtomicInteger
      val childMaps2     = new AtomicInteger
      val first          = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(outer: Reader[Int]))
        .filter { value => predicates1.incrementAndGet(); value % 2 != 0 }
        .flatMap { _ =>
          Stream.fromReaderAsync[Nothing, Int] {
            childAcquires1.incrementAndGet()
            Async.succeed(
              new SkipCapableIntReader(Async.succeed(false), 0, 1) {
                override def close(): Async[Unit] = {
                  childCloses1.incrementAndGet()
                  super.close()
                }
              }: Reader[Int]
            )
          }
        }
        .map { value => childMaps1.incrementAndGet(); value + 1 }
      val stream = first.filter { value => predicates2.incrementAndGet(); value % 2 != 0 }.flatMap { _ =>
        Stream.fromReaderAsync[Nothing, Int] {
          childAcquires2.incrementAndGet()
          Async.succeed(
            new SkipCapableIntReader(Async.succeed(false), 0, 1, 2) {
              override def close(): Async[Unit] = {
                childCloses2.incrementAndGet()
                super.close()
              }
            }: Reader[Int]
          )
        }
      }.map { value => childMaps2.incrementAndGet(); value + 1 }
      for {
        result <- run(stream.runFoldAsync(0L)((sum, value) => Async.succeed(sum + value)))
      } yield assertTrue(
        result == Right(12L),
        outer.specializedReads.get == 5,
        predicates1.get == 4,
        childAcquires1.get == 2,
        childCloses1.get == 2,
        childMaps1.get == 4,
        predicates2.get == 4,
        childAcquires2.get == 2,
        childCloses2.get == 2,
        childMaps2.get == 6
      )
    },
    test("direct nested mixed layers resume each owned parent reader and preserve pending fold carry") {
      val outer              = new HostileIntReader(1, 3)
      val firstChildren      = Array(new NativePendingReader, new NativePendingReader)
      val secondChildren     = Array(new NativePendingReader, new NativePendingReader)
      val firstAcquisitions  = new AtomicInteger
      val secondAcquisitions = new AtomicInteger
      val pendingFold        = new Completer[Long]
      val pendingFoldStarted = new Completer[Unit]
      val suspendFold        = new AtomicInteger
      val first              = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(outer: Reader[Int]))
        .filter(_ % 2 != 0)
        .flatMap { _ =>
          val index = firstAcquisitions.getAndIncrement()
          Stream.fromReaderAsync[Nothing, Int](Async.succeed(firstChildren(index): Reader[Int]))
        }
        .map(_ + 1)
      val stream = first
        .filter(_ % 2 != 0)
        .flatMap { _ =>
          val index = secondAcquisitions.getAndIncrement()
          Stream.fromReaderAsync[Nothing, Int](Async.succeed(secondChildren(index): Reader[Int]))
        }
        .map(_ + 1)
      val running = stream
        .runFoldAsync(0L) { (sum, value) =>
          if (value == 3 && suspendFold.compareAndSet(0, 1)) {
            pendingFoldStarted.succeed(())
            pendingFold
          } else Async.succeed(sum + value)
        }
        .start
      for {
        _      <- run(firstChildren(0).started)
        _       = firstChildren(0).resume()
        _      <- run(secondChildren(0).started)
        _       = secondChildren(0).resume()
        _      <- run(pendingFoldStarted)
        _       = pendingFold.succeed(5L)
        _      <- run(firstChildren(1).started)
        _       = firstChildren(1).resume()
        _      <- run(secondChildren(1).started)
        _       = secondChildren(1).resume()
        result <- run(running)
      } yield assertTrue(
        result == Right(18L),
        outer.specializedReads.get == 3,
        firstAcquisitions.get == 2,
        secondAcquisitions.get == 2,
        firstChildren.forall(_.closes.get == 1),
        secondChildren.forall(_.closes.get == 1)
      )
    },
    test("direct native asynchronous flatMap composes three mixed layers with exact child ownership") {
      val outer        = new HostileIntReader(1, 3)
      val acquisitions = Array.fill(3)(new AtomicInteger)
      val layerCloses  = Array.fill(3)(new AtomicInteger)
      var stream       = Stream.fromReaderAsync[Nothing, Int](Async.succeed(outer: Reader[Int]))
      var depth        = 0
      while (depth < 3) {
        val layer = depth
        stream = stream
          .filter(_ % 2 != 0)
          .flatMap { _ =>
            Stream.fromReaderAsync[Nothing, Int] {
              acquisitions(layer).incrementAndGet()
              Async.succeed(
                new SkipCapableIntReader(Async.succeed(false), 0, 1, 2) {
                  override def close(): Async[Unit] = {
                    layerCloses(layer).incrementAndGet()
                    super.close()
                  }
                }: Reader[Int]
              )
            }
          }
          .map(_ + 1)
        depth += 1
      }
      for {
        result <- run(stream.runFoldAsync(0L)((sum, value) => Async.succeed(sum + value)))
      } yield assertTrue(
        result == Right(48L),
        outer.specializedReads.get == 3,
        acquisitions.map(_.get).sameElements(Array(2, 4, 8)),
        layerCloses.map(_.get).sameElements(Array(2, 4, 8))
      )
    },
    test("direct native asynchronous flatMap trampolines deep nested flatMaps") {
      val depth       = 10000
      val outer       = new HostileIntReader(1)
      val acquires    = new AtomicInteger
      val childCloses = new AtomicInteger
      var stream      = Stream.fromReaderAsync[Nothing, Int](Async.succeed(outer: Reader[Int]))
      var index       = 0
      while (index < depth) {
        stream = stream.flatMap { value =>
          Stream.fromReaderAsync[Nothing, Int] {
            acquires.incrementAndGet()
            Async.succeed(
              new SkipCapableIntReader(Async.succeed(false), value + 1) {
                override def close(): Async[Unit] = {
                  childCloses.incrementAndGet()
                  super.close()
                }
              }: Reader[Int]
            )
          }
        }
        index += 1
      }
      for {
        result <- run(stream.runFoldAsync(0L)((sum, value) => Async.succeed(sum + value)))
      } yield assertTrue(
        result == Right(depth.toLong + 1L),
        outer.specializedReads.get == 2,
        acquires.get == depth,
        childCloses.get == depth
      )
    },
    test("direct native deeply nested flatMap cancellation closes parent-linked readers inner-first") {
      val depth       = 256
      val closeIndex  = new AtomicInteger
      val closeRanks  = Array.fill(depth + 1)(new AtomicInteger(-1))
      val pendingFold = new CancelledRead
      val outer       = new SkipCapableIntReader(Async.succeed(false), 1) {
        override def close(): Async[Unit] = {
          closeRanks(0).set(closeIndex.getAndIncrement())
          super.close()
        }
      }
      var stream = Stream.fromReaderAsync[Nothing, Int](Async.succeed(outer: Reader[Int]))
      var index  = 0
      while (index < depth) {
        val layer = index + 1
        stream = stream.flatMap { value =>
          Stream.fromReaderAsync[Nothing, Int](
            Async.succeed(
              new SkipCapableIntReader(Async.succeed(false), value + 1) {
                override def close(): Async[Unit] = {
                  closeRanks(layer).set(closeIndex.getAndIncrement())
                  super.close()
                }
              }: Reader[Int]
            )
          )
        }
        index += 1
      }
      val running = stream
        .runFoldAsync(0L) { (sum, value) =>
          val _ = (sum, value)
          pendingFold.asInstanceOf[Async[Long]]
        }
        .start
      for {
        _             <- run(pendingFold.started)
        cleanup        = Async.cancelWithCleanup(running)
        cleanupPending = fold(cleanup).isInstanceOf[Pending[_]]
        _              = pendingFold.cleanup.succeed(())
        _             <- run(cleanup)
      } yield assertTrue(
        cleanupPending,
        pendingFold.cancelled.get == 1,
        closeIndex.get == depth + 1,
        closeRanks.indices.forall(layer => closeRanks(layer).get == depth - layer)
      )
    },
    test("direct native asynchronous flatMap resumes child work and preserves typed child failure") {
      val pending    = new Completer[Long]
      val outer      = new HostileIntReader(1, 2)
      val firstChild = new NativePendingReader
      val running    = Stream
        .fromReaderAsync[String, Int](Async.succeed(outer: Reader[Int]))
        .flatMap { value =>
          if (value == 1) Stream.fromReaderAsync[Nothing, Int](Async.succeed(firstChild: Reader[Int]))
          else
            Stream.fromReaderAsync[String, Int](Async.succeed(new HostileIntReader(4): Reader[Int])) ++ Stream.fail(
              "child"
            )
        }
        .runFoldAsync(0L)((sum, value) => if (value == 3) pending else Async.succeed(sum + value))
        .start
      firstChild.resume()
      pending.succeed(6L)
      for {
        result <- run(running)
      } yield assertTrue(
        result == Left("child"),
        outer.specializedReads.get == 2,
        firstChild.closes.get == 1
      )
    },
    test("direct native asynchronous flatMap cancellation joins and closes the active child") {
      val childRead   = new CancelledRead
      val childCloses = new AtomicInteger
      val outer       = new HostileIntReader(1)
      val child       = new Reader.AsyncReader[Int] {
        override def jvmType: JvmType                                               = JvmType.Int
        def close(): Async[Unit]                                                    = { childCloses.incrementAndGet(); Async.succeed(()) }
        def isClosed: Async[Boolean]                                                = Async.succeed(childCloses.get > 0)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = childRead.asInstanceOf[Async[A]]
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
          childRead.asInstanceOf[Async[Long]]
      }
      val running = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(outer: Reader[Int]))
        .flatMap(_ => Stream.fromReaderAsync[Nothing, Int](Async.succeed(child: Reader[Int])))
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      for {
        _           <- run(childRead.started)
        cleanup      = Async.cancelWithCleanup(running)
        pending      = fold(cleanup).isInstanceOf[Pending[_]]
        _            = childRead.cleanup.succeed(())
        _           <- run(cleanup)
        outerClosed <- run(outer.isClosed)
      } yield assertTrue(
        pending,
        childRead.cancelled.get == 1,
        childCloses.get == 1,
        outerClosed
      )
    },
    test("direct native asynchronous flatMap preserves fold defects and closes every owner after a fairness yield") {
      val outer         = new HostileIntReader((0 until 1026): _*)
      val childAcquires = new AtomicInteger
      val childCloses   = new AtomicInteger
      val forged        = new StreamError("flatMap-fold-after-yield")
      val effect        = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(outer: Reader[Int]))
        .flatMap { value =>
          Stream.fromReaderAsync[Nothing, Int] {
            childAcquires.incrementAndGet()
            Async.succeed(
              new SkipCapableIntReader(Async.succeed(false), value) {
                override def close(): Async[Unit] = {
                  childCloses.incrementAndGet()
                  super.close()
                }
              }: Reader[Int]
            )
          }
        }
        .runFoldAsync(0L) { (sum, value) =>
          if (value == 1024) throw forged else Async.succeed(sum + value)
        }
        .either
      for {
        result      <- run(effect)
        outerClosed <- run(outer.isClosed)
      } yield assertTrue(
        result.left.exists {
          case error: StreamError =>
            (error ne forged) && error.value == "flatMap-fold-after-yield" && !error.isTrusted
          case _ => false
        },
        outer.specializedReads.get == 1025,
        childAcquires.get == 1025,
        childCloses.get == 1025,
        outerClosed
      )
    },
    test("native asynchronous take bounds a specialized fold terminal") {
      val source = new HostileIntReader(0, 1, 2, 3, 4)
      run(
        Stream
          .fromReader[Nothing, Int](source)
          .take(3)
          .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
      ).map(result => assertTrue(result == Right(3L), source.specializedReads.get == 3))
    },
    test("direct native asynchronous take supports a generic accumulator without over-reading") {
      val source  = new HostileIntReader((1 to 12): _*)
      val pending = new Completer[String]
      val running = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .take(10)
        .runFoldAsync("")((acc, value) => if (value == 2) pending else Async.succeed(acc + value.toString))
        .start
      pending.succeed("12")
      for {
        result <- run(running)
        closed <- run(source.isClosed)
      } yield assertTrue(
        result == Right("12345678910"),
        source.specializedReads.get == 10,
        closed
      )
    },
    test("direct native asynchronous take generic fold supports unprotected acquisition") {
      val callbacks = new AtomicInteger
      run(
        Stream
          .attemptAsync(Async.succeed(7))
          .take(1)
          .runFoldAsync("") { (acc, value) => callbacks.incrementAndGet(); Async.succeed(acc + value.toString) }
      ).map(result => assertTrue(result == Right("7"), callbacks.get == 1))
    },
    test("direct native asynchronous take generic fold preserves typed failures and cleanup suppression") {
      val afterYield    = StreamError.source("take-reference-after-yield")
      val yieldedCloses = new AtomicInteger
      val yielded       = new Reader.AsyncReader[Int] {
        private var reads                         = 0
        def close(): Async[Unit]                  = { yieldedCloses.incrementAndGet(); Async.succeed(()) }
        override def jvmType: JvmType             = JvmType.Int
        def isClosed: Async[Boolean]              = Async.succeed(yieldedCloses.get > 0)
        def read[A >: Int](sentinel: A): Async[A] =
          Async.fail(new AssertionError("generic read used for Int lane"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = {
          reads += 1
          if (reads <= 1024) Async.succeed(reads.toLong)
          else Async.failTrusted(afterYield)
        }
        def readable(): Async[Boolean] = Async.succeed(true)
      }
      val useFailure    = StreamError.source("take-reference-use")
      val closeFailure  = new RuntimeException("take-reference-close")
      val cleanupCloses = new AtomicInteger
      val cleanup       = new Reader.AsyncReader[Int] {
        def close(): Async[Unit] = {
          cleanupCloses.incrementAndGet()
          Async.fail(closeFailure)
        }
        override def jvmType: JvmType                                               = JvmType.Int
        def isClosed: Async[Boolean]                                                = Async.succeed(cleanupCloses.get > 0)
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.failTrusted(useFailure)
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = Async.failTrusted(useFailure)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
      }
      for {
        typed <- run(
                   Stream
                     .fromReaderAsync[String, Int](Async.succeed(yielded: Reader[Int]))
                     .take(2048)
                     .runFoldAsync(())((_, _) => Async.succeed(()))
                 )
        failed <- run(
                    Stream
                      .fromReaderAsync[String, Int](Async.succeed(cleanup: Reader[Int]))
                      .take(1)
                      .runFoldAsync("")((acc, value) => Async.succeed(acc + value.toString))
                      .either
                  )
      } yield assertTrue(
        typed == Left("take-reference-after-yield"),
        yieldedCloses.get == 1,
        failed.left.exists(_ eq useFailure),
        useFailure.cleanupFailed,
        useFailure.getSuppressed.toList == List(closeFailure),
        cleanupCloses.get == 1
      )
    },
    test("cancelling direct native asynchronous take generic fold joins its callback and closes exactly once") {
      val source          = new HostileIntReader(1)
      val pendingCallback = new CancelledRead
      val running         = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .take(1)
        .runFoldAsync("")((_, _) => pendingCallback.asInstanceOf[Async[String]])
        .start
      for {
        _               <- run(pendingCallback.started)
        cleanup         <- ZIO.succeed(Async.cancelWithCleanup(running))
        waitsForCallback = fold(cleanup).isInstanceOf[Pending[_]]
        _                = pendingCallback.cleanup.succeed(())
        _               <- run(cleanup)
        closed          <- run(source.isClosed)
      } yield assertTrue(
        waitsForCallback,
        pendingCallback.cancelled.get == 1,
        source.specializedReads.get == 1,
        closed
      )
    },
    test("direct native asynchronous take preserves suspension fairness and exact boundaries") {
      val zeroSource    = new HostileIntReader(1)
      val readSource    = new NativePendingReader
      val foldSource    = new HostileIntReader(1, 2, 3)
      val fairSource    = new GatedIntReader(1025, (0 until 2049): _*)
      val zeroCallbacks = new AtomicInteger
      val foldCallbacks = new AtomicInteger
      val fairCallbacks = new AtomicInteger
      val pendingFold   = new Completer[Long]
      val yieldPermit   = new Completer[Unit]
      val zeroRunning   = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(zeroSource: Reader[Int]))
        .take(0)
        .runFoldAsync(0L) { (sum, value) => zeroCallbacks.incrementAndGet(); Async.succeed(sum + value) }
        .start
      val readRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(readSource: Reader[Int]))
        .take(1)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      val foldRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(foldSource: Reader[Int]))
        .take(2)
        .runFoldAsync(0L) { (sum, value) =>
          foldCallbacks.incrementAndGet()
          if (value == 2) pendingFold else Async.succeed(sum + value)
        }
        .start
      val fair = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(fairSource: Reader[Int]))
        .take(2049)
        .runFoldAsync(0L) { (sum, value) => fairCallbacks.incrementAndGet(); Async.succeed(sum + value) }
      val firstPoll = fold(fair) match {
        case Pending(operation) => fold(operation.poll(() => yieldPermit.succeed(())))
        case result             => result
      }
      readSource.resume()
      pendingFold.succeed(3L)
      for {
        _                    <- run(fairSource.gateStarted)
        callbacksAtFirstYield = fairCallbacks.get()
        _                     = fairSource.resume()
        zeroResult           <- run(zeroRunning)
        readResult           <- run(readRunning)
        foldResult           <- run(foldRunning)
        _                    <- run(yieldPermit)
        fairResult           <- run(fair)
        zeroClosed           <- run(zeroSource.isClosed)
        foldClosed           <- run(foldSource.isClosed)
      } yield assertTrue(
        zeroResult == Right(0L),
        zeroCallbacks.get == 0,
        zeroSource.specializedReads.get == 0,
        zeroClosed,
        readResult == Right(1L),
        readSource.closes.get == 1,
        foldResult == Right(3L),
        foldCallbacks.get == 2,
        foldSource.specializedReads.get == 2,
        foldClosed,
        firstPoll.isInstanceOf[Pending[_]],
        callbacksAtFirstYield == 1024,
        fairResult == Right(2098176L),
        fairCallbacks.get == 2049,
        fairSource.specializedReads.get == 2049
      )
    },
    test("direct native asynchronous take preserves fold defects after a fairness yield") {
      val source = new HostileIntReader((0 until 1026): _*)
      val forged = new StreamError("take-fold-after-yield")
      val effect = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .take(1026)
        .runFoldAsync(0L) { (sum, value) =>
          if (value == 1024) throw forged else Async.succeed(sum + value)
        }
        .either
      for {
        result <- run(effect)
        closed <- run(source.isClosed)
      } yield assertTrue(
        result.left.exists {
          case error: StreamError =>
            (error ne forged) && error.value == "take-fold-after-yield" && !error.isTrusted
          case _ => false
        },
        source.specializedReads.get == 1025,
        closed
      )
    },
    test("direct native asynchronous takeDrop preserves bulk and scalar skip, suspension, fairness, and boundaries") {
      val zeroSource      = new SkipCapableIntReader(Async.succeed(true), 1)
      val scalarSource    = new SkipCapableIntReader(Async.succeed(false), 0, 1, 2, 3, 4, 5)
      val bulkSource      = new SkipCapableIntReader(Async.succeed(true), 0, 1, 2, 3, 4, 5)
      val pendingSkip     = new Completer[Boolean]
      val pendingSource   = new SkipCapableIntReader(pendingSkip, 0, 1, 2, 3)
      val readSource      = new NativePendingReader
      val foldSource      = new HostileIntReader(1, 2, 3, 4)
      val exhaustedSource = new HostileIntReader(1, 2)
      val fairSource      = new GatedIntReader(1025, (0 until 2049): _*)
      val scalarCallbacks = new AtomicInteger
      val foldCallbacks   = new AtomicInteger
      val fairCallbacks   = new AtomicInteger
      val pendingFold     = new Completer[Long]
      val yieldPermit     = new Completer[Unit]
      val zeroRunning     = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(zeroSource: Reader[Int]))
        .drop(1)
        .take(0)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      val scalarRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(scalarSource: Reader[Int]))
        .drop(2)
        .take(3)
        .runFoldAsync(0L) { (sum, value) => scalarCallbacks.incrementAndGet(); Async.succeed(sum + value) }
        .start
      val bulkRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(bulkSource: Reader[Int]))
        .drop(2)
        .take(3)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      val pendingRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(pendingSource: Reader[Int]))
        .drop(2)
        .take(2)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      val readRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(readSource: Reader[Int]))
        .drop(1)
        .take(2)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      val foldRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(foldSource: Reader[Int]))
        .drop(1)
        .take(2)
        .runFoldAsync(0L) { (sum, value) =>
          foldCallbacks.incrementAndGet()
          if (value == 2) pendingFold else Async.succeed(sum + value)
        }
        .start
      val exhaustedRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(exhaustedSource: Reader[Int]))
        .drop(4)
        .take(2)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      val fair = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(fairSource: Reader[Int]))
        .drop(1024)
        .take(1025)
        .runFoldAsync(0L) { (sum, value) => fairCallbacks.incrementAndGet(); Async.succeed(sum + value) }
      val firstPoll = fold(fair) match {
        case Pending(operation) => fold(operation.poll(() => yieldPermit.succeed(())))
        case result             => result
      }
      pendingSkip.succeed(true)
      readSource.resume()
      pendingFold.succeed(2L)
      for {
        _                    <- run(fairSource.gateStarted)
        callbacksAtFirstYield = fairCallbacks.get()
        _                     = fairSource.resume()
        zeroResult           <- run(zeroRunning)
        scalarResult         <- run(scalarRunning)
        bulkResult           <- run(bulkRunning)
        pendingResult        <- run(pendingRunning)
        readResult           <- run(readRunning)
        foldResult           <- run(foldRunning)
        exhaustedResult      <- run(exhaustedRunning)
        _                    <- run(yieldPermit)
        fairResult           <- run(fair)
      } yield assertTrue(
        zeroResult == Right(0L),
        zeroSource.setSkipCalls.get == 0,
        zeroSource.specializedReads.get == 0,
        zeroSource.closes.get == 1,
        scalarResult == Right(9L),
        scalarCallbacks.get == 3,
        scalarSource.setSkipCalls.get == 1,
        scalarSource.specializedReads.get == 5,
        scalarSource.closes.get == 1,
        bulkResult == Right(9L),
        bulkSource.setSkipCalls.get == 1,
        bulkSource.specializedReads.get == 3,
        bulkSource.closes.get == 1,
        pendingResult == Right(5L),
        pendingSource.setSkipCalls.get == 1,
        pendingSource.specializedReads.get == 2,
        pendingSource.closes.get == 1,
        readResult == Right(5L),
        readSource.closes.get == 1,
        foldResult == Right(5L),
        foldCallbacks.get == 2,
        foldSource.specializedReads.get == 3,
        exhaustedResult == Right(0L),
        exhaustedSource.specializedReads.get == 3,
        firstPoll.isInstanceOf[Pending[_]],
        callbacksAtFirstYield == 0,
        fairResult == Right(1574400L),
        fairCallbacks.get == 1025,
        fairSource.specializedReads.get == 2049
      )
    },
    test("direct native asynchronous takeDrop preserves fold defects after a fairness yield") {
      val source = new HostileIntReader((0 until 2050): _*)
      val forged = new StreamError("takeDrop-fold-after-yield")
      val effect = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .drop(1024)
        .take(1026)
        .runFoldAsync(0L) { (sum, value) =>
          if (value == 1024) throw forged else Async.succeed(sum + value)
        }
        .either
      for {
        result <- run(effect)
        closed <- run(source.isClosed)
      } yield assertTrue(
        result.left.exists {
          case error: StreamError =>
            (error ne forged) && error.value == "takeDrop-fold-after-yield" && !error.isTrusted
          case _ => false
        },
        source.specializedReads.get == 1025,
        closed
      )
    },
    test("direct native asynchronous takeDrop classifies skip, read, and fold failures") {
      def runReader[E](
        reader: Reader.AsyncReader[Int],
        fold: (Long, Int) => Async[Long] = (sum, value) => Async.succeed(sum + value)
      ) =
        Stream
          .fromReaderAsync[E, Int](Async.succeed(reader: Reader[Int]))
          .drop(1)
          .take(1)
          .runFoldAsync(0L)(fold)
          .either

      def reader(skip0: => Async[Boolean], read0: => Async[Long]) = new Reader.AsyncReader[Int] {
        def close(): Async[Unit]                                                    = Async.succeed(())
        override def jvmType: JvmType                                               = JvmType.Int
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = read0.map(_.toInt.asInstanceOf[A])
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = read0
        override def setSkip(n: Long): Async[Boolean]                               = skip0
      }

      val skipThrow        = new RuntimeException("takeDrop-skip-throw")
      val skipTypedThrow   = StreamError.source("takeDrop-skip-typed-throw")
      val skipFailure      = new RuntimeException("takeDrop-skip-failure")
      val skipTypedFailure = StreamError.source("takeDrop-skip-typed-failure")
      val readThrow        = new RuntimeException("takeDrop-read-throw")
      val readTypedThrow   = StreamError.source("takeDrop-read-typed-throw")
      val readFailure      = new RuntimeException("takeDrop-read-failure")
      val readTypedFailure = StreamError.source("takeDrop-read-typed-failure")
      val foldFailure      = new RuntimeException("takeDrop-fold-failure")
      for {
        skipThrown      <- run(runReader[Nothing](reader(throw skipThrow, Async.succeed(1L))))
        skipTypedThrown <- run(runReader[String](reader(throw skipTypedThrow, Async.succeed(1L))))
        skipFailed      <- run(runReader[Nothing](reader(Async.fail(skipFailure), Async.succeed(1L))))
        skipTypedFailed <- run(runReader[String](reader(Async.failTrusted(skipTypedFailure), Async.succeed(1L))))
        readThrown      <- run(runReader[Nothing](reader(Async.succeed(false), throw readThrow)))
        readTypedThrown <- run(runReader[String](reader(Async.succeed(false), throw readTypedThrow)))
        readFailed      <- run(runReader[Nothing](reader(Async.succeed(false), Async.fail(readFailure))))
        readTypedFailed <- run(runReader[String](reader(Async.succeed(false), Async.failTrusted(readTypedFailure))))
        foldFailed      <- run(
                        runReader[Nothing](
                          new SkipCapableIntReader(Async.succeed(false), 0, 1),
                          (_, _) => Async.fail(foldFailure)
                        )
                      )
      } yield assertTrue(
        skipThrown == Left(skipThrow),
        skipTypedThrown == Right(Left("takeDrop-skip-typed-throw")),
        skipFailed == Left(skipFailure),
        skipTypedFailed == Right(Left("takeDrop-skip-typed-failure")),
        readThrown == Left(readThrow),
        readTypedThrown == Right(Left("takeDrop-read-typed-throw")),
        readFailed == Left(readFailure),
        readTypedFailed == Right(Left("takeDrop-read-typed-failure")),
        foldFailed == Left(foldFailure)
      )
    },
    test("direct native asynchronous takeWhile stops at the first false and preserves suspension fairness") {
      val readSource     = new NativePendingReader
      val foldSource     = new HostileIntReader(1, 2, 3, 4)
      val fairSource     = new GatedIntReader(1025, (0 until 2049): _*)
      val predicates     = new AtomicInteger
      val foldCallbacks  = new AtomicInteger
      val fairPredicates = new AtomicInteger
      val pendingFold    = new Completer[Long]
      val yieldPermit    = new Completer[Unit]
      val readRunning    = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(readSource: Reader[Int]))
        .takeWhile(_ < 2)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      val foldRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(foldSource: Reader[Int]))
        .takeWhile { value => predicates.incrementAndGet(); value < 3 }
        .runFoldAsync(0L) { (sum, value) =>
          foldCallbacks.incrementAndGet()
          if (value == 2) pendingFold else Async.succeed(sum + value)
        }
        .start
      val fair = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(fairSource: Reader[Int]))
        .takeWhile { _ => fairPredicates.incrementAndGet(); true }
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
      val firstPoll = fold(fair) match {
        case Pending(operation) => fold(operation.poll(() => yieldPermit.succeed(())))
        case result             => result
      }
      readSource.resume()
      pendingFold.succeed(3L)
      for {
        _                     <- run(fairSource.gateStarted)
        predicatesAtFirstYield = fairPredicates.get()
        _                      = fairSource.resume()
        readResult            <- run(readRunning)
        foldResult            <- run(foldRunning)
        _                     <- run(yieldPermit)
        fairResult            <- run(fair)
        foldClosed            <- run(foldSource.isClosed)
      } yield assertTrue(
        readResult == Right(1L),
        readSource.closes.get == 1,
        foldResult == Right(3L),
        predicates.get == 3,
        foldCallbacks.get == 2,
        foldSource.specializedReads.get == 3,
        foldClosed,
        firstPoll.isInstanceOf[Pending[_]],
        predicatesAtFirstYield == 1024,
        fairResult == Right(2098176L),
        fairPredicates.get == 2049,
        fairSource.specializedReads.get == 2050
      )
    },
    test("direct native asynchronous takeWhile preserves predicate and fold defects after a fairness yield") {
      val predicateSource = new HostileIntReader((0 until 1026): _*)
      val foldSource      = new HostileIntReader((0 until 1026): _*)
      val predicateForged = new StreamError("takeWhile-predicate-after-yield")
      val foldForged      = new StreamError("takeWhile-fold-after-yield")
      val predicateEffect = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(predicateSource: Reader[Int]))
        .takeWhile { value =>
          if (value == 1024) throw predicateForged else true
        }
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .either
      val foldEffect = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(foldSource: Reader[Int]))
        .takeWhile(_ => true)
        .runFoldAsync(0L) { (sum, value) =>
          if (value == 1024) throw foldForged else Async.succeed(sum + value)
        }
        .either
      for {
        predicateResult <- run(predicateEffect)
        foldResult      <- run(foldEffect)
        predicateClosed <- run(predicateSource.isClosed)
        foldClosed      <- run(foldSource.isClosed)
      } yield assertTrue(
        predicateResult.left.exists {
          case error: StreamError =>
            (error ne predicateForged) &&
            error.value == "takeWhile-predicate-after-yield" &&
            !error.isTrusted
          case _ => false
        },
        foldResult.left.exists {
          case error: StreamError =>
            (error ne foldForged) && error.value == "takeWhile-fold-after-yield" && !error.isTrusted
          case _ => false
        },
        predicateSource.specializedReads.get == 1025,
        foldSource.specializedReads.get == 1025,
        predicateClosed,
        foldClosed
      )
    },
    test("native asynchronous specialized fold resumes pending reads and callbacks") {
      val readSource      = new NativePendingReader
      val pendingCallback = new Completer[Long]
      val callbackSource  = new HostileIntReader(1, 2, 3)
      val readRunning     = Stream
        .fromReader[Nothing, Int](readSource)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      val callbackRunning = Stream
        .fromReader[Nothing, Int](callbackSource)
        .runFoldAsync(0L)((sum, value) => if (value == 2) pendingCallback else Async.succeed(sum + value))
        .start
      readSource.resume()
      pendingCallback.succeed(3L)
      for {
        readResult     <- run(readRunning)
        callbackResult <- run(callbackRunning)
      } yield assertTrue(
        readResult == Right(6L),
        readSource.closes.get == 1,
        callbackResult == Right(6L),
        callbackSource.specializedReads.get == 4
      )
    },
    test("direct asynchronous acquire-release Long fold releases exactly once on every exit") {
      val successReleases = new AtomicInteger
      val typedReleases   = new AtomicInteger
      val defectReleases  = new AtomicInteger
      val cancelReleases  = new AtomicInteger
      val defect          = new RuntimeException("managed defect")
      val callback        = new CancelledRead
      val success         = Stream
        .fromAcquireReleaseAsync(
          Async.succeed(2),
          (_: Int) => Async.succeed { successReleases.incrementAndGet(); () }
        )(resource => Stream(resource, resource + 1))
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
      val typed = Stream
        .fromAcquireReleaseAsync[Unit, String, Int](
          Async.succeed(()),
          (_: Unit) => Async.succeed { typedReleases.incrementAndGet(); () }
        )(_ => Stream.fail[String]("typed"))
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
      val failed = Stream
        .fromAcquireReleaseAsync[Unit, Nothing, Int](
          Async.succeed(()),
          (_: Unit) => Async.succeed { defectReleases.incrementAndGet(); () }
        )(_ => Stream.unwrap[Nothing, Int](Async.fail(defect)))
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
      val cancelled = Stream
        .fromAcquireReleaseAsync(
          Async.succeed(()),
          (_: Unit) => Async.succeed { cancelReleases.incrementAndGet(); () }
        )(_ => Stream(1).mapAsync(_ => callback.asInstanceOf[Async[Int]]))
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      for {
        successResult <- run(success)
        typedResult   <- run(typed)
        failedResult  <- run(failed.either)
        _             <- run(callback.started)
        cleanup        = Async.cancelWithCleanup(cancelled)
        waitsForUse    = fold(cleanup).isInstanceOf[Pending[_]]
        _              = callback.cleanup.succeed(())
        _             <- run(cleanup)
      } yield assertTrue(
        successResult == Right(5L),
        typedResult == Left("typed"),
        failedResult == Left(defect),
        waitsForUse,
        callback.cancelled.get == 1,
        successReleases.get == 1,
        typedReleases.get == 1,
        defectReleases.get == 1,
        cancelReleases.get == 1
      )
    },
    test("direct async map Long fold resumes pending reads, maps, and folds") {
      val readSource  = new NativePendingReader
      val mapSource   = new HostileIntReader(1, 2, 3)
      val foldSource  = new HostileIntReader(1, 2, 3)
      val pendingMap  = new Completer[Int]
      val pendingFold = new Completer[Long]
      val mapStarted  = new Completer[Unit]
      val foldStarted = new Completer[Unit]
      val readRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(readSource: Reader[Int]))
        .mapAsync(value => Async.succeed(value + 1))
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      val mapRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(mapSource: Reader[Int]))
        .mapAsync(value =>
          if (value == 2) { mapStarted.succeed(()); pendingMap }
          else Async.succeed(value + 1)
        )
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      val foldRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(foldSource: Reader[Int]))
        .mapAsync(value => Async.succeed(value + 1))
        .runFoldAsync(0L)((sum, value) =>
          if (value == 3) { foldStarted.succeed(()); pendingFold }
          else Async.succeed(sum + value)
        )
        .start
      for {
        _          <- run(readSource.started)
        _           = readSource.resume()
        _          <- run(mapStarted)
        _           = pendingMap.succeed(3)
        _          <- run(foldStarted)
        _           = pendingFold.succeed(5L)
        readResult <- run(readRunning)
        mapResult  <- run(mapRunning)
        foldResult <- run(foldRunning)
      } yield assertTrue(
        readResult == Right(9L),
        mapResult == Right(9L),
        foldResult == Right(9L),
        readSource.closes.get == 1,
        mapSource.specializedReads.get == 4,
        foldSource.specializedReads.get == 4
      )
    },
    test("direct async map Long fold classifies initial read, map, fold, and close failures") {
      def asyncReader(
        read0: Long => Async[Long],
        close0: () => Async[Unit] = () => Async.succeed(())
      ): Reader.AsyncReader[Int] = new Reader.AsyncReader[Int] {
        override def jvmType: JvmType                                               = JvmType.Int
        def close(): Async[Unit]                                                    = close0()
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.fail(new AssertionError("generic read"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = read0(sentinel)
      }
      def asyncEffect(
        reader: Reader.AsyncReader[Int],
        map: Int => Async[Int] = Async.succeed,
        reduce: (Long, Int) => Async[Long] = (sum, value) => Async.succeed(sum + value)
      ): Async[Either[Throwable, Either[String, Long]]] =
        Stream
          .fromReaderAsync[String, Int](Async.succeed(reader: Reader[Int]))
          .mapAsync(map)
          .runFoldAsync(0L)(reduce)
          .either
      def syncEffect(
        stream: Stream[String, Int],
        map: Int => Async[Int] = Async.succeed,
        reduce: (Long, Int) => Async[Long] = (sum, value) => Async.succeed(sum + value)
      ): Async[Either[Throwable, Either[String, Long]]] =
        stream.mapAsync(map).runFoldAsync(0L)(reduce).either

      val ordinaryRead       = new RuntimeException("initial-read")
      val trustedRead        = StreamError.source("initial-typed-read")
      val acquisitionThrow   = StreamError.source("initial-acquisition-throw")
      val acquisitionFailure = StreamError.source("initial-acquisition-failure")
      val mapThrow           = new StreamError("initial-map-throw")
      val mapFailure         = new StreamError("initial-map-failure")
      val foldThrow          = new StreamError("initial-fold-throw")
      val foldFailure        = new StreamError("initial-fold-failure")
      val closeThrow         = new RuntimeException("initial-close-throw")
      val closeFailure       = new RuntimeException("initial-close-failure")
      val useAndCloseFailure = new StreamError("initial-use-and-close")
      val asyncValue         = new HostileIntReader(1)
      val asyncMapFailure    = new HostileIntReader(1)
      val asyncFoldThrow     = new HostileIntReader(1)
      val asyncFoldFailure   = new HostileIntReader(1)
      val closeThrowReader   = asyncReader(sentinel => Async.succeed(sentinel), () => throw closeThrow)
      val closeFailedReader  = asyncReader(sentinel => Async.succeed(sentinel), () => Async.fail(closeFailure))
      var useRead            = false
      val useFailedReader    = asyncReader(
        sentinel =>
          if (!useRead) { useRead = true; Async.succeed(1L) }
          else Async.succeed(sentinel),
        () => Async.fail(closeFailure)
      )
      for {
        asyncAcquisitionThrow <- run(
                                   Stream
                                     .fromReaderAsync[String, Int](throw acquisitionThrow)
                                     .mapAsync(Async.succeed)
                                     .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
                                     .either
                                 )
        asyncAcquisitionFailure <- run(
                                     Stream
                                       .fromReaderAsync[String, Int](Async.failTrusted(acquisitionFailure))
                                       .mapAsync(Async.succeed)
                                       .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
                                       .either
                                   )
        asyncThrownTrusted  <- run(asyncEffect(asyncReader(_ => throw trustedRead)))
        asyncThrownRead     <- run(asyncEffect(asyncReader(_ => throw ordinaryRead)))
        asyncFailedTrusted  <- run(asyncEffect(asyncReader(_ => Async.failTrusted(trustedRead))))
        asyncTrustedDefect  <- run(asyncEffect(asyncReader(_ => Async.failTrusted(ordinaryRead))))
        asyncFailedRead     <- run(asyncEffect(asyncReader(_ => Async.fail(ordinaryRead))))
        asyncThrownMap      <- run(asyncEffect(asyncValue, _ => throw mapThrow))
        asyncFailedMap      <- run(asyncEffect(asyncMapFailure, _ => Async.failTrusted(mapFailure)))
        asyncThrownFold     <- run(asyncEffect(asyncFoldThrow, reduce = (_, _) => throw foldThrow))
        asyncFailedFold     <- run(asyncEffect(asyncFoldFailure, reduce = (_, _) => Async.failTrusted(foldFailure)))
        asyncCloseThrow     <- run(asyncEffect(closeThrowReader))
        asyncCloseFailure   <- run(asyncEffect(closeFailedReader))
        asyncUseAndClose    <- run(asyncEffect(useFailedReader, _ => Async.fail(useAndCloseFailure)))
        asyncAttemptTrusted <- run(
                                 Stream
                                   .attemptAsync[Int](Async.fail(ordinaryRead))
                                   .mapAsync(Async.succeed)
                                   .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
                                   .either
                               )
        syncThrownTrusted <- run(
                               Stream
                                 .attempt[Int](throw ordinaryRead)
                                 .mapAsync(Async.succeed)
                                 .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
                                 .either
                             )
        syncThrownRead <- run(
                            syncEffect(Stream(1).map[Int](_ => throw ordinaryRead))
                          )
        syncEof        <- run(syncEffect(Stream.fromChunk(Chunk.empty[Int])))
        syncThrownMap  <- run(syncEffect(Stream(1), _ => throw mapThrow))
        syncFailedMap  <- run(syncEffect(Stream(1), _ => Async.failTrusted(mapFailure)))
        syncThrownFold <- run(syncEffect(Stream(1), reduce = (_, _) => throw foldThrow))
        syncFailedFold <- run(syncEffect(Stream(1), reduce = (_, _) => Async.failTrusted(foldFailure)))
      } yield assertTrue(
        asyncAcquisitionThrow.left.exists {
          case error: StreamError =>
            (error ne acquisitionThrow) && error.value == acquisitionThrow.value && !error.isTrusted
          case _ => false
        },
        asyncAcquisitionFailure.left.exists {
          case error: StreamError =>
            (error ne acquisitionFailure) && error.value == acquisitionFailure.value && !error.isTrusted
          case _ => false
        },
        asyncThrownTrusted == Right(Left("initial-typed-read")),
        asyncThrownRead == Left(ordinaryRead),
        asyncFailedTrusted == Right(Left("initial-typed-read")),
        asyncTrustedDefect == Left(ordinaryRead),
        asyncFailedRead == Left(ordinaryRead),
        asyncThrownMap.left.exists {
          case error: StreamError => (error ne mapThrow) && error.value == mapThrow.value && !error.isTrusted
          case _                  => false
        },
        asyncFailedMap.left.exists {
          case error: StreamError => (error ne mapFailure) && error.value == mapFailure.value && !error.isTrusted
          case _                  => false
        },
        asyncThrownFold.left.exists {
          case error: StreamError => (error ne foldThrow) && error.value == foldThrow.value && !error.isTrusted
          case _                  => false
        },
        asyncFailedFold.left.exists {
          case error: StreamError => (error ne foldFailure) && error.value == foldFailure.value && !error.isTrusted
          case _                  => false
        },
        asyncCloseThrow == Left(closeThrow),
        asyncCloseFailure == Left(closeFailure),
        asyncUseAndClose.left.exists {
          case error: StreamError =>
            error.value == useAndCloseFailure.value && error.cleanupFailed &&
            error.getSuppressed.toList == List(closeFailure)
          case _ => false
        },
        asyncAttemptTrusted == Right(Left(ordinaryRead)),
        syncThrownTrusted == Right(Left(ordinaryRead)),
        syncThrownRead == Left(ordinaryRead),
        syncEof == Right(Right(0L)),
        syncThrownMap.left.exists {
          case error: StreamError => (error ne mapThrow) && error.value == mapThrow.value && !error.isTrusted
          case _                  => false
        },
        syncFailedMap.left.exists {
          case error: StreamError => (error ne mapFailure) && error.value == mapFailure.value && !error.isTrusted
          case _                  => false
        },
        syncThrownFold.left.exists {
          case error: StreamError => (error ne foldThrow) && error.value == foldThrow.value && !error.isTrusted
          case _                  => false
        },
        syncFailedFold.left.exists {
          case error: StreamError => (error ne foldFailure) && error.value == foldFailure.value && !error.isTrusted
          case _                  => false
        }
      )
    },
    test("direct synchronous Int-to-Long map fold supports protected and unprotected async sources") {
      val protectedSource = new HostileIntReader(1, 2, 3)
      val protectedEffect = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(protectedSource: Reader[Int]))
        .map(_.toLong)
        .runFoldAsync(10L)((sum, value) => Async.succeed(sum + value))
      val unprotectedEffect = Stream
        .attemptAsync(Async.succeed(4))
        .map(_.toLong)
        .runFoldAsync(10L)((sum, value) => Async.succeed(sum + value))
      for {
        protectedResult   <- run(protectedEffect)
        unprotectedResult <- run(unprotectedEffect)
        protectedClosed   <- run(protectedSource.isClosed)
      } yield assertTrue(
        protectedResult == Right(16L),
        unprotectedResult == Right(14L),
        protectedSource.specializedReads.get == 4,
        protectedClosed
      )
    },
    test("direct async map Long fold classifies callback failures after a pending map") {
      val mapSource   = new HostileIntReader(1, 2)
      val foldSource  = new HostileIntReader(1, 2)
      val mapGate     = new Completer[Int]
      val foldGate    = new Completer[Int]
      val mapStarted  = new Completer[Unit]
      val foldStarted = new Completer[Unit]
      val forgedMap   = new StreamError("map-after-pending")
      val forgedFold  = new StreamError("fold-after-pending")
      val mapRunning  = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(mapSource: Reader[Int]))
        .mapAsync { value =>
          val effect: Async[Int] =
            if (value == 1) { mapStarted.succeed(()); mapGate }
            else Async.fail(forgedMap)
          effect
        }
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .either
        .start
      val foldRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(foldSource: Reader[Int]))
        .mapAsync(value =>
          if (value == 1) { foldStarted.succeed(()); foldGate }
          else Async.succeed(value)
        )
        .runFoldAsync(0L)((sum, value) => if (value == 2) Async.fail(forgedFold) else Async.succeed(sum + value))
        .either
        .start
      for {
        _          <- run(mapStarted)
        _           = mapGate.succeed(1)
        _          <- run(foldStarted)
        _           = foldGate.succeed(1)
        mapResult  <- run(mapRunning)
        foldResult <- run(foldRunning)
        mapClosed  <- run(mapSource.isClosed)
        foldClosed <- run(foldSource.isClosed)
      } yield assertTrue(
        mapResult.left.exists {
          case error: StreamError => (error ne forgedMap) && error.value == "map-after-pending" && !error.isTrusted
          case _                  => false
        },
        foldResult.left.exists {
          case error: StreamError => (error ne forgedFold) && error.value == "fold-after-pending" && !error.isTrusted
          case _                  => false
        },
        mapSource.specializedReads.get == 2,
        foldSource.specializedReads.get == 2,
        mapClosed,
        foldClosed
      )
    },
    test("direct async map Long fold classifies suspended reader, map, and fold failures") {
      val ordinaryReadSource  = new NativePendingReader
      val trustedReadSource   = new NativePendingReader
      val mapSource           = new HostileIntReader(1)
      val foldSource          = new HostileIntReader(1)
      val mapGate             = new Completer[Int]
      val foldGate            = new Completer[Long]
      val mapStarted          = new Completer[Unit]
      val foldStarted         = new Completer[Unit]
      val ordinaryRead        = new RuntimeException("pending-read")
      val trustedRead         = StreamError.source("typed-read")
      val forgedMap           = new StreamError("pending-map")
      val forgedFold          = new StreamError("pending-fold")
      val ordinaryReadRunning = Stream
        .fromReaderAsync[String, Int](Async.succeed(ordinaryReadSource: Reader[Int]))
        .mapAsync(value => Async.succeed(value + 1))
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .either
        .start
      val trustedReadRunning = Stream
        .fromReaderAsync[String, Int](Async.succeed(trustedReadSource: Reader[Int]))
        .mapAsync(value => Async.succeed(value + 1))
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .either
        .start
      val mapRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(mapSource: Reader[Int]))
        .mapAsync { value => mapStarted.succeed(()); mapGate }
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .either
        .start
      val foldRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(foldSource: Reader[Int]))
        .mapAsync(Async.succeed)
        .runFoldAsync(0L) { (_, _) => foldStarted.succeed(()); foldGate }
        .either
        .start
      for {
        _                  <- run(ordinaryReadSource.started)
        _                   = ordinaryReadSource.fail(ordinaryRead)
        _                  <- run(trustedReadSource.started)
        _                   = trustedReadSource.failTrusted(trustedRead)
        _                  <- run(mapStarted)
        _                   = mapGate.failTrusted(forgedMap)
        _                  <- run(foldStarted)
        _                   = foldGate.failTrusted(forgedFold)
        ordinaryReadResult <- run(ordinaryReadRunning)
        trustedReadResult  <- run(trustedReadRunning)
        mapResult          <- run(mapRunning)
        foldResult         <- run(foldRunning)
      } yield assertTrue(
        ordinaryReadResult == Left(ordinaryRead),
        trustedReadResult == Right(Left("typed-read")),
        mapResult.left.exists {
          case error: StreamError => (error ne forgedMap) && error.value == "pending-map" && !error.isTrusted
          case _                  => false
        },
        foldResult.left.exists {
          case error: StreamError => (error ne forgedFold) && error.value == "pending-fold" && !error.isTrusted
          case _                  => false
        }
      )
    },
    test("direct async map Long fold follows replacement pollables and captures poll defects") {
      val mapSource     = new HostileIntReader(1)
      val foldSource    = new HostileIntReader(1)
      val replacingMap  = new Replacing[Int]
      val replacingFold = new Replacing[Long]
      val mapResult     = mapSource.specializedReads
      val mapRunning    = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(mapSource: Reader[Int]))
        .mapAsync(_ => replacingMap)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      val foldRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(foldSource: Reader[Int]))
        .mapAsync(Async.succeed)
        .runFoldAsync(0L)((_, _) => replacingFold)
        .start
      val readDefect     = new RuntimeException("read-poll")
      val mapDefect      = new StreamError("map-poll")
      val foldDefect     = new StreamError("fold-poll")
      val throwingReader = new Reader.AsyncReader[Int] {
        override def jvmType: JvmType                                               = JvmType.Int
        def close(): Async[Unit]                                                    = Async.succeed(())
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A]                                   = new Throwing[A](readDefect)
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = new Throwing[Long](readDefect)
      }
      val readFailure = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(throwingReader: Reader[Int]))
        .mapAsync(Async.succeed)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .either
      val mapFailure = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(new HostileIntReader(1): Reader[Int]))
        .mapAsync(_ => new Throwing[Int](mapDefect))
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .either
      val foldFailure = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(new HostileIntReader(1): Reader[Int]))
        .mapAsync(Async.succeed)
        .runFoldAsync(0L)((_, _) => new Throwing[Long](foldDefect))
        .either
      for {
        _                 <- run(replacingMap.started)
        _                  = replacingMap.succeed(2)
        _                 <- run(replacingFold.started)
        _                  = replacingFold.succeed(1L)
        mapped            <- run(mapRunning)
        folded            <- run(foldRunning)
        readFailureResult <- run(readFailure)
        mapFailureResult  <- run(mapFailure)
        foldFailureResult <- run(foldFailure)
      } yield assertTrue(
        mapped == Right(2L),
        folded == Right(1L),
        mapResult.get == 2,
        readFailureResult == Left(readDefect),
        mapFailureResult.left.exists {
          case error: StreamError => (error ne mapDefect) && error.value == "map-poll" && !error.isTrusted
          case _                  => false
        },
        foldFailureResult.left.exists {
          case error: StreamError => (error ne foldDefect) && error.value == "fold-poll" && !error.isTrusted
          case _                  => false
        }
      )
    },
    test("direct async map Long fold resumes EOF and a read-map-fold-read suspension ladder") {
      val eofSource    = new NativePendingReader
      val ladderSource = new TwoPendingIntReader
      val pendingMap   = new Replacing[Int]
      val pendingFold  = new Replacing[Long]
      val mapStarted   = new Completer[Unit]
      val foldStarted  = new Completer[Unit]
      val eofRunning   = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(eofSource: Reader[Int]))
        .mapAsync(Async.succeed)
        .runFoldAsync(7L)((sum, value) => Async.succeed(sum + value))
        .start
      val ladderRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(ladderSource: Reader[Int]))
        .mapAsync { value => mapStarted.succeed(()); pendingMap }
        .runFoldAsync(0L) { (_, _) => foldStarted.succeed(()); pendingFold }
        .start
      for {
        _      <- run(eofSource.started)
        _       = eofSource.eof()
        eof    <- run(eofRunning.either)
        _      <- run(ladderSource.firstStarted)
        _       = ladderSource.resumeFirst(1L)
        _      <- run(mapStarted)
        _       = pendingMap.succeed(2)
        _      <- run(foldStarted)
        _       = pendingFold.succeed(2L)
        _      <- run(ladderSource.secondStarted)
        _       = ladderSource.resumeSecond(Long.MinValue)
        ladder <- run(ladderRunning)
      } yield assertTrue(eof == Right(Right(7L)), ladder == Right(2L))
    },
    test("direct async map Long fold captures post-resume source, map, and fold defects") {
      val ordinaryRead                            = new RuntimeException("post-resume-read")
      val trustedRead                             = StreamError.source("post-resume-typed")
      val mapDefect                               = new StreamError("post-resume-map")
      val foldDefect                              = new StreamError("post-resume-fold")
      val ordinarySource                          = new PendingThenIntReader(_ => throw ordinaryRead)
      val trustedSource                           = new PendingThenIntReader(_ => throw trustedRead)
      val failedSource                            = new PendingThenIntReader(_ => Async.failTrusted(trustedRead))
      def folded[E](source: PendingThenIntReader) = Stream
        .fromReaderAsync[E, Int](Async.succeed(source: Reader[Int]))
        .mapAsync(Async.succeed)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .either
        .start
      val ordinaryRunning = folded[Nothing](ordinarySource)
      val trustedRunning  = folded[String](trustedSource)
      val failedRunning   = folded[String](failedSource)
      val mapSource       = new NativePendingReader
      val foldSource      = new NativePendingReader
      val mapRunning      = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(mapSource: Reader[Int]))
        .mapAsync[Int](_ => throw mapDefect)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .either
        .start
      val foldRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(foldSource: Reader[Int]))
        .mapAsync(Async.succeed)
        .runFoldAsync(0L)((_, _) => throw foldDefect)
        .either
        .start
      for {
        _              <- run(ordinarySource.started)
        _               = ordinarySource.resume()
        _              <- run(trustedSource.started)
        _               = trustedSource.resume()
        _              <- run(failedSource.started)
        _               = failedSource.resume()
        _              <- run(mapSource.started)
        _               = mapSource.resume()
        _              <- run(foldSource.started)
        _               = foldSource.resume()
        ordinaryResult <- run(ordinaryRunning)
        trustedResult  <- run(trustedRunning)
        failedResult   <- run(failedRunning)
        mapResult      <- run(mapRunning)
        foldResult     <- run(foldRunning)
      } yield assertTrue(
        ordinaryResult == Left(ordinaryRead),
        trustedResult == Right(Left("post-resume-typed")),
        failedResult == Right(Left("post-resume-typed")),
        mapResult.left.exists {
          case error: StreamError => (error ne mapDefect) && error.value == "post-resume-map" && !error.isTrusted
          case _                  => false
        },
        foldResult.left.exists {
          case error: StreamError => (error ne foldDefect) && error.value == "post-resume-fold" && !error.isTrusted
          case _                  => false
        }
      )
    },
    test("direct async map Long fold suspends and fails callbacks on later ready elements") {
      def twoValues(): PendingThenIntReader = {
        var emitted = false
        new PendingThenIntReader(sentinel =>
          if (!emitted) { emitted = true; Async.succeed(2L) }
          else Async.succeed(sentinel)
        )
      }
      val pendingMapSource   = twoValues()
      val pendingFoldSource  = twoValues()
      val throwingMapSource  = twoValues()
      val throwingFoldSource = twoValues()
      val pendingMap         = new Replacing[Int]
      val pendingFold        = new Replacing[Long]
      val mapDefect          = new StreamError("later-map")
      val foldDefect         = new StreamError("later-fold")
      val pendingMapRunning  = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(pendingMapSource: Reader[Int]))
        .mapAsync(value => if (value == 2) pendingMap else Async.succeed(value))
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      val pendingFoldRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(pendingFoldSource: Reader[Int]))
        .mapAsync(Async.succeed)
        .runFoldAsync(0L)((sum, value) => if (value == 2) pendingFold else Async.succeed(sum + value))
        .start
      val throwingMapRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(throwingMapSource: Reader[Int]))
        .mapAsync[Int](value => if (value == 2) throw mapDefect else Async.succeed(value))
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .either
        .start
      val throwingFoldRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(throwingFoldSource: Reader[Int]))
        .mapAsync(Async.succeed)
        .runFoldAsync(0L)((sum, value) => if (value == 2) throw foldDefect else Async.succeed(sum + value))
        .either
        .start
      for {
        _                  <- run(pendingMapSource.started)
        _                   = pendingMapSource.resume()
        _                  <- run(pendingFoldSource.started)
        _                   = pendingFoldSource.resume()
        _                  <- run(throwingMapSource.started)
        _                   = throwingMapSource.resume()
        _                  <- run(throwingFoldSource.started)
        _                   = throwingFoldSource.resume()
        _                  <- run(pendingMap.started)
        _                   = pendingMap.succeed(2)
        _                  <- run(pendingFold.started)
        _                   = pendingFold.succeed(3L)
        pendingMapResult   <- run(pendingMapRunning)
        pendingFoldResult  <- run(pendingFoldRunning)
        throwingMapResult  <- run(throwingMapRunning)
        throwingFoldResult <- run(throwingFoldRunning)
      } yield assertTrue(
        pendingMapResult == Right(3L),
        pendingFoldResult == Right(3L),
        throwingMapResult.left.exists {
          case error: StreamError => (error ne mapDefect) && error.value == "later-map" && !error.isTrusted
          case _                  => false
        },
        throwingFoldResult.left.exists {
          case error: StreamError => (error ne foldDefect) && error.value == "later-fold" && !error.isTrusted
          case _                  => false
        }
      )
    },
    test("direct async map Long fold yields after an initial suspension") {
      var next   = 1
      val source = new PendingThenIntReader(sentinel =>
        if (next <= 1025) {
          val value = next
          next += 1
          Async.succeed(value.toLong)
        } else Async.succeed(sentinel)
      )
      val running = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .mapAsync(Async.succeed)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      for {
        _      <- run(source.started)
        _       = source.resume(0L)
        result <- run(running)
      } yield assertTrue(result == Right(525825L), next == 1026)
    },
    test("direct async map Long fold lets cancellation win every continuation-ownership boundary") {
      def source(readEffect: Async[Long]) = new Reader.AsyncReader[Int] {
        override def jvmType: JvmType                                               = JvmType.Int
        def close(): Async[Unit]                                                    = Async.succeed(())
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.fail(new AssertionError("generic read"))
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = readEffect
      }
      val eofChild                                         = new CancelOnPoll[Long](Async.succeed(Long.MinValue))
      val readChild                                        = new CancelOnPoll[Long](Async.succeed(1L))
      val mapChild                                         = new CancelOnPoll[Int](Async.succeed(1))
      val foldChild                                        = new CancelOnPoll[Long](Async.succeed(1L))
      val acquiredReader                                   = new HostileIntReader(1)
      val acquisitionChild                                 = new CancelOnPoll[Reader[Int]](Async.succeed(acquiredReader: Reader[Int]))
      var acquisitionRunning: Async[Either[Nothing, Long]] = null
      var eofRunning: Async[Either[Nothing, Long]]         = null
      var readRunning: Async[Either[Nothing, Long]]        = null
      var mapRunning: Async[Either[Nothing, Long]]         = null
      var foldRunning: Async[Either[Nothing, Long]]        = null
      var eofCleanup: Async[Unit]                          = null
      var readCleanup: Async[Unit]                         = null
      var mapCleanup: Async[Unit]                          = null
      var foldCleanup: Async[Unit]                         = null
      var acquisitionCleanup: Async[Unit]                  = null
      val acquisitionCancelled                             = new Completer[Unit]
      val eofCancelled                                     = new Completer[Unit]
      val readCancelled                                    = new Completer[Unit]
      val mapCancelled                                     = new Completer[Unit]
      val foldCancelled                                    = new Completer[Unit]
      acquisitionRunning = Stream
        .fromReaderAsync[Nothing, Int](acquisitionChild)
        .mapAsync(Async.succeed)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      eofRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source(eofChild): Reader[Int]))
        .mapAsync(Async.succeed)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      readRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source(readChild): Reader[Int]))
        .mapAsync(Async.succeed)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      mapRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(new HostileIntReader(1): Reader[Int]))
        .mapAsync(_ => mapChild)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      foldRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(new HostileIntReader(1): Reader[Int]))
        .mapAsync(Async.succeed)
        .runFoldAsync(0L)((_, _) => foldChild)
        .start
      for {
        _ <- run(acquisitionChild.started)
        _  = acquisitionChild.arm { () =>
              acquisitionCleanup = Async.cancelWithCleanup(acquisitionRunning.asInstanceOf[Pollable[Any]])
              acquisitionCancelled.succeed(())
            }
        _ <- run(eofChild.started)
        _  = eofChild.arm(() =>
              Async.startRegistered(Async.cancelWithCleanup(eofRunning.asInstanceOf[Pollable[Any]])) { running =>
                eofCleanup = running; eofCancelled.succeed(())
              }
            )
        _ <- run(readChild.started)
        _  = readChild.arm(() =>
              Async.startRegistered(Async.cancelWithCleanup(readRunning.asInstanceOf[Pollable[Any]])) { running =>
                readCleanup = running; readCancelled.succeed(())
              }
            )
        _ <- run(mapChild.started)
        _  = mapChild.arm(() =>
              Async.startRegistered(Async.cancelWithCleanup(mapRunning.asInstanceOf[Pollable[Any]])) { running =>
                mapCleanup = running; mapCancelled.succeed(())
              }
            )
        _ <- run(foldChild.started)
        _  = foldChild.arm(() =>
              Async.startRegistered(Async.cancelWithCleanup(foldRunning.asInstanceOf[Pollable[Any]])) { running =>
                foldCleanup = running; foldCancelled.succeed(())
              }
            )
        _                 <- run(eofCancelled)
        _                 <- run(readCancelled)
        _                 <- run(mapCancelled)
        _                 <- run(foldCancelled)
        _                 <- run(acquisitionCancelled)
        _                 <- run(acquisitionCleanup)
        _                 <- run(eofCleanup)
        _                 <- run(readCleanup)
        _                 <- run(mapCleanup)
        _                 <- run(foldCleanup)
        acquisitionClosed <- run(acquiredReader.isClosed)
      } yield assertTrue(acquisitionClosed)
    },
    test("direct async map Long fold resumes pending callbacks after a fairness yield") {
      val values      = 0 until 1026
      val mapSource   = new HostileIntReader(values: _*)
      val foldSource  = new HostileIntReader(values: _*)
      val mapStarted  = new Completer[Unit]
      val foldStarted = new Completer[Unit]
      val pendingMap  = new Completer[Int]
      val pendingFold = new Completer[Long]
      val mapRunning  = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(mapSource: Reader[Int]))
        .mapAsync { value =>
          if (value == 1024) { mapStarted.succeed(()); pendingMap }
          else Async.succeed(value + 1)
        }
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      val foldRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(foldSource: Reader[Int]))
        .mapAsync(value => Async.succeed(value + 1))
        .runFoldAsync(0L) { (sum, value) =>
          if (value == 1025) { foldStarted.succeed(()); pendingFold }
          else Async.succeed(sum + value)
        }
        .start
      for {
        _          <- run(mapStarted)
        _           = pendingMap.succeed(1025)
        mapResult  <- run(mapRunning)
        _          <- run(foldStarted)
        _           = pendingFold.succeed(525825L)
        foldResult <- run(foldRunning)
      } yield assertTrue(
        mapResult == Right(526851L),
        foldResult == Right(526851L),
        mapSource.specializedReads.get == 1027,
        foldSource.specializedReads.get == 1027
      )
    },
    test("direct async map Long fold cancellation joins its pending map before closing") {
      val source   = new HostileIntReader(1)
      val callback = new CancelledRead
      val running  = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .mapAsync(_ => callback.asInstanceOf[Async[Int]])
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      for {
        _      <- run(callback.started)
        cleanup = Async.cancelWithCleanup(running)
        pending = fold(cleanup).isInstanceOf[Pending[_]]
        _       = callback.cleanup.succeed(())
        _      <- run(cleanup)
        closed <- run(source.isClosed)
      } yield assertTrue(pending, callback.cancelled.get == 1, source.specializedReads.get == 1, closed)
    },
    test("synchronous map after async map stays on the direct primitive fold") {
      val source      = new HostileIntReader(1, 2, 3)
      val asyncMaps   = new AtomicInteger
      val syncMaps    = new AtomicInteger
      val folds       = new AtomicInteger
      val pendingMap  = new Completer[Int]
      val pendingFold = new Completer[Long]
      val running     = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .mapAsync { value => asyncMaps.incrementAndGet(); if (value == 2) pendingMap else Async.succeed(value) }
        .map { value => syncMaps.incrementAndGet(); value + 1 }
        .runFoldAsync(0L) { (sum, value) =>
          folds.incrementAndGet()
          if (value == 3) pendingFold else Async.succeed(sum + value)
        }
        .start
      pendingMap.succeed(2)
      pendingFold.succeed(5L)
      for {
        result <- run(running)
        closed <- run(source.isClosed)
      } yield assertTrue(
        result == Right(9L),
        asyncMaps.get == 3,
        syncMaps.get == 3,
        folds.get == 3,
        source.specializedReads.get == 4,
        closed
      )
    },
    test("async map direct folds decline non-Int inputs") {
      val source = Reader.fromIterable(List("1", "2", "3")).toAsync
      val effect = Stream
        .fromReaderAsync[Nothing, String](Async.succeed(source: Reader[String]))
        .mapAsync(value => Async.succeed(value.toInt))
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
      run(effect).map(result => assertTrue(result == Right(6L)))
    },
    test("synchronous map after a pending async map classifies forged StreamErrors as defects") {
      val source  = new HostileIntReader(1, 2)
      val pending = new Completer[Int]
      val forged  = new StreamError("sync-map-after-pending")
      val running = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .mapAsync(value => if (value == 2) pending else Async.succeed(value))
        .map(value => if (value == 2) throw forged else value)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .either
        .start
      pending.succeed(2)
      for {
        result <- run(running)
        closed <- run(source.isClosed)
      } yield assertTrue(
        result.left.exists {
          case error: StreamError => (error ne forged) && error.value == forged.value && !error.isTrusted
          case _                  => false
        },
        source.specializedReads.get == 2,
        closed
      )
    },
    test("synchronous map after a non-source async map retains its direct pending continuation") {
      val pending = new Completer[Int]
      val running = Stream
        .range(1, 4)
        .filter(_ > 0)
        .mapAsync(value => if (value == 2) pending else Async.succeed(value))
        .map(_ + 1)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      pending.succeed(2)
      run(running).map(result => assertTrue(result == Right(9L)))
    },
    test("direct synchronous map Long fold resumes pending reads and folds") {
      val readSource  = new NativePendingReader
      val foldSource  = new HostileIntReader(1, 2, 3)
      val pendingFold = new Completer[Long]
      val readRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(readSource: Reader[Int]))
        .map(_ + 1)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      val foldRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(foldSource: Reader[Int]))
        .map(_ + 1)
        .runFoldAsync(0L)((sum, value) => if (value == 3) pendingFold else Async.succeed(sum + value))
        .start
      readSource.resume()
      pendingFold.succeed(5L)
      for {
        readResult <- run(readRunning)
        foldResult <- run(foldRunning)
      } yield assertTrue(
        readResult == Right(9L),
        foldResult == Right(9L),
        readSource.closes.get == 1,
        foldSource.specializedReads.get == 4
      )
    },
    test("direct synchronous map Long fold preserves callback defects after a fairness yield") {
      val source = new HostileIntReader((0 until 1026): _*)
      val forged = new StreamError("sync-map-after-yield")
      val effect = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .map(value => if (value == 1024) throw forged else value + 1)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .either
      for {
        result <- run(effect)
        closed <- run(source.isClosed)
      } yield assertTrue(
        result.left.exists {
          case error: StreamError =>
            (error ne forged) && error.value == "sync-map-after-yield" && !error.isTrusted
          case _ => false
        },
        source.specializedReads.get == 1025,
        closed
      )
    },
    test("direct Int to Long map preserves primitive values and resumes the Long fold") {
      val source      = new HostileIntReader(Int.MinValue, 5)
      val maps        = new AtomicInteger
      val folds       = new AtomicInteger
      val pendingFold = new Completer[Long]
      val running     = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .map { value => maps.incrementAndGet(); if (value == Int.MinValue) Long.MinValue else value.toLong }
        .runFoldAsync(0L) { (sum, value) =>
          folds.incrementAndGet()
          if (value == Long.MinValue) pendingFold else Async.succeed(sum + value)
        }
        .start
      pendingFold.succeed(Long.MinValue)
      for {
        result <- run(running)
        closed <- run(source.isClosed)
      } yield assertTrue(
        result == Right(Long.MinValue + 5L),
        maps.get == 2,
        folds.get == 2,
        source.specializedReads.get == 3,
        closed
      )
    },
    test("direct Int to Long map resumes a pending physical read") {
      val source  = new NativePendingReader
      val maps    = new AtomicInteger
      val folds   = new AtomicInteger
      val running = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .map { value => maps.incrementAndGet(); value.toLong * 10L }
        .runFoldAsync(0L) { (sum, value) => folds.incrementAndGet(); Async.succeed(sum + value) }
        .start
      source.resume()
      for {
        result <- run(running)
      } yield assertTrue(
        result == Right(60L),
        maps.get == 3,
        folds.get == 3,
        source.specializedReads.get == 4,
        source.closes.get == 1
      )
    },
    test("direct Int to Long map preserves transform defects after a fairness yield") {
      val source = new HostileIntReader((0 until 1026): _*)
      val forged = new StreamError("int-long-map-after-yield")
      val effect = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .map(value => if (value == 1024) throw forged else value.toLong)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .either
      for {
        result <- run(effect)
        closed <- run(source.isClosed)
      } yield assertTrue(
        result.left.exists {
          case error: StreamError =>
            (error ne forged) && error.value == "int-long-map-after-yield" && !error.isTrusted
          case _ => false
        },
        source.specializedReads.get == 1025,
        closed
      )
    },
    test("direct Int to Long map followed by an asynchronous Long map resumes callbacks") {
      val source      = new HostileIntReader(1, 2)
      val transforms  = new AtomicInteger
      val maps        = new AtomicInteger
      val folds       = new AtomicInteger
      val pendingMap  = new Completer[Long]
      val pendingFold = new Completer[Long]
      val running     = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .map { value => transforms.incrementAndGet(); value.toLong * 10L }
        .mapAsync { value => maps.incrementAndGet(); if (value == 10L) pendingMap else Async.succeed(value + 1L) }
        .runFoldAsync(0L) { (sum, value) =>
          folds.incrementAndGet()
          if (value == 21L) pendingFold else Async.succeed(sum + value)
        }
        .start
      pendingMap.succeed(11L)
      pendingFold.succeed(32L)
      for {
        result <- run(running)
        closed <- run(source.isClosed)
      } yield assertTrue(
        result == Right(32L),
        transforms.get == 2,
        maps.get == 2,
        folds.get == 2,
        source.specializedReads.get == 3,
        closed
      )
    },
    test("direct Int to Long asynchronous map classifies callback defects after fairness yields") {
      val transformSource  = new HostileIntReader((0 until 1026): _*)
      val mapSource        = new HostileIntReader((0 until 1026): _*)
      val foldSource       = new HostileIntReader((0 until 1026): _*)
      val transformFailure = new StreamError("long-callback-transform-after-yield")
      val mapFailure       = new StreamError("long-callback-map-after-yield")
      val foldFailure      = new StreamError("long-callback-fold-after-yield")
      val transformEffect  = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(transformSource: Reader[Int]))
        .map(value => if (value == 1024) throw transformFailure else value.toLong)
        .mapAsync(Async.succeed)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .either
      val mapEffect = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(mapSource: Reader[Int]))
        .map(_.toLong)
        .mapAsync(value => if (value == 1024L) Async.fail(mapFailure) else Async.succeed(value))
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .either
      val foldEffect = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(foldSource: Reader[Int]))
        .map(_.toLong)
        .mapAsync(value => Async.succeed(value + 1L))
        .runFoldAsync(0L)((sum, value) => if (value == 1025L) Async.fail(foldFailure) else Async.succeed(sum + value))
        .either
      for {
        transformResult <- run(transformEffect)
        mapResult       <- run(mapEffect)
        foldResult      <- run(foldEffect)
        transformClosed <- run(transformSource.isClosed)
        mapClosed       <- run(mapSource.isClosed)
        foldClosed      <- run(foldSource.isClosed)
      } yield assertTrue(
        transformResult.left.exists {
          case error: StreamError =>
            (error ne transformFailure) && error.value == transformFailure.value && !error.isTrusted
          case _ => false
        },
        mapResult.left.exists {
          case error: StreamError => (error ne mapFailure) && error.value == mapFailure.value && !error.isTrusted
          case _                  => false
        },
        foldResult.left.exists {
          case error: StreamError => (error ne foldFailure) && error.value == foldFailure.value && !error.isTrusted
          case _                  => false
        },
        transformSource.specializedReads.get == 1025,
        mapSource.specializedReads.get == 1025,
        foldSource.specializedReads.get == 1025,
        transformClosed,
        mapClosed,
        foldClosed
      )
    },
    test("direct Int to Long asynchronous map supports unprotected acquisition") {
      run(
        Stream
          .attemptAsync(Async.succeed(1))
          .map(_.toLong)
          .mapAsync(value => Async.succeed(value + 1L))
          .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
      ).map(result => assertTrue(result == Right(2L)))
    },
    test("direct Int to Float and Double folds cover protected acquisition failure and unprotected sources") {
      val floatAcquisition  = new StreamError("float-acquisition")
      val doubleAcquisition = new StreamError("double-acquisition")
      val protectedFloat    = Stream
        .fromReaderAsync[Nothing, Int](throw floatAcquisition)
        .map(_.toFloat)
        .runFoldAsync(0.0f)((sum, value) => Async.succeed(sum + value))
        .either
      val protectedDouble = Stream
        .fromReaderAsync[Nothing, Int](throw doubleAcquisition)
        .map(_.toDouble)
        .runFoldAsync(0.0d)((sum, value) => Async.succeed(sum + value))
        .either
      val unprotectedFloat = Stream
        .attemptAsync(Async.succeed(2))
        .map(_.toFloat)
        .runFoldAsync(1.0f)((sum, value) => Async.succeed(sum + value))
      val unprotectedDouble = Stream
        .attemptAsync(Async.succeed(2))
        .map(_.toDouble)
        .runFoldAsync(1.0d)((sum, value) => Async.succeed(sum + value))
      for {
        protectedFloatResult  <- run(protectedFloat)
        protectedDoubleResult <- run(protectedDouble)
        floatResult           <- run(unprotectedFloat)
        doubleResult          <- run(unprotectedDouble)
      } yield assertTrue(
        protectedFloatResult.left.exists {
          case error: StreamError =>
            (error ne floatAcquisition) && !error.isTrusted && error.value == "float-acquisition"
          case _ => false
        },
        protectedDoubleResult.left.exists {
          case error: StreamError =>
            (error ne doubleAcquisition) && !error.isTrusted && error.value == "double-acquisition"
          case _ => false
        },
        floatResult == Right(3.0f),
        doubleResult == Right(3.0d)
      )
    },
    test("direct Int to Double map preserves primitive values and resumes the Double fold") {
      val source      = new HostileIntReader(Int.MinValue, 5)
      val maps        = new AtomicInteger
      val folds       = new AtomicInteger
      val pendingFold = new Completer[Double]
      val running     = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .map { value => maps.incrementAndGet(); if (value == Int.MinValue) Double.NaN else value.toDouble }
        .runFoldAsync(0.0d) { (sum, value) =>
          folds.incrementAndGet()
          if (value.isNaN) pendingFold else Async.succeed(sum + value)
        }
        .start
      pendingFold.succeed(Double.NaN)
      for {
        result <- run(running)
        closed <- run(source.isClosed)
      } yield assertTrue(
        result.exists(_.isNaN),
        maps.get == 2,
        folds.get == 2,
        source.specializedReads.get == 3,
        closed
      )
    },
    test("direct Int to Double map resumes a pending physical read") {
      val source  = new NativePendingReader
      val maps    = new AtomicInteger
      val folds   = new AtomicInteger
      val running = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .map { value => maps.incrementAndGet(); value.toDouble * 10.0d }
        .runFoldAsync(0.0d) { (sum, value) => folds.incrementAndGet(); Async.succeed(sum + value) }
        .start
      source.resume()
      for {
        result <- run(running)
      } yield assertTrue(
        result == Right(60.0d),
        maps.get == 3,
        folds.get == 3,
        source.specializedReads.get == 4,
        source.closes.get == 1
      )
    },
    test("direct Int to Double map preserves transform defects after a fairness yield") {
      val source = new HostileIntReader((0 until 1026): _*)
      val forged = new StreamError("int-double-map-after-yield")
      val effect = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .map(value => if (value == 1024) throw forged else value.toDouble)
        .runFoldAsync(0.0d)((sum, value) => Async.succeed(sum + value))
        .either
      for {
        result <- run(effect)
        closed <- run(source.isClosed)
      } yield assertTrue(
        result.left.exists {
          case error: StreamError =>
            (error ne forged) && error.value == "int-double-map-after-yield" && !error.isTrusted
          case _ => false
        },
        source.specializedReads.get == 1025,
        closed
      )
    },
    test("direct Int to Double map followed by an asynchronous Double map resumes callbacks") {
      val source      = new HostileIntReader(1, 2)
      val transforms  = new AtomicInteger
      val maps        = new AtomicInteger
      val folds       = new AtomicInteger
      val pendingMap  = new Completer[Double]
      val pendingFold = new Completer[Double]
      val running     = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .map { value => transforms.incrementAndGet(); value.toDouble * 10.0d }
        .mapAsync { value => maps.incrementAndGet(); if (value == 10.0d) pendingMap else Async.succeed(value + 1.0d) }
        .runFoldAsync(0.0d) { (sum, value) =>
          folds.incrementAndGet()
          if (value == 21.0d) pendingFold else Async.succeed(sum + value)
        }
        .start
      pendingMap.succeed(11.0d)
      pendingFold.succeed(32.0d)
      for {
        result <- run(running)
        closed <- run(source.isClosed)
      } yield assertTrue(
        result == Right(32.0d),
        transforms.get == 2,
        maps.get == 2,
        folds.get == 2,
        source.specializedReads.get == 3,
        closed
      )
    },
    test("direct Int to Double asynchronous map classifies callback defects after fairness yields") {
      val transformSource  = new HostileIntReader((0 until 1026): _*)
      val mapSource        = new HostileIntReader((0 until 1026): _*)
      val foldSource       = new HostileIntReader((0 until 1026): _*)
      val transformFailure = new StreamError("double-callback-transform-after-yield")
      val mapFailure       = new StreamError("double-callback-map-after-yield")
      val foldFailure      = new StreamError("double-callback-fold-after-yield")
      val transformEffect  = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(transformSource: Reader[Int]))
        .map(value => if (value == 1024) throw transformFailure else value.toDouble)
        .mapAsync(Async.succeed)
        .runFoldAsync(0.0d)((sum, value) => Async.succeed(sum + value))
        .either
      val mapEffect = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(mapSource: Reader[Int]))
        .map(_.toDouble)
        .mapAsync(value => if (value == 1024.0d) Async.fail(mapFailure) else Async.succeed(value))
        .runFoldAsync(0.0d)((sum, value) => Async.succeed(sum + value))
        .either
      val foldEffect = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(foldSource: Reader[Int]))
        .map(_.toDouble)
        .mapAsync(value => Async.succeed(value + 1.0d))
        .runFoldAsync(0.0d) { (sum, value) =>
          if (value == 1025.0d) Async.fail(foldFailure) else Async.succeed(sum + value)
        }
        .either
      for {
        transformResult <- run(transformEffect)
        mapResult       <- run(mapEffect)
        foldResult      <- run(foldEffect)
        transformClosed <- run(transformSource.isClosed)
        mapClosed       <- run(mapSource.isClosed)
        foldClosed      <- run(foldSource.isClosed)
      } yield assertTrue(
        transformResult.left.exists {
          case error: StreamError =>
            (error ne transformFailure) && error.value == transformFailure.value && !error.isTrusted
          case _ => false
        },
        mapResult.left.exists {
          case error: StreamError => (error ne mapFailure) && error.value == mapFailure.value && !error.isTrusted
          case _                  => false
        },
        foldResult.left.exists {
          case error: StreamError => (error ne foldFailure) && error.value == foldFailure.value && !error.isTrusted
          case _                  => false
        },
        transformSource.specializedReads.get == 1025,
        mapSource.specializedReads.get == 1025,
        foldSource.specializedReads.get == 1025,
        transformClosed,
        mapClosed,
        foldClosed
      )
    },
    test("direct Int to Double asynchronous map supports unprotected acquisition") {
      run(
        Stream
          .attemptAsync(Async.succeed(1))
          .map(_.toDouble)
          .mapAsync(value => Async.succeed(value + 1.0d))
          .runFoldAsync(0.0d)((sum, value) => Async.succeed(sum + value))
      ).map(result => assertTrue(result == Right(2.0d)))
    },
    test("direct Int to Float map preserves primitive values and resumes the Float fold") {
      val source      = new HostileIntReader(Int.MinValue, 5)
      val maps        = new AtomicInteger
      val folds       = new AtomicInteger
      val pendingFold = new Completer[Float]
      val running     = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .map { value => maps.incrementAndGet(); if (value == Int.MinValue) Float.NaN else value.toFloat }
        .runFoldAsync[Float](0.0f) { (sum, value) =>
          folds.incrementAndGet()
          if (value.isNaN) pendingFold else Async.succeed(sum + value)
        }
        .start
      pendingFold.succeed(Float.NaN)
      for {
        result <- run(running)
        closed <- run(source.isClosed)
      } yield assertTrue(
        result.exists(_.isNaN),
        maps.get == 2,
        folds.get == 2,
        source.specializedReads.get == 3,
        closed
      )
    },
    test("direct Int to Float map resumes a pending physical read") {
      val source  = new NativePendingReader
      val maps    = new AtomicInteger
      val folds   = new AtomicInteger
      val running = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .map { value => maps.incrementAndGet(); value.toFloat * 10.0f }
        .runFoldAsync[Float](0.0f) { (sum, value) => folds.incrementAndGet(); Async.succeed(sum + value) }
        .start
      source.resume()
      for {
        result <- run(running)
      } yield assertTrue(
        result == Right(60.0f),
        maps.get == 3,
        folds.get == 3,
        source.specializedReads.get == 4,
        source.closes.get == 1
      )
    },
    test("direct Int to Float map preserves transform defects after a fairness yield") {
      val source = new HostileIntReader((0 until 1026): _*)
      val forged = new StreamError("int-float-map-after-yield")
      val effect = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .map(value => if (value == 1024) throw forged else value.toFloat)
        .runFoldAsync[Float](0.0f)((sum, value) => Async.succeed(sum + value))
        .either
      for {
        result <- run(effect)
        closed <- run(source.isClosed)
      } yield assertTrue(
        result.left.exists {
          case error: StreamError =>
            (error ne forged) && error.value == "int-float-map-after-yield" && !error.isTrusted
          case _ => false
        },
        source.specializedReads.get == 1025,
        closed
      )
    },
    test("direct Int to Float map followed by an asynchronous Float map resumes callbacks") {
      val source      = new HostileIntReader(1, 2)
      val transforms  = new AtomicInteger
      val maps        = new AtomicInteger
      val folds       = new AtomicInteger
      val pendingMap  = new Completer[Float]
      val pendingFold = new Completer[Float]
      val running     = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .map { value => transforms.incrementAndGet(); value.toFloat * 10.0f }
        .mapAsync { value => maps.incrementAndGet(); if (value == 10.0f) pendingMap else Async.succeed(value + 1.0f) }
        .runFoldAsync[Float](0.0f) { (sum, value) =>
          folds.incrementAndGet()
          if (value == 21.0f) pendingFold else Async.succeed(sum + value)
        }
        .start
      pendingMap.succeed(11.0f)
      pendingFold.succeed(32.0f)
      for {
        result <- run(running)
        closed <- run(source.isClosed)
      } yield assertTrue(
        result == Right(32.0f),
        transforms.get == 2,
        maps.get == 2,
        folds.get == 2,
        source.specializedReads.get == 3,
        closed
      )
    },
    test("direct Int to Float asynchronous map classifies callback defects after fairness yields") {
      val transformSource  = new HostileIntReader((0 until 1026): _*)
      val mapSource        = new HostileIntReader((0 until 1026): _*)
      val foldSource       = new HostileIntReader((0 until 1026): _*)
      val transformFailure = new StreamError("float-callback-transform-after-yield")
      val mapFailure       = new StreamError("float-callback-map-after-yield")
      val foldFailure      = new StreamError("float-callback-fold-after-yield")
      val transformEffect  = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(transformSource: Reader[Int]))
        .map(value => if (value == 1024) throw transformFailure else value.toFloat)
        .mapAsync(Async.succeed)
        .runFoldAsync[Float](0.0f)((sum, value) => Async.succeed(sum + value))
        .either
      val mapEffect = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(mapSource: Reader[Int]))
        .map(_.toFloat)
        .mapAsync(value => if (value == 1024.0f) Async.fail(mapFailure) else Async.succeed(value))
        .runFoldAsync[Float](0.0f)((sum, value) => Async.succeed(sum + value))
        .either
      val foldEffect = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(foldSource: Reader[Int]))
        .map(_.toFloat)
        .mapAsync(value => Async.succeed(value + 1.0f))
        .runFoldAsync[Float](0.0f) { (sum, value) =>
          if (value == 1025.0f) Async.fail(foldFailure) else Async.succeed(sum + value)
        }
        .either
      for {
        transformResult <- run(transformEffect)
        mapResult       <- run(mapEffect)
        foldResult      <- run(foldEffect)
        transformClosed <- run(transformSource.isClosed)
        mapClosed       <- run(mapSource.isClosed)
        foldClosed      <- run(foldSource.isClosed)
      } yield assertTrue(
        transformResult.left.exists {
          case error: StreamError =>
            (error ne transformFailure) && error.value == transformFailure.value && !error.isTrusted
          case _ => false
        },
        mapResult.left.exists {
          case error: StreamError => (error ne mapFailure) && error.value == mapFailure.value && !error.isTrusted
          case _                  => false
        },
        foldResult.left.exists {
          case error: StreamError => (error ne foldFailure) && error.value == foldFailure.value && !error.isTrusted
          case _                  => false
        },
        transformSource.specializedReads.get == 1025,
        mapSource.specializedReads.get == 1025,
        foldSource.specializedReads.get == 1025,
        transformClosed,
        mapClosed,
        foldClosed
      )
    },
    test("direct Int to Float asynchronous map supports unprotected acquisition") {
      run(
        Stream
          .attemptAsync(Async.succeed(1))
          .map(_.toFloat)
          .mapAsync(value => Async.succeed(value + 1.0f))
          .runFoldAsync[Float](0.0f)((sum, value) => Async.succeed(sum + value))
      ).map(result => assertTrue(result == Right(2.0f)))
    },
    test("direct Int accumulator folds preserve transformed primitive semantics") {
      val booleanSource = new HostileIntReader(0, 1, 2)
      val byteSource    = new HostileIntReader(0, 1, 2)
      val charSource    = new HostileIntReader(0, 1, 2)
      val shortSource   = new HostileIntReader(0, 1, 2)
      val boolean       = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(booleanSource: Reader[Int]))
        .map(value => (value & 1) == 0)
        .runFoldAsync(0)((sum, value) => Async.succeed(sum + (if (value) 1 else 0)))
      val byte = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(byteSource: Reader[Int]))
        .map(_.toByte)
        .runFoldAsync(0)((sum, value) => Async.succeed(sum + value))
      val char = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(charSource: Reader[Int]))
        .map(_.toChar)
        .runFoldAsync(0)((sum, value) => Async.succeed(sum + value.toInt))
      val short = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(shortSource: Reader[Int]))
        .map(_.toShort)
        .runFoldAsync(0)((sum, value) => Async.succeed(sum + value))
      for {
        booleanResult <- run(boolean)
        byteResult    <- run(byte)
        charResult    <- run(char)
        shortResult   <- run(short)
        booleanClosed <- run(booleanSource.isClosed)
        byteClosed    <- run(byteSource.isClosed)
        charClosed    <- run(charSource.isClosed)
        shortClosed   <- run(shortSource.isClosed)
      } yield assertTrue(
        booleanResult == Right(2),
        byteResult == Right(3),
        charResult == Right(3),
        shortResult == Right(3),
        List(booleanSource, byteSource, charSource, shortSource).forall(_.specializedReads.get == 4),
        booleanClosed,
        byteClosed,
        charClosed,
        shortClosed
      )
    },
    test("direct Int accumulator mapped fold preserves defects after fairness and unprotected acquisition") {
      val transformSource  = new HostileIntReader((0 until 1026): _*)
      val foldSource       = new HostileIntReader((0 until 1026): _*)
      val transformFailure = new StreamError("int-accumulator-transform-after-yield")
      val foldFailure      = new StreamError("int-accumulator-fold-after-yield")
      val foldCalls        = new AtomicInteger
      val transformEffect  = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(transformSource: Reader[Int]))
        .map(value => if (value == 1024) throw transformFailure else (value & 1) == 0)
        .runFoldAsync(0)((sum, value) => Async.succeed(sum + (if (value) 1 else 0)))
        .either
      val foldEffect = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(foldSource: Reader[Int]))
        .map(value => (value & 1) == 0)
        .runFoldAsync(0) { (sum, value) =>
          if (foldCalls.incrementAndGet() == 1025) Async.fail(foldFailure)
          else Async.succeed(sum + (if (value) 1 else 0))
        }
        .either
      val unprotected = Stream
        .attemptAsync(Async.succeed(1))
        .map(value => (value & 1) == 1)
        .runFoldAsync(0)((sum, value) => Async.succeed(sum + (if (value) 1 else 0)))
      for {
        transformResult <- run(transformEffect)
        foldResult      <- run(foldEffect)
        directResult    <- run(unprotected)
        transformClosed <- run(transformSource.isClosed)
        foldClosed      <- run(foldSource.isClosed)
      } yield assertTrue(
        transformResult.left.exists {
          case error: StreamError =>
            (error ne transformFailure) && error.value == transformFailure.value && !error.isTrusted
          case _ => false
        },
        foldResult.left.exists {
          case error: StreamError => (error ne foldFailure) && error.value == foldFailure.value && !error.isTrusted
          case _                  => false
        },
        directResult == Right(1),
        transformSource.specializedReads.get == 1025,
        foldSource.specializedReads.get == 1025,
        transformClosed,
        foldClosed
      )
    },
    test("async Long folds over filtered and mapped synchronous Booleans stay on physical lanes") {
      val source                                                = new HostileSyncBooleanReader(false, true, false, true)
      val pendingFold                                           = new Completer[Long]
      val folds                                                 = new AtomicInteger
      def drive[A](effect: Async[A], remaining: Int): Folded[A] = fold(effect) match {
        case Pending(operation) if folds.get < 2 && remaining > 0 => drive(operation.poll(() => ()), remaining - 1)
        case result                                               => result
      }
      val running = Stream
        .fromReader[Nothing, Boolean](source)
        .filter(identity)
        .map(value => if (value) 10L else 20L)
        .runFoldAsync(0L) { (sum, value) =>
          if (folds.incrementAndGet() == 2) pendingFold else Async.succeed(sum + value)
        }
      val firstPoll    = drive(running, 8)
      val beforeResume = (folds.get(), source.specializedReads.get, source.closes.get)
      pendingFold.succeed(20L)
      run(running).map(result =>
        assertTrue(
          firstPoll.isInstanceOf[Pending[_]],
          beforeResume == ((2, 4, 0)),
          result == Right(20L),
          folds.get == 2,
          source.specializedReads.get == 5,
          source.closes.get == 1
        )
      )
    },
    test("direct sync-to-async map Long fold resumes pending maps and folds and yields fairly") {
      val pendingMap  = new Completer[Int]
      val pendingFold = new Completer[Long]
      val callbacks   = new AtomicInteger
      val fairMap     = new Completer[Int]
      val fairStarted = new Completer[Unit]
      val yieldPermit = new Completer[Unit]
      val mapRunning  = Stream(1, 2, 3)
        .mapAsync(value => if (value == 2) pendingMap else Async.succeed(value + 1))
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      val foldRunning = Stream(1, 2, 3)
        .mapAsync(value => Async.succeed(value + 1))
        .runFoldAsync(0L)((sum, value) => if (value == 3) pendingFold else Async.succeed(sum + value))
        .start
      val yielding = Stream
        .range(0, 2049)
        .mapAsync { value =>
          if (value == 1024) {
            fairStarted.succeed(())
            fairMap.map { mapped => callbacks.incrementAndGet(); mapped }
          } else {
            callbacks.incrementAndGet()
            Async.succeed(value + 1)
          }
        }
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
      val firstPoll = fold(yielding) match {
        case Pending(operation) => fold(operation.poll(() => yieldPermit.succeed(())))
        case result             => result
      }
      pendingMap.succeed(3)
      pendingFold.succeed(5L)
      for {
        _                    <- run(fairStarted)
        callbacksAtFirstYield = callbacks.get()
        _                     = fairMap.succeed(1025)
        mapResult            <- run(mapRunning)
        foldResult           <- run(foldRunning)
        _                    <- run(yieldPermit)
        yielded              <- run(yielding)
      } yield assertTrue(
        mapResult == Right(9L),
        foldResult == Right(9L),
        firstPoll.isInstanceOf[Pending[_]],
        callbacksAtFirstYield == 1024,
        yielded == Right(2100225L)
      )
    },
    test("direct sync-to-async map Long fold cancellation joins the callback and closes the reader") {
      val callback = new CancelledRead
      val closes   = new AtomicInteger
      val running  = Stream
        .fromReader[Nothing, Int](Reader.singleInt(1).withRelease(() => closes.incrementAndGet()))
        .mapAsync(_ => callback.asInstanceOf[Async[Int]])
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      for {
        _      <- run(callback.started)
        cleanup = Async.cancelWithCleanup(running)
        pending = fold(cleanup).isInstanceOf[Pending[_]]
        _       = callback.cleanup.succeed(())
        _      <- run(cleanup)
      } yield assertTrue(pending, callback.cancelled.get == 1, closes.get == 1)
    },
    test("direct sync-to-async map Long fold cancellation joins a pending fold before closing") {
      val callback = new CancelledRead
      val closes   = new AtomicInteger
      val running  = Stream
        .fromReader[Nothing, Int](Reader.singleInt(1).withRelease(() => closes.incrementAndGet()))
        .mapAsync(Async.succeed)
        .runFoldAsync(0L)((_, _) => callback.asInstanceOf[Async[Long]])
        .start
      for {
        _      <- run(callback.started)
        cleanup = Async.cancelWithCleanup(running)
        pending = fold(cleanup).isInstanceOf[Pending[_]]
        _       = callback.cleanup.succeed(())
        _      <- run(cleanup)
      } yield assertTrue(pending, callback.cancelled.get == 1, closes.get == 1)
    },
    test("direct async filter Long fold covers protected, unprotected, and fallback dispatch") {
      val acquisitionFailure = new StreamError("filter-acquisition")
      val protectedEffect    = Stream
        .fromReaderAsync[Nothing, Int](throw acquisitionFailure)
        .filterAsync(_ => Async.succeed(true))
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .either
      val unprotectedEffect = Stream
        .attemptAsync(Async.succeed(2))
        .filterAsync(_ => Async.succeed(true))
        .runFoldAsync(1L)((sum, value) => Async.succeed(sum + value))
      val fallbackEffect = Stream
        .succeed(3)
        .map(identity)
        .filterAsync(_ => Async.succeed(true))
        .runFoldAsync(1L)((sum, value) => Async.succeed(sum + value))
      for {
        protectedResult   <- run(protectedEffect)
        unprotectedResult <- run(unprotectedEffect)
        fallbackResult    <- run(fallbackEffect)
      } yield assertTrue(
        protectedResult.left.exists {
          case error: StreamError =>
            (error ne acquisitionFailure) && !error.isTrusted && error.value == "filter-acquisition"
          case _ => false
        },
        unprotectedResult == Right(3L),
        fallbackResult == Right(4L)
      )
    },
    test("direct async filter Long fold resumes pending reads, predicates, and folds") {
      val readSource     = new NativePendingReader
      val trueSource     = new HostileIntReader(1, 2, 3)
      val falseSource    = new HostileIntReader(1, 2, 3)
      val foldSource     = new HostileIntReader(1, 2, 3)
      val rejectedSource = new HostileIntReader((0 until 2049): _*)
      val pendingTrue    = new Completer[Boolean]
      val pendingFalse   = new Completer[Boolean]
      val pendingFold    = new Completer[Long]
      val readRunning    = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(readSource: Reader[Int]))
        .filterAsync(_ => Async.succeed(true))
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      val trueRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(trueSource: Reader[Int]))
        .filterAsync(value => if (value == 2) pendingTrue else Async.succeed(true))
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      val falseRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(falseSource: Reader[Int]))
        .filterAsync(value => if (value == 2) pendingFalse else Async.succeed(true))
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      val foldRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(foldSource: Reader[Int]))
        .filterAsync(_ => Async.succeed(true))
        .runFoldAsync(0L)((sum, value) => if (value == 2) pendingFold else Async.succeed(sum + value))
        .start
      val rejectedRunning = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(rejectedSource: Reader[Int]))
        .filterAsync(_ => Async.succeed(false))
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .start
      readSource.resume()
      pendingTrue.succeed(true)
      pendingFalse.succeed(false)
      pendingFold.succeed(3L)
      for {
        readResult  <- run(readRunning)
        trueResult  <- run(trueRunning)
        falseResult <- run(falseRunning)
        foldResult  <- run(foldRunning)
        rejected    <- run(rejectedRunning)
      } yield assertTrue(
        readResult == Right(6L),
        trueResult == Right(6L),
        falseResult == Right(4L),
        foldResult == Right(6L),
        rejected == Right(0L),
        readSource.closes.get == 1,
        trueSource.specializedReads.get == 4,
        falseSource.specializedReads.get == 4,
        foldSource.specializedReads.get == 4,
        rejectedSource.specializedReads.get == 2050
      )
    },
    test("direct async filter Long fold classifies read, predicate, and fold failures") {
      def runFilter[E](
        reader: Reader.AsyncReader[Int],
        predicate: Int => Async[Boolean] = _ => Async.succeed(true),
        fold: (Long, Int) => Async[Long] = (sum, value) => Async.succeed(sum + value)
      ) =
        Stream
          .fromReaderAsync[E, Int](Async.succeed(reader: Reader[Int]))
          .filterAsync(predicate)
          .runFoldAsync(0L)(fold)
          .either

      def reader(result: => Async[Long]) = new Reader.AsyncReader[Int] {
        def close(): Async[Unit]                                                    = Async.succeed(())
        override def jvmType: JvmType                                               = JvmType.Int
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = result.map(_.toInt.asInstanceOf[A])
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = result
      }

      val readThrow        = new RuntimeException("filter-read-throw")
      val readTypedThrow   = StreamError.source("filter-read-typed-throw")
      val readFailure      = new RuntimeException("filter-read-failure")
      val readTypedFailure = StreamError.source("filter-read-typed-failure")
      val predicateThrow   = StreamError.source("filter-predicate-throw")
      val predicateFailure = StreamError.source("filter-predicate-failure")
      val foldThrow        = StreamError.source("filter-fold-throw")
      val foldFailure      = StreamError.source("filter-fold-failure")
      for {
        thrown          <- run(runFilter[Nothing](reader(throw readThrow)))
        typedThrown     <- run(runFilter[String](reader(throw readTypedThrow)))
        failed          <- run(runFilter[Nothing](reader(Async.fail(readFailure))))
        typedFailed     <- run(runFilter[String](reader(Async.failTrusted(readTypedFailure))))
        predicateThrown <- run(runFilter[Nothing](new HostileIntReader(1), _ => throw predicateThrow))
        predicateFailed <- run(runFilter[Nothing](new HostileIntReader(1), _ => Async.failTrusted(predicateFailure)))
        foldThrown      <- run(runFilter[Nothing](new HostileIntReader(1), fold = (_, _) => throw foldThrow))
        foldFailed      <- run(runFilter[Nothing](new HostileIntReader(1), fold = (_, _) => Async.failTrusted(foldFailure)))
      } yield assertTrue(
        thrown == Left(readThrow),
        typedThrown == Right(Left("filter-read-typed-throw")),
        failed == Left(readFailure),
        typedFailed == Right(Left("filter-read-typed-failure")),
        predicateThrown.left.exists { case error: StreamError => !error.isTrusted; case _ => false },
        predicateFailed.left.exists { case error: StreamError => !error.isTrusted; case _ => false },
        foldThrown.left.exists { case error: StreamError => !error.isTrusted; case _ => false },
        foldFailed.left.exists { case error: StreamError => !error.isTrusted; case _ => false }
      )
    },
    test("direct async filter Long fold preserves typed read failure when cleanup also fails") {
      val readFailure  = StreamError.source("filter-use-and-close")
      val closeFailure = new RuntimeException("filter-close")
      val closes       = new AtomicInteger
      val reader       = new Reader.AsyncReader[Int] {
        def close(): Async[Unit]                                                    = { closes.incrementAndGet(); Async.fail(closeFailure) }
        override def jvmType: JvmType                                               = JvmType.Int
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.failTrusted(readFailure)
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = Async.failTrusted(readFailure)
      }
      run(
        Stream
          .fromReaderAsync[String, Int](Async.succeed(reader: Reader[Int]))
          .filterAsync(_ => Async.succeed(true))
          .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
          .either
      ).map(result =>
        assertTrue(
          result.left.exists(_ eq readFailure),
          readFailure.cleanupFailed,
          readFailure.getSuppressed.toList == List(closeFailure),
          closes.get == 1
        )
      )
    },
    test("direct synchronous filter Long fold uses specialized reads and yields across rejections") {
      val source    = new HostileIntReader((0 until 2049): _*)
      val callbacks = new AtomicInteger
      val stream    = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .filter { value => callbacks.incrementAndGet(); value == 2048 }
      for {
        result <- run(stream.runFoldAsync(0L)((sum, value) => Async.succeed(sum + value)))
      } yield assertTrue(
        result == Right(2048L),
        callbacks.get == 2049,
        source.specializedReads.get == 2050
      )
    },
    test("direct synchronous filter Long fold preserves callback defects after a fairness yield") {
      val source = new HostileIntReader((0 until 1026): _*)
      val forged = new StreamError("sync-filter-after-yield")
      val effect = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .filter(value => if (value == 1024) throw forged else false)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .either
      for {
        result <- run(effect)
        closed <- run(source.isClosed)
      } yield assertTrue(
        result.left.exists {
          case error: StreamError =>
            (error ne forged) && error.value == "sync-filter-after-yield" && !error.isTrusted
          case _ => false
        },
        source.specializedReads.get == 1025,
        closed
      )
    },
    test("direct synchronous filter-map Long fold skips rejected maps and resumes folds") {
      val source     = new HostileIntReader(1, 2, 3, 4)
      val predicates = new AtomicInteger
      val maps       = new AtomicInteger
      val pending    = new Completer[Long]
      val running    = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .filter { value => predicates.incrementAndGet(); value % 2 == 0 }
        .map { value => maps.incrementAndGet(); value + 1 }
        .runFoldAsync(0L)((sum, value) => if (value == 3) pending else Async.succeed(sum + value))
        .start
      pending.succeed(3L)
      for {
        result <- run(running)
      } yield assertTrue(
        result == Right(8L),
        predicates.get == 4,
        maps.get == 2,
        source.specializedReads.get == 5
      )
    },
    test("direct synchronous filter-map Long fold preserves map defects after a fairness yield") {
      val source = new HostileIntReader((0 until 1026): _*)
      val forged = new StreamError("filter-map-after-yield")
      val effect = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .filter(_ => true)
        .map(value => if (value == 1024) throw forged else value)
        .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
        .either
      for {
        result <- run(effect)
        closed <- run(source.isClosed)
      } yield assertTrue(
        result.left.exists {
          case error: StreamError => (error ne forged) && error.value == "filter-map-after-yield" && !error.isTrusted
          case _                  => false
        },
        source.specializedReads.get == 1025,
        closed
      )
    },
    test("direct alternating synchronous filters and maps preserve order and resume folds") {
      val source  = new HostileIntReader(1, 2, 14, 100000)
      val calls   = scala.collection.mutable.ArrayBuffer.empty[String]
      val pending = new Completer[Long]
      val running = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .filter { value => calls += s"f1:$value"; value % 2 == 0 }
        .map { value => calls += s"m1:$value"; value + 1 }
        .filter { value => calls += s"f2:$value"; value % 3 == 0 }
        .map { value => calls += s"m2:$value"; value * 2 }
        .filter { value => calls += s"f3:$value"; value % 5 == 0 }
        .map { value => calls += s"m3:$value"; value + 3 }
        .filter { value => calls += s"f4:$value"; value > 0 }
        .map { value => calls += s"m4:$value"; value - 1 }
        .filter { value => calls += s"f5:$value"; value < 100000 }
        .map { value => calls += s"m5:$value"; value + 7 }
        .runFoldAsync(0L)((sum, value) => if (value == 39) pending else Async.succeed(sum + value))
        .start
      pending.succeed(39L)
      for {
        result <- run(running)
      } yield assertTrue(
        result == Right(39L),
        calls.toList == List(
          "f1:1",
          "f1:2",
          "m1:2",
          "f2:3",
          "m2:3",
          "f3:6",
          "f1:14",
          "m1:14",
          "f2:15",
          "m2:15",
          "f3:30",
          "m3:30",
          "f4:33",
          "m4:33",
          "f5:32",
          "m5:32",
          "f1:100000",
          "m1:100000",
          "f2:100001"
        ),
        source.specializedReads.get == 5
      )
    },
    test("deep alternating synchronous filters and maps use the packed stage words") {
      val source                       = new HostileIntReader(1)
      val maps                         = new AtomicInteger
      val predicates                   = new AtomicInteger
      var stream: Stream[Nothing, Int] =
        Stream.fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
      var index = 0
      while (index < 65) {
        stream = stream.map { value => maps.incrementAndGet(); value + 1 }.filter { value =>
          predicates.incrementAndGet(); value > 0
        }
        index += 1
      }
      run(stream.runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))).map(result =>
        assertTrue(
          result == Right(66L),
          maps.get == 65,
          predicates.get == 65,
          source.specializedReads.get == 2
        )
      )
    },
    test("direct chained synchronous maps Long fold preserve order and resume folds") {
      val source                       = new HostileIntReader(1, 2)
      val maps                         = new AtomicInteger
      val pending                      = new Completer[Long]
      var stream: Stream[Nothing, Int] =
        Stream.fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
      var index = 0
      while (index < 5) {
        stream = stream.map { value => maps.incrementAndGet(); value + 1 }
        index += 1
      }
      val running = stream
        .runFoldAsync(0L)((sum, value) => if (value == 6) pending else Async.succeed(sum + value))
        .start
      pending.succeed(6L)
      for {
        result <- run(running)
      } yield assertTrue(result == Right(13L), maps.get == 10, source.specializedReads.get == 3)
    },
    test("direct chained synchronous maps Long fold preserve callback defects after a fairness yield") {
      val source                       = new HostileIntReader((0 until 1026): _*)
      val forged                       = new StreamError("sync-map-chain-after-yield")
      var stream: Stream[Nothing, Int] = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(source: Reader[Int]))
        .map(value => if (value == 1024) throw forged else value + 1)
      var index = 1
      while (index < 5) {
        stream = stream.map(_ + 1)
        index += 1
      }
      val effect = stream.runFoldAsync(0L)((sum, value) => Async.succeed(sum + value)).either
      for {
        result <- run(effect)
        closed <- run(source.isClosed)
      } yield assertTrue(
        result.left.exists {
          case error: StreamError =>
            (error ne forged) && error.value == "sync-map-chain-after-yield" && !error.isTrusted
          case _ => false
        },
        source.specializedReads.get == 1025,
        closed
      )
    },
    test("deep synchronous maps Long fold stays direct and stack safe") {
      val depth  = Stream.DepthCutoff + 10
      val stream = (0 until depth).foldLeft(
        Stream.fromReaderAsync[Nothing, Int](Async.succeed(new HostileIntReader(1): Reader[Int]))
      )((current, _) => current.map(_ + 1))
      run(stream.runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))).map(result =>
        assertTrue(result == Right(depth.toLong + 1L))
      )
    },
    test("typed failures become Left and callback failures remain defects") {
      val defect = new RuntimeException("callback")
      for {
        typed  <- run(Stream.fail("typed").runDrainAsync)
        failed <-
          run(Stream(1).runForeachAsync(_ => Async.fail(defect)).map(Right(_)).catchAll(t => Async.succeed(Left(t))))
      } yield assertTrue(typed == Left("typed"), failed == Left(defect))
    },
    test("cleanup failures stay outside the typed channel and replay-equivalent close remains typed") {
      def failedReader(readFailure: StreamError, closeFailure: Throwable) = new Reader.SyncReader[Int] {
        def close(): Unit                  = throw closeFailure
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A = throw readFailure
      }
      val typed       = StreamError.source("typed")
      val replayError = StreamError.source("replay")
      val close       = new RuntimeException("close")
      val closeOnly   = Stream.fromReader[Nothing, Int](new Reader.SyncReader[Int] {
        def close(): Unit                  = throw StreamError.source("close-only")
        def isClosed: Boolean              = true
        def read[A >: Int](sentinel: A): A = sentinel
      })
      val distinct = Stream.fromReader[String, Int](failedReader(typed, close))
      val replay   = Stream.fromReader[String, Int](failedReader(replayError, replayError))
      for {
        closeResult    <- run(closeOnly.runCollectAsync).either
        distinctResult <- run(distinct.runCollectAsync).either
        replayResult   <- run(replay.runCollectAsync)
      } yield assertTrue(
        closeResult.left.exists {
          case error: StreamError => error.value == "close-only" && error.cleanupFailed
          case _                  => false
        },
        distinctResult == Left(typed),
        typed.cleanupFailed,
        typed.getSuppressed.contains(close),
        replayResult == Left("replay")
      )
    },
    test("trusted failure after a fairness yield remains typed") {
      val reader = new Reader.SyncReader[Int] {
        private var index                  = 0
        def close(): Unit                  = ()
        def isClosed: Boolean              = false
        override def jvmType: JvmType      = JvmType.Int
        def read[A >: Int](sentinel: A): A =
          if (index < 300) { val value = index; index += 1; value.asInstanceOf[A] }
          else throw StreamError.source("after-yield")
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long =
          if (index < 300) { val value = index; index += 1; value.toLong }
          else throw StreamError.source("after-yield")
      }
      run(Stream.fromReader[String, Int](reader).countAsync).map(result => assertTrue(result == Left("after-yield")))
    },
    test("mapParAsync fails fast, closes its source once, and replays the identical failure") {
      val failure = new RuntimeException("mapParAsync")
      val closes  = new AtomicInteger
      val never   = new Completer[Int]
      val source  = Reader.fromIterable(List(1, 2)).withRelease(() => closes.incrementAndGet()).toAsync
      val reader  = Stream
        .fromReader[Nothing, Int](source)
        .mapParAsync(2)(i =>
          if (i == 1) Async.fail(failure).asInstanceOf[Async[Int]] else never.asInstanceOf[Async[Int]]
        )
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Int]]
      for {
        first  <- run(reader.read(-1).either)
        second <- run(reader.read(-2).either)
        _      <- run(reader.close())
      } yield assertTrue(
        first.left.exists(_ eq failure),
        second.left.exists(_ eq failure),
        closes.get == 1,
        reader.isClosed.block
      )
    },
    test("mapParAsync classifies synchronous and asynchronous callback StreamErrors as defects") {
      val synchronous                      = StreamError.source("synchronous-mapPar-callback")
      val asynchronous                     = StreamError.source("asynchronous-mapPar-callback")
      val syncCallback: Int => Async[Int]  = _ => throw synchronous
      val asyncCallback: Int => Async[Int] = _ => Async.fail(asynchronous)
      for {
        syncResult <- run(
                        Stream(1).mapParAsync(1)(syncCallback).runDrainAsync.either
                      )
        asyncResult <- run(
                         Stream(1).mapParAsync(1)(asyncCallback).runDrainAsync.either
                       )
      } yield assertTrue(
        syncResult.left.exists {
          case error: StreamError =>
            (error ne synchronous) && error.value == synchronous.value && !error.isTrusted
          case _ => false
        },
        asyncResult.left.exists {
          case error: StreamError =>
            (error ne asynchronous) && error.value == asynchronous.value && !error.isTrusted
          case _ => false
        }
      )
    },
    test("mapParAsync replenishes a completed slot without waiting for a stalled sibling batch") {
      val cancellations  = new AtomicInteger
      val stalledEntered = new Completer[Unit]
      val stalled        = new Async.Operation[Int] {
        def poll(onComplete: Runnable): Async[Int]   = { stalledEntered.succeed(()); this }
        protected def cancelOperation(): Async[Unit] = { cancellations.incrementAndGet(); Async.succeed(()) }
      }
      val invoked = new AtomicInteger
      val reader  = Stream(1, 2, 3)
        .mapParAsync(2) { value =>
          invoked.incrementAndGet()
          if (value == 1) stalled else Async.succeed(value)
        }
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Int]]
      val firstRead = reader.read(-1).start
      for {
        _      <- run(stalledEntered)
        first  <- run(firstRead)
        second <- run(reader.read(-1))
        _      <- run(reader.close())
      } yield assertTrue(
        first == 2,
        second == 3,
        invoked.get == 3,
        cancellations.get == 1
      )
    },
    test("mapParAsync direct fold never exceeds its concurrency bound") {
      val callbacks  = Array.fill(16)(new Completer[Int])
      val firstWave  = new Completer[Unit]
      val allStarted = new Completer[Unit]
      val invoked    = new AtomicInteger
      val inFlight   = new AtomicInteger
      val maximum    = new AtomicInteger
      val folded     = Stream
        .range(0, callbacks.length)
        .mapParAsync(8) { value =>
          val active   = inFlight.incrementAndGet()
          var observed = maximum.get()
          while (active > observed && !maximum.compareAndSet(observed, active)) observed = maximum.get()
          val count = invoked.incrementAndGet()
          if (count == 8) firstWave.succeed(())
          if (count == callbacks.length) allStarted.succeed(())
          callbacks(value).map { result => inFlight.decrementAndGet(); result }
        }
        .runFoldAsync(0L)((acc, value) => Async.succeed(acc + value))
        .start
      for {
        _             <- run(firstWave)
        firstWaveCount = invoked.get()
        _              = callbacks.take(8).indices.foreach(index => callbacks(index).succeed(index))
        _             <- run(allStarted)
        _              = callbacks.indices.drop(8).foreach(index => callbacks(index).succeed(index))
        result        <- run(folded)
      } yield assertTrue(
        firstWaveCount == 8,
        maximum.get() == 8,
        inFlight.get() == 0,
        result == Right(120L)
      )
    },
    test("concurrent boundary close is lazy, cancels callback work, and joins its cleanup") {
      val entered     = new Completer[Unit]
      val cleanupGate = new Completer[Unit]
      val cancelled   = new AtomicInteger
      val callback    = new Async.Operation[Int] {
        def poll(onComplete: Runnable): Async[Int]   = { entered.succeed(()); this }
        protected def cancelOperation(): Async[Unit] = {
          cancelled.incrementAndGet()
          cleanupGate
        }
      }
      val reader = Stream(1)
        .mapParAsync(2)(_ => callback)
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Int]]
      val pull        = reader.read(-1).start
      val closeEffect = reader.close()
      for {
        _           <- run(entered)
        openBefore  <- run(reader.isClosed)
        closing      = closeEffect.start
        closePending = closing.poll(new Runnable { def run(): Unit = () }).isInstanceOf[Pollable[?]]
        _            = cleanupGate.succeed(())
        _           <- run(closing)
        value       <- run(pull)
        closedAfter <- run(reader.isClosed)
      } yield assertTrue(!openBefore, closePending, value == -1, closedAfter, cancelled.get == 1)
    },
    test("direct cancellation settles concurrent scalar pulls with their exact sentinel") {
      val entered     = new Completer[Unit]
      val cleanupGate = new Completer[Unit]
      val callback    = new Async.Operation[Int] {
        def poll(onComplete: Runnable): Async[Int]   = { entered.succeed(()); this }
        protected def cancelOperation(): Async[Unit] = cleanupGate
      }
      val reader = Stream(1)
        .mapParAsync(2)(_ => callback)
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Int]]
      val pull    = reader.read(-73)
      val running = pull.start
      for {
        _        <- run(entered)
        cleanup   = Async.cancelWithCleanup(pull.asInstanceOf[Pollable[Int]]).start
        _         = cleanupGate.succeed(())
        _        <- run(cleanup)
        original <- run(pull)
        driven   <- run(running.either)
        _        <- run(reader.close())
      } yield assertTrue(original == -73, driven == Right(-73))
    },
    test("terminal failure wins cancellation and retains its cleanup join") {
      val failure      = new RuntimeException("terminal-winner")
      val closeEntered = new Completer[Unit]
      val closeGate    = new Completer[Unit]
      val source       = new Reader.AsyncReader[Int] {
        override def jvmType: JvmType                                               = JvmType.Int
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.fail(failure)
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = Async.fail(failure)
        def close(): Async[Unit]                                                    = { closeEntered.succeed(()); closeGate }
      }
      val reader = Stream
        .fromReader[Nothing, Int](source)
        .mapParAsync(2)(Async.succeed)
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Int]]
      val pull    = reader.read(-41)
      val running = pull.start
      for {
        _             <- run(closeEntered)
        cancellation   = Async.cancelWithCleanup(pull.asInstanceOf[Pollable[Int]]).start
        pending        = cancellation.poll(new Runnable { def run(): Unit = () }).isInstanceOf[Pollable[?]]
        _              = closeGate.succeed(())
        cancelOutcome <- run(cancellation.either)
        original      <- run(pull.either)
        driven        <- run(running.either)
        replay        <- run(reader.read(-42).either)
        _             <- run(reader.close())
      } yield assertTrue(
        pending,
        cancelOutcome.left.exists(_ eq failure),
        original.left.exists(_ eq failure),
        driven.left.exists(_ eq failure),
        replay.left.exists(_ eq failure)
      )
    },
    test("concurrent bulk pulls return their committed prefix without consuming a pending result") {
      val secondEntered = new Completer[Unit]
      val secondValue   = new Completer[Int]
      val second        = new Async.Operation[Int] {
        def poll(onComplete: Runnable): Async[Int] = {
          secondEntered.succeed(())
          val result = secondValue.poll(onComplete)
          if (result.asInstanceOf[AnyRef] eq secondValue) this else result
        }
        protected def cancelOperation(): Async[Unit] = Async.succeed(())
      }
      val reader = Stream(1, 2)
        .mapParAsync(2)(value => if (value == 1) Async.succeed(value) else second)
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Int]]
      val dest    = Array.fill(2)(0)
      val bulk    = reader.readInts(dest, 0, 2)
      val running = bulk.start
      for {
        count      <- run(bulk)
        driven     <- run(running.either)
        next        = reader.readInt(-88L).start
        _          <- run(secondEntered)
        _           = secondValue.succeed(2)
        secondRead <- run(next)
        end        <- run(reader.readInt(-88L))
        _          <- run(reader.close())
      } yield assertTrue(
        count == 1,
        driven == Right(1),
        dest.sameElements(Array(1, 0)),
        secondRead == 2L,
        end == -88L
      )
    },
    test("stateful boundaries discard cached output only when lazy close is driven") {
      val source = Reader.fromIterable(List(1, 2, 3, 4)).toAsync
      val reader = Stream
        .fromReader[Nothing, Int](source)
        .buffer(3)
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Int]]
      for {
        first          <- run(reader.read(-1))
        bufferedBefore <- run(reader.readable())
        closeEffect     = reader.close()
        stillOpen      <- run(reader.isClosed)
        _              <- run(closeEffect)
        bufferedAfter  <- run(reader.readable())
        end            <- run(reader.read(-1))
      } yield assertTrue(first == 1, bufferedBefore, !stillOpen, !bufferedAfter, end == -1)
    },
    test("concurrent async boundaries close every owned reader before publishing natural EOF") {
      def drain(reader: Reader.AsyncReader[Int], acc: List[Int] = Nil): Async[List[Int]] =
        reader.read[Any](zio.blocks.streams.internal.EndOfStream).flatMap { value =>
          if (value.asInstanceOf[AnyRef] eq zio.blocks.streams.internal.EndOfStream) Async.succeed(acc.reverse)
          else drain(reader, value.asInstanceOf[Int] :: acc)
        }

      val mapSourceCloses  = new AtomicInteger
      val flatSourceCloses = new AtomicInteger
      val flatInnerCloses  = new AtomicInteger
      val mergeOuterCloses = new AtomicInteger
      val mergeInnerCloses = new AtomicInteger
      val mapped           = Stream
        .fromReader[Nothing, Int](Reader.fromIterable(List(1, 2)).withRelease(() => mapSourceCloses.incrementAndGet()))
        .mapParAsync(2)(value => Async.succeed(value + 1))
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Int]]
      val flatMapped = Stream
        .fromReader[Nothing, Int](Reader.singleInt(1).withRelease(() => flatSourceCloses.incrementAndGet()))
        .flatMapPar(1)(value =>
          Stream.unwrap(
            Async.succeed(
              Stream.fromReader[Nothing, Int](
                Reader.singleInt(value + 1).withRelease(() => flatInnerCloses.incrementAndGet())
              )
            )
          )
        )
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Int]]
      val mergedReader = Stream
        .mergeAll(1)(
          Stream.fromReader[Nothing, Stream[Nothing, Int]](
            Reader
              .single(
                Stream
                  .fromReader[Nothing, Int](Reader.singleInt(2).withRelease(() => mergeInnerCloses.incrementAndGet()))
              )
              .withRelease(() => mergeOuterCloses.incrementAndGet())
          )
        )
        .compile(0, Stream.DefaultBufferSize)
      val merged = mergedReader match {
        case sync: Reader.SyncReader[Int @unchecked]   => sync.toAsync
        case async: Reader.AsyncReader[Int @unchecked] => async
      }
      for {
        mappedValues <- run(drain(mapped))
        flatValues   <- run(drain(flatMapped))
        mergeValues  <- run(drain(merged))
        _            <- run(mapped.close())
        _            <- run(flatMapped.close())
        _            <- run(merged.close())
      } yield assertTrue(
        mappedValues.sorted == List(2, 3),
        flatValues == List(2),
        mergeValues == List(2),
        mapSourceCloses.get == 1,
        flatSourceCloses.get == 1,
        flatInnerCloses.get == 1,
        mergeOuterCloses.get == 1,
        mergeInnerCloses.get == 1
      )
    },
    test("mapParAsync observes a sibling failure while the sole source refill is pending") {
      val sourcePending = new Completer[Any]
      val sibling       = new Completer[Int]
      val failure       = new RuntimeException("sibling")
      val source        = new Reader.AsyncReader[Int] {
        private var index        = 0
        def close(): Async[Unit] = {
          sourcePending.succeed(zio.blocks.streams.internal.EndOfStream)
          Async.succeed(())
        }
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = {
          index += 1
          if (index == 1) Async.succeed(1)
          else if (index == 2) Async.succeed(2)
          else sourcePending.asInstanceOf[Async[A]]
        }
      }
      val reader = Stream
        .fromReader[Nothing, Int](source)
        .mapParAsync(2)(value => if (value == 1) Async.succeed(value) else sibling)
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Int]]
      for {
        first  <- run(reader.read(-1))
        _       = sibling.fail(failure)
        failed <- run(reader.read(-1).either)
        _      <- run(reader.close())
      } yield assertTrue(first == 1, failed.left.exists(_ eq failure))
    },
    test("concurrent primitive bulk read returns a ready prefix without awaiting the next result") {
      val second = new Completer[Int]
      val reader = Stream(1, 2)
        .mapParAsync(2)(value => if (value == 1) Async.succeed(value) else second)
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Int]]
      val values = Array(0, 0)
      for {
        count <- run(reader.readInts(values, 0, 2))
        _     <- run(reader.close())
      } yield assertTrue(count == 1, values.toVector == Vector(1, 0))
    },
    test("widened concurrent Char results retain generic low-byte behavior") {
      val reader = Stream(1)
        .mapParAsync[Any](2)(_ => Async.succeed('\u0141'))
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Any]]
      for {
        value <- run(reader.readByte())
        _     <- run(reader.close())
      } yield assertTrue(value == 0x41)
    },
    test("flatMapPar with unwrap supports synchronous and asynchronous inners") {
      val stream = Stream(1, 2).flatMapPar(2) { value =>
        val inner =
          if (value == 1) Stream(value, value + 10)
          else Stream.fromReader[Nothing, Int](Reader.fromIterable(List(value, value + 10)).toAsync)
        Stream.unwrap(Async.succeed(inner))
      }
      run(stream.runCollectAsync).map(result => assertTrue(result.exists(_.toList.sorted == List(1, 2, 11, 12))))
    },
    test("concurrent async boundaries dispatch primitive sources and dynamic inners through specialized lanes") {
      val mapSource    = new HostileIntReader(1, 2)
      val longSource   = new HostileLongReader(Long.MinValue, Long.MaxValue)
      val floatSource  = new HostileFloatReader(-Float.MaxValue, Float.MaxValue)
      val doubleSource = new HostileDoubleReader(Double.MinValue, Double.MaxValue)
      val byteSource   = new HostileByteReader(Byte.MinValue, Byte.MaxValue)
      val flatInner    = new HostileIntReader(Int.MinValue, Int.MaxValue)
      val mergeInner   = new HostileIntReader(3, 4)
      val mapped       = Stream.fromReader[Nothing, Int](mapSource).mapParAsync(2)(value => Async.succeed(value + 10))
      val mappedLong   = Stream.fromReader[Nothing, Long](longSource).mapParAsync(2)(Async.succeed)
      val mappedFloat  = Stream.fromReader[Nothing, Float](floatSource).mapParAsync(2)(Async.succeed)
      val mappedDouble = Stream.fromReader[Nothing, Double](doubleSource).mapParAsync(2)(Async.succeed)
      val mappedByte   = Stream.fromReader[Nothing, Byte](byteSource).mapParAsync(2)(Async.succeed)
      val flattened    =
        Stream(1).flatMapPar(1)(_ => Stream.unwrap(Async.succeed(Stream.fromReader[Nothing, Int](flatInner))))
      val merged = Stream.mergeAll(1)(Stream(Stream.fromReader[Nothing, Int](mergeInner)))
      for {
        mapValues    <- run(mapped.runCollectAsync)
        longValues   <- run(mappedLong.runCollectAsync)
        floatValues  <- run(mappedFloat.runCollectAsync)
        doubleValues <- run(mappedDouble.runCollectAsync)
        byteValues   <- run(mappedByte.runCollectAsync)
        flatValues   <- run(flattened.runCollectAsync)
        mergeValues  <- run(merged.runCollectAsync)
      } yield assertTrue(
        mapValues.exists(_.toList.sorted == List(11, 12)),
        longValues.exists(_.toList == List(Long.MinValue, Long.MaxValue)),
        floatValues.exists(_.toList == List(-Float.MaxValue, Float.MaxValue)),
        doubleValues.exists(_.toList == List(Double.MinValue, Double.MaxValue)),
        byteValues.exists(_.toList == List(Byte.MinValue, Byte.MaxValue)),
        flatValues.exists(_.toList == List(Int.MinValue, Int.MaxValue)),
        mergeValues.exists(_.toList == List(3, 4)),
        mapSource.specializedReads.get > 0,
        longSource.specializedReads.get > 0,
        floatSource.specializedReads.get > 0,
        doubleSource.specializedReads.get > 0,
        byteSource.specializedReads.get > 0,
        flatInner.specializedReads.get > 0,
        mergeInner.specializedReads.get > 0
      )
    },
    test("flatMapPar with unwrap fails fast, cancels a stalled callback, and closes its source once") {
      val failure       = new RuntimeException("flatMapPar-unwrap")
      val closes        = new AtomicInteger
      val cancellations = new AtomicInteger
      val started       = new Completer[Unit]
      val stalled       = new Async.Operation[Stream[Nothing, Int]] {
        def poll(onComplete: Runnable): Async[Stream[Nothing, Int]] = {
          started.succeed(())
          this
        }
        protected def cancelOperation(): Async[Unit] = {
          cancellations.incrementAndGet()
          Async.succeed(())
        }
      }
      val source = Reader.fromIterable(List(1, 2)).withRelease(() => closes.incrementAndGet()).toAsync
      val reader = Stream
        .fromReader[Nothing, Int](source)
        .flatMapPar(2)(value =>
          Stream.unwrap(
            if (value == 1) stalled.asInstanceOf[Async[Stream[Nothing, Int]]]
            else started.peek.flatMap(_ => Async.fail(failure)).asInstanceOf[Async[Stream[Nothing, Int]]]
          )
        )
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Int]]
      for {
        failed <- run(reader.read(-1).either)
        replay <- run(reader.read(-2).either)
        _      <- run(reader.close())
      } yield assertTrue(
        failed.left.exists(_ eq failure),
        replay.left.exists(_ eq failure),
        cancellations.get == 1,
        closes.get == 1
      )
    },
    test("mergeAll supports synchronous and native asynchronous inners from a synchronous outer") {
      val sync     = Stream(1, 2)
      val async    = Stream.fromReader[Nothing, Int](Reader.fromIterable(List(3, 4)).toAsync)
      val merged   = Stream.mergeAll(2)(Stream(sync, async))
      val compiled = merged.compile(0, Stream.DefaultBufferSize)
      for {
        _      <- run(compiled.asInstanceOf[Reader.AsyncReader[Int]].close())
        result <- run(merged.runCollectAsync)
      } yield assertTrue(
        compiled.isInstanceOf[Reader.AsyncReader[?]],
        result.exists(_.toList.sorted == List(1, 2, 3, 4))
      )
    },
    test("mergeAll supports a native asynchronous inner when dynamically materialized by flatMap") {
      val async  = Stream.fromReader[Nothing, Int](Reader.fromIterable(List(2, 3)).toAsync)
      val stream = Stream(1).flatMap(_ => Stream.mergeAll(2)(Stream(Stream(1), async)))
      run(stream.runCollectAsync).map(result => assertTrue(result.exists(_.toList.sorted == List(1, 2, 3))))
    },
    test("flatMap materializes every asynchronous and platform wrapper child") {
      val asyncChild = Stream(1).flatMap(_ => Stream(2).mapAsync(i => Async.succeed(i + 1)))
      val buffered   = Stream(1).flatMap(_ => Stream(2).buffer(1))
      val resized    = Stream(1).flatMap(_ => Stream.bufferSize(2)(Stream(2).mapAsync(Async.succeed)))
      val mappedPar  = Stream(1).flatMap(_ => Stream(2).mapPar(1)(_ + 1))
      val merged     = Stream(1).flatMap(_ => Stream.mergeAll(1)(Stream(Stream(2))))
      for {
        a <- run(asyncChild.runCollectAsync)
        b <- run(buffered.runCollectAsync)
        c <- run(resized.runCollectAsync)
        d <- run(mappedPar.runCollectAsync)
        e <- run(merged.runCollectAsync)
      } yield assertTrue(
        a == Right(Chunk(3)),
        b == Right(Chunk(2)),
        c == Right(Chunk(2)),
        d == Right(Chunk(3)),
        e == Right(Chunk(2))
      )
    },
    test("synchronous takeWhile promotes over a native asynchronous upstream") {
      val stream = Stream.fromReader[Nothing, Int](Reader.fromIterable(List(1, 2, 3, 4)).toAsync).takeWhile(_ < 3)
      for {
        result <- run(stream.runCollectAsync)
      } yield assertTrue(result == Right(Chunk(1, 2)))
    },
    test("synchronous scan promotes over a native asynchronous upstream") {
      val stream = Stream.fromReader[Nothing, Int](Reader.fromIterable(List(1, 2, 3)).toAsync).scan(0)(_ + _)
      for {
        result <- run(stream.runCollectAsync)
      } yield assertTrue(result == Right(Chunk(0, 1, 3, 6)))
    },
    test("synchronous wrappers preserve deferred and resource-owned asynchronous children") {
      val suspended = new AtomicInteger
      val acquired  = new AtomicInteger
      val released  = new AtomicInteger
      val async     = Stream.fromReader[String, Int](Reader.fromIterable(List(1, 2)).toAsync)
      val deferred  = Stream.suspend { suspended.incrementAndGet(); async }.map(_ + 1)
      val resource  = Stream.fromAcquireRelease(
        { acquired.incrementAndGet(); "resource" },
        (_: String) => released.incrementAndGet()
      )(_ => async)
      for {
        deferredResult <- run(deferred.runCollectAsync)
        resourceResult <- run(resource.runCollectAsync)
      } yield assertTrue(
        deferredResult == Right(Chunk(2, 3)),
        resourceResult == Right(Chunk(1, 2)),
        suspended.get == 1,
        acquired.get == 1,
        released.get == 1
      )
    },
    test("zip compilation failure transfers ownership of an asynchronous left reader") {
      val cause      = new RuntimeException("right compile")
      val closes     = new AtomicInteger
      val leftReader =
        Reader.singleInt(1).toAsync.withReleaseAsync(() => Async.succeed { closes.incrementAndGet(); () })
      val zipped = Stream.fromReader[Nothing, Int](leftReader) &&
        (Stream.suspend(throw cause): Stream[Nothing, Int])
      val reader = zipped.compile(0, Stream.DefaultBufferSize).asInstanceOf[Reader.AsyncReader[(Int, Int)]]
      for {
        _ <- run(reader.close())
      } yield assertTrue(closes.get == 1)
    },
    test("nested asynchronous finalizers retain inner-to-outer order") {
      val order  = new java.util.ArrayList[String]
      val stream = Stream
        .succeed(1)
        .ensuringAsync(Async.succeed { order.add("inner"); () })
        .ensuringAsync(Async.succeed { order.add("outer"); () })
        .mapAsync(value => Async.succeed(value))
      for {
        result <- run(stream.runCollectAsync)
      } yield assertTrue(result == Right(Chunk(1)), order.toArray.toList == List("inner", "outer"))
    },
    test("synchronous error mapping preserves an asynchronous upstream") {
      val asyncFailure = Stream.fromReader[String, Int](new Reader.AsyncReader[Int] {
        def close(): Async[Unit]                  = Async.succeed(())
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = Async.failTrusted(StreamError.source("typed"))
      })
      run(asyncFailure.mapError(_.length).runCollectAsync).map(result => assertTrue(result == Left(5)))
    },
    test("deep synchronous maps preserve an asynchronous upstream") {
      val stream = (0 until Stream.DepthCutoff + 10).foldLeft(
        Stream.fromReader[Nothing, Int](Reader.singleInt(1).toAsync): Stream[Nothing, Int]
      )((stream, _) => stream.map(_ + 1))
      run(stream.runCollectAsync).map(result => assertTrue(result == Right(Chunk(Stream.DepthCutoff + 11))))
    },
    test("deep asynchronous maps preserve an asynchronous upstream") {
      val stream = (0 until Stream.DepthCutoff + 10).foldLeft(
        Stream.fromReader[Nothing, Int](Reader.singleInt(1).toAsync): Stream[Nothing, Int]
      )((stream, _) => stream.mapAsync(value => Async.succeed(value + 1)))
      run(stream.runCollectAsync).map(result => assertTrue(result == Right(Chunk(Stream.DepthCutoff + 11))))
    },
    test("kind-dynamic reader sources never acquire ownership in the synchronous interpreter") {
      val acquisitions = new AtomicInteger
      val dynamic      = Stream.fromReader[Nothing, Int] {
        acquisitions.incrementAndGet()
        (Reader.singleInt(1).toAsync: Reader[Int])
      }
      val failure =
        try { SyncInterpreter.fromStream(dynamic); null }
        catch { case cause: Throwable => cause }
      assertTrue(failure eq Stream.AsyncBoundaryRequired, acquisitions.get == 0)
    },
    test("statically synchronous reader sources remain synchronously compilable") {
      val stream = Stream.fromReader[Nothing, Int](Reader.singleInt(1))
      val reader = SyncInterpreter.fromStream(stream).asInstanceOf[Reader.SyncReader[Int]]
      assertTrue(reader.readInt(Long.MinValue) == 1L)
    },
    test("deep kind-dynamic async reader sources preserve kind and close exactly once") {
      val source  = new NativePendingReader
      val dynamic = Stream.fromReader[Nothing, Int](source: Reader[Int])
      val stream  = (0 until Stream.DepthCutoff + 10).foldLeft(dynamic)((current, _) => current.map(identity))
      val reader  = Stream.compileToReader(stream).asInstanceOf[Reader.AsyncReader[Int]]
      for {
        _ <- run(reader.close())
        _ <- run(reader.close())
      } yield assertTrue(source.closes.get == 1)
    },
    test("deep heterogeneous nodes preserve operator order") {
      val operatorsPerIteration = 7
      val iterations            = Stream.DepthCutoff / operatorsPerIteration + 10
      val stream                = (0 until iterations).foldLeft(
        Stream.fail("typed").asInstanceOf[Stream[String, Int]]
      ) { (stream, _) =>
        stream
          .catchAll(_ => Stream.succeed(1): Stream[String, Int])
          .catchDefect { case _ => Stream.succeed(2): Stream[String, Int] }
          .ensuring(())
          .mapError(identity)
          .drop(0)
          .take(Long.MaxValue)
          .takeWhile(_ => true)
      }
      run(stream.runCollectAsync).map(result => assertTrue(result == Right(Chunk(1))))
    },
    test("deep asynchronous take and drop preserve their positional boundary") {
      val bounded = Stream
        .fromReader[Nothing, Int](Reader.fromIterable(List(1, 2, 3)).toAsync)
        .take(2)
        .drop(1)
      val stream = (0 until Stream.DepthCutoff + 10).foldLeft(bounded) { (current, _) =>
        current.mapAsync(value => Async.succeed(value))
      }
      run(stream.runCollectAsync).map(result => assertTrue(result == Right(Chunk(2))))
    },
    test("deep asynchronous error mapping precedes recovery") {
      val recovered = Stream
        .fail("x")
        .asInstanceOf[Stream[String, Int]]
        .mapErrorAsync(error => Async.succeed(error + "i"))
        .catchAll(error => Stream.unwrap(Async.succeed(Stream.succeed(error.length))))
      val stream = (0 until Stream.DepthCutoff + 10).foldLeft(recovered) { (current, _) =>
        current.mapAsync(value => Async.succeed(value))
      }
      run(stream.runCollectAsync).map(result => assertTrue(result == Right(Chunk(2))))
    },
    test("recovery replaces the boundary output without re-running upstream operators") {
      val upstreamCalls   = new AtomicInteger
      val downstreamCalls = new AtomicInteger
      val defect          = new RuntimeException("map")
      val stream          = Stream(1).map { value =>
        upstreamCalls.incrementAndGet(); if (value == 1) throw defect else value + 1
      }.catchDefect { case `defect` => Stream.succeed(10) }.map { value =>
        downstreamCalls.incrementAndGet(); value + 1
      }
      run(stream.runCollectAsync).map(result =>
        assertTrue(result == Right(Chunk(11)), upstreamCalls.get == 1, downstreamCalls.get == 1)
      )
    },
    test("recovery normalizes legitimate widened branches to a reference boundary") {
      val successful: Stream[String, Int] = Stream.succeed(1)
      val failed: Stream[String, Int]     = Stream.fail("typed")
      val first: Stream[Nothing, AnyVal]  = successful.catchAll(_ => Stream.succeed(2L): Stream[Nothing, AnyVal])
      val second: Stream[Nothing, AnyVal] = failed.catchAll(_ => Stream.succeed(2L): Stream[Nothing, AnyVal])
      for {
        firstResult  <- run(first.runCollectAsync)
        secondResult <- run(second.runCollectAsync)
      } yield assertTrue(firstResult == Right(Chunk(1)), secondResult == Right(Chunk(2L)))
    },
    test("recovery preserves Byte Short and Char values across logical lane normalization") {
      val bytes: Stream[String, Byte]      = Stream(Byte.MinValue, (-1).toByte, Byte.MaxValue)
      val shorts: Stream[String, Short]    = Stream(Short.MinValue, (-1).toShort, Short.MaxValue)
      val chars: Stream[String, Char]      = Stream.fromIterable(List(Char.MinValue, 'A', Char.MaxValue))
      val failedByte: Stream[String, Byte] = Stream.fail("byte")
      val bytesAny: Stream[String, Any]    = bytes
      val shortsAny: Stream[String, Any]   = shorts
      for {
        byteValues    <- run(bytesAny.catchAll(_ => Stream.empty.asInstanceOf[Stream[Nothing, Any]]).runCollectAsync)
        shortValues   <- run(shortsAny.catchAll(_ => Stream.empty.asInstanceOf[Stream[Nothing, Any]]).runCollectAsync)
        charValues    <- run(chars.catchAll(_ => Stream.empty.asInstanceOf[Stream[Nothing, Char]]).runCollectAsync)
        recoveredByte <- run(failedByte.catchAll(_ => Stream(Byte.MinValue, (-1).toByte)).runCollectAsync)
      } yield assertTrue(
        byteValues == Right(Chunk[Any](Byte.MinValue, (-1).toByte, Byte.MaxValue)),
        shortValues == Right(Chunk[Any](Short.MinValue, (-1).toShort, Short.MaxValue)),
        charValues == Right(Chunk(Char.MinValue, 'A', Char.MaxValue)),
        recoveredByte == Right(Chunk(Byte.MinValue, (-1).toByte))
      )
    },
    test("reference-backed Char recovery normalization widens to numeric lanes") {
      val interpreter = AsyncInterpreter.fromStream(Stream.fromIterable(List('A')))
      interpreter.normalizeRecoveryOutput(JvmType.Long)
      run(interpreter.readLong(Long.MinValue)).map(value => assertTrue(value == 65L))
    },
    test("recovery accepts inferred numeric widening") {
      val values: Concat.WithOut[Int, Long, Int | Long] = implicitly
      implicit val output: JvmType.Infer[Int | Long]    = JvmType.Infer.boxed[Int | Long]
      val stream: Stream[Nothing, Int | Long]           =
        Stream
          .fail("typed")
          .asInstanceOf[Stream[String, Int]]
          .catchAll[Nothing, Long, Int | Long](_ => Stream.succeed(2L))(values, output)
      run(stream.runCollectAsync).map(result => assertTrue(result == Right(Chunk(values.right(2L)))))
    },
    test("empty bottom recovery preserves the primitive output lane") {
      val typed: Stream[String, Int]   = Stream.fail("typed")
      val defect: Stream[Nothing, Int] = Stream.die(new RuntimeException("defect"))
      val caught                       = Stream.compileToReader(typed.catchAll(_ => Stream.empty))
      val caughtDefect                 = Stream.compileToReader(defect.catchDefect { case _ => Stream.empty })
      val caughtAsync                  = caught.asInstanceOf[Reader.AsyncReader[Int]]
      val defectAsync                  = caughtDefect.asInstanceOf[Reader.AsyncReader[Int]]
      for {
        typedEnd  <- run(caughtAsync.readInt(Long.MinValue))
        defectEnd <- run(defectAsync.readInt(Long.MinValue))
        _         <- run(caughtAsync.close())
        _         <- run(defectAsync.close())
      } yield assertTrue(
        caught.jvmType == JvmType.Int,
        caughtDefect.jvmType == JvmType.Int,
        typedEnd == Long.MinValue,
        defectEnd == Long.MinValue
      )
    },
    test("dynamic asynchronous inners preserve take/drop and error/recovery boundaries") {
      val bounded = Stream(0).flatMap(_ =>
        Stream.unwrap(
          Async.succeed(Stream.fromReader[Nothing, Int](Reader.fromIterable(List(1, 2, 3)).toAsync).take(2).drop(1))
        )
      )
      val recovered = Stream(0).flatMap(_ =>
        Stream.unwrap(
          Async.succeed(
            Stream
              .fail("x")
              .asInstanceOf[Stream[String, Int]]
              .mapErrorAsync(error => Async.succeed(error + "i"))
              .catchAll(error => Stream.unwrap(Async.succeed(Stream.succeed(error.length))))
          )
        )
      )
      for {
        boundedResult   <- run(bounded.runCollectAsync)
        recoveredResult <- run(recovered.runCollectAsync)
      } yield assertTrue(boundedResult == Right(Chunk(2)), recoveredResult == Right(Chunk(2)))
    },
    test("deep rejected asynchronous child closes before releasing outer resources") {
      val order        = new java.util.ArrayList[String]
      val rightFailure = new RuntimeException("right-materialization")
      val left         = Reader.singleInt(1).toAsync.withReleaseAsync(() => Async.succeed { order.add("left-close"); () })
      val zipped       = Stream.fromAcquireRelease((), (_: Unit) => { order.add("release"); () }) { _ =>
        Stream.fromReader[Nothing, Int](left) && Stream.fromReader[Nothing, Int] {
          throw rightFailure
        }
      }
      val stream = (0 until Stream.DepthCutoff + 10).foldLeft(zipped) { (current, _) =>
        current.mapAsync(value => Async.succeed(value))
      }
      for {
        result <- run(stream.runCollectAsync).either
      } yield assertTrue(
        result.left.toOption.contains(rightFailure),
        order.toArray.toList == List("left-close", "release")
      )
    },
    test("deep chunked nodes preserve an asynchronous upstream") {
      val source = Stream.fromReader[Nothing, Int](Reader.singleInt(1).toAsync): Stream[Nothing, Int]
      val stream = (0 until Stream.DepthCutoff + 10).foldLeft(source)((current, _) => current.chunked(1).map(_.head))
      run(stream.runCollectAsync).map(result => assertTrue(result == Right(Chunk(1))))
    },
    test("deep intersperse nodes preserve an asynchronous upstream") {
      val source = Stream.fromReader[Nothing, Int](Reader.singleInt(1).toAsync): Stream[Nothing, Int]
      val stream = (0 until Stream.DepthCutoff + 10).foldLeft(source)((current, _) => current.intersperse(0))
      run(stream.runCollectAsync).map(result => assertTrue(result == Right(Chunk(1))))
    },
    test("deep sliding nodes preserve an asynchronous upstream") {
      val source = Stream.fromReader[Nothing, Int](Reader.singleInt(1).toAsync): Stream[Nothing, Int]
      val stream = (0 until Stream.DepthCutoff + 10).foldLeft(source)((current, _) => current.sliding(1).map(_.head))
      run(stream.runCollectAsync).map(result => assertTrue(result == Right(Chunk(1))))
    },
    test("deep scan nodes preserve an asynchronous upstream") {
      val depths =
        List(1, 2, 10, Stream.DepthCutoff - 1, Stream.DepthCutoff, Stream.DepthCutoff + 1, Stream.DepthCutoff + 10)
      ZIO
        .foreach(depths) { depth =>
          val source = Stream.fromReader[Nothing, Int](Reader.singleInt(1).toAsync): Stream[Nothing, Int]
          val stream = (0 until depth).foldLeft(source) { (current, _) =>
            current.scan(0)((_, value) => value).drop(1)
          }
          run(stream.runCollectAsync)
        }
        .map(results => assertTrue(results == depths.map(_ => Right(Chunk(1)))))
    },
    test("deep deferred nodes preserve an asynchronous upstream") {
      val source = Stream.fromReader[Nothing, Int](Reader.singleInt(1).toAsync): Stream[Nothing, Int]
      val stream = (0 until Stream.DepthCutoff + 10).foldLeft(source)((current, _) => Stream.suspend(current))
      run(stream.runCollectAsync).map(result => assertTrue(result == Right(Chunk(1))))
    },
    test("deep resource nodes preserve and finalize an asynchronous upstream") {
      val depth     = Stream.DepthCutoff + 10
      val acquired  = new AtomicInteger
      val released  = new AtomicInteger
      val source    = Stream.fromReader[Nothing, Int](Reader.singleInt(1).toAsync): Stream[Nothing, Int]
      val resources = (0 until depth).foldLeft(source) { (stream, _) =>
        Stream.fromAcquireRelease({ acquired.incrementAndGet(); () }, (_: Unit) => released.incrementAndGet())(_ =>
          stream
        )
      }
      run(resources.runCollectAsync).map(result =>
        assertTrue(result == Right(Chunk(1)), acquired.get == depth, released.get == depth)
      )
    },
    test("deep concurrency nodes preserve an asynchronous upstream") {
      val source = Stream.fromReader[Nothing, Int](Reader.singleInt(1).toAsync): Stream[Nothing, Int]
      val stream = (0 until Stream.DepthCutoff + 10).foldLeft(source) { (stream, _) =>
        val merged = Stream.mergeAll(1)(Stream.succeed(stream))
        Stream.bufferSize(16)(merged.buffer(1).mapPar(1)(identity))
      }
      run(stream.runCollectAsync).map(result => assertTrue(result == Right(Chunk(1))))
    },
    test("deep zip nodes preserve an asynchronous upstream") {
      val source = Stream.fromReader[Nothing, Int](Reader.singleInt(1).toAsync): Stream[Nothing, Int]
      val stream = (0 until Stream.DepthCutoff + 10).foldLeft(source) { (stream, _) =>
        (stream && Stream.succeed(0)).map(_._1)
      }
      run(stream.runCollectAsync).map(result => assertTrue(result == Right(Chunk(1))))
    },
    test("deep synchronous graphs preserve reader kind and eager materialization") {
      val depth     = Stream.DepthCutoff + 10
      val suspended = new AtomicInteger
      val acquired  = new AtomicInteger
      val released  = new AtomicInteger
      val stateful  = (0 until depth).foldLeft(Stream.succeed(1): Stream[Nothing, Int]) { (stream, _) =>
        stream.chunked(1).map(_.head).sliding(1).map(_.head).scan(0)((_, value) => value).drop(1)
      }
      val deferred = (0 until depth).foldLeft(Stream.succeed(1): Stream[Nothing, Int]) { (stream, _) =>
        Stream.suspend { suspended.incrementAndGet(); stream }
      }
      val resources = (0 until depth).foldLeft(Stream.succeed(1): Stream[Nothing, Int]) { (stream, _) =>
        Stream.fromAcquireRelease({ acquired.incrementAndGet(); () }, (_: Unit) => released.incrementAndGet())(_ =>
          stream
        )
      }
      val statefulReader = Stream.compileToReader(stateful)
      val deferredReader = Stream.compileToReader(deferred)
      val resourceReader = Stream.compileToReader(resources)
      val values         = (statefulReader, deferredReader, resourceReader) match {
        case (
              statefulSync: Reader.SyncReader[Int @unchecked],
              deferredSync: Reader.SyncReader[Int @unchecked],
              resourceSync: Reader.SyncReader[Int @unchecked]
            ) =>
          val result = (statefulSync.read(-1), deferredSync.read(-1), resourceSync.read(-1))
          statefulSync.close()
          deferredSync.close()
          resourceSync.close()
          result
        case readers => throw new AssertionError(s"Expected synchronous readers, got $readers")
      }
      assertTrue(
        values == ((1, 1, 1)),
        suspended.get == depth,
        acquired.get == depth,
        released.get == depth
      )
    },
    test("startAsync materializes unsuffixed deferred resources before returning and close-before-read finalizes") {
      val suspended = new AtomicInteger
      val acquired  = new AtomicInteger
      val released  = new AtomicInteger
      val stream    = Stream.suspend {
        suspended.incrementAndGet()
        Stream.fromAcquireRelease({ acquired.incrementAndGet(); () }, (_: Unit) => released.incrementAndGet())(_ =>
          Stream.succeed(1)
        )
      }
      for {
        reader <- run(stream.startAsync)
        before <- ZIO.succeed((suspended.get, acquired.get, released.get))
        _      <- run(reader.close())
      } yield assertTrue(before == ((1, 1, 0)), released.get == 1)
    },
    test("segmented pipelines close synchronous and asynchronous sources exactly once") {
      val syncCloses  = new AtomicInteger
      val asyncCloses = new AtomicInteger
      val syncSource  = Stream.fromReader[Nothing, Int](
        Reader.singleInt(1).withRelease { () => syncCloses.incrementAndGet(); () }
      )
      val asyncSource = Stream.fromReader[Nothing, Int](
        Reader.singleInt(1).toAsync.withReleaseAsync(() => Async.succeed { asyncCloses.incrementAndGet(); () })
      )
      def exceed(source: Stream[Nothing, Int]): Stream[Nothing, Int] =
        (0 until (StreamState.MaxIndex + Stream.DepthCutoff)).foldLeft(source)((stream, _) => stream.map(identity))
      val syncReader = Stream.compileToReader(exceed(syncSource)).asInstanceOf[Reader.SyncReader[Int]]
      val syncValue  = syncReader.read(-1)
      syncReader.close()
      val asyncReader = Stream.compileToReader(exceed(asyncSource)).asInstanceOf[Reader.AsyncReader[Int]]
      for {
        asyncValue <- run(asyncReader.read(-1))
        _          <- run(asyncReader.close())
      } yield assertTrue(
        syncValue == 1,
        asyncValue == 1,
        syncCloses.get == 1,
        asyncCloses.get == 1
      )
    },
    test("a valid finite identity pipeline remains compositional beyond the packed interpreter index") {
      val stream =
        (0 until (StreamState.MaxIndex + Stream.DepthCutoff)).foldLeft(Stream.succeed(1))((result, _) =>
          result.map(identity)
        )
      run(stream.runCollectAsync).map(result => assertTrue(result == Right(Chunk(1))))
    },
    test("compiler metadata failure closes synchronous and asynchronous sources exactly once") {
      val syncPrimary  = new RuntimeException("sync-metadata")
      val asyncPrimary = new RuntimeException("async-metadata")
      val syncCloses   = new AtomicInteger
      val asyncCloses  = new AtomicInteger
      val releases     = new AtomicInteger
      val syncOwner    = new Reader.SyncReader[Int] {
        override def jvmType: JvmType      = throw syncPrimary
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A = sentinel
        def close(): Unit                  = { syncCloses.incrementAndGet(); () }
      }
      val asyncOwner = new Reader.AsyncReader[Int] {
        override def jvmType: JvmType             = throw asyncPrimary
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(sentinel)
        def close(): Async[Unit]                  = Async.succeed { asyncCloses.incrementAndGet(); () }
      }
      val deepSync =
        (0 to Stream.DepthCutoff).foldLeft(Stream.fromReader[Nothing, Int](syncOwner))((stream, _) =>
          stream.map(identity)
        )
      val syncFailure =
        try { Stream.compileToReader(deepSync); null }
        catch { case cause: Throwable => cause }
      val asyncReader = Stream.compileToReader(
        Stream
          .fromAcquireRelease((), (_: Unit) => { releases.incrementAndGet(); () })(_ =>
            Stream.fromReader[Nothing, Int](asyncOwner)
          )
          .map(identity)
      )
      for {
        asyncFailure <- run(asyncReader.asInstanceOf[Reader.AsyncReader[Int]].read(-1).either)
      } yield assertTrue(
        syncFailure eq syncPrimary,
        asyncFailure.left.toOption.contains(asyncPrimary),
        syncCloses.get == 1,
        asyncCloses.get == 1,
        releases.get == 1
      )
    },
    test("mapParAsync preserves the full signed byte domain") {
      val values = Chunk(Byte.MinValue, -1.toByte, 0.toByte, 1.toByte, Byte.MaxValue)
      for {
        result <- run(Stream.fromIterable(values).mapParAsync(3)(Async.succeed).runCollectAsync)
      } yield assertTrue(result.exists(_.toList.sorted == values.toList.sorted))
    },
    test("mergeAll asynchronous folds preserve null failures and classify callback StreamErrors as defects") {
      def failing(trusted: Boolean) = new Reader.AsyncReader[Int] {
        def close()                                                    = Async.succeed(())
        override def jvmType                                           = JvmType.Int
        def isClosed                                                   = Async.succeed(false)
        def read[A >: Int](sentinel: A)                                = if (trusted) Async.failTrusted(null) else Async.fail(null)
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int) =
          if (trusted) Async.failTrusted(null) else Async.fail(null)
        def readable() = Async.succeed(true)
      }
      def merged(reader: Reader.AsyncReader[Int]) =
        Stream.mergeAll(1)(Stream.succeed(Stream.fromReaderAsync(Async.succeed(reader))))
      val nullCallback = Stream
        .mergeAll(1)(Stream.succeed(Stream.succeed(1)))
        .runFoldAsync(0L)((_, _) => Async.fail(null))
        .either
      val untrustedSource =
        merged(failing(trusted = false)).runFoldAsync(0L)((sum, value) => Async.succeed(sum + value)).either
      val trustedSource =
        merged(failing(trusted = true)).runFoldAsync(0L)((sum, value) => Async.succeed(sum + value)).either
      val forged          = new StreamError("forged")
      val genericCallback = Stream
        .mergeAll(1)(Stream.succeed(Stream.succeed("value")))
        .runFoldAsync(0L)((_, _) => Async.fail(forged))
        .either
      def isNullThrow(failure: Throwable): Boolean =
        failure
          .isInstanceOf[NullPointerException] || Option(failure.getCause).exists(_.isInstanceOf[NullPointerException])
      for {
        callbackResult  <- run(nullCallback)
        untrustedResult <- run(untrustedSource)
        trustedResult   <- run(trustedSource)
        genericResult   <- run(genericCallback)
      } yield assertTrue(
        callbackResult.left.exists(isNullThrow),
        untrustedResult.left.exists(isNullThrow),
        trustedResult.left.exists(isNullThrow),
        genericResult.left.exists {
          case error: StreamError => (error ne forged) && error.value == "forged" && !error.isTrusted
          case _                  => false
        }
      )
    },
    test("startAsync transfers ownership and useReaderAsync retains it") {
      val closes = new AtomicInteger
      def stream =
        Stream.fromReader[Nothing, Int](Reader.fromIterable(List(1)).withRelease(() => closes.incrementAndGet()))
      for {
        reader <- run(stream.startAsync)
        value  <- run(reader.read(-1))
        _      <- run(reader.close())
        used   <- run(stream.useReaderAsync(_.read(-1)))
      } yield assertTrue(value == 1, used == 1, closes.get == 2)
    }
  )
}

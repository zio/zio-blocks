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
import zio.blocks.streams.internal.StreamError
import zio.blocks.streams.io.Reader
import zio.test._

object AsyncSourceMappedIntIntUseSpec extends StreamsBaseSpec {
  private implicit val ec: ExecutionContext = new ExecutionContext {
    def execute(runnable: Runnable): Unit     = Async.schedule(runnable, forceMacrotask = false)
    def reportFailure(cause: Throwable): Unit = throw cause
  }

  private sealed trait Folded[+A]
  private final case class Ready[A](value: A)             extends Folded[A]
  private final case class Failed(cause: Throwable)       extends Folded[Nothing]
  private final case class Pending[A](value: Pollable[A]) extends Folded[A]

  private def fold[A](effect: Async[A]): Folded[A] =
    Async.foldStep(effect)(new Async.StepFold[A, Folded[A]] {
      def success(value: A): Folded[A]                         = Ready(value)
      def failure(cause: Throwable): Folded[A]                 = Failed(cause)
      override def trustedFailure(cause: Throwable): Folded[A] = Failed(cause)
      def pending(value: Pollable[A]): Folded[A]               = Pending(value)
    })

  private final class IntReader(
    reads: Vector[() => Async[Long]],
    closeEffect: () => Async[Unit] = () => Async.succeed(())
  ) extends Reader.AsyncReader[Int] {
    private var index                                                           = 0
    val closes                                                                  = new AtomicInteger
    val readCount                                                               = new AtomicInteger
    override def jvmType: JvmType                                               = JvmType.Int
    def close(): Async[Unit]                                                    = { closes.incrementAndGet(); closeEffect() }
    def isClosed: Async[Boolean]                                                = Async.succeed(index >= reads.length)
    def readable(): Async[Boolean]                                              = Async.succeed(index < reads.length)
    def read[A >: Int](sentinel: A): Async[A]                                   = Async.fail(new AssertionError("generic read"))
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = {
      readCount.incrementAndGet()
      if (index >= reads.length) Async.succeed(sentinel)
      else {
        val next = reads(index)
        index += 1
        next()
      }
    }
  }

  private final class CancelOnPoll[A](result: Async[A]) extends Pollable[A] {
    private var armed                    = false
    private var cancelAction: () => Unit = null
    private var wake: Runnable           = null
    val entered                          = new Completer[Unit]

    def arm(action: () => Unit): Unit = {
      val notify = synchronized { armed = true; cancelAction = action; wake }
      if (notify ne null) notify.run()
    }

    def poll(onComplete: Runnable): Async[A] = {
      val action = synchronized {
        if (armed) cancelAction
        else { wake = onComplete; entered.succeed(()); null }
      }
      if (action eq null) this
      else { action(); result }
    }
  }

  private def run[A](effect: Async[A]): ZIO[Any, Throwable, A] = ZIO.fromFuture(_ => effect.toFuture)

  private def ready(values: Int*): IntReader =
    new IntReader(values.map(value => () => Async.succeed(value.toLong)).toVector)

  private def mapped[E, B](reader: IntReader)(f: Int => B): Stream[E, B] =
    Stream.fromReaderAsync[E, Int](Async.succeed(reader: Reader[Int])).map(f)

  private def protectedFailure(result: Either[Throwable, _], value: String): Boolean = result.left.exists {
    case error: StreamError => error.value == value && !error.isTrusted
    case _                  => false
  }

  def spec: Spec[TestEnvironment with Scope, Any] = suite("AsyncSourceMappedIntIntUse")(
    test("uses the physical Int lane for ready values and EOF with Int and reference maps") {
      val ints = ready(1, 2, 3)
      val refs = ready(4, 5)
      for {
        intResult <- run(mapped[Nothing, Int](ints)(_ + 1).runFoldAsync(0)((acc, value) => Async.succeed(acc + value)))
        refResult <-
          run(
            mapped[Nothing, String](refs)(_.toString).runFoldAsync(1)((acc, value) => Async.succeed(acc + value.toInt))
          )
      } yield assertTrue(
        intResult == Right(9),
        refResult == Right(10),
        ints.readCount.get == 4,
        refs.readCount.get == 3,
        ints.closes.get == 1,
        refs.closes.get == 1
      )
    },
    test("resumes genuinely pending physical reads with a value and EOF") {
      val valueEntered = new Completer[Unit]
      val valueGate    = new Completer[Long]
      val eofEntered   = new Completer[Unit]
      val eofGate      = new Completer[Long]
      val valueReader  = new IntReader(Vector { () => valueEntered.succeed(()); valueGate })
      val eofReader    = new IntReader(Vector { () => eofEntered.succeed(()); eofGate })
      val valueRun     = mapped[Nothing, Int](valueReader)(_ + 1).runFoldAsync(10)((a, b) => Async.succeed(a + b)).start
      val eofRun       =
        mapped[Nothing, String](eofReader)(_.toString).runFoldAsync(10)((a, b) => Async.succeed(a + b.toInt)).start
      for {
        _           <- run(valueEntered)
        _            = valueGate.succeed(2L)
        _           <- run(eofEntered)
        _            = eofGate.succeed(Long.MinValue)
        valueResult <- run(valueRun)
        eofResult   <- run(eofRun)
      } yield assertTrue(
        valueResult == Right(13),
        eofResult == Right(10),
        valueReader.closes.get == 1,
        eofReader.closes.get == 1
      )
    },
    test("resumes a genuinely pending Int fold") {
      val entered = new Completer[Unit]
      val gate    = new Completer[Int]
      val reader  = ready(2, 3)
      val running = mapped[Nothing, Int](reader)(identity)
        .runFoldAsync(1) { (acc, value) =>
          if (value == 2) { entered.succeed(()); gate }
          else Async.succeed(acc + value)
        }
        .start
      for {
        _      <- run(entered)
        _       = gate.succeed(3)
        result <- run(running)
      } yield assertTrue(result == Right(6), reader.closes.get == 1)
    },
    test("classifies ordinary, trusted, thrown, and trusted-thrown physical reads") {
      val ordinary = new RuntimeException("ordinary")
      val typed    = StreamError.source("typed")
      val thrown   = new RuntimeException("thrown")
      val readers  = Vector(
        new IntReader(Vector(() => Async.fail(ordinary))),
        new IntReader(Vector(() => Async.failTrusted(typed))),
        new IntReader(Vector(() => throw thrown)),
        new IntReader(Vector(() => throw typed))
      )
      for {
        ordinaryResult <-
          run(mapped[Nothing, Int](readers(0))(identity).runFoldAsync(0)((a, b) => Async.succeed(a + b)).either)
        trustedResult <- run(mapped[String, Int](readers(1))(identity).runFoldAsync(0)((a, b) => Async.succeed(a + b)))
        thrownResult  <-
          run(
            mapped[Nothing, String](readers(2))(_.toString).runFoldAsync(0)((a, b) => Async.succeed(a + b.toInt)).either
          )
        typedResult <- run(mapped[String, Int](readers(3))(identity).runFoldAsync(0)((a, b) => Async.succeed(a + b)))
      } yield assertTrue(
        ordinaryResult == Left(ordinary),
        trustedResult == Left("typed"),
        thrownResult == Left(thrown),
        typedResult == Left("typed"),
        readers.forall(_.closes.get == 1)
      )
    },
    test("protects synchronous map throws and thrown or failed fold callbacks") {
      val mapThrow  = new StreamError("map-throw")
      val foldThrow = new StreamError("fold-throw")
      val foldFail  = new StreamError("fold-fail")
      for {
        mappedThrown <-
          run(
            mapped[Nothing, Int](ready(1))(_ => throw mapThrow).runFoldAsync(0)((a, b) => Async.succeed(a + b)).either
          )
        foldThrown <-
          run(mapped[Nothing, String](ready(1))(_.toString).runFoldAsync(0)((_, _) => throw foldThrow).either)
        foldFailed <-
          run(mapped[Nothing, Int](ready(1))(identity).runFoldAsync(0)((_, _) => Async.failTrusted(foldFail)).either)
      } yield assertTrue(
        protectedFailure(mappedThrown, "map-throw"),
        protectedFailure(foldThrown, "fold-throw"),
        protectedFailure(foldFailed, "fold-fail")
      )
    },
    test("reports close-only failure and suppresses close failure onto use failure") {
      val closeOnly  = new RuntimeException("close-only")
      val primary    = new RuntimeException("primary")
      val cleanup    = new RuntimeException("cleanup")
      val successful = new IntReader(Vector.empty, () => Async.fail(closeOnly))
      val failed     = new IntReader(Vector(() => Async.fail(primary)), () => Async.fail(cleanup))
      for {
        closeResult <-
          run(mapped[Nothing, Int](successful)(identity).runFoldAsync(0)((a, b) => Async.succeed(a + b)).either)
        useResult <-
          run(mapped[Nothing, String](failed)(_.toString).runFoldAsync(0)((a, b) => Async.succeed(a + b.toInt)).either)
      } yield assertTrue(
        closeResult == Left(closeOnly),
        useResult.left.exists(error => (error eq primary) && error.getSuppressed.toList == List(cleanup)),
        successful.closes.get == 1,
        failed.closes.get == 1
      )
    },
    test("protects thrown and asynchronously failed acquisition") {
      val thrown    = new StreamError("acquire-throw")
      val failed    = new StreamError("acquire-fail")
      val thrownRun = Stream
        .fromReaderAsync[Nothing, Int](throw thrown)
        .map(identity)
        .runFoldAsync(0)((a, b) => Async.succeed(a + b))
        .either
      val failedRun = Stream
        .fromReaderAsync[Nothing, Int](Async.fail(failed))
        .map(_.toString)
        .runFoldAsync(0)((a, b) => Async.succeed(a + b.toInt))
        .either
      for {
        thrownResult <- run(thrownRun)
        failedResult <- run(failedRun)
      } yield assertTrue(
        protectedFailure(thrownResult, "acquire-throw"),
        protectedFailure(failedResult, "acquire-fail")
      )
    },
    test("closes when acquisition succeeds concurrently with cancellation") {
      val reader                               = ready(1)
      val acquire                              = new CancelOnPoll[Reader[Int]](Async.succeed(reader: Reader[Int]))
      var running: Async[Either[Nothing, Int]] = null
      var cleanup: Async[Unit]                 = null
      val cancelled                            = new Completer[Unit]
      running = Stream
        .fromReaderAsync[Nothing, Int](acquire)
        .map(identity)
        .runFoldAsync(0)((a, b) => Async.succeed(a + b))
        .start
      for {
        _ <- run(acquire.entered)
        _  = acquire.arm { () =>
              cleanup = Async.cancelWithCleanup(running.asInstanceOf[Pollable[Any]]); cancelled.succeed(())
            }
        _ <- run(cancelled)
        _ <- run(cleanup)
      } yield assertTrue(reader.closes.get == 1)
    },
    test("cancellation during a resumed asynchronous map joins cleanup and closes once") {
      val readGate = new Completer[Long]
      val entered  = new Completer[Unit]
      val reader   = new IntReader(Vector(() => readGate))
      var running  = null.asInstanceOf[Async.Running[Either[Nothing, Long]]]
      var cleanup  = null.asInstanceOf[Async[Unit]]
      val effect   = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(reader: Reader[Int]))
        .mapAsync { value =>
          cleanup = Async.cancelWithCleanup(running)
          entered.succeed(())
          Async.succeed(value)
        }
        .runFoldAsync(0L)((acc, value) => Async.succeed(acc + value))
      running = effect.start
      for {
        _ <- ZIO.succeed(readGate.succeed(1L))
        _ <- run(entered)
        _ <- run(cleanup)
      } yield assertTrue(reader.closes.get == 1)
    },
    test("cancellation during a resumed asynchronous fold joins cleanup and closes once") {
      val mapGate = new Completer[Int]
      val entered = new Completer[Unit]
      val reader  = ready(1)
      var running = null.asInstanceOf[Async.Running[Either[Nothing, Long]]]
      var cleanup = null.asInstanceOf[Async[Unit]]
      val effect  = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(reader: Reader[Int]))
        .mapAsync(_ => mapGate)
        .runFoldAsync(0L) { (acc, value) =>
          cleanup = Async.cancelWithCleanup(running)
          entered.succeed(())
          Async.succeed(acc + value)
        }
      running = effect.start
      for {
        _ <- ZIO.succeed(mapGate.succeed(1))
        _ <- run(entered)
        _ <- run(cleanup)
      } yield assertTrue(reader.closes.get == 1)
    },
    test("cancellation during mapped continuation outcomes joins every replacement and closes once") {
      def readScenario(after: (() => Unit) => Async[Long]): ZIO[Any, Throwable, Boolean] = {
        val gate           = new Completer[Long]
        val entered        = new Completer[Unit]
        var running        = null.asInstanceOf[Async.Running[Either[Nothing, Long]]]
        var cleanup        = null.asInstanceOf[Async[Unit]]
        def cancel(): Unit = { cleanup = Async.cancelWithCleanup(running); entered.succeed(()) }
        val reader         = new IntReader(Vector(() => gate, () => after(() => cancel())))
        running = Stream
          .fromReaderAsync[Nothing, Int](Async.succeed(reader: Reader[Int]))
          .mapAsync(Async.succeed)
          .runFoldAsync(0L)((acc, value) => Async.succeed(acc + value))
          .start
        for {
          _ <- ZIO.succeed(gate.succeed(1L))
          _ <- run(entered)
          _ <- run(cleanup)
        } yield reader.closes.get == 1
      }
      def mapScenario(after: (() => Unit) => Async[Int]): ZIO[Any, Throwable, Boolean] = {
        val gate           = new Completer[Long]
        val entered        = new Completer[Unit]
        val calls          = new AtomicInteger
        val reader         = new IntReader(Vector(() => gate, () => Async.succeed(2L)))
        var running        = null.asInstanceOf[Async.Running[Either[Nothing, Long]]]
        var cleanup        = null.asInstanceOf[Async[Unit]]
        def cancel(): Unit = { cleanup = Async.cancelWithCleanup(running); entered.succeed(()) }
        running = Stream
          .fromReaderAsync[Nothing, Int](Async.succeed(reader: Reader[Int]))
          .mapAsync(value => if (calls.incrementAndGet() == 1) Async.succeed(value) else after(() => cancel()))
          .runFoldAsync(0L)((acc, value) => Async.succeed(acc + value))
          .start
        for {
          _ <- ZIO.succeed(gate.succeed(1L))
          _ <- run(entered)
          _ <- run(cleanup)
        } yield reader.closes.get == 1
      }
      def foldScenario(after: (() => Unit) => Async[Long]): ZIO[Any, Throwable, Boolean] = {
        val gate           = new Completer[Long]
        val entered        = new Completer[Unit]
        val calls          = new AtomicInteger
        val reader         = new IntReader(Vector(() => gate, () => Async.succeed(2L)))
        var running        = null.asInstanceOf[Async.Running[Either[Nothing, Long]]]
        var cleanup        = null.asInstanceOf[Async[Unit]]
        def cancel(): Unit = { cleanup = Async.cancelWithCleanup(running); entered.succeed(()) }
        running = Stream
          .fromReaderAsync[Nothing, Int](Async.succeed(reader: Reader[Int]))
          .mapAsync(Async.succeed)
          .runFoldAsync(0L) { (acc, value) =>
            if (calls.incrementAndGet() == 1) Async.succeed(acc + value) else after(() => cancel())
          }
          .start
        for {
          _ <- ZIO.succeed(gate.succeed(1L))
          _ <- run(entered)
          _ <- run(cleanup)
        } yield reader.closes.get == 1
      }
      val ordinary = new RuntimeException("cancelled continuation")
      val trusted  = StreamError.source("cancelled trusted continuation")
      for {
        thrown        <- readScenario { cancel => cancel(); throw ordinary }
        trustedThrown <- readScenario { cancel => cancel(); throw trusted }
        failed        <- readScenario { cancel => cancel(); Async.fail(ordinary) }
        pendingRead   <- readScenario { cancel => cancel(); new Completer[Long] }
        eof           <- readScenario { cancel => cancel(); Async.succeed(Long.MinValue) }
        mapFailed     <- mapScenario { cancel => cancel(); Async.fail(ordinary) }
        mapPending    <- mapScenario { cancel => cancel(); new Completer[Int] }
        foldFailed    <- foldScenario { cancel => cancel(); Async.fail(ordinary) }
        foldPending   <- foldScenario { cancel => cancel(); new Completer[Long] }
      } yield assertTrue(
        thrown,
        trustedThrown,
        failed,
        pendingRead,
        eof,
        mapFailed,
        mapPending,
        foldFailed,
        foldPending
      )
    },
    test("cancellation at the mapped continuation fairness boundary joins cleanup and closes once") {
      val gate    = new Completer[Long]
      val entered = new Completer[Unit]
      val calls   = new AtomicInteger
      val reader  = new IntReader((Vector(() => gate) ++ Vector.fill(1024)(() => Async.succeed(1L))))
      var running = null.asInstanceOf[Async.Running[Either[Nothing, Long]]]
      var cleanup = null.asInstanceOf[Async[Unit]]
      running = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(reader: Reader[Int]))
        .mapAsync { value =>
          if (calls.incrementAndGet() == 1025) {
            cleanup = Async.cancelWithCleanup(running)
            entered.succeed(())
          }
          Async.succeed(value)
        }
        .runFoldAsync(0L)((acc, value) => Async.succeed(acc + value))
        .start
      for {
        _ <- ZIO.succeed(gate.succeed(1L))
        _ <- run(entered)
        _ <- run(cleanup)
      } yield assertTrue(calls.get == 1025, reader.closes.get == 1)
    },
    test("yields at the 1024-callback fairness boundary and resumes") {
      val reader                   = ready((0 to 1024): _*)
      val callbacks                = new AtomicInteger
      val callbacksWhenYieldWokeUp = new AtomicInteger(-1)
      val yieldPermit              = new Completer[Unit]
      val effect                   = mapped[Nothing, Int](reader) { value => callbacks.incrementAndGet(); value }
        .runFoldAsync(0)((acc, value) => Async.succeed(acc + value))
      val firstPoll = fold(effect) match {
        case Pending(operation) =>
          fold(operation.poll { () => callbacksWhenYieldWokeUp.set(callbacks.get); yieldPermit.succeed(()) })
        case result => result
      }
      for {
        _      <- run(yieldPermit)
        result <- run(effect)
      } yield assertTrue(
        firstPoll.isInstanceOf[Pending[_]],
        callbacksWhenYieldWokeUp.get >= 1024,
        callbacksWhenYieldWokeUp.get <= 1025,
        result == Right(524800),
        callbacks.get == 1025,
        reader.closes.get == 1
      )
    }
  )
}

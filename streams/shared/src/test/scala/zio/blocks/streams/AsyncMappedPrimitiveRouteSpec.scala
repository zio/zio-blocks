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
import zio.blocks.streams.internal.{AsyncInterpreter, StreamError, SyncInterpreter}
import zio.blocks.streams.io.Reader
import zio.test._

object AsyncMappedPrimitiveRouteSpec extends StreamsBaseSpec {
  private implicit val ec: ExecutionContext = new ExecutionContext {
    def execute(runnable: Runnable): Unit     = Async.schedule(runnable, forceMacrotask = false)
    def reportFailure(cause: Throwable): Unit = throw cause
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

  private def run[A](effect: Async[A]): ZIO[Any, Throwable, A] = ZIO.fromFuture(_ => effect.toFuture)

  private class IntReader(
    reads: Vector[() => Async[Long]],
    closeResult: () => Async[Unit] = () => Async.succeed(())
  ) extends Reader.AsyncReader[Int] {
    private var index                                                           = 0
    val closes                                                                  = new AtomicInteger
    val readCount                                                               = new AtomicInteger
    override def jvmType: JvmType                                               = JvmType.Int
    def close(): Async[Unit]                                                    = { closes.incrementAndGet(); closeResult() }
    def isClosed: Async[Boolean]                                                = Async.succeed(index >= reads.length)
    def readable(): Async[Boolean]                                              = Async.succeed(index < reads.length)
    def read[A >: Int](sentinel: A): Async[A]                                   = Async.fail(new AssertionError("generic read"))
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = {
      readCount.incrementAndGet()
      if (index >= reads.length) Async.succeed(sentinel)
      else { val read = reads(index); index += 1; read() }
    }
  }

  private final class ClosingSyncReader(closeFailure: Throwable) extends Reader.SyncReader[Int] {
    val closes: AtomicInteger          = new AtomicInteger
    override def jvmType: JvmType      = JvmType.Int
    def close(): Unit                  = { closes.incrementAndGet(); if (closeFailure ne null) throw closeFailure }
    def isClosed: Boolean              = false
    override def readable(): Boolean   = true
    def read[A >: Int](sentinel: A): A = sentinel
  }

  private def ready(values: Int*): IntReader =
    new IntReader(values.map(value => () => Async.succeed(value.toLong)).toVector)

  private def source[E](reader: IntReader): Stream[E, Int] =
    Stream.fromReaderAsync[E, Int](Async.succeed(reader: Reader[Int]))

  private final class FailingCompile(error: Throwable) extends Stream[Nothing, Int] {
    private[streams] def compile(depth: Int, bufferSize: Int): Reader[Int]   = throw error
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = throw error
    def render: String                                                       = "failing-compile"
  }

  def spec: Spec[TestEnvironment with Scope, Any] = suite("AsyncMapped primitive routes")(
    test("routes synchronous Int maps through asynchronous Float, Double, and Long folds") {
      val floatReader  = ready(1, 2, 3)
      val doubleReader = ready(1, 2, 3)
      val longReader   = ready(1, 2, 3)
      for {
        float <- run(
                   source[Nothing](floatReader)
                     .map(_.toFloat + 0.5f)
                     .mapAsync(value => Async.succeed(value * 2.0f))
                     .runFoldAsync(1.0f)((acc, value) => Async.succeed(acc + value))
                 )
        double <- run(
                    source[Nothing](doubleReader)
                      .map(_.toDouble + 0.25)
                      .mapAsync(value => Async.succeed(value * 2.0))
                      .runFoldAsync(1.0)((acc, value) => Async.succeed(acc + value))
                  )
        long <- run(
                  source[Nothing](longReader)
                    .map(_.toLong + 1L)
                    .mapAsync(value => Async.succeed(value * 2L))
                    .runFoldAsync(1L)((acc, value) => Async.succeed(acc + value))
                )
      } yield assertTrue(
        float == Right(16.0f),
        double == Right(14.5),
        long == Right(19L),
        floatReader.readCount.get == 4,
        doubleReader.readCount.get == 4,
        longReader.readCount.get == 4,
        floatReader.closes.get == 1,
        doubleReader.closes.get == 1,
        longReader.closes.get == 1
      )
    },
    test("interpreter terminal folds preserve every logical input and accumulator lane") {
      val ints = Stream.range(1, 4).mapAsync(Async.succeed)
      for {
        booleanInt <-
          run(
            ints
              .map(_ % 2 == 1)
              .runAsync(Sink.foldLeftAsync[Boolean, Int](0)((acc, value) => Async.succeed(acc + (if (value) 1 else 0))))
          )
        byteLong <- run(
                      ints
                        .map(_.toByte)
                        .runAsync(Sink.foldLeftAsync[Byte, Long](10L)((acc, value) => Async.succeed(acc + value)))
                    )
        charLong <- run(
                      ints
                        .map(value => ('a'.toInt + value).toChar)
                        .runAsync(Sink.foldLeftAsync[Char, Long](0L)((acc, value) => Async.succeed(acc + value.toLong)))
                    )
        shortLong <- run(
                       ints
                         .map(_.toShort)
                         .runAsync(Sink.foldLeftAsync[Short, Long](20L)((acc, value) => Async.succeed(acc + value)))
                     )
        intLong  <- run(ints.runAsync(Sink.foldLeftAsync[Int, Long](30L)((acc, value) => Async.succeed(acc + value))))
        longLong <- run(
                      ints
                        .map(_.toLong * 2L)
                        .runAsync(Sink.foldLeftAsync[Long, Long](40L)((acc, value) => Async.succeed(acc + value)))
                    )
        floatFloat <- run(
                        ints
                          .map(_.toFloat + 0.5f)
                          .runAsync(Sink.foldLeftAsync[Float, Float](1.0f)((acc, value) => Async.succeed(acc + value)))
                      )
        doubleDouble <-
          run(
            ints
              .map(_.toDouble + 0.25)
              .runAsync(Sink.foldLeftAsync[Double, Double](1.0)((acc, value) => Async.succeed(acc + value)))
          )
        refRef <- run(
                    ints
                      .map(_.toString)
                      .runAsync(Sink.foldLeftAsync[String, String]("x")((acc, value) => Async.succeed(acc + value)))
                  )
      } yield assertTrue(
        booleanInt == Right(2),
        byteLong == Right(16L),
        charLong == Right(297L),
        shortLong == Right(26L),
        intLong == Right(36L),
        longLong == Right(52L),
        floatFloat == Right(8.5f),
        doubleDouble == Right(7.75),
        refRef == Right("x123")
      )
    },
    test("interpreter slices and takeWhile preserve non-Int lanes and accumulators") {
      val ints = Stream.range(0, 6)
      for {
        longInt <- run(
                     ints
                       .map(_.toLong)
                       .mapAsync(Async.succeed)
                       .drop(2L)
                       .take(3L)
                       .runAsync(Sink.foldLeftAsync[Long, Int](10)((acc, value) => Async.succeed(acc + value.toInt)))
                   )
        floatInt <- run(
                      ints
                        .map(_.toFloat + 0.5f)
                        .mapAsync(Async.succeed)
                        .take(2L)
                        .runAsync(Sink.foldLeftAsync[Float, Int](10)((acc, value) => Async.succeed(acc + value.toInt)))
                    )
        doubleRef <- run(
                       ints
                         .map(_.toDouble + 0.25)
                         .mapAsync(Async.succeed)
                         .takeWhile(_ < 3.0)
                         .runAsync(
                           Sink.foldLeftAsync[Double, String]("")((acc, value) =>
                             Async.succeed(if (acc.isEmpty) value.toString else s"$acc|$value")
                           )
                         )
                     )
        booleanLong <-
          run(
            ints
              .map(_ % 2 == 0)
              .mapAsync(Async.succeed)
              .drop(1L)
              .take(3L)
              .runAsync(
                Sink.foldLeftAsync[Boolean, Long](0L)((acc, value) => Async.succeed(acc + (if (value) 1L else 0L)))
              )
          )
      } yield assertTrue(
        longInt == Right(19),
        floatInt == Right(11),
        doubleRef == Right("0.25|1.25|2.25"),
        booleanLong == Right(1L)
      )
    },
    test("interpreter inlines scalar flatMap children across logical lanes") {
      val stream = Stream
        .range(0, 1)
        .mapAsync(Async.succeed)
        .flatMap(_ => Stream.succeed(true))
        .flatMap(value => Stream.succeed(if (value) 2.toByte else 0.toByte))
        .flatMap(value => Stream.succeed((value + 1).toShort))
        .flatMap(value => Stream.succeed((value + 1).toChar))
        .flatMap(value => Stream.succeed(value.toInt + 1))
        .flatMap(value => Stream.succeed(value.toLong + 1L))
        .flatMap(value => Stream.succeed(value.toFloat + 0.5f))
        .flatMap(value => Stream.succeed(value.toDouble + 0.25d))
        .flatMap(value => Stream.succeed(value.toString))
      val concatenated = Stream
        .succeed(1L)
        .mapAsync(Async.succeed)
        .concat(Stream.succeed(2L).mapAsync(Async.succeed))

      for {
        flatMapped <- run(
                        stream.runAsync(
                          Sink.foldLeftAsync[String, String]("value=")((acc, value) => Async.succeed(acc + value))
                        )
                      )
        concat <- run(
                    concatenated.runAsync(
                      Sink.foldLeftAsync[Long, Double](0.5d)((acc, value) => Async.succeed(acc + value.toDouble))
                    )
                  )
      } yield assertTrue(flatMapped == Right("value=6.75"), concat == Right(3.5d))
    },
    test("direct Int to Long folding classifies ready and failed source lanes") {
      val ordinary          = new RuntimeException("direct-long-ordinary")
      val typed             = StreamError.source("direct-long-typed")
      val readyReader       = ready(1, 2, 3)
      val failedReader      = new IntReader(Vector(() => Async.fail(ordinary)))
      val trustedReader     = new IntReader(Vector(() => Async.failTrusted(typed)))
      val defectReader      = new IntReader(Vector(() => Async.failTrusted(ordinary)))
      val thrownReader      = new IntReader(Vector(() => throw ordinary))
      val typedThrownReader = new IntReader(Vector(() => throw typed))
      for {
        readyResult  <- run(source[Nothing](readyReader).runFoldAsync(1L)((acc, value) => Async.succeed(acc + value)))
        failedResult <- run(
                          source[Nothing](failedReader)
                            .runFoldAsync(0L)((acc, value) => Async.succeed(acc + value))
                            .either
                        )
        trustedResult <- run(
                           source[String](trustedReader)
                             .runFoldAsync(0L)((acc, value) => Async.succeed(acc + value))
                         )
        defectResult <- run(
                          source[Nothing](defectReader)
                            .runFoldAsync(0L)((acc, value) => Async.succeed(acc + value))
                            .either
                        )
        thrownResult <- run(
                          source[Nothing](thrownReader)
                            .runFoldAsync(0L)((acc, value) => Async.succeed(acc + value))
                            .either
                        )
        typedThrownResult <- run(
                               source[String](typedThrownReader)
                                 .runFoldAsync(0L)((acc, value) => Async.succeed(acc + value))
                             )
      } yield assertTrue(
        readyResult == Right(7L),
        failedResult == Left(ordinary),
        trustedResult == Left("direct-long-typed"),
        defectResult == Left(ordinary),
        thrownResult == Left(ordinary),
        typedThrownResult == Left("direct-long-typed"),
        readyReader.closes.get == 1,
        failedReader.closes.get == 1,
        trustedReader.closes.get == 1,
        defectReader.closes.get == 1,
        thrownReader.closes.get == 1,
        typedThrownReader.closes.get == 1
      )
    },
    test("resumes pending read, map, and fold on Float and Double routes") {
      val readGate          = new Completer[Long]
      val readStarted       = new Completer[Unit]
      val floatMapGate      = new Completer[Float]
      val floatMapStarted   = new Completer[Unit]
      val doubleFoldGate    = new Completer[Double]
      val doubleFoldStarted = new Completer[Unit]
      val pendingReadReader =
        new IntReader(Vector(() => { readStarted.succeed(()); readGate }, () => Async.succeed(2L)))
      val floatMapReader = ready(1, 2)
      val doubleReader   = ready(1, 2)
      val pendingReadRun = source[Nothing](pendingReadReader)
        .map(_.toFloat)
        .mapAsync(Async.succeed)
        .runFoldAsync(0.0f)((acc, value) => Async.succeed(acc + value))
        .start
      val floatMapRun = source[Nothing](floatMapReader)
        .map(_.toFloat)
        .mapAsync(value =>
          if (value == 1.0f) { floatMapStarted.succeed(()); floatMapGate }
          else Async.succeed(value)
        )
        .runFoldAsync(0.0f)((acc, value) => Async.succeed(acc + value))
        .start
      val doubleRun = source[Nothing](doubleReader)
        .map(_.toDouble)
        .mapAsync(Async.succeed)
        .runFoldAsync(0.0)((acc, value) =>
          if (value == 1.0) { doubleFoldStarted.succeed(()); doubleFoldGate }
          else Async.succeed(acc + value)
        )
        .start
      for {
        _           <- run(readStarted)
        _            = readGate.succeed(1L)
        _           <- run(floatMapStarted)
        _            = floatMapGate.succeed(1.0f)
        _           <- run(doubleFoldStarted)
        _            = doubleFoldGate.succeed(1.0)
        pendingRead <- run(pendingReadRun)
        float       <- run(floatMapRun)
        double      <- run(doubleRun)
      } yield assertTrue(
        pendingRead == Right(3.0f),
        float == Right(3.0f),
        double == Right(3.0),
        pendingReadReader.closes.get == 1,
        floatMapReader.closes.get == 1,
        doubleReader.closes.get == 1
      )
    },
    test("resumes pending Float and Double reads with values and end of stream") {
      def pendingReader(started: Completer[Unit], gate: Completer[Long]): IntReader =
        new IntReader(Vector { () => started.succeed(()); gate })

      val floatValueStarted  = new Completer[Unit]
      val floatValueGate     = new Completer[Long]
      val floatEofStarted    = new Completer[Unit]
      val floatEofGate       = new Completer[Long]
      val doubleValueStarted = new Completer[Unit]
      val doubleValueGate    = new Completer[Long]
      val doubleEofStarted   = new Completer[Unit]
      val doubleEofGate      = new Completer[Long]
      val floatValueReader   = pendingReader(floatValueStarted, floatValueGate)
      val floatEofReader     = pendingReader(floatEofStarted, floatEofGate)
      val doubleValueReader  = pendingReader(doubleValueStarted, doubleValueGate)
      val doubleEofReader    = pendingReader(doubleEofStarted, doubleEofGate)
      val floatValue         = source[Nothing](floatValueReader)
        .map(_.toFloat)
        .mapAsync(Async.succeed)
        .runFoldAsync(1.0f)((acc, value) => Async.succeed(acc + value))
        .start
      val floatEof = source[Nothing](floatEofReader)
        .map(_.toFloat)
        .mapAsync(Async.succeed)
        .runFoldAsync(1.0f)((acc, value) => Async.succeed(acc + value))
        .start
      val doubleValue = source[Nothing](doubleValueReader)
        .map(_.toDouble)
        .mapAsync(Async.succeed)
        .runFoldAsync(1.0)((acc, value) => Async.succeed(acc + value))
        .start
      val doubleEof = source[Nothing](doubleEofReader)
        .map(_.toDouble)
        .mapAsync(Async.succeed)
        .runFoldAsync(1.0)((acc, value) => Async.succeed(acc + value))
        .start
      for {
        _                 <- run(floatValueStarted)
        _                  = floatValueGate.succeed(2L)
        _                 <- run(floatEofStarted)
        _                  = floatEofGate.succeed(Long.MinValue)
        _                 <- run(doubleValueStarted)
        _                  = doubleValueGate.succeed(2L)
        _                 <- run(doubleEofStarted)
        _                  = doubleEofGate.succeed(Long.MinValue)
        floatValueResult  <- run(floatValue)
        floatEofResult    <- run(floatEof)
        doubleValueResult <- run(doubleValue)
        doubleEofResult   <- run(doubleEof)
      } yield assertTrue(
        floatValueResult == Right(3.0f),
        floatEofResult == Right(1.0f),
        doubleValueResult == Right(3.0),
        doubleEofResult == Right(1.0)
      )
    },
    test("classifies failed and thrown reads and always closes") {
      val ordinary            = new RuntimeException("ordinary-read")
      val typed               = StreamError.source("typed-read")
      val thrown              = new RuntimeException("thrown-read")
      val ordinaryRead        = new IntReader(Vector(() => Async.fail(ordinary)))
      val trustedRead         = new IntReader(Vector(() => Async.failTrusted(typed)))
      val thrownRead          = new IntReader(Vector(() => throw thrown))
      val floatOrdinaryThrow  = new IntReader(Vector(() => throw ordinary))
      val floatTrustedThrow   = new IntReader(Vector(() => throw typed))
      val floatTrustedDefect  = new IntReader(Vector(() => Async.failTrusted(ordinary)))
      val doubleOrdinaryThrow = new IntReader(Vector(() => throw ordinary))
      val doubleTrustedThrow  = new IntReader(Vector(() => throw typed))
      val doubleTrustedDefect = new IntReader(Vector(() => Async.failTrusted(ordinary)))
      val ordinaryRun         = source[String](ordinaryRead)
        .map(_.toFloat)
        .mapAsync(Async.succeed)
        .runFoldAsync(0.0f)((a, b) => Async.succeed(a + b))
        .either
      val trustedRun = source[String](trustedRead)
        .map(_.toDouble)
        .mapAsync(Async.succeed)
        .runFoldAsync(0.0)((a, b) => Async.succeed(a + b))
      val thrownRun = source[Nothing](thrownRead)
        .map(_.toLong)
        .mapAsync(Async.succeed)
        .runFoldAsync(0L)((a, b) => Async.succeed(a + b))
        .either
      val floatOrdinaryThrowRun = source[Nothing](floatOrdinaryThrow)
        .map(_.toFloat)
        .mapAsync(Async.succeed)
        .runFoldAsync(0.0f)((a, b) => Async.succeed(a + b))
        .either
      val floatTrustedThrowRun = source[String](floatTrustedThrow)
        .map(_.toFloat)
        .mapAsync(Async.succeed)
        .runFoldAsync(0.0f)((a, b) => Async.succeed(a + b))
      val floatTrustedDefectRun = source[Nothing](floatTrustedDefect)
        .map(_.toFloat)
        .mapAsync(Async.succeed)
        .runFoldAsync(0.0f)((a, b) => Async.succeed(a + b))
        .either
      val doubleOrdinaryThrowRun = source[Nothing](doubleOrdinaryThrow)
        .map(_.toDouble)
        .mapAsync(Async.succeed)
        .runFoldAsync(0.0)((a, b) => Async.succeed(a + b))
        .either
      val doubleTrustedThrowRun = source[String](doubleTrustedThrow)
        .map(_.toDouble)
        .mapAsync(Async.succeed)
        .runFoldAsync(0.0)((a, b) => Async.succeed(a + b))
      val doubleTrustedDefectRun = source[Nothing](doubleTrustedDefect)
        .map(_.toDouble)
        .mapAsync(Async.succeed)
        .runFoldAsync(0.0)((a, b) => Async.succeed(a + b))
        .either
      for {
        ordinaryResult            <- run(ordinaryRun)
        trustedResult             <- run(trustedRun)
        thrownResult              <- run(thrownRun)
        floatOrdinaryThrowResult  <- run(floatOrdinaryThrowRun)
        floatTrustedThrowResult   <- run(floatTrustedThrowRun)
        floatTrustedDefectResult  <- run(floatTrustedDefectRun)
        doubleOrdinaryThrowResult <- run(doubleOrdinaryThrowRun)
        doubleTrustedThrowResult  <- run(doubleTrustedThrowRun)
        doubleTrustedDefectResult <- run(doubleTrustedDefectRun)
      } yield assertTrue(
        ordinaryResult == Left(ordinary),
        trustedResult == Left("typed-read"),
        thrownResult == Left(thrown),
        floatOrdinaryThrowResult == Left(ordinary),
        floatTrustedThrowResult == Left("typed-read"),
        floatTrustedDefectResult == Left(ordinary),
        doubleOrdinaryThrowResult == Left(ordinary),
        doubleTrustedThrowResult == Left("typed-read"),
        doubleTrustedDefectResult == Left(ordinary),
        ordinaryRead.closes.get == 1,
        trustedRead.closes.get == 1,
        thrownRead.closes.get == 1,
        floatOrdinaryThrow.closes.get == 1,
        floatTrustedThrow.closes.get == 1,
        floatTrustedDefect.closes.get == 1,
        doubleOrdinaryThrow.closes.get == 1,
        doubleTrustedThrow.closes.get == 1,
        doubleTrustedDefect.closes.get == 1
      )
    },
    test("protects map and fold callbacks, including forged StreamErrors") {
      val mapThrow                                                          = new StreamError("map-throw")
      val foldThrow                                                         = new StreamError("fold-throw")
      val mapFailure                                                        = new StreamError("map-fail")
      val foldTyped                                                         = StreamError.source("fold-typed")
      def isProtected(result: Either[Throwable, _], value: String): Boolean = result.left.exists {
        case error: StreamError => (error.value == value) && !error.isTrusted
        case _                  => false
      }
      for {
        thrownMap <- run(
                       source[Nothing](ready(1))
                         .map(_.toFloat)
                         .mapAsync[Float](_ => throw mapThrow)
                         .runFoldAsync(0.0f)((a, b) => Async.succeed(a + b))
                         .either
                     )
        failedMap <- run(
                       source[Nothing](ready(1))
                         .map(_.toDouble)
                         .mapAsync[Double](_ => Async.fail(mapFailure))
                         .runFoldAsync(0.0)((a, b) => Async.succeed(a + b))
                         .either
                     )
        thrownFold <- run(
                        source[Nothing](ready(1))
                          .map(_.toLong)
                          .mapAsync(Async.succeed)
                          .runFoldAsync(0L)((_, _) => throw foldThrow)
                          .either
                      )
        typedFold <- run(
                       source[String](ready(1))
                         .map(_.toFloat)
                         .mapAsync(Async.succeed)
                         .runFoldAsync(0.0f)((_, _) => Async.failTrusted(foldTyped))
                         .either
                     )
      } yield assertTrue(
        isProtected(thrownMap, "map-throw"),
        isProtected(failedMap, "map-fail"),
        isProtected(thrownFold, "fold-throw"),
        isProtected(typedFold, "fold-typed")
      )
    },
    test("reports close failure and suppresses it onto a primary failure") {
      val closeOnly       = new RuntimeException("close-only")
      val primary         = new RuntimeException("primary")
      val suppressedClose = new RuntimeException("suppressed-close")
      val successReader   = new IntReader(Vector.empty, () => Async.fail(closeOnly))
      val failedReader    = new IntReader(Vector(() => Async.fail(primary)), () => Async.fail(suppressedClose))
      for {
        closeResult <- run(
                         source[Nothing](successReader)
                           .map(_.toFloat)
                           .mapAsync(Async.succeed)
                           .runFoldAsync(0.0f)((a, b) => Async.succeed(a + b))
                           .either
                       )
        useResult <- run(
                       source[Nothing](failedReader)
                         .map(_.toDouble)
                         .mapAsync(Async.succeed)
                         .runFoldAsync(0.0)((a, b) => Async.succeed(a + b))
                         .either
                     )
      } yield assertTrue(
        closeResult == Left(closeOnly),
        useResult.left.exists(error => (error eq primary) && error.getSuppressed.contains(suppressedClose)),
        successReader.closes.get == 1,
        failedReader.closes.get == 1
      )
    },
    test("yields after 1024 ready elements and resumes all primitive routes") {
      def values = ready((0 to 1024): _*)
      for {
        float <- run(
                   source[Nothing](values)
                     .map(_.toFloat)
                     .mapAsync(Async.succeed)
                     .runFoldAsync(0.0f)((a, b) => Async.succeed(a + b))
                 )
        double <- run(
                    source[Nothing](values)
                      .map(_.toDouble)
                      .mapAsync(Async.succeed)
                      .runFoldAsync(0.0)((a, b) => Async.succeed(a + b))
                  )
        long <- run(
                  source[Nothing](values)
                    .map(_.toLong)
                    .mapAsync(Async.succeed)
                    .runFoldAsync(0L)((a, b) => Async.succeed(a + b))
                )
      } yield assertTrue(float == Right(524800.0f), double == Right(524800.0), long == Right(524800L))
    },
    test("protects synchronous and asynchronously failed acquisition") {
      val thrown    = new StreamError("acquire-throw")
      val failed    = new StreamError("acquire-fail")
      val thrownRun = Stream
        .fromReaderAsync[Nothing, Int](throw thrown)
        .map(_.toFloat)
        .mapAsync(Async.succeed)
        .runFoldAsync(0.0f)((a, b) => Async.succeed(a + b))
        .either
      val failedRun = Stream
        .fromReaderAsync[Nothing, Int](Async.fail(failed))
        .map(_.toDouble)
        .mapAsync(Async.succeed)
        .runFoldAsync(0.0)((a, b) => Async.succeed(a + b))
        .either
      for {
        thrownResult <- run(thrownRun)
        failedResult <- run(failedRun)
      } yield assertTrue(
        thrownResult.left.exists { case error: StreamError => (error ne thrown) && !error.isTrusted; case _ => false },
        failedResult.left.exists { case error: StreamError => (error ne failed) && !error.isTrusted; case _ => false }
      )
    },
    test("closes Float and Double readers when acquisition wins cancellation") {
      val floatReader                                   = ready(1)
      val doubleReader                                  = ready(1)
      val floatAcquire                                  = new CancelOnPoll[Reader[Int]](Async.succeed(floatReader: Reader[Int]))
      val doubleAcquire                                 = new CancelOnPoll[Reader[Int]](Async.succeed(doubleReader: Reader[Int]))
      var floatRunning: Async[Either[Nothing, Float]]   = null
      var doubleRunning: Async[Either[Nothing, Double]] = null
      var floatCleanup: Async[Unit]                     = null
      var doubleCleanup: Async[Unit]                    = null
      val floatCancelled                                = new Completer[Unit]
      val doubleCancelled                               = new Completer[Unit]
      floatRunning = Stream
        .fromReaderAsync[Nothing, Int](floatAcquire)
        .map(_.toFloat)
        .mapAsync(Async.succeed)
        .runFoldAsync(0.0f)((acc, value) => Async.succeed(acc + value))
        .start
      doubleRunning = Stream
        .fromReaderAsync[Nothing, Int](doubleAcquire)
        .map(_.toDouble)
        .mapAsync(Async.succeed)
        .runFoldAsync(0.0)((acc, value) => Async.succeed(acc + value))
        .start
      for {
        _ <- run(floatAcquire.started)
        _  = floatAcquire.arm { () =>
              floatCleanup = Async.cancelWithCleanup(floatRunning.asInstanceOf[Pollable[Any]])
              floatCancelled.succeed(())
            }
        _ <- run(doubleAcquire.started)
        _  = doubleAcquire.arm { () =>
              doubleCleanup = Async.cancelWithCleanup(doubleRunning.asInstanceOf[Pollable[Any]])
              doubleCancelled.succeed(())
            }
        _ <- run(floatCancelled)
        _ <- run(doubleCancelled)
        _ <- run(floatCleanup)
        _ <- run(doubleCleanup)
      } yield assertTrue(floatReader.closes.get == 1, doubleReader.closes.get == 1)
    },
    test("direct dispatch accepts each owned Int shape and declines unsupported shapes") {
      val unprotected = new Stream.GenericAsyncSource[Nothing, Int](
        () => Async.succeed(ready(1, 2): Reader[Int]),
        "unprotected",
        ElementRepresentation.fromJvmType(JvmType.Int),
        protectedAcquisition = false
      )
      val asyncMapped = unprotected
        .mapAsync(value => Async.succeed(value + 1))
        .asInstanceOf[Stream.AsyncMapped[Nothing, Int, Int]]
      val nonSourceMapped = Stream(1, 2)
        .mapAsync(value => Async.succeed(value + 1))
        .asInstanceOf[Stream.AsyncMapped[Nothing, Int, Int]]
      val mappedLongNonSource = Stream(1, 2)
        .map(_.toLong)
        .mapAsync(Async.succeed)
        .asInstanceOf[Stream.AsyncMapped[Nothing, Long, Long]]
      val mappedFloatNonSource = Stream(1, 2)
        .map(_.toFloat)
        .mapAsync(Async.succeed)
        .asInstanceOf[Stream.AsyncMapped[Nothing, Float, Float]]
      val mappedDoubleNonSource = Stream(1, 2)
        .map(_.toDouble)
        .mapAsync(Async.succeed)
        .asInstanceOf[Stream.AsyncMapped[Nothing, Double, Double]]
      val plainFloat = unprotected
        .mapAsync(value => Async.succeed(value.toFloat))
        .asInstanceOf[Stream.AsyncMapped[Nothing, Int, Float]]
      val plainDouble = unprotected
        .mapAsync(value => Async.succeed(value.toDouble))
        .asInstanceOf[Stream.AsyncMapped[Nothing, Int, Double]]
      val reduceLong = (acc: Long, value: Int) => Async.succeed(acc + value)
      for {
        direct   <- run(asyncMapped.runMappedFoldLongDirect(0L, (value: Int) => value + 1, JvmType.Int, reduceLong))
        fallback <- run(nonSourceMapped.runMappedFoldLongDirect(0L, (value: Int) => value + 1, JvmType.Int, reduceLong))
      } yield assertTrue(
        direct == Right(7L),
        fallback == Right(7L),
        asyncMapped.runMappedFoldLongDirect(0L, identity[Int], JvmType.Long, reduceLong) == null,
        asyncMapped.runFoldFloatDirect(0.0f, (a, b) => Async.succeed(a + b.toFloat)) == null,
        asyncMapped.runFoldDoubleDirect(0.0, (a, b) => Async.succeed(a + b.toDouble)) == null,
        plainFloat.runFoldFloatDirect(0.0f, (a, b) => Async.succeed(a + b)) == null,
        plainDouble.runFoldDoubleDirect(0.0, (a, b) => Async.succeed(a + b)) == null,
        mappedFloatNonSource.runFoldFloatDirect(0.0f, (a, b) => Async.succeed(a + b)) == null,
        mappedDoubleNonSource.runFoldDoubleDirect(0.0, (a, b) => Async.succeed(a + b)) == null,
        mappedLongNonSource.runFoldLongDirect(0L, (a, b) => Async.succeed(a + b)) == null
      )
    },
    test("runAsync dispatches mapped Int streams directly and falls back for unsupported sources") {
      val directReader = ready(1, 2, 3)
      val direct       = source[Nothing](directReader).map(_ + 1)
      val fallback     = Stream(1, 2, 3).map(_ + 1)
      for {
        directResult   <- run(direct.runAsync(Sink.foldLeft[Int, Long](0L)(_ + _)))
        fallbackResult <- run(fallback.runAsync(Sink.foldLeft[Int, Long](0L)(_ + _)))
      } yield assertTrue(
        directResult == Right(9L),
        fallbackResult == Right(9L),
        directReader.closes.get == 1
      )
    },
    test("runAsync directly folds filters and flatMaps above a ready effect source") {
      val filteredReader = ready(1, 2, 3, 4)
      val flatMapReader  = ready(1, 2, 3)
      val chainReader    = ready(2, 8, 14, 20, 26)
      val sink           = Sink.foldLeft[Int, Long](0L)(_ + _)
      val filtered       = source[Nothing](filteredReader).mapAsync(Async.succeed).filter(_ % 2 == 0)
      val flatMapped     =
        source[Nothing](flatMapReader).mapAsync(Async.succeed).flatMap(value => Stream(value, value + 10))
      val chained = source[Nothing](chainReader)
        .mapAsync(Async.succeed)
        .filter(_ % 2 == 0)
        .map(_ + 1)
        .filter(_ % 3 == 0)
        .map(_ * 2)
        .filter(_ % 5 == 0)
        .map(_ + 3)
        .filter(_ > 0)
        .map(_ - 1)
        .filter(_ < 100000)
        .map(_ + 7)
      for {
        filteredResult   <- run(filtered.runAsync(sink))
        flatMappedResult <- run(flatMapped.runAsync(sink))
        chainedResult    <- run(chained.runAsync(sink))
      } yield assertTrue(
        filteredResult == Right(6L),
        flatMappedResult == Right(42L),
        chainedResult == Right(39L),
        filteredReader.closes.get == 1,
        flatMapReader.closes.get == 1,
        chainReader.closes.get == 1
      )
    },
    test("runDrainAsync executes every ready map and closes its source") {
      val reader = ready(1, 2, 3)
      val mapped = new AtomicInteger
      for {
        result <- run(source[Nothing](reader).mapAsync { value =>
                    mapped.incrementAndGet()
                    Async.succeed(value + 1)
                  }.runDrainAsync)
      } yield assertTrue(result == Right(()), mapped.get == 3, reader.closes.get == 1)
    },
    test("direct folds preserve slicing above a ready effect source") {
      val takeReader       = ready(1, 2, 3, 4)
      val takeDropReader   = ready(1, 2, 3, 4)
      val takeWhileReader  = ready(1, 2, 3, 4)
      val takeEffects      = new AtomicInteger
      val takeDropEffects  = new AtomicInteger
      val takeWhileEffects = new AtomicInteger
      val closeFailure     = new RuntimeException("takeWhile-close")
      val closeReader      =
        new IntReader(Vector(() => Async.succeed(1L), () => Async.succeed(2L)), () => Async.fail(closeFailure))
      val sink        = Sink.foldLeft[Int, Long](0L)(_ + _)
      val closeStream = source[Nothing](closeReader)
        .mapAsync(value => Async.succeed(value))
        .takeWhile(_ < 2)
        .asInstanceOf[Stream.TakenWhile[Nothing, Int]]
      val closeDirect = closeStream.runFoldLongDirect(0L, (acc, value) => Async.succeed(acc + value))
      for {
        take <- run(source[Nothing](takeReader).mapAsync { value =>
                  takeEffects.incrementAndGet(); Async.succeed(value)
                }.take(2).runAsync(sink))
        takeDrop <- run(source[Nothing](takeDropReader).mapAsync { value =>
                      takeDropEffects.incrementAndGet(); Async.succeed(value)
                    }.drop(1).take(2).runAsync(sink))
        takeWhile <- run(source[Nothing](takeWhileReader).mapAsync { value =>
                       takeWhileEffects.incrementAndGet(); Async.succeed(value)
                     }.takeWhile(_ < 3).runAsync(sink))
        closeResult <- run(closeDirect.either)
      } yield assertTrue(
        take == Right(3L),
        takeDrop == Right(5L),
        takeWhile == Right(3L),
        takeEffects.get == 2,
        takeDropEffects.get == 3,
        takeWhileEffects.get == 3,
        closeDirect != null,
        closeResult == Left(closeFailure),
        takeReader.closes.get == 1,
        takeDropReader.closes.get == 1,
        takeWhileReader.closes.get == 1,
        closeReader.closes.get == 1
      )
    },
    test("mapPar route renders, materializes, and selects direct acquisition ownership") {
      val unprotectedSource = new Stream.GenericAsyncSource[Nothing, Int](
        () => Async.succeed(ready(1, 2): Reader[Int]),
        "unprotected-mapPar",
        ElementRepresentation.fromJvmType(JvmType.Int),
        protectedAcquisition = false
      )
      val unprotected = unprotectedSource
        .mapParAsync(2)(value => Async.succeed(value + 1))
        .asInstanceOf[Stream.AsyncMapPar[Nothing, Int, Int]]
      val protectedSource = Stream.fromReaderAsync[Nothing, Int](Async.succeed(ready(3): Reader[Int]))
      val protectedMap    = protectedSource
        .mapParAsync(2)(value => Async.succeed(value + 1))
        .asInstanceOf[Stream.AsyncMapPar[Nothing, Int, Int]]
      val unsupported = unprotectedSource
        .mapParAsync(2)(value => Async.succeed(value.toLong))
        .asInstanceOf[Stream.AsyncMapPar[Nothing, Int, Long]]
      val asyncPipeline = new AsyncInterpreter(Reader.closed.toAsync.asInstanceOf[Reader.AsyncReader[Any]])
      val materialized  = unprotected.materializeAsync(asyncPipeline)
      val detached      = AsyncInterpreter.fromStream(unprotected)
      val syncPipeline  = SyncInterpreter.fromStream(Stream.empty)
      val rejected      =
        try { unprotected.compileInterpreter(syncPipeline); false }
        catch { case Stream.AsyncBoundaryRequired => true }
      for {
        unprotectedResult <- run(unprotected.runFoldLongDirect(0L, (acc, value) => Async.succeed(acc + value)))
        protectedResult   <- run(protectedMap.runFoldLongDirect(0L, (acc, value) => Async.succeed(acc + value)))
      } yield assertTrue(
        unprotected.render == "unprotected-mapPar.mapParAsync(2)(...)",
        materialized == null,
        detached.readInt(-1L).block == 2L,
        rejected,
        unprotectedResult == Right(5L),
        protectedResult == Right(4L),
        unsupported.runFoldLongDirect(0L, (acc, value) => Async.succeed(acc + value)) == null
      )
    },
    test("protected direct routes classify acquisition and mapPar setup defects") {
      val acquisition                        = new RuntimeException("protected-acquisition")
      def failedSource: Stream[Nothing, Int] =
        Stream.fromReaderAsync[Nothing, Int](throw acquisition)
      val routes: List[Async[Any]] = List(
        failedSource.runCollectAsync,
        failedSource.filterAsync(_ => Async.succeed(true)).take(1).runCollectAsync,
        failedSource.runFoldAsync(0L)((acc, value) => Async.succeed(acc + value)),
        failedSource.map(_.toLong).runFoldAsync(0L)((acc, value) => Async.succeed(acc + value)),
        failedSource.map(_.toLong).mapAsync(Async.succeed).runFoldAsync(0L)((acc, value) => Async.succeed(acc + value)),
        failedSource.runAsync(Sink.foldLeft[Int, Long](0L)(_ + _).mapAsync(Async.succeed)),
        failedSource.mapAsync(Async.succeed).map(identity).runFoldAsync(0L)((acc, value) => Async.succeed(acc + value)),
        failedSource.mapParAsync(1)(Async.succeed).runFoldAsync(0L)((acc, value) => Async.succeed(acc + value)),
        failedSource.take(1).runFoldAsync(0L)((acc, value) => Async.succeed(acc + value)),
        failedSource.drop(1).take(1).runFoldAsync(0L)((acc, value) => Async.succeed(acc + value)),
        failedSource.takeWhile(_ => true).runFoldAsync(0L)((acc, value) => Async.succeed(acc + value))
      )
      val setupTrusted                                                               = StreamError.source("mapPar-setup")
      val setupOrdinary                                                              = new RuntimeException("mapPar-setup")
      def mapParFailure(failure: Throwable): Either[Throwable, Either[String, Long]] = {
        val reader = new IntReader(Vector.empty) {
          override def jvmType: JvmType = throw failure
        }
        source[String](reader)
          .mapParAsync(1)(value => Async.succeed(value))
          .runFoldAsync(0L)((acc, value) => Async.succeed(acc + value))
          .either
          .block
      }
      val failures = routes.map(effect => effect.either.block.left.toOption)
      val trusted  = mapParFailure(setupTrusted)
      val ordinary = mapParFailure(setupOrdinary)
      assertTrue(
        failures.forall(_.contains(acquisition)),
        trusted == Right(Left("mapPar-setup")),
        ordinary == Left(setupOrdinary)
      )
    },
    test("unprotected direct take drop and concatenation routes preserve results") {
      def unprotected(values: Int*): Stream[Nothing, Int] =
        new Stream.GenericAsyncSource[Nothing, Int](
          () => Async.succeed(ready(values: _*): Reader[Int]),
          "unprotected",
          ElementRepresentation.fromJvmType(JvmType.Int),
          protectedAcquisition = false
        )
      val fold         = (acc: Long, value: Int) => Async.succeed(acc + value)
      val taken        = unprotected(1, 2, 3).take(2).runFoldAsync(0L)(fold)
      val takeDrop     = unprotected(1, 2, 3).drop(1).take(1).runFoldAsync(0L)(fold)
      val whileTaken   = unprotected(1, 2, 3).takeWhile(_ < 3).runFoldAsync(0L)(fold)
      val concatenated = unprotected(1, 2).concat(unprotected(3, 4)).runFoldAsync(0L)(fold)
      val mappedConcat = unprotected(1)
        .mapAsync(Async.succeed)
        .concat(unprotected(2).mapAsync(Async.succeed))
        .concat(unprotected(3).mapAsync(Async.succeed))
        .runFoldAsync(0L)(fold)
      var acquired  = false
      val emptyFold = new Stream.GenericAsyncSource[Nothing, Int](
        () => { acquired = true; Async.succeed(ready(1): Reader[Int]) },
        "unprotected-empty",
        ElementRepresentation.fromJvmType(JvmType.Int),
        protectedAcquisition = false
      ).take(0).runFoldAsync("")((acc, value) => Async.succeed(acc + value))
      assertTrue(
        taken.block == Right(3L),
        takeDrop.block == Right(2L),
        whileTaken.block == Right(3L),
        concatenated.block == Right(10L),
        mappedConcat.block == Right(6L),
        emptyFold.block == Right(""),
        acquired
      )
    },
    test("optimized direct routes retain source failure when close also fails") {
      def failedStream(label: String): (Stream[Nothing, Int], StreamError, Throwable) = {
        val sourceFailure = StreamError.source(label)
        val closeFailure  = new RuntimeException(s"$label-close")
        val reader        = new IntReader(Vector(() => Async.failTrusted(sourceFailure)), () => Async.fail(closeFailure))
        val stream        = new Stream.GenericAsyncSource[Nothing, Int](
          () => Async.succeed(reader: Reader[Int]),
          label,
          ElementRepresentation.fromJvmType(JvmType.Int),
          protectedAcquisition = false
        )
        (stream, sourceFailure, closeFailure)
      }
      def preserves(
        route: Stream[Nothing, Int] => Async[Any],
        label: String
      ): Boolean = {
        val (stream, sourceFailure, closeFailure) = failedStream(label)
        route(stream).either.block.left.exists(error =>
          (error eq sourceFailure) && sourceFailure.cleanupFailed && sourceFailure.getSuppressed.toList == List(
            closeFailure
          )
        )
      }
      assertTrue(
        preserves(_.map(_ + 1).runFoldAsync(0)((acc, value) => Async.succeed(acc + value)), "mapped"),
        preserves(_.take(1).runFoldAsync(0L)((acc, value) => Async.succeed(acc + value)), "take"),
        preserves(_.drop(1).take(1).runFoldAsync(0L)((acc, value) => Async.succeed(acc + value)), "take-drop"),
        preserves(_.takeWhile(_ => true).runFoldAsync(0L)((acc, value) => Async.succeed(acc + value)), "take-while")
      )
    },
    test("async unary representations retain their exact primitive lanes") {
      val distinct    = Stream(1, 1).distinctByAsync(value => Async.succeed(value))
      val accumulated = Stream(1, 2).mapAccumAsync(0L)((state, value) => Async.succeed((state + value, state + value)))
      val ensuring    = Stream(1).ensuringAsync(Async.succeed(()))
      val deepReader  = distinct.compile(Stream.DepthCutoff, Stream.DefaultBufferSize)
      val unwrapped   = Stream.unwrap(Async.succeed(Stream(1)))
      val rejected    =
        try { unwrapped.compileInterpreter(SyncInterpreter.fromStream(Stream.empty)); false }
        catch { case Stream.AsyncBoundaryRequired => true }
      for {
        deepValue         <- run(deepReader.asInstanceOf[Reader.AsyncReader[Int]].readInt(-1L))
        distinctValues    <- run(distinct.runCollectAsync)
        accumulatedValues <- run(accumulated.runCollectAsync)
      } yield assertTrue(
        distinct.elementRepresentation.stableJvmType.contains(JvmType.Int),
        accumulated.elementRepresentation.stableJvmType.contains(JvmType.Long),
        ensuring.elementRepresentation.stableJvmType.contains(JvmType.Int),
        deepValue == 1L,
        rejected,
        distinctValues == Right(zio.blocks.chunk.Chunk(1)),
        accumulatedValues == Right(zio.blocks.chunk.Chunk(1L, 3L))
      )
    },
    test("direct acquisition transforms specialized async, generic async, sync, and compiled sources") {
      val asyncInt = source[Nothing](ready(1))
        .mapAsync(value => Async.succeed(value + 1))
        .asInstanceOf[Stream.AsyncMapped[Nothing, Int, Int]]
      val asyncLong = source[Nothing](ready(2))
        .mapAsync(value => Async.succeed(value.toLong + 1L))
        .asInstanceOf[Stream.AsyncMapped[Nothing, Int, Long]]
      val syncSource = new Stream.GenericAsyncSource[Nothing, Int](
        () => Async.succeed(Reader.fromRange(3 until 4): Reader[Int]),
        "sync",
        ElementRepresentation.fromJvmType(JvmType.Int),
        protectedAcquisition = false
      )
      val syncInt = syncSource
        .mapAsync(value => Async.succeed(value + 1))
        .asInstanceOf[Stream.AsyncMapped[Nothing, Int, Int]]
      val compiled = Stream(4)
        .mapAsync(value => Async.succeed(value + 1))
        .asInstanceOf[Stream.AsyncMapped[Nothing, Int, Int]]
      for {
        asyncIntReader  <- run(asyncInt.acquireDirect())
        asyncLongReader <- run(asyncLong.acquireDirect())
        syncIntReader   <- run(syncInt.acquireDirect())
        compiledReader  <- run(compiled.acquireDirect())
        intValue        <- run(asyncIntReader.asInstanceOf[Reader.AsyncReader[Int]].readIntPhysical(Long.MinValue))
        longValue       <- run(asyncLongReader.asInstanceOf[Reader.AsyncReader[Long]].readLongPhysical(Long.MinValue))
        syncValue       <- run(syncIntReader.asInstanceOf[Reader.AsyncReader[Int]].readIntPhysical(Long.MinValue))
        compiledValue   <- run(compiledReader.asInstanceOf[Reader.AsyncReader[Int]].readIntPhysical(Long.MinValue))
        _               <- run(asyncIntReader.asInstanceOf[Reader.AsyncReader[Int]].close())
        _               <- run(asyncLongReader.asInstanceOf[Reader.AsyncReader[Long]].close())
        _               <- run(syncIntReader.asInstanceOf[Reader.AsyncReader[Int]].close())
        _               <- run(compiledReader.asInstanceOf[Reader.AsyncReader[Int]].close())
      } yield assertTrue(intValue == 2L, longValue == 3L, syncValue == 4L, compiledValue == 5L)
    },
    test("direct acquisition preserves trusted and ordinary compilation failures") {
      val ordinary       = new RuntimeException("compile-ordinary")
      val trusted        = StreamError.source("compile-trusted")
      val ordinaryMapped = new FailingCompile(ordinary)
        .mapAsync(Async.succeed)
        .asInstanceOf[Stream.AsyncMapped[Nothing, Int, Int]]
      val trustedMapped = new FailingCompile(trusted)
        .mapAsync(Async.succeed)
        .asInstanceOf[Stream.AsyncMapped[Nothing, Int, Int]]
      for {
        ordinaryResult <- run(ordinaryMapped.acquireDirect().either)
        trustedResult  <- run(trustedMapped.acquireDirect().either)
      } yield assertTrue(
        ordinaryResult == Left(ordinary),
        trustedResult == Left(trusted),
        trustedResult.left.exists(_.asInstanceOf[StreamError].isTrusted)
      )
    },
    test("direct acquisition releases late asynchronous and synchronous readers") {
      val asyncReader                                = ready(1)
      val syncFailure                                = new RuntimeException("late-sync-close")
      val syncReader                                 = new ClosingSyncReader(syncFailure)
      val asyncAcquire                               = new CancelOnPoll[Reader[Int]](Async.succeed(asyncReader: Reader[Int]))
      val syncAcquire                                = new CancelOnPoll[Reader[Int]](Async.succeed(syncReader: Reader[Int]))
      def mapped(acquire: CancelOnPoll[Reader[Int]]) =
        new Stream.GenericAsyncSource[Nothing, Int](
          () => acquire,
          "late-reader",
          ElementRepresentation.fromJvmType(JvmType.Int),
          protectedAcquisition = false
        ).mapAsync(value => Async.succeed(value + 1)).asInstanceOf[Stream.AsyncMapped[Nothing, Int, Int]]
      val asyncRunning              = mapped(asyncAcquire).acquireDirect().start
      val syncRunning               = mapped(syncAcquire).acquireDirect().start
      var asyncCleanup: Async[Unit] = null
      var syncCleanup: Async[Unit]  = null
      val asyncCancelled            = new Completer[Unit]
      val syncCancelled             = new Completer[Unit]
      for {
        _ <- run(asyncAcquire.started)
        _  = asyncAcquire.arm { () =>
              asyncCleanup = Async.cancelWithCleanup(asyncRunning.asInstanceOf[Pollable[Any]])
              asyncCancelled.succeed(())
            }
        _ <- run(syncAcquire.started)
        _  = syncAcquire.arm { () =>
              syncCleanup = Async.cancelWithCleanup(syncRunning.asInstanceOf[Pollable[Any]])
              syncCancelled.succeed(())
            }
        _           <- run(asyncCancelled)
        _           <- run(syncCancelled)
        asyncResult <- run(asyncCleanup.either)
        syncResult  <- run(syncCleanup.either)
      } yield assertTrue(
        asyncResult == Right(()),
        syncResult == Left(syncFailure),
        asyncReader.closes.get == 1,
        syncReader.closes.get == 1
      )
    }
  )
}

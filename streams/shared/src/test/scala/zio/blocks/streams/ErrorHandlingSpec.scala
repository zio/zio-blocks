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

// Cross-platform semantic coverage.

// JVM-only because these assertions exercise the blocking terminal family.

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.combinators.Concat
import zio.blocks.scope.Resource
import zio.blocks.streams.internal.{StreamError, SyncInterpreter}
import zio.blocks.streams.io.Reader
import zio.test._
import zio.test.Assertion._

/**
 * Tests for error handling, resource management, new constructors, and
 * combinators.
 */
object ErrorHandlingSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("ErrorHandling")(
    // ---- Suite 1: Error construction ----

    suite("Error construction")(
      test("Stream.fail(e).runCollect returns Left(e)") {
        runAsync(Stream.fail("boom").runCollectAsync).map(result => assert(result)(equalTo(Left("boom"))))
      },
      test("Stream.fail(e).runDrain returns Left(e)") {
        runAsync(Stream.fail(42).runDrainAsync).map(result => assert(result)(equalTo(Left(42))))
      },
      test("Stream.die(t).runCollect throws t") {
        val t = new RuntimeException("die")
        runAsync(Stream.die(t).runCollectAsync).either.map {
          case Left(e: RuntimeException) => assert(e.getMessage)(equalTo("die"))
          case _                         => assert("no exception")(equalTo("die"))
        }
      },
      test("Stream.attempt { 42 }.runCollect returns Right(Chunk(42))") {
        runAsync(Stream.attempt(42).runCollectAsync).map(result => assert(result)(equalTo(Right(Chunk(42)))))
      },
      test("Stream.attempt { throw RuntimeException }.runCollect returns Left") {
        runAsync(Stream.attempt[Nothing](throw new RuntimeException("boom")).runCollectAsync).map { result =>
          assert(result.isLeft)(isTrue) &&
          assert(result.left.exists(_.isInstanceOf[RuntimeException]))(isTrue)
        }
      },
      test("Stream.suspend produces correct elements") {
        runAsync(Stream.suspend(Stream.range(0, 3)).runCollectAsync).map(result =>
          assert(result)(equalTo(Right(Chunk(0, 1, 2))))
        )
      },
      test("Stream.eval executes side-effect and emits nothing") {
        var executed = false
        runAsync(Stream.eval { executed = true }.runCollectAsync).map { result =>
          assertTrue(executed) && assert(result)(equalTo(Right(Chunk.empty)))
        }
      },
      test("Stream.eval defect propagates as defect") {
        runAsync(Stream.eval(throw new RuntimeException("boom")).runCollectAsync).either.map { result =>
          assertTrue(result.isLeft) &&
          assertTrue(result.left.exists(_.isInstanceOf[RuntimeException]))
        }
      },
      test("Stream.attemptEval captures throwable as typed error") {
        runAsync(Stream.attemptEval(throw new RuntimeException("oops")).runCollectAsync).map { result =>
          assertTrue(result.isLeft) &&
          assertTrue(result.left.exists(_.getMessage == "oops"))
        }
      },
      test("Stream.attemptEval succeeds with no elements on success") {
        var executed = false
        runAsync(Stream.attemptEval { executed = true }.runCollectAsync).map { result =>
          assertTrue(executed) && assert(result)(equalTo(Right(Chunk.empty)))
        }
      },
      test("Stream.defer registers release action on close") {
        var released = false
        runAsync(Stream.defer { released = true }.runCollectAsync).map { result =>
          assert(result)(equalTo(Right(Chunk.empty))) && assertTrue(released)
        }
      },
      test("Stream.defer release runs after preceding elements") {
        var released = false
        runAsync((Stream.range(0, 3) ++ Stream.defer { released = true }).runCollectAsync).map { result =>
          assert(result)(equalTo(Right(Chunk(0, 1, 2)))) && assertTrue(released)
        }
      },
      test("nested defers run in LIFO order") {
        val order = scala.collection.mutable.ListBuffer.empty[Int]
        runAsync(
          (
            Stream.defer(order += 1) ++
              Stream.range(0, 2) ++
              Stream.defer(order += 2)
          ).runCollectAsync
        ).map { result =>
          assert(result)(equalTo(Right(Chunk(0, 1)))) &&
          assert(order.toList)(equalTo(List(1, 2)))
        }
      },
      test("concat of defers have distinct lifetimes") {
        val order  = scala.collection.mutable.ListBuffer.empty[String]
        val stream =
          Stream.defer(order += "a-release") ++
            Stream.succeed(1) ++
            Stream.defer(order += "b-release") ++
            Stream.succeed(2)
        runAsync(stream.runCollectAsync).map { result =>
          assert(result)(equalTo(Right(Chunk(1, 2)))) &&
          assert(order.toList)(equalTo(List("a-release", "b-release")))
        }
      }
    ),

    // ---- Suite 2: catchAll ----

    suite("catchAll")(
      test("catches error and switches to fallback stream") {
        runAsync(Stream.fail("e").catchAll(_ => Stream.succeed(1)).runCollectAsync).map(result =>
          assert(result)(equalTo(Right(Chunk(1))))
        )
      },
      test("catches error after some elements") {
        runAsync((Stream.range(0, 5) ++ Stream.fail("e")).catchAll(_ => Stream.succeed(99)).runCollectAsync).map(
          result => assert(result)(equalTo(Right(Chunk(0, 1, 2, 3, 4, 99))))
        )
      },
      test("fallback can also fail") {
        runAsync(Stream.fail("e").catchAll[String, Nothing, Nothing]((_: String) => Stream.fail("e2")).runCollectAsync)
          .map(result => assert(result)(equalTo(Left("e2"))))
      },
      test("does not catch when no error") {
        runAsync(Stream.succeed(1).catchAll((_: Nothing) => Stream.succeed(2)).runCollectAsync).map(result =>
          assert(result)(equalTo(Right(Chunk(1))))
        )
      },
      test("passes the error value to the handler") {
        runAsync(Stream.fail("hello").catchAll((e: String) => Stream.succeed(e.length)).runCollectAsync).map(result =>
          assert(result)(equalTo(Right(Chunk(5))))
        )
      }
    ),

    // ---- Suite 3: catchDefect ----

    suite("catchDefect")(
      test("catches RuntimeException from die") {
        runAsync(
          Stream
            .die(new RuntimeException("boom"))
            .catchDefect { case _: RuntimeException => Stream.succeed(42) }
            .runCollectAsync
        ).map(result => assert(result)(equalTo(Right(Chunk(42)))))
      },
      test("does NOT catch typed StreamError") {
        runAsync(
          Stream.fail("typed").catchDefect[Nothing, String, Nothing, Nothing] { case _ => Stream.empty }.runCollectAsync
        ).map(result => assert(result)(equalTo(Left("typed"))))
      },
      test("non-matching defect is rethrown") {
        // Use an Exception subclass that doesn't match the PartialFunction
        val t = new java.io.IOException("io error")
        runAsync(
          Stream.die(t).catchDefect { case _: RuntimeException => Stream.succeed(42) }.runCollectAsync
        ).either.map {
          case Left(e: java.io.IOException) => assert(e.getMessage)(equalTo("io error"))
          case _                            => assert("no exception")(equalTo("io error"))
        }
      },
      test("close failure prevents defect recovery construction") {
        val closeFailure        = new RuntimeException("close")
        val defect              = new RuntimeException("defect")
        var closeAttempts       = 0
        var recoveryConstructed = false
        val reader              = Stream
          .fromReader[Nothing, Int](new Reader.SyncReader[Int] {
            def isClosed: Boolean                 = false
            def read[A1 >: Int](sentinel: A1): A1 = throw defect
            def close(): Unit                     = {
              closeAttempts += 1
              throw closeFailure
            }
          })
          .catchDefect { case _ =>
            recoveryConstructed = true
            Stream.succeed(42)
          }
          .compile(0)
          .expectedAsync

        for {
          first  <- runAsync(reader.read[Any](null)).either
          second <- runAsync(reader.close()).either
        } yield assertTrue(
          first.left.exists(_ eq defect),
          first.left.exists(_.getSuppressed.toList == List(closeFailure)),
          second.left.exists(_ eq defect),
          closeAttempts == 1,
          !recoveryConstructed
        )
      }
    ),

    // ---- Suite 4: orElse / || ----

    suite("orElse / ||")(
      test("fail || succeed produces the succeed") {
        runAsync((Stream.fail("e") || Stream.succeed(1)).runCollectAsync).map(result =>
          assert(result)(equalTo(Right(Chunk(1))))
        )
      },
      test("succeed || succeed produces the first") {
        runAsync((Stream.succeed(1) || Stream.succeed(2)).runCollectAsync).map(result =>
          assert(result)(equalTo(Right(Chunk(1))))
        )
      },
      test("orElse is the same as ||") {
        runAsync(Stream.fail("e").orElse(Stream.succeed(99)).runCollectAsync).map(result =>
          assert(result)(equalTo(Right(Chunk(99))))
        )
      }
    ),

    // ---- Suite 5: Error + combinators ----

    suite("Error + combinators")(
      test("StreamError thrown by map callback is a defect") {
        val error = new StreamError("e")
        runAsync(
          Stream
            .range(0, 3)
            .map { (_: Int) => throw error; 0 }
            .catchAll((_: String) => Stream.succeed(99))
            .runCollectAsync
        ).either.map {
          case Left(thrown) => assertTrue(thrown.isInstanceOf[StreamError], thrown ne error)
          case Right(_)     => assertTrue(false)
        }
      },
      test("error in flatMap inner stream, caught by catchAll") {
        runAsync(
          Stream
            .range(0, 10)
            .flatMap((i: Int) => if (i == 5) Stream.fail("e") else Stream.succeed(i))
            .catchAll((_: String) => Stream.empty)
            .runCollectAsync
        ).map(result => assert(result)(equalTo(Right(Chunk(0, 1, 2, 3, 4)))))
      },
      test("error in concat, caught by catchAll") {
        runAsync(
          (Stream.range(0, 3) ++ Stream.fail("e"))
            .catchAll((_: String) => Stream.range(10, 13))
            .runCollectAsync
        ).map(result => assert(result)(equalTo(Right(Chunk(0, 1, 2, 10, 11, 12)))))
      }
    ),

    // ---- Suite 6: Resource cleanup ----

    suite("Resource cleanup")(
      test("catchAll closes upstream reader on switch") {
        var closed   = false
        val upstream = Stream.fromReader[String, Int](new Reader.SyncReader[Int] {
          private var done                      = false
          def isClosed: Boolean                 = done
          def read[A1 >: Int](sentinel: A1): A1 = { done = true; throw StreamError.source("err") }
          def close(): Unit                     = closed = true
        })
        runAsync(upstream.catchAll((_: String) => Stream.succeed(42)).runCollectAsync).map { result =>
          assert(result)(equalTo(Right(Chunk(42)))) &&
          assert(closed)(isTrue)
        }
      },
      test("catchAll preserves upstream close failure before constructing recovery") {
        val closeFailure        = new RuntimeException("close")
        val trigger             = StreamError.source("err")
        var closeAttempts       = 0
        var recoveryConstructed = false
        val reader              = Stream
          .fromReader[String, Int](new Reader.SyncReader[Int] {
            def isClosed: Boolean                 = false
            def read[A1 >: Int](sentinel: A1): A1 = throw trigger
            def close(): Unit                     = {
              closeAttempts += 1
              throw closeFailure
            }
          })
          .catchAll { _ =>
            recoveryConstructed = true
            Stream.succeed(42)
          }
          .compile(0)
          .expectedAsync

        for {
          first  <- runAsync(reader.read[Any](null)).either
          second <- runAsync(reader.close()).either
        } yield assertTrue(
          first.left.exists(_ eq trigger),
          first.left.exists(_.getSuppressed.toList == List(closeFailure)),
          second.left.exists(_ eq trigger),
          closeAttempts == 1,
          !recoveryConstructed
        )
      },
      test("recovery remains responsible for cleanup when upstream close replays the trigger") {
        def replaying(failure: StreamError): Reader.SyncReader[Int] = new Reader.SyncReader[Int] {
          def isClosed: Boolean                 = false
          def read[A1 >: Int](sentinel: A1): A1 = throw failure
          def close(): Unit                     = throw failure
        }

        val typedTrigger  = StreamError.source("typed")
        val defectTrigger = new StreamError("defect")
        var typedCloses   = 0
        var defectCloses  = 0

        val typed = Stream
          .fromReader[String, Int](replaying(typedTrigger))
          .catchAll(_ => Stream.succeed(1).ensuring(typedCloses += 1))
          .runCollectAsync
        val defect = Stream
          .fromReader[Nothing, Int](replaying(defectTrigger))
          .catchDefect { case `defectTrigger` => Stream.succeed(1).ensuring(defectCloses += 1) }
          .runCollectAsync

        for {
          typedResult  <- runAsync(typed)
          defectResult <- runAsync(defect)
        } yield assertTrue(
          typedResult == Right(Chunk(1)),
          defectResult == Right(Chunk(1)),
          typedCloses == 1,
          defectCloses == 1
        )
      },
      test("recovery construction failures are sticky across pull and close") {
        def replay(reader: Reader.AsyncReader[Int], failure: Throwable) =
          for {
            first <- runAsync(reader.read[Any](null)).either
            next  <- runAsync(reader.read[Any](null)).either
            close <- runAsync(reader.close()).either
          } yield first.left.exists(_ eq failure) &&
            next.left.exists(_ eq failure) &&
            close.left.exists(_ eq failure)

        val typedTrigger = StreamError.source("typed")
        val typedFailure = new RuntimeException("typed recovery")
        val typed        =
          Stream
            .fromReader[String, Int](new Reader.SyncReader[Int] {
              def isClosed: Boolean                 = false
              def read[A1 >: Int](sentinel: A1): A1 = throw typedTrigger
              def close(): Unit                     = ()
            })
            .catchAll(_ => throw typedFailure)
            .compile(0)
            .expectedAsync

        val defectTrigger = new RuntimeException("defect")
        val defectFailure = new RuntimeException("defect recovery")
        val defect        =
          Stream
            .fromReader[Nothing, Int](new Reader.SyncReader[Int] {
              def isClosed: Boolean                 = false
              def read[A1 >: Int](sentinel: A1): A1 = throw defectTrigger
              def close(): Unit                     = ()
            })
            .catchDefect { case `defectTrigger` => throw defectFailure }
            .compile(0)
            .expectedAsync

        for {
          typedReplayed  <- replay(typed, typedFailure)
          defectReplayed <- replay(defect, defectFailure)
        } yield assertTrue(typedReplayed, defectReplayed)
      },
      test("concat tail materialization failures are sticky across pull and close") {
        def replay(tail: () => Reader.SyncReader[Int], expected: Class[_]): Boolean = {
          val reader = Reader.fromChunk(Chunk.empty[Int]).concat(tail)
          val first  = scala.util.Try(reader.read[Any](null)).failed.toOption.orNull
          val next   = scala.util.Try(reader.read[Any](null)).failed.toOption.orNull
          val close  = scala.util.Try(reader.close()).failed.toOption.orNull
          expected.isInstance(first) && (next eq first) && (close eq first)
        }

        val failure = new RuntimeException("tail")
        assertTrue(
          replay(() => throw failure, classOf[RuntimeException]),
          replay(() => null, classOf[NullPointerException])
        )
      },
      test("concat reset clears a transient tail materialization failure") {
        val failure  = new RuntimeException("tail")
        var attempts = 0
        def reader() = Reader.fromChunk(Chunk.empty[Int]).concat { () =>
          attempts += 1
          if (attempts == 1) throw failure else Reader.singleInt(1)
        }

        val direct      = reader()
        val directFirst = scala.util.Try(direct.read[Any](null)).failed.toOption.orNull
        direct.reset()
        val directValue = direct.read[Any](null)

        attempts = 0
        val interpreted      = SyncInterpreter(reader())
        val interpretedFirst = scala.util.Try(interpreted.read[Any](null)).failed.toOption.orNull
        interpreted.reset()
        val interpretedValue = interpreted.read[Any](null)

        assertTrue(
          directFirst eq failure,
          directValue == 1,
          interpretedFirst eq failure,
          interpretedValue == 1
        )
      },
      test("concat transition failure is closed and sticky on zero-work pulls") {
        val failure = new RuntimeException("head close")
        val head    = new Reader.SyncReader[Int] {
          def isClosed: Boolean              = false
          def read[A >: Int](sentinel: A): A = sentinel
          def close(): Unit                  = throw failure
        }
        val reader = head.concat(() => Reader.singleInt(1))

        val first = scala.util.Try(reader.read[Any](null)).failed.toOption.orNull
        val bulk  = scala.util.Try(reader.readInts(new Array[Int](0), 0, 0)).failed.toOption.orNull
        val exact = scala.util.Try(reader.readN[Any](0)).failed.toOption.orNull
        val upTo  = scala.util.Try(reader.readUpToN[Any](0)).failed.toOption.orNull
        val skip  = scala.util.Try(reader.skip(0)).failed.toOption.orNull

        assertTrue(
          first eq failure,
          bulk eq failure,
          exact eq failure,
          upTo eq failure,
          skip eq failure,
          reader.isClosed,
          !reader.readable()
        )
      },
      test("run keeps typed read failure primary when close fails") {
        val trigger      = StreamError.source("typed")
        val closeFailure = new RuntimeException("close")
        var closes       = 0
        val stream       = Stream.fromReader[String, Int](new Reader.SyncReader[Int] {
          def isClosed: Boolean                 = false
          def read[A1 >: Int](sentinel: A1): A1 = throw trigger
          def close(): Unit                     = { closes += 1; throw closeFailure }
        })

        runAsync(stream.runDrainAsync).either.map { result =>
          assertTrue(
            result.left.exists(_ eq trigger),
            result.left.exists(_.getSuppressed.toList == List(closeFailure)),
            closes == 1
          )
        }
      },
      test("run keeps defect primary when close fails") {
        val trigger      = new RuntimeException("defect")
        val closeFailure = new RuntimeException("close")
        var closes       = 0
        val stream       = Stream.fromReader[Nothing, Int](new Reader.SyncReader[Int] {
          def isClosed: Boolean                 = false
          def read[A1 >: Int](sentinel: A1): A1 = throw trigger
          def close(): Unit                     = { closes += 1; throw closeFailure }
        })

        runAsync(stream.runDrainAsync).either.map { result =>
          assertTrue(
            result.left.exists(_ eq trigger),
            result.left.exists(_.getSuppressed.toList == List(closeFailure)),
            closes == 1
          )
        }
      },
      test("pipeline sink keeps drain failure primary when close fails") {
        val trigger       = new RuntimeException("sink")
        val closeFailure  = new RuntimeException("close")
        var closes        = 0
        val closingReader = new Reader.SyncReader[Int] {
          def isClosed: Boolean                 = false
          def read[A1 >: Int](sentinel: A1): A1 = sentinel
          def close(): Unit                     = { closes += 1; throw closeFailure }
        }
        val sink = new Sink[Nothing, Int, Unit] {
          private[streams] def drain(reader: Reader.SyncReader[_]): Unit         = throw trigger
          private[streams] def drain(reader: Reader.AsyncReader[_]): Async[Unit] = Async.fail(trigger)
        }
        val pipeline = new Pipeline[Int, Int] {
          def applyToStream[E](stream: Stream[E, Int]): Stream[E, Int]                                    = Stream.fromReader[E, Int](closingReader)
          def applyToSink[E, Z](sink: Sink[E, Int, Z]): Sink[E, Int, Z]                                   = Pipeline.runViaSink(this, sink)
          private[streams] def applyToAsyncReader(reader: Reader.AsyncReader[_]): Reader.AsyncReader[Int] =
            closingReader.toAsync
        }
        val adapted = pipeline.andThenSink(sink)

        runAsync(adapted.drain(Reader.fromChunk(Chunk.empty[Int]).toAsync)).either.map { result =>
          assertTrue(
            result.left.exists(_ eq trigger),
            result.left.exists(_.getSuppressed.toList == List(closeFailure)),
            closes == 1
          )
        }
      },
      test("nested interpreter replay of the same typed error remains typed") {
        val inner = SyncInterpreter.fromStream(Stream.fail("typed"))
        val outer = SyncInterpreter(inner)
        runAsync(Stream.fromReader[String, Any](outer).runDrainAsync).map(result => assertTrue(result == Left("typed")))
      },
      test("owned-reader wrappers preserve a first typed close replay") {
        def replaying(error: StreamError) = new Reader.SyncReader[Int] {
          def isClosed: Boolean                 = false
          def read[A1 >: Int](sentinel: A1): A1 = throw error
          def close(): Unit                     = throw error
        }

        val releaseError  = StreamError.source("release-wrapper")
        val releaseResult = Stream.fromReader[String, Int](replaying(releaseError).withRelease(() => ())).runDrainAsync

        val concatError  = StreamError.source("concat-wrapper")
        val concatResult = Stream
          .fromReader[String, Int](replaying(concatError).concat(() => Reader.singleInt(1)))
          .runDrainAsync

        val flatMapError  = StreamError.source("flatmap-wrapper")
        val flatMapResult = Stream
          .succeed(1)
          .flatMap(_ => Stream.fromReader[String, Int](replaying(flatMapError)))
          .runDrainAsync

        for {
          release <- runAsync(releaseResult)
          concat  <- runAsync(concatResult)
          flatMap <- runAsync(flatMapResult)
        } yield assertTrue(
          release == Left("release-wrapper"),
          concat == Left("concat-wrapper"),
          flatMap == Left("flatmap-wrapper"),
          !releaseError.cleanupFailed,
          !concatError.cleanupFailed,
          !flatMapError.cleanupFailed
        )
      },
      test("mapped primary inherits cleanup-bearing equivalent replay without cycles or duplicates") {
        val original = StreamError.source("source")
        val first    = new RuntimeException("first cleanup")
        val second   = new RuntimeException("second cleanup")
        val replay   = StreamError.mapped(original, "replayed")
        StreamError.attachCleanup(replay, first)
        StreamError.attachCleanup(replay, second)
        val source = new Reader.SyncReader[Int] {
          def isClosed: Boolean                 = false
          def read[A1 >: Int](sentinel: A1): A1 = throw original
          def close(): Unit                     = throw replay
        }

        runAsync(Stream.fromReader[String, Int](source).mapError(_ + "-mapped").runDrainAsync).either.map { result =>
          val error = result.left.toOption.orNull.asInstanceOf[StreamError]
          assertTrue(
            error.value == "source-mapped",
            error.cleanupFailed,
            error.getSuppressed.toList == List(first, second),
            !error.getSuppressed.exists(_ eq error),
            first.getSuppressed.isEmpty,
            second.getSuppressed.isEmpty
          )
        }
      },
      test("a real release throwing the read error object is a cleanup defect") {
        val error  = StreamError.source("typed")
        val source = new Reader.SyncReader[Int] {
          def isClosed: Boolean                 = false
          def read[A1 >: Int](sentinel: A1): A1 = throw error
          def close(): Unit                     = ()
        }
        val stream = Stream.fromReader[String, Int](source.withRelease(() => throw error))
        runAsync(stream.runDrainAsync).either.map(result =>
          assertTrue(result.left.exists(_ eq error), error.cleanupFailed)
        )
      },
      test("concat transition close StreamError is cleanup-marked") {
        val error = new StreamError("concat-close")
        val first = new Reader.SyncReader[Int] {
          override def jvmType: JvmType                                        = JvmType.Int
          def isClosed: Boolean                                                = true
          def read[A1 >: Int](sentinel: A1): A1                                = sentinel
          override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long = sentinel
          def close(): Unit                                                    = throw error
        }
        val thrown = try { first.concatWithJvmType(() => Reader.singleInt(1), JvmType.Int).readInt(-1L); null }
        catch { case cause: Throwable => cause }
        assertTrue(thrown eq error, error.cleanupFailed)
      },
      test("repeated EOF reset StreamError is cleanup-marked") {
        val error  = new StreamError("repeat-reset")
        val source = new Reader.SyncReader[Int] {
          override def jvmType: JvmType                                        = JvmType.Int
          def isClosed: Boolean                                                = true
          def read[A1 >: Int](sentinel: A1): A1                                = sentinel
          override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long = sentinel
          def close(): Unit                                                    = ()
          override def reset(): Unit                                           = throw error
        }
        val thrown = try { Reader.repeated(source).readInt(-1L); null }
        catch { case cause: Throwable => cause }
        assertTrue(thrown eq error, error.cleanupFailed)
      },
      test("flatMap exhausted inner close StreamError is cleanup-marked") {
        val error = new StreamError("flatmap-close")
        val inner = new Reader.SyncReader[Int] {
          def isClosed: Boolean                 = true
          def read[A1 >: Int](sentinel: A1): A1 = sentinel
          def close(): Unit                     = throw error
        }
        val stream = Stream.succeed(1).flatMap(_ => Stream.fromReader[String, Int](inner))
        runAsync(stream.runCollectAsync).either.map(result =>
          assertTrue(result.left.exists(_ eq error), error.cleanupFailed)
        )
      },
      test("Sink.mapError does not map a cleanup-marked StreamError") {
        val error = StreamError.attachCleanup(null, new StreamError("cleanup")).asInstanceOf[StreamError]
        val sink  = new Sink[String, Int, Unit] {
          private[streams] def drain(reader: Reader.SyncReader[_]): Unit         = throw error
          private[streams] def drain(reader: Reader.AsyncReader[_]): Async[Unit] = Async.failTrusted(error)
        }.mapError(_ + "-mapped")
        val thrown = try { sink.drain(Reader.singleInt(1)); null }
        catch { case cause: Throwable => cause }
        assertTrue(thrown eq error, error.value == "cleanup", error.cleanupFailed)
      },
      test("pipeline sink close cleanup cannot become Left and same-identity replay stays typed") {
        def adapted(error: StreamError, onClose: () => Unit): Sink[String, Int, Unit] = {
          val closingReader = new Reader.SyncReader[Int] {
            def isClosed: Boolean                 = false
            def read[A1 >: Int](sentinel: A1): A1 = throw error
            def close(): Unit                     = { onClose(); throw error }
          }
          val sink: Sink[String, Int, Unit] = Sink.drain
          val pipeline                      = new Pipeline[Int, Int] {
            def applyToStream[E](stream: Stream[E, Int]): Stream[E, Int]                                    = Stream.fromReader[E, Int](closingReader)
            def applyToSink[E, Z](sink: Sink[E, Int, Z]): Sink[E, Int, Z]                                   = Pipeline.runViaSink(this, sink)
            private[streams] def applyToAsyncReader(reader: Reader.AsyncReader[_]): Reader.AsyncReader[Int] =
              closingReader.toAsync
          }
          pipeline.andThenSink(sink)
        }
        val replay       = StreamError.source("replay")
        var replayCloses = 0

        val cleanup       = new StreamError("cleanup")
        var cleanupCloses = 0
        val cleanupReader = new Reader.SyncReader[Int] {
          def isClosed: Boolean                 = false
          def read[A1 >: Int](sentinel: A1): A1 = sentinel
          def close(): Unit                     = { cleanupCloses += 1; throw cleanup }
        }
        val cleanupPipeline = new Pipeline[Int, Int] {
          def applyToStream[E](stream: Stream[E, Int]): Stream[E, Int]                                    = Stream.fromReader[E, Int](cleanupReader)
          def applyToSink[E, Z](sink: Sink[E, Int, Z]): Sink[E, Int, Z]                                   = Pipeline.runViaSink(this, sink)
          private[streams] def applyToAsyncReader(reader: Reader.AsyncReader[_]): Reader.AsyncReader[Int] =
            cleanupReader.toAsync
        }
        val cleanupAdapted: Sink[String, Int, Unit] = cleanupPipeline.andThenSink(Sink.drain)
        val replayResult                            = runAsync(adapted(replay, () => replayCloses += 1).drain(Reader.singleInt(1).toAsync)).either
        val cleanupResult                           = runAsync(cleanupAdapted.drain(Reader.singleInt(1).toAsync)).either
        replayResult.zipWith(cleanupResult) { (replayOutcome, cleanupOutcome) =>
          assertTrue(
            replayOutcome.left.exists {
              case error: StreamError => (error eq replay) && error.value == "replay" && !error.cleanupFailed
              case _                  => false
            },
            replayCloses == 1,
            cleanupOutcome.left.exists(_ eq cleanup),
            cleanup.cleanupFailed,
            cleanupCloses == 1
          )
        }
      },
      test("ConcatWith closes first reader on switch") {
        var closed = false
        val first  = Stream.fromReader[Nothing, Int](new Reader.SyncReader[Int] {
          private var emitted                   = false
          def isClosed: Boolean                 = emitted
          def read[A1 >: Int](sentinel: A1): A1 = if (!emitted) { emitted = true; Int.box(1).asInstanceOf[A1] }
          else sentinel
          def close(): Unit = closed = true
        })
        runAsync((first ++ Stream.succeed(2)).runCollectAsync).map { result =>
          assert(result)(equalTo(Right(Chunk(1, 2)))) &&
          assert(closed)(isTrue)
        }
      },
      test("ensuring calls finalizer on normal completion") {
        var finalized = false
        runAsync(Stream.range(0, 3).ensuring { finalized = true }.runCollectAsync).map { result =>
          assert(result)(equalTo(Right(Chunk(0, 1, 2)))) &&
          assert(finalized)(isTrue)
        }
      },
      test("ensuring calls finalizer on error") {
        var finalized = false
        runAsync(Stream.fail("err").ensuring { finalized = true }.runCollectAsync).map { result =>
          assert(result)(equalTo(Left("err"))) &&
          assert(finalized)(isTrue)
        }
      },
      test("fromAcquireRelease calls release on error during use compile") {
        var released = false
        runAsync(
          Stream
            .fromAcquireRelease("resource", (_: String) => { released = true }) { _ =>
              throw new RuntimeException("compile failed")
              Stream.empty
            }
            .runCollectAsync
        ).either.map { result =>
          assertTrue(result.left.exists(_.isInstanceOf[RuntimeException]), released)
        }
      },
      test("fromAcquireRelease calls release on normal close") {
        var released = false
        val result   = Stream
          .fromAcquireRelease("res", (_: String) => { released = true }) { r =>
            Stream.succeed(r)
          }
          .runCollectAsync
        runAsync(result).map { collected =>
          assert(collected)(equalTo(Right(Chunk("res")))) &&
          assert(released)(isTrue)
        }
      }
    ),

    // ---- Suite 7: mapAccum ----

    suite("mapAccum")(
      test("cumulative sum") {
        runAsync(
          Stream
            .range(0, 5)
            .mapAccum(0) { (sum, a) =>
              val s = sum + a; (s, s)
            }
            .runCollectAsync
        ).map(result => assert(result)(equalTo(Right(Chunk(0, 1, 3, 6, 10)))))
      },
      test("finalized ownership wrappers reject reset and finalize exactly once") {
        var readerCloses = 0
        var releases     = 0
        val resettable   = new Reader.SyncReader[Int] {
          def isClosed: Boolean                 = false
          def read[A1 >: Int](sentinel: A1): A1 = sentinel
          def close(): Unit                     = readerCloses += 1
          override def reset(): Unit            = ()
        }
        val released = resettable.withRelease(() => releases += 1)
        released.reset()
        released.close()
        val releasedReset = scala.util.Try(released.reset()).failed.toOption.orNull
        released.close()

        var acquiredReleases = 0
        val acquired         = compileToSyncReader(
          Stream.fromAcquireRelease(new Object, (_: Object) => acquiredReleases += 1)(_ =>
            Stream.fromReader(resettable)
          )
        )
        acquired.reset()
        acquired.close()
        val acquiredReset = scala.util.Try(acquired.reset()).failed.toOption.orNull
        acquired.close()

        var resourceReleases = 0
        val resource         = Resource.acquireRelease(new Object)(_ => resourceReleases += 1)
        val scoped           = compileToSyncReader(Stream.fromResource(resource)(_ => Stream.fromReader(resettable)))
        scoped.reset()
        scoped.close()
        val scopedReset = scala.util.Try(scoped.reset()).failed.toOption.orNull
        scoped.close()

        assertTrue(
          releasedReset.isInstanceOf[UnsupportedOperationException],
          acquiredReset.isInstanceOf[UnsupportedOperationException],
          scopedReset.isInstanceOf[UnsupportedOperationException],
          releases == 1,
          acquiredReleases == 1,
          resourceReleases == 1,
          readerCloses == 3
        )
      },
      test("close reentered during ownership reset closes the reopened generation before returning") {
        def source(closeWrapper: () => Unit, closes: () => Unit) = new Reader.SyncReader[Int] {
          private var open                      = false
          def isClosed: Boolean                 = !open
          def read[A1 >: Int](sentinel: A1): A1 = if (open) 1.asInstanceOf[A1] else sentinel
          def close(): Unit                     = { open = false; closes() }
          override def reset(): Unit            = { open = true; closeWrapper() }
        }

        var releaseReader: Reader.SyncReader[Int] = null
        var releaseCloses                         = 0
        var releases                              = 0
        val releasedSource                        = source(() => releaseReader.close(), () => releaseCloses += 1)
        releaseReader = releasedSource.withRelease(() => releases += 1)

        var ensuringReader: Reader.SyncReader[Int] = null
        var ensuringCloses                         = 0
        var finalizers                             = 0
        val ensuringSource                         = source(() => ensuringReader.close(), () => ensuringCloses += 1)
        ensuringReader = compileToSyncReader(Stream.fromReader(ensuringSource).ensuring(finalizers += 1))

        val releaseReset  = scala.util.Try(releaseReader.reset()).failed.toOption.orNull
        val ensuringReset = scala.util.Try(ensuringReader.reset()).failed.toOption.orNull
        val releaseValue  = releaseReader.read[Any](null)
        val ensuringValue = ensuringReader.read[Any](null)
        releaseReader.close()
        ensuringReader.close()

        assertTrue(
          releaseReset.isInstanceOf[UnsupportedOperationException],
          ensuringReset.isInstanceOf[UnsupportedOperationException],
          releaseValue == null,
          ensuringValue == null,
          releaseCloses == 1,
          ensuringCloses == 1,
          releases == 1,
          finalizers == 1
        )
      },
      test("finalized ensuring reset records a fresh deferred-close failure") {
        val secondFailure                   = new RuntimeException("second finalizer")
        var wrapper: Reader.SyncReader[Int] = null
        var finalizations                   = 0
        val source                          = new Reader.SyncReader[Int] {
          private var open                      = false
          def isClosed: Boolean                 = !open
          def read[A1 >: Int](sentinel: A1): A1 = if (open) 1.asInstanceOf[A1] else sentinel
          def close(): Unit                     = open = false
          override def reset(): Unit            = { open = true; wrapper.close() }
        }
        wrapper = compileToSyncReader(Stream.fromReader(source).ensuring {
          finalizations += 1
          if (finalizations == 2) throw secondFailure
        })

        wrapper.close()
        val resetFailure = scala.util.Try(wrapper.reset()).failed.toOption.orNull
        val closeReplay  = scala.util.Try(wrapper.close()).failed.toOption.orNull

        assertTrue(
          resetFailure.isInstanceOf[UnsupportedOperationException],
          resetFailure.getSuppressed.toList == List(secondFailure),
          closeReplay eq secondFailure,
          finalizations == 2
        )
      },
      test("ownership wrappers stop delegating as soon as close begins") {
        def verify(wrap: Reader.SyncReader[Int] => Reader.SyncReader[Int]): Boolean = {
          var wrapper: Reader.SyncReader[Int] = null
          var reentrant: Any                  = "unread"
          val source                          = new Reader.SyncReader[Int] {
            def isClosed: Boolean              = false
            def read[A >: Int](sentinel: A): A = 1.asInstanceOf[A]
            def close(): Unit                  = reentrant = wrapper.read[Any](null)
          }
          wrapper = wrap(source)
          wrapper.close()
          (reentrant == null) && wrapper.isClosed && !wrapper.readable() && wrapper.read[Any](null) == null
        }

        assertTrue(
          verify(_.withRelease(() => ())),
          verify(source => compileToSyncReader(Stream.fromReader(source).ensuring(())))
        )
      },
      test("running index") {
        runAsync(
          Stream
            .fromIterable(List("a", "b", "c"))
            .mapAccum(0)((idx, s) => (idx + 1, s"$idx:$s"))
            .runCollectAsync
        ).map(result => assert(result)(equalTo(Right(Chunk("0:a", "1:b", "2:c")))))
      }
    ),

    suite("callback StreamError provenance")(
      test("external Reader subclass can report a trusted typed source failure") {
        val source = new Reader.SyncReader[Int] {
          def isClosed: Boolean              = false
          def close(): Unit                  = ()
          def read[A >: Int](sentinel: A): A = failSource("external-source")
        }
        runAsync(Stream.fromReader[String, Int](source).runDrainAsync).map(result =>
          assertTrue(result == Left("external-source"))
        )
      },
      test("iterator factory, hasNext, next, and reset factory are callback boundaries") {
        def untrusted(run: Async[Any]) =
          runAsync(run).either.map {
            case Left(error: StreamError) => !error.isTrusted
            case _                        => false
          }
        val factory = untrusted(Stream.fromIterator[Int](throw new StreamError("factory")).runDrainAsync)
        val hasNext = untrusted(
          Stream
            .fromIterator(new Iterator[Int] {
              def hasNext: Boolean = throw new StreamError("hasNext")
              def next(): Int      = 1
            })
            .runDrainAsync
        )
        val next = untrusted(
          Stream
            .fromIterator(new Iterator[Int] {
              def hasNext: Boolean = true
              def next(): Int      = throw new StreamError("next")
            })
            .runDrainAsync
        )
        var creations = 0
        val iterable  = new Iterable[Int] {
          def iterator: Iterator[Int] = {
            creations += 1; if (creations == 2) throw new StreamError("reset") else Iterator.single(1)
          }
        }
        val reader = Reader.fromIterable(iterable)
        reader.read[Any](null)
        val resetFailure = scala.util.Try(reader.reset()).failed.toOption.orNull
        for {
          factoryResult <- factory
          hasNextResult <- hasNext
          nextResult    <- next
        } yield assertTrue(
          factoryResult,
          hasNextResult,
          nextResult,
          resetFailure.isInstanceOf[StreamError],
          !resetFailure.asInstanceOf[StreamError].isTrusted
        )
      },
      test("stream callback owners expose direct StreamError as defects") {
        def defects(run: Async[Any]) =
          runAsync(run).either.map {
            case Left(error: StreamError) => !error.isTrusted
            case _                        => false
          }
        val pf: PartialFunction[Int, Int] = { case _ => throw new StreamError("collect") }
        val mapPar: Int => Int            = _ => throw new StreamError("mapPar")
        for {
          scan      <- defects(Stream.succeed(1).scan(0)((_, _) => throw new StreamError("scan")).runDrainAsync)
          collect   <- defects(Stream.succeed(1).collect(pf).runDrainAsync)
          takeWhile <- defects(Stream.succeed(1).takeWhile(_ => throw new StreamError("takeWhile")).runDrainAsync)
          mapPar    <- defects(Stream.succeed(1).mapPar(1)(mapPar).runDrainAsync)
          suspend   <- defects(Stream.suspend(throw new StreamError("suspend")).runDrainAsync)
          eval      <- defects(Stream.eval(throw new StreamError("eval")).runDrainAsync)
          defer     <- defects(Stream.defer(throw new StreamError("defer")).runDrainAsync)
          unfold    <- defects(Stream.unfold[Int, Nothing](0)(_ => throw new StreamError("unfold")).runDrainAsync)
        } yield assertTrue(scan, collect, takeWhile, mapPar, suspend, eval, defer, unfold)
      },
      test("sink callback owners expose direct StreamError as defects") {
        def defects(sink: Sink[Nothing, Int, _]) =
          runAsync(Stream.succeed(1).runAsync(sink)).either.map {
            case Left(error: StreamError) => !error.isTrusted
            case _                        => false
          }
        for {
          exists    <- defects(Sink.exists[Int](_ => throw new StreamError("exists")))
          find      <- defects(Sink.find[Int](_ => throw new StreamError("find")))
          forall    <- defects(Sink.forall[Int](_ => throw new StreamError("forall")))
          fold      <- defects(Sink.foldLeft[Int, Int](0)((_, _) => throw new StreamError("fold")))
          contramap <- defects(Sink.drain.contramap[Nothing, Int](_ => throw new StreamError("contramap")))
          map       <- defects(Sink.count.map(_ => throw new StreamError("map")))
        } yield assertTrue(exists, find, forall, fold, contramap, map)
      },
      test("Reader lazy callback owners expose direct StreamError as defects") {
        val concat = Reader.singleInt(1).concatWithJvmType(() => throw new StreamError("concat"), JvmType.Int)
        concat.readInt(-1L)
        val concatFailure = scala.util.Try(concat.readInt(-1L)).failed.toOption.orNull
        val unfoldFailure =
          scala.util
            .Try(Reader.unfold[Int, Nothing](0)(_ => throw new StreamError("unfold")).read[Any](null))
            .failed
            .toOption
            .orNull
        assertTrue(
          concatFailure.isInstanceOf[StreamError],
          !concatFailure.asInstanceOf[StreamError].isTrusted,
          unfoldFailure.isInstanceOf[StreamError],
          !unfoldFailure.asInstanceOf[StreamError].isTrusted
        )
      },
      test("Sink.map preserves upstream typed failure and Sink.createAsync distinguishes callback from reader origin") {
        val mapped          = runAsync(Stream.fail("upstream").runAsync(Sink.drain.map(_ => 1)))
        val callbackFailure = runAsync(
          Stream.succeed(1).runAsync(Sink.createAsync[String, Int, Int](_ => throw new StreamError("callback")))
        ).either
        val readerOrigin = runAsync(
          (Stream.succeed(1).take(0) ++ Stream.fail("reader"))
            .runAsync(Sink.createAsync[String, Int, Int](_.readInt(-1L).map(_.toInt)))
        )
        for {
          mappedResult   <- mapped
          callbackResult <- callbackFailure
          readerResult   <- readerOrigin
        } yield assertTrue(
          mappedResult == Left("upstream"),
          callbackResult.left.exists { case error: StreamError => !error.isTrusted; case _ => false },
          readerResult == Left("reader")
        )
      },
      test("callback provenance preserves ordered cleanup failures") {
        val first  = new RuntimeException("first")
        val second = new RuntimeException("second")
        val source = StreamError.source("typed")
        source.cleanupFailed = true
        source.addSuppressed(first)
        source.addSuppressed(second)

        val failure =
          scala.util.Try(StreamError.callback(throw source)).failed.toOption.orNull.asInstanceOf[StreamError]

        assertTrue(
          !failure.isTrusted,
          failure.cleanupFailed,
          failure.getSuppressed.toList == List(first, second)
        )
      }
    ),

    // ---- Suite 8: Sink errors ----

    suite("Sink errors")(
      test("Sink.fail(e) produces Left(e)") {
        runAsync(Stream.succeed(1).runAsync(Sink.fail("e"))).map(result => assert(result)(equalTo(Left("e"))))
      },
      test("Sink.mapError transforms the error value") {
        var mapped = 0
        runAsync(
          Stream.succeed(1).runAsync(Sink.fail(true).mapError { value => mapped += 1; value.toString })
        ).map(result => assertTrue(result == Left("true"), mapped == 1))
      },
      test("Sink.mapError never receives an upstream stream error") {
        var mapped        = 0
        val consumingSink = new Sink[Boolean, Int, Unit] {
          private[streams] def drain(reader: Reader.SyncReader[_]): Unit = {
            val _ = reader.read[Any](new AnyRef)
            ()
          }
          private[streams] def drain(reader: Reader.AsyncReader[_]): Async[Unit] =
            reader.read[Any](new AnyRef).map(_ => ())
        }
        val stream: Stream[String, Int] = Stream.fail("upstream")
        runAsync(stream.runAsync(consumingSink.mapError { value => mapped += 1; value.toString })).map(result =>
          assertTrue(result == Left("upstream"), mapped == 0)
        )
      },
      test("runAsync preserves heterogeneous stream and sink error origins") {
        val errors: Concat.WithOut[String, Boolean, String | Boolean] = implicitly
        val streamFailure: Stream[String, Int]                        = Stream.fail("stream")
        val consumingSink                                             = new Sink[Boolean, Int, Unit] {
          private[streams] def drain(reader: Reader.SyncReader[_]): Unit = {
            val _ = reader.read[Any](new AnyRef)
            ()
          }
          private[streams] def drain(reader: Reader.AsyncReader[_]): Async[Unit] =
            reader.read[Any](new AnyRef).map(_ => ())
        }
        val streamResult: Async[Either[String | Boolean, Unit]]  = streamFailure.runAsync(consumingSink)(errors)
        val sinkResult: Async[Either[String | Boolean, Nothing]] =
          Stream.succeed(1).asInstanceOf[Stream[String, Int]].runAsync(Sink.fail(true))(errors)
        for {
          left  <- runAsync(streamResult)
          right <- runAsync(sinkResult)
        } yield assertTrue(left == Left(errors.left("stream")), right == Left(errors.right(true)))
      }
    ),

    // ---- Suite 9: fromReader ----

    suite("fromReader")(
      test("fromReader wraps a reader as a stream") {
        runAsync(Stream.fromReader[Nothing, Int](Reader.fromRange(0 until 3)).runCollectAsync).map(result =>
          assert(result)(equalTo(Right(Chunk(0, 1, 2))))
        )
      },
      test("drop and take close ownership when synchronous control initialization throws") {
        def rejected(take: Boolean): (Throwable, Int) = {
          val initialization = new RuntimeException(if (take) "limit" else "skip")
          var closes         = 0
          val source         = new Reader.SyncReader[Int] {
            def isClosed: Boolean                   = closes != 0
            def close(): Unit                       = closes += 1
            def read[A1 >: Int](sentinel: A1): A1   = sentinel
            override def setLimit(n: Long): Boolean = throw initialization
            override def setSkip(n: Long): Boolean  = throw initialization
          }
          val stream =
            if (take) Stream.fromReader[Nothing, Int](source).take(1)
            else Stream.fromReader[Nothing, Int](source).drop(1)
          val failure = scala.util.Try(stream.compile(0)).failed.toOption.orNull
          (failure, closes)
        }
        val taken   = rejected(take = true)
        val dropped = rejected(take = false)
        assertTrue(taken._1.getMessage == "limit", taken._2 == 1, dropped._1.getMessage == "skip", dropped._2 == 1)
      }
    )
  )
}

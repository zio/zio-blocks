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

import zio.ZIO
import zio.blocks.async._
import zio.blocks.combinators.Concat
import zio.blocks.streams.internal.StreamError
import zio.blocks.streams.io.Reader
import zio.durationInt
import zio.test._

object PlatformSpec extends StreamsBaseSpec {
  override def aspects: zio.Chunk[TestAspectAtLeastR[TestEnvironment]] =
    zio.Chunk(TestAspect.timeout(60.seconds), TestAspect.timed, TestAspect.sequential)

  private def isExactRange(chunk: zio.blocks.chunk.Chunk[Int], n: Int): Boolean =
    if (chunk.length != n) false
    else {
      val iterator = chunk.iterator
      var i        = 0
      var ok       = true
      while (ok && iterator.hasNext) {
        if (iterator.next() != i) ok = false
        i += 1
      }
      ok && i == n
    }

  def spec: Spec[TestEnvironment, Any] = suite("Platform")(
    test("startVirtualThread runs the task") {
      ZIO.attemptBlocking {
        val flag = new java.util.concurrent.atomic.AtomicBoolean(false)
        val t    = Platform.startVirtualThread("test-vthread", () => flag.set(true))
        t.join(5000)
        assertTrue(flag.get())
      }
    },
    test("buffer collects range with full fidelity") {
      ZIO.attemptBlocking {
        val n      = 200
        val result = Stream.range(0, n).buffer(8).runCollect
        assertTrue(result match {
          case Right(chunk) => isExactRange(chunk, n)
          case _            => false
        })
      }
    },
    test("deep primitive concatenation folds in order") {
      ZIO.attemptBlocking {
        var stream: Stream[Nothing, Int] = Stream.succeed(0)
        var i                            = 1
        while (i <= 10000) {
          stream = stream ++ Stream.succeed(i)
          i += 1
        }
        assertTrue(stream.runFold(0L)(_ + _) == Right(50005000L))
      }
    },
    test("buffer propagates stream failures") {
      ZIO.attemptBlocking {
        val result = Stream.fail("boom").buffer(16).runCollect
        assertTrue(result == Left("boom"))
      }
    },
    test("buffer supports early termination") {
      ZIO.attemptBlocking {
        val result = Stream.range(0, Int.MaxValue).buffer(64).take(10).runCollect
        assertTrue(result match {
          case Right(chunk) => isExactRange(chunk, 10)
          case _            => false
        })
      }
    },
    test("range drain preserves callbacks, failures, and finalization") {
      ZIO.attemptBlocking {
        var sum       = 0
        var finalized = 0
        val success   = Stream
          .range(0, 5)
          .map { value => sum += value; value }
          .ensuring(finalized += 1)
          .runDrain
        val failure = Stream.range(0, 5).map(value => if (value == 3) throw new RuntimeException("boom") else value)
        assertTrue(
          Stream.range(0, 5).runDrain == Right(()),
          success == Right(()),
          sum == 10,
          finalized == 1,
          scala.util.Try(failure.runDrain).failed.toOption.exists(_.getMessage == "boom")
        )
      }
    },
    test("range filter/fold fusion keeps predicate and fold failures untrusted") {
      ZIO.attemptBlocking {
        val predicateFailure = scala.util
          .Try(Stream.range(0, 3).filter(_ => throw StreamError.source("predicate")).runFold(0L)(_ + _))
          .failed
          .toOption
          .orNull
        val foldFailure = scala.util
          .Try(Stream.range(0, 3).filter(_ => true).runFold(0L)((_, _) => throw StreamError.source("fold")))
          .failed
          .toOption
          .orNull
        assertTrue(
          predicateFailure.isInstanceOf[StreamError],
          !predicateFailure.asInstanceOf[StreamError].isTrusted,
          foldFailure.isInstanceOf[StreamError],
          !foldFailure.asInstanceOf[StreamError].isTrusted,
          Stream.range(0, 6).filter(_ % 2 != 0).runFold(0L)(_ + _) == Right(9L)
        )
      }
    },
    test("range take/fold fusion handles boundaries and callback failures") {
      ZIO.attemptBlocking {
        val failure       = new RuntimeException("fold failed")
        val streamFailure = scala.util
          .Try(Stream.range(0, 5).take(3).runFold(0L)((_, _) => throw StreamError.source("fold")))
          .failed
          .toOption
          .orNull
        assertTrue(
          Stream.range(0, 5).take(-1).runFold(0L)(_ + _) == Right(0L),
          Stream.range(0, 5).take(0).runFold(0L)(_ + _) == Right(0L),
          Stream.range(0, 5).take(3).runFold(0L)(_ + _) == Right(3L),
          Stream.range(0, 5).take(100).runFold(0L)(_ + _) == Right(10L),
          scala.util
            .Try(Stream.range(0, 5).take(3).runFold(0L)((_, _) => throw failure))
            .failed
            .toOption
            .contains(failure),
          streamFailure.isInstanceOf[StreamError],
          !streamFailure.asInstanceOf[StreamError].isTrusted
        )
      }
    },
    test("range drop/take/fold fusion handles boundaries and callback failures") {
      ZIO.attemptBlocking {
        val failure       = new RuntimeException("fold failed")
        val streamFailure = scala.util
          .Try(Stream.range(0, 5).drop(1).take(3).runFold(0L)((_, _) => throw StreamError.source("fold")))
          .failed
          .toOption
          .orNull
        assertTrue(
          Stream.range(0, 5).drop(-1).take(2).runFold(0L)(_ + _) == Right(1L),
          Stream.range(0, 5).drop(0).take(0).runFold(0L)(_ + _) == Right(0L),
          Stream.range(0, 5).drop(2).take(2).runFold(0L)(_ + _) == Right(5L),
          Stream.range(0, 5).drop(100).take(2).runFold(0L)(_ + _) == Right(0L),
          Stream.range(0, 5).drop(Long.MaxValue).take(Long.MaxValue).runFold(0L)(_ + _) == Right(0L),
          Stream
            .range(Int.MaxValue - 3, Int.MaxValue)
            .drop(1)
            .take(Long.MaxValue)
            .runFold(0L)(_ + _) == Right((Int.MaxValue - 2).toLong + Int.MaxValue - 1),
          scala.util
            .Try(Stream.range(0, 5).drop(1).take(3).runFold(0L)((_, _) => throw failure))
            .failed
            .toOption
            .contains(failure),
          streamFailure.isInstanceOf[StreamError],
          !streamFailure.asInstanceOf[StreamError].isTrusted
        )
      }
    },
    test("range takeWhile/fold fusion preserves stopping, effects, failures, and integer boundaries") {
      ZIO.attemptBlocking {
        var tested           = List.empty[Int]
        var folded           = List.empty[Int]
        val predicateFailure = new RuntimeException("predicate failed")
        val foldFailure      = new RuntimeException("fold failed")
        val result           = Stream
          .range(Int.MaxValue - 3, Int.MaxValue)
          .takeWhile { value =>
            tested ::= value
            value < Int.MaxValue - 1
          }
          .runFold(0L) { (acc, value) =>
            folded ::= value
            acc + value.toLong
          }
        assertTrue(
          result == Right((Int.MaxValue - 3).toLong + (Int.MaxValue - 2).toLong),
          tested.reverse == List(Int.MaxValue - 3, Int.MaxValue - 2, Int.MaxValue - 1),
          folded.reverse == List(Int.MaxValue - 3, Int.MaxValue - 2),
          Stream.range(Int.MinValue, Int.MinValue + 3).takeWhile(_ => false).runFold(7L)(_ + _) == Right(7L),
          scala.util
            .Try(Stream.range(0, 5).takeWhile(i => if (i == 2) throw predicateFailure else true).runFold(0L)(_ + _))
            .failed
            .toOption
            .contains(predicateFailure),
          scala.util
            .Try(Stream.range(0, 5).takeWhile(_ => true).runFold(0L) { (acc, i) =>
              if (i == 2) throw foldFailure else acc + i
            })
            .failed
            .toOption
            .contains(foldFailure)
        )
      }
    },
    test("blocking run preserves heterogeneous stream and sink error origins") {
      ZIO.attemptBlocking {
        val errors: Concat.WithOut[String, Boolean, String | Boolean] = implicitly
        val consumingSink                                             = new Sink[Boolean, Int, Unit] {
          private[streams] def drain(reader: Reader.SyncReader[_]): Unit = {
            val _ = reader.read[Any](new AnyRef)
            ()
          }
          private[streams] def drain(reader: Reader.AsyncReader[_]): Async[Unit] =
            reader.read[Any](new AnyRef).map(_ => ())
        }
        val streamResult: Either[String | Boolean, Unit] =
          Stream.fail[String]("stream").asInstanceOf[Stream[String, Int]].run(consumingSink)(errors)
        val sinkResult: Either[String | Boolean, Nothing] =
          Stream.succeed(1).asInstanceOf[Stream[String, Int]].run(Sink.fail(true))(errors)
        assertTrue(streamResult == Left(errors.left("stream")), sinkResult == Left(errors.right(true)))
      }
    },
    test("blocking Sink.mapError never receives an upstream stream error") {
      ZIO.attemptBlocking {
        var mapped        = 0
        val consumingSink = new Sink[Boolean, Int, Unit] {
          private[streams] def drain(reader: Reader.SyncReader[_]): Unit = {
            val _ = reader.read[Any](new AnyRef)
            ()
          }
          private[streams] def drain(reader: Reader.AsyncReader[_]): Async[Unit] =
            reader.read[Any](new AnyRef).map(_ => ())
        }
        val result = Stream
          .fail[String]("upstream")
          .asInstanceOf[Stream[String, Int]]
          .run(consumingSink.mapError { value => mapped += 1; value.toString })
        assertTrue(result == Left("upstream"), mapped == 0)
      }
    }
  )
}

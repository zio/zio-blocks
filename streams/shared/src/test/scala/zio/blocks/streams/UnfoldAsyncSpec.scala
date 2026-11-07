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
import zio._
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.internal.StreamError
import zio.blocks.streams.io.Reader
import zio.test._

object UnfoldAsyncSpec extends StreamsBaseSpec {
  private implicit val ec: ExecutionContext = new ExecutionContext {
    def execute(runnable: Runnable): Unit     = Async.schedule(runnable, forceMacrotask = false)
    def reportFailure(cause: Throwable): Unit = throw cause
  }
  private def run[A](effect: Async[A]): ZIO[Any, Throwable, A] = ZIO.fromFuture(_ => effect.toFuture)

  private def jvm(name: String)(body: => TestResult) =
    test(name)(if (TestPlatform.isJVM) body else assertTrue(true))

  def spec = suite("unfoldAsync")(
    test("Boolean pulls preserve true and false without boxing") {
      val reader = Reader.unfoldAsync(0)(state => Async.succeed(if (state < 2) Some((state == 0, state + 1)) else None))
      for {
        first  <- run(reader.readBoolean(-1))
        second <- run(reader.readBoolean(-1))
        eof    <- run(reader.readBoolean(-1))
      } yield assertTrue(first == 1, second == 0, eof == -1)
    },
    test("construction is lazy and ready callbacks commit state in order") {
      var calls  = 0
      val reader = Reader.unfoldAsync(0) { state =>
        calls += 1
        Async.succeed(if (state < 3) Some((state, state + 1)) else None)
      }
      val lazyConstruction = calls == 0
      for {
        ready  <- run(reader.readable())
        values <- run(reader.readAll[Int]())
      } yield assertTrue(lazyConstruction, ready, values == Chunk(0, 1, 2), calls == 4)
    },
    jvm("pending callback permits one pull and commits only after success") {
      val pending = new Completer[Option[(Int, Int)]]
      val started = new Completer[Unit]
      val reader  = Reader.unfoldAsync(0) { _ => started.succeed(()); pending }
      val first   = reader.readInt(-1L).start
      started.block
      val second = Try(reader.readInt(-2L).block).failed.toOption
      val during = reader.readable().block
      pending.succeed(Some((10, 1)))
      assertTrue(second.exists(_.isInstanceOf[IllegalStateException]), !during, first.block == 10L)
    },
    test("callback failure is untrusted, closes the generation, and commits no state") {
      val forged = new StreamError("forged")
      val reader = Reader.unfoldAsync[Int, Nothing](0)(_ => Async.fail(forged))
      for {
        first  <- run(reader.readInt(-1L).either)
        _      <- run(reader.reset())
        second <- run(reader.readInt(-1L).either)
        closed <- run(reader.isClosed)
      } yield {
        val failure = first.left.toOption.orNull
        val replay  = second.left.toOption.orNull
        assertTrue(
          failure.isInstanceOf[StreamError],
          !failure.asInstanceOf[StreamError].isTrusted,
          replay.isInstanceOf[StreamError],
          closed
        )
      }
    },
    test("cancellation closes and rejects a late callback state commit") {
      if (TestPlatform.isJVM) {
        val pending = new Completer[Option[(Int, Int)]]
        val started = new Completer[Unit]
        var calls   = 0
        val reader  = Reader.unfoldAsync(0) { state =>
          calls += 1
          if (calls == 1) { started.succeed(()); pending.asInstanceOf[Async[Option[(Int, Int)]]] }
          else Async.succeed(Some((state, state + 1)))
        }
        val running = reader.readInt(-1L).start
        for {
          _      <- run(started)
          cleanup = Async.cancelWithCleanup(running)
          _       = pending.succeed(Some((99, 100)))
          _      <- run(cleanup)
          _      <- run(reader.close())
          _      <- run(reader.reset())
          value  <- run(reader.readInt(-1L))
        } yield assertTrue(value == 0L)
      } else ZIO.succeed(assertTrue(true))
    },
    jvm("reset invalidates a stale completion and restarts from initial state") {
      val pending = new Completer[Option[(Int, Int)]]
      val started = new Completer[Unit]
      var calls   = 0
      val reader  = Reader.unfoldAsync(0) { state =>
        calls += 1
        if (calls == 1) { started.succeed(()); pending.asInstanceOf[Async[Option[(Int, Int)]]] }
        else Async.succeed(Some((state, state + 1)))
      }
      val stale = reader.readInt(-1L).start
      started.block
      reader.reset().block
      pending.succeed(Some((7, 8)))
      val staleValue = stale.block
      val freshValue = reader.readInt(-1L).block
      assertTrue(staleValue == -1L, freshValue == 0L)
    },
    test("close cancels an active callback and settles its pull to EOF") {
      val pending = new Completer[Option[(Int, Int)]]
      val started = new Completer[Unit]
      val reader  = Reader.unfoldAsync(0) { _ => started.succeed(()); pending }
      val pull    = reader.readInt(-1L).start
      for {
        _      <- run(started)
        _      <- run(reader.close())
        _       = pending.succeed(Some((99, 100)))
        value  <- run(pull)
        closed <- run(reader.isClosed)
      } yield assertTrue(value == -1L, closed)
    },
    test("close owns callback construction and waits for cancellation cleanup") {
      val started     = new Completer[Unit]
      val cleanupGate = new Completer[Unit]
      val resultGate  = new Completer[Option[(Int, Int)]]
      var cleaned     = false
      val reader      = Reader.unfoldAsync(0) { _ =>
        Async.bracketAsync(
          () => Async.succeed(()),
          (_: Unit) => { started.succeed(()); resultGate },
          (_: Unit) => cleanupGate.map { _ => cleaned = true; () }
        )
      }
      val pull = reader.readInt(-1L).start
      val noop = new Runnable { def run(): Unit = () }
      for {
        _      <- run(started)
        closing = reader.close().start
        pending = closing.poll(noop).isInstanceOf[Pollable[?]]
        _       = cleanupGate.succeed(())
        _      <- run(closing)
        value  <- run(pull)
      } yield assertTrue(pending, cleaned, value == -1L)
    },
    test("callback construction may close its own reader without self-joining") {
      var reader: Reader.AsyncReader[Int] = null
      reader = Reader.unfoldAsync(0) { _ =>
        reader.close().block
        Async.succeed(Some((1, 1)))
      }
      Live
        .live(
          run(reader.readInt(-1L)).timeoutFail(new RuntimeException("reentrant close self-joined"))(5.seconds)
        )
        .map(value => assertTrue(value == -1L, reader.isClosed.block))
    },
    test("callback construction rejects a reentrant reset without corrupting its generation") {
      var resetFailure: Throwable         = null
      var reader: Reader.AsyncReader[Int] = null
      reader = Reader.unfoldAsync(0) { state =>
        resetFailure = Try(reader.reset().block).failed.toOption.orNull
        Async.succeed(Some((state, state + 1)))
      }
      for {
        value <- run(reader.readInt(-1L))
        next  <- run(reader.readInt(-1L))
      } yield assertTrue(
        resetFailure.isInstanceOf[java.io.IOException],
        resetFailure.getMessage == "Reader reset cannot run reentrantly from an active pull",
        value == 0L,
        next == 1L
      )
    },
    test("cleanup may join an externally claimed close without self-joining the pull") {
      val started                         = new Completer[Unit]
      val nestedReturned                  = new Completer[Unit]
      val cleanupGate                     = new Completer[Unit]
      val resultGate                      = new Completer[Option[(Int, Int)]]
      var cleanups                        = 0
      var reader: Reader.AsyncReader[Int] = null
      reader = Reader.unfoldAsync(0) { _ =>
        Async.bracketAsync(
          () => Async.succeed(()),
          (_: Unit) => { started.succeed(()); resultGate },
          (_: Unit) =>
            Async.start {
              reader.close().block
              nestedReturned.succeed(())
            }
              .flatMap(_ => cleanupGate.map { _ => cleanups += 1; () })
        )
      }
      val pull = reader.readInt(-1L).start
      val noop = new Runnable { def run(): Unit = () }
      for {
        _              <- run(started)
        closing         = reader.close().start
        _              <- run(nestedReturned)
        follower        = reader.close().start
        closingPending  = closing.poll(noop).isInstanceOf[Pollable[?]]
        followerPending = follower.poll(noop).isInstanceOf[Pollable[?]]
        _               = cleanupGate.succeed(())
        _              <- run(closing)
        _              <- run(follower)
        value          <- run(pull)
      } yield assertTrue(closingPending, followerPending, cleanups == 1, value == -1L)
    },
    test("direct pull cancellation retains ownership through nested close cleanup") {
      val started                         = new Completer[Unit]
      val cleanupDone                     = new Completer[Unit]
      val resultGate                      = new Completer[Option[(Int, Int)]]
      var cleanups                        = 0
      var reader: Reader.AsyncReader[Int] = null
      reader = Reader.unfoldAsync(0) { _ =>
        Async.bracketAsync(
          () => Async.succeed(()),
          (_: Unit) => { started.succeed(()); resultGate },
          (_: Unit) => {
            reader.close().block
            cleanups += 1
            cleanupDone.succeed(())
            Async.succeed(())
          }
        )
      }
      val pull = reader.readInt(-1L).start
      for {
        _      <- run(started)
        _      <- run(Async.cancelWithCleanup(pull))
        _      <- run(cleanupDone)
        closed <- run(reader.isClosed)
      } yield assertTrue(cleanups == 1, closed)
    },
    test("a stale pull cancelled after reset cannot close the fresh generation") {
      val started     = new Completer[Unit]
      val cleanupGate = new Completer[Unit]
      val resultGate  = new Completer[Option[(Int, Int)]]
      var calls       = 0
      val reader      = Reader.unfoldAsync(0) { state =>
        calls += 1
        if (calls == 1)
          Async.bracketAsync(
            () => Async.succeed(()),
            (_: Unit) => { started.succeed(()); resultGate },
            (_: Unit) => cleanupGate
          )
        else Async.succeed(Some((state, state + 1)))
      }
      val stale = reader.readInt(-1L).start
      for {
        _          <- run(started)
        reset       = reader.reset().start
        _           = cleanupGate.succeed(())
        _          <- run(reset)
        staleValue <- run(stale)
        _           = stale.cancel()
        fresh      <- run(reader.readInt(-1L))
        closed     <- run(reader.isClosed)
      } yield assertTrue(staleValue == -1L, fresh == 0L, !closed)
    },
    test("close racing reset invalidates the reset reservation and cannot reopen the reader") {
      val started        = new Completer[Unit]
      val cleanupStarted = new Completer[Unit]
      val cleanupGate    = new Completer[Unit]
      val resultGate     = new Completer[Option[(Int, Int)]]
      val reader         = Reader.unfoldAsync(0) { _ =>
        Async.bracketAsync(
          () => Async.succeed(()),
          (_: Unit) => { started.succeed(()); resultGate },
          (_: Unit) => { cleanupStarted.succeed(()); cleanupGate }
        )
      }
      val pull = reader.readInt(-1L).start
      for {
        _           <- run(started)
        reset        = reader.reset().start
        _           <- run(cleanupStarted)
        close        = reader.close().asInstanceOf[Pollable[Unit]].poll(new Runnable { def run(): Unit = () })
        _            = cleanupGate.succeed(())
        resetResult <- run(reset.either)
        _           <- run(close)
        value       <- run(pull)
        closed      <- run(reader.isClosed)
      } yield assertTrue(resetResult.isLeft, value == -1L, closed)
    },
    test("an invalidated reset cannot adopt or clear a newer reset reservation") {
      val started        = new Completer[Unit]
      val cleanupStarted = new Completer[Unit]
      val cleanupGate    = new Completer[Unit]
      val resultGate     = new Completer[Option[(Int, Int)]]
      var calls          = 0
      val reader         = Reader.unfoldAsync(0) { state =>
        calls += 1
        if (calls == 1)
          Async.bracketAsync(
            () => Async.succeed(()),
            (_: Unit) => { started.succeed(()); resultGate },
            (_: Unit) => { cleanupStarted.succeed(()); cleanupGate }
          )
        else Async.succeed(Some((state, state + 1)))
      }
      val pull = reader.readInt(-1L).start
      for {
        _            <- run(started)
        first         = reader.reset().start
        _            <- run(cleanupStarted)
        closing       = reader.close().asInstanceOf[Pollable[Unit]].poll(new Runnable { def run(): Unit = () })
        second        = reader.reset().start
        _             = cleanupGate.succeed(())
        firstResult  <- run(first.either)
        _            <- run(closing)
        secondResult <- run(second.either)
        stale        <- run(pull)
        fresh        <- run(reader.readInt(-1L))
        closed       <- run(reader.isClosed)
      } yield assertTrue(
        firstResult.isLeft,
        secondResult == Right(()),
        stale == -1L,
        fresh == 0L,
        !closed
      )
    },
    test("natural EOF remains resettable and is not an explicit close") {
      var calls  = 0
      val reader = Reader.unfoldAsync(0) { state =>
        calls += 1
        Async.succeed(if (state == 0) None else Some((state, state)))
      }
      for {
        eof    <- run(reader.readInt(-1L))
        _      <- run(reader.reset())
        replay <- run(reader.readInt(-2L))
      } yield assertTrue(eof == -1L, replay == -2L, calls == 2)
    },
    test("EOF is sticky until reset and control operations are honest") {
      var calls  = 0
      val reader = Reader.unfoldAsync(0) { state =>
        calls += 1
        Async.succeed(if (state == 0) Some((1, 1)) else None)
      }
      for {
        first    <- run(reader.readInt(-1L))
        eof1     <- run(reader.readInt(-1L))
        eof2     <- run(reader.readInt(-2L))
        closed   <- run(reader.isClosed)
        readable <- run(reader.readable())
        limit    <- run(reader.setLimit(1))
        skip     <- run(reader.setSkip(1))
        repeat   <- run(reader.setRepeat())
      } yield assertTrue(
        first == 1L,
        eof1 == -1L,
        eof2 == -2L,
        calls == 2,
        closed,
        !readable,
        !limit,
        !skip,
        !repeat
      )
    },
    test("Stream.unfoldAsync is a native async source") {
      run(
        Stream
          .unfoldAsync(0)(state => Async.succeed(if (state < 3) Some((state, state + 1)) else None))
          .runCollectAsync
      ).map(result => assertTrue(result == Right(Chunk(0, 1, 2))))
    }
  )
}

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

import zio.blocks.chunk.Chunk
import zio.blocks.streams.internal.StreamError
import zio.blocks.streams.io.Reader
import zio.test._

object ZipSpec extends StreamsBaseSpec {
  def spec = suite("Stream.&&")(
    test("zips two streams of equal length") {
      runAsync((Stream(1, 2, 3) && Stream("a", "b", "c")).runCollectAsync).map(result =>
        assertTrue(result == Right(Chunk((1, "a"), (2, "b"), (3, "c"))))
      )
    },
    test("shorter stream determines length (left shorter)") {
      runAsync((Stream(1, 2) && Stream("a", "b", "c")).runCollectAsync)
        .map(result => assertTrue(result == Right(Chunk((1, "a"), (2, "b")))))
    },
    test("shorter stream determines length (right shorter)") {
      runAsync((Stream(1, 2, 3) && Stream("a")).runCollectAsync)
        .map(result => assertTrue(result == Right(Chunk((1, "a")))))
    },
    test("empty stream yields empty") {
      runAsync((Stream[Int]() && Stream(1, 2, 3)).runCollectAsync)
        .map(result => assertTrue(result == Right(Chunk.empty)))
    },
    test("triple zip flattens: a && b && c gives (A, B, C)") {
      runAsync((Stream(1, 2) && Stream("a", "b") && Stream(true, false)).runCollectAsync)
        .map(result => assertTrue(result == Right(Chunk((1, "a", true), (2, "b", false)))))
    },
    test("error in left stream propagates") {
      runAsync(((Stream.fail("boom"): Stream[String, Int]) && Stream(1, 2)).runCollectAsync)
        .map(result => assertTrue(result == Left("boom")))
    },
    test("error in right stream propagates") {
      runAsync((Stream(1, 2) && (Stream.fail("boom"): Stream[String, Int])).runCollectAsync)
        .map(result => assertTrue(result == Left("boom")))
    },
    test("resources are cleaned up on both sides") {
      val leftClosed  = new java.util.concurrent.atomic.AtomicBoolean(false)
      val rightClosed = new java.util.concurrent.atomic.AtomicBoolean(false)
      val left        = Stream.range(0, 100).ensuring(leftClosed.set(true))
      val right       = Stream.range(0, 3).ensuring(rightClosed.set(true))
      runAsync((left && right).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk((0, 0), (1, 1), (2, 2)))) &&
        assertTrue(leftClosed.get()) &&
        assertTrue(rightClosed.get())
      }
    },
    test("&& closes left if right compilation fails") {
      var leftClosed = false
      val left       = Stream.fromAcquireRelease("left", (_: String) => leftClosed = true)(_ => Stream(1, 2, 3))
      val right      = Stream.fromReader[Nothing, Int](throw new RuntimeException("compile fail"))
      runAsync((left && right).runCollectAsync).either.map { result =>
        val status = result match {
          case Left(_: RuntimeException) => "threw"
          case _                         => "no exception"
        }
        assertTrue(status == "threw") && assertTrue(leftClosed)
      }
    },
    test("&& close accumulates exceptions from both sides") {
      var leftClosed  = false
      var rightClosed = false
      val left        = Stream(1, 2, 3).ensuring { leftClosed = true; throw new RuntimeException("left close") }
      val right       = Stream(4, 5, 6).ensuring { rightClosed = true; throw new RuntimeException("right close") }
      runAsync((left && right).runCollectAsync).either.map { result =>
        val (msg, suppressed) = result match {
          case Left(e: RuntimeException) => (e.getMessage, e.getSuppressed.length)
          case _                         => ("no exception", -1)
        }
        assertTrue(leftClosed) && assertTrue(rightClosed) &&
        assertTrue(msg == "left close") && assertTrue(suppressed == 1)
      }
    },
    test("right read failure stays primary when left close fails and right close replays the read failure") {
      val readFailure = StreamError.source("read")
      val leftClose   = new RuntimeException("left-close")
      val left        = new Reader.SyncReader[Int] {
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A = 1
        def close(): Unit                  = throw leftClose
      }
      val right = new Reader.SyncReader[Int] {
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A = throw readFailure
        def close(): Unit                  = throw readFailure
      }

      runAsync(
        (Stream.fromReader[String, Int](left) && Stream.fromReader[String, Int](right)).runDrainAsync
      ).either.map { result =>
        val thrown = result.left.toOption.orNull
        assertTrue(
          thrown eq readFailure,
          readFailure.getSuppressed.toList == List(leftClose),
          !leftClose.getSuppressed.contains(readFailure),
          readFailure.cleanupFailed
        )
      }
    },
    test("right read failure is sticky without consuming another left value") {
      val failure   = new RuntimeException("right read")
      var leftPulls = 0
      val left      = new Reader.SyncReader[Int] {
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A = {
          leftPulls += 1
          leftPulls.asInstanceOf[A]
        }
        def close(): Unit = ()
      }
      val right = new Reader.SyncReader[Int] {
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A = throw failure
        def close(): Unit                  = ()
      }
      val reader = compileToSyncReader(Stream.fromReader(left) && Stream.fromReader(right))

      val first  = scala.util.Try(reader.read[Any](null)).failed.toOption.orNull
      val second = scala.util.Try(reader.read[Any](null)).failed.toOption.orNull
      val close  = scala.util.Try(reader.close()).failed.toOption.orNull

      assertTrue(first eq failure, second eq failure, close eq failure, leftPulls == 1)
    },
    test("close-only typed failure is classified as cleanup") {
      val closeFailure = StreamError.source("close")
      val left         = new Reader.SyncReader[Int] {
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A = sentinel
        def close(): Unit                  = throw closeFailure
      }
      runAsync(
        (Stream.fromReader[String, Int](left) && (Stream.empty: Stream[Nothing, Int])).runDrainAsync
      ).either.map { result =>
        val thrown = result.left.toOption.orNull
        assertTrue(thrown eq closeFailure, closeFailure.cleanupFailed)
      }
    }
  )
}

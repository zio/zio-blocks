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
import zio.test._

object BufferSpec extends StreamsBaseSpec {

  def spec = suite("Buffer")(
    correctnessSuite,
    errorHandlingSuite,
    pipelineCompositionSuite,
    resourceSafetySuite,
    earlyTerminationSuite,
    platformDetectionSuite
  ) @@ TestAspect.sequential

  // =========================================================================
  //  Correctness
  // =========================================================================

  private val correctnessSuite = suite("correctness")(
    test("empty stream yields empty chunk") {
      val result = Stream.empty.buffer(8).runCollect
      assertTrue(result == Right(Chunk.empty))
    },
    test("single element") {
      val result = Stream.succeed(42).buffer(4).runCollect
      assertTrue(result == Right(Chunk(42)))
    },
    test("multiple elements preserve order") {
      val result = Stream.range(1, 6).buffer(8).runCollect
      assertTrue(result == Right(Chunk(1, 2, 3, 4, 5)))
    },
    test("ordering preserved for larger range") {
      val result = Stream.range(0, 50).buffer(64).runCollect
      assertTrue(result == Right(Chunk.fromIterable(0 until 50)))
    },
    test("buffer larger than stream") {
      val result = Stream(1, 2, 3).buffer(1024).runCollect
      assertTrue(result == Right(Chunk(1, 2, 3)))
    },
    test("buffer equal to stream size") {
      val result = Stream.range(0, 8).buffer(16).runCollect
      assertTrue(result == Right(Chunk.fromIterable(0 until 8)))
    },
    test("string elements preserve order") {
      val result = Stream("a", "b", "c", "d").buffer(8).runCollect
      assertTrue(result == Right(Chunk("a", "b", "c", "d")))
    }
  )

  // =========================================================================
  //  Error handling
  // =========================================================================

  private val errorHandlingSuite = suite("error handling")(
    test("error-only stream propagates error") {
      val result = Stream.fail("boom").buffer(16).runCollect
      assertTrue(result == Left("boom"))
    },
    test("error after elements propagates error") {
      val result = Stream(1, 2, 3).concat(Stream.fail[String]("mid")).buffer(16).runCollect
      assertTrue(result == Left("mid"))
    },
    test("invalid buffer size 0 throws IllegalArgumentException") {
      assertTrue {
        try { Pipeline.buffer[Int](0); false }
        catch { case _: IllegalArgumentException => true }
      }
    },
    test("invalid buffer size -1 throws IllegalArgumentException") {
      assertTrue {
        try { Pipeline.buffer[Int](-1); false }
        catch { case _: IllegalArgumentException => true }
      }
    },
    test("Stream#buffer(0) throws IllegalArgumentException") {
      assertTrue {
        try { Stream.succeed(1).buffer(0); false }
        catch { case _: IllegalArgumentException => true }
      }
    }
  )

  // =========================================================================
  //  Pipeline composition
  // =========================================================================

  private val pipelineCompositionSuite = suite("pipeline composition")(
    test("via Pipeline.buffer(n)") {
      val result = Stream.range(0, 5).via(Pipeline.buffer(8)).runCollect
      assertTrue(result == Right(Chunk.fromIterable(0 until 5)))
    },
    test("andThen composition: buffer then map") {
      val result =
        Stream.range(0, 5).via(Pipeline.buffer[Int](8).andThen(Pipeline.map[Int, Int](_ * 2))).runCollect
      assertTrue(result == Right(Chunk(0, 2, 4, 6, 8)))
    },
    test("andThen composition: map then buffer") {
      val result =
        Stream.range(0, 5).via(Pipeline.map[Int, Int](_ + 10).andThen(Pipeline.buffer[Int](8))).runCollect
      assertTrue(result == Right(Chunk(10, 11, 12, 13, 14)))
    },
    test("render contains .buffer(n)") {
      assertTrue(Stream.succeed(1).buffer(8).render.contains(".buffer(8)"))
    }
  )

  // =========================================================================
  //  Resource safety
  // =========================================================================

  private val resourceSafetySuite = suite("resource safety")(
    test("ensuring finalizer runs after buffer") {
      val ref = new java.util.concurrent.atomic.AtomicBoolean(false)
      val _   = Stream.succeed(1).ensuring(ref.set(true)).buffer(8).runCollect
      assertTrue(ref.get())
    },
    test("ensuring finalizer runs on error after buffer") {
      val ref = new java.util.concurrent.atomic.AtomicBoolean(false)
      val _   = Stream.fail("err").ensuring(ref.set(true)).buffer(8).runCollect
      assertTrue(ref.get())
    }
  )

  // =========================================================================
  //  Early termination
  // =========================================================================

  private val earlyTerminationSuite = suite("early termination")(
    test("take(5) after buffer") {
      val result = Stream.range(0, 1000).buffer(16).take(5).runCollect
      assertTrue(result == Right(Chunk.fromIterable(0 until 5)))
    },
    test("head after buffer") {
      val result = Stream.range(0, 1000).buffer(16).head
      assertTrue(result == Right(Some(0)))
    }
  )

  // =========================================================================
  //  Platform detection
  // =========================================================================

  private val platformDetectionSuite = suite("platform detection")(
    test("Platform.supportsConcurrency is consistent with TestPlatform") {
      assertTrue(Platform.supportsConcurrency == TestPlatform.isJVM)
    }
  )
}

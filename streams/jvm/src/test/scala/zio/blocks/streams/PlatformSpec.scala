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
    }
  )
}

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

package zio.blocks.streams.internal

import zio.blocks.streams.StreamsBaseSpec
import zio.test._

object ConcurrentShutdownSpec extends StreamsBaseSpec {
  def spec = suite("ConcurrentShutdown")(
    test("join ignores null and the current thread") {
      val interruption = new InterruptedException("existing")
      assertTrue(
        ConcurrentShutdown.join(null, interruption) eq interruption,
        ConcurrentShutdown.join(Thread.currentThread(), null) == null
      )
    },
    test("join records interruption but waits for deterministic termination") {
      val release = new java.util.concurrent.CountDownLatch(1)
      val worker  = new Thread(() => release.await())
      worker.start()
      Thread.currentThread().interrupt()
      val releaser = new Thread(() => release.countDown())
      releaser.start()
      val interruption = ConcurrentShutdown.join(worker, null)
      releaser.join()
      Thread.interrupted()
      assertTrue(interruption.isInstanceOf[InterruptedException], !worker.isAlive)
    },
    test("replay preserves primary failure and interruption") {
      val error        = new RuntimeException("primary")
      val interruption = new InterruptedException("interrupted")
      val thrown       = try { ConcurrentShutdown.replay(error, interruption); null }
      catch { case cause: Throwable => cause }
      val interrupted = Thread.interrupted()
      val replayed    = try { ConcurrentShutdown.replay(null, interruption); null }
      catch { case cause: Throwable => cause }
      val replayedInterrupt = Thread.interrupted()
      ConcurrentShutdown.replay(null, null)
      assertTrue(
        thrown eq error,
        interrupted,
        error.getSuppressed.toList == List(interruption),
        replayed == null,
        replayedInterrupt
      )
    }
  )
}

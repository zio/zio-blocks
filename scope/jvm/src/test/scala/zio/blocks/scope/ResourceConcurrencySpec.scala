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

package zio.blocks.scope

import java.util.concurrent.atomic.AtomicInteger
import zio.test._

/**
 * JVM-only concurrency tests for Resource.shared.
 *
 * These tests use java.util.concurrent classes (CyclicBarrier, CountDownLatch,
 * ConcurrentLinkedQueue) which are not available on Scala.js.
 */
object ResourceConcurrencySpec extends ZIOSpecDefault {

  def spec = suite("Resource concurrency (JVM)")(
    test("Resource.shared is thread-safe under concurrent makes") {
      val counter      = new AtomicInteger(0)
      val closeCounter = new AtomicInteger(0)
      val resource     = Resource.shared[Int] { scope =>
        scope.defer { closeCounter.incrementAndGet(); () }
        counter.incrementAndGet()
      }
      val results = new java.util.concurrent.ConcurrentLinkedQueue[Int]()
      Scope.global.scoped { scope =>
        val barrier = new java.util.concurrent.CyclicBarrier(20)
        val latch   = new java.util.concurrent.CountDownLatch(20)
        (0 until 20).foreach { _ =>
          new Thread(() => {
            barrier.await()
            results.add(resource.make(scope))
            latch.countDown()
          }).start()
        }
        latch.await()
      }
      import scala.jdk.CollectionConverters._
      val allResults = results.asScala.toList
      assertTrue(allResults.forall(_ == 1), counter.get() == 1, closeCounter.get() == 1)
    }
  )
}

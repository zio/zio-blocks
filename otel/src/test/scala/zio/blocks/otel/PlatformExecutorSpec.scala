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

package zio.blocks.otel

import zio.test._

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object PlatformExecutorSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Nothing] = suite("PlatformExecutor")(
    test("create() returns a PlatformExecutor with a usable executor") {
      val pe     = PlatformExecutor.create()
      val latch  = new CountDownLatch(1)
      val future = pe.schedule(0L, 100L, TimeUnit.MILLISECONDS)(new Runnable {
        def run(): Unit = latch.countDown()
      })
      try {
        val ran = latch.await(2, TimeUnit.SECONDS)
        assertTrue(pe.executor != null && ran)
      } finally {
        future.cancel(false)
        pe.shutdown()
      }
    },
    test("schedule executes task and runs periodically") {
      val pe      = PlatformExecutor.create()
      val counter = new AtomicInteger(0)
      val latch   = new CountDownLatch(3)
      val future  = pe.schedule(0L, 50L, TimeUnit.MILLISECONDS)(new Runnable {
        def run(): Unit = {
          counter.incrementAndGet()
          latch.countDown()
        }
      })
      try {
        val awaited = latch.await(2, TimeUnit.SECONDS)
        assertTrue(awaited && counter.get() >= 3)
      } finally {
        future.cancel(false)
        pe.shutdown()
      }
    },
    test("cancelled future stops further executions") {
      val pe      = PlatformExecutor.create()
      val counter = new AtomicInteger(0)
      val started = new CountDownLatch(1)
      val future  = pe.schedule(0L, 50L, TimeUnit.MILLISECONDS)(new Runnable {
        def run(): Unit = {
          counter.incrementAndGet()
          started.countDown()
        }
      })
      try {
        val ok = started.await(2, TimeUnit.SECONDS)
        future.cancel(false)
        val countAtCancel = counter.get()
        Thread.sleep(200)
        val countAfterWait = counter.get()
        assertTrue(ok && countAfterWait <= countAtCancel + 1)
      } finally pe.shutdown()
    },
    test("hasLoom is consistent with ContextStorage.hasLoom") {
      assertTrue(PlatformExecutor.hasLoom == ContextStorage.hasLoom)
    }
  ) @@ TestAspect.sequential
}

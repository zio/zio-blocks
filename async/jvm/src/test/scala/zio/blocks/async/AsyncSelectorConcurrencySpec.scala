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

package zio.blocks.async

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import zio.test._

object AsyncSelectorConcurrencySpec extends ZIOSpecDefault {
  def spec = suite("AsyncSelectorConcurrencySpec")(
    test("shutdown signals a reserved blocking poll and cancels its distinct replacement exactly once") {
      val entered                  = new CountDownLatch(1)
      val cancelled                = new CountDownLatch(1)
      val predecessorCancellations = new AtomicInteger
      val replacementCancellations = new AtomicInteger
      val replacement              = new Pollable[Int] {
        def poll(onComplete: Runnable): Async[Int]                   = this
        override private[async] def cancelWithCleanup(): Async[Unit] = {
          replacementCancellations.incrementAndGet()
          Async.succeed(())
        }
      }
      val predecessor = new Pollable[Int] {
        def poll(onComplete: Runnable): Async[Int] = {
          entered.countDown()
          cancelled.await()
          replacement
        }
        override private[async] def cancelWithCleanup(): Async[Unit] = {
          predecessorCancellations.incrementAndGet()
          cancelled.countDown()
          Async.succeed(())
        }
      }
      val selector  = Async.selector(Vector[Async[Int]](predecessor))
      val selection = selector.select.asInstanceOf[Pollable[(Int, Int)]]
      val observed  = new AtomicReference[Async[(Int, Int)]]
      val polling   = new Thread(() => observed.set(selection.poll(AsyncTestSupport.noopRunnable)))
      polling.start()
      val admitted = entered.await(5, TimeUnit.SECONDS)
      val closing  = selector.shutdown.start
      polling.join(5000L)
      for {
        _ <- AsyncTestSupport.runAsync(closing)
      } yield assertTrue(
        admitted,
        !polling.isAlive,
        observed.get().isInstanceOf[Failure],
        predecessorCancellations.get() == 1,
        replacementCancellations.get() == 1
      )
    }
  ) @@ TestAspect.timeout(zio.Duration.fromSeconds(10))
}

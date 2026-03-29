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

import java.util.concurrent._

final class PlatformExecutor private (val executor: ScheduledExecutorService) {

  def schedule(initialDelay: Long, period: Long, unit: TimeUnit)(task: Runnable): ScheduledFuture[_] =
    executor.scheduleAtFixedRate(task, initialDelay, period, unit)

  def shutdown(): Unit =
    executor.shutdown()
}

object PlatformExecutor {

  def create(): PlatformExecutor =
    new PlatformExecutor(
      Executors.newScheduledThreadPool(1, Thread.ofVirtual().name("otel-", 0L).factory())
    )
}

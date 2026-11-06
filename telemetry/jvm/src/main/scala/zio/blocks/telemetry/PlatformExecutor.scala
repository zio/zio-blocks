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

package zio.blocks.telemetry

import java.util.concurrent._

/**
 * A scheduler that runs periodic tasks on one virtual thread, named `otel-0`.
 * The OTLP trace and log exporters of the `otel` module run their periodic
 * exports on a `PlatformExecutor`; the metric exporter does not use one. JVM
 * only.
 */
final class PlatformExecutor private (val executor: ScheduledExecutorService) {

  /**
   * Runs `task` at a fixed rate, every `period` after `initialDelay`, and
   * returns its future; cancel the future to stop it. An exception from `task`
   * stops its later runs.
   */
  def schedule(initialDelay: Long, period: Long, unit: TimeUnit)(task: Runnable): ScheduledFuture[_] =
    executor.scheduleAtFixedRate(task, initialDelay, period, unit)

  /** Stops the scheduler: periodic tasks do not run again. */
  def shutdown(): Unit =
    executor.shutdown()
}

/** Creates [[PlatformExecutor]]s. */
object PlatformExecutor {

  /** Returns a new scheduler with its own virtual thread. */
  def create(): PlatformExecutor =
    new PlatformExecutor(
      Executors.newScheduledThreadPool(1, Thread.ofVirtual().name("otel-", 0L).factory())
    )
}

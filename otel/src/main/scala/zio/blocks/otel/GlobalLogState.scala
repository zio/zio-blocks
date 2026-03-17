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

import java.util.concurrent.atomic.AtomicReference

final class LogState(
  val logger: Logger,
  val minSeverity: Int
)

object GlobalLogState {
  private val ref: AtomicReference[LogState] = new AtomicReference[LogState](null)

  def get(): LogState = ref.get()

  def set(state: LogState): Unit = ref.set(state)

  def install(logger: Logger, minSeverity: Severity = Severity.Trace): Unit =
    ref.set(new LogState(logger, minSeverity.number))

  def uninstall(): Unit = ref.set(null)
}

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
  val minSeverity: Int,
  val levelOverrides: Map[String, Int]
) {
  def effectiveLevel(className: String): Int = {
    if (levelOverrides.isEmpty) return minSeverity
    var bestLen   = -1
    var bestLevel = minSeverity
    val iter      = levelOverrides.iterator
    while (iter.hasNext) {
      val (prefix, level) = iter.next()
      if (className.startsWith(prefix) && prefix.length > bestLen) {
        bestLen = prefix.length
        bestLevel = level
      }
    }
    bestLevel
  }
}

object GlobalLogState {
  private val ref: AtomicReference[LogState] = new AtomicReference[LogState](null)

  def get(): LogState = ref.get()

  def set(state: LogState): Unit = ref.set(state)

  def install(logger: Logger, minSeverity: Severity = Severity.Trace): Unit =
    ref.set(new LogState(logger, minSeverity.number, Map.empty))

  def setLevel(prefix: String, severity: Severity): Unit = {
    var current = ref.get()
    while (current != null) {
      val updated =
        new LogState(current.logger, current.minSeverity, current.levelOverrides + (prefix -> severity.number))
      if (ref.compareAndSet(current, updated)) return
      current = ref.get()
    }
  }

  def clearLevel(prefix: String): Unit = {
    var current = ref.get()
    while (current != null) {
      val updated = new LogState(current.logger, current.minSeverity, current.levelOverrides - prefix)
      if (ref.compareAndSet(current, updated)) return
      current = ref.get()
    }
  }

  def clearAllLevels(): Unit = {
    var current = ref.get()
    while (current != null) {
      val updated = new LogState(current.logger, current.minSeverity, Map.empty)
      if (ref.compareAndSet(current, updated)) return
      current = ref.get()
    }
  }

  def uninstall(): Unit = ref.set(null)
}

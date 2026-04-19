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

import java.util.concurrent.atomic.AtomicReference

final class LogState(
  val logger: Logger,
  val minSeverity: Int,
  val levelOverridesMap: Map[String, Int],
  val levelOverrideKeys: Array[String],
  val levelOverrideValues: Array[Int]
) {
  def effectiveLevel(className: String): Int = {
    if (levelOverrideKeys.length == 0) return minSeverity
    var bestLen   = -1
    var bestLevel = minSeverity
    var i         = 0
    while (i < levelOverrideKeys.length) {
      val prefix = levelOverrideKeys(i)
      if (className.startsWith(prefix) && prefix.length > bestLen) {
        bestLen = prefix.length
        bestLevel = levelOverrideValues(i)
      }
      i += 1
    }
    bestLevel
  }
}

object LogState {
  def apply(logger: Logger, minSeverity: Int, overrides: Map[String, Int]): LogState = {
    val keys   = overrides.keysIterator.toArray
    val values = overrides.valuesIterator.toArray
    new LogState(logger, minSeverity, overrides, keys, values)
  }
}

object GlobalLogState {

  @volatile private[telemetry] var globalMinLevel: Int = 1

  private val ref: AtomicReference[LogState] = new AtomicReference[LogState](null)

  private lazy val defaultState: LogState = {
    val processor = new ConsoleLogRecordProcessor
    val cs        = ContextStorage.create[Option[SpanContext]](None)
    val logger    = new Logger(
      InstrumentationScope(name = "default"),
      Resource.empty,
      Array(processor),
      cs
    )
    LogState(logger, Severity.Trace.number, Map.empty)
  }

  def get(): LogState = {
    val state = ref.get()
    if (state != null) state else defaultState
  }

  def set(state: LogState): Unit = {
    ref.set(state)
    updateGlobalMinLevel()
  }

  def install(logger: Logger, minSeverity: Severity = Severity.Trace): Unit = {
    ref.set(LogState(logger, minSeverity.number, Map.empty))
    updateGlobalMinLevel()
  }

  def setLevel(prefix: String, severity: Severity): Unit = {
    var current = ref.get()
    while (current != null) {
      val updated =
        LogState(current.logger, current.minSeverity, current.levelOverridesMap + (prefix -> severity.number))
      if (ref.compareAndSet(current, updated)) {
        updateGlobalMinLevel()
        return
      }
      current = ref.get()
    }
  }

  def clearLevel(prefix: String): Unit = {
    var current = ref.get()
    while (current != null) {
      val updated = LogState(current.logger, current.minSeverity, current.levelOverridesMap - prefix)
      if (ref.compareAndSet(current, updated)) {
        updateGlobalMinLevel()
        return
      }
      current = ref.get()
    }
  }

  def clearAllLevels(): Unit = {
    var current = ref.get()
    while (current != null) {
      val updated = LogState(current.logger, current.minSeverity, Map.empty)
      if (ref.compareAndSet(current, updated)) {
        updateGlobalMinLevel()
        return
      }
      current = ref.get()
    }
  }

  def uninstall(): Unit = {
    ref.set(null)
    globalMinLevel = 1
  }

  private def updateGlobalMinLevel(): Unit = {
    val state = ref.get()
    if (state == null) { globalMinLevel = 1; return }
    var min = state.minSeverity
    var i   = 0
    while (i < state.levelOverrideValues.length) {
      val v = state.levelOverrideValues(i)
      if (v < min) min = v
      i += 1
    }
    globalMinLevel = min
  }
}

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

import scala.util.control.NonFatal

/**
 * Turns the raw values of a `log.*` call into output; each [[Logger]] picks one
 * when it is created, from its processors.
 */
private[telemetry] trait LogEmitter {

  /** Emits one log call's values and clears `builder`. */
  def emit(
    timestampNanos: Long,
    severity: Severity,
    severityText: String,
    body: String,
    builder: Attributes.AttributesBuilder,
    traceIdHi: Long,
    traceIdLo: Long,
    spanId: Long,
    traceFlags: Byte,
    resource: Resource,
    instrumentationScope: InstrumentationScope,
    throwable: Option[Throwable]
  ): Unit
}

/**
 * Builds a [[LogRecord]] from the raw values and passes it to every processor,
 * unless its severity is below `processorMinLevel`.
 */
private[telemetry] final class StandardLogEmitter(
  processors: Array[LogRecordProcessor],
  processorMinLevel: Int
) extends LogEmitter {

  def emit(
    timestampNanos: Long,
    severity: Severity,
    severityText: String,
    body: String,
    builder: Attributes.AttributesBuilder,
    traceIdHi: Long,
    traceIdLo: Long,
    spanId: Long,
    traceFlags: Byte,
    resource: Resource,
    instrumentationScope: InstrumentationScope,
    throwable: Option[Throwable]
  ): Unit =
    if (severity.number >= processorMinLevel) {
      val record = LogRecord(
        timestampNanos = timestampNanos,
        observedTimestampNanos = timestampNanos,
        severity = severity,
        severityText = severityText,
        body = LogMessage(body),
        attributes = builder.build,
        traceIdHi = traceIdHi,
        traceIdLo = traceIdLo,
        spanId = spanId,
        traceFlags = traceFlags,
        resource = resource,
        instrumentationScope = instrumentationScope,
        throwable = throwable
      )
      builder.clear()
      var i = 0
      while (i < processors.length) {
        try {
          processors(i).onEmit(record)
        } catch {
          case NonFatal(e) =>
            System.err.println("[zio-blocks-telemetry] logging error: " + e.getMessage)
        }
        i += 1
      }
    } else {
      builder.clear()
    }
}

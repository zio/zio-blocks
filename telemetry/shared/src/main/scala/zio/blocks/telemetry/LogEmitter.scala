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

/**
 * Strategy for emitting log records. The Logger dispatches to the emitter
 * chosen at construction time based on processor capabilities.
 *
 * The macro generates code that passes raw field values to Logger.emitRaw(),
 * which delegates to the emitter. The emitter decides the representation:
 *   - StandardLogEmitter builds an immutable LogRecord and passes to processors
 *   - Future emitters (e.g., ZeroCopyLogEmitter) can read the builder's raw
 *     arrays directly for zero-allocation export
 */
private[telemetry] trait LogEmitter {
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
 * Standard emitter: builds an immutable LogRecord from raw values, then
 * dispatches to all processors via onEmit.
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
      try {
        var i = 0
        while (i < processors.length) {
          processors(i).onEmit(record)
          i += 1
        }
      } catch {
        case e: Throwable =>
          System.err.println("[zio-blocks-telemetry] logging error: " + e.getMessage)
      }
    } else {
      builder.clear()
    }
}

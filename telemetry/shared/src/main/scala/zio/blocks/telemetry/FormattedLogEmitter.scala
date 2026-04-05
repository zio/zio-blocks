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
 * Composes a LogFormatter and LogWriter into a LogEmitter. Allocates a new
 * StringBuilder per call (~32 bytes, tiny vs I/O cost).
 *
 * Usage: new FormattedLogEmitter(TextLogFormatter, StdoutWriter) new
 * FormattedLogEmitter(JsonLogFormatter, NoopWriter)
 */
private[telemetry] final class FormattedLogEmitter(
  formatter: LogFormatter,
  writer: LogWriter
) extends LogEmitter {

  override def emit(
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
  ): Unit = {
    val sb = new StringBuilder(256)
    formatter.format(
      sb,
      timestampNanos,
      severity,
      severityText,
      body,
      builder,
      traceIdHi,
      traceIdLo,
      spanId,
      traceFlags,
      throwable
    )
    builder.clear()
    try writer.write(sb)
    catch { case e: Throwable => System.err.println("[zio-blocks-telemetry] write error: " + e.getMessage) }
  }
}

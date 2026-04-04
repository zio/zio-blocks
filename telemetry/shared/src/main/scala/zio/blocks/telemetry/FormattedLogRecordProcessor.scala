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
 * Processor that formats LogRecords using a LogFormatter and writes them via a
 * LogWriter. Used by log.writer() to add formatted outputs.
 */
private[telemetry] final class FormattedLogRecordProcessor(
  formatter: LogFormatter,
  writer: LogWriter
) extends LogRecordProcessor {

  override def onEmit(logRecord: LogRecord): Unit = {
    val sb = new StringBuilder(256)
    formatter.formatRecord(sb, logRecord)
    try writer.write(sb)
    catch { case e: Throwable => System.err.println("[zio-blocks-telemetry] write error: " + e.getMessage) }
  }

  override def shutdown(): Unit =
    try writer.close()
    catch { case _: Throwable => () }

  override def forceFlush(): Unit =
    try writer.flush()
    catch { case _: Throwable => () }
}

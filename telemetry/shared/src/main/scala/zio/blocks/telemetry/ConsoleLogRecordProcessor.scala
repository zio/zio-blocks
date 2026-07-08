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
  * Console log processor — triggers the fast ConsoleLogEmitter in Logger. Uses a
  * format that writes directly from raw values, bypassing LogRecord. Format:
  * "2026-03-31T17:30:00.123Z INFO [MyClass.method:42] message {key=val}"
  *
  * When a LogRecord is passed via emit() (e.g. via bridges/adapters), delegates to
  * StdoutLogRecordProcessor to ensure full formatting with attributes and stacktrace.
  */
class ConsoleLogRecordProcessor extends LogRecordProcessor {

  private val delegate = new StdoutLogRecordProcessor()

  override def onEmit(logRecord: LogRecord): Unit =
    delegate.onEmit(logRecord)

  override def shutdown(): Unit   = delegate.shutdown()
  override def forceFlush(): Unit = delegate.forceFlush()
}

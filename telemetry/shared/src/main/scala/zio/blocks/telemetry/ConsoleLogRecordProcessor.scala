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
 * Prints each record to standard output as one text line, such as
 * `2026-11-06T18:43:36.815Z INFO  [Checkout.placeOrder:42] order placed {orderId="ord-123"}`,
 * followed by the stack trace when the record carries an exception. It is the
 * default output of the global [[log]].
 *
 * When it is a logger's only processor, `log.*` calls skip `onEmit` and format
 * the line with [[TextLogFormatter]] themselves, so a subclass that overrides
 * `onEmit` does not see them. On that path, a known bug prints the full class
 * name and two numbers in the bracket when the class is in a package.
 */
class ConsoleLogRecordProcessor extends LogRecordProcessor {

  private val delegate = new StdoutLogRecordProcessor()

  override def onEmit(logRecord: LogRecord): Unit =
    delegate.onEmit(logRecord)

  override def shutdown(): Unit   = delegate.shutdown()
  override def forceFlush(): Unit = delegate.forceFlush()
}

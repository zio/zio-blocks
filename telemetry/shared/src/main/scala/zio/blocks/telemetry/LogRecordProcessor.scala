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
 * Receives every [[LogRecord]] that a [[Logger]] lets through, to print, export
 * or drop it. Register one with `log.addProcessor` or
 * `LoggerProviderBuilder#addLogRecordProcessor`.
 *
 * `onEmit` runs on the thread that logged, before the log call returns, so a
 * processor that ships records over the network should buffer them and send
 * them from another thread.
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/logging/log-record-processor/ LogRecordProcessor]]
 */
trait LogRecordProcessor extends AutoCloseable {

  /**
   * Handles one record. An exception it throws is caught and printed to
   * standard error, and the other processors still receive the record.
   */
  def onEmit(logRecord: LogRecord): Unit

  /**
   * Flushes what the processor holds and releases its resources.
   * `LoggerProvider#shutdown` and `log.removeAll` call it.
   */
  def shutdown(): Unit

  /** Sends any buffered records now. */
  def forceFlush(): Unit
  override def close(): Unit = shutdown()

  /**
   * The lowest `Severity#number` this processor wants; 1, every record, by
   * default.
   *
   * A logger builds no record below the lowest `minimumLevel` of its
   * processors, but a record at or above it reaches every processor, so a
   * processor with a higher `minimumLevel` must check `record.severity` itself.
   */
  def minimumLevel: Int = 1
}

/** Holds the built-in no-op processor. */
object LogRecordProcessor {

  /** Accepts every record and discards it. */
  val noop: LogRecordProcessor = new LogRecordProcessor {
    def onEmit(logRecord: LogRecord): Unit = ()
    def shutdown(): Unit                   = ()
    def forceFlush(): Unit                 = ()
  }
}

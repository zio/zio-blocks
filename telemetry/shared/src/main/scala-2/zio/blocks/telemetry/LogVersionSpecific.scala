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

import scala.language.experimental.macros

private[telemetry] trait LogVersionSpecific { self: log.type =>

  /**
   * Logs `message` at `Severity.Trace`; each of `enrichments` adds an attribute
   * (`"key" -> value`), an exception (a `Throwable`) or a new level (a
   * [[Severity]]), as [[LogEnrichment]] lists.
   */
  def trace(message: String, enrichments: Any*): Unit = macro LogMacros.traceImpl

  /**
   * Logs `message` at `Severity.Debug`; each of `enrichments` adds an attribute
   * (`"key" -> value`), an exception (a `Throwable`) or a new level (a
   * [[Severity]]), as [[LogEnrichment]] lists.
   */
  def debug(message: String, enrichments: Any*): Unit = macro LogMacros.debugImpl

  /**
   * Logs `message` at `Severity.Info`; each of `enrichments` adds an attribute
   * (`"key" -> value`), an exception (a `Throwable`) or a new level (a
   * [[Severity]]), as [[LogEnrichment]] lists.
   *
   * {{{
   * // scala-3-only
   * import scala.collection.mutable.ListBuffer
   *
   * val records = ListBuffer.empty[LogRecord]
   * val inMemory = new LogRecordProcessor {
   *   def onEmit(record: LogRecord): Unit = records += record
   *   def shutdown(): Unit = ()
   *   def forceFlush(): Unit = ()
   * }
   * log.install(LoggerProvider.builder.addLogRecordProcessor(inMemory).build().get("orders"))
   *
   * log.info("order shipped", "orderId" -> "ord-123", "items" -> 3, "express" -> true)
   *
   * records.head.attributes.get(AttributeKey.long("items")) // Some(3)
   * records.head.attributes.get(AttributeKey.boolean("express")) // Some(true)
   * }}}
   */
  def info(message: String, enrichments: Any*): Unit = macro LogMacros.infoImpl

  /**
   * Logs `message` at `Severity.Warn`; each of `enrichments` adds an attribute
   * (`"key" -> value`), an exception (a `Throwable`) or a new level (a
   * [[Severity]]), as [[LogEnrichment]] lists.
   */
  def warn(message: String, enrichments: Any*): Unit = macro LogMacros.warnImpl

  /**
   * Logs `message` at `Severity.Error`; each of `enrichments` adds an attribute
   * (`"key" -> value`), an exception (a `Throwable`) or a new level (a
   * [[Severity]]), as [[LogEnrichment]] lists.
   *
   * A `Throwable` adds the `exception.type` and `exception.message` attributes
   * and keeps the exception for its stack trace. On Scala 3, an argument whose
   * static type is a subclass of `Throwable`, such as `java.io.IOException`,
   * fails to compile with "No LogEnrichment instance found", a known bug:
   * ascribe it as `Throwable`.
   *
   * {{{
   * // scala-3-only
   * import scala.collection.mutable.ListBuffer
   *
   * val records = ListBuffer.empty[LogRecord]
   * val inMemory = new LogRecordProcessor {
   *   def onEmit(record: LogRecord): Unit = records += record
   *   def shutdown(): Unit = ()
   *   def forceFlush(): Unit = ()
   * }
   * log.install(LoggerProvider.builder.addLogRecordProcessor(inMemory).build().get("payments"))
   *
   * val failure: Throwable = new java.io.IOException("connection reset")
   * log.error("payment failed", failure, "orderId" -> "ord-123", "attempt" -> 2)
   *
   * records.head.attributes.get(AttributeKey.string("exception.type")) // Some(java.io.IOException)
   * records.head.throwable // Some(java.io.IOException: connection reset)
   * }}}
   */
  def error(message: String, enrichments: Any*): Unit = macro LogMacros.errorImpl

  /**
   * Logs `message` at `Severity.Fatal`; each of `enrichments` adds an attribute
   * (`"key" -> value`), an exception (a `Throwable`) or a new level (a
   * [[Severity]]), as [[LogEnrichment]] lists.
   */
  def fatal(message: String, enrichments: Any*): Unit = macro LogMacros.fatalImpl

  /**
   * Logs `message` at `Severity.Trace` on every `every`-th call from this call
   * site (the `every`-th, the `2 * every`-th, and so on), and never when
   * `every` is 0 or less.
   *
   * Call sites share 4096 counters, chosen by a hash of the file and line, so
   * two sites can share a counter and count each other's calls.
   */
  def traceEvery(every: Int, message: String, enrichments: Any*): Unit = macro LogMacros.traceEveryImpl

  /**
   * Logs `message` at `Severity.Debug` on every `every`-th call from this call
   * site (the `every`-th, the `2 * every`-th, and so on), and never when
   * `every` is 0 or less.
   *
   * Call sites share 4096 counters, chosen by a hash of the file and line, so
   * two sites can share a counter and count each other's calls.
   */
  def debugEvery(every: Int, message: String, enrichments: Any*): Unit = macro LogMacros.debugEveryImpl

  /**
   * Logs `message` at `Severity.Info` on every `every`-th call from this call
   * site (the `every`-th, the `2 * every`-th, and so on), and never when
   * `every` is 0 or less.
   *
   * Call sites share 4096 counters, chosen by a hash of the file and line, so
   * two sites can share a counter and count each other's calls.
   */
  def infoEvery(every: Int, message: String, enrichments: Any*): Unit = macro LogMacros.infoEveryImpl

  /**
   * Logs `message` at `Severity.Warn` on every `every`-th call from this call
   * site (the `every`-th, the `2 * every`-th, and so on), and never when
   * `every` is 0 or less.
   *
   * Call sites share 4096 counters, chosen by a hash of the file and line, so
   * two sites can share a counter and count each other's calls.
   *
   * {{{
   * // scala-3-only
   * import scala.collection.mutable.ListBuffer
   *
   * val records = ListBuffer.empty[LogRecord]
   * val inMemory = new LogRecordProcessor {
   *   def onEmit(record: LogRecord): Unit = records += record
   *   def shutdown(): Unit = ()
   *   def forceFlush(): Unit = ()
   * }
   * log.install(LoggerProvider.builder.addLogRecordProcessor(inMemory).build().get("upstream"))
   *
   * (1 to 10).foreach { attempt =>
   *   log.warnEvery(5, "retrying upstream call", "attempt" -> attempt)
   * }
   *
   * records.map(_.attributes.get(AttributeKey.long("attempt")).getOrElse(0L)) // ListBuffer(5, 10)
   * }}}
   */
  def warnEvery(every: Int, message: String, enrichments: Any*): Unit = macro LogMacros.warnEveryImpl

  /**
   * Logs `message` at `Severity.Error` on every `every`-th call from this call
   * site (the `every`-th, the `2 * every`-th, and so on), and never when
   * `every` is 0 or less.
   *
   * Call sites share 4096 counters, chosen by a hash of the file and line, so
   * two sites can share a counter and count each other's calls.
   */
  def errorEvery(every: Int, message: String, enrichments: Any*): Unit = macro LogMacros.errorEveryImpl

  /**
   * Logs `message` at `Severity.Fatal` on every `every`-th call from this call
   * site (the `every`-th, the `2 * every`-th, and so on), and never when
   * `every` is 0 or less.
   *
   * Call sites share 4096 counters, chosen by a hash of the file and line, so
   * two sites can share a counter and count each other's calls.
   */
  def fatalEvery(every: Int, message: String, enrichments: Any*): Unit = macro LogMacros.fatalEveryImpl

  /**
   * Logs `message` at `Severity.Trace` at most once per `intervalMillis`
   * milliseconds from this call site; the first call logs.
   *
   * Call sites share 4096 timestamps, chosen by a hash of the file and line, so
   * two sites can share one and log less often.
   */
  def traceAtMost(intervalMillis: Long, message: String, enrichments: Any*): Unit = macro LogMacros.traceAtMostImpl

  /**
   * Logs `message` at `Severity.Debug` at most once per `intervalMillis`
   * milliseconds from this call site; the first call logs.
   *
   * Call sites share 4096 timestamps, chosen by a hash of the file and line, so
   * two sites can share one and log less often.
   */
  def debugAtMost(intervalMillis: Long, message: String, enrichments: Any*): Unit = macro LogMacros.debugAtMostImpl

  /**
   * Logs `message` at `Severity.Info` at most once per `intervalMillis`
   * milliseconds from this call site; the first call logs.
   *
   * Call sites share 4096 timestamps, chosen by a hash of the file and line, so
   * two sites can share one and log less often.
   */
  def infoAtMost(intervalMillis: Long, message: String, enrichments: Any*): Unit = macro LogMacros.infoAtMostImpl

  /**
   * Logs `message` at `Severity.Warn` at most once per `intervalMillis`
   * milliseconds from this call site; the first call logs.
   *
   * Call sites share 4096 timestamps, chosen by a hash of the file and line, so
   * two sites can share one and log less often.
   */
  def warnAtMost(intervalMillis: Long, message: String, enrichments: Any*): Unit = macro LogMacros.warnAtMostImpl

  /**
   * Logs `message` at `Severity.Error` at most once per `intervalMillis`
   * milliseconds from this call site; the first call logs.
   *
   * Call sites share 4096 timestamps, chosen by a hash of the file and line, so
   * two sites can share one and log less often.
   */
  def errorAtMost(intervalMillis: Long, message: String, enrichments: Any*): Unit = macro LogMacros.errorAtMostImpl

  /**
   * Logs `message` at `Severity.Fatal` at most once per `intervalMillis`
   * milliseconds from this call site; the first call logs.
   *
   * Call sites share 4096 timestamps, chosen by a hash of the file and line, so
   * two sites can share one and log less often.
   */
  def fatalAtMost(intervalMillis: Long, message: String, enrichments: Any*): Unit = macro LogMacros.fatalAtMostImpl
}

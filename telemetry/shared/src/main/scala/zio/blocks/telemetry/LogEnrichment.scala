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
 * Lets a `log.*` call take a value of type `A` after the message and fold it
 * into the record. The call fails to compile when an argument's type has no
 * instance.
 *
 * The macro handles `String`, `Throwable`, [[Severity]], [[Attributes]] and
 * `(String, String)`, `(String, Long)`, `(String, Int)`, `(String, Double)` and
 * `(String, Boolean)` pairs directly, and calls `enrich` for any other type.
 * A [[Severity]] changes the recorded level but not the one the call was
 * filtered at.
 *
 * {{{
 * // scala-3-only
 * import scala.collection.mutable.ListBuffer
 *
 * final case class RequestId(value: String)
 *
 * implicit val requestIdEnrichment: LogEnrichment[RequestId] = new LogEnrichment[RequestId] {
 *   def enrich(record: LogRecord, value: RequestId): LogRecord =
 *     record.copy(attributes = record.attributes ++ Attributes.of(AttributeKey.string("request.id"), value.value))
 * }
 *
 * val records = ListBuffer.empty[LogRecord]
 * val inMemory = new LogRecordProcessor {
 *   def onEmit(record: LogRecord): Unit = records += record
 *   def shutdown(): Unit = ()
 *   def forceFlush(): Unit = ()
 * }
 * log.install(LoggerProvider.builder.addLogRecordProcessor(inMemory).build().get("api"))
 *
 * log.info("handling request", RequestId("req-001"))
 *
 * records.head.attributes.get(AttributeKey.string("request.id")) // Some(req-001)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/logging/log-enrichment/ LogEnrichment]]
 */
trait LogEnrichment[A] {

  /** Returns a copy of `record` with `value` folded in. */
  def enrich(record: LogRecord, value: A): LogRecord
}

/**
 * The built-in instances; the macro handles their types directly, so they run
 * only when an instance is called by hand, or on Scala 3 for an argument of
 * static type `Throwable`.
 */
object LogEnrichment {

  /** Replaces the message with the string. */
  implicit val stringEnrichment: LogEnrichment[String] = new LogEnrichment[String] {
    def enrich(record: LogRecord, value: String): LogRecord =
      record.copy(body = LogMessage.Simple(value))
  }

  /**
   * Adds the `exception.type` and `exception.message` attributes and keeps the
   * exception for its stack trace.
   */
  implicit val throwableEnrichment: LogEnrichment[Throwable] = new LogEnrichment[Throwable] {
    def enrich(record: LogRecord, value: Throwable): LogRecord = {
      val builder = Attributes.builder
      builder.put("exception.type", value.getClass.getName)
      builder.put("exception.message", if (value.getMessage != null) value.getMessage else "")
      record.copy(
        attributes = record.attributes ++ builder.build,
        throwable = Some(value)
      )
    }
  }

  /** Adds the pair as a string attribute. */
  implicit val stringStringEnrichment: LogEnrichment[(String, String)] =
    new LogEnrichment[(String, String)] {
      def enrich(record: LogRecord, value: (String, String)): LogRecord =
        record.copy(attributes = record.attributes ++ Attributes.of(AttributeKey.string(value._1), value._2))
    }

  /** Adds the pair as a long attribute. */
  implicit val stringLongEnrichment: LogEnrichment[(String, Long)] =
    new LogEnrichment[(String, Long)] {
      def enrich(record: LogRecord, value: (String, Long)): LogRecord =
        record.copy(attributes = record.attributes ++ Attributes.of(AttributeKey.long(value._1), value._2))
    }

  /** Adds the pair as a double attribute. */
  implicit val stringDoubleEnrichment: LogEnrichment[(String, Double)] =
    new LogEnrichment[(String, Double)] {
      def enrich(record: LogRecord, value: (String, Double)): LogRecord =
        record.copy(attributes = record.attributes ++ Attributes.of(AttributeKey.double(value._1), value._2))
    }

  /** Adds the pair as a boolean attribute. */
  implicit val stringBooleanEnrichment: LogEnrichment[(String, Boolean)] =
    new LogEnrichment[(String, Boolean)] {
      def enrich(record: LogRecord, value: (String, Boolean)): LogRecord =
        record.copy(attributes = record.attributes ++ Attributes.of(AttributeKey.boolean(value._1), value._2))
    }

  /** Adds the pair as a long attribute. */
  implicit val stringIntEnrichment: LogEnrichment[(String, Int)] =
    new LogEnrichment[(String, Int)] {
      def enrich(record: LogRecord, value: (String, Int)): LogRecord =
        record.copy(attributes = record.attributes ++ Attributes.of(AttributeKey.long(value._1), value._2.toLong))
    }

  /** Appends the attributes to the record's attributes. */
  implicit val attributesEnrichment: LogEnrichment[Attributes] = new LogEnrichment[Attributes] {
    def enrich(record: LogRecord, value: Attributes): LogRecord =
      record.copy(attributes = record.attributes ++ value)
  }

  /** Replaces the record's severity and severity text. */
  implicit val severityEnrichment: LogEnrichment[Severity] = new LogEnrichment[Severity] {
    def enrich(record: LogRecord, value: Severity): LogRecord =
      record.copy(severity = value, severityText = value.text)
  }
}

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

trait LogEnrichment[A] {
  def enrich(record: LogRecord, value: A): LogRecord
}

object LogEnrichment {

  implicit val stringEnrichment: LogEnrichment[String] = new LogEnrichment[String] {
    def enrich(record: LogRecord, value: String): LogRecord =
      record.copy(body = LogMessage.Simple(value))
  }

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

  implicit val stringStringEnrichment: LogEnrichment[(String, String)] =
    new LogEnrichment[(String, String)] {
      def enrich(record: LogRecord, value: (String, String)): LogRecord =
        record.copy(attributes = record.attributes ++ Attributes.of(AttributeKey.string(value._1), value._2))
    }

  implicit val stringLongEnrichment: LogEnrichment[(String, Long)] =
    new LogEnrichment[(String, Long)] {
      def enrich(record: LogRecord, value: (String, Long)): LogRecord =
        record.copy(attributes = record.attributes ++ Attributes.of(AttributeKey.long(value._1), value._2))
    }

  implicit val stringDoubleEnrichment: LogEnrichment[(String, Double)] =
    new LogEnrichment[(String, Double)] {
      def enrich(record: LogRecord, value: (String, Double)): LogRecord =
        record.copy(attributes = record.attributes ++ Attributes.of(AttributeKey.double(value._1), value._2))
    }

  implicit val stringBooleanEnrichment: LogEnrichment[(String, Boolean)] =
    new LogEnrichment[(String, Boolean)] {
      def enrich(record: LogRecord, value: (String, Boolean)): LogRecord =
        record.copy(attributes = record.attributes ++ Attributes.of(AttributeKey.boolean(value._1), value._2))
    }

  implicit val stringIntEnrichment: LogEnrichment[(String, Int)] =
    new LogEnrichment[(String, Int)] {
      def enrich(record: LogRecord, value: (String, Int)): LogRecord =
        record.copy(attributes = record.attributes ++ Attributes.of(AttributeKey.long(value._1), value._2.toLong))
    }

  implicit val attributesEnrichment: LogEnrichment[Attributes] = new LogEnrichment[Attributes] {
    def enrich(record: LogRecord, value: Attributes): LogRecord =
      record.copy(attributes = record.attributes ++ value)
  }

  implicit val severityEnrichment: LogEnrichment[Severity] = new LogEnrichment[Severity] {
    def enrich(record: LogRecord, value: Severity): LogRecord =
      record.copy(severity = value, severityText = value.text)
  }
}

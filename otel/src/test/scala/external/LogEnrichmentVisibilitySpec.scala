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

package external

import zio.blocks.telemetry._
import zio.test._

import scala.collection.mutable.ArrayBuffer

/**
 * Proves the record's operation methods (`withBody`, `withSeverity`,
 * `withThrowable`, `withAttribute`) plus the read API (`sourceFile`,
 * `sourceFunction`, `sourceLine`, `attribute`, `foreachAttribute`) are
 * genuinely public: this suite lives outside `zio.blocks.telemetry`, so any
 * `private[telemetry]` member used here fails compilation. Custom
 * `LogEnrichment` instances defined here must preserve source and user
 * attributes while updating body, severity, and throwable.
 */
object LogEnrichmentVisibilitySpec extends ZIOSpecDefault {

  final case class Shout(text: String)
  final case class Escalate(severity: Severity)
  final case class Attach(throwable: Throwable)

  implicit val shoutEnrichment: LogEnrichment[Shout] = new LogEnrichment[Shout] {
    def enrich(record: LogRecord, value: Shout): LogRecord =
      record.withBody(LogMessage.Simple(value.text))
  }

  implicit val escalateEnrichment: LogEnrichment[Escalate] = new LogEnrichment[Escalate] {
    def enrich(record: LogRecord, value: Escalate): LogRecord =
      record.withSeverity(value.severity)
  }

  implicit val attachEnrichment: LogEnrichment[Attach] = new LogEnrichment[Attach] {
    def enrich(record: LogRecord, value: Attach): LogRecord =
      record
        .withAttribute(AttributeKey.string("exception.type"), value.throwable.getClass.getName)
        .withThrowable(Some(value.throwable))
  }

  def spec = suite("LogEnrichmentVisibility")(
    test("custom enrichments update body severity throwable and preserve source plus attributes") {
      val ex: Throwable = new RuntimeException("external boom")
      val base          = LogRecord.builder
        .setBody("base")
        .setSeverity(Severity.Info)
        .setSourceFile("e/E.scala")
        .setSourceFunction("pkg.Svc.go")
        .setSourceLine(5)
        .setAttribute(AttributeKey.string("k"), "v")
        .setAttribute(AttributeKey.long("n"), 7L)
        .build
      val enriched = attachEnrichment.enrich(
        escalateEnrichment.enrich(shoutEnrichment.enrich(base, Shout("loud")), Escalate(Severity.Error)),
        Attach(ex)
      )
      assertTrue(
        enriched.body.value == "loud",
        enriched.severity == Severity.Error,
        enriched.severityText == Severity.Error.text,
        enriched.throwable.contains(ex),
        enriched.sourceFile == "e/E.scala",
        enriched.sourceFunction == "pkg.Svc.go",
        enriched.sourceLine == 5,
        enriched.attribute(AttributeKey.string("k")).contains("v"),
        enriched.attribute(AttributeKey.long("n")).contains(7L),
        enriched.attribute("exception.type").contains(AttributeValue.StringValue("java.lang.RuntimeException"))
      )
    },
    test("attribute and foreachAttribute read user data from outside the package") {
      val record = LogRecord.builder
        .setBody("read")
        .setSourceFile("e/E.scala")
        .setSourceFunction("pkg.Svc.go")
        .setSourceLine(5)
        .setAttribute(AttributeKey.string("first"), "1")
        .setAttribute(AttributeKey.boolean("second"), true)
        .build
      val seen = ArrayBuffer.empty[(String, AttributeValue)]
      record.foreachAttribute { (key, value) => seen += ((key, value)); () }
      assertTrue(
        record.sourceFile == "e/E.scala",
        record.sourceFunction == "pkg.Svc.go",
        record.sourceLine == 5,
        record.attribute(AttributeKey.string("first")).contains("1"),
        record.attribute("second").contains(AttributeValue.BooleanValue(true)),
        record.attribute("missing").isEmpty,
        seen.toList == List(
          "first"  -> AttributeValue.StringValue("1"),
          "second" -> AttributeValue.BooleanValue(true)
        )
      )
    }
  )
}

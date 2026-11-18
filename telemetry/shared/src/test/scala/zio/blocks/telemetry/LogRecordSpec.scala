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

import zio.test._

import scala.collection.mutable.ArrayBuffer

object LogRecordSpec extends ZIOSpecDefault {

  private final class Capturing(level: Int = 1) extends LogRecordProcessor {
    val seen: ArrayBuffer[LogRecord]                = ArrayBuffer.empty
    override def minimumLevel: Int                  = level
    override def onEmit(logRecord: LogRecord): Unit =
      seen += logRecord
    override def shutdown(): Unit   = ()
    override def forceFlush(): Unit = ()
  }

  private def withLogger[A](minSeverity: Severity = Severity.Trace)(f: Capturing => A): (Capturing, A) = {
    val processor = new Capturing
    val logger    = LoggerProvider.builder.addLogRecordProcessor(processor).build().get("record-spec")
    log.install(logger, minSeverity)
    try {
      val result = f(processor)
      (processor, result)
    } finally {
      log.clearAllOverrides()
      log.removeAll()
    }
  }

  /**
   * Named fixture: the `log.info` call sits directly in `probe`'s body, so both
   * the Scala 2 and Scala 3 macros must report this exact owner and method — no
   * closures or indirection in between.
   */
  object ProbeFixture {
    def probe(): Unit = log.info("record-probe", "user" -> "ada", "tries" -> 3L)
  }

  def spec = suite("LogRecord")(
    suite("source accessors")(
      test("macro records expose compile-time source through sourceFile sourceFunction sourceLine") {
        val (processor, _) = withLogger() { _ =>
          ProbeFixture.probe()
        }
        val record = processor.seen.head
        assertTrue(
          processor.seen.size == 1,
          record.body.value == "record-probe",
          record.sourceFile.endsWith("LogRecordSpec.scala"),
          record.sourceFunction == "zio.blocks.telemetry.LogRecordSpec.ProbeFixture.probe",
          record.sourceLine > 0
        )
      },
      test("source metadata is not stored as user attributes") {
        val (processor, _) = withLogger() { _ =>
          ProbeFixture.probe()
        }
        val record = processor.seen.head
        val keys   = ArrayBuffer.empty[String]
        record.foreachAttribute { (key, _) => keys += key; () }
        assertTrue(
          record.attribute("code.file.path").isEmpty,
          record.attribute(AttributeKey.string("code.file.path")).isEmpty,
          record.attribute("code.function.name").isEmpty,
          record.attribute(AttributeKey.string("code.function.name")).isEmpty,
          record.attribute("code.line.number").isEmpty,
          record.attribute(AttributeKey.long("code.line.number")).isEmpty,
          keys.toList == List("user", "tries")
        )
      },
      test("direct logger records carry absent source") {
        val processor = new Capturing
        val logger    = LoggerProvider.builder.addLogRecordProcessor(processor).build().get("direct")
        logger.info("direct message", "k" -> AttributeValue.StringValue("v"))
        val record    = processor.seen.head
        assertTrue(
          record.sourceFile == "",
          record.sourceFunction == "",
          record.sourceLine == 0,
          record.body.value == "direct message",
          record.attribute("k").contains(AttributeValue.StringValue("v"))
        )
      },
      test("builder records default to absent source") {
        val record = LogRecord.builder.setBody("empty source").build
        assertTrue(
          record.sourceFile == "",
          record.sourceFunction == "",
          record.sourceLine == 0
        )
      },
      test("builder records carry explicit source") {
        val record = LogRecord.builder
          .setBody("explicit source")
          .setSourceFile("a/B.scala")
          .setSourceFunction("x.Y.go")
          .setSourceLine(7)
          .build
        assertTrue(
          record.sourceFile == "a/B.scala",
          record.sourceFunction == "x.Y.go",
          record.sourceLine == 7
        )
      },
      test("null source inputs coerce to absent instead of crashing") {
        val record = LogRecord.builder
          .setBody("null source")
          .setSourceFile(null)
          .setSourceFunction(null)
          .build
        assertTrue(
          record.sourceFile == "",
          record.sourceFunction == "",
          record.sourceLine == 0
        )
      },
      test("non-positive source lines normalize to absent on every ingress") {
        val viaBuilder = LogRecord.builder
          .setBody("negative line")
          .setSourceFile("n/N.scala")
          .setSourceFunction("pkg.N.go")
          .setSourceLine(-3)
          .build
        val viaCreate = LogRecord.create(
          timestampNanos = 1000L,
          observedTimestampNanos = 1000L,
          severity = Severity.Info,
          severityText = "INFO",
          body = LogMessage("negative line"),
          sourceFile = "n/N.scala",
          sourceFunction = "pkg.N.go",
          sourceLine = -3,
          userAttributes = Attributes.empty,
          traceIdHi = 0L,
          traceIdLo = 0L,
          spanId = 0L,
          traceFlags = 0.toByte,
          resource = Resource.empty,
          instrumentationScope = InstrumentationScope("ingress"),
          throwable = None
        )
        assertTrue(
          viaBuilder.sourceLine == 0,
          viaBuilder.sourceFile == "n/N.scala",
          viaCreate.sourceLine == 0,
          viaCreate.sourceFile == "n/N.scala",
          !LogRecord.isSourceCollision("code.line.number", "n/N.scala", "pkg.N.go", viaBuilder.sourceLine)
        )
      }
    ),
    suite("attribute")(
      test("typed lookup covers every stored kind") {
        val record = LogRecord.builder
          .setBody("typed")
          .setAttribute(AttributeKey.string("s"), "text")
          .setAttribute(AttributeKey.long("l"), 42L)
          .setAttribute(AttributeKey.double("d"), 1.5)
          .setAttribute(AttributeKey.boolean("b"), true)
          .setAttribute(AttributeKey.stringSeq("ss"), Seq("a", "b"))
          .setAttribute(AttributeKey.longSeq("ls"), Seq(1L, 2L))
          .setAttribute(AttributeKey.doubleSeq("ds"), Seq(1.5))
          .setAttribute(AttributeKey.booleanSeq("bs"), Seq(true, false))
          .build
        assertTrue(
          record.attribute(AttributeKey.string("s")).contains("text"),
          record.attribute(AttributeKey.long("l")).contains(42L),
          record.attribute(AttributeKey.double("d")).contains(1.5),
          record.attribute(AttributeKey.boolean("b")).contains(true),
          record.attribute(AttributeKey.stringSeq("ss")).contains(Seq("a", "b")),
          record.attribute(AttributeKey.longSeq("ls")).contains(Seq(1L, 2L)),
          record.attribute(AttributeKey.doubleSeq("ds")).contains(Seq(1.5)),
          record.attribute(AttributeKey.booleanSeq("bs")).contains(Seq(true, false))
        )
      },
      test("untyped lookup returns AttributeValue wrappers and None when absent") {
        val record = LogRecord.builder
          .setBody("untyped")
          .setAttribute(AttributeKey.string("s"), "text")
          .setAttribute(AttributeKey.long("l"), 42L)
          .setAttribute(AttributeKey.double("d"), 1.5)
          .setAttribute(AttributeKey.boolean("b"), true)
          .build
        assertTrue(
          record.attribute("s").contains(AttributeValue.StringValue("text")),
          record.attribute("l").contains(AttributeValue.LongValue(42L)),
          record.attribute("d").contains(AttributeValue.DoubleValue(1.5)),
          record.attribute("b").contains(AttributeValue.BooleanValue(true)),
          record.attribute("missing").isEmpty,
          record.attribute(AttributeKey.string("missing")).isEmpty
        )
      },
      test("user attributes named like canonical source keys stay ordinary user data") {
        val record = LogRecord.builder
          .setBody("collision")
          .setAttribute(AttributeKey.string("code.file.path"), "user-supplied.scala")
          .setAttribute(AttributeKey.long("code.line.number"), 99L)
          .build
        assertTrue(
          record.sourceFile == "",
          record.sourceLine == 0,
          record.attribute("code.file.path").contains(AttributeValue.StringValue("user-supplied.scala")),
          record.attribute(AttributeKey.long("code.line.number")).contains(99L)
        )
      },
      test("malformed external line values are kept, never dropped silently") {
        val record = LogRecord.builder
          .setBody("malformed")
          .setAttribute(AttributeKey.string("code.line.number"), "not-a-number")
          .build
        assertTrue(
          record.sourceLine == 0,
          record.attribute("code.line.number").contains(AttributeValue.StringValue("not-a-number"))
        )
      },
      test("unknown slot types read back as empty strings") {
        val raw = Attributes.builder.put("mystery", "x")
        raw.builderTypes(0) = 99.toByte
        val record = LogRecord.builder.setBody("unknown").addAttributes(raw.build).build
        assertTrue(record.attribute("mystery").contains(AttributeValue.StringValue("")))
      },
      test("collision policy is per-field with source winning only when present") {
        assertTrue(
          LogRecord.isSourceCollision("code.file.path", "f.scala", "", 0),
          LogRecord.isSourceCollision("code.function.name", "", "g.go", 0),
          LogRecord.isSourceCollision("code.line.number", "", "", 7),
          !LogRecord.isSourceCollision("code.file.path", "", "", 0),
          !LogRecord.isSourceCollision("code.function.name", "", "", 0),
          !LogRecord.isSourceCollision("code.line.number", "", "", 0),
          !LogRecord.isSourceCollision("code.file.path", null, null, 0),
          !LogRecord.isSourceCollision("user", "f.scala", "g.go", 7)
        )
      },
      test("merged attributes resolve to the last write") {
        val first  = Attributes.of(AttributeKey.string("k"), "old")
        val second = Attributes.of(AttributeKey.string("k"), "new")
        val record = LogRecord.builder.setBody("merged").addAttributes(first ++ second).build
        assertTrue(record.attribute(AttributeKey.string("k")).contains("new"))
      }
    ),
    suite("foreachAttribute")(
      test("enumerates user attributes in insertion order") {
        val record = LogRecord.builder
          .setBody("order")
          .setAttribute(AttributeKey.string("first"), "1")
          .setAttribute(AttributeKey.long("second"), 2L)
          .setAttribute(AttributeKey.boolean("third"), true)
          .build
        val seen = ArrayBuffer.empty[(String, AttributeValue)]
        record.foreachAttribute { (key, value) => seen += ((key, value)); () }
        assertTrue(
          seen.toList == List(
            "first"  -> AttributeValue.StringValue("1"),
            "second" -> AttributeValue.LongValue(2L),
            "third"  -> AttributeValue.BooleanValue(true)
          )
        )
      },
      test("empty records enumerate nothing") {
        val record = LogRecord.builder.setBody("empty").build
        var calls  = 0
        record.foreachAttribute { (_, _) => calls += 1; () }
        assertTrue(calls == 0)
      }
    ),
    suite("enrichment preserves source")(
      test("every LogEnrichment keeps sourceFile sourceFunction sourceLine") {
        val base = LogRecord.builder
          .setBody("base")
          .setSourceFile("s/S.scala")
          .setSourceFunction("pkg.Svc.go")
          .setSourceLine(11)
          .build
        val ex: Throwable = new RuntimeException("boom")
        val enriched      =
          LogEnrichment.throwableEnrichment.enrich(
            LogEnrichment.stringStringEnrichment.enrich(
              LogEnrichment.stringLongEnrichment.enrich(
                LogEnrichment.severityEnrichment.enrich(
                  LogEnrichment.attributesEnrichment.enrich(
                    LogEnrichment.stringEnrichment.enrich(base, "new body"),
                    Attributes.of(AttributeKey.string("extra"), "x")
                  ),
                  Severity.Error
                ),
                ("n", 7L)
              ),
              ("s", "v")
            ),
            ex
          )
        assertTrue(
          enriched.sourceFile == "s/S.scala",
          enriched.sourceFunction == "pkg.Svc.go",
          enriched.sourceLine == 11,
          enriched.body.value == "new body",
          enriched.severity == Severity.Error,
          enriched.attribute(AttributeKey.string("extra")).contains("x"),
          enriched.attribute(AttributeKey.long("n")).contains(7L),
          enriched.attribute(AttributeKey.string("s")).contains("v"),
          enriched.attribute(AttributeKey.string("exception.type")).contains("java.lang.RuntimeException"),
          enriched.throwable.contains(ex)
        )
      }
    ),
    suite("ownership")(
      test("records compare by value across every field") {
        def build(
          body: String = "v",
          file: String = "s/S.scala",
          function: String = "pkg.Svc.go",
          line: Int = 11,
          user: Attributes = Attributes.of(AttributeKey.string("k"), "v"),
          severity: Severity = Severity.Info,
          throwable: Option[Throwable] = None
        ): LogRecord =
          LogRecord.builder
            .setTimestamp(1000L)
            .setObservedTimestamp(1001L)
            .setSeverity(severity)
            .setBody(body)
            .setSourceFile(file)
            .setSourceFunction(function)
            .setSourceLine(line)
            .addAttributes(user)
            .setTraceId(10L, 20L)
            .setSpanId(30L)
            .setTraceFlags(1.toByte)
            .setResource(Resource.empty)
            .setInstrumentationScope(InstrumentationScope("eq"))
            .setThrowable(throwable)
            .build
        val ex: Throwable = new RuntimeException("boom")
        val base          = build()
        assertTrue(
          base == build(),
          base.hashCode == build().hashCode,
          base.toString.contains("INFO"),
          base != build(body = "other"),
          base != build(file = "other.scala"),
          base != build(function = "other.go"),
          base != build(line = 12),
          base != build(user = Attributes.of(AttributeKey.string("k"), "other")),
          base != build(severity = Severity.Error),
          base != build(throwable = Some(ex)),
          (base: Any) != "not-a-record"
        )
      },
      test("later builder writes do not mutate a published record") {
        val attrs  = Attributes.builder.put("k", "v1")
        val record = LogRecord.builder.setBody("stable").addAttributes(attrs.build).build
        attrs.put("k", "CHANGED")
        attrs.put("fresh", "x")
        assertTrue(
          record.attribute(AttributeKey.string("k")).contains("v1"),
          record.attribute(AttributeKey.string("fresh")).isEmpty
        )
      },
      test("retained records stay stable while the pooled builder is reused") {
        val processor = new Capturing
        val emitter   = new StandardLogEmitter(Array(processor), 1)
        val pooled    = AttributeBuilderPool.get().put("k", "first")
        emitter.emit(
          timestampNanos = 1000L,
          severity = Severity.Info,
          severityText = "INFO",
          body = "retained",
          sourceFile = "r/R.scala",
          sourceFunction = "pkg.R.go",
          sourceLine = 3,
          builder = pooled,
          traceIdHi = 0L,
          traceIdLo = 0L,
          spanId = 0L,
          traceFlags = 0.toByte,
          resource = Resource.empty,
          instrumentationScope = InstrumentationScope("retain"),
          throwable = None
        )
        pooled.put("k", "second")
        pooled.put("code.file.path", "intruder.scala")
        val retained = processor.seen.head
        assertTrue(
          processor.seen.size == 1,
          retained.sourceFile == "r/R.scala",
          retained.sourceFunction == "pkg.R.go",
          retained.sourceLine == 3,
          retained.attribute(AttributeKey.string("k")).contains("first"),
          retained.attribute("code.file.path").isEmpty
        )
      },
      test("emitter clears pooled user attributes without touching source scalars") {
        val processor = new Capturing
        val emitter   = new StandardLogEmitter(Array(processor), 1)
        emitter.emit(
          timestampNanos = 1000L,
          severity = Severity.Info,
          severityText = "INFO",
          body = "one",
          sourceFile = "a/A.scala",
          sourceFunction = "pkg.A.go",
          sourceLine = 1,
          builder = AttributeBuilderPool.get().put("k", "v"),
          traceIdHi = 0L,
          traceIdLo = 0L,
          spanId = 0L,
          traceFlags = 0.toByte,
          resource = Resource.empty,
          instrumentationScope = InstrumentationScope("reuse"),
          throwable = None
        )
        emitter.emit(
          timestampNanos = 2000L,
          severity = Severity.Info,
          severityText = "INFO",
          body = "two",
          sourceFile = "",
          sourceFunction = "",
          sourceLine = 0,
          builder = AttributeBuilderPool.get(),
          traceIdHi = 0L,
          traceIdLo = 0L,
          spanId = 0L,
          traceFlags = 0.toByte,
          resource = Resource.empty,
          instrumentationScope = InstrumentationScope("reuse"),
          throwable = None
        )
        val first  = processor.seen(0)
        val second = processor.seen(1)
        assertTrue(
          processor.seen.size == 2,
          first.sourceFile == "a/A.scala",
          first.attribute(AttributeKey.string("k")).contains("v"),
          second.sourceFile == "",
          second.sourceFunction == "",
          second.sourceLine == 0,
          second.attribute(AttributeKey.string("k")).isEmpty
        )
      }
    )
  ) @@ TestAspect.sequential
}

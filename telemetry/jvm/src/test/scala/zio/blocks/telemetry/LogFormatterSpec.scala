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

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object LogFormatterSpec extends ZIOSpecDefault {

  private val timestampFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

  private def expectedTimestamp(timestampNanos: Long): String =
    timestampFormatter.format(Instant.ofEpochMilli(timestampNanos / 1000000L))

  private def renderText(
    timestampNanos: Long,
    severity: Severity,
    severityText: String,
    body: String,
    builder: Attributes.AttributesBuilder,
    throwable: Option[Throwable] = None
  ): String = {
    val sb = new StringBuilder
    TextLogFormatter.format(sb, timestampNanos, severity, severityText, body, builder, 0L, 0L, 0L, 0.toByte, throwable)
    sb.toString
  }

  private def renderTextRecord(record: LogRecord): String = {
    val sb = new StringBuilder
    TextLogFormatter.formatRecord(sb, record)
    sb.toString
  }

  private def renderJson(
    timestampNanos: Long,
    severity: Severity,
    severityText: String,
    body: String,
    builder: Attributes.AttributesBuilder,
    traceIdHi: Long = 0L,
    traceIdLo: Long = 0L,
    spanId: Long = 0L,
    throwable: Option[Throwable] = None
  ): String = {
    val sb = new StringBuilder
    JsonLogFormatter.format(
      sb,
      timestampNanos,
      severity,
      severityText,
      body,
      builder,
      traceIdHi,
      traceIdLo,
      spanId,
      0.toByte,
      throwable
    )
    sb.toString
  }

  private def renderJsonRecord(record: LogRecord): String = {
    val sb = new StringBuilder
    JsonLogFormatter.formatRecord(sb, record)
    sb.toString
  }

  private def logRecord(
    timestampNanos: Long,
    severity: Severity,
    severityText: String,
    body: String,
    attributes: Attributes,
    traceIdHi: Long = 0L,
    traceIdLo: Long = 0L,
    spanId: Long = 0L,
    throwable: Option[Throwable] = None
  ): LogRecord =
    LogRecord(
      timestampNanos = timestampNanos,
      observedTimestampNanos = timestampNanos,
      severity = severity,
      severityText = severityText,
      body = LogMessage.Simple(body),
      attributes = attributes,
      traceIdHi = traceIdHi,
      traceIdLo = traceIdLo,
      spanId = spanId,
      traceFlags = 0.toByte,
      resource = Resource.empty,
      instrumentationScope = InstrumentationScope("test"),
      throwable = throwable
    )

  private def countOccurrences(haystack: String, needle: String): Int = {
    var count = 0
    var from  = 0
    var next  = haystack.indexOf(needle, from)
    while (next >= 0) {
      count += 1
      from = next + needle.length
      next = haystack.indexOf(needle, from)
    }
    count
  }

  private def setTextFormatterCache(second: Long, prefix: String): Unit = {
    val module      = TextLogFormatter
    val cls         = module.getClass
    val secondField = cls.getDeclaredField("cachedSecond")
    secondField.setAccessible(true)
    secondField.setLong(module, second)
    val prefixField = cls.getDeclaredField("cachedPrefix")
    prefixField.setAccessible(true)
    prefixField.set(module, prefix)
  }

  private def cachedTextFormatterSecond: Long = {
    val field = TextLogFormatter.getClass.getDeclaredField("cachedSecond")
    field.setAccessible(true)
    field.getLong(TextLogFormatter)
  }

  private def cachedTextFormatterPrefix: String = {
    val field = TextLogFormatter.getClass.getDeclaredField("cachedPrefix")
    field.setAccessible(true)
    field.get(TextLogFormatter).asInstanceOf[String]
  }

  def spec = suite("LogFormatter")(
    suite("TextLogFormatter.format")(
      test("formats all builder types, source location, throwable, and timestamp cache") {
        val timestamp1 = 1719792600123000000L
        val timestamp2 = 1719792600456000000L
        val timestamp3 = 1719792601123000000L
        val throwable  = new IllegalArgumentException("boom")
        val builder    = Attributes.builder
          .put("code.filepath", "Service.scala")
          .put("code.namespace", "com.example.Service")
          .put("code.function", "run")
          .put("code.lineno", 42L)
          .put("user.string", "value")
          .put("user.long", 7L)
          .put("user.double", 3.5)
          .put("user.bool", true)
          .put("user.unknown", "ignored")

        builder.builderTypes(8) = 99.toByte

        setTextFormatterCache(Long.MinValue, "stale-prefix-")

        val rendered1     = renderText(timestamp1, Severity.Info, "INFO", "hello", builder, Some(throwable))
        val cachedSecond1 = cachedTextFormatterSecond
        val cachedPrefix1 = cachedTextFormatterPrefix
        val rendered2     = renderText(timestamp2, Severity.Info, "INFO", "hello-again", builder)
        val cachedSecond2 = cachedTextFormatterSecond
        val cachedPrefix2 = cachedTextFormatterPrefix
        val rendered3     = renderText(timestamp3, Severity.Info, "INFO", "next-second", builder)
        val cachedSecond3 = cachedTextFormatterSecond
        val cachedPrefix3 = cachedTextFormatterPrefix

        assertTrue(
          rendered1.startsWith(s"${expectedTimestamp(timestamp1)} INFO  ["),
          rendered1.contains("com.example.Service"),
          rendered1.contains(".run:42] hello {"),
          rendered1.contains("user.string=\"value\""),
          rendered1.contains("user.long=7"),
          rendered1.contains("user.double=3.5"),
          rendered1.contains("user.bool=true"),
          rendered1.contains("user.unknown=?"),
          rendered1.contains("\njava.lang.IllegalArgumentException: boom"),
          rendered2.startsWith(s"${expectedTimestamp(timestamp2)} INFO  ["),
          rendered2.contains(".run:42] hello-again {"),
          rendered3.startsWith(s"${expectedTimestamp(timestamp3)} INFO  ["),
          rendered3.contains(".run:42] next-second {"),
          cachedSecond1 == timestamp1 / 1000000000L,
          cachedSecond2 == cachedSecond1,
          cachedSecond3 == timestamp3 / 1000000000L,
          cachedPrefix1 == expectedTimestamp(timestamp1).dropRight(4),
          cachedPrefix2 == cachedPrefix1,
          cachedPrefix3 == expectedTimestamp(timestamp3).dropRight(4)
        )
      },
      test("handles empty namespace with exact-width severity and no user attributes") {
        val timestamp = 1719792602123000000L
        val builder   = Attributes.builder
          .put("code.filepath", "OnlyPath.scala")
          .put("code.namespace", "")
          .put("code.function", "go")
          .put("code.lineno", 7L)

        val rendered = renderText(timestamp, Severity.Error, "ERROR", "plain", builder)

        assertTrue(
          rendered.startsWith(s"${expectedTimestamp(timestamp)} ERROR [.go:7] plain"),
          !rendered.contains("{"),
          !rendered.contains("\njava.")
        )
      },
      test("handles a namespace without dots") {
        val timestamp = 1719792602323000000L
        val builder   = Attributes.builder
          .put("code.filepath", "Root.scala")
          .put("code.namespace", "RootService")
          .put("code.function", "go")
          .put("code.lineno", 3L)

        val rendered = renderText(timestamp, Severity.Info, "INFO", "dotless", builder)

        assertTrue(rendered.startsWith(s"${expectedTimestamp(timestamp)} INFO  [RootService.go:3] dotless"))
      }
    ),
    suite("TextLogFormatter.formatRecord")(
      test("formats record attributes, skips code attrs, and renders throwable") {
        val timestamp1 = 1719792603123000000L
        val timestamp2 = 1719792603456000000L
        val throwable  = new RuntimeException("record boom")
        val attrs      = Attributes.builder
          .put("code.filepath", "Standalone.scala")
          .put("code.namespace", "Standalone")
          .put("code.function", "act")
          .put("code.lineno", 9L)
          .put("user.string", "value")
          .put("user.long", 1L)
          .put("user.double", 2.5)
          .put("user.bool", false)
          .build

        val rendered1 = renderTextRecord(
          logRecord(timestamp1, Severity.Warn, "WARN", "record message", attrs, throwable = Some(throwable))
        )
        val rendered2 = renderTextRecord(logRecord(timestamp2, Severity.Warn, "WARN", "record message 2", attrs))

        assertTrue(
          rendered1.startsWith(s"${expectedTimestamp(timestamp1)} WARN  [Standalone.act:9] record message {"),
          rendered1.contains("user.string=\"value\""),
          rendered1.contains("user.long=1"),
          rendered1.contains("user.double=2.5"),
          rendered1.contains("user.bool=false"),
          !rendered1.contains("code.filepath="),
          rendered1.contains("\njava.lang.RuntimeException: record boom"),
          rendered2.startsWith(s"${expectedTimestamp(timestamp2)} WARN  [Standalone.act:9] record message 2 {")
        )
      },
      test("handles empty source data with no throwable and no user attributes") {
        val timestamp = 1719792604123000000L
        val attrs     = Attributes.builder
          .put("code.namespace", "")
          .put("code.filepath", "OnlyPath.scala")
          .build

        val rendered = renderTextRecord(logRecord(timestamp, Severity.Error, "ERROR", "bare", attrs))

        assertTrue(
          rendered.startsWith(s"${expectedTimestamp(timestamp)} ERROR [] bare"),
          !rendered.contains("{"),
          !rendered.contains("\njava.")
        )
      },
      test("omits the line number when code.lineno is zero") {
        val timestamp = 1719792604323000000L
        val attrs     = Attributes.builder
          .put("code.namespace", "Standalone")
          .put("code.function", "act")
          .put("code.lineno", 0L)
          .build

        val rendered = renderTextRecord(logRecord(timestamp, Severity.Warn, "WARN", "no line", attrs))

        assertTrue(rendered.startsWith(s"${expectedTimestamp(timestamp)} WARN  [Standalone.act] no line"))
      }
    ),
    suite("JsonLogFormatter.format")(
      test("formats all attribute types with trace context when throwable is absent") {
        val timestamp = 1719792605123000000L
        val traceIdHi = 0x0123456789abcdefL
        val traceIdLo = 0x0fedcba987654321L
        val spanId    = 0x1234abcd5678ef90L
        val builder   = Attributes.builder
          .put("user.string", "value")
          .put("user.long", 7L)
          .put("user.double", 3.5)
          .put("user.bool", false)
          .put("user.unknown", "ignored")

        builder.builderTypes(4) = 99.toByte

        val rendered =
          renderJson(timestamp, Severity.Info, "INFO", "body \"quoted\"\nnext", builder, traceIdHi, traceIdLo, spanId)

        assertTrue(
          rendered.contains(s"\"timeUnixNano\":\"$timestamp\""),
          rendered.contains("\"severityNumber\":9"),
          rendered.contains("\"severityText\":\"INFO\""),
          rendered.contains("\"body\":{\"stringValue\":\"body \\\"quoted\\\"\\nnext\"}"),
          rendered.contains("{\"key\":\"user.string\",\"value\":{\"stringValue\":\"value\"}}"),
          rendered.contains("{\"key\":\"user.long\",\"value\":{\"intValue\":\"7\"}}"),
          rendered.contains("{\"key\":\"user.double\",\"value\":{\"doubleValue\":3.5}}"),
          rendered.contains("{\"key\":\"user.bool\",\"value\":{\"boolValue\":false}}"),
          rendered.contains("{\"key\":\"user.unknown\",\"value\":{\"stringValue\":\"?\"}}"),
          rendered.contains(s"\"traceId\":\"${TraceId.toHex(traceIdHi, traceIdLo)}\""),
          rendered.contains(s"\"spanId\":\"${String.format("%016x", spanId: java.lang.Long)}\""),
          !rendered.contains("\"key\":\"exception.stacktrace\""),
          countOccurrences(rendered, "\"attributes\":") == 1
        )
      },
      test("splices throwable into an existing attributes array") {
        val timestamp = 1719792605623000000L
        val builder   = Attributes.builder
          .put("user.string", "value")
          .put("user.long", 7L)
          .put("user.double", 3.5)
          .put("user.bool", false)

        val rendered = renderJson(
          timestamp,
          Severity.Info,
          "INFO",
          "body",
          builder,
          throwable = Some(new IllegalArgumentException("json boom"))
        )

        assertTrue(
          rendered.contains("{\"key\":\"user.string\",\"value\":{\"stringValue\":\"value\"}}"),
          rendered.contains("\"key\":\"exception.stacktrace\""),
          rendered.contains("IllegalArgumentException: json boom"),
          countOccurrences(rendered, "\"attributes\":") == 1
        )
      },
      test("creates a fresh attributes array for throwable when builder has none") {
        val timestamp = 1719792606123000000L
        val rendered  = renderJson(
          timestamp,
          Severity.Warn,
          "WARN",
          "only throwable",
          Attributes.builder,
          throwable = Some(new RuntimeException("only stack"))
        )

        assertTrue(
          rendered.contains("\"body\":{\"stringValue\":\"only throwable\"}"),
          rendered.contains("\"attributes\":[{\"key\":\"exception.stacktrace\""),
          rendered.contains("RuntimeException: only stack"),
          !rendered.contains("\"traceId\":"),
          !rendered.contains("\"spanId\":"),
          countOccurrences(rendered, "\"attributes\":") == 1
        )
      },
      test("omits optional json sections when trace ids, span ids, attrs, and throwable are absent") {
        val timestamp = 1719792607123000000L
        val rendered  = renderJson(timestamp, Severity.Error, "ERROR", "minimal", Attributes.builder)

        assertTrue(
          rendered ==
            s"{\"timeUnixNano\":\"$timestamp\",\"severityNumber\":17,\"severityText\":\"ERROR\",\"body\":{\"stringValue\":\"minimal\"}}"
        )
      }
    ),
    suite("JsonLogFormatter.formatRecord")(
      test("formats record attributes and trace context when throwable is absent") {
        val timestamp = 1719792608123000000L
        val traceIdHi = 0x1111222233334444L
        val traceIdLo = 0x5555666677778888L
        val spanId    = 0x9999aaaabbbbccccL
        val attrs     = Attributes.builder
          .put("code.namespace", "com.example.Record")
          .put("user.string", "value")
          .put("user.long", 11L)
          .put("user.double", 4.25)
          .put("user.bool", true)
          .build
        val rendered = renderJsonRecord(
          logRecord(
            timestamp,
            Severity.Warn,
            "WARN",
            "json record",
            attrs,
            traceIdHi,
            traceIdLo,
            spanId
          )
        )

        assertTrue(
          rendered.contains(s"\"timeUnixNano\":\"$timestamp\""),
          rendered.contains("\"severityNumber\":13"),
          rendered.contains("\"severityText\":\"WARN\""),
          rendered.contains("{\"key\":\"code.namespace\",\"value\":{\"stringValue\":\"com.example.Record\"}}"),
          rendered.contains("{\"key\":\"user.string\",\"value\":{\"stringValue\":\"value\"}}"),
          rendered.contains("{\"key\":\"user.long\",\"value\":{\"intValue\":\"11\"}}"),
          rendered.contains("{\"key\":\"user.double\",\"value\":{\"doubleValue\":4.25}}"),
          rendered.contains("{\"key\":\"user.bool\",\"value\":{\"boolValue\":true}}"),
          rendered.contains(s"\"traceId\":\"${TraceId.toHex(traceIdHi, traceIdLo)}\""),
          rendered.contains(s"\"spanId\":\"${String.format("%016x", spanId: java.lang.Long)}\""),
          !rendered.contains("\"key\":\"exception.stacktrace\""),
          countOccurrences(rendered, "\"attributes\":") == 1
        )
      },
      test("splices throwable into record attributes when they already exist") {
        val timestamp = 1719792608623000000L
        val attrs     = Attributes.builder
          .put("code.namespace", "com.example.Record")
          .put("user.string", "value")
          .put("user.long", 11L)
          .put("user.double", 4.25)
          .put("user.bool", true)
          .build
        val rendered = renderJsonRecord(
          logRecord(
            timestamp,
            Severity.Warn,
            "WARN",
            "json record",
            attrs,
            throwable = Some(new RuntimeException("record stack"))
          )
        )

        assertTrue(
          rendered.contains("{\"key\":\"code.namespace\",\"value\":{\"stringValue\":\"com.example.Record\"}}"),
          rendered.contains("\"key\":\"exception.stacktrace\""),
          rendered.contains("RuntimeException: record stack"),
          countOccurrences(rendered, "\"attributes\":") == 1
        )
      },
      test("creates throwable attributes when the record has no attributes") {
        val timestamp = 1719792609123000000L
        val rendered  = renderJsonRecord(
          logRecord(
            timestamp,
            Severity.Info,
            "INFO",
            "just throwable",
            Attributes.empty,
            throwable = Some(new IllegalStateException("missing attrs"))
          )
        )

        assertTrue(
          rendered.contains("\"attributes\":[{\"key\":\"exception.stacktrace\""),
          rendered.contains("IllegalStateException: missing attrs"),
          !rendered.contains("\"traceId\":"),
          !rendered.contains("\"spanId\":"),
          countOccurrences(rendered, "\"attributes\":") == 1
        )
      },
      test("omits optional record json sections when attrs trace and throwable are absent") {
        val timestamp = 1719792610123000000L
        val rendered  =
          renderJsonRecord(logRecord(timestamp, Severity.Error, "ERROR", "record minimal", Attributes.empty))

        assertTrue(
          rendered ==
            s"{\"timeUnixNano\":\"$timestamp\",\"severityNumber\":17,\"severityText\":\"ERROR\",\"body\":{\"stringValue\":\"record minimal\"}}"
        )
      }
    ),
    suite("JsonLogFormatter.writeJsonStringContent")(
      test("escapes quotes, slashes, control chars, surrogates, and preserves normal text") {
        val input = "\"\\\n\r\t\b\f" + 1.toChar + 31.toChar + "\ud83d\ude00plain"
        val sb    = new StringBuilder

        JsonLogFormatter.writeJsonStringContent(sb, input)

        assertTrue(sb.toString == "\\\"\\\\\\n\\r\\t\\b\\f\\u0001\\u001f\\ud83d\\ude00plain")
      },
      test("escapes lone surrogate code units individually") {
        val input = "a" + 0xd800.toChar + "b" + 0xdfff.toChar + "c"
        val sb    = new StringBuilder

        JsonLogFormatter.writeJsonStringContent(sb, input)

        assertTrue(sb.toString == "a\\ud800b\\udfffc")
      }
    )
  ) @@ TestAspect.sequential
}

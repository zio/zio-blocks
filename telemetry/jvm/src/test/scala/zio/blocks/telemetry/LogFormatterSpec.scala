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
          .put("code.file.path", "Service.scala")
          .put("code.function.name", "com.example.Service.run")
          .put("code.line.number", 42L)
          .put("user.string", "value")
          .put("user.long", 7L)
          .put("user.double", 3.5)
          .put("user.bool", true)
          .put("user.unknown", "ignored")

        builder.builderTypes(7) = 99.toByte

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
          rendered1.startsWith(s"${expectedTimestamp(timestamp1)} INFO  hello [Service.run] Service.scala:42 {"),
          rendered1.contains("hello [Service.run] Service.scala:42 {"),
          // The method stays outside the path:line token: contiguous
          // `path:line`, never `method:line` inside the path.
          rendered1.contains("Service.scala:42"),
          !rendered1.contains("[Service.run:42]"),
          rendered1.contains("user.string=\"value\""),
          rendered1.contains("user.long=7"),
          rendered1.contains("user.double=3.5"),
          rendered1.contains("user.bool=true"),
          rendered1.contains("user.unknown=?"),
          rendered1.contains("\njava.lang.IllegalArgumentException: boom"),
          rendered2.startsWith(s"${expectedTimestamp(timestamp2)} INFO  hello-again [Service.run] Service.scala:42 {"),
          rendered2.contains("hello-again [Service.run] Service.scala:42 {"),
          rendered3.startsWith(s"${expectedTimestamp(timestamp3)} INFO  next-second [Service.run] Service.scala:42 {"),
          rendered3.contains("next-second [Service.run] Service.scala:42 {"),
          cachedSecond1 == timestamp1 / 1000000000L,
          cachedSecond2 == cachedSecond1,
          cachedSecond3 == timestamp3 / 1000000000L,
          cachedPrefix1 == expectedTimestamp(timestamp1).dropRight(4),
          cachedPrefix2 == cachedPrefix1,
          cachedPrefix3 == expectedTimestamp(timestamp3).dropRight(4)
        )
      },
      test("handles a dotless function name with exact-width severity and no user attributes") {
        val timestamp = 1719792602123000000L
        val builder   = Attributes.builder
          .put("code.file.path", "OnlyPath.scala")
          .put("code.function.name", "go")
          .put("code.line.number", 7L)

        val rendered = renderText(timestamp, Severity.Error, "ERROR", "plain", builder)

        assertTrue(
          rendered.startsWith(s"${expectedTimestamp(timestamp)} ERROR plain [go] OnlyPath.scala:7"),
          !rendered.contains("{"),
          !rendered.contains("\njava.")
        )
      },
      test("handles a two-segment function name") {
        val timestamp = 1719792602323000000L
        val builder   = Attributes.builder
          .put("code.file.path", "Root.scala")
          .put("code.function.name", "RootService.go")
          .put("code.line.number", 3L)

        val rendered = renderText(timestamp, Severity.Info, "INFO", "dotless", builder)

        assertTrue(rendered.startsWith(s"${expectedTimestamp(timestamp)} INFO  dotless [RootService.go] Root.scala:3"))
      },
      test("renders all seq attribute types and an empty seq inline") {
        val timestamp = 1719792602523000000L
        val builder   = Attributes.builder
          .put("code.file.path", "Seqs.scala")
          .put("code.function.name", "Seqs.go")
          .put("code.line.number", 1L)
          .put(AttributeKey.stringSeq("tags"), Seq("a", "b"))
          .put(AttributeKey.longSeq("nums"), Seq(1L, 2L))
          .put(AttributeKey.doubleSeq("rates"), Seq(1.5, 2.5))
          .put(AttributeKey.booleanSeq("flags"), Seq(true, false))
          .put(AttributeKey.stringSeq("empty"), Seq.empty[String])

        val rendered = renderText(timestamp, Severity.Info, "INFO", "seqs", builder)

        assertTrue(
          rendered.startsWith(s"${expectedTimestamp(timestamp)} INFO  seqs [Seqs.go] Seqs.scala:1 {"),
          rendered.contains("tags=[\"a\", \"b\"]"),
          rendered.contains("nums=[1, 2]"),
          rendered.contains("rates=[1.5, 2.5]"),
          rendered.contains("flags=[true, false]"),
          rendered.contains("empty=[]")
        )
      },
      test("escapes quotes, backslashes, and line breaks in text string values") {
        val timestamp = 1719792602723000000L
        val builder   = Attributes.builder
          .put("code.file.path", "Escape.scala")
          .put("code.function.name", "Escape.go")
          .put("code.line.number", 1L)
          .put("quote", "say \"hi\"\nbye\\done")
          .put(AttributeKey.stringSeq("tags"), Seq("a\"b", "c\nd"))

        val rendered = renderText(timestamp, Severity.Info, "INFO", "escaped", builder)

        assertTrue(
          rendered.contains("quote=\"say \\\"hi\\\"\\nbye\\\\done\""),
          rendered.contains("tags=[\"a\\\"b\", \"c\\nd\"]")
        )
      }
    ),
    suite("TextLogFormatter.formatRecord")(
      test("formats record attributes, skips code attrs, and renders throwable") {
        val timestamp1 = 1719792603123000000L
        val timestamp2 = 1719792603456000000L
        val throwable  = new RuntimeException("record boom")
        val attrs      = Attributes.builder
          .put("code.file.path", "Standalone.scala")
          .put("code.function.name", "Standalone.act")
          .put("code.line.number", 9L)
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
          rendered1.startsWith(
            s"${expectedTimestamp(timestamp1)} WARN  record message [Standalone.act] Standalone.scala:9 {"
          ),
          rendered1.contains("record message [Standalone.act] Standalone.scala:9 {"),
          rendered1.contains("Standalone.scala:9"),
          !rendered1.contains("[Standalone.act:9]"),
          rendered1.contains("user.string=\"value\""),
          rendered1.contains("user.long=1"),
          rendered1.contains("user.double=2.5"),
          rendered1.contains("user.bool=false"),
          !rendered1.contains("code.file.path="),
          !rendered1.contains("code.function.name="),
          !rendered1.contains("code.line.number="),
          rendered1.contains("\njava.lang.RuntimeException: record boom"),
          rendered2.startsWith(
            s"${expectedTimestamp(timestamp2)} WARN  record message 2 [Standalone.act] Standalone.scala:9 {"
          )
        )
      },
      test("handles empty source data with no throwable and no user attributes") {
        val timestamp = 1719792604123000000L
        val attrs     = Attributes.builder
          .put("code.function.name", "")
          .put("code.file.path", "OnlyPath.scala")
          .build

        val rendered = renderTextRecord(logRecord(timestamp, Severity.Error, "ERROR", "bare", attrs))

        assertTrue(
          rendered.startsWith(s"${expectedTimestamp(timestamp)} ERROR bare OnlyPath.scala"),
          !rendered.contains("{"),
          !rendered.contains("["),
          !rendered.contains("\njava.")
        )
      },
      test("omits the line number when code.line.number is zero") {
        val timestamp = 1719792604323000000L
        val attrs     = Attributes.builder
          .put("code.function.name", "Standalone.act")
          .put("code.line.number", 0L)
          .build

        val rendered = renderTextRecord(logRecord(timestamp, Severity.Warn, "WARN", "no line", attrs))

        assertTrue(rendered.startsWith(s"${expectedTimestamp(timestamp)} WARN  no line [Standalone.act]"))
      },
      test("escapes quotes and line breaks in record string values") {
        val timestamp = 1719792604523000000L
        val attrs     = Attributes.builder
          .put("code.function.name", "Escape.go")
          .put("code.line.number", 1L)
          .put("quote", "say \"hi\"\nbye")
          .build

        val rendered = renderTextRecord(logRecord(timestamp, Severity.Info, "INFO", "escaped", attrs))

        assertTrue(rendered.contains("quote=\"say \\\"hi\\\"\\nbye\""))
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
      },
      test("renders all seq attribute types as OTLP arrayValue") {
        val timestamp = 1719792607523000000L
        val builder   = Attributes.builder
          .put(AttributeKey.stringSeq("tags"), Seq("a", "b"))
          .put(AttributeKey.longSeq("nums"), Seq(1L, 2L))
          .put(AttributeKey.doubleSeq("rates"), Seq(1.5, 2.5))
          .put(AttributeKey.booleanSeq("flags"), Seq(true, false))

        val rendered = renderJson(timestamp, Severity.Info, "INFO", "seqs", builder)

        assertTrue(
          rendered.contains(
            "{\"key\":\"tags\",\"value\":{\"arrayValue\":{\"values\":[{\"stringValue\":\"a\"},{\"stringValue\":\"b\"}]}}}"
          ),
          rendered.contains(
            "{\"key\":\"nums\",\"value\":{\"arrayValue\":{\"values\":[{\"intValue\":\"1\"},{\"intValue\":\"2\"}]}}}"
          ),
          rendered.contains(
            "{\"key\":\"rates\",\"value\":{\"arrayValue\":{\"values\":[{\"doubleValue\":1.5},{\"doubleValue\":2.5}]}}}"
          ),
          rendered.contains(
            "{\"key\":\"flags\",\"value\":{\"arrayValue\":{\"values\":[{\"boolValue\":true},{\"boolValue\":false}]}}}"
          )
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
          .put("code.function.name", "com.example.Record")
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
          rendered.contains("{\"key\":\"code.function.name\",\"value\":{\"stringValue\":\"com.example.Record\"}}"),
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
          .put("code.function.name", "com.example.Record")
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
          rendered.contains("{\"key\":\"code.function.name\",\"value\":{\"stringValue\":\"com.example.Record\"}}"),
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
    suite("direct builder without code attributes")(
      test("text format prints every user attribute") {
        val timestamp = 1719792611123000000L
        val builder   = Attributes.builder
          .put("user", "nabil")
          .put("tries", 3L)
          .put("ratio", 1.5)
          .put("active", true)

        val rendered = renderText(timestamp, Severity.Info, "INFO", "hello", builder)

        assertTrue(
          rendered == s"${expectedTimestamp(timestamp)} INFO  hello " +
            "{user=\"nabil\", tries=3, ratio=1.5, active=true}",
          !rendered.contains("["),
          !rendered.contains("]")
        )
      }
    ),
    suite("shared render core parity")(
      test("text format and formatRecord are byte-identical") {
        val timestamp = 1719792612123000000L
        val builder   = Attributes.builder
          .put("code.file.path", "Service.scala")
          .put("code.function.name", "com.example.Service.run")
          .put("code.line.number", 42L)
          .put("code.reviewer", "ada")
          .put("user.string", "value")
          .put("user.long", 7L)
          .put("user.double", 3.5)
          .put("user.bool", true)
          .put(AttributeKey.stringSeq("tags"), Seq("a", "b"))
          .put(AttributeKey.longSeq("nums"), Seq(1L, 2L))

        val viaBuilder = renderText(timestamp, Severity.Info, "INFO", "hello", builder)
        val viaRecord  =
          renderTextRecord(logRecord(timestamp, Severity.Info, "INFO", "hello", builder.build))

        val expected =
          s"${expectedTimestamp(timestamp)} INFO  hello [Service.run] Service.scala:42 " +
            "{code.reviewer=\"ada\", user.string=\"value\", user.long=7, " +
            "user.double=3.5, user.bool=true, tags=[\"a\", \"b\"], nums=[1, 2]}"

        assertTrue(
          viaBuilder == expected,
          viaRecord == expected,
          viaBuilder == viaRecord
        )
      },
      test("json format and formatRecord are byte-identical") {
        val timestamp = 1719792613123000000L
        val traceIdHi = 0x1111222233334444L
        val traceIdLo = 0x5555666677778888L
        val spanId    = 0x9999aaaabbbbccccL
        val builder   = Attributes.builder
          .put("code.function.name", "com.example.Service.run")
          .put("code.reviewer", "ada")
          .put("user.string", "value")
          .put("user.long", 11L)
          .put("user.double", 4.25)
          .put("user.bool", true)

        val viaBuilder =
          renderJson(timestamp, Severity.Warn, "WARN", "json message", builder, traceIdHi, traceIdLo, spanId)
        val viaRecord =
          renderJsonRecord(
            logRecord(timestamp, Severity.Warn, "WARN", "json message", builder.build, traceIdHi, traceIdLo, spanId)
          )

        assertTrue(
          viaBuilder == viaRecord,
          viaBuilder.contains("\"key\":\"code.function.name\""),
          viaBuilder.contains("\"key\":\"code.reviewer\""),
          viaRecord.contains("\"key\":\"code.reviewer\"")
        )
      },
      test("json format and formatRecord agree byte-for-byte on hostile keys and values") {
        val timestamp = 1719792613223000000L
        // Malicious key AND value: quotes, backslash, CR, LF, tab, C0
        // control, and surrogate halves (lone in the key, paired in the value).
        val evilKey   = "ke\"y\\with\n\r" + 1.toChar + 0xdfff.toChar + "end"
        val evilValue = "va\"l\\ue\n\r\t" + 1.toChar + 0xd83d.toChar + 0xde00.toChar + "tail"
        val builder   = Attributes.builder
          .put(evilKey, evilValue)
          .put("plain", 7L)

        val viaBuilder =
          renderJson(timestamp, Severity.Warn, "WARN", "msg \"hi\"\nbye\\done", builder)
        val viaRecord =
          renderJsonRecord(logRecord(timestamp, Severity.Warn, "WARN", "msg \"hi\"\nbye\\done", builder.build))

        // Independently specified full rendering: field order, escaping, and
        // attribute order/multiplicity pinned, not just path equality.
        val expected =
          "{\"timeUnixNano\":\"1719792613223000000\",\"severityNumber\":13,\"severityText\":\"WARN\"," +
            "\"body\":{\"stringValue\":\"msg \\\"hi\\\"\\nbye\\\\done\"}," +
            "\"attributes\":[{\"key\":\"ke\\\"y\\\\with\\n\\r\\u0001\\udfffend\"," +
            "\"value\":{\"stringValue\":\"va\\\"l\\\\ue\\n\\r\\t\\u0001\\ud83d\\ude00tail\"}}," +
            "{\"key\":\"plain\",\"value\":{\"intValue\":\"7\"}}]}"

        assertTrue(
          viaBuilder == viaRecord,
          viaBuilder == expected,
          viaRecord == expected,
          countOccurrences(viaBuilder, "\"attributes\":") == 1,
          countOccurrences(viaBuilder, "\"key\":") == 2
        )
      }
    ),
    suite("text log-forging resistance")(
      test("newline, quote, and equals in attr key plus multi-line body stay on one physical line") {
        val timestamp = 1719792614123000000L
        val evilKey   = "evil\nkey\"with=sign"
        val body      = "line1\nline2\r\nline3"
        val builder   = Attributes.builder
          .put("code.file.path", "Forge.scala")
          .put("code.function.name", "Forge.go")
          .put("code.line.number", 1L)
          .put(evilKey, "v")

        val viaBuilder = renderText(timestamp, Severity.Info, "INFO", body, builder)
        val viaRecord  =
          renderTextRecord(logRecord(timestamp, Severity.Info, "INFO", body, builder.build))

        assertTrue(
          viaBuilder == viaRecord,
          !viaBuilder.contains("\n"),
          !viaBuilder.contains("\r"),
          viaBuilder.contains("evil\\nkey\\\"with\\=sign=\"v\""),
          viaBuilder.contains("line1\\nline2\\r\\nline3"),
          viaBuilder.contains("line1\\nline2\\r\\nline3 [Forge.go] Forge.scala:1 {")
        )
      },
      test("String-typed code.line.number feeds the location prefix on both paths") {
        val timestamp = 1719792614323000000L
        val builder   = Attributes.builder
          .put("code.file.path", "WrongType.scala")
          .put("code.function.name", "WrongType.go")
          .put("code.line.number", "42")

        val viaBuilder = renderText(timestamp, Severity.Info, "INFO", "hello", builder)
        val viaRecord  =
          renderTextRecord(logRecord(timestamp, Severity.Info, "INFO", "hello", builder.build))

        assertTrue(
          viaBuilder == viaRecord,
          viaBuilder.contains("hello [WrongType.go] WrongType.scala:42"),
          viaBuilder.contains("WrongType.scala:42"),
          !viaBuilder.contains("code.line.number=")
        )
      },
      test("unparseable String-typed code.line.number stays visible instead of being dropped") {
        val timestamp = 1719792614523000000L
        val builder   = Attributes.builder
          .put("code.function.name", "WrongType.go")
          .put("code.line.number", "not-a-number")

        val viaBuilder = renderText(timestamp, Severity.Info, "INFO", "hello", builder)
        val viaRecord  =
          renderTextRecord(logRecord(timestamp, Severity.Info, "INFO", "hello", builder.build))

        assertTrue(
          viaBuilder == viaRecord,
          viaBuilder.contains("hello [WrongType.go] {"),
          viaBuilder.contains("code.line.number=\"not-a-number\"")
        )
      }
    ),
    suite("deprecated code attributes")(
      test("deprecated keys stay visible as ordinary user attributes on both paths") {
        val timestamp = 1719792615123000000L
        val builder   = Attributes.builder
          .put("code.filepath", "Test.scala")
          .put("code.namespace", "com.example.MyClass")
          .put("code.function", "myMethod")
          .put("code.lineno", 42L)

        val viaBuilder = renderText(timestamp, Severity.Info, "INFO", "hello", builder)
        val viaRecord  =
          renderTextRecord(logRecord(timestamp, Severity.Info, "INFO", "hello", builder.build))

        // No canonical location key is present, so no location renders —
        // and every deprecated key renders as an ordinary attribute.
        val expected =
          s"${expectedTimestamp(timestamp)} INFO  hello " +
            "{code.filepath=\"Test.scala\", code.namespace=\"com.example.MyClass\", " +
            "code.function=\"myMethod\", code.lineno=42}"

        assertTrue(
          viaBuilder == expected,
          viaRecord == expected,
          viaBuilder == viaRecord
        )
      }
    ),
    suite("text source-location layout")(
      test("renders the approved message-first layout with separate function and contiguous path:line") {
        val timestamp = 1719792616123000000L
        val builder   = Attributes.builder
          .put("code.file.path", "/captured/path/PaymentService.scala")
          .put("code.function.name", "com.example.PaymentService.handle")
          .put("code.line.number", 42L)
          .put("user", "ada")

        val viaBuilder = renderText(timestamp, Severity.Info, "INFO", "Payment authorized", builder)
        val viaRecord  =
          renderTextRecord(logRecord(timestamp, Severity.Info, "INFO", "Payment authorized", builder.build))

        val expected =
          s"${expectedTimestamp(timestamp)} INFO  Payment authorized " +
            "[PaymentService.handle] /captured/path/PaymentService.scala:42 {user=\"ada\"}"

        assertTrue(
          viaBuilder == expected,
          viaRecord == expected,
          viaBuilder == viaRecord,
          !viaBuilder.contains("[PaymentService.handle:42]")
        )
      },
      test("renders path only when function and line are absent") {
        val timestamp = 1719792616223000000L
        val builder   = Attributes.builder
          .put("code.file.path", "only/Path.scala")

        val viaBuilder = renderText(timestamp, Severity.Info, "INFO", "hello", builder)
        val viaRecord  =
          renderTextRecord(logRecord(timestamp, Severity.Info, "INFO", "hello", builder.build))

        val expected = s"${expectedTimestamp(timestamp)} INFO  hello only/Path.scala"

        assertTrue(
          viaBuilder == expected,
          viaRecord == expected,
          viaBuilder == viaRecord,
          !viaBuilder.contains("[")
        )
      },
      test("renders path without a line and keeps the compact function free of a duplicated line") {
        val timestamp = 1719792616323000000L
        val builder   = Attributes.builder
          .put("code.file.path", "svc/Service.scala")
          .put("code.function.name", "com.example.Svc.go")
          .put("code.line.number", 0L)

        val viaBuilder = renderText(timestamp, Severity.Info, "INFO", "hello", builder)
        val viaRecord  =
          renderTextRecord(logRecord(timestamp, Severity.Info, "INFO", "hello", builder.build))

        val expected = s"${expectedTimestamp(timestamp)} INFO  hello [Svc.go] svc/Service.scala"

        assertTrue(
          viaBuilder == expected,
          viaRecord == expected,
          viaBuilder == viaRecord,
          !viaBuilder.contains("Svc.go:")
        )
      },
      test("renders path:line without a function bracket when the function is absent") {
        val timestamp = 1719792616423000000L
        val builder   = Attributes.builder
          .put("code.file.path", "svc/Job.scala")
          .put("code.line.number", 9L)

        val viaBuilder = renderText(timestamp, Severity.Info, "INFO", "hello", builder)
        val viaRecord  =
          renderTextRecord(logRecord(timestamp, Severity.Info, "INFO", "hello", builder.build))

        val expected = s"${expectedTimestamp(timestamp)} INFO  hello svc/Job.scala:9"

        assertTrue(
          viaBuilder == expected,
          viaRecord == expected,
          viaBuilder == viaRecord,
          !viaBuilder.contains("[")
        )
      },
      test("falls back to compact function:line without a path") {
        val timestamp = 1719792616523000000L
        val builder   = Attributes.builder
          .put("code.function.name", "com.example.Svc.doWork")
          .put("code.line.number", 42L)

        val viaBuilder = renderText(timestamp, Severity.Info, "INFO", "hello", builder)
        val viaRecord  =
          renderTextRecord(logRecord(timestamp, Severity.Info, "INFO", "hello", builder.build))

        val expected = s"${expectedTimestamp(timestamp)} INFO  hello [Svc.doWork:42]"

        assertTrue(
          viaBuilder == expected,
          viaRecord == expected,
          viaBuilder == viaRecord
        )
      },
      test("renders the function alone without a line when the line is absent") {
        val timestamp = 1719792616623000000L
        val builder   = Attributes.builder
          .put("code.function.name", "RootService.go")

        val viaBuilder = renderText(timestamp, Severity.Info, "INFO", "hello", builder)
        val viaRecord  =
          renderTextRecord(logRecord(timestamp, Severity.Info, "INFO", "hello", builder.build))

        val expected = s"${expectedTimestamp(timestamp)} INFO  hello [RootService.go]"

        assertTrue(
          viaBuilder == expected,
          viaRecord == expected,
          viaBuilder == viaRecord
        )
      },
      test("renders a bare line label when only the line is present") {
        val timestamp = 1719792616723000000L
        val builder   = Attributes.builder
          .put("code.line.number", 42L)

        val viaBuilder = renderText(timestamp, Severity.Info, "INFO", "hello", builder)
        val viaRecord  =
          renderTextRecord(logRecord(timestamp, Severity.Info, "INFO", "hello", builder.build))

        val expected = s"${expectedTimestamp(timestamp)} INFO  hello [line:42]"

        assertTrue(
          viaBuilder == expected,
          viaRecord == expected,
          viaBuilder == viaRecord,
          !viaBuilder.contains("[:42]")
        )
      },
      test("renders no location clutter when no location fields are present") {
        val timestamp = 1719792616823000000L

        val viaBuilder = renderText(timestamp, Severity.Info, "INFO", "hello", Attributes.builder)
        val viaRecord  =
          renderTextRecord(logRecord(timestamp, Severity.Info, "INFO", "hello", Attributes.empty))

        val expected = s"${expectedTimestamp(timestamp)} INFO  hello"

        assertTrue(
          viaBuilder == expected,
          viaRecord == expected,
          viaBuilder == viaRecord,
          !viaBuilder.contains("["),
          !viaBuilder.contains("]")
        )
      },
      test("keeps spacing clean with an empty body and a location") {
        val timestamp = 1719792616923000000L
        val builder   = Attributes.builder
          .put("code.file.path", "a/B.scala")
          .put("code.function.name", "com.example.Svc.go")
          .put("code.line.number", 3L)

        val viaBuilder = renderText(timestamp, Severity.Info, "INFO", "", builder)
        val viaRecord  =
          renderTextRecord(logRecord(timestamp, Severity.Info, "INFO", "", builder.build))

        val expected = s"${expectedTimestamp(timestamp)} INFO  [Svc.go] a/B.scala:3"

        assertTrue(
          viaBuilder == expected,
          viaRecord == expected,
          viaBuilder == viaRecord
        )
      },
      test("hides a Double-typed code.line.number instead of rendering it") {
        val timestamp = 1719792617023000000L
        val builder   = Attributes.builder
          .put("code.file.path", "s/S.scala")
          .put("code.function.name", "com.example.Svc.go")
          .put("code.line.number", 42.5)

        val viaBuilder = renderText(timestamp, Severity.Info, "INFO", "hello", builder)
        val viaRecord  =
          renderTextRecord(logRecord(timestamp, Severity.Info, "INFO", "hello", builder.build))

        val expected = s"${expectedTimestamp(timestamp)} INFO  hello [Svc.go] s/S.scala"

        assertTrue(
          viaBuilder == expected,
          viaRecord == expected,
          viaBuilder == viaRecord,
          !viaBuilder.contains("code.line.number"),
          !viaBuilder.contains("42.5")
        )
      },
      test("orders the throwable after body, location, and attributes") {
        val timestamp = 1719792617123000000L
        val throwable = new IllegalArgumentException("boom")
        val builder   = Attributes.builder
          .put("code.file.path", "Service.scala")
          .put("code.function.name", "com.example.Service.run")
          .put("code.line.number", 42L)
          .put("user", "ada")

        val viaBuilder = renderText(timestamp, Severity.Info, "INFO", "hello", builder, Some(throwable))
        val viaRecord  =
          renderTextRecord(
            logRecord(timestamp, Severity.Info, "INFO", "hello", builder.build, throwable = Some(throwable))
          )

        val prefix =
          s"${expectedTimestamp(timestamp)} INFO  hello [Service.run] Service.scala:42 " +
            "{user=\"ada\"}\njava.lang.IllegalArgumentException: boom"

        assertTrue(
          viaBuilder == viaRecord,
          viaBuilder.startsWith(prefix),
          viaBuilder.indexOf("Service.scala:42") < viaBuilder.indexOf("{user="),
          viaBuilder.indexOf("{user=") < viaBuilder.indexOf("IllegalArgumentException")
        )
      },
      test("preserves Windows backslash separators literally in path:line") {
        val timestamp = 1719792617323000000L
        val builder   = Attributes.builder
          .put("code.file.path", "C:\\work\\src\\PaymentService.scala")
          .put("code.function.name", "com.example.PaymentService.handle")
          .put("code.line.number", 42L)

        val viaBuilder = renderText(timestamp, Severity.Info, "INFO", "Payment authorized", builder)
        val viaRecord  =
          renderTextRecord(logRecord(timestamp, Severity.Info, "INFO", "Payment authorized", builder.build))

        val expected =
          s"${expectedTimestamp(timestamp)} INFO  Payment authorized " +
            "[PaymentService.handle] C:\\work\\src\\PaymentService.scala:42"

        assertTrue(
          viaBuilder == expected,
          viaRecord == expected,
          viaBuilder == viaRecord,
          viaBuilder.contains("C:\\work\\src\\PaymentService.scala:42"),
          !viaBuilder.contains("\\\\")
        )
      },
      test("keeps a hostile path/function on one physical line via escaping") {
        // A captured path containing a real newline is intentionally no longer
        // a navigable path once escaped — this asserts the safe rendering.
        val timestamp = 1719792617223000000L
        val builder   = Attributes.builder
          .put("code.file.path", "a/b\r\nEvil.scala")
          .put("code.function.name", "com.example.Evil\nMethod")
          .put("code.line.number", 3L)

        val viaBuilder = renderText(timestamp, Severity.Info, "INFO", "hello", builder)
        val viaRecord  =
          renderTextRecord(logRecord(timestamp, Severity.Info, "INFO", "hello", builder.build))

        val expected =
          s"${expectedTimestamp(timestamp)} INFO  hello " +
            "[example.Evil\\nMethod] a/b\\r\\nEvil.scala:3"

        assertTrue(
          viaBuilder == expected,
          viaRecord == expected,
          viaBuilder == viaRecord,
          !viaBuilder.contains("\n"),
          !viaBuilder.contains("\r")
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

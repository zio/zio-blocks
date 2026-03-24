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

package zio.blocks.otel

import zio.test._

object LogRecordSpec extends ZIOSpecDefault {

  def spec = suite("LogRecord")(
    suite("Severity")(
      suite("levels")(
        test("Trace has number 1") {
          assertTrue(Severity.Trace.number == 1)
        },
        test("Trace2 has number 2") {
          assertTrue(Severity.Trace2.number == 2)
        },
        test("Trace3 has number 3") {
          assertTrue(Severity.Trace3.number == 3)
        },
        test("Trace4 has number 4") {
          assertTrue(Severity.Trace4.number == 4)
        },
        test("Debug has number 5") {
          assertTrue(Severity.Debug.number == 5)
        },
        test("Debug2 has number 6") {
          assertTrue(Severity.Debug2.number == 6)
        },
        test("Debug3 has number 7") {
          assertTrue(Severity.Debug3.number == 7)
        },
        test("Debug4 has number 8") {
          assertTrue(Severity.Debug4.number == 8)
        },
        test("Info has number 9") {
          assertTrue(Severity.Info.number == 9)
        },
        test("Info2 has number 10") {
          assertTrue(Severity.Info2.number == 10)
        },
        test("Info3 has number 11") {
          assertTrue(Severity.Info3.number == 11)
        },
        test("Info4 has number 12") {
          assertTrue(Severity.Info4.number == 12)
        },
        test("Warn has number 13") {
          assertTrue(Severity.Warn.number == 13)
        },
        test("Warn2 has number 14") {
          assertTrue(Severity.Warn2.number == 14)
        },
        test("Warn3 has number 15") {
          assertTrue(Severity.Warn3.number == 15)
        },
        test("Warn4 has number 16") {
          assertTrue(Severity.Warn4.number == 16)
        },
        test("Error has number 17") {
          assertTrue(Severity.Error.number == 17)
        },
        test("Error2 has number 18") {
          assertTrue(Severity.Error2.number == 18)
        },
        test("Error3 has number 19") {
          assertTrue(Severity.Error3.number == 19)
        },
        test("Error4 has number 20") {
          assertTrue(Severity.Error4.number == 20)
        },
        test("Fatal has number 21") {
          assertTrue(Severity.Fatal.number == 21)
        },
        test("Fatal2 has number 22") {
          assertTrue(Severity.Fatal2.number == 22)
        },
        test("Fatal3 has number 23") {
          assertTrue(Severity.Fatal3.number == 23)
        },
        test("Fatal4 has number 24") {
          assertTrue(Severity.Fatal4.number == 24)
        }
      ),
      suite("text values")(
        test("Trace has text TRACE") {
          assertTrue(Severity.Trace.text == "TRACE")
        },
        test("Debug has text DEBUG") {
          assertTrue(Severity.Debug.text == "DEBUG")
        },
        test("Info has text INFO") {
          assertTrue(Severity.Info.text == "INFO")
        },
        test("Warn has text WARN") {
          assertTrue(Severity.Warn.text == "WARN")
        },
        test("Error has text ERROR") {
          assertTrue(Severity.Error.text == "ERROR")
        },
        test("Fatal has text FATAL") {
          assertTrue(Severity.Fatal.text == "FATAL")
        },
        test("Trace2 inherits TRACE text") {
          assertTrue(Severity.Trace2.text == "TRACE")
        },
        test("Info3 inherits INFO text") {
          assertTrue(Severity.Info3.text == "INFO")
        }
      ),
      suite("fromNumber")(
        test("returns Trace for 1") {
          assertTrue(Severity.fromNumber(1) == Some(Severity.Trace))
        },
        test("returns Warn for 13") {
          assertTrue(Severity.fromNumber(13) == Some(Severity.Warn))
        },
        test("returns Fatal4 for 24") {
          assertTrue(Severity.fromNumber(24) == Some(Severity.Fatal4))
        },
        test("returns None for 0") {
          assertTrue(Severity.fromNumber(0).isEmpty)
        },
        test("returns None for 25") {
          assertTrue(Severity.fromNumber(25).isEmpty)
        },
        test("returns None for negative") {
          assertTrue(Severity.fromNumber(-1).isEmpty)
        }
      ),
      suite("fromText")(
        test("returns Some(Trace) for 'TRACE'") {
          assertTrue(Severity.fromText("TRACE").isDefined)
        },
        test("returns Some for lowercase 'trace'") {
          assertTrue(Severity.fromText("trace").isDefined)
        },
        test("returns Some for mixed case 'TrAcE'") {
          assertTrue(Severity.fromText("TrAcE").isDefined)
        },
        test("returns Some for 'DEBUG'") {
          assertTrue(Severity.fromText("DEBUG").isDefined)
        },
        test("returns Some for 'INFO'") {
          assertTrue(Severity.fromText("INFO").isDefined)
        },
        test("returns Some for 'WARN'") {
          assertTrue(Severity.fromText("WARN").isDefined)
        },
        test("returns Some for 'ERROR'") {
          assertTrue(Severity.fromText("ERROR").isDefined)
        },
        test("returns Some for 'FATAL'") {
          assertTrue(Severity.fromText("FATAL").isDefined)
        },
        test("returns None for invalid text") {
          assertTrue(Severity.fromText("INVALID").isEmpty)
        },
        test("returns None for empty string") {
          assertTrue(Severity.fromText("").isEmpty)
        }
      )
    ),
    suite("LogRecord")(
      suite("creation")(
        test("creates with all fields") {
          val record = LogRecord(
            timestampNanos = 1000L,
            observedTimestampNanos = 2000L,
            severity = Severity.Info,
            severityText = "INFO",
            body = "Test log",
            attributes = Attributes.empty,
            traceId = None,
            spanId = None,
            traceFlags = None,
            resource = Resource.empty,
            instrumentationScope = InstrumentationScope(name = "unknown")
          )
          assertTrue(record.timestampNanos == 1000L && record.body == "Test log")
        }
      ),
      suite("builder")(
        test("builder creates default LogRecord") {
          val record = LogRecord.builder.build
          assertTrue(
            record.severity == Severity.Info &&
              record.severityText == "INFO" &&
              record.body == "" &&
              record.attributes == Attributes.empty &&
              record.traceId.isEmpty &&
              record.spanId.isEmpty &&
              record.traceFlags.isEmpty
          )
        },
        test("builder setTimestamp") {
          val record = LogRecord.builder.setTimestamp(5000L).build
          assertTrue(record.timestampNanos == 5000L)
        },
        test("builder setSeverity") {
          val record = LogRecord.builder.setSeverity(Severity.Error).build
          assertTrue(record.severity == Severity.Error && record.severityText == "ERROR")
        },
        test("builder setBody") {
          val record = LogRecord.builder.setBody("Test message").build
          assertTrue(record.body == "Test message")
        },
        test("builder setAttribute") {
          val record = LogRecord.builder
            .setAttribute(AttributeKey.string("key1"), "value1")
            .build
          assertTrue(record.attributes.get(AttributeKey.string("key1")) == Some("value1"))
        },
        test("builder setAttribute accumulates multiple attributes") {
          val record = LogRecord.builder
            .setAttribute(AttributeKey.string("key1"), "value1")
            .setAttribute(AttributeKey.string("key2"), "value2")
            .setAttribute(AttributeKey.long("key3"), 42L)
            .build
          assertTrue(
            record.attributes.get(AttributeKey.string("key1")) == Some("value1") &&
              record.attributes.get(AttributeKey.string("key2")) == Some("value2") &&
              record.attributes.get(AttributeKey.long("key3")) == Some(42L)
          )
        },
        test("builder setTraceId") {
          val traceId = TraceId.random
          val record  = LogRecord.builder.setTraceId(traceId).build
          assertTrue(record.traceId == Some(traceId))
        },
        test("builder setSpanId") {
          val spanId = SpanId.random
          val record = LogRecord.builder.setSpanId(spanId).build
          assertTrue(record.spanId == Some(spanId))
        },
        test("builder setTraceFlags") {
          val flags  = TraceFlags.sampled
          val record = LogRecord.builder.setTraceFlags(flags).build
          assertTrue(record.traceFlags == Some(flags))
        },
        test("builder setResource") {
          val resource = Resource.empty
          val record   = LogRecord.builder.setResource(resource).build
          assertTrue(record.resource == resource)
        },
        test("builder setInstrumentationScope") {
          val scope  = InstrumentationScope(name = "unknown")
          val record = LogRecord.builder.setInstrumentationScope(scope).build
          assertTrue(record.instrumentationScope == scope)
        },
        test("builder chains multiple calls") {
          val traceId = TraceId.random
          val spanId  = SpanId.random
          val record  = LogRecord.builder
            .setTimestamp(1000L)
            .setSeverity(Severity.Warn)
            .setBody("Warning message")
            .setTraceId(traceId)
            .setSpanId(spanId)
            .build
          assertTrue(
            record.timestampNanos == 1000L &&
              record.severity == Severity.Warn &&
              record.body == "Warning message" &&
              record.traceId == Some(traceId) &&
              record.spanId == Some(spanId)
          )
        }
      ),
      suite("immutability")(
        test("LogRecord cannot be modified after creation") {
          val record1 = LogRecord(
            timestampNanos = 1000L,
            observedTimestampNanos = 2000L,
            severity = Severity.Info,
            severityText = "INFO",
            body = "Test",
            attributes = Attributes.empty,
            traceId = None,
            spanId = None,
            traceFlags = None,
            resource = Resource.empty,
            instrumentationScope = InstrumentationScope(name = "unknown")
          )
          val record2 = record1.copy(body = "Modified")
          assertTrue(record1.body == "Test" && record2.body == "Modified")
        }
      ),
      suite("trace correlation")(
        test("LogRecord preserves trace context") {
          val traceId = TraceId.random
          val spanId  = SpanId.random
          val flags   = TraceFlags.sampled
          val record  = LogRecord(
            timestampNanos = 1000L,
            observedTimestampNanos = 2000L,
            severity = Severity.Error,
            severityText = "ERROR",
            body = "Error occurred",
            attributes = Attributes.empty,
            traceId = Some(traceId),
            spanId = Some(spanId),
            traceFlags = Some(flags),
            resource = Resource.empty,
            instrumentationScope = InstrumentationScope(name = "unknown")
          )
          assertTrue(
            record.traceId == Some(traceId) &&
              record.spanId == Some(spanId) &&
              record.traceFlags == Some(flags)
          )
        }
      )
    )
  )
}

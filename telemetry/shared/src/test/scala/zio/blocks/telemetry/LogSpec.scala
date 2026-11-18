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

object LogSpec extends ZIOSpecDefault {

  private class TestLogProcessor extends LogRecordProcessor {
    val emitted: ArrayBuffer[LogRecord]    = ArrayBuffer.empty
    def onEmit(logRecord: LogRecord): Unit = emitted += logRecord
    def shutdown(): Unit                   = ()
    def forceFlush(): Unit                 = ()
  }

  /**
   * Named fixture: the `log.info` call sits directly in `emitProbe`'s body, so
   * both the Scala 2 and Scala 3 macros must report this exact owner and method
   * — no closures or indirection in between.
   */
  object StableCodeFixture {
    def emitProbe(): Unit = log.info("stable-code-probe")
  }

  private def withLogger(
    minSeverity: Severity = Severity.Trace,
    resource: Resource = Resource.empty
  )(
    f: TestLogProcessor => Unit
  ): TestLogProcessor = {
    val processor = new TestLogProcessor
    val provider  = LoggerProvider.builder
      .addLogRecordProcessor(processor)
      .setResource(resource)
      .build()
    val logger = provider.get("test")
    log.install(logger, minSeverity)
    try f(processor)
    finally {
      log.clearAllOverrides()
      log.removeAll()
    }
    processor
  }

  def spec: Spec[Any, Nothing] = suite("log")(
    test("does nothing after removeAll") {
      log.removeAll()
      log.info("should not crash")
      assertTrue(true)
    },
    test("emits when log is installed") {
      val processor = withLogger() { _ =>
        log.info("hello")
      }
      assertTrue(
        processor.emitted.size == 1,
        processor.emitted.head.body.value == "hello",
        processor.emitted.head.severity == Severity.Info
      )
    },
    test("emits logger resource through log facade") {
      val resource = Resource.create(
        Attributes.of(AttributeKey.string("service.name"), "test-service")
      )
      val processor = withLogger(resource = resource) { _ =>
        log.info("hello")
      }
      assertTrue(
        processor.emitted.size == 1,
        processor.emitted.head.resource == resource,
        processor.emitted.head.resource.attributes.get(AttributeKey.string("service.name")).contains("test-service")
      )
    },
    test("macro emits the exact fully qualified code.function.name") {
      val processor = withLogger() { _ =>
        StableCodeFixture.emitProbe()
      }
      val record = processor.emitted.head
      assertTrue(
        record.sourceFunction == "zio.blocks.telemetry.LogSpec.StableCodeFixture.emitProbe",
        record.sourceFile.endsWith("LogSpec.scala"),
        record.sourceLine > 0,
        record.attribute(AttributeKey.string("code.filepath")).isEmpty,
        record.attribute(AttributeKey.string("code.namespace")).isEmpty,
        record.attribute(AttributeKey.string("code.function")).isEmpty,
        record.attribute(AttributeKey.long("code.lineno")).isEmpty
      )
    },
    test("level overrides match the raw module namespace") {
      // Gating uses the raw enclosing-class identity from a version-specific
      // constant (no runtime probing): on Scala 3 the `$` prefix is not a
      // prefix of the normalized emitted name, so a normalized lookup would
      // drop the probe and fail this test there.
      val rawPrefix = RawFixtureNamespace.value
      val processor = withLogger(Severity.Warn) { _ =>
        log.setMinSeverity(rawPrefix, Severity.Debug)
        StableCodeFixture.emitProbe()
      }
      assertTrue(
        processor.emitted.size == 1,
        processor.emitted.head.body.value == "stable-code-probe"
      )
    },
    test("programmatic emit records an explicit source location exactly") {
      val processor = withLogger() { _ =>
        log.emit(Severity.Info, "explicit", SourceLocation("src/Svc.scala", "com.example.Svc", "doWork", 7))
      }
      val record = processor.emitted.head
      assertTrue(
        record.body.value == "explicit",
        record.sourceFile == "src/Svc.scala",
        record.sourceFunction == "com.example.Svc.doWork",
        record.sourceLine == 7,
        record.attribute("code.file.path").isEmpty,
        record.attribute("code.function.name").isEmpty,
        record.attribute("code.line.number").isEmpty
      )
    },
    test("programmatic records handle empty namespace and method") {
      def functionNameOf(location: SourceLocation): String =
        log.baseRecord(Severity.Info, "m", location).sourceFunction
      assertTrue(
        functionNameOf(SourceLocation("f.scala", "com.example.Svc", "doWork", 7)) == "com.example.Svc.doWork",
        functionNameOf(SourceLocation("f.scala", "", "go", 3)) == "go",
        functionNameOf(SourceLocation("f.scala", "com.example.Svc", "", 3)) == "com.example.Svc",
        functionNameOf(SourceLocation("f.scala", "", "", 0)) == ""
      )
    },
    test("level filtering skips messages below minSeverity") {
      val processor = withLogger(Severity.Info) { _ =>
        log.debug("should be skipped")
        log.info("should be emitted")
      }
      assertTrue(
        processor.emitted.size == 1,
        processor.emitted.head.body.value == "should be emitted"
      )
    },
    test("all six severity methods work") {
      val processor = withLogger() { _ =>
        log.trace("t")
        log.debug("d")
        log.info("i")
        log.warn("w")
        log.error("e")
        log.fatal("f")
      }
      assertTrue(
        processor.emitted.size == 6,
        processor.emitted(0).severity == Severity.Trace,
        processor.emitted(1).severity == Severity.Debug,
        processor.emitted(2).severity == Severity.Info,
        processor.emitted(3).severity == Severity.Warn,
        processor.emitted(4).severity == Severity.Error,
        processor.emitted(5).severity == Severity.Fatal
      )
    },
    suite("LogEnrichment")(
      test("string enrichment sets log body") {
        val processor = withLogger() { _ =>
          log.info("hello world")
        }
        assertTrue(
          processor.emitted.size == 1,
          processor.emitted.head.body.value == "hello world"
        )
      },
      test("key-value string enrichment adds attribute") {
        val processor = withLogger() { _ =>
          log.info("msg", "userId" -> "abc")
        }
        val record = processor.emitted.head
        assertTrue(
          processor.emitted.size == 1,
          processor.emitted.head.body.value == "msg",
          record.attribute(AttributeKey.string("userId")).contains("abc")
        )
      },
      test("key-value long enrichment adds attribute") {
        val processor = withLogger() { _ =>
          log.info("msg", "count" -> 42L)
        }
        val record = processor.emitted.head
        assertTrue(
          processor.emitted.size == 1,
          record.attribute(AttributeKey.long("count")).contains(42L)
        )
      },
      test("key-value int enrichment adds attribute") {
        val processor = withLogger() { _ =>
          log.info("msg", "count" -> 42)
        }
        val record = processor.emitted.head
        assertTrue(
          processor.emitted.size == 1,
          record.attribute(AttributeKey.long("count")).contains(42L)
        )
      },
      test("key-value double enrichment adds attribute") {
        val processor = withLogger() { _ =>
          log.info("msg", "score" -> 3.14)
        }
        val record = processor.emitted.head
        assertTrue(
          processor.emitted.size == 1,
          record.attribute(AttributeKey.double("score")).contains(3.14)
        )
      },
      test("key-value boolean enrichment adds attribute") {
        val processor = withLogger() { _ =>
          log.info("msg", "active" -> true)
        }
        val record = processor.emitted.head
        assertTrue(
          processor.emitted.size == 1,
          record.attribute(AttributeKey.boolean("active")).contains(true)
        )
      },
      test("throwable enrichment adds exception attributes") {
        val ex: Throwable = new RuntimeException("boom")
        val processor     = withLogger() { _ =>
          log.info("failed", ex)
        }
        val record = processor.emitted.head
        assertTrue(
          processor.emitted.size == 1,
          processor.emitted.head.body.value == "failed",
          record.attribute(AttributeKey.string("exception.type")).contains("java.lang.RuntimeException"),
          record.attribute(AttributeKey.string("exception.message")).contains("boom"),
          processor.emitted.head.throwable.contains(ex)
        )
      },
      test("severity enrichment overrides level") {
        val sev: Severity = Severity.Error
        val processor     = withLogger() { _ =>
          log.info("msg", sev)
        }
        assertTrue(
          processor.emitted.size == 1,
          processor.emitted.head.severity == Severity.Error,
          processor.emitted.head.severityText == "ERROR"
        )
      },
      test("multiple enrichments combine") {
        val processor = withLogger() { _ =>
          log.info("msg", "k1" -> "v1", "k2" -> 42L)
        }
        val record = processor.emitted.head
        assertTrue(
          processor.emitted.size == 1,
          processor.emitted.head.body.value == "msg",
          record.attribute(AttributeKey.string("k1")).contains("v1"),
          record.attribute(AttributeKey.long("k2")).contains(42L)
        )
      },
      test("Logger.info preserves seq attribute Seq structure and type") {
        val processor = new TestLogProcessor
        val logger    = LoggerProvider.builder
          .addLogRecordProcessor(processor)
          .build()
          .get("test")
        logger.info(
          "msg",
          "tags"  -> AttributeValue.StringSeqValue(Seq("a", "b")),
          "nums"  -> AttributeValue.LongSeqValue(Seq(1L, 2L)),
          "rates" -> AttributeValue.DoubleSeqValue(Seq(1.5, 2.5)),
          "flags" -> AttributeValue.BooleanSeqValue(Seq(true, false))
        )
        val record = processor.emitted.head
        assertTrue(
          processor.emitted.size == 1,
          record.attribute(AttributeKey.stringSeq("tags")).contains(Seq("a", "b")),
          record.attribute(AttributeKey.longSeq("nums")).contains(Seq(1L, 2L)),
          record.attribute(AttributeKey.doubleSeq("rates")).contains(Seq(1.5, 2.5)),
          record.attribute(AttributeKey.booleanSeq("flags")).contains(Seq(true, false))
        )
      }
    ) @@ TestAspect.sequential,
    test("processor failures do not stop later processors") {
      val emitted = ArrayBuffer.empty[LogRecord]
      val failing = new LogRecordProcessor {
        def onEmit(logRecord: LogRecord): Unit = throw new RuntimeException("boom")
        def shutdown(): Unit                   = ()
        def forceFlush(): Unit                 = ()
      }
      val collecting = new LogRecordProcessor {
        def onEmit(logRecord: LogRecord): Unit = emitted += logRecord
        def shutdown(): Unit                   = ()
        def forceFlush(): Unit                 = ()
      }

      val logger = LoggerProvider.builder
        .addLogRecordProcessor(failing)
        .addLogRecordProcessor(collecting)
        .build()
        .get("test")

      logger.info("survives")

      assertTrue(emitted.size == 1, emitted.head.body.value == "survives")
    },
    suite("Hierarchical log levels")(
      test("specific prefix overrides general level") {
        val processor = withLogger(Severity.Warn) { _ =>
          log.setMinSeverity("zio.blocks.telemetry", Severity.Debug)
          log.debug("should be emitted")
        }
        assertTrue(
          processor.emitted.size == 1,
          processor.emitted.head.body.value == "should be emitted"
        )
      },
      test("most specific prefix wins") {
        var debugLevel = 0
        var noisyLevel = 0
        var otherLevel = 0
        withLogger(Severity.Info) { _ =>
          log.setMinSeverity("com.example", Severity.Debug)
          log.setMinSeverity("com.example.noisy", Severity.Warn)
          val state = GlobalLogState.get()
          debugLevel = state.effectiveLevel("com.example.Service")
          noisyLevel = state.effectiveLevel("com.example.noisy.Thing")
          otherLevel = state.effectiveLevel("com.other.Foo")
        }
        assertTrue(
          debugLevel == Severity.Debug.number,
          noisyLevel == Severity.Warn.number,
          otherLevel == Severity.Info.number
        )
      },
      test("clearLevel removes override") {
        var level = 0
        withLogger(Severity.Info) { _ =>
          log.setMinSeverity("com.test", Severity.Debug)
          log.clearMinSeverity("com.test")
          level = GlobalLogState.get().effectiveLevel("com.test.Foo")
        }
        assertTrue(level == Severity.Info.number)
      },
      test("clearAllLevels removes all overrides") {
        var aLevel = 0
        var bLevel = 0
        withLogger(Severity.Info) { _ =>
          log.setMinSeverity("a", Severity.Debug)
          log.setMinSeverity("b", Severity.Warn)
          log.clearAllOverrides()
          val state = GlobalLogState.get()
          aLevel = state.effectiveLevel("a.Foo")
          bLevel = state.effectiveLevel("b.Bar")
        }
        assertTrue(
          aLevel == Severity.Info.number,
          bLevel == Severity.Info.number
        )
      }
    ) @@ TestAspect.sequential,
    suite("log.annotated")(
      test("annotations attach to log records") {
        val processor = withLogger() { _ =>
          log.annotated("requestId" -> "abc") {
            log.info("test")
          }
        }
        val record = processor.emitted.head
        assertTrue(
          processor.emitted.size == 1,
          record.attribute(AttributeKey.string("requestId")).contains("abc")
        )
      },
      test("annotations are removed after scope exits") {
        val processor = withLogger() { _ =>
          log.annotated("k" -> "v") {
            log.info("inside")
          }
          log.info("outside")
        }
        val inside  = processor.emitted(0)
        val outside = processor.emitted(1)
        assertTrue(
          processor.emitted.size == 2,
          processor.emitted(0).body.value == "inside",
          processor.emitted(1).body.value == "outside",
          inside.attribute(AttributeKey.string("k")).contains("v"),
          outside.attribute(AttributeKey.string("k")).isEmpty
        )
      },
      test("nested annotations merge") {
        val processor = withLogger() { _ =>
          log.annotated("a" -> "1") {
            log.annotated("b" -> "2") {
              log.info("nested")
            }
          }
        }
        val record = processor.emitted.head
        assertTrue(
          processor.emitted.size == 1,
          record.attribute(AttributeKey.string("a")).contains("1"),
          record.attribute(AttributeKey.string("b")).contains("2")
        )
      },
      test("annotations cleanup on exception") {
        val processor = withLogger() { _ =>
          try
            log.annotated("k" -> "v") {
              throw new RuntimeException("boom")
            }
          catch {
            case _: RuntimeException => ()
          }
          log.info("after")
        }
        val after = processor.emitted.head
        assertTrue(
          processor.emitted.size == 1,
          processor.emitted.head.body.value == "after",
          after.attribute(AttributeKey.string("k")).isEmpty
        )
      }
    ) @@ TestAspect.sequential
  ) @@ TestAspect.sequential @@ TestAspect.withLiveClock
}

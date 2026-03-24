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

import scala.collection.mutable.ArrayBuffer

object LogSpec extends ZIOSpecDefault {

  private class TestLogProcessor extends LogRecordProcessor {
    val emitted: ArrayBuffer[LogRecord]    = ArrayBuffer.empty
    def onEmit(logRecord: LogRecord): Unit = emitted += logRecord
    def shutdown(): Unit                   = ()
    def forceFlush(): Unit                 = ()
  }

  private def withLogger(minSeverity: Severity = Severity.Trace)(
    f: TestLogProcessor => Unit
  ): TestLogProcessor = {
    val processor = new TestLogProcessor
    val provider  = LoggerProvider.builder
      .addLogRecordProcessor(processor)
      .setResource(Resource.empty)
      .build()
    val logger = provider.get("test")
    GlobalLogState.install(logger, minSeverity)
    try f(processor)
    finally {
      GlobalLogState.clearAllLevels()
      GlobalLogState.uninstall()
    }
    processor
  }

  def spec: Spec[Any, Nothing] = suite("log")(
    test("does nothing when GlobalLogState is not installed") {
      GlobalLogState.uninstall()
      log.info("should not crash")
      assertTrue(true)
    },
    test("emits when GlobalLogState is installed") {
      val processor = withLogger() { _ =>
        log.info("hello")
      }
      assertTrue(
        processor.emitted.size == 1,
        processor.emitted.head.body == "hello",
        processor.emitted.head.severity == Severity.Info
      )
    },
    test("source location attributes are present") {
      val processor = withLogger() { _ =>
        log.info("located")
      }
      val attrs = processor.emitted.head.attributes
      assertTrue(
        attrs.get(AttributeKey.string("code.filepath")).isDefined,
        attrs.get(AttributeKey.string("code.namespace")).isDefined,
        attrs.get(AttributeKey.string("code.function")).isDefined,
        attrs.get(AttributeKey.long("code.lineno")).isDefined
      )
    },
    test("level filtering skips messages below minSeverity") {
      val processor = withLogger(Severity.Info) { _ =>
        log.debug("should be skipped")
        log.info("should be emitted")
      }
      assertTrue(
        processor.emitted.size == 1,
        processor.emitted.head.body == "should be emitted"
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
          processor.emitted.head.body == "hello world"
        )
      },
      test("key-value string enrichment adds attribute") {
        val processor = withLogger() { _ =>
          log.info("msg", "userId" -> "abc")
        }
        val attrs = processor.emitted.head.attributes
        assertTrue(
          processor.emitted.size == 1,
          processor.emitted.head.body == "msg",
          attrs.get(AttributeKey.string("userId")).contains("abc")
        )
      },
      test("key-value long enrichment adds attribute") {
        val processor = withLogger() { _ =>
          log.info("msg", "count" -> 42L)
        }
        val attrs = processor.emitted.head.attributes
        assertTrue(
          processor.emitted.size == 1,
          attrs.get(AttributeKey.long("count")).contains(42L)
        )
      },
      test("key-value int enrichment adds attribute") {
        val processor = withLogger() { _ =>
          log.info("msg", "count" -> 42)
        }
        val attrs = processor.emitted.head.attributes
        assertTrue(
          processor.emitted.size == 1,
          attrs.get(AttributeKey.long("count")).contains(42L)
        )
      },
      test("key-value double enrichment adds attribute") {
        val processor = withLogger() { _ =>
          log.info("msg", "score" -> 3.14)
        }
        val attrs = processor.emitted.head.attributes
        assertTrue(
          processor.emitted.size == 1,
          attrs.get(AttributeKey.double("score")).contains(3.14)
        )
      },
      test("key-value boolean enrichment adds attribute") {
        val processor = withLogger() { _ =>
          log.info("msg", "active" -> true)
        }
        val attrs = processor.emitted.head.attributes
        assertTrue(
          processor.emitted.size == 1,
          attrs.get(AttributeKey.boolean("active")).contains(true)
        )
      },
      test("throwable enrichment adds exception attributes") {
        val ex: Throwable = new RuntimeException("boom")
        val processor     = withLogger() { _ =>
          log.info("failed", ex)
        }
        val attrs = processor.emitted.head.attributes
        assertTrue(
          processor.emitted.size == 1,
          processor.emitted.head.body == "failed",
          attrs.get(AttributeKey.string("exception.type")).contains("java.lang.RuntimeException"),
          attrs.get(AttributeKey.string("exception.message")).contains("boom"),
          attrs.get(AttributeKey.string("exception.stacktrace")).isDefined
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
        val attrs = processor.emitted.head.attributes
        assertTrue(
          processor.emitted.size == 1,
          processor.emitted.head.body == "msg",
          attrs.get(AttributeKey.string("k1")).contains("v1"),
          attrs.get(AttributeKey.long("k2")).contains(42L)
        )
      }
    ) @@ TestAspect.sequential,
    suite("Hierarchical log levels")(
      test("specific prefix overrides general level") {
        val processor = withLogger(Severity.Warn) { _ =>
          GlobalLogState.setLevel("zio.blocks.otel", Severity.Debug)
          log.debug("should be emitted")
        }
        assertTrue(
          processor.emitted.size == 1,
          processor.emitted.head.body == "should be emitted"
        )
      },
      test("most specific prefix wins") {
        var debugLevel = 0
        var noisyLevel = 0
        var otherLevel = 0
        withLogger(Severity.Info) { _ =>
          GlobalLogState.setLevel("com.example", Severity.Debug)
          GlobalLogState.setLevel("com.example.noisy", Severity.Warn)
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
          GlobalLogState.setLevel("com.test", Severity.Debug)
          GlobalLogState.clearLevel("com.test")
          level = GlobalLogState.get().effectiveLevel("com.test.Foo")
        }
        assertTrue(level == Severity.Info.number)
      },
      test("clearAllLevels removes all overrides") {
        var aLevel = 0
        var bLevel = 0
        withLogger(Severity.Info) { _ =>
          GlobalLogState.setLevel("a", Severity.Debug)
          GlobalLogState.setLevel("b", Severity.Warn)
          GlobalLogState.clearAllLevels()
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
        val attrs = processor.emitted.head.attributes
        assertTrue(
          processor.emitted.size == 1,
          attrs.get(AttributeKey.string("requestId")).contains("abc")
        )
      },
      test("annotations are removed after scope exits") {
        val processor = withLogger() { _ =>
          log.annotated("k" -> "v") {
            log.info("inside")
          }
          log.info("outside")
        }
        val insideAttrs  = processor.emitted(0).attributes
        val outsideAttrs = processor.emitted(1).attributes
        assertTrue(
          processor.emitted.size == 2,
          processor.emitted(0).body == "inside",
          processor.emitted(1).body == "outside",
          insideAttrs.get(AttributeKey.string("k")).contains("v"),
          outsideAttrs.get(AttributeKey.string("k")).isEmpty
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
        val attrs = processor.emitted.head.attributes
        assertTrue(
          processor.emitted.size == 1,
          attrs.get(AttributeKey.string("a")).contains("1"),
          attrs.get(AttributeKey.string("b")).contains("2")
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
        val afterAttrs = processor.emitted.head.attributes
        assertTrue(
          processor.emitted.size == 1,
          processor.emitted.head.body == "after",
          afterAttrs.get(AttributeKey.string("k")).isEmpty
        )
      }
    ) @@ TestAspect.sequential
  ) @@ TestAspect.sequential @@ TestAspect.withLiveClock
}

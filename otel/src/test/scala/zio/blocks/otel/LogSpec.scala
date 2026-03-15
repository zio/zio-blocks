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
    finally GlobalLogState.uninstall()
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
    }
  )
}

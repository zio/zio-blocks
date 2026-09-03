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

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets

import scala.collection.mutable.ArrayBuffer

object LoggerDirectSpec extends ZIOSpecDefault {

  private final class RecordingProcessor(level: Int) extends LogRecordProcessor {
    val seen: ArrayBuffer[LogRecord] = ArrayBuffer.empty

    override def minimumLevel: Int = level

    override def onEmit(logRecord: LogRecord): Unit =
      seen += logRecord

    override def shutdown(): Unit   = ()
    override def forceFlush(): Unit = ()
  }

  private def captureStdout[A](body: => A): (A, String) = {
    val original = System.out
    val bytes    = new ByteArrayOutputStream()
    val stream   = new PrintStream(bytes, true, StandardCharsets.UTF_8.name)

    System.setOut(stream)
    try {
      val result = Console.withOut(stream)(body)
      stream.flush()
      (result, bytes.toString(StandardCharsets.UTF_8.name))
    } finally {
      System.setOut(original)
      stream.close()
    }
  }

  private def consoleLogger(): Logger =
    LoggerProvider.builder
      .addLogRecordProcessor(new ConsoleLogRecordProcessor)
      .build()
      .get("direct")

  def spec = suite("LoggerDirect")(
    test("formatted emit without code attributes still prints every attribute") {
      // emitRaw with a builder that carries no code.* slots exercises the
      // builder-array formatter path directly (the macro path always writes
      // code.* first, so only this path could drop user attributes).
      val logger  = consoleLogger()
      val builder = Attributes.builder
        .put("user", "nabil")
        .put("tries", 3L)
        .put("ratio", 1.5)
        .put("active", true)
      val (_, out) = captureStdout {
        logger.emitRaw(
          EpochClock.epochNanos(),
          Severity.Info,
          "INFO",
          "hello",
          builder,
          0L,
          0L,
          0L,
          0.toByte,
          Resource.empty,
          InstrumentationScope("direct"),
          None
        )
      }
      assertTrue(
        out.contains("hello"),
        out.contains("user=\"nabil\""),
        out.contains("tries=3"),
        out.contains("ratio=1.5"),
        out.contains("active=true")
      )
    },
    test("direct info with attributes prints every attribute") {
      val logger   = consoleLogger()
      val (_, out) = captureStdout {
        logger.info(
          "hello",
          "user"   -> AttributeValue.StringValue("nabil"),
          "tries"  -> AttributeValue.LongValue(3L),
          "ratio"  -> AttributeValue.DoubleValue(1.5),
          "active" -> AttributeValue.BooleanValue(true)
        )
      }
      assertTrue(
        out.contains("hello"),
        out.contains("user=\"nabil\""),
        out.contains("tries=3"),
        out.contains("ratio=1.5"),
        out.contains("active=true")
      )
    },
    test("direct call below the processor minimum level emits nothing") {
      val processor = new RecordingProcessor(Severity.Error.number)
      val logger    =
        LoggerProvider.builder
          .addLogRecordProcessor(processor)
          .build()
          .get("direct")
      logger.info("dropped", "k" -> AttributeValue.StringValue("v"))
      logger.debug("dropped")
      val dropped = processor.seen.isEmpty
      logger.error("kept", "k" -> AttributeValue.StringValue("v"))
      assertTrue(
        dropped,
        processor.seen.size == 1,
        processor.seen.head.body.value == "kept",
        processor.seen.head.attributes.get(AttributeKey.string("k")).contains("v")
      )
    },
    test("direct call at exactly the minimum level emits") {
      val processor = new RecordingProcessor(Severity.Warn.number)
      val logger    =
        LoggerProvider.builder
          .addLogRecordProcessor(processor)
          .build()
          .get("direct")
      logger.warn("boundary")
      assertTrue(
        processor.seen.size == 1,
        processor.seen.head.severity == Severity.Warn
      )
    }
  ) @@ TestAspect.sequential
}

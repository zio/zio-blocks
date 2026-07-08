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

object LogProcessorSpec extends ZIOSpecDefault {

  private final class TestLogWriter extends LogWriter {
    val writes: ArrayBuffer[String] = ArrayBuffer.empty
    var flushCount: Int             = 0
    var closeCount: Int             = 0

    override def write(content: CharSequence): Unit =
      writes += content.toString

    override def flush(): Unit =
      flushCount += 1

    override def close(): Unit =
      closeCount += 1
  }

  private final class ThrowingLogWriter(message: String) extends LogWriter {
    override def write(content: CharSequence): Unit =
      throw new IllegalStateException(message)
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

  private def captureStderr[A](body: => A): (A, String) = {
    val original = System.err
    val bytes    = new ByteArrayOutputStream()
    val stream   = new PrintStream(bytes, true, StandardCharsets.UTF_8.name)

    System.setErr(stream)
    try {
      val result = body
      stream.flush()
      (result, bytes.toString(StandardCharsets.UTF_8.name))
    } finally {
      System.setErr(original)
      stream.close()
    }
  }

  private def captureOutput[A](body: => A): (A, String, String) = {
    val originalOut = System.out
    val originalErr = System.err
    val outBytes    = new ByteArrayOutputStream()
    val errBytes    = new ByteArrayOutputStream()
    val outStream   = new PrintStream(outBytes, true, StandardCharsets.UTF_8.name)
    val errStream   = new PrintStream(errBytes, true, StandardCharsets.UTF_8.name)

    System.setOut(outStream)
    System.setErr(errStream)
    try {
      val result = body
      outStream.flush()
      errStream.flush()
      (
        result,
        outBytes.toString(StandardCharsets.UTF_8.name),
        errBytes.toString(StandardCharsets.UTF_8.name)
      )
    } finally {
      System.setOut(originalOut)
      System.setErr(originalErr)
      outStream.close()
      errStream.close()
    }
  }

  private def logRecord(
    namespace: String = "com.example.MyClass",
    method: Option[String] = Some("myMethod"),
    line: Long = 42L,
    includeUserAttrs: Boolean = true,
    includeOtherAttr: Boolean = true,
    throwable: Option[Throwable] = None
  ): LogRecord = {
    val builder = Attributes.builder
      .put("code.filepath", "Test.scala")
      .put("code.namespace", namespace)
      .put("code.lineno", line)

    method.foreach(builder.put("code.function", _))

    if (includeUserAttrs) {
      builder.put("userId", "abc")
      builder.put("count", 5L)
      builder.put("ratio", 2.5)
      builder.put("active", value = true)
    }

    if (includeOtherAttr) builder.put(AttributeKey.stringSeq("tags"), List("alpha", "beta"))

    LogRecord(
      timestampNanos = 1719792600123000000L,
      observedTimestampNanos = 1719792600123000000L,
      severity = Severity.Info,
      severityText = "INFO",
      body = LogMessage("test message"),
      attributes = builder.build,
      traceIdHi = 0L,
      traceIdLo = 0L,
      spanId = 0L,
      traceFlags = 0,
      resource = Resource.empty,
      instrumentationScope = InstrumentationScope("test"),
      throwable = throwable
    )
  }

  private def emitterBuilder(): Attributes.AttributesBuilder =
    Attributes.builder
      .put("code.filepath", "Emitter.scala")
      .put("code.namespace", "com.example.EmitterClass")
      .put("code.function", "emitMethod")
      .put("code.lineno", 7L)
      .put("userId", "abc")
      .put("count", 5L)

  def spec = suite("LogProcessor")(
    stdoutLogRecordProcessorSuite,
    consoleLogRecordProcessorSuite,
    formattedLogRecordProcessorSuite,
    formattedLogEmitterSuite,
    logWriterSuite
  ) @@ TestAspect.sequential

  private val stdoutLogRecordProcessorSuite = suite("StdoutLogRecordProcessor")(
    test("onEmit renders full log record with source location user attributes and throwable") {
      val processor      = new StdoutLogRecordProcessor()
      val failure        = new IllegalStateException("boom")
      val (_, rendered0) = captureStdout(processor.onEmit(logRecord(throwable = Some(failure))))
      val rendered       = rendered0.trim

      assertTrue(
        rendered.startsWith("2024-07-01T00:10:00.123Z INFO  [MyClass.myMethod:42] test message"),
        rendered.contains("userId=\"abc\""),
        rendered.contains("count=5"),
        rendered.contains("ratio=2.5"),
        rendered.contains("active=true"),
        rendered.contains("tags=StringSeqValue(List(alpha, beta))"),
        !rendered.contains("code.filepath"),
        !rendered.contains("code.namespace"),
        !rendered.contains("code.function"),
        !rendered.contains("code.lineno"),
        rendered.contains("java.lang.IllegalStateException: boom")
      )
    },
    test("onEmit handles namespace without dot method absent line zero and no throwable") {
      val processor      = new StdoutLogRecordProcessor()
      val (_, rendered0) = captureStdout(
        processor.onEmit(
          logRecord(
            namespace = "PlainClass",
            method = None,
            line = 0L,
            includeUserAttrs = false,
            includeOtherAttr = false,
            throwable = None
          )
        )
      )
      val rendered = rendered0.trim

      assertTrue(
        rendered.startsWith("2024-07-01T00:10:00.123Z INFO  [PlainClass] test message"),
        !rendered.contains("{"),
        !rendered.contains("Exception")
      )
    },
    test("shutdown and forceFlush are no-ops") {
      val processor = new StdoutLogRecordProcessor()

      processor.shutdown()
      processor.forceFlush()

      assertTrue(true)
    }
  )

  private val consoleLogRecordProcessorSuite = suite("ConsoleLogRecordProcessor")(
    test("onEmit renders full log record with source location user attributes and throwable") {
      val processor      = new ConsoleLogRecordProcessor()
      val failure        = new IllegalStateException("boom")
      val (_, rendered0) = captureStdout(processor.onEmit(logRecord(throwable = Some(failure))))
      val rendered       = rendered0.trim

      assertTrue(
        rendered.startsWith("2024-07-01T00:10:00.123Z INFO  [MyClass.myMethod:42] test message"),
        rendered.contains("userId=\"abc\""),
        rendered.contains("count=5"),
        rendered.contains("ratio=2.5"),
        rendered.contains("active=true"),
        rendered.contains("tags=StringSeqValue(List(alpha, beta))"),
        !rendered.contains("code.filepath"),
        !rendered.contains("code.namespace"),
        !rendered.contains("code.function"),
        !rendered.contains("code.lineno"),
        rendered.contains("java.lang.IllegalStateException: boom")
      )
    },
    test("shutdown and forceFlush are no-ops") {
      val processor = new ConsoleLogRecordProcessor()

      processor.shutdown()
      processor.forceFlush()

      assertTrue(true)
    }
  )

  private val formattedLogRecordProcessorSuite = suite("FormattedLogRecordProcessor")(
    test("onEmit writes text formatted content") {
      val writer    = new TestLogWriter()
      val processor = new FormattedLogRecordProcessor(TextLogFormatter, writer)

      processor.onEmit(logRecord(includeOtherAttr = false))

      val rendered = writer.writes.headOption.getOrElse("")
      assertTrue(
        writer.writes.size == 1,
        rendered.startsWith("2024-07-01T00:10:00.123Z INFO  [(com.example.MyClass,12,19).myMethod:42] test message"),
        rendered.contains("userId=\"abc\""),
        rendered.contains("count=5"),
        rendered.contains("ratio=2.5"),
        rendered.contains("active=true")
      )
    },
    test("onEmit writes json formatted content") {
      val writer    = new TestLogWriter()
      val processor = new FormattedLogRecordProcessor(JsonLogFormatter, writer)

      processor.onEmit(logRecord(includeOtherAttr = false))

      val rendered = writer.writes.headOption.getOrElse("")
      assertTrue(
        writer.writes.size == 1,
        rendered.contains("\"timeUnixNano\":\"1719792600123000000\""),
        rendered.contains("\"severityNumber\":9"),
        rendered.contains("\"severityText\":\"INFO\""),
        rendered.contains("\"body\":{\"stringValue\":\"test message\"}"),
        rendered.contains("\"key\":\"userId\",\"value\":{\"stringValue\":\"abc\"}}"),
        rendered.contains("\"key\":\"count\",\"value\":{\"intValue\":\"5\"}}"),
        rendered.contains("\"key\":\"ratio\",\"value\":{\"doubleValue\":2.5}}"),
        rendered.contains("\"key\":\"active\",\"value\":{\"boolValue\":true}}")
      )
    },
    test("shutdown calls writer close") {
      val writer    = new TestLogWriter()
      val processor = new FormattedLogRecordProcessor(TextLogFormatter, writer)

      processor.shutdown()

      assertTrue(writer.closeCount == 1)
    },
    test("forceFlush calls writer flush") {
      val writer    = new TestLogWriter()
      val processor = new FormattedLogRecordProcessor(TextLogFormatter, writer)

      processor.forceFlush()

      assertTrue(writer.flushCount == 1)
    }
  )

  private val formattedLogEmitterSuite = suite("FormattedLogEmitter")(
    test("emit writes formatted content and clears builder") {
      val writer    = new TestLogWriter()
      val emitter   = new FormattedLogEmitter(TextLogFormatter, writer)
      val builder   = emitterBuilder()
      val throwable = new RuntimeException("emit boom")

      emitter.emit(
        timestampNanos = 1719792600123000000L,
        severity = Severity.Info,
        severityText = "INFO",
        body = "emitted message",
        builder = builder,
        traceIdHi = 0L,
        traceIdLo = 0L,
        spanId = 0L,
        traceFlags = 1.toByte,
        resource = Resource.empty,
        instrumentationScope = InstrumentationScope("test"),
        throwable = Some(throwable)
      )

      val rendered = writer.writes.headOption.getOrElse("")
      assertTrue(
        writer.writes.size == 1,
        rendered.startsWith(
          "2024-07-01T00:10:00.123Z INFO  [(com.example.EmitterClass,12,24).emitMethod:7] emitted message"
        ),
        rendered.contains("userId=\"abc\""),
        rendered.contains("count=5"),
        rendered.contains("java.lang.RuntimeException: emit boom"),
        builder.builderLen == 0
      )
    },
    test("emit swallows writer NonFatal exceptions and reports to stderr") {
      val emitter       = new FormattedLogEmitter(TextLogFormatter, new ThrowingLogWriter("write failed"))
      val builder       = emitterBuilder()
      val (_, rendered) = captureStderr(
        emitter.emit(
          timestampNanos = 1719792600123000000L,
          severity = Severity.Info,
          severityText = "INFO",
          body = "emitted message",
          builder = builder,
          traceIdHi = 0L,
          traceIdLo = 0L,
          spanId = 0L,
          traceFlags = 0,
          resource = Resource.empty,
          instrumentationScope = InstrumentationScope("test"),
          throwable = None
        )
      )

      assertTrue(
        rendered.contains("[zio-blocks-telemetry] write error: write failed"),
        builder.builderLen == 0
      )
    }
  )

  private val logWriterSuite = suite("LogWriter implementations")(
    test("StdoutWriter writes to stdout") {
      val (_, rendered0) = captureStdout(StdoutWriter.write("stdout message"))
      val rendered       = rendered0.trim

      assertTrue(rendered == "stdout message")
    },
    test("StderrWriter writes to stderr") {
      val (_, rendered0) = captureStderr(StderrWriter.write("stderr message"))
      val rendered       = rendered0.trim

      assertTrue(rendered == "stderr message")
    },
    test("NoopWriter write does not throw and produces no output") {
      val (_, stdout, stderr) = captureOutput {
        NoopWriter.write("ignored")
        NoopWriter.flush()
        NoopWriter.close()
      }

      assertTrue(stdout.isEmpty, stderr.isEmpty)
    }
  )
}

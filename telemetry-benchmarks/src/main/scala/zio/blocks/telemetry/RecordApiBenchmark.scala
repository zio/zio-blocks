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

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import java.util.concurrent.TimeUnit

import scala.compiletime.uninitialized

/**
 * Focused JMH for the final private-record API: source metadata travels as
 * dedicated scalars (never inserted into the attribute builder), user
 * attributes stay in the pooled builder, and exporters read source fields
 * directly.
 *
 * Cells: `emitRetain` runs the production `emitRaw` path (pooled builder
 * acquire, N user-attribute puts, `StandardLogEmitter` retained dispatch to a
 * single capturing processor); `formatRetained` renders a prebuilt retained
 * record with `TextLogFormatter.formatRecord`. Both sweep source present /
 * absent x 0 / 4 / 16 user attributes with 8 rotating callsites (defeats
 * constant folding). Run with `-prof gc` for bytes/op.
 */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
  value = 2,
  jvmArgs = Array("-server", "-Xms2g", "-Xmx2g", "-XX:+UseParallelGC")
)
class RecordApiBenchmark {
  @Param(Array("0", "4", "16"))
  var userAttrs: Int = 0

  @Param(Array("true", "false"))
  var sourcePresent: Boolean = false

  var counter: Long                  = 0L
  var files: Array[String]           = uninitialized
  var functions: Array[String]       = uninitialized
  var lines: Array[Int]              = uninitialized
  var bodies: Array[String]          = uninitialized
  var userKeys: Array[String]        = uninitialized
  var strVals: Array[String]         = uninitialized
  var retained: Array[LogRecord]     = uninitialized
  var out: StringBuilder             = uninitialized
  var scope: InstrumentationScope    = uninitialized
  var logger: Logger                 = uninitialized
  var capturing: LastRecordProcessor = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit = {
    files = Array.tabulate(8)(i => s"src/main/scala/com/example/Service$i.scala")
    functions = Array.tabulate(8)(i => s"com.example.Service$i.doWork")
    lines = Array.tabulate(8)(i => 100 + i * 17)
    bodies = Array.tabulate(8)(i => s"retained message $i")
    userKeys = Array.tabulate(16)(i => s"user.k$i")
    strVals = Array.tabulate(8)(i => s"value-$i")
    out = new StringBuilder(256)
    scope = InstrumentationScope(name = "bench")
    capturing = new LastRecordProcessor()
    logger = new Logger(
      scope,
      Resource.empty,
      Array(capturing),
      ContextStorage.create[Option[SpanContext]](None)
    )
    retained = Array.tabulate(8) { i =>
      val builder = Attributes.builder
      putUserAttrs(builder, i)
      val (file, function, line) =
        if (sourcePresent) (files(i), functions(i), lines(i))
        else ("", "", 0)
      LogRecord.create(
        timestampNanos = 1719792600123000000L,
        observedTimestampNanos = 1719792600123000000L,
        severity = Severity.Info,
        severityText = "INFO",
        body = LogMessage(bodies(i)),
        sourceFile = file,
        sourceFunction = function,
        sourceLine = line,
        userAttributes = builder.build,
        traceIdHi = 0L,
        traceIdLo = 0L,
        spanId = 0L,
        traceFlags = 0.toByte,
        resource = Resource.empty,
        instrumentationScope = scope,
        throwable = None
      )
    }
  }

  /**
   * Production emit path: pooled builder acquire, N mixed-type user puts,
   * scalar source handoff, retained dispatch.
   */
  @Benchmark
  def emitRetain(bh: Blackhole): Unit = {
    val i = (counter & 7).toInt
    counter += 1
    val builder = AttributeBuilderPool.get()
    putUserAttrs(builder, i)
    val now = EpochClock.epochNanos()
    if (sourcePresent)
      logger.emitRaw(
        now,
        Severity.Info,
        "INFO",
        bodies(i),
        files(i),
        functions(i),
        lines(i),
        builder,
        0L,
        0L,
        0L,
        0.toByte,
        Resource.empty,
        scope,
        None
      )
    else
      logger.emitRaw(
        now,
        Severity.Info,
        "INFO",
        bodies(i),
        "",
        "",
        0,
        builder,
        0L,
        0L,
        0L,
        0.toByte,
        Resource.empty,
        scope,
        None
      )
    bh.consume(capturing.last)
  }

  /**
   * Retained-record text formatting (location from source fields, user loop
   * over retained attributes, checksum consumption to defeat DCE without
   * allocating).
   */
  @Benchmark
  def formatRetained(bh: Blackhole): Unit = {
    val i = (counter & 7).toInt
    counter += 1
    out.setLength(0)
    TextLogFormatter.formatRecord(out, retained(i))
    var h = 0
    var j = 0
    val n = out.length
    while (j < n) {
      h = h * 31 + out.charAt(j)
      j += 1
    }
    bh.consume(h)
  }

  private def putUserAttrs(builder: Attributes.AttributesBuilder, i: Int): Unit = {
    var k = 0
    while (k < userAttrs) {
      val key = userKeys(k & 15)
      (k & 3) match {
        case 0 => builder.put(key, strVals((i + k) & 7))
        case 1 => builder.put(key, (i * 31 + k).toLong)
        case 2 => builder.put(key, (i * 31 + k).toDouble)
        case _ => builder.put(key, (k & 1) == 0)
      }
      k += 1
    }
  }

  final class LastRecordProcessor extends LogRecordProcessor {
    var last: LogRecord                             = uninitialized
    override def minimumLevel: Int                  = 1
    override def onEmit(logRecord: LogRecord): Unit =
      last = logRecord
    override def shutdown(): Unit   = ()
    override def forceFlush(): Unit = ()
  }
}

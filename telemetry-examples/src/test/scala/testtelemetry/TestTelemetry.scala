package testtelemetry

import java.util.concurrent.ConcurrentLinkedQueue

import scala.jdk.CollectionConverters._

import zio.blocks.telemetry._

/** A span processor that keeps every finished span in memory, in the order the spans ended. */
final class SpanRecorder extends SpanProcessor {
  private val ended = new ConcurrentLinkedQueue[SpanData]()

  def onStart(span: Span): Unit       = ()
  def onEnd(spanData: SpanData): Unit = { ended.add(spanData); () }
  def shutdown(): Unit                = ()
  def forceFlush(): Unit              = ()

  def spans: List[SpanData] = ended.asScala.toList

  /** Each span's name paired with its parent's name, or `None` for a root span. */
  def tree: List[(String, Option[String])] = {
    val nameOf = spans.map(s => s.spanContext.spanId -> s.name).toMap
    spans.map(s => s.name -> nameOf.get(s.parentSpanContext.spanId))
  }
}

/** A log record processor that keeps every log record in memory, in the order they were emitted. */
final class LogRecorder extends LogRecordProcessor {
  private val emitted = new ConcurrentLinkedQueue[LogRecord]()

  def onEmit(logRecord: LogRecord): Unit = { emitted.add(logRecord); () }
  def shutdown(): Unit                   = ()
  def forceFlush(): Unit                 = ()

  def records: List[LogRecord] = emitted.asScala.toList
}

object Metrics {

  /** The counter's total over the series whose string attributes match `attributes`. */
  def total(counter: Counter, attributes: (String, String)*): Long =
    counter.collect() match {
      case MetricData.SumData(points) =>
        points.filter { p =>
          attributes.forall { case (key, value) => p.attributes.get(AttributeKey.string(key)).contains(value) }
        }
          .map(_.value)
          .sum
      case _ => 0L
    }
}

/**
 * A tracer, a logger and a meter that record into memory and belong to one
 * test alone. Create a new one in every test.
 */
final class TestTelemetry {
  val spanRecorder = new SpanRecorder
  val logRecorder  = new LogRecorder

  val tracer: Tracer = TracerProvider.builder.addSpanProcessor(spanRecorder).build().get("test")
  val logger: Logger = LoggerProvider.builder.addLogRecordProcessor(logRecorder).build().get("test")
  val meter: Meter   = MeterProvider.builder.build().get("test")

  def spans: List[SpanData]  = spanRecorder.spans
  def logs: List[LogRecord]  = logRecorder.records
  def spanTree: List[(String, Option[String])] = spanRecorder.tree
}

package instrumentaprogramwithlogstracesandmetrics

import zio.blocks.telemetry._

/**
 * Instrument a Program with Logs, Traces and Metrics: Trace the job with spans.
 *
 * Wraps the restock job in a root span with one child span per step, then prints the collected spans.
 *
 * Run with: sbt "runMain instrumentaprogramwithlogstracesandmetrics.Concept4SpansExample"
 */
object Concept4SpansExample {
  object RestockJob {
    val stock = List("Zionomicon" -> 12, "Programming in Scala" -> 5, "Functional Programming in Scala" -> 8)

    def run(): Unit =
      trace.span("restock") { _ =>
        trace.span("load-inventory") { _ =>
          log.info("Inventory loaded", "titles" -> stock.size)
        }
        trace.span("restock-titles") { span =>
          span.setAttribute("copies", stock.map(_._2).sum.toLong)
        }
        trace.span("write-report") { span =>
          log.info("Report written")
          span.setStatus(SpanStatus.Ok)
        }
      }
  }

  def printSpans(): Unit = {
    val spans  = trace.collectedSpans
    val nameOf = spans.map(s => s.spanContext.spanId -> s.name).toMap

    spans.foreach { s =>
      val parent = nameOf.getOrElse(s.parentSpanContext.spanId, "none")
      val timed  = s.endTimeNanos >= s.startTimeNanos
      println(s"${s.name}: parent=$parent, status=${s.status}, timed=$timed, attributes=${s.attributes.toMap}")
    }
  }

  def main(args: Array[String]): Unit = {
    log.install(LoggerProvider.builder.build().get("bookstore.restock"))
    log.writer(JsonLogFormatter, StderrWriter)

    RestockJob.run()

    printSpans()
  }
}

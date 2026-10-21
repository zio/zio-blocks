package instrumentaprogramwithlogstracesandmetrics

import zio.blocks.telemetry._

/**
 * Complete example: JSON logs on standard error, a span per step, a counter per title, a histogram per step, and a check
 * on the shape of the trace.
 *
 * Run with: sbt "runMain instrumentaprogramwithlogstracesandmetrics.CompleteExample"
 */
object CompleteExample {
  object RestockJob {
    val stock = List("Zionomicon" -> 12, "Programming in Scala" -> 5, "Functional Programming in Scala" -> 8)

    val booksRestocked: Counter = metric.counter("bookstore.books.restocked")
    val stepDuration: Histogram = metric.histogram("bookstore.restock.step.duration.ms")

    def step[A](name: String)(body: Span => A): A =
      trace.span(name) { span =>
        val start = System.nanoTime()
        try body(span)
        finally stepDuration.record((System.nanoTime() - start) / 1e6, "step" -> name)
      }

    def run(): Unit =
      log.annotated("job.id" -> "restock-2026-10-21") {
        trace.span("restock") { _ =>
          step("load-inventory") { _ =>
            log.info("Inventory loaded", "titles" -> stock.size)
          }
          step("restock-titles") { _ =>
            stock.foreach { case (title, copies) =>
              booksRestocked.add(copies.toLong, "title" -> title)
            }
          }
          step("write-report") { span =>
            log.info("Report written")
            span.setStatus(SpanStatus.Ok)
          }
        }
      }
  }

  def spanTree(): List[(String, String)] = {
    val spans  = trace.collectedSpans
    val nameOf = spans.map(s => s.spanContext.spanId -> s.name).toMap
    spans.map(s => s.name -> nameOf.getOrElse(s.parentSpanContext.spanId, "none"))
  }

  val expected = List(
    "load-inventory" -> "restock",
    "restock-titles" -> "restock",
    "write-report"   -> "restock",
    "restock"        -> "none"
  )

  def main(args: Array[String]): Unit = {
    log.install(LoggerProvider.builder.build().get("bookstore.restock"))
    log.writer(JsonLogFormatter, StderrWriter)

    RestockJob.run()

    val titleKey = AttributeKey.string("title")
    RestockJob.booksRestocked.collect() match {
      case MetricData.SumData(points) =>
        points.sortBy(_.attributes.get(titleKey)).foreach { p =>
          println(s"${p.attributes.get(titleKey).getOrElse("?")}: ${p.value}")
        }
      case other => println(s"unexpected: $other")
    }

    val stepKey = AttributeKey.string("step")
    RestockJob.stepDuration.collect() match {
      case MetricData.HistogramData(points) =>
        points.sortBy(_.attributes.get(stepKey)).foreach { p =>
          println(s"${p.attributes.get(stepKey).getOrElse("?")}: count=${p.count}, buckets=${p.boundaries.length + 1}")
        }
      case other => println(s"unexpected: $other")
    }

    assert(spanTree() == expected, s"unexpected spans: ${spanTree()}")
    println(s"Instrumentation check passed: ${expected.size} spans")
  }
}

package instrumentaprogramwithlogstracesandmetrics

import zio.blocks.telemetry._

/**
 * Instrument a Program with Logs, Traces and Metrics: Time each step.
 *
 * Records each step's duration in a histogram and reads the count and bucket numbers back.
 *
 * Run with: sbt "runMain instrumentaprogramwithlogstracesandmetrics.Concept6HistogramExample"
 */
object Concept6HistogramExample {
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

  def main(args: Array[String]): Unit = {
    log.install(LoggerProvider.builder.build().get("bookstore.restock"))
    log.writer(JsonLogFormatter, StderrWriter)

    RestockJob.run()
    RestockJob.run()

    val stepKey = AttributeKey.string("step")

    RestockJob.stepDuration.collect() match {
      case MetricData.HistogramData(points) =>
        points.sortBy(_.attributes.get(stepKey)).foreach { p =>
          println(s"${p.attributes.get(stepKey).getOrElse("?")}: count=${p.count}, buckets=${p.boundaries.length + 1}")
        }
      case other => println(s"unexpected: $other")
    }
  }
}

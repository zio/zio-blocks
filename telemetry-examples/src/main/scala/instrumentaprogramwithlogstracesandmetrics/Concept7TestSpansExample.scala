package instrumentaprogramwithlogstracesandmetrics

import zio.blocks.telemetry._

/**
 * Test the instrumentation.
 *
 * Run with: sbt "runMain instrumentaprogramwithlogstracesandmetrics.Concept7TestSpansExample"
 */
object Concept7TestSpansExample {
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

    trace.clearSpans()
    RestockJob.run()

    assert(spanTree() == expected, s"unexpected spans: ${spanTree()}")
    println(s"Instrumentation check passed: ${expected.size} spans")
  }
}

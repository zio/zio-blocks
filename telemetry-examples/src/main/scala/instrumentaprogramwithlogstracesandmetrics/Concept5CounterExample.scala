package instrumentaprogramwithlogstracesandmetrics

import zio.blocks.telemetry._

/**
 * Count restocked books.
 *
 * Run with: sbt "runMain instrumentaprogramwithlogstracesandmetrics.Concept5CounterExample"
 */
object Concept5CounterExample {
  object RestockJob {
    val stock = List("Zionomicon" -> 12, "Programming in Scala" -> 5, "Functional Programming in Scala" -> 8)

    val booksRestocked: Counter = metric.counter("bookstore.books.restocked")

    def run(): Unit =
      trace.span("restock") { _ =>
        trace.span("load-inventory") { _ =>
          log.info("Inventory loaded", "titles" -> stock.size)
        }
        trace.span("restock-titles") { _ =>
          stock.foreach { case (title, copies) =>
            booksRestocked.add(copies.toLong, "title" -> title)
          }
        }
        trace.span("write-report") { span =>
          log.info("Report written")
          span.setStatus(SpanStatus.Ok)
        }
      }
  }

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
  }
}

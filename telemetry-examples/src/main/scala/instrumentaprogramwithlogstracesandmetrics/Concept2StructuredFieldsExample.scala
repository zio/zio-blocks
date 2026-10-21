package instrumentaprogramwithlogstracesandmetrics

import zio.blocks.telemetry._

/**
 * Instrument a Program with Logs, Traces and Metrics: Add structured fields.
 *
 * Attaches `key -> value` fields to log lines and tags a whole unit of work with `log.annotated`.
 *
 * Run with: sbt "runMain instrumentaprogramwithlogstracesandmetrics.Concept2StructuredFieldsExample"
 */
object Concept2StructuredFieldsExample {
  object RestockJob {
    val stock = List("Zionomicon" -> 12, "Programming in Scala" -> 5, "Functional Programming in Scala" -> 8)

    def run(): Unit =
      log.annotated("job.id" -> "restock-2026-10-21") {
        log.info("Nightly restock started")
        stock.foreach { case (title, copies) =>
          log.info("Restocked a title", "title" -> title, "copies" -> copies)
        }
      }
  }

  def main(args: Array[String]): Unit =
    RestockJob.run()
}

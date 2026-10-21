package instrumentaprogramwithlogstracesandmetrics

import zio.blocks.telemetry._

/**
 * Instrument a Program with Logs, Traces and Metrics: Your first log line.
 *
 * Writes one INFO line to standard output with the global `log` object, with no setup.
 *
 * Run with: sbt "runMain instrumentaprogramwithlogstracesandmetrics.Concept1FirstLogExample"
 */
object Concept1FirstLogExample {
  object RestockJob {
    def run(): Unit =
      log.info("Nightly restock started")
  }

  def main(args: Array[String]): Unit =
    RestockJob.run()
}

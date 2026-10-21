package instrumentaprogramwithlogstracesandmetrics

import zio.blocks.telemetry._

/**
 * Your first log line.
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

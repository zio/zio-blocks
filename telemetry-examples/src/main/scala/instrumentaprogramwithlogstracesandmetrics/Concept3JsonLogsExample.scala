package instrumentaprogramwithlogstracesandmetrics

import zio.blocks.telemetry._

/**
 * Instrument a Program with Logs, Traces and Metrics: Switch to JSON logs on standard error.
 *
 * Installs a fresh logger and adds a JSON output on standard error with `log.install` and `log.writer`.
 *
 * Run with: sbt "runMain instrumentaprogramwithlogstracesandmetrics.Concept3JsonLogsExample"
 */
object Concept3JsonLogsExample {
  object RestockJob {
    def run(): Unit =
      log.info("Nightly restock started", "titles" -> 3)
  }

  def main(args: Array[String]): Unit = {
    log.install(LoggerProvider.builder.build().get("bookstore.restock"))
    log.writer(JsonLogFormatter, StderrWriter)

    RestockJob.run()
  }
}

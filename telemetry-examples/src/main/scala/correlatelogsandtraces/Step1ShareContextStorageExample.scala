package correlatelogsandtraces

import zio.blocks.telemetry._

/**
 * How to Correlate Logs and Traces Across Services: Share One Context Storage
 * Between Logs and Spans.
 *
 * Installs a tracer and a logger that share one `ContextStorage`, then writes a
 * log line inside a span: the JSON line carries the span's trace and span ids.
 */
// Run from telemetry-examples:  sbt "runMain correlatelogsandtraces.Step1ShareContextStorageExample"
object Step1ShareContextStorageExample {
  def main(args: Array[String]): Unit = {
    Telemetry.installLogger()

    Telemetry.tracer.span("checkout.placeOrder") { _ =>
      log.info("Order received", "order.id" -> "order-1042")
    }
  }
}

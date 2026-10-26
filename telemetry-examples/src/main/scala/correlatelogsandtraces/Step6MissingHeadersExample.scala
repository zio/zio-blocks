package correlatelogsandtraces

import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._

/**
 * How to Correlate Logs and Traces Across Services: Handle Missing or Invalid
 * Headers.
 *
 * Shows that `extract` returns `None` for a missing or invalid `traceparent`,
 * and that the payment service then starts a new trace of its own.
 */
// Run from telemetry-examples:  sbt "runMain correlatelogsandtraces.Step6MissingHeadersExample"
object Step6MissingHeadersExample {
  def main(args: Array[String]): Unit = {
    // Setup from the first section: tracer and logger share one storage
    Telemetry.installLogger()

    val untraced = PaymentApi.chargeRequest("order-1043", 1250)

    List(
      "no header"         -> untraced,
      "all-zero trace id" -> untraced.setHeader(
        "traceparent",
        "00-00000000000000000000000000000000-00f067aa0ba902b7-01"
      ),
      "version ff" -> untraced.setHeader("traceparent", "ff-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"),
      "cut short"  -> untraced.setHeader("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-01")
    ).foreach { case (label, request) =>
      println(s"$label: ${W3CTraceContextPropagator.extract(request, HttpGlue.getHeader)}")
    }

    Telemetry.tracer.span("checkout.callPayment", SpanKind.Client) { _ =>
      log.info("Calling the payment service without trace headers")
      val response = Network.send(untraced)
      log.info("Payment answered", "status" -> response.status.code)
    }

    Network.shutdown()
  }
}

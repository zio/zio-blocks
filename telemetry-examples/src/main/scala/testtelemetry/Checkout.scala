package testtelemetry

import zio.blocks.telemetry._

/**
 * Application code that records telemetry through the global `log`, `trace` and
 * `metric` objects instead of taking a tracer, logger and meter as parameters.
 * `GlobalTelemetrySpec` shows how to test it.
 */
object Checkout {
  // Created once: every call to `metric.counter` registers a new instrument.
  val ordersFailed: Counter = metric.counter("checkout.orders.failed")

  def placeOrder(order: Order, payments: PaymentGateway): Either[String, String] =
    trace.span("checkout") { span =>
      span.setAttribute("order.id", order.id)
      val charged = payments.charge(order.id, order.totalCents)
      charged match {
        case Right(receipt) =>
          log.info("Order placed", "order.id" -> order.id, "receipt" -> receipt)
          span.setStatus(SpanStatus.Ok)
        case Left(reason) =>
          ordersFailed.add(1L, "reason" -> reason)
          span.setStatus(SpanStatus.Error(reason))
          log.error("Payment failed", "order.id" -> order.id, "reason" -> reason)
      }
      charged
    }
}

package testtelemetry

import zio.blocks.telemetry._
import zio.test._

/**
 * How to Test Code That Logs, Traces and Records Metrics: assert on a counter.
 *
 * Gives the service a meter of its own, runs `placeOrder` and reads the
 * counters back with `Metrics.total`.
 *
 * Run with: sbt "testOnly testtelemetry.ReadCountersSpec"
 */
object ReadCountersSpec extends ZIOSpecDefault {
  private def service(payments: PaymentGateway): OrderService =
    new OrderService(
      payments,
      TracerProvider.builder.build().get("test"),
      LoggerProvider.builder.build().get("test"),
      MeterProvider.builder.build().get("test")
    )

  def spec = suite("placeOrder counters")(
    test("a declined payment adds one to orders.failed, tagged with the reason") {
      val orders = service(PaymentGateway.declineAll("card declined"))

      orders.placeOrder(Order("order-2", "Programming in Scala", 2, 5000L))

      assertTrue(
        Metrics.total(orders.ordersFailed) == 1L,
        Metrics.total(orders.ordersFailed, "reason" -> "card declined") == 1L,
        Metrics.total(orders.ordersPlaced) == 0L
      )
    },
    test("orders.placed keeps one total per title") {
      val orders = service(PaymentGateway.approveAll)

      orders.placeOrder(Order("order-1", "Zionomicon", 1, 4500L))
      orders.placeOrder(Order("order-3", "Zionomicon", 3, 4500L))
      orders.placeOrder(Order("order-4", "Programming in Scala", 1, 5000L))

      assertTrue(
        Metrics.total(orders.ordersPlaced, "title" -> "Zionomicon") == 2L,
        Metrics.total(orders.ordersPlaced, "title" -> "Programming in Scala") == 1L,
        Metrics.total(orders.ordersFailed) == 0L
      )
    }
  )
}

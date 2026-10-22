package testtelemetry

import zio.blocks.telemetry._
import zio.test._

/**
 * How to Test Code That Logs, Traces and Records Metrics: the complete spec.
 *
 * Every test creates its own `TestTelemetry`, so the tests share no state and
 * ZIO Test can run them in parallel.
 *
 * Run with: sbt "testOnly testtelemetry.OrderServiceSpec"
 */
object OrderServiceSpec extends ZIOSpecDefault {
  private val reasonKey = AttributeKey.string("reason")

  private def orderService(payments: PaymentGateway, telemetry: TestTelemetry): OrderService =
    new OrderService(payments, telemetry.tracer, telemetry.logger, telemetry.meter)

  def spec = suite("OrderService telemetry")(
    test("an approved order is traced, logged at INFO and counted per title") {
      val telemetry = new TestTelemetry
      val orders    = orderService(PaymentGateway.approveAll, telemetry)

      val result = orders.placeOrder(Order("order-1", "Zionomicon", 1, 4500L))

      assertTrue(
        result == Right("receipt-order-1"),
        telemetry.spanTree == List("charge" -> Some("placeOrder"), "placeOrder" -> None),
        telemetry.spans.find(_.name == "placeOrder").map(_.status) == Some(SpanStatus.Ok),
        telemetry.logs.map(r => r.severity -> r.body.value) == List(Severity.Info -> "Order placed"),
        Metrics.total(orders.ordersPlaced, "title" -> "Zionomicon") == 1L,
        Metrics.total(orders.ordersFailed) == 0L
      )
    },
    test("a declined payment sets an error status, logs an ERROR and bumps orders.failed") {
      val telemetry = new TestTelemetry
      val orders    = orderService(PaymentGateway.declineAll("card declined"), telemetry)

      val result = orders.placeOrder(Order("order-2", "Programming in Scala", 2, 5000L))

      val error = telemetry.logs.find(_.severity == Severity.Error)
      assertTrue(
        result == Left("card declined"),
        telemetry.spans.find(_.name == "placeOrder").map(_.status) == Some(SpanStatus.Error("card declined")),
        error.map(_.body.value) == Some("Payment failed"),
        error.flatMap(_.attributes.get(reasonKey)) == Some("card declined"),
        Metrics.total(orders.ordersFailed, "reason" -> "card declined") == 1L,
        Metrics.total(orders.ordersPlaced) == 0L
      )
    },
    test("each test sees only its own telemetry") {
      val telemetry = new TestTelemetry
      val orders    = orderService(PaymentGateway.approveAll, telemetry)

      (1 to 3).foreach(i => orders.placeOrder(Order(s"order-$i", "Zionomicon", 1, 4500L)))

      assertTrue(
        telemetry.spans.count(_.name == "placeOrder") == 3,
        telemetry.logs.size == 3,
        Metrics.total(orders.ordersPlaced) == 3L
      )
    }
  )
}

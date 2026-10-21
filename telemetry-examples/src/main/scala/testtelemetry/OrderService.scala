package testtelemetry

import zio.blocks.telemetry._

/** One order: `copies` copies of the book `title`, at `priceCents` each. */
final case class Order(id: String, title: String, copies: Int, priceCents: Long) {
  def totalCents: Long = copies * priceCents
}

/** Charges a customer and returns a receipt id, or the reason the payment was declined. */
trait PaymentGateway {
  def charge(orderId: String, amountCents: Long): Either[String, String]
}

object PaymentGateway {
  val approveAll: PaymentGateway = (orderId, _) => Right(s"receipt-$orderId")

  def declineAll(reason: String): PaymentGateway = (_, _) => Left(reason)
}

/**
 * The code under test. It receives its tracer, logger and meter as
 * constructor parameters, so a test can pass in ones that record into memory.
 */
final class OrderService(payments: PaymentGateway, tracer: Tracer, logger: Logger, meter: Meter) {
  val ordersPlaced: Counter = meter.counterBuilder("orders.placed").build()
  val ordersFailed: Counter = meter.counterBuilder("orders.failed").build()

  def placeOrder(order: Order): Either[String, String] =
    tracer.span("placeOrder") { span =>
      span.setAttribute("order.id", order.id)
      span.setAttribute("book.title", order.title)

      val charged = tracer.span("charge") { chargeSpan =>
        chargeSpan.setAttribute("amount.cents", order.totalCents)
        payments.charge(order.id, order.totalCents)
      }

      charged match {
        case Right(receipt) =>
          ordersPlaced.add(1L, "title" -> order.title)
          logger.info(
            "Order placed",
            "order.id" -> AttributeValue.StringValue(order.id),
            "receipt"  -> AttributeValue.StringValue(receipt)
          )
          span.setStatus(SpanStatus.Ok)
        case Left(reason) =>
          ordersFailed.add(1L, "reason" -> reason)
          logger.error(
            "Payment failed",
            "order.id" -> AttributeValue.StringValue(order.id),
            "reason"   -> AttributeValue.StringValue(reason)
          )
          span.setStatus(SpanStatus.Error(reason))
      }
      charged
    }
}

/**
 * How to Test Code That Logs, Traces and Records Metrics: the order service
 * wired for production.
 *
 * The tracer and meter come from the global `trace` and `metric` objects, and
 * the logger prints text lines to standard output.
 *
 * Run with: sbt "runMain testtelemetry.OrderServiceApp"
 */
object OrderServiceApp {
  def main(args: Array[String]): Unit = {
    val logger = LoggerProvider.builder
      .addLogRecordProcessor(new ConsoleLogRecordProcessor)
      .build()
      .get("bookstore.orders")

    def service(payments: PaymentGateway) =
      new OrderService(payments, trace.get("bookstore.orders"), logger, metric.get("bookstore.orders"))

    println(service(PaymentGateway.approveAll).placeOrder(Order("order-1", "Zionomicon", 1, 4500L)))
    println(
      service(PaymentGateway.declineAll("card declined"))
        .placeOrder(Order("order-2", "Programming in Scala", 2, 5000L))
    )
  }
}

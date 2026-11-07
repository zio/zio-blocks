package generateservicetraits.app

import com.acme.orders._

/**
 * An in-memory order service that implements the generated `OrderService`
 * trait.
 *
 * The trait is generated from `src/main/smithy/order-service.smithy` on every
 * build. Because this object extends it and defines each operation with the
 * generated signature, a renamed operation, a changed input type or a changed
 * error type stops this file from compiling, and CI reports the break.
 */
object StubOrderService extends OrderService {

  def getOrder(input: GetOrderInput): Either[OrderError, GetOrderOutput] =
    if (input.orderId.isEmpty) Left(OrderNotFound(input.orderId))
    else Right(GetOrderOutput(input.orderId, OrderState.PLACED))

  def cancelOrder(input: CancelOrderInput): Either[OrderError, CancelOrderOutput] =
    if (input.orderId.startsWith("shipped-")) Left(OrderAlreadyShipped(input.orderId))
    else Right(CancelOrderOutput(input.orderId, OrderState.CANCELLED))
}

object OrderServiceApp {
  def main(args: Array[String]): Unit = {
    println(StubOrderService.getOrder(GetOrderInput("order-1")))
    println(StubOrderService.cancelOrder(CancelOrderInput("shipped-2", Some("changed mind"))))
  }
}

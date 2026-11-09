package generateservicetraits.app

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

import com.example.bookstore._

/**
 * An in-memory bookstore that implements the generated `BookstoreService`
 * trait.
 *
 * The trait and its data types are generated from
 * `src/main/openapi/bookstore-service.json` on every build. Because this object
 * extends the trait and defines each operation with the generated signature, a
 * renamed operation, a changed parameter or a changed error type stops this
 * file from compiling, and CI reports the break.
 */
object StubBookstoreService extends BookstoreService {

  def getOrder(orderId: String): Either[GetOrderError, Order] =
    if (orderId.startsWith("missing-")) Left(GetOrderError.Status404(ApiError(s"no order $orderId")))
    else Right(orderFor(orderId, OrderStatus.Placed))

  def cancelOrder(orderId: String, body: CancelRequest): Either[CancelOrderError, Order] =
    if (orderId.startsWith("missing-")) Left(CancelOrderError.Status404(ApiError(s"no order $orderId")))
    else if (orderId.startsWith("shipped-")) Left(CancelOrderError.Status409(ApiError(s"order $orderId has shipped")))
    else Right(orderFor(orderId, OrderStatus.Cancelled))

  private def orderFor(orderId: String, status: OrderStatus): Order =
    Order(
      id = UUID.nameUUIDFromBytes(orderId.getBytes(StandardCharsets.UTF_8)),
      customerId = 1L,
      placedAt = Instant.EPOCH,
      lines = Nil,
      payment = Payment.CardPayment("0000"),
      status = status
    )
}

object BookstoreServiceApp {
  def main(args: Array[String]): Unit = {
    println(StubBookstoreService.getOrder("order-1").map(_.status))
    println(StubBookstoreService.cancelOrder("shipped-2", CancelRequest(Some("changed mind"))).map(_.status))
  }
}

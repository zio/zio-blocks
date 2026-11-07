// Run from smithy-examples:  sbt ordersApp/run
// The generated types come from `smithy-examples/src/main/smithy/orders.smithy`; sbt generates them before it compiles.

package generatescalafromsmithy.app

import com.acme.orders._
import zio.blocks.schema._

/**
 * Generate Scala Code from a Smithy Model — Compile the Generated Code with sbt
 *
 * Builds an `Order` from the generated types, encodes it to JSON with
 * `toJsonString` and decodes it back with `fromJson`.
 */
object OrdersApp {
  def main(args: Array[String]): Unit = {
    val order = Order(
      id = "order-1001",
      items = List(LineItem("MUG-RED", 2, BigDecimal("12.50"))),
      status = OrderStatus.Shipped(Shipment("1Z999", java.time.Instant.parse("2026-10-27T09:30:00Z"))),
      priority = Priority.EXPRESS,
      note = Some("Leave at the door")
    )
    val json = order.toJsonString
    println(json)
    println(s"round trip: ${json.fromJson[Order] == Right(order)}")
  }
}

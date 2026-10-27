package avrorecords

import zio.blocks.schema._
import java.time.{Instant, LocalDate}

sealed trait OrderEvent

object OrderEvent {
  implicit val schema: Schema[OrderEvent] = Schema.derived
}

case class OrderPlaced(
  orderId: Long,
  customer: String,
  amount: BigDecimal,
  placedAt: Instant,
  note: Option[String],
  items: List[LineItem]
) extends OrderEvent

case class OrderShipped(orderId: Long, shippedOn: LocalDate, carrier: String) extends OrderEvent

case class OrderCancelled(orderId: Long, reason: String) extends OrderEvent

case class LineItem(sku: String, quantity: Int, unitPrice: BigDecimal)

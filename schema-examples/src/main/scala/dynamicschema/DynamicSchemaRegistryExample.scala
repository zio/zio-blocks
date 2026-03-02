package dynamicschema

import zio.blocks.schema._
import zio.blocks.schema.binding._
import util.ShowExpr.show

/**
 * DynamicSchema Reference — Schema Registry Pipeline
 *
 * End-to-end demonstration of the schema registry pattern: producer registers a
 * schema as a DynamicValue blob; consumer fetches the blob, restores the
 * DynamicSchema, rebinds it, and decodes incoming events.
 *
 * Run with: sbt "schema-examples/runMain
 * dynamicschema.DynamicSchemaRegistryExample"
 */
object DynamicSchemaRegistryExample extends App {

  // ── Shared domain types ────────────────────────────────────────────────────

  case class ItemId(value: String)
  case class OrderItem(itemId: ItemId, quantity: Int)
  case class OrderCreated(orderId: String, customerId: String, items: List[OrderItem])

  object ItemId       { implicit val schema: Schema[ItemId] = Schema.derived[ItemId]             }
  object OrderItem    { implicit val schema: Schema[OrderItem] = Schema.derived[OrderItem]       }
  object OrderCreated { implicit val schema: Schema[OrderCreated] = Schema.derived[OrderCreated] }

  // ── Schema registry (in-memory simulation) ────────────────────────────────
  // In production this would be a remote service (e.g. Confluent Schema Registry).
  // DynamicSchema.toDynamicValue strips all Scala closures, so the blob can be
  // stored as JSON or bytes without any issue.

  var registry: Map[String, DynamicValue] = Map.empty

  def registerSchema(id: String, schema: Schema[_]): Unit = {
    val blob = DynamicSchema.toDynamicValue(schema.toDynamicSchema)
    registry = registry + (id -> blob)
    println(s"[Registry] Registered '$id'")
  }

  def fetchSchema(id: String): DynamicSchema =
    DynamicSchema.fromDynamicValue(registry(id))

  // ── Message queue (in-memory simulation) ──────────────────────────────────

  case class Message(schemaId: String, payload: DynamicValue)
  var queue: List[Message] = Nil

  // ── Producer side ─────────────────────────────────────────────────────────

  println("=== Producer startup ===")
  registerSchema("order-created-v1", Schema[OrderCreated])

  def publishEvent(event: OrderCreated): Unit = {
    val payload = Schema[OrderCreated].toDynamicValue(event)
    queue = queue :+ Message("order-created-v1", payload)
    println(s"[Producer] Published OrderCreated(orderId=${event.orderId})")
  }

  publishEvent(
    OrderCreated(
      orderId = "ORD-001",
      customerId = "CUST-42",
      items = List(OrderItem(ItemId("ITEM-A"), 2), OrderItem(ItemId("ITEM-B"), 1))
    )
  )

  publishEvent(
    OrderCreated(
      orderId = "ORD-002",
      customerId = "CUST-17",
      items = List(OrderItem(ItemId("ITEM-C"), 5))
    )
  )

  // ── Consumer side ─────────────────────────────────────────────────────────

  println("\n=== Consumer processing ===")

  // Build a resolver from the consumer's local type bindings.
  val resolver: BindingResolver =
    BindingResolver.empty
      .bind(Binding.of[ItemId])
      .bind(Binding.of[OrderItem])
      .bind(Binding.of[OrderCreated])
      ++ BindingResolver.defaults

  queue.foreach { msg =>
    val dynamic = fetchSchema(msg.schemaId)
    val rebound = dynamic.rebind[OrderCreated](resolver)
    val decoded = rebound.fromDynamicValue(msg.payload)
    show(decoded)
  }
}

package dynamicschema

import zio.blocks.schema._
import zio.blocks.schema.binding._
import util.ShowExpr.show

/**
 * DynamicSchema Reference — Rebinding
 *
 * Demonstrates DynamicSchema#rebind: attaching runtime bindings to a restored
 * structural schema to produce a fully operational Schema[A] that can encode
 * and decode values.
 *
 * Run with: sbt "schema-examples/runMain
 * dynamicschema.DynamicSchemaRebindExample"
 */
object DynamicSchemaRebindExample extends App {

  // Domain types with schemas

  case class CustomerId(value: String)
  case class LineItem(sku: String, quantity: Int, unitPrice: Double)
  case class Order(customerId: CustomerId, items: List[LineItem], total: Double)

  object CustomerId { implicit val schema: Schema[CustomerId] = Schema.derived[CustomerId] }
  object LineItem   { implicit val schema: Schema[LineItem] = Schema.derived[LineItem]     }
  object Order      { implicit val schema: Schema[Order] = Schema.derived[Order]           }

  // ── Step 1: strip bindings and serialize ──────────────────────────────────

  val blob: DynamicValue = DynamicSchema.toDynamicValue(Schema[Order].toDynamicSchema)

  // ── Step 2: restore on the consumer side ──────────────────────────────────

  val dynamic: DynamicSchema = DynamicSchema.fromDynamicValue(blob)

  // ── Step 3: build a BindingResolver ───────────────────────────────────────
  // Binding.of[T] derives a Binding from the implicit Schema[T].
  // BindingResolver.defaults covers all primitives, java.time, List, Map, Option, etc.

  val resolver: BindingResolver =
    BindingResolver.empty
      .bind(Binding.of[CustomerId])
      .bind(Binding.of[LineItem])
      .bind(Binding.of[Order])
      ++ BindingResolver.defaults

  // ── Step 4: rebind to recover a fully operational Schema[Order] ───────────

  val rebound: Schema[Order] = dynamic.rebind[Order](resolver)

  // ── Step 5: round-trip an Order value ─────────────────────────────────────

  val order = Order(
    customerId = CustomerId("CUST-42"),
    items = List(
      LineItem("SKU-A", quantity = 2, unitPrice = 29.99),
      LineItem("SKU-B", quantity = 1, unitPrice = 9.99)
    ),
    total = 69.97
  )

  val encoded = rebound.toDynamicValue(order)
  val decoded = rebound.fromDynamicValue(encoded)

  println("=== Rebind + round-trip ===")
  show(decoded)                 // Right(Order(...))
  show(decoded == Right(order)) // true
}

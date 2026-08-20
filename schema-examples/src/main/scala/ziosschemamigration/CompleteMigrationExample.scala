package ziosschemamigration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.patch._

/**
 * Migrating from ZIO Schema to ZIO Blocks Schema — Complete Example
 *
 * This is a self-contained, end-to-end example that demonstrates the idiomatic
 * ZIO Blocks Schema approach for a realistic domain: an e-commerce order
 * management system with products, customers, and orders.
 *
 * It covers:
 *   - Schema derivation (replacing DeriveSchema.gen)
 *   - Annotations / modifiers (replacing Chunk[Any] annotations)
 *   - Newtype wrapper schemas (replacing Schema.transformOrFail)
 *   - DynamicValue conversion and navigation
 *   - DynamicSchema for runtime structural validation
 *   - Optics (Lens, Prism) obtained from the schema
 *   - Diff and patch
 *
 * Run with: sbt "schema-examples/runMain
 * ziosschemamigration.CompleteMigrationExample"
 */
object CompleteMigrationExample extends App {

  // ═══════════════════════════════════════════════════════════════════════
  // Domain Model
  // ═══════════════════════════════════════════════════════════════════════

  // Validated newtype for a positive price
  final case class Price private (cents: Long)
  object Price {
    def apply(cents: Long): Price =
      if (cents >= 0) new Price(cents)
      else throw new IllegalArgumentException(s"Price cannot be negative: $cents")

    implicit val schema: Schema[Price] =
      Schema[Long].transform(
        to = cents =>
          if (cents >= 0) new Price(cents)
          else throw SchemaError.validationFailed(s"Price cannot be negative: $cents"),
        from = _.cents
      )
  }

  // Order status enum
  sealed trait OrderStatus
  object OrderStatus {
    case object Pending   extends OrderStatus
    case object Paid      extends OrderStatus
    case object Shipped   extends OrderStatus
    case object Cancelled extends OrderStatus

    implicit val schema: Schema[OrderStatus] = Schema.derived[OrderStatus]
  }

  // Product with a renamed field and a transient audit field
  // Transient fields must have a default value in ZIO Blocks Schema
  final case class Product(
    id: String,
    @Modifier.rename("product_name") name: String,
    price: Price,
    @Modifier.transient() internalNotes: String = ""
  )
  object Product {
    implicit val schema: Schema[Product] = Schema.derived[Product]
  }

  // Customer
  final case class Customer(
    @Modifier.config("json.name", "customer_id") id: String,
    name: String,
    email: String
  )
  object Customer {
    implicit val schema: Schema[Customer] = Schema.derived[Customer]
  }

  // Order — a recursive-friendly structure
  final case class Order(
    id: String,
    customer: Customer,
    items: List[Product],
    status: OrderStatus,
    notes: Option[String]
  )
  object Order {
    implicit val schema: Schema[Order] = Schema.derived[Order]

    // Optics — obtained from the schema (replacing ZIO Schema's AccessorBuilder)
    val status: Lens[Order, OrderStatus] =
      schema.reflect.asRecord.get.lensByName[OrderStatus]("status").get
    val notes: Lens[Order, Option[String]] =
      schema.reflect.asRecord.get.lensByName[Option[String]]("notes").get
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Sample Data
  // ═══════════════════════════════════════════════════════════════════════

  val customer = Customer("cust-001", "Alice Smith", "alice@example.com")

  val widget = Product("prod-001", "Super Widget", Price(999L), "needs QA review")
  val gadget = Product("prod-002", "Nano Gadget", Price(4999L), "popular item")

  val order = Order(
    id = "order-001",
    customer = customer,
    items = List(widget, gadget),
    status = OrderStatus.Pending,
    notes = None
  )

  def printHeader(title: String): Unit = {
    println()
    println("=" * 60)
    println(s"  $title")
    println("=" * 60)
  }

  // ═══════════════════════════════════════════════════════════════════════
  // 1. Schema Derivation and DynamicValue Roundtrip
  // ═══════════════════════════════════════════════════════════════════════

  printHeader("1. Schema Derivation and DynamicValue Roundtrip")

  val orderDv: DynamicValue = Order.schema.toDynamicValue(order)
  println(s"Order as DynamicValue:\n  $orderDv")

  val restored: Either[SchemaError, Order] = Order.schema.fromDynamicValue(orderDv)
  println(s"\nRoundtrip succeeded: ${restored.isRight}")
  println(s"Restored order id:   ${restored.map(_.id)}")

  // ═══════════════════════════════════════════════════════════════════════
  // 2. DynamicValue Navigation (not available in ZIO Schema)
  // ═══════════════════════════════════════════════════════════════════════

  printHeader("2. DynamicValue Navigation")

  // DynamicValue.get returns a DynamicValueSelection (monadic chaining)
  val customerName: Either[SchemaError, DynamicValue] =
    orderDv.get("customer").get("name").one
  println(s"customer.name (dynamic): $customerName")

  // ═══════════════════════════════════════════════════════════════════════
  // 3. DynamicSchema Validation (replaces MetaSchema / schema.ast)
  // ═══════════════════════════════════════════════════════════════════════

  printHeader("3. DynamicSchema Structural Validation")

  val orderDynSchema: DynamicSchema = Order.schema.toDynamicSchema

  // Conforming value (the order we just encoded)
  println(s"Conforming value:     ${orderDynSchema.conforms(orderDv)}")

  // Non-conforming value (missing required fields)
  val incomplete = DynamicValue.Record(Chunk("id" -> DynamicValue.Primitive(PrimitiveValue.String("x"))))
  println(s"Incomplete value:     ${orderDynSchema.conforms(incomplete)}")
  println(s"Validation error:     ${orderDynSchema.check(incomplete)}")

  // ═══════════════════════════════════════════════════════════════════════
  // 4. Optics — Lens and Prism
  // ═══════════════════════════════════════════════════════════════════════

  printHeader("4. Optics (Lens and Prism)")

  // Use the pre-computed Lens to modify a field
  val shippedOrder: Order = Order.status.replace(order, OrderStatus.Shipped)
  println(s"Original status: ${order.status}")
  println(s"After shipping:  ${shippedOrder.status}")

  // Prism for the sealed trait (order status)
  val paidPrism: Prism[OrderStatus, OrderStatus.Paid.type] =
    OrderStatus.schema.reflect.asVariant.get
      .prismByName[OrderStatus.Paid.type]("Paid")
      .get

  println(s"\nIsPaid (Pending):  ${paidPrism.getOption(OrderStatus.Pending)}")
  println(s"IsPaid (Paid):     ${paidPrism.getOption(OrderStatus.Paid)}")

  // ═══════════════════════════════════════════════════════════════════════
  // 5. Diff and Patch
  // ═══════════════════════════════════════════════════════════════════════

  printHeader("5. Diff and Patch")

  val shippedWithNotes: Order = shippedOrder.copy(notes = Some("Shipped via express"))
  val patch: Patch[Order]     = Order.schema.diff(order, shippedWithNotes)
  println(s"Patch (pending → shipped+notes): $patch")

  val applied: Either[SchemaError, Order] = Order.schema.patch(order, patch)
  println(s"Applied patch notes: ${applied.map(_.notes)}")
  println(s"Applied patch status: ${applied.map(_.status)}")

  // Programmatic patch (new in ZIO Blocks)
  val programmaticPatch: Patch[Order]       = Patch.set(Order.status, OrderStatus.Cancelled)
  val cancelled: Either[SchemaError, Order] = programmaticPatch.apply(order, PatchMode.Strict)
  println(s"\nCancelled order status: ${cancelled.map(_.status)}")

  // ═══════════════════════════════════════════════════════════════════════
  // 6. Documentation (new in ZIO Blocks — first-class Doc per node)
  // ═══════════════════════════════════════════════════════════════════════

  printHeader("6. First-class Documentation")

  val documentedSchema: Schema[Order] =
    Order.schema
      .doc("An e-commerce order with customer details, line items, and status")

  println(s"Schema doc: ${documentedSchema.reflect.doc}")

  // ═══════════════════════════════════════════════════════════════════════
  // Done
  // ═══════════════════════════════════════════════════════════════════════

  printHeader("Complete Example Done")
  println("All migration patterns demonstrated successfully.")
}

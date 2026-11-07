package generatescalafromsmithy

/**
 * Generate Scala Code from a Smithy Model — shared model
 *
 * The order service's Smithy contract as a string, shared by the section
 * examples. `CompleteExample` reads the same text from
 * `src/main/smithy/orders.smithy`.
 */
object OrdersModel {
  val ordersSmithy: String =
    """$version: "2"
      |
      |namespace com.acme.orders
      |
      |/// An order placed by a customer.
      |structure Order {
      |    @required
      |    id: String
      |
      |    @required
      |    items: LineItems
      |
      |    @required
      |    status: OrderStatus
      |
      |    @required
      |    priority: Priority
      |
      |    /// Free-text instructions from the customer.
      |    note: String
      |
      |    attributes: Attributes
      |}
      |
      |list LineItems {
      |    member: LineItem
      |}
      |
      |map Attributes {
      |    key: String
      |    value: String
      |}
      |
      |/// One product line in an order.
      |structure LineItem {
      |    @required
      |    sku: String
      |
      |    @required
      |    quantity: Integer
      |
      |    @required
      |    unitPrice: BigDecimal
      |}
      |
      |/// Where an order is in its life cycle.
      |union OrderStatus {
      |    placed: Unit
      |    shipped: Shipment
      |    cancelled: Cancellation
      |}
      |
      |structure Shipment {
      |    @required
      |    trackingNumber: String
      |
      |    @required
      |    shippedAt: Timestamp
      |}
      |
      |structure Cancellation {
      |    @required
      |    reason: String
      |}
      |
      |/// How fast the order ships.
      |enum Priority {
      |    STANDARD
      |    EXPRESS
      |}
      |""".stripMargin
}

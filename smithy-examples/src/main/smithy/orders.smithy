$version: "2"

namespace com.acme.orders

/// An order placed by a customer.
structure Order {
    @required
    id: String

    @required
    items: LineItems

    @required
    status: OrderStatus

    @required
    priority: Priority

    /// Free-text instructions from the customer.
    note: String

    attributes: Attributes
}

list LineItems {
    member: LineItem
}

map Attributes {
    key: String
    value: String
}

/// One product line in an order.
structure LineItem {
    @required
    sku: String

    @required
    quantity: Integer

    @required
    unitPrice: BigDecimal
}

/// Where an order is in its life cycle.
union OrderStatus {
    placed: Unit
    shipped: Shipment
    cancelled: Cancellation
}

structure Shipment {
    @required
    trackingNumber: String

    @required
    shippedAt: Timestamp
}

structure Cancellation {
    @required
    reason: String
}

/// How fast the order ships.
enum Priority {
    STANDARD
    EXPRESS
}

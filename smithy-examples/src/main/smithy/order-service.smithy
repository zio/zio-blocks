$version: "2"

namespace com.acme.orders

/// The operations a client can call on the order service.
service OrderService {
    operations: [GetOrder, CancelOrder]
}

/// Look up one order by its id.
operation GetOrder {
    input: GetOrderInput
    output: GetOrderOutput
    errors: [OrderNotFound]
}

/// Cancel an order that has not shipped yet.
operation CancelOrder {
    input: CancelOrderInput
    output: CancelOrderOutput
    errors: [OrderNotFound, OrderAlreadyShipped]
}

structure GetOrderInput {
    @required
    orderId: String
}

structure GetOrderOutput {
    @required
    orderId: String

    @required
    state: OrderState

    trackingNumber: String
}

structure CancelOrderInput {
    @required
    orderId: String

    reason: String
}

structure CancelOrderOutput {
    @required
    orderId: String

    @required
    state: OrderState
}

/// The order does not exist.
@error("client")
structure OrderNotFound {
    @required
    orderId: String
}

/// The order has shipped, so it can no longer be cancelled.
@error("client")
structure OrderAlreadyShipped {
    @required
    orderId: String
}

/// Where an order is in its life cycle, as the service reports it.
enum OrderState {
    PLACED
    SHIPPED
    CANCELLED
}

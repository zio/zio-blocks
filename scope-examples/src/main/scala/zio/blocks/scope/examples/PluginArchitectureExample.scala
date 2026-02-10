package zio.blocks.scope.examples

import zio.blocks.scope._

/**
 * Plugin Architecture Example — Trait Injection via Subtype Wires
 *
 * Demonstrates how abstract trait dependencies are resolved via concrete
 * implementation wires using subtype resolution. This pattern enables:
 *   - Clean interface/implementation separation
 *   - Easy swapping of implementations (e.g., Stripe vs PayPal)
 *   - Compile-time verified dependency graphs
 */

/** Configuration for payment gateway connections. */
final case class GatewayConfig(apiKey: String, merchantId: String)

/** Result of a payment operation. */
final case class PaymentResult(transactionId: String, success: Boolean, message: String)

/**
 * Abstract payment gateway interface.
 *
 * Services depend on this trait, not concrete implementations.
 */
trait PaymentGateway {
  def charge(amount: BigDecimal, currency: String): PaymentResult
  def refund(transactionId: String): PaymentResult
}

/**
 * Stripe implementation of [[PaymentGateway]].
 *
 * When wired via `Wire.shared[StripeGateway]`, this satisfies any
 * `PaymentGateway` dependency through subtype resolution.
 */
final class StripeGateway(config: GatewayConfig) extends PaymentGateway with AutoCloseable {
  println(s"[Stripe] Connected with merchant ${config.merchantId}")

  def charge(amount: BigDecimal, currency: String): PaymentResult = {
    val txId = s"stripe_${System.nanoTime()}"
    PaymentResult(txId, success = true, s"Charged $currency $amount via Stripe")
  }

  def refund(transactionId: String): PaymentResult =
    PaymentResult(transactionId, success = true, s"Refunded $transactionId via Stripe")

  def close(): Unit = println("[Stripe] Connection closed")
}

/** PayPal implementation — demonstrates swappability. */
final class PayPalGateway(config: GatewayConfig) extends PaymentGateway with AutoCloseable {
  println(s"[PayPal] Connected with merchant ${config.merchantId}")

  def charge(amount: BigDecimal, currency: String): PaymentResult = {
    val txId = s"paypal_${System.nanoTime()}"
    PaymentResult(txId, success = true, s"Charged $currency $amount via PayPal")
  }

  def refund(transactionId: String): PaymentResult =
    PaymentResult(transactionId, success = true, s"Refunded $transactionId via PayPal")

  def close(): Unit = println("[PayPal] Connection closed")
}

/**
 * Checkout service that depends on the abstract [[PaymentGateway]] trait.
 *
 * This service is unaware of which gateway implementation is injected.
 */
final class CheckoutService(gateway: PaymentGateway) extends AutoCloseable {
  def processOrder(orderId: String, amount: BigDecimal): PaymentResult = {
    println(s"[Checkout] Processing order $orderId")
    gateway.charge(amount, "USD")
  }

  def close(): Unit = println("[Checkout] Service shutdown")
}

@main def pluginArchitectureExample(): Unit = {
  val config = GatewayConfig(apiKey = "sk_test_xxx", merchantId = "acme_corp")

  println("=== Using Stripe Gateway ===")
  val stripeResource: Resource[CheckoutService] = Resource.from[CheckoutService](
    Wire(config),
    Wire.shared[StripeGateway] // Satisfies PaymentGateway via subtyping
  )

  Scope.global.scoped { scope =>
    val checkout = scope.allocate(stripeResource)
    val c        = @@.unscoped(checkout)
    val result   = c.processOrder("ORD-001", BigDecimal("99.99"))
    println(s"Result: ${result.message}")
  }

  println("\n=== Using PayPal Gateway ===")
  val paypalResource: Resource[CheckoutService] = Resource.from[CheckoutService](
    Wire(config),
    Wire.shared[PayPalGateway] // Swap to PayPal — no other changes needed
  )

  Scope.global.scoped { scope =>
    val checkout = scope.allocate(paypalResource)
    val c        = @@.unscoped(checkout)
    val result   = c.processOrder("ORD-002", BigDecimal("149.99"))
    println(s"Result: ${result.message}")
  }
}

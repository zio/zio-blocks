package zio.blocks.schema.migration

import zio.blocks.schema.Schema

/**
 * Shared Test Models (Single Source of Truth)
 * ------------------------------------------- Reduces duplication across test
 * files.
 */
object SharedTestModels {

  // --- Record Models ---
  case class UserV1(name: String, age: Int)
  case class UserV2(fullName: String, age: Int, status: String)

  // --- Enum Models (Sum Types) ---
  sealed trait Payment
  object Payment {
    case class CreditCard(number: String, cvc: Int) extends Payment
    case class PayPal(email: String)                extends Payment
  }

  sealed trait PaymentV2
  object PaymentV2 {
    case class Card(number: String, cvc: Int) extends PaymentV2
    case class PayPal(email: String)          extends PaymentV2
  }

  // --- Schemas ---
  implicit val sUserV1: Schema[UserV1]       = Schema.derived
  implicit val sUserV2: Schema[UserV2]       = Schema.derived
  implicit val sPayment: Schema[Payment]     = Schema.derived
  implicit val sPaymentV2: Schema[PaymentV2] = Schema.derived
}

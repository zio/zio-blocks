package schemaerror

import zio.blocks.schema._
import zio.blocks.schema.json.{JsonBinaryCodec, JsonFormat}

/**
 * SchemaError — Schema#transform with validation
 *
 * Demonstrates how to use SchemaError.validationFailed inside a
 * Schema#transform smart constructor to reject invalid values during both
 * decoding and encoding, and how those validation failures are surfaced as
 * SchemaError instances.
 *
 * Run with: sbt "examples/runMain schemaerror.SchemaErrorExample"
 */
object SchemaErrorExample extends App {

  // --- Domain Types ---

  /** A positive integer — construction fails if n <= 0. */
  case class PositiveInt private (value: Int)

  object PositiveInt {
    def make(n: Int): PositiveInt =
      if (n > 0) PositiveInt(n)
      else throw SchemaError.validationFailed(s"expected a positive integer, got $n")

    implicit val schema: Schema[PositiveInt] =
      Schema[Int].transform(make, _.value)
  }

  /** A non-empty string — construction fails if the value is blank. */
  case class NonEmptyString private (value: String)

  object NonEmptyString {
    def make(s: String): NonEmptyString =
      if (s.trim.nonEmpty) NonEmptyString(s)
      else throw SchemaError.validationFailed("string must not be blank")

    implicit val schema: Schema[NonEmptyString] =
      Schema[String].transform(make, _.value)
  }

  case class ShoppingCart(items: List[Product])
  object ShoppingCart {
    implicit val schema: Schema[ShoppingCart]         = Schema.derived[ShoppingCart]
    implicit val codec: JsonBinaryCodec[ShoppingCart] = schema.derive[JsonFormat.type](JsonFormat)
  }

  /** A product type combining both validated wrappers. */
  case class Product(name: NonEmptyString, quantity: PositiveInt, price: PositiveInt)

  object Product {
    implicit val schema: Schema[Product] = Schema.derived[Product]
  }

  // --- Helpers ---

  def printHeader(title: String): Unit = {
    println()
    println("=" * 60)
    println(title)
    println("=" * 60)
  }

  // --- Example 1: fromDynamicValue with multiple validation errors (accumulates) ---

  printHeader("Example 1: fromDynamicValue — all errors (accumulates)")

  // Same invalid cart as a DynamicValue. fromDynamicValue processes every
  // field/element and collects every failure before returning.
  import zio.blocks.schema.DynamicValue

  val invalidCartDv = DynamicValue.Record(
    "items" -> DynamicValue.Sequence(
      // item[0]: quantity = 0  → PositiveInt.make fails
      DynamicValue.Record(
        "name"     -> DynamicValue.string("Gadget"),
        "quantity" -> DynamicValue.int(0),
        "price"    -> DynamicValue.int(199)
      ),
      // item[1]: name is blank → NonEmptyString.make fails
      //          price = -50   → PositiveInt.make fails
      DynamicValue.Record(
        "name"     -> DynamicValue.string("   "),
        "quantity" -> DynamicValue.int(5),
        "price"    -> DynamicValue.int(-50)
      )
    )
  )

  // All three failures surface with precise paths.
  Schema[ShoppingCart].fromDynamicValue(invalidCartDv) match {
    case Right(cart) => println(s"Decoded: $cart")
    case Left(err)   =>
      err.errors.foreach(e => println(s"${e.message} (source: ${e.source})"))
  }

  // --- Example 2: ShoppingCart with multiple nested validation errors ---
  //
  // The JSON binary codec SHORT-CIRCUITS on the first validation failure —
  // it throws immediately and reports only that one error.

  printHeader("Example: JSON codec — first error only (short-circuits)")

  val invalidCartJson =
    """
      |{
      |  "items": [
      |    { "name": "Gadget", "quantity": 0,   "price": 199 },
      |    { "name": "   ",    "quantity": 5,   "price": -50 }
      |  ]
      |}
    """.stripMargin

  ShoppingCart.codec.decode(invalidCartJson) match {
    case Right(cart) => println(s"Decoded: $cart")
    case Left(err)   => err.errors.foreach(e => println(s"${e.message}"))
  }

  // --- Example 3: Manual SchemaError.validationFailed and path annotation ---

  printHeader("Example 3: Manual error construction with path")

  val err = SchemaError
    .validationFailed("must be positive")
    .atField("quantity")
    .atField("product")

  println(s"Error message : ${err.message}")
  // must be positive at: .product.quantity

  println(s"Error count   : ${err.errors.length}")
  println(s"Error variant : ${err.errors.head.getClass.getSimpleName}")

  // --- Example 4: Aggregating multiple independent errors ---

  printHeader("Example 4: Error aggregation with ++")

  val nameErr  = SchemaError.validationFailed("string must not be blank").atField("name")
  val priceErr = SchemaError.validationFailed("expected a positive integer, got -1").atField("price")
  val combined = nameErr ++ priceErr

  println(s"Total errors: ${combined.errors.length}")
  println(s"Combined message:\n${combined.message}")

}

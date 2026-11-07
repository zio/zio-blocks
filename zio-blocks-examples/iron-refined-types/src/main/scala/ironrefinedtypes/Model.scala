package ironrefinedtypes

import io.github.iltotore.iron._
import io.github.iltotore.iron.constraint.all._
import io.github.iltotore.iron.zioBlocksSchema.given
import zio.blocks.schema._

import ids._

// The shop's input model, shared by every example of
// "How to Use Iron Refined Types and Typed IDs with Schema".

// Section "Refine the Fields of a Case Class"
type EmailFormat = Match["^[^@\\s]+@[^@\\s]+\\.[a-z]+$"]
type Email       = String :| EmailFormat
type DisplayName = String :| (MinLength[1] & MaxLength[30])

final case class Customer(name: DisplayName, email: Email)

object Customer {
  implicit val schema: Schema[Customer] = Schema.derived
}

// Section "Name a Refined Type with RefinedType"
type Sku = Sku.T
object Sku extends RefinedType[String, Match["^[A-Z]{3}-[0-9]{4}$"]]

type Price = BigDecimal :| Positive

final case class Product(sku: Sku, title: DisplayName, price: Price, tags: List[DisplayName])

object Product {
  implicit val schema: Schema[Product] = Schema.derived
}

// Section "Refine Numeric Fields with a Validated Newtype"
final case class Quantity private (value: Int)

object Quantity {
  def parse(value: Int): Either[SchemaError, Quantity] =
    if (value > 0) Right(new Quantity(value))
    else Left(SchemaError.validationFailed(s"quantity must be positive, got $value"))

  implicit val schema: Schema[Quantity] =
    Schema[Int].transform(n => parse(n).fold(e => throw e, identity), _.value)
}

final case class Line(sku: Sku, quantity: Quantity)

object Line {
  implicit val schema: Schema[Line] = Schema.derived
}

// Section "Keep IDs Apart with a Typed Id"
final case class Order(id: Id[Order], customer: Id[Customer], lines: List[Line], watchers: Set[Id[Customer]])

object Order {
  implicit val schema: Schema[Order] = Schema.derived
}

package buildaschemaregistry

import zio.blocks.schema._

/**
 * How to Build a Schema Registry with DynamicSchema — The Events
 *
 * Both versions of the `BookPublished` event, shared by the step examples. In a
 * real code base there is one `BookPublished` class whose fields change between
 * releases; here the two versions live in objects named `v1` and `v2`.
 */
object v1 {
  final case class BookPublished(isbn: String, title: String, price: BigDecimal)

  object BookPublished {
    implicit val schema: Schema[BookPublished] = Schema.derived
  }
}

object v2 {
  final case class BookPublished(
    isbn: String,
    title: String,
    price: BigDecimal,
    authors: List[String],
    edition: Option[Int]
  )

  object BookPublished {
    implicit val schema: Schema[BookPublished] = Schema.derived
  }
}

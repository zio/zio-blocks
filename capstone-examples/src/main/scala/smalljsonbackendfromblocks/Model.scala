package smalljsonbackendfromblocks

import zio.blocks.schema.Schema
import zio.blocks.schema.json.JsonCodec

/**
 * Build a Small JSON Backend from Blocks — the data model
 *
 * A book in the catalog, the body of a request that adds one, and the body of
 * an error response. Each derives a `Schema`, and the JSON codecs come from
 * those schemas.
 */
final case class Book(
  id: Long,
  title: String,
  firstAuthor: String,
  year: Int,
  isbn: Option[String],
  stock: Int,
  priceCents: Int
)
object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

final case class NewBook(
  title: String,
  firstAuthor: String,
  year: Int,
  isbn: Option[String],
  stock: Int,
  priceCents: Int
)
object NewBook {
  implicit val schema: Schema[NewBook] = Schema.derived
}

final case class ApiError(error: String)
object ApiError {
  implicit val schema: Schema[ApiError] = Schema.derived
}

object Json {
  val book: JsonCodec[Book]        = Schema[Book].jsonCodec
  val books: JsonCodec[List[Book]] = Schema[List[Book]].jsonCodec
  val newBook: JsonCodec[NewBook]  = Schema[NewBook].jsonCodec
  val error: JsonCodec[ApiError]   = Schema[ApiError].jsonCodec
}

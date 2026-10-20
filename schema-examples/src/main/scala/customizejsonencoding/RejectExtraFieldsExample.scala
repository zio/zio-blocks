package customizejsonencoding

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * Customize JSON: Reject Unknown Fields
 *
 * Rejects unknown fields for one type with Modifier.noExtraFields and for every
 * type with JsonCodecDeriver#withRejectExtraFields.
 *
 * Run with: cd schema-examples && sbt "runMain
 * customizejsonencoding.RejectExtraFieldsExample"
 */
object RejectExtraFieldsExample extends App {

  @Modifier.noExtraFields()
  case class Author(fullName: String)
  object Author {
    implicit val schema: Schema[Author] = Schema.derived
  }

  case class Book(title: String, author: Author)
  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  // Book still skips an unknown subtitle
  println("""{"title":"Zionomicon","subtitle":"A Guide","author":{"fullName":"John A. De Goes"}}""".fromJson[Book])

  // Author rejects a misspelled field and reports where it is
  """{"title":"Zionomicon","author":{"fullname":"John A. De Goes"}}""".fromJson[Book] match {
    case Left(error) => println(error.message)
    case Right(book) => println(s"Decoded: $book")
  }

  // A deriver setting rejects unknown fields in every type the codec handles
  Book.schema
    .derive(JsonCodecDeriver.withRejectExtraFields(true))
    .decode("""{"title":"Zionomicon","subtitle":"A Guide","author":{"fullName":"John A. De Goes"}}""") match {
    case Left(error) => println(error.message)
    case Right(book) => println(s"Decoded: $book")
  }
}

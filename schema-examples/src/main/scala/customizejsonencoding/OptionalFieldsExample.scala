package customizejsonencoding

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * Customize JSON: Control Optional Fields, Empty Collections and Defaults
 *
 * Writes None, empty lists and default values explicitly, and makes decoding
 * require every Option field.
 *
 * Run with: cd schema-examples && sbt "runMain
 * customizejsonencoding.OptionalFieldsExample"
 */
object OptionalFieldsExample extends App {

  case class Author(fullName: String)
  object Author {
    implicit val schema: Schema[Author] = Schema.derived
  }

  case class Book(
    title: String,
    publishedYear: Int,
    isbn13: Option[String],
    author: Author,
    tags: List[String],
    edition: Option[Int],
    language: String = "en"
  )
  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  val zionomicon = Book("Zionomicon", 2024, None, Author("John A. De Goes"), Nil, None)

  // The default codec leaves out isbn13, tags, edition and language
  println(zionomicon.toJsonString)

  // Turning the three encoder settings off writes every field
  val explicitCodec: JsonCodec[Book] =
    Book.schema.derive(
      JsonCodecDeriver
        .withTransientNone(false)
        .withTransientEmptyCollection(false)
        .withTransientDefaultValue(false)
    )

  println(explicitCodec.encodeToString(zionomicon))
  println(explicitCodec.decode(explicitCodec.encodeToString(zionomicon)) == Right(zionomicon))

  // The decoder settings make decoding strict
  val strictCodec: JsonCodec[Book] =
    Book.schema.derive(JsonCodecDeriver.withRequireOptionFields(true))

  strictCodec.decode("""{"title":"Zionomicon","publishedYear":2024,"author":{"fullName":"John A. De Goes"}}""") match {
    case Left(error) => println(error.message)
    case Right(book) => println(s"Decoded: $book")
  }
}

package customizejsonencoding

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * Customize JSON: Putting It Together — Complete Example
 *
 * One API format combining snake_case field names, a renamed ISBN with an
 * alias, a "type" field for book formats, explicit null values and strict
 * decoding of unknown fields.
 *
 * Run with: cd schema-examples && sbt "runMain
 * customizejsonencoding.CompleteExample"
 */
object CompleteExample extends App {

  @Modifier.discriminator("type")
  @Modifier.caseNaming("snake_case")
  sealed trait Format
  object Format {
    case class Paperback(pages: Int)   extends Format
    case class Ebook(sizeMb: Double)   extends Format
    case class Audiobook(minutes: Int) extends Format

    implicit val schema: Schema[Format] = Schema.derived
  }

  case class Author(fullName: String)
  object Author {
    implicit val schema: Schema[Author] = Schema.derived
  }

  case class Book(
    title: String,
    publishedYear: Int,
    @Modifier.rename("isbn")
    @Modifier.alias("isbn_13")
    isbn13: Option[String],
    author: Author,
    tags: List[String],
    edition: Option[Int],
    formats: List[Format]
  )
  object Book {
    implicit val schema: Schema[Book] = Schema.derived

    // Keep the codec in one place and use it everywhere this JSON is read or written
    val apiCodec: JsonCodec[Book] =
      schema.derive(
        JsonCodecDeriver
          .withFieldNameMapper(NameMapper.SnakeCase)
          .withTransientNone(false)
          .withRejectExtraFields(true)
      )
  }

  val zionomicon = Book("Zionomicon", 2024, None, Author("John A. De Goes"), Nil, None, Nil)

  val fpInScala = Book(
    "Functional Programming in Scala",
    2014,
    Some("9781617290657"),
    Author("Paul Chiusano"),
    List("scala", "functional-programming"),
    Some(1),
    List(Format.Paperback(320))
  )

  // Encode both books
  println(Book.apiCodec.encodeToString(zionomicon))
  println(Book.apiCodec.encodeToString(fpInScala))

  // Decode a payload that still uses the alias
  println(
    Book.apiCodec.decode(
      """{"title":"Programming in Scala","published_year":2008,"isbn_13":"9780981531601","author":{"full_name":"Martin Odersky"},"edition":null}"""
    )
  )

  // Reject a payload with an unknown field
  Book.apiCodec.decode(
    """{"title":"Zionomicon","published_year":2024,"isbn":null,"author":{"full_name":"John A. De Goes"},"edition":null,"subtitle":"A Guide"}"""
  ) match {
    case Left(error) => println(error.message)
    case Right(book) => println(s"Decoded: $book")
  }

  // Check the round trip
  println(Book.apiCodec.decode(Book.apiCodec.encodeToString(fpInScala)) == Right(fpInScala))
}

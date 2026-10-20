package jsonlibrarymigration

import zio.blocks.schema._
import zio.blocks.schema.json._

import java.time.LocalDate
import java.time.format.{DateTimeFormatter, DateTimeParseException}

/**
 * Coming from circe, zio-json or jsoniter-scala — Complete Example
 *
 * A book catalog migrated to ZIO Blocks: Schema.derived for every type, one
 * JsonCodecDeriver for snake_case names, a "type" discriminator, explicit nulls
 * and empty lists, and dd/MM/yyyy dates, plus a renamed ISBN that still accepts
 * its Scala name.
 *
 * Run with: cd schema-examples && sbt "runMain
 * jsonlibrarymigration.BookCatalogMigratedExample"
 */
object BookCatalogMigratedExample extends App {

  sealed trait Format
  object Format {
    case class Paperback(pages: Int) extends Format
    case class Ebook(sizeMb: Double) extends Format

    implicit val schema: Schema[Format] = Schema.derived
  }

  case class Book(
    title: String,
    firstAuthor: String,
    year: Int,
    @Modifier.rename("isbn_13")
    @Modifier.alias("isbn")
    isbn: Option[String],
    tags: List[String],
    addedOn: LocalDate,
    formats: List[Format]
  )
  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  // Replaces an implicit Encoder/Decoder (circe), JsonCodec (zio-json) or JsonValueCodec (jsoniter-scala)
  val partnerDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

  val partnerDateCodec: JsonCodec[LocalDate] = new JsonCodec[LocalDate] {
    private def parse(s: String): LocalDate =
      try LocalDate.parse(s, partnerDateFormat)
      catch { case _: DateTimeParseException => error(s"expected a dd/MM/yyyy date, got: $s") }

    def decodeValue(in: JsonReader): LocalDate =
      parse(in.readString())

    def encodeValue(x: LocalDate, out: JsonWriter): Unit =
      out.writeVal(partnerDateFormat.format(x))

    override def decodeValue(json: Json): LocalDate = json match {
      case s: Json.String => parse(s.value)
      case _              => error("expected a JSON string")
    }

    override def encodeValue(x: LocalDate): Json =
      Json.String(partnerDateFormat.format(x))
  }

  // Replaces a circe Configuration or a jsoniter-scala CodecMakerConfig
  val apiDeriver =
    JsonCodecDeriver
      .withFieldNameMapper(NameMapper.SnakeCase)
      .withDiscriminatorKind(DiscriminatorKind.Field("type"))
      .withTransientNone(false)
      .withTransientEmptyCollection(false)
      .withInstance[LocalDate](partnerDateCodec)

  // Keep the codec in one place: toJsonString and fromJson would use the default codec instead
  val bookCodec: JsonCodec[Book] = Book.schema.deriving(apiDeriver).derive

  // The added-on dates, page counts and file sizes are sample data
  val catalog = List(
    Book("Zionomicon", "John A. De Goes", 2024, None, List("zio", "effects"), LocalDate.of(2025, 3, 14), Nil),
    Book(
      "Programming in Scala",
      "Martin Odersky",
      2008,
      Some("9780981531601"),
      List("scala"),
      LocalDate.of(2025, 4, 2),
      List(Format.Paperback(736))
    ),
    Book(
      "Functional Programming in Scala",
      "Paul Chiusano",
      2014,
      Some("9781617290657"),
      Nil,
      LocalDate.of(2025, 5, 21),
      List(Format.Paperback(320), Format.Ebook(4.5))
    )
  )

  // Encode every book
  catalog.foreach(book => println(bookCodec.encodeToString(book)))

  // Decode a payload that still sends the ISBN under its Scala name
  println(
    bookCodec.decode(
      """{"title":"Programming in Scala","first_author":"Martin Odersky","year":2008,"isbn":"9780981531601","tags":["scala"],"added_on":"02/04/2025","formats":[{"type":"Paperback","pages":736}]}"""
    )
  )

  // Read a decode error with its path
  bookCodec.decode(
    """{"title":"Zionomicon","first_author":"John A. De Goes","year":2024,"tags":[],"added_on":"2025-03-14","formats":[]}"""
  ) match {
    case Left(error) => println(error.message)
    case Right(book) => println(s"Decoded: $book")
  }

  // Check the round trip
  println(catalog.forall(book => bookCodec.decode(bookCodec.encodeToString(book)) == Right(book)))
}

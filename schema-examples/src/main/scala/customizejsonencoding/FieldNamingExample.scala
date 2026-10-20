package customizejsonencoding

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * Customize JSON: Rename Every Field With a Naming Strategy
 *
 * Applies snake_case names to every type with JsonCodecDeriver, and to one type
 * with Modifier.fieldNaming.
 *
 * Run with: cd schema-examples && sbt "runMain
 * customizejsonencoding.FieldNamingExample"
 */
object FieldNamingExample extends App {

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

  val fpInScala = Book(
    "Functional Programming in Scala",
    2014,
    Some("9781617290657"),
    Author("Paul Chiusano"),
    List("scala", "functional-programming"),
    Some(1)
  )

  // With no customization, every field is written under its Scala name
  println(fpInScala.toJsonString)

  // A codec derived with a field name mapper applies to every type it touches
  val snakeCaseCodec: JsonCodec[Book] =
    Book.schema.derive(JsonCodecDeriver.withFieldNameMapper(NameMapper.SnakeCase))

  println(snakeCaseCodec.encodeToString(fpInScala))
  println(snakeCaseCodec.decode(snakeCaseCodec.encodeToString(fpInScala)) == Right(fpInScala))

  // Modifier.fieldNaming applies to one type and travels with the schema
  @Modifier.fieldNaming("snake_case")
  case class AnnotatedBook(title: String, publishedYear: Int, isbn13: Option[String], author: Author)
  object AnnotatedBook {
    implicit val schema: Schema[AnnotatedBook] = Schema.derived
  }

  println(
    AnnotatedBook("Programming in Scala", 2008, Some("9780981531601"), Author("Martin Odersky")).toJsonString
  )
}

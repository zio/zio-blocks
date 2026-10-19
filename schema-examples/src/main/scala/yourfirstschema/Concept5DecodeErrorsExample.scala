package yourfirstschema

import zio.blocks.schema._

/**
 * Your First Schema: Round-Trip a Type Through JSON — Concept 5: Read and Fix a
 * Decode Error
 *
 * Decodes JSON with a missing field and a wrongly typed nested field, prints
 * each SchemaError message with its path, then decodes the fixed input.
 *
 * Run with: cd schema-examples && sbt "runMain
 * yourfirstschema.Concept5DecodeErrorsExample"
 */
object Concept5DecodeErrorsExample extends App {

  case class Author(name: String, born: Option[Int])

  case class Book(title: String, author: Author, year: Int, tags: List[String])

  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  val missingYear = """{"title":"Programming in Scala","author":{"name":"Martin Odersky"}}""".fromJson[Book]

  println(missingYear match {
    case Left(error) => error.message
    case Right(book) => s"Decoded: $book"
  })

  val badName = """{"title":"Programming in Scala","author":{"name":42},"year":2008}""".fromJson[Book]

  println(badName match {
    case Left(error) => error.message
    case Right(book) => s"Decoded: $book"
  })

  val fixed = """{"title":"Programming in Scala","author":{"name":"Martin Odersky"},"year":2008}""".fromJson[Book]

  println(fixed)
}

package yourfirstschema

import zio.blocks.schema._

/**
 * Your First Schema: Round-Trip a Type Through JSON — Complete Example
 *
 * Derives a schema for nested case classes, encodes to JSON, decodes back,
 * checks the round trip, handles a decode error and prints the generic view.
 *
 * Run with: cd schema-examples && sbt "runMain yourfirstschema.CompleteExample"
 */
object CompleteExample extends App {

  case class Author(name: String, born: Option[Int])

  case class Book(title: String, author: Author, year: Int, tags: List[String])

  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  val programmingInScala =
    Book("Programming in Scala", Author("Martin Odersky", Some(1958)), 2008, List("scala", "fundamentals"))

  // Encode to JSON and decode it back
  val programmingInScalaJson = programmingInScala.toJsonString
  println(programmingInScalaJson)

  val decoded = programmingInScalaJson.fromJson[Book]
  println(decoded)
  println(decoded == Right(programmingInScala))

  // Read a decode error
  val missingYear = """{"title":"Programming in Scala","author":{"name":"Martin Odersky"}}""".fromJson[Book]

  missingYear match {
    case Left(error) => println(error.message)
    case Right(book) => println(s"Decoded: $book")
  }

  // The generic view of the same data
  println(programmingInScala.show)
}

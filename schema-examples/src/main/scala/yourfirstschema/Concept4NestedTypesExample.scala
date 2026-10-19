package yourfirstschema

import zio.blocks.schema._

/**
 * Your First Schema: Round-Trip a Type Through JSON — Concept 4: Grow the
 * Model: Nested Types, Optional Fields and Lists
 *
 * Adds a nested Author, an optional birth year and a list of tags, and shows
 * that None and empty lists are left out of the JSON and restored on decode.
 *
 * Run with: cd schema-examples && sbt "runMain
 * yourfirstschema.Concept4NestedTypesExample"
 */
object Concept4NestedTypesExample extends App {

  case class Author(name: String, born: Option[Int])

  case class Book(title: String, author: Author, year: Int, tags: List[String])

  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  val programmingInScala =
    Book("Programming in Scala", Author("Martin Odersky", Some(1958)), 2008, List("scala", "fundamentals"))

  println(programmingInScala.toJsonString)

  val zionomicon = Book("Zionomicon", Author("John A. De Goes", None), 2024, Nil)

  println(zionomicon.toJsonString)

  println("""{"title":"Zionomicon","author":{"name":"John A. De Goes"},"year":2024}""".fromJson[Book])
}

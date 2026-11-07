package yourfirstschema

import zio.blocks.schema._

/**
 * Your First Schema: Round-Trip a Type Through JSON — Concept 6: Look at the
 * Same Data Two More Ways
 *
 * Prints the DynamicValue view of a book and the JSON Schema document derived
 * from Book.schema.
 *
 * Run with: cd schema-examples && sbt "runMain
 * yourfirstschema.Concept6OtherViewsExample"
 */
object Concept6OtherViewsExample extends App {

  case class Author(name: String, born: Option[Int])

  case class Book(title: String, author: Author, year: Int, tags: List[String])

  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  val programmingInScala =
    Book("Programming in Scala", Author("Martin Odersky", Some(1958)), 2008, List("scala", "fundamentals"))

  println(Book.schema.toDynamicValue(programmingInScala))

  println(Book.schema.toJsonSchema)
}

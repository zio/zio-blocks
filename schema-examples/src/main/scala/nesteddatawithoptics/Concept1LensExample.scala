package nesteddatawithoptics

import zio.blocks.schema._

/**
 * Navigate and Update Nested Data with Optics — Concept 1: Put a Lens on One
 * Field
 *
 * Declares lenses on a book's title and first author in the companion object,
 * then reads, replaces and modifies the title.
 *
 * Run with: cd schema-examples && sbt "runMain
 * nesteddatawithoptics.Concept1LensExample"
 */
object Concept1LensExample extends App {

  case class Author(name: String)

  sealed trait Format
  case class Paperback(pages: Int) extends Format
  case class Ebook(sizeMb: Double) extends Format

  case class Book(title: String, author: Author, format: Format, stock: Int)

  object Book extends CompanionOptics[Book] {
    implicit val schema: Schema[Book] = Schema.derived

    val title: Lens[Book, String]      = $(_.title)
    val authorName: Lens[Book, String] = $(_.author.name)
  }

  val programmingInScala = Book("Programming in Scala", Author("Martin Odersky"), Paperback(860), 7)

  println(Book.title.get(programmingInScala))

  println(Book.title.replace(programmingInScala, "Programming in Scala, Fifth Edition"))

  println(Book.title.modify(programmingInScala, _.toUpperCase))

  println(programmingInScala)

  println(Book.title)
}

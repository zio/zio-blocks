package nesteddatawithoptics

import zio.blocks.schema._

/**
 * Navigate and Update Nested Data with Optics — Concept 2: Follow a Path Into
 * Nested Data
 *
 * Uses a lens on the first author's name, compares its update with nested copy
 * calls and shows which values are shared with the original.
 *
 * Run with: cd schema-examples && sbt "runMain
 * nesteddatawithoptics.Concept2NestedPathExample"
 */
object Concept2NestedPathExample extends App {

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

  println(Book.authorName.get(programmingInScala))

  val shouting = Book.authorName.modify(programmingInScala, _.toUpperCase)

  val byHand =
    programmingInScala.copy(author = programmingInScala.author.copy(name = programmingInScala.author.name.toUpperCase))

  println(shouting)
  println(shouting == byHand)

  println(shouting.author eq programmingInScala.author)
  println(shouting.format eq programmingInScala.format)
}

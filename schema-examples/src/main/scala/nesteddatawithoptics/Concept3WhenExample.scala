package nesteddatawithoptics

import zio.blocks.schema._

/**
 * Navigate and Update Nested Data with Optics — Concept 3: Focus on One Case
 * with .when[B]
 *
 * Declares an optional on the page count of paperbacks and shows that it finds
 * nothing in an ebook.
 *
 * Run with: cd schema-examples && sbt "runMain
 * nesteddatawithoptics.Concept3WhenExample"
 */
object Concept3WhenExample extends App {

  case class Author(name: String)

  sealed trait Format
  case class Paperback(pages: Int) extends Format
  case class Ebook(sizeMb: Double) extends Format

  case class Book(title: String, author: Author, format: Format, stock: Int)

  object Book extends CompanionOptics[Book] {
    implicit val schema: Schema[Book] = Schema.derived

    val title: Lens[Book, String]           = $(_.title)
    val authorName: Lens[Book, String]      = $(_.author.name)
    val paperbackPages: Optional[Book, Int] = $(_.format.when[Paperback].pages)
  }

  val programmingInScala = Book("Programming in Scala", Author("Martin Odersky"), Paperback(860), 7)

  val zionomicon = Book("Zionomicon", Author("John A. De Goes"), Ebook(5.2), 3)

  println(Book.paperbackPages.getOption(programmingInScala))
  println(Book.paperbackPages.getOption(zionomicon))

  println(Book.paperbackPages.modify(programmingInScala, _ + 16))

  println(Book.paperbackPages.modify(zionomicon, _ + 16) == zionomicon)
}

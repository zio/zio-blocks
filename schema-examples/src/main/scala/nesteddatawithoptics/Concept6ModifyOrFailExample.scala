package nesteddatawithoptics

import zio.blocks.schema._

/**
 * Navigate and Update Nested Data with Optics — Concept 6: See Why an Update
 * Failed with modifyOrFail
 *
 * Prints the OpticCheck message for a wrong case and for a missing key, and the
 * Right result of a successful update.
 *
 * Run with: cd schema-examples && sbt "runMain
 * nesteddatawithoptics.Concept6ModifyOrFailExample"
 */
object Concept6ModifyOrFailExample extends App {

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

  case class Shelf(name: String, books: List[Book])

  case class Inventory(store: String, shelves: List[Shelf], prices: Map[String, BigDecimal])

  object Inventory extends CompanionOptics[Inventory] {
    implicit val schema: Schema[Inventory] = Schema.derived

    val allStock: Traversal[Inventory, Int]              = $(_.shelves.each.books.each.stock)
    val zionomiconPrice: Optional[Inventory, BigDecimal] = $(_.prices.atKey("Zionomicon"))
    val fpInScalaPrice: Optional[Inventory, BigDecimal]  = $(_.prices.atKey("Functional Programming in Scala"))
  }

  val programmingInScala = Book("Programming in Scala", Author("Martin Odersky"), Paperback(860), 7)

  val zionomicon = Book("Zionomicon", Author("John A. De Goes"), Ebook(5.2), 3)

  val fpInScala = Book("Functional Programming in Scala", Author("Paul Chiusano"), Paperback(320), 0)

  val inventory = Inventory(
    store = "Lambda Books",
    shelves = List(
      Shelf("Scala", List(programmingInScala, fpInScala)),
      Shelf("ZIO", List(zionomicon))
    ),
    prices = Map("Programming in Scala" -> BigDecimal("59.95"), "Zionomicon" -> BigDecimal("49.99"))
  )

  Book.paperbackPages.modifyOrFail(zionomicon, _ + 16) match {
    case Left(check) => println(check.message)
    case Right(book) => println(s"Updated: $book")
  }

  Inventory.fpInScalaPrice.modifyOrFail(inventory, _ + 1) match {
    case Left(check)    => println(check.message)
    case Right(updated) => println(s"Updated: ${updated.prices}")
  }

  println(Book.paperbackPages.modifyOrFail(programmingInScala, _ + 16).map(_.format))
}

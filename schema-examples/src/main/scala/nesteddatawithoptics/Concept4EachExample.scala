package nesteddatawithoptics

import zio.blocks.schema._

/**
 * Navigate and Update Nested Data with Optics — Concept 4: Update Every Element
 * with .each
 *
 * Declares a traversal over the stock of every book on every shelf, folds it
 * into a list and restocks every book in one call.
 *
 * Run with: cd schema-examples && sbt "runMain
 * nesteddatawithoptics.Concept4EachExample"
 */
object Concept4EachExample extends App {

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

    val allStock: Traversal[Inventory, Int] = $(_.shelves.each.books.each.stock)
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

  println(Inventory.allStock.fold(inventory)(List.empty[Int], (stocks, stock) => stocks :+ stock))

  val restocked = Inventory.allStock.modify(inventory, _ + 1)

  println(Inventory.allStock.fold(restocked)(List.empty[Int], (stocks, stock) => stocks :+ stock))
}

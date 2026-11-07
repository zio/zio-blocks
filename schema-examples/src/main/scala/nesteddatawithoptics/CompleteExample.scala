package nesteddatawithoptics

import zio.blocks.schema._

/**
 * Navigate and Update Nested Data with Optics — Complete Example
 *
 * Combines lenses, a nested path, an optional on one case and on one map key, a
 * traversal over every book, modifyOrFail for a missing target and the same
 * path as a p"..." string.
 *
 * Run with: cd schema-examples && sbt "runMain
 * nesteddatawithoptics.CompleteExample"
 */
object CompleteExample extends App {

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

  println(Book.title.get(programmingInScala))

  println(Book.title.replace(programmingInScala, "Programming in Scala, Fifth Edition"))

  println(Book.title.modify(programmingInScala, _.toUpperCase))

  println(programmingInScala)

  println(Book.title)

  println(Book.authorName.get(programmingInScala))

  val shouting = Book.authorName.modify(programmingInScala, _.toUpperCase)

  val byHand =
    programmingInScala.copy(author = programmingInScala.author.copy(name = programmingInScala.author.name.toUpperCase))

  println(shouting)
  println(shouting == byHand)

  println(shouting.author eq programmingInScala.author)
  println(shouting.format eq programmingInScala.format)

  println(Book.paperbackPages.getOption(programmingInScala))
  println(Book.paperbackPages.getOption(zionomicon))

  println(Book.paperbackPages.modify(programmingInScala, _ + 16))

  println(Book.paperbackPages.modify(zionomicon, _ + 16) == zionomicon)

  println(Inventory.allStock.fold(inventory)(List.empty[Int], (stocks, stock) => stocks :+ stock))

  val restocked = Inventory.allStock.modify(inventory, _ + 1)

  println(Inventory.allStock.fold(restocked)(List.empty[Int], (stocks, stock) => stocks :+ stock))

  println(Inventory.zionomiconPrice.getOption(inventory))

  println(Inventory.zionomiconPrice.replace(inventory, BigDecimal("44.99")).prices)

  println(Inventory.fpInScalaPrice.getOption(inventory))

  println(Inventory.fpInScalaPrice.modify(inventory, _ + 1) == inventory)

  Book.paperbackPages.modifyOrFail(zionomicon, _ + 16) match {
    case Left(check) => println(check.message)
    case Right(book) => println(s"Updated: $book")
  }

  Inventory.fpInScalaPrice.modifyOrFail(inventory, _ + 1) match {
    case Left(check)    => println(check.message)
    case Right(updated) => println(s"Updated: ${updated.prices}")
  }

  println(Book.paperbackPages.modifyOrFail(programmingInScala, _ + 16).map(_.format))

  val stockPath: DynamicOptic = p".shelves[*].books[*].stock"

  println(stockPath)
  println(Inventory.allStock.toDynamic == stockPath)

  val dynamicInventory = Inventory.schema.toDynamicValue(inventory)

  println(dynamicInventory.get(stockPath).toChunk.mkString(", "))
}

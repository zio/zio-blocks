package readandwriteyaml

import zio.blocks.schema._
import zio.blocks.schema.yaml._

/**
 * How to Read and Write YAML Documents — Report Decode Errors
 *
 * Decodes four damaged copies of the catalog and prints each error message with
 * the path of the bad value.
 *
 * Run with: cd schema-examples && sbt "runMain
 * readandwriteyaml.Step2DecodeErrorsExample"
 */
object Step2DecodeErrorsExample {

  sealed trait Format

  object Format {
    case object Paperback extends Format
    case object Hardcover extends Format
    case object Ebook     extends Format

    val all: List[Format] = List(Paperback, Hardcover, Ebook)

    implicit val schema: Schema[Format] = Schema[String].transform[Format](
      name => all.find(_.toString == name).getOrElse(throw new IllegalArgumentException(s"Unknown format: $name")),
      _.toString
    )
  }

  case class Book(
    title: String,
    authors: List[String],
    isbn: String,
    price: Double,
    pageCount: Int,
    tags: List[String],
    formats: List[Format]
  )

  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  case class Catalog(store: String, books: List[Book])

  object Catalog {
    implicit val schema: Schema[Catalog] = Schema.derived
  }

  val catalogCodec: YamlCodec[Catalog] = Catalog.schema.derive(YamlFormat)

  val catalogYaml: String =
    """# Books on the front table this month
      |store: Scala Books Berlin
      |books:
      |  - title: Zionomicon
      |    authors: [John A. De Goes, Adam Fraser]
      |    isbn: 978-0-9862-0040-6
      |    price: 49.99
      |    page-count: 744
      |    tags: [zio, effects, concurrency]
      |    formats: [Ebook]
      |  - title: Programming in Scala
      |    authors:
      |      - Martin Odersky
      |      - Lex Spoon
      |      - Bill Venners
      |      - Frank Sommers
      |    isbn: "9780997148008"
      |    price: 59.95
      |    page-count: 852
      |    tags: [scala, language]
      |    formats:
      |      - Paperback
      |      - Ebook
      |""".stripMargin

  val zionomicon: Book = Book(
    title = "Zionomicon",
    authors = List("John A. De Goes", "Adam Fraser"),
    isbn = "978-0-9862-0040-6",
    price = 49.99,
    pageCount = 744,
    tags = List("zio", "effects"),
    formats = List(Format.Ebook, Format.Paperback)
  )

  def report(yaml: String): Unit =
    catalogCodec.decode(yaml) match {
      case Right(catalog) => println(s"OK: ${catalog.books.size} books")
      case Left(error)    => println(error.getMessage)
    }

  def main(args: Array[String]): Unit = {
    report(catalogYaml.replace("price: 59.95", "price: cheap"))
    report(catalogYaml.replace("page-count: 744", "page-count: many"))
    report(catalogYaml.replace("formats: [Ebook]", "formats: [Kindle]"))
    report(catalogYaml.replace("    isbn: \"9780997148008\"\n", ""))
  }
}

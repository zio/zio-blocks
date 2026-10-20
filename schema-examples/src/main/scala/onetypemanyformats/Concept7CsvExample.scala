package onetypemanyformats

import java.nio.CharBuffer
import zio.blocks.schema._
import zio.blocks.schema.csv.CsvFormat

/**
 * One Type, Many Formats — Concept 7: Flatten the Catalog for CSV
 *
 * Shows why a Book with tags cannot become CSV, derives the codec for the flat
 * BookRow, prints the CSV table and checks the round trip.
 *
 * Run with: cd schema-examples && sbt "runMain
 * onetypemanyformats.Concept7CsvExample"
 */
object Concept7CsvExample extends App {

  case class Book(title: String, author: String, year: Int, tags: List[String])

  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  case class Catalog(books: List[Book])

  object Catalog {
    implicit val schema: Schema[Catalog] = Schema.derived
  }

  val catalog = Catalog(
    List(
      Book("Zionomicon", "John A. De Goes, Adam Fraser", 2024, List("zio", "effects")),
      Book("Programming in Scala", "Martin Odersky, Lex Spoon, Bill Venners", 2008, List("scala", "fundamentals")),
      Book("Functional Programming in Scala", "Paul Chiusano, Rúnar Bjarnason", 2014, List("scala", "fp"))
    )
  )

  println(scala.util.Try(Book.schema.derive(CsvFormat)))

  case class Maybe(title: String, subtitle: Option[String])

  object Maybe {
    implicit val schema: Schema[Maybe] = Schema.derived
  }

  println(scala.util.Try(Maybe.schema.derive(CsvFormat)))

  case class BookRow(title: String, author: String, year: Int)

  object BookRow {
    implicit val schema: Schema[BookRow] = Schema.derived
  }

  val rows = catalog.books.map(book => BookRow(book.title, book.author, book.year))

  val csvCodec = BookRow.schema.derive(CsvFormat)

  def toCsvLine(row: BookRow): String = {
    val buffer = CharBuffer.allocate(256)
    csvCodec.encode(row, buffer)
    buffer.flip()
    buffer.toString
  }

  val csvLines = rows.map(toCsvLine)

  println(csvCodec.headerNames.mkString(","))

  csvLines.foreach(line => println(line.stripLineEnd))

  println(csvLines.map(line => csvCodec.decode(CharBuffer.wrap(line))) == rows.map(Right(_)))
}

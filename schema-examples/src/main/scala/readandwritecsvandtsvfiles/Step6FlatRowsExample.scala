package readandwritecsvandtsvfiles

import java.nio.CharBuffer
import zio.blocks.schema._
import zio.blocks.schema.csv._

/**
 * How to Read and Write CSV and TSV Files — Flatten Lists and Optional Fields
 *
 * Shows the CSV deriver rejecting a record with a List field at run time, then
 * exports books through a flat row type that joins the authors and turns a
 * missing ISBN into an empty cell, and imports them back.
 *
 * Run with: cd schema-examples && sbt "runMain
 * readandwritecsvandtsvfiles.Step6FlatRowsExample"
 */
object Step6FlatRowsExample {

  case class Book(title: String, authors: List[String], year: Int, isbn: Option[String])

  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  case class BookExportRow(title: String, authors: String, year: Int, isbn: String)

  object BookExportRow {
    implicit val schema: Schema[BookExportRow] = Schema.derived

    def fromBook(book: Book): BookExportRow =
      BookExportRow(book.title, book.authors.mkString("; "), book.year, book.isbn.getOrElse(""))

    def toBook(row: BookExportRow): Book =
      Book(row.title, row.authors.split("; ").toList, row.year, Some(row.isbn).filter(_.nonEmpty))
  }

  def encodeLine[A](codec: CsvCodec[A], value: A): String = {
    val buffer = CharBuffer.allocate(4096)
    codec.encode(value, buffer)
    buffer.flip()
    buffer.toString
  }

  def writeCsv[A](codec: CsvCodec[A], values: Iterable[A]): String =
    CsvWriter.writeHeader(codec.headerNames, CsvConfig.default) + values.map(value => encodeLine(codec, value)).mkString

  def readRecords[A](codec: CsvCodec[A], text: String): Either[String, IndexedSeq[A]] =
    CsvReader.readAll(text, CsvConfig.default) match {
      case Left(error)      => Left(error.getMessage)
      case Right((_, rows)) =>
        val decoded = rows.map { cells =>
          codec.decode(CharBuffer.wrap(CsvWriter.writeRow(cells, CsvConfig.default))).left.map(_.getMessage)
        }
        decoded.collectFirst { case Left(error) => error }.toLeft(decoded.collect { case Right(a) => a })
    }

  def main(args: Array[String]): Unit = {
    println(scala.util.Try(Book.schema.derive(CsvFormat)))

    val exportCodec: CsvCodec[BookExportRow] = BookExportRow.schema.derive(CsvFormat)

    val catalog: List[Book] = List(
      Book("Zionomicon", List("John A. De Goes", "Adam Fraser", "Milad Khajavi"), 2024, None),
      Book("Programming in Scala", List("Martin Odersky", "Lex Spoon", "Bill Venners"), 2008, Some("978-0-9815316-0-1"))
    )

    val catalogCsv: String = writeCsv(exportCodec, catalog.map(BookExportRow.fromBook))
    catalogCsv.linesIterator.foreach(println)

    println(readRecords(exportCodec, catalogCsv).map(_.map(BookExportRow.toBook) == catalog))
  }
}

package readandwritecsvandtsvfiles

import java.nio.CharBuffer
import zio.blocks.schema._
import zio.blocks.schema.csv._

/**
 * How to Read and Write CSV and TSV Files — Read and Write TSV
 *
 * Shows that the derived codec only reads commas, then writes the inventory
 * with CsvConfig.tsv through CsvWriter.writeAll and reads it back with
 * CsvReader.readAll.
 *
 * Run with: cd schema-examples && sbt "runMain
 * readandwritecsvandtsvfiles.Step4TsvExample"
 */
object Step4TsvExample {

  case class BookRow(title: String, authors: String, year: Int, copies: Int)

  object BookRow {
    implicit val schema: Schema[BookRow] = Schema.derived
  }

  final class RowDecoder[A](codec: CsvCodec[A], width: Int, positions: IndexedSeq[Int]) {
    def decode(cells: IndexedSeq[String]): Either[String, A] =
      if (cells.length != width) Left(s"expected $width cells, got ${cells.length}")
      else {
        val line = CsvWriter.writeRow(positions.map(cells), CsvConfig.default)
        codec.decode(CharBuffer.wrap(line)).left.map(_.getMessage)
      }
  }

  object RowDecoder {
    def fromHeader[A](codec: CsvCodec[A], header: IndexedSeq[String]): Either[String, RowDecoder[A]] = {
      val missing = codec.headerNames.filterNot(header.contains)
      if (missing.nonEmpty) Left(s"missing column(s): ${missing.mkString(", ")}")
      else Right(new RowDecoder(codec, header.length, codec.headerNames.map(name => header.indexOf(name))))
    }
  }

  final case class Report[A](records: IndexedSeq[A], errors: IndexedSeq[String])

  def readCsv[A](codec: CsvCodec[A], text: String, config: CsvConfig): Either[String, Report[A]] =
    CsvReader.readAll(text, config) match {
      case Left(error)           => Left(error.getMessage)
      case Right((header, rows)) =>
        RowDecoder.fromHeader(codec, header).map { decoder =>
          val results = rows.zipWithIndex.map { case (cells, index) =>
            decoder.decode(cells).left.map(error => s"row ${index + 2}: $error")
          }
          Report(results.collect { case Right(a) => a }, results.collect { case Left(e) => e })
        }
    }

  def encodeLine[A](codec: CsvCodec[A], value: A): String = {
    val buffer = CharBuffer.allocate(4096)
    codec.encode(value, buffer)
    buffer.flip()
    buffer.toString
  }

  def encodeCells[A](codec: CsvCodec[A], value: A): IndexedSeq[String] =
    CsvReader.readRow(encodeLine(codec, value), 0, CsvConfig.default) match {
      case Right((cells, _)) => cells
      case Left(error)       => throw error
    }

  def writeDelimited[A](codec: CsvCodec[A], values: Iterable[A], config: CsvConfig): String =
    CsvWriter.writeAll(codec.headerNames, values.map(value => encodeCells(codec, value)), config)

  def main(args: Array[String]): Unit = {
    val bookCodec: CsvCodec[BookRow] = BookRow.schema.derive(CsvFormat)

    println(bookCodec.decode(CharBuffer.wrap("Zionomicon\tJohn A. De Goes, Adam Fraser and Milad Khajavi\t2024\t12")))

    val restocked: List[BookRow] = List(
      BookRow("Zionomicon", "John A. De Goes, Adam Fraser and Milad Khajavi", 2024, 20),
      BookRow("Programming in Scala", "Martin Odersky, Lex Spoon, Bill Venners", 2008, 7),
      BookRow("Functional Programming in Scala", "Paul Chiusano, Rúnar Bjarnason", 2014, 9)
    )

    val restockedTsv: String = writeDelimited(bookCodec, restocked, CsvConfig.tsv)
    restockedTsv.linesIterator.foreach(println)

    println(readCsv(bookCodec, restockedTsv, CsvConfig.tsv).map(_.records == restocked))
  }
}

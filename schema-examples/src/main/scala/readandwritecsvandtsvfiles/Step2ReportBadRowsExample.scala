package readandwritecsvandtsvfiles

import java.nio.CharBuffer
import zio.blocks.schema._
import zio.blocks.schema.csv._

/**
 * How to Read and Write CSV and TSV Files — Report Every Bad Row
 *
 * Reads a damaged inventory, keeps the good records and prints every bad row
 * with its row number. Also shows a file with a missing column and a file with
 * an unclosed quote.
 *
 * Run with: cd schema-examples && sbt "runMain
 * readandwritecsvandtsvfiles.Step2ReportBadRowsExample"
 */
object Step2ReportBadRowsExample {

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

  def main(args: Array[String]): Unit = {
    val bookCodec: CsvCodec[BookRow] = BookRow.schema.derive(CsvFormat)

    val damagedCsv: String =
      """title,year,authors,copies
        |Zionomicon,2024,"John A. De Goes, Adam Fraser and Milad Khajavi",12
        |Programming in Scala,MMVIII,"Martin Odersky, Lex Spoon, Bill Venners",7
        |Functional Programming in Scala,2014,"Paul Chiusano, Rúnar Bjarnason"
        |""".stripMargin

    readCsv(bookCodec, damagedCsv, CsvConfig.default).foreach { report =>
      report.records.foreach(println)
      report.errors.foreach(println)
    }

    println(
      readCsv(
        bookCodec,
        "title,year,authors\nZionomicon,2024,\"John A. De Goes, Adam Fraser and Milad Khajavi\"\n",
        CsvConfig.default
      )
    )

    val unclosedCsv: String =
      """title,year,authors,copies
        |Zionomicon,2024,"John A. De Goes, Adam Fraser and Milad Khajavi",12
        |Programming in Scala,2008,"Martin Odersky, Lex Spoon, Bill Venners,7
        |""".stripMargin

    println(readCsv(bookCodec, unclosedCsv, CsvConfig.default))
  }
}

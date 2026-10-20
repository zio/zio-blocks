package readandwritecsvandtsvfiles

import java.nio.CharBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import zio.blocks.schema._
import zio.blocks.schema.csv._

/**
 * How to Read and Write CSV and TSV Files — Read a CSV File With a Header
 *
 * Writes a small inventory file whose columns are in a different order from
 * BookRow, reads it with CsvReader.readAll, and decodes each row by column name
 * with the derived CSV codec.
 *
 * Run with: cd schema-examples && sbt "runMain
 * readandwritecsvandtsvfiles.Step1ReadCsvExample"
 */
object Step1ReadCsvExample {

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

  def main(args: Array[String]): Unit = {
    val bookCodec: CsvCodec[BookRow] = BookRow.schema.derive(CsvFormat)
    println(bookCodec.headerNames)

    val inventoryCsv: String =
      """title,year,authors,copies
        |Zionomicon,2024,"John A. De Goes, Adam Fraser",12
        |Programming in Scala,2008,"Martin Odersky, Lex Spoon, Bill Venners",7
        |Functional Programming in Scala,2014,"Paul Chiusano, Rúnar Bjarnason",5
        |""".stripMargin

    val inventoryPath: Path = Files.createTempFile("inventory", ".csv")
    Files.writeString(inventoryPath, inventoryCsv, UTF_8)

    val inventory = CsvReader.readAll(Files.readString(inventoryPath, UTF_8), CsvConfig.default)
    println(inventory)

    inventory.foreach { case (header, rows) =>
      RowDecoder.fromHeader(bookCodec, header) match {
        case Left(error)    => println(error)
        case Right(decoder) => rows.map(decoder.decode).foreach(println)
      }
    }
  }
}

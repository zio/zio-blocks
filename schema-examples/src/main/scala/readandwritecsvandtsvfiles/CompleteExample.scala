package readandwritecsvandtsvfiles

import java.io.BufferedReader
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.annotation.tailrec
import scala.util.Using
import zio.blocks.schema._
import zio.blocks.schema.csv._

/**
 * How to Read and Write CSV and TSV Files — Complete Example
 *
 * Reads an inventory CSV file one record at a time, matching columns by header
 * name, reports every bad row with its line in the file, and writes each good
 * record to a TSV file as soon as it is decoded.
 *
 * Run with: cd schema-examples && sbt "runMain
 * readandwritecsvandtsvfiles.CompleteExample"
 */
object CompleteExample {

  case class BookRow(title: String, authors: String, year: Int, copies: Int)

  object BookRow {
    implicit val schema: Schema[BookRow] = Schema.derived
  }

  /** Decodes rows whose columns may come in any order, by header name. */
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

  /** Joins physical lines into CSV records, each with its first line number. */
  def csvRecords(reader: BufferedReader, quoteChar: Char): Iterator[(Int, String)] = {
    val lines = Iterator.continually(reader.readLine()).takeWhile(_ != null).zip(Iterator.from(1))

    @tailrec
    def complete(record: String): String =
      if (record.count(_ == quoteChar) % 2 == 0 || !lines.hasNext) record
      else complete(record + "\n" + lines.next()._1)

    lines.map { case (firstLine, lineNumber) => (lineNumber, complete(firstLine)) }
  }

  /** Opens a CSV or TSV file and passes its decoded rows to `use`. */
  def withCsvFile[A, B](path: Path, codec: CsvCodec[A], config: CsvConfig)(
    use: Iterator[(Int, Either[String, A])] => B
  ): Either[String, B] =
    Using.resource(Files.newBufferedReader(path, UTF_8)) { reader =>
      val records = csvRecords(reader, config.quoteChar)
      if (!records.hasNext) Left("the file is empty")
      else {
        val (_, headerText) = records.next()
        CsvReader.readRow(headerText, 0, config).left.map(_.getMessage).flatMap { case (header, _) =>
          RowDecoder.fromHeader(codec, header).map { decoder =>
            use(records.filter(_._2.nonEmpty).map { case (line, text) =>
              val decoded = CsvReader.readRow(text, 0, config) match {
                case Left(error)       => Left(s"line ${line + error.row - 1}, column ${error.column}: ${error.message}")
                case Right((cells, _)) => decoder.decode(cells).left.map(error => s"line $line: $error")
              }
              (line, decoded)
            })
          }
        }
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

  def main(args: Array[String]): Unit = {
    val bookCodec: CsvCodec[BookRow] = BookRow.schema.derive(CsvFormat)

    val inventoryPath: Path = Files.createTempFile("inventory", ".csv")
    Files.writeString(
      inventoryPath,
      """title,year,authors,copies
        |Zionomicon,2024,"John A. De Goes, Adam Fraser",12
        |Programming in Scala,MMVIII,"Martin Odersky, Lex Spoon, Bill Venners",7
        |Functional Programming in Scala,2014,"Paul Chiusano, Rúnar Bjarnason",5
        |Scala with Cats,2017,"Noel Welsh, Dave Gurnell"
        |""".stripMargin,
      UTF_8
    )

    val tsvPath: Path = Files.createTempFile("inventory", ".tsv")

    val result = Using.resource(Files.newBufferedWriter(tsvPath, UTF_8)) { writer =>
      writer.write(CsvWriter.writeHeader(bookCodec.headerNames, CsvConfig.tsv))
      withCsvFile(inventoryPath, bookCodec, CsvConfig.default) { rows =>
        rows.foldLeft(0) {
          case (count, (_, Right(book))) =>
            writer.write(CsvWriter.writeRow(encodeCells(bookCodec, book), CsvConfig.tsv))
            count + 1
          case (count, (_, Left(error))) =>
            println(s"skipped $error")
            count
        }
      }
    }

    result match {
      case Left(error)  => println(s"cannot read ${inventoryPath.getFileName}: $error")
      case Right(count) =>
        println(s"wrote $count records to the TSV file:")
        Files.readString(tsvPath, UTF_8).linesIterator.foreach(println)
    }
  }
}

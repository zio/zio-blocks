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
 * How to Read and Write CSV and TSV Files — Process a Large File Row by Row
 *
 * Reads a file one record at a time, joining the lines of a quoted cell that
 * spans several lines, and reports bad rows with their line in the file. Then
 * writes 100,000 rows and adds up the copies without loading the file into
 * memory.
 *
 * Run with: cd schema-examples && sbt "runMain
 * readandwritecsvandtsvfiles.Step5LargeFileExample"
 */
object Step5LargeFileExample {

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

  def encodeLine[A](codec: CsvCodec[A], value: A): String = {
    val buffer = CharBuffer.allocate(4096)
    codec.encode(value, buffer)
    buffer.flip()
    buffer.toString
  }

  def csvRecords(reader: BufferedReader, quoteChar: Char): Iterator[(Int, String)] = {
    val lines = Iterator.continually(reader.readLine()).takeWhile(_ != null).zip(Iterator.from(1))

    @tailrec
    def complete(record: String): String =
      if (record.count(_ == quoteChar) % 2 == 0 || !lines.hasNext) record
      else complete(record + "\n" + lines.next()._1)

    lines.map { case (firstLine, lineNumber) => (lineNumber, complete(firstLine)) }
  }

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

  def writeCsvFile[A](path: Path, codec: CsvCodec[A], values: Iterator[A]): Unit =
    Using.resource(Files.newBufferedWriter(path, UTF_8)) { writer =>
      writer.write(CsvWriter.writeHeader(codec.headerNames, CsvConfig.default))
      values.foreach(value => writer.write(encodeLine(codec, value)))
    }

  def main(args: Array[String]): Unit = {
    val bookCodec: CsvCodec[BookRow] = BookRow.schema.derive(CsvFormat)

    val awkwardPath: Path = Files.createTempFile("awkward", ".csv")
    Files.writeString(
      awkwardPath,
      """title,year,authors,copies
        |Zionomicon,2024,"John A. De Goes,
        |Adam Fraser",12
        |Programming in Scala,MMVIII,"Martin Odersky, Lex Spoon, Bill Venners",7
        |"Functional Programming in Scala"!,2014,"Paul Chiusano, Rúnar Bjarnason",5
        |""".stripMargin,
      UTF_8
    )

    println(withCsvFile(awkwardPath, bookCodec, CsvConfig.default)(rows => rows.foreach(println)))

    val restocked: List[BookRow] = List(
      BookRow("Zionomicon", "John A. De Goes, Adam Fraser", 2024, 20),
      BookRow("Programming in Scala", "Martin Odersky, Lex Spoon, Bill Venners", 2008, 7),
      BookRow("Functional Programming in Scala", "Paul Chiusano, Rúnar Bjarnason", 2014, 9)
    )

    val largePath: Path = Files.createTempFile("large-inventory", ".csv")
    writeCsvFile(largePath, bookCodec, Iterator.continually(restocked).flatten.take(100000))

    val totals = withCsvFile(largePath, bookCodec, CsvConfig.default) { rows =>
      rows.foldLeft((0, 0L)) {
        case ((count, copies), (_, Right(book))) => (count + 1, copies + book.copies)
        case (totals, (_, Left(error)))          => println(error); totals
      }
    }
    println(totals)
  }
}

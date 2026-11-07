package readandwritecsvandtsvfiles

import java.nio.CharBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import zio.blocks.schema._
import zio.blocks.schema.csv._

/**
 * How to Read and Write CSV and TSV Files — Write Records as CSV
 *
 * Encodes records with the derived CSV codec, adds a header with
 * CsvWriter.writeHeader, writes the text to a file and reads it back to check
 * the round trip.
 *
 * Run with: cd schema-examples && sbt "runMain
 * readandwritecsvandtsvfiles.Step3WriteCsvExample"
 */
object Step3WriteCsvExample {

  case class BookRow(title: String, authors: String, year: Int, copies: Int)

  object BookRow {
    implicit val schema: Schema[BookRow] = Schema.derived
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
    val bookCodec: CsvCodec[BookRow] = BookRow.schema.derive(CsvFormat)

    val restocked: List[BookRow] = List(
      BookRow("Zionomicon", "John A. De Goes, Adam Fraser and Milad Khajavi", 2024, 20),
      BookRow("Programming in Scala", "Martin Odersky, Lex Spoon, Bill Venners", 2008, 7),
      BookRow("Functional Programming in Scala", "Paul Chiusano, Rúnar Bjarnason", 2014, 9)
    )

    val restockedCsv: String = writeCsv(bookCodec, restocked)
    restockedCsv.linesIterator.foreach(println)

    println(CsvWriter.writeRow(IndexedSeq("Zionomicon", "the \"ZIO\" book"), CsvConfig.default).stripLineEnd)

    val restockedPath: Path = Files.createTempFile("restocked", ".csv")
    Files.writeString(restockedPath, restockedCsv, UTF_8)

    println(readRecords(bookCodec, Files.readString(restockedPath, UTF_8)).map(_ == restocked))
  }
}

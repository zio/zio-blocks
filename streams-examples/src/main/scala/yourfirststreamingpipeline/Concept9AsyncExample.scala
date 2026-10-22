package yourfirststreamingpipeline

import zio.blocks.streams._
import zio.blocks.chunk.Chunk
import java.io.BufferedReader
import java.nio.file.{Files, Path, StandardOpenOption}
import scala.jdk.CollectionConverters._
import zio.blocks.async._

/**
 * Your First Streaming Pipeline: Turn a Sales Log into a Report — Concept 9:
 * Run the Same Pipeline Asynchronously
 *
 * Runs the pipeline with runAsync and waits for the result with block.
 *
 * Run with: cd streams-examples && sbt "runMain
 * yourfirststreamingpipeline.Concept9AsyncExample"
 */
object Concept9AsyncExample {
  final case class Sale(title: String, quantity: Int)

  def toSale(line: String): Sale = {
    val fields = line.split(",")
    Sale(fields(1), fields(2).toInt)
  }

  final case class MalformedLine(line: String)

  def parse(line: String): Stream[MalformedLine, Sale] =
    line.split(",") match {
      case Array(_, title, quantity) if quantity.toIntOption.isDefined =>
        Stream.succeed(Sale(title, quantity.toInt))
      case _ =>
        Stream.fail(MalformedLine(line))
    }

  def main(args: Array[String]): Unit = {
    val salesLog: Path = Files.createTempFile("sales", ".csv")

    Files.writeString(
      salesLog,
      """timestamp,title,quantity
        |2026-10-19T10:15,Zionomicon,2
        |2026-10-19T10:42,Programming in Scala,1
        |2026-10-19T11:05,Functional Programming in Scala,3
        |2026-10-19T12:10,Zionomicon,1
        |""".stripMargin
    )

    val salesLines: Stream[Nothing, String] =
      Stream.fromAcquireRelease(
        acquire = {
          println("Opened the sales log")
          Files.newBufferedReader(salesLog)
        },
        release = (reader: BufferedReader) => {
          reader.close()
          println("Closed the sales log")
        }
      )(reader => Stream.fromIterator(reader.lines().iterator().asScala))

    val header = "timestamp,title,quantity"

    val saleLines: Stream[Nothing, String] = salesLines.filter(line => line != header)

    val sales: Stream[Nothing, Sale] = saleLines.map(toSale)

    val copiesPerTitle: Sink[Nothing, Chunk[Sale], Map[String, Int]] =
      Sink.foldLeft(Map.empty[String, Int]) { (totals: Map[String, Int], batch: Chunk[Sale]) =>
        batch.foldLeft(totals) { (acc, sale) =>
          acc.updated(sale.title, acc.getOrElse(sale.title, 0) + sale.quantity)
        }
      }

    Files.writeString(salesLog, "2026-10-19T12:31,Zionom", StandardOpenOption.APPEND)

    val parsedSales: Stream[MalformedLine, Sale] = saleLines.flatMap(parse)

    val cleanSales: Stream[Nothing, Sale] =
      parsedSales.catchAll { bad =>
        println(s"Skipping malformed line: ${bad.line}")
        Stream.empty
      }

    val report: Async[Either[Nothing, Map[String, Int]]] = cleanSales.grouped(2).runAsync(copiesPerTitle)

    println("Report described; nothing has run yet")
    println(report.block)
  }
}

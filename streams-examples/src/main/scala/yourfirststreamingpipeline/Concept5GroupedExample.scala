package yourfirststreamingpipeline

import zio.blocks.streams._
import java.io.BufferedReader
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

/**
 * Your First Streaming Pipeline: Turn a Sales Log into a Report — Concept 5:
 * Batch Sales with grouped
 *
 * Batches the sales two at a time with grouped.
 *
 * Run with: cd streams-examples && sbt "runMain
 * yourfirststreamingpipeline.Concept5GroupedExample"
 */
object Concept5GroupedExample {
  final case class Sale(title: String, quantity: Int)

  def toSale(line: String): Sale = {
    val fields = line.split(",")
    Sale(fields(1), fields(2).toInt)
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

    sales.grouped(2).runForeach(batch => println(batch))
  }
}

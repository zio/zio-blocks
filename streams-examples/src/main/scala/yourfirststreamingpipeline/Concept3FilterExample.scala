package yourfirststreamingpipeline

import zio.blocks.streams._
import java.io.BufferedReader
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

/**
 * Your First Streaming Pipeline: Turn a Sales Log into a Report — Concept 3:
 * Keep Only the Sales with filter
 *
 * Drops the header line with filter.
 *
 * Run with: cd streams-examples && sbt "runMain
 * yourfirststreamingpipeline.Concept3FilterExample"
 */
object Concept3FilterExample {
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

    saleLines.runForeach(line => println(line))
  }
}

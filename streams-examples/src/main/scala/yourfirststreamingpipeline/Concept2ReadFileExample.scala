package yourfirststreamingpipeline

import zio.blocks.streams._
import java.io.BufferedReader
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

/**
 * Your First Streaming Pipeline: Turn a Sales Log into a Report — Concept 2:
 * Read the Sales Log from a File
 *
 * Reads the sales log with Stream.fromAcquireRelease and shows that the file is
 * closed after a full run and after an early stop with take.
 *
 * Run with: cd streams-examples && sbt "runMain
 * yourfirststreamingpipeline.Concept2ReadFileExample"
 */
object Concept2ReadFileExample {
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

    salesLines.runForeach(line => println(line))
    println("--- take(2) ---")
    salesLines.take(2).runForeach(line => println(line))
  }
}

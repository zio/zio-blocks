//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using dep dev.zio::zio-blocks-streams:0.0.56
//> using file Model.scala

package readandwritejsonlines

import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters._

import zio.blocks.schema._
import zio.blocks.streams._

/**
 * How to Read and Write JSON Lines (NDJSON) with Streams — Decode Each Line
 *
 * Numbers the lines of the sample sensor log, decodes each one into a
 * SensorReading, and prints the decoded value or the bad line with its reason.
 *
 * Run with: cd zio-blocks-examples && sbt "jsonLines/runMain
 * readandwritejsonlines.Step2DecodeLinesExample"
 */
object Step2DecodeLinesExample {

  def readLines(path: Path): Stream[Nothing, String] =
    Stream.fromAcquireRelease(Files.newBufferedReader(path))(reader =>
      Stream.fromIterator(reader.lines().iterator().asScala)
    )

  def main(args: Array[String]): Unit = {
    val dir  = Files.createTempDirectory("json-lines")
    val file = Sample.writeTo(dir)

    val numbered: Stream[Nothing, (Long, String)] =
      readLines(file).mapAccum(1L)((n, text) => (n + 1, (n, text)))

    val decoded: Stream[Nothing, Either[BadLine, SensorReading]] =
      numbered.map { case (n, text) =>
        text.fromJson[SensorReading].left.map(error => BadLine(n, text, error.getMessage))
      }

    decoded.runForeach(result => println(result))

    Files.delete(file)
    Files.delete(dir)
  }
}

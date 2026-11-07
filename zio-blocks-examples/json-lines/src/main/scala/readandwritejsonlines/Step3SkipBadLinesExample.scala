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
 * How to Read and Write JSON Lines (NDJSON) with Streams — Skip and Count Bad
 * Lines
 *
 * Reads the sample sensor log, logs each bad line as it goes by, keeps the good
 * readings and counts the skipped lines in one pass.
 *
 * Run with: cd zio-blocks-examples && sbt "jsonLines/runMain
 * readandwritejsonlines.Step3SkipBadLinesExample"
 */
object Step3SkipBadLinesExample {

  final case class Import(readings: Vector[SensorReading], skipped: Int)

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

    val result: Either[Nothing, Import] = decoded.run(
      Sink.foldLeft(Import(Vector.empty, 0)) { (acc, next) =>
        next match {
          case Right(reading) => acc.copy(readings = acc.readings :+ reading)
          case Left(bad)      =>
            println(s"skipped line ${bad.line}: ${bad.reason}")
            acc.copy(skipped = acc.skipped + 1)
        }
      }
    )

    result.foreach { imported =>
      println(s"kept ${imported.readings.size} readings, skipped ${imported.skipped} lines")
      imported.readings.foreach(println)
    }

    Files.delete(file)
    Files.delete(dir)
  }
}

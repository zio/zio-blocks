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
 * How to Read and Write JSON Lines (NDJSON) with Streams — Fail at the First
 * Bad Line
 *
 * Reads the sample sensor log and stops at the first line that does not decode,
 * returning it as a typed BadLine with its line number.
 *
 * Run with: cd zio-blocks-examples && sbt "jsonLines/runMain
 * readandwritejsonlines.Step4FailWithLineNumberExample"
 */
object Step4FailWithLineNumberExample {

  def readLines(path: Path): Stream[Nothing, String] =
    Stream.fromAcquireRelease(Files.newBufferedReader(path))(reader =>
      Stream.fromIterator(reader.lines().iterator().asScala)
    )

  def main(args: Array[String]): Unit = {
    val dir  = Files.createTempDirectory("json-lines")
    val file = Sample.writeTo(dir)

    val numbered: Stream[Nothing, (Long, String)] =
      readLines(file).mapAccum(1L)((n, text) => (n + 1, (n, text)))

    val strict: Stream[BadLine, SensorReading] =
      numbered.flatMap { case (n, text) =>
        text.fromJson[SensorReading] match {
          case Right(reading) => Stream.succeed(reading)
          case Left(error)    => Stream.fail(BadLine(n, text, error.getMessage))
        }
      }

    println(strict.runCollect)

    Files.delete(file)
    Files.delete(dir)
  }
}

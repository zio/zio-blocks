//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using dep dev.zio::zio-blocks-streams:0.0.56
//> using file Model.scala

package readandwritejsonlines

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters._

import zio.blocks.schema._
import zio.blocks.streams._

/**
 * How to Read and Write JSON Lines (NDJSON) with Streams — Complete Example
 *
 * Cleans a sensor log in one pass: reads the lines, decodes each one, logs and
 * counts the bad lines, writes the good readings to a new JSON Lines file, and
 * reads that file back to check it.
 *
 * Run with: cd zio-blocks-examples && sbt "jsonLines/runMain
 * readandwritejsonlines.CompleteExample"
 */
object CompleteExample {

  final case class Import(readings: Vector[SensorReading], skipped: Int)

  def readLines(path: Path): Stream[Nothing, String] =
    Stream.fromAcquireRelease(Files.newBufferedReader(path))(reader =>
      Stream.fromIterator(reader.lines().iterator().asScala)
    )

  def jsonLine(reading: SensorReading): Array[Byte] =
    (reading.toJsonString + "\n").getBytes(UTF_8)

  def writeJsonLines(path: Path, records: Seq[SensorReading]): Either[Nothing, Unit] = {
    val out = Files.newOutputStream(path)
    try
      Stream
        .fromIterable(records)
        .flatMap(reading => Stream.fromIterable(jsonLine(reading).toIndexedSeq))
        .run(Sink.fromOutputStream(out))
    finally out.close()
  }

  def main(args: Array[String]): Unit = {
    val dir    = Files.createTempDirectory("json-lines")
    val input  = Sample.writeTo(dir)
    val output = dir.resolve("clean.jsonl")

    val numbered: Stream[Nothing, (Long, String)] =
      readLines(input).mapAccum(1L)((n, text) => (n + 1, (n, text)))

    val decoded: Stream[Nothing, Either[BadLine, SensorReading]] =
      numbered.map { case (n, text) =>
        text.fromJson[SensorReading].left.map(error => BadLine(n, text, error.getMessage))
      }

    val imported: Either[Nothing, Import] = decoded.run(
      Sink.foldLeft(Import(Vector.empty, 0)) { (acc, next) =>
        next match {
          case Right(reading) => acc.copy(readings = acc.readings :+ reading)
          case Left(bad)      =>
            println(s"skipped line ${bad.line}: ${bad.reason}")
            acc.copy(skipped = acc.skipped + 1)
        }
      }
    )

    imported.foreach { result =>
      writeJsonLines(output, result.readings)
      println(s"wrote ${result.readings.size} readings, skipped ${result.skipped} lines")
    }

    println("clean.jsonl:")
    print(Files.readString(output))

    Files.delete(input)
    Files.delete(output)
    Files.delete(dir)
  }
}

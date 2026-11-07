//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using dep dev.zio::zio-blocks-streams:0.0.56
//> using file Model.scala

package readandwritejsonlines

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import zio.blocks.schema._
import zio.blocks.streams._

/**
 * How to Read and Write JSON Lines (NDJSON) with Streams — Write One Record Per
 * Line
 *
 * Encodes three sensor readings, appends a newline after each one, writes the
 * bytes to a new file and prints the file's content.
 *
 * Run with: cd zio-blocks-examples && sbt "jsonLines/runMain
 * readandwritejsonlines.Step5WriteJsonLinesExample"
 */
object Step5WriteJsonLinesExample {

  val readings: Seq[SensorReading] = Seq(
    SensorReading("boiler-7", 21.5, 1L),
    SensorReading("attic-2", 14.25, 2L),
    SensorReading("cellar-1", 9.0, 3L)
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
    val dir = Files.createTempDirectory("json-lines")
    val out = dir.resolve("readings.jsonl")

    writeJsonLines(out, readings)

    print(Files.readString(out))

    Files.delete(out)
    Files.delete(dir)
  }
}

package gettingstarted

import java.io.BufferedReader
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.scope._

/**
 * Getting Started — Your First Five Minutes
 *
 * Reads temperature readings from a JSON Lines file, decodes them with Schema,
 * summarizes them in a Chunk, and prints the summary as JSON. Scope opens the
 * file and closes it as soon as the lines are read.
 *
 * Run with: sbt "gettingStarted/runMain gettingstarted.FirstFiveMinutes"
 */
final case class Reading(sensor: String, celsius: Double)

object Reading {
  implicit val schema: Schema[Reading] = Schema.derived
}

final case class Summary(readings: Int, averageCelsius: Double, hottestSensor: String)

object Summary {
  implicit val schema: Schema[Summary] = Schema.derived
}

object FirstFiveMinutes {

  def readLines(path: Path): List[String] =
    Scope.global.scoped { scope =>
      import scope._
      val reader: $[BufferedReader] = allocate(
        Resource.acquireRelease {
          println("Opened the readings file")
          Files.newBufferedReader(path)
        } { reader =>
          reader.close()
          println("Closed the readings file")
        }
      )
      $(reader)(_.lines().iterator().asScala.toList)
    }

  def summarize(readings: Chunk[Reading]): Summary =
    Summary(
      readings = readings.size,
      averageCelsius = readings.map(_.celsius).sum / readings.size,
      hottestSensor = readings.maxBy(_.celsius).sensor
    )

  def main(args: Array[String]): Unit = {
    val path = Files.createTempFile("readings", ".jsonl")
    Files.writeString(
      path,
      """{"sensor":"kitchen","celsius":21.5}
        |{"sensor":"garage","celsius":12.0}
        |{"sensor":"attic","celsius":"hot"}
        |{"sensor":"office","celsius":23.5}
        |""".stripMargin
    )

    val lines            = readLines(path)
    val (errors, parsed) = lines.map(_.fromJson[Reading]).partitionMap(identity)

    errors.foreach(error => println(s"Skipped a line: ${error.getMessage}"))

    val readings = Chunk.fromIterable(parsed)

    println(summarize(readings).toJsonString)

    Files.delete(path)
  }
}

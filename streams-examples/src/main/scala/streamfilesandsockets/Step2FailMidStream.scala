package streamfilesandsockets

import zio.blocks.streams._

import java.nio.file.Path

import SampleFiles._

/**
 * Fail Mid-Stream: a thrown exception is a defect, a typed `BadRow` is a `Left`;
 * the file is closed both times.
 *
 * Run with: sbt "runMain streamfilesandsockets.Step2FailMidStream"
 */
object Step2FailMidStream {
  def parseOrThrow(row: String): Stock = {
    val fields = row.split(",")
    Stock(fields(0), fields(1).toInt)
  }

  def main(args: Array[String]): Unit = {
    val damaged: Path = writeDamaged(newShop())

    try println(inventoryRows(damaged).map(parseOrThrow).runCollect)
    catch { case e: NumberFormatException => println(s"thrown: $e") }

    println(inventoryRows(damaged).flatMap(parse).runCollect)
  }
}

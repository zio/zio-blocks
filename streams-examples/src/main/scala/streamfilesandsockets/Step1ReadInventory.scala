package streamfilesandsockets

import java.nio.file.Path

import SampleFiles._

/**
 * How to Stream Files and Sockets Without Leaking Resources
 *
 * Read a File and Stop Early: `Stream.fromAcquireRelease` closes the file after
 * the last row and after an early `take`.
 *
 * Run with: sbt "runMain streamfilesandsockets.Step1ReadInventory"
 */
object Step1ReadInventory {
  def main(args: Array[String]): Unit = {
    val inventory: Path = writeInventory(newShop())

    println(inventoryRows(inventory).runForeach(row => println(row)))

    println(inventoryRows(inventory).take(2).runForeach(row => println(row)))
  }
}

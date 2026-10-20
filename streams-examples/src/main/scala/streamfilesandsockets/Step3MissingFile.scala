package streamfilesandsockets

import zio.blocks.streams._

import java.io.BufferedReader
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

import SampleFiles._

/**
 * Report a Missing File: a defect thrown by `acquire`, then a typed error with
 * `Stream.attempt`.
 *
 * Run with: sbt "runMain streamfilesandsockets.Step3MissingFile"
 */
object Step3MissingFile {
  def inventoryRowsOrError(path: Path): Stream[Throwable, String] =
    Stream.attempt(Files.newBufferedReader(path)).flatMap { reader =>
      Stream.fromAcquireRelease(reader)(r => Stream.fromIterator(r.lines().iterator().asScala))
    }

  def main(args: Array[String]): Unit = {
    val shop: Path      = newShop()
    val inventory: Path = writeInventory(shop)

    val missing: Path = shop.resolve("inventory-2025.csv")

    try println(inventoryRows(missing).runCollect)
    catch { case e: java.nio.file.NoSuchFileException => println(s"thrown: $e") }

    println(inventoryRowsOrError(missing).runCollect)
    println(inventoryRowsOrError(inventory).take(1).runCollect)
  }
}

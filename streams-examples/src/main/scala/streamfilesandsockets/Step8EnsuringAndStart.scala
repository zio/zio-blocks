package streamfilesandsockets

import zio.blocks.scope.Scope
import zio.blocks.streams._

import java.nio.file.{Files, Path}

import SampleFiles._

/**
 * How to Stream Files and Sockets Without Leaking Resources
 *
 * Cleanup That Is Not a Resource, and Pulling Elements by Hand: `Stream#ensuring`
 * deletes a staging file, `Stream#start` reads two rows inside a `Scope`.
 *
 * Run with: sbt "runMain streamfilesandsockets.Step8EnsuringAndStart"
 */
object Step8EnsuringAndStart {
  def main(args: Array[String]): Unit = {
    val shop: Path      = newShop()
    val inventory: Path = writeInventory(shop)

    val staging: Path = shop.resolve("inventory-staging.csv")
    Files.copy(inventory, staging)

    val fromStaging: Stream[Nothing, String] =
      inventoryRows(staging).ensuring {
        Files.deleteIfExists(staging)
        println("deleted inventory-staging.csv")
      }

    println(fromStaging.take(1).runCollect)
    println(Files.exists(staging))

    val firstTwo: String =
      Scope.global.scoped { implicit scope =>
        import scope._
        val reader = inventoryRows(inventory).start
        $(reader)(r => r.readN[String](2).mkString(" | "))
      }

    println(firstTwo)
  }
}

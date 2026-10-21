package streamfilesandsockets

import zio.blocks.scope.Resource
import zio.blocks.streams._

import java.io.{BufferedReader, BufferedWriter}
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

import SampleFiles._

/**
 * How to Stream Files and Sockets Without Leaking Resources
 *
 * Compose Scope Resources: `Stream.fromResource` with one resource, then with a
 * reader and a writer that close in reverse order.
 *
 * Run with: sbt "runMain streamfilesandsockets.Step4ComposeResources"
 */
object Step4ComposeResources {
  def openReader(path: Path): Resource[BufferedReader] =
    Resource.acquireRelease {
      println(s"opened ${path.getFileName}")
      Files.newBufferedReader(path)
    } { reader =>
      reader.close()
      println(s"closed ${path.getFileName}")
    }

  def openWriter(path: Path): Resource[BufferedWriter] =
    Resource.acquireRelease {
      println(s"opened ${path.getFileName}")
      Files.newBufferedWriter(path)
    } { writer =>
      writer.close()
      println(s"closed ${path.getFileName}")
    }

  def main(args: Array[String]): Unit = {
    val shop: Path      = newShop()
    val inventory: Path = writeInventory(shop)

    println(
      Stream
        .fromResource(openReader(inventory))(reader => Stream.fromIterator(reader.lines().iterator().asScala))
        .take(1)
        .runCollect
    )

    val restock: Path = shop.resolve("restock.csv")

    val inventoryAndRestock: Resource[(BufferedReader, BufferedWriter)] =
      for {
        reader <- openReader(inventory)
        writer <- openWriter(restock)
      } yield (reader, writer)

    println(
      Stream
        .fromResource(inventoryAndRestock) { case (reader, writer) =>
          Stream
            .fromIterator(reader.lines().iterator().asScala)
            .filter(row => row.split(",")(1).toInt < 5)
            .tapEach { row =>
              writer.write(row)
              writer.newLine()
            }
        }
        .runDrain
    )

    print(Files.readString(restock))
  }
}

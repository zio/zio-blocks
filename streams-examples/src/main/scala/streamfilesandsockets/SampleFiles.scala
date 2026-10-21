package streamfilesandsockets

import zio.blocks.chunk.Chunk
import zio.blocks.streams._

import java.io.BufferedReader
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

final case class Stock(title: String, copies: Int)

final case class BadRow(row: String)

/**
 * Shared sample data and helpers for the "How to Stream Files and Sockets
 * Without Leaking Resources" guide.
 */
object SampleFiles {

  /** Creates a temporary directory for one example run. */
  def newShop(): Path = Files.createTempDirectory("bookshop")

  /** Writes the clean inventory export into `shop` and returns its path. */
  def writeInventory(shop: Path): Path = {
    val inventory: Path = shop.resolve("inventory.csv")
    Files.writeString(
      inventory,
      """Zionomicon,12
        |Programming in Scala,7
        |Functional Programming in Scala,3
        |Scala for the Impatient,0
        |Hands-on Scala Programming,4
        |""".stripMargin
    )
    inventory
  }

  /** Writes the damaged inventory export into `shop` and returns its path. */
  def writeDamaged(shop: Path): Path = {
    val damaged: Path = shop.resolve("inventory-damaged.csv")
    Files.writeString(
      damaged,
      """Zionomicon,12
        |Programming in Scala,seven
        |Functional Programming in Scala,3
        |""".stripMargin
    )
    damaged
  }

  def inventoryRows(path: Path): Stream[Nothing, String] =
    Stream.fromAcquireRelease(
      acquire = {
        println(s"opened ${path.getFileName}")
        Files.newBufferedReader(path)
      },
      release = (reader: BufferedReader) => {
        reader.close()
        println(s"closed ${path.getFileName}")
      }
    )(reader => Stream.fromIterator(reader.lines().iterator().asScala))

  def parse(row: String): Stream[BadRow, Stock] = {
    val fields = row.split(",")
    fields(1).toIntOption match {
      case Some(copies) => Stream.succeed(Stock(fields(0), copies))
      case None         => Stream.fail(BadRow(row))
    }
  }

  def text(bytes: Chunk[Byte]): String = new String(bytes.toArray, UTF_8)
}

package streamfilesandsockets

import zio.blocks.streams._

import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption.{CREATE, TRUNCATE_EXISTING, WRITE}
import java.nio.file.{Files, Path}
import scala.util.Using

import SampleFiles._

/**
 * Copy a File Through a Sink: `NioSinks.fromChannel` writes the bytes and
 * `Using.resource` closes the backup file.
 *
 * Run with: sbt "runMain streamfilesandsockets.Step6CopyFile"
 */
object Step6CopyFile {
  def main(args: Array[String]): Unit = {
    val shop: Path      = newShop()
    val inventory: Path = writeInventory(shop)

    val backup: Path = shop.resolve("inventory-backup.csv")

    val copied: Either[java.io.IOException, Unit] =
      Using.resource(FileChannel.open(backup, CREATE, TRUNCATE_EXISTING, WRITE)) { out =>
        NioStreams.fromChannel(FileChannel.open(inventory)).run(NioSinks.fromChannel(out))
      }

    println(copied)
    println(Files.readString(backup) == Files.readString(inventory))
  }
}

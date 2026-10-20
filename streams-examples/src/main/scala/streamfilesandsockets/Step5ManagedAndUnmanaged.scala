package streamfilesandsockets

import zio.blocks.streams._

import java.nio.channels.FileChannel
import java.nio.file.{Files, Path}

import SampleFiles._

/**
 * Managed and Unmanaged Handles: which constructor closes the handle, and what
 * that means for running a stream twice.
 *
 * Run with: sbt "runMain streamfilesandsockets.Step5ManagedAndUnmanaged"
 */
object Step5ManagedAndUnmanaged {
  def main(args: Array[String]): Unit = {
    val inventory: Path = writeInventory(newShop())

    val managed: Stream[java.io.IOException, Byte] =
      Stream.fromInputStream(Files.newInputStream(inventory))

    println(managed.take(10).runCollect.map(text))
    println(managed.take(10).runCollect.map(text))

    val rerunnable: Stream[java.io.IOException, Byte] =
      Stream.fromAcquireRelease(Files.newInputStream(inventory))(in => Stream.fromInputStreamUnmanaged(in))

    println(rerunnable.take(10).runCollect.map(text))
    println(rerunnable.take(10).runCollect.map(text))

    val channel: FileChannel = FileChannel.open(inventory)

    println(NioStreams.fromChannelUnmanaged(channel).take(10).runCollect.map(text))
    println(channel.isOpen)

    channel.close()

    val managedChannel: FileChannel = FileChannel.open(inventory)

    println(NioStreams.fromChannel(managedChannel).take(10).runCollect.map(text))
    println(managedChannel.isOpen)
  }
}

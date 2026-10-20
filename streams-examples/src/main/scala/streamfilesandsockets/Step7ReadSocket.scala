package streamfilesandsockets

import zio.blocks.streams._

import java.net.{InetAddress, InetSocketAddress, ServerSocket}
import java.nio.channels.SocketChannel
import java.nio.file.{Files, Path}

import SampleFiles._

/**
 * Read from a Socket: connect in `acquire`, disconnect in `release`, on every
 * run.
 *
 * Run with: sbt "runMain streamfilesandsockets.Step7ReadSocket"
 */
object Step7ReadSocket {
  def main(args: Array[String]): Unit = {
    val inventory: Path = writeInventory(newShop())

    val server       = new ServerSocket(0, 50, InetAddress.getLoopbackAddress)
    val serverThread = new Thread(() =>
      for (_ <- 1 to 2) {
        val client = server.accept()
        try client.getOutputStream.write(Files.readAllBytes(inventory))
        catch { case _: java.io.IOException => () }
        finally client.close()
      }
    )
    serverThread.start()

    val address = new InetSocketAddress(InetAddress.getLoopbackAddress, server.getLocalPort)

    val inventoryFeed: Stream[java.io.IOException, Byte] =
      Stream.fromAcquireRelease(
        acquire = {
          val socket = SocketChannel.open(address)
          println("connected")
          socket
        },
        release = (socket: SocketChannel) => {
          socket.close()
          println("disconnected")
        }
      )(socket => NioStreams.fromChannelUnmanaged(socket))

    print(inventoryFeed.runCollect.map(text).getOrElse(""))
    println(inventoryFeed.take(10).runCollect.map(text))

    serverThread.join()
    server.close()
  }
}

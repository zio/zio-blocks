package streamfilesandsockets

import zio.blocks.scope.Resource
import zio.blocks.streams._

import java.io.{BufferedReader, BufferedWriter, IOException}
import java.net.{InetAddress, InetSocketAddress, ServerSocket}
import java.nio.channels.{FileChannel, SocketChannel}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.StandardOpenOption.{CREATE, TRUNCATE_EXISTING, WRITE}
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._
import scala.util.Using

/**
 * Nightly restock job: downloads the inventory export from a loopback server
 * into a staging file, reads it and writes a restock list through two composed
 * `Resource` values, fails with a typed error on a damaged row, and deletes the
 * staging file with `Stream#ensuring`.
 *
 * Run with: sbt "runMain streamfilesandsockets.CompleteExample"
 */
object CompleteExample {

  final case class Stock(title: String, copies: Int)

  final case class BadRow(row: String)

  sealed trait RestockError
  final case class DownloadFailed(cause: IOException) extends RestockError
  final case class DamagedExport(row: BadRow)         extends RestockError

  val cleanExport: String =
    """Zionomicon,12
      |Programming in Scala,7
      |Functional Programming in Scala,3
      |Scala for the Impatient,0
      |Hands-on Scala Programming,4
      |""".stripMargin

  val damagedExport: String =
    """Zionomicon,12
      |Programming in Scala,seven
      |Functional Programming in Scala,3
      |""".stripMargin

  def parse(row: String): Stream[BadRow, Stock] = {
    val fields = row.split(",")
    fields(1).toIntOption match {
      case Some(copies) => Stream.succeed(Stock(fields(0), copies))
      case None         => Stream.fail(BadRow(row))
    }
  }

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

  def connect(address: InetSocketAddress): Stream[IOException, Byte] =
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

  def download(address: InetSocketAddress, staging: Path): Either[RestockError, Unit] =
    Using
      .resource(FileChannel.open(staging, CREATE, TRUNCATE_EXISTING, WRITE)) { out =>
        connect(address).run(NioSinks.fromChannel(out))
      }
      .left
      .map(DownloadFailed(_))

  def restock(staging: Path, restockList: Path): Either[RestockError, Unit] = {
    val staged: Resource[(BufferedReader, BufferedWriter)] =
      for {
        reader <- openReader(staging)
        writer <- openWriter(restockList)
      } yield (reader, writer)

    Stream
      .fromResource(staged) { case (reader, writer) =>
        Stream
          .fromIterator(reader.lines().iterator().asScala)
          .flatMap(parse)
          .filter(_.copies < 5)
          .tapEach { stock =>
            writer.write(stock.title)
            writer.newLine()
          }
      }
      .ensuring {
        Files.deleteIfExists(staging)
        println(s"deleted ${staging.getFileName}")
      }
      .mapError(DamagedExport(_))
      .runDrain
  }

  def restockJob(address: InetSocketAddress, dir: Path): Either[RestockError, Unit] = {
    val staging     = dir.resolve("inventory-staging.csv")
    val restockList = dir.resolve("restock.csv")

    val result = download(address, staging).flatMap(_ => restock(staging, restockList))

    println(result)
    print(Files.readString(restockList))
    println(s"staging exists: ${Files.exists(staging)}")
    result
  }

  def main(args: Array[String]): Unit = {
    val dir = Files.createTempDirectory("restock")

    val server       = new ServerSocket(0, 50, InetAddress.getLoopbackAddress)
    val serverThread = new Thread(() =>
      for (payload <- List(cleanExport, damagedExport)) {
        val client = server.accept()
        try client.getOutputStream.write(payload.getBytes(UTF_8))
        catch { case _: IOException => () }
        finally client.close()
      }
    )
    serverThread.start()

    val address = new InetSocketAddress(InetAddress.getLoopbackAddress, server.getLocalPort)

    println("--- clean export ---")
    restockJob(address, dir)

    println("--- damaged export ---")
    restockJob(address, dir)

    serverThread.join()
    server.close()
  }
}

package effectintegration

import java.io.BufferedWriter
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import zio.blocks.schema._
import zio.blocks.scope.Resource

// An append-only JSON-lines file of placed orders: a resource that must be closed exactly once.
final class OrderArchive(path: Path) extends AutoCloseable {
  private val writer: BufferedWriter = Files.newBufferedWriter(path, UTF_8)
  println("archive opened")

  def append(order: Order): Unit = {
    writer.write(order.toJsonString)
    writer.newLine()
  }

  def close(): Unit = {
    writer.close()
    println("archive closed")
  }
}

object OrderArchive {
  def resource(path: Path): Resource[OrderArchive] = Resource.fromAutoCloseable(new OrderArchive(path))
}

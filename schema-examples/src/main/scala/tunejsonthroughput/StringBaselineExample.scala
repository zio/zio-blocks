package tunejsonthroughput

import zio.blocks.schema._
import java.nio.charset.StandardCharsets.UTF_8

/**
 * Tune JSON Throughput: The String Baseline
 *
 * Runs the String-based handler and the byte-based handler that replaces it,
 * and checks that both return the same reply.
 *
 * Run with: cd schema-examples && sbt "runMain
 * tunejsonthroughput.StringBaselineExample"
 */
object StringBaselineExample extends App {
  import BookCatalog._

  // Called once per request with the raw request body
  def recentBooks(requestBody: Array[Byte]): Array[Byte] = {
    val text  = new String(requestBody, UTF_8)
    val books = text.fromJson[List[Book]]
    books match {
      case Right(all)  => all.filter(_.year >= 2020).toJsonString.getBytes(UTF_8)
      case Left(error) => error.getMessage.getBytes(UTF_8)
    }
  }

  // Bytes in, bytes out, one codec for the whole catalog
  def recentBooksFromBytes(requestBody: Array[Byte]): Array[Byte] =
    Book.catalogCodec.decode(requestBody) match {
      case Right(all)  => Book.catalogCodec.encode(all.filter(_.year >= 2020))
      case Left(error) => error.getMessage.getBytes(UTF_8)
    }

  val stringReply = recentBooks(catalogBytes)
  val bytesReply  = recentBooksFromBytes(catalogBytes)

  println(s"Request body: ${catalogBytes.length} bytes")
  println(s"String-based reply: ${stringReply.length} bytes")
  println(s"Byte-based reply: ${bytesReply.length} bytes")
  println(s"Same reply: ${stringReply.sameElements(bytesReply)}")
}

package tunejsonthroughput

import java.io.{BufferedOutputStream, ByteArrayOutputStream}
import java.nio.{BufferOverflowException, ByteBuffer}

/**
 * Tune JSON Throughput: Encode to Bytes, a ByteBuffer or an OutputStream
 *
 * Encodes to an array, into a ByteBuffer (including recovering from an
 * overflow), and to a buffered OutputStream that it flushes.
 *
 * Run with: cd schema-examples && sbt "runMain
 * tunejsonthroughput.EncodeToBytesExample"
 */
object EncodeToBytesExample extends App {
  import BookCatalog._

  // To an array of bytes
  println(Book.catalogCodec.encode(catalog).length)

  // Into a ByteBuffer: reading before flip() starts after the JSON, reading after it returns the book
  val out = ByteBuffer.allocate(1024)
  Book.codec.encode(classics.head, out)

  println(out.position())
  println(Book.codec.decode(out.duplicate()))

  out.flip()
  println(Book.codec.decode(out).map(_.title))

  // A buffer that is too small throws and leaves a truncated value behind
  val tooSmall = ByteBuffer.allocate(16)

  try Book.codec.encode(classics.head, tooSmall)
  catch { case _: BufferOverflowException => println(s"overflow at position ${tooSmall.position()}") }

  // Save the position and restore it on overflow
  def encodeOrRewind(book: Book, buffer: ByteBuffer): Boolean = {
    val start = buffer.position()
    try {
      Book.codec.encode(book, buffer)
      true
    } catch {
      case _: BufferOverflowException =>
        buffer.position(start)
        false
    }
  }

  val small = ByteBuffer.allocate(16)

  println(encodeOrRewind(classics.head, small))
  println(small.position())

  // To an OutputStream: nothing reaches the sink until we flush
  val sink     = new ByteArrayOutputStream()
  val response = new BufferedOutputStream(sink, 1024 * 1024)

  Book.catalogCodec.encode(catalog, response)
  println(sink.size())

  response.flush()
  println(sink.size())
}

package tunejsonthroughput

import zio.blocks.schema.json._
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

/**
 * Tune JSON Throughput: Decode From Bytes, a ByteBuffer or an InputStream
 *
 * Decodes from an array, a slice of a framed array, heap and direct ByteBuffers
 * holding two books, and an InputStream.
 *
 * Run with: cd schema-examples && sbt "runMain
 * tunejsonthroughput.DecodeFromBytesExample"
 */
object DecodeFromBytesExample extends App {
  import BookCatalog._

  // From an array of bytes
  println(Book.catalogCodec.decode(catalogBytes).map(_.size))

  // From a slice of a length-prefixed frame
  val payload: Array[Byte] = Book.codec.encode(classics.head)
  val frame: Array[Byte]   = ByteBuffer.allocate(4 + payload.length).putInt(payload.length).put(payload).array()
  val length: Int          = ByteBuffer.wrap(frame).getInt(0)

  println(Book.codec.decode(frame, 4, 4 + length))

  // From a heap ByteBuffer holding two books back to back
  val oneValueAtATime: ReaderConfig = ReaderConfig.withCheckForEndOfInput(false)

  val twoBytes: Array[Byte] = Book.codec.encode(classics(0)) ++ Book.codec.encode(classics(1))
  val twoBooks: ByteBuffer  = ByteBuffer.wrap(twoBytes)

  println(Book.codec.decode(twoBooks, oneValueAtATime).map(_.title))
  println(Book.codec.decode(twoBooks, oneValueAtATime).map(_.title))
  println(twoBooks.remaining())

  // From a direct ByteBuffer holding the same two books
  val twoDirect: ByteBuffer = ByteBuffer.allocateDirect(twoBytes.length)
  twoDirect.put(twoBytes)
  twoDirect.flip()

  println(Book.codec.decode(twoDirect, oneValueAtATime).map(_.title))
  println(Book.codec.decode(twoDirect, oneValueAtATime).map(_.title))
  println(twoDirect.remaining())

  // From an InputStream
  println(Book.catalogCodec.decode(new ByteArrayInputStream(catalogBytes)).map(_.size))

  // One stream holds one value: the second read finds nothing left
  val booksStream = new ByteArrayInputStream(twoBytes)

  println(Book.codec.decode(booksStream, oneValueAtATime).map(_.title))
  println(Book.codec.decode(booksStream, oneValueAtATime).map(_.title))
}

package tunejsonthroughput

import zio.blocks.schema._
import zio.blocks.schema.json._
import java.io.{BufferedOutputStream, ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}
import java.nio.{BufferOverflowException, ByteBuffer}

/**
 * Tune JSON Throughput: Putting It Together
 *
 * A catalog endpoint that keeps its codecs and configs in vals, decodes the
 * request straight from an InputStream with tight limits, encodes the reply to
 * an OutputStream with a sized writer buffer, and reuses one heap ByteBuffer
 * for small messages.
 *
 * Run with: cd schema-examples && sbt "runMain
 * tunejsonthroughput.CompleteExample"
 */
object CompleteExample extends App {

  case class Book(id: Long, title: String, authors: List[String], year: Int, tags: List[String])
  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  case class ErrorReply(message: String)
  object ErrorReply {
    implicit val schema: Schema[ErrorReply] = Schema.derived
  }

  object CatalogEndpoint {
    val codec: JsonCodec[Book]              = Book.schema.jsonCodec
    val catalogCodec: JsonCodec[List[Book]] = Schema[List[Book]].jsonCodec
    val errorCodec: JsonCodec[ErrorReply]   = ErrorReply.schema.jsonCodec

    // Tight limits for untrusted request bodies
    val requestConfig: ReaderConfig = ReaderConfig.withPreferredCharBufSize(1024).withMaxCharBufSize(64 * 1024)

    // A writer buffer sized for a typical reply
    val replyConfig: WriterConfig = WriterConfig.withPreferredBufSize(1024 * 1024)

    def handle(request: InputStream, response: OutputStream): Unit = {
      catalogCodec.decode(request, requestConfig) match {
        case Right(books) => catalogCodec.encode(books.filter(_.year >= 2020), response, replyConfig)
        case Left(error)  => errorCodec.encode(ErrorReply(error.getMessage), response, replyConfig)
      }
      response.flush()
    }

    // Reuse one heap buffer for small messages, restoring its position on overflow
    def encodeOrRewind(book: Book, buffer: ByteBuffer): Boolean = {
      val start = buffer.position()
      try {
        codec.encode(book, buffer)
        true
      } catch {
        case _: BufferOverflowException =>
          buffer.position(start)
          false
      }
    }
  }

  val classics: List[Book] = List(
    Book(1L, "Zionomicon", List("John A. De Goes", "Adam Fraser", "Milad Khajavi"), 2024, List("zio", "effects")),
    Book(2L, "Programming in Scala", List("Martin Odersky", "Lex Spoon", "Bill Venners"), 2008, List("scala")),
    Book(3L, "Functional Programming in Scala", List("Paul Chiusano", "Rúnar Bjarnason"), 2014, List("fp")),
    Book(4L, "Scala for the Impatient", List("Cay S. Horstmann"), 2012, List("scala")),
    Book(5L, "Hands-on Scala Programming", List("Li Haoyi"), 2020, List("scala", "tooling")),
    Book(6L, "Essential Effects", List("Adam Rosien"), 2021, List("cats-effect", "effects")),
    Book(7L, "Practical FP in Scala", List("Gabriel Volpe"), 2020, List("fp", "cats-effect")),
    Book(8L, "Scala with Cats", List("Noel Welsh", "Dave Gurnell"), 2017, List("cats", "fp"))
  )

  val catalog: List[Book] = List.tabulate(4000)(i => classics(i % classics.size).copy(id = i + 1L))

  def serve(body: Array[Byte]): Array[Byte] = {
    val sink     = new ByteArrayOutputStream()
    val response = new BufferedOutputStream(sink, 1024 * 1024)
    CatalogEndpoint.handle(new ByteArrayInputStream(body), response)
    sink.toByteArray
  }

  // A valid catalog: keep the books published in 2020 or later
  val reply = serve(CatalogEndpoint.catalogCodec.encode(catalog))
  println(s"Reply: ${reply.length} bytes, ${CatalogEndpoint.catalogCodec.decode(reply).map(_.size)} books")

  // A malformed body gets a small JSON error
  println(new String(serve("""[{"id":1,"title":"Zionomicon"""".getBytes("UTF-8")), "UTF-8"))

  // A 100,000-character title exceeds the string limit
  val hugeTitle = CatalogEndpoint.catalogCodec.encode(List(classics.head.copy(title = "Z" * 100000)))
  println(new String(serve(hugeTitle), "UTF-8"))

  // One heap buffer for small messages
  val buffer = ByteBuffer.allocate(512)
  classics
    .take(3)
    .foreach(book => println(s"${book.title}: ${CatalogEndpoint.encodeOrRewind(book, buffer)} at ${buffer.position()}"))

  val bigBook = classics.head.copy(title = "Z" * 600)
  println(
    s"${bigBook.title.length}-char title: ${CatalogEndpoint.encodeOrRewind(bigBook, buffer)} at ${buffer.position()}"
  )
}

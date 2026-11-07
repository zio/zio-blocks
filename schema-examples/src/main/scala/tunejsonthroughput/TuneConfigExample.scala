package tunejsonthroughput

import zio.blocks.schema._
import zio.blocks.schema.json._
import java.io.ByteArrayInputStream

/**
 * Tune JSON Throughput: Tune ReaderConfig and WriterConfig
 *
 * Builds tuned reader and writer configs, shows the validation order for buffer
 * sizes, triggers both safety limits, and compares compact, indented and
 * escaped output.
 *
 * Run with: cd schema-examples && sbt "runMain
 * tunejsonthroughput.TuneConfigExample"
 */
object TuneConfigExample extends App {
  import BookCatalog._

  // A bigger writer buffer: same bytes
  val largeReplies: WriterConfig = WriterConfig.withPreferredBufSize(1024 * 1024)

  println(Book.catalogCodec.encode(catalog, largeReplies).sameElements(catalogBytes))

  // A bigger char buffer for long strings
  val longDescriptions: ReaderConfig = ReaderConfig.withPreferredCharBufSize(16 * 1024)

  println(Book.catalogCodec.decode(catalogBytes, longDescriptions).map(_.size))

  // Validation order: raise the maximum first
  scala.util.Try(ReaderConfig.withPreferredBufSize(64 * 1024 * 1024)).failed.foreach(e => println(e.getMessage))

  println(ReaderConfig.withMaxBufSize(64 * 1024 * 1024).withPreferredBufSize(64 * 1024 * 1024).preferredBufSize)

  // maxCharBufSize: a 5,000-character title under a 2,048-character limit
  val strict: ReaderConfig = ReaderConfig.withPreferredCharBufSize(1024).withMaxCharBufSize(2048)

  val longTitle: Array[Byte] = Book.codec.encode(classics.head.copy(title = "Z" * 5000))

  println(Book.codec.decode(longTitle, strict))

  // maxBufSize: a discriminator that comes last forces the reader to buffer the whole object
  sealed trait Edition
  object Edition {
    case class Print(isbn: String, pages: Int) extends Edition
    case class Ebook(url: String)              extends Edition

    implicit val schema: Schema[Edition] = Schema.derived
    val codec: JsonCodec[Edition]        =
      schema.derive(JsonCodecDeriver.withDiscriminatorKind(DiscriminatorKind.Field("type")))
  }

  // A 100 KB object whose discriminator comes last, under a 64 KiB limit. Keep maxBufSize at or above the
  // reader's initial 32 KiB buffer: in 0.0.56 a smaller limit is not applied reliably.
  val lastDiscriminator: Array[Byte] =
    s"""{"isbn":"${"9" * 100000}","pages":852,"type":"Print"}""".getBytes("UTF-8")
  val strictBuffer: ReaderConfig = ReaderConfig.withMaxBufSize(64 * 1024)

  println(Edition.codec.decode(new ByteArrayInputStream(lastDiscriminator), strictBuffer).left.map(_.getMessage))
  println(Edition.codec.decode(lastDiscriminator).isRight)

  // checkForEndOfInput rejects trailing garbage
  println(Book.codec.decode(Book.codec.encode(classics.head) ++ "garbage".getBytes("UTF-8")).left.map(_.getMessage))

  // Compact, indented and escaped output
  val fpInScala = classics(2)

  println(Book.codec.encode(fpInScala).length)
  println(Book.codec.encode(fpInScala, WriterConfig.withIndentionStep(2)).length)
  println(Book.codec.encode(fpInScala, WriterConfig.withEscapeUnicode(true)).length)
  println(new String(Book.codec.encode(fpInScala, WriterConfig.withEscapeUnicode(true)), "UTF-8"))
}

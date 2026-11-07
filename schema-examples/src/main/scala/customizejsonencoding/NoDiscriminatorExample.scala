package customizejsonencoding

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * Customize JSON: Encode Sealed Traits Without a Tag
 *
 * Writes each case as a plain object with DiscriminatorKind.None and decodes by
 * trying the cases in declaration order.
 *
 * Run with: cd schema-examples && sbt "runMain
 * customizejsonencoding.NoDiscriminatorExample"
 */
object NoDiscriminatorExample extends App {

  sealed trait Format
  object Format {
    case class Paperback(pages: Int)   extends Format
    case class Ebook(sizeMb: Double)   extends Format
    case class Audiobook(minutes: Int) extends Format

    implicit val schema: Schema[Format] = Schema.derived
  }

  val untaggedCodec: JsonCodec[Format] =
    Format.schema.derive(JsonCodecDeriver.withDiscriminatorKind(DiscriminatorKind.None))

  // Encoding drops the tag, and decoding picks the first case whose fields fit
  println(untaggedCodec.encodeToString(Format.Paperback(320)))
  println(untaggedCodec.decode("""{"minutes":600}"""))

  // Input that fits no case fails with one generic message
  untaggedCodec.decode("""{"chapters":12}""") match {
    case Left(error)   => println(error.message)
    case Right(format) => println(s"Decoded: $format")
  }
}

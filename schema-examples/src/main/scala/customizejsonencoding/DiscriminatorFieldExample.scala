package customizejsonencoding

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * Customize JSON: Tag Sealed Trait Cases With a Type Field
 *
 * Compares the default wrapper-key encoding with a "type" field, set through
 * JsonCodecDeriver and then through modifiers on the sealed trait.
 *
 * Run with: cd schema-examples && sbt "runMain
 * customizejsonencoding.DiscriminatorFieldExample"
 */
object DiscriminatorFieldExample extends App {

  sealed trait Format
  object Format {
    case class Paperback(pages: Int)   extends Format
    case class Ebook(sizeMb: Double)   extends Format
    case class Audiobook(minutes: Int) extends Format

    implicit val schema: Schema[Format] = Schema.derived
  }

  val paperback: Format = Format.Paperback(320)

  // By default, the case name is the key of a wrapper object
  println(paperback.toJsonString)

  // A deriver setting produces a flat object with a "type" field
  val typeFieldCodec: JsonCodec[Format] =
    Format.schema.derive(
      JsonCodecDeriver
        .withDiscriminatorKind(DiscriminatorKind.Field("type"))
        .withCaseNameMapper(NameMapper.SnakeCase)
    )

  println(typeFieldCodec.encodeToString(paperback))
  println(typeFieldCodec.decode("""{"type":"audiobook","minutes":600}"""))

  // The same choice can live on the sealed trait itself
  @Modifier.discriminator("type")
  @Modifier.caseNaming("snake_case")
  sealed trait AnnotatedFormat
  object AnnotatedFormat {
    case class Paperback(pages: Int) extends AnnotatedFormat
    case class Ebook(sizeMb: Double) extends AnnotatedFormat
    @Modifier.rename("audio")
    case class Audiobook(minutes: Int) extends AnnotatedFormat

    implicit val schema: Schema[AnnotatedFormat] = Schema.derived
  }

  val formats: List[AnnotatedFormat] =
    List(AnnotatedFormat.Paperback(320), AnnotatedFormat.Ebook(4.5), AnnotatedFormat.Audiobook(600))

  formats.foreach(format => println(format.toJsonString))
}

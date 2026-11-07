package customizejsonencoding

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * Customize JSON: Encode Case-Object Enums as Strings
 *
 * Writes a case-object enumeration as a string, with a case name mapper, and as
 * a wrapper object.
 *
 * Run with: cd schema-examples && sbt "runMain
 * customizejsonencoding.EnumStringsExample"
 */
object EnumStringsExample extends App {

  sealed trait Availability
  object Availability {
    case object InStock    extends Availability
    case object OutOfPrint extends Availability
    case object Preorder   extends Availability

    implicit val schema: Schema[Availability] = Schema.derived
  }

  val outOfPrint: Availability = Availability.OutOfPrint

  println(outOfPrint.toJsonString)
  println(
    Availability.schema.derive(JsonCodecDeriver.withCaseNameMapper(NameMapper.SnakeCase)).encodeToString(outOfPrint)
  )
  println(Availability.schema.derive(JsonCodecDeriver.withEnumValuesAsStrings(false)).encodeToString(outOfPrint))
}

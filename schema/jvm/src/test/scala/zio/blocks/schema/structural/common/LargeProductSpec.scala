package zio.blocks.schema.structural.common

import zio.blocks.schema._
import zio.test._

/**
 * Tests for large product types (20+ fields) to structural conversion.
 */
object LargeProductSpec extends ZIOSpecDefault {

  case class LargeRecord(
    f1: String, f2: Int, f3: Boolean, f4: Double, f5: Long,
    f6: String, f7: Int, f8: Boolean, f9: Double, f10: Long,
    f11: String, f12: Int, f13: Boolean, f14: Double, f15: Long,
    f16: String, f17: Int, f18: Boolean, f19: Double, f20: Long,
    f21: String
  )

  case class Record10(
    a: Int, b: Int, c: Int, d: Int, e: Int,
    f: Int, g: Int, h: Int, i: Int, j: Int
  )

  def spec = suite("LargeProductSpec")(
    test("large record (21 fields) converts to structural") {
      val schema = Schema.derived[LargeRecord]
      val structural = schema.structural

      val numFields = structural.reflect match {
        case record: Reflect.Record[_, _] => record.fields.size
        case _                            => -1
      }
      assertTrue(numFields == 21)
    } @@ TestAspect.ignore,
    test("10 field record converts correctly") {
      val schema = Schema.derived[Record10]
      val structural = schema.structural

      val fieldNames = structural.reflect match {
        case record: Reflect.Record[_, _] => record.fields.map(_.name).toSet
        case _                            => Set.empty[String]
      }
      assertTrue(
        fieldNames.size == 10,
        fieldNames.contains("a"),
        fieldNames.contains("j")
      )
    } @@ TestAspect.ignore,
    test("large record type name is normalized") {
      val schema = Schema.derived[LargeRecord]
      val structural = schema.structural

      val typeName = structural.reflect.typeName.name
      // Fields should be alphabetically sorted in type name
      assertTrue(
        typeName.indexOf("f1:") < typeName.indexOf("f10:"),
        typeName.indexOf("f10:") < typeName.indexOf("f2:")
      )
    } @@ TestAspect.ignore
  )
}


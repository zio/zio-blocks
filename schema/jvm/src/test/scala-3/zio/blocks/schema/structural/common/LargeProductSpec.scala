package zio.blocks.schema.structural.common

import zio.blocks.schema._
import zio.test._

/** Tests for large product types (>22 fields) to structural conversion. */
object LargeProductSpec extends ZIOSpecDefault {

  case class Record25(
    f1: Int,
    f2: Int,
    f3: Int,
    f4: Int,
    f5: Int,
    f6: Int,
    f7: Int,
    f8: Int,
    f9: Int,
    f10: Int,
    f11: Int,
    f12: Int,
    f13: Int,
    f14: Int,
    f15: Int,
    f16: Int,
    f17: Int,
    f18: Int,
    f19: Int,
    f20: Int,
    f21: Int,
    f22: Int,
    f23: Int,
    f24: Int,
    f25: Int
  )

  def spec: Spec[Any, Nothing] = suite("LargeProductSpec")(
    test("25 field record converts with correct field count") {
      val schema     = Schema.derived[Record25]
      val structural = schema.structural
      val numFields  = (structural.reflect: @unchecked) match {
        case record: Reflect.Record[_, _] => record.fields.size
      }
      assertTrue(numFields == 25)
    },
    test("25 field record has expected field names") {
      val schema     = Schema.derived[Record25]
      val structural = schema.structural
      val fieldNames = (structural.reflect: @unchecked) match {
        case record: Reflect.Record[_, _] => record.fields.map(_.name).toSet
      }
      assertTrue(fieldNames.contains("f1"), fieldNames.contains("f25"))
    }
  )
}

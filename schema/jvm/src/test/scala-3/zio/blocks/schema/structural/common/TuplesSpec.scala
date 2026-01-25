package zio.blocks.schema.structural.common

import zio.blocks.schema._
import zio.test._

/** Tests for tuple to structural type conversion. */
object TuplesSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Nothing] = suite("TuplesSpec")(
    test("tuple2 converts to structural with correct type name") {
      val schema     = Schema.derived[(String, Int)]
      val structural = schema.structural
      val typeName   = structural.reflect.typeName.name
      assertTrue(typeName == "{_1:String,_2:Int}")
    },
    test("tuple3 has correct field names") {
      val schema     = Schema.derived[(String, Int, Boolean)]
      val structural = schema.structural
      val fieldNames = (structural.reflect: @unchecked) match {
        case record: Reflect.Record[_, _] => record.fields.map(_.name).toList
      }
      assertTrue(fieldNames == List("_1", "_2", "_3"))
    },
    test("tuple round-trip preserves data") {
      val tuple     = ("hello", 42)
      val schema    = Schema.derived[(String, Int)]
      val dynamic   = schema.toDynamicValue(tuple)
      val roundTrip = schema.fromDynamicValue(dynamic)
      assertTrue(roundTrip == Right(tuple))
    }
  )
}

package zio.blocks.schema.json

import zio.blocks.schema.DynamicValue.{Primitive, Record, Sequence, Variant}
import zio.blocks.schema.json.DynamicValueGen._
import zio.blocks.schema.{DynamicValue, PrimitiveValue}
import zio.test._

object SerdeTest extends ZIOSpecDefault {

  private def toJsonSpec = suite("toJson")(
    test("simple test") {
      val record = Record(
        List(
          "number"  -> Primitive(value = PrimitiveValue.Int(42)),
          "hello"   -> Primitive(value = PrimitiveValue.String("world")),
          "boolean" -> Primitive(value = PrimitiveValue.Boolean(true))
        ).toIndexedSeq
      )
      val json = record.toJson
      assertTrue(json.nonEmpty)
    },
    test("record")(check(genRecord)(assertToJson)),
    test("variant")(check(genVariant)(assertToJson)),
    test("sequence")(check(genSequence)(assertToJson)),
    test("map")(check(genMap)(assertToJson))
  ) @@ TestAspect.exceptNative

  private def assertToJson(dynamicValue: DynamicValue): TestResult = {
    val json = dynamicValue.toJson
    // println(dynamicValue + "\n\t\t" + json)
    assertTrue(dynamicValue.toJson.nonEmpty)
  }

  def fromJson = suite("fromJson") {
    test("fromJson") {
      assertCompletes
    }
  }

  def spec = suite("SerdeTest")(toJsonSpec, fromJson)
}

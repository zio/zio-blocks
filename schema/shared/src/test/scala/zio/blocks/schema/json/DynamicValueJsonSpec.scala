package zio.blocks.schema.json

import zio.blocks.schema.DynamicValue.{Primitive, Record}
import zio.blocks.schema.DynamicValueGen._
import zio.blocks.schema.{DynamicValue, PrimitiveValue}
import zio.test._

object DynamicValueJsonSpec extends ZIOSpecDefault {
  private val simpleRecord = Record(
    List(
      "number"  -> Primitive(value = PrimitiveValue.Int(42)),
      "hello"   -> Primitive(value = PrimitiveValue.String("world")),
      "boolean" -> Primitive(value = PrimitiveValue.Boolean(true))
    ).toVector
  )
  private def toJsonSpec = suite("toJson")(
    test("simple test") {
      val json = simpleRecord.toJson
      assertTrue(json.nonEmpty)
    },
    test("record")(check(genRecord)(verifyDynamicValueToJson)),
    test("variant")(check(genVariant)(verifyDynamicValueToJson)),
    test("sequence")(check(genSequence)(verifyDynamicValueToJson)),
    test("map")(check(genMap)(verifyDynamicValueToJson))
  )

  private def verifyDynamicValueToJson(dynamicValue: DynamicValue): TestResult = {
    val json = dynamicValue.toJson
    assertTrue(json.nonEmpty && (json.startsWith("{") || json.startsWith("[")))
  }

  private def fromJson = suite("fromJson")(
    test("low-level") {
      assertTrue(parseJson("""{"number":42,"hello":"world","boolean":true}""").isRight)
    },
    test("sample") {
      val jsonOfRecord = simpleRecord.toJson
      val dynamicValue = DynamicValue.fromJson(jsonOfRecord)
      // TODO: Equality for DynamicValue should be further improved
      assertTrue(dynamicValue.toJson == simpleRecord.toJson)
    },
    test("sequence")(check(genAlphaNumericSequence) { seq =>
      assertTrue(seq == DynamicValue.fromJson(seq.toJson))
    }),
    test("map")(check(genMap) { m =>
      val mapJson  = m.toJson
      val mapJson2 = DynamicValue.fromJson(mapJson).toJson
      assertTrue(mapJson.nonEmpty && mapJson2.nonEmpty)
    })
  )

  def spec =
    suite("DynamicValueJsonTest")(toJsonSpec, fromJson) @@ TestAspect.exceptNative
}

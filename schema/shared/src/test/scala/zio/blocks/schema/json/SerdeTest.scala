package zio.blocks.schema.json

import zio.blocks.schema.DynamicValue.{Primitive, Record, Sequence, Variant}
import zio.blocks.schema.{DynamicValue, PrimitiveValue}
import zio.test._

object SerdeTest extends ZIOSpecDefault {

  private def genPrimitiveValue: Gen[Any, PrimitiveValue] =
    Gen.oneOf(
      Gen.alphaNumericString.map(PrimitiveValue.String),
      Gen.int.map(PrimitiveValue.Int),
      Gen.boolean.map(PrimitiveValue.Boolean),
      Gen.byte.map(PrimitiveValue.Byte),
      Gen.boolean.map(PrimitiveValue.Boolean),
      Gen.double.map(PrimitiveValue.Double),
      Gen.float.map(PrimitiveValue.Float),
      Gen.long.map(PrimitiveValue.Long),
      Gen.short.map(PrimitiveValue.Short),
      Gen.char.map(PrimitiveValue.Char),
      Gen.bigInt(BigInt(100), BigInt(1000)).map(PrimitiveValue.BigInt),
      Gen.bigDecimal(BigDecimal(100), BigDecimal(1000)).map(PrimitiveValue.BigDecimal)
      // TODO: Add more here...
    )

  private def genRecord: Gen[Any, Record] = Gen
    .listOfBounded(0, 10) {
      for {
        key   <- Gen.alphaNumericString
        value <- genPrimitiveValue
      } yield key -> Primitive(value)
    }
    .map(f => Record(f.toIndexedSeq))

  private def genVariant: Gen[Any, Variant] = for {
    caseName <- Gen.alphaNumericString
    value    <- genPrimitiveValue
  } yield Variant(caseName, Primitive(value))

  private def genSequence: Gen[Any, Sequence] =
    Gen
      .listOfBounded(0, 10)(genPrimitiveValue.map(Primitive(_)))
      .map(f => Sequence(f.toIndexedSeq))

  private def genMap: Gen[Any, DynamicValue.Map] =
    Gen
      .listOfBounded(0, 10) {
        for {
          key   <- genPrimitiveValue.map(Primitive(_))
          value <- genPrimitiveValue.map(Primitive(_))
        } yield key -> value
      }
      .map(list => DynamicValue.Map(list.toIndexedSeq))

  private def genDynamicValue: Gen[Any, DynamicValue] =
    Gen.oneOf(
      genRecord,
      genVariant,
      genSequence,
      genMap
    )

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

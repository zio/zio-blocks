package zio.blocks.schema

import zio.test._

object DynamicValueTypeSpec extends SchemaBaseSpec {

  val stringVal: DynamicValue  = DynamicValue.string("hello")
  val recordVal: DynamicValue  = DynamicValue.Record("name" -> stringVal)
  val variantVal: DynamicValue = DynamicValue.Variant("Some", stringVal)
  val seqVal: DynamicValue     = DynamicValue.Sequence(stringVal)
  val mapVal: DynamicValue     = DynamicValue.Map(stringVal -> DynamicValue.int(1))
  val nullVal: DynamicValue    = DynamicValue.Null

  def spec: Spec[TestEnvironment, Any] = suite("DynamicValueTypeSpec")(
    suite("Type indices")(
      test("Primitive has typeIndex 0") {
        assertTrue(DynamicValueType.Primitive.typeIndex == 0)
      },
      test("Record has typeIndex 1") {
        assertTrue(DynamicValueType.Record.typeIndex == 1)
      },
      test("Variant has typeIndex 2") {
        assertTrue(DynamicValueType.Variant.typeIndex == 2)
      },
      test("Sequence has typeIndex 3") {
        assertTrue(DynamicValueType.Sequence.typeIndex == 3)
      },
      test("Map has typeIndex 4") {
        assertTrue(DynamicValueType.Map.typeIndex == 4)
      },
      test("Null has typeIndex 5") {
        assertTrue(DynamicValueType.Null.typeIndex == 5)
      }
    ),
    suite("apply method (predicate)")(
      test("Primitive applies correctly") {
        assertTrue(DynamicValueType.Primitive(stringVal)) &&
        assertTrue(!DynamicValueType.Primitive(recordVal))
      },
      test("Record applies correctly") {
        assertTrue(DynamicValueType.Record(recordVal)) &&
        assertTrue(!DynamicValueType.Record(stringVal))
      },
      test("Variant applies correctly") {
        assertTrue(DynamicValueType.Variant(variantVal)) &&
        assertTrue(!DynamicValueType.Variant(stringVal))
      },
      test("Sequence applies correctly") {
        assertTrue(DynamicValueType.Sequence(seqVal)) &&
        assertTrue(!DynamicValueType.Sequence(stringVal))
      },
      test("Map applies correctly") {
        assertTrue(DynamicValueType.Map(mapVal)) &&
        assertTrue(!DynamicValueType.Map(stringVal))
      },
      test("Null applies correctly") {
        assertTrue(DynamicValueType.Null(nullVal)) &&
        assertTrue(!DynamicValueType.Null(stringVal))
      }
    ),
    suite("Type member consistency")(
      test("Primitive Type is DynamicValue.Primitive") {
        val dv: DynamicValueType.Primitive.Type = DynamicValue.Primitive(PrimitiveValue.Int(1))
        assertTrue(dv.isInstanceOf[DynamicValue.Primitive])
      },
      test("Record Type is DynamicValue.Record") {
        val dv: DynamicValueType.Record.Type = DynamicValue.Record.empty
        assertTrue(dv.isInstanceOf[DynamicValue.Record])
      },
      test("Variant Type is DynamicValue.Variant") {
        val dv: DynamicValueType.Variant.Type = DynamicValue.Variant("case", DynamicValue.unit)
        assertTrue(dv.isInstanceOf[DynamicValue.Variant])
      },
      test("Sequence Type is DynamicValue.Sequence") {
        val dv: DynamicValueType.Sequence.Type = DynamicValue.Sequence.empty
        assertTrue(dv.isInstanceOf[DynamicValue.Sequence])
      },
      test("Map Type is DynamicValue.Map") {
        val dv: DynamicValueType.Map.Type = DynamicValue.Map.empty
        assertTrue(dv.isInstanceOf[DynamicValue.Map])
      },
      test("Null Type is DynamicValue.Null.type") {
        val dv: DynamicValueType.Null.Type = DynamicValue.Null
        assertTrue(dv == DynamicValue.Null)
      }
    ),
    suite("Equality")(
      test("DynamicValueType objects are singletons") {
        assertTrue(DynamicValueType.Primitive eq DynamicValueType.Primitive) &&
        assertTrue(DynamicValueType.Record eq DynamicValueType.Record) &&
        assertTrue(DynamicValueType.Variant eq DynamicValueType.Variant) &&
        assertTrue(DynamicValueType.Sequence eq DynamicValueType.Sequence) &&
        assertTrue(DynamicValueType.Map eq DynamicValueType.Map) &&
        assertTrue(DynamicValueType.Null eq DynamicValueType.Null)
      },
      test("Different types are not equal") {
        assertTrue(DynamicValueType.Primitive != DynamicValueType.Record) &&
        assertTrue(DynamicValueType.Record != DynamicValueType.Variant) &&
        assertTrue(DynamicValueType.Variant != DynamicValueType.Sequence) &&
        assertTrue(DynamicValueType.Sequence != DynamicValueType.Map) &&
        assertTrue(DynamicValueType.Map != DynamicValueType.Null)
      }
    )
  )
}

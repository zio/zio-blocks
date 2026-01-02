package zio.blocks.schema.structural.common

import zio.blocks.schema._
import zio.test._

/**
 * Tests for large product types (>22 fields) to structural conversion.
 * 22 is a significant boundary in Scala (tuple limit, old case class limit).
 */
object LargeProductSpec extends ZIOSpecDefault {

  case class Record25(
    f1: Int, f2: Int, f3: Int, f4: Int, f5: Int,
    f6: Int, f7: Int, f8: Int, f9: Int, f10: Int,
    f11: Int, f12: Int, f13: Int, f14: Int, f15: Int,
    f16: Int, f17: Int, f18: Int, f19: Int, f20: Int,
    f21: Int, f22: Int, f23: Int, f24: Int, f25: Int
  )

  case class MixedRecord30(
    s1: String, i1: Int, b1: Boolean,
    s2: String, i2: Int, b2: Boolean,
    s3: String, i3: Int, b3: Boolean,
    s4: String, i4: Int, b4: Boolean,
    s5: String, i5: Int, b5: Boolean,
    s6: String, i6: Int, b6: Boolean,
    s7: String, i7: Int, b7: Boolean,
    s8: String, i8: Int, b8: Boolean,
    s9: String, i9: Int, b9: Boolean,
    s10: String, i10: Int, b10: Boolean
  )

  private def intPrim(i: Int) = DynamicValue.Primitive(PrimitiveValue.Int(i))
  private def strPrim(s: String) = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def boolPrim(b: Boolean) = DynamicValue.Primitive(PrimitiveValue.Boolean(b))

  def spec = suite("LargeProductSpec")(
    suite("Field Count Verification")(
      test("25 field record converts with correct field count") {
        val schema = Schema.derived[Record25]
        val structural = schema.structural

        val numFields = structural.reflect match {
          case record: Reflect.Record[_, _] => record.fields.size
          case _                            => -1
        }
        assertTrue(numFields == 25)
      },
      test("25 field record has all field names") {
        val schema = Schema.derived[Record25]
        val structural = schema.structural

        val fieldNames = structural.reflect match {
          case record: Reflect.Record[_, _] => record.fields.map(_.name).toSet
          case _                            => Set.empty[String]
        }
        assertTrue(
          fieldNames.size == 25,
          fieldNames.contains("f1"),
          fieldNames.contains("f13"),
          fieldNames.contains("f25")
        )
      },
      test("30 field mixed record converts correctly") {
        val schema = Schema.derived[MixedRecord30]
        val structural = schema.structural

        val numFields = structural.reflect match {
          case record: Reflect.Record[_, _] => record.fields.size
          case _                            => -1
        }
        assertTrue(numFields == 30)
      }
    ),
    suite("Type Name Normalization")(
      test("type name is alphabetically sorted") {
        val schema = Schema.derived[Record25]
        val structural = schema.structural

        val typeName = structural.reflect.typeName.name
        // Fields should be alphabetically sorted: f1, f10, f11, ..., f2, f20, ...
        val f1Idx = typeName.indexOf("f1:")
        val f10Idx = typeName.indexOf("f10:")
        val f2Idx = typeName.indexOf("f2:")

        assertTrue(
          f1Idx < f10Idx,  // f1 before f10 (alphabetically)
          f10Idx < f2Idx   // f10 before f2 (alphabetically)
        )
      },
      test("type name format is correct for large record") {
        val schema = Schema.derived[Record25]
        val structural = schema.structural

        val typeName = structural.reflect.typeName.name
        assertTrue(
          typeName.startsWith("{"),
          typeName.endsWith("}"),
          typeName.contains("f1:Int"),
          typeName.contains("f25:Int"),
          !typeName.contains(" ") // no whitespace
        )
      }
    ),
    suite("Construction and Destruction")(
      test("25 field record round-trip preserves all data") {
        val record = Record25(
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
          11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
          21, 22, 23, 24, 25
        )
        val schema = Schema.derived[Record25]
        val structural = schema.structural

        val dynamic = structural.asInstanceOf[Schema[Any]].toDynamicValue(record)

        dynamic match {
          case dv: DynamicValue.Record =>
            val fieldMap = dv.fields.toMap
            assertTrue(
              fieldMap.size == 25,
              fieldMap.get("f1").contains(intPrim(1)),
              fieldMap.get("f13").contains(intPrim(13)),
              fieldMap.get("f22").contains(intPrim(22)),
              fieldMap.get("f25").contains(intPrim(25))
            )
          case _ =>
            assertTrue(false)
        }
      },
      test("30 field mixed record round-trip preserves all data") {
        val record = MixedRecord30(
          "s1", 1, true, "s2", 2, false, "s3", 3, true,
          "s4", 4, false, "s5", 5, true, "s6", 6, false,
          "s7", 7, true, "s8", 8, false, "s9", 9, true,
          "s10", 10, false
        )
        val schema = Schema.derived[MixedRecord30]
        val structural = schema.structural

        val dynamic = structural.asInstanceOf[Schema[Any]].toDynamicValue(record)

        dynamic match {
          case dv: DynamicValue.Record =>
            val fieldMap = dv.fields.toMap
            assertTrue(
              fieldMap.size == 30,
              fieldMap.get("s1").contains(strPrim("s1")),
              fieldMap.get("i5").contains(intPrim(5)),
              fieldMap.get("b10").contains(boolPrim(false)),
              fieldMap.get("s10").contains(strPrim("s10"))
            )
          case _ =>
            assertTrue(false)
        }
      },
      test("construction from DynamicValue succeeds for 25 field record") {
        val schema = Schema.derived[Record25]
        val structural = schema.structural

        val fields = (1 to 25).map { i =>
          s"f$i" -> intPrim(i * 10)
        }.toVector

        val dynamic = DynamicValue.Record(fields)

        val result = structural.fromDynamicValue(dynamic)
        assertTrue(result.isRight)
      }
    ),
    suite("Edge Cases")(
      test("all 25 fields have correct values after round-trip") {
        val record = Record25(
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
          11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
          21, 22, 23, 24, 25
        )
        val schema = Schema.derived[Record25]
        val structural = schema.structural

        val dynamic = structural.asInstanceOf[Schema[Any]].toDynamicValue(record)

        dynamic match {
          case dv: DynamicValue.Record =>
            val fieldMap = dv.fields.toMap
            assertTrue(
              (1 to 25).forall { i =>
                fieldMap.get(s"f$i").contains(intPrim(i))
              }
            )
          case _ =>
            assertTrue(false)
        }
      },
      test("field types are preserved in mixed 30-field record") {
        val schema = Schema.derived[MixedRecord30]
        val structural = schema.structural

        val fieldTypes = structural.reflect match {
          case record: Reflect.Record[_, _] =>
            record.fields.map(f => f.name -> f.value.typeName.name).toMap
          case _ => Map.empty[String, String]
        }

        assertTrue(
          fieldTypes.size == 30,
          fieldTypes.get("s1").contains("String"),
          fieldTypes.get("i1").contains("Int"),
          fieldTypes.get("b1").contains("Boolean"),
          fieldTypes.get("s10").contains("String"),
          fieldTypes.get("i10").contains("Int"),
          fieldTypes.get("b10").contains("Boolean")
        )
      }
    )
  )
}


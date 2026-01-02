package zio.blocks.schema.structural.common

import zio.blocks.schema._
import zio.test._

/**
 * Tests for tuple to structural type conversion.
 */
object TuplesSpec extends ZIOSpecDefault {

  case class Inner(value: Int)

  private def intPrim(i: Int)      = DynamicValue.Primitive(PrimitiveValue.Int(i))
  private def strPrim(s: String)   = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def boolPrim(b: Boolean) = DynamicValue.Primitive(PrimitiveValue.Boolean(b))

  def spec = suite("TuplesSpec")(
    suite("Type Name Verification")(
      test("tuple2 schema converts to structural with correct type name") {
        val tupleSchema: Schema[(String, Int)] = Schema.derived[(String, Int)]
        val structuralSchema                   = tupleSchema.structural

        val typeName = structuralSchema.reflect.typeName.name
        assertTrue(typeName == "{_1:String,_2:Int}")
      },
      test("tuple3 structural has correct field names") {
        val tupleSchema      = Schema.derived[(String, Int, Boolean)]
        val structuralSchema = tupleSchema.structural

        val fieldNames = structuralSchema.reflect match {
          case record: Reflect.Record[_, _] => record.fields.map(_.name).toList
          case _                            => Nil
        }
        assertTrue(fieldNames == List("_1", "_2", "_3"))
      },
      test("tuple4 converts with correct type name") {
        val tupleSchema      = Schema.derived[(Int, String, Boolean, Double)]
        val structuralSchema = tupleSchema.structural

        val typeName = structuralSchema.reflect.typeName.name
        assertTrue(typeName == "{_1:Int,_2:String,_3:Boolean,_4:Double}")
      },
      test("tuple10 has correct field count") {
        val tupleSchema      = Schema.derived[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)]
        val structuralSchema = tupleSchema.structural

        val fieldNames = structuralSchema.reflect match {
          case record: Reflect.Record[_, _] => record.fields.map(_.name).toList
          case _                            => Nil
        }
        assertTrue(
          fieldNames.size == 10,
          fieldNames == List("_1", "_2", "_3", "_4", "_5", "_6", "_7", "_8", "_9", "_10")
        )
      }
    ),
    suite("Construction and Destruction")(
      test("tuple2 round-trip through structural schema preserves data") {
        val tuple            = ("hello", 42)
        val tupleSchema      = Schema.derived[(String, Int)]
        val structuralSchema = tupleSchema.structural

        val dynamic = structuralSchema.asInstanceOf[Schema[Any]].toDynamicValue(tuple)

        dynamic match {
          case record: DynamicValue.Record =>
            val fieldMap = record.fields.toMap
            assertTrue(
              fieldMap.get("_1").contains(strPrim("hello")),
              fieldMap.get("_2").contains(intPrim(42))
            )
          case _ =>
            assertTrue(false)
        }
      },
      test("tuple3 deconstruction extracts all fields correctly") {
        val tuple            = ("a", 1, true)
        val tupleSchema      = Schema.derived[(String, Int, Boolean)]
        val structuralSchema = tupleSchema.structural

        val dynamic = structuralSchema.asInstanceOf[Schema[Any]].toDynamicValue(tuple)

        dynamic match {
          case record: DynamicValue.Record =>
            val fieldMap = record.fields.toMap
            assertTrue(
              fieldMap.size == 3,
              fieldMap.get("_1").contains(strPrim("a")),
              fieldMap.get("_2").contains(intPrim(1)),
              fieldMap.get("_3").contains(boolPrim(true))
            )
          case _ =>
            assertTrue(false)
        }
      },
      test("tuple construction from DynamicValue works") {
        val tupleSchema      = Schema.derived[(String, Int)]
        val structuralSchema = tupleSchema.structural

        val dynamic = DynamicValue.Record(
          Vector(
            "_1" -> strPrim("test"),
            "_2" -> intPrim(99)
          )
        )

        val result = structuralSchema.fromDynamicValue(dynamic)
        assertTrue(result.isRight)
      }
    ),
    suite("Nested Types")(
      test("tuple with nested case class converts inner type to structural") {
        val tupleSchema      = Schema.derived[(String, Inner)]
        val structuralSchema = tupleSchema.structural

        val typeName = structuralSchema.reflect.typeName.name

        assertTrue(
          typeName.contains("_1"),
          typeName.contains("_2"),
          typeName.contains("String")
        )
      },
      test("tuple with nested case class round-trip preserves nested data") {
        val tuple            = ("outer", Inner(42))
        val tupleSchema      = Schema.derived[(String, Inner)]
        val structuralSchema = tupleSchema.structural

        val dynamic = structuralSchema.asInstanceOf[Schema[Any]].toDynamicValue(tuple)

        dynamic match {
          case record: DynamicValue.Record =>
            val fieldMap = record.fields.toMap
            assertTrue(
              fieldMap.get("_1").contains(strPrim("outer")),
              fieldMap.get("_2") match {
                case Some(DynamicValue.Record(innerFields)) =>
                  val innerMap = innerFields.toMap
                  innerMap.get("value").contains(intPrim(42))
                case _ => false
              }
            )
          case _ =>
            assertTrue(false)
        }
      },
      test("tuple with multiple nested case classes") {
        case class Point(x: Int, y: Int)
        val tupleSchema      = Schema.derived[(Inner, Point)]
        val structuralSchema = tupleSchema.structural

        val tuple   = (Inner(1), Point(10, 20))
        val dynamic = structuralSchema.asInstanceOf[Schema[Any]].toDynamicValue(tuple)

        dynamic match {
          case record: DynamicValue.Record =>
            val fieldMap = record.fields.toMap
            assertTrue(
              fieldMap.size == 2,
              fieldMap.get("_1") match {
                case Some(DynamicValue.Record(f)) => f.toMap.get("value").contains(intPrim(1))
                case _                            => false
              },
              fieldMap.get("_2") match {
                case Some(DynamicValue.Record(f)) =>
                  val m = f.toMap
                  m.get("x").contains(intPrim(10)) &&
                  m.get("y").contains(intPrim(20))
                case _ => false
              }
            )
          case _ =>
            assertTrue(false)
        }
      }
    ),
    suite("Edge Cases")(
      test("tuple with all same types") {
        val tupleSchema      = Schema.derived[(Int, Int, Int)]
        val structuralSchema = tupleSchema.structural

        val tuple   = (1, 2, 3)
        val dynamic = structuralSchema.asInstanceOf[Schema[Any]].toDynamicValue(tuple)

        dynamic match {
          case record: DynamicValue.Record =>
            val fields = record.fields
            assertTrue(
              fields.size == 3,
              fields(0) == ("_1" -> intPrim(1)),
              fields(1) == ("_2" -> intPrim(2)),
              fields(2) == ("_3" -> intPrim(3))
            )
          case _ =>
            assertTrue(false)
        }
      },
      test("tuple with Option field") {
        val tupleSchema      = Schema.derived[(String, Option[Int])]
        val structuralSchema = tupleSchema.structural

        val typeName = structuralSchema.reflect.typeName.name
        assertTrue(
          typeName.contains("_1"),
          typeName.contains("_2")
        )
      }
    )
  )
}

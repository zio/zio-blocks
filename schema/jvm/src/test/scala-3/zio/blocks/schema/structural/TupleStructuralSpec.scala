package zio.blocks.schema.structural

import zio.blocks.schema._
import zio.test._

/**
 * Tests for tuple to structural type conversion (JVM only).
 */
object TupleStructuralSpec extends ZIOSpecDefault {

  def spec = suite("TupleStructuralSpec")(
    suite("Tuple2 structural conversion")(
      test("Tuple2 converts to structural type") {
        val schema     = Schema.derived[(String, Int)]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name
        assertTrue(
          typeName.contains("_1:String"),
          typeName.contains("_2:Int")
        )
      },
      test("Tuple2 round-trips correctly") {
        val schema   = Schema.derived[(String, Int)]
        val original = ("hello", 42)

        val dynamic   = schema.toDynamicValue(original)
        val roundTrip = schema.fromDynamicValue(dynamic)

        assertTrue(roundTrip == Right(original))
      }
    ),
    suite("Tuple3 structural conversion")(
      test("Tuple3 converts to structural type") {
        val schema     = Schema.derived[(String, Int, Boolean)]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name
        assertTrue(
          typeName.contains("_1:String"),
          typeName.contains("_2:Int"),
          typeName.contains("_3:Boolean")
        )
      },
      test("Tuple3 structural schema preserves field types") {
        val schema     = Schema.derived[(Double, Long, String)]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name
        assertTrue(
          typeName.contains("_1:Double"),
          typeName.contains("_2:Long"),
          typeName.contains("_3:String")
        )
      }
    ),
    suite("Tuple with complex types")(
      test("Tuple with Option field") {
        val schema     = Schema.derived[(String, Option[Int])]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name
        assertTrue(
          typeName.contains("_1:String"),
          typeName.contains("_2:Option")
        )
      },
      test("Tuple with List field") {
        val schema     = Schema.derived[(String, List[Int])]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name
        assertTrue(
          typeName.contains("_1:String"),
          typeName.contains("_2:List")
        )
      }
    ),
    suite("Tuple structural schema is a Record")(
      test("Tuple2 structural schema is a Record") {
        val schema     = Schema.derived[(String, Int)]
        val structural = schema.structural
        val isRecord   = (structural.reflect: @unchecked) match {
          case _: Reflect.Record[_, _] => true
        }
        assertTrue(isRecord)
      },
      test("Tuple3 structural schema has correct field count") {
        val schema     = Schema.derived[(String, Int, Boolean)]
        val structural = schema.structural
        val fieldCount = (structural.reflect: @unchecked) match {
          case r: Reflect.Record[_, _] => r.fields.size
        }
        assertTrue(fieldCount == 3)
      }
    )
  )
}

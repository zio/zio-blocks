package zio.blocks.schema.structural
import zio.blocks.schema.SchemaBaseSpec

import zio.blocks.schema._
import zio.test._

/**
 * Tests for tuple to structural type conversion (JVM only).
 */
object TupleStructuralSpec extends SchemaBaseSpec {

  def spec = suite("TupleStructuralSpec")(
    suite("Tuple2 structural conversion")(
      test("Tuple2 round-trips correctly") {
        val schema   = Schema.derived[(String, Int)]
        val original = ("hello", 42)

        val dynamic   = schema.toDynamicValue(original)
        val roundTrip = schema.fromDynamicValue(dynamic)

        assertTrue(roundTrip == Right(original))
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
    ),
    suite("Type-level structural conversion")(
      test("Tuple2 converts to expected structural type") {
        typeCheck("""
          import zio.blocks.schema._
          val schema = Schema.derived[(String, Int)]
          val structural: Schema[{def _1: String; def _2: Int}] = schema.structural
        """).map(result => assertTrue(result.isRight))
      },
      test("Tuple3 converts to expected structural type") {
        typeCheck("""
          import zio.blocks.schema._
          val schema = Schema.derived[(String, Int, Boolean)]
          val structural: Schema[{def _1: String; def _2: Int; def _3: Boolean}] = schema.structural
        """).map(result => assertTrue(result.isRight))
      }
    )
  )
}

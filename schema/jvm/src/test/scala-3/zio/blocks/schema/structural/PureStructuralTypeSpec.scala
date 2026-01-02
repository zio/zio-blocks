package zio.blocks.schema.structural

import zio.blocks.schema._
import zio.test._

/**
 * Tests for pure structural type Schema derivation in Scala 3 (JVM only).
 *
 * Pure structural types (refinement types without a Selectable base) are
 * supported via reflection-based field access. The generated schema creates
 * anonymous Selectable instances at runtime.
 *
 * Note: This uses JVM reflection, so pure structural types only work on JVM.
 * For cross-platform support, use a Selectable base class with a Map[String,
 * Any] constructor.
 */
object PureStructuralTypeSpec extends ZIOSpecDefault {

  type PersonLike  = { def name: String; def age: Int }
  type PointLike   = { def x: Int; def y: Int }
  type SingleField = { def value: String }

  def spec = suite("PureStructuralTypeSpec")(
    suite("Schema Derivation")(
      test("pure structural type PersonLike derives schema") {
        val schema = Schema.derived[PersonLike]
        assertTrue(schema != null)
      },
      test("pure structural type PointLike derives schema") {
        val schema = Schema.derived[PointLike]
        assertTrue(schema != null)
      },
      test("pure structural type with single field derives schema") {
        val schema = Schema.derived[SingleField]
        assertTrue(schema != null)
      }
    ),
    suite("Schema Structure")(
      test("pure structural type schema has correct field names") {
        val schema     = Schema.derived[PersonLike]
        val fieldNames = schema.reflect match {
          case record: Reflect.Record[_, _] => record.fields.map(_.name).toSet
          case _                            => Set.empty[String]
        }
        assertTrue(fieldNames == Set("name", "age"))
      },
      test("pure structural type schema has correct field count") {
        val schema     = Schema.derived[PointLike]
        val fieldCount = schema.reflect match {
          case record: Reflect.Record[_, _] => record.fields.size
          case _                            => -1
        }
        assertTrue(fieldCount == 2)
      }
    )
  )
}

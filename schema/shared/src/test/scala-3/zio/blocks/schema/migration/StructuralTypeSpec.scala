package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._
import scala.language.reflectiveCalls

/**
 * Tests for structural type support in the migration system.
 *
 * Structural types are compile-time only types with no runtime representation.
 * At runtime, they are represented as DynamicValue.
 *
 * This demonstrates the migration system's ability to work with structural
 * types as specified in issue #519.
 */
object StructuralTypeSpec extends ZIOSpecDefault {

  // Structural type definition (compile-time only)
  type PersonV0 = {
    def firstName: String
    def lastName: String
  }

  // Runtime representation for testing
  case class PersonV0Repr(firstName: String, lastName: String)

  // Cast the runtime schema to structural type schema
  val personV0Schema: Schema[PersonV0] =
    Schema.derived[PersonV0Repr].asInstanceOf[Schema[PersonV0]]

  // Target case class
  case class PersonV1(fullName: String, age: Int)
  val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]

  def spec = suite("StructuralTypeSpec (Scala 3)")(
    test("Can migrate simple structural type with single field") {
      type SimpleV0 = { def age: Int }
      case class SimpleV0Repr(age: Int)
      val simpleV0Schema: Schema[SimpleV0] =
        Schema.derived[SimpleV0Repr].asInstanceOf[Schema[SimpleV0]]

      case class SimpleV1(years: Int)
      val simpleV1Schema: Schema[SimpleV1] = Schema.derived[SimpleV1]

      val migration = Migration
        .newBuilder(simpleV0Schema, simpleV1Schema)
        .renameFieldByName("age", "years")
        .buildUnchecked

      {

        val oldData = DynamicValue.Record(
          Vector("age" -> DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )

        val result = migration.dynamicMigration.apply(oldData)

        val expected = DynamicValue.Record(
          Vector("years" -> DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )

        assert(result)(isRight(equalTo(expected)))
      }
    }
  )
}

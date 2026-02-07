package zio.blocks.schema.migration

import zio.test.*
import zio.blocks.schema.*

object MigrationBuildValidationSpec extends ZIOSpecDefault {

  private def literal(dv: DynamicValue): SchemaExpr[Any, DynamicValue] =
    SchemaExpr.Literal(dv)

  def spec: Spec[TestEnvironment, Any] = suite("MigrationBuildValidationSpec")(
    suite("build validation")(
      test("build succeeds when all fields are handled via auto-mapping") {
        case class PersonV1(name: String, age: Int)
        case class PersonV2(name: String, age: Int)

        given Schema[PersonV1] = Schema.derived[PersonV1]
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .build

        val input  = PersonV1("Alice", 30)
        val result = migration(input)

        assertTrue(result == Right(PersonV2("Alice", 30)))
      },
      test("build succeeds when new field is added") {
        case class PersonV1(name: String)
        case class PersonV2(name: String, age: Int)

        given Schema[PersonV1] = Schema.derived[PersonV1]
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .addField(_.age, literal(DynamicValue.Primitive(PrimitiveValue.Int(0))))
          .build

        val input  = PersonV1("Alice")
        val result = migration(input)

        assertTrue(result == Right(PersonV2("Alice", 0)))
      },
      test("build succeeds when field is dropped") {
        case class PersonV1(name: String, age: Int)
        case class PersonV2(name: String)

        given Schema[PersonV1] = Schema.derived[PersonV1]
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .dropField(_.age, literal(DynamicValue.Primitive(PrimitiveValue.Int(0))))
          .build

        val input  = PersonV1("Alice", 30)
        val result = migration(input)

        assertTrue(result == Right(PersonV2("Alice")))
      },
      test("build succeeds when field is renamed") {
        case class PersonV1(firstName: String)
        case class PersonV2(fullName: String)

        given Schema[PersonV1] = Schema.derived[PersonV1]
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .renameField(_.firstName, _.fullName)
          .build

        val input  = PersonV1("Alice")
        val result = migration(input)

        assertTrue(result == Right(PersonV2("Alice")))
      },
      test("build succeeds when field is transformed") {
        case class PersonV1(age: Int)
        case class PersonV2(age: Long)

        given Schema[PersonV1] = Schema.derived[PersonV1]
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .transformField(_.age, _.age, literal(DynamicValue.Primitive(PrimitiveValue.Long(30L))))
          .build

        val input  = PersonV1(30)
        val result = migration(input)

        assertTrue(result == Right(PersonV2(30L)))
      },
      test("build fails to compile when target field is missing") {
        typeCheck {
          """
          import zio.blocks.schema.Schema
          import zio.blocks.schema.migration.Migration
          
          case class V1(name: String)
          case class V2(name: String, age: Int)

          given Schema[V1] = Schema.derived[V1]
          given Schema[V2] = Schema.derived[V2]

          Migration.newBuilder[V1, V2].build
          """
        }.map { result =>
          assertTrue(result.isLeft)
        }
      },
      test("build fails to compile when source field is not handled") {
        typeCheck {
          """
          import zio.blocks.schema.Schema
          import zio.blocks.schema.migration.Migration
          
          case class V1(name: String, age: Int)
          case class V2(name: String)

          given Schema[V1] = Schema.derived[V1]
          given Schema[V2] = Schema.derived[V2]

          Migration.newBuilder[V1, V2].build
          """
        }.map { result =>
          assertTrue(result.isLeft)
        }
      },
      test("buildPartial succeeds even when fields are missing") {
        case class PersonV1(name: String, age: Int)
        case class PersonV2(name: String, birthYear: Int)

        given Schema[PersonV1] = Schema.derived[PersonV1]
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .buildPartial

        assertTrue(migration.actions.isEmpty)
      }
    )
  )
}

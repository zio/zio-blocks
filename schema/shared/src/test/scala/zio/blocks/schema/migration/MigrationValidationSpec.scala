package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test.Assertion._
import zio.test._

object MigrationValidationSpec extends SchemaBaseSpec {

  case class UserV1(name: String, age: Int)
  case class UserV2(name: String, age: Int, email: String)
  case class UserV3(name: String, email: String)
  case class UserV4(fullName: String, age: Int)

  implicit val schemaV1: Schema[UserV1] = Schema.derived[UserV1]
  implicit val schemaV2: Schema[UserV2] = Schema.derived[UserV2]
  implicit val schemaV3: Schema[UserV3] = Schema.derived[UserV3]
  implicit val schemaV4: Schema[UserV4] = Schema.derived[UserV4]

  private val defaultStr =
    DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
  private val defaultInt =
    DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))

  def spec: Spec[TestEnvironment, Any] = suite("MigrationValidationSpec")(
    validateSuite,
    buildSuite,
    buildPartialSuite
  )

  private val validateSuite = suite("validate")(
    test("same-schema migration is valid") {
      val errors = MigrationValidation.validate(
        Schema[UserV1],
        Schema[UserV1],
        Vector.empty
      )
      assert(errors)(isEmpty)
    },
    test("addField migration is valid when field exists in target") {
      val errors = MigrationValidation.validate(
        Schema[UserV1],
        Schema[UserV2],
        Vector(MigrationAction.AddField(DynamicOptic.root.field("email"), defaultStr))
      )
      assert(errors)(isEmpty)
    },
    test("missing addField for new target field reports error") {
      val errors = MigrationValidation.validate(
        Schema[UserV1],
        Schema[UserV2],
        Vector.empty
      )
      assert(errors)(exists(containsString("not accounted for")))
    },
    test("dropField migration is valid when field exists in source") {
      val errors = MigrationValidation.validate(
        Schema[UserV2],
        Schema[UserV3],
        Vector(MigrationAction.DropField(DynamicOptic.root.field("age"), defaultInt))
      )
      assert(errors)(isEmpty)
    },
    test("dropField for nonexistent source field reports error") {
      val errors = MigrationValidation.validate(
        Schema[UserV1],
        Schema[UserV1],
        Vector(MigrationAction.DropField(DynamicOptic.root.field("nonexistent"), defaultInt))
      )
      assert(errors)(exists(containsString("does not exist in the source")))
    },
    test("rename migration is valid") {
      val errors = MigrationValidation.validate(
        Schema[UserV1],
        Schema[UserV4],
        Vector(MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName"))
      )
      assert(errors)(isEmpty)
    },
    test("rename from nonexistent field reports error") {
      val errors = MigrationValidation.validate(
        Schema[UserV1],
        Schema[UserV4],
        Vector(MigrationAction.Rename(DynamicOptic.root.field("nonexistent"), "fullName"))
      )
      assert(errors)(exists(containsString("does not exist in the source")))
    },
    test("unhandled source fields are reported") {
      val errors = MigrationValidation.validate(
        Schema[UserV2],
        Schema[UserV1],
        Vector.empty
      )
      assert(errors)(exists(containsString("not handled")))
    },
    test("primitive schemas skip field validation") {
      val errors = MigrationValidation.validate(
        Schema[Int],
        Schema[String],
        Vector.empty
      )
      assert(errors)(isEmpty)
    }
  )

  private val buildSuite = suite("build")(
    test("build succeeds for valid migration") {
      val builder = new MigrationBuilder[UserV1, UserV2](
        Schema[UserV1],
        Schema[UserV2],
        Vector(MigrationAction.AddField(DynamicOptic.root.field("email"), defaultStr))
      )
      val migration = builder.build
      assert(migration.size)(equalTo(1))
    },
    test("build throws for invalid migration") {
      val builder = new MigrationBuilder[UserV1, UserV2](
        Schema[UserV1],
        Schema[UserV2],
        Vector.empty
      )
      assert(
        try {
          builder.build
          "no exception"
        } catch {
          case e: MigrationValidation.ValidationError => e.getMessage
        }
      )(containsString("not accounted for"))
    }
  )

  private val buildPartialSuite = suite("buildPartial")(
    test("buildPartial succeeds even for incomplete migration") {
      val builder = new MigrationBuilder[UserV1, UserV2](
        Schema[UserV1],
        Schema[UserV2],
        Vector.empty
      )
      val migration = builder.buildPartial
      assert(migration.size)(equalTo(0))
    }
  )
}

package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
import zio.test.Gen._

/**
 * Tests for the Schema Migration System
 */
object MigrationSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] =
    suite("MigrationSpec")(
      suite("DynamicMigration")(
        test("identity migration preserves value") {
          val migration = DynamicMigration.identity
          val value = DynamicValue.Primitive(PrimitiveValue.Int(42))
          migration(value) match {
            case Right(result) =>
              assertTrue(result == value)
            case Left(error) =>
              fail(s"Migration failed: ${error.message}")
          }
        },
        test("can compose migrations") {
          val m1 = DynamicMigration(Vector(MigrationAction.Identity()))
          val m2 = DynamicMigration(Vector(MigrationAction.Identity()))
          val composed = m1 ++ m2
          val value = DynamicValue.Primitive(PrimitiveValue.Int(42))
          composed(value) match {
            case Right(result) =>
              assertTrue(result == value)
            case Left(error) =>
              fail(s"Migration failed: ${error.message}")
          }
        },
        test("reverse returns inverse migration") {
          val migration = DynamicMigration(Vector(MigrationAction.Identity()))
          val reversed = migration.reverse
          assertTrue(reversed.actions.length == migration.actions.length)
        }
      ),
      suite("Migration")(
        test("identity migration preserves typed value") {
          val migration = Migration.identity[Int]
          migration(42) match {
            case Right(result) =>
              assertTrue(result == 42)
            case Left(error) =>
              fail(s"Migration failed: ${error.message}")
          }
        }
      ),
      suite("MigrationBuilder")(
        test("can add fields") {
          val sourceSchema = Schema[PersonV1]
          val targetSchema = Schema[PersonV2]

          val migration = Migration
            .newBuilder(sourceSchema, targetSchema)
            .addField(_.age, SchemaExpr.Literal(0, Schema[Int]))
            .build

          val v1 = PersonV1("John", 30)
          migration(v1) match {
            case Right(v2) =>
              assertTrue(v2.name == "John")
            case Left(error) =>
              fail(s"Migration failed: ${error.message}")
          }
        },
        test("can drop fields") {
          val sourceSchema = Schema[PersonV2]
          val targetSchema = Schema[PersonV1]

          val migration = Migration
            .newBuilder(sourceSchema, targetSchema)
            .dropField(_.country)
            .build

          val v2 = PersonV2("John", 30, Some("USA"))
          migration(v2) match {
            case Right(v1) =>
              assertTrue(v1.name == "John" && v1.age == 30)
            case Left(error) =>
              fail(s"Migration failed: ${error.message}")
          }
        }
      ),
      suite("MigrationError")(
        test("MissingField error has correct message") {
          val error = MigrationError.MissingField(
            DynamicOptic(IndexedSeq.empty),
            "fieldName"
          )
          assertTrue(error.message.contains("Missing required field"))
        },
        test("TransformationFailed error has correct message") {
          val error = MigrationError.TransformationFailed(
            DynamicOptic(IndexedSeq.empty),
            "reason"
          )
          assertTrue(error.message.contains("Transformation failed"))
        }
      )
    )

  // Test case classes
  case class PersonV1(name: String, age: Int)
  case class PersonV2(name: String, age: Int, country: Option[String])
}

package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

object MigrationErrorSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("MigrationErrorSpec")(
    suite("Single error types")(
      test("FieldNotFound provides meaningful message") {
        val error = MigrationError.FieldNotFound(DynamicOptic.root.field("person"), "name")
        assertTrue(
          error.message.contains("name"),
          error.message.contains("not found"),
          error.path == DynamicOptic.root.field("person")
        )
      },
      test("FieldAlreadyExists provides meaningful message") {
        val error = MigrationError.FieldAlreadyExists(DynamicOptic.root.field("person"), "age")
        assertTrue(
          error.message.contains("age"),
          error.message.contains("already exists"),
          error.path == DynamicOptic.root.field("person")
        )
      },
      test("NotARecord provides meaningful message") {
        val error = MigrationError.NotARecord(DynamicOptic.root.field("data"), "Sequence")
        assertTrue(
          error.message.contains("record"),
          error.message.contains("Sequence"),
          error.path == DynamicOptic.root.field("data")
        )
      },
      test("TypeConversionFailed provides meaningful message") {
        val error = MigrationError.TypeConversionFailed(DynamicOptic.root, "String", "Int", "Invalid format")
        assertTrue(
          error.message.contains("String"),
          error.message.contains("Int"),
          error.message.contains("Invalid format")
        )
      },
      test("DefaultValueMissing provides meaningful message") {
        val error = MigrationError.DefaultValueMissing(DynamicOptic.root.field("config"), "port")
        assertTrue(
          error.message.contains("Default value"),
          error.message.contains("port")
        )
      }
    ),
    suite("MigrationError composition")(
      test("can combine multiple errors with ++") {
        val error1 = MigrationError.single(MigrationError.FieldNotFound(DynamicOptic.root, "a"))
        val error2 = MigrationError.single(MigrationError.FieldNotFound(DynamicOptic.root, "b"))
        val combined = error1 ++ error2
        assertTrue(
          combined.errors.length == 2,
          combined.message.contains("a"),
          combined.message.contains("b")
        )
      },
      test("getMessage returns formatted error message") {
        val error = MigrationError.single(MigrationError.TransformFailed(DynamicOptic.root, "Test error"))
        assertTrue(error.getMessage == error.message)
      }
    )
  )
}

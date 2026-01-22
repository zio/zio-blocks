package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic
import zio.test._

object MigrationErrorSpec extends ZIOSpecDefault {

  def spec = suite("MigrationErrorSpec")(
    suite("error messages")(
      test("should include path information") {
        val optic = DynamicOptic.root.field("address").field("street")
        val error = MigrationError.FieldNotFound(optic, "street")

        val message = error.message

        assertTrue(message.contains("address")) &&
        assertTrue(message.contains("street"))
      },
      test("should have a prettyPrint method") {
        val optic = DynamicOptic.root.field("user").field("name")
        val error = MigrationError.FieldNotFound(optic, "name")

        val pretty = error.prettyPrint

        assertTrue(pretty.nonEmpty) &&
        assertTrue(pretty.contains("user.name") || pretty.contains("user") && pretty.contains("name"))
      }
    ),
    suite("error types")(
      test("FieldNotFound should capture field name and path") {
        val optic = DynamicOptic.root.field("age")
        val error = MigrationError.FieldNotFound(optic, "age")

        assertTrue(error.path == optic) &&
        assertTrue(error.fieldName == "age")
      },
      test("FieldAlreadyExists should capture field name and path") {
        val optic = DynamicOptic.root.field("name")
        val error = MigrationError.FieldAlreadyExists(optic, "name")

        assertTrue(error.path == optic) &&
        assertTrue(error.fieldName == "name")
      },
      test("InvalidStructure should capture expected and actual types") {
        val optic = DynamicOptic.root
        val error = MigrationError.InvalidStructure(optic, "Record", "Primitive")

        assertTrue(error.path == optic) &&
        assertTrue(error.expected == "Record") &&
        assertTrue(error.actual == "Primitive")
      },
      test("EvaluationError should capture SchemaExpr error") {
        val optic = DynamicOptic.root.field("computed")
        val error = MigrationError.EvaluationError(optic, "Division by zero")

        assertTrue(error.path == optic) &&
        assertTrue(error.message.contains("Division by zero"))
      }
    )
  )
}

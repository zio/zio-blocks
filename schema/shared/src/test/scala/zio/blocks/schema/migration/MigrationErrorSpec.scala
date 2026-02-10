package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, SchemaError}
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
        assertTrue(error.fieldName == "age") &&
        assertTrue(error.message.contains("'age'")) &&
        assertTrue(error.message.contains("not found"))
      },
      test("FieldAlreadyExists should capture field name and path") {
        val optic = DynamicOptic.root.field("name")
        val error = MigrationError.FieldAlreadyExists(optic, "name")

        assertTrue(error.path == optic) &&
        assertTrue(error.fieldName == "name") &&
        assertTrue(error.message.contains("'name'")) &&
        assertTrue(error.message.contains("already exists"))
      },
      test("InvalidStructure should capture expected and actual types") {
        val optic = DynamicOptic.root
        val error = MigrationError.InvalidStructure(optic, "Record", "Primitive")

        assertTrue(error.path == optic) &&
        assertTrue(error.expected == "Record") &&
        assertTrue(error.actual == "Primitive") &&
        assertTrue(error.message.contains("Expected Record")) &&
        assertTrue(error.message.contains("found Primitive"))
      },
      test("EvaluationError should capture DynamicSchemaExpr error") {
        val optic = DynamicOptic.root.field("computed")
        val error = MigrationError.EvaluationError(optic, "Division by zero")

        assertTrue(error.path == optic) &&
        assertTrue(error.message.contains("Division by zero"))
      },
      test("FromDynamicValueFailed should wrap SchemaError") {
        val optic       = DynamicOptic.root.field("data")
        val schemaError = SchemaError.missingField(Nil, "requiredField")
        val error       = MigrationError.FromDynamicValueFailed(optic, schemaError)

        assertTrue(error.path == optic) &&
        assertTrue(error.schemaError == schemaError) &&
        assertTrue(error.message.contains("Failed to convert")) &&
        assertTrue(error.message.contains("requiredField"))
      },
      test("IntermediateFieldNotFound should capture field name, depth, and path") {
        val optic = DynamicOptic.root.field("address").field("city").field("name")
        val error = MigrationError.IntermediateFieldNotFound(optic, "city", 1)

        assertTrue(error.path == optic) &&
        assertTrue(error.fieldName == "city") &&
        assertTrue(error.depth == 1) &&
        assertTrue(error.message.contains("Intermediate field")) &&
        assertTrue(error.message.contains("'city'")) &&
        assertTrue(error.message.contains("depth 1"))
      },
      test("IntermediateFieldNotRecord should capture field name, depth, actualType, and path") {
        val optic = DynamicOptic.root.field("data").field("nested")
        val error = MigrationError.IntermediateFieldNotRecord(optic, "data", 0, "Primitive")

        assertTrue(error.path == optic) &&
        assertTrue(error.fieldName == "data") &&
        assertTrue(error.depth == 0) &&
        assertTrue(error.actualType == "Primitive") &&
        assertTrue(error.message.contains("'data'")) &&
        assertTrue(error.message.contains("not a Record")) &&
        assertTrue(error.message.contains("Primitive"))
      },
      test("CrossPathJoinNotSupported should capture target and source paths") {
        val targetPath  = DynamicOptic.root.field("address").field("fullAddress")
        val sourcePath2 = DynamicOptic.root.field("contact").field("city") // different parent
        val error       = MigrationError.CrossPathJoinNotSupported(targetPath, Vector(sourcePath2))

        assertTrue(error.path == targetPath) &&
        assertTrue(error.invalidPaths == Vector(sourcePath2)) &&
        assertTrue(error.message.contains("Join operation")) &&
        assertTrue(error.message.contains("same parent"))
      },
      test("CrossPathSplitNotSupported should capture source and target paths") {
        val sourcePath  = DynamicOptic.root.field("address").field("fullAddress")
        val targetPath2 = DynamicOptic.root.field("contact").field("city") // different parent
        val error       = MigrationError.CrossPathSplitNotSupported(sourcePath, Vector(targetPath2))

        assertTrue(error.path == sourcePath) &&
        assertTrue(error.invalidPaths == Vector(targetPath2)) &&
        assertTrue(error.message.contains("Split operation")) &&
        assertTrue(error.message.contains("same parent"))
      },
      test("IrreversibleOperation should capture path and reason") {
        val optic  = DynamicOptic.root.field("fullName")
        val reason = "Cannot reverse Join: unsupported combiner expression type."
        val error  = MigrationError.IrreversibleOperation(optic, reason)

        assertTrue(error.path == optic) &&
        assertTrue(error.reason == reason) &&
        assertTrue(error.message.contains("Cannot reverse operation")) &&
        assertTrue(error.message.contains("fullName")) &&
        assertTrue(error.message.contains("unsupported combiner"))
      }
    )
  )
}

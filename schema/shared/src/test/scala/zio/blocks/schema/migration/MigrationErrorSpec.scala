package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic
import zio.test._

object MigrationErrorSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("MigrationErrorSpec")(
    suite("PathNotFound")(
      test("message lists available paths") {
        val error = MigrationError.PathNotFound(
          DynamicOptic.root.field("missing"),
          Set("name", "age", "email")
        )
        assertTrue(
          error.message.contains("Available") &&
            error.message.contains("name") &&
            error.message.contains("age")
        )
      },
      test("render includes path and message") {
        val error = MigrationError.PathNotFound(
          DynamicOptic.root.field("missing"),
          Set("name")
        )
        val rendered = error.render
        assertTrue(rendered.contains("missing") && rendered.contains("name"))
      }
    ),
    suite("TypeMismatch")(
      test("message shows expected and actual types") {
        val error = MigrationError.TypeMismatch(
          DynamicOptic.root.field("age"),
          "Int",
          "String"
        )
        assertTrue(error.message.contains("Int") && error.message.contains("String"))
      },
      test("render includes path") {
        val error = MigrationError.TypeMismatch(
          DynamicOptic.root.field("age"),
          "Int",
          "String"
        )
        assertTrue(error.render.contains("age"))
      }
    ),
    suite("TransformFailed")(
      test("message includes cause") {
        val error = MigrationError.TransformFailed(
          DynamicOptic.root,
          "value out of range"
        )
        assertTrue(error.message.contains("value out of range"))
      },
      test("render shows transform failure") {
        val error = MigrationError.TransformFailed(
          DynamicOptic.root.field("count"),
          "negative value"
        )
        assertTrue(error.render.contains("Transform failed"))
      }
    ),
    suite("ValidationFailed")(
      test("message includes reason") {
        val error = MigrationError.ValidationFailed(
          DynamicOptic.root,
          "schema mismatch"
        )
        assertTrue(error.message.contains("schema mismatch"))
      },
      test("render shows validation failure") {
        val error = MigrationError.ValidationFailed(
          DynamicOptic.root,
          "invalid structure"
        )
        assertTrue(error.render.contains("Validation failed"))
      }
    ),
    suite("CaseNotFound")(
      test("message lists case name and available cases") {
        val error = MigrationError.CaseNotFound(
          DynamicOptic.root,
          "Unknown",
          Set("Active", "Inactive", "Pending")
        )
        assertTrue(
          error.message.contains("Unknown") &&
            error.message.contains("Active") &&
            error.message.contains("Inactive")
        )
      },
      test("render includes path") {
        val error = MigrationError.CaseNotFound(
          DynamicOptic.root.caseOf("status"),
          "Missing",
          Set("Present")
        )
        assertTrue(error.render.contains("status"))
      }
    ),
    suite("NoDefaultValue")(
      test("message includes type name") {
        val error = MigrationError.NoDefaultValue(
          DynamicOptic.root.field("complex"),
          "ComplexType"
        )
        assertTrue(error.message.contains("ComplexType"))
      },
      test("render shows no default value message") {
        val error = MigrationError.NoDefaultValue(
          DynamicOptic.root,
          "SomeType"
        )
        assertTrue(error.render.contains("No default value"))
      }
    ),
    suite("Error path navigation")(
      test("PathNotFound with nested path") {
        val error = MigrationError.PathNotFound(
          DynamicOptic.root.field("person").field("address").field("city"),
          Set("street", "zip", "country")
        )
        assertTrue(error.path.nodes.size == 3)
      },
      test("TypeMismatch with array element path") {
        val error = MigrationError.TypeMismatch(
          DynamicOptic.root.field("items").at(0),
          "Int",
          "String"
        )
        assertTrue(error.path.nodes.size == 2)
      },
      test("TransformFailed at deep path") {
        val error = MigrationError.TransformFailed(
          DynamicOptic.root.field("data").field("nested").field("value"),
          "conversion error"
        )
        assertTrue(error.render.contains("data") || error.render.contains("Transform failed"))
      },
      test("CaseNotFound with many available cases") {
        val error = MigrationError.CaseNotFound(
          DynamicOptic.root,
          "NonExistent",
          Set("Case1", "Case2", "Case3", "Case4", "Case5", "Case6", "Case7", "Case8", "Case9", "Case10")
        )
        assertTrue(error.available.size == 10)
      },
      test("NoDefaultValue with complex type path") {
        val error = MigrationError.NoDefaultValue(
          DynamicOptic.root.field("config").field("settings"),
          "Map[String, List[Config]]"
        )
        assertTrue(error.typeName.contains("Map"))
      }
    ),
    suite("Error equality and properties")(
      test("PathNotFound path property") {
        val path  = DynamicOptic.root.field("test")
        val error = MigrationError.PathNotFound(path, Set("a"))
        assertTrue(error.path == path)
      },
      test("TypeMismatch properties") {
        val error = MigrationError.TypeMismatch(DynamicOptic.root, "Int", "String")
        assertTrue(error.expected == "Int" && error.actual == "String")
      },
      test("TransformFailed cause property") {
        val error = MigrationError.TransformFailed(DynamicOptic.root, "the cause")
        assertTrue(error.cause == "the cause")
      },
      test("ValidationFailed reason property") {
        val error = MigrationError.ValidationFailed(DynamicOptic.root, "the reason")
        assertTrue(error.reason == "the reason")
      },
      test("CaseNotFound caseName property") {
        val error = MigrationError.CaseNotFound(DynamicOptic.root, "TestCase", Set())
        assertTrue(error.caseName == "TestCase")
      }
    )
  )
}

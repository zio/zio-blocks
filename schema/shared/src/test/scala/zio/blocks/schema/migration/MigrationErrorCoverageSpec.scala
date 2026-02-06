package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationErrorCoverageSpec extends SchemaBaseSpec {

  private val root = DynamicOptic.root
  private val path = root.field("x")

  def spec: Spec[TestEnvironment, Any] = suite("MigrationErrorCoverageSpec")(
    suite("MigrationError combining")(
      test("++ combines two errors") {
        val e1       = MigrationError.single(MigrationError.FieldNotFound(root, "a"))
        val e2       = MigrationError.single(MigrationError.FieldNotFound(root, "b"))
        val combined = e1 ++ e2
        assertTrue(combined.errors.length == 2)
      },
      test("message joins all error messages") {
        val e1       = MigrationError.single(MigrationError.FieldNotFound(root, "a"))
        val e2       = MigrationError.single(MigrationError.FieldNotFound(root, "b"))
        val combined = e1 ++ e2
        assertTrue(combined.message.contains("a") && combined.message.contains("b"))
      },
      test("getMessage returns message") {
        val e = MigrationError.single(MigrationError.FieldNotFound(root, "x"))
        assertTrue(e.getMessage == e.message)
      },
      test("single creates error with one entry") {
        val e = MigrationError.single(MigrationError.FieldNotFound(root, "x"))
        assertTrue(e.errors.length == 1)
      }
    ),
    suite("FieldNotFound")(
      test("message format") {
        val e = MigrationError.FieldNotFound(path, "name")
        assertTrue(e.message.contains("name") && e.message.contains("not found"))
      },
      test("path is preserved") {
        val e = MigrationError.FieldNotFound(path, "name")
        assertTrue(e.path == path && e.fieldName == "name")
      }
    ),
    suite("FieldAlreadyExists")(
      test("message format") {
        val e = MigrationError.FieldAlreadyExists(path, "name")
        assertTrue(e.message.contains("name") && e.message.contains("already exists"))
      }
    ),
    suite("NotARecord")(
      test("message format") {
        val e = MigrationError.NotARecord(path, "Sequence")
        assertTrue(e.message.contains("record") && e.message.contains("Sequence"))
      }
    ),
    suite("NotAVariant")(
      test("message format") {
        val e = MigrationError.NotAVariant(path, "Record")
        assertTrue(e.message.contains("variant") && e.message.contains("Record"))
      }
    ),
    suite("NotASequence")(
      test("message format") {
        val e = MigrationError.NotASequence(path, "Map")
        assertTrue(e.message.contains("sequence") && e.message.contains("Map"))
      }
    ),
    suite("NotAMap")(
      test("message format") {
        val e = MigrationError.NotAMap(path, "Primitive")
        assertTrue(e.message.contains("map") && e.message.contains("Primitive"))
      }
    ),
    suite("OptionalMismatch")(
      test("message format") {
        val e = MigrationError.OptionalMismatch(path, "expected optional")
        assertTrue(e.message == "expected optional")
      }
    ),
    suite("CaseNotFound")(
      test("message format") {
        val e = MigrationError.CaseNotFound(path, "Dog")
        assertTrue(e.message.contains("Dog") && e.message.contains("not found"))
      }
    ),
    suite("TypeConversionFailed")(
      test("message format") {
        val e = MigrationError.TypeConversionFailed(path, "Int", "String", "not possible")
        assertTrue(e.message.contains("Int") && e.message.contains("String") && e.message.contains("not possible"))
      }
    ),
    suite("TransformFailed")(
      test("message format") {
        val e = MigrationError.TransformFailed(path, "bad expression")
        assertTrue(e.message.contains("Transform failed") && e.message.contains("bad expression"))
      }
    ),
    suite("PathNavigationFailed")(
      test("message format") {
        val e = MigrationError.PathNavigationFailed(path, "not found")
        assertTrue(e.message.contains("navigate") && e.message.contains("not found"))
      }
    ),
    suite("DefaultValueMissing")(
      test("message format") {
        val e = MigrationError.DefaultValueMissing(path, "email")
        assertTrue(e.message.contains("email") && e.message.contains("Default value"))
      }
    ),
    suite("IndexOutOfBounds")(
      test("message format") {
        val e = MigrationError.IndexOutOfBounds(path, 5, 3)
        assertTrue(e.message.contains("5") && e.message.contains("3"))
      }
    ),
    suite("KeyNotFound")(
      test("message format") {
        val e = MigrationError.KeyNotFound(path, "myKey")
        assertTrue(e.message.contains("myKey") && e.message.contains("not found"))
      }
    ),
    suite("NumericOverflow")(
      test("message format") {
        val e = MigrationError.NumericOverflow(path, "multiply")
        assertTrue(e.message.contains("overflow") && e.message.contains("multiply"))
      }
    ),
    suite("ActionFailed")(
      test("message format") {
        val e = MigrationError.ActionFailed(path, "Split", "wrong count")
        assertTrue(e.message.contains("Split") && e.message.contains("wrong count"))
      }
    )
  )
}

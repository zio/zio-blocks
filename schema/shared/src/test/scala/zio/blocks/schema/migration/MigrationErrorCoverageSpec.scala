package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Supplementary coverage tests for [[MigrationError]].
 *
 * Covers error type message formats and accessors not exercised by
 * [[MigrationErrorSpec]], which already covers FieldNotFound,
 * FieldAlreadyExists, NotARecord, TypeConversionFailed, DefaultValueMissing,
 * ++, and getMessage.
 */
object MigrationErrorCoverageSpec extends SchemaBaseSpec {

  private val root = DynamicOptic.root
  private val path = root.field("x")

  def spec: Spec[TestEnvironment, Any] = suite("MigrationErrorCoverageSpec")(
    test("single creates error with one entry") {
      val e = MigrationError.single(MigrationError.FieldNotFound(root, "x"))
      assertTrue(e.errors.length == 1)
    },
    test("FieldNotFound preserves path accessor") {
      val e = MigrationError.FieldNotFound(path, "name")
      assertTrue(e.path == path && e.fieldName == "name")
    },
    suite("error type message formats")(
      test("NotAVariant message format") {
        val e = MigrationError.NotAVariant(path, "Record")
        assertTrue(e.message.contains("variant") && e.message.contains("Record"))
      },
      test("NotASequence message format") {
        val e = MigrationError.NotASequence(path, "Map")
        assertTrue(e.message.contains("sequence") && e.message.contains("Map"))
      },
      test("NotAMap message format") {
        val e = MigrationError.NotAMap(path, "Primitive")
        assertTrue(e.message.contains("map") && e.message.contains("Primitive"))
      },
      test("OptionalMismatch message format") {
        val e = MigrationError.OptionalMismatch(path, "expected optional")
        assertTrue(e.message == "expected optional")
      },
      test("CaseNotFound message format") {
        val e = MigrationError.CaseNotFound(path, "Dog")
        assertTrue(e.message.contains("Dog") && e.message.contains("not found"))
      },
      test("TransformFailed message format") {
        val e = MigrationError.TransformFailed(path, "bad expression")
        assertTrue(e.message.contains("Transform failed") && e.message.contains("bad expression"))
      },
      test("PathNavigationFailed message format") {
        val e = MigrationError.PathNavigationFailed(path, "not found")
        assertTrue(e.message.contains("navigate") && e.message.contains("not found"))
      },
      test("IndexOutOfBounds message format") {
        val e = MigrationError.IndexOutOfBounds(path, 5, 3)
        assertTrue(e.message.contains("5") && e.message.contains("3"))
      },
      test("KeyNotFound message format") {
        val e = MigrationError.KeyNotFound(path, "myKey")
        assertTrue(e.message.contains("myKey") && e.message.contains("not found"))
      },
      test("NumericOverflow message format") {
        val e = MigrationError.NumericOverflow(path, "multiply")
        assertTrue(e.message.contains("overflow") && e.message.contains("multiply"))
      },
      test("ActionFailed message format") {
        val e = MigrationError.ActionFailed(path, "Split", "wrong count")
        assertTrue(e.message.contains("Split") && e.message.contains("wrong count"))
      }
    )
  )
}

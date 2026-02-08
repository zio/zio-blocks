package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationBuildValidationSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test Data Types
  // ─────────────────────────────────────────────────────────────────────────

  case class PersonV0(name: String, age: Int)
  object PersonV0 {
    implicit val schema: Schema[PersonV0] = Schema.derived
  }

  case class PersonV1(name: String, age: Int, email: String)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  case class SimpleRecord(x: Int, y: String)
  object SimpleRecord {
    implicit val schema: Schema[SimpleRecord] = Schema.derived
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Helpers
  // ─────────────────────────────────────────────────────────────────────────

  private def intDV(i: Int): DynamicValue       = DynamicValue.Primitive(PrimitiveValue.Int(i))
  private def stringDV(s: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def litInt(i: Int): MigrationExpr     = MigrationExpr.Literal(intDV(i))
  private def litStr(s: String): MigrationExpr  = MigrationExpr.Literal(stringDV(s))

  def spec: Spec[TestEnvironment, Any] = suite("MigrationBuildValidationSpec")(
    validMigrationSuite,
    duplicateAddFieldSuite,
    renameConflictSuite,
    multipleErrorsSuite,
    dropThenAddSuite,
    emptyBuilderSuite,
    buildPartialBypassesSuite
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Valid migration passes
  // ─────────────────────────────────────────────────────────────────────────

  private val validMigrationSuite = suite("valid migration passes build")(
    test("a correct builder produces Right(migration)") {
      val result = MigrationBuilder
        .create(PersonV0.schema, PersonV1.schema)
        .addField("email", litStr("default@example.com"))
        .build
      assertTrue(result.isRight)
    },
    test("valid migration result is usable") {
      val result = MigrationBuilder
        .create(PersonV0.schema, PersonV1.schema)
        .addField("email", litStr("test@test.com"))
        .build
      assertTrue(result.isRight) && {
        val migration = result.toOption.get
        val applied   = migration(PersonV0("Alice", 30))
        assertTrue(applied == Right(PersonV1("Alice", 30, "test@test.com")))
      }
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Duplicate AddField detected
  // ─────────────────────────────────────────────────────────────────────────

  private val duplicateAddFieldSuite = suite("duplicate AddField detected")(
    test("adding the same field twice at root produces an error") {
      val result = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .addField("extra", litInt(1))
        .addField("extra", litInt(2))
        .build
      assertTrue(result.isLeft) && {
        val errors = result.swap.toOption.get
        assertTrue(errors.size == 1) && {
          errors(0) match {
            case MigrationError.FieldAlreadyExists(path, fieldName) =>
              assertTrue(path == DynamicOptic.root, fieldName == "extra")
            case _ => assertTrue(false)
          }
        }
      }
    },
    test("adding the same field twice at a nested path produces an error") {
      val nestedPath = DynamicOptic.root.field("address")
      val result     = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .addFieldAt(nestedPath, "zip", litStr("00000"))
        .addFieldAt(nestedPath, "zip", litStr("99999"))
        .build
      assertTrue(result.isLeft) && {
        val errors = result.swap.toOption.get
        assertTrue(errors.size == 1) && {
          errors(0) match {
            case MigrationError.FieldAlreadyExists(path, fieldName) =>
              assertTrue(path == nestedPath, fieldName == "zip")
            case _ => assertTrue(false)
          }
        }
      }
    },
    test("adding different fields at the same path is valid") {
      val result = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .addField("alpha", litInt(1))
        .addField("beta", litInt(2))
        .build
      assertTrue(result.isRight)
    },
    test("adding the same field at different paths is valid") {
      val pathA  = DynamicOptic.root.field("a")
      val pathB  = DynamicOptic.root.field("b")
      val result = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .addFieldAt(pathA, "extra", litInt(1))
        .addFieldAt(pathB, "extra", litInt(2))
        .build
      assertTrue(result.isRight)
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Rename conflict detected
  // ─────────────────────────────────────────────────────────────────────────

  private val renameConflictSuite = suite("rename conflict detected")(
    test("two renames from the same source field at the same path produces an error") {
      val result = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .renameField("name", "fullName")
        .renameField("name", "displayName")
        .build
      assertTrue(result.isLeft) && {
        val errors = result.swap.toOption.get
        assertTrue(errors.size == 1) && {
          errors(0) match {
            case MigrationError.CustomError(path, reason) =>
              assertTrue(
                path == DynamicOptic.root,
                reason.contains("name"),
                reason.contains("renamed more than once")
              )
            case _ => assertTrue(false)
          }
        }
      }
    },
    test("two renames from different source fields at the same path is valid") {
      val result = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .renameField("first", "firstName")
        .renameField("last", "lastName")
        .build
      assertTrue(result.isRight)
    },
    test("same-source rename at different paths is valid") {
      val pathA  = DynamicOptic.root.field("a")
      val pathB  = DynamicOptic.root.field("b")
      val result = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .renameFieldAt(pathA, "name", "fullName")
        .renameFieldAt(pathB, "name", "fullName")
        .build
      assertTrue(result.isRight)
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Multiple errors accumulated
  // ─────────────────────────────────────────────────────────────────────────

  private val multipleErrorsSuite = suite("multiple errors accumulated")(
    test("two different validation errors in one builder are both reported") {
      val result = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .addField("dup", litInt(1))
        .addField("dup", litInt(2))
        .renameField("src", "dst1")
        .renameField("src", "dst2")
        .build
      assertTrue(result.isLeft) && {
        val errors = result.swap.toOption.get
        assertTrue(errors.size == 2) && {
          val hasFieldAlreadyExists = errors.exists {
            case _: MigrationError.FieldAlreadyExists => true
            case _                                    => false
          }
          val hasCustomError = errors.exists {
            case _: MigrationError.CustomError => true
            case _                             => false
          }
          assertTrue(hasFieldAlreadyExists, hasCustomError)
        }
      }
    },
    test("three duplicate additions report all three errors") {
      val result = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .addField("f1", litInt(1))
        .addField("f1", litInt(2))
        .addField("f2", litStr("a"))
        .addField("f2", litStr("b"))
        .addField("f3", litInt(3))
        .addField("f3", litInt(4))
        .build
      assertTrue(result.isLeft) && {
        val errors = result.swap.toOption.get
        assertTrue(errors.size == 3)
      }
    },
    test("errors are reported in action order") {
      val result = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .addField("aaa", litInt(1))
        .addField("zzz", litInt(2))
        .addField("zzz", litInt(3))
        .addField("aaa", litInt(4))
        .build
      assertTrue(result.isLeft) && {
        val errors = result.swap.toOption.get
        assertTrue(errors.size == 2) && {
          // "zzz" duplicate is encountered first (index 2), then "aaa" (index 3)
          val first = errors(0) match {
            case MigrationError.FieldAlreadyExists(_, fieldName) => fieldName
            case _                                               => ""
          }
          val second = errors(1) match {
            case MigrationError.FieldAlreadyExists(_, fieldName) => fieldName
            case _                                               => ""
          }
          assertTrue(first == "zzz", second == "aaa")
        }
      }
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Drop-then-add is valid
  // ─────────────────────────────────────────────────────────────────────────

  private val dropThenAddSuite = suite("drop-then-add is valid")(
    test("dropping a field then adding it back is NOT an error") {
      val result = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .addField("temp", litInt(1))
        .dropField("temp", litInt(1))
        .addField("temp", litInt(2))
        .build
      assertTrue(result.isRight)
    },
    test("dropping a field then adding it back at nested path is valid") {
      val nested = DynamicOptic.root.field("inner")
      val result = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .addFieldAt(nested, "extra", litStr("a"))
        .dropFieldAt(nested, "extra", litStr("a"))
        .addFieldAt(nested, "extra", litStr("b"))
        .build
      assertTrue(result.isRight)
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Empty builder validates
  // ─────────────────────────────────────────────────────────────────────────

  private val emptyBuilderSuite = suite("empty builder validates")(
    test("empty builder produces valid empty migration") {
      val result = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .build
      assertTrue(result.isRight) && {
        val migration = result.toOption.get
        assertTrue(migration.isEmpty)
      }
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // buildPartial bypasses validation
  // ─────────────────────────────────────────────────────────────────────────

  private val buildPartialBypassesSuite = suite("buildPartial bypasses validation")(
    test("same invalid builder succeeds with buildPartial") {
      val builder = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .addField("dup", litInt(1))
        .addField("dup", litInt(2))

      // build detects the error
      val buildResult = builder.build
      assertTrue(buildResult.isLeft) && {
        // buildPartial skips validation and succeeds
        val migration = builder.buildPartial
        assertTrue(!migration.isEmpty)
      }
    }
  )
}

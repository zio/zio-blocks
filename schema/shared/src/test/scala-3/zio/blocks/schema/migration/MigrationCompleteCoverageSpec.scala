package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for compile-time field tracking via [[TrackedMigrationBuilder]] and
 * [[MigrationComplete]].
 *
 * These tests verify that:
 *   - Complete migrations compile and produce correct results
 *   - The tracked builder properly delegates to the underlying builder
 *   - Non-tracked methods (transformField, etc.) preserve type state
 *   - `buildPartial` works without completeness evidence
 *   - `buildChecked` skips runtime validation but enforces compile-time checks
 *   - The `tracked` extension and `checkedBuilder` factory work
 *   - Tracked and untracked builders produce identical migrations
 */

// Case classes defined at top level for Schema derivation compatibility.

final case class CTSrcDrop(name: String, age: Int, email: String)
object CTSrcDrop { given Schema[CTSrcDrop] = Schema.derived }

final case class CTTgtDrop(name: String, age: Int, country: String)
object CTTgtDrop { given Schema[CTTgtDrop] = Schema.derived }

final case class CTSrcRename(firstName: String, lastName: String, age: Int)
object CTSrcRename { given Schema[CTSrcRename] = Schema.derived }

final case class CTTgtRename(fullName: String, age: Int)
object CTTgtRename { given Schema[CTTgtRename] = Schema.derived }

final case class CTSrcSame(name: String, age: Int)
object CTSrcSame { given Schema[CTSrcSame] = Schema.derived }

final case class CTTgtSame(name: String, age: Int)
object CTTgtSame { given Schema[CTTgtSame] = Schema.derived }

final case class CTSrcExtra(name: String, age: Int, score: Double)
object CTSrcExtra { given Schema[CTSrcExtra] = Schema.derived }

final case class CTTgtExtra(name: String, age: Int, score: Double, rank: Int)
object CTTgtExtra { given Schema[CTTgtExtra] = Schema.derived }

final case class CTSrcMulti(name: String, middleName: String, age: Int)
object CTSrcMulti { given Schema[CTSrcMulti] = Schema.derived }

final case class CTTgtMulti(name: String, age: Int, nickname: String)
object CTTgtMulti { given Schema[CTTgtMulti] = Schema.derived }

final case class CTSrcTransform(name: String, value: Int)
object CTSrcTransform { given Schema[CTSrcTransform] = Schema.derived }

final case class CTTgtTransform(name: String, value: Int)
object CTTgtTransform { given Schema[CTTgtTransform] = Schema.derived }

final case class CTSrcRename2(name: String, age: Int, city: String)
object CTSrcRename2 { given Schema[CTSrcRename2] = Schema.derived }

final case class CTTgtRename2(fullName: String, years: Int, city: String)
object CTTgtRename2 { given Schema[CTTgtRename2] = Schema.derived }

object MigrationCompleteCoverageSpec extends ZIOSpecDefault {
  import MigrationBuilderSyntax._

  def spec: Spec[TestEnvironment, Any] = suite("MigrationComplete")(
    completeMigrationsSuite,
    trackedExtensionSuite,
    nonTrackedMethodsSuite,
    buildPartialSuite,
    buildCheckedSuite,
    endToEndSuite
  )

  private def completeMigrationsSuite = suite("complete migrations compile and work")(
    test("addField + dropField covers all non-auto-mapped fields") {
      val migration = MigrationBuilderSyntax
        .checkedBuilder[CTSrcDrop, CTTgtDrop]
        .dropField(_.email)
        .addField(_.country, "US")
        .build

      val input  = Schema[CTSrcDrop].toDynamicValue(CTSrcDrop("Alice", 30, "alice@example.com"))
      val result = migration.applyDynamic(input)
      assertTrue(result.isRight)
    },
    test("renameField covers both source and target") {
      val migration = MigrationBuilderSyntax
        .checkedBuilder[CTSrcRename, CTTgtRename]
        .renameField(_.firstName, _.fullName)
        .dropField(_.lastName)
        .build

      assertTrue(migration.dynamicMigration.actions.nonEmpty)
    },
    test("identical schemas need no explicit field handling") {
      val migration = MigrationBuilderSyntax
        .checkedBuilder[CTSrcSame, CTTgtSame]
        .build

      val input  = Schema[CTSrcSame].toDynamicValue(CTSrcSame("Alice", 30))
      val result = migration.applyDynamic(input)
      assertTrue(result.isRight)
    },
    test("addField only when target has extra fields") {
      val migration = MigrationBuilderSyntax
        .checkedBuilder[CTSrcExtra, CTTgtExtra]
        .addField(_.rank, 0)
        .build

      assertTrue(migration.dynamicMigration.actions.size == 1)
    },
    test("combined drop + add for multiple field changes") {
      val migration = MigrationBuilderSyntax
        .checkedBuilder[CTSrcMulti, CTTgtMulti]
        .dropField(_.middleName)
        .addField(_.nickname, "")
        .build

      assertTrue(migration.dynamicMigration.actions.size == 2)
    },
    test("multiple renames with auto-mapped common field") {
      val migration = MigrationBuilderSyntax
        .checkedBuilder[CTSrcRename2, CTTgtRename2]
        .renameField(_.name, _.fullName)
        .renameField(_.age, _.years)
        .build

      assertTrue(migration.dynamicMigration.actions.size == 2)
    }
  )

  private def trackedExtensionSuite = suite("tracked extension method")(
    test("builder.tracked converts untracked to tracked builder") {
      val migration = Migration
        .newBuilder[CTSrcDrop, CTTgtDrop]
        .tracked
        .dropField(_.email)
        .addField(_.country, "US")
        .build

      assertTrue(migration.dynamicMigration.actions.size == 2)
    },
    test("checkedBuilder produces equivalent result to .tracked") {
      val viaChecked = MigrationBuilderSyntax
        .checkedBuilder[CTSrcDrop, CTTgtDrop]
        .dropField(_.email)
        .addField(_.country, "US")
        .buildPartial

      val viaTracked = Migration
        .newBuilder[CTSrcDrop, CTTgtDrop]
        .tracked
        .dropField(_.email)
        .addField(_.country, "US")
        .buildPartial

      val input   = Schema[CTSrcDrop].toDynamicValue(CTSrcDrop("Bob", 25, "bob@test.com"))
      val result1 = viaChecked.applyDynamic(input)
      val result2 = viaTracked.applyDynamic(input)
      assertTrue(result1 == result2)
    }
  )

  private def nonTrackedMethodsSuite = suite("non-tracked methods preserve type state")(
    test("transformField works without affecting completeness") {
      val twoVal    = Schema[Int].toDynamicValue(2)
      val migration = MigrationBuilderSyntax
        .checkedBuilder[CTSrcTransform, CTTgtTransform]
        .transformField(
          _.value,
          DynamicSchemaExpr.Arithmetic(
            DynamicSchemaExpr.Path(DynamicOptic.root.field("value")),
            DynamicSchemaExpr.Literal(twoVal),
            DynamicSchemaExpr.ArithmeticOperator.Multiply
          )
        )
        .build

      assertTrue(migration.dynamicMigration.actions.size == 1)
    },
    test("transformField with reverse expression") {
      val twoVal    = Schema[Int].toDynamicValue(2)
      val migration = MigrationBuilderSyntax
        .checkedBuilder[CTSrcTransform, CTTgtTransform]
        .transformField(
          _.value,
          DynamicSchemaExpr.Arithmetic(
            DynamicSchemaExpr.Path(DynamicOptic.root.field("value")),
            DynamicSchemaExpr.Literal(twoVal),
            DynamicSchemaExpr.ArithmeticOperator.Multiply
          ),
          DynamicSchemaExpr.Arithmetic(
            DynamicSchemaExpr.Path(DynamicOptic.root.field("value")),
            DynamicSchemaExpr.Literal(twoVal),
            DynamicSchemaExpr.ArithmeticOperator.Subtract
          )
        )
        .build

      assertTrue(migration.dynamicMigration.actions.size == 1)
    },
    test("renameCase preserves field tracking state") {
      // renameCase targets enum types; use buildPartial to skip runtime
      // structural validation (which rightfully rejects enum ops on records)
      val migration = MigrationBuilderSyntax
        .checkedBuilder[CTSrcSame, CTTgtSame]
        .renameCase("OldCase", "NewCase")
        .buildPartial

      assertTrue(migration.dynamicMigration.actions.size == 1)
    },
    test("changeFieldType preserves field tracking state") {
      val migration = MigrationBuilderSyntax
        .checkedBuilder[CTSrcTransform, CTTgtTransform]
        .changeFieldType(
          _.value,
          DynamicSchemaExpr.CoercePrimitive(
            DynamicSchemaExpr.Path(DynamicOptic.root.field("value")),
            "Long"
          )
        )
        .buildPartial

      assertTrue(migration.dynamicMigration.actions.size == 1)
    }
  )

  private def buildPartialSuite = suite("buildPartial skips validation")(
    test("buildPartial works with incomplete migration") {
      val migration = MigrationBuilderSyntax
        .checkedBuilder[CTSrcDrop, CTTgtDrop]
        .buildPartial

      assertTrue(migration.dynamicMigration.actions.isEmpty)
    },
    test("buildPartial works with partial field coverage") {
      val migration = MigrationBuilderSyntax
        .checkedBuilder[CTSrcDrop, CTTgtDrop]
        .dropField(_.email)
        .buildPartial

      assertTrue(migration.dynamicMigration.actions.size == 1)
    }
  )

  private def buildCheckedSuite = suite("buildChecked (compile-time only)")(
    test("buildChecked skips runtime MigrationValidator") {
      val migration = MigrationBuilderSyntax
        .checkedBuilder[CTSrcDrop, CTTgtDrop]
        .dropField(_.email)
        .addField(_.country, "US")
        .buildChecked

      assertTrue(migration.dynamicMigration.actions.size == 2)
    }
  )

  private def endToEndSuite = suite("end-to-end correctness")(
    test("tracked and untracked builders produce identical migrations") {
      val untrackedMigration = Migration
        .newBuilder[CTSrcDrop, CTTgtDrop]
        .dropField(DynamicOptic.root.field("email"))
        .addField[String](DynamicOptic.root.field("country"), "US")
        .buildPartial

      val trackedMigration = MigrationBuilderSyntax
        .checkedBuilder[CTSrcDrop, CTTgtDrop]
        .dropField(_.email)
        .addField(_.country, "US")
        .buildPartial

      val input           = Schema[CTSrcDrop].toDynamicValue(CTSrcDrop("Alice", 30, "alice@test.com"))
      val untrackedResult = untrackedMigration.applyDynamic(input)
      val trackedResult   = trackedMigration.applyDynamic(input)

      assertTrue(
        untrackedResult.isRight,
        trackedResult.isRight,
        untrackedResult == trackedResult
      )
    },
    test("addFieldExpr works with expression defaults") {
      val usVal     = Schema[String].toDynamicValue("US")
      val migration = MigrationBuilderSyntax
        .checkedBuilder[CTSrcDrop, CTTgtDrop]
        .dropField(_.email)
        .addFieldExpr(_.country, DynamicSchemaExpr.Literal(usVal))
        .build

      val input  = Schema[CTSrcDrop].toDynamicValue(CTSrcDrop("Bob", 25, "bob@test.com"))
      val result = migration.applyDynamic(input)
      assertTrue(result.isRight)
    },
    test("dropField with reverse default preserves reversibility") {
      val defaultEmail = Schema[String].toDynamicValue("default@test.com")
      val migration    = MigrationBuilderSyntax
        .checkedBuilder[CTSrcDrop, CTTgtDrop]
        .dropField(_.email, DynamicSchemaExpr.Literal(defaultEmail))
        .addField(_.country, "US")
        .build

      assertTrue(migration.dynamicMigration.actions.size == 2)
    },
    test("migration actually transforms data correctly") {
      val migration = MigrationBuilderSyntax
        .checkedBuilder[CTSrcDrop, CTTgtDrop]
        .dropField(_.email)
        .addField(_.country, "US")
        .build

      val input  = Schema[CTSrcDrop].toDynamicValue(CTSrcDrop("Charlie", 40, "charlie@test.com"))
      val result = migration.applyDynamic(input)

      result match {
        case Right(dv) =>
          val fields = dv match {
            case DynamicValue.Record(fieldMap) => fieldMap.map(_._1).toSet
            case _                             => Set.empty[String]
          }
          assertTrue(
            fields.contains("name"),
            fields.contains("age"),
            fields.contains("country"),
            !fields.contains("email")
          )
        case Left(_) =>
          assertTrue(false)
      }
    }
  )
}

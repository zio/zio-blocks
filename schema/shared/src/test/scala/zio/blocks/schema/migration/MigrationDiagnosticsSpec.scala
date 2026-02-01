package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Schema}
import zio.test._

object MigrationDiagnosticsSpec extends ZIOSpecDefault {

  case class User(name: String, age: Int)
  object User {
    implicit val schema: Schema[User] = Schema.derived
  }

  case class UserV2(name: String, age: Int, email: String)
  object UserV2 {
    implicit val schema: Schema[UserV2] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("MigrationDiagnosticsSpec")(
    suite("DynamicMigration diagnostics")(
      test("identity migration has no actions") {
        val migration = DynamicMigration.identity
        assertTrue(migration.actions.isEmpty)
      },
      test("migration with single action") {
        val addAction = MigrationAction.AddField(
          DynamicOptic.root,
          "email",
          Resolved.Literal.string("")
        )
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(addAction))
        assertTrue(migration.actions.size == 1)
      },
      test("migration composition with ++") {
        val addAction1 = MigrationAction.AddField(DynamicOptic.root, "field1", Resolved.Literal.string(""))
        val addAction2 = MigrationAction.AddField(DynamicOptic.root, "field2", Resolved.Literal.string(""))
        val m1         = DynamicMigration(zio.blocks.chunk.Chunk(addAction1))
        val m2         = DynamicMigration(zio.blocks.chunk.Chunk(addAction2))
        val composed   = m1 ++ m2
        assertTrue(composed.actions.size == 2)
      },
      test("migration reverse") {
        val addAction    = MigrationAction.AddField(DynamicOptic.root, "email", Resolved.Literal.string(""))
        val renameAction = MigrationAction.Rename(DynamicOptic.root, "oldName", "newName")
        val migration    = DynamicMigration(zio.blocks.chunk.Chunk(addAction, renameAction))
        val reversed     = migration.reverse
        assertTrue(reversed.actions.size == 2)
      }
    ),
    suite("MigrationAction fields")(
      test("AddField has fieldName") {
        val action = MigrationAction.AddField(DynamicOptic.root, "email", Resolved.Literal.string(""))
        assertTrue(action.fieldName == "email")
      },
      test("DropField has fieldName") {
        val action = MigrationAction.DropField(DynamicOptic.root, "oldField", Resolved.Literal.string(""))
        assertTrue(action.fieldName == "oldField")
      },
      test("Rename has from and to names") {
        val action = MigrationAction.Rename(DynamicOptic.root, "oldName", "newName")
        assertTrue(action.from == "oldName" && action.to == "newName")
      },
      test("ChangeType has fieldName and converters") {
        val action = MigrationAction.ChangeType(
          DynamicOptic.root,
          "field",
          Resolved.Literal.long(0L),
          Resolved.Literal.int(0)
        )
        assertTrue(action.fieldName == "field")
      }
    ),
    suite("Resolved expressions")(
      test("Literal string evaluates correctly") {
        val literal = Resolved.Literal.string("test")
        val result  = literal.evalDynamic
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("test"))))
      },
      test("Literal int evaluates correctly") {
        val literal = Resolved.Literal.int(42)
        val result  = literal.evalDynamic
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(42))))
      },
      test("Literal long evaluates correctly") {
        val literal = Resolved.Literal.long(123L)
        val result  = literal.evalDynamic
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(123L))))
      },
      test("Literal boolean evaluates correctly") {
        val literal = Resolved.Literal.boolean(true)
        val result  = literal.evalDynamic
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("Literal double evaluates correctly") {
        val literal = Resolved.Literal.double(3.14)
        val result  = literal.evalDynamic
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(3.14))))
      },
      test("Identity returns input unchanged") {
        val input  = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val result = Resolved.Identity.evalDynamic(input)
        assertTrue(result == Right(input))
      },
      test("FieldAccess expression") {
        val fieldAccess = Resolved.FieldAccess("name", Resolved.Identity)
        val input       = DynamicValue.Record("name" -> DynamicValue.Primitive(PrimitiveValue.String("test")))
        val result      = fieldAccess.evalDynamic(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("test"))))
      },
      test("Fail expression returns error") {
        val fail   = Resolved.Fail("error message")
        val result = fail.evalDynamic
        assertTrue(result.isLeft)
      }
    ),
    suite("MigrationDiagnostics formatting")(
      test("formatMigration with empty migration") {
        val migration = DynamicMigration.identity
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("no actions"))
      },
      test("formatMigration with AddField") {
        val action    = MigrationAction.AddField(DynamicOptic.root, "email", Resolved.Literal.string(""))
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(action))
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("ADD") && formatted.contains("email"))
      },
      test("formatMigration with DropField") {
        val action    = MigrationAction.DropField(DynamicOptic.root, "old", Resolved.Literal.string(""))
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(action))
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("DROP") && formatted.contains("old"))
      },
      test("formatMigration with Rename") {
        val action    = MigrationAction.Rename(DynamicOptic.root, "from", "to")
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(action))
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("RENAME"))
      },
      test("formatMigration with TransformValue") {
        val action    = MigrationAction.TransformValue(DynamicOptic.root, "field", Resolved.Identity, Resolved.Identity)
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(action))
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("TRANSFORM"))
      },
      test("formatMigration with Mandate") {
        val action    = MigrationAction.Mandate(DynamicOptic.root, "field", Resolved.Literal.string(""))
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(action))
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("MANDATE"))
      },
      test("formatMigration with Optionalize") {
        val action    = MigrationAction.Optionalize(DynamicOptic.root, "field")
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(action))
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("OPTIONALIZE"))
      },
      test("formatMigration with ChangeType") {
        val action =
          MigrationAction.ChangeType(DynamicOptic.root, "field", Resolved.Literal.int(0), Resolved.Literal.string(""))
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(action))
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("CHANGE TYPE"))
      },
      test("formatMigration with RenameCase") {
        val action    = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(action))
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("RENAME CASE"))
      },
      test("formatMigration with TransformCase") {
        val action    = MigrationAction.TransformCase(DynamicOptic.root, "Case1", zio.blocks.chunk.Chunk.empty)
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(action))
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("TRANSFORM CASE"))
      },
      test("formatMigration with TransformElements") {
        val action    = MigrationAction.TransformElements(DynamicOptic.root, Resolved.Identity, Resolved.Identity)
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(action))
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("TRANSFORM ELEMENTS"))
      },
      test("formatMigration with TransformKeys") {
        val action    = MigrationAction.TransformKeys(DynamicOptic.root, Resolved.Identity, Resolved.Identity)
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(action))
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("TRANSFORM KEYS"))
      },
      test("formatMigration with TransformValues") {
        val action    = MigrationAction.TransformValues(DynamicOptic.root, Resolved.Identity, Resolved.Identity)
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(action))
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("TRANSFORM VALUES"))
      },
      test("formatMigration with Join") {
        val action = MigrationAction.Join(
          DynamicOptic.root,
          "fullName",
          zio.blocks.chunk.Chunk(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
          Resolved.Identity,
          Resolved.Identity
        )
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(action))
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("JOIN"))
      },
      test("formatMigration with Split") {
        val action = MigrationAction.Split(
          DynamicOptic.root,
          "fullName",
          zio.blocks.chunk.Chunk(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
          Resolved.Identity,
          Resolved.Identity
        )
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(action))
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("SPLIT"))
      },
      test("toMermaidDiagram generates flowchart") {
        val action    = MigrationAction.AddField(DynamicOptic.root, "email", Resolved.Literal.string(""))
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(action))
        val diagram   = MigrationDiagnostics.toMermaidDiagram(migration)
        assertTrue(diagram.contains("flowchart") && diagram.contains("Source") && diagram.contains("Target"))
      }
    ),
    suite("MigrationDiagnostics analysis")(
      test("analyze detects data loss warning for drops without renames") {
        val dropAction = MigrationAction.DropField(DynamicOptic.root, "field", Resolved.Literal.string(""))
        val migration  = DynamicMigration(zio.blocks.chunk.Chunk(dropAction))
        val analysis   = MigrationDiagnostics.analyze(migration)
        assertTrue(analysis.warnings.exists(_.contains("data loss")))
      },
      test("analyze detects large migration suggestion") {
        val actions =
          (1 to 15).map(i => MigrationAction.AddField(DynamicOptic.root, s"field$i", Resolved.Literal.string("")))
        val migration = DynamicMigration(zio.blocks.chunk.Chunk.fromIterable(actions))
        val analysis  = MigrationDiagnostics.analyze(migration)
        assertTrue(analysis.suggestions.exists(_.contains("Large migration")))
      },
      test("analyze detects redundant renames") {
        val rename1   = MigrationAction.Rename(DynamicOptic.root, "a", "b")
        val rename2   = MigrationAction.Rename(DynamicOptic.root, "b", "c")
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(rename1, rename2))
        val analysis  = MigrationDiagnostics.analyze(migration)
        assertTrue(analysis.suggestions.exists(_.contains("Redundant")))
      },
      test("analyze detects join and split combination") {
        val join =
          MigrationAction.Join(
            DynamicOptic.root,
            "fullName",
            zio.blocks.chunk.Chunk(DynamicOptic.root.field("f"), DynamicOptic.root.field("l")),
            Resolved.Identity,
            Resolved.Identity
          )
        val split =
          MigrationAction.Split(
            DynamicOptic.root,
            "name",
            zio.blocks.chunk.Chunk(DynamicOptic.root.field("x"), DynamicOptic.root.field("y")),
            Resolved.Identity,
            Resolved.Identity
          )
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(join, split))
        val analysis  = MigrationDiagnostics.analyze(migration)
        assertTrue(analysis.suggestions.exists(_.contains("join and split")))
      },
      test("analysis render produces formatted output") {
        val migration = DynamicMigration.identity
        val analysis  = MigrationDiagnostics.analyze(migration)
        val rendered  = analysis.render
        assertTrue(rendered.contains("Action Count") && rendered.contains("Reversible"))
      },
      test("suggestFixes generates rename suggestions for similar paths") {
        val fixes = MigrationDiagnostics.suggestFixes(List("firstName"), List("first_name"))
        assertTrue(fixes.exists(_.contains("renameField")))
      },
      test("suggestFixes generates drop suggestions for unhandled paths") {
        val fixes = MigrationDiagnostics.suggestFixes(List("oldField"), List())
        assertTrue(fixes.exists(_.contains("dropField")))
      },
      test("suggestFixes generates add suggestions for unprovided paths") {
        val fixes = MigrationDiagnostics.suggestFixes(List(), List("newField"))
        assertTrue(fixes.exists(_.contains("addField")))
      }
    ),
    suite("MigrationOptimizer")(
      test("optimize removes no-op renames") {
        val noOpRename = MigrationAction.Rename(DynamicOptic.root, "same", "same")
        val migration  = DynamicMigration(zio.blocks.chunk.Chunk(noOpRename))
        val optimized  = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.isEmpty)
      },
      test("optimize collapses sequential renames") {
        val rename1   = MigrationAction.Rename(DynamicOptic.root, "a", "b")
        val rename2   = MigrationAction.Rename(DynamicOptic.root, "b", "c")
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(rename1, rename2))
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 1)
      },
      test("optimize removes add-then-drop pairs") {
        val add       = MigrationAction.AddField(DynamicOptic.root, "temp", Resolved.Literal.string(""))
        val drop      = MigrationAction.DropField(DynamicOptic.root, "temp", Resolved.Literal.string(""))
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(add, drop))
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.isEmpty)
      },
      test("optimize converts drop-then-add to transform or removes") {
        val drop      = MigrationAction.DropField(DynamicOptic.root, "field", Resolved.Literal.string("old"))
        val add       = MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal.string("new"))
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(drop, add))
        val optimized = MigrationOptimizer.optimize(migration)
        // The optimizer either removes both or converts to transform
        assertTrue(
          optimized.actions.isEmpty || optimized.actions.exists(_.isInstanceOf[MigrationAction.TransformValue])
        )
      },
      test("optimizer report shows reduction stats") {
        val noOpRename = MigrationAction.Rename(DynamicOptic.root, "same", "same")
        val migration  = DynamicMigration(zio.blocks.chunk.Chunk(noOpRename))
        val report     = MigrationOptimizer.report(migration)
        assertTrue(report.actionsRemoved == 1 && report.percentReduced == 100.0)
      },
      test("optimizer report render produces formatted output") {
        val migration = DynamicMigration.identity
        val report    = MigrationOptimizer.report(migration)
        val rendered  = report.render
        assertTrue(rendered.contains("Optimization Report"))
      }
    ),
    suite("MigrationIntrospector")(
      test("summarize counts all action types") {
        val add       = MigrationAction.AddField(DynamicOptic.root, "f1", Resolved.Literal.string(""))
        val drop      = MigrationAction.DropField(DynamicOptic.root, "f2", Resolved.Literal.string(""))
        val rename    = MigrationAction.Rename(DynamicOptic.root, "a", "b")
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(add, drop, rename))
        val summary   = MigrationIntrospector.summarize(migration)
        assertTrue(
          summary.totalActions == 3 && summary.addedFields.nonEmpty && summary.droppedFields.nonEmpty && summary.renamedFields.nonEmpty
        )
      },
      test("isFullyReversible returns true for safe migrations") {
        val rename    = MigrationAction.Rename(DynamicOptic.root, "a", "b")
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(rename))
        assertTrue(MigrationIntrospector.isFullyReversible(migration))
      },
      test("calculateComplexity returns bounded score") {
        val actions = (1 to 20).map(i =>
          MigrationAction.Join(
            DynamicOptic.root,
            s"f$i",
            zio.blocks.chunk.Chunk(DynamicOptic.root.field("x"), DynamicOptic.root.field("y")),
            Resolved.Identity,
            Resolved.Identity
          )
        )
        val migration  = DynamicMigration(zio.blocks.chunk.Chunk.fromIterable(actions))
        val complexity = MigrationIntrospector.calculateComplexity(migration)
        assertTrue(complexity >= 1 && complexity <= 10)
      },
      test("generateSqlDdl produces PostgreSQL statements") {
        val add       = MigrationAction.AddField(DynamicOptic.root, "email", Resolved.Literal.string("default@test.com"))
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(add))
        val ddl       = MigrationIntrospector.generateSqlDdl(migration, "users", MigrationIntrospector.SqlDialect.PostgreSQL)
        assertTrue(ddl.statements.exists(_.contains("ALTER TABLE users ADD COLUMN email")))
      },
      test("generateSqlDdl produces MySQL statements for rename") {
        val rename    = MigrationAction.Rename(DynamicOptic.root, "old_col", "new_col")
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(rename))
        val ddl       = MigrationIntrospector.generateSqlDdl(migration, "users", MigrationIntrospector.SqlDialect.MySQL)
        assertTrue(ddl.statements.exists(_.contains("CHANGE old_col new_col")))
      },
      test("generateSqlDdl produces SQLite statements for rename") {
        val rename    = MigrationAction.Rename(DynamicOptic.root, "old_col", "new_col")
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(rename))
        val ddl       = MigrationIntrospector.generateSqlDdl(migration, "users", MigrationIntrospector.SqlDialect.SQLite)
        assertTrue(ddl.statements.exists(_.contains("RENAME COLUMN old_col TO new_col")))
      },
      test("generateSqlDdl warns about destructive DROP") {
        val drop      = MigrationAction.DropField(DynamicOptic.root, "col", Resolved.Literal.string(""))
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(drop))
        val ddl       = MigrationIntrospector.generateSqlDdl(migration, "users")
        assertTrue(ddl.warnings.exists(_.contains("destructive")))
      },
      test("generateSqlDdl warns about SQLite ALTER COLUMN TYPE") {
        val change    = MigrationAction.ChangeType(DynamicOptic.root, "col", Resolved.Identity, Resolved.Identity)
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(change))
        val ddl       = MigrationIntrospector.generateSqlDdl(migration, "users", MigrationIntrospector.SqlDialect.SQLite)
        assertTrue(ddl.warnings.exists(_.contains("SQLite")))
      },
      test("generateSqlDdl produces PostgreSQL ALTER COLUMN TYPE") {
        val change    = MigrationAction.ChangeType(DynamicOptic.root, "col", Resolved.Identity, Resolved.Identity)
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(change))
        val ddl       = MigrationIntrospector.generateSqlDdl(migration, "users", MigrationIntrospector.SqlDialect.PostgreSQL)
        assertTrue(ddl.statements.exists(_.contains("ALTER COLUMN col TYPE")))
      },
      test("generateSqlDdl produces MySQL MODIFY COLUMN") {
        val change    = MigrationAction.ChangeType(DynamicOptic.root, "col", Resolved.Identity, Resolved.Identity)
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(change))
        val ddl       = MigrationIntrospector.generateSqlDdl(migration, "users", MigrationIntrospector.SqlDialect.MySQL)
        assertTrue(ddl.statements.exists(_.contains("MODIFY COLUMN col")))
      },
      test("generateSqlDdl DDL result render produces statement list") {
        val add       = MigrationAction.AddField(DynamicOptic.root, "email", Resolved.Literal.int(42))
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(add))
        val ddl       = MigrationIntrospector.generateSqlDdl(migration, "users")
        assertTrue(ddl.render.contains("ALTER TABLE"))
      },
      test("generateDocumentation produces markdown") {
        val add       = MigrationAction.AddField(DynamicOptic.root, "email", Resolved.Literal.string(""))
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(add))
        val doc       = MigrationIntrospector.generateDocumentation(migration, "1.0.0", "1.1.0")
        assertTrue(doc.contains("# Migration") && doc.contains("Added Fields"))
      },
      test("generateDocumentation includes all sections for complex migration") {
        val add       = MigrationAction.AddField(DynamicOptic.root, "f1", Resolved.Literal.string(""))
        val drop      = MigrationAction.DropField(DynamicOptic.root, "f2", Resolved.Literal.string(""))
        val rename    = MigrationAction.Rename(DynamicOptic.root, "a", "b")
        val change    = MigrationAction.ChangeType(DynamicOptic.root, "c", Resolved.Identity, Resolved.Identity)
        val transform = MigrationAction.TransformValue(DynamicOptic.root, "d", Resolved.Identity, Resolved.Identity)
        val join      = MigrationAction.Join(
          DynamicOptic.root,
          "j",
          zio.blocks.chunk.Chunk(DynamicOptic.root.field("x"), DynamicOptic.root.field("y")),
          Resolved.Identity,
          Resolved.Identity
        )
        val split = MigrationAction.Split(
          DynamicOptic.root,
          "s",
          zio.blocks.chunk.Chunk(DynamicOptic.root.field("x"), DynamicOptic.root.field("y")),
          Resolved.Identity,
          Resolved.Identity
        )
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(add, drop, rename, change, transform, join, split))
        val doc       = MigrationIntrospector.generateDocumentation(migration, "1.0", "2.0")
        assertTrue(
          doc.contains("Added Fields") && doc.contains("Dropped Fields") &&
            doc.contains("Renamed Fields") && doc.contains("Type Changes") &&
            doc.contains("Transformed Fields") && doc.contains("Joined Fields") && doc.contains("Split Fields")
        )
      },
      test("validate warns about empty migration") {
        val migration = DynamicMigration.identity
        val report    = MigrationIntrospector.validate(migration)
        assertTrue(report.warnings.exists(_.contains("no actions")))
      },
      test("validate warns about TransformValue at root") {
        val action    = MigrationAction.TransformValue(DynamicOptic.root, "field", Resolved.Identity, Resolved.Identity)
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(action))
        val report    = MigrationIntrospector.validate(migration)
        assertTrue(report.warnings.exists(_.contains("root")))
      },
      test("summarize includes mandated and optionalized fields") {
        val mandate     = MigrationAction.Mandate(DynamicOptic.root, "opt", Resolved.Literal.string(""))
        val optionalize = MigrationAction.Optionalize(DynamicOptic.root, "req")
        val migration   = DynamicMigration(zio.blocks.chunk.Chunk(mandate, optionalize))
        val summary     = MigrationIntrospector.summarize(migration)
        assertTrue(summary.mandatedFields.nonEmpty && summary.optionalizedFields.nonEmpty)
      },
      test("summarize includes renamed cases") {
        val renameCase = MigrationAction.RenameCase(DynamicOptic.root, "Old", "New")
        val migration  = DynamicMigration(zio.blocks.chunk.Chunk(renameCase))
        val summary    = MigrationIntrospector.summarize(migration)
        assertTrue(summary.renamedCases.nonEmpty)
      }
    )
  )
}

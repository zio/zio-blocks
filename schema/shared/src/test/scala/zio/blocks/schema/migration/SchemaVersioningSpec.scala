package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema}
import zio.test._

object SchemaVersioningSpec extends ZIOSpecDefault {

  // Test case classes for schema versioning tests
  case class UserV1(name: String)
  object UserV1 {
    implicit val schema: Schema[UserV1] = Schema.derived
  }

  case class UserV2(name: String, email: String)
  object UserV2 {
    implicit val schema: Schema[UserV2] = Schema.derived
  }

  case class UserV3(name: String, email: String, age: Int)
  object UserV3 {
    implicit val schema: Schema[UserV3] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("SchemaVersioningSpec")(
    suite("SchemaVersion")(
      test("parses major version correctly") {
        val version = SchemaVersioning.SchemaVersion(
          version = "1.2.3",
          schema = UserV1.schema,
          description = "Initial version",
          timestamp = 0L,
          migrationFromPrevious = None
        )
        assertTrue(version.major == 1)
      },
      test("parses minor version correctly") {
        val version = SchemaVersioning.SchemaVersion(
          version = "1.2.3",
          schema = UserV1.schema,
          description = "Initial version",
          timestamp = 0L,
          migrationFromPrevious = None
        )
        assertTrue(version.minor == 2)
      },
      test("parses patch version correctly") {
        val version = SchemaVersioning.SchemaVersion(
          version = "1.2.3",
          schema = UserV1.schema,
          description = "Initial version",
          timestamp = 0L,
          migrationFromPrevious = None
        )
        assertTrue(version.patch == 3)
      },
      test("handles missing version parts gracefully") {
        val version = SchemaVersioning.SchemaVersion(
          version = "1",
          schema = UserV1.schema,
          description = "Simple version",
          timestamp = 0L,
          migrationFromPrevious = None
        )
        assertTrue(version.major == 1 && version.minor == 0 && version.patch == 0)
      },
      test("handles invalid version parts gracefully") {
        val version = SchemaVersioning.SchemaVersion(
          version = "abc.def.ghi",
          schema = UserV1.schema,
          description = "Invalid version",
          timestamp = 0L,
          migrationFromPrevious = None
        )
        assertTrue(version.major == 0 && version.minor == 0 && version.patch == 0)
      },
      test("isCompatibleWith returns true for same major version") {
        val v1 = SchemaVersioning.SchemaVersion(
          version = "1.0.0",
          schema = UserV1.schema,
          description = "V1",
          timestamp = 0L,
          migrationFromPrevious = None
        )
        val v2 = SchemaVersioning.SchemaVersion(
          version = "1.5.0",
          schema = UserV2.schema,
          description = "V2",
          timestamp = 0L,
          migrationFromPrevious = None
        )
        assertTrue(v1.isCompatibleWith(v2))
      },
      test("isCompatibleWith returns false for different major versions") {
        val v1 = SchemaVersioning.SchemaVersion(
          version = "1.0.0",
          schema = UserV1.schema,
          description = "V1",
          timestamp = 0L,
          migrationFromPrevious = None
        )
        val v2 = SchemaVersioning.SchemaVersion(
          version = "2.0.0",
          schema = UserV2.schema,
          description = "V2",
          timestamp = 0L,
          migrationFromPrevious = None
        )
        assertTrue(!v1.isCompatibleWith(v2))
      },
      test("isNewerThan compares versions correctly") {
        val v1 = SchemaVersioning.SchemaVersion(
          version = "1.0.0",
          schema = UserV1.schema,
          description = "V1",
          timestamp = 0L,
          migrationFromPrevious = None
        )
        val v2 = SchemaVersioning.SchemaVersion(
          version = "1.1.0",
          schema = UserV2.schema,
          description = "V2",
          timestamp = 0L,
          migrationFromPrevious = None
        )
        val v3 = SchemaVersioning.SchemaVersion(
          version = "2.0.0",
          schema = UserV3.schema,
          description = "V3",
          timestamp = 0L,
          migrationFromPrevious = None
        )
        assertTrue(v2.isNewerThan(v1) && v3.isNewerThan(v2) && !v1.isNewerThan(v2))
      }
    ),
    suite("VersionChain")(
      test("create initializes chain with single version") {
        val chain = SchemaVersioning.VersionChain.create(
          "1.0.0",
          UserV1.schema,
          "Initial version"
        )
        assertTrue(chain.versions.size == 1 && chain.versionHistory == List("1.0.0"))
      },
      test("latest returns the most recent version") {
        val chain = SchemaVersioning.VersionChain
          .create("1.0.0", UserV1.schema, "Initial")
          .addVersion("1.1.0", UserV2.schema, "Add email", DynamicMigration.identity)
        assertTrue(chain.latest.map(_.version) == Some("1.1.0"))
      },
      test("oldest returns the first version") {
        val chain = SchemaVersioning.VersionChain
          .create("1.0.0", UserV1.schema, "Initial")
          .addVersion("1.1.0", UserV2.schema, "Add email", DynamicMigration.identity)
        assertTrue(chain.oldest.map(_.version) == Some("1.0.0"))
      },
      test("findVersion locates existing version") {
        val chain = SchemaVersioning.VersionChain
          .create("1.0.0", UserV1.schema, "Initial")
          .addVersion("1.1.0", UserV2.schema, "Add email", DynamicMigration.identity)
        assertTrue(chain.findVersion("1.1.0").isDefined)
      },
      test("findVersion returns None for unknown version") {
        val chain = SchemaVersioning.VersionChain.create("1.0.0", UserV1.schema, "Initial")
        assertTrue(chain.findVersion("9.9.9").isEmpty)
      },
      test("migrationPath returns empty list for same version") {
        val chain = SchemaVersioning.VersionChain.create("1.0.0", UserV1.schema, "Initial")
        assertTrue(chain.migrationPath("1.0.0", "1.0.0") == Right(Nil))
      },
      test("migrationPath returns error for unknown source version") {
        val chain = SchemaVersioning.VersionChain.create("1.0.0", UserV1.schema, "Initial")
        assertTrue(chain.migrationPath("unknown", "1.0.0").isLeft)
      },
      test("migrationPath returns error for unknown target version") {
        val chain = SchemaVersioning.VersionChain.create("1.0.0", UserV1.schema, "Initial")
        assertTrue(chain.migrationPath("1.0.0", "unknown").isLeft)
      },
      test("migrationPath returns migrations for forward path") {
        val addFieldAction = MigrationAction.AddField(
          DynamicOptic.root,
          "email",
          Resolved.Literal.string("")
        )
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(addFieldAction))
        val chain     = SchemaVersioning.VersionChain
          .create("1.0.0", UserV1.schema, "Initial")
          .addVersion("1.1.0", UserV2.schema, "Add email", migration)
        val path = chain.migrationPath("1.0.0", "1.1.0")
        assertTrue(path.isRight && path.toOption.exists(_.nonEmpty))
      },
      test("migrationPath returns reversed migrations for backward path") {
        val addFieldAction = MigrationAction.AddField(
          DynamicOptic.root,
          "email",
          Resolved.Literal.string("")
        )
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(addFieldAction))
        val chain     = SchemaVersioning.VersionChain
          .create("1.0.0", UserV1.schema, "Initial")
          .addVersion("1.1.0", UserV2.schema, "Add email", migration)
        val path = chain.migrationPath("1.1.0", "1.0.0")
        assertTrue(path.isRight && path.toOption.exists(_.nonEmpty))
      },
      test("composeMigration combines migrations") {
        val migration = DynamicMigration.identity
        val chain     = SchemaVersioning.VersionChain
          .create("1.0.0", UserV1.schema, "Initial")
          .addVersion("1.1.0", UserV2.schema, "Add email", migration)
        assertTrue(chain.composeMigration("1.0.0", "1.1.0").isRight)
      },
      test("summary generates version history text") {
        val chain = SchemaVersioning.VersionChain
          .create("1.0.0", UserV1.schema, "Initial version")
          .addVersion("1.1.0", UserV2.schema, "Add email field", DynamicMigration.identity)
        val summary = chain.summary
        assertTrue(summary.contains("1.0.0") && summary.contains("1.1.0"))
      }
    ),
    suite("CompatibilityChecker")(
      test("fully compatible for identity migration") {
        val migration = DynamicMigration.identity
        val level     = SchemaVersioning.CompatibilityChecker.checkCompatibility(migration)
        assertTrue(level == SchemaVersioning.CompatibilityChecker.CompatibilityLevel.FullyCompatible)
      },
      test("breaking change for drop field") {
        val dropAction = MigrationAction.DropField(
          DynamicOptic.root,
          "field",
          Resolved.Literal.string("")
        )
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(dropAction))
        val level     = SchemaVersioning.CompatibilityChecker.checkCompatibility(migration)
        assertTrue(level == SchemaVersioning.CompatibilityChecker.CompatibilityLevel.BreakingChange)
      },
      test("breaking change for type change") {
        val changeTypeAction = MigrationAction.ChangeType(
          DynamicOptic.root,
          "field",
          Resolved.Literal.string(""),
          Resolved.Literal.int(0)
        )
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(changeTypeAction))
        val level     = SchemaVersioning.CompatibilityChecker.checkCompatibility(migration)
        assertTrue(level == SchemaVersioning.CompatibilityChecker.CompatibilityLevel.BreakingChange)
      },
      test("backward compatible for add field only") {
        val addFieldAction = MigrationAction.AddField(
          DynamicOptic.root,
          "field",
          Resolved.Literal.string("")
        )
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(addFieldAction))
        val level     = SchemaVersioning.CompatibilityChecker.checkCompatibility(migration)
        assertTrue(level == SchemaVersioning.CompatibilityChecker.CompatibilityLevel.BackwardCompatible)
      },
      test("forward compatible for rename only") {
        val renameAction = MigrationAction.Rename(
          DynamicOptic.root,
          "oldName",
          "newName"
        )
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(renameAction))
        val level     = SchemaVersioning.CompatibilityChecker.checkCompatibility(migration)
        assertTrue(level == SchemaVersioning.CompatibilityChecker.CompatibilityLevel.ForwardCompatible)
      },
      test("generateCompatibilityReport produces output for all levels") {
        val identity = DynamicMigration.identity

        val dropAction = MigrationAction.DropField(DynamicOptic.root, "field", Resolved.Literal.string(""))
        val dropField  = DynamicMigration(zio.blocks.chunk.Chunk(dropAction))

        val addAction = MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal.string(""))
        val addField  = DynamicMigration(zio.blocks.chunk.Chunk(addAction))

        val renameAction = MigrationAction.Rename(DynamicOptic.root, "old", "new")
        val rename       = DynamicMigration(zio.blocks.chunk.Chunk(renameAction))

        val reportIdentity = SchemaVersioning.CompatibilityChecker.generateCompatibilityReport(identity)
        val reportDrop     = SchemaVersioning.CompatibilityChecker.generateCompatibilityReport(dropField)
        val reportAdd      = SchemaVersioning.CompatibilityChecker.generateCompatibilityReport(addField)
        val reportRename   = SchemaVersioning.CompatibilityChecker.generateCompatibilityReport(rename)

        assertTrue(
          reportIdentity.contains("Compatibility Report") &&
            reportDrop.contains("WARNING") &&
            reportAdd.contains("Backward") &&
            reportRename.contains("Forward")
        )
      }
    ),
    suite("SemVer")(
      test("parse valid version") {
        val result = SchemaVersioning.SemVer.parse("1.2.3")
        assertTrue(result == Right((1, 2, 3)))
      },
      test("parse invalid version format returns error") {
        val result = SchemaVersioning.SemVer.parse("1.2")
        assertTrue(result.isLeft)
      },
      test("parse non-numeric version returns error") {
        val result = SchemaVersioning.SemVer.parse("1.a.3")
        assertTrue(result.isLeft)
      },
      test("nextMajor increments major and resets others") {
        val result = SchemaVersioning.SemVer.nextMajor("1.2.3")
        assertTrue(result == Right("2.0.0"))
      },
      test("nextMinor increments minor and resets patch") {
        val result = SchemaVersioning.SemVer.nextMinor("1.2.3")
        assertTrue(result == Right("1.3.0"))
      },
      test("nextPatch increments patch only") {
        val result = SchemaVersioning.SemVer.nextPatch("1.2.3")
        assertTrue(result == Right("1.2.4"))
      },
      test("suggestNextVersion for breaking change suggests major bump") {
        val dropAction = MigrationAction.DropField(DynamicOptic.root, "field", Resolved.Literal.string(""))
        val migration  = DynamicMigration(zio.blocks.chunk.Chunk(dropAction))
        val result     = SchemaVersioning.SemVer.suggestNextVersion("1.2.3", migration)
        assertTrue(result == Right("2.0.0"))
      },
      test("suggestNextVersion for backward compatible suggests minor bump") {
        val addAction = MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal.string(""))
        val migration = DynamicMigration(zio.blocks.chunk.Chunk(addAction))
        val result    = SchemaVersioning.SemVer.suggestNextVersion("1.2.3", migration)
        assertTrue(result == Right("1.3.0"))
      },
      test("suggestNextVersion for forward compatible suggests minor bump") {
        val renameAction = MigrationAction.Rename(DynamicOptic.root, "old", "new")
        val migration    = DynamicMigration(zio.blocks.chunk.Chunk(renameAction))
        val result       = SchemaVersioning.SemVer.suggestNextVersion("1.2.3", migration)
        assertTrue(result == Right("1.3.0"))
      },
      test("suggestNextVersion for fully compatible suggests patch bump") {
        val migration = DynamicMigration.identity
        val result    = SchemaVersioning.SemVer.suggestNextVersion("1.2.3", migration)
        assertTrue(result == Right("1.2.4"))
      }
    )
  )
}

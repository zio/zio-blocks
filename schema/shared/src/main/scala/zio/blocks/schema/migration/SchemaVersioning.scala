package zio.blocks.schema.migration

import zio.blocks.schema.Schema
import scala.math.Ordering.Implicits.infixOrderingOps

/**
 * Schema version management utilities.
 *
 * This object provides capabilities for managing multiple schema versions,
 * creating version chains, and performing upgrades/downgrades between arbitrary
 * versions in a schema evolution history.
 */
object SchemaVersioning {

  /**
   * Represents a version of a schema with its migration from the previous
   * version.
   */
  case class SchemaVersion[A](
    version: String,
    schema: Schema[A],
    description: String,
    timestamp: Long,
    migrationFromPrevious: Option[DynamicMigration]
  ) {
    def major: Int = version.split('.').headOption.flatMap(_.toIntOption).getOrElse(0)
    def minor: Int = version.split('.').lift(1).flatMap(_.toIntOption).getOrElse(0)
    def patch: Int = version.split('.').lift(2).flatMap(_.toIntOption).getOrElse(0)

    def isCompatibleWith(other: SchemaVersion[?]): Boolean =
      major == other.major

    def isNewerThan(other: SchemaVersion[?]): Boolean =
      (major, minor, patch) > (other.major, other.minor, other.patch)
  }

  /**
   * A chain of schema versions enabling migration between any two versions.
   */
  case class VersionChain[Latest](
    versions: List[SchemaVersion[?]],
    latestSchema: Schema[Latest]
  ) {
    def latest: Option[SchemaVersion[?]] = versions.lastOption

    def oldest: Option[SchemaVersion[?]] = versions.headOption

    def findVersion(version: String): Option[SchemaVersion[?]] =
      versions.find(_.version == version)

    /**
     * Get the migration path from one version to another. Returns the list of
     * migrations to apply in order.
     */
    def migrationPath(
      fromVersion: String,
      toVersion: String
    ): Either[String, List[DynamicMigration]] = {
      val fromIdx = versions.indexWhere(_.version == fromVersion)
      val toIdx   = versions.indexWhere(_.version == toVersion)

      if (fromIdx < 0) Left(s"Unknown source version: $fromVersion")
      else if (toIdx < 0) Left(s"Unknown target version: $toVersion")
      else if (fromIdx == toIdx) Right(Nil)
      else if (fromIdx < toIdx) {
        val versionsToApply = versions.slice(fromIdx + 1, toIdx + 1)
        val migrations      = versionsToApply.flatMap(_.migrationFromPrevious)
        Right(migrations)
      } else {
        val versionsToReverse = versions.slice(toIdx + 1, fromIdx + 1).reverse
        val migrations        = versionsToReverse.flatMap(_.migrationFromPrevious.map(_.reverse))
        Right(migrations)
      }
    }

    /**
     * Compose a single migration from one version to another.
     */
    def composeMigration(
      fromVersion: String,
      toVersion: String
    ): Either[String, DynamicMigration] =
      migrationPath(fromVersion, toVersion).map { migrations =>
        migrations.foldLeft(DynamicMigration.identity)(_ ++ _)
      }

    /**
     * Add a new version to the chain.
     */
    def addVersion[A](
      version: String,
      schema: Schema[A],
      description: String,
      migration: DynamicMigration
    ): VersionChain[A] = {
      val newVersion = SchemaVersion(
        version = version,
        schema = schema,
        description = description,
        timestamp = System.currentTimeMillis(),
        migrationFromPrevious = Some(migration)
      )
      VersionChain(versions :+ newVersion, schema)
    }

    def versionHistory: List[String] = versions.map(_.version)

    def summary: String = {
      val lines = versions.map { v =>
        s"${v.version}: ${v.description}"
      }
      lines.mkString("\n")
    }
  }

  object VersionChain {
    def create[A](
      initialVersion: String,
      initialSchema: Schema[A],
      description: String
    ): VersionChain[A] = {
      val initial = SchemaVersion[A](
        version = initialVersion,
        schema = initialSchema,
        description = description,
        timestamp = System.currentTimeMillis(),
        migrationFromPrevious = None
      )
      VersionChain(List(initial), initialSchema)
    }
  }

  /**
   * Migration compatibility checker for version chains.
   */
  object CompatibilityChecker {

    sealed trait CompatibilityLevel
    object CompatibilityLevel {
      case object FullyCompatible    extends CompatibilityLevel
      case object BackwardCompatible extends CompatibilityLevel
      case object ForwardCompatible  extends CompatibilityLevel
      case object BreakingChange     extends CompatibilityLevel
    }

    def checkCompatibility(migration: DynamicMigration): CompatibilityLevel = {
      val hasDrops       = migration.actions.exists(_.isInstanceOf[MigrationAction.DropField])
      val hasAdds        = migration.actions.exists(_.isInstanceOf[MigrationAction.AddField])
      val hasTypeChanges = migration.actions.exists(_.isInstanceOf[MigrationAction.ChangeType])
      val hasRenames     = migration.actions.exists(_.isInstanceOf[MigrationAction.Rename])

      if (!hasDrops && !hasAdds && !hasTypeChanges && !hasRenames) {
        CompatibilityLevel.FullyCompatible
      } else if (hasDrops || hasTypeChanges) {
        CompatibilityLevel.BreakingChange
      } else if (hasAdds && !hasDrops) {
        CompatibilityLevel.BackwardCompatible
      } else {
        CompatibilityLevel.ForwardCompatible
      }
    }

    def generateCompatibilityReport(migration: DynamicMigration): String = {
      val level = checkCompatibility(migration)
      val sb    = new StringBuilder

      sb.append("# Compatibility Report\n\n")
      sb.append(s"**Level:** ${level.toString}\n\n")

      level match {
        case CompatibilityLevel.FullyCompatible =>
          sb.append("This migration makes no structural changes that affect compatibility.\n")

        case CompatibilityLevel.BackwardCompatible =>
          sb.append("New readers can read old data. Old readers cannot read new data.\n")
          sb.append("Safe for rolling upgrades where writers are upgraded before readers.\n")

        case CompatibilityLevel.ForwardCompatible =>
          sb.append("Old readers can read new data. New readers cannot read old data.\n")
          sb.append("Safe for rolling upgrades where readers are upgraded before writers.\n")

        case CompatibilityLevel.BreakingChange =>
          sb.append("**WARNING:** This migration contains breaking changes.\n")
          sb.append("Both readers and writers must be upgraded simultaneously.\n")
          sb.append("Consider a data migration strategy.\n")
      }

      sb.toString
    }
  }

  /**
   * Semantic versioning utilities for schema versions.
   */
  object SemVer {
    def parse(version: String): Either[String, (Int, Int, Int)] = {
      val parts = version.split('.')
      if (parts.length != 3) {
        Left(s"Invalid version format: $version (expected major.minor.patch)")
      } else {
        try {
          Right((parts(0).toInt, parts(1).toInt, parts(2).toInt))
        } catch {
          case _: NumberFormatException =>
            Left(s"Invalid version numbers in: $version")
        }
      }
    }

    def nextMajor(version: String): Either[String, String] =
      parse(version).map { case (major, _, _) => s"${major + 1}.0.0" }

    def nextMinor(version: String): Either[String, String] =
      parse(version).map { case (major, minor, _) => s"$major.${minor + 1}.0" }

    def nextPatch(version: String): Either[String, String] =
      parse(version).map { case (major, minor, patch) => s"$major.$minor.${patch + 1}" }

    def suggestNextVersion(
      currentVersion: String,
      migration: DynamicMigration
    ): Either[String, String] = {
      val level = CompatibilityChecker.checkCompatibility(migration)
      level match {
        case CompatibilityChecker.CompatibilityLevel.BreakingChange     => nextMajor(currentVersion)
        case CompatibilityChecker.CompatibilityLevel.BackwardCompatible => nextMinor(currentVersion)
        case CompatibilityChecker.CompatibilityLevel.ForwardCompatible  => nextMinor(currentVersion)
        case CompatibilityChecker.CompatibilityLevel.FullyCompatible    => nextPatch(currentVersion)
      }
    }
  }
}

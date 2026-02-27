package zio.blocks.schema

/**
 * ZIO Schema Migration System
 *
 * Provides a pure, algebraic migration system for ZIO Schema 2 that represents
 * structural transformations between schema versions as first-class, serializable data.
 *
 * A migration describes how to transform data from one schema version to another, enabling:
 * - schema evolution
 * - backward / forward compatibility
 * - data versioning
 * - offline migrations (JSON, SQL, data lakes, registries, etc.)
 *
 * The system provides a typed, macro-validated user API (`Migration[A, B]`) built on
 * a pure, serializable core (`DynamicMigration`) that operates on `DynamicValue`.
 */
package object migration {
  type MigrationAction = migration.MigrationAction
  type MigrationError = migration.MigrationError
  type MigrationBuilder = migration.MigrationBuilder

  val Migration = migration.Migration
  val MigrationBuilder = migration.MigrationBuilder
  val DynamicMigration = migration.DynamicMigration

  // Re-export commonly used types
  type DynamicMigration = migration.DynamicMigration
  type Migration[A, B] = migration.Migration[A, B]
}

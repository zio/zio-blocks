package zio.blocks.schema

/**
 * Schema migration: pure, algebraic transformations between schema versions.
 *
 * - [[migration.Migration]] — typed migration from A to B
 * - [[migration.DynamicMigration]] — untyped, serializable migration on DynamicValue
 * - [[migration.MigrationBuilder]] — path-based builder for constructing migrations
 * - [[migration.MigrationAction]] — individual steps (AddField, Rename, etc.)
 * - [[migration.MigrationExpr]] — serializable value expressions (Literal, SourcePath)
 */
package object migration {}

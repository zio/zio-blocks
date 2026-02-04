package zio.blocks.schema

/**
 * The migration package provides a pure, algebraic migration system for ZIO
 * Schema.
 *
 * Key types:
 *   - [[Migration]] - A typed migration from A to B
 *   - [[DynamicMigration]] - An untyped, serializable migration operating on
 *     DynamicValue
 *   - [[MigrationAction]] - Individual migration actions (add/drop/rename
 *     field, etc.)
 *   - [[MigrationBuilder]] - A fluent builder for constructing migrations
 *   - [[DynamicSchemaExpr]] - Serializable expressions for value
 *     transformations
 *   - [[MigrationError]] - Errors that can occur during migration
 */
package object migration

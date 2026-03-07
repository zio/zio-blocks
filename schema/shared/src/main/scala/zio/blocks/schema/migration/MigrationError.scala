package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, SchemaError}

/**
 * Error type for schema migration operations. Captures path information for
 * diagnostics (e.g. "Failed to apply TransformValue at `.addresses.each.streetNumber`").
 */
type MigrationError = SchemaError

object MigrationError {

  /** Creates a migration error with message and path. */
  def apply(details: String, path: DynamicOptic = DynamicOptic.root): MigrationError =
    SchemaError.message(details, path)
}

package zio.blocks.schema.migration

import zio.blocks.schema.{Schema, SchemaExpr}

/**
 * Migration helpers around SchemaExpr.DefaultValue (issue #519).
 *
 * Users can write MigrationSchemaExpr.default in the DSL. Macros must replace
 * this marker with SchemaExpr.DefaultValueFromSchema(fieldSchema).
 */
object MigrationSchemaExpr {

  /** User-facing marker */
  def default[S, A]: SchemaExpr[S, A] =
    SchemaExpr.DefaultValueMarker.asInstanceOf[SchemaExpr[S, A]]

  /**
   * Used by macros: if expr is the marker, replace it with captured default.
   */
  def captureDefaultIfMarker[S, A](
    expr: SchemaExpr[S, A],
    fieldSchema: Schema[A]
  ): SchemaExpr[S, A] =
    expr match {
      case SchemaExpr.DefaultValueMarker =>
        SchemaExpr.DefaultValueFromSchema(fieldSchema).asInstanceOf[SchemaExpr[S, A]]
      case other =>
        other
    }

  /** Used by macros directly. */
  def defaultFromSchema[S, A](fieldSchema: Schema[A]): SchemaExpr[S, A] =
    SchemaExpr.DefaultValueFromSchema(fieldSchema).asInstanceOf[SchemaExpr[S, A]]
}

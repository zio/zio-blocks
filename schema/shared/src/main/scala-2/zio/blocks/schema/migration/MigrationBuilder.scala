package zio.blocks.schema.migration

/**
 * Scala 2 does not support the macro-based MigrationBuilder DSL.
 *
 * Use DynamicMigration directly.
 */
object MigrationBuilder {
  @deprecated(
    "MigrationBuilder DSL is only available on Scala 3. Use DynamicMigration on Scala 2.",
    since = "x.y.z"
  )
  def unsupported: Nothing =
    throw new UnsupportedOperationException(
      "MigrationBuilder is Scala 3 only"
    )
}

package zio.blocks.schema.migration

import zio.blocks.schema.Schema

/**
 * Scala 3 specific companion methods for Migration.
 */
trait MigrationCompanionPlatformSpecific extends MigrationSelectorSyntax {

  /** Create a new migration builder with selector syntax support. */
  def builder[A, B](using
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, EmptyTuple, EmptyTuple] =
    MigrationBuilder[A, B]
}

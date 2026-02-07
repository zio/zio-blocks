package zio.blocks.schema.migration

import zio.blocks.schema.Schema

/**
 * Scala 2 specific companion methods for Migration.
 */
trait MigrationCompanionPlatformSpecific extends MigrationSelectorSyntax {

  /** Create a new migration builder with selector syntax support. */
  def builder[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B] =
    MigrationBuilder[A, B]
}

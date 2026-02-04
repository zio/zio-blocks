package zio.blocks.schema.migration

import zio.blocks.schema.Schema

trait MigrationCompanionVersionSpecific {
  def newBuilder[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}

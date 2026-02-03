package zio.blocks.schema.migration

import zio.blocks.schema.Schema

trait MigrationBuilderCompanionMacro {

  def apply[A, B](
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, TypeLevel.Empty, TypeLevel.Empty] =
    MigrationBuilder[A, B, TypeLevel.Empty, TypeLevel.Empty](
      sourceSchema,
      targetSchema,
      MigrationStep.Record.empty,
      MigrationStep.Variant.empty
    )

  def apply[A, B](using
    sourceSchema: Schema[A],
    targetSchema: Schema[B],
    d: DummyImplicit
  ): MigrationBuilder[A, B, TypeLevel.Empty, TypeLevel.Empty] =
    MigrationBuilder[A, B, TypeLevel.Empty, TypeLevel.Empty](
      sourceSchema,
      targetSchema,
      MigrationStep.Record.empty,
      MigrationStep.Variant.empty
    )

  def fromSchemas[A, B](
    schemas: (Schema[A], Schema[B])
  ): MigrationBuilder[A, B, TypeLevel.Empty, TypeLevel.Empty] =
    apply(schemas._1, schemas._2)

  def from[A](using sourceSchema: Schema[A]): FromBuilder[A] =
    new FromBuilder[A](sourceSchema)

  final class FromBuilder[A](sourceSchema: Schema[A]) {
    def to[B](using targetSchema: Schema[B]): MigrationBuilder[A, B, TypeLevel.Empty, TypeLevel.Empty] =
      MigrationBuilder[A, B, TypeLevel.Empty, TypeLevel.Empty](
        sourceSchema,
        targetSchema,
        MigrationStep.Record.empty,
        MigrationStep.Variant.empty
      )
  }
}

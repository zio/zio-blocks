package zio.blocks.schema.migration

import zio.blocks.schema.Schema
import zio.blocks.schema.migration.TypeLevel.Empty

trait MigrationBuilderCompanionMacro extends MigrationBuilderSyntaxMacro {

  def apply[A, B](
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, Empty, Empty] =
    new MigrationBuilder[A, B, Empty, Empty](
      sourceSchema,
      targetSchema,
      MigrationStep.Record.empty,
      MigrationStep.Variant.empty
    )

  def apply[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B],
    d: DummyImplicit
  ): MigrationBuilder[A, B, Empty, Empty] =
    new MigrationBuilder[A, B, Empty, Empty](
      sourceSchema,
      targetSchema,
      MigrationStep.Record.empty,
      MigrationStep.Variant.empty
    )

  def fromSchemas[A, B](
    schemas: (Schema[A], Schema[B])
  ): MigrationBuilder[A, B, Empty, Empty] =
    apply(schemas._1, schemas._2)

  def from[A](implicit sourceSchema: Schema[A]): FromBuilder[A] =
    new FromBuilder[A](sourceSchema)

  final class FromBuilder[A](sourceSchema: Schema[A]) {
    def to[B](implicit targetSchema: Schema[B]): MigrationBuilder[A, B, Empty, Empty] =
      new MigrationBuilder[A, B, Empty, Empty](
        sourceSchema,
        targetSchema,
        MigrationStep.Record.empty,
        MigrationStep.Variant.empty
      )
  }
}

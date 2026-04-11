package zio.blocks.schema.migration

import zio.blocks.schema.Schema

final case class Migration[A, B](
    toDynamic: DynamicMigration,
    schemaA: Schema[A],
    schemaB: Schema[B]
) {
  def apply(value: A): Either[MigrationError, B] =
    toDynamic(schemaA.toDynamic(value))
     .flatMap(_.toTypedValue(schemaB).left.map(err => MigrationError(err.toString)))

  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(toDynamic ++ that.toDynamic, schemaA, that.schemaB)

  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that
  def reverse: Migration[B, A] = Migration(toDynamic.reverse, schemaB, schemaA)
}
object Migration {
  def newBuilder[A, B](using Schema[A], Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B]
  def identity[A](using Schema[A]): Migration[A, A] =
    Migration(DynamicMigration.empty, summon[Schema[A]], summon[Schema[A]])
}

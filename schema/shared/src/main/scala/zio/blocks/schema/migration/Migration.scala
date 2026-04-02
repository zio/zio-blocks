package zio.blocks.schema.migration

import zio.blocks.schema.Schema

final case class Migration[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  dynamicMigration: DynamicMigration
) {
  def apply(value: A): Either[MigrationError, B] = {
    val dynApp = for {
      dynSrc <- Right(sourceSchema.toDynamicValue(value))
      dynTgt <- dynamicMigration.apply(dynSrc)
      b      <- targetSchema.fromDynamicValue(dynTgt).left.map(e => MigrationError.EvaluationError(e.message))
    } yield b
    dynApp
  }

  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(sourceSchema, that.targetSchema, dynamicMigration ++ that.dynamicMigration)

  def reverse: Migration[B, A] = Migration(targetSchema, sourceSchema, dynamicMigration.reverse)
}

object Migration {
  def derive[A, B](implicit source: Schema[A], target: Schema[B]): MigrationBuilder[A, B, A] =
    new MigrationBuilder[A, B, A](source, target, DynamicMigration.empty)
}

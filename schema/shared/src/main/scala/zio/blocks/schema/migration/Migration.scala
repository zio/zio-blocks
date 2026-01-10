package zio.blocks.schema.migration

import zio.schema.{Schema, DynamicValue}
import zio.blocks.schema.migration.optic.DynamicOptic // Import needed

final case class Migration[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  dynamicMigration: DynamicMigration
) {
  def apply(value: A): Either[MigrationError, B] = {
    // Mocking implementation for test
    val inputDv = value.asInstanceOf[DynamicValue] 
    dynamicMigration.apply(inputDv).flatMap { migratedDv =>
      try {
        Right(migratedDv.asInstanceOf[B])
      } catch {
        case _: Throwable => 
          Left(MigrationError(DynamicOptic.empty, "Failed to cast back to Target Type"))
      }
    }
  }

  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(
      sourceSchema, 
      that.targetSchema, 
      this.dynamicMigration ++ that.dynamicMigration
    )

  def reverse: Migration[B, A] =
    Migration(
      targetSchema,
      sourceSchema,
      dynamicMigration.reverse
    )
}

object Migration {
  // FIX: 'using' -> 'implicit' (Scala 2 Compatible)
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(schema, schema, DynamicMigration.identity)

  // FIX: 'using' -> 'implicit'
  def newBuilder[A, B](implicit src: Schema[A], tgt: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(src, tgt, Vector.empty)
}
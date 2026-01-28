package zio.blocks.schema

package object migration {

  type MigrationResult[A] = Either[MigrationError, A]

  implicit class DynamicValueOps(private val value: DynamicValue) extends AnyVal {
    def migrate(migration: DynamicMigration): Either[MigrationError, DynamicValue] =
      migration(value)
  }

  implicit class MigrationOps[A, B](private val m1: Migration[A, B]) extends AnyVal {
    def compose[C](m2: Migration[B, C]): Migration[A, C] = m1 andThen m2
  }
}

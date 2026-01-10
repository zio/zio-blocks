package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * A typed wrapper around DynamicMigration that enforces source and target schemas.
 * 
 * `Migration[A, B]` represents a transformation from schema version A to schema version B.
 * It provides a high-level, type-safe API for running migrations on typed values.
 * 
 * @tparam A The source schema type
 * @tparam B The target schema type
 * @param dynamic The underlying dynamic migration
 * @param from Schema for the source type A
 * @param to Schema for the target type B
 */
final case class Migration[A, B](dynamic: DynamicMigration)(implicit val from: Schema[A], val to: Schema[B]) {
  
  /**
   * Apply this migration to a value of type A.
   * 
   * @param value The input value
   * @return Either an error or the migrated value of type B
   */
  def apply(value: A): Either[MigrationError, B] =
    for {
      dynamicValue <- Right(from.toDynamicValue(value))
      migratedDynamic <- dynamic.apply(dynamicValue)
      result <- to.fromDynamicValue(migratedDynamic) match {
        case Right(b) => Right(b)
        case Left(schemaError) => 
          Left(MigrationError.EvaluationFailed(DynamicOptic.root, schemaError.toString))
      }
    } yield result
  
  /**
   * Compose this migration with another.
   * 
   * @param that The migration to apply after this one
   * @return The composed migration from A to C
   */
  def andThen[C](that: Migration[B, C]): Migration[A, C] =
    Migration[A, C](this.dynamic ++ that.dynamic)(this.from, that.to)
  
  /**
   * Alias for andThen.
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] = andThen(that)
  
  /**
   * Attempt to reverse this migration.
   * 
   * @return Either the reversed migration or an error if not reversible
   */
  def reverse: Either[MigrationError, Migration[B, A]] =
    dynamic.reverse.map(reversed => Migration[B, A](reversed)(to, from))
  
  /**
   * Check if this migration is reversible.
   */
  def isReversible: Boolean = dynamic.isReversible
  
  /**
   * Get a human-readable description of this migration.
   */
  def describe: String = dynamic.describe
}

object Migration {
  
  /**
   * Create an empty migration (identity).
   */
  def empty[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration[A, A](DynamicMigration.empty)(schema, schema)
  
  /**
   * Create a migration from a single action.
   */
  def single[A, B](action: MigrationAction)(implicit from: Schema[A], to: Schema[B]): Migration[A, B] =
    Migration[A, B](DynamicMigration.single(action))(from, to)
  
  /**
   * Create a migration builder for type-safe migration construction.
   * 
   * Example:
   * {{{
   *   // Scala 3:
   *   val migration = Migration.builder[PersonV0, Person]
   *     .renameField(_.name, _.fullName)  // Type-safe selector
   *     .addField("country", "USA")
   *     .build
   *   
   *   // Scala 2:
   *   val migration = Migration.builder[PersonV0, Person]
   *     .renameField("name", "fullName")  // String-based
   *     .addField("country", "USA")
   *     .build
   * }}}
   */
  def builder[A, B](implicit fromSchema: Schema[A], toSchema: Schema[B]): MigrationBuilder[A, B] = {
    MigrationBuilder[A, B]
  }
}

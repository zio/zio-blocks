package zio.blocks.schema.migration

import zio.blocks.schema._

import scala.util.control.NonFatal

/**
 * A typed migration from type `A` to type `B`.
 *
 * [[Migration]] wraps a [[DynamicMigration]] with source and target schemas,
 * providing:
 *   - Type-safe application of migrations
 *   - Compile-time validation via macros
 *   - Composable migration chains
 *   - Reversibility (structural inverse)
 *
 * The schemas are "structural schemas" - for old versions, these may be derived
 * from structural types that exist only at compile time, with no runtime
 * representation.
 *
 * @tparam A
 *   the source type
 * @tparam B
 *   the target type
 * @param dynamicMigration
 *   the underlying untyped migration
 * @param sourceSchema
 *   schema for the source type
 * @param targetSchema
 *   schema for the target type
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /**
   * Apply this migration to transform a value of type A to type B.
   */
  def apply(value: A): Either[MigrationError, B] = {
    val dynamicValueEither =
      try Right(sourceSchema.toDynamicValue(value))
      catch {
        case _: UnsupportedOperationException =>
          Left(
            MigrationError.single(
              MigrationError.TransformFailed(
                DynamicOptic.root,
                "Cannot apply typed migration to a structural source schema at runtime. Use `applyDynamic` with `DynamicValue` instead."
              )
            )
          )
        case NonFatal(e) =>
          Left(
            MigrationError.single(
              MigrationError.TransformFailed(
                DynamicOptic.root,
                s"Failed to convert source value to DynamicValue: ${e.getMessage}"
              )
            )
          )
      }

    dynamicValueEither.flatMap { dynamicValue =>
      dynamicMigration(dynamicValue).flatMap { result =>
        try {
          targetSchema.fromDynamicValue(result) match {
            case Right(b)          => Right(b)
            case Left(schemaError) =>
              Left(
                MigrationError.single(
                  MigrationError.TransformFailed(
                    DynamicOptic.root,
                    s"Failed to convert result to target type: ${schemaError.getMessage}"
                  )
                )
              )
          }
        } catch {
          case _: UnsupportedOperationException =>
            Left(
              MigrationError.single(
                MigrationError.TransformFailed(
                  DynamicOptic.root,
                  "Cannot materialize a structural target schema at runtime. Use `applyDynamic` with `DynamicValue` instead."
                )
              )
            )
          case NonFatal(e) =>
            Left(
              MigrationError.single(
                MigrationError.TransformFailed(
                  DynamicOptic.root,
                  s"Failed to convert result to target type: ${e.getMessage}"
                )
              )
            )
        }
      }
    }
  }

  /**
   * Apply this migration on a [[DynamicValue]] directly.
   */
  def applyDynamic(value: DynamicValue): Either[MigrationError, DynamicValue] =
    dynamicMigration(value)

  /**
   * Compose this migration with another, creating a migration from A to C.
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    new Migration(
      dynamicMigration ++ that.dynamicMigration,
      sourceSchema,
      that.targetSchema
    )

  /**
   * Alias for `++`.
   */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /**
   * Get the structural reverse of this migration.
   *
   * The reversed migration transforms from B back to A. Runtime correctness is
   * best-effort - it depends on sufficient default values being available for
   * reverse operations.
   */
  def reverse: Migration[B, A] =
    new Migration(
      dynamicMigration.reverse,
      targetSchema,
      sourceSchema
    )

  /**
   * Check if this migration is empty (no actions).
   */
  def isEmpty: Boolean = dynamicMigration.isEmpty

  /**
   * Check if this migration has actions.
   */
  def nonEmpty: Boolean = dynamicMigration.nonEmpty

  /**
   * Get the list of actions in this migration.
   */
  def actions: Vector[MigrationAction] = dynamicMigration.actions
}

object Migration {

  /**
   * Create an identity migration that performs no changes.
   */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    new Migration(DynamicMigration.empty, schema, schema)

  /**
   * Create a new migration builder for migrating from type A to type B.
   */
  def newBuilder[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](sourceSchema, targetSchema, Vector.empty)

  /**
   * Create a migration from a single action.
   */
  def fromAction[A, B](action: MigrationAction)(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    new Migration(DynamicMigration(action), sourceSchema, targetSchema)

  /**
   * Create a migration from a sequence of actions.
   */
  def fromActions[A, B](actions: MigrationAction*)(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    new Migration(new DynamicMigration(actions.toVector), sourceSchema, targetSchema)

  /**
   * Create a migration from a [[DynamicMigration]].
   */
  def fromDynamic[A, B](
    dynamicMigration: DynamicMigration
  )(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    new Migration(dynamicMigration, sourceSchema, targetSchema)
}

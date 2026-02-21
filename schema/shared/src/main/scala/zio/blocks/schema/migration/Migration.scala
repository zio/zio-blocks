package zio.blocks.schema.migration

import zio.blocks.schema.Schema

/**
 * A typed, user-facing migration from schema `A` to schema `B`.
 *
 * `Migration[A, B]` wraps a `DynamicMigration` with compile-time type
 * information and provides a type-safe API for applying, composing, and
 * reversing migrations.
 *
 * The runtime flow is:
 * `A -> toDynamicValue -> DynamicMigration.apply -> fromDynamicValue -> B`
 *
 * @param dynamicMigration
 *   The untyped, serializable migration core
 * @param sourceSchema
 *   Schema for the source type A
 * @param targetSchema
 *   Schema for the target type B
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /**
   * Applies this migration to a value of type A, producing a value of type B or
   * a MigrationError.
   *
   * Encodes A to DynamicValue, applies the dynamic migration, then decodes the
   * result back to B.
   */
  def apply(value: A): Either[MigrationError, B] = {
    val dynValue = sourceSchema.toDynamicValue(value)
    dynamicMigration.apply(dynValue) match {
      case Right(transformed) =>
        targetSchema.fromDynamicValue(transformed) match {
          case Right(result) => Right(result)
          case Left(err)     =>
            Left(
              MigrationError(
                s"Failed to decode migrated value as target type: ${err.message}",
                zio.blocks.schema.DynamicOptic.root,
                cause = Some(err)
              )
            )
        }
      case Left(err) => Left(err)
    }
  }

  /** Composes this migration with another, producing A -> C. */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(dynamicMigration ++ that.dynamicMigration, sourceSchema, that.targetSchema)

  /** Alias for `++`. */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /**
   * Composes this migration with another in strict mode. Throws if the
   * right-hand migration contains lossy actions.
   *
   * Throws `IllegalArgumentException` if `that` contains lossy actions.
   */
  def composeStrict[C](that: Migration[B, C]): Migration[A, C] = {
    if (that.isLossy) {
      val lossyDetails = that.dynamicMigration.actions.zipWithIndex.collect {
        case (a, i) if a.lossy => s"  [action $i] ${a.getClass.getSimpleName} at ${a.at}"
      }
      throw new IllegalArgumentException(
        s"Cannot compose with lossy migration in strict mode.\n" +
          s"  Lossy actions in right-hand migration:\n${lossyDetails.mkString("\n")}"
      )
    }
    this ++ that
  }

  /**
   * Returns the reverse migration (B -> A) if all actions are reversible.
   * Returns `None` if any action is lossy.
   */
  def reverse: Option[Migration[B, A]] =
    dynamicMigration.reverse.map(dm => Migration(dm, targetSchema, sourceSchema))

  /**
   * Returns the reverse migration or throws if any action is lossy.
   *
   * Throws `IllegalStateException` listing which actions are irreversible.
   */
  def unsafeReverse: Migration[B, A] =
    Migration(dynamicMigration.unsafeReverse, targetSchema, sourceSchema)

  /** Whether any action in this migration is lossy. */
  def isLossy: Boolean = dynamicMigration.isLossy

  /** Indices of all lossy actions. */
  def lossyActionIndices: Vector[Int] = dynamicMigration.lossyActionIndices

  /** Metadata associated with the underlying dynamic migration. */
  def metadata: MigrationMetadata = dynamicMigration.metadata

  /**
   * Returns a human-readable summary of this migration including type info.
   */
  def explain: String = {
    val sourceType = sourceSchema.reflect.typeId.toString
    val targetType = targetSchema.reflect.typeId.toString
    s"Migration[$sourceType -> $targetType]\n${dynamicMigration.explain}"
  }
}

object Migration {

  /** Creates an identity migration that passes values through unchanged. */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration(Vector.empty), schema, schema)
}

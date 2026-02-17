package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, Schema, SchemaError}

/**
 * A typed migration from schema version `A` to schema version `B`.
 *
 * `Migration[A, B]` wraps a `DynamicMigration` together with the source and
 * target `Schema` instances. This provides:
 *
 *   - '''Compile-time type safety''': The `A` and `B` type parameters track
 *     which schema versions are being migrated between.
 *   - '''Runtime validation''': The `migrate` method converts `A` to
 *     `DynamicValue`, applies the untyped migration, then validates and
 *     converts back to `B`.
 *   - '''Schema tracking''': Both schemas are carried for introspection,
 *     validation, and codec generation.
 *
 * == Usage ==
 * {{{
 * case class UserV1(name: String, age: Int)
 * case class UserV2(name: String, age: Int, email: String)
 *
 * val migration = Migration.builder[UserV1, UserV2]
 *   .addField("email", literal[String]("unknown@example.com"))
 *   .build
 * }}}
 */
final case class Migration[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  underlying: DynamicMigration
) {

  /**
   * Migrate a value of type `A` to type `B`.
   *
   * Steps:
   *   1. Convert `A` to `DynamicValue` using `sourceSchema`
   *   2. Apply `DynamicMigration` actions
   *   3. Convert result back to `B` using `targetSchema`
   */
  def migrate(value: A): Either[SchemaError, B] = {
    val dynamic = sourceSchema.toDynamicValue(value)
    underlying.migrate(dynamic).flatMap(targetSchema.fromDynamicValue)
  }

  /**
   * Compose this migration with another, producing `Migration[A, C]`.
   */
  def andThen[C](that: Migration[B, C]): Migration[A, C] =
    Migration(sourceSchema, that.targetSchema, underlying ++ that.underlying)

  /**
   * Compute the structural reverse of this migration, if possible.
   *
   * Returns `Some(Migration[B, A])` if every action is reversible.
   */
  def reverse: Option[Migration[B, A]] =
    underlying.reverse.map(rev => Migration(targetSchema, sourceSchema, rev))

  /**
   * Get the number of migration steps.
   */
  def size: Int = underlying.size
}

object Migration {

  /**
   * Create a `MigrationBuilder` for constructing a typed migration.
   */
  def builder[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](sourceSchema, targetSchema, Vector.empty)

  /**
   * Create an identity migration that passes values through a codec roundtrip.
   * Useful when `A` and `B` share the same dynamic representation.
   */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(schema, schema, DynamicMigration.identity)
}

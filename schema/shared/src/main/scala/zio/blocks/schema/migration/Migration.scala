package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * A typed wrapper around DynamicMigration that enforces source and target
 * schemas.
 *
 * `Migration[A, B]` represents a transformation from schema version A to schema
 * version B. It provides a high-level, type-safe API for running migrations on
 * typed values.
 *
 * ==Structural Type Compatibility==
 *
 * This migration system is designed to work with structural types, enabling
 * schema evolution where old versions exist only at compile time with zero
 * runtime overhead. When `Schema.structural` is implemented (separately
 * specified work), users can define migrations like:
 *
 * {{{
 *   // Old version as structural type (no runtime representation)
 *   type PersonV0 = { val firstName: String; val lastName: String }
 *
 *   // Current version as real case class
 *   case class Person(fullName: String, age: Int)
 *
 *   // Migration between structural and runtime types
 *   implicit val v0Schema: Schema[PersonV0] = Schema.structural[PersonV0]
 *   implicit val personSchema: Schema[Person] = Schema.derived
 *
 *   val migration = Migration.builder[PersonV0, Person]
 *     .addField(_.age, DynamicValue.Primitive(PrimitiveValue.Int(0)))
 *     .build
 * }}}
 *
 * The migration system operates on `DynamicValue`, making it agnostic to
 * whether the source/target types are case classes, structural types, or any
 * other representation that has a `Schema` instance.
 *
 * ==Laws==
 *
 * Identity:
 * {{{
 *   Migration.identity[A].apply(a) == Right(a)
 * }}}
 *
 * Associativity:
 * {{{
 *   (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)
 * }}}
 *
 * Structural Reverse:
 * {{{
 *   m.reverse.reverse == m
 * }}}
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /**
   * Apply this migration to a value of type A.
   */
  def apply(value: A): Either[SchemaError, B] =
    for {
      dynamicValue    <- Right(sourceSchema.toDynamicValue(value))
      migratedDynamic <- dynamicMigration.apply(dynamicValue)
      result          <- targetSchema.fromDynamicValue(migratedDynamic) match {
                  case Right(b)          => Right(b)
                  case Left(schemaError) => Left(schemaError)
                }
    } yield result

  /**
   * Compose this migration with another.
   */
  def andThen[C](that: Migration[B, C]): Migration[A, C] =
    Migration[A, C](
      this.dynamicMigration ++ that.dynamicMigration,
      this.sourceSchema,
      that.targetSchema
    )

  /** Alias for andThen. */
  def ++[C](that: Migration[B, C]): Migration[A, C] = andThen(that)

  /**
   * Structural reverse of this migration. Returns a migration from B to A that
   * structurally undoes this one.
   */
  def reverse: Migration[B, A] =
    Migration[B, A](dynamicMigration.reverse, targetSchema, sourceSchema)

  /** Check if this migration is semantically reversible. */
  def isSemanticReversible: Boolean = dynamicMigration.isSemanticReversible

  /** Get a human-readable description. */
  def describe: String = dynamicMigration.describe
}

object Migration {

  /**
   * Create an identity migration.
   */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration[A, A](DynamicMigration.empty, schema, schema)

  /** Create an empty migration (alias for identity). */
  def empty[A](implicit schema: Schema[A]): Migration[A, A] = identity[A]

  /**
   * Create a migration builder for type-safe migration construction.
   *
   * Example (Scala 3):
   * {{{
   *   val migration = Migration.builder[PersonV0, Person]
   *     .renameField(_.name, _.fullName)
   *     .addField(_.country, SchemaExpr.literal("USA"))
   *     .build
   * }}}
   *
   * Example (Scala 2):
   * {{{
   *   val migration = Migration.builder[PersonV0, Person]
   *     .renameField("name", "fullName")
   *     .addField("country", SchemaExpr.literal("USA"))
   *     .build
   * }}}
   */
  def builder[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    MigrationBuilder[A, B](sourceSchema, targetSchema)
}

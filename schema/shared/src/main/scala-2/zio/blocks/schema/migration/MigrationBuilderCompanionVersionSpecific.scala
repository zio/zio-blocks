package zio.blocks.schema.migration

import zio.blocks.schema._
import TypeLevel._

/**
 * Scala 2 version-specific companion for MigrationBuilder.
 *
 * Returns MigrationBuilder with TNil for Handled and Provided type parameters.
 * Compile-time field tracking uses TList types (like Scala 3 uses Tuple).
 */
trait MigrationBuilderCompanionVersionSpecific {

  /**
   * Creates a new migration builder for transforming from type A to type B.
   *
   * In Scala 2, the Handled and Provided type parameters are TList types
   * that accumulate field names at compile time.
   */
  def newBuilder[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, TNil, TNil] =
    MigrationBuilder(sourceSchema, targetSchema, Vector.empty)

  /**
   * Type alias for a fresh builder with empty tracked fields.
   */
  type Fresh[A, B] = MigrationBuilder[A, B, TNil, TNil]
}

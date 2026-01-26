package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * Scala 2 version-specific companion for MigrationBuilder.
 *
 * Returns MigrationBuilder with Any for Handled and Provided type parameters.
 * Compile-time field tracking is not supported in Scala 2; validation happens at runtime.
 */
trait MigrationBuilderCompanionVersionSpecific {

  /**
   * Creates a new migration builder for transforming from type A to type B.
   *
   * In Scala 2, the Handled and Provided type parameters are always Any,
   * meaning compile-time field tracking is not available. Use .build for runtime validation.
   */
  def newBuilder[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, Any, Any] =
    MigrationBuilder(sourceSchema, targetSchema, Vector.empty)

  /**
   * Type alias for a builder (no compile-time tracking in Scala 2).
   */
  type Fresh[A, B] = MigrationBuilder[A, B, Any, Any]
}

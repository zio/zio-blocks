package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * Scala 3 version-specific companion for MigrationBuilder.
 *
 * Returns MigrationBuilder with EmptyTuple for Handled and Provided type parameters,
 * enabling compile-time tracking of field handling.
 */
trait MigrationBuilderCompanionVersionSpecific {

  /**
   * Creates a new migration builder for transforming from type A to type B.
   *
   * The returned builder has EmptyTuple for both Handled and Provided type parameters,
   * indicating no fields have been handled or provided yet. As builder methods are called,
   * these type parameters will accumulate the field names being handled/provided.
   */
  def newBuilder[A, B](using
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, EmptyTuple, EmptyTuple] =
    MigrationBuilder(sourceSchema, targetSchema, Vector.empty)

  /**
   * Type alias for a builder with no fields handled or provided yet.
   */
  type Fresh[A, B] = MigrationBuilder[A, B, EmptyTuple, EmptyTuple]
}

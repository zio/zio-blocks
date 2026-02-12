package zio.blocks.schema.migration

import scala.language.implicitConversions
import zio.blocks.schema._
import TypeLevel._

/**
 * Scala 2 version-specific companion for MigrationBuilder.
 *
 * Returns MigrationBuilder with TNil for Handled and Provided type parameters.
 * Compile-time field tracking uses TList types (like Scala 3 uses Tuple).
 */
private[migration] trait MigrationBuilderCompanionVersionSpecific {

  /**
   * Creates a new migration builder for transforming from type A to type B.
   *
   * In Scala 2, the Handled and Provided type parameters are TList types that
   * accumulate field names at compile time.
   */
  def newBuilder[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, TNil, TNil] =
    MigrationBuilder(sourceSchema, targetSchema, Vector.empty)

  implicit def toSyntax[A, B, Handled <: TList, Provided <: TList](
    builder: MigrationBuilder[A, B, Handled, Provided]
  ): MigrationBuilderSyntax[A, B, Handled, Provided] =
    new MigrationBuilderSyntax[A, B, Handled, Provided](builder)

  /**
   * Type alias for a fresh builder with empty tracked fields.
   */
  type Fresh[A, B] = MigrationBuilder[A, B, TNil, TNil]
}

package zio.blocks.schema.migration

import scala.compiletime.{summonFrom, summonInline}
import zio.blocks.schema.migration.TypeLevel._
import zio.blocks.schema.migration.FieldExtraction._

/**
 * Compile-time proof that a migration is complete.
 *
 * A migration from A to B is complete when:
 *   - All "removed" fields (in A but not in B) are handled
 *   - All "added" fields (in B but not in A) are provided
 *
 * Fields that exist in both A and B (unchanged fields) are automatically
 * handled/provided.
 *
 * Type parameters:
 *   - A: Source type
 *   - B: Target type
 *   - Handled: Tuple of field names that have been handled (from source)
 *   - Provided: Tuple of field names that have been provided (for target)
 */
sealed trait ValidationProof[A, B, Handled <: Tuple, Provided <: Tuple]

object ValidationProof {

  /**
   * Implementation class for ValidationProof. Public because transparent inline
   * given instances require it.
   */
  final class Impl[A, B, Handled <: Tuple, Provided <: Tuple] extends ValidationProof[A, B, Handled, Provided]

  /**
   * Type alias for required fields to handle (fields in A that are not in B).
   */
  type RequiredHandled[A, B, FieldsA <: Tuple, FieldsB <: Tuple] =
    Difference[FieldsA, FieldsB]

  /**
   * Type alias for required fields to provide (fields in B that are not in A).
   */
  type RequiredProvided[A, B, FieldsA <: Tuple, FieldsB <: Tuple] =
    Difference[FieldsB, FieldsA]

  /**
   * Derive a ValidationProof when the migration is complete.
   *
   * This given instance only exists when:
   *   - IsSubset[RequiredHandled, Handled] =:= true
   *   - IsSubset[RequiredProvided, Provided] =:= true
   */
  transparent inline given derive[A, B, Handled <: Tuple, Provided <: Tuple](using
    fnA: FieldNames[A],
    fnB: FieldNames[B]
  ): ValidationProof[A, B, Handled, Provided] =
    summonFrom { case _: (IsSubset[Difference[fnA.Labels, fnB.Labels], Handled] =:= true) =>
      summonFrom { case _: (IsSubset[Difference[fnB.Labels, fnA.Labels], Provided] =:= true) =>
        new Impl[A, B, Handled, Provided]
      }
    }

  /**
   * Helper type to compute whether a migration is valid.
   */
  type IsValid[A, B, Handled <: Tuple, Provided <: Tuple, FieldsA <: Tuple, FieldsB <: Tuple] =
    (IsSubset[Difference[FieldsA, FieldsB], Handled], IsSubset[Difference[FieldsB, FieldsA], Provided]) match {
      case (true, true) => true
      case _            => false
    }

  /**
   * Explicitly summon a validation proof with better error messages.
   *
   * This is an alternative way to get a proof that provides more helpful
   * compile error messages when validation fails.
   */
  inline def require[A, B, Handled <: Tuple, Provided <: Tuple](using
    fnA: FieldNames[A],
    fnB: FieldNames[B]
  ): ValidationProof[A, B, Handled, Provided] = {
    summonInline[IsSubset[Difference[fnA.Labels, fnB.Labels], Handled] =:= true]
    summonInline[IsSubset[Difference[fnB.Labels, fnA.Labels], Provided] =:= true]
    new Impl[A, B, Handled, Provided]
  }
}

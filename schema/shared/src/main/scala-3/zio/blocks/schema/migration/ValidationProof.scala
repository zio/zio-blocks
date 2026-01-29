package zio.blocks.schema.migration

import scala.compiletime.{summonFrom, summonInline}
import zio.blocks.schema.migration.TypeLevel._
import zio.blocks.schema.migration.FieldExtraction._

/**
 * Compile-time proof that a migration is complete.
 *
 * A migration from A to B is complete when:
 *   - All "removed" paths (in A but not in B) are handled
 *   - All "added" paths (in B but not in A) are provided
 *
 * Paths that exist in both A and B (unchanged paths) are automatically
 * handled/provided. This includes nested paths like "address.street".
 *
 * Type parameters:
 *   - A: Source type
 *   - B: Target type
 *   - Handled: Tuple of field paths that have been handled (from source)
 *   - Provided: Tuple of field paths that have been provided (for target)
 */
sealed trait ValidationProof[A, B, Handled <: Tuple, Provided <: Tuple]

object ValidationProof {

  /**
   * Implementation class for ValidationProof. Public because transparent inline
   * given instances require it.
   */
  final class Impl[A, B, Handled <: Tuple, Provided <: Tuple] extends ValidationProof[A, B, Handled, Provided]

  /**
   * Type alias for required paths to handle (paths in A that are not in B).
   */
  type RequiredHandled[A, B, PathsA <: Tuple, PathsB <: Tuple] =
    Difference[PathsA, PathsB]

  /**
   * Type alias for required paths to provide (paths in B that are not in A).
   */
  type RequiredProvided[A, B, PathsA <: Tuple, PathsB <: Tuple] =
    Difference[PathsB, PathsA]

  /**
   * Derive a ValidationProof when the migration is complete.
   *
   * This given instance only exists when:
   *   - IsSubset[RequiredHandled, Handled] =:= true
   *   - IsSubset[RequiredProvided, Provided] =:= true
   *
   * Uses FieldPaths to extract full nested paths (e.g., "address.street")
   * rather than just top-level field names.
   */
  transparent inline given derive[A, B, Handled <: Tuple, Provided <: Tuple](using
    fpA: FieldPaths[A],
    fpB: FieldPaths[B]
  ): ValidationProof[A, B, Handled, Provided] =
    summonFrom { case _: (IsSubset[Difference[fpA.Paths, fpB.Paths], Handled] =:= true) =>
      summonFrom { case _: (IsSubset[Difference[fpB.Paths, fpA.Paths], Provided] =:= true) =>
        new Impl[A, B, Handled, Provided]
      }
    }

  /**
   * Helper type to compute whether a migration is valid.
   */
  type IsValid[A, B, Handled <: Tuple, Provided <: Tuple, PathsA <: Tuple, PathsB <: Tuple] =
    (IsSubset[Difference[PathsA, PathsB], Handled], IsSubset[Difference[PathsB, PathsA], Provided]) match {
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
    fpA: FieldPaths[A],
    fpB: FieldPaths[B]
  ): ValidationProof[A, B, Handled, Provided] = {
    summonInline[IsSubset[Difference[fpA.Paths, fpB.Paths], Handled] =:= true]
    summonInline[IsSubset[Difference[fpB.Paths, fpA.Paths], Provided] =:= true]
    new Impl[A, B, Handled, Provided]
  }
}

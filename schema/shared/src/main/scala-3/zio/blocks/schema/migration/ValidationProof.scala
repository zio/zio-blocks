package zio.blocks.schema.migration

import scala.compiletime.{summonFrom, summonInline}
import zio.blocks.schema.migration.TypeLevel._
import zio.blocks.schema.migration.FieldExtraction._

/**
 * Compile-time proof that a migration is complete.
 *
 * A migration from A to B is complete when:
 *   - All "removed" field paths (in A but not in B) are handled
 *   - All "added" field paths (in B but not in A) are provided
 *   - All "removed" case names (in A but not in B) are handled
 *   - All "added" case names (in B but not in A) are provided
 *
 * Paths that exist in both A and B (unchanged paths) are automatically
 * handled/provided. This includes nested paths like "address.street" and
 * case names like "case:Success".
 *
 * Type parameters:
 *   - A: Source type
 *   - B: Target type
 *   - Handled: Tuple of field paths and case names that have been handled (from
 *     source). Case names are prefixed with "case:" (e.g., "case:OldCase").
 *   - Provided: Tuple of field paths and case names that have been provided
 *     (for target). Case names are prefixed with "case:" (e.g., "case:NewCase").
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
   * Concatenate two tuples at the type level.
   */
  type Concat[A <: Tuple, B <: Tuple] <: Tuple = A match {
    case EmptyTuple => B
    case h *: t     => h *: Concat[t, B]
  }

  /**
   * Derive a ValidationProof when the migration is complete.
   *
   * This given instance only exists when:
   *   - All removed field paths are in Handled
   *   - All added field paths are in Provided
   *   - All removed case names are in Handled (prefixed with "case:")
   *   - All added case names are in Provided (prefixed with "case:")
   *
   * Uses FieldPaths to extract full nested paths (e.g., "address.street")
   * and CasePaths to extract case names (e.g., "case:Success").
   */
  transparent inline given derive[A, B, Handled <: Tuple, Provided <: Tuple](using
    fpA: FieldPaths[A],
    fpB: FieldPaths[B],
    cpA: CasePaths[A],
    cpB: CasePaths[B]
  ): ValidationProof[A, B, Handled, Provided] =
    // Validate field paths
    summonFrom { case _: (IsSubset[Difference[fpA.Paths, fpB.Paths], Handled] =:= true) =>
      summonFrom { case _: (IsSubset[Difference[fpB.Paths, fpA.Paths], Provided] =:= true) =>
        // Validate case names
        summonFrom { case _: (IsSubset[Difference[cpA.Cases, cpB.Cases], Handled] =:= true) =>
          summonFrom { case _: (IsSubset[Difference[cpB.Cases, cpA.Cases], Provided] =:= true) =>
            new Impl[A, B, Handled, Provided]
          }
        }
      }
    }

  /**
   * Helper type to compute whether a migration is valid. Checks both field
   * paths and case names.
   */
  type IsValid[
    A,
    B,
    Handled <: Tuple,
    Provided <: Tuple,
    PathsA <: Tuple,
    PathsB <: Tuple,
    CasesA <: Tuple,
    CasesB <: Tuple
  ] =
    (
      IsSubset[Difference[PathsA, PathsB], Handled],
      IsSubset[Difference[PathsB, PathsA], Provided],
      IsSubset[Difference[CasesA, CasesB], Handled],
      IsSubset[Difference[CasesB, CasesA], Provided]
    ) match {
      case (true, true, true, true) => true
      case _                        => false
    }

  /**
   * Explicitly summon a validation proof with better error messages.
   *
   * This is an alternative way to get a proof that provides more helpful
   * compile error messages when validation fails.
   */
  inline def require[A, B, Handled <: Tuple, Provided <: Tuple](using
    fpA: FieldPaths[A],
    fpB: FieldPaths[B],
    cpA: CasePaths[A],
    cpB: CasePaths[B]
  ): ValidationProof[A, B, Handled, Provided] = {
    summonInline[IsSubset[Difference[fpA.Paths, fpB.Paths], Handled] =:= true]
    summonInline[IsSubset[Difference[fpB.Paths, fpA.Paths], Provided] =:= true]
    summonInline[IsSubset[Difference[cpA.Cases, cpB.Cases], Handled] =:= true]
    summonInline[IsSubset[Difference[cpB.Cases, cpA.Cases], Provided] =:= true]
    new Impl[A, B, Handled, Provided]
  }
}

package zio.blocks.schema.migration

/**
 * Type-level operations on Tuples for compile-time migration validation.
 *
 * These match types enable tracking handled and provided fields at the type
 * level, allowing the compiler to verify migration completeness.
 *
 * Note: Additional type-level operations (Union, Intersect, TupleEquals, Size)
 * could be implemented using match types if needed for advanced use cases.
 */
object TypeLevel {

  /**
   * Check if tuple T contains element X.
   *
   * {{{
   * Contains[("a", "b", "c"), "b"] =:= true
   * Contains[("a", "b", "c"), "d"] =:= false
   * Contains[EmptyTuple, "a"] =:= false
   * }}}
   */
  type Contains[T <: Tuple, X] <: Boolean = T match {
    case EmptyTuple => false
    case X *: _     => true
    case _ *: tail  => Contains[tail, X]
  }

  /**
   * Check if all elements of A are contained in B (A ⊆ B).
   *
   * {{{
   * IsSubset[("a"), ("a", "b")] =:= true
   * IsSubset[("a", "c"), ("a", "b")] =:= false
   * IsSubset[EmptyTuple, ("a", "b")] =:= true
   * }}}
   */
  type IsSubset[A <: Tuple, B <: Tuple] <: Boolean = A match {
    case EmptyTuple => true
    case h *: tail  =>
      Contains[B, h] match {
        case true  => IsSubset[tail, B]
        case false => false
      }
  }

  /**
   * Elements in A that are not in B.
   *
   * {{{
   * Difference[("a", "b", "c"), ("b")] produces a tuple containing "a", "c"
   * Difference[("a", "b"), ("c")] =:= ("a", "b")
   * Difference[("a"), ("a")] =:= EmptyTuple
   * }}}
   */
  type Difference[A <: Tuple, B <: Tuple] <: Tuple = A match {
    case EmptyTuple => EmptyTuple
    case h *: tail  =>
      Contains[B, h] match {
        case true  => Difference[tail, B]
        case false => h *: Difference[tail, B]
      }
  }

  /**
   * Type-level evidence that A is a subset of B. Used for compile-time
   * validation proofs.
   */
  sealed trait SubsetEvidence[A <: Tuple, B <: Tuple]

  object SubsetEvidence {
    given emptySubset[B <: Tuple]: SubsetEvidence[EmptyTuple, B] = new SubsetEvidence[EmptyTuple, B] {}

    given inductiveSubset[H, T <: Tuple, B <: Tuple](using
      ev1: Contains[B, H] =:= true,
      ev2: SubsetEvidence[T, B]
    ): SubsetEvidence[H *: T, B] = new SubsetEvidence[H *: T, B] {}
  }
}

package zio.blocks.schema.migration

/**
 * Type-level operations on Tuples for compile-time migration validation.
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
}

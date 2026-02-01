package zio.blocks.schema.migration

/**
 * Type-level operations for compile-time migration validation.
 *
 * This object provides type-level set operations on tuples, enabling the
 * migration system to verify at compile time that all required field paths have
 * been handled or provided.
 *
 * Key operations:
 *   - IsSubset: Check if all elements of one tuple are in another
 *   - Difference: Compute elements in A that are not in B
 *   - Contains: Check if a tuple contains a specific element
 *   - Union: Combine two tuples with deduplication
 */
object TypeLevel {

  /**
   * Type-level check: is element E contained in tuple T?
   *
   * Returns true if E is found anywhere in T, false otherwise.
   */
  type Contains[T <: Tuple, E] <: Boolean = T match {
    case EmptyTuple => false
    case E *: _     => true
    case _ *: tail  => Contains[tail, E]
  }

  /**
   * Type-level check: is A a subset of B?
   *
   * Returns true if every element in A is also in B. An empty tuple is a subset
   * of any tuple.
   */
  type IsSubset[A <: Tuple, B <: Tuple] <: Boolean = A match {
    case EmptyTuple => true
    case h *: t     =>
      Contains[B, h] match {
        case true  => IsSubset[t, B]
        case false => false
      }
  }

  /**
   * Type-level set difference: elements in A that are not in B.
   *
   * Returns a tuple containing only elements from A that do not appear in B.
   */
  type Difference[A <: Tuple, B <: Tuple] <: Tuple = A match {
    case EmptyTuple => EmptyTuple
    case h *: t     =>
      Contains[B, h] match {
        case true  => Difference[t, B]
        case false => h *: Difference[t, B]
      }
  }

  /**
   * Type-level union: combine two tuples, keeping all elements from both.
   *
   * Elements from B that already appear in A are not duplicated.
   */
  type Union[A <: Tuple, B <: Tuple] <: Tuple = B match {
    case EmptyTuple => A
    case h *: t     =>
      Contains[A, h] match {
        case true  => Union[A, t]
        case false => Union[Tuple.Append[A, h], t]
      }
  }

  /**
   * Type-level intersection: elements present in both A and B.
   */
  type Intersection[A <: Tuple, B <: Tuple] <: Tuple = A match {
    case EmptyTuple => EmptyTuple
    case h *: t     =>
      Contains[B, h] match {
        case true  => h *: Intersection[t, B]
        case false => Intersection[t, B]
      }
  }

  /**
   * Type-level size: count elements in a tuple.
   *
   * Note: Scala 3's match types don't support arithmetic operations. Use
   * Tuple.Size from the standard library instead.
   */
  type Size[T <: Tuple] = Tuple.Size[T]

  /**
   * Type-level append: add an element to the end of a tuple.
   *
   * Delegates to Tuple.Append from the standard library.
   */
  type Append[T <: Tuple, E] = Tuple.Append[T, E]

  /**
   * Type-level prepend: add an element to the front of a tuple.
   */
  type Prepend[E, T <: Tuple] = E *: T

  /**
   * Type-level reverse: reverse the order of tuple elements.
   */
  type Reverse[T <: Tuple] <: Tuple = T match {
    case EmptyTuple => EmptyTuple
    case h *: t     => Tuple.Concat[Reverse[t], h *: EmptyTuple]
  }

  /**
   * Type-level concatenation: combine two tuples sequentially.
   */
  type Concat[A <: Tuple, B <: Tuple] = Tuple.Concat[A, B]

  /**
   * Type-level check: are two tuples equal (same elements in same order)?
   */
  type Equals[A <: Tuple, B <: Tuple] <: Boolean = (A, B) match {
    case (EmptyTuple, EmptyTuple) => true
    case (h1 *: t1, h2 *: t2)     =>
      h1 match {
        case h2 => Equals[t1, t2]
        case _  => false
      }
    case _ => false
  }

  /**
   * Type-level check: are two tuples set-equal (same elements, any order)?
   */
  type SetEquals[A <: Tuple, B <: Tuple] <: Boolean =
    (IsSubset[A, B], IsSubset[B, A]) match {
      case (true, true) => true
      case _            => false
    }

  /**
   * Type-level filter: keep only elements matching a predicate.
   *
   * This is more complex and requires a type-level predicate function. For
   * common cases, use Intersection or Difference instead.
   */
  type Filter[T <: Tuple, Keep <: Tuple] = Intersection[T, Keep]

  /**
   * Type-level isEmpty: check if a tuple is empty.
   */
  type IsEmpty[T <: Tuple] <: Boolean = T match {
    case EmptyTuple => true
    case _          => false
  }

  /**
   * Type-level head: get the first element of a tuple.
   */
  type Head[T <: Tuple] = T match {
    case h *: _ => h
  }

  /**
   * Type-level tail: get all elements except the first.
   */
  type Tail[T <: Tuple] <: Tuple = T match {
    case _ *: t => t
  }

  /**
   * Type-level last: get the last element of a tuple.
   */
  type Last[T <: Tuple] = T match {
    case h *: EmptyTuple => h
    case _ *: t          => Last[t]
  }

  /**
   * Type-level evidence that A is a subset of B.
   *
   * Used for compile-time validation proofs. An instance of SubsetEvidence[A,
   * B] can only be derived when every element in A is also in B.
   */
  sealed trait SubsetEvidence[A <: Tuple, B <: Tuple]

  object SubsetEvidence {

    /**
     * Base case: empty tuple is a subset of any tuple.
     */
    given emptySubset[B <: Tuple]: SubsetEvidence[EmptyTuple, B] =
      new SubsetEvidence[EmptyTuple, B] {}

    /**
     * Inductive case: (H *: T) is a subset of B when:
     *   - H is contained in B
     *   - T is a subset of B
     */
    given inductiveSubset[H, T <: Tuple, B <: Tuple](using
      ev1: Contains[B, H] =:= true,
      ev2: SubsetEvidence[T, B]
    ): SubsetEvidence[H *: T, B] =
      new SubsetEvidence[H *: T, B] {}
  }

  /**
   * Type-level evidence that a tuple contains a specific element.
   *
   * Provides a compile-time proof that element E exists in tuple T.
   */
  sealed trait ContainsEvidence[T <: Tuple, E]

  object ContainsEvidence {

    /**
     * Found case: element is at the head of the tuple.
     */
    given foundHead[E, T <: Tuple]: ContainsEvidence[E *: T, E] =
      new ContainsEvidence[E *: T, E] {}

    /**
     * Inductive case: element is somewhere in the tail.
     */
    given foundTail[H, T <: Tuple, E](using
      ev: ContainsEvidence[T, E]
    ): ContainsEvidence[H *: T, E] =
      new ContainsEvidence[H *: T, E] {}
  }

  /**
   * Type-level evidence that two tuples are set-equal.
   *
   * Provides a compile-time proof that A and B contain the same elements
   * (regardless of order).
   */
  sealed trait SetEqualEvidence[A <: Tuple, B <: Tuple]

  object SetEqualEvidence {

    /**
     * Two tuples are set-equal when they are mutual subsets.
     */
    given derive[A <: Tuple, B <: Tuple](using
      ev1: SubsetEvidence[A, B],
      ev2: SubsetEvidence[B, A]
    ): SetEqualEvidence[A, B] =
      new SetEqualEvidence[A, B] {}
  }
}

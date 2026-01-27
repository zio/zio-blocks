package zio.blocks.schema.migration

import scala.annotation.nowarn

/**
 * Type-level operations on type-level lists for compile-time migration
 * validation (Scala 2).
 *
 * In Scala 3, these operations use match types on Tuples. In Scala 2, we use:
 *   - Custom TList type (TNil, TCons) instead of Tuple
 *   - Implicit resolution instead of match types
 *
 * The key insight: implicit existence = proof. If an implicit can be found, the
 * type-level proposition is true. If not, compilation fails.
 */
object TypeLevel {

  // ============================================================================
  // Type-Level List
  // ============================================================================

  /** Type-level heterogeneous list. Analogous to Scala 3's Tuple. */
  sealed trait TList

  /** Empty type-level list. Analogous to EmptyTuple. */
  sealed trait TNil extends TList

  /** Cons cell for type-level list. Analogous to H *: T in Scala 3. */
  sealed trait TCons[+H, +T <: TList] extends TList

  /** Convenient type alias for cons, mimicking :: syntax */
  type ::[+H, +T <: TList] = TCons[H, T]

  // ============================================================================
  // Contains - Evidence that list L contains element X
  // ============================================================================

  /**
   * Type-level evidence that list L contains element X. An implicit instance
   * exists iff X is in L.
   */
  sealed trait Contains[L <: TList, X]

  object Contains extends ContainsLowPriority {

    /** Base case: X is at the head of the list */
    implicit def containsHead[X, T <: TList]: Contains[X :: T, X] =
      instance.asInstanceOf[Contains[X :: T, X]]
  }

  trait ContainsLowPriority {
    protected val instance: Contains[_ <: TList, _] = new Contains[TNil, Nothing] {}

    /** Inductive case: X is somewhere in the tail */
    implicit def containsTail[H, T <: TList, X](implicit ev: Contains[T, X]): Contains[H :: T, X] =
      instance.asInstanceOf[Contains[H :: T, X]]
  }

  // ============================================================================
  // NotContains - Evidence that list L does NOT contain element X
  // ============================================================================

  /**
   * Type-level evidence that list L does NOT contain element X. Used internally
   * for Difference computation.
   */
  sealed trait NotContains[L <: TList, X]

  object NotContains extends NotContainsLowPriority {

    /** Base case: empty list contains nothing */
    implicit def notContainsNil[X]: NotContains[TNil, X] =
      instance.asInstanceOf[NotContains[TNil, X]]
  }

  trait NotContainsLowPriority {
    protected val instance: NotContains[_ <: TList, _] = new NotContains[TNil, Nothing] {}

    /**
     * Inductive case: H :: T does not contain X if:
     *   - H is not X (ensured by implicit not found for H =:= X)
     *   - T does not contain X
     *
     * We use implicit ambiguity to ensure H != X: If H =:= X, the ambiguous
     * implicit below will conflict.
     */
    implicit def notContainsCons[H, T <: TList, X](implicit
      ev: NotContains[T, X],
      neq: H =:!= X
    ): NotContains[H :: T, X] =
      instance.asInstanceOf[NotContains[H :: T, X]]
  }

  // ============================================================================
  // Type Inequality (=:!=)
  // ============================================================================

  /**
   * Evidence that types A and B are NOT equal. Uses the "ambiguous implicit"
   * trick.
   */
  sealed trait =:!=[A, B]

  object =:!= extends NeqLowPriority {

    /** Ambiguous implicit when A =:= B - causes implicit search to fail */
    implicit def neqAmbig1[A]: =:!=[A, A] = sys.error("unreachable")
    implicit def neqAmbig2[A]: =:!=[A, A] = sys.error("unreachable")
  }

  trait NeqLowPriority {

    /** Default case: A != B */
    implicit def neq[A, B]: A =:!= B     = instance.asInstanceOf[A =:!= B]
    protected val instance: Any =:!= Any = new =:!=[Any, Any] {}
  }

  // ============================================================================
  // IsSubset - Evidence that A is a subset of B
  // ============================================================================

  /**
   * Type-level evidence that all elements of A are contained in B (A ⊆ B). This
   * is the key typeclass for ValidationProof.
   */
  sealed trait IsSubset[A <: TList, B <: TList]

  object IsSubset extends IsSubsetLowPriority {

    /** Base case: empty set is subset of everything */
    implicit def subsetNil[B <: TList]: IsSubset[TNil, B] =
      instance.asInstanceOf[IsSubset[TNil, B]]
  }

  trait IsSubsetLowPriority {
    protected val instance: IsSubset[_ <: TList, _ <: TList] = new IsSubset[TNil, TNil] {}

    /** Inductive case: H :: T ⊆ B if H ∈ B and T ⊆ B */
    implicit def subsetCons[H, T <: TList, B <: TList](implicit
      containsH: Contains[B, H],
      subsetT: IsSubset[T, B]
    ): IsSubset[H :: T, B] =
      instance.asInstanceOf[IsSubset[H :: T, B]]
  }

  /** Alias for IsSubset, matching Scala 3 naming */
  type SubsetEvidence[A <: TList, B <: TList] = IsSubset[A, B]

  // ============================================================================
  // Append - Add element to end of list
  // ============================================================================

  /**
   * Appends element X to the end of list L, producing Out. Used by builder
   * methods to track fields.
   */
  sealed trait Append[L <: TList, X] {
    type Out <: TList
  }

  object Append {
    type Aux[L <: TList, X, O <: TList] = Append[L, X] { type Out = O }

    /** Base case: appending to empty list */
    implicit def appendNil[X]: Append.Aux[TNil, X, X :: TNil] =
      instance.asInstanceOf[Append.Aux[TNil, X, X :: TNil]]

    /** Inductive case: append to non-empty list */
    @nowarn("msg=never used")
    implicit def appendCons[H, T <: TList, X, TO <: TList](implicit
      ev: Append.Aux[T, X, TO]
    ): Append.Aux[H :: T, X, H :: TO] =
      instance.asInstanceOf[Append.Aux[H :: T, X, H :: TO]]

    private val instance: Append[TNil, Nothing] = new Append[TNil, Nothing] { type Out = Nothing :: TNil }
  }

  // ============================================================================
  // Prepend - Add element to front of list (simpler than Append)
  // ============================================================================

  /**
   * Prepends element X to the front of list L. This is O(1) at the type level,
   * unlike Append which is O(n).
   */
  sealed trait Prepend[X, L <: TList] {
    type Out <: TList
  }

  object Prepend {
    type Aux[X, L <: TList, O <: TList] = Prepend[X, L] { type Out = O }

    implicit def prepend[X, L <: TList]: Prepend.Aux[X, L, X :: L] =
      instance.asInstanceOf[Prepend.Aux[X, L, X :: L]]

    private val instance: Prepend[Nothing, TNil] = new Prepend[Nothing, TNil] { type Out = Nothing :: TNil }
  }

  // ============================================================================
  // Difference - Elements in A but not in B
  // ============================================================================

  /**
   * Computes the set difference A \ B (elements in A that are not in B).
   */
  sealed trait Difference[A <: TList, B <: TList] {
    type Out <: TList
  }

  object Difference extends DifferenceLowPriority {
    type Aux[A <: TList, B <: TList, O <: TList] = Difference[A, B] { type Out = O }

    /** Base case: difference of empty list is empty */
    implicit def differenceNil[B <: TList]: Difference.Aux[TNil, B, TNil] =
      instance.asInstanceOf[Difference.Aux[TNil, B, TNil]]

    /** Case: H is in B, so exclude it from result */
    @nowarn("msg=never used")
    implicit def differenceConsExclude[H, T <: TList, B <: TList, TO <: TList](implicit
      containsH: Contains[B, H],
      diffT: Difference.Aux[T, B, TO]
    ): Difference.Aux[H :: T, B, TO] =
      instance.asInstanceOf[Difference.Aux[H :: T, B, TO]]
  }

  trait DifferenceLowPriority {
    protected val instance: Difference[TNil, TNil] = new Difference[TNil, TNil] { type Out = TNil }

    /** Case: H is NOT in B, so include it in result */
    @nowarn("msg=never used")
    implicit def differenceConsInclude[H, T <: TList, B <: TList, TO <: TList](implicit
      notContainsH: NotContains[B, H],
      diffT: Difference.Aux[T, B, TO]
    ): Difference.Aux[H :: T, B, H :: TO] =
      instance.asInstanceOf[Difference.Aux[H :: T, B, H :: TO]]
  }

  // ============================================================================
  // Intersect - Elements in both A and B
  // ============================================================================

  /**
   * Computes the set intersection A ∩ B (elements in both A and B).
   */
  sealed trait Intersect[A <: TList, B <: TList] {
    type Out <: TList
  }

  object Intersect extends IntersectLowPriority {
    type Aux[A <: TList, B <: TList, O <: TList] = Intersect[A, B] { type Out = O }

    /** Base case: intersection with empty list is empty */
    implicit def intersectNil[B <: TList]: Intersect.Aux[TNil, B, TNil] =
      instance.asInstanceOf[Intersect.Aux[TNil, B, TNil]]

    /** Case: H is in B, so include it in result */
    @nowarn("msg=never used")
    implicit def intersectConsInclude[H, T <: TList, B <: TList, TO <: TList](implicit
      containsH: Contains[B, H],
      intT: Intersect.Aux[T, B, TO]
    ): Intersect.Aux[H :: T, B, H :: TO] =
      instance.asInstanceOf[Intersect.Aux[H :: T, B, H :: TO]]
  }

  trait IntersectLowPriority {
    protected val instance: Intersect[TNil, TNil] = new Intersect[TNil, TNil] { type Out = TNil }

    /** Case: H is NOT in B, so exclude it from result */
    @nowarn("msg=never used")
    implicit def intersectConsExclude[H, T <: TList, B <: TList, TO <: TList](implicit
      notContainsH: NotContains[B, H],
      intT: Intersect.Aux[T, B, TO]
    ): Intersect.Aux[H :: T, B, TO] =
      instance.asInstanceOf[Intersect.Aux[H :: T, B, TO]]
  }

  // ============================================================================
  // Union - Elements in either A or B
  // ============================================================================

  /**
   * Computes the set union A ∪ B (all elements from both, no duplicates).
   */
  sealed trait Union[A <: TList, B <: TList] {
    type Out <: TList
  }

  object Union extends UnionLowPriority {
    type Aux[A <: TList, B <: TList, O <: TList] = Union[A, B] { type Out = O }

    /** Base case: union with empty B is just A */
    implicit def unionNilB[A <: TList]: Union.Aux[A, TNil, A] =
      instance.asInstanceOf[Union.Aux[A, TNil, A]]
  }

  trait UnionLowPriority extends UnionLowPriority2 {

    /** Case: H from B is already in A, skip it */
    @nowarn("msg=never used")
    implicit def unionConsSkip[A <: TList, H, T <: TList, UTO <: TList](implicit
      containsH: Contains[A, H],
      unionT: Union.Aux[A, T, UTO]
    ): Union.Aux[A, H :: T, UTO] =
      instance.asInstanceOf[Union.Aux[A, H :: T, UTO]]
  }

  trait UnionLowPriority2 {
    protected val instance: Union[TNil, TNil] = new Union[TNil, TNil] { type Out = TNil }

    /** Case: H from B is NOT in A, add it */
    @nowarn("msg=never used")
    implicit def unionConsAdd[A <: TList, H, T <: TList, UTO <: TList](implicit
      notContainsH: NotContains[A, H],
      unionT: Union.Aux[H :: A, T, UTO]
    ): Union.Aux[A, H :: T, UTO] =
      instance.asInstanceOf[Union.Aux[A, H :: T, UTO]]
  }

  // ============================================================================
  // Size - Count elements in list
  // ============================================================================

  /**
   * Computes the size of a type-level list. Uses value-level Int with type
   * refinement.
   */
  sealed trait Size[L <: TList] {
    type N <: Int
    def value: Int
  }

  object Size {
    type Aux[L <: TList, N0 <: Int] = Size[L] { type N = N0 }

    implicit val sizeNil: Size.Aux[TNil, 0] = new Size[TNil] {
      type N = 0
      def value: Int = 0
    }

    implicit def sizeCons[H, T <: TList](implicit sizeT: Size[T]): Size[H :: T] =
      new Size[H :: T] {
        type N = Int // We lose the precise type here, but keep runtime value
        def value: Int = 1 + sizeT.value
      }
  }

  // ============================================================================
  // TupleEquals - Bidirectional subset check
  // ============================================================================

  /**
   * Evidence that two type-level lists contain the same elements
   * (order-independent).
   */
  sealed trait TListEquals[A <: TList, B <: TList]

  object TListEquals {
    implicit def tlistEquals[A <: TList, B <: TList](implicit
      aSubB: IsSubset[A, B],
      bSubA: IsSubset[B, A]
    ): TListEquals[A, B] = instance.asInstanceOf[TListEquals[A, B]]

    private val instance: TListEquals[TNil, TNil] = new TListEquals[TNil, TNil] {}
  }

  // ============================================================================
  // Helper type aliases for cleaner syntax
  // ============================================================================

  /** Single-element list */
  type TList1[A] = A :: TNil

  /** Two-element list */
  type TList2[A, B] = A :: B :: TNil

  /** Three-element list */
  type TList3[A, B, C] = A :: B :: C :: TNil
}

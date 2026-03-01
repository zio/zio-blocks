package zio.blocks.schema.migration

/**
 * Type-level operations for tracking field sets in migrations (Scala 2).
 *
 * In Scala 2, we use type classes with dependent types to simulate type-level
 * computation. Field sets are represented as HList-like structures using :: and
 * HNil.
 *
 * These operations are used by [[MigrationBuilder]] to track which fields have
 * been handled during migration construction.
 */
object FieldSet {

  // ─────────────────────────────────────────────────────────────────────────
  // HList-like structure for field names
  // ─────────────────────────────────────────────────────────────────────────

  /** Empty field set */
  sealed trait HNil

  /** Non-empty field set: head :: tail */
  sealed trait ::[+H, +T]

  /** Singleton instance for HNil */
  case object HNil extends HNil

  // ─────────────────────────────────────────────────────────────────────────
  // Remove operation
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Type class for removing a field name from a field set.
   *
   * @tparam S
   *   The field set (HList of string literal types)
   * @tparam Name
   *   The field name to remove
   */
  trait Remove[S, Name] {

    /** The resulting field set with Name removed */
    type Out
  }

  trait LowPriorityRemove {

    /**
     * Removing from tail (head doesn't match) - lower priority than removeHead
     */
    implicit def removeTail[Name, Head, Tail, TailOut](implicit
      @annotation.unused ev: Remove.Aux[Tail, Name, TailOut]
    ): Remove.Aux[Head :: Tail, Name, Head :: TailOut] =
      new Remove[Head :: Tail, Name] { type Out = Head :: TailOut }
  }

  object Remove extends LowPriorityRemove {
    type Aux[S, Name, O] = Remove[S, Name] { type Out = O }

    /** Removing from empty set yields empty set */
    implicit def removeFromEmpty[Name]: Aux[HNil, Name, HNil] =
      new Remove[HNil, Name] { type Out = HNil }

    /** Removing head element - higher priority than removeTail */
    implicit def removeHead[Name, Tail]: Aux[Name :: Tail, Name, Tail] =
      new Remove[Name :: Tail, Name] { type Out = Tail }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Contains operation
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Evidence that a field name exists in a field set.
   *
   * If an implicit Contains[S, Name] exists, then Name is in S.
   */
  sealed trait Contains[S, Name]

  trait LowPriorityContains {

    /** Found in tail - lower priority than containsHead */
    implicit def containsTail[Name, Head, Tail](implicit
      ev: Contains[Tail, Name]
    ): Contains[Head :: Tail, Name] =
      new Contains[Head :: Tail, Name] {}
  }

  object Contains extends LowPriorityContains {

    /** Found at head - higher priority than containsTail */
    implicit def containsHead[Name, Tail]: Contains[Name :: Tail, Name] =
      new Contains[Name :: Tail, Name] {}
  }

  // ─────────────────────────────────────────────────────────────────────────
  // IsEmpty operation
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Evidence that a field set is empty.
   *
   * If an implicit IsEmpty[S] exists, then S is HNil.
   */
  sealed trait IsEmpty[S]

  object IsEmpty {
    implicit val emptyIsEmpty: IsEmpty[HNil] = new IsEmpty[HNil] {}
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Size operation
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Type class for computing the size of a field set.
   */
  trait Size[S] {
    def value: Int
  }

  object Size {
    def apply[S](implicit s: Size[S]): Int = s.value

    implicit val sizeEmpty: Size[HNil] = new Size[HNil] {
      def value: Int = 0
    }

    implicit def sizeCons[H, T](implicit tailSize: Size[T]): Size[H :: T] =
      new Size[H :: T] {
        def value: Int = 1 + tailSize.value
      }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Concat operation
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Type class for concatenating two field sets.
   */
  trait Concat[S1, S2] {
    type Out
  }

  object Concat {
    type Aux[S1, S2, O] = Concat[S1, S2] { type Out = O }

    implicit def concatEmpty[S2]: Aux[HNil, S2, S2] =
      new Concat[HNil, S2] { type Out = S2 }

    implicit def concatCons[H, T, S2, TOut](implicit
      @annotation.unused ev: Aux[T, S2, TOut]
    ): Aux[H :: T, S2, H :: TOut] =
      new Concat[H :: T, S2] { type Out = H :: TOut }
  }
}

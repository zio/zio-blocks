package zio.blocks.schema.migration

/**
 * Type-level list for compile-time migration validation (Scala 2).
 *
 * In Scala 3, these operations use match types on Tuples. In Scala 2, we use a
 * custom TList type (TNil, TCons) for type-level field tracking.
 *
 * The compile-time validation in Scala 2 is done via macro-based type
 * extraction and runtime List[String] operations in MigrationBuilderSyntax.
 *
 * Note: Additional type-level operations (Contains, IsSubset, Difference,
 * Union, Intersect, Size, etc.) could be implemented using implicit resolution
 * if needed for advanced type-level computations. These would follow the
 * pattern of using implicit existence as proof of type-level propositions.
 */
object TypeLevel {

  /** Type-level heterogeneous list. Analogous to Scala 3's Tuple. */
  sealed trait TList

  /** Empty type-level list. Analogous to EmptyTuple. */
  sealed trait TNil extends TList

  /** Cons cell for type-level list. Analogous to H *: T in Scala 3. */
  sealed trait TCons[+H, +T <: TList] extends TList

  /** Convenient type alias for cons, mimicking :: syntax */
  type ::[+H, +T <: TList] = TCons[H, T]
}

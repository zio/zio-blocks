package zio.blocks.schema

/**
 * A typeclass that enables conversion from a nominal type `A` (like a case
 * class) to its structural equivalent.
 *
 * Structural types use "duck typing" - if two types have the same structure,
 * they're compatible, regardless of their names. This trait bridges nominal and
 * structural typing by providing:
 *
 *   1. The structural type equivalent (`StructuralType`)
 *   2. A conversion function from nominal to structural (`toStructural`)
 *   3. A way to derive the structural schema (`structuralSchema`)
 *
 * Example:
 * {{{
 * case class Person(name: String, age: Int)
 *
 * // ToStructural[Person] would have:
 * // - StructuralType = { val name: String; val age: Int }
 * // - toStructural converts Person instances to structural values
 * // - structuralSchema provides Schema[{ val name: String; val age: Int }]
 * }}}
 *
 * @tparam A
 *   The nominal type (typically a case class or product type)
 */
trait ToStructural[A] {

  /**
   * The structural type equivalent of `A`.
   *
   * This is typically a refinement type like `{ val name: String; val age: Int
   * }` that mirrors the fields of `A`.
   */
  type StructuralType

  /**
   * Converts a value of the nominal type `A` to its structural equivalent.
   *
   * The returned value can be accessed using `selectDynamic` for field access.
   *
   * @param value
   *   The nominal value to convert
   * @return
   *   A structural representation of the value
   */
  def toStructural(value: A): StructuralType

  /**
   * Creates a Schema for the structural type based on the nominal Schema.
   *
   * This method reuses field information from the nominal schema but creates
   * new bindings that work with structural values (using Selectable/Dynamic).
   *
   * @param schema
   *   The Schema for the nominal type A
   * @return
   *   A Schema for the structural equivalent type
   */
  def structuralSchema(implicit schema: Schema[A]): Schema[StructuralType]
}

object ToStructural extends ToStructuralVersionSpecific {

  /**
   * Summons a ToStructural instance for type A.
   */
  def apply[A](implicit ts: ToStructural[A]): ToStructural[A] = ts
}

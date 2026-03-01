package zio.blocks.schema.migration

/**
 * SelectorSyntax provides implicit extensions to enable a type-safe DSL for
 * schema path selection in Scala 2. * These methods are strictly "marker
 * methods"—they are not intended for execution at runtime. Instead, they serve
 * as structural hints for macros to identify collection traversals and sum-type
 * refinements during the derivation of DynamicOptics.
 */
object SelectorSyntax {

  /**
   * Provides syntax for traversing elements within a collection. Example:
   * `_.addresses.each.street`
   */
  implicit class CollectionOps[A](val self: Iterable[A]) extends AnyVal {

    /**
     * A marker method representing element-wise traversal. Throws an exception
     * if invoked at runtime, as it should only be used within macro-supported
     * selector expressions.
     */
    def each: A = throw new UnsupportedOperationException(
      "The '.each' method is a selector marker and cannot be executed at runtime."
    )
  }

  /**
   * Provides syntax for refining a sum-type (enum) to a specific subtype.
   * Example: `_.paymentMethod.when[CreditCard]`
   */
  implicit class SumTypeOps[A](val self: A) extends AnyVal {

    /**
     * A marker method representing a subtype refinement or "downcast". Throws
     * an exception if invoked at runtime.
     */
    def when[Sub <: A]: Sub = throw new UnsupportedOperationException(
      "The '.when' method is a selector marker and cannot be executed at runtime."
    )
  }
}

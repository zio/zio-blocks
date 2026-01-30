package zio.blocks.typeid

/**
 * Low-priority implicit for TypeId derivation.
 *
 * This trait provides the `derived` given which has lower priority than the
 * predefined instances in TypeIdInstances. This ensures that when both a
 * predefined instance and `derived` could match, the predefined instance wins.
 */
trait TypeIdLowPriority {

  /**
   * Derives a TypeId for any type or type constructor.
   *
   * This given has lower priority than predefined instances in TypeIdInstances,
   * so for types like Int, String, etc., the predefined instances will be used
   * instead of macro derivation.
   */
  inline given derived[A <: AnyKind]: TypeId[A] = ${ TypeIdMacros.derivedImpl[A] }
}

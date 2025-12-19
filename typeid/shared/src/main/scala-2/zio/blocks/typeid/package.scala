package zio.blocks

/**
 * Scala 2 compatibility for AnyKind-like behavior. Since Scala 2 doesn't
 * support higher-kinded wildcards directly, we use Any as the upper bound and
 * cast internally.
 */
package object typeid {

  /**
   * In Scala 2, we use Any as a stand-in for AnyKind. TypeId[_] will be our
   * generic representation.
   */
  private[typeid] type AnyKind = Any
}

package zio.blocks.scope

import scala.deriving.Mirror
import scala.compiletime.*

private[scope] trait UnscopedVersionSpecific {

  private val singleton: Unscoped[Any] = new Unscoped[Any] {}

  /**
   * Derives [[Unscoped]] for case classes, sealed traits, and enums. All
   * fields/cases must have `Unscoped` instances.
   *
   * Uses `Mirror.Of[A]` constraint so this given is only a candidate for types
   * that have a Mirror (case classes, sealed traits, enums). This allows
   * implicit search to fall through to lower-priority instances for non-Mirror
   * types.
   *
   * @tparam A
   *   the case class, sealed trait, or enum to derive `Unscoped` for
   * @return
   *   an `Unscoped[A]` instance, or a compile error if any constituent type
   *   lacks an `Unscoped` instance
   */
  inline given derived[A](using m: Mirror.Of[A]): Unscoped[A] =
    inline m match {
      case pm: Mirror.ProductOf[A] => derivedProduct[A](using pm)
      case sm: Mirror.SumOf[A]     => derivedSum[A](using sm)
    }

  private inline def derivedProduct[A](using m: Mirror.ProductOf[A]): Unscoped[A] = {
    requireAllUnscoped[m.MirroredElemTypes]
    singleton.asInstanceOf[Unscoped[A]]
  }

  private inline def derivedSum[A](using m: Mirror.SumOf[A]): Unscoped[A] = {
    requireAllUnscoped[m.MirroredElemTypes]
    singleton.asInstanceOf[Unscoped[A]]
  }

  private inline def requireAllUnscoped[T <: Tuple]: Unit =
    inline erasedValue[T] match {
      case _: EmptyTuple => ()
      case _: (h *: t)   =>
        summonInline[Unscoped[h]]
        requireAllUnscoped[t]
    }
}

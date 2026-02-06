package zio.blocks.scope

import scala.util.NotGiven

/**
 * Typeclass that determines how a value is unscoped when extracted via `$`.
 *
 * If `A` has an [[Unscoped]] instance, `Out = A` (raw value). Otherwise, `Out
 * = A @@ S` (re-scoped with scope).
 *
 * This enables conditional unscoping: data types escape freely, resource types
 * stay tracked.
 */
trait AutoUnscoped[A, S] {
  type Out
  def apply(a: A): Out
}

object AutoUnscoped {

  /** Unscoped types escape as raw values. Zero overhead: identity function. */
  given unscoped[A, S](using Unscoped[A]): AutoUnscoped[A, S] with {
    type Out = A
    def apply(a: A): Out = a
  }

  /** Non-Unscoped types stay scoped. Zero overhead: opaque type alias. */
  given resourceful[A, S](using NotGiven[Unscoped[A]]): AutoUnscoped[A, S] with {
    type Out = A @@ S
    def apply(a: A): Out = @@.scoped(a)
  }
}

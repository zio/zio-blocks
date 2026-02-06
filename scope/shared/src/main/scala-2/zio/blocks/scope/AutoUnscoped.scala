package zio.blocks.scope

/**
 * Typeclass that determines how a value is unscoped when extracted via `$`.
 *
 * If `A` has an [[Unscoped]] instance, `Out = A` (raw value). Otherwise,
 * `Out = A @@ S` (re-scoped with scope).
 *
 * This enables conditional unscoping: data types escape freely, resource types
 * stay tracked.
 */
trait AutoUnscoped[A, S] {
  type Out
  def apply(a: A): Out
}

object AutoUnscoped extends AutoUnscopedLowPriority {
  type Aux[A, S, O] = AutoUnscoped[A, S] { type Out = O }

  /** Unscoped types escape as raw values. Zero overhead: identity function. */
  implicit def unscoped[A, S](implicit ev: Unscoped[A]): AutoUnscoped.Aux[A, S, A] =
    new AutoUnscoped[A, S] {
      type Out = A
      def apply(a: A): Out = a
    }
}

trait AutoUnscopedLowPriority {

  /** Non-Unscoped types stay scoped. Zero overhead: opaque type alias. */
  implicit def resourceful[A, S]: AutoUnscoped.Aux[A, S, A @@ S] =
    new AutoUnscoped[A, S] {
      type Out = A @@ S
      def apply(a: A): Out = @@.scoped(a)
    }
}

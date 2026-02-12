package zio.blocks.scope

/**
 * Typeclass that controls what types can be returned from a child scope and how
 * they are lifted to the parent scope.
 *
 * When calling `scope.scoped { child => ... }`, the return type must have a
 * `ScopeLift` instance. This serves two purposes:
 *   1. Ensures scope safety by preventing closures that capture the child scope
 *      from escaping
 *   2. Determines the output type (raw value or tagged value)
 *
 * ==Allowed Types and Their Output==
 *
 *   - '''Global scope''': Any type `A` lifts to `A` (global scope never closes)
 *   - '''Nothing''': Lifts to `Nothing` (for blocks that throw)
 *   - '''[[Unscoped]] types''': Pure data lifts to raw `A`
 *   - '''Parent-scoped values''': `B @@ T` where `S <:< T` lifts to `B @@ T`
 *
 * ==Rejected Types==
 *
 *   - '''Closures''': Function types like `() => A` don't have instances
 *     because they could capture the child scope
 *   - '''Child-tagged values''': `A @@ ChildTag` cannot escape because the
 *     child scope closes when `scoped` exits
 *
 * @tparam A
 *   the type being returned
 * @tparam S
 *   the parent scope's tag
 *
 * @see
 *   [[Scope.scoped]] which requires this typeclass
 * @see
 *   [[Unscoped]] for marking types as safe data
 */
trait ScopeLift[A, S] {

  /**
   * The output type after lifting.
   *
   * For unscoped types and global scope, this is the raw type `A`. For
   * parent-scoped values `B @@ T`, this stays as `B @@ T`.
   */
  type Out

  /**
   * Lifts the value to the output type.
   *
   * @param a
   *   the value to lift
   * @return
   *   the lifted value
   */
  def apply(a: A): Out
}

object ScopeLift extends ScopeLiftMidPriority {

  /**
   * Auxiliary type alias that exposes the `Out` type member at the type level.
   *
   * @tparam A
   *   the type being lifted
   * @tparam S
   *   the scope tag type
   * @tparam O
   *   the output type
   */
  type Aux[A, S, O] = ScopeLift[A, S] { type Out = O }

  /**
   * Global scope: everything lifts as raw A (highest priority).
   *
   * The global scope never closes during normal execution, so there's no risk
   * of use-after-close.
   */
  given globalScope[A]: ScopeLift.Aux[A, Scope.GlobalTag, A] =
    new ScopeLift[A, Scope.GlobalTag] {
      type Out = A
      def apply(a: A): Out = a
    }
}

/**
 * Mid-priority instances for [[ScopeLift]].
 */
trait ScopeLiftMidPriority extends ScopeLiftLowPriority {

  /**
   * Nothing can always be lifted (represents non-termination/exceptions).
   *
   * When a block throws or diverges, the result type is `Nothing`. This is
   * always safe since no value is actually returned.
   */
  given nothing[S]: ScopeLift.Aux[Nothing, S, Nothing] =
    new ScopeLift[Nothing, S] {
      type Out = Nothing
      def apply(a: Nothing): Out = a
    }

  /**
   * Unscoped types lift as raw values.
   *
   * Pure data types (primitives, strings, collections marked with [[Unscoped]])
   * don't hold resources and can safely escape any scope boundary.
   */
  given unscoped[A, S](using Unscoped[A]): ScopeLift.Aux[A, S, A] =
    new ScopeLift[A, S] {
      type Out = A
      def apply(a: A): Out = a
    }
}

/**
 * Low-priority instances for [[ScopeLift]].
 */
trait ScopeLiftLowPriority extends ScopeLiftLowestPriority {

  /**
   * Parent-scoped values lift as-is.
   *
   * A value `B @@ T` is safe to return from a child of scope `S` when
   * `S <:< T`, meaning `T` is `S` or a supertype. Such values are scoped to the
   * parent or an ancestor, so they outlive the child scope.
   */
  given scoped[B, T, S](using S <:< T): ScopeLift.Aux[B @@ T, S, B @@ T] =
    new ScopeLift[B @@ T, S] {
      type Out = B @@ T
      def apply(a: B @@ T): Out = a
    }
}

/**
 * Lowest-priority instances for [[ScopeLift]].
 */
trait ScopeLiftLowestPriority {

  /**
   * Scoped values with Unscoped inner type can be unwrapped.
   *
   * When the inner type `A` is `Unscoped` (pure data), it's safe to extract the
   * raw value from `A @@ T` regardless of the scope tag `T`. The value is
   * evaluated while the scope is still open, and the result is pure data that
   * doesn't hold resources.
   *
   * '''UNSOUND (TODO fix):''' This instance outputs raw `A`, losing all scope
   * tracking. The sound behavior would be to output `A @@ S` (parent-scoped),
   * maintaining laziness and scope protection. With raw `A`, values are
   * accessible even after the parent scope closes, bypassing scope safety.
   */
  given scopedUnscoped[A, T, S](using Unscoped[A]): ScopeLift.Aux[A @@ T, S, A] =
    new ScopeLift[A @@ T, S] {
      type Out = A
      def apply(a: A @@ T): Out = @@.unscoped(a)
    }
}

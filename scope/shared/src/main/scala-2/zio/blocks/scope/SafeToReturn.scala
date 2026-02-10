package zio.blocks.scope

/**
 * Typeclass that controls what types can be returned from a child scope.
 *
 * When calling `scope.scoped { child => ... }`, the return type must have a
 * `SafeToReturn` instance to ensure scope safety. This prevents closures that
 * capture the child scope from escaping, which would allow use-after-close.
 *
 * ==Allowed Types==
 *
 *   - '''[[Unscoped]] types''': Pure data (primitives, strings, collections)
 *     can always be returned safely.
 *   - '''Scoped values with parent-level tags''': `A @@ T` where `T` is the
 *     parent's tag or above (i.e., `ParentTag <:< T`). These values outlive
 *     the child scope.
 *
 * ==Rejected Types==
 *
 *   - '''Closures''': Function types like `() => A` don't have instances
 *     because they could capture the child scope.
 *   - '''Child-tagged values''': `A @@ ChildTag` cannot escape because the
 *     child scope closes when `scoped` exits.
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
trait SafeToReturn[A, S]

object SafeToReturn extends SafeToReturnMidPriority {

  /**
   * Anything can be returned from global scope (highest priority).
   *
   * The global scope never closes during normal execution, so there's no
   * risk of use-after-close. This matches [[ScopeEscape.globalScope]].
   */
  implicit def globalScope[A]: SafeToReturn[A, Scope.GlobalTag] =
    instance.asInstanceOf[SafeToReturn[A, Scope.GlobalTag]]

  private[scope] val instance: SafeToReturn[Any, Any] = new SafeToReturn[Any, Any] {}
}

trait SafeToReturnMidPriority extends SafeToReturnLowPriority {

  /**
   * Nothing can always be returned (represents non-termination/exceptions).
   *
   * When a block throws or diverges, the result type is `Nothing`. This is
   * always safe since no value is actually returned.
   */
  implicit def nothing[S]: SafeToReturn[Nothing, S] =
    SafeToReturn.instance.asInstanceOf[SafeToReturn[Nothing, S]]

  /**
   * Unscoped types can always be returned from any scope.
   *
   * Pure data types (primitives, strings, collections marked with [[Unscoped]])
   * don't hold resources and can safely escape any scope boundary.
   */
  implicit def unscoped[A, S](implicit ev: Unscoped[A]): SafeToReturn[A, S] =
    SafeToReturn.instance.asInstanceOf[SafeToReturn[A, S]]
}

/**
 * Low-priority instances for [[SafeToReturn]].
 */
trait SafeToReturnLowPriority {

  /**
   * Scoped values can be returned if their tag is at parent level or above.
   *
   * A value `B @@ T` is safe to return from a child of scope `S` when
   * `S <:< T`, meaning `T` is `S` or a supertype. Such values are scoped to
   * the parent or an ancestor, so they outlive the child scope.
   */
  implicit def scoped[B, T, S](implicit ev: S <:< T): SafeToReturn[B @@ T, S] =
    SafeToReturn.instance.asInstanceOf[SafeToReturn[B @@ T, S]]
}

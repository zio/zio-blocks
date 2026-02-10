package zio.blocks.scope

/**
 * Typeclass that controls whether `A @@ S` escapes the scope as raw `A` or
 * remains scoped as `A @@ S` when extracted via `.get` or `$`.
 *
 * Priority (highest to lowest):
 *   1. Global scope (`Scope.Global`): all types escape as raw `A`
 *   2. [[Unscoped]] types: escape as raw `A`
 *   3. Resource types: stay scoped as `A @@ S`
 *
 * This enables conditional scoping: data types and global-scope values escape
 * freely, while resource types in child scopes stay tracked.
 */
trait ScopeEscape[A, S] {
  type Out
  def apply(a: A): Out
}

object ScopeEscape extends ScopeEscapeMidPriority {

  /**
   * Auxiliary type alias that exposes the `Out` type member at the type level.
   *
   * Use this when you need to refer to both the input types and the output type
   * of a `ScopeEscape` instance in a type signature.
   *
   * @tparam A
   *   the value type being escaped
   * @tparam S
   *   the scope tag type
   * @tparam O
   *   the output type (either `A` or `A @@ S`)
   */
  type Aux[A, S, O] = ScopeEscape[A, S] { type Out = O }

  /**
   * Global scope: all types escape as raw values.
   *
   * Values scoped with `Scope.GlobalTag` (the global scope) can always be
   * extracted because the global scope never closes during normal execution.
   */
  implicit def globalScope[A]: ScopeEscape.Aux[A, Scope.GlobalTag, A] =
    new ScopeEscape[A, Scope.GlobalTag] {
      type Out = A
      def apply(a: A): Out = a
    }
}

trait ScopeEscapeMidPriority extends ScopeEscapeLowPriority {

  /** Unscoped types escape as raw values. Zero overhead: identity function. */
  implicit def unscoped[A, S](implicit ev: Unscoped[A]): ScopeEscape.Aux[A, S, A] =
    new ScopeEscape[A, S] {
      type Out = A
      def apply(a: A): Out = a
    }
}

trait ScopeEscapeLowPriority {

  /**
   * Non-Unscoped types stay scoped. The result is wrapped in a Scoped thunk.
   */
  implicit def resourceful[A, S]: ScopeEscape.Aux[A, S, A @@ S] =
    new ScopeEscape[A, S] {
      type Out = A @@ S
      def apply(a: A): Out = Scoped.scoped[A, S](a)
    }
}

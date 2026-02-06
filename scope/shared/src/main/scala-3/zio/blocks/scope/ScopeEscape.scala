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

object ScopeEscape extends ScopeEscapeLowPriority {
  type Aux[A, S, O] = ScopeEscape[A, S] { type Out = O }

  /**
   * Global scope: all types escape as raw values.
   *
   * Values scoped with `Scope.Global` (the global scope) can always be
   * extracted because the global scope never closes during normal execution.
   */
  given globalScope[A]: ScopeEscape[A, Scope.Global] with {
    type Out = A
    def apply(a: A): Out = a
  }

  /** Unscoped types escape as raw values. Zero overhead: identity function. */
  given unscoped[A, S](using Unscoped[A]): ScopeEscape[A, S] with {
    type Out = A
    def apply(a: A): Out = a
  }
}

trait ScopeEscapeLowPriority {

  /** Non-Unscoped types stay scoped. Zero overhead: opaque type alias. */
  given resourceful[A, S]: ScopeEscape[A, S] with {
    type Out = A @@ S
    def apply(a: A): Out = @@.scoped(a)
  }
}

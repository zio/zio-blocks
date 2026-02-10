package zio.blocks.scope

/**
 * Typeclass that controls whether `A @@ S` escapes the scope as raw `A` or
 * remains scoped as `A @@ S` when extracted via `scope.$` or `scope.execute`.
 *
 * ==Priority (highest to lowest)==
 *
 *   1. '''Global scope''' (`S =:= Scope.GlobalTag`): all types escape as raw
 *      `A`
 *   2. '''[[Unscoped]] types''': escape as raw `A` regardless of scope
 *   3. '''Resource types''': stay scoped as `A @@ S`
 *
 * This enables conditional scoping: data types (primitives, strings,
 * collections) and global-scope values escape freely, while resource types
 * (streams, connections, handles) in child scopes stay tracked to prevent
 * escape.
 *
 * ==Zero Overhead==
 *
 * The typeclass dispatch is resolved at compile time. Both branches compile to
 * identity operations at runtime since `@@` is an opaque type alias.
 *
 * @tparam A
 *   the value type being extracted
 * @tparam S
 *   the scope tag type
 *
 * @see
 *   [[Unscoped]] for marking types as safe to escape
 * @see
 *   [[Scope.$]] which uses this typeclass
 */
trait ScopeEscape[A, S] {

  /**
   * The output type: either `A` (escaped) or `A @@ S` (still scoped).
   */
  type Out

  /**
   * Converts the value to the output type.
   *
   * For escaped types, this is an identity operation. For scoped types, this
   * wraps the value with the scope tag.
   *
   * @param a
   *   the value to convert
   * @return
   *   the value as the output type (`A` if escaped, `A @@ S` if scoped)
   */
  def apply(a: A): Out
}

/**
 * Companion object providing given instances for [[ScopeEscape]].
 */
object ScopeEscape extends ScopeEscapeLowPriority {

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
   * Global scope: all types escape as raw values (highest priority).
   *
   * Values scoped with [[Scope.GlobalTag]] can always be extracted because the
   * global scope never closes during normal execution. This makes global-
   * scoped values behave like unscoped values.
   */
  given globalScope[A]: ScopeEscape[A, Scope.GlobalTag] with {
    type Out = A
    def apply(a: A): Out = a
  }

  /**
   * [[Unscoped]] types escape as raw values (high priority).
   *
   * Types with an `Unscoped` instance are considered safe data that doesn't
   * hold resources. Zero overhead: this is an identity function at runtime.
   */
  given unscoped[A, S](using Unscoped[A]): ScopeEscape[A, S] with {
    type Out = A
    def apply(a: A): Out = a
  }
}

/**
 * Low-priority given instances for [[ScopeEscape]].
 *
 * This trait exists to establish implicit priority. Instances defined here have
 * lower priority than those in the [[ScopeEscape]] companion object, ensuring
 * that `Unscoped` types and global-scope values take precedence over the
 * default "stay scoped" behavior.
 */
trait ScopeEscapeLowPriority {

  /**
   * Non-[[Unscoped]] types stay scoped (lowest priority fallback).
   *
   * Resource types without an `Unscoped` instance remain tagged with the scope,
   * preventing them from escaping. Zero overhead: `@@` is an opaque type alias.
   */
  given resourceful[A, S]: ScopeEscape[A, S] with {
    type Out = A @@ S
    def apply(a: A): Out = @@.scoped(a)
  }
}

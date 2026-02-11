package zio.blocks.scope

/**
 * A deferred or eager scoped computation that can only be executed by an
 * appropriate Scope.
 *
 * `A @@ S` (type alias for `Scoped[A, S]`) represents a value of type `A`
 * locked to scope tag `S`. At runtime, the value is stored as either:
 *   - An eager value (when scope was open at creation time, zero thunk
 *     overhead)
 *   - A lazy thunk (when scope was closed, or for composed computations)
 *
 * This dual representation eliminates thunk allocation in the happy path (scope
 * is open) while preserving safety when scope is closed.
 *
 * @tparam A
 *   the result type of the computation (covariant)
 * @tparam S
 *   the scope tag required to execute this computation (contravariant)
 */
final class Scoped[+A, -S] private[scope] (
  private[scope] val eagerValue: Any,
  private[scope] val lazyThunk: () => Any
) {

  /**
   * Maps over the result of this scoped computation.
   *
   * The resulting computation is always lazy, regardless of whether the input
   * is eager or lazy. This ensures safety: map/flatMap never eagerly evaluate
   * user functions (only Scope.$/execute may do that).
   *
   * @param f
   *   the function to apply to the result
   * @tparam B
   *   the new result type
   * @return
   *   a new scoped computation with the mapped result
   */
  def map[B](f: A => B): Scoped[B, S] =
    Scoped.deferred[B, S](f(Scoped.run(this)))

  /**
   * FlatMaps this scoped computation with a function returning another Scoped.
   *
   * The resulting computation requires both this computation's tag and the
   * result's tag, combined via intersection.
   *
   * @param f
   *   function from result to another scoped computation
   * @tparam T
   *   the additional tag requirement
   * @tparam B
   *   the result type
   * @return
   *   a scoped computation with combined tag `S with T`
   */
  def flatMap[B, T](f: A => Scoped[B, T]): Scoped[B, S with T] =
    Scoped.deferred[B, S with T](Scoped.run(f(Scoped.run(this))))
}

object Scoped {

  private[scope] val EagerSentinel: () => Any = null

  /**
   * Lifts a value into a scoped computation.
   *
   * This allows ordinary values to participate in for-comprehensions alongside
   * scoped values. The resulting computation has no scope requirements (uses
   * `Any` as the tag), so it can be combined with any other scoped value.
   *
   * The value is evaluated lazily when the computation is run.
   *
   * @param a
   *   the value to lift (by-name, evaluated lazily)
   * @tparam A
   *   the value type
   * @return
   *   a scoped computation that produces `a` when run
   */
  def apply[A](a: => A): Scoped[A, Any] =
    deferred[A, Any](a)

  private[scope] def eager[A, S](a: A): Scoped[A, S] =
    new Scoped[A, S](a.asInstanceOf[Any], EagerSentinel)

  private[scope] def deferred[A, S](a: => A): Scoped[A, S] =
    new Scoped[A, S](null, () => a)

  private[scope] def run[A, S](x: Scoped[A, S]): A =
    if (x.lazyThunk eq EagerSentinel) x.eagerValue.asInstanceOf[A]
    else x.lazyThunk().asInstanceOf[A]

  /**
   * Creates a scoped computation tagged with a specific scope.
   *
   * @deprecated
   *   Use deferred or eager instead.
   */
  private[scope] def scoped[A, S](a: => A): Scoped[A, S] =
    deferred[A, S](a)

  /**
   * Unwraps a scoped computation, returning the thunk result.
   *
   * This is package-private to prevent bypassing the scope safety checks.
   */
  private[scope] def unscoped[A, S](scoped: Scoped[A, S]): A =
    run(scoped)
}

/**
 * Companion object for the `@@` type alias, providing factory methods.
 *
 * These methods delegate to [[Scoped]].
 */
object @@ {

  /**
   * Creates a scoped value tagged with scope S.
   *
   * This wraps a by-name value into a scoped computation `A @@ S`. The scope
   * tag `S` is typically the path-dependent `Tag` type of a [[Scope]] instance.
   *
   * The value is evaluated lazily when the scoped computation is executed.
   *
   * @param a
   *   the value to scope (by-name, evaluated lazily)
   * @tparam A
   *   the value type
   * @tparam S
   *   the scope tag type
   * @return
   *   the scoped computation
   */
  def scoped[A, S](a: => A): A @@ S =
    Scoped.deferred[A, S](a)

  /**
   * Unwraps a scoped value, returning the underlying value.
   *
   * This is package-private to prevent bypassing the scope safety checks.
   * External code should use scope.execute with proper scope proof.
   */
  private[scope] def unscoped[A, S](scoped: A @@ S): A =
    Scoped.run(scoped)
}

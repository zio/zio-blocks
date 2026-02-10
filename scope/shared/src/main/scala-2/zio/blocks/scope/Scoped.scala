package zio.blocks.scope

/**
 * A deferred scoped computation that can only be executed by an appropriate
 * Scope.
 *
 * `Scoped[-S, +A]` is a description of a computation that produces an `A` and
 * requires a scope with tag `<: S` to execute. Unlike eager value wrappers,
 * `Scoped` builds a simple thunk that is only interpreted when given to a scope
 * via `scope.execute(scoped)`.
 *
 * ==Unified Design==
 *
 * `Scoped` is the single representation for scoped values:
 *   - `scope.allocate` returns `Scoped[Tag, A]` (also written `A @@ Tag`)
 *   - `map` and `flatMap` compose `Scoped` computations
 *   - `scope.execute` runs the computation
 *
 * ==Contravariance in Tag==
 *
 * A `Scoped[Parent, A]` can be executed by any scope with `Tag <: Parent`.
 * Child scopes have more access than parents (their tag is a subtype), so they
 * can execute parent-level Scoped computations.
 *
 * ==Safety==
 *
 * Because `Scoped` is just data until executed, you cannot accidentally access
 * scoped values outside their lifecycle. The scope is the gatekeeper that
 * controls when execution happens.
 *
 * @example
 *   {{{
 *   Scope.global.scoped { scope =>
 *     val program: Scoped[scope.Tag, Result] = for {
 *       db   <- scope.allocate(Resource[Database])
 *       conn <- scope.allocate(db.connect())
 *       data <- conn.map(_.query("SELECT *"))
 *     } yield process(data)
 *
 *     scope.execute(program)
 *   }
 *   }}}
 *
 * @tparam S
 *   the scope tag required to execute this computation (contravariant)
 * @tparam A
 *   the result type of the computation (covariant)
 *
 * @see
 *   [[@@]] type alias for `Scoped` with swapped parameters
 * @see
 *   [[Scope.execute]] for executing Scoped computations
 */
final class Scoped[-S, +A] private[scope] (private[scope] val executeFn: () => A) {

  /**
   * Maps over the result of this Scoped computation.
   *
   * @param f
   *   the function to apply to the result
   * @tparam B
   *   the new result type
   * @return
   *   a new Scoped computation with the mapped result
   */
  def map[B](f: A => B): Scoped[S, B] =
    new Scoped(() => f(executeFn()))

  /**
   * FlatMaps this Scoped computation with a function returning another Scoped.
   *
   * The resulting computation requires both this computation's tag and the
   * result's tag, combined via intersection.
   *
   * @param f
   *   function from result to another Scoped computation
   * @tparam T
   *   the additional tag requirement
   * @tparam B
   *   the result type
   * @return
   *   a Scoped computation with combined tag `S with T`
   */
  def flatMap[T, B](f: A => Scoped[T, B]): Scoped[S with T, B] =
    new Scoped(() => f(executeFn()).executeFn())

  /**
   * Executes the scoped computation and returns the result.
   *
   * This is package-private because execution should go through the Scope.
   */
  private[scope] def run(): A = executeFn()
}

object Scoped {

  /**
   * Lifts a value into a Scoped computation.
   *
   * This allows ordinary values to participate in `Scoped` for-comprehensions
   * alongside scoped values. The resulting computation has no scope
   * requirements (uses `Any` as the tag), so it can be combined with any other
   * `Scoped`.
   *
   * The value is evaluated lazily when the Scoped computation is run.
   *
   * @param a
   *   the value to lift (by-name, evaluated lazily)
   * @tparam A
   *   the value type
   * @return
   *   a Scoped computation that produces `a` when run
   */
  def apply[A](a: => A): Scoped[Any, A] =
    new Scoped(() => a)

  /**
   * Creates a Scoped computation tagged with a specific scope.
   *
   * This is the primary way to create scoped values. The computation is
   * deferred until executed by a matching scope.
   *
   * @param a
   *   the value to wrap (by-name, evaluated lazily)
   * @tparam A
   *   the value type
   * @tparam S
   *   the scope tag type
   * @return
   *   a Scoped computation tagged with S
   */
  private[scope] def scoped[A, S](a: => A): Scoped[S, A] =
    new Scoped(() => a)

  /**
   * Unwraps a Scoped computation, returning the thunk result.
   *
   * This is package-private to prevent bypassing the scope safety checks.
   * External code should use scope.execute with proper scope proof.
   */
  private[scope] def unscoped[S, A](scoped: Scoped[S, A]): A =
    scoped.executeFn()
}

/**
 * Companion object for the `@@` type alias, providing factory methods.
 *
 * These methods delegate to [[Scoped]] but maintain the `@@` parameter order
 * for consistency.
 */
object @@ {

  /**
   * Creates a scoped value tagged with scope S.
   *
   * This wraps a by-name value into a scoped computation `A @@ S`. The scope
   * tag `S` is typically the path-dependent `Tag` type of a [[Scope]] instance.
   *
   * The value is evaluated lazily when the Scoped computation is executed.
   *
   * '''Note:''' This only tags the value - it does not manage lifecycle. For
   * resources that need cleanup, prefer `scope.allocate` with a [[Resource]]
   * which automatically registers finalizers.
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
    Scoped.scoped[A, S](a)

  /**
   * Unwraps a scoped value, returning the underlying value.
   *
   * This is package-private to prevent bypassing the scope safety checks.
   * External code should use scope.execute with proper scope proof.
   */
  private[scope] def unscoped[A, S](scoped: A @@ S): A =
    Scoped.unscoped(scoped)
}

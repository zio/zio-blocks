package zio.blocks.scope

/**
 * A deferred scoped computation that can only be executed by an appropriate
 * Scope.
 *
 * `A @@ S` (type alias for `Scoped[A, S]`) is a description of a computation
 * that produces an `A` and requires a scope with tag `<: S` to execute. Unlike
 * eager value wrappers, `Scoped` builds a simple thunk that is only interpreted
 * when given to a scope via `scope.execute(scoped)`.
 *
 * ==Unified Design==
 *
 * `Scoped` is the single representation for scoped values:
 *   - `scope.allocate` returns `A @@ scope.Tag`
 *   - `map` and `flatMap` compose scoped computations
 *   - `scope.execute` runs the computation
 *
 * ==Contravariance in Tag==
 *
 * A value `A @@ Parent` can be executed by any scope with `Tag <: Parent`.
 * Child scopes have more access than parents (their tag is a subtype), so they
 * can execute parent-level scoped computations.
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
 *     val program: Result @@ scope.Tag = for {
 *       db   <- scope.allocate(Resource[Database])
 *       conn <- scope.allocate(db.connect())
 *       data <- conn.map(_.query("SELECT *"))
 *     } yield process(data)
 *
 *     scope.execute(program)
 *   }
 *   }}}
 *
 * @tparam A
 *   the result type of the computation (covariant)
 * @tparam S
 *   the scope tag required to execute this computation (contravariant)
 *
 * @see
 *   [[@@]] type alias for `Scoped`
 * @see
 *   [[Scope.execute]] for executing scoped computations
 */
final class Scoped[+A, -S] private[scope] (private[scope] val executeFn: () => A) {

  /**
   * Maps over the result of this scoped computation.
   *
   * @param f
   *   the function to apply to the result
   * @tparam B
   *   the new result type
   * @return
   *   a new scoped computation with the mapped result
   */
  def map[B](f: A => B): Scoped[B, S] =
    new Scoped(() => f(executeFn()))

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
    new Scoped(() => f(executeFn()).executeFn())

  /**
   * Executes the scoped computation and returns the result.
   *
   * This is package-private because execution should go through the Scope.
   */
  private[scope] def run(): A = executeFn()

  /**
   * Unsafely unwraps the scoped value, bypassing scope safety.
   *
   * '''Warning''': This method is intended only for use by the `leak` macro.
   * Direct use bypasses compile-time scope safety guarantees.
   */
  def unsafeRun(): A = executeFn()
}

object Scoped {

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
    new Scoped(() => a)

  /**
   * Creates a scoped computation tagged with a specific scope.
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
   *   a scoped computation tagged with S
   */
  private[scope] def scoped[A, S](a: => A): Scoped[A, S] =
    new Scoped(() => a)

  /**
   * Unwraps a scoped computation, returning the thunk result.
   *
   * This is package-private to prevent bypassing the scope safety checks.
   * External code should use scope.execute with proper scope proof.
   */
  private[scope] def unscoped[A, S](scoped: Scoped[A, S]): A =
    scoped.executeFn()
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

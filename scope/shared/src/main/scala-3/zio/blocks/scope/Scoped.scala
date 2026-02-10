package zio.blocks.scope

/**
 * Internal lazy wrapper for scoped computations.
 *
 * When a scope is closed (or for deferred computations like map/flatMap),
 * values are wrapped in LazyScoped to defer evaluation. When a scope is open,
 * values are stored as raw `A` without any wrapper.
 *
 * This class is private to the scope package and unforgeable by user code.
 */
private[scope] final class LazyScoped[+A](private[scope] val thunk: () => A)

/**
 * A scoped value that can only be accessed through an appropriate Scope.
 *
 * `A @@ S` represents a value of type `A` that is locked to scope tag `S`. At
 * runtime, it is represented as either:
 *   - The raw value `A` (eager form, zero overhead — used when scope is open)
 *   - A `LazyScoped` thunk (lazy form — used when scope is closed or for
 *     composed computations via `map`/`flatMap`)
 *
 * This dual representation eliminates thunk allocation in the happy path (scope
 * is open) while preserving safety when scope is closed.
 *
 * ==Contravariance in Tag==
 *
 * A value `A @@ Parent` can be used by any scope with `Tag <: Parent`. Child
 * scopes can access parent-scoped values automatically.
 *
 * ==Safety==
 *
 * Because the opaque type boundary prevents direct access, you cannot
 * accidentally use scoped values outside their lifecycle. The scope is the
 * gatekeeper that controls when values are accessed.
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
 */
object Scoped {

  opaque type Scoped[+A, -S] = Any

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
    a.asInstanceOf[Scoped[A, S]]

  private[scope] def deferred[A, S](a: => A): Scoped[A, S] =
    (new LazyScoped(() => a)).asInstanceOf[Scoped[A, S]]

  private[scope] def run[A, S](x: Scoped[A, S]): A =
    x match {
      case l: LazyScoped[_] => l.thunk().asInstanceOf[A]
      case a                => a.asInstanceOf[A]
    }

  private[scope] def isLazy(x: Any): Boolean =
    x.isInstanceOf[LazyScoped[_]]

  extension [A, S](self: Scoped[A, S]) {

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
      deferred[B, S](f(run(self)))

    /**
     * FlatMaps this scoped computation with a function returning another
     * Scoped.
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
     *   a scoped computation with combined tag `S & T`
     */
    def flatMap[B, T](f: A => Scoped[B, T]): Scoped[B, S & T] =
      deferred[B, S & T](run(f(run(self))))
  }
}

/**
 * Type alias for Scoped.Scoped.
 *
 * `A @@ S` is equivalent to `Scoped.Scoped[A, S]`. This infix syntax reads
 * naturally when declaring scoped values:
 *
 * {{{
 * val db: Database @@ scope.Tag = scope.allocate(Resource[Database])
 * }}}
 *
 * @tparam A
 *   the value type (covariant)
 * @tparam S
 *   the scope tag type (contravariant)
 */
infix type @@[+A, -S] = Scoped.Scoped[A, S]

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
  def scoped[A, S](a: => A): A @@ S = Scoped.deferred[A, S](a)

  /**
   * Unwraps a scoped value, returning the underlying value.
   *
   * This is package-private to prevent bypassing the scope safety checks.
   * External code should use scope.execute with proper scope proof.
   */
  private[scope] def unscoped[A, S](scoped: A @@ S): A =
    Scoped.run(scoped)
}

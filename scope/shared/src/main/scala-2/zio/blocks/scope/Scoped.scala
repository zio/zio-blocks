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
 * `A @@ S` (type alias for `Scoped[A, S]`) represents a value of type `A`
 * locked to scope tag `S`. At runtime, it is represented as either:
 *   - The raw value `A` (eager form, zero overhead — used when scope is open)
 *   - A `LazyScoped` thunk (lazy form — used when scope is closed or for
 *     composed computations via `map`/`flatMap`)
 *
 * This dual representation eliminates thunk allocation in the happy path (scope
 * is open) while preserving safety when scope is closed.
 *
 * @example
 *   {{{
 *   Scope.global.scoped { scope =>
 *     import scope._
 *     val program: Result @@ ScopeTag = for {
 *       db   <- allocate(Resource[Database])
 *       conn <- allocate(db.connect())
 *       data <- conn.map(_.query("SELECT *"))
 *     } yield process(data)
 *
 *     execute(program)
 *   }
 *   }}}
 */
object Scoped {
  type Tag[+A, -S]
  type Scoped[+A, -S] = Tag[A, S]

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
    if (a.isInstanceOf[LazyScoped[_]])
      new LazyScoped(() => a).asInstanceOf[Scoped[A, S]]
    else
      a.asInstanceOf[Scoped[A, S]]

  private[scope] def deferred[A, S](a: => A): Scoped[A, S] =
    new LazyScoped(() => a).asInstanceOf[Scoped[A, S]]

  private[scope] def run[A, S](x: Scoped[A, S]): A =
    (x: Any) match {
      case l: LazyScoped[_] => l.thunk().asInstanceOf[A]
      case a                => a.asInstanceOf[A]
    }
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
   * tag `S` is typically the path-dependent `ScopeTag` type of a [[Scope]]
   * instance.
   *
   * The value is evaluated lazily when the scoped computation is executed.
   *
   * '''Note:''' This only tags the value - it does not manage lifecycle. For
   * resources that need cleanup, prefer `allocate` with a [[Resource]] which
   * automatically registers finalizers.
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
   * External code should use execute with proper scope proof.
   */
  private[scope] def unscoped[A, S](scoped: A @@ S): A =
    Scoped.run(scoped)
}

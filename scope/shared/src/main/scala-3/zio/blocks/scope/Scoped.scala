package zio.blocks.scope

/**
 * Opaque type for scoping values with scope identity.
 *
 * A value of type `A @@ S` is a value of type `A` that is "locked" to a scope
 * with tag `S`. The opaque type hides all methods on `A`, so the only way to
 * use the value is through the scope's `$` method or by building a `Scoped`
 * computation via `map`/`flatMap`.
 *
 * This prevents scoped resources from escaping their scope at compile time.
 * Zero overhead at runtime: `@@` is an opaque type alias, so `A @@ S` is
 * represented as just `A` at runtime.
 *
 * ==Operations==
 *
 * The `@@` companion object provides extension methods:
 *   - `map`: Transform the value, returning a `Scoped` computation
 *   - `flatMap`: Combine scoped values, returning a `Scoped` computation
 *   - `_1`, `_2`: Extract tuple elements (stay scoped)
 *
 * @example
 *   {{{
 *   Scope.global.scoped { scope =>
 *     val db: Database @@ scope.Tag = scope.allocate(Resource[Database])
 *
 *     // Build a Scoped computation
 *     val query: Scoped[scope.Tag, String] = db.map(_.query("SELECT 1"))
 *
 *     // Execute via scope.execute
 *     val result: String = scope.execute(query)
 *   }
 *   }}}
 *
 * @see
 *   [[Scoped]] for deferred scoped computations
 * @see
 *   [[Scope.$]] for direct method access on scoped values
 */
opaque infix type @@[+A, S] = A

/**
 * Companion object for the `@@` opaque type, providing factory methods and
 * extension operations.
 */
object @@ {

  /**
   * Scopes a value with a scope identity.
   *
   * This wraps a raw value `A` into a scoped value `A @@ S`. The scope tag `S`
   * is typically the path-dependent `Tag` type of a [[Scope]] instance.
   *
   * Zero overhead: since `@@` is an opaque type alias, this is an identity
   * operation at runtime.
   *
   * '''Note:''' This only tags the value - it does not manage lifecycle. For
   * resources that need cleanup, prefer `scope.allocate` with a [[Resource]]
   * which automatically registers finalizers.
   *
   * @param a
   *   the value to scope
   * @tparam A
   *   the value type
   * @tparam S
   *   the scope tag type
   * @return
   *   the scoped value
   */
  inline def scoped[A, S](a: A): A @@ S = a

  /**
   * Retrieves the underlying value without unscoping (internal use only).
   *
   * This is package-private to prevent bypassing the scope safety checks.
   * External code should use scope methods with proper scope proof.
   */
  private[scope] inline def unscoped[A, S](scoped: A @@ S): A = scoped

  extension [A, S](scoped: A @@ S) {

    /**
     * Maps over a scoped value, returning a Scoped computation.
     *
     * The function `f` is applied to the underlying value when the Scoped
     * computation is executed by a matching scope. This builds a description of
     * work, not immediate execution.
     *
     * @example
     *   {{{
     *   val db: Database @@ S = ...
     *   val query: Scoped[S, ResultSet] = db.map(_.query("SELECT 1"))
     *   // Execute later: scope.execute(query)
     *   }}}
     *
     * @param f
     *   the function to apply to the underlying value
     * @tparam B
     *   the result type of the function
     * @return
     *   a Scoped computation that will apply f when executed
     */
    inline def map[B](inline f: A => B): Scoped[S, B] =
      Scoped(f(@@.unscoped(scoped)))

    /**
     * FlatMaps over a scoped value, combining scope tags via intersection.
     *
     * Enables for-comprehension syntax with scoped values. The resulting Scoped
     * computation is tagged with the intersection of both scope tags, ensuring
     * it can only be executed where both scopes are available.
     *
     * @example
     *   {{{
     *   val program: Scoped[S & T, Result] = for {
     *     a <- scopedA  // A @@ S
     *     b <- scopedB  // B @@ T
     *   } yield combine(a, b)
     *   }}}
     *
     * @param f
     *   function returning a scoped result
     * @tparam B
     *   the underlying result type
     * @tparam T
     *   the scope tag of the returned value
     * @return
     *   a Scoped computation with combined scope tag `S & T`
     */
    inline def flatMap[B, T](inline f: A => B @@ T): Scoped[S & T, B] =
      Scoped(@@.unscoped(f(@@.unscoped(scoped))))

    /**
     * Extracts the first element of a scoped tuple.
     *
     * @example
     *   {{{
     *   val pair: (Int, String) @@ S = ...
     *   val first: Int @@ S = pair._1
     *   }}}
     *
     * @return
     *   the first element, still scoped with tag `S`
     */
    inline def _1[X, Y](using ev: A =:= (X, Y)): X @@ S =
      ev(scoped)._1

    /**
     * Extracts the second element of a scoped tuple.
     *
     * @example
     *   {{{
     *   val pair: (Int, String) @@ S = ...
     *   val second: String @@ S = pair._2
     *   }}}
     *
     * @return
     *   the second element, still scoped with tag `S`
     */
    inline def _2[X, Y](using ev: A =:= (X, Y)): Y @@ S =
      ev(scoped)._2
  }
}

/**
 * A deferred scoped computation that can only be executed by an appropriate
 * Scope.
 *
 * `Scoped[-Tag, +A]` is a description of a computation that produces an `A` and
 * requires a scope with tag `<: Tag` to execute. Unlike eager `@@` operations,
 * `Scoped` builds a simple thunk that is only interpreted when given to a scope
 * via `scope.execute(scoped)`.
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
 *     val db: Database @@ scope.Tag = scope.allocate(Resource[Database])
 *
 *     val program: Scoped[scope.Tag, Result] = for {
 *       conn <- db.map(_.connect())
 *       data <- db.map(_.query("SELECT *"))
 *     } yield process(conn, data)
 *
 *     scope.execute(program)  // Execute the Scoped computation
 *   }
 *   }}}
 *
 * @tparam Tag
 *   the scope tag required to execute this computation (contravariant)
 * @tparam A
 *   the result type of the computation (covariant)
 *
 * @see
 *   [[@@]] for scoped values
 * @see
 *   [[Scope.execute]] for executing Scoped computations
 */
final class Scoped[-Tag, +A] private (private val executeFn: () => A) {

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
  def map[B](f: A => B): Scoped[Tag, B] =
    new Scoped(() => f(executeFn()))

  /**
   * FlatMaps this Scoped computation with a function returning another scoped
   * value.
   *
   * The resulting computation requires both this computation's Tag and the
   * result's Tag, combined via intersection.
   *
   * @param f
   *   function from result to scoped value
   * @tparam B
   *   the result type
   * @tparam T
   *   the additional tag requirement
   * @return
   *   a Scoped computation with combined tag `Tag & T`
   */
  def flatMap[B, T](f: A => B @@ T): Scoped[Tag & T, B] =
    new Scoped(() => @@.unscoped(f(executeFn())))

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
   * @example
   *   {{{
   *   val program: Scoped[scope.Tag, Result] = for {
   *     db     <- scopedDb         // Database @@ scope.Tag
   *     config <- Scoped(myConfig) // lift ordinary value
   *   } yield db.query(config.sql)
   *   }}}
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
}

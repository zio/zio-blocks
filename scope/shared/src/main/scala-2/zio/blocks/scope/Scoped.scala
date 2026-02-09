package zio.blocks.scope

import scala.language.implicitConversions

/**
 * Companion object for the `@@` type providing scoping operations.
 *
 * In Scala 2, `@@` is implemented via `ScopedModule` rather than an opaque
 * type. The `scoped` and `unscoped` operations delegate to the module
 * implementation.
 *
 * @see
 *   [[ScopedOps]] for extension methods on scoped values
 */
object @@ {

  /**
   * Scopes a value with a scope identity.
   *
   * This wraps a raw value `A` into a scoped value `A @@ S`. The scope tag `S`
   * is typically the path-dependent `Tag` type of a [[Scope]] instance.
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
  def scoped[A, S](a: A): A @@ S = ScopedModule.instance.scoped(a)

  /**
   * Retrieves the underlying value without unscoping (internal use only).
   *
   * This is package-private to prevent bypassing the scope safety checks.
   * External code should use `$` or `get` with proper scope in context.
   */
  private[scope] def unscoped[A, S](scoped: A @@ S): A = ScopedModule.instance.unscoped(scoped)
}

/**
 * Implicit class providing operations on scoped values (Scala 2).
 *
 * This is automatically available via the implicit conversion in
 * [[ScopedOps$]]. Use [[toScopedOps]] to convert a scoped value to this
 * wrapper.
 *
 * @example
 *   {{{
 *   val stream: InputStream @@ scope.Tag = closeable.value
 *   stream.$(_.read())  // Returns Int (unscoped, since Int is Unscoped)
 *   stream.map(_.available)  // Returns Int @@ scope.Tag
 *   }}}
 *
 * @tparam A
 *   the underlying value type
 * @tparam S
 *   the scope tag type
 *
 * @see
 *   [[@@]] for scoping operations
 * @see
 *   [[ScopeEscape]] for the typeclass determining escape behavior
 */
final class ScopedOps[A, S](private val scoped: A @@ S) extends AnyVal {

  /**
   * Maps over a scoped value, returning a Scoped computation.
   *
   * The function `f` is applied to the underlying value when the Scoped
   * computation is executed by a matching scope. This builds a description of
   * work, not immediate execution.
   *
   * @param f
   *   the function to apply to the underlying value
   * @tparam B
   *   the result type of the function
   * @return
   *   a Scoped computation that will apply f when executed
   */
  def map[B](f: A => B): Scoped[S, B] =
    Scoped.create(() => f(@@.unscoped(scoped)))

  /**
   * FlatMaps over a scoped value, combining scope tags via intersection.
   *
   * Enables for-comprehension syntax with scoped values. The resulting Scoped
   * computation is tagged with the intersection of both scope tags, ensuring it
   * can only be executed where both scopes are available.
   *
   * @param f
   *   function returning a scoped result
   * @tparam B
   *   the underlying result type
   * @tparam T
   *   the scope tag of the returned value
   * @return
   *   a Scoped computation with combined scope tag `S with T`
   */
  def flatMap[B, T](f: A => B @@ T): Scoped[S with T, B] =
    Scoped.create(() => @@.unscoped(f(@@.unscoped(scoped))))

  /**
   * Extracts the first element of a scoped tuple.
   *
   * @return
   *   the first element, still scoped with tag `S`
   */
  def _1[X, Y](implicit ev: A =:= (X, Y)): X @@ S =
    @@.scoped(ev(@@.unscoped(scoped))._1)

  /**
   * Extracts the second element of a scoped tuple.
   *
   * @return
   *   the second element, still scoped with tag `S`
   */
  def _2[X, Y](implicit ev: A =:= (X, Y)): Y @@ S =
    @@.scoped(ev(@@.unscoped(scoped))._2)
}

/**
 * Companion object providing implicit conversion to [[ScopedOps]].
 */
object ScopedOps {

  /**
   * Implicit conversion from a scoped value to [[ScopedOps]].
   *
   * This enables the extension methods (`$`, `get`, `map`, etc.) on scoped
   * values.
   */
  implicit def toScopedOps[A, S](scoped: A @@ S): ScopedOps[A, S] = new ScopedOps(scoped)
}

/**
 * A deferred scoped computation that can only be executed by an appropriate
 * Scope.
 *
 * `Scoped[-Tag, +A]` is a description of a computation that produces an `A` and
 * requires a scope with tag `<: Tag` to execute. It is a simple thunk, not a
 * free monad.
 *
 * @tparam Tag
 *   the scope tag required to execute this computation (contravariant)
 * @tparam A
 *   the result type of the computation (covariant)
 */
final class Scoped[-Tag, +A] private (private val executeFn: () => A) {

  /**
   * Maps over the result of this Scoped computation.
   */
  def map[B](f: A => B): Scoped[Tag, B] =
    new Scoped(() => f(executeFn()))

  /**
   * FlatMaps this Scoped computation with a function returning another scoped
   * value.
   */
  def flatMap[B, T](f: A => B @@ T): Scoped[Tag with T, B] =
    new Scoped(() => @@.unscoped(f(executeFn())))

  /**
   * Executes the scoped computation and returns the result.
   */
  private[scope] def run(): A = executeFn()
}

object Scoped {

  /**
   * Lifts a value into a Scoped computation.
   *
   * This allows ordinary values to participate in `Scoped` for-comprehensions
   * alongside scoped values. The resulting computation has no scope requirements
   * (uses `Any` as the tag), so it can be combined with any other `Scoped`.
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
   * Creates a Scoped computation from a thunk with an explicit tag.
   *
   * This is an internal factory used by the `@@` extension methods.
   */
  private[scope] def create[Tag, A](f: () => A): Scoped[Tag, A] =
    new Scoped(f)
}

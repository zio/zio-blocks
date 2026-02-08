package zio.blocks.scope

/**
 * A description of how to acquire and release a resource.
 *
 * Resources encapsulate both acquisition and finalization, tying a value's
 * lifecycle to a [[Scope]]. When a resource is created via
 * `scope.create(resource)`, the scope registers any finalizers and ensures they
 * run when the scope closes.
 *
 * Resources are lazy—they describe ''what'' to do, not ''when''. Creation only
 * happens when passed to a scope via `scope.create(resource)`.
 *
 * ==Resource Types==
 *
 *   - [[Resource.Shared]]: Memoized within a single Wire graph (default from
 *     Wire)
 *   - [[Resource.Unique]]: Fresh instance each time (default from direct
 *     creation)
 *
 * ==Creating Resources==
 *
 *   - `Resource(=> a)`: By-name value; auto-registers `close()` if
 *     `AutoCloseable`
 *   - `Resource.acquireRelease(acquire)(release)`: Explicit acquire/release
 *   - `Resource.fromAutoCloseable(=> a)`: For `AutoCloseable` subtypes
 *   - `Resource[T]` macro (Scala 3): Derives from constructor
 *
 * @example
 *   {{{
 *   Scope.global.scoped { scope =>
 *     val db = scope.create(Resource.fromAutoCloseable(new Database()))
 *     scope.$(db)(_.query("SELECT 1"))
 *   }
 *   }}}
 *
 * @tparam A
 *   the type of value this resource produces
 *
 * @see
 *   [[Scope.create]] for using resources [[Wire.toResource]] for converting
 *   wires
 */
sealed trait Resource[+A] {

  /**
   * Acquires the resource value using the given scope for finalizer
   * registration.
   *
   * This method is package-private to ensure resources are only created through
   * [[Scope.create]], which properly tags the result with the scope's identity.
   *
   * @param scope
   *   the scope to register finalizers with
   * @return
   *   the acquired resource value
   */
  private[scope] def make(scope: Scope[?, ?]): A
}

object Resource extends ResourceCompanionVersionSpecific {

  /**
   * A resource that produces shared (memoized) instances within a Wire graph.
   *
   * When used in dependency injection via [[Wire]], shared resources create one
   * instance that is reused across all dependents within the same scope.
   */
  final class Shared[+A] private[scope] (
    private[scope] val makeFn: Scope[?, ?] => A
  ) extends Resource[A] {
    private[scope] def make(scope: Scope[?, ?]): A = makeFn(scope)
  }

  /**
   * A resource that produces unique instances each time.
   *
   * Each call to `scope.create` with a unique resource produces a fresh value.
   * Use for resources that should not be shared, like per-request state.
   */
  final class Unique[+A] private[scope] (
    private[scope] val makeFn: Scope[?, ?] => A
  ) extends Resource[A] {
    private[scope] def make(scope: Scope[?, ?]): A = makeFn(scope)
  }

  /**
   * Creates a resource from a by-name value.
   *
   * If the value implements `AutoCloseable`, its `close()` method is
   * automatically registered as a finalizer. For non-closeable values, no
   * finalization is performed.
   *
   * @param value
   *   a by-name expression that produces the resource value
   * @tparam A
   *   the value type
   * @return
   *   a resource that acquires the value and auto-closes if applicable
   *
   * @example
   *   {{{
   *   // AutoCloseable is auto-finalized
   *   val dbResource = Resource(new Database())
   *
   *   // Non-closeable is just wrapped
   *   val configResource = Resource(Config("localhost", 8080))
   *   }}}
   */
  def apply[A](value: => A): Resource[A] = new Unique[A](scope => {
    val a = value
    a match {
      case closeable: AutoCloseable => scope.defer(closeable.close())
      case _                        => ()
    }
    a
  })

  /**
   * Creates a resource with explicit acquire and release functions.
   *
   * The `acquire` thunk is called when the resource is created. The `release`
   * function is registered as a finalizer and runs when the scope closes.
   *
   * @param acquire
   *   a by-name expression that acquires the resource
   * @param release
   *   a function that releases the resource
   * @tparam A
   *   the resource type
   * @return
   *   a resource with explicit lifecycle management
   *
   * @example
   *   {{{
   *   val fileResource = Resource.acquireRelease {
   *     new FileInputStream("data.txt")
   *   } { stream =>
   *     stream.close()
   *   }
   *   }}}
   */
  def acquireRelease[A](acquire: => A)(release: A => Unit): Resource[A] = new Unique[A](scope => {
    val a = acquire
    scope.defer(release(a))
    a
  })

  /**
   * Creates a resource from an `AutoCloseable` value.
   *
   * The value's `close()` method is registered as a finalizer. This is
   * type-safe alternative to `Resource(value)` when you know the value is
   * `AutoCloseable` at compile time.
   *
   * @param thunk
   *   a by-name expression that produces the `AutoCloseable` value
   * @tparam A
   *   the `AutoCloseable` subtype
   * @return
   *   a resource that closes the value when the scope closes
   *
   * @example
   *   {{{
   *   val streamResource = Resource.fromAutoCloseable {
   *     new BufferedInputStream(new FileInputStream("data.bin"))
   *   }
   *   }}}
   */
  def fromAutoCloseable[A <: AutoCloseable](thunk: => A): Resource[A] = new Unique[A](scope => {
    val a = thunk
    scope.defer(a.close())
    a
  })

  /**
   * Creates a shared resource from a function.
   *
   * Used internally by [[Wire.toResource]] to create memoized resources. Prefer
   * `Resource.apply` or `Resource.acquireRelease` for direct use.
   *
   * @param f
   *   a function from scope to value
   * @tparam A
   *   the value type
   * @return
   *   a shared resource
   */
  def shared[A](f: Scope[?, ?] => A): Resource.Shared[A] = new Shared(f)

  /**
   * Creates a unique resource from a function.
   *
   * Used internally when converting [[Wire.Unique]] to resources. Prefer
   * `Resource.apply` or `Resource.acquireRelease` for direct use.
   *
   * @param f
   *   a function from scope to value
   * @tparam A
   *   the value type
   * @return
   *   a unique resource
   */
  def unique[A](f: Scope[?, ?] => A): Resource.Unique[A] = new Unique(f)
}

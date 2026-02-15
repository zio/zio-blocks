package zio.blocks.scope

import java.util.concurrent.atomic.AtomicReference

/**
 * A description of how to acquire and release a resource.
 *
 * Resources encapsulate both acquisition and finalization, tying a value's
 * lifecycle to a [[Scope]]. When a resource is created via
 * `scope.allocate(resource)`, the scope registers any finalizers and ensures
 * they run when the scope closes.
 *
 * Resources are lazy—they describe ''what'' to do, not ''when''. Creation only
 * happens when passed to a scope via `scope.allocate(resource)`.
 *
 * ==Creating Resources==
 *
 *   - `Resource(=> a)`: By-name value; auto-registers `close()` if
 *     `AutoCloseable`
 *   - `Resource.acquireRelease(acquire)(release)`: Explicit acquire/release
 *   - `Resource.fromAutoCloseable(=> a)`: For `AutoCloseable` subtypes
 *   - `Resource.from[T]` macro: Derives from constructor
 *
 * @example
 *   {{{
 *   Scope.global.scoped { scope =>
 *     import scope._
 *     val db = allocate(Resource.fromAutoCloseable(new Database()))
 *     $(db)(_.query("SELECT 1"))
 *   }
 *   }}}
 *
 * @tparam A
 *   the type of value this resource produces
 *
 * @see
 *   [[Scope.allocate[A](resource:zio\.blocks\.scope\.Resource[A])* Scope.allocate]]
 *   for using resources
 * @see
 *   `Wire.toResource` for converting wires
 */
sealed trait Resource[+A] { self =>

  /**
   * Acquires the resource value using the given scope for cleanup registration.
   *
   * This method is package-private to ensure resources are only created through
   * [[Scope.allocate]], which properly tags the result with the scope's
   * identity.
   *
   * @param scope
   *   the scope to register cleanup actions with
   * @return
   *   the acquired resource value
   */
  private[scope] def make(scope: Scope): A

  /**
   * Transforms the value produced by this resource.
   *
   * The transformation function `f` is applied after the resource is acquired.
   * Finalizers from the original resource still run when the scope closes.
   *
   * @param f
   *   the transformation function
   * @tparam B
   *   the result type
   * @return
   *   a new resource that produces transformed values
   *
   * @example
   *   {{{
   *   val portResource: Resource[Int] = Resource(8080)
   *   val urlResource: Resource[String] = portResource.map(port => s"http://localhost:$$port")
   *   }}}
   */
  def map[B](f: A => B): Resource[B] = new Resource.Unique[B](scope => f(self.make(scope)))

  /**
   * Sequences two resources, using the result of this resource to create
   * another.
   *
   * Both resources' finalizers are registered: the inner resource's finalizers
   * run before the outer resource's finalizers (LIFO order).
   *
   * @param f
   *   a function that produces a resource from the value of this resource
   * @tparam B
   *   the type produced by the resulting resource
   * @return
   *   a new resource combining both acquisitions
   *
   * @example
   *   {{{
   *   val configResource: Resource[Config] = Resource(loadConfig())
   *   val dbResource: Resource[Database] = configResource.flatMap { config =>
   *     Resource.fromAutoCloseable(new Database(config.url))
   *   }
   *   }}}
   */
  def flatMap[B](f: A => Resource[B]): Resource[B] = new Resource.Unique[B](scope => {
    val a = self.make(scope)
    f(a).make(scope)
  })

  /**
   * Combines this resource with another, producing a tuple of both values.
   *
   * Both resources are acquired, and both sets of finalizers are registered.
   * Finalizers run in LIFO order (second resource's finalizers before first).
   *
   * @param that
   *   the resource to combine with
   * @tparam B
   *   the type produced by the other resource
   * @return
   *   a new resource producing a tuple of both values
   *
   * @example
   *   {{{
   *   val dbResource: Resource[Database] = Resource.fromAutoCloseable(new Database())
   *   val cacheResource: Resource[Cache] = Resource.fromAutoCloseable(new Cache())
   *   val combined: Resource[(Database, Cache)] = dbResource.zip(cacheResource)
   *   }}}
   */
  def zip[B](that: Resource[B]): Resource[(A, B)] = new Resource.Unique[(A, B)](scope => {
    val a = self.make(scope)
    val b = that.make(scope)
    (a, b)
  })
}

object Resource extends ResourceCompanionVersionSpecific {

  /**
   * A resource that produces shared (memoized) instances with reference
   * counting.
   *
   * The first call to `make` initializes the value using an OpenScope parented
   * to Scope.global. Subsequent calls increment a reference count. Each scope
   * that receives the value registers a finalizer that decrements the count.
   * When the count reaches zero, the shared scope is closed.
   *
   * This is thread-safe and lock-free using AtomicReference with CAS.
   */
  private[scope] final class Shared[A] private[scope] (
    private[scope] val makeFn: Scope => A
  ) extends Resource[A] {
    import Resource.SharedState._

    private val state: AtomicReference[SharedState[A]] = new AtomicReference(Uninitialized)

    private[scope] def make(scope: Scope): A = {
      var result: A = null.asInstanceOf[A]
      var done      = false

      while (!done) {
        state.get() match {
          case Uninitialized =>
            if (state.compareAndSet(Uninitialized, Pending)) {
              val os = Scope.global.open()
              try {
                val value = makeFn(os.scope)
                state.set(Created(value, os, 1))
                result = value
                done = true
              } catch {
                case t: Throwable =>
                  os.close()
                  state.set(Uninitialized)
                  throw t
              }
            }

          case Pending =>
            PlatformScope.threadYield()

          case created: Created[A @unchecked] =>
            val newState = created.copy(refCount = created.refCount + 1)
            if (state.compareAndSet(created, newState)) {
              result = created.value
              done = true
            }

          case Destroyed =>
            throw new IllegalStateException("Cannot allocate from a destroyed shared resource")
        }
      }

      scope.defer {
        var decrementDone = false
        while (!decrementDone) {
          state.get() match {
            case created: Created[A @unchecked] =>
              if (created.refCount == 1) {
                if (state.compareAndSet(created, Destroyed)) {
                  created.openScope.close().orThrow()
                  decrementDone = true
                }
              } else {
                val newState = created.copy(refCount = created.refCount - 1)
                if (state.compareAndSet(created, newState)) {
                  decrementDone = true
                }
              }

            case Destroyed =>
              decrementDone = true

            case Uninitialized | Pending =>
              throw new IllegalStateException("Shared resource in unexpected state during finalization")
          }
        }
      }

      result
    }
  }

  /**
   * State machine for [[Shared]] resource lifecycle.
   *
   * Transitions: Uninitialized → Pending → Created → Destroyed
   *
   * @tparam A
   *   the type of value managed by this state
   */
  private[scope] sealed trait SharedState[+A]
  private[scope] object SharedState {
    case object Uninitialized                                                        extends SharedState[Nothing]
    case object Pending                                                              extends SharedState[Nothing]
    final case class Created[A](value: A, openScope: Scope.OpenScope, refCount: Int) extends SharedState[A]
    case object Destroyed                                                            extends SharedState[Nothing]
  }

  /**
   * A resource that produces unique instances each time.
   *
   * Each call to `scope.allocate` with a unique resource produces a fresh
   * value. Use for resources that should not be shared, like per-request state.
   */
  private[scope] final class Unique[+A] private[scope] (
    private[scope] val makeFn: Scope => A
  ) extends Resource[A] {
    private[scope] def make(scope: Scope): A = makeFn(scope)
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
   *   a unique resource that acquires the value and auto-closes if applicable
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
   *   a unique resource with explicit lifecycle management
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
   *   a unique resource that closes the value when the scope closes
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
   * Shared resources are memoized: the first call initializes the value,
   * subsequent calls return the same instance with reference counting.
   * Finalizers run when the last reference is released.
   *
   * @param f
   *   a function from scope to value
   * @tparam A
   *   the value type
   * @return
   *   a shared resource
   */
  def shared[A](f: Scope => A): Resource[A] = new Shared(f)

  /**
   * Creates a unique resource from a function.
   *
   * Unique resources create a fresh instance each time they are allocated.
   *
   * @param f
   *   a function from scope to value
   * @tparam A
   *   the value type
   * @return
   *   a unique resource
   */
  def unique[A](f: Scope => A): Resource[A] = new Unique(f)
}

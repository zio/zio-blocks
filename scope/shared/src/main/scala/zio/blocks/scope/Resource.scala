package zio.blocks.scope

import java.util.concurrent.atomic.AtomicReference
import zio.blocks.context._
import zio.blocks.scope.internal.ProxyFinalizer

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
 *     val db = scope.allocate(Resource.fromAutoCloseable(new Database()))
 *     scope.$(db)(_.query("SELECT 1"))
 *   }
 *   }}}
 *
 * @tparam A
 *   the type of value this resource produces
 *
 * @see
 *   [[Scope.allocate]] for using resources
 * @see
 *   `Wire.toResource` for converting wires
 */
sealed trait Resource[+A] { self =>

  /**
   * Acquires the resource value using the given finalizer for cleanup
   * registration.
   *
   * This method is package-private to ensure resources are only created through
   * [[Scope.allocate]], which properly tags the result with the scope's
   * identity.
   *
   * @param finalizer
   *   the finalizer to register cleanup actions with
   * @return
   *   the acquired resource value
   */
  private[scope] def make(finalizer: Finalizer): A

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
   *   val urlResource: Resource[String] = portResource.map(port => s"http://localhost:$port")
   *   }}}
   */
  def map[B](f: A => B): Resource[B] = new Resource.Unique[B](finalizer => f(self.make(finalizer)))

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
  def flatMap[B](f: A => Resource[B]): Resource[B] = new Resource.Unique[B](finalizer => {
    val a = self.make(finalizer)
    f(a).make(finalizer)
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
  def zip[B](that: Resource[B]): Resource[(A, B)] = new Resource.Unique[(A, B)](finalizer => {
    val a = self.make(finalizer)
    val b = that.make(finalizer)
    (a, b)
  })

  /**
   * Wraps this resource's value in a [[Context]], enabling type-indexed access.
   *
   * This is useful when you want to combine multiple resources into a single
   * [[Context]] that can be passed around and queried by type.
   *
   * @tparam A1
   *   the type of value produced by this resource (must be a nominal type)
   * @return
   *   a resource producing a [[Context]] containing the original value
   *
   * @example
   *   {{{
   *   case class Database(url: String)
   *   val dbResource: Resource[Database] = Resource(Database("jdbc://localhost"))
   *   val ctxResource: Resource[Context[Database]] = dbResource.contextual
   *   }}}
   */
  def contextual[A1 >: A](implicit ev: IsNominalType[A1]): Resource[Context[A1]] =
    self.map(a => Context[A1](a)(ev))

  /**
   * Combines this resource's [[Context]] with another resource's [[Context]],
   * merging their contents.
   *
   * This method is available when both resources produce contexts. It combines
   * the contexts using `Context.++`, with entries from `that` taking precedence
   * when both contexts contain the same type.
   *
   * @tparam R1
   *   the type parameter of this resource's Context
   * @tparam R2
   *   the type parameter of the other resource's Context
   * @param that
   *   the resource whose context to merge with
   * @param ev
   *   evidence that this resource produces a `Context[R1]`
   * @return
   *   a resource producing a merged [[Context]] containing entries from both
   *
   * @example
   *   {{{
   *   case class Database(url: String)
   *   case class Cache(size: Int)
   *
   *   val dbCtx: Resource[Context[Database]] = Resource(Database("jdbc://")).contextual
   *   val cacheCtx: Resource[Context[Cache]] = Resource(Cache(100)).contextual
   *
   *   val combined: Resource[Context[Database & Cache]] = dbCtx ++ cacheCtx
   *   }}}
   */
  def ++[R1, R2](that: Resource[Context[R2]])(implicit ev: A <:< Context[R1]): Resource[Context[R1 & R2]] =
    self.flatMap(a => that.map(b => ev(a) ++ b))

  /**
   * Appends a resource's value to this resource's [[Context]].
   *
   * This method is available when this resource produces a `Context[R1]`. It
   * acquires the other resource and adds its value to the context, expanding
   * the type to include the new entry.
   *
   * @tparam R1
   *   the type parameter of this resource's Context
   * @tparam B
   *   the type of value produced by the other resource (must be a nominal type)
   * @param that
   *   the resource whose value to add to the context
   * @param ev
   *   evidence that this resource produces a `Context[R1]`
   * @param evB
   *   evidence that `B` is a nominal type
   * @return
   *   a resource producing a [[Context]] with the added value
   *
   * @example
   *   {{{
   *   case class Database(url: String)
   *   case class Cache(size: Int)
   *
   *   val dbCtx: Resource[Context[Database]] = Resource(Database("jdbc://")).contextual
   *   val cacheRes: Resource[Cache] = Resource(Cache(100))
   *   val combined: Resource[Context[Database & Cache]] = dbCtx :+ cacheRes
   *   }}}
   */
  def :+[R1, B](that: Resource[B])(implicit ev: A <:< Context[R1], evB: IsNominalType[B]): Resource[Context[R1 & B]] =
    self.flatMap(a => that.map(b => ev(a).add[B](b)(evB)))
}

object Resource extends ResourceCompanionVersionSpecific {

  /**
   * A resource that produces shared (memoized) instances with reference
   * counting.
   *
   * The first call to `make` initializes the value using a proxy finalizer that
   * collects cleanup actions. Subsequent calls increment a reference count.
   * Each scope that receives the value registers a finalizer that decrements
   * the count. When the count reaches zero, collected finalizers run.
   *
   * This is thread-safe and lock-free using AtomicReference with CAS.
   */
  private[scope] final class Shared[A] private[scope] (
    private[scope] val makeFn: Finalizer => A
  ) extends Resource[A] {
    import Resource.SharedState._

    private val state: AtomicReference[SharedState[A]] = new AtomicReference(Uninitialized)

    private[scope] def make(realFinalizer: Finalizer): A = {
      var result: A = null.asInstanceOf[A]
      var done      = false

      while (!done) {
        state.get() match {
          case Uninitialized =>
            // Try to become the initializer
            if (state.compareAndSet(Uninitialized, Pending)) {
              // We won - initialize the resource
              val proxy = new ProxyFinalizer
              try {
                val value = makeFn(proxy)
                // Transition to Created with refCount=1
                state.set(Created(value, proxy, 1))
                result = value
                done = true
              } catch {
                case t: Throwable =>
                  // Initialization failed - reset to Uninitialized so others can retry
                  state.set(Uninitialized)
                  throw t
              }
            }
          // else: lost race, loop and retry

          case Pending =>
            // Another thread is initializing - spin wait
            PlatformScope.threadYield()

          case created: Created[A @unchecked] =>
            // Resource exists - try to increment refCount
            val newState = created.copy(refCount = created.refCount + 1)
            if (state.compareAndSet(created, newState)) {
              result = created.value
              done = true
            }
          // else: state changed, retry

          case Destroyed =>
            throw new IllegalStateException("Cannot allocate from a destroyed shared resource")
        }
      }

      // Register a decrement finalizer with the real scope
      realFinalizer.defer {
        var decrementDone = false
        while (!decrementDone) {
          state.get() match {
            case created: Created[A @unchecked] =>
              if (created.refCount == 1) {
                // We're the last reference - transition to Destroyed and run finalizers
                if (state.compareAndSet(created, Destroyed)) {
                  val errors = created.proxy.runAll()
                  if (errors.nonEmpty) {
                    val first = errors.head
                    errors.tail.foreach(first.addSuppressed)
                    throw first
                  }
                  decrementDone = true
                }
                // else: state changed, retry
              } else {
                // Decrement refCount
                val newState = created.copy(refCount = created.refCount - 1)
                if (state.compareAndSet(created, newState)) {
                  decrementDone = true
                }
                // else: state changed, retry
              }

            case Destroyed =>
              // Already destroyed (shouldn't happen in correct usage)
              decrementDone = true

            case Uninitialized | Pending =>
              // Shouldn't happen - we have a reference so it must be Created
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
    case object Uninitialized                                                   extends SharedState[Nothing]
    case object Pending                                                         extends SharedState[Nothing]
    final case class Created[A](value: A, proxy: ProxyFinalizer, refCount: Int) extends SharedState[A]
    case object Destroyed                                                       extends SharedState[Nothing]
  }

  /**
   * A resource that produces unique instances each time.
   *
   * Each call to `scope.allocate` with a unique resource produces a fresh
   * value. Use for resources that should not be shared, like per-request state.
   */
  private[scope] final class Unique[+A] private[scope] (
    private[scope] val makeFn: Finalizer => A
  ) extends Resource[A] {
    private[scope] def make(finalizer: Finalizer): A = makeFn(finalizer)
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
  def apply[A](value: => A): Resource[A] = new Unique[A](finalizer => {
    val a = value
    a match {
      case closeable: AutoCloseable => finalizer.defer(closeable.close())
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
  def acquireRelease[A](acquire: => A)(release: A => Unit): Resource[A] = new Unique[A](finalizer => {
    val a = acquire
    finalizer.defer(release(a))
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
  def fromAutoCloseable[A <: AutoCloseable](thunk: => A): Resource[A] = new Unique[A](finalizer => {
    val a = thunk
    finalizer.defer(a.close())
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
   *   a function from finalizer to value
   * @tparam A
   *   the value type
   * @return
   *   a shared resource
   */
  def shared[A](f: Finalizer => A): Resource[A] = new Shared(f)

  /**
   * Creates a unique resource from a function.
   *
   * Unique resources create a fresh instance each time they are allocated.
   *
   * @param f
   *   a function from finalizer to value
   * @tparam A
   *   the value type
   * @return
   *   a unique resource
   */
  def unique[A](f: Finalizer => A): Resource[A] = new Unique(f)
}

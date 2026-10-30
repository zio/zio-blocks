/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.scope

import java.util.concurrent.atomic.AtomicReference

/**
 * A description of how to acquire a value and how to release it.
 *
 * A `Resource` does nothing when it is created: the value is acquired when a
 * scope allocates the resource (`scope.allocate(resource)`) or when `use` runs,
 * and that scope runs the release steps when it closes. Build resources with
 * these constructors and combine them with `map`, `flatMap` and `zip`.
 *
 * ==Creating Resources==
 *
 *   - `Resource(=> a)`: a by-name value; registers `close()` if the value is
 *     `AutoCloseable` at run time
 *   - `Resource.acquireRelease(acquire)(release)`: explicit acquire and release
 *   - `Resource.fromAutoCloseable(=> a)`: for `AutoCloseable` subtypes
 *   - `Resource.shared(f)` and `Resource.unique(f)`: from a function of the
 *     scope, shared by reference counting or fresh on every allocation
 *   - `Resource.from[T]`: derived from `T`'s constructor (a macro)
 *
 * {{{
 * final class Connection(val url: String) extends AutoCloseable {
 *   def query(sql: String): Int = sql.length
 *   def close(): Unit           = println("closed " + url)
 * }
 *
 * val connection: Resource[Connection] =
 *   Resource(new Connection("jdbc:h2:mem:shop"))
 *
 * val rows: Int = connection.use(_.query("SELECT 1"))
 * // prints: closed jdbc:h2:mem:shop
 * // rows: 8
 * }}}
 *
 * @tparam A
 *   the type of value this resource produces
 *
 * @see
 *   [[Scope.allocate[A](resource:zio\.blocks\.scope\.Resource[A])* Scope.allocate]]
 *   for using resources
 * @see
 *   `Wire.toResource` for converting wires
 * @see
 *   [[https://zioblocks.com/reference/resource-management/resource/ Resource]]
 */
sealed trait Resource[+A] { self =>

  /**
   * Acquires the resource value using the given scope for cleanup registration.
   *
   * This method is package-private: user code acquires a resource through
   * [[Scope.allocate]], which tags the result with the scope's identity, or
   * through `use`.
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
   * {{{
   * val port: Resource[Int]   = Resource(8080)
   * val url: Resource[String] = port.map(p => "http://localhost:" + p)
   *
   * url.use(u => u) // "http://localhost:8080"
   * }}}
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
   * {{{
   * final case class DbConfig(url: String)
   * final class Database(val url: String) extends AutoCloseable {
   *   def close(): Unit = println("closed " + url)
   * }
   *
   * val config: Resource[DbConfig] = Resource(DbConfig("jdbc:h2:mem:shop"))
   * val database: Resource[Database] = config.flatMap { c =>
   *   Resource.fromAutoCloseable(new Database(c.url))
   * }
   * }}}
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
   * {{{
   * final class Database extends AutoCloseable {
   *   def close(): Unit = println("database closed")
   * }
   * final class Cache extends AutoCloseable {
   *   def close(): Unit = println("cache closed")
   * }
   *
   * val both: Resource[(Database, Cache)] =
   *   Resource(new Database).zip(Resource(new Cache))
   *
   * both.use(_ => ())
   * // prints: cache closed
   * // prints: database closed
   * }}}
   */
  def zip[B](that: Resource[B]): Resource[(A, B)] = new Resource.Unique[(A, B)](scope => {
    val a = self.make(scope)
    val b = that.make(scope)
    (a, b)
  })

  /**
   * Acquires this resource in a new child of [[Scope.global]], applies `f` to
   * the value, and closes the child when `f` returns or throws.
   *
   * It works like allocating the resource inside `Scope.global.scoped`, except
   * that `f` receives the raw value, and it is a short entry point for
   * application code. The result type must have an [[Unscoped]] instance. On
   * Scala 3, a case class that extends `AutoCloseable` gets one automatically,
   * so `use(c => c)` on such a resource compiles and returns a closed value (a
   * known bug).
   *
   * {{{
   * final class Server(val port: Int) extends AutoCloseable {
   *   def start(): String = "listening on " + port
   *   def close(): Unit   = println("server stopped")
   * }
   *
   * val status: String = Resource(new Server(8080)).use(_.start())
   * // prints: server stopped
   * // status: listening on 8080
   * }}}
   *
   * @param f
   *   callback that uses the acquired value
   * @tparam B
   *   result type; must have an [[Unscoped]] instance
   * @return
   *   the callback result after all finalizers have run
   */
  final def use[B](f: A => B)(implicit ev: Unscoped[B]): B =
    Scope.global.scoped { scope =>
      f(self.make(scope))
    }
}

/**
 * Constructors for [[Resource]]: from a value, an acquire and release pair, an
 * `AutoCloseable`, a function of the scope, or (`Resource.from[T]`) a
 * constructor.
 */
object Resource extends ResourceCompanionVersionSpecific {

  /**
   * A resource that produces shared (memoized) instances with reference
   * counting.
   *
   * The first call to `make` initializes the value using an OpenScope parented
   * to Scope.global. Subsequent calls increment a reference count. Each scope
   * that receives the value registers a finalizer that decrements the count.
   * When the count reaches zero, the shared scope is closed and the resource
   * moves to `Destroyed`, after which `make` throws.
   *
   * The state is an `AtomicReference` updated with compare-and-set. While one
   * thread runs `makeFn`, other callers spin (yielding the thread) until the
   * value is ready.
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
   * Transitions: Uninitialized → Pending → Created → Destroyed, and Pending →
   * Uninitialized when the construction function throws.
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
   * {{{
   * final class Database extends AutoCloseable {
   *   def close(): Unit = println("database closed")
   * }
   * final case class ServerConfig(host: String, port: Int)
   *
   * // AutoCloseable: close() runs when the scope closes
   * val database: Resource[Database] = Resource(new Database)
   *
   * // not AutoCloseable: no finalizer
   * val config: Resource[ServerConfig] = Resource(ServerConfig("localhost", 8080))
   * }}}
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
   * The `acquire` thunk runs each time a scope allocates the resource. The
   * `release` function is registered as a finalizer of that scope and runs when
   * the scope closes.
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
   * {{{
   * import java.util.concurrent.{ExecutorService, Executors}
   *
   * val workers: Resource[ExecutorService] =
   *   Resource.acquireRelease(Executors.newFixedThreadPool(4))(_.shutdown())
   * }}}
   */
  def acquireRelease[A](acquire: => A)(release: A => Unit): Resource[A] = new Unique[A](scope => {
    val a = acquire
    scope.defer(release(a))
    a
  })

  /**
   * Creates a resource from an `AutoCloseable` value.
   *
   * The value's `close()` method is registered as a finalizer. Unlike
   * `Resource(value)`, which checks for `AutoCloseable` at run time, this
   * requires it at compile time.
   *
   * @param thunk
   *   a by-name expression that produces the `AutoCloseable` value
   * @tparam A
   *   the `AutoCloseable` subtype
   * @return
   *   a unique resource that closes the value when the scope closes
   *
   * {{{
   * import java.io.{BufferedInputStream, FileInputStream}
   *
   * val input: Resource[BufferedInputStream] = Resource.fromAutoCloseable {
   *   new BufferedInputStream(new FileInputStream("data.bin"))
   * }
   * }}}
   */
  def fromAutoCloseable[A <: AutoCloseable](thunk: => A): Resource[A] = new Unique[A](scope => {
    val a = thunk
    scope.defer(a.close())
    a
  })

  /**
   * Creates a shared resource from a function.
   *
   * The first allocation runs `f` and later allocations get the same instance,
   * counting references. `f` receives the shared instance's own scope (a child
   * of [[Scope.global]]), not the allocating scope, and the finalizers it
   * registers run when the last allocating scope closes. After that the
   * resource is destroyed: allocating it again throws
   * `IllegalStateException("Cannot allocate from a destroyed shared resource")`,
   * so build a new one (for example with a `def`) for each use.
   *
   * {{{
   * final class Pool extends AutoCloseable {
   *   def close(): Unit = println("pool closed")
   * }
   *
   * var created = 0
   * val pool: Resource[Pool] = Resource.shared[Pool] { scope =>
   *   created += 1
   *   val p = new Pool
   *   scope.defer(p.close())
   *   p
   * }
   *
   * Scope.global.scoped { scope =>
   *   import scope._
   *   allocate(pool)
   *   allocate(pool)
   *   ()
   * }
   * // prints: pool closed
   * // created: 1
   * }}}
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
   * Unique resources create a fresh instance each time they are allocated. `f`
   * receives the allocating scope, so it can register finalizers there.
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

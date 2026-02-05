package zio.blocks.scope

import zio.blocks.chunk.Chunk
import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.internal.Finalizers

/**
 * A scope that manages resource lifecycle and tracks available services at the
 * type level.
 *
 * The `Stack` type parameter encodes which services are available in this
 * scope, enabling compile-time verification that requested services exist. This
 * prevents lifecycle errors where resources are used outside their intended
 * scope.
 *
 * The `Tag` type member provides compile-time scope identity for preventing
 * resource escape. Values scoped with `A @@ scope.Tag` can only be accessed
 * through the `$` operator when the matching scope is in context.
 *
 * @example
 *   {{{
 *   // Scala 3
 *   Scope.global.injected[Database].run {
 *     val db = $[Database]  // Compile-time verified
 *     db.query("SELECT ...")
 *   }
 *   // Database is automatically cleaned up here
 *   }}}
 *
 * @tparam Stack
 *   Type-level encoding of available services (e.g.,
 *   `Context[Database] :: Context[Config] :: TNil`)
 */
sealed trait Scope[+Stack] extends ScopeVersionSpecific[Stack] {

  /**
   * Path-dependent type that identifies this scope at the type level.
   *
   * Child scopes have tags that are SUPERtypes of their parent's tag (via
   * union: `Tag = ParentTag | this.type`). This enables child scopes to use
   * parent-scoped values: when accessing a value scoped with `ParentTag`, the
   * `$` operator requires `scope.Tag >: ParentTag`, which is satisfied by child
   * scopes since `ParentTag <: (ParentTag | this.type)`.
   *
   * The global scope has `Tag = this.type` (a singleton type). Each child
   * scope's Tag includes the parent's Tag in a union, forming a chain where
   * deeper scopes have "wider" tags that encompass all ancestor scopes.
   */
  type Tag

  private[scope] def getImpl[T](nom: IsNominalType[T]): T

  /**
   * Registers a finalizer to run when this scope closes.
   *
   * Finalizers run in LIFO order (last registered runs first). If a finalizer
   * throws an exception, remaining finalizers still run, and the first
   * exception is propagated.
   *
   * @param finalizer
   *   Code to execute on scope close (evaluated by-name)
   */
  def defer(finalizer: => Unit): Unit
}

object Scope {

  /**
   * A scope with an unknown stack type. Use in constructors that need `defer`
   * but don't access services.
   */
  type Any = Scope[scala.Any]

  /**
   * A scope that has service `T` available.
   *
   * This is the typical constraint for functions that need to access a service:
   * {{{
   * def doWork()(using Scope.Has[Database]): Unit = {
   *   val db = $[Database]
   *   // ...
   * }
   * }}}
   */
  type Has[+T] = Scope[Context[T] :: scala.Any]

  implicit final class ScopeOps[Stack](private val self: Scope[Stack]) extends AnyVal {

    /**
     * Retrieves a service from this scope.
     *
     * The `InStack` evidence ensures at compile time that `T` exists in the
     * scope's stack.
     *
     * @tparam T
     *   The service type to retrieve
     * @return
     *   The service instance
     */
    def get[T](implicit ev: InStack[T, Stack], nom: IsNominalType[T]): T = self.getImpl(nom)
  }

  /**
   * A scope that can be explicitly closed, releasing all registered resources.
   *
   * Created by `injected[T]`.
   *
   * @tparam Head
   *   The service type at the top of this scope's stack
   * @tparam Tail
   *   The remaining stack from the parent scope
   */
  trait Closeable[+Head, +Tail] extends Scope[Context[Head] :: Tail] with CloseableVersionSpecific[Head, Tail] {

    /**
     * Closes this scope, running all registered finalizers in LIFO order.
     *
     * This method is idempotent - calling it multiple times has no additional
     * effect. Finalizers run even if previous finalizers threw exceptions; all
     * exceptions are collected and returned.
     *
     * @return
     *   A Chunk containing any exceptions thrown by finalizers. Empty if no
     *   errors occurred or if the scope was already closed.
     */
    def close(): Chunk[Throwable]

    /**
     * Closes this scope and throws the first exception if any occurred.
     *
     * Convenience method for cases where you want traditional exception
     * propagation.
     */
    def closeOrThrow(): Unit = close().headOption.foreach(throw _)
  }

  private[scope] def makeCloseable[T, S](
    parent: Scope[?],
    context: Context[T],
    finalizers: Finalizers
  ): Closeable[T, S] =
    ScopeFactory.createScopeImpl[T, S](parent, context, finalizers)

  private val globalInstance: GlobalScope = new GlobalScope

  private[scope] def closeGlobal(): Unit = globalInstance.close()

  /**
   * The global scope, which never closes during normal execution.
   *
   * Use for application-lifetime services. A JVM shutdown hook ensures
   * finalizers run on exit. For tests, use `injected[T]` to create child scopes
   * with deterministic cleanup.
   */
  lazy val global: Scope[TNil] = {
    PlatformScope.registerShutdownHook(() => closeGlobal())
    globalInstance
  }

  private[scope] def createTestableScope(): (Scope[TNil], () => Unit) = {
    val scope = new GlobalScope
    (scope, () => scope.close())
  }

  private final class GlobalScope extends Scope[TNil] {
    type Tag = this.type

    private val finalizers = new Finalizers

    private[scope] def getImpl[T](nom: IsNominalType[T]): T =
      throw new IllegalStateException("Global scope has no services")

    def defer(finalizer: => Unit): Unit = finalizers.add(finalizer)

    private[scope] def close(): Unit = {
      val errors = finalizers.runAll()
      errors.headOption.foreach(throw _)
    }
  }
}

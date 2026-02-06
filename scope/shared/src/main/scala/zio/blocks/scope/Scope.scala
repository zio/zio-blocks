package zio.blocks.scope

import zio.blocks.chunk.Chunk
import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.internal.Finalizers

/**
 * A scope that manages resource lifecycle with path-dependent tag for escape
 * prevention.
 *
 * Scope forms an HList-like structure where each level carries a Context and
 * has its own Tag type. The Tag chain follows the scope structure:
 *   - Global.Tag <: GlobalTag (base case)
 *   - (H :: T).Tag <: T.Tag (child tags are subtypes of parent tags)
 *
 * This enables child scopes to use parent-scoped values: a value tagged with a
 * parent's tag is usable in child scopes since child.Tag <: parent.Tag.
 */
sealed trait Scope extends ScopeVersionSpecific {

  /**
   * Path-dependent type that identifies this scope.
   *
   * Each scope instance has its own unique Tag type. Child scopes have tags
   * that are subtypes of their parent's tag, enabling child scopes to access
   * parent-scoped values while preventing child values from escaping to
   * parents.
   */
  type Tag

  private[scope] def getImpl[T](nom: IsNominalType[T]): T

  /**
   * Registers a finalizer to run when this scope closes.
   *
   * Finalizers run in LIFO order (last registered runs first).
   */
  def defer(finalizer: => Unit): Unit
}

object Scope {

  /**
   * The global tag type - the root of the scope tag hierarchy.
   *
   * All scope tags are subtypes of `GlobalTag`. Since child scope tags are
   * subtypes of their parent's tag, a value tagged with a parent scope can
   * be used in child scopes (the child's tag satisfies the parent tag
   * constraint), but child-tagged values cannot escape to parent scopes.
   *
   * @see [[Global]] for the scope instance that uses this tag
   */
  type GlobalTag

  /**
   * A scope with an unknown structure.
   *
   * Use this type alias in constructors that need access to `defer` for
   * resource cleanup but don't need to access specific services from the
   * scope.
   *
   * @example
   *   {{{
   *   class MyResource()(using Scope.Any) {
   *     val handle = acquire()
   *     defer(handle.release())
   *   }
   *   }}}
   */
  type Any = Scope

  /**
   * A scope that has service `T` available at the head position.
   *
   * This is an alias for `Scope.::[T, Scope]`, representing a scope where
   * the head contains a `Context[T]`. Use this when you need to retrieve
   * a specific service from the scope.
   *
   * @example
   *   {{{
   *   def useDatabase()(using scope: Scope.Has[Database]): Unit = {
   *     val db = $[Database]
   *     db $ (_.query("SELECT ..."))
   *   }
   *   }}}
   *
   * @tparam T the service type available in this scope
   */
  type Has[+T] = ::[T, Scope]

  /**
   * Cons cell: a scope with head context `H` and tail scope `T`.
   *
   * This class forms the HList-like structure of scopes. Each `::` node
   * contains:
   *   - A `Context[H]` holding the head service(s)
   *   - A reference to the tail (parent) scope
   *   - A `Finalizers` collection for cleanup
   *
   * The `Tag` type is a subtype of `tail.Tag`, enabling child scopes to
   * access parent-scoped values while preventing values from escaping
   * upward.
   *
   * @tparam H the type of service(s) at the head of this scope
   * @tparam T the tail scope type
   */
  final class ::[+H, +T <: Scope](
    val head: Context[H],
    val tail: T,
    private[scope] val finalizers: Finalizers
  ) extends Scope
      with Closeable[H, T]
      with ScopeConsVersionSpecific[H, T] {

    type Tag <: tail.Tag

    private[scope] def getImpl[A](nom: IsNominalType[A]): A =
      head.getOption[A](nom) match {
        case Some(value) => value
        case None        => tail.getImpl(nom)
      }

    def defer(finalizer: => Unit): Unit = finalizers.add(finalizer)

    /**
     * Closes this scope, running all registered finalizers in LIFO order.
     *
     * @return a Chunk containing any exceptions thrown by finalizers
     */
    def close(): Chunk[Throwable] = finalizers.runAll()
  }

  /**
   * The global scope - the root of all scopes.
   *
   * The global scope is a singleton that exists for the lifetime of the
   * application. It has no services available and its finalizers run only
   * during JVM shutdown (via a shutdown hook).
   *
   * Values scoped with `Global` (i.e., `A @@ Scope.Global`) can always be
   * extracted as raw `A` because the global scope never closes during normal
   * execution.
   *
   * @example
   *   {{{
   *   // Start building a scope hierarchy from global
   *   Scope.global.injected[App](shared[Config]).use { ... }
   *   }}}
   */
  final class Global private[scope] (private val finalizers: Finalizers) extends Scope {

    type Tag <: GlobalTag

    private[scope] def getImpl[T](nom: IsNominalType[T]): T =
      throw new IllegalStateException("Global scope has no services")

    def defer(finalizer: => Unit): Unit = finalizers.add(finalizer)

    private[scope] def close(): Unit = {
      val errors = finalizers.runAll()
      errors.headOption.foreach(throw _)
    }
  }

  /**
   * A scope that can be explicitly closed, releasing all registered resources.
   */
  trait Closeable[+Head, +Tail <: Scope] extends Scope with CloseableVersionSpecific[Head, Tail] {

    /**
     * Closes this scope, running all registered finalizers in LIFO order.
     */
    def close(): Chunk[Throwable]

    /**
     * Closes this scope and throws the first exception if any occurred.
     */
    def closeOrThrow(): Unit = close().headOption.foreach(throw _)
  }

  /**
   * Extension methods for [[Scope]] instances.
   *
   * Provides the `get` method for retrieving services from a scope.
   */
  implicit final class ScopeOps(private val self: Scope) extends AnyVal {

    /**
     * Retrieves a service of type `T` from this scope.
     *
     * Searches the scope's context stack for a service matching the nominal
     * type. Throws if the service is not found (should not happen if types
     * are correctly constrained).
     *
     * @tparam T the service type to retrieve
     * @return the service instance
     */
    def get[T](implicit nom: IsNominalType[T]): T = self.getImpl(nom)
  }

  private val globalInstance: Global = new Global(new Finalizers)

  private[scope] def closeGlobal(): Unit = globalInstance.close()

  /**
   * The global scope singleton.
   *
   * This is the root of all scope hierarchies. Use it as the starting point
   * for building scopes with `injected`. The global scope:
   *   - Has no services available
   *   - Never closes during normal execution
   *   - Runs finalizers only during JVM shutdown
   *
   * @example
   *   {{{
   *   Scope.global.injected[MyApp](shared[Database]).use {
   *     val app = $[MyApp]
   *     app.run()
   *   }
   *   }}}
   */
  lazy val global: Global = {
    PlatformScope.registerShutdownHook(() => closeGlobal())
    globalInstance
  }

  private[scope] def createTestableScope(): (Global, () => Unit) = {
    val scope = new Global(new Finalizers)
    (scope, () => scope.close())
  }

  private[scope] def makeCloseable[T, S <: Scope](
    parent: S,
    context: Context[T],
    finalizers: Finalizers
  ): ::[T, S] = new ::[T, S](context, parent, finalizers)

}

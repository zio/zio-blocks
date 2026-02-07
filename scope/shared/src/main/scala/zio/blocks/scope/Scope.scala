package zio.blocks.scope

import zio.blocks.chunk.Chunk
import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.internal.Finalizers

/**
 * A scope that manages resource lifecycle with a path-dependent tag for escape
 * prevention.
 *
 * Scope forms an HList-like structure where each level carries a
 * [[zio.blocks.context.Context]] and has its own unique `Tag` type. The tag
 * chain follows the scope structure:
 *   - `Global.Tag <: GlobalTag` (base case)
 *   - `(H :: T).Tag <: T.Tag` (child tags are subtypes of parent tags)
 *
 * This enables child scopes to use parent-scoped values: a value tagged with a
 * parent's tag is usable in child scopes since `child.Tag <: parent.Tag`.
 *
 * ==Usage Pattern==
 *
 * The primary way to work with scopes is through the `.use` method on
 * [[Scope.Closeable]], which provides three implicit parameters:
 *   - `Scope.Permit[Tag]`: An unforgeable capability marker proving code is
 *     inside a `.use` block
 *   - `Context[Head] @@ Tag`: The scoped context containing this scope's
 *     services
 *   - `self.type`: The scope itself for `defer` and other operations
 *
 * @example
 *   {{{
 *   Scope.global.injected[App](shared[Database]).use {
 *     val app = $[App]      // App @@ Tag
 *     app $ (_.run())       // Access underlying App methods
 *   }
 *   }}}
 *
 * @see
 *   [[@@]] for scoped value operations
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
   * Finalizers run in LIFO order (last registered runs first). If a finalizer
   * throws an exception, subsequent finalizers still run; all exceptions are
   * collected and returned by [[Scope.Closeable.close]].
   *
   * @param finalizer
   *   a by-name expression to execute when the scope closes
   */
  def defer(finalizer: => Unit): Unit
}

object Scope {

  /**
   * The global tag type - the root of the scope tag hierarchy.
   *
   * All scope tags are subtypes of `GlobalTag`. Since child scope tags are
   * subtypes of their parent's tag, a value tagged with a parent scope can be
   * used in child scopes (the child's tag satisfies the parent tag constraint),
   * but child-tagged values cannot escape to parent scopes.
   *
   * @see
   *   [[Global]] for the scope instance that uses this tag
   */
  type GlobalTag

  /**
   * An unforgeable capability marker that proves code is executing inside a
   * `.use` block.
   *
   * `Permit[S]` is a phantom-typed capability that can only be created inside
   * the `zio.blocks.scope` package. It serves as compile-time evidence that the
   * current code is within a valid scope context.
   *
   * All operations that access scoped values (`$[T]`, `(value: A @@ S).$`,
   * `(value: A @@ S).get`) require implicit `Permit[S]`.
   *
   * The `.use` method on [[Closeable]] creates a `Permit[Tag]` and makes it
   * implicitly available within the block, ensuring scoped values cannot be
   * accessed outside.
   *
   * @tparam S
   *   the scope tag this permit token is valid for
   */
  sealed abstract class Permit[S] private[scope] ()

  private[scope] object Permit {

    /**
     * Creates a Permit token for use inside `.use` blocks.
     *
     * This is package-private to ensure Permit tokens cannot be forged by user
     * code.
     */
    def apply[S]: Permit[S] = instance.asInstanceOf[Permit[S]]

    private val instance: Permit[Any] = new Permit[Any] {}
  }

  /**
   * A scope with an unknown structure.
   *
   * Use this type alias in constructors that need access to [[Scope.defer]] for
   * resource cleanup but don't need to access specific services from the scope.
   * This is the most permissive scope type and accepts any scope instance.
   *
   * @example
   *   {{{
   *   // Scala 3
   *   class MyResource()(using Scope.Any) {
   *     val handle = acquire()
   *     defer(handle.release())
   *   }
   *
   *   // Scala 2
   *   class MyResource()(implicit scope: Scope.Any) {
   *     val handle = acquire()
   *     scope.defer(handle.release())
   *   }
   *   }}}
   */
  type Any = Scope

  /**
   * A scope that has service `T` available somewhere in its stack.
   *
   * This is an alias for `Scope.::[T, Scope]`, representing a scope where `T`
   * is available (either at the head or in the tail). Used internally by
   * `Wire.construct` and for type constraints.
   *
   * Note: This is a covariant type (`+T`), so `Scope.Has[Dog]` is a subtype of
   * `Scope.Has[Animal]` when `Dog <: Animal`.
   *
   * @tparam T
   *   the service type available in this scope
   */
  type Has[+T] = ::[T, Scope]

  /**
   * Cons cell: a scope with head context `H` and tail scope `T`.
   *
   * This class forms the HList-like structure of scopes. Each `::` node
   * contains:
   *   - A [[zio.blocks.context.Context]][H] holding the head service(s)
   *   - A reference to the tail (parent) scope
   *   - A `Finalizers` collection for cleanup
   *
   * The `Tag` type is a subtype of `tail.Tag`, enabling child scopes to access
   * parent-scoped values while preventing values from escaping upward.
   *
   * Use [[Closeable.use]] or [[Closeable.useWithErrors]] to execute code within
   * the scope and automatically close it afterward.
   *
   * @tparam H
   *   the type of service(s) at the head of this scope
   * @tparam T
   *   the tail scope type
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
     * @return
     *   a Chunk containing any exceptions thrown by finalizers
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
   *
   * The primary methods for working with a closeable scope are:
   *   - [[CloseableVersionSpecific.use]]: Execute code in the scope and
   *     auto-close, discarding errors
   *   - [[CloseableVersionSpecific.useWithErrors]]: Execute code and return
   *     both result and errors
   *   - [[close]]: Manually close and get all finalizer exceptions
   *   - [[closeOrThrow]]: Manually close and throw the first exception if any
   *
   * @tparam Head
   *   the service type at the head of this scope
   * @tparam Tail
   *   the parent scope type
   */
  trait Closeable[+Head, +Tail <: Scope] extends Scope with CloseableVersionSpecific[Head, Tail] {

    /**
     * Closes this scope, running all registered finalizers in LIFO order.
     *
     * All finalizers are run even if some throw exceptions. The returned
     * [[zio.blocks.chunk.Chunk]] contains all exceptions that were thrown.
     *
     * @return
     *   a Chunk containing any exceptions thrown by finalizers (empty if all
     *   succeeded)
     */
    def close(): Chunk[Throwable]

    /**
     * Closes this scope and throws the first exception if any finalizer failed.
     *
     * This is a convenience method for when you want fail-fast behavior. All
     * finalizers still run, but only the first exception is thrown.
     */
    def closeOrThrow(): Unit = close().headOption.foreach(throw _)
  }

  /**
   * Extension methods for [[Scope]] instances (internal use only).
   *
   * Provides the `get` method for retrieving services from a scope. This is
   * package-private to prevent direct access outside `.use` blocks. External
   * code should use `$[T]` instead, which provides proper scoping.
   */
  private[scope] implicit final class ScopeOps(private val self: Scope) extends AnyVal {

    /**
     * Retrieves a service of type `T` from this scope (internal use).
     *
     * Searches the scope's context stack for a service matching the nominal
     * type. This is used internally by `$[T]` and wire construction.
     *
     * @tparam T
     *   the service type to retrieve
     * @param nom
     *   evidence that T is a nominal type
     * @return
     *   the service instance
     * @throws IllegalStateException
     *   if the service is not found in the scope stack
     */
    def get[T](implicit nom: IsNominalType[T]): T = self.getImpl(nom)
  }

  private val globalInstance: Global = new Global(new Finalizers)

  private[scope] def closeGlobal(): Unit = globalInstance.close()

  /**
   * The global scope singleton.
   *
   * This is the root of all scope hierarchies. Use it as the starting point for
   * building scopes with `injected`. The global scope:
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

package zio.blocks.scope

import zio.blocks.chunk.Chunk
import zio.blocks.scope.internal.Finalizers

/**
 * A scope that manages resource lifecycle with compile-time verified safety.
 *
 * The key insight is tag-based scoping: each scope has a unique `Tag` type, and
 * values created in a scope are tagged with that scope's Tag, preventing them
 * from escaping to outer scopes.
 *
 * ==Two-Parameter Design==
 *
 * `Scope[ParentTag, Tag]` where `Tag <: ParentTag`:
 *   - `ParentTag`: The parent scope's tag, bounding this scope's capabilities
 *   - `Tag`: This scope's unique identity, enabling resource tracking
 *
 * The tag hierarchy follows: `child.Tag <: parent.Tag <: ... <: GlobalTag`
 *
 * ==Key Methods==
 *
 *   - `allocate`: Allocate a Resource into this scope
 *   - `$`: Apply a function to a scoped value, escaping if Unscoped
 *   - `apply`: Execute a Scoped computation
 *   - `scoped`: Create a child scope
 *   - `defer`: Register cleanup action
 *
 * @example
 *   {{{
 *   Scope.global.scoped { scope =>
 *     val db = scope.allocate(Resource[Database])
 *     val result = (scope $ db)(_.query("SELECT 1"))
 *     println(result)
 *   }
 *   }}}
 *
 * @tparam ParentTag
 *   the parent scope's tag type
 * @tparam Tag
 *   this scope's tag type (subtype of ParentTag)
 *
 * @see
 *   [[@@]] for scoped value type
 * @see
 *   [[Resource]] for creating scoped values
 */
final class Scope[ParentTag, Tag0 <: ParentTag] private[scope] (
  private[scope] val finalizers: Finalizers
) extends ScopeVersionSpecific[ParentTag, Tag0]
    with Finalizer {

  /**
   * This scope's tag type, exposed as a type member for path-dependent typing.
   */
  type Tag = Tag0

  /**
   * Allocates a value in this scope using the given resource.
   *
   * The resource is acquired immediately and its finalizers are registered with
   * this scope. The result is wrapped in a scoped computation tagged with this
   * scope's `Tag`, preventing escape.
   *
   * @param resource
   *   the resource to create the value
   * @tparam A
   *   the value type
   * @return
   *   a scoped computation that produces the allocated value
   */
  def allocate[A](resource: Resource[A]): A @@ Tag = {
    val value = resource.make(this)
    Scoped.eager(value)
  }

  /**
   * Allocates an AutoCloseable value directly in this scope.
   *
   * This is a convenience overload that wraps the value in a Resource and
   * registers its `close()` method as a finalizer.
   *
   * @param value
   *   a by-name expression that creates the AutoCloseable
   * @tparam A
   *   the value type (must be AutoCloseable)
   * @return
   *   the created value tagged with this scope's Tag
   */
  def allocate[A <: AutoCloseable](value: => A): A @@ Tag =
    allocate(Resource(value))

  /**
   * Registers a finalizer to run when this scope closes.
   *
   * Finalizers run in LIFO order (last registered runs first). If a finalizer
   * throws an exception, subsequent finalizers still run; all exceptions are
   * collected.
   *
   * @param f
   *   a by-name expression to execute when the scope closes
   */
  def defer(f: => Unit): Unit = finalizers.add(f)

  /**
   * Returns true if this scope has been closed.
   *
   * A closed scope has already run its finalizers. Attempting to use resources
   * from a closed scope is unsafe.
   */
  private[scope] def isClosed: Boolean = finalizers.isClosed

  /**
   * Closes this scope, running all registered finalizers in LIFO order.
   *
   * @return
   *   a Chunk containing any exceptions thrown by finalizers
   */
  private[scope] def close(): Chunk[Throwable] = finalizers.runAll()
}

object Scope {

  /**
   * The global tag type - the root of the scope tag hierarchy.
   *
   * All scope tags are subtypes of `GlobalTag`. Values tagged with `GlobalTag`
   * can always be extracted because the global scope never closes during normal
   * execution.
   *
   * @see
   *   [[global]]
   */
  type GlobalTag

  /**
   * The global scope singleton.
   *
   * This is the root of all scope hierarchies. Use it as the starting point for
   * creating scoped computations. The global scope:
   *   - Never closes during normal execution
   *   - Runs finalizers only during JVM shutdown
   *
   * @example
   *   {{{
   *   Scope.global.scoped { scope =>
   *     val app = scope.allocate(Resource[App])
   *     (scope $ app)(_.run())
   *   }
   *   }}}
   */
  lazy val global: Scope[GlobalTag, GlobalTag] = {
    val scope = new Scope[GlobalTag, GlobalTag](new Finalizers)
    PlatformScope.registerShutdownHook { () =>
      val errors = scope.close()
      if (errors.nonEmpty) {
        val first = errors.head
        errors.tail.foreach(first.addSuppressed)
        throw first
      }
    }
    scope
  }

  /**
   * Creates a testable scope that can be manually closed.
   *
   * Unlike `Scope.global`, this scope's finalizers can be triggered manually
   * for testing purposes.
   *
   * @note
   *   The close function throws the first exception with any subsequent
   *   exceptions added as suppressed if any finalizers fail.
   * @return
   *   a tuple of (scope, close function)
   */
  private[scope] def createTestableScope(): (Scope[GlobalTag, GlobalTag], () => Unit) = {
    val scope = new Scope[GlobalTag, GlobalTag](new Finalizers)
    (
      scope,
      () => {
        val errors = scope.close()
        if (errors.nonEmpty) {
          val first = errors.head
          errors.tail.foreach(first.addSuppressed)
          throw first
        }
      }
    )
  }
}

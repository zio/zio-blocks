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

  /** The global tag type - the base of all scope tags. */
  type GlobalTag

  /**
   * A scope with an unknown structure. Use in constructors that need `defer`
   * but don't access services.
   */
  type Any = Scope

  /**
   * A scope that has service `T` available at the head.
   */
  type Has[+T] = ::[T, Scope]

  /**
   * Cons cell: a scope with head resource H and tail scope T. The
   * use/useWithErrors implementations are provided by ScopeConsVersionSpecific.
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

    def close(): Chunk[Throwable] = finalizers.runAll()
  }

  /**
   * The global scope - the root of all scopes.
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

  implicit final class ScopeOps(private val self: Scope) extends AnyVal {

    /**
     * Retrieves a service from this scope.
     */
    def get[T](implicit nom: IsNominalType[T]): T = self.getImpl(nom)
  }

  private val globalInstance: Global = new Global(new Finalizers)

  private[scope] def closeGlobal(): Unit = globalInstance.close()

  /**
   * The global scope, which never closes during normal execution.
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

package zio.blocks

import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.internal.Finalizers
import scala.language.experimental.macros

/**
 * Top-level functions for the Scope dependency injection library.
 *
 * Import `zio.blocks.scope._` to access these functions.
 */
package object scope {

  /**
   * Registers a finalizer to run when the current scope closes.
   *
   * @example
   *   {{{
   *   class MyService()(implicit scope: Scope.Any) {
   *     val resource = acquire()
   *     defer { resource.release() }
   *   }
   *   }}}
   */
  def defer(finalizer: => Unit)(implicit scope: Scope.Any): Unit =
    scope.defer(finalizer)

  /**
   * Retrieves a service from the current scope.
   *
   * Short syntax for accessing services within a scope context.
   *
   * @example
   *   {{{
   *   def doWork()(implicit scope: Scope.Has[Database]): Unit = {
   *     val db = $[Database]
   *     db.query("SELECT ...")
   *   }
   *   }}}
   */
  def $[T](implicit scope: Scope.Has[T], nom: IsNominalType[T]): T =
    scope.get[T]

  /**
   * Creates a closeable scope containing the given value.
   *
   * If the value is `AutoCloseable`, its `close()` method is automatically registered as a
   * finalizer.
   */
  def injectedValue[T](t: T)(implicit scope: Scope.Any, nom: IsNominalType[T]): Scope.Closeable[T, _] = {
    val ctx        = Context(t)
    val finalizers = new Finalizers
    if (t.isInstanceOf[AutoCloseable]) {
      finalizers.add(t.asInstanceOf[AutoCloseable].close())
    }
    Scope.makeCloseable(scope, ctx, finalizers)
  }

  /**
   * Derives a shared [[Wire]] for type `T` by inspecting its constructor.
   *
   * If a `Wireable[T]` exists in implicit scope, it is used. Otherwise, the macro inspects `T`'s
   * primary constructor and generates a wire that:
   *   - Retrieves constructor parameters from the scope
   *   - Passes an implicit `Scope` parameter if present
   *   - Registers `close()` as a finalizer if `T` extends `AutoCloseable`
   *
   * @example
   *   {{{
   *   Scope.global.injected[App](shared[Database], shared[Cache]).run { ... }
   *   }}}
   */
  def shared[T]: Wire.Shared[_, T] = macro ScopeMacros.sharedImpl[T]

  /**
   * Derives a unique [[Wire]] for type `T` by inspecting its constructor.
   *
   * Like `shared[T]`, but the wire creates a fresh instance each time it's used.
   */
  def unique[T]: Wire.Unique[_, T] = macro ScopeMacros.uniqueImpl[T]

  /**
   * Creates a child scope containing an instance of `T` and its dependencies.
   *
   * The macro inspects `T`'s constructor to determine dependencies. Dependencies are resolved from:
   *   1. Provided wires (in order)
   *   2. The parent scope's stack
   *
   * @example
   *   {{{
   *   Scope.global.injected[App](shared[Config]).run { scope =>
   *     // App and Config are available here
   *     val app = scope.get[App]
   *     app.run()
   *   }
   *   }}}
   */
  def injected[T](wires: Wire[_, _]*)(implicit scope: Scope.Any): Scope.Closeable[T, _] =
    macro ScopeMacros.injectedImpl[T]
}

package zio.blocks

import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.internal.Finalizers

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
   *   class MyService()(using Scope.Any) {
   *     val resource = acquire()
   *     defer { resource.release() }
   *   }
   *   }}}
   */
  def defer(finalizer: => Unit)(using scope: Scope.Any): Unit =
    scope.defer(finalizer)

  /**
   * Retrieves a service of type `T` from the current scoped context.
   *
   * The returned value is scoped to prevent escape. Use the `$` operator on the
   * scoped value to access methods.
   *
   * The context's type `R` must be a subtype of `T` (meaning `T` is available
   * in the context).
   *
   * @example
   *   {{{
   *   closeable.use {
   *     val db = $[Database]           // Database @@ Tag
   *     db $ (_.query("SELECT ..."))   // String (unscoped, since String is Unscoped)
   *   }
   *   }}}
   */
  transparent inline def $[T]: Any = ${ ScopeMacros.dollarImpl[T] }

  /**
   * Creates a closeable scope containing the given value.
   *
   * If the value is `AutoCloseable`, its `close()` method is automatically
   * registered as a finalizer.
   *
   * @example
   *   {{{
   *   val config = Config.load()
   *   injected(config).run {
   *     val cfg = $[Config]
   *     cfg $ (_.dbUrl)
   *   }
   *   }}}
   */
  def injected[T, S <: Scope](t: T)(using scope: S, nom: IsNominalType[T]): Scope.::[T, S] = {
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
   * If a `Wireable[T]` exists in implicit scope, it is used. Otherwise, the
   * macro inspects `T`'s primary constructor and generates a wire that:
   *   - Retrieves constructor parameters from the scope
   *   - Passes an implicit `Scope` parameter if present
   *   - Registers `close()` as a finalizer if `T` extends `AutoCloseable`
   *
   * @example
   *   {{{
   *   Scope.global.injected[App](shared[Database], shared[Cache]).run { ... }
   *   }}}
   */
  transparent inline def shared[T]: Wire.Shared[?, T] = ${ ScopeMacros.sharedImpl[T] }

  /**
   * Derives a unique [[Wire]] for type `T` by inspecting its constructor.
   *
   * Like `shared[T]`, but the wire creates a fresh instance each time it's
   * used.
   */
  transparent inline def unique[T]: Wire.Unique[?, T] = ${ ScopeMacros.uniqueImpl[T] }

  /**
   * Creates a child scope containing an instance of `T` and its dependencies.
   *
   * The macro inspects `T`'s constructor to determine dependencies.
   * Dependencies are resolved from:
   *   1. Provided wires (in order)
   *   2. The parent scope's stack
   *
   * @example
   *   {{{
   *   Scope.global.injected[App](shared[Config]).run {
   *     // App and Config are available here
   *     val app = $[App]
   *     app.run()
   *   }
   *   }}}
   */
  inline def injected[T](using scope: Scope.Any): Scope.Closeable[T, ?] =
    ${ ScopeMacros.injectedImpl[T]('{ Seq.empty[Wire[?, ?]] }, 'scope) }

  inline def injected[T](inline wires: Wire[?, ?]*)(using scope: Scope.Any): Scope.Closeable[T, ?] =
    ${ ScopeMacros.injectedImpl[T]('wires, 'scope) }

  /**
   * Leaks a scoped value out of its scope, returning the raw unwrapped value.
   *
   * This function emits a compiler warning because leaking resources bypasses
   * Scope's compile-time safety guarantees. Use only for legacy code interop
   * where third-party or Java code cannot operate with scoped values.
   *
   * @example
   *   {{{
   *   // Legacy Java API that needs a raw InputStream
   *   def processWithLegacyApi()(using scope: Scope.Has[Request]): Unit = {
   *     val stream = leak($[Request].body.getInputStream())
   *     LegacyJavaProcessor.process(stream)  // Third-party code
   *   }
   *   }}}
   *
   * To suppress the warning for a specific call site, use
   * `@nowarn("msg=is being leaked")` or configure your build tool's lint
   * settings.
   *
   * If the type is not actually resourceful, consider adding a `given
   * ScopeEscape` instance to avoid needing `leak`.
   */
  inline def leak[A, S](inline scoped: A @@ S): A = ${ LeakMacros.leakImpl[A, S]('scoped) }
}

package zio.blocks

import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.internal.Finalizers
import scala.language.experimental.macros
import scala.language.implicitConversions

/**
 * Top-level functions for the Scope dependency injection library.
 *
 * Import `zio.blocks.scope._` to access these functions.
 */
package object scope {

  /**
   * Opaque-like type for scoping values with scope identity.
   *
   * A value of type `A @@ S` is a value of type `A` that is "locked" to a scope
   * with tag `S`. The abstract type hides all methods on `A`, so the only way
   * to use the value is through the `$` operator, which requires the matching
   * scope capability.
   *
   * This prevents scoped resources from escaping their scope at compile time.
   *
   * @example
   *   {{{
   *   // Scoped value cannot escape
   *   val stream: InputStream @@ scope.Tag = getStream()
   *   stream.read()  // Compile error: read() is not a member of InputStream @@ Tag
   *
   *   // Must use $ operator with scope in context
   *   stream.$(_.read())(scope, implicitly)  // Works, returns Int (unscoped)
   *   }}}
   */
  type @@[+A, S] = ScopedModule.instance.@@[A, S]

  /** Implicit conversion to enable `$`, `map`, `flatMap` on scoped values. */
  implicit def toScopedOps[A, S](scoped: A @@ S): ScopedOps[A, S] = new ScopedOps(scoped)

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
   * Retrieves a service from the current scope, scoped with the scope's
   * identity.
   *
   * The returned value is scoped to prevent escape. Use the `$` operator on the
   * scoped value to access methods:
   *
   * @example
   *   {{{
   *   def doWork()(implicit scope: Scope.Has[Database]): Unit = {
   *     val db = $[Database]                      // Database @@ scope.Tag
   *     db.$(_.query("SELECT ..."))(scope, implicitly)  // String (unscoped)
   *   }
   *   }}}
   */
  def $[T](implicit scope: Scope.Has[T], nom: IsNominalType[T]): T @@ scope.Tag =
    @@.scoped(scope.get[T])

  /**
   * Creates a closeable scope containing the given value.
   *
   * If the value is `AutoCloseable`, its `close()` method is automatically
   * registered as a finalizer.
   *
   * @example
   *   {{{
   *   val config = Config.load()
   *   injected(config).run { implicit scope =>
   *     val cfg = $[Config]
   *     cfg.$(_.dbUrl)
   *   }
   *   }}}
   */
  def injected[T](t: T)(implicit scope: Scope.Any, nom: IsNominalType[T]): Scope.Closeable[T, _] = {
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
  def shared[T]: Wire.Shared[_, T] = macro ScopeMacros.sharedImpl[T]

  /**
   * Derives a unique [[Wire]] for type `T` by inspecting its constructor.
   *
   * Like `shared[T]`, but the wire creates a fresh instance each time it's
   * used.
   */
  def unique[T]: Wire.Unique[_, T] = macro ScopeMacros.uniqueImpl[T]

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
   *   Scope.global.injected[App](shared[Config]).run { scope =>
   *     // App and Config are available here
   *     val app = scope.get[App]
   *     app.run()
   *   }
   *   }}}
   */
  // format: off
  def injected[T](implicit scope: Scope.Any): Scope.Closeable[T, _] =
    macro ScopeMacros.injectedNoArgsImpl[T]

  def injected[T](wires: Wire[_, _]*)(implicit scope: Scope.Any): Scope.Closeable[T, _] =
    macro ScopeMacros.injectedImpl[T]
  // format: on

  @deprecated("Use ScopeEscape instead", "0.1.0")
  type AutoUnscoped[A, S] = ScopeEscape[A, S]

  @deprecated("Use ScopeEscape instead", "0.1.0")
  val AutoUnscoped = ScopeEscape

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
   *   def processWithLegacyApi()(implicit scope: Scope.Has[Request]): Unit = {
   *     val stream = leak($[Request].body.getInputStream())
   *     LegacyJavaProcessor.process(stream)  // Third-party code
   *   }
   *   }}}
   *
   * To suppress the warning for a specific call site, use
   * `@nowarn("msg=is being leaked")` or configure your build tool's lint
   * settings.
   *
   * If the type is not actually resourceful, consider adding an `implicit
   * ScopeEscape` instance to avoid needing `leak`.
   */
  def leak[A, S](scoped: A @@ S): A = macro LeakMacros.leakImpl[A, S]
}

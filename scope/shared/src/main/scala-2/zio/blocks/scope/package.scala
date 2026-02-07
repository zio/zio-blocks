package zio.blocks

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
   *   Scope.global.scoped { scope =>
   *     val resource = acquire()
   *     scope.defer { resource.release() }
   *     // use resource...
   *   }
   *   }}}
   */
  def defer(finalizer: => Unit)(implicit scope: Scope[_, _]): Unit =
    scope.defer(finalizer)

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
   *   // Create a shared wire for Database
   *   val dbWire = shared[Database]
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
   * Leaks a scoped value out of its scope, returning the raw unwrapped value.
   *
   * This function emits a compiler warning because leaking resources bypasses
   * Scope's compile-time safety guarantees. Use only for interop where
   * third-party or Java code cannot operate with scoped values.
   *
   * @example
   *   {{{
   *   Scope.global.scoped { implicit scope =>
   *     val stream = scope.create(Factory[InputStream])
   *     val leaked = leak(stream)
   *     ThirdPartyProcessor.process(leaked)
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

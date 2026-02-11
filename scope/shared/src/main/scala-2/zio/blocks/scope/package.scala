package zio.blocks

import scala.language.experimental.macros

/**
 * Scope: A compile-time safe resource management library using existential
 * types.
 *
 * ==Quick Start==
 *
 * {{{
 * import zio.blocks.scope._
 *
 * Scope.global.scoped { scope =>
 *   val db = scope.allocate(Resource[Database])
 *   val result = (scope $ db)(_.query("SELECT 1"))
 *   println(result)
 * }
 * }}}
 *
 * ==Key Concepts==
 *
 *   - '''Scoped values''' (`A @@ S`): Values tagged with a scope, preventing
 *     escape
 *   - '''`scope.allocate(resource)`''': Allocate a value in a scope
 *   - '''`(scope $ value)(f)`''': Apply a function to a scoped value
 *   - '''`scope.scoped { s => ... }`''': Create a child scope with existential
 *     tag
 *   - '''`scope.defer { ... }`''': Register cleanup to run when scope closes
 *
 * ==How It Works==
 *
 * The `.scoped` method creates a fresh existential `Tag` type for each
 * invocation using a local `type Fresh <: ParentTag` declaration. This tag
 * cannot be named outside the lambda, making it impossible to leak resources or
 * capabilities.
 *
 * @see
 *   [[scope.Scope]] for scope types and operations [[scope.@@]] for scoped
 *   value operations [[scope.Resource]] for creating scoped values
 *   [[scope.Scoped]] for deferred scoped computations
 */
package object scope {

  /**
   * Type alias for Scoped.
   *
   * `A @@ S` is equivalent to `Scoped[A, S]`. This infix syntax reads naturally
   * when declaring scoped values:
   *
   * {{{
   * val db: Database @@ scope.Tag = scope.allocate(Resource[Database])
   * }}}
   *
   * @tparam A
   *   the value type (covariant)
   * @tparam S
   *   the scope tag type (contravariant)
   */
  type @@[+A, -S] = Scoped.Scoped[A, S]

  /**
   * Registers a finalizer to run when the finalizer closes.
   *
   * This overload allows classes that accept an implicit Finalizer to use the
   * top-level defer syntax.
   *
   * @param finalizer
   *   a by-name expression to execute on finalizer close
   * @param fin
   *   the finalizer capability to register cleanup with
   */
  def defer(finalizer: => Unit)(implicit fin: Finalizer): Unit =
    fin.defer(finalizer)

  /**
   * Leaks a scoped value out of its scope, returning the raw unwrapped value.
   *
   * '''Warning''': This function emits a compiler warning because leaking
   * resources bypasses Scope's compile-time safety guarantees. The resource may
   * be closed while still in use, leading to runtime errors.
   *
   * Use only for interop where third-party or Java code cannot operate with
   * scoped values.
   *
   * @example
   *   {{{
   *   Scope.global.scoped { implicit scope =>
   *     val stream = scope.allocate(Resource[InputStream])
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
   *
   * @param scoped
   *   the scoped value to leak
   * @tparam A
   *   the underlying value type
   * @tparam S
   *   the scope tag type
   * @return
   *   the raw unwrapped value
   */
  def leak[A, S](scoped: A @@ S): A = macro LeakMacros.leakImpl[A, S]

}

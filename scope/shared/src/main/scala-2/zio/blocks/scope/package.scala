package zio.blocks

import scala.language.experimental.macros
import scala.language.implicitConversions

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
 *   val result = scope.$(db)(_.query("SELECT 1"))
 *   println(result)
 * }
 * }}}
 *
 * ==Key Concepts==
 *
 *   - '''Scoped values''' (`A @@ S`): Values tagged with a scope, preventing
 *     escape
 *   - '''`scope.allocate(resource)`''': Allocate a value in a scope
 *   - '''`scope.$(value)(f)`''': Apply a function to a scoped value
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

  /**
   * Implicit conversion that enables `$`, `map`, and `flatMap` extension
   * methods on scoped values.
   *
   * This conversion allows scoped values to be used with fluent syntax:
   * {{{
   * val stream: InputStream @@ S = ...
   * stream.$(_.read())           // Apply function via $ operator
   * stream.map(_.available())    // Map over the scoped value
   * stream.flatMap(s => ...)     // FlatMap for chaining scoped operations
   * }}}
   *
   * @tparam A
   *   the underlying value type
   * @tparam S
   *   the scope tag type
   * @param scoped
   *   the scoped value to wrap
   * @return
   *   a [[ScopedOps]] wrapper providing extension methods
   */
  implicit def toScopedOps[A, S](scoped: A @@ S): ScopedOps[A, S] = new ScopedOps(scoped)

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
   * used. Use for services that should not be shared across dependents.
   *
   * @tparam T
   *   the service type to construct (must be a class, not a trait or abstract)
   * @return
   *   a unique wire for constructing `T`
   */
  def unique[T]: Wire.Unique[_, T] = macro ScopeMacros.uniqueImpl[T]

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

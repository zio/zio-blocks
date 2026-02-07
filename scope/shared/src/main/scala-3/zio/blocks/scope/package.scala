package zio.blocks

import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.internal.Finalizers

/**
 * Scope: A compile-time safe dependency injection and resource management
 * library.
 *
 * ==Quick Start==
 *
 * {{{
 * import zio.blocks.scope._
 *
 * Scope.global.injected[App](shared[Database], shared[Config]).use {
 *   val app = $[App]      // App @@ Tag - scoped value
 *   app $ (_.run())       // Access underlying App methods
 * }
 * }}}
 *
 * ==Key Concepts==
 *
 *   - '''Scoped values''' (`A @@ S`): Values tagged with a scope, preventing
 *     escape
 *   - '''`$[T]`''': Retrieve a service from the current scope
 *   - '''`defer { ... }`''': Register cleanup to run when scope closes
 *   - '''`shared[T]` / `unique[T]`''': Create wires for dependency injection
 *   - '''`.use { ... }`''': Execute code in a scope, then auto-close
 *
 * ==How It Works==
 *
 * The `.use` method on [[Scope.Closeable]] provides three implicit parameters:
 *   - `Scope.Permit[Tag]`: Unforgeable proof you're inside a `.use` block
 *   - `Context[Head] @@ Tag`: The scoped context with services
 *   - `self.type`: The scope for `defer` calls
 *
 * The `$` and `.get` operators require [[Scope.Permit]], which is only
 * available inside `.use` blocks.
 *
 * @see
 *   [[Scope]] for scope types and operations
 * @see
 *   [[@@]] for scoped value operations
 * @see
 *   [[Wire]] for dependency injection wires
 */
package object scope {

  /**
   * Registers a finalizer to run when the current scope closes.
   *
   * Finalizers run in LIFO order (last registered runs first). If a finalizer
   * throws, subsequent finalizers still run.
   *
   * @example
   *   {{{
   *   class MyService()(using Scope.Any) {
   *     val resource = acquire()
   *     defer { resource.release() }
   *   }
   *   }}}
   *
   * @param finalizer
   *   a by-name expression to execute on scope close
   * @param scope
   *   the scope to register the finalizer with
   */
  def defer(finalizer: => Unit)(using scope: Scope.Any): Unit =
    scope.defer(finalizer)

  /**
   * Retrieves a service of type `T` from the current scoped context.
   *
   * This is a transparent inline macro that requires `Scope.Permit[S]` +
   * `Context[T] @@ S` from a `.use` block.
   *
   * The returned value is scoped (`T @@ S`) to prevent escape. Use the `$`
   * operator on the scoped value to access methods.
   *
   * @example
   *   {{{
   *   closeable.use {
   *     val db = $[Database]           // Database @@ Tag
   *     db $ (_.query("SELECT ..."))   // String (unscoped, since String is Unscoped)
   *   }
   *   }}}
   *
   * @tparam T
   *   the service type to retrieve (must have
   *   [[zio.blocks.context.IsNominalType]] evidence)
   * @return
   *   `T @@ S` where `S` is the current scope's tag
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
   *   injected(config).use {
   *     val cfg = $[Config]
   *     cfg $ (_.dbUrl)
   *   }
   *   }}}
   *
   * @param t
   *   the value to inject into the new scope
   * @param scope
   *   the parent scope
   * @param nom
   *   evidence that T is a nominal type
   * @tparam T
   *   the service type
   * @tparam S
   *   the parent scope type
   * @return
   *   a closeable scope containing `T`
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
   * If a [[Wireable]][T] exists in implicit scope, it is used. Otherwise, the
   * macro inspects `T`'s primary constructor and generates a wire that:
   *   - Retrieves constructor parameters from the scope
   *   - Passes an implicit `Scope` parameter if present
   *   - Registers `close()` as a finalizer if `T` extends `AutoCloseable`
   *
   * Shared wires are memoized within a single `injected` call, so multiple
   * dependents receive the same instance.
   *
   * @example
   *   {{{
   *   Scope.global.injected[App](shared[Database], shared[Cache]).use { ... }
   *   }}}
   *
   * @tparam T
   *   the service type to construct (must be a class, not a trait or abstract)
   * @return
   *   a shared wire for constructing `T`
   */
  transparent inline def shared[T]: Wire.Shared[?, T] = ${ ScopeMacros.sharedImpl[T] }

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
   *   Scope.global.injected[App](shared[Config]).use {
   *     // App and Config are available here
   *     val app = $[App]
   *     app $ (_.run())
   *   }
   *   }}}
   *
   * @tparam T
   *   the service type to construct
   * @param scope
   *   the parent scope
   * @return
   *   a closeable scope containing `T` and its dependencies
   */
  inline def injected[T](using scope: Scope.Any): Scope.Closeable[T, ?] =
    ${ ScopeMacros.injectedImpl[T]('{ Seq.empty[Wire[?, ?]] }, 'scope) }

  /**
   * Creates a child scope containing an instance of `T` and its dependencies.
   *
   * @param wires
   *   wires providing dependencies for constructing `T`
   * @tparam T
   *   the service type to construct
   * @param scope
   *   the parent scope
   * @return
   *   a closeable scope containing `T` and its dependencies
   */
  inline def injected[T](inline wires: Wire[?, ?]*)(using scope: Scope.Any): Scope.Closeable[T, ?] =
    ${ ScopeMacros.injectedImpl[T]('wires, 'scope) }

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
   *   closeable.use {
   *     val stream = leak($[Request] $ (_.body.getInputStream()))
   *     ThirdPartyProcessor.process(stream)
   *   }
   *   }}}
   *
   * To suppress the warning for a specific call site, use
   * `@nowarn("msg=is being leaked")` or configure your build tool's lint
   * settings.
   *
   * If the type is not actually resourceful, consider adding a `given Unscoped`
   * instance to avoid needing `leak`.
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
  inline def leak[A, S](inline scoped: A @@ S): A = ${ LeakMacros.leakImpl[A, S]('scoped) }
}

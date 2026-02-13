package zio.blocks

/**
 * Scope: A compile-time safe resource management library using scope-local
 * opaque types.
 *
 * ==Quick Start==
 *
 * {{{
 * import zio.blocks.scope._
 *
 * Scope.global.scoped { scope =>
 *   import scope._
 *   val db: $[Database] = allocate(Resource[Database])
 *   val result: $[Int] = scope.use(db)(_.query("SELECT 1"))
 *   result  // Returns $[Int], unwrapped to Int at boundary
 * }
 * }}}
 *
 * ==Key Concepts==
 *
 *   - '''Scoped values''' (`scope.$[A]`): Values tied to a scope, preventing
 *     escape
 *   - '''`import scope._`''': Bring scope operations into lexical scope
 *   - '''`allocate(resource)`''': Allocate a value in the current scope
 *   - '''`scope.use(value)(f)`''': Apply a function to a scoped value
 *   - '''`scoped { s => ... }`''': Create a child scope
 *   - '''`defer { ... }`''': Register cleanup to run when scope closes
 *
 * ==How It Works==
 *
 * Each scope has its own `$[A]` opaque type. Child scopes create structurally
 * incompatible `$[A]` types, preventing sibling-scoped values from being mixed.
 * Parent-scoped values can be lowered into child scopes via `lower()`.
 *
 * The `.scoped` method requires `Unscoped[A]` evidence on the return type,
 * ensuring only pure data (not resources or closures) can escape.
 *
 * @see
 *   [[scope.Scope]] for scope types and operations [[scope.Resource]] for
 *   creating scoped values [[scope.Unscoped]] for types that can cross scope
 *   boundaries
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
   *   Scope.global.scoped { scope =>
   *     val resource = acquire()
   *     scope.defer { resource.release() }
   *     // use resource...
   *   }
   *   }}}
   *
   * @param finalizer
   *   a by-name expression to execute on scope close
   * @param fin
   *   the finalizer capability to register cleanup with
   */
  def defer(finalizer: => Unit)(implicit fin: Finalizer): Unit =
    fin.defer(finalizer)

}

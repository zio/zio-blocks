package zio.blocks.scope

/**
 * SAM (Single Abstract Method) type for scoped execution with existential tags.
 *
 * '''Note:''' This type is not currently used by `Scope.scoped`. The actual
 * implementation uses a wildcard type parameter with a local `type Fresh`
 * declaration to create the existential tag. This type is retained for
 * potential future use or as an alternative implementation pattern.
 *
 * ==The Existential Pattern==
 *
 * The goal is to create a fresh, unknowable `Tag` type for each scope
 * invocation. This makes it impossible to:
 *   - Return permits or capabilities from the scope
 *   - Access scoped values outside their lifecycle
 *   - Leak resources across scope boundaries
 *
 * @example
 *   {{{
 *   Scope.global.scoped { scope =>
 *     // scope: Scope[GlobalTag, ? <: GlobalTag]
 *     // The child tag is existential — cannot be named outside this lambda
 *     val db: Database @@ scope.Tag = scope.create(Factory[Database])
 *     scope.$(db)(_.query("SELECT 1"))
 *   }
 *   }}}
 *
 * @tparam ParentTag
 *   the parent scope's tag type that bounds this scope's tag
 * @tparam A
 *   the result type of the scoped computation
 *
 * @see
 *   [[Scope.scoped]] for the actual implementation
 */
abstract class ScopedFunction[ParentTag, +A] {

  /**
   * The existential tag type for this scope invocation.
   *
   * This type is fresh for each lambda instantiation and cannot be named
   * outside the lambda body. It is bounded by `ParentTag`, enabling child
   * scopes to access parent-scoped values.
   */
  type Tag <: ParentTag

  /**
   * Executes the scoped computation.
   *
   * @param scope
   *   the child scope with the fresh existential tag
   * @return
   *   the result of the computation
   */
  def apply(scope: Scope[ParentTag, Tag]): A
}

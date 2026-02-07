package zio.blocks.scope

/**
 * SAM (Single Abstract Method) type for scoped execution with existential tags.
 *
 * When you write a lambda for `Scope.scoped`, Scala creates an anonymous class
 * with a fresh `Tag` type. This type is existential — it exists but cannot be
 * named outside the lambda body, providing compile-time resource safety.
 *
 * ==The Existential Trick==
 *
 * Each instantiation of `ScopedFunction` creates a fresh, unknowable `Tag`
 * type. This makes it impossible to:
 *   - Return permits or capabilities from the scope
 *   - Access scoped values outside their lifecycle
 *   - Leak resources across scope boundaries
 *
 * @example
 *   {{{
 *   Scope.global.scoped { scope =>
 *     // scope: Scope[GlobalTag, scope.Tag]
 *     // scope.Tag is EXISTENTIAL — cannot be named outside this lambda
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
 *   [[Scope.scoped]] which uses this type
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

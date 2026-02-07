package zio.blocks.scope

/**
 * Scala 2-specific extension methods for Scope.
 */
private[scope] trait ScopeVersionSpecific[ParentTag, Tag <: ParentTag] {
  self: Scope[ParentTag, Tag] =>

  /**
   * Applies a function to a scoped value, escaping if the result is Unscoped.
   *
   * @param scoped
   *   the scoped value to access
   * @param f
   *   the function to apply to the underlying value
   * @param ev
   *   evidence that this scope's Tag is a subtype of S
   * @param escape
   *   typeclass determining whether the result escapes
   * @return
   *   either raw B or B @@ S depending on ScopeEscape
   */
  def $[A, B, S](scoped: A @@ S)(f: A => B)(implicit
    ev: Tag <:< S,
    escape: ScopeEscape[B, S]
  ): escape.Out =
    escape(f(@@.unscoped(scoped)))

  /**
   * Executes a Scoped computation.
   *
   * @param scoped
   *   the Scoped computation to execute
   * @param ev
   *   evidence that this scope's Tag is a subtype of S
   * @param escape
   *   typeclass determining whether the result escapes
   * @return
   *   either raw A or A @@ S depending on ScopeEscape
   */
  def apply[A, S](scoped: Scoped[S, A])(implicit
    ev: Tag <:< S,
    escape: ScopeEscape[A, S]
  ): escape.Out =
    escape(scoped.run())
}

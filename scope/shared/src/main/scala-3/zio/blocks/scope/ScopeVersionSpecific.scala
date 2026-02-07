package zio.blocks.scope

/**
 * Scala 3-specific extension methods for Scope.
 */
private[scope] trait ScopeVersionSpecific[ParentTag, Tag <: ParentTag] {
  self: Scope[ParentTag, Tag] =>

  /**
   * Applies a function to a scoped value, escaping if the result is Unscoped.
   *
   * The `Tag <:< S` constraint ensures this scope can access values tagged with
   * `S`. Since child tags are subtypes of parent tags, a child scope can access
   * all ancestor-tagged values.
   *
   * @param scoped
   *   the scoped value to access
   * @param f
   *   the function to apply to the underlying value
   * @param ev
   *   evidence that this scope's Tag is a subtype of S
   * @param escape
   *   typeclass determining whether the result escapes
   * @tparam A
   *   the underlying value type
   * @tparam B
   *   the function result type
   * @tparam S
   *   the scoped value's tag
   * @return
   *   either raw B or B @@ S depending on ScopeEscape
   */
  def $[A, B, S](scoped: A @@ S)(f: A => B)(using
    ev: Tag <:< S,
    escape: ScopeEscape[B, S]
  ): escape.Out =
    escape(f(@@.unscoped(scoped)))

  /**
   * Executes a Scoped computation.
   *
   * The `Tag <:< S` constraint ensures this scope can execute computations
   * requiring tag `S`. The escape typeclass determines whether the result stays
   * scoped or escapes as a raw value.
   *
   * @param scoped
   *   the Scoped computation to execute
   * @param ev
   *   evidence that this scope's Tag is a subtype of S
   * @param escape
   *   typeclass determining whether the result escapes
   * @tparam A
   *   the computation result type
   * @tparam S
   *   the computation's required tag
   * @return
   *   either raw A or A @@ S depending on ScopeEscape
   */
  def apply[A, S](scoped: Scoped[S, A])(using
    ev: Tag <:< S,
    escape: ScopeEscape[A, S]
  ): escape.Out =
    escape(scoped.run())
}

package zio.blocks.scope

import zio.blocks.scope.internal.Finalizers

/**
 * Scala 3-specific extension methods for Scope.
 */
private[scope] trait ScopeVersionSpecific[ParentTag, Tag0 <: ParentTag] {
  self: Scope[ParentTag, Tag0] =>

  /**
   * Applies a function to a scoped value, escaping if the result is Unscoped.
   *
   * The `Tag <:< S` constraint ensures this scope can access values tagged with
   * `S`. Since child tags are subtypes of parent tags, a child scope can access
   * all ancestor-tagged values.
   *
   * This method executes the scoped computation, applies the function, and
   * determines whether the result escapes based on `ScopeEscape`.
   *
   * @param scoped
   *   the scoped computation to execute
   * @param f
   *   the function to apply to the computed value
   * @param ev
   *   evidence that this scope's Tag is a subtype of S
   * @param escape
   *   typeclass determining whether the result escapes
   * @tparam A
   *   the scoped computation's result type
   * @tparam B
   *   the function result type
   * @tparam S
   *   the scoped value's tag
   * @return
   *   either raw B or B @@ S depending on ScopeEscape
   */
  def $[A, B, S](scoped: A @@ S)(f: A => B)(using
    ev: self.Tag <:< S,
    escape: ScopeEscape[B, S]
  ): escape.Out =
    escape(f(scoped.run()))

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
  def execute[A, S](scoped: Scoped[S, A])(using
    ev: self.Tag <:< S,
    escape: ScopeEscape[A, S]
  ): escape.Out =
    escape(scoped.run())

  /**
   * Creates a child scope with an existential tag.
   *
   * The block receives a child scope that can access this scope's resources.
   * The child scope closes when the block exits, running all finalizers.
   *
   * The child scope's Tag is existential - it's a fresh type for each
   * invocation that cannot be named outside the lambda body. This provides
   * compile-time resource safety by preventing child-scoped resources from
   * escaping.
   *
   * @param f
   *   a function that receives the child scope
   * @tparam A
   *   the result type
   * @return
   *   the result of the function
   */
  def scoped[A](f: Scope[self.Tag, ? <: self.Tag] => A): A = {
    val childScope                = new Scope[self.Tag, self.Tag](new Finalizers)
    var primary: Throwable | Null = null
    try f(childScope)
    catch {
      case t: Throwable =>
        primary = t
        throw t
    } finally {
      val errors = childScope.close()
      if (primary != null) {
        errors.foreach(primary.nn.addSuppressed)
      } else if (errors.nonEmpty) {
        val first = errors.head
        errors.tail.foreach(first.addSuppressed)
        throw first
      }
    }
  }
}

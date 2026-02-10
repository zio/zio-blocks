package zio.blocks.scope

import zio.blocks.scope.internal.Finalizers

/**
 * Scala 2-specific extension methods for Scope.
 */
private[scope] trait ScopeVersionSpecific[ParentTag, Tag0 <: ParentTag] {
  self: Scope[ParentTag, Tag0] =>

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
   * @tparam A
   *   the underlying value type
   * @tparam B
   *   the function result type
   * @tparam S
   *   the scoped value's tag
   * @return
   *   either raw B or B @@ S depending on ScopeEscape
   */
  def $[A, B, S](scoped: A @@ S)(f: A => B)(implicit
    ev: self.Tag <:< S,
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
   * @tparam A
   *   the computation result type
   * @tparam S
   *   the computation's required tag
   * @return
   *   either raw A or A @@ S depending on ScopeEscape
   */
  def execute[A, S](scoped: Scoped[S, A])(implicit
    ev: self.Tag <:< S,
    escape: ScopeEscape[A, S]
  ): escape.Out =
    escape(scoped.run())

  /**
   * Creates a child scope with an existential tag.
   *
   * The function receives a child scope that can access this scope's resources.
   * The child scope closes when the block exits, running all finalizers.
   *
   * The child scope's Tag is existential - it's a fresh type for each
   * invocation that cannot be named outside the lambda body. This provides
   * compile-time resource safety by preventing child-scoped resources from
   * escaping.
   *
   * @param f
   *   the function to execute with the child scope
   * @tparam A
   *   the result type
   * @return
   *   the result of the function
   */
  def scoped[A](f: Scope[self.Tag, _ <: self.Tag] => A): A = {
    val childScope         = new Scope[self.Tag, self.Tag](new Finalizers)
    var primary: Throwable = null.asInstanceOf[Throwable]
    try f(childScope)
    catch {
      case t: Throwable =>
        primary = t
        throw t
    } finally {
      val errors = childScope.close()
      if (primary != null) {
        errors.foreach(primary.addSuppressed)
      } else if (errors.nonEmpty) {
        val first = errors.head
        errors.tail.foreach(first.addSuppressed)
        throw first
      }
    }
  }
}

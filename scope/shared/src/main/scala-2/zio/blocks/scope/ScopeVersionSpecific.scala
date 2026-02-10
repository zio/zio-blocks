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
   * Accepts any scoped value whose tag is a supertype of this scope's Tag. Due
   * to contravariance in `S`, an `A @@ ParentTag` is a subtype of
   * `A @@ ChildTag`, so parent-scoped values can be passed to child scopes.
   *
   * @param scoped
   *   the scoped computation to execute
   * @param f
   *   the function to apply to the computed value
   * @param escape
   *   typeclass determining whether the result escapes
   * @tparam A
   *   the scoped computation's result type
   * @tparam B
   *   the function result type
   * @return
   *   either raw B or B @@ Tag depending on ScopeEscape
   */
  def $[A, B](scoped: A @@ self.Tag)(f: A => B)(implicit
    escape: ScopeEscape[B, self.Tag]
  ): escape.Out =
    escape(f(scoped.run()))

  /**
   * Executes a scoped computation.
   *
   * Accepts any scoped computation whose tag is a supertype of this scope's
   * Tag. The escape typeclass determines whether the result stays scoped or
   * escapes as a raw value.
   *
   * @param scoped
   *   the scoped computation to execute
   * @param escape
   *   typeclass determining whether the result escapes
   * @tparam A
   *   the computation result type
   * @return
   *   either raw A or A @@ Tag depending on ScopeEscape
   */
  def execute[A](scoped: A @@ self.Tag)(implicit
    escape: ScopeEscape[A, self.Tag]
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
   * ==Return Type Safety==
   *
   * The return type `A` must satisfy [[SafeToReturn]], which allows:
   *   - [[Unscoped]] types (pure data)
   *   - Scoped values `B @@ T` where `T` is this scope's tag or above
   *
   * This prevents returning closures that capture the child scope, which
   * would allow use-after-close of child-scoped resources.
   *
   * @param f
   *   the function to execute with the child scope
   * @tparam A
   *   the result type (must be [[SafeToReturn]])
   * @return
   *   the result of the function
   */
  def scoped[A](f: Scope[self.Tag, _ <: self.Tag] => A)(implicit ev: SafeToReturn[A, self.Tag]): A = {
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

package zio.blocks.scope

import zio.blocks.scope.internal.Finalizers

/**
 * Scala 3-specific extension methods for Scope.
 */
private[scope] trait ScopeVersionSpecific[ParentTag, Tag0 <: ParentTag] {
  self: Scope[ParentTag, Tag0] =>

  /**
   * Applies a function to a scoped value.
   *
   * Accepts any scoped value whose tag is a supertype of this scope's Tag. Due
   * to contravariance in `S`, an `A @@ ParentTag` is a subtype of
   * `A @@ ChildTag`, so parent-scoped values can be passed to child scopes.
   *
   * The result is always tagged with the scope's Tag, preventing closures from
   * escaping with an unscoped result that could be executed after scope close.
   *
   * @param scoped
   *   the scoped computation to execute
   * @param f
   *   the function to apply to the computed value
   * @tparam A
   *   the scoped computation's result type
   * @tparam B
   *   the function result type
   * @return
   *   B @@ Tag (result is always scoped)
   */
  def $[A, B](scoped: A @@ self.Tag)(f: A => B): B @@ self.Tag = {
    val result = f(scoped.run()) // Execute immediately
    Scoped.scoped[B, self.Tag](result) // Wrap already-computed result
  }

  /**
   * Executes a scoped computation.
   *
   * Accepts any scoped computation whose tag is a supertype of this scope's
   * Tag. The result is always tagged with the scope's Tag, preventing closures
   * from escaping with an unscoped result.
   *
   * @param scoped
   *   the scoped computation to execute
   * @tparam A
   *   the computation result type
   * @return
   *   A @@ Tag (result is always scoped)
   */
  def execute[A](scoped: A @@ self.Tag): A @@ self.Tag = {
    val result = scoped.run() // Execute immediately
    Scoped.scoped[A, self.Tag](result) // Wrap already-computed result
  }

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
   * ==Return Type Safety==
   *
   * The return type `A` must satisfy [[ScopeLift]], which allows:
   *   - [[Unscoped]] types (pure data) - lifted as raw value
   *   - Scoped values `B @@ T` where `T` is this scope's tag or above - lifted
   *     as-is
   *
   * This prevents returning closures that capture the child scope, which would
   * allow use-after-close of child-scoped resources.
   *
   * @param f
   *   a function that receives the child scope
   * @param lift
   *   typeclass determining how the result is lifted to the parent scope
   * @tparam A
   *   the result type (must have a [[ScopeLift]] instance)
   * @return
   *   the lifted result (type depends on `lift.Out`)
   */
  def scoped[A](f: Scope[self.Tag, ? <: self.Tag] => A)(using lift: ScopeLift[A, self.Tag]): lift.Out = {
    val childScope                = new Scope[self.Tag, self.Tag](new Finalizers)
    var primary: Throwable | Null = null
    var result: A                 = null.asInstanceOf[A]
    try result = f(childScope)
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
    lift(result)
  }
}

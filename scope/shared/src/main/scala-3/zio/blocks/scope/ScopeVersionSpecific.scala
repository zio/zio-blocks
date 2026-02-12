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
   * Accepts any scoped value whose tag is a supertype of this scope's ScopeTag.
   * Due to contravariance in `S`, an `A @@ ParentTag` is a subtype of
   * `A @@ ChildTag`, so parent-scoped values can be passed to child scopes.
   *
   * The result is always tagged with the scope's ScopeTag, preventing closures
   * from escaping with an unscoped result that could be executed after scope
   * close.
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
   *   B @@ ScopeTag (result is always scoped)
   *
   * @example
   *   {{{
   *   import scope._
   *   $(database)(_.query("SELECT 1"))
   *   }}}
   */
  infix def $[A, B](scoped: A @@ self.ScopeTag)(f: A => B): B @@ self.ScopeTag =
    // NOTE: This isClosed check is racy - a proper synchronization mechanism
    // is needed to fully prevent use-after-close in concurrent scenarios.
    if (self.isClosed) {
      // Scope is closed - stay lazy to prevent use-after-close
      Scoped.deferred[B, self.ScopeTag](f(Scoped.run(scoped)))
    } else {
      // Scope is open - execute eagerly
      val result = f(Scoped.run(scoped))
      Scoped.eager[B, self.ScopeTag](result)
    }

  /**
   * Executes a scoped computation.
   *
   * Accepts any scoped computation whose tag is a supertype of this scope's
   * ScopeTag. The result is always tagged with the scope's ScopeTag, preventing
   * closures from escaping with an unscoped result.
   *
   * @param scoped
   *   the scoped computation to execute
   * @tparam A
   *   the computation result type
   * @return
   *   A @@ ScopeTag (result is always scoped)
   */
  def execute[A](scoped: A @@ self.ScopeTag): A @@ self.ScopeTag =
    // NOTE: This isClosed check is racy - a proper synchronization mechanism
    // is needed to fully prevent use-after-close in concurrent scenarios.
    if (self.isClosed) {
      // Scope is closed - stay lazy to prevent use-after-close
      Scoped.deferred[A, self.ScopeTag](Scoped.run(scoped))
    } else {
      // Scope is open - execute eagerly
      val result = Scoped.run(scoped)
      Scoped.eager[A, self.ScopeTag](result)
    }

  /**
   * Implicit class providing map/flatMap on scoped values for this scope.
   * Brought into scope via `import scope._`.
   */
  implicit class ScopedOps[A](private val scoped: A @@ self.ScopeTag) {
    def map[B](f: A => B): B @@ self.ScopeTag =
      Scoped.deferred[B, self.ScopeTag](f(Scoped.run(scoped)))

    def flatMap[B](f: A => B @@ self.ScopeTag): B @@ self.ScopeTag =
      Scoped.deferred[B, self.ScopeTag](Scoped.run(f(Scoped.run(scoped))))
  }

  /**
   * Creates a child scope with an existential tag.
   *
   * The block receives a child scope that can access this scope's resources.
   * The child scope closes when the block exits, running all finalizers.
   *
   * The child scope's ScopeTag is existential - it's a fresh type for each
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
  def scoped[A](f: Scope[self.ScopeTag, ? <: self.ScopeTag] => A)(using lift: ScopeLift[A, self.ScopeTag]): lift.Out = {
    // If parent scope is closed, create child as already-closed.
    // This makes all child operations ($ , execute, defer, allocate) no-ops,
    // preventing use-after-close when a leaked scope is misused.
    val childFinalizers           = if (self.isClosed) Finalizers.closed else new Finalizers
    val childScope                = new Scope[self.ScopeTag, self.ScopeTag](childFinalizers)
    var primary: Throwable | Null = null
    var out: lift.Out             = null.asInstanceOf[lift.Out]
    try {
      val result = f(childScope)
      // CRITICAL: Apply lift BEFORE closing the scope.
      // scopedUnscoped calls @@.unscoped which forces Scoped.run on deferred
      // computations. These must execute while the scope is still open.
      out = lift(result)
    } catch {
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
    out
  }
}

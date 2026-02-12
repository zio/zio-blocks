package zio.blocks.scope

/**
 * Scala 3 version-specific methods for Scope.
 *
 * Provides the `scoped` method using Scala 3's dependent function types.
 */
private[scope] trait ScopeVersionSpecific { self: Scope =>

  /**
   * Create a child scope. Block must return child.$[A], unwrapped to A at
   * boundary. Validates Unscoped[A] at compile time.
   *
   * @example
   *   {{{
   *   Scope.global.scoped { scope =>
   *     import scope._
   *     val db: $[Database] = allocate(Resource(new Database))
   *     val result: String = scope.$(db)(_.query("SELECT 1"))
   *     result  // String is Unscoped, can be returned
   *   }
   *   }}}
   */
  final def scoped[A](f: (child: Scope.Child[self.type]) => child.$[A])(using ev: Unscoped[A]): A = {
    val child              = new Scope.Child[self.type](self, new internal.Finalizers)
    var primary: Throwable = null
    var unwrapped: A       = null.asInstanceOf[A]
    try {
      val result: child.$[A] = f(child)
      unwrapped = child.wrap.run(result)
    } catch {
      case t: Throwable =>
        primary = t
        throw t
    } finally {
      val errors = child.close()
      if (primary != null) {
        errors.foreach(primary.addSuppressed)
      } else if (errors.nonEmpty) {
        val first = errors.head
        errors.tail.foreach(first.addSuppressed)
        throw first
      }
    }
    unwrapped
  }
}

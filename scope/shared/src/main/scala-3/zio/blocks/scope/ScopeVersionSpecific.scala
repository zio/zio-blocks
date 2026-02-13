package zio.blocks.scope

/**
 * Scala 3 version-specific methods for Scope.
 *
 * Provides the `scoped` method using Scala 3's dependent function types, and
 * the `leak` macro for escaping the scoped type system with a warning.
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
   *     val result: String = scope.use(db)(_.query("SELECT 1"))
   *     result  // String is Unscoped, can be returned
   *   }
   *   }}}
   */
  final def scoped[A](f: (child: Scope.Child[self.type]) => child.$[A])(using ev: Unscoped[A]): A = {
    // If this scope is already closed, create a born-closed child.
    // The child's operations will be no-ops (returning null-scoped values),
    // and its finalizers run immediately after the block completes.
    val fins               = if (self.isClosed) internal.Finalizers.closed else new internal.Finalizers
    val child              = new Scope.Child[self.type](self, fins)
    var primary: Throwable = null
    var unwrapped: A       = null.asInstanceOf[A]
    try {
      val result: child.$[A] = f(child)
      unwrapped = child.$run(result)
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

  /**
   * Escape hatch: unwrap a scoped value to its raw type, bypassing compile-time
   * scope safety. Emits a compiler warning.
   *
   * Use this only for interop with code that cannot work with scoped types. If
   * the type is pure data, prefer adding an `Unscoped` instance instead.
   *
   * @example
   *   {{{
   *   Scope.global.scoped { scope =>
   *     import scope._
   *     val db: $[Database] = allocate(Resource(new Database))
   *     val leaked: Database = scope.leak(db) // compiler warning
   *     leaked
   *   }
   *   }}}
   */
  inline def leak[A](inline sa: $[A]): A = ${ LeakMacros.leakImpl[A]('sa, 'self) }
}

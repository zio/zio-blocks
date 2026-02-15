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
   *     val result = scope.use(db)(_.query("SELECT 1"))
   *     result  // returned as child.$[String], unwrapped to String at the boundary
   *   }
   *   }}}
   *
   * @tparam A
   *   the return type of the scoped block; must have an [[Unscoped]] instance,
   *   ensuring only pure data escapes the scope boundary
   * @param f
   *   a dependent function that receives a [[Scope.Child]] and returns a scoped
   *   value `child.$[A]`
   * @return
   *   the unwrapped value of type `A`, after all child-scope finalizers have
   *   run
   * @throws java.lang.IllegalStateException
   *   if the current thread does not own this scope (thread-ownership
   *   violation)
   */
  final def scoped[A](f: (child: Scope.Child[self.type]) => child.$[A])(using ev: Unscoped[A]): A = {
    if (!self.isOwner) {
      val current   = PlatformScope.currentThreadName()
      val ownerInfo = self match {
        case c: Scope.Child[_] => s" (owner: '${PlatformScope.ownerName(c.owner)}')"
        case _                 => ""
      }
      throw new IllegalStateException(
        s"Cannot create child scope: current thread '$current' does not own this scope$ownerInfo"
      )
    }
    // If this scope is already closed, create a born-closed child.
    // The child's operations will be no-ops (returning null-scoped values),
    // and its finalizers run immediately after the block completes.
    val fins               = if (self.isClosed) internal.Finalizers.closed else new internal.Finalizers
    val child              = new Scope.Child[self.type](self, fins, PlatformScope.captureOwner())
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
      // Use fins.runAll() directly (not child.close()) for consistency with
      // the Scala 2 macro, where private[scope] members are inaccessible
      // at the call-site expansion.
      val finalization = fins.runAll()
      if (primary != null) {
        finalization.suppress(primary)
      } else {
        finalization.orThrow()
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
   *
   * @tparam A
   *   the underlying type of the scoped value
   * @param sa
   *   the scoped value to unwrap
   * @return
   *   the raw value of type `A`, no longer tracked by the scope
   */
  inline def leak[A](inline sa: $[A]): A = ${ LeakMacros.leakImpl[A]('sa, 'self) }
}

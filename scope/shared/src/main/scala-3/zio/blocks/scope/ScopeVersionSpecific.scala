package zio.blocks.scope

/**
 * Scala 3 version-specific methods for Scope.
 *
 * Provides the `scoped` method using Scala 3's dependent function types, the
 * macro-enforced `$` for safe resource access, and the `leak` macro for
 * escaping the scoped type system with a warning.
 */
private[scope] trait ScopeVersionSpecific { self: Scope =>

  implicit val thisScope: this.type = this

  /**
   * Create a child scope. The block receives a child scope and returns a plain
   * value of type `A`, which must have an [[Unscoped]] instance.
   *
   * @example
   *   {{{
   *   Scope.global.scoped { scope =>
   *     import scope._
   *     val db: $[Database] = allocate(Resource(new Database))
   *     (scope $ db)(_.query("SELECT 1")).get
   *   }
   *   }}}
   *
   * @tparam A
   *   the return type of the scoped block; must have an [[Unscoped]] instance,
   *   ensuring only pure data escapes the scope boundary
   * @param f
   *   a function that receives a [[Scope.Child]] and returns a value of type
   *   `A`
   * @return
   *   the value of type `A`, after all child-scope finalizers have run
   * @throws java.lang.IllegalStateException
   *   if the current thread does not own this scope (thread-ownership
   *   violation)
   */
  final def scoped[A](f: (child: Scope.Child[self.type]) => A)(using ev: Unscoped[A]): A = {
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
    val fins               = if (self.isClosed) internal.Finalizers.closed else new internal.Finalizers
    val child              = new Scope.Child[self.type](self, fins, PlatformScope.captureOwner())
    var primary: Throwable = null
    var result: A          = null.asInstanceOf[A]
    try {
      result = f(child)
    } catch {
      case t: Throwable =>
        primary = t
        throw t
    } finally {
      val finalization = fins.runAll()
      if (primary != null) {
        finalization.suppress(primary)
      } else {
        finalization.orThrow()
      }
    }
    result
  }

  /**
   * Macro-enforced access to a scoped value.
   *
   * Unwraps the scoped value, applies the function, and re-wraps the result.
   * The macro verifies at compile time that the lambda parameter is only used
   * in method-receiver position (e.g., `x.method()`), preventing resource leaks
   * through capture, storage, or passing as an argument.
   *
   * @example
   *   {{{
   *   // Allowed:
   *   (scope $ db)(_.query("SELECT 1"))
   *   (scope $ db)(db => db.query("a") + db.query("b"))
   *
   *   // Rejected at compile time:
   *   (scope $ db)(db => store(db))       // param as argument
   *   (scope $ db)(db => () => db.query()) // captured in closure
   *   }}}
   *
   * @param sa
   *   the scoped value to access
   * @param f
   *   a lambda whose parameter is only used as a method receiver
   * @tparam A
   *   the input value type
   * @tparam B
   *   the output value type
   * @return
   *   the result wrapped as `$[B]`, or a default-valued `$[B]` if closed
   */
  infix transparent inline def $[A, B](sa: $[A])(inline f: A => B): $[B] = {
    UseMacros.check[A, B](f)
    if (self.isClosed) null.asInstanceOf[$[B]]
    else {
      val unwrapped = sa.asInstanceOf[A]
      val result    = f(unwrapped)
      result.asInstanceOf[$[B]]
    }
  }

  /**
   * Escape hatch: unwrap a scoped value to its raw type, bypassing compile-time
   * scope safety. Emits a compiler warning.
   *
   * Use this only for interop with code that cannot work with scoped types. If
   * the type is pure data, prefer adding an `Unscoped` instance instead.
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

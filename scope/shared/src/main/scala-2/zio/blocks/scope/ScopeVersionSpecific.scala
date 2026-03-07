package zio.blocks.scope

import scala.language.experimental.macros

/**
 * Scala 2 version-specific methods for Scope.
 *
 * Provides the `scoped` method, the macro-enforced `$` for safe resource
 * access, and the `leak` macro for escaping the scoped type system with a
 * warning.
 */
private[scope] trait ScopeVersionSpecific { self: Scope =>

  /**
   * Create a child scope. The block receives a child scope and returns a plain
   * value of type `A`, which must have an [[Unscoped]] instance.
   *
   * @param f
   *   a function that receives a [[Scope.Child]] and returns a value of type
   *   `A`
   * @return
   *   the value of type `A`, after all child-scope finalizers have run
   * @throws java.lang.IllegalStateException
   *   if the current thread does not own this scope (thread-ownership
   *   violation)
   */
  final def scoped[A](f: Scope.Child[self.type] => A)(implicit ev: Unscoped[A]): A = {
    if (!self.isOwner) {
      val current   = PlatformScope.currentThreadName()
      val ownerInfo =
        if (self.isInstanceOf[Scope.Child[_]])
          " (owner: '" + PlatformScope.ownerName(
            self.asInstanceOf[Scope.Child[_]].owner
          ) + "')"
        else ""
      throw new IllegalStateException(
        "Cannot create child scope: current thread '" + current + "' does not own this scope" + ownerInfo
      )
    }
    val fins =
      if (self.isClosed) internal.Finalizers.closed
      else new internal.Finalizers
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

  // ── N-ary $ operator ────────────────────────────────────────────────────
  //
  // N=1: use `(scope $ sa)(f)` or `$(sa)(f)` after `import scope._`
  // N≥2: use `$(sa1, sa2)(f)` after `import scope._`
  //       (infix syntax is not available for N≥2)
  // N>5: compose — `$(sa1)(v1 => $(sa2)(v2 => ...))`
  //
  // All are whitebox macros: the declared return type `Any` is refined at
  // expansion time to B (when Unscoped[B] exists) or $[B] (otherwise).

  /**
   * Macro-enforced access to a scoped value (N=1).
   *
   * @param sa
   *   the scoped value to access
   * @param f
   *   a lambda whose parameter is only used as a method receiver
   * @return
   *   the result as `B` if `B` has an `Unscoped` instance, otherwise as `$[B]`
   * @throws java.lang.IllegalStateException
   *   if this scope is already closed
   */
  def $[A, B](sa: $[A])(f: A => B): Any = macro ScopeMacros.useImpl

  /**
   * Macro-enforced access to two scoped values simultaneously (N=2).
   *
   * @throws java.lang.IllegalStateException
   *   if this scope is already closed
   */
  def $[A1, A2, B](sa1: $[A1], sa2: $[A2])(f: (A1, A2) => B): Any =
    macro ScopeMacros.use2Impl

  /**
   * Macro-enforced access to three scoped values simultaneously (N=3).
   *
   * @throws java.lang.IllegalStateException
   *   if this scope is already closed
   */
  def $[A1, A2, A3, B](sa1: $[A1], sa2: $[A2], sa3: $[A3])(f: (A1, A2, A3) => B): Any =
    macro ScopeMacros.use3Impl

  /**
   * Macro-enforced access to four scoped values simultaneously (N=4).
   *
   * @throws java.lang.IllegalStateException
   *   if this scope is already closed
   */
  def $[A1, A2, A3, A4, B](sa1: $[A1], sa2: $[A2], sa3: $[A3], sa4: $[A4])(
    f: (A1, A2, A3, A4) => B
  ): Any = macro ScopeMacros.use4Impl

  /**
   * Macro-enforced access to five scoped values simultaneously (N=5).
   *
   * @throws java.lang.IllegalStateException
   *   if this scope is already closed
   */
  def $[A1, A2, A3, A4, A5, B](sa1: $[A1], sa2: $[A2], sa3: $[A3], sa4: $[A4], sa5: $[A5])(
    f: (A1, A2, A3, A4, A5) => B
  ): Any = macro ScopeMacros.use5Impl

  /**
   * Escape hatch: unwrap a scoped value to its raw type, bypassing compile-time
   * scope safety. Emits a compiler warning.
   *
   * @tparam A
   *   the underlying type of the scoped value
   * @param sa
   *   the scoped value to unwrap
   * @return
   *   the raw value of type `A`, no longer tracked by the scope
   */
  def leak[A](sa: $[A]): A = macro ScopeMacros.leakImpl
}

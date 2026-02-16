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

  /**
   * Macro-enforced access to a scoped value.
   *
   * Unwraps the scoped value, applies the function, and returns the result. If
   * `B` has an [[Unscoped]] instance, the result is returned directly as `B`
   * (auto-unwrapped). Otherwise, the result is re-wrapped as `$[B]`. The macro
   * verifies at compile time that the lambda parameter is only used in
   * method-receiver position (e.g., `x.method()`), preventing resource leaks.
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
   *   the result as `B` if `B` has an `Unscoped` instance (auto-unwrapped),
   *   otherwise as `$[B]`; returns a default value if the scope is closed
   */
  def $[A, B](sa: $[A])(f: A => B): Any = macro ScopeMacros.useImpl

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

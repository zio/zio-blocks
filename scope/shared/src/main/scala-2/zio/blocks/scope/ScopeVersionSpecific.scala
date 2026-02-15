package zio.blocks.scope

import scala.language.experimental.macros

/**
 * Scala 2 version-specific methods for Scope.
 *
 * Provides the macro-based `scoped` method that enables ergonomic
 * `scoped { child => ... }` syntax in Scala 2, and the `leak` macro for
 * escaping the scoped type system with a warning.
 */
private[scope] trait ScopeVersionSpecific { self: Scope =>

  /**
   * Create a child scope. Block must return child.$[A], unwrapped to A at
   * boundary. Validates Unscoped[A] at compile time.
   *
   * In Scala 2, this must be a lambda literal (no method references).
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
   * @param f
   *   a function that receives a [[Scope.Child]] and returns a scoped value;
   *   the macro rewrites the return type so that only values with an
   *   [[Unscoped]] instance can escape the scope boundary
   * @return
   *   the unwrapped value, after all child-scope finalizers have run. The
   *   erased signature is `Any`; the macro narrows the type at the call site
   * @throws java.lang.IllegalStateException
   *   if the current thread does not own this scope (thread-ownership
   *   violation)
   */
  final def scoped(f: Scope.Child[self.type] => Any): Any = macro ScopeMacros.scopedImpl

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
  def leak[A](sa: $[A]): A = macro ScopeMacros.leakImpl
}

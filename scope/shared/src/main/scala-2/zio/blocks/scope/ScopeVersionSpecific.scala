package zio.blocks.scope

import scala.language.experimental.macros

/**
 * Scala 2 version-specific methods for Scope.
 *
 * Provides the macro-based `scoped` method that enables ergonomic
 * `scoped { child => ... }` syntax in Scala 2, and the `leak` macro
 * for escaping the scoped type system with a warning.
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
   *     val result: String = scope.use(db)(_.query("SELECT 1"))
   *     result  // String is Unscoped, can be returned
   *   }
   *   }}}
   */
  final def scoped(f: Scope.Child[self.type] => Any): Any = macro ScopeMacros.scopedImpl

  /**
   * Escape hatch: unwrap a scoped value to its raw type, bypassing
   * compile-time scope safety. Emits a compiler warning.
   *
   * Use this only for interop with code that cannot work with scoped types.
   * If the type is pure data, prefer adding an `Unscoped` instance instead.
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
  def leak[A](sa: $[A]): A = macro ScopeMacros.leakImpl
}

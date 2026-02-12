package zio.blocks.scope

import scala.language.experimental.macros

/**
 * Scala 2 version-specific methods for Scope.
 * 
 * Provides the macro-based `scoped` method that enables ergonomic
 * `scoped { child => ... }` syntax in Scala 2.
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
   *     val result: String = scope.$(db)(_.query("SELECT 1"))
   *     result  // String is Unscoped, can be returned
   *   }
   *   }}}
   */
  final def scoped(f: Scope.Child[self.type] => Any): Any = macro ScopeMacros.scopedImpl
}

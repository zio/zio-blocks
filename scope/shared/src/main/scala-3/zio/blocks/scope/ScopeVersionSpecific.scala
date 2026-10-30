/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.scope

/**
 * The members of [[Scope]] whose implementation differs between Scala 2 and
 * Scala 3: `scoped`, the `$` macro and `leak`, plus, on Scala 3 only, the
 * implicit `thisScope`.
 */
private[scope] trait ScopeVersionSpecific { self: Scope =>

  /**
   * This scope as an implicit value (Scala 3 only). After `import scope.*`, a
   * constructor or method that takes an implicit `Finalizer` or `Scope`
   * receives this scope.
   */
  implicit val thisScope: this.type = this

  /**
   * Opens a child scope, runs `f` in it, closes the child, and returns the
   * result of `f`.
   *
   * The child's finalizers run when `f` returns or throws, in reverse order of
   * registration. If `f` throws, finalizer errors are added to its exception as
   * suppressed exceptions; otherwise the first finalizer error is thrown with
   * the others suppressed. The result type `A` must have an `Unscoped`
   * instance, so a resource or a scoped value cannot be returned. On a closed
   * scope, `f` runs in a child that is already closed.
   *
   * On Scala 3, when the last expression of `f` does not type-check, the
   * compiler also reports an "Ambiguous given instances" error for `Unscoped`;
   * fix the first error first (a known bug).
   *
   * {{{
   * final class Connection extends AutoCloseable {
   *   def query(sql: String): Int = sql.length
   *   def close(): Unit           = println("connection closed")
   * }
   *
   * val rows: Int = Scope.global.scoped { scope =>
   *   import scope._
   *   val conn = allocate(new Connection)
   *   $(conn)(_.query("SELECT 1"))
   * }
   * // prints: connection closed
   * // rows: 8
   * }}}
   *
   * @tparam A
   *   the return type of the scoped block; must have an `Unscoped` instance, so
   *   that only plain data leaves the scope
   * @param f
   *   a function that receives a `Scope.Child` and returns a value of type `A`
   * @return
   *   the value of type `A`, after all child-scope finalizers have run
   * @throws java.lang.IllegalStateException
   *   if the current thread does not own this scope (see `Scope#isOwner`)
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

  // ── N-ary $ operator ────────────────────────────────────────────────────
  //
  // N=1: infix — use `(scope $ sa)(f)` or `$(sa)(f)` after `import scope.*`
  // N≥2: not infix — use `$(sa1, sa2)(f)` after `import scope.*`
  //       (infix requires exactly one operand)
  // N>5: compose — `$(sa1)(v1 => $(sa2)(v2 => ...))`
  //
  // On Scope.global, type $[+A] = A, so the overloads accept plain values.
  // This is a pre-existing property of the design (zero-cost, no phantom types
  // on global) and is unchanged from the unary case.
  //
  // Coverage note: these methods live under scala-3/ and are excluded from the
  // statement/branch coverage gate. The test suite is the sole quality gate.

  /**
   * Applies `f` to the value inside `sa` and returns the result (N=1).
   *
   * At compile time, the macro checks that `f` uses its parameter only as the
   * receiver of a method call, as in `_.query("...")` or `c => c.query("a") +
   * c.query("b")`. Returning the parameter, passing it as an argument, storing
   * it in a `val` or `var`, or capturing it in a nested lambda is a compile
   * error. The result is a plain `B` when `B` has an `Unscoped` instance, and
   * `$[B]` otherwise, so a method that returns another resource keeps it in
   * this scope. On Scala 3 this is a `transparent inline` method; on Scala 2 it
   * is a whitebox macro whose declared result type `Any` is narrowed to `B` or
   * `$[B]` at the call site.
   *
   * After `import scope._`, call it unqualified as `$(sa)(f)`; without the
   * import, use the infix form `(scope $ sa)(f)`.
   *
   * {{{
   * final class Connection extends AutoCloseable {
   *   def query(sql: String): Int = sql.length
   *   def close(): Unit           = ()
   * }
   *
   * Scope.global.scoped { scope =>
   *   import scope._
   *   val conn = allocate(new Connection)
   *   val rows: Int = $(conn)(c => c.query("SELECT 1") + c.query("SELECT 2"))
   *   rows
   * }
   * }}}
   *
   * @tparam A
   *   the underlying type of the scoped value
   * @tparam B
   *   the result type produced by the lambda
   * @param sa
   *   the scoped value to access
   * @param f
   *   a lambda whose parameter is only used as a method receiver
   * @return
   *   the result as `B` if `B` has an `Unscoped` instance, otherwise as `$[B]`
   * @throws java.lang.IllegalStateException
   *   if this scope is already closed
   */
  infix transparent inline def $[A, B](sa: $[A])(inline f: A => B) = {
    UseMacros.check[A, B](f)
    if (self.isClosed)
      throw new IllegalStateException(
        zio.blocks.scope.internal.ErrorMessages
          .renderUseOnClosedScope(self.scopeDisplayName, color = false)
      )
    scala.compiletime.summonFrom {
      case _: Unscoped[B] =>
        val unwrapped = sa.asInstanceOf[A]
        f(unwrapped)
      case _ =>
        val unwrapped = sa.asInstanceOf[A]
        val result    = f(unwrapped)
        result.asInstanceOf[$[B]]
    }
  }

  /**
   * Applies `f` to the values inside `sa1` and `sa2` and returns the result
   * (N=2).
   *
   * Each parameter of `f` may only be used as a method receiver, as in the
   * one-value `$`, but the result of a call on one may be passed to a method of
   * the other: `(d, c) => d.query(c.key())`. The result is `B` when `B` has an
   * `Unscoped` instance, and `$[B]` otherwise. The infix form is not available
   * for two or more values, so call it as `$(sa1, sa2)(f)` after
   * `import scope._`.
   *
   * {{{
   * final class Database extends AutoCloseable {
   *   def query(sql: String): Int = sql.length
   *   def close(): Unit           = ()
   * }
   * final class Cache extends AutoCloseable {
   *   def key(): String = "SELECT 1"
   *   def close(): Unit = ()
   * }
   *
   * Scope.global.scoped { scope =>
   *   import scope._
   *   val db    = allocate(new Database)
   *   val cache = allocate(new Cache)
   *   $(db, cache)((d, c) => d.query(c.key()))
   * }
   * }}}
   *
   * @tparam A1
   *   the underlying type of the first scoped value
   * @tparam A2
   *   the underlying type of the second scoped value
   * @tparam B
   *   the result type produced by the lambda
   * @param sa1
   *   the first scoped value to access
   * @param sa2
   *   the second scoped value to access
   * @param f
   *   a lambda whose parameters are only used as method receivers
   * @return
   *   the result as `B` if `B` has an `Unscoped` instance, otherwise as `$[B]`
   * @throws java.lang.IllegalStateException
   *   if this scope is already closed
   */
  transparent inline def $[A1, A2, B](sa1: $[A1], sa2: $[A2])(inline f: (A1, A2) => B) =
    ${ UseMacros.applyN2[A1, A2, B]('self, 'sa1, 'sa2, 'f) }

  /**
   * Applies `f` to the values inside three scoped values and returns the result
   * (N=3).
   *
   * Each parameter of `f` is subject to the same receiver-only rule as in the
   * two-value `$`. The result is `B` when `B` has an `Unscoped` instance, and
   * `$[B]` otherwise. For more than five values, nest calls:
   * `$(sa1)(v1 => $(sa2)(v2 => ...))`.
   *
   * @tparam A1
   *   the underlying type of the first scoped value
   * @tparam A2
   *   the underlying type of the second scoped value
   * @tparam A3
   *   the underlying type of the third scoped value
   * @tparam B
   *   the result type produced by the lambda
   * @param sa1
   *   the first scoped value to access
   * @param sa2
   *   the second scoped value to access
   * @param sa3
   *   the third scoped value to access
   * @param f
   *   a lambda whose parameters are only used as method receivers
   * @return
   *   the result as `B` if `B` has an `Unscoped` instance, otherwise as `$[B]`
   * @throws java.lang.IllegalStateException
   *   if this scope is already closed
   */
  transparent inline def $[A1, A2, A3, B](sa1: $[A1], sa2: $[A2], sa3: $[A3])(
    inline f: (A1, A2, A3) => B
  ) = ${ UseMacros.applyN3[A1, A2, A3, B]('self, 'sa1, 'sa2, 'sa3, 'f) }

  /**
   * Applies `f` to the values inside four scoped values and returns the result
   * (N=4).
   *
   * Each parameter of `f` is subject to the same receiver-only rule as in the
   * two-value `$`. The result is `B` when `B` has an `Unscoped` instance, and
   * `$[B]` otherwise. For more than five values, nest calls:
   * `$(sa1)(v1 => $(sa2)(v2 => ...))`.
   *
   * @tparam A1
   *   the underlying type of the first scoped value
   * @tparam A2
   *   the underlying type of the second scoped value
   * @tparam A3
   *   the underlying type of the third scoped value
   * @tparam A4
   *   the underlying type of the fourth scoped value
   * @tparam B
   *   the result type produced by the lambda
   * @param sa1
   *   the first scoped value to access
   * @param sa2
   *   the second scoped value to access
   * @param sa3
   *   the third scoped value to access
   * @param sa4
   *   the fourth scoped value to access
   * @param f
   *   a lambda whose parameters are only used as method receivers
   * @return
   *   the result as `B` if `B` has an `Unscoped` instance, otherwise as `$[B]`
   * @throws java.lang.IllegalStateException
   *   if this scope is already closed
   */
  transparent inline def $[A1, A2, A3, A4, B](
    sa1: $[A1],
    sa2: $[A2],
    sa3: $[A3],
    sa4: $[A4]
  )(inline f: (A1, A2, A3, A4) => B) =
    ${ UseMacros.applyN4[A1, A2, A3, A4, B]('self, 'sa1, 'sa2, 'sa3, 'sa4, 'f) }

  /**
   * Applies `f` to the values inside five scoped values and returns the result
   * (N=5).
   *
   * Each parameter of `f` is subject to the same receiver-only rule as in the
   * two-value `$`. The result is `B` when `B` has an `Unscoped` instance, and
   * `$[B]` otherwise. For more than five values, nest calls:
   * `$(sa1)(v1 => $(sa2)(v2 => ...))`.
   *
   * @tparam A1
   *   the underlying type of the first scoped value
   * @tparam A2
   *   the underlying type of the second scoped value
   * @tparam A3
   *   the underlying type of the third scoped value
   * @tparam A4
   *   the underlying type of the fourth scoped value
   * @tparam A5
   *   the underlying type of the fifth scoped value
   * @tparam B
   *   the result type produced by the lambda
   * @param sa1
   *   the first scoped value to access
   * @param sa2
   *   the second scoped value to access
   * @param sa3
   *   the third scoped value to access
   * @param sa4
   *   the fourth scoped value to access
   * @param sa5
   *   the fifth scoped value to access
   * @param f
   *   a lambda whose parameters are only used as method receivers
   * @return
   *   the result as `B` if `B` has an `Unscoped` instance, otherwise as `$[B]`
   * @throws java.lang.IllegalStateException
   *   if this scope is already closed
   */
  transparent inline def $[A1, A2, A3, A4, A5, B](
    sa1: $[A1],
    sa2: $[A2],
    sa3: $[A3],
    sa4: $[A4],
    sa5: $[A5]
  )(inline f: (A1, A2, A3, A4, A5) => B) =
    ${ UseMacros.applyN5[A1, A2, A3, A4, A5, B]('self, 'sa1, 'sa2, 'sa3, 'sa4, 'sa5, 'f) }

  /**
   * Returns the raw value inside `sa`, outside the control of the scope, and
   * makes the compiler emit a warning at the call site.
   *
   * Use it only for interop with code that cannot work with scoped values, such
   * as a library that needs the raw object. The scope still runs the value's
   * finalizers when it closes, so do not use the value after that. If the type
   * is plain data, give it an `Unscoped` instance instead.
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

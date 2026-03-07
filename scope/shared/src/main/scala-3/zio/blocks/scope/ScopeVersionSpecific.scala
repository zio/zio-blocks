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
   *     (scope $ db)(_.query("SELECT 1"))
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
   * Macro-enforced access to a scoped value (N=1).
   *
   * Unwraps the scoped value, applies the function, and returns the result. If
   * `B` has an [[Unscoped]] instance, the result is returned directly as `B`
   * (auto-unwrapped). Otherwise, the result is re-wrapped as `$[B]`. The macro
   * verifies at compile time that the lambda parameter is only used in
   * method-receiver position (e.g., `x.method()`), preventing resource leaks
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
   *   the result as `B` if `B` has an `Unscoped` instance (auto-unwrapped),
   *   otherwise as `$[B]`
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
   * Macro-enforced access to two scoped values simultaneously (N=2).
   *
   * Both scoped values are unwrapped and the lambda is applied. The lambda may
   * call methods on either parameter, and may feed the result of one as an
   * argument to a method of the other (e.g., `d1.method(d2.result())`). Direct
   * passing of either parameter as a bare argument is rejected at compile time.
   *
   * @example
   *   {{{
   *   $(db, cache)((d, c) => d.query(c.key()))
   *   $(db, cache)((d, c) => d.query("x") + c.get("y"))
   *   }}}
   *
   * @throws java.lang.IllegalStateException
   *   if this scope is already closed
   */
  transparent inline def $[A1, A2, B](sa1: $[A1], sa2: $[A2])(inline f: (A1, A2) => B) =
    ${ UseMacros.applyN2[A1, A2, B]('self, 'sa1, 'sa2, 'f) }

  /**
   * Macro-enforced access to three scoped values simultaneously (N=3).
   *
   * All three parameters are subject to the same receiver-only constraint as
   * the N=1 and N=2 overloads. See [[$ (sa: $[A])(f: A => B)]] for the full
   * safety rules.
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
   *   the result as `B` if `B` has an [[Unscoped]] instance, otherwise as
   *   `$[B]`
   * @throws java.lang.IllegalStateException
   *   if this scope is already closed
   */
  transparent inline def $[A1, A2, A3, B](sa1: $[A1], sa2: $[A2], sa3: $[A3])(
    inline f: (A1, A2, A3) => B
  ) = ${ UseMacros.applyN3[A1, A2, A3, B]('self, 'sa1, 'sa2, 'sa3, 'f) }

  /**
   * Macro-enforced access to four scoped values simultaneously (N=4).
   *
   * All four parameters are subject to the same receiver-only constraint as the
   * N=1 and N=2 overloads. See [[$ (sa: $[A])(f: A => B)]] for the full safety
   * rules.
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
   *   the result as `B` if `B` has an [[Unscoped]] instance, otherwise as
   *   `$[B]`
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
   * Macro-enforced access to five scoped values simultaneously (N=5).
   *
   * All five parameters are subject to the same receiver-only constraint as the
   * N=1 and N=2 overloads. See [[$ (sa: $[A])(f: A => B)]] for the full safety
   * rules.
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
   *   the result as `B` if `B` has an [[Unscoped]] instance, otherwise as
   *   `$[B]`
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

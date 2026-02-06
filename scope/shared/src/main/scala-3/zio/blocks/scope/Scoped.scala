package zio.blocks.scope

/**
 * Opaque type for scoping values with scope identity.
 *
 * A value of type `A @@ S` is a value of type `A` that is "locked" to a scope
 * with tag `S`. The opaque type hides all methods on `A`, so the only way to
 * use the value is through the `$` operator, which requires the matching scope
 * capability.
 *
 * This prevents scoped resources from escaping their scope at compile time.
 *
 * @example
 *   {{{
 *   // Scoped value cannot escape
 *   val stream: InputStream @@ scope.Tag = getStream()
 *   stream.read()  // Compile error: read() is not a member of InputStream @@ Tag
 *
 *   // Must use $ operator with scope in context
 *   stream $ (_.read())  // Works, returns Int (unscoped, since Int is Unscoped)
 *   }}}
 */
opaque infix type @@[+A, +S] = A

object @@ {

  /** Scopes a value with a scope identity. */
  inline def scoped[A, S](a: A): A @@ S = a

  /** Retrieves the underlying value without unscoping (internal use). */
  private[scope] inline def unscoped[A, S](scoped: A @@ S): A = scoped

  extension [A, S](scoped: A @@ S) {

    /**
     * Applies a function to the scoped value within the scope context.
     *
     * The result type depends on whether `B` is [[Unscoped]]:
     *   - If `B` is `Unscoped`, returns raw `B`
     *   - Otherwise, returns `B @@ S` (stays scoped)
     *
     * Zero overhead: The typeclass dispatch is resolved at compile time, and
     * both branches (identity for Unscoped, scoped for resources) compile to
     * no-ops since `@@` is an opaque type alias.
     *
     * @param f
     *   The function to apply to the underlying value
     * @param scope
     *   Evidence that the current scope encompasses tag `S`
     * @param u
     *   Typeclass determining the result type
     * @return
     *   Either raw `B` or `B @@ S` depending on ScopeEscape instance
     */
    inline infix def $[B](inline f: A => B)(using scope: Scope[?] { type Tag >: S })(using
      u: ScopeEscape[B, S]
    ): u.Out =
      u(f(scoped))

    /**
     * Extracts the scoped value, auto-unscoping if the type is [[Unscoped]].
     *
     * Equivalent to `scoped $ identity`. The result type depends on whether `A`
     * is [[Unscoped]]:
     *   - If `A` is `Unscoped`, returns raw `A`
     *   - Otherwise, returns `A @@ S` (stays scoped)
     *
     * @param scope
     *   Evidence that the current scope encompasses tag `S`
     * @param u
     *   Typeclass determining the result type
     * @return
     *   Either raw `A` or `A @@ S` depending on ScopeEscape instance
     */
    inline def get(using scope: Scope[?] { type Tag >: S })(using u: ScopeEscape[A, S]): u.Out =
      u(scoped)

    /**
     * Maps over a scoped value, preserving the tag.
     *
     * @param f
     *   The function to apply
     * @return
     *   Result with same tag
     */
    inline def map[B](inline f: A => B): B @@ S =
      f(scoped)

    /**
     * FlatMaps over a scoped value, widening to the outer scope.
     *
     * @param f
     *   Function returning a scoped result
     * @return
     *   Result with the wider tag T
     */
    inline def flatMap[B, T >: S](inline f: A => B @@ T): B @@ T =
      f(scoped)

    /** Extracts the first element of a scoped tuple. */
    inline def _1[X, Y](using ev: A =:= (X, Y)): X @@ S =
      ev(scoped)._1

    /** Extracts the second element of a scoped tuple. */
    inline def _2[X, Y](using ev: A =:= (X, Y)): Y @@ S =
      ev(scoped)._2
  }
}

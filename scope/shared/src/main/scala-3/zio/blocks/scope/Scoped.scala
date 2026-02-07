package zio.blocks.scope

/**
 * Opaque type for scoping values with scope identity.
 *
 * A value of type `A @@ S` is a value of type `A` that is "locked" to a scope
 * with tag `S`. The opaque type hides all methods on `A`, so the only way to
 * use the value is through the `$` operator, which requires the matching scope
 * capability via [[Scope.Permit]].
 *
 * This prevents scoped resources from escaping their scope at compile time.
 * Zero overhead at runtime: `@@` is an opaque type alias, so `A @@ S` is
 * represented as just `A` at runtime.
 *
 * ==Operations==
 *
 * The `@@` companion object provides extension methods:
 *   - `$`: Apply a function to the value, auto-unscoping if the result is
 *     [[Unscoped]]
 *   - `get`: Extract the value, auto-unscoping if the type is [[Unscoped]]
 *   - `map`: Transform the value, preserving the scope tag
 *   - `flatMap`: Combine scoped values, intersecting tags
 *   - `_1`, `_2`: Extract tuple elements
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
 *
 * @see
 *   [[ScopeEscape]] for the typeclass that determines if a result escapes
 * @see
 *   [[Scope.Permit]] for the evidence required to access scoped values
 */
opaque infix type @@[+A, +S] = A

/**
 * Companion object for the `@@` opaque type, providing factory methods and
 * extension operations.
 */
object @@ {

  /**
   * Scopes a value with a scope identity.
   *
   * This wraps a raw value `A` into a scoped value `A @@ S`. The scope tag `S`
   * is typically the path-dependent `Tag` type of a [[Scope]] instance.
   *
   * Zero overhead: since `@@` is an opaque type alias, this is an identity
   * operation at runtime.
   *
   * @param a
   *   the value to scope
   * @tparam A
   *   the value type
   * @tparam S
   *   the scope tag type
   * @return
   *   the scoped value
   */
  inline def scoped[A, S](a: A): A @@ S = a

  /**
   * Retrieves the underlying value without unscoping (internal use only).
   *
   * This is package-private to prevent bypassing the scope safety checks.
   * External code should use `$` or `get` with proper scope proof.
   */
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
     * Requires `Scope.Permit[S]` from a `.use` block.
     *
     * @param f
     *   The function to apply to the underlying value
     * @param permit
     *   Evidence that we have scope access
     * @param u
     *   Typeclass determining the result type
     * @return
     *   Either raw `B` or `B @@ S` depending on ScopeEscape instance
     */
    inline infix def $[B](inline f: A => B)(using
      permit: Scope.Permit[S]
    )(using
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
     * Requires `Scope.Permit[S]` from a `.use` block.
     *
     * @param permit
     *   Evidence that we have scope access
     * @param u
     *   Typeclass determining the result type
     * @return
     *   Either raw `A` or `A @@ S` depending on ScopeEscape instance
     */
    inline def get(using permit: Scope.Permit[S])(using u: ScopeEscape[A, S]): u.Out =
      u(scoped)

    /**
     * Maps over a scoped value, preserving the scope tag.
     *
     * The function `f` is applied to the underlying value, and the result is
     * wrapped with the same scope tag. This does not require the scope to be in
     * context.
     *
     * @example
     *   {{{
     *   val conn: Connection @@ S = ...
     *   val stmt: Statement @@ S = conn.map(_.createStatement())
     *   }}}
     *
     * @param f
     *   the function to apply to the underlying value
     * @tparam B
     *   the result type of the function
     * @return
     *   the result wrapped with the same scope tag
     */
    inline def map[B](inline f: A => B): B @@ S =
      f(scoped)

    /**
     * FlatMaps over a scoped value, combining scope tags via intersection.
     *
     * Enables for-comprehension syntax with scoped values. The resulting value
     * is tagged with the intersection of both scope tags, ensuring it can only
     * be used where both scopes are available.
     *
     * @example
     *   {{{
     *   val result: Result @@ (S & T) = for {
     *     a <- scopedA  // A @@ S
     *     b <- scopedB  // B @@ T
     *   } yield combine(a, b)
     *   }}}
     *
     * @param f
     *   function returning a scoped result
     * @tparam B
     *   the underlying result type
     * @tparam T
     *   the scope tag of the returned value
     * @return
     *   the result with combined scope tag `S & T`
     */
    inline def flatMap[B, T](inline f: A => B @@ T): B @@ (S & T) =
      f(scoped)

    /**
     * Extracts the first element of a scoped tuple.
     *
     * @example
     *   {{{
     *   val pair: (Int, String) @@ S = ...
     *   val first: Int @@ S = pair._1
     *   }}}
     *
     * @return
     *   the first element, still scoped with tag `S`
     */
    inline def _1[X, Y](using ev: A =:= (X, Y)): X @@ S =
      ev(scoped)._1

    /**
     * Extracts the second element of a scoped tuple.
     *
     * @example
     *   {{{
     *   val pair: (Int, String) @@ S = ...
     *   val second: String @@ S = pair._2
     *   }}}
     *
     * @return
     *   the second element, still scoped with tag `S`
     */
    inline def _2[X, Y](using ev: A =:= (X, Y)): Y @@ S =
      ev(scoped)._2
  }
}

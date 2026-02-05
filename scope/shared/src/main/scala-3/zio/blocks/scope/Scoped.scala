package zio.blocks.scope

import scala.util.NotGiven

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
     *   Either raw `B` or `B @@ S` depending on AutoUnscoped instance
     */
    inline infix def $[B](inline f: A => B)(using scope: Scope[?] { type Tag >: S })(using
      u: AutoUnscoped[B, S]
    ): u.Out =
      u(f(scoped))

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

/**
 * Marker typeclass for types that can escape a scope unscoped.
 *
 * Types with an `Unscoped` instance are considered "safe data" - they don't
 * hold resources and can be freely extracted from a scope without tracking.
 *
 * Primitives, strings, and collections of unscoped types are unscoped by
 * default. Resource types (streams, connections, handles) should NOT have
 * instances.
 *
 * @example
 *   {{{
 *   // Primitives escape freely
 *   val n: Int = stream $ (_.read())  // Int is Unscoped
 *
 *   // Resources stay scoped
 *   val body = request $ (_.body)     // InputStream @@ Tag (not raw InputStream)
 *   }}}
 */
trait Unscoped[A]

object Unscoped {
  // Primitives
  given Unscoped[Int]        = new Unscoped[Int] {}
  given Unscoped[Long]       = new Unscoped[Long] {}
  given Unscoped[Short]      = new Unscoped[Short] {}
  given Unscoped[Byte]       = new Unscoped[Byte] {}
  given Unscoped[Char]       = new Unscoped[Char] {}
  given Unscoped[Boolean]    = new Unscoped[Boolean] {}
  given Unscoped[Float]      = new Unscoped[Float] {}
  given Unscoped[Double]     = new Unscoped[Double] {}
  given Unscoped[Unit]       = new Unscoped[Unit] {}
  given Unscoped[String]     = new Unscoped[String] {}
  given Unscoped[BigInt]     = new Unscoped[BigInt] {}
  given Unscoped[BigDecimal] = new Unscoped[BigDecimal] {}

  // Collections of unscoped elements
  given [A: Unscoped]: Unscoped[Array[A]]  = new Unscoped[Array[A]] {}
  given [A: Unscoped]: Unscoped[List[A]]   = new Unscoped[List[A]] {}
  given [A: Unscoped]: Unscoped[Vector[A]] = new Unscoped[Vector[A]] {}
  given [A: Unscoped]: Unscoped[Set[A]]    = new Unscoped[Set[A]] {}
  given [A: Unscoped]: Unscoped[Option[A]] = new Unscoped[Option[A]] {}
  given [A: Unscoped]: Unscoped[Seq[A]]    = new Unscoped[Seq[A]] {}

  // Tuples of unscoped elements
  given [A: Unscoped, B: Unscoped]: Unscoped[(A, B)]                                 = new Unscoped[(A, B)] {}
  given [A: Unscoped, B: Unscoped, C: Unscoped]: Unscoped[(A, B, C)]                 = new Unscoped[(A, B, C)] {}
  given [A: Unscoped, B: Unscoped, C: Unscoped, D: Unscoped]: Unscoped[(A, B, C, D)] =
    new Unscoped[(A, B, C, D)] {}

  // Maps with unscoped keys and values
  given [K: Unscoped, V: Unscoped]: Unscoped[Map[K, V]] = new Unscoped[Map[K, V]] {}
}

/**
 * Typeclass that determines how a value is unscoped when extracted via `$`.
 *
 * If `A` has an [[Unscoped]] instance, `Out = A` (raw value). Otherwise, `Out
 * = A @@ S` (re-scoped with scope).
 *
 * This enables conditional unscoping: data types escape freely, resource types
 * stay tracked.
 */
trait AutoUnscoped[A, S] {
  type Out
  def apply(a: A): Out
}

object AutoUnscoped {

  /** Unscoped types escape as raw values. Zero overhead: identity function. */
  given unscoped[A, S](using Unscoped[A]): AutoUnscoped[A, S] with {
    type Out = A
    def apply(a: A): Out = a
  }

  /** Non-Unscoped types stay scoped. Zero overhead: opaque type alias. */
  given resourceful[A, S](using NotGiven[Unscoped[A]]): AutoUnscoped[A, S] with {
    type Out = A @@ S
    def apply(a: A): Out = @@.scoped(a)
  }
}

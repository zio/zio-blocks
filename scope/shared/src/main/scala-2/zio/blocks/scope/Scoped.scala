package zio.blocks.scope

import scala.language.experimental.macros
import scala.language.implicitConversions

/**
 * Companion object for the `@@` type providing scoping operations.
 */
object @@ {

  /** Scopes a value with a scope identity. */
  def scoped[A, S](a: A): A @@ S = ScopedModule.instance.scoped(a)

  /** Retrieves the underlying value without unscoping (internal use). */
  private[scope] def unscoped[A, S](scoped: A @@ S): A = ScopedModule.instance.unscoped(scoped)
}

/**
 * Implicit class providing operations on scoped values.
 *
 * @example
 *   {{{
 *   val stream: InputStream @@ scope.Tag = closeable.value
 *   stream.$(_.read())(closeable, implicitly)  // Returns Int (unscoped)
 *   stream.map(_.available)                     // Returns Int @@ scope.Tag
 *   }}}
 */
final class ScopedOps[A, S](private val scoped: A @@ S) extends AnyVal {

  /**
   * Applies a function to the scoped value within the scope context.
   *
   * The result type depends on whether `B` is [[Unscoped]]:
   *   - If `B` is `Unscoped`, returns raw `B`
   *   - Otherwise, returns `B @@ S` (stays scoped)
   *
   * This is a macro that verifies at compile time that the implicit scope's Tag
   * is a supertype of S, matching the Scala 3 constraint
   * `Scope[?] { type Tag >: S }`.
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
  def $[B](f: A => B)(implicit scope: Scope.Any, u: AutoUnscoped[B, S]): u.Out = macro ScopedMacros.dollarImpl[A, S, B]

  /**
   * Extracts the scoped value, auto-unscoping if the type is [[Unscoped]].
   *
   * Equivalent to `scoped $ identity`. The result type depends on whether `A`
   * is [[Unscoped]]:
   *   - If `A` is `Unscoped`, returns raw `A`
   *   - Otherwise, returns `A @@ S` (stays scoped)
   *
   * This is a macro that verifies at compile time that the implicit scope's Tag
   * is a supertype of S, matching the Scala 3 constraint
   * `Scope[?] { type Tag >: S }`.
   *
   * @param scope
   *   Evidence that the current scope encompasses tag `S`
   * @param u
   *   Typeclass determining the result type
   * @return
   *   Either raw `A` or `A @@ S` depending on AutoUnscoped instance
   */
  def get(implicit scope: Scope.Any, u: AutoUnscoped[A, S]): u.Out = macro ScopedMacros.getImpl[A, S]

  /**
   * Maps over a scoped value, preserving the tag.
   *
   * @param f
   *   The function to apply
   * @return
   *   Result with same tag
   */
  def map[B](f: A => B): B @@ S =
    @@.scoped(f(@@.unscoped(scoped)))

  /**
   * FlatMaps over a scoped value, widening to the outer scope.
   *
   * @param f
   *   Function returning a scoped result
   * @return
   *   Result with the wider tag T
   */
  def flatMap[B, T >: S](f: A => B @@ T): B @@ T =
    f(@@.unscoped(scoped))

  /** Extracts the first element of a scoped tuple. */
  def _1[X, Y](implicit ev: A =:= (X, Y)): X @@ S =
    @@.scoped(ev(@@.unscoped(scoped))._1)

  /** Extracts the second element of a scoped tuple. */
  def _2[X, Y](implicit ev: A =:= (X, Y)): Y @@ S =
    @@.scoped(ev(@@.unscoped(scoped))._2)
}

object ScopedOps {
  implicit def toScopedOps[A, S](scoped: A @@ S): ScopedOps[A, S] = new ScopedOps(scoped)
}

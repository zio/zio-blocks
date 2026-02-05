package zio.blocks.scope

import scala.annotation.unused
import scala.language.implicitConversions

/**
 * Module that provides opaque-like scoping for Scala 2.
 *
 * Uses the "module pattern" to emulate Scala 3's opaque types with zero runtime
 * overhead. The type `A @@ S` is a value of type `A` that is "locked" to a
 * scope with tag `S`. The abstract type hides all methods on `A`, so the only
 * way to use the value is through the `$` operator, which requires the matching
 * scope capability.
 */
private[scope] sealed trait ScopedModule {
  type @@[+A, +S]

  def scoped[A, S](a: A): A @@ S
  private[scope] def unscoped[A, S](scoped: A @@ S): A
}

private[scope] object ScopedModule {
  val instance: ScopedModule = new ScopedModule {
    type @@[+A, +S] = A

    def scoped[A, S](a: A): A @@ S                       = a
    private[scope] def unscoped[A, S](scoped: A @@ S): A = scoped
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
  implicit val unscopedInt: Unscoped[Int]               = new Unscoped[Int] {}
  implicit val unscopedLong: Unscoped[Long]             = new Unscoped[Long] {}
  implicit val unscopedShort: Unscoped[Short]           = new Unscoped[Short] {}
  implicit val unscopedByte: Unscoped[Byte]             = new Unscoped[Byte] {}
  implicit val unscopedChar: Unscoped[Char]             = new Unscoped[Char] {}
  implicit val unscopedBoolean: Unscoped[Boolean]       = new Unscoped[Boolean] {}
  implicit val unscopedFloat: Unscoped[Float]           = new Unscoped[Float] {}
  implicit val unscopedDouble: Unscoped[Double]         = new Unscoped[Double] {}
  implicit val unscopedUnit: Unscoped[Unit]             = new Unscoped[Unit] {}
  implicit val unscopedString: Unscoped[String]         = new Unscoped[String] {}
  implicit val unscopedBigInt: Unscoped[BigInt]         = new Unscoped[BigInt] {}
  implicit val unscopedBigDecimal: Unscoped[BigDecimal] = new Unscoped[BigDecimal] {}

  // Collections of unscoped elements
  implicit def unscopedArray[A: Unscoped]: Unscoped[Array[A]]   = new Unscoped[Array[A]] {}
  implicit def unscopedList[A: Unscoped]: Unscoped[List[A]]     = new Unscoped[List[A]] {}
  implicit def unscopedVector[A: Unscoped]: Unscoped[Vector[A]] = new Unscoped[Vector[A]] {}
  implicit def unscopedSet[A: Unscoped]: Unscoped[Set[A]]       = new Unscoped[Set[A]] {}
  implicit def unscopedOption[A: Unscoped]: Unscoped[Option[A]] = new Unscoped[Option[A]] {}
  implicit def unscopedSeq[A: Unscoped]: Unscoped[Seq[A]]       = new Unscoped[Seq[A]] {}

  // Tuples of unscoped elements
  implicit def unscopedTuple2[A: Unscoped, B: Unscoped]: Unscoped[(A, B)] =
    new Unscoped[(A, B)] {}
  implicit def unscopedTuple3[A: Unscoped, B: Unscoped, C: Unscoped]: Unscoped[(A, B, C)] =
    new Unscoped[(A, B, C)] {}
  implicit def unscopedTuple4[A: Unscoped, B: Unscoped, C: Unscoped, D: Unscoped]: Unscoped[(A, B, C, D)] =
    new Unscoped[(A, B, C, D)] {}

  // Maps with unscoped keys and values
  implicit def unscopedMap[K: Unscoped, V: Unscoped]: Unscoped[Map[K, V]] = new Unscoped[Map[K, V]] {}
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

object AutoUnscoped extends AutoUnscopedLowPriority {
  type Aux[A, S, O] = AutoUnscoped[A, S] { type Out = O }

  /** Unscoped types escape as raw values. Zero overhead: identity function. */
  implicit def unscoped[A, S](implicit ev: Unscoped[A]): AutoUnscoped.Aux[A, S, A] =
    new AutoUnscoped[A, S] {
      type Out = A
      def apply(a: A): Out = a
    }
}

trait AutoUnscopedLowPriority {

  /** Non-Unscoped types stay scoped. Zero overhead: opaque type alias. */
  implicit def resourceful[A, S]: AutoUnscoped.Aux[A, S, A @@ S] =
    new AutoUnscoped[A, S] {
      type Out = A @@ S
      def apply(a: A): Out = @@.scoped(a)
    }
}

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
   * @param f
   *   The function to apply to the underlying value
   * @param scope
   *   Evidence that the scope is available (compile-time check)
   * @param u
   *   Typeclass determining the result type
   * @return
   *   Either raw `B` or `B @@ S` depending on AutoUnscoped instance
   */
  def $[B](f: A => B)(implicit @unused scope: Scope.Any, u: AutoUnscoped[B, S]): u.Out =
    u(f(@@.unscoped(scoped)))

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

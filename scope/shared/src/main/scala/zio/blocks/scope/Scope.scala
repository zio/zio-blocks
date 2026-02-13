package zio.blocks.scope

import scala.language.implicitConversions
import zio.blocks.chunk.Chunk
import zio.blocks.scope.internal.Finalizers

/**
 * A scope that manages resource lifecycle with compile-time verified safety.
 *
 * ==Closed-scope defense==
 *
 * If a scope reference escapes to another thread (e.g. via a `Future`) and the
 * original scope closes while the other thread still holds a reference, all
 * scope operations (`$`, `use`, `map`, `flatMap`, `allocate`) become no-ops
 * that return `null` (wrapped as `$[B]`). This prevents the escaped thread from
 * interacting with already-released resources, but callers should be aware that
 * `null` values may appear if scopes are used across thread boundaries
 * incorrectly. `defer` on a closed scope is silently ignored, and `scoped`
 * creates a born-closed child.
 */
sealed abstract class Scope extends Finalizer with ScopeVersionSpecific { self =>

  /**
   * The scoped value type - unique to each scope instance. Zero-cost: $[A] = A.
   */
  type $[+A]

  /** Parent scope type. */
  type Parent <: Scope

  /** Reference to parent scope. */
  val parent: Parent

  /** Wrap a value (identity - zero cost). */
  protected def $wrap[A](a: A): $[A]

  /** Unwrap a value (identity - zero cost). */
  protected def $unwrap[A](sa: $[A]): A

  /** Create a scoped value from a raw value (zero-cost). */
  def $[A](a: A): $[A] =
    if (isClosed) $wrap(null.asInstanceOf[A])
    else $wrap(a)

  /**
   * Force evaluation of a scoped value. Package-private - UNSOUND if exposed.
   */
  private[scope] def $run[A](sa: $[A]): A = $unwrap(sa)

  /**
   * Lower a parent-scoped value into this scope. Safe because parent outlives
   * child.
   */
  final def lower[A](value: parent.$[A]): $[A] =
    value.asInstanceOf[$[A]]

  // Resource allocation - abstract, implemented by Child and global
  protected def finalizers: Finalizers

  /** Returns true if this scope has been closed (finalizers already ran). */
  def isClosed: Boolean = finalizers.isClosed

  /** Allocate a resource in this scope. Returns null-scoped if closed. */
  def allocate[A](resource: Resource[A]): $[A] =
    if (isClosed) $wrap(null.asInstanceOf[A])
    else {
      val value = resource.make(this)
      $wrap(value)
    }

  /** Allocate an AutoCloseable directly. Returns null-scoped if closed. */
  def allocate[A <: AutoCloseable](value: => A): $[A] =
    allocate(Resource(value))

  /**
   * Register a finalizer to run when scope closes (no-op if already closed).
   */
  def defer(f: => Unit): Unit = finalizers.add(f)

  /** Apply a function to a scoped value. Returns null-scoped if closed. */
  def use[A, B](scoped: $[A])(f: A => B): $[B] =
    if (isClosed) $wrap(null.asInstanceOf[B])
    else $wrap(f($unwrap(scoped)))

  /** Apply a function to two scoped values. Returns null-scoped if closed. */
  def use[A1, A2, B](s1: $[A1], s2: $[A2])(f: (A1, A2) => B): $[B] =
    if (isClosed) $wrap(null.asInstanceOf[B])
    else $wrap(f($unwrap(s1), $unwrap(s2)))

  /** Apply a function to three scoped values. Returns null-scoped if closed. */
  def use[A1, A2, A3, B](s1: $[A1], s2: $[A2], s3: $[A3])(f: (A1, A2, A3) => B): $[B] =
    if (isClosed) $wrap(null.asInstanceOf[B])
    else $wrap(f($unwrap(s1), $unwrap(s2), $unwrap(s3)))

  /** Apply a function to four scoped values. Returns null-scoped if closed. */
  def use[A1, A2, A3, A4, B](s1: $[A1], s2: $[A2], s3: $[A3], s4: $[A4])(
    f: (A1, A2, A3, A4) => B
  ): $[B] =
    if (isClosed) $wrap(null.asInstanceOf[B])
    else $wrap(f($unwrap(s1), $unwrap(s2), $unwrap(s3), $unwrap(s4)))

  /** Apply a function to five scoped values. Returns null-scoped if closed. */
  def use[A1, A2, A3, A4, A5, B](s1: $[A1], s2: $[A2], s3: $[A3], s4: $[A4], s5: $[A5])(
    f: (A1, A2, A3, A4, A5) => B
  ): $[B] =
    if (isClosed) $wrap(null.asInstanceOf[B])
    else $wrap(f($unwrap(s1), $unwrap(s2), $unwrap(s3), $unwrap(s4), $unwrap(s5)))

  /**
   * Implicit ops for map/flatMap on scoped values. Guarded against closed
   * scope.
   */
  implicit class ScopedOps[A](private val sa: $[A]) {
    def map[B](f: A => B): $[B] =
      if (isClosed) $wrap(null.asInstanceOf[B])
      else $wrap(f($unwrap(sa)))

    def flatMap[B](f: A => $[B]): $[B] =
      if (isClosed) $wrap(null.asInstanceOf[B])
      else f($unwrap(sa))
  }

  /**
   * Implicit conversion: wrap Unscoped values so they can be returned from
   * scoped blocks.
   */
  implicit def wrapUnscoped[A](a: A)(implicit ev: Unscoped[A]): $[A] = $(a)
}

object Scope {

  /** Global scope - self-referential, never closes. $[A] = A (zero-cost). */
  object global extends Scope { self =>
    type $[+A]  = A
    type Parent = global.type
    val parent: Parent = this

    protected def $wrap[A](a: A): $[A]    = a
    protected def $unwrap[A](sa: $[A]): A = sa

    protected val finalizers: Finalizers = {
      val f = new Finalizers
      PlatformScope.registerShutdownHook { () =>
        val errors = f.runAll()
        if (errors.nonEmpty) {
          val first = errors.head
          errors.tail.foreach(first.addSuppressed)
          throw first
        }
      }
      f
    }

    private[scope] def runFinalizers(): Chunk[Throwable] = finalizers.runAll()
  }

  /** Child scope - created by scoped { ... }. */
  final class Child[P <: Scope] private[scope] (
    val parent: P,
    protected val finalizers: Finalizers
  ) extends Scope { self =>
    type Parent = P

    def close(): Chunk[Throwable] = finalizers.runAll()

    // $[A] type and $wrap/$unwrap are version-specific
    // Scala 3: opaque type $[+A] = A
    // Scala 2: module pattern
    //
    // For cross-compilation, we use asInstanceOf which is sound
    // because $[A] = A at runtime for both Scala 2 and 3
    type $[+A]
    protected def $wrap[A](a: A): $[A]    = a.asInstanceOf[$[A]]
    protected def $unwrap[A](sa: $[A]): A = sa.asInstanceOf[A]
  }
}

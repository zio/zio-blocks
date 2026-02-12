package zio.blocks.scope

import scala.language.implicitConversions
import zio.blocks.chunk.Chunk
import zio.blocks.scope.internal.Finalizers

/**
 * A scope that manages resource lifecycle with compile-time verified safety.
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
  def $[A](a: A): $[A] = $wrap(a)

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

  /** Allocate a resource in this scope. */
  def allocate[A](resource: Resource[A]): $[A] = {
    val value = resource.make(this)
    $wrap(value)
  }

  /** Allocate an AutoCloseable directly. */
  def allocate[A <: AutoCloseable](value: => A): $[A] =
    allocate(Resource(value))

  /** Register a finalizer to run when scope closes. */
  def defer(f: => Unit): Unit = finalizers.add(f)

  /** Apply a function to a scoped value. Always eager (zero-cost). */
  def use[A, B](scoped: $[A])(f: A => B): $[B] =
    $wrap(f($unwrap(scoped)))

  /** Implicit ops for map/flatMap on scoped values. All eager (zero-cost). */
  implicit class ScopedOps[A](private val sa: $[A]) {
    def map[B](f: A => B): $[B] =
      $wrap(f($unwrap(sa)))

    def flatMap[B](f: A => $[B]): $[B] =
      f($unwrap(sa))
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

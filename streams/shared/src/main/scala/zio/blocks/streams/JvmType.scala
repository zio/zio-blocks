package zio.blocks.streams

/**
 * Tag identifying which JVM type `A` corresponds to: one of the 8 primitives,
 * or `AnyRef` for reference types.
 *
 * Used throughout the streaming library for runtime specialization dispatch:
 *   - [[zio.blocks.streams.io.Reader]] checks `jvmType` to select
 *     sentinel-return pull paths (`readInt`, `readLong`, etc.).
 *   - [[zio.blocks.streams.Sink]] dispatches on `jvmType` to avoid boxing in
 *     drain, fold, and collect loops.
 *
 * Identity comparison (`eq`) is safe because each variant is a singleton
 * `case object`.
 *
 * @param ordinal
 *   Stable integer identifier for serialization or indexing.
 */
sealed abstract class JvmType(val ordinal: scala.Int)

/**
 * Companion enumerating all 8 JVM primitive types plus the `AnyRef` fallback
 * for reference types. Also contains [[JvmType.Infer]], the type-class that
 * maps Scala types to their `JvmType` at compile time.
 */
object JvmType {

  /** Scala `Int` / Java `int`. */
  case object Int extends JvmType(0)

  /** Scala `Long` / Java `long`. */
  case object Long extends JvmType(1)

  /** Scala `Double` / Java `double`. */
  case object Double extends JvmType(2)

  /** Scala `Float` / Java `float`. */
  case object Float extends JvmType(3)

  /** Scala `Byte` / Java `byte`. */
  case object Byte extends JvmType(4)

  /** Scala `Short` / Java `short`. */
  case object Short extends JvmType(5)

  /** Scala `Char` / Java `char`. */
  case object Char extends JvmType(6)

  /** Scala `Boolean` / Java `boolean`. */
  case object Boolean extends JvmType(7)

  /** Any reference type (String, case classes, etc.). */
  case object AnyRef extends JvmType(8)

  private val byOrdinal: Array[JvmType] =
    Array(Int, Long, Double, Float, Byte, Short, Char, Boolean, AnyRef)

  /** Returns the [[JvmType]] for the given ordinal index (0–8). */
  def fromOrdinal(i: scala.Int): JvmType = byOrdinal(i)

  // --------------------------------------------------------------------------
  //  Infer — type class mapping Scala types to JvmType at compile time
  // --------------------------------------------------------------------------

  /**
   * A type class that records at compile time which JVM type `A` maps to.
   *
   * Contravariant in `A`: if you need `Infer[Dog]`, an `Infer[Animal]` (which
   * resolves to `AnyRef`) is acceptable. This contravariance allows `Infer` to
   * appear as an implicit parameter on covariant types like `Stream[+E, +A]`
   * without variance conflicts.
   *
   * Adding `(implicit jt: JvmType.Infer[A])` to a method is always
   * source-compatible — it never fails to resolve.
   */
  sealed trait Infer[-A] {

    /** The [[JvmType]] tag for `A`, or `AnyRef` for reference types. */
    def jvmType: JvmType

    /** Convenience check: `true` when `A` is `Byte`. */
    final def isByte: scala.Boolean = jvmType eq JvmType.Byte
  }

  /**
   * Implicit instances for all 8 JVM primitive types and a low-priority
   * fallback.
   */
  object Infer extends LowPriorityJvmTypeInfer {
    implicit val int: Infer[scala.Int]         = new Infer[scala.Int] { def jvmType = JvmType.Int }
    implicit val long: Infer[scala.Long]       = new Infer[scala.Long] { def jvmType = JvmType.Long }
    implicit val double: Infer[scala.Double]   = new Infer[scala.Double] { def jvmType = JvmType.Double }
    implicit val float: Infer[scala.Float]     = new Infer[scala.Float] { def jvmType = JvmType.Float }
    implicit val byte: Infer[scala.Byte]       = new Infer[scala.Byte] { def jvmType = JvmType.Byte }
    implicit val short: Infer[scala.Short]     = new Infer[scala.Short] { def jvmType = JvmType.Short }
    implicit val char: Infer[scala.Char]       = new Infer[scala.Char] { def jvmType = JvmType.Char }
    implicit val boolean: Infer[scala.Boolean] = new Infer[scala.Boolean] { def jvmType = JvmType.Boolean }
  }

  /**
   * Low-priority fallback: resolves for any type `A` not covered by the 8
   * explicit primitive instances, returning `JvmType.AnyRef`.
   */
  private[streams] sealed abstract class LowPriorityJvmTypeInfer {
    private val _anyRef: Infer[Any] = new Infer[Any] { def jvmType = JvmType.AnyRef }

    /** Fallback: resolves for any type `A`, returning `AnyRef`. */
    implicit def anyRef[A]: Infer[A] = _anyRef.asInstanceOf[Infer[A]]
  }
}

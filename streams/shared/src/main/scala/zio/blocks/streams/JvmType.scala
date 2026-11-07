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

package zio.blocks.streams

/**
 * The lane an element type travels on: one of the eight JVM primitive types, or
 * `AnyRef` for every other type, whose values travel boxed.
 *
 * Readers and writers report their lane through `jvmType`, and streams, sinks
 * and pipelines find it through [[JvmType.Infer]]; the engine dispatches on it
 * to move primitive values without boxing, for example through `Reader#readInt`
 * on the `Int` lane. Each lane is a `case object`, so comparing lanes with `eq`
 * is safe.
 *
 * @param ordinal
 *   The lane's index, from 0 (`Int`) to 8 (`AnyRef`), in the order the
 *   companion declares them; [[JvmType.fromOrdinal]] maps it back.
 */
sealed abstract class JvmType(val ordinal: scala.Int)

/**
 * The nine lanes: the eight JVM primitive types and `AnyRef`. Also holds
 * [[JvmType.Infer]], the type class that finds the lane of a type at compile
 * time.
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

  /**
   * Every type that is not one of the eight primitives (`String`, case classes,
   * and so on), and any type the compiler cannot see as a primitive, such as an
   * abstract type parameter.
   */
  case object AnyRef extends JvmType(8)

  private val byOrdinal: Array[JvmType] =
    Array(Int, Long, Double, Float, Byte, Short, Char, Boolean, AnyRef)

  /**
   * Returns the lane whose [[JvmType#ordinal]] is `i`. Throws
   * `ArrayIndexOutOfBoundsException` when `i` is outside 0 to 8.
   */
  def fromOrdinal(i: scala.Int): JvmType = byOrdinal(i)

  // --------------------------------------------------------------------------
  //  Infer — type class mapping Scala types to JvmType at compile time
  // --------------------------------------------------------------------------

  /**
   * A type class that records at compile time which lane `A` travels on.
   *
   * An instance exists for every type: the eight primitives have their own, and
   * every other type gets the low-priority `AnyRef` fallback, so an
   * `(implicit jt: JvmType.Infer[A])` parameter always resolves. Resolution
   * happens where `A` is known: inside generic code, where `A` is an abstract
   * type parameter, it finds the `AnyRef` fallback even when the caller later
   * passes an `Int`, unless that code takes its own `Infer[A]` parameter.
   *
   * `Infer` is invariant in `A`, so the instance for one primitive never stands
   * in for another type's.
   */
  sealed trait Infer[A] {

    /** The lane of `A`: its primitive lane, or `AnyRef`. */
    def jvmType: JvmType

    /** Returns `true` when `A` is on the `Byte` lane. */
    final def isByte: scala.Boolean = jvmType eq JvmType.Byte
  }

  /**
   * The instances for the eight primitive types and `Nothing`, plus the
   * low-priority `AnyRef` fallback for every other type.
   */
  object Infer extends LowPriorityJvmTypeInferPlatform {

    /** `Nothing` has no values to carry, so its lane is `AnyRef`. */
    implicit val nothing: Infer[Nothing] = boxed[Nothing]

    /** The `Int` lane. */
    implicit val int: Infer[scala.Int] = new Infer[scala.Int] { def jvmType = JvmType.Int }

    /** The `Long` lane. */
    implicit val long: Infer[scala.Long] = new Infer[scala.Long] { def jvmType = JvmType.Long }

    /** The `Double` lane. */
    implicit val double: Infer[scala.Double] = new Infer[scala.Double] { def jvmType = JvmType.Double }

    /** The `Float` lane. */
    implicit val float: Infer[scala.Float] = new Infer[scala.Float] { def jvmType = JvmType.Float }

    /** The `Byte` lane. */
    implicit val byte: Infer[scala.Byte] = new Infer[scala.Byte] { def jvmType = JvmType.Byte }

    /** The `Short` lane. */
    implicit val short: Infer[scala.Short] = new Infer[scala.Short] { def jvmType = JvmType.Short }

    /** The `Char` lane. */
    implicit val char: Infer[scala.Char] = new Infer[scala.Char] { def jvmType = JvmType.Char }

    /** The `Boolean` lane. */
    implicit val boolean: Infer[scala.Boolean] = new Infer[scala.Boolean] { def jvmType = JvmType.Boolean }

    /**
     * Returns an instance on the `AnyRef` lane for any `A`, for code that
     * passes an `Infer` explicitly and wants the boxed lane.
     */
    def boxed[A]: Infer[A] = new Infer[A] { def jvmType = JvmType.AnyRef }
  }

}

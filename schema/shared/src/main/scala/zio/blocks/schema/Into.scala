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

package zio.blocks.schema

import scala.collection.Factory

/**
 * An `Into[A, B]` converts a value of type `A` into a value of type `B`, or
 * fails with a [[SchemaError]]. Use it to move data from one version of a type
 * to the next, or from an external DTO to a validated domain type, without
 * writing the conversion by hand.
 *
 * The companion provides instances for numeric widening and narrowing, and for
 * `Option`, `Either`, `Map`, collections and arrays whose elements convert.
 * [[Into.derived]] generates a conversion at compile time between case classes,
 * tuples, sealed traits and Scala 3 enums, and to or from [[DynamicValue]]. For
 * a conversion in both directions, use [[As]].
 *
 * {{{
 * case class BookV1(title: String, pages: Int)
 * case class BookV2(title: String, pages: Long, subtitle: Option[String])
 *
 * Into.derived[BookV1, BookV2].into(BookV1("Zionomicon", 600))
 * // Right(BookV2("Zionomicon", 600L, None))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/schema-evolution/into/ Into]]
 */
trait Into[-A, +B] {

  /**
   * Converts `a`, or returns `Left` with the reasons it cannot be represented
   * as a `B`.
   *
   * A derived conversion checks every field and returns all failures in one
   * [[SchemaError]]. Each error's path is the root, so the field appears only
   * in the message, and Scala 2 and Scala 3 list the errors in opposite orders
   * (see `docs/dev/issues.md`).
   */
  def into(a: A): Either[SchemaError, B]
}

/**
 * Built-in [[Into]] instances and the [[Into.derived]] macro. The instances are
 * found without an import.
 */
object Into extends IntoVersionSpecific with IntoPrimitiveInstances with IntoContainerInstances {

  /** Returns the implicit `Into[A, B]` in scope. */
  def apply[A, B](implicit ev: Into[A, B]): Into[A, B] = ev

  /**
   * Returns every value unchanged, as a `Right`. The compiler finds it for any
   * `Into[A, A]`.
   */
  implicit def identity[A]: Into[A, A] = (a: A) => new Right(a)
}

/**
 * Conversions between the numeric primitive types. A widening conversion always
 * succeeds. A narrowing conversion checks the value at run time and returns a
 * `Left` holding a [[SchemaError.ConversionFailed]] when the value does not
 * fit.
 */
trait IntoPrimitiveInstances {

  // === Numeric Widening (Lossless) ===

  /** Widens a `Byte` to a `Short`. Always succeeds. */
  implicit val byteToShort: Into[Byte, Short] = (a: Byte) => new Right(a.toShort)

  /** Widens a `Byte` to an `Int`. Always succeeds. */
  implicit val byteToInt: Into[Byte, Int] = (a: Byte) => new Right(a.toInt)

  /** Widens a `Byte` to a `Long`. Always succeeds. */
  implicit val byteToLong: Into[Byte, Long] = (a: Byte) => new Right(a.toLong)

  /** Widens a `Byte` to a `Float`. Always succeeds. */
  implicit val byteToFloat: Into[Byte, Float] = (a: Byte) => new Right(a.toFloat)

  /** Widens a `Byte` to a `Double`. Always succeeds. */
  implicit val byteToDouble: Into[Byte, Double] = (a: Byte) => new Right(a.toDouble)

  /** Widens a `Short` to an `Int`. Always succeeds. */
  implicit val shortToInt: Into[Short, Int] = (a: Short) => new Right(a.toInt)

  /** Widens a `Short` to a `Long`. Always succeeds. */
  implicit val shortToLong: Into[Short, Long] = (a: Short) => new Right(a.toLong)

  /** Widens a `Short` to a `Float`. Always succeeds. */
  implicit val shortToFloat: Into[Short, Float] = (a: Short) => new Right(a.toFloat)

  /** Widens a `Short` to a `Double`. Always succeeds. */
  implicit val shortToDouble: Into[Short, Double] = (a: Short) => new Right(a.toDouble)

  /** Widens an `Int` to a `Long`. Always succeeds. */
  implicit val intToLong: Into[Int, Long] = (a: Int) => new Right(a.toLong)

  /**
   * Converts an `Int` to a `Float`. Always succeeds, but a value beyond `2^24`
   * in magnitude may be rounded to the nearest `Float`: `16777217` becomes
   * `1.6777216E7`.
   */
  implicit val intToFloat: Into[Int, Float] = (a: Int) => new Right(a.toFloat)

  /** Widens an `Int` to a `Double`. Always succeeds. */
  implicit val intToDouble: Into[Int, Double] = (a: Int) => new Right(a.toDouble)

  /**
   * Converts a `Long` to a `Float`. Always succeeds, but a value beyond `2^24`
   * in magnitude may be rounded to the nearest `Float`.
   */
  implicit val longToFloat: Into[Long, Float] = (a: Long) => new Right(a.toFloat)

  /**
   * Converts a `Long` to a `Double`. Always succeeds, but a value beyond `2^53`
   * in magnitude may be rounded to the nearest `Double`.
   */
  implicit val longToDouble: Into[Long, Double] = (a: Long) => new Right(a.toDouble)

  /** Widens a `Float` to a `Double`. Always succeeds. */
  implicit val floatToDouble: Into[Float, Double] = (a: Float) => new Right(a.toDouble)

  // === Numeric Narrowing (with Runtime Validation) ===

  /**
   * Narrows a `Short` to a `Byte`, failing when the value is outside
   * `[-128, 127]`.
   */
  implicit val shortToByte: Into[Short, Byte] = (a: Short) =>
    if (a >= Byte.MinValue && a <= Byte.MaxValue) new Right(a.toByte)
    else
      new Left(
        SchemaError.conversionFailed(Nil, s"Value $a is out of range for Byte [${Byte.MinValue}, ${Byte.MaxValue}]")
      )

  /**
   * Narrows an `Int` to a `Byte`, failing when the value is outside
   * `[-128, 127]`.
   */
  implicit val intToByte: Into[Int, Byte] = (a: Int) =>
    if (a >= Byte.MinValue && a <= Byte.MaxValue) new Right(a.toByte)
    else
      new Left(
        SchemaError.conversionFailed(Nil, s"Value $a is out of range for Byte [${Byte.MinValue}, ${Byte.MaxValue}]")
      )

  /**
   * Narrows an `Int` to a `Short`, failing when the value is outside
   * `[-32768, 32767]`.
   */
  implicit val intToShort: Into[Int, Short] = (a: Int) =>
    if (a >= Short.MinValue && a <= Short.MaxValue) new Right(a.toShort)
    else
      new Left(
        SchemaError.conversionFailed(Nil, s"Value $a is out of range for Short [${Short.MinValue}, ${Short.MaxValue}]")
      )

  /**
   * Narrows a `Long` to a `Byte`, failing when the value is outside
   * `[-128, 127]`.
   */
  implicit val longToByte: Into[Long, Byte] = (a: Long) =>
    if (a >= Byte.MinValue && a <= Byte.MaxValue) new Right(a.toByte)
    else
      new Left(
        SchemaError.conversionFailed(Nil, s"Value $a is out of range for Byte [${Byte.MinValue}, ${Byte.MaxValue}]")
      )

  /**
   * Narrows a `Long` to a `Short`, failing when the value is outside
   * `[-32768, 32767]`.
   */
  implicit val longToShort: Into[Long, Short] = (a: Long) =>
    if (a >= Short.MinValue && a <= Short.MaxValue) new Right(a.toShort)
    else
      new Left(
        SchemaError.conversionFailed(Nil, s"Value $a is out of range for Short [${Short.MinValue}, ${Short.MaxValue}]")
      )

  /**
   * Narrows a `Long` to an `Int`, failing when the value is outside the `Int`
   * range.
   */
  implicit val longToInt: Into[Long, Int] = (a: Long) =>
    if (a >= Int.MinValue && a <= Int.MaxValue) new Right(a.toInt)
    else
      new Left(
        SchemaError.conversionFailed(Nil, s"Value $a is out of range for Int [${Int.MinValue}, ${Int.MaxValue}]")
      )

  /**
   * Narrows a `Double` to a `Float`, failing for `NaN`, the infinities and
   * values beyond `Float.MaxValue` in magnitude. It checks only the range: a
   * value inside it is rounded to the nearest `Float`, so `1e-50` becomes
   * `0.0f`.
   */
  implicit val doubleToFloat: Into[Double, Float] = (a: Double) =>
    if (a >= -Float.MaxValue && a <= Float.MaxValue) new Right(a.toFloat)
    else new Left(SchemaError.conversionFailed(Nil, s"Value $a is out of range for Float"))

  /**
   * Converts a `Float` to an `Int`, failing unless the value is a whole number
   * in the `Int` range (so `NaN` and the infinities fail).
   */
  implicit val floatToInt: Into[Float, Int] = (a: Float) =>
    if (a >= Int.MinValue && a <= Int.MaxValue && a == a.toInt.toFloat) new Right(a.toInt)
    else new Left(SchemaError.conversionFailed(Nil, s"Value $a cannot be precisely converted to Int"))

  /**
   * Converts a `Float` to a `Long`, failing unless the value is a whole number
   * in the `Long` range (so `NaN` and the infinities fail).
   */
  implicit val floatToLong: Into[Float, Long] = (a: Float) =>
    if (a >= Long.MinValue && a <= Long.MaxValue && a == a.toLong.toFloat) new Right(a.toLong)
    else new Left(SchemaError.conversionFailed(Nil, s"Value $a cannot be precisely converted to Long"))

  /**
   * Converts a `Double` to an `Int`, failing unless the value is a whole number
   * in the `Int` range (so `NaN` and the infinities fail).
   */
  implicit val doubleToInt: Into[Double, Int] = (a: Double) =>
    if (a >= Int.MinValue && a <= Int.MaxValue && a == a.toInt.toDouble) new Right(a.toInt)
    else new Left(SchemaError.conversionFailed(Nil, s"Value $a cannot be precisely converted to Int"))

  /**
   * Converts a `Double` to a `Long`, failing unless the value is a whole number
   * in the `Long` range (so `NaN` and the infinities fail).
   */
  implicit val doubleToLong: Into[Double, Long] = (a: Double) =>
    if (a >= Long.MinValue && a <= Long.MaxValue && a == a.toLong.toDouble) new Right(a.toLong)
    else new Left(SchemaError.conversionFailed(Nil, s"Value $a cannot be precisely converted to Long"))
}

/**
 * Conversions that lift element conversions over `Option`, `Either`, `Map`,
 * collections and arrays. The collection and array conversions convert every
 * element and, when some fail, return all their errors combined in one
 * [[SchemaError]].
 */
trait IntoContainerInstances {

  /** Converts the value inside a `Some` with `into`, and keeps `None`. */
  implicit def optionInto[A, B](implicit into: Into[A, B]): Into[Option[A], Option[B]] = {
    case Some(value) => into.into(value).map(new Some(_))
    case None        => new Right(None)
  }

  /**
   * Converts the value inside a `Left` with `leftInto` and the value inside a
   * `Right` with `rightInto`.
   */
  implicit def eitherInto[L1, R1, L2, R2](implicit
    leftInto: Into[L1, L2],
    rightInto: Into[R1, R2]
  ): Into[Either[L1, R1], Either[L2, R2]] = {
    case Left(l)  => leftInto.into(l).map(new Left(_))
    case Right(r) => rightInto.into(r).map(new Right(_))
  }

  /**
   * Converts every key with `keyInto` and every value with `valueInto`,
   * returning the errors of all failing entries. For an entry whose key fails,
   * the value is not converted. Keys that become equal after conversion
   * collapse into one entry, the last one winning.
   */
  implicit def mapInto[K1, V1, K2, V2](implicit
    keyInto: Into[K1, K2],
    valueInto: Into[V1, V2]
  ): Into[Map[K1, V1], Map[K2, V2]] = { (a: Map[K1, V1]) =>
    val results = a.toList.map { case (k, v) =>
      for {
        k2 <- keyInto.into(k)
        v2 <- valueInto.into(v)
      } yield (k2, v2)
    }
    sequence(results).map(_.toMap)
  }

  /**
   * Converts every element of a collection with `intoAB` and builds the target
   * collection with `factory`, so the collection kind can change: `List` to
   * `Vector`, or to `Set`, which drops duplicates.
   */
  implicit def iterableInto[A, B, F1[X] <: Iterable[X], F2[_]](implicit
    intoAB: Into[A, B],
    factory: Factory[B, F2[B]]
  ): Into[F1[A], F2[B]] = { (a: F1[A]) =>
    val results = a.map(intoAB.into).toList
    sequence(results).map { list =>
      val builder = factory.newBuilder
      builder ++= list
      builder.result()
    }
  }

  /**
   * Converts every element of an array with `intoAB` and builds the target
   * collection with `factory`.
   */
  implicit def arrayToIterable[A, B, F[_]](implicit
    intoAB: Into[A, B],
    factory: Factory[B, F[B]]
  ): Into[Array[A], F[B]] = { (a: Array[A]) =>
    val results = a.map(intoAB.into).toList
    sequence(results).map { list =>
      val builder = factory.newBuilder
      builder ++= list
      builder.result()
    }
  }

  /** Converts every element of a collection with `intoAB` into an array. */
  implicit def iterableToArray[A, B, F[X] <: Iterable[X]](implicit
    intoAB: Into[A, B],
    ct: scala.reflect.ClassTag[B]
  ): Into[F[A], Array[B]] = { (a: F[A]) =>
    val results = a.map(intoAB.into).toList
    sequence(results).map(_.toArray)
  }

  /** Converts every element of an array with `intoAB` into a new array. */
  implicit def arrayToArray[A, B](implicit
    intoAB: Into[A, B],
    ct: scala.reflect.ClassTag[B]
  ): Into[Array[A], Array[B]] = { (a: Array[A]) =>
    val results = a.map(intoAB.into).toList
    sequence(results).map(_.toArray)
  }

  /**
   * Collects the values of `list`, or returns all its errors combined into one.
   */
  protected def sequence[A](list: List[Either[SchemaError, A]]): Either[SchemaError, List[A]] = {
    val successBuilder = List.newBuilder[A]
    val errorBuilder   = List.newBuilder[SchemaError]
    val iter           = list.iterator
    while (iter.hasNext) {
      iter.next() match {
        case Right(a) => successBuilder.addOne(a)
        case Left(e)  => errorBuilder.addOne(e)
      }
    }
    val errors = errorBuilder.result()
    if (errors.isEmpty) new Right(successBuilder.result())
    else new Left(errors.reduceLeft(_ ++ _))
  }
}

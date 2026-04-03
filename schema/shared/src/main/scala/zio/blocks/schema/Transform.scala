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

/**
 * A typed, serializable transformation from type `A` to type `B`.
 *
 * `Transform[A, B]` provides a type-safe wrapper around [[DynamicTransform]]
 * while maintaining serializability. Unlike functions `A => B`, transforms
 * can be stored in registries, serialized, and applied dynamically.
 *
 * @tparam A the source type
 * @tparam B the target type
 */
final case class Transform[A, B] private (
  dynamic: DynamicTransform,
  sourceType: Reflect.Bound[A],
  targetType: Reflect.Bound[B]
) { self =>

  /**
   * Applies this transformation to a value of type `A`.
   */
  def apply(value: A): Either[MigrationError, B] = {
    val dynamicValue = sourceType.toDynamicValue(value)
    dynamic(dynamicValue) match {
      case Right(transformed) =>
        targetType.fromDynamicValue(transformed) match {
          case Right(b) => Right(b)
          case Left(err) => Left(MigrationError.TransformFailed("", err.message))
        }
      case Left(err) => Left(err)
    }
  }

  /**
   * Composes this transform with another.
   */
  def andThen[C](that: Transform[B, C]): Transform[A, C] =
    Transform(
      DynamicTransform.Compose(dynamic, that.dynamic),
      sourceType,
      that.targetType
    )

  /**
   * Alias for `andThen`.
   */
  def >>>[C](that: Transform[B, C]): Transform[A, C] = andThen(that)

  /**
   * Returns the structural reverse of this transformation.
   */
  def reverse: Transform[B, A] =
    Transform(dynamic.reverse, targetType, sourceType)

  /**
   * Converts this typed transform to its underlying dynamic representation.
   */
  def toDynamic: DynamicTransform = dynamic
}

object Transform {

  /**
   * Creates a Transform from a DynamicTransform with explicit reflect types.
   */
  def fromDynamic[A, B](
    dynamic: DynamicTransform,
    sourceType: Reflect.Bound[A],
    targetType: Reflect.Bound[B]
  ): Transform[A, B] =
    Transform(dynamic, sourceType, targetType)

  // ═══════════════════════════════════════════════════════════════════════════════
  // Identity
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * An identity transformation that returns the input unchanged.
   */
  def identity[A](implicit reflect: Reflect.Bound[A]): Transform[A, A] =
    Transform(DynamicTransform.Identity, reflect, reflect)

  // ═══════════════════════════════════════════════════════════════════════════════
  // String Transforms
  // ═══════════════════════════════════════════════════════════════════════════════

  implicit val stringReflect: Reflect.Bound[String] = Reflect.string
  implicit val intReflect: Reflect.Bound[Int] = Reflect.int
  implicit val longReflect: Reflect.Bound[Long] = Reflect.long
  implicit val doubleReflect: Reflect.Bound[Double] = Reflect.double
  implicit val floatReflect: Reflect.Bound[Float] = Reflect.float
  implicit val booleanReflect: Reflect.Bound[Boolean] = Reflect.boolean

  /**
   * Transforms a string to uppercase.
   */
  val stringToUpperCase: Transform[String, String] =
    Transform(DynamicTransform.StringUpperCase, stringReflect, stringReflect)

  /**
   * Transforms a string to lowercase.
   */
  val stringToLowerCase: Transform[String, String] =
    Transform(DynamicTransform.StringLowerCase, stringReflect, stringReflect)

  /**
   * Trims whitespace from a string.
   */
  val stringTrim: Transform[String, String] =
    Transform(DynamicTransform.StringTrim, stringReflect, stringReflect)

  /**
   * Splits a string by a delimiter.
   */
  def stringSplit(delimiter: String): Transform[String, String] =
    Transform(DynamicTransform.StringSplit(delimiter), stringReflect, stringReflect)

  /**
   * Concatenates strings with a delimiter.
   */
  def stringConcatWith(delimiter: String): Transform[String, String] =
    Transform(DynamicTransform.StringConcatWith(delimiter), stringReflect, stringReflect)

  // ═══════════════════════════════════════════════════════════════════════════════
  // Type Conversions
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Converts a string to an integer.
   */
  val stringToInt: Transform[String, Int] =
    Transform(DynamicTransform.StringToInt, stringReflect, intReflect)

  /**
   * Converts an integer to a string.
   */
  val intToString: Transform[Int, String] =
    Transform(DynamicTransform.IntToString, intReflect, stringReflect)

  /**
   * Converts a string to a long.
   */
  val stringToLong: Transform[String, Long] =
    Transform(DynamicTransform.StringToLong, stringReflect, longReflect)

  /**
   * Converts a long to a string.
   */
  val longToString: Transform[Long, String] =
    Transform(DynamicTransform.LongToString, longReflect, stringReflect)

  /**
   * Converts a string to a double.
   */
  val stringToDouble: Transform[String, Double] =
    Transform(DynamicTransform.StringToDouble, stringReflect, doubleReflect)

  /**
   * Converts a double to a string.
   */
  val doubleToString: Transform[Double, String] =
    Transform(DynamicTransform.DoubleToString, doubleReflect, stringReflect)

  /**
   * Converts a string to a boolean.
   */
  val stringToBoolean: Transform[String, Boolean] =
    Transform(DynamicTransform.StringToBoolean, stringReflect, booleanReflect)

  /**
   * Converts a boolean to a string.
   */
  val booleanToString: Transform[Boolean, String] =
    Transform(DynamicTransform.BooleanToString, booleanReflect, stringReflect)

  /**
   * Widens an integer to a long.
   */
  val intToLong: Transform[Int, Long] =
    Transform(DynamicTransform.IntToLong, intReflect, longReflect)

  /**
   * Narrows a long to an integer (may lose precision).
   */
  val longToInt: Transform[Long, Int] =
    Transform(DynamicTransform.LongToInt, longReflect, intReflect)

  /**
   * Widens a float to a double.
   */
  val floatToDouble: Transform[Float, Double] =
    Transform(DynamicTransform.FloatToDouble, floatReflect, doubleReflect)

  /**
   * Narrows a double to a float (may lose precision).
   */
  val doubleToFloat: Transform[Double, Float] =
    Transform(DynamicTransform.DoubleToFloat, doubleReflect, floatReflect)

  // ═══════════════════════════════════════════════════════════════════════════════
  // Numeric Transforms
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Creates a transform that adds a constant to an integer.
   */
  def intAdd(amount: Int): Transform[Int, Int] =
    Transform(
      DynamicTransform.NumericAdd(DynamicValue.Primitive(PrimitiveValue.Int(amount))),
      intReflect,
      intReflect
    )

  /**
   * Creates a transform that subtracts a constant from an integer.
   */
  def intSubtract(amount: Int): Transform[Int, Int] =
    Transform(
      DynamicTransform.NumericSubtract(DynamicValue.Primitive(PrimitiveValue.Int(amount))),
      intReflect,
      intReflect
    )

  /**
   * Creates a transform that multiplies an integer by a constant.
   */
  def intMultiply(factor: Int): Transform[Int, Int] =
    Transform(
      DynamicTransform.NumericMultiply(DynamicValue.Primitive(PrimitiveValue.Int(factor))),
      intReflect,
      intReflect
    )

  /**
   * Creates a transform that divides an integer by a constant.
   */
  def intDivide(divisor: Int): Transform[Int, Int] =
    Transform(
      DynamicTransform.NumericDivide(DynamicValue.Primitive(PrimitiveValue.Int(divisor))),
      intReflect,
      intReflect
    )

  /**
   * Creates a transform that adds a constant to a long.
   */
  def longAdd(amount: Long): Transform[Long, Long] =
    Transform(
      DynamicTransform.NumericAdd(DynamicValue.Primitive(PrimitiveValue.Long(amount))),
      longReflect,
      longReflect
    )

  /**
   * Creates a transform that subtracts a constant from a long.
   */
  def longSubtract(amount: Long): Transform[Long, Long] =
    Transform(
      DynamicTransform.NumericSubtract(DynamicValue.Primitive(PrimitiveValue.Long(amount))),
      longReflect,
      longReflect
    )

  /**
   * Creates a transform that adds a constant to a double.
   */
  def doubleAdd(amount: Double): Transform[Double, Double] =
    Transform(
      DynamicTransform.NumericAdd(DynamicValue.Primitive(PrimitiveValue.Double(amount))),
      doubleReflect,
      doubleReflect
    )

  /**
   * Creates a transform that subtracts a constant from a double.
   */
  def doubleSubtract(amount: Double): Transform[Double, Double] =
    Transform(
      DynamicTransform.NumericSubtract(DynamicValue.Primitive(PrimitiveValue.Double(amount))),
      doubleReflect,
      doubleReflect
    )

  /**
   * Creates a transform that multiplies a double by a constant.
   */
  def doubleMultiply(factor: Double): Transform[Double, Double] =
    Transform(
      DynamicTransform.NumericMultiply(DynamicValue.Primitive(PrimitiveValue.Double(factor))),
      doubleReflect,
      doubleReflect
    )

  /**
   * Creates a transform that divides a double by a constant.
   */
  def doubleDivide(divisor: Double): Transform[Double, Double] =
    Transform(
      DynamicTransform.NumericDivide(DynamicValue.Primitive(PrimitiveValue.Double(divisor))),
      doubleReflect,
      doubleReflect
    )
}

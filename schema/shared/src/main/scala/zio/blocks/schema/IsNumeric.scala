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
 * Evidence that `A` is one of the number types `Byte`, `Short`, `Int`, `Long`,
 * `Float`, `Double`, `BigInt` and `BigDecimal`, the types for which the
 * arithmetic operators of [[Optic]], such as `+`, `-`, `*`, `/`, `%` and `pow`,
 * build a [[SchemaExpr]]. It is sealed: those eight types are the only
 * instances.
 *
 * For new code, consider [[NumericPrimitiveType]] directly: it is part of the
 * sealed [[PrimitiveType]] hierarchy.
 */
sealed trait IsNumeric[A] {

  /** The primitive type of `A`, without validation. */
  def numericPrimitiveType: NumericPrimitiveType[A]

  /** The same as `numericPrimitiveType`, typed as a [[PrimitiveType]]. */
  def primitiveType: PrimitiveType[A] = numericPrimitiveType

  /** The `scala.math.Numeric` of `A`. */
  def numeric: Numeric[A] = numericPrimitiveType.numeric

  /** The schema of `A`. */
  def schema: Schema[A]
}

/** The eight instances of [[IsNumeric]]. */
object IsNumeric {

  /** `Byte` is numeric. */
  implicit val IsByte: IsNumeric[Byte] = new IsNumeric[Byte] {
    def numericPrimitiveType: NumericPrimitiveType[Byte] = PrimitiveType.Byte(Validation.None)

    def schema: Schema[Byte] = Schema[Byte]
  }

  /** `Short` is numeric. */
  implicit val IsShort: IsNumeric[Short] = new IsNumeric[Short] {
    def numericPrimitiveType: NumericPrimitiveType[Short] = PrimitiveType.Short(Validation.None)

    def schema: Schema[Short] = Schema[Short]
  }

  /** `Int` is numeric. */
  implicit val IsInt: IsNumeric[Int] = new IsNumeric[Int] {
    def numericPrimitiveType: NumericPrimitiveType[Int] = PrimitiveType.Int(Validation.None)

    def schema: Schema[Int] = Schema[Int]
  }

  /** `Long` is numeric. */
  implicit val IsLong: IsNumeric[Long] = new IsNumeric[Long] {
    def numericPrimitiveType: NumericPrimitiveType[Long] = PrimitiveType.Long(Validation.None)

    def schema: Schema[Long] = Schema[Long]
  }

  /** `Float` is numeric. */
  implicit val IsFloat: IsNumeric[Float] = new IsNumeric[Float] {
    def numericPrimitiveType: NumericPrimitiveType[Float] = PrimitiveType.Float(Validation.None)

    def schema: Schema[Float] = Schema[Float]
  }

  /** `Double` is numeric. */
  implicit val IsDouble: IsNumeric[Double] = new IsNumeric[Double] {
    def numericPrimitiveType: NumericPrimitiveType[Double] = PrimitiveType.Double(Validation.None)

    def schema: Schema[Double] = Schema[Double]
  }

  /** `BigInt` is numeric. */
  implicit val IsBigInt: IsNumeric[BigInt] = new IsNumeric[BigInt] {
    def numericPrimitiveType: NumericPrimitiveType[BigInt] = PrimitiveType.BigInt(Validation.None)

    def schema: Schema[BigInt] = Schema[BigInt]
  }

  /** `BigDecimal` is numeric. */
  implicit val IsBigDecimal: IsNumeric[BigDecimal] = new IsNumeric[BigDecimal] {
    def numericPrimitiveType: NumericPrimitiveType[BigDecimal] = PrimitiveType.BigDecimal(Validation.None)

    def schema: Schema[BigDecimal] = Schema[BigDecimal]
  }
}

/*
 * Copyright 2023 ZIO Blocks Maintainers
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

sealed trait IsNumeric[A] {
  val primitiveType: PrimitiveType[A] = schema.reflect.asPrimitive.get.primitiveType

  def numeric: Numeric[A]

  def schema: Schema[A]
}

object IsNumeric {
  implicit val IsByte: IsNumeric[Byte] = new IsNumeric[Byte] {
    def numeric: Numeric[Byte] = implicitly[Numeric[Byte]]

    def schema: Schema[Byte] = Schema[Byte]
  }

  implicit val IsShort: IsNumeric[Short] = new IsNumeric[Short] {
    def numeric: Numeric[Short] = implicitly[Numeric[Short]]

    def schema: Schema[Short] = Schema[Short]
  }

  implicit val IsInt: IsNumeric[Int] = new IsNumeric[Int] {
    def numeric: Numeric[Int] = implicitly[Numeric[Int]]

    def schema: Schema[Int] = Schema[Int]
  }

  implicit val IsLong: IsNumeric[Long] = new IsNumeric[Long] {
    def numeric: Numeric[Long] = implicitly[Numeric[Long]]

    def schema: Schema[Long] = Schema[Long]
  }

  implicit val IsFloat: IsNumeric[Float] = new IsNumeric[Float] {
    def numeric: Numeric[Float] = implicitly[Numeric[Float]]

    def schema: Schema[Float] = Schema[Float]
  }

  implicit val IsDouble: IsNumeric[Double] = new IsNumeric[Double] {
    def numeric: Numeric[Double] = implicitly[Numeric[Double]]

    def schema: Schema[Double] = Schema[Double]
  }

  implicit val IsBigInt: IsNumeric[BigInt] = new IsNumeric[BigInt] {
    def numeric: Numeric[BigInt] = implicitly[Numeric[BigInt]]

    def schema: Schema[BigInt] = Schema[BigInt]
  }

  implicit val IsBigDecimal: IsNumeric[BigDecimal] = new IsNumeric[BigDecimal] {
    def numeric: Numeric[BigDecimal] = implicitly[Numeric[BigDecimal]]

    def schema: Schema[BigDecimal] = Schema[BigDecimal]
  }
}

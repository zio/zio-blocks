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

package zio.blocks.html

trait ToText[-A] {
  def toText(a: A): String
}

object ToText {

  def apply[A](implicit ev: ToText[A]): ToText[A] = ev

  implicit val stringToText: ToText[String] = new ToText[String] {
    def toText(a: String): String = a
  }

  implicit val intToText: ToText[Int] = new ToText[Int] {
    def toText(a: Int): String = a.toString
  }

  implicit val longToText: ToText[Long] = new ToText[Long] {
    def toText(a: Long): String = a.toString
  }

  implicit val doubleToText: ToText[Double] = new ToText[Double] {
    def toText(a: Double): String = a.toString
  }

  implicit val floatToText: ToText[Float] = new ToText[Float] {
    def toText(a: Float): String = a.toString
  }

  implicit val booleanToText: ToText[Boolean] = new ToText[Boolean] {
    def toText(a: Boolean): String = a.toString
  }

  implicit val charToText: ToText[Char] = new ToText[Char] {
    def toText(a: Char): String = a.toString
  }

  implicit val byteToText: ToText[Byte] = new ToText[Byte] {
    def toText(a: Byte): String = a.toString
  }

  implicit val shortToText: ToText[Short] = new ToText[Short] {
    def toText(a: Short): String = a.toString
  }

  implicit val bigIntToText: ToText[BigInt] = new ToText[BigInt] {
    def toText(a: BigInt): String = a.toString
  }

  implicit val bigDecimalToText: ToText[BigDecimal] = new ToText[BigDecimal] {
    def toText(a: BigDecimal): String = a.toString
  }
}

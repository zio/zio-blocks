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

/**
 * Converts a value to plain text, without escaping. Nothing else in the module
 * uses it: the DSL takes values through [[ToModifier]] and `html"..."` through
 * [[ToElements]].
 */
trait ToText[-A] {

  /** Converts the value to plain text, without escaping. */
  def toText(a: A): String
}

/** Instances of [[ToText]] for strings, numbers, booleans and characters. */
object ToText {

  /** Summons the implicit [[ToText]] instance for `A`. */
  def apply[A](implicit ev: ToText[A]): ToText[A] = ev

  /** Returns the `String` unchanged, without escaping. */
  implicit val stringToText: ToText[String] = new ToText[String] {
    def toText(a: String): String = a
  }

  /** Renders an `Int` in decimal. */
  implicit val intToText: ToText[Int] = new ToText[Int] {
    def toText(a: Int): String = a.toString
  }

  /** Renders a `Long` in decimal. */
  implicit val longToText: ToText[Long] = new ToText[Long] {
    def toText(a: Long): String = a.toString
  }

  /**
   * Renders a `Double` with `Double#toString`: `1.0` on the JVM, `1` on
   * Scala.js.
   */
  implicit val doubleToText: ToText[Double] = new ToText[Double] {
    def toText(a: Double): String = a.toString
  }

  /** Renders a `Float` with `Float#toString`. */
  implicit val floatToText: ToText[Float] = new ToText[Float] {
    def toText(a: Float): String = a.toString
  }

  /** Renders a `Boolean` as `true` or `false`. */
  implicit val booleanToText: ToText[Boolean] = new ToText[Boolean] {
    def toText(a: Boolean): String = a.toString
  }

  /** Renders a `Char` as a one-character string. */
  implicit val charToText: ToText[Char] = new ToText[Char] {
    def toText(a: Char): String = a.toString
  }

  /** Renders a `Byte` in decimal. */
  implicit val byteToText: ToText[Byte] = new ToText[Byte] {
    def toText(a: Byte): String = a.toString
  }

  /** Renders a `Short` in decimal. */
  implicit val shortToText: ToText[Short] = new ToText[Short] {
    def toText(a: Short): String = a.toString
  }

  /** Renders a `BigInt` in decimal. */
  implicit val bigIntToText: ToText[BigInt] = new ToText[BigInt] {
    def toText(a: BigInt): String = a.toString
  }

  /**
   * Renders a `BigDecimal` with `BigDecimal#toString`, keeping its scale
   * (`1.50` stays `1.50`).
   */
  implicit val bigDecimalToText: ToText[BigDecimal] = new ToText[BigDecimal] {
    def toText(a: BigDecimal): String = a.toString
  }
}

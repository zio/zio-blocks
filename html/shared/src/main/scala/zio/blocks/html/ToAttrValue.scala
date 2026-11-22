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
 * Typeclass for converting values to HTML attribute values. Escaping of
 * attribute values is handled uniformly at render time by [[Dom]].
 */
trait ToAttrValue[-A] {

  /**
   * Converts the value to the text of an HTML attribute value.
   */
  def toAttrValue(a: A): String
}

/**
 * Instances of [[ToAttrValue]] for the attribute value types the DSL accepts.
 */
object ToAttrValue {

  /** Summons the implicit [[ToAttrValue]] instance for `A`. */
  def apply[A](implicit ev: ToAttrValue[A]): ToAttrValue[A] = ev

  /**
   * Renders a `String` value. Escaping is handled at render time by [[Dom]].
   */
  implicit val stringToAttrValue: ToAttrValue[String] = new ToAttrValue[String] {
    def toAttrValue(a: String): String = a
  }

  /** Renders an `Int` in decimal, with no escaping needed. */
  implicit val intToAttrValue: ToAttrValue[Int] = new ToAttrValue[Int] {
    def toAttrValue(a: Int): String = a.toString
  }

  /** Renders a `Long` in decimal, with no escaping needed. */
  implicit val longToAttrValue: ToAttrValue[Long] = new ToAttrValue[Long] {
    def toAttrValue(a: Long): String = a.toString
  }

  /**
   * Renders a `Double` with `Double#toString`: `1.0` and `1.5e10` become `1.0`
   * and `1.5E10` on the JVM, and `1` and `15000000000` on Scala.js.
   */
  implicit val doubleToAttrValue: ToAttrValue[Double] = new ToAttrValue[Double] {
    def toAttrValue(a: Double): String = a.toString
  }

  /** Renders a `Boolean` as `true` or `false`. */
  implicit val booleanToAttrValue: ToAttrValue[Boolean] = new ToAttrValue[Boolean] {
    def toAttrValue(a: Boolean): String = a.toString
  }

  /** Renders a `Char` as a one-character string. */
  implicit val charToAttrValue: ToAttrValue[Char] = new ToAttrValue[Char] {
    def toAttrValue(a: Char): String = a.toString
  }

  /** Renders the source text of a [[Js]] value. */
  implicit val jsToAttrValue: ToAttrValue[Js] = new ToAttrValue[Js] {
    def toAttrValue(a: Js): String = a.value
  }

  /**
   * Renders a [[Css]] value with its `render` text.
   */
  implicit val cssToAttrValue: ToAttrValue[Css] = new ToAttrValue[Css] {
    def toAttrValue(a: Css): String = a.render
  }

  /**
   * Renders the wrapped value of a `Some` with the instance for `A`, and an
   * empty string for `None`.
   */
  implicit def optionToAttrValue[A](implicit ev: ToAttrValue[A]): ToAttrValue[Option[A]] =
    new ToAttrValue[Option[A]] {
      def toAttrValue(a: Option[A]): String = a match {
        case Some(v) => ev.toAttrValue(v)
        case None    => ""
      }
    }

  /**
   * Renders each string and joins them with a single space.
   */
  implicit def iterableStringToAttrValue: ToAttrValue[Iterable[String]] =
    new ToAttrValue[Iterable[String]] {
      def toAttrValue(a: Iterable[String]): String = {
        val sb  = new java.lang.StringBuilder
        val it  = a.iterator
        var sep = false
        while (it.hasNext) {
          if (sep) sb.append(' ')
          sb.append(it.next())
          sep = true
        }
        sb.toString
      }
    }
}
